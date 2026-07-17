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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.CreateList;
import com.google.api.expr.v1alpha1.Expr.ExprKindCase;
import com.google.api.expr.v1alpha1.Expr.Select;
import java.util.List;
import java.util.function.Supplier;
import org.projectnessie.cel.common.ErrorWithLocation;
import org.projectnessie.cel.common.Location;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.Overloads;

public final class Macro {
  /** AccumulatorName is the traditional variable name assigned to the fold accumulator variable. */
  public static final String AccumulatorName = "__result__";

  /** AllMacros includes the list of all spec-supported macros. */
  public static final List<Macro> AllMacros =
      asList(
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

          /* The macro "cel.bind(var, value, result)" binds a local variable for use in result. */
          newReceiverMacro("bind", 3, Macro::makeBind));

  /** TestOnlyBlockMacros includes the test-only macros used by CEL-Spec block conformance tests. */
  public static final List<Macro> TestOnlyBlockMacros =
      asList(
          newReceiverMacro("block", 2, Macro::makeBlock),
          newReceiverMacro("index", 1, Macro::makeIndex),
          newReceiverMacro("iterVar", 2, Macro::makeIterVar),
          newReceiverMacro("accuVar", 2, Macro::makeAccuVar));

  /** NoMacros list. */
  public static List<Macro> MoMacros = emptyList();

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

