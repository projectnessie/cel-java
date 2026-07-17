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

import static org.projectnessie.cel.parser.Macro.AllMacros;
import static org.projectnessie.cel.parser.Token.TokenType.BYTES;
import static org.projectnessie.cel.parser.Token.TokenType.COLON;
import static org.projectnessie.cel.parser.Token.TokenType.COMMA;
import static org.projectnessie.cel.parser.Token.TokenType.DOT;
import static org.projectnessie.cel.parser.Token.TokenType.EOF;
import static org.projectnessie.cel.parser.Token.TokenType.EXCLAM;
import static org.projectnessie.cel.parser.Token.TokenType.FALSE;
import static org.projectnessie.cel.parser.Token.TokenType.IDENTIFIER;
import static org.projectnessie.cel.parser.Token.TokenType.LBRACE;
import static org.projectnessie.cel.parser.Token.TokenType.LBRACKET;
import static org.projectnessie.cel.parser.Token.TokenType.LPAREN;
import static org.projectnessie.cel.parser.Token.TokenType.MINUS;
import static org.projectnessie.cel.parser.Token.TokenType.NULL;
import static org.projectnessie.cel.parser.Token.TokenType.NUM_FLOAT;
import static org.projectnessie.cel.parser.Token.TokenType.NUM_INT;
import static org.projectnessie.cel.parser.Token.TokenType.NUM_UINT;
import static org.projectnessie.cel.parser.Token.TokenType.QUESTIONMARK;
import static org.projectnessie.cel.parser.Token.TokenType.RBRACE;
import static org.projectnessie.cel.parser.Token.TokenType.RBRACKET;
import static org.projectnessie.cel.parser.Token.TokenType.RPAREN;
import static org.projectnessie.cel.parser.Token.TokenType.STRING;
import static org.projectnessie.cel.parser.Token.TokenType.TRUE;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.CreateStruct.Entry;
import com.google.api.expr.v1alpha1.Expr.Select;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.projectnessie.cel.common.ErrorWithLocation;
import org.projectnessie.cel.common.Errors;
import org.projectnessie.cel.common.Location;
import org.projectnessie.cel.common.Source;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.parser.Helper.Balancer;
import org.projectnessie.cel.parser.ast.ConstantLiteral;
import org.projectnessie.cel.parser.ast.ExprList;
import org.projectnessie.cel.parser.ast.Field;
import org.projectnessie.cel.parser.ast.FieldInitializerList;
import org.projectnessie.cel.parser.ast.ListInitializerList;
import org.projectnessie.cel.parser.ast.MapInitializerList;
import org.projectnessie.cel.parser.ast.Start;

public final class Parser {

  private static final Set<String> reservedIds =
      Set.of(
          "as",
          "break",
          "const",
          "continue",
          "else",
          "false",
          "for",
          "function",
          "if",
          "import",
          "in",
          "let",
          "loop",
          "package",
          "namespace",
          "null",
          "return",
          "true",
          "var",
          "void",
          "while");

  private final Options options;

  public static ParseResult parseAllMacros(Source source) {
    return parse(Options.builder().macros(AllMacros).build(), source);
  }

  public static ParseResult parseWithMacros(Source source, List<Macro> macros) {
    return parse(Options.builder().macros(macros).build(), source);
  }

  public static ParseResult parse(Options options, Source source) {
    return new Parser(options).parse(source);
  }

  Parser(Options options) {
    this.options = options;
  }

  ParseResult parse(Source source) {
    Helper helper = new Helper(source);
    Errors errors = new Errors(source);
    Expr expr = null;

    int codePointCount = source.content().codePointCount(0, source.content().length());
    if (codePointCount > options.getExpressionSizeCodePointLimit()) {
      errors.reportError(
          Location.NoLocation,
          "expression code point size exceeds limit: size: %d, limit %d",
          codePointCount,
          options.getExpressionSizeCodePointLimit());
    } else {
      CelGrammarParser parser = new CelGrammarParser(source.description(), source.content());
      try {
        parser.Start();
        expr = new AstBuilder(helper, errors).exprVisit(firstExpressionNode(parser.rootNode()));
      } catch (ParseException e) {
        errors.syntaxError(location(e.getLocation()), e.getMessage());
      } catch (RecursionError e) {
        errors.reportError(e, Location.NoLocation, "%s", e.getMessage());
      }
    }

    if (errors.hasErrors()) {
      expr = null;
    }

    return new ParseResult(expr, errors, helper.getSourceInfo());
  }

