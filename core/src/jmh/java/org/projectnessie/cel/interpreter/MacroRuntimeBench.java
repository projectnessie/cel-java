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
package org.projectnessie.cel.interpreter;

import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.Util.mapOf;

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
import org.openjdk.jmh.infra.Blackhole;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.EvalOption;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;

@Warmup(iterations = 1, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MacroRuntimeBench {

  @State(Scope.Benchmark)
  public static class MacroState {
    @Param({"existsEarly", "existsLate", "all", "filter", "map"})
    public String expression;

    @Param({"10", "1000"})
    public int size;

    Program program;
    Map<Object, Object> vars;

    @Setup
    public void init() {
      switch (expression) {
        case "existsEarly":
          program = program("items.exists(i, i == target)");
          vars = mapOf("items", list(size), "target", 0L);
          return;
        case "existsLate":
          program = program("items.exists(i, i == target)");
          vars = mapOf("items", list(size), "target", (long) size - 1);
          return;
        case "all":
          program = program("items.all(i, i >= 0)");
          vars = mapOf("items", list(size));
          return;
        case "filter":
          program = program("items.filter(i, i > target)");
          vars = mapOf("items", list(size), "target", (long) size / 2);
          return;
        case "map":
          program = program("items.map(i, i + 1)");
          vars = mapOf("items", list(size));
          return;
        default:
          throw new IllegalArgumentException("Unknown macro benchmark expression: " + expression);
      }
    }
  }

  @Benchmark
  public void macroRuntime(MacroState state, Blackhole blackhole) {
    blackhole.consume(state.program.eval(state.vars));
  }

  private static Program program(String expression) {
    Env env =
        newEnv(
            declarations(
                Decls.newVar("items", Decls.newListType(Decls.Int)),
                Decls.newVar("target", Decls.Int)));
    AstIssuesTuple ast = env.compile(expression);
    if (ast.hasIssues()) {
      throw ast.getIssues().err();
    }
    return env.program(ast.getAst(), evalOptions(EvalOption.OptOptimize));
  }

  private static List<Long> list(int size) {
    List<Long> values = new ArrayList<>(size);
    for (long i = 0; i < size; i++) {
      values.add(i);
    }
    return values;
  }
}