  @Override
  public String toString() {
    return "Macro{"
        + "function='"
        + function
        + '\''
        + ", receiverStyle="
        + receiverStyle
        + ", varArgStyle="
        + varArgStyle
        + ", argCount="
        + argCount
        + '}';
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
  public static Macro newReceiverMacro(String function, int argCount, MacroExpander expander) {
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

  static Expr makeAll(ExprHelper eh, Expr target, List<Expr> args) {
    return makeQuantifier(QuantifierKind.quantifierAll, eh, target, args);
  }

  static Expr makeExists(ExprHelper eh, Expr target, List<Expr> args) {
    return makeQuantifier(QuantifierKind.quantifierExists, eh, target, args);
  }

  static Expr makeExistsOne(ExprHelper eh, Expr target, List<Expr> args) {
    return makeQuantifier(QuantifierKind.quantifierExistsOne, eh, target, args);
  }

  static Expr makeQuantifier(QuantifierKind kind, ExprHelper eh, Expr target, List<Expr> args) {
    String v = extractIdent(args.get(0));
    if (v == null) {
      Location location = eh.offsetLocation(args.get(0).getId());
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
        step = eh.globalCall(Operator.LogicalAnd.id, accuIdent.get(), args.get(1));
        result = accuIdent.get();
        break;
      case quantifierExists:
        init = eh.literalBool(false);
        condition =
            eh.globalCall(
                Operator.NotStrictlyFalse.id,
                eh.globalCall(Operator.LogicalNot.id, accuIdent.get()));
        step = eh.globalCall(Operator.LogicalOr.id, accuIdent.get(), args.get(1));
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
                args.get(1),
                eh.globalCall(Operator.Add.id, accuIdent.get(), oneExpr),
                accuIdent.get());
        result = eh.globalCall(Operator.Equals.id, accuIdent.get(), oneExpr);
        break;
      default:
        throw new ErrorWithLocation(null, String.format("unrecognized quantifier '%s'", kind));
    }
    return eh.fold(v, target, AccumulatorName, init, condition, step, result);
  }

  static Expr makeMap(ExprHelper eh, Expr target, List<Expr> args) {
    String v = extractIdent(args.get(0));
    if (v == null) {
      throw new ErrorWithLocation(null, "argument is not an identifier");
    }

    Expr fn;
    Expr filter;

    if (args.size() == 3) {
      filter = args.get(1);
      fn = args.get(2);
    } else {
      filter = null;
      fn = args.get(1);
    }

    Expr accuExpr = eh.ident(AccumulatorName);
    Expr init = eh.newList();
    Expr condition = eh.literalBool(true);
    Expr step = eh.globalCall(Operator.Add.id, accuExpr, eh.newList(fn));

    if (filter != null) {
      step = eh.globalCall(Operator.Conditional.id, filter, step, accuExpr);
    }
    return eh.fold(v, target, AccumulatorName, init, condition, step, accuExpr);
  }

  static Expr makeFilter(ExprHelper eh, Expr target, List<Expr> args) {
    String v = extractIdent(args.get(0));
    if (v == null) {
      throw new ErrorWithLocation(null, "argument is not an identifier");
    }

    Expr filter = args.get(1);
    Expr accuExpr = eh.ident(AccumulatorName);
    Expr init = eh.newList();
    Expr condition = eh.literalBool(true);
    Expr step = eh.globalCall(Operator.Add.id, accuExpr, eh.newList(args.get(0)));
    step = eh.globalCall(Operator.Conditional.id, filter, step, accuExpr);
    return eh.fold(v, target, AccumulatorName, init, condition, step, accuExpr);
  }

  static Expr makeBind(ExprHelper eh, Expr target, List<Expr> args) {
    if (target == null
        || target.getExprKindCase() != ExprKindCase.IDENT_EXPR
        || !"cel".equals(target.getIdentExpr().getName())) {
      Location location = target != null ? eh.offsetLocation(target.getId()) : null;
      throw new ErrorWithLocation(
          location, "cel.bind() must be called with receiver identifier cel");
    }

    String v = extractIdent(args.get(0));
    if (v == null) {
      Location location = eh.offsetLocation(args.get(0).getId());
      throw new ErrorWithLocation(location, "argument must be a simple name");
    }

    Expr accuExpr = eh.ident(AccumulatorName);
    Expr dynNull = eh.globalCall(Overloads.TypeConvertDyn, eh.literalNull());
    return eh.fold(
        v,
        eh.newList(args.get(1)),
        AccumulatorName,
        dynNull,
        eh.literalBool(true),
        args.get(2),
        accuExpr);
  }

  static Expr makeBlock(ExprHelper eh, Expr target, List<Expr> args) {
    validateCelReceiver(eh, target, "cel.block()");

    Expr bindings = args.get(0);
    if (bindings.getExprKindCase() != ExprKindCase.LIST_EXPR) {
      Location location = eh.offsetLocation(bindings.getId());
      throw new ErrorWithLocation(location, "cel.block() first argument must be a list literal");
    }

    CreateList list = bindings.getListExpr();
    Expr result = args.get(1);
    for (int i = list.getElementsCount() - 1; i >= 0; i--) {
      result = makeLocalBinding(eh, indexName(i), list.getElements(i), result);
    }
    return result;
  }

  static Expr makeIndex(ExprHelper eh, Expr target, List<Expr> args) {
    validateCelReceiver(eh, target, "cel.index()");
    return eh.ident(indexName(extractIntegerArgument(eh, args.get(0), "cel.index()")));
  }

  static Expr makeIterVar(ExprHelper eh, Expr target, List<Expr> args) {
    validateCelReceiver(eh, target, "cel.iterVar()");
    return eh.ident(
        String.format(
            "@it:%d:%d",
            extractIntegerArgument(eh, args.get(0), "cel.iterVar()"),
            extractIntegerArgument(eh, args.get(1), "cel.iterVar()")));
  }

  static Expr makeAccuVar(ExprHelper eh, Expr target, List<Expr> args) {
    validateCelReceiver(eh, target, "cel.accuVar()");
    return eh.ident(
        String.format(
            "@ac:%d:%d",
            extractIntegerArgument(eh, args.get(0), "cel.accuVar()"),
            extractIntegerArgument(eh, args.get(1), "cel.accuVar()")));
  }

  private static Expr makeLocalBinding(ExprHelper eh, String variable, Expr value, Expr result) {
    Expr accuExpr = eh.ident(AccumulatorName);
    Expr dynNull = eh.globalCall(Overloads.TypeConvertDyn, eh.literalNull());
    return eh.fold(
        variable,
        eh.newList(value),
        AccumulatorName,
        dynNull,
        eh.literalBool(true),
        result,
        accuExpr);
  }

  private static void validateCelReceiver(ExprHelper eh, Expr target, String macroName) {
    if (target != null
        && target.getExprKindCase() == ExprKindCase.IDENT_EXPR
        && "cel".equals(target.getIdentExpr().getName())) {
      return;
    }

    Location location = target != null ? eh.offsetLocation(target.getId()) : null;
    throw new ErrorWithLocation(
        location, macroName + " must be called with receiver identifier cel");
  }

  private static int extractIntegerArgument(ExprHelper eh, Expr expr, String macroName) {
    if (expr.getExprKindCase() != ExprKindCase.CONST_EXPR || !expr.getConstExpr().hasInt64Value()) {
      Location location = eh.offsetLocation(expr.getId());
      throw new ErrorWithLocation(location, macroName + " argument must be an integer literal");
    }

    long value = expr.getConstExpr().getInt64Value();
    if (value < 0 || value > Integer.MAX_VALUE) {
      Location location = eh.offsetLocation(expr.getId());
      throw new ErrorWithLocation(location, macroName + " argument out of range");
    }
    return (int) value;
  }

  private static String indexName(int index) {
    return "@index" + index;
  }

  static String extractIdent(Expr e) {
    if (e.getExprKindCase() == ExprKindCase.IDENT_EXPR) {
      return e.getIdentExpr().getName();
    }
    return null;
  }

  static Expr makeHas(ExprHelper eh, Expr target, List<Expr> args) {
    if (args.get(0).getExprKindCase() == ExprKindCase.SELECT_EXPR) {
      Select s = args.get(0).getSelectExpr();
      return eh.presenceTest(s.getOperand(), s.getField());
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
