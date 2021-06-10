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
package org.projectnessie.cel;

import static java.util.Arrays.asList;
import static org.projectnessie.cel.Util.mapOf;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Call;
import com.google.api.expr.v1alpha1.Expr.Comprehension;
import com.google.api.expr.v1alpha1.Expr.CreateList;
import com.google.api.expr.v1alpha1.Expr.CreateStruct;
import com.google.api.expr.v1alpha1.Expr.Ident;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.protobuf.ByteString;
import java.nio.charset.StandardCharsets;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.operators.Operator;

/** TestExpr packages an Expr with SourceInfo, for testing. */
public class TestExpr {

  final Expr expr;
  final SourceInfo sourceInfo;

  public TestExpr(Expr expr, SourceInfo sourceInfo) {
    this.expr = expr;
    this.sourceInfo = sourceInfo;
  }

  /** Info returns a copy of the SourceInfo with the given location. */
  public SourceInfo info(String location) {
    return sourceInfo.toBuilder().setLocation(location).build();
  }

  /** Empty generates a program with no instructions. */
  public static final TestExpr Empty =
      new TestExpr(Expr.getDefaultInstance(), SourceInfo.newBuilder().build());

  /** Exists generates "[1, 1u, 1.0].exists(x, type(x) == uint)". */
  public static final TestExpr Exists =
      new TestExpr(
          ExprComprehension(
              1,
              "x",
              ExprList(
                  8,
                  ExprLiteral(2, 0L),
                  ExprLiteral(3, 1L),
                  ExprLiteral(4, 2L),
                  ExprLiteral(5, 3L),
                  ExprLiteral(6, 4L),
                  ExprLiteral(7, ULong.valueOf(5))),
              "__result__",
              ExprLiteral(9, false),
              ExprCall(
                  12,
                  Operator.NotStrictlyFalse.id,
                  ExprCall(10, Operator.LogicalNot.id, ExprIdent(11, "__result__"))),
              ExprCall(
                  13,
                  Operator.LogicalOr.id,
                  ExprIdent(14, "__result__"),
                  ExprCall(
                      15,
                      Operator.Equals.id,
                      ExprCall(16, "type", ExprIdent(17, "x")),
                      ExprIdent(18, "uint"))),
              ExprIdent(19, "__result__")),
          SourceInfo.newBuilder()
              .putAllPositions(
                  mapOf(
                      0L, 12,
                      1L, 0,
                      2L, 1,
                      3L, 4,
                      4L, 8,
                      5L, 0,
                      6L, 18,
                      7L, 18,
                      8L, 18,
                      9L, 18,
                      10L, 18,
                      11L, 20,
                      12L, 20,
                      13L, 28,
                      14L, 28,
                      15L, 28,
                      16L, 28,
                      17L, 28,
                      18L, 28,
                      19L, 28))
              .build());

  /** ExistsWithInput generates "elems.exists(x, type(x) == uint)". */
  public static final TestExpr ExistsWithInput =
      new TestExpr(
          ExprComprehension(
              1,
              "x",
              ExprIdent(2, "elems"),
              "__result__",
              ExprLiteral(3, false),
              ExprCall(4, Operator.LogicalNot.id, ExprIdent(5, "__result__")),
              ExprCall(
                  6,
                  Operator.Equals.id,
                  ExprCall(7, "type", ExprIdent(8, "x")),
                  ExprIdent(9, "uint")),
              ExprIdent(10, "__result__")),
          SourceInfo.newBuilder()
              .putAllPositions(
                  mapOf(
                      0L, 12,
                      1L, 0,
                      2L, 1,
                      3L, 4,
                      4L, 8,
                      5L, 0,
                      6L, 18,
                      7L, 18,
                      8L, 18,
                      9L, 18,
                      10L, 18))
              .build());

