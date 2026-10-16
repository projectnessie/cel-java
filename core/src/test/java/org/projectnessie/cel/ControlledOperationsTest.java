/*
 * Copyright (C) 2026 The Authors of CEL-Java
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

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.ProgramOption.functions;
import static org.projectnessie.cel.checker.Decls.Int;
import static org.projectnessie.cel.checker.Decls.newFunction;
import static org.projectnessie.cel.checker.Decls.newOverload;
import static org.projectnessie.cel.common.types.IntT.intOf;

import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.SourceInfo;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.Source;
import org.projectnessie.cel.interpreter.ActivationFunction;
import org.projectnessie.cel.interpreter.functions.Overload;

class ControlledOperationsTest {
  @Test
  void controlledCompilePlanAndEvaluationSucceed() {
    var env = Env.newEnv();
    var checked = env.compileCancelable("[1, 2, 3].exists(x, x == 2)").execute();
    assertThat(checked.hasIssues()).isFalse();

    var program = env.programCancelable(checked.getAst()).execute();
    assertThat(program.evalCancelable(emptyMap()).eval().getVal().booleanValue()).isTrue();
  }

  @Test
  void preCancelledEvaluationAbortsAndHandleIsOneShot() {
    var env = Env.newEnv();
    var checked = env.compile("true");
    var handle = env.program(checked.getAst()).evalCancelable(emptyMap());
    handle.cancel();

    assertThatThrownBy(handle::eval)
        .isInstanceOfSatisfying(
            OperationAbortedException.class,
            e ->
                assertThat(e.getReason())
                    .isEqualTo(OperationAbortedException.Reason.EXPLICIT_CANCELLATION));
    assertThatThrownBy(handle::eval).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void structuralLimitsApplyAfterMacroExpansion() {
    var limits = ResourceLimits.newBuilder().astNodeLimit(1).build();
    var handle = Env.newEnv().parseCancelable("[1, 2].exists(x, x == 2)", limits);

    assertThatThrownBy(handle::execute)
        .isInstanceOfSatisfying(
            OperationAbortedException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(OperationAbortedException.Reason.AST_NODE_LIMIT);
              assertThat(e.getPhase()).isEqualTo(OperationAbortedException.Phase.AST_BUILD);
            });
  }

  @Test
  void metadataLimitReportsTheTotalAcrossRetainedMacroTrees() {
    var env = Env.newEnv();
    var scalar =
        Expr.newBuilder()
            .setId(1)
            .setConstExpr(com.google.api.expr.v1alpha1.Constant.getDefaultInstance())
            .build();
    var info = SourceInfo.newBuilder().putMacroCalls(1, scalar).build();
    var ast = new Ast(scalar, info, Source.newTextSource(""));
    long metadataBeforeRetainedTrees =
        (long) info.getPositionsCount() + info.getLineOffsetsCount() + info.getMacroCallsCount();
    var limits =
        ResourceLimits.newBuilder().astMetadataEntryLimit(metadataBeforeRetainedTrees).build();

    assertThatThrownBy(() -> env.checkCancelable(ast, limits).execute())
        .isInstanceOfSatisfying(
            OperationAbortedException.class,
            e -> {
              assertThat(e.getReason())
                  .isEqualTo(OperationAbortedException.Reason.AST_METADATA_LIMIT);
              assertThat(e.getLimit()).hasValue(metadataBeforeRetainedTrees);
              assertThat(e.getObserved()).hasValue(metadataBeforeRetainedTrees + 1);
            });
  }

  @Test
  void structuralAdmissionHandlesDeepCallerSuppliedAstIteratively() {
    Expr expression =
        Expr.newBuilder()
            .setId(1)
            .setConstExpr(com.google.api.expr.v1alpha1.Constant.getDefaultInstance())
            .build();
    for (int depth = 2; depth <= 2_000; depth++) {
      expression =
          Expr.newBuilder()
              .setId(depth)
              .setSelectExpr(Expr.Select.newBuilder().setOperand(expression).setField("field"))
              .build();
    }
    var ast =
        new Ast(
            expression,
            SourceInfo.getDefaultInstance(),
            Source.newStringSource("", "deep imported AST"));
    var limits = ResourceLimits.newBuilder().astDepthLimit(1_999).build();

    assertThatThrownBy(() -> Env.newEnv().checkCancelable(ast, limits).execute())
        .isInstanceOfSatisfying(
            OperationAbortedException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(OperationAbortedException.Reason.AST_DEPTH_LIMIT);
              assertThat(e.getLimit()).hasValue(1_999);
              assertThat(e.getObserved()).hasValue(2_000);
            });
  }

  @Test
  void cancellationFromActivationEscapesEstablishedAndNativeFastPaths() {
    for (var options :
        new ProgramOption[][] {new ProgramOption[0], {evalOptions(OptDisableNativeEval)}}) {
      var env = Env.newEnv(declarations(Decls.newVar("xs", Decls.newListType(Decls.Int))));
      var checked = env.compile("xs.exists(x, x == 3)");
      var program = env.program(checked.getAst(), options);
      var handleReference = new AtomicReference<CancelableEval>();
      ActivationFunction activation =
          name -> {
            handleReference.get().cancel();
            return name.equals("xs") ? List.of(1L, 2L, 3L) : ActivationFunction.ABSENT;
          };
      var handle = program.evalCancelable(activation);
      handleReference.set(handle);

      assertThatThrownBy(handle::eval)
          .isInstanceOfSatisfying(
              OperationAbortedException.class,
              e ->
                  assertThat(e.getReason())
                      .isEqualTo(OperationAbortedException.Reason.EXPLICIT_CANCELLATION));
    }
  }

  @Test
  void cancellationRequestedByCustomOverloadIsObservedAtCallbackBoundary() {
    var env =
        Env.newEnv(
            declarations(
                newFunction("cancel_me", newOverload("cancel_me_int", List.of(Int), Int)),
                newFunction("observe", newOverload("observe_int", List.of(Int), Int))));
    var checked = env.compile("cancel_me(1) + observe(1)");
    var handleReference = new AtomicReference<CancelableEval>();
    var observerCalled = new AtomicBoolean();
    var program =
        env.program(
            checked.getAst(),
            functions(
                Overload.unary(
                    "cancel_me_int",
                    ignored -> {
                      handleReference.get().cancel();
                      return intOf(1);
                    }),
                Overload.unary(
                    "observe_int",
                    ignored -> {
                      observerCalled.set(true);
                      return intOf(1);
                    })));
    var handle = program.evalCancelable(emptyMap());
    handleReference.set(handle);

    assertThatThrownBy(handle::eval)
        .isInstanceOfSatisfying(
            OperationAbortedException.class,
            e -> {
              assertThat(e.getReason())
                  .isEqualTo(OperationAbortedException.Reason.EXPLICIT_CANCELLATION);
              assertThat(e.getPhase()).isEqualTo(OperationAbortedException.Phase.EVALUATE);
            });
    assertThat(observerCalled).isFalse();
  }
}
