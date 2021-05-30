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

import org.projectnessie.cel.common.debug.Debug;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.pb.Constant;
import org.projectnessie.cel.pb.Expr;
import org.projectnessie.cel.pb.Expr.CallExpr;
import org.projectnessie.cel.pb.Expr.ComprehensionExpr;
import org.projectnessie.cel.pb.Expr.Const;
import org.projectnessie.cel.pb.Expr.IdentExpr;
import org.projectnessie.cel.pb.Expr.ListExpr;
import org.projectnessie.cel.pb.Expr.SelectExpr;
import org.projectnessie.cel.pb.Expr.StructExpr;
import org.projectnessie.cel.pb.Expr.StructExpr.Entry;
import org.projectnessie.cel.pb.Expr.StructExpr.FieldKey;
import org.projectnessie.cel.pb.Expr.StructExpr.MapKey;
import org.projectnessie.cel.pb.SourceInfo;

/**
 * Unparse takes an input expression and source position information and generates a human-readable
 * expression.
 *
 * <p>Note, unparsing an AST will often generate the same expression as was originally parsed, but
 * some formatting may be lost in translation, notably:
 *
 * <p>- All quoted literals are doubled quoted. - Byte literals are represented as octal escapes
 * (same as Google SQL). - Floating point values are converted to the small number of digits needed
 * to represent the value. - Spacing around punctuation marks may be lost. - Parentheses will only
 * be applied when they affect operator precedence.
 */
public class Unparser {

  private final SourceInfo info;
  private final StringBuilder str;
  private int offset;

  /** unparser visits an expression to reconstruct a human-readable string from an AST. */
  public static String unparse(Expr expr, SourceInfo info) {
    Unparser unparser = new Unparser(info);
    unparser.visit(expr);
    return unparser.str.toString();
  }

  private Unparser(SourceInfo info) {
    this.info = info;
    str = new StringBuilder();
  }

  void visit(Expr expr) {
    if (expr instanceof CallExpr) {
      visitCall((CallExpr) expr);
    } else if (expr instanceof ComprehensionExpr) {
      visitComprehension((ComprehensionExpr) expr);
    } else if (expr instanceof Const) {
      visitConst((Const) expr);
    } else if (expr instanceof IdentExpr) {
      visitIdent((IdentExpr) expr);
    } else if (expr instanceof ListExpr) {
      visitList((ListExpr) expr);
    } else if (expr instanceof SelectExpr) {
      visitSelect((SelectExpr) expr);
    } else if (expr instanceof StructExpr) {
      visitStruct((StructExpr) expr);
    } else {
      throw new UnsupportedOperationException(
          String.format("Unsupported expr: %s", expr.getClass().getSimpleName()));
    }
  }

  void visitCall(CallExpr expr) {
    Operator op = Operator.byId(expr.function);
    if (op != null) {
      switch (op) {
          // ternary operator
        case Conditional:
          visitCallConditional(expr);
          return;
          // index operator
        case Index:
          visitCallIndex(expr);
          return;
          // unary operators
        case LogicalNot:
        case Negate:
          visitCallUnary(expr);
          return;
          // binary operators
        case Add:
        case Divide:
        case Equals:
        case Greater:
        case GreaterEquals:
        case In:
        case Less:
        case LessEquals:
        case LogicalAnd:
        case LogicalOr:
        case Modulo:
        case Multiply:
        case NotEquals:
        case OldIn:
        case Subtract:
          visitCallBinary(expr);
          return;
      }
    }
    // standard function calls.
    visitCallFunc(expr);
  }

  void visitCallBinary(CallExpr expr) {
    String fun = expr.function;
    Expr[] args = expr.args;
    Expr lhs = args[0];
    // add parens if the current operator is lower precedence than the lhs expr operator.
    boolean lhsParen = isComplexOperatorWithRespectTo(fun, lhs);
    Expr rhs = args[1];
    // add parens if the current operator is lower precedence than the rhs expr operator,
    // or the same precedence and the operator is left recursive.
    boolean rhsParen = isComplexOperatorWithRespectTo(fun, rhs);
    if (!rhsParen && isLeftRecursive(fun)) {
      rhsParen = isSamePrecedence(Operator.precedence(fun), rhs);
    }
    visitMaybeNested(lhs, lhsParen);
    String unmangled = Operator.findReverseBinaryOperator(fun);
    if (unmangled == null) {
      throw new IllegalStateException(String.format("cannot unmangle operator: %s", fun));
    }
    str.append(" ");
    str.append(unmangled);
    str.append(" ");
    visitMaybeNested(rhs, rhsParen);
  }

  void visitCallConditional(CallExpr expr) {
    Expr[] args = expr.args;
    // add parens if operand is a conditional itself.
    boolean nested =
        isSamePrecedence(Operator.Conditional.precedence, args[0]) || isComplexOperator(args[0]);
    visitMaybeNested(args[0], nested);
    str.append(" ? ");
    // add parens if operand is a conditional itself.
    nested =
        isSamePrecedence(Operator.Conditional.precedence, args[1]) || isComplexOperator(args[1]);
    visitMaybeNested(args[1], nested);
    str.append(" : ");
    // add parens if operand is a conditional itself.
    nested =
        isSamePrecedence(Operator.Conditional.precedence, args[2]) || isComplexOperator(args[2]);

    visitMaybeNested(args[2], nested);
  }

  void visitCallFunc(CallExpr expr) {
    String fun = expr.function;
    Expr[] args = expr.args;
    if (expr.target != null) {
      boolean nested = isBinaryOrTernaryOperator(expr.target);
      visitMaybeNested(expr.target, nested);
      str.append(".");
    }
    str.append(fun);
    str.append("(");
    for (int i = 0; i < args.length; i++) {
      if (i > 0) {
        str.append(", ");
      }
      visit(args[i]);
    }
    str.append(")");
  }