  private static Node firstExpressionNode(Node root) {
    if (root instanceof Start) {
      for (Node child : root.children()) {
        if (!isToken(child, EOF)) {
          return child;
        }
      }
    }
    return root;
  }

  private static Location location(Node node) {
    if (node == null) {
      return Location.NoLocation;
    }
    return Location.newLocation(node.getBeginLine(), node.getBeginColumn() - 1);
  }

  public static final class ParseResult {
    private final Expr expr;
    private final Errors errors;
    private final SourceInfo sourceInfo;

    public ParseResult(Expr expr, Errors errors, SourceInfo sourceInfo) {
      this.expr = expr;
      this.errors = errors;
      this.sourceInfo = sourceInfo;
    }

    public Expr getExpr() {
      return expr;
    }

    public Errors getErrors() {
      return errors;
    }

    public SourceInfo getSourceInfo() {
      return sourceInfo;
    }

    public boolean hasErrors() {
      return errors.hasErrors();
    }
  }

  static final class RecursionError extends RuntimeException {
    RecursionError(String message) {
      super(message);
    }
  }

  final class AstBuilder implements CelExprBuilder {
    private final Helper helper;
    private final Errors errors;
    private int depth;

    AstBuilder(Helper helper, Errors errors) {
      this.helper = helper;
      this.errors = errors;
    }

    Expr exprVisit(Node node) {
      if (node == null) {
        return reportError(Location.NoLocation, "unknown parse element encountered: <<nil>>");
      }
      if (depth >= options.getMaxRecursionDepth()) {
        throw new RecursionError(
            String.format(
                "expression recursion limit exceeded: %d", options.getMaxRecursionDepth()));
      }
      depth++;
      try {
        return doExprVisit(node);
      } finally {
        depth--;
      }
    }

    private Expr doExprVisit(Node node) {
      if (node instanceof CelExprNode) {
        return ((CelExprNode) node).toCelExpr(this);
      }
      return reportError(
          node, "unknown parse element encountered: <<%s>>", node.getClass().getSimpleName());
    }

    @Override
    public Expr visitExpr(Node node) {
      List<Node> children = significantChildren(node);
      int question = indexOf(children, QUESTIONMARK);
      if (question < 0) {
        return exprVisit(firstExpressionChild(children, node));
      }
      Expr condition = exprVisit(children.get(0));
      long opID = helper.id(children.get(question));
      Expr ifTrue = exprVisit(children.get(question + 1));
      Expr ifFalse = exprVisit(children.get(question + 3));
      return globalCallOrMacro(opID, Operator.Conditional.id, condition, ifTrue, ifFalse);
    }

    @Override
    public Expr visitBalanced(Node node, Operator operator) {
      List<Node> children = significantChildren(node);
      if (children.size() == 1) {
        return exprVisit(children.get(0));
      }
      Expr result = exprVisit(children.get(0));
      Balancer balancer = helper.newBalancer(operator.id, result);
      for (int i = 1; i < children.size(); i += 2) {
        Node op = children.get(i);
        if (i + 1 >= children.size()) {
          return reportError(node, "unexpected character, wanted '%s'", tokenText(op));
        }
        Expr next = exprVisit(children.get(i + 1));
        balancer.addTerm(helper.id(op), next);
      }
      return balancer.balance();
    }

    @Override
    public Expr visitBinary(Node node) {
      List<Node> children = significantChildren(node);
      if (children.size() == 1) {
        return exprVisit(children.get(0));
      }
      Expr result = exprVisit(children.get(0));
      for (int i = 1; i < children.size(); i += 2) {
        Node opNode = children.get(i);
        if (i + 1 >= children.size()) {
          return reportError(node, "operator not found");
        }
        Operator op = Operator.find(tokenText(opNode));
        if (op == null) {
          return reportError(opNode, "operator not found");
        }
        long opID = helper.id(opNode);
        Expr rhs = exprVisit(children.get(i + 1));
        result = globalCallOrMacro(opID, op.id, result, rhs);
      }
      return result;
    }

