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

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.CreateStruct.Entry;
import com.google.api.expr.v1alpha1.Expr.Select;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.protobuf.NullValue;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.projectnessie.cel.common.ErrorWithLocation;
import org.projectnessie.cel.common.Errors;
import org.projectnessie.cel.common.Location;
import org.projectnessie.cel.common.Source;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.parser.Helper.Balancer;
import org.projectnessie.cel.parser.gen.CELLexer;
import org.projectnessie.cel.parser.gen.CELParser;
import org.projectnessie.cel.parser.gen.CELParser.BoolFalseContext;
import org.projectnessie.cel.parser.gen.CELParser.BoolTrueContext;
import org.projectnessie.cel.parser.gen.CELParser.BytesContext;
import org.projectnessie.cel.parser.gen.CELParser.CalcContext;
import org.projectnessie.cel.parser.gen.CELParser.ConditionalAndContext;
import org.projectnessie.cel.parser.gen.CELParser.ConditionalOrContext;
import org.projectnessie.cel.parser.gen.CELParser.ConstantLiteralContext;
import org.projectnessie.cel.parser.gen.CELParser.CreateListContext;
import org.projectnessie.cel.parser.gen.CELParser.CreateMessageContext;
import org.projectnessie.cel.parser.gen.CELParser.CreateStructContext;
import org.projectnessie.cel.parser.gen.CELParser.DoubleContext;
import org.projectnessie.cel.parser.gen.CELParser.ExprContext;
import org.projectnessie.cel.parser.gen.CELParser.ExprListContext;
import org.projectnessie.cel.parser.gen.CELParser.FieldInitializerListContext;
import org.projectnessie.cel.parser.gen.CELParser.IdentOrGlobalCallContext;
import org.projectnessie.cel.parser.gen.CELParser.IndexContext;
import org.projectnessie.cel.parser.gen.CELParser.IntContext;
import org.projectnessie.cel.parser.gen.CELParser.LogicalNotContext;
import org.projectnessie.cel.parser.gen.CELParser.MapInitializerListContext;
import org.projectnessie.cel.parser.gen.CELParser.MemberExprContext;
import org.projectnessie.cel.parser.gen.CELParser.NegateContext;
import org.projectnessie.cel.parser.gen.CELParser.NestedContext;
import org.projectnessie.cel.parser.gen.CELParser.NullContext;
import org.projectnessie.cel.parser.gen.CELParser.PrimaryExprContext;
import org.projectnessie.cel.parser.gen.CELParser.RelationContext;
import org.projectnessie.cel.parser.gen.CELParser.SelectOrCallContext;
import org.projectnessie.cel.parser.gen.CELParser.StartContext;
import org.projectnessie.cel.parser.gen.CELParser.StringContext;
import org.projectnessie.cel.parser.gen.CELParser.UintContext;
import org.projectnessie.cel.parser.gen.CELParser.UnaryContext;

public class Parser {

