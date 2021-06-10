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
package org.projectnessie.cel.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.TestExpr.ExprCall;
import static org.projectnessie.cel.TestExpr.ExprLiteral;
import static org.projectnessie.cel.Util.mapOf;

import com.google.api.expr.v1alpha1.CheckRequest;
import com.google.api.expr.v1alpha1.CheckResponse;
import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.ConformanceServiceGrpc;
import com.google.api.expr.v1alpha1.ConformanceServiceGrpc.ConformanceServiceBlockingStub;
import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Constant.ConstantKindCase;
import com.google.api.expr.v1alpha1.EvalRequest;
import com.google.api.expr.v1alpha1.EvalResponse;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Call;
import com.google.api.expr.v1alpha1.Expr.ExprKindCase;
import com.google.api.expr.v1alpha1.ExprValue;
import com.google.api.expr.v1alpha1.ExprValue.KindCase;
import com.google.api.expr.v1alpha1.ParseRequest;
import com.google.api.expr.v1alpha1.ParseResponse;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.api.expr.v1alpha1.Type;
import com.google.api.expr.v1alpha1.Type.PrimitiveType;
import com.google.api.expr.v1alpha1.Type.TypeKindCase;
import com.google.api.expr.v1alpha1.Value;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.operators.Operator;

class ConformanceServerTest {

  private static Server server;
  private static ConformanceServiceBlockingStub stub;
  private static ManagedChannel channel;

  private static ParsedExpr parsed;

  @BeforeAll
  static void startServer() throws Exception {
    server = ServerBuilder.forPort(0).addService(new ConformanceServiceImpl()).build();
    server.start();

    String host = ConformanceServer.getListenHost(server);

    channel = ManagedChannelBuilder.forAddress(host, server.getPort()).usePlaintext().build();
    stub = ConformanceServiceGrpc.newBlockingStub(channel);

    parsed =
        ParsedExpr.newBuilder()
            .setExpr(ExprCall(1, Operator.Add.id, ExprLiteral(2, 1L), ExprLiteral(3, 1L)))
            .setSourceInfo(
                SourceInfo.newBuilder()
                    .setLocation("the location")
                    .putAllPositions(
                        mapOf(
                            1L, 0,
                            2L, 0,
                            3L, 4))
                    .build())
            .build();
  }

  @AfterAll
  static void stopServer() throws Exception {
    Exception x = null;
    try {
      channel.shutdown();
    } catch (Exception e) {
      x = e;
    }

    try {
      server.shutdown();
    } catch (Exception e) {
      if (x == null) x = e;
      else x.addSuppressed(e);
    }

    try {
      channel.awaitTermination(30, TimeUnit.SECONDS);
    } catch (Exception e) {
      if (x == null) x = e;
      else x.addSuppressed(e);
    }

    try {
      server.awaitTermination(30, TimeUnit.SECONDS);
    } catch (Exception e) {
      if (x == null) x = e;
      else x.addSuppressed(e);
    }

    if (x != null) throw x;
  }

  /** TestParse tests the Parse method. */
  @Test
  void Parse() {
    ParseRequest req = ParseRequest.newBuilder().setCelSource("1 + 1").build();
    ParseResponse res = stub.parse(req);
    assertThat(res.isInitialized()).isTrue();
    assertThat(res.getParsedExpr().isInitialized()).isTrue();
    // Could check against 'parsed' above,
    // but the expression ids are arbitrary,
    // and explicit comparison logic is about as
    // much work as normalization would be.
    assertThat(res.getParsedExpr().getExpr().isInitialized()).isTrue();
    assertThat(res.getParsedExpr().getExpr().getExprKindCase()).isSameAs(ExprKindCase.CALL_EXPR);

    Call c = res.getParsedExpr().getExpr().getCallExpr();
    assertThat(c.getTarget().isInitialized()).isTrue();
    assertThat(c.getFunction()).isEqualTo("_+_");
    assertThat(c.getArgsCount()).isEqualTo(2);
    for (Expr a : c.getArgsList()) {
      assertThat(a.getExprKindCase()).isSameAs(ExprKindCase.CONST_EXPR);
      Constant l = a.getConstExpr();
      assertThat(l.getConstantKindCase()).isSameAs(ConstantKindCase.INT64_VALUE);
      assertThat(l.getInt64Value()).isEqualTo(1);
    }
  }

