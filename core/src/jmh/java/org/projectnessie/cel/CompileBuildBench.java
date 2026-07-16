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

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptHost;

@Warmup(iterations = 1, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class CompileBuildBench {

  @State(Scope.Thread)
  public static class CompileState {
    @Param({"simplePredicate", "deepSelectors", "macroPipeline"})
    public String expression;

    String source() {
      switch (expression) {
        case "simplePredicate":
          return "resource == 'projects/p1' && user == 'alice'";
        case "deepSelectors":
          return "request.auth.claims.email.endsWith('@example.com')"
              + " && request.resource.labels['env'] == 'prod'";
        case "macroPipeline":
          return "items.filter(i, i.score > 10).map(i, i.name).exists(n, n.startsWith('a'))";
        default:
          throw new IllegalArgumentException("Unknown compile benchmark expression: " + expression);
      }
    }
  }

  @Benchmark
  public void envCompileAndProgram(CompileState state, Blackhole blackhole) {
    Env env =
        newEnv(
            declarations(
                Decls.newVar("resource", Decls.String),
                Decls.newVar("user", Decls.String),
                Decls.newVar("request", Decls.Dyn),
                Decls.newVar("items", Decls.newListType(Decls.Dyn))));
    AstIssuesTuple ast = env.compile(state.source());
    if (ast.hasIssues()) {
      throw ast.getIssues().err();
    }
    blackhole.consume(env.program(ast.getAst()));
  }

  @Benchmark
  public void scriptHostBuild(CompileState state, Blackhole blackhole) throws Exception {
    ScriptHost host = ScriptHost.newBuilder().build();
    Script script =
        host.buildScript(state.source())
            .withDeclarations(
                Decls.newVar("resource", Decls.String),
                Decls.newVar("user", Decls.String),
                Decls.newVar("request", Decls.Dyn),
                Decls.newVar("items", Decls.newListType(Decls.Dyn)))
            .build();
    blackhole.consume(script);
  }

  @Benchmark
  public void protoRegistryCreation(Blackhole blackhole) {
    blackhole.consume(ProtoTypeRegistry.newRegistry());
  }
}