  /**
   * DynMap generates a map literal: <code><pre>
   * {"hello": "world".size(),
   *  "dur": duration.Duration{10},
   *  "ts": timestamp.Timestamp{1000},
   *  "null": null,
   *  "bytes": b"bytes-string"}
   *  </pre></code>
   */
  public static final TestExpr DynMap =
      new TestExpr(
          ExprMap(
              17,
              ExprEntry(
                  2, ExprLiteral(1, "hello"), ExprMemberCall(3, "size", ExprLiteral(4, "world"))),
              ExprEntry(12, ExprLiteral(11, "null"), ExprLiteral(13, null)),
              ExprEntry(
                  15,
                  ExprLiteral(14, "bytes"),
                  ExprLiteral(16, "bytes-string".getBytes(StandardCharsets.UTF_8)))),
          SourceInfo.newBuilder().build());

  /** LogicalAnd generates "a && {c: true}.c". */
  public static final TestExpr LogicalAnd =
      new TestExpr(
          ExprCall(
              2,
              Operator.LogicalAnd.id,
              ExprIdent(1, "a"),
              ExprSelect(
                  8, ExprMap(5, ExprEntry(4, ExprLiteral(6, "c"), ExprLiteral(7, true))), "c")),
          SourceInfo.newBuilder().build());

  /** LogicalOr generates "{c: false}.c || a". */
  public static final TestExpr LogicalOr =
      new TestExpr(
          ExprCall(
              2,
              Operator.LogicalOr.id,
              ExprSelect(
                  8, ExprMap(5, ExprEntry(4, ExprLiteral(6, "c"), ExprLiteral(7, false))), "c"),
              ExprIdent(1, "a")),
          SourceInfo.newBuilder().build());

  /** LogicalOrEquals generates "a || b == 'b'". */
  public static final TestExpr LogicalOrEquals =
      new TestExpr(
          ExprCall(
              5,
              Operator.LogicalOr.id,
              ExprIdent(1, "a"),
              ExprCall(4, Operator.Equals.id, ExprIdent(2, "b"), ExprLiteral(3, "b"))),
          SourceInfo.newBuilder().build());

  /**
   * LogicalAndMissingType generates "a && TestProto{c: true}.c" where the type 'TestProto' is
   * undefined.
   */
  public static final TestExpr LogicalAndMissingType =
      new TestExpr(
          ExprCall(
              2,
              Operator.LogicalAnd.id,
              ExprIdent(1, "a"),
              ExprSelect(
                  7, ExprType(5, "TestProto", ExprField(4, "c", ExprLiteral(6, true))), "c")),
          SourceInfo.newBuilder().build());

  /** Conditional generates "a ? b < 1.0 : c == ["hello"]". */
  public static final TestExpr Conditional =
      new TestExpr(
          ExprCall(
              9,
              Operator.Conditional.id,
              ExprIdent(1, "a"),
              ExprCall(3, Operator.Less.id, ExprIdent(2, "b"), ExprLiteral(4, 1.0)),
              ExprCall(
                  6, Operator.Equals.id, ExprIdent(5, "c"), ExprList(8, ExprLiteral(7, "hello")))),
          SourceInfo.newBuilder().build());

  /** Select generates "a.b.c". */
  public static final TestExpr Select =
      new TestExpr(
          ExprSelect(3, ExprSelect(2, ExprIdent(1, "a"), "b"), "c"),
          SourceInfo.newBuilder().build());

  /** Equality generates "a == 42". */
  public static final TestExpr Equality =
      new TestExpr(
          ExprCall(2, Operator.Equals.id, ExprIdent(1, "a"), ExprLiteral(3, 42L)),
          SourceInfo.newBuilder().build());

  /** TypeEquality generates "type(a) == uint". */
  public static final TestExpr TypeEquality =
      new TestExpr(
          ExprCall(
              4, Operator.Equals.id, ExprCall(1, "type", ExprIdent(2, "a")), ExprIdent(3, "uint")),
          SourceInfo.newBuilder().build());

  /** ExprIdent creates an ident (variable) Expr. */
  public static Expr ExprIdent(long id, String name) {
    return Expr.newBuilder().setId(id).setIdentExpr(Ident.newBuilder().setName(name)).build();
  }

  /** ExprSelect creates a select Expr. */
  public static Expr ExprSelect(long id, Expr operand, String field) {
    return Expr.newBuilder()
        .setId(id)
        .setSelectExpr(
            Expr.Select.newBuilder().setOperand(operand).setField(field).setTestOnly(false))
        .build();
  }