  /** TestCheck tests the Check method. */
  @Test
  void Check() {
    // If TestParse() passes, it validates a good chunk
    // of the server mechanisms for data conversion, so we
    // won't be as fussy here..
    CheckRequest req = CheckRequest.newBuilder().setParsedExpr(parsed).build();
    CheckResponse res = stub.check(req);
    assertThat(res.isInitialized()).isTrue();
    assertThat(res.getCheckedExpr().isInitialized()).isTrue();
    Type tp = res.getCheckedExpr().getTypeMapMap().get(1L);
    assertThat(tp).isNotNull();
    assertThat(tp.getTypeKindCase()).isSameAs(TypeKindCase.PRIMITIVE);
    assertThat(tp.getPrimitive()).isSameAs(PrimitiveType.INT64);
  }

  /** TestEval tests the Eval method. */
  @Test
  void Eval() {
    EvalRequest req = EvalRequest.newBuilder().setParsedExpr(parsed).build();
    EvalResponse res = stub.eval(req);
    assertThat(res.isInitialized()).isTrue();
    assertThat(res.getResult().isInitialized()).isTrue();

    assertThat(res.getResult().getKindCase()).isSameAs(KindCase.VALUE);
    assertThat(res.getResult().getValue().getKindCase()).isSameAs(Value.KindCase.INT64_VALUE);
    assertThat(res.getResult().getValue().getInt64Value()).isEqualTo(2L);
  }

  /** TestFullUp tests Parse, Check, and Eval back-to-back. */
  @Test
  void FullUp() {
    ParseRequest preq = ParseRequest.newBuilder().setCelSource("x + y").build();
    ParseResponse pres = stub.parse(preq);
    assertThat(pres.isInitialized()).isTrue();
    ParsedExpr parsedExpr = pres.getParsedExpr();
    assertThat(parsedExpr.isInitialized()).isTrue();

    CheckRequest creq =
        CheckRequest.newBuilder()
            .setParsedExpr(parsedExpr)
            .addTypeEnv(Decls.newVar("x", Decls.Int))
            .addTypeEnv(Decls.newVar("y", Decls.Int))
            .build();
    CheckResponse cres = stub.check(creq);
    assertThat(cres.isInitialized()).isTrue();
    CheckedExpr checkedExpr = cres.getCheckedExpr();
    assertThat(checkedExpr.isInitialized()).isTrue();

    Type tp = checkedExpr.getTypeMapMap().get(1L);
    assertThat(tp).isNotNull();
    assertThat(tp.getTypeKindCase()).isSameAs(TypeKindCase.PRIMITIVE);
    assertThat(tp.getPrimitive()).isSameAs(PrimitiveType.INT64);

    EvalRequest ereq =
        EvalRequest.newBuilder()
            .setCheckedExpr(checkedExpr)
            .putBindings("x", exprValueInt64(1))
            .putBindings("y", exprValueInt64(2))
            .build();
    EvalResponse eres = stub.eval(ereq);
    assertThat(eres.isInitialized()).isTrue();
    assertThat(eres.getResult().isInitialized()).isTrue();

    assertThat(eres.getResult().getKindCase()).isSameAs(KindCase.VALUE);
    assertThat(eres.getResult().getValue().getKindCase()).isSameAs(Value.KindCase.INT64_VALUE);
    assertThat(eres.getResult().getValue().getInt64Value()).isEqualTo(3L);
  }

  static ExprValue exprValueInt64(long x) {
    return ExprValue.newBuilder().setValue(Value.newBuilder().setInt64Value(x)).build();
  }

  static class FullPipelineResult {
    final ParseResponse parseResponse;
    final CheckResponse checkResponse;
    final EvalResponse evalResponse;

