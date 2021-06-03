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

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Call;
import com.google.api.expr.v1alpha1.Expr.Comprehension;
import com.google.api.expr.v1alpha1.Expr.CreateList;
import com.google.api.expr.v1alpha1.Expr.CreateStruct;
import com.google.api.expr.v1alpha1.Expr.CreateStruct.Entry;
import com.google.api.expr.v1alpha1.Expr.ExprKindCase;
import com.google.api.expr.v1alpha1.Expr.Ident;
import com.google.api.expr.v1alpha1.Expr.Select;
import com.google.api.expr.v1alpha1.SourceInfo;
import java.util.List;
import org.projectnessie.cel.common.debug.Debug;
import org.projectnessie.cel.common.operators.Operator;

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
    switch (expr.getExprKindCase()) {
      case CALL_EXPR:
        visitCall(expr.getCallExpr());
        break;
      case COMPREHENSION_EXPR:
        visitComprehension(expr.getComprehensionExpr());
        break;
      case CONST_EXPR:
        visitConst(expr.getConstExpr());
        break;
      case IDENT_EXPR:
        visitIdent(expr.getIdentExpr());
        break;
      case LIST_EXPR:
        visitList(expr.getListExpr());
        break;
      case SELECT_EXPR:
        visitSelect(expr.getSelectExpr());
        break;
      case STRUCT_EXPR:
        visitStruct(expr.getStructExpr());
        break;
      default:
        throw new UnsupportedOperationException(
            String.format("Unsupported expr: %s", expr.getClass().getSimpleName()));
    }
  }

  void visitCall(Call expr) {
    Operator op = Operator.byId(expr.getFunction());
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

  void visitCallBinary(Call expr) {
    String fun = expr.getFunction();
    List<Expr> args = expr.getArgsList();
    Expr lhs = args.get(0);
    // add parens if the current operator is lower precedence than the lhs expr operator.
    boolean lhsParen = isComplexOperatorWithRespectTo(fun, lhs);
    Expr rhs = args.get(1);
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

  void visitCallConditional(Call expr) {
    List<Expr> args = expr.getArgsList();
    // add parens if operand is a conditional itself.
    boolean nested =
        isSamePrecedence(Operator.Conditional.precedence, args.get(0))
            || isComplexOperator(args.get(0));
    visitMaybeNested(args.get(0), nested);
    str.append(" ? ");
    // add parens if operand is a conditional itself.
    nested =
        isSamePrecedence(Operator.Conditional.precedence, args.get(1))
            || isComplexOperator(args.get(1));
    visitMaybeNested(args.get(1), nested);
    str.append(" : ");
    // add parens if operand is a conditional itself.
    nested =
        isSamePrecedence(Operator.Conditional.precedence, args.get(2))
            || isComplexOperator(args.get(2));

    visitMaybeNested(args.get(2), nested);
  }

  void visitCallFunc(Call expr) {
    String fun = expr.getFunction();
    List<Expr> args = expr.getArgsList();
    if (expr.hasTarget()) {
      boolean nested = isBinaryOrTernaryOperator(expr.getTarget());
      visitMaybeNested(expr.getTarget(), nested);
      str.append(".");
    }
    str.append(fun);
    str.append("(");
    for (int i = 0; i < args.size(); i++) {
      if (i > 0) {
        str.append(", ");
      }
      visit(args.get(i));
    }
    str.append(")");
  }

  void visitCallIndex(Call expr) {
    List<Expr> args = expr.getArgsList();
    boolean nested = isBinaryOrTernaryOperator(args.get(0));
    visitMaybeNested(args.get(0), nested);
    str.append("[");
    visit(args.get(1));
    str.append("]");
  }

  void visitCallUnary(Call expr) {
    String fun = expr.getFunction();
    List<Expr> args = expr.getArgsList();
    String unmangled = Operator.findReverse(fun);
    if (unmangled == null) {
      throw new IllegalStateException(String.format("cannot unmangle operator: %s", fun));
    }
    str.append(unmangled);
    boolean nested = isComplexOperator(args.get(0));
    visitMaybeNested(args.get(0), nested);
  }

  void visitComprehension(Comprehension expr) {
    // TODO: introduce a macro expansion map between the top-level comprehension id and the
    // function call that the macro replaces.
    throw new IllegalStateException(
        String.format("unimplemented : %s", expr.getClass().getSimpleName()));
  }

  void visitConst(Constant v) {
    str.append(Debug.formatLiteral(v));
  }

  void visitIdent(Ident expr) {
    str.append(expr.getName());
  }

  void visitList(CreateList expr) {
    List<Expr> elems = expr.getElementsList();
    str.append("[");
    for (int i = 0; i < elems.size(); i++) {
      if (i > 0) {
        str.append(", ");
      }
      Expr elem = elems.get(i);
      visit(elem);
    }
    str.append("]");
  }

  void visitSelect(Select expr) {
    // handle the case when the select expression was generated by the has() macro.
    if (expr.getTestOnly()) {
      str.append("has(");
    }
    boolean nested = !expr.getTestOnly() && isBinaryOrTernaryOperator(expr.getOperand());
    visitMaybeNested(expr.getOperand(), nested);
    str.append(".");
    str.append(expr.getField());
    if (expr.getTestOnly()) {
      str.append(")");
    }
  }

  void visitStruct(CreateStruct expr) {
    // If the message name is non-empty, then this should be treated as message construction.
    if (!expr.getMessageName().isEmpty()) {
      visitStructMsg(expr);
    } else {
      // Otherwise, build a map.
      visitStructMap(expr);
    }
  }

  void visitStructMsg(CreateStruct expr) {
    List<Entry> entries = expr.getEntriesList();
    str.append(expr.getMessageName());
    str.append("{");
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) {
        str.append(", ");
      }
      Entry entry = entries.get(i);
      String f = entry.getFieldKey();
      str.append(f);
      str.append(": ");
      Expr v = entry.getValue();
      visit(v);
    }
    str.append("}");
  }

  void visitStructMap(CreateStruct expr) {
    List<Entry> entries = expr.getEntriesList();
    str.append("{");
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) {
        str.append(", ");
      }
      Entry entry = entries.get(i);
      Expr k = entry.getMapKey();
      visit(k);
      str.append(": ");
      Expr v = entry.getValue();
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
    if (expr.getExprKindCase() != ExprKindCase.CALL_EXPR) {
      return false;
    }
    String other = expr.getCallExpr().getFunction();
    return opPrecedence == Operator.precedence(other);
  }

  /**
   * isLowerPrecedence indicates whether the precedence of the input operator is lower precedence
   * than the (possible) operation represented in the input Expr.
   *
   * <p>If the expr is not a Call, the result is false.
   */
  boolean isLowerPrecedence(String op, Expr expr) {
    if (expr.getExprKindCase() != ExprKindCase.CALL_EXPR) {
      return false;
    }
    String other = expr.getCallExpr().getFunction();
    return Operator.precedence(op) < Operator.precedence(other);
  }

  /**
   * Indicates whether the expr is a complex operator, i.e., a call expression with 2 or more
   * arguments.
   */
  boolean isComplexOperator(Expr expr) {
    return expr.getExprKindCase() == ExprKindCase.CALL_EXPR
        && expr.getCallExpr().getArgsCount() >= 2;
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
    boolean isBinaryOp =
        Operator.findReverseBinaryOperator(expr.getCallExpr().getFunction()) != null;
    return isBinaryOp || isSamePrecedence(Operator.Conditional.precedence, expr);
  }
}