  private static final Set<String> reservedIds =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
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
                  "while")));

  private final Options options;

  public static ParseResult parseAllMacros(Source source) {
    return parse(Options.builder().macros(AllMacros).build(), source);
  }

  public static ParseResult parse(Options options, Source source) {
    return new Parser(options).parse(source);
  }

  Parser(Options options) {
    this.options = options;
  }

  ParseResult parse(Source source) {
    StringCharStream charStream = new StringCharStream(source.content(), source.description());
    CELLexer lexer = new CELLexer(charStream);
    CELParser parser = new CELParser(new CommonTokenStream(lexer, 0));

    RecursionListener parserListener = new RecursionListener(options.getMaxRecursionDepth());

    parser.addParseListener(parserListener);

    parser.setErrorHandler(new RecoveryLimitErrorStrategy(options.getErrorRecoveryLimit()));

    Helper helper = new Helper(source);
    Errors errors = new Errors(source);

    InnerParser inner = new InnerParser(helper, errors);

    lexer.addErrorListener(inner);
    parser.addErrorListener(inner);

    Expr expr = null;
    try {
      if (charStream.size() > options.getExpressionSizeCodePointLimit()) {
        errors.reportError(
            Location.NoLocation,
            "expression code point size exceeds limit: size: %d, limit %d",
            charStream.size(),
            options.getExpressionSizeCodePointLimit());
      } else {
        expr = inner.exprVisit(parser.start());
      }
    } catch (RecoveryLimitError | RecursionError e) {
      errors.reportError(Location.NoLocation, "%s", e.getMessage());
    }

    if (errors.hasErrors()) {
      expr = null;
    }

    return new ParseResult(expr, errors, helper.getSourceInfo());
  }

  public static class ParseResult {
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

  static class RecursionListener implements ParseTreeListener {
    private final int maxDepth;
    private int depth;

    RecursionListener(int maxDepth) {
      this.maxDepth = maxDepth;
    }

    @Override
    public void visitTerminal(TerminalNode node) {}

    @Override
    public void visitErrorNode(ErrorNode node) {}

    @Override
    public void enterEveryRule(ParserRuleContext ctx) {
      if (ctx != null && ctx.getRuleIndex() == CELParser.RULE_expr) {
        if (this.depth >= this.maxDepth) {
          this.depth++;
          throw new RecursionError(
              String.format("expression recursion limit exceeded: %d", maxDepth));
        }
        this.depth++;
      }
    }

    @Override
    public void exitEveryRule(ParserRuleContext ctx) {
      if (ctx != null && ctx.getRuleIndex() == CELParser.RULE_expr) {
        depth--;
      }
    }
  }

  static class RecursionError extends RuntimeException {
    public RecursionError(String message) {
      super(message);
    }
  }

  static class RecoveryLimitError extends RecognitionException {
    public RecoveryLimitError(
        String message, Recognizer<?, ?> recognizer, IntStream input, ParserRuleContext ctx) {
      super(message, recognizer, input, ctx);
    }
  }

  static class RecoveryLimitErrorStrategy extends DefaultErrorStrategy {
    private final int maxAttempts;
    private int attempts;

    private RecoveryLimitErrorStrategy(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    @Override
    public void recover(org.antlr.v4.runtime.Parser recognizer, RecognitionException e) {
      checkAttempts(recognizer);
      super.recover(recognizer, e);
    }

    @Override
    public Token recoverInline(org.antlr.v4.runtime.Parser recognizer) throws RecognitionException {
      checkAttempts(recognizer);
      return super.recoverInline(recognizer);
    }

    void checkAttempts(org.antlr.v4.runtime.Parser recognizer) throws RecognitionException {
      if (attempts >= maxAttempts) {
        attempts++;
        String msg = String.format("error recovery attempt limit exceeded: %d", maxAttempts);
        recognizer.notifyErrorListeners(null, msg, null);
        throw new RecoveryLimitError(msg, recognizer, null, null);
      }
      attempts++;
    }
  }

  class InnerParser extends AbstractParseTreeVisitor<Object> implements ANTLRErrorListener {

    private final Helper helper;
    private final Errors errors;

    InnerParser(Helper helper, Errors errors) {
      this.helper = helper;
      this.errors = errors;
    }

    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer,
        Object offendingSymbol,
        int line,
        int charPositionInLine,
        String msg,
        RecognitionException e) {
      errors.syntaxError(Location.newLocation(line, charPositionInLine), msg);
    }

    @Override
    public void reportAmbiguity(
        org.antlr.v4.runtime.Parser recognizer,
        DFA dfa,
        int startIndex,
        int stopIndex,
        boolean exact,
        BitSet ambigAlts,
        ATNConfigSet configs) {
      // TODO implement ??
    }

    @Override
    public void reportAttemptingFullContext(
        org.antlr.v4.runtime.Parser recognizer,
        DFA dfa,
        int startIndex,
        int stopIndex,
        BitSet conflictingAlts,
        ATNConfigSet configs) {
      // TODO implement ??
    }

    @Override
    public void reportContextSensitivity(
        org.antlr.v4.runtime.Parser recognizer,
        DFA dfa,
        int startIndex,
        int stopIndex,
        int prediction,
        ATNConfigSet configs) {
      // TODO implement ??
    }

    Expr reportError(Object ctx, String message) {
      return reportError(ctx, "%s", message);
    }

    Expr reportError(Object ctx, String format, Object... args) {
      Location location;
      if (ctx instanceof Location) {
        location = (Location) ctx;
      } else if (ctx instanceof Token || ctx instanceof ParserRuleContext) {
        Expr err = helper.newExpr(ctx);
        location = helper.getLocation(err.getId());
      } else {
        location = Location.NoLocation;
      }
      Expr err = helper.newExpr(ctx);
      // Provide arguments to the report error.
      errors.reportError(location, format, args);
      return err;
    }

    public Expr exprVisit(ParseTree tree) {
      Object r = visit(tree);
      return (Expr) r;
    }

    @Override
    public Object visit(ParseTree tree) {
      if (tree instanceof StartContext) {
        return visitStart((StartContext) tree);
      } else if (tree instanceof ExprContext) {
        return visitExpr((ExprContext) tree);
      } else if (tree instanceof ConditionalAndContext) {
        return visitConditionalAnd((ConditionalAndContext) tree);
      } else if (tree instanceof ConditionalOrContext) {
        return visitConditionalOr((ConditionalOrContext) tree);
      } else if (tree instanceof RelationContext) {
        return visitRelation((RelationContext) tree);
      } else if (tree instanceof CalcContext) {
        return visitCalc((CalcContext) tree);
      } else if (tree instanceof LogicalNotContext) {
        return visitLogicalNot((LogicalNotContext) tree);
      } else if (tree instanceof MemberExprContext) {
        return visitMemberExpr((MemberExprContext) tree);
      } else if (tree instanceof PrimaryExprContext) {
        return visitPrimaryExpr((PrimaryExprContext) tree);
      } else if (tree instanceof SelectOrCallContext) {
        return visitSelectOrCall((SelectOrCallContext) tree);
      } else if (tree instanceof MapInitializerListContext) {
        return visitMapInitializerList((MapInitializerListContext) tree);
      } else if (tree instanceof NegateContext) {
        return visitNegate((NegateContext) tree);
      } else if (tree instanceof IndexContext) {
        return visitIndex((IndexContext) tree);
      } else if (tree instanceof UnaryContext) {
        return visitUnary((UnaryContext) tree);
      } else if (tree instanceof CreateListContext) {
        return visitCreateList((CreateListContext) tree);
      } else if (tree instanceof CreateMessageContext) {
        return visitCreateMessage((CreateMessageContext) tree);
      } else if (tree instanceof CreateStructContext) {
        return visitCreateStruct((CreateStructContext) tree);
      }

      // Report at least one error if the parser reaches an unknown parse element.
      // Typically, this happens if the parser has already encountered a syntax error elsewhere.
      if (!errors.hasErrors()) {
        String txt = "<<nil>>";
        if (tree != null) {
          txt = String.format("<<%s>>", tree.getClass().getSimpleName());
        }
        return reportError(Location.NoLocation, "unknown parse element encountered: %s", txt);
      }
      return helper.newExpr(Location.NoLocation);
    }

    private Object visitStart(StartContext ctx) {
      return visit(ctx.expr());
    }

    private Expr visitExpr(ExprContext ctx) {
      Expr result = exprVisit(ctx.e);
      if (ctx.op == null) {
        return result;
      }
      long opID = helper.id(ctx.op);
      Expr ifTrue = exprVisit(ctx.e1);
      Expr ifFalse = exprVisit(ctx.e2);
      return globalCallOrMacro(opID, Operator.Conditional.id, result, ifTrue, ifFalse);
    }

    private Expr visitConditionalAnd(ConditionalAndContext ctx) {
      Expr result = exprVisit(ctx.e);
      if (ctx.ops == null || ctx.ops.isEmpty()) {
        return result;
      }
      Balancer b = helper.newBalancer(Operator.LogicalAnd.id, result);
      List<RelationContext> rest = ctx.e1;
      for (int i = 0; i < ctx.ops.size(); i++) {
        Token op = ctx.ops.get(i);
        if (i >= rest.size()) {
          return reportError(ctx, "unexpected character, wanted '&&'");
        }
        Expr next = exprVisit(rest.get(i));
        long opID = helper.id(op);
        b.addTerm(opID, next);
      }
      return b.balance();
    }

    private Expr visitConditionalOr(ConditionalOrContext ctx) {
      Expr result = exprVisit(ctx.e);
      if (ctx.ops == null || ctx.ops.isEmpty()) {
        return result;
      }
      Balancer b = helper.newBalancer(Operator.LogicalOr.id, result);
      List<ConditionalAndContext> rest = ctx.e1;
      for (int i = 0; i < ctx.ops.size(); i++) {
        Token op = ctx.ops.get(i);
        if (i >= rest.size()) {
          return reportError(ctx, "unexpected character, wanted '||'");
        }
        Expr next = exprVisit(rest.get(i));
        long opID = helper.id(op);
        b.addTerm(opID, next);
      }
      return b.balance();
    }

    private Expr visitRelation(RelationContext ctx) {
      if (ctx.calc() != null) {
        return exprVisit(ctx.calc());
      }
      String opText = "";
      if (ctx.op != null) {
        opText = ctx.op.getText();
      }
      Operator op = Operator.find(opText);
      if (op != null) {
        Expr lhs = exprVisit(ctx.relation(0));
        long opID = helper.id(ctx.op);
        Expr rhs = exprVisit(ctx.relation(1));
        return globalCallOrMacro(opID, op.id, lhs, rhs);
      }
      return reportError(ctx, "operator not found");
    }

    private Expr visitCalc(CalcContext ctx) {
      if (ctx.unary() != null) {
        return exprVisit(ctx.unary());
      }
      String opText = "";
      if (ctx.op != null) {
        opText = ctx.op.getText();
      }
      Operator op = Operator.find(opText);
      if (op != null) {
        Expr lhs = exprVisit(ctx.calc(0));
        long opID = helper.id(ctx.op);
        Expr rhs = exprVisit(ctx.calc(1));
        return globalCallOrMacro(opID, op.id, lhs, rhs);
      }
      return reportError(ctx, "operator not found");
    }

    private Expr visitLogicalNot(LogicalNotContext ctx) {
      if (ctx.ops.size() % 2 == 0) {
        return exprVisit(ctx.member());
      }
      long opID = helper.id(ctx.ops.get(0));
      Expr target = exprVisit(ctx.member());
      return globalCallOrMacro(opID, Operator.LogicalNot.id, target);
    }

    private Expr visitMemberExpr(MemberExprContext ctx) {
      if (ctx.member() instanceof PrimaryExprContext) {
        return visitPrimaryExpr((PrimaryExprContext) ctx.member());
      } else if (ctx.member() instanceof SelectOrCallContext) {
        return visitSelectOrCall((SelectOrCallContext) ctx.member());
      } else if (ctx.member() instanceof IndexContext) {
        return visitIndex((IndexContext) ctx.member());
      } else if (ctx.member() instanceof CreateMessageContext) {
        return visitCreateMessage((CreateMessageContext) ctx.member());
      }
      return reportError(ctx, "unsupported simple expression");
    }

    private Expr visitPrimaryExpr(PrimaryExprContext ctx) {
      if (ctx.primary() instanceof NestedContext) {
        return visitNested((NestedContext) ctx.primary());
      } else if (ctx.primary() instanceof IdentOrGlobalCallContext) {
        return visitIdentOrGlobalCall((IdentOrGlobalCallContext) ctx.primary());
      } else if (ctx.primary() instanceof CreateListContext) {
        return visitCreateList((CreateListContext) ctx.primary());
      } else if (ctx.primary() instanceof CreateStructContext) {
        return visitCreateStruct((CreateStructContext) ctx.primary());
      } else if (ctx.primary() instanceof ConstantLiteralContext) {
        return visitConstantLiteral((ConstantLiteralContext) ctx.primary());
      }

      return reportError(ctx, "invalid primary expression");
    }

    private Expr visitConstantLiteral(ConstantLiteralContext ctx) {
      if (ctx.literal() instanceof IntContext) {
        return visitInt((IntContext) ctx.literal());
      } else if (ctx.literal() instanceof UintContext) {
        return visitUint((UintContext) ctx.literal());
      } else if (ctx.literal() instanceof DoubleContext) {
        return visitDouble((DoubleContext) ctx.literal());
      } else if (ctx.literal() instanceof StringContext) {
        return visitString((StringContext) ctx.literal());
      } else if (ctx.literal() instanceof BytesContext) {
        return visitBytes((BytesContext) ctx.literal());
      } else if (ctx.literal() instanceof BoolFalseContext) {
        return visitBoolFalse((BoolFalseContext) ctx.literal());
      } else if (ctx.literal() instanceof BoolTrueContext) {
        return visitBoolTrue((BoolTrueContext) ctx.literal());
      } else if (ctx.literal() instanceof NullContext) {
        return visitNull((NullContext) ctx.literal());
      }
      return reportError(ctx, "invalid literal");
    }

    private Expr visitInt(IntContext ctx) {
      String text = ctx.tok.getText();
      int base = 10;
      if (text.startsWith("0x")) {
        base = 16;
        text = text.substring(2);
      }
      if (ctx.sign != null) {
        text = ctx.sign.getText() + text;
      }
      try {
        long i = Long.parseLong(text, base);
        return helper.newLiteralInt(ctx, i);
      } catch (Exception e) {
        return reportError(ctx, "invalid int literal");
      }
    }

    private Expr visitUint(UintContext ctx) {
      String text = ctx.tok.getText();
      // trim the 'u' designator included in the uint literal.
      text = text.substring(0, text.length() - 1);
      int base = 10;
      if (text.startsWith("0x")) {
        base = 16;
        text = text.substring(2);
      }
      try {
        long i = Long.parseUnsignedLong(text, base);
        return helper.newLiteralUint(ctx, i);
      } catch (Exception e) {
        return reportError(ctx, "invalid int literal");
      }
    }

    private Expr visitDouble(DoubleContext ctx) {
      String txt = ctx.tok.getText();
      if (ctx.sign != null) {
        txt = ctx.sign.getText() + txt;
      }
      try {
        double f = Double.parseDouble(txt);
        return helper.newLiteralDouble(ctx, f);
      } catch (Exception e) {
        return reportError(ctx, "invalid double literal");
      }
    }

    private Expr visitString(StringContext ctx) {
      String s = unquote(ctx, ctx.getText(), false);
      return helper.newLiteralString(ctx, s);
    }

    private Expr visitBytes(BytesContext ctx) {
      byte[] b =
          unquote(ctx, ctx.tok.getText().substring(1), true).getBytes(StandardCharsets.UTF_8);
      return helper.newLiteralBytes(ctx, b);
    }

    private Expr visitBoolFalse(BoolFalseContext ctx) {
      return helper.newLiteralBool(ctx, false);
    }

    private Expr visitBoolTrue(BoolTrueContext ctx) {
      return helper.newLiteralBool(ctx, true);
    }

    private Expr visitNull(NullContext ctx) {
      return helper.newLiteral(ctx, Constant.newBuilder().setNullValue(NullValue.NULL_VALUE));
    }

    private List<Expr> visitList(ExprListContext ctx) {
      if (ctx == null) {
        return Collections.emptyList();
      }
      return visitSlice(ctx.e);
    }

    private List<Expr> visitSlice(List<ExprContext> expressions) {
      if (expressions == null) {
        return Collections.emptyList();
      }
      List<Expr> result = new ArrayList<>(expressions.size());
      for (ExprContext e : expressions) {
        Expr ex = exprVisit(e);
        result.add(ex);
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
      // TODO: Add a method to Source to get location from character offset.
      Location location = helper.getLocation(e.getId());
      reportError(location, "expected a qualified name");
      return null;
    }

    // Visit a parse tree of field initializers.
    List<Entry> visitIFieldInitializerList(FieldInitializerListContext ctx) {
      if (ctx == null || ctx.fields == null) {
        // This is the result of a syntax error handled elswhere, return empty.
        return Collections.emptyList();
      }

      List<Entry> result = new ArrayList<>(ctx.fields.size());
      List<Token> cols = ctx.cols;
      List<ExprContext> vals = ctx.values;
      for (int i = 0; i < ctx.fields.size(); i++) {
        Token f = ctx.fields.get(i);
        if (i >= cols.size() || i >= vals.size()) {
          // This is the result of a syntax error detected elsewhere.
          return Collections.emptyList();
        }
        long initID = helper.id(cols.get(i));
        Expr value = exprVisit(vals.get(i));
        Entry field = helper.newObjectField(initID, f.getText(), value);
        result.add(field);
      }
      return result;
    }

    private Expr visitIdentOrGlobalCall(IdentOrGlobalCallContext ctx) {
      String identName = "";
      if (ctx.leadingDot != null) {
        identName = ".";
      }
      // Handle the error case where no valid identifier is specified.
      if (ctx.id == null) {
        return helper.newExpr(ctx);
      }
      // Handle reserved identifiers.
      String id = ctx.id.getText();
      if (reservedIds.contains(id)) {
        return reportError(ctx, "reserved identifier: %s", id);
      }
      identName += id;
      if (ctx.op != null) {
        long opID = helper.id(ctx.op);
        return globalCallOrMacro(opID, identName, visitList(ctx.args));
      }
      return helper.newIdent(ctx.id, identName);
    }

    private Expr visitNested(NestedContext ctx) {
      return exprVisit(ctx.e);
    }

    private Expr visitSelectOrCall(SelectOrCallContext ctx) {
      Expr operand = exprVisit(ctx.member());
      // Handle the error case where no valid identifier is specified.
      if (ctx.id == null) {
        return helper.newExpr(ctx);
      }
      String id = ctx.id.getText();
      if (ctx.open != null) {
        long opID = helper.id(ctx.open);
        return receiverCallOrMacro(opID, id, operand, visitList(ctx.args));
      }
      return helper.newSelect(ctx.op, operand, id);
    }

    private List<Entry> visitMapInitializerList(MapInitializerListContext ctx) {
      if (ctx == null || ctx.keys.isEmpty()) {
        // This is the result of a syntax error handled elswhere, return empty.
        return Collections.emptyList();
      }

      List<Entry> result = new ArrayList<>(ctx.cols.size());
      List<ExprContext> keys = ctx.keys;
      List<ExprContext> vals = ctx.values;
      for (int i = 0; i < ctx.cols.size(); i++) {
        Token col = ctx.cols.get(i);
        long colID = helper.id(col);
        if (i >= keys.size() || i >= vals.size()) {
          // This is the result of a syntax error detected elsewhere.
          return Collections.emptyList();
        }
        Expr key = exprVisit(keys.get(i));
        Expr value = exprVisit(vals.get(i));
        Entry entry = helper.newMapEntry(colID, key, value);
        result.add(entry);
      }
      return result;
    }

    private Expr visitNegate(NegateContext ctx) {
      if (ctx.ops.size() % 2 == 0) {
        return exprVisit(ctx.member());
      }
      long opID = helper.id(ctx.ops.get(0));
      Expr target = exprVisit(ctx.member());
      return globalCallOrMacro(opID, Operator.Negate.id, target);
    }

    private Expr visitIndex(IndexContext ctx) {
      Expr target = exprVisit(ctx.member());
      long opID = helper.id(ctx.op);
      Expr index = exprVisit(ctx.index);
      return globalCallOrMacro(opID, Operator.Index.id, target, index);
    }

    private Expr visitUnary(UnaryContext ctx) {
      return helper.newLiteralString(ctx, "<<error>>");
    }

    private Expr visitCreateList(CreateListContext ctx) {
      long listID = helper.id(ctx.op);
      return helper.newList(listID, visitList(ctx.elems));
    }

    private Expr visitCreateMessage(CreateMessageContext ctx) {
      Expr target = exprVisit(ctx.member());
      long objID = helper.id(ctx.op);
      String messageName = extractQualifiedName(target);
      if (messageName != null) {
        List<Entry> entries = visitIFieldInitializerList(ctx.entries);
        return helper.newObject(objID, messageName, entries);
      }
      return helper.newExpr(objID);
    }

    private Expr visitCreateStruct(CreateStructContext ctx) {
      long structID = helper.id(ctx.op);
      if (ctx.entries != null) {
        return helper.newMap(structID, visitMapInitializerList(ctx.entries));
      } else {
        return helper.newMap(structID, Collections.emptyList());
      }
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
        return macro.expander().func(eh, target, args);
      } catch (ErrorWithLocation err) {
        Location loc = err.getLocation();
        if (loc == null) {
          loc = helper.getLocation(exprID);
        }
        return reportError(loc, err.getMessage());
      } catch (Exception e) {
        e.printStackTrace(); // TODO do what exactly with the exception here
        return reportError(helper.getLocation(exprID), e.getMessage());
      }
    }

    String unquote(Object ctx, String value, boolean isBytes) {
      try {
        return Unescape.unescape(value, isBytes);
      } catch (Exception e) {
        // TODO this can probably be done better
        reportError(ctx, e.toString());
        return value;
      }
    }
  }
}
