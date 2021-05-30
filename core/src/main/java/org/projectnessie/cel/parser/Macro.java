/*
 * Copyright (C) 2021 The Authors of CEL-Java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projectnessie.cel.parser;

import java.util.function.Supplier;
import org.projectnessie.cel.common.ErrorWithLocation;
import org.projectnessie.cel.common.Location;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.pb.Expr;
import org.projectnessie.cel.pb.Expr.IdentExpr;
import org.projectnessie.cel.pb.Expr.SelectExpr;

public class Macro {
  /** AccumulatorName is the traditional variable name assigned to the fold accumulator variable. */
  public static final String AccumulatorName = "__result__";
  /** AllMacros includes the list of all spec-supported macros. */
  public static final Macro[] AllMacros =
      new Macro[] {
        /* The macro "has(m.f)" which tests the presence of a field, avoiding the need to specify
         * the field as a string.
         */
        newGlobalMacro(Operator.Has.id, 1, Macro::makeHas),

        /* The macro "range.all(var, predicate)", which is true if for all elements in range the
         * predicate holds.
         */
        newReceiverMacro(Operator.All.id, 2, Macro::makeAll),

        /* The macro "range.exists(var, predicate)", which is true if for at least one element in
         * range the predicate holds.
         */
        newReceiverMacro(Operator.Exists.id, 2, Macro::makeExists),

        /* The macro "range.exists_one(var, predicate)", which is true if for exactly one element
         * in range the predicate holds.
         */
        newReceiverMacro(Operator.ExistsOne.id, 2, Macro::makeExistsOne),

        /* The macro "range.map(var, function)", applies the function to the vars in the range. */
        newReceiverMacro(Operator.Map.id, 2, Macro::makeMap),

        /* The macro "range.map(var, predicate, function)", applies the function to the vars in
         * the range for which the predicate holds true. The other variables are filtered out.
         */
        newReceiverMacro(Operator.Map.id, 3, Macro::makeMap),

        /* The macro "range.filter(var, predicate)", filters out the variables for which the
         * predicate is false.
         */
        newReceiverMacro(Operator.Filter.id, 2, Macro::makeFilter),
      };

  /** NoMacros list. */
  public static Macro[] MoMacros = new Macro[0];

  private final String function;
  private final boolean receiverStyle;
  private final boolean varArgStyle;
  private final int argCount;
  private final MacroExpander expander;

  public Macro(
      String function,
      boolean receiverStyle,
      boolean varArgStyle,
      int argCount,
      MacroExpander expander) {
    this.function = function;
    this.receiverStyle = receiverStyle;
    this.varArgStyle = varArgStyle;
    this.argCount = argCount;
    this.expander = expander;
  }

  static String makeMacroKey(String name, int args, boolean receiverStyle) {
    return String.format("%s:%d:%s", name, args, receiverStyle);
  }

  static String makeVarArgMacroKey(String name, boolean receiverStyle) {
    return String.format("%s:*:%s", name, receiverStyle);
  }

  /** NewGlobalMacro creates a Macro for a global function with the specified arg count. */
  static Macro newGlobalMacro(String function, int argCount, MacroExpander expander) {
    return new Macro(function, false, false, argCount, expander);
  }

  /** NewReceiverMacro creates a Macro for a receiver function matching the specified arg count. */
  static Macro newReceiverMacro(String function, int argCount, MacroExpander expander) {
    return new Macro(function, true, false, argCount, expander);
  }

  /** NewGlobalVarArgMacro creates a Macro for a global function with a variable arg count. */
  static Macro newGlobalVarArgMacro(String function, MacroExpander expander) {
    return new Macro(function, false, true, 0, expander);
  }

  /**
   * NewReceiverVarArgMacro creates a Macro for a receiver function matching a variable arg count.
   */
  static Macro newReceiverVarArgMacro(String function, MacroExpander expander) {
    return new Macro(function, true, true, 0, expander);
  }

  static Expr makeAll(ExprHelper eh, Expr target, Expr[] args) {
    return makeQuantifier(QuantifierKind.quantifierAll, eh, target, args);
  }

  static Expr makeExists(ExprHelper eh, Expr target, Expr[] args) {
    return makeQuantifier(QuantifierKind.quantifierExists, eh, target, args);
  }

  static Expr makeExistsOne(ExprHelper eh, Expr target, Expr[] args) {
    return makeQuantifier(QuantifierKind.quantifierExistsOne, eh, target, args);
  }

  static Expr makeQuantifier(QuantifierKind kind, ExprHelper eh, Expr target, Expr[] args) {
    String v = extractIdent(args[0]);
    if (v == null) {
      Location location = eh.offsetLocation(args[0].id);
      throw new ErrorWithLocation(location, "argument must be a simple name");
    }

    Supplier<Expr> accuIdent = () -> eh.ident(AccumulatorName);

    Expr init;
    Expr condition;
    Expr step;
    Expr result;
    switch (kind) {
      case quantifierAll:
        init = eh.literalBool(true);
        condition = eh.globalCall(Operator.NotStrictlyFalse.id, accuIdent.get());
        step = eh.globalCall(Operator.LogicalAnd.id, accuIdent.get(), args[1]);
        result = accuIdent.get();
        break;
      case quantifierExists:
        init = eh.literalBool(false);
        condition =
            eh.globalCall(
                Operator.NotStrictlyFalse.id,
                eh.globalCall(Operator.LogicalNot.id, accuIdent.get()));
        step = eh.globalCall(Operator.LogicalOr.id, accuIdent.get(), args[1]);
        result = accuIdent.get();
        break;
      case quantifierExistsOne:
        Expr zeroExpr = eh.literalInt(0);
        Expr oneExpr = eh.literalInt(1);
        init = zeroExpr;
        condition = eh.literalBool(true);
        step =
            eh.globalCall(
                Operator.Conditional.id,
                args[1],
                eh.globalCall(Operator.Add.id, accuIdent.get(), oneExpr),
                accuIdent.get());
        result = eh.globalCall(Operator.Equals.id, accuIdent.get(), oneExpr);
        break;
      default:
        throw new ErrorWithLocation(null, String.format("unrecognized quantifier '%s'", kind));
    }
    return eh.fold(v, target, AccumulatorName, init, condition, step, result);
  }

  static Expr makeMap(ExprHelper eh, Expr target, Expr[] args) {
    String v = extractIdent(args[0]);
    if (v == null) {
      throw new ErrorWithLocation(null, "argument is not an identifier");
    }

    Expr fn;
    Expr filter;

    if (args.length == 3) {
      filter = args[1];
      fn = args[2];
    } else {
      filter = null;
      fn = args[1];
    }

    Expr accuExpr = eh.ident(AccumulatorName);
    Expr init = eh.newList();
    Expr condition = eh.literalBool(true);
    // TODO: use compiler internal method for faster, stateful add.
    Expr step = eh.globalCall(Operator.Add.id, accuExpr, eh.newList(fn));

    if (filter != null) {
      step = eh.globalCall(Operator.Conditional.id, filter, step, accuExpr);
    }
    return eh.fold(v, target, AccumulatorName, init, condition, step, accuExpr);
  }

  static Expr makeFilter(ExprHelper eh, Expr target, Expr[] args) {
    String v = extractIdent(args[0]);
    if (v == null) {
      throw new ErrorWithLocation(null, "argument is not an identifier");
    }

    Expr filter = args[1];
    Expr accuExpr = eh.ident(AccumulatorName);
    Expr init = eh.newList();
    Expr condition = eh.literalBool(true);
    // TODO: use compiler internal method for faster, stateful add.
    Expr step = eh.globalCall(Operator.Add.id, accuExpr, eh.newList(args[0]));
    step = eh.globalCall(Operator.Conditional.id, filter, step, accuExpr);
    return eh.fold(v, target, AccumulatorName, init, condition, step, accuExpr);
  }

  static String extractIdent(Expr e) {
    if (e instanceof IdentExpr) {
      return ((IdentExpr) e).name;
    }
    return null;
  }

  static Expr makeHas(ExprHelper eh, Expr target, Expr[] args) {
    if (args[0] instanceof SelectExpr) {
      SelectExpr s = (SelectExpr) args[0];
      return eh.presenceTest(s.operand, s.field);
    }
    throw new ErrorWithLocation(null, "invalid argument to has() macro");
  }

  public String function() {
    return function;
  }

  public boolean isReceiverStyle() {
    return receiverStyle;
  }

  public boolean isVarArgStyle() {
    return varArgStyle;
  }

  public int argCount() {
    return argCount;
  }

  public MacroExpander expander() {
    return expander;
  }

  public String macroKey() {
    if (varArgStyle) {
      return makeVarArgMacroKey(function, receiverStyle);
    }
    return makeMacroKey(function, argCount, receiverStyle);
  }

  enum QuantifierKind {
    quantifierAll,
    quantifierExists,
    quantifierExistsOne
  }
}
