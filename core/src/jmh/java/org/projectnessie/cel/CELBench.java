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
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.Util.mapOf;

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
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.checker.Decls;

@Warmup(iterations = 1, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class CELBench {

  @State(Scope.Benchmark)
  public static class Prg {

    @Param({"OptExhaustiveEval", "OptOptimize", "OptPartialEval"})
    public EvalOption evalOption;

    private Program prg;
    private Map<Object, Object> vars;

    @Setup
    public void init() {
      Env e =
          newEnv(
              declarations(
                  Decls.newVar("ai", Decls.Int),
                  Decls.newVar("ar", Decls.newMapType(Decls.String, Decls.String))));
      AstIssuesTuple astIss = e.compile("ai == 20 || ar['foo'] == 'bar'");
      vars = mapOf("ai", 2, "ar", mapOf("foo", "bar"));

      prg = e.program(astIss.getAst(), evalOptions(evalOption));
    }
  }

  @Benchmark
  public void eval(Prg prg) {
    prg.prg.eval(prg.vars);
  }
}
