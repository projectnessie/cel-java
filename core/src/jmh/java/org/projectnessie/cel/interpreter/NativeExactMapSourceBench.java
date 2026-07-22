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

import java.util.LinkedHashMap;
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

/** End-to-end benchmark for exact constant-key map-source consumers. */
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class NativeExactMapSourceBench {
  @State(Scope.Benchmark)
  public static class MapState {
    @Param({"size", "hit", "membershipMiss", "presentNull"})
    public String operation;

    @Param({"0", "1", "16", "1024"})
    public int size;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Map<String, Object> variables;
    Map<String, Long> values;
    Map<String, Object> nullable;

    @Setup
    public void setup() {
      String expression =
          switch (operation) {
            case "size" -> "size(values)";
            case "hit" -> "cardinality == 0 ? -1 : values['last']";
            case "membershipMiss" -> "'missing' in values";
            case "presentNull" -> "nullable['present']";
            default -> throw new IllegalArgumentException(operation);
          };
      values = new LinkedHashMap<>();
      for (long value = 0; value < size; value++) {
        values.put(value == size - 1L ? "last" : value + "-key", value);
      }
      nullable = new LinkedHashMap<>();
      nullable.put("present", null);
      variables =
          Map.of(
              "values", values,
              "nullable", nullable,
              "cardinality", (long) size);

      Env exactEnv = environment(new ExactAdapter());
      Env generalEnv = environment(DefaultTypeAdapter.Instance);
      Ast exactAst = compile(exactEnv, expression);
      exactNative = exactEnv.program(exactAst);
      exactDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));
      general = generalEnv.program(compile(generalEnv, expression));
    }
  }

  @Benchmark
  public Object exactNative(MapState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object exactDisabled(MapState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object generalAdapter(MapState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public Object javaCeiling(MapState state) {
    return switch (state.operation) {
      case "size" -> state.values.size();
      case "hit" -> state.size == 0 ? -1L : state.values.get("last");
      case "membershipMiss" -> state.values.containsKey("missing");
      case "presentNull" -> state.nullable.get("present");
      default -> throw new IllegalArgumentException(state.operation);
    };
  }

  private static Env environment(org.projectnessie.cel.common.types.ref.TypeAdapter adapter) {
    return newEnv(
        customTypeAdapter(adapter),
        declarations(
            Decls.newVar("values", Decls.newMapType(Decls.String, Decls.Int)),
            Decls.newVar("nullable", Decls.newMapType(Decls.String, Decls.Null)),
            Decls.newVar("cardinality", Decls.Int)));
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
