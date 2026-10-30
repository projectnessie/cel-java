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

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EvalOption.OptExhaustiveEval;
import static org.projectnessie.cel.EvalOption.OptTrackState;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.common.types.BoolT.True;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.Program.EvalResult;
import org.projectnessie.cel.interpreter.EvalState;

class EvalDetailsTest {

  private static final long CALLER_VALUE_ID = Long.MAX_VALUE;

  @Test
  void ordinaryEvaluationReturnsIndependentMutableEmptyState() {
    Program program = program();

    EvalResult first = program.eval(emptyMap());
    EvalResult second = program.eval(emptyMap());

    assertThat(first.getEvalDetails()).isNotNull();
    assertThat(first.getEvalDetails().getState()).isNotNull();
    assertThat(first.getEvalDetails().getState().ids()).isEmpty();
    assertThat(second.getEvalDetails().getState()).isNotSameAs(first.getEvalDetails().getState());

    first.getEvalDetails().getState().setValue(CALLER_VALUE_ID, True);

    assertThat(first.getEvalDetails().getState().value(CALLER_VALUE_ID)).isSameAs(True);
    assertThat(second.getEvalDetails().getState().value(CALLER_VALUE_ID)).isNull();
    assertThat(program.eval(emptyMap()).getEvalDetails().getState().value(CALLER_VALUE_ID))
        .isNull();
  }

  @Test
  void trackedAndExhaustiveEvaluationReturnIndependentMutableState() {
    for (EvalOption option : List.of(OptTrackState, OptExhaustiveEval)) {
      Program program = program(option);

      EvalState first = program.eval(emptyMap()).getEvalDetails().getState();
      EvalState second = program.eval(emptyMap()).getEvalDetails().getState();

      assertThat(first).isNotSameAs(second);
      assertThat(first.ids()).isNotEmpty();
      assertThat(second.ids()).isNotEmpty();

      first.setValue(CALLER_VALUE_ID, True);

      assertThat(first.value(CALLER_VALUE_ID)).isSameAs(True);
      assertThat(second.value(CALLER_VALUE_ID)).isNull();
    }
  }

  @Test
  void concurrentEvaluationsOwnDistinctState() throws Exception {
    for (Program program : List.of(program(), program(OptTrackState), program(OptExhaustiveEval))) {
      int concurrency = 8;
      ExecutorService executor = Executors.newFixedThreadPool(concurrency);
      try {
        List<CompletableFuture<EvalState>> futures =
            IntStream.range(0, concurrency)
                .mapToObj(
                    ignored ->
                        CompletableFuture.supplyAsync(
                            () -> program.eval(emptyMap()).getEvalDetails().getState(), executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .get(30, TimeUnit.SECONDS);
        Set<EvalState> states = Collections.newSetFromMap(new IdentityHashMap<>());
        futures.forEach(future -> states.add(future.join()));

        assertThat(states).hasSize(concurrency);
        EvalState mutated = states.iterator().next();
        mutated.setValue(CALLER_VALUE_ID, True);
        assertThat(states)
            .allSatisfy(
                state -> {
                  if (state != mutated) {
                    assertThat(state.value(CALLER_VALUE_ID)).isNull();
                  }
                });
      } finally {
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
      }
    }
  }

  @Test
  void directEvalDetailsConstructionRetainsSuppliedState() {
    EvalState state = EvalState.newEvalState();

    assertThat(new EvalDetails(state).getState()).isSameAs(state);
    assertThat(new EvalDetails(null).getState()).isNull();
    assertThat(Program.newEvalResult(null, null))
        .extracting(EvalResult::getVal, EvalResult::getEvalDetails)
        .containsExactly(null, null);
  }

  private static Program program(EvalOption... options) {
    Env env = newEnv();
    AstIssuesTuple compiled = env.compile("true && true");
    assertThat(compiled.hasIssues()).isFalse();
    return options.length == 0
        ? env.program(compiled.getAst())
        : env.program(compiled.getAst(), evalOptions(options));
  }
}