  void visitCallIndex(CallExpr expr) {
    Expr[] args = expr.args;
    boolean nested = isBinaryOrTernaryOperator(args[0]);
    visitMaybeNested(args[0], nested);
    str.append("[");
    visit(args[1]);
    str.append("]");
  }

  void visitCallUnary(CallExpr expr) {
    String fun = expr.function;
    Expr[] args = expr.args;
    String unmangled = Operator.findReverse(fun);
    if (unmangled == null) {
      throw new IllegalStateException(String.format("cannot unmangle operator: %s", fun));
    }
    str.append(unmangled);
    boolean nested = isComplexOperator(args[0]);
    visitMaybeNested(args[0], nested);
  }

  void visitComprehension(ComprehensionExpr expr) {
    // TODO: introduce a macro expansion map between the top-level comprehension id and the
    // function call that the macro replaces.
    throw new IllegalStateException(
        String.format("unimplemented : %s", expr.getClass().getSimpleName()));
  }

  void visitConst(Const expr) {
    Constant v = expr.value;
    str.append(Debug.formatLiteral(v));
  }

  void visitIdent(IdentExpr expr) {
    str.append(expr.name);
  }

  void visitList(ListExpr expr) {
    Expr[] elems = expr.elements;
    str.append("[");
    for (int i = 0; i < elems.length; i++) {
      if (i > 0) {
        str.append(", ");
      }
      Expr elem = elems[i];
      visit(elem);
    }
    str.append("]");
  }

  void visitSelect(SelectExpr expr) {
    // handle the case when the select expression was generated by the has() macro.
    if (expr.testOnly) {
      str.append("has(");
    }
    boolean nested = !expr.testOnly && isBinaryOrTernaryOperator(expr.operand);
    visitMaybeNested(expr.operand, nested);
    str.append(".");
    str.append(expr.field);
    if (expr.testOnly) {
      str.append(")");
    }
  }

  void visitStruct(StructExpr expr) {
    // If the message name is non-empty, then this should be treated as message construction.
    if (expr.messageName != null) {
      visitStructMsg(expr);
    } else {
      // Otherwise, build a map.
      visitStructMap(expr);
    }
  }

  void visitStructMsg(StructExpr expr) {
    Entry[] entries = expr.entries;
    str.append(expr.messageName);
    str.append("{");
    for (int i = 0; i < entries.length; i++) {
      if (i > 0) {
        str.append(", ");
      }
      Entry entry = entries[i];
      String f = ((FieldKey) entry.key).field;
      str.append(f);
      str.append(": ");
      Expr v = entry.value;
      visit(v);
    }
    str.append("}");
  }

  void visitStructMap(StructExpr expr) {
    Entry[] entries = expr.entries;
    str.append("{");
    for (int i = 0; i < entries.length; i++) {
      if (i > 0) {
        str.append(", ");
      }
      Entry entry = entries[i];
      Expr k = ((MapKey) entry.key).mapKey;
      visit(k);
      str.append(": ");
      Expr v = entry.value;
      visit(v);
    }
    str.append("}");
  }

  void visitMaybeNested(Expr expr, boolean nested) {
    if (nested) {
      str.append("(");
    }
    visit(expr);
    if (nested) {
      str.append(")");
    }
  }

  /**
   * isLeftRecursive indicates whether the parser resolves the call in a left-recursive manner as
   * this can have an effect of how parentheses affect the order of operations in the AST.
   */
  boolean isLeftRecursive(String op) {
    Operator o = Operator.byId(op);
    return o != Operator.LogicalAnd && o != Operator.LogicalOr;
  }

  /*** isSamePrecedence indicates whether the precedence of the input operator is the same as the
   * precedence of the (possible) operation represented in the input Expr.
   *
   * If the expr is not a Call, the result is false. */
  boolean isSamePrecedence(int opPrecedence, Expr expr) {
    if (!(expr instanceof CallExpr)) {
      return false;
    }
    String other = ((CallExpr) expr).function;
    return opPrecedence == Operator.precedence(other);
  }

  /**
   * isLowerPrecedence indicates whether the precedence of the input operator is lower precedence
   * than the (possible) operation represented in the input Expr.
   *
   * <p>If the expr is not a Call, the result is false.
   */
  boolean isLowerPrecedence(String op, Expr expr) {
    if (!(expr instanceof CallExpr)) {
      return false;
    }
    String other = ((CallExpr) expr).function;
    return Operator.precedence(op) < Operator.precedence(other);
  }

  /**
   * Indicates whether the expr is a complex operator, i.e., a call expression with 2 or more
   * arguments.
   */
  boolean isComplexOperator(Expr expr) {
    return expr instanceof CallExpr && ((CallExpr) expr).args.length >= 2;
  }

  /**
   * Indicates whether it is a complex operation compared to another. expr is *not* considered
   * complex if it is not a call expression or has less than two arguments, or if it has a higher
   * precedence than op.
   */
  boolean isComplexOperatorWithRespectTo(String op, Expr expr) {
    if (!isComplexOperator(expr)) {
      return false;
    }
    return isLowerPrecedence(op, expr);
  }

  /** Indicate whether this is a binary or ternary operator. */
  boolean isBinaryOrTernaryOperator(Expr expr) {
    if (!isComplexOperator(expr)) {
      return false;
    }
    boolean isBinaryOp = Operator.findReverseBinaryOperator(((CallExpr) expr).function) != null;
    return isBinaryOp || isSamePrecedence(Operator.Conditional.precedence, expr);
  }
}
