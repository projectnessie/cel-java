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
package org.projectnessie.cel.tools;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.expr.v1alpha1.Decl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.RegexEngine;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.IntT;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.interpreter.functions.Overload;
import org.projectnessie.cel.toolstests.Dummy;

class ScriptCompilerTest {

  @Test
  void configuresOnceAndCompilesMultipleSources() throws Exception {
    ScriptCompiler compiler =
        ScriptCompiler.newBuilder()
            .withDeclarations(Decls.newVar("x", Decls.String), Decls.newVar("y", Decls.String))
            .build();

    Script concatenate = compiler.compile("x + ' ' + y");
    Script compare = compiler.compile("x == y");

    assertThat(concatenate.execute(String.class, Map.of("x", "hello", "y", "world")))
        .isEqualTo("hello world");
    assertThat(compare.execute(Boolean.class, Map.of("x", "same", "y", "same"))).isTrue();
  }

  @Test
  void snapshotsRegistryAndConfigurationInputs() throws Exception {
    ProtoTypeRegistry supplied = ProtoTypeRegistry.newEmptyRegistry();
    List<Decl> declarations = new ArrayList<>();
    declarations.add(
        Decls.newVar("inp", Decls.newObjectType(Dummy.MyPojo.getDescriptor().getFullName())));
    List<Object> types = new ArrayList<>();
    types.add(Dummy.MyPojo.getDefaultInstance());

    ScriptCompiler.Builder builder =
        ScriptCompiler.newBuilder()
            .registry(supplied)
            .withContainer("org.projectnessie.cel.toolstests")
            .withDeclarations(declarations)
            .withTypes(types);
    ScriptCompiler compiler = builder.build();

    declarations.clear();
    types.clear();
    builder.withDeclarations(Decls.newVar("unrelated", Decls.Int));

    String messageType = Dummy.MyPojo.getDescriptor().getFullName();
    assertThat(supplied.findType(messageType)).isNull();

    Script script = compiler.compile("inp.Property1");
    assertThat(
            script.execute(
                String.class,
                Map.of("inp", Dummy.MyPojo.newBuilder().setProperty1("value").build())))
        .isEqualTo("value");
    assertThat(
            compiler
                .compile("MyPojo{Property1: 'from container'}.Property1")
                .execute(String.class, Map.of()))
        .isEqualTo("from container");
  }

  @Test
  void supportsGeneralAndExactProtobufRegistries() throws Exception {
    for (TypeRegistry registry :
        List.of(ProtoTypeRegistry.newRegistry(), ProtoTypeRegistry.newExactAggregateRegistry())) {
      ScriptCompiler compiler =
          ScriptCompiler.newBuilder()
              .registry(registry)
              .withDeclarations(
                  Decls.newVar(
                      "inp", Decls.newObjectType(Dummy.MyPojo.getDescriptor().getFullName())))
              .withTypes(Dummy.MyPojo.getDefaultInstance())
              .build();

      Script script = compiler.compile("inp.Property1");
      assertThat(
              script.execute(
                  String.class,
                  Map.of("inp", Dummy.MyPojo.newBuilder().setProperty1("value").build())))
          .isEqualTo("value");
    }
  }

  @Test
  void appliesLibrariesAndOptimizationPolicy() throws Exception {
    AtomicInteger optimizedCalls = new AtomicInteger();
    ScriptCompiler optimized =
        ScriptCompiler.newBuilder()
            .withLibraries(new CountingStringToIntLibrary(optimizedCalls))
            .build();
    Script optimizedScript = optimized.compile("int('ignored')");

    assertThat(optimizedCalls).hasValue(1);
    assertThat(optimizedScript.execute(Integer.class, Map.of())).isEqualTo(42);
    assertThat(optimizedScript.execute(Integer.class, Map.of())).isEqualTo(42);
    assertThat(optimizedCalls).hasValue(1);

    AtomicInteger unoptimizedCalls = new AtomicInteger();
    ScriptCompiler unoptimized =
        ScriptCompiler.newBuilder()
            .disableOptimize()
            .withLibraries(new CountingStringToIntLibrary(unoptimizedCalls))
            .build();
    Script unoptimizedScript = unoptimized.compile("int('ignored')");

    assertThat(unoptimizedCalls).hasValue(0);
    assertThat(unoptimizedScript.execute(Integer.class, Map.of())).isEqualTo(42);
    assertThat(unoptimizedScript.execute(Integer.class, Map.of())).isEqualTo(42);
    assertThat(unoptimizedCalls).hasValue(2);
  }

  @Test
  void appliesRegexEngineToEveryCompiledSource() throws Exception {
    ScriptCompiler javaCompiler = ScriptCompiler.newBuilder().build();
    assertThat(javaCompiler.compile("'ab'.matches('a(?=b)')").execute(Boolean.class, Map.of()))
        .isTrue();

    ScriptCompiler re2Compiler = ScriptCompiler.newBuilder().regexEngine(RegexEngine.RE2).build();
    assertThat(re2Compiler.compile("'abc'.matches('b')").execute(Boolean.class, Map.of())).isTrue();
    Script javaOnly = re2Compiler.compile("'ab'.matches('a(?=b)')");
    assertThatThrownBy(() -> javaOnly.execute(Boolean.class, Map.of()))
        .isInstanceOf(ScriptExecutionException.class);
  }

  @Test
  void reportsInvalidSource() {
    ScriptCompiler compiler = ScriptCompiler.newBuilder().build();

    assertThatThrownBy(() -> compiler.compile(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("sourceText");
    assertThatThrownBy(() -> compiler.compile(" \t"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No source code.");
    assertThatThrownBy(() -> compiler.compile("-.,"))
        .isInstanceOf(ScriptCreateException.class)
        .hasMessageStartingWith("parse failed:");
    assertThatThrownBy(() -> compiler.compile("missing"))
        .isInstanceOf(ScriptCreateException.class)
        .hasMessageStartingWith("check failed:");
  }

  @Test
  void compilesConcurrentlyAfterConstruction() throws Exception {
    ScriptCompiler compiler =
        ScriptCompiler.newBuilder().withDeclarations(Decls.newVar("value", Decls.Int)).build();
    List<Callable<Long>> tasks = new ArrayList<>();
    for (int i = 0; i < 24; i++) {
      int increment = i;
      tasks.add(
          () -> compiler.compile("value + " + increment).execute(Long.class, Map.of("value", 100)));
    }

    ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      List<Future<Long>> results = executor.invokeAll(tasks, 30, SECONDS);
      for (int i = 0; i < results.size(); i++) {
        assertThat(results.get(i).isCancelled()).isFalse();
        assertThat(results.get(i).get()).isEqualTo(100L + i);
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(30, SECONDS)).isTrue();
    }
  }

  private static final class CountingStringToIntLibrary implements Library {
    private final AtomicInteger calls;

    private CountingStringToIntLibrary(AtomicInteger calls) {
      this.calls = calls;
    }

    @Override
    public List<EnvOption> getCompileOptions() {
      return Collections.emptyList();
    }

    @Override
    public List<ProgramOption> getProgramOptions() {
      return Collections.singletonList(
          ProgramOption.functions(
              Overload.unary(
                  Overloads.StringToInt,
                  ignored -> {
                    calls.incrementAndGet();
                    return IntT.intOf(42);
                  })));
    }
  }
}
