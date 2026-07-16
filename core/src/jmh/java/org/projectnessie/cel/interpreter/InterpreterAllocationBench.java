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
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.Util.mapOf;

import com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes;
import java.util.ArrayList;
import java.util.HashMap;
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
public class InterpreterAllocationBench {

  private static final String TEST_ALL_TYPES = "google.api.expr.test.v1.proto3.TestAllTypes";

  @State(Scope.Benchmark)
  public static class JavaInputState {
    @Param({"arrayListExistsEarly", "arrayListExistsLate", "intArrayExistsEarly", "mapLookup"})
    public String kind;

    @Param({"10", "1000"})
    public int size;

    Program program;
    Map<Object, Object> vars;

    @Setup
    public void init() {
      switch (kind) {
        case "arrayListExistsEarly":
          program =
              program(
                  "items.exists(i, i == target)",
                  Decls.newVar("items", listOfInt()),
                  Decls.newVar("target", Decls.Int));
          vars = mapOf("items", list(size), "target", 0L);
          return;
        case "arrayListExistsLate":
          program =
              program(
                  "items.exists(i, i == target)",
                  Decls.newVar("items", listOfInt()),
                  Decls.newVar("target", Decls.Int));
          vars = mapOf("items", list(size), "target", (long) size - 1);
          return;
        case "intArrayExistsEarly":
          program =
              program(
                  "items.exists(i, i == target)",
                  Decls.newVar("items", listOfInt()),
                  Decls.newVar("target", Decls.Int));
          vars = mapOf("items", intArray(size), "target", 0L);
          return;
        case "mapLookup":
          program =
              program(
                  "attrs[key] == target",
                  Decls.newVar("attrs", Decls.newMapType(Decls.String, Decls.Int)),
                  Decls.newVar("key", Decls.String),
                  Decls.newVar("target", Decls.Int));
          vars =
              mapOf("attrs", mapStringInt(size), "key", "key-" + (size - 1), "target", size - 1L);
          return;
        default:
          throw new IllegalArgumentException("Unknown interpreter benchmark kind: " + kind);
      }
    }
  }

  @Benchmark
  public void javaInput(JavaInputState state, Blackhole blackhole) {
    blackhole.consume(state.program.eval(state.vars));
  }

  @State(Scope.Benchmark)
  public static class DynamicMapLiteralState {
    Program program;
    Map<Object, Object> vars;

    @Setup
    public void init() {
      program =
          program(
              "{'a': a, 'b': b, 'c': c, 'd': d, 'e': e, 'f': f}",
              Decls.newVar("a", Decls.Int),
              Decls.newVar("b", Decls.Int),
              Decls.newVar("c", Decls.Int),
              Decls.newVar("d", Decls.Int),
              Decls.newVar("e", Decls.Int),
              Decls.newVar("f", Decls.Int));
      vars = mapOf("a", 1L, "b", 2L, "c", 3L, "d", 4L, "e", 5L, "f", 6L);
    }
  }

  @Benchmark
  public void dynamicMapLiteral(DynamicMapLiteralState state, Blackhole blackhole) {
    blackhole.consume(state.program.eval(state.vars));
  }

  @State(Scope.Benchmark)
  public static class ProtoInputState {
    @Param({"mapLookup", "repeatedUintExistsEarly", "repeatedUintExistsLate"})
    public String kind;

    @Param({"10", "1000"})
    public int size;

    Program program;
    Map<Object, Object> vars;

    @Setup
    public void init() {
      switch (kind) {
        case "mapLookup":
          program =
              protoProgram(
                  "msg.map_string_uint64[key] == target",
                  Decls.newVar("key", Decls.String),
                  Decls.newVar("target", Decls.Uint));
          vars =
              mapOf(
                  "msg", protoMessage(size), "key", "key-" + (size - 1), "target", (long) size - 1);
          return;
        case "repeatedUintExistsEarly":
          program =
              protoProgram(
                  "msg.repeated_uint32.exists(x, x == target)", Decls.newVar("target", Decls.Uint));
          vars = mapOf("msg", protoMessage(size), "target", 0L);
          return;
        case "repeatedUintExistsLate":
          program =
              protoProgram(
                  "msg.repeated_uint32.exists(x, x == target)", Decls.newVar("target", Decls.Uint));
          vars = mapOf("msg", protoMessage(size), "target", (long) size - 1);
          return;
        default:
          throw new IllegalArgumentException("Unknown protobuf benchmark kind: " + kind);
      }
    }
  }

  @Benchmark
  public void protoInput(ProtoInputState state, Blackhole blackhole) {
    blackhole.consume(state.program.eval(state.vars));
  }

  private static Program protoProgram(
      String expression, com.google.api.expr.v1alpha1.Decl... decls) {
    com.google.api.expr.v1alpha1.Decl[] allDecls =
        new com.google.api.expr.v1alpha1.Decl[decls.length + 1];
    allDecls[0] = Decls.newVar("msg", Decls.newObjectType(TEST_ALL_TYPES));
    System.arraycopy(decls, 0, allDecls, 1, decls.length);
    Env env = newEnv(types(TestAllTypes.getDefaultInstance()), declarations(allDecls));
    return program(env, expression);
  }

  private static Program program(String expression, com.google.api.expr.v1alpha1.Decl... decls) {
    return program(newEnv(declarations(decls)), expression);
  }

  private static Program program(Env env, String expression) {
    AstIssuesTuple ast = env.compile(expression);
    if (ast.hasIssues()) {
      throw ast.getIssues().err();
    }
    return env.program(ast.getAst(), evalOptions(EvalOption.OptOptimize));
  }

  private static com.google.api.expr.v1alpha1.Type listOfInt() {
    return Decls.newListType(Decls.Int);
  }

  private static List<Long> list(int size) {
    List<Long> values = new ArrayList<>(size);
    for (long i = 0; i < size; i++) {
      values.add(i);
    }
    return values;
  }

  private static int[] intArray(int size) {
    int[] values = new int[size];
    for (int i = 0; i < size; i++) {
      values[i] = i;
    }
    return values;
  }

  private static Map<String, Long> mapStringInt(int size) {
    Map<String, Long> values = new HashMap<>(size * 4 / 3 + 1);
    for (long i = 0; i < size; i++) {
      values.put("key-" + i, i);
    }
    return values;
  }

  private static TestAllTypes protoMessage(int size) {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    for (int i = 0; i < size; i++) {
      builder.putMapStringUint64("key-" + i, i);
      builder.addRepeatedUint32(i);
    }
    return builder.build();
  }
}
