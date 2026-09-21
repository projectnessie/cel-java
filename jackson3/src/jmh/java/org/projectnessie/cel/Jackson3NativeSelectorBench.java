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
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.customTypeProvider;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.interpreter.Activation.newActivation;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.Activation;
import org.projectnessie.cel.interpreter.Interpretable;
import org.projectnessie.cel.interpreter.NativeCapabilityBenchmark;
import org.projectnessie.cel.types.jackson3.Jackson3Registry;

/** Measures native and current evaluation of a top-level Jackson 3 string selector. */
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class Jackson3NativeSelectorBench {
  @State(Scope.Benchmark)
  public static class EvaluationState {
    Prog nativeProgram;
    NativeCapabilityBenchmark nativePlan;
    Program currentProgram;
    Interpretable currentPlan;
    Activation activation;
    Map<String, Object> variables;
    Input input;

    @Setup
    public void init() {
      Jackson3Registry registry = (Jackson3Registry) Jackson3Registry.newRegistry();
      Env env =
          newEnv(
              customTypeAdapter(registry),
              customTypeProvider(registry),
              types(Input.class),
              declarations(
                  Decls.newVar("input", Decls.newObjectType(Input.class.getName())),
                  Decls.newVar("expected", Decls.String)));
      AstIssuesTuple result = env.compile("input.text == expected");
      if (result.hasIssues()) {
        throw result.getIssues().err();
      }
      Ast ast = result.getAst();

      nativeProgram = (Prog) env.program(ast);
      nativePlan = NativeCapabilityBenchmark.require(nativeProgram.interpretable);
      currentProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      currentPlan = ((Prog) currentProgram).interpretable;

      input = new Input("alice@example.com");
      variables = Map.of("input", input, "expected", "alice@example.com");
      activation = newActivation(variables);
      assertEquivalent(nativePlan.eval(activation), currentPlan.eval(activation));
    }
  }

  @Benchmark
  public boolean nativePrimitive(EvaluationState state) {
    return state.nativePlan.evalBoolean(state.activation);
  }

  @Benchmark
  public Val currentRaw(EvaluationState state) {
    return state.currentPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult nativeProgramEval(EvaluationState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult currentProgramEval(EvaluationState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public boolean nativeJava(EvaluationState state) {
    return state.input.getText().equals("alice@example.com");
  }

  private static void assertEquivalent(Val nativeValue, Val currentValue) {
    if (!nativeValue.type().equals(currentValue.type())
        || !nativeValue.toString().equals(currentValue.toString())) {
      throw new IllegalStateException(
          String.format(
              "native result %s differs from current result %s", nativeValue, currentValue));
    }
  }

  public static final class Input {
    private final String text;

    public Input(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }
  }
}