    @Override
    public Expr visitUnary(Node node) {
      List<Node> children = significantChildren(node);
      int opCount = 0;
      while (opCount < children.size()
          && (isToken(children.get(opCount), MINUS) || isToken(children.get(opCount), EXCLAM))) {
        opCount++;
      }
      Node operand = children.get(opCount);
      if (opCount == 0) {
        return exprVisit(operand);
      }
      Node op = children.get(0);
      boolean logicalNot = isToken(op, EXCLAM);
      if (opCount % 2 == 0) {
        return exprVisit(operand);
      }
      if (!logicalNot && isIntOrFloatLiteral(operand)) {
        return visitNegativeNumericLiteral(op, operand);
      }
      return globalCallOrMacro(
          helper.id(op),
          logicalNot ? Operator.LogicalNot.id : Operator.Negate.id,
          exprVisit(operand));
    }

    @Override
    public Expr visitPrimary(Node node) {
      List<Node> children = significantChildren(node);
      if (children.isEmpty()) {
        return reportError(node, "invalid primary expression");
      }
      Node first = children.get(0);
      if (isToken(first, DOT) || isToken(first, IDENTIFIER)) {
        return visitIdentOrGlobalCall(children, node);
      } else if (isToken(first, LPAREN)) {
        return exprVisit(children.get(1));
      } else if (isToken(first, LBRACKET)) {
        long listID = helper.id(first);
        ListInitializerList list = firstChildOfType(children, ListInitializerList.class);
        ListElements elements =
            list != null ? listElements(list) : listElements(children.subList(1, children.size()));
        return helper.newList(listID, elements.expressions(), elements.optionalIndices());
      } else if (isToken(first, LBRACE)) {
        return helper.newMap(
            helper.id(first), mapEntries(firstChildOfType(children, MapInitializerList.class)));
      } else if (first instanceof ConstantLiteral || isLiteralToken(first)) {
        return exprVisit(first);
      }
      return reportError(node, "invalid primary expression");
    }

    private Expr visitIdentOrGlobalCall(List<Node> children, Node ctx) {
      int i = 0;
      String prefix = "";
      if (isToken(children.get(i), DOT)) {
        prefix = ".";
        i++;
      }
      if (i >= children.size() || !isToken(children.get(i), IDENTIFIER)) {
        return helper.newExpr(ctx);
      }
      Token ident = (Token) children.get(i++);
      String name = prefix + tokenText(ident);
      if (reservedIds.contains(tokenText(ident))) {
        return reportError(ident, "reserved identifier: %s", tokenText(ident));
      }
      if (i < children.size() && isToken(children.get(i), LPAREN)) {
        Node open = children.get(i);
        return globalCallOrMacro(
            helper.id(open), name, expressionsBetween(children, i + 1, RPAREN));
      }
      return helper.newIdent(ident, name);
    }

    @Override
    public Expr visitIdentifier(Token token) {
      return identOrReserved(token, tokenText(token));
    }

    private Expr identOrReserved(Token token, String name) {
      if (reservedIds.contains(name)) {
        return reportError(token, "reserved identifier: %s", name);
      }
      return helper.newIdent(token, name);
    }

    @Override
    public Expr visitMember(Node node) {
      List<Node> children = significantChildren(node);
      Expr operand = exprVisit(children.get(0));
      int i = 1;
      while (i < children.size()) {
        Node op = children.get(i++);
        if (isToken(op, DOT)) {
          if (i >= children.size()) {
            return helper.newExpr(node);
          }
          boolean optional = false;
          Node optionalNode = null;
          if (isToken(children.get(i), QUESTIONMARK)) {
            optional = true;
            optionalNode = children.get(i++);
          }
          String id = fieldName(children.get(i++));
          if (i < children.size() && isToken(children.get(i), LPAREN)) {
            if (optional) {
              return reportError(optionalNode, "optional select does not support function calls");
            }
            Node open = children.get(i++);
            long openID = helper.id(open);
            List<Expr> args = expressionsBetween(children, i, RPAREN);
            while (i < children.size() && !isToken(children.get(i), RPAREN)) {
              i++;
            }
            if (i < children.size()) {
              i++;
            }
            operand = receiverCallOrMacro(openID, id, operand, args);
          } else if (optional) {
            operand =
                globalCallOrMacro(
                    helper.id(optionalNode),
                    Operator.OptionalSelect.id,
                    operand,
                    helper.newLiteralString(optionalNode, id));
          } else {
            operand = helper.newSelect(op, operand, id);
          }
        } else if (isToken(op, LBRACKET)) {
          long opID = helper.id(op);
          boolean optional = false;
          if (isToken(children.get(i), QUESTIONMARK)) {
            optional = true;
            opID = helper.id(children.get(i++));
          }
          Expr index = exprVisit(children.get(i++));
          if (i < children.size() && isToken(children.get(i), RBRACKET)) {
            i++;
          }
          operand =
              globalCallOrMacro(
                  opID, optional ? Operator.OptionalIndex.id : Operator.Index.id, operand, index);
        } else if (isToken(op, LBRACE)) {
          String messageName = extractQualifiedName(operand);
          FieldInitializerList fields =
              firstChildOfType(children.subList(i, children.size()), FieldInitializerList.class);
          if (messageName != null) {
            operand = helper.newObject(helper.id(op), messageName, objectFields(fields));
          } else {
            operand = helper.newExpr(helper.id(op));
          }
          while (i < children.size() && !isToken(children.get(i), RBRACE)) {
            i++;
          }
          if (i < children.size()) {
            i++;
          }
        } else {
          return reportError(op, "unsupported member expression");
        }
      }
      return operand;
    }

