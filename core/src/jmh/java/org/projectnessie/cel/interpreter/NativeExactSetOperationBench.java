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
package org.projectnessie.cel.interpreter;

import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.projectnessie.cel.Ast;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

/**
 * End-to-end comparison of exact Set membership and encounter-order list equality.
 *
 * <p>The size-zero {@code membershipHit} case necessarily behaves like a miss. The one-element
 * {@code unequalOrder} case uses a different value because reordering one element is impossible.
 */
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class NativeExactSetOperationBench {
  @State(Scope.Benchmark)
  public static class OperationState {
    @Param({
      "membershipHit",
      "membershipMiss",
      "equalSetSet",
      "equalSetList",
      "equalListSet",
      "unequalOrder"
    })
    public String operation;

    @Param({"0", "1", "16", "1024"})
    public int size;

    Program exactNativeProgram;
    Program exactNativeDisabledProgram;
    Program generalProgram;
    Map<String, Object> variables;
    Set<Long> leftSet;
    Object right;
    long needle;

    @Setup
    public void init() {
      String expression;
      boolean membership = operation.startsWith("membership");
      if (membership) {
        expression = "needle in left";
      } else {
        expression =
            switch (operation) {
              case "equalSetSet", "equalSetList", "unequalOrder" -> "left == right";
              case "equalListSet" -> "right == left";
              default -> throw new IllegalArgumentException(operation);
            };
      }

      Env exactEnv =
          newEnv(
              customTypeAdapter(new ExactAdapter()),
              declarations(
                  Decls.newVar("needle", Decls.Int),
                  Decls.newVar("left", Decls.newListType(Decls.Int)),
                  Decls.newVar("right", Decls.newListType(Decls.Int))));
      Env generalEnv =
          newEnv(
              declarations(
                  Decls.newVar("needle", Decls.Int),
                  Decls.newVar("left", Decls.newListType(Decls.Int)),
                  Decls.newVar("right", Decls.newListType(Decls.Int))));
      var exactAst = compile(exactEnv, expression);
      exactNativeProgram = exactEnv.program(exactAst);
      exactNativeDisabledProgram = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));
      generalProgram = generalEnv.program(compile(generalEnv, expression));

      leftSet = values(size);
      needle =
          operation.equals("membershipMiss") || size == 0 ? Long.MIN_VALUE : 50_000L + size - 1;
      right =
          switch (operation) {
            case "equalSetList", "equalListSet" -> new ArrayList<>(leftSet);
            case "unequalOrder" -> unequalEncounterOrder(leftSet);
            default -> values(size);
          };
      variables = Map.of("needle", needle, "left", leftSet, "right", right);
    }
  }

  @Benchmark
  public Object exactNative(OperationState state) {
    return state.exactNativeProgram.eval(state.variables);
  }

  @Benchmark
  public Object exactNativeDisabled(OperationState state) {
    return state.exactNativeDisabledProgram.eval(state.variables);
  }

  @Benchmark
  public Object generalAdapter(OperationState state) {
    return state.generalProgram.eval(state.variables);
  }

  @Benchmark
  public boolean javaCeiling(OperationState state) {
    if (state.operation.startsWith("membership")) {
      return state.leftSet.contains(state.needle);
    }
    Object left = state.operation.equals("equalListSet") ? state.right : state.leftSet;
    Object right = state.operation.equals("equalListSet") ? state.leftSet : state.right;
    return encounterOrderEquals(left, right);
  }

  private static Ast compile(Env env, String expression) {
    var compiled = env.compile(expression);
    if (compiled.hasIssues()) {
      throw new IllegalStateException(compiled.getIssues().toString());
    }
    return compiled.getAst();
  }

  private static LinkedHashSet<Long> values(int size) {
    LinkedHashSet<Long> values = new LinkedHashSet<>(hashCapacity(size));
    for (int i = 0; i < size; i++) {
      values.add(50_000L + i);
    }
    return values;
  }

  private static Object unequalEncounterOrder(Set<Long> left) {
    if (left.isEmpty()) {
      return List.of(50_000L);
    }
    if (left.size() == 1) {
      return List.of(left.iterator().next() + 1L);
    }
    List<Long> reversed = new ArrayList<>(left);
    Collections.reverse(reversed);
    return new LinkedHashSet<>(reversed);
  }

  private static boolean encounterOrderEquals(Object left, Object right) {
    Iterator<?> leftIterator = ((Iterable<?>) left).iterator();
    Iterator<?> rightIterator = ((Iterable<?>) right).iterator();
    while (leftIterator.hasNext() && rightIterator.hasNext()) {
      if (!leftIterator.next().equals(rightIterator.next())) {
        return false;
      }
    }
    return !leftIterator.hasNext() && !rightIterator.hasNext();
  }

  private static int hashCapacity(int size) {
    return Math.max(16, size * 4 / 3 + 1);
  }

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }
}