    FullPipelineResult(
        ParseResponse parseResponse, CheckResponse checkResponse, EvalResponse evalResponse) {
      this.parseResponse = parseResponse;
      this.checkResponse = checkResponse;
      this.evalResponse = evalResponse;
    }
  }

  /**
   * fullPipeline parses, checks, and evaluates the CEL expression in source and returns the result
   * from the Eval call.
   */
  FullPipelineResult fullPipeline(String source) {

    // Parse
    ParseRequest preq = ParseRequest.newBuilder().setCelSource(source).build();
    ParseResponse pres = stub.parse(preq);
    assertThat(pres.isInitialized()).isTrue();
    ParsedExpr parsedExpr = pres.getParsedExpr();
    assertThat(parsedExpr.isInitialized()).isTrue();
    assertThat(parsedExpr.getExpr().isInitialized()).isTrue();

    // Check
    CheckRequest creq = CheckRequest.newBuilder().setParsedExpr(parsedExpr).build();
    CheckResponse cres = stub.check(creq);
    assertThat(cres.isInitialized()).isTrue();
    CheckedExpr checkedExpr = cres.getCheckedExpr();
    assertThat(checkedExpr.isInitialized()).isTrue();

    // Eval
    EvalRequest ereq = EvalRequest.newBuilder().setCheckedExpr(checkedExpr).build();
    EvalResponse eres = stub.eval(ereq);
    assertThat(eres.isInitialized()).isTrue();
    assertThat(eres.getResult().isInitialized()).isTrue();

    return new FullPipelineResult(pres, cres, eres);
  }

  /**
   * expectEvalTrue parses, checks, and evaluates the CEL expression in source and checks that the
   * result is the boolean value 'true'.
   */
  void expectEvalTrue(String source) {
    FullPipelineResult fp = fullPipeline(source);

    long rootID = fp.parseResponse.getParsedExpr().getExpr().getId();
    Type topType = fp.checkResponse.getCheckedExpr().getTypeMapMap().get(rootID);
    assertThat(topType).extracting(Type::getTypeKindCase).isEqualTo(Type.TypeKindCase.PRIMITIVE);
    assertThat(topType).extracting(Type::getPrimitive).isEqualTo(Type.PrimitiveType.BOOL);

    ExprValue er = fp.evalResponse.getResult();
    assertThat(er).extracting(ExprValue::getKindCase).isEqualTo(ExprValue.KindCase.VALUE);
    Value ev = er.getValue();
    assertThat(ev).extracting(Value::getKindCase).isEqualTo(Value.KindCase.BOOL_VALUE);
    assertThat(ev).extracting(Value::getBoolValue).isEqualTo(true);
  }

  /** TestCondTrue tests true conditional behavior. */
  @Test
  void CondTrue() {
    expectEvalTrue("(true ? 'a' : 'b') == 'a'");
  }

  /** TestCondFalse tests false conditional behavior. */
  @Test
  void CondFalse() {
    expectEvalTrue("(false ? 'a' : 'b') == 'b'");
  }

  /** TestMapOrderInsignificant tests that maps with different order are equal. */
  @Test
  void MapOrderInsignificant() {
    expectEvalTrue("{1: 'a', 2: 'b'} == {2: 'b', 1: 'a'}");
  }

  /** FailsTestOneMetaType tests that types of different types are equal. */
  @Test
  void FailsTestOneMetaType() {
    expectEvalTrue("type(type(1)) == type(type('foo'))");
  }

  /** FailsTestTypeType tests that the meta-type is its own type. */
  @Test
  void FailsTestTypeType() {
    expectEvalTrue("type(type) == type");
  }

  /** FailsTestNullTypeName checks that the type of null is "null_type". */
  @Test
  void FailsTestNullTypeName() {
    expectEvalTrue("type(null) == null_type");
  }

  /** TestError ensures that errors are properly transmitted. */
  @Test
  void Error() {
    FullPipelineResult fp = fullPipeline("1 / 0");
    assertThat(fp.evalResponse.getResult().getKindCase()).isEqualTo(ExprValue.KindCase.ERROR);
  }
}
