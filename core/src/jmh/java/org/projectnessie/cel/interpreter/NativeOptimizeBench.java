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
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.EvalOption.OptOptimize;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.checker.Decls.Int;
import static org.projectnessie.cel.checker.Decls.String;
import static org.projectnessie.cel.checker.Decls.newListType;
import static org.projectnessie.cel.checker.Decls.newVar;

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
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.Program;

/** Decision benchmark for composing {@code OptOptimize} with native checked evaluation. */
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class NativeOptimizeBench {

  @State(Scope.Benchmark)
  public static class ProgramState {
    @Param({
      "conversionTerminal",
      "conversionParent",
      "listTerminal",
      "listSize",
      "listIndex",
      "listDynamicIndex",
      "stringMembership",
      "mapTerminal",
      "mapIndex",
      "mapSize",
      "mapMembership",
      "composition"
    })
    public String shape;

    @Param({"established", "establishedOptimize", "native", "nativeOptimize"})
    public String mode;

    Program program;
    Map<String, Object> variables;

    @Setup
    public void setup() {
      Env env =
          newEnv(
              declarations(
                  newVar("x", Int),
                  newVar("y", Int),
                  newVar("s", String),
                  newVar("values", newListType(Int))));
      String list = intList(16);
      String expression =
          switch (shape) {
            case "conversionTerminal" -> "int(\"41\")";
            case "conversionParent" -> "int(\"41\") + x";
            case "listTerminal" -> list;
            case "listSize" -> "size(" + list + ")";
            case "listIndex" -> list + "[8]";
            case "listDynamicIndex" -> "values[int(\"8\")]";
            case "stringMembership" ->
                "s in [\"v0\", \"v1\", \"v2\", \"v3\", \"v4\", \"v5\", \"v6\", \"needle\"]";
            case "mapTerminal" -> "{\"a\": int(\"1\"), \"b\": 2}";
            case "mapIndex" -> "{\"a\": int(\"1\"), \"b\": 2}[\"b\"]";
            case "mapSize" -> "size({\"a\": int(\"1\"), \"b\": 2})";
            case "mapMembership" -> "\"b\" in {\"a\": int(\"1\"), \"b\": 2}";
            case "composition" -> "(x in [1, 2, 3]) && int(\"41\") + 1 == y";
            default -> throw new IllegalArgumentException("unknown shape " + shape);
          };
      Ast ast = compile(env, expression);
      program = program(env, ast, mode);
      variables =
          Map.of(
              "x",
              2L,
              "y",
              42L,
              "s",
              "needle",
              "values",
              new long[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
      program.eval(variables);
    }
  }

  @State(Scope.Benchmark)
  public static class ConstructionState {
    @Param({"conversionParent", "listIndex", "mapIndex", "composition"})
    public String shape;

    @Param({"established", "establishedOptimize", "native", "nativeOptimize"})
    public String mode;

    Env env;
    Ast ast;

    @Setup
    public void setup() {
      env = newEnv(declarations(newVar("x", Int), newVar("y", Int)));
      String list = intList(16);
      String expression =
          switch (shape) {
            case "conversionParent" -> "int(\"41\") + x";
            case "listIndex" -> list + "[8]";
            case "mapIndex" -> "{\"a\": int(\"1\"), \"b\": 2}[\"b\"]";
            case "composition" -> "(x in [1, 2, 3]) && int(\"41\") + 1 == y";
            default -> throw new IllegalArgumentException("unknown shape " + shape);
          };
      ast = compile(env, expression);
    }
  }

  @State(Scope.Benchmark)
  public static class MembershipProgramState {
    @Param({"0", "1", "16", "1024"})
    public int size;

    @Param({"first", "middle", "last", "miss"})
    public String position;

    @Param({"established", "establishedOptimize", "native", "nativeOptimize"})
    public String mode;

    Program program;
    Map<String, Object> variables;

    @Setup
    public void setup() {
      Env env = newEnv(declarations(newVar("x", Int)));
      Ast ast = compile(env, "x in " + intList(size));
      program = program(env, ast, mode);
      long needle =
          switch (position) {
            case "first" -> 0L;
            case "middle" -> size / 2L;
            case "last" -> size - 1L;
            case "miss" -> size + 1L;
            default -> throw new IllegalArgumentException("unknown position " + position);
          };
      variables = Map.of("x", needle);
      program.eval(variables);
    }
  }

  @Benchmark
  public Program.EvalResult evaluate(ProgramState state) {
    return state.program.eval(state.variables);
  }

  @Benchmark
  public Program construct(ConstructionState state) {
    return program(state.env, state.ast, state.mode);
  }

  @Benchmark
  public Program.EvalResult membershipProgram(MembershipProgramState state) {
    return state.program.eval(state.variables);
  }

  private static Program program(Env env, Ast ast, String mode) {
    return switch (mode) {
      case "established" -> env.program(ast, evalOptions(OptDisableNativeEval));
      case "establishedOptimize" ->
          env.program(ast, evalOptions(OptDisableNativeEval, OptOptimize));
      case "native" -> env.program(ast);
      case "nativeOptimize" -> env.program(ast, evalOptions(OptOptimize));
      default -> throw new IllegalArgumentException("unknown mode " + mode);
    };
  }

  private static Ast compile(Env env, String expression) {
    AstIssuesTuple result = env.compile(expression);
    if (result.hasIssues()) {
      throw new IllegalArgumentException(result.getIssues().toString());
    }
    return result.getAst();
  }

  private static String intList(int size) {
    StringBuilder source = new StringBuilder(size * 5 + 2).append('[');
    for (int index = 0; index < size; index++) {
      if (index != 0) {
        source.append(',');
      }
      source.append(index);
    }
    return source.append(']').toString();
  }
}