    @Override
    public Expr visitLiteral(Node node) {
      Node token = node;
      if (node instanceof ConstantLiteral) {
        List<Node> children = significantChildren(node);
        token = children.get(children.size() - 1);
      }
      if (isToken(token, NUM_INT)) {
        return intLiteral(token);
      } else if (isToken(token, NUM_UINT)) {
        return uintLiteral(token);
      } else if (isToken(token, NUM_FLOAT)) {
        return doubleLiteral(token);
      } else if (isToken(token, STRING)) {
        return helper.newLiteralString(token, unquoteString(token, tokenText(token)));
      } else if (isToken(token, BYTES)) {
        return helper.newLiteralBytes(token, unquoteBytes(token, tokenText(token).substring(1)));
      } else if (isToken(token, FALSE)) {
        return helper.newLiteralBool(token, false);
      } else if (isToken(token, TRUE)) {
        return helper.newLiteralBool(token, true);
      } else if (isToken(token, NULL)) {
        return helper.newLiteral(token, Constant.newBuilder().setNullValue(NullValue.NULL_VALUE));
      }
      return reportError(node, "invalid literal");
    }

    private static boolean isIntOrFloatLiteral(Node operand) {
      return isToken(operand, NUM_INT)
          || isToken(operand, NUM_FLOAT)
          || (operand instanceof ConstantLiteral
              && significantChildren(operand).stream()
                  .anyMatch(child -> isToken(child, NUM_INT) || isToken(child, NUM_FLOAT)));
    }

    private Expr visitNegativeNumericLiteral(Node op, Node operand) {
      Node token = operand;
      if (operand instanceof ConstantLiteral) {
        List<Node> children = significantChildren(operand);
        token = children.get(children.size() - 1);
      }
      if (isToken(token, NUM_INT)) {
        return intLiteral(op, "-" + tokenText(token));
      } else if (isToken(token, NUM_FLOAT)) {
        return doubleLiteral(op, "-" + tokenText(token));
      }
      return globalCallOrMacro(helper.id(op), Operator.Negate.id, exprVisit(operand));
    }

    private Expr intLiteral(Node token) {
      return intLiteral(token, tokenText(token));
    }

    private Expr intLiteral(Node token, String text) {
      int base = 10;
      if (text.startsWith("-0x")) {
        base = 16;
        text = "-" + text.substring(3);
      } else if (text.startsWith("0x")) {
        base = 16;
        text = text.substring(2);
      }
      try {
        return helper.newLiteralInt(token, Long.parseLong(text, base));
      } catch (Exception e) {
        return reportError(token, "invalid int literal");
      }
    }

    private Expr uintLiteral(Node token) {
      String text = tokenText(token);
      text = text.substring(0, text.length() - 1);
      int base = 10;
      if (text.startsWith("0x")) {
        base = 16;
        text = text.substring(2);
      }
      try {
        return helper.newLiteralUint(token, Long.parseUnsignedLong(text, base));
      } catch (Exception e) {
        return reportError(token, "invalid int literal");
      }
    }

    private Expr doubleLiteral(Node token) {
      return doubleLiteral(token, tokenText(token));
    }

    private Expr doubleLiteral(Node token, String text) {
      try {
        return helper.newLiteralDouble(token, Double.parseDouble(text));
      } catch (Exception e) {
        return reportError(token, "invalid double literal");
      }
    }

