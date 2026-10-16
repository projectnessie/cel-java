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
import static org.projectnessie.cel.EvalOption.OptOptimize;
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
import org.projectnessie.cel.interpreter.Activation;
import org.projectnessie.cel.types.jackson3.Jackson3Registry;

/** Measures checked Jackson 3 selection at each evaluator result boundary. */
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class Jackson3EvaluatorBaselineBench {

  @State(Scope.Benchmark)
  public static class EvaluationState {
    Program program;
    Prog internalProgram;
    Map<String, Object> variables;
    Activation activation;
    Policy policy;

    @Setup
    public void init() {
      Jackson3Registry registry = (Jackson3Registry) Jackson3Registry.newRegistry();
      Env env =
          newEnv(
              customTypeAdapter(registry),
              customTypeProvider(registry),
              types(Policy.class, Principal.class),
              declarations(
                  Decls.newVar("policy", Decls.newObjectType(Policy.class.getName())),
                  Decls.newVar("expected", Decls.String)));
      AstIssuesTuple ast = env.compile("policy.owner.email == expected");
      if (ast.hasIssues()) {
        throw ast.getIssues().err();
      }

      policy = new Policy(new Principal("alice@example.com"));
      variables = Map.of("policy", policy, "expected", "alice@example.com");
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
        state.program.eval(state.variables).getVal(), Boolean.class);
  }

  @Benchmark
  public boolean nativeJava(EvaluationState state) {
    return state.policy.getOwner().getEmail().equals("alice@example.com");
  }

  public static final class Principal {
    private final String email;

    public Principal(String email) {
      this.email = email;
    }

    public String getEmail() {
      return email;
    }
  }

  public static final class Policy {
    private final Principal owner;

    public Policy(Principal owner) {
      this.owner = owner;
    }

    public Principal getOwner() {
      return owner;
    }
  }
}
