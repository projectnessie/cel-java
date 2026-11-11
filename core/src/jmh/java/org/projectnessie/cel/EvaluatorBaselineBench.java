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

import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.EvalOption.OptOptimize;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.interpreter.Activation.newActivation;

import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.interpreter.Activation;

/** Attributes evaluator cost at the raw, public-program, and native-result boundaries. */
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class EvaluatorBaselineBench {

  @State(Scope.Benchmark)
  public static class EvaluationState {
    @Param({
      "identityInt",
      "addInt",
      "chainInt",
      "chainDouble",
      "chainString",
      "shortCircuit",
      "mapSelection",
      "protoSelection"
    })
    public String expression;

    Program program;
    Prog internalProgram;
    Map<String, Object> variables;
    Activation activation;
    Class<?> nativeResultType;
    Supplier<Object> nativeJava;

    @Setup
    public void init() {
      Env env;
      String source;
      switch (expression) {
        case "identityInt":
          env = newEnv(declarations(Decls.newVar("x", Decls.Int)));
          source = "x";
          variables = Map.of("x", 41L);
          nativeResultType = Long.class;
          nativeJava = () -> variables.get("x");
          break;
        case "addInt":
          env = newEnv(declarations(Decls.newVar("x", Decls.Int)));
          source = "x + 1";
          variables = Map.of("x", 41L);
          nativeResultType = Long.class;
          nativeJava = () -> (long) variables.get("x") + 1L;
          break;
        case "chainInt":
          env = newEnv(declarations(Decls.newVar("x", Decls.Int)));
          source = "((x + 1) * 3 - 2) / 2";
          variables = Map.of("x", 41L);
          nativeResultType = Long.class;
          nativeJava = () -> (((long) variables.get("x") + 1L) * 3L - 2L) / 2L;
          break;
        case "chainDouble":
          env = newEnv(declarations(Decls.newVar("x", Decls.Double)));
          source = "((x + 1.25) * 3.0 - 2.0) / 2.0";
          variables = Map.of("x", 41.5d);
          nativeResultType = Double.class;
          nativeJava = () -> (((double) variables.get("x") + 1.25d) * 3.0d - 2.0d) / 2.0d;
          break;
        case "chainString":
          env =
              newEnv(
                  declarations(
                      Decls.newVar("prefix", Decls.String),
                      Decls.newVar("x", Decls.String),
                      Decls.newVar("suffix", Decls.String)));
          source = "prefix + x + suffix";
          variables = Map.of("prefix", "pre-", "x", "value", "suffix", "-post");
          nativeResultType = String.class;
          nativeJava =
              () -> (String) variables.get("prefix") + variables.get("x") + variables.get("suffix");
          break;
        case "shortCircuit":
          env =
              newEnv(
                  declarations(
                      Decls.newVar("enabled", Decls.Bool), Decls.newVar("expensive", Decls.Bool)));
          source = "enabled && expensive";
          variables = Map.of("enabled", false, "expensive", true);
          nativeResultType = Boolean.class;
          nativeJava =
              () -> (boolean) variables.get("enabled") && (boolean) variables.get("expensive");
          break;
        case "mapSelection":
          env =
              newEnv(
                  declarations(
                      Decls.newVar("attrs", Decls.newMapType(Decls.String, Decls.Int)),
                      Decls.newVar("key", Decls.String),
                      Decls.newVar("target", Decls.Int)));
          source = "attrs[key] == target";
          variables = Map.of("attrs", Map.of("answer", 42L), "key", "answer", "target", 42L);
          nativeResultType = Boolean.class;
          nativeJava =
              () ->
                  ((Map<?, ?>) variables.get("attrs"))
                      .get(variables.get("key"))
                      .equals(variables.get("target"));
          break;
        case "protoSelection":
          env =
              newEnv(
                  types(TestAllTypes.getDefaultInstance()),
                  declarations(
                      Decls.newVar(
                          "msg", Decls.newObjectType("cel.expr.conformance.proto3.TestAllTypes")),
                      Decls.newVar("target", Decls.Int)));
          source = "msg.single_int64 == target";
          TestAllTypes message = TestAllTypes.newBuilder().setSingleInt64(42L).build();
          variables = Map.of("msg", message, "target", 42L);
          nativeResultType = Boolean.class;
          nativeJava = () -> message.getSingleInt64() == (long) variables.get("target");
          break;
        default:
          throw new IllegalArgumentException(
              "Unknown evaluator benchmark expression: " + expression);
      }

      AstIssuesTuple ast = env.compile(source);
      if (ast.hasIssues()) {
        throw ast.getIssues().err();
      }
      program = env.program(ast.getAst(), evalOptions(OptOptimize));
      internalProgram = (Prog) program;
      activation = newActivation(variables);
    }
  }

  @Benchmark
  public Object rawEvaluator(EvaluationState state) {
    return state.internalProgram.interpretable.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult programEval(EvaluationState state) {
    return state.program.eval(state.variables);
  }

  @Benchmark
  public Object programEvalNative(EvaluationState state) {
    return state.internalProgram.e.adapter.valueToNative(
        state.program.eval(state.variables).getVal(), state.nativeResultType);
  }

  @Benchmark
  public Object nativeJava(EvaluationState state) {
    return state.nativeJava.get();
  }
}