    private List<Expr> expressionsIn(ExprList list) {
      if (list == null) {
        return Collections.emptyList();
      }
      List<Expr> result = new ArrayList<>();
      for (Node child : significantChildren(list)) {
        if (!isToken(child, COMMA)) {
          result.add(exprVisit(child));
        }
      }
      return result;
    }

    private ListElements listElements(ListInitializerList list) {
      if (list == null) {
        return new ListElements(Collections.emptyList(), Collections.emptyList());
      }
      return listElements(significantChildren(list));
    }

    private ListElements listElements(List<Node> children) {
      List<Expr> expressions = new ArrayList<>();
      List<Integer> optionalIndices = new ArrayList<>();
      boolean optional = false;
      for (Node child : children) {
        if (isToken(child, COMMA) || isToken(child, RBRACKET)) {
          continue;
        }
        if (isToken(child, QUESTIONMARK)) {
          optional = true;
          continue;
        }
        if (optional) {
          optionalIndices.add(expressions.size());
          optional = false;
        }
        expressions.add(exprVisit(child));
      }
      return new ListElements(expressions, optionalIndices);
    }

    private List<Expr> expressionsBetween(List<Node> children, int start, Token.TokenType end) {
      List<Expr> result = new ArrayList<>();
      for (int i = start; i < children.size() && !isToken(children.get(i), end); i++) {
        Node child = children.get(i);
        if (isToken(child, COMMA)) {
          continue;
        }
        if (child instanceof ExprList) {
          result.addAll(expressionsIn((ExprList) child));
        } else {
          result.add(exprVisit(child));
        }
      }
      return result;
    }

    private List<Entry> objectFields(FieldInitializerList fields) {
      if (fields == null) {
        return Collections.emptyList();
      }
      List<Node> children = significantChildren(fields);
      List<Entry> result = new ArrayList<>();
      for (int i = 0; i < children.size(); ) {
        boolean optional = false;
        if (isToken(children.get(i), QUESTIONMARK)) {
          optional = true;
          i++;
        }
        Node field = children.get(i++);
        if (i >= children.size() || !isToken(children.get(i), COLON)) {
          break;
        }
        Node colon = children.get(i++);
        if (i >= children.size()) {
          break;
        }
        long colonID = helper.id(colon);
        Expr value = exprVisit(children.get(i++));
        result.add(helper.newObjectField(colonID, fieldName(field), value, optional));
        if (i < children.size() && isToken(children.get(i), COMMA)) {
          i++;
        }
      }
      return result;
    }

    private List<Entry> mapEntries(MapInitializerList entries) {
      if (entries == null) {
        return Collections.emptyList();
      }
      List<Node> children = significantChildren(entries);
      List<Entry> result = new ArrayList<>();
      for (int i = 0; i < children.size(); ) {
        boolean optional = false;
        if (isToken(children.get(i), QUESTIONMARK)) {
          optional = true;
          i++;
        }
        Node keyNode = children.get(i++);
        if (i >= children.size() || !isToken(children.get(i), COLON)) {
          break;
        }
        Node colon = children.get(i++);
        long colonID = helper.id(colon);
        Expr key = exprVisit(keyNode);
        if (i >= children.size()) {
          break;
        }
        Expr value = exprVisit(children.get(i++));
        result.add(helper.newMapEntry(colonID, key, value, optional));
        if (i < children.size() && isToken(children.get(i), COMMA)) {
          i++;
        }
      }
      return result;
    }

    String extractQualifiedName(Expr e) {
      if (e == null) {
        return null;
      }
      switch (e.getExprKindCase()) {
        case IDENT_EXPR:
          return e.getIdentExpr().getName();
        case SELECT_EXPR:
          Select s = e.getSelectExpr();
          String prefix = extractQualifiedName(s.getOperand());
          return prefix + "." + s.getField();
      }
      Location location = helper.getLocation(e.getId());
      reportError(location, "expected a qualified name");
      return null;
    }

    Expr globalCallOrMacro(long exprID, String function, Expr... args) {
      return globalCallOrMacro(exprID, function, Arrays.asList(args));
    }

    Expr globalCallOrMacro(long exprID, String function, List<Expr> args) {
      Expr expr = expandMacro(exprID, function, null, args);
      if (expr != null) {
        return expr;
      }
      return helper.newGlobalCall(exprID, function, args);
    }