  /** ExprLiteral creates a literal (constant) Expr. */
  public static Expr ExprLiteral(long id, Object value) {
    Constant.Builder literal;
    if (value instanceof Boolean) {
      literal = Constant.newBuilder().setBoolValue((Boolean) value);
    } else if (value instanceof Double) {
      literal = Constant.newBuilder().setDoubleValue((Double) value);
    } else if (value instanceof Float) {
      literal = Constant.newBuilder().setDoubleValue((Float) value);
    } else if (value instanceof ULong) {
      literal = Constant.newBuilder().setUint64Value(((Number) value).longValue());
    } else if (value instanceof Number) {
      literal = Constant.newBuilder().setInt64Value(((Number) value).longValue());
    } else if (value instanceof String) {
      literal = Constant.newBuilder().setStringValue(value.toString());
    } else if (value instanceof byte[]) {
      literal = Constant.newBuilder().setBytesValue(ByteString.copyFrom((byte[]) value));
    } else if (value == null) {
      literal = Constant.newBuilder().setNullValueValue(0);
    } else {
      throw new IllegalArgumentException("literal type not implemented");
    }
    return Expr.newBuilder().setId(id).setConstExpr(literal).build();
  }

  /** ExprCall creates a call Expr. */
  public static Expr ExprCall(long id, String function, Expr... args) {
    return Expr.newBuilder()
        .setId(id)
        .setCallExpr(Call.newBuilder().setFunction(function).addAllArgs(asList(args)))
        .build();
  }

  /** ExprMemberCall creates a receiver-style call Expr. */
  public static Expr ExprMemberCall(long id, String function, Expr target, Expr... args) {
    return Expr.newBuilder()
        .setId(id)
        .setCallExpr(
            Call.newBuilder().setTarget(target).setFunction(function).addAllArgs(asList(args)))
        .build();
  }

  /** ExprList creates a create list Expr. */
  public static Expr ExprList(long id, Expr... elements) {
    return Expr.newBuilder()
        .setId(id)
        .setListExpr(CreateList.newBuilder().addAllElements(asList(elements)))
        .build();
  }

  /** ExprMap creates a create struct Expr for a map. */
  public static Expr ExprMap(long id, CreateStruct.Entry... entries) {
    return Expr.newBuilder()
        .setId(id)
        .setStructExpr(CreateStruct.newBuilder().addAllEntries(asList(entries)))
        .build();
  }

  /** ExprType creates creates a create struct Expr for a message. */
  public static Expr ExprType(long id, String messageName, CreateStruct.Entry... entries) {
    return Expr.newBuilder()
        .setId(id)
        .setStructExpr(
            CreateStruct.newBuilder().setMessageName(messageName).addAllEntries(asList(entries)))
        .build();
  }

  /** ExprEntry creates a map entry for a create struct Expr. */
  public static CreateStruct.Entry ExprEntry(long id, Expr key, Expr value) {
    return CreateStruct.Entry.newBuilder().setId(id).setMapKey(key).setValue(value).build();
  }

  /** ExprField creates a field entry for a create struct Expr. */
  public static CreateStruct.Entry ExprField(long id, String field, Expr value) {
    return CreateStruct.Entry.newBuilder().setId(id).setFieldKey(field).setValue(value).build();
  }

  /** ExprComprehension returns a comprehension Expr. */
  public static Expr ExprComprehension(
      long id,
      String iterVar,
      Expr iterRange,
      String accuVar,
      Expr accuInit,
      Expr loopCondition,
      Expr loopStep,
      Expr resultExpr) {
    return Expr.newBuilder()
        .setId(id)
        .setComprehensionExpr(
            Comprehension.newBuilder()
                .setIterVar(iterVar)
                .setIterVar(iterVar)
                .setIterRange(iterRange)
                .setAccuVar(accuVar)
                .setAccuInit(accuInit)
                .setLoopCondition(loopCondition)
                .setLoopStep(loopStep)
                .setResult(resultExpr))
        .build();
  }
}
