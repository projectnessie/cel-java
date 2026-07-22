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
import java.util.List;
import java.util.Map;
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

/** End-to-end benchmark for immediate checked list-concatenation consumers. */
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class NativeListConcatBench {
  @State(Scope.Benchmark)
  public static class ConcatState {
    @Param({"size", "boundaryIndex"})
    public String operation;

    @Param({"0", "1", "16", "1024"})
    public int size;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Map<String, Object> variables;
    List<Long> left;
    List<Long> right;

    @Setup
    public void setup() {
      String expression =
          operation.equals("size")
              ? "size(left + right)"
              : size == 0 ? "-1" : "(left + right)[" + size + "]";
      left = values(size, 0);
      right = values(size, size);
      variables = Map.of("left", left, "right", right);

      Env exactEnv =
          newEnv(
              customTypeAdapter(new ExactAdapter()),
              declarations(
                  Decls.newVar("left", Decls.newListType(Decls.Int)),
                  Decls.newVar("right", Decls.newListType(Decls.Int))));
      Env generalEnv =
          newEnv(
              declarations(
                  Decls.newVar("left", Decls.newListType(Decls.Int)),
                  Decls.newVar("right", Decls.newListType(Decls.Int))));
      Ast exactAst = compile(exactEnv, expression);
      exactNative = exactEnv.program(exactAst);
      exactDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));
      general = generalEnv.program(compile(generalEnv, expression));
    }
  }

  @Benchmark
  public Object exactNative(ConcatState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object exactDisabled(ConcatState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object generalAdapter(ConcatState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public long javaCeiling(ConcatState state) {
    return state.operation.equals("size")
        ? (long) state.left.size() + state.right.size()
        : state.size == 0 ? -1L : state.right.get(0);
  }

  private static List<Long> values(int size, int offset) {
    List<Long> values = new ArrayList<>(size);
    for (long value = 0; value < size; value++) {
      values.add(offset + value);
    }
    return values;
  }

  private static Ast compile(Env env, String expression) {
    var result = env.compile(expression);
    if (result.hasIssues()) {
      throw new IllegalStateException(result.getIssues().toString());
    }
    return result.getAst();
  }

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }
}