    Expr receiverCallOrMacro(long exprID, String function, Expr target, List<Expr> args) {
      Expr expr = expandMacro(exprID, function, target, args);
      if (expr != null) {
        return expr;
      }
      return helper.newReceiverCall(exprID, function, target, args);
    }

    Expr expandMacro(long exprID, String function, Expr target, List<Expr> args) {
      Macro macro = options.getMacro(Macro.makeMacroKey(function, args.size(), target != null));
      if (macro == null) {
        macro = options.getMacro(Macro.makeVarArgMacroKey(function, target != null));
        if (macro == null) {
          return null;
        }
      }

      ExprHelperImpl eh = new ExprHelperImpl(helper, exprID);
      try {
        return macro.expander().expand(eh, target, args);
      } catch (ErrorWithLocation err) {
        Location loc = err.getLocation();
        if (loc == null) {
          loc = helper.getLocation(exprID);
        }
        return reportError(loc, err.getMessage());
      } catch (Exception e) {
        return reportError(helper.getLocation(exprID), e.getMessage());
      }
    }

    ByteString unquoteBytes(Object ctx, String value) {
      try {
        ByteBuffer buf = Unescape.unescape(value, true);
        return ByteString.copyFrom(buf);
      } catch (Exception e) {
        reportError(ctx, e.toString());
        return ByteString.copyFromUtf8(value);
      }
    }

    String unquoteString(Object ctx, String value) {
      try {
        ByteBuffer buf = Unescape.unescape(value, false);
        return Unescape.toUtf8(buf);
      } catch (Exception e) {
        reportError(ctx, e.toString());
        return value;
      }
    }

    Expr reportError(Object ctx, String message) {
      return reportError(ctx, "%s", message);
    }

    Expr reportError(Object ctx, String format, Object... args) {
      Location loc = Location.NoLocation;
      if (ctx instanceof Location) {
        loc = (Location) ctx;
      } else if (ctx instanceof Node) {
        loc = location((Node) ctx);
      }
      Expr err = helper.newExpr(ctx);
      errors.reportError(loc, format, args);
      return err;
    }

    private static String fieldName(Node node) {
      if (node instanceof Field) {
        return fieldName(significantChildren(node).get(0));
      }
      String text = tokenText(node);
      if (text.length() >= 2 && text.charAt(0) == '`' && text.charAt(text.length() - 1) == '`') {
        return text.substring(1, text.length() - 1);
      }
      return text;
    }

    private static Node firstExpressionChild(List<Node> children, Node ctx) {
      for (Node child : children) {
        if (!isStructuralToken(child)) {
          return child;
        }
      }
      return ctx;
    }
  }

  private static List<Node> significantChildren(Node node) {
    if (node == null) {
      return Collections.emptyList();
    }
    List<Node> children = new ArrayList<>();
    for (Node child : node.children()) {
      if (!isToken(child, EOF)) {
        children.add(child);
      }
    }
    return children;
  }

  private static boolean isLiteralToken(Node node) {
    return isToken(node, NUM_INT)
        || isToken(node, NUM_UINT)
        || isToken(node, NUM_FLOAT)
        || isToken(node, STRING)
        || isToken(node, BYTES)
        || isToken(node, TRUE)
        || isToken(node, FALSE)
        || isToken(node, NULL);
  }

  private static boolean isStructuralToken(Node node) {
    return isToken(node, COMMA)
        || isToken(node, COLON)
        || isToken(node, LPAREN)
        || isToken(node, RPAREN)
        || isToken(node, LBRACKET)
        || isToken(node, RBRACKET)
        || isToken(node, LBRACE)
        || isToken(node, RBRACE);
  }

  private static boolean isToken(Node node, Token.TokenType type) {
    return node instanceof Token && node.getType() == type;
  }

  private static String tokenText(Node node) {
    return node == null ? "" : node.getSource();
  }

  private static int indexOf(List<Node> children, Token.TokenType type) {
    for (int i = 0; i < children.size(); i++) {
      if (isToken(children.get(i), type)) {
        return i;
      }
    }
    return -1;
  }

  @SuppressWarnings("unchecked")
  private static <T extends Node> T firstChildOfType(List<Node> children, Class<T> type) {
    for (Node child : children) {
      if (type.isInstance(child)) {
        return (T) child;
      }
    }
    return null;
  }

  private record ListElements(List<Expr> expressions, List<Integer> optionalIndices) {}
}
