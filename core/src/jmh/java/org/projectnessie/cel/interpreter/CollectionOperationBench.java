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
import static org.projectnessie.cel.common.types.IntT.intOf;

import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.HashMap;
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
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.EvalOption;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.MapT;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Mapper;

@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class CollectionOperationBench {
  private static final String TEST_ALL_TYPES = "cel.expr.conformance.proto3.TestAllTypes";

  @State(Scope.Benchmark)
  public static class DirectListState {
    @Param({"containsLast", "containsMiss", "equal", "iterate", "indexScan"})
    public String operation;

    @Param({"intArray", "longArray", "doubleArray", "javaList"})
    public String representation;

    @Param({"16", "1024"})
    public int size;

    Lister left;
    Val right;
    Val needle;

    @Setup
    public void init() {
      ProtoTypeRegistry adapter = ProtoTypeRegistry.newRegistry();
      Object leftNative = listValue(representation, size, 50_000);
      Object rightNative = listValue(representation, size, 50_000);
      left = (Lister) adapter.nativeToValue(leftNative);
      right = adapter.nativeToValue(rightNative);
      needle = intOf(operation.equals("containsMiss") ? -50_000 : 50_000L + size - 1);
      if (representation.equals("doubleArray")) {
        needle = adapter.nativeToValue(needle.intValue() * 1.0d);
      }
    }
  }

  @Benchmark
  public Val directList(DirectListState state) {
    return switch (state.operation) {
      case "containsLast", "containsMiss" -> state.left.contains(state.needle);
      case "equal" -> state.left.equal(state.right);
      case "iterate" -> consumeIterator(state.left.iterator());
      case "indexScan" -> consumeIndexed(state.left);
      default -> throw new IllegalArgumentException(state.operation);
    };
  }

  @State(Scope.Benchmark)
  public static class ListProgramState {
    @Param({"containsLast", "containsMiss", "equal", "existsLate"})
    public String operation;

    @Param({"intArray", "longArray", "javaList"})
    public String representation;

    @Param({"16", "1024"})
    public int size;

    Program program;
    Map<Object, Object> vars;

    @Setup
    public void init() {
      String expression;
      switch (operation) {
        case "containsLast", "containsMiss" -> expression = "target in items";
        case "equal" -> expression = "items == other";
        case "existsLate" -> expression = "items.exists(i, i == target)";
        default -> throw new IllegalArgumentException(operation);
      }
      program =
          program(
              expression,
              Decls.newVar("items", Decls.newListType(Decls.Int)),
              Decls.newVar("other", Decls.newListType(Decls.Int)),
              Decls.newVar("target", Decls.Int));
      long target = operation.equals("containsMiss") ? -50_000 : 50_000L + size - 1;
      vars =
          mapOf(
              "items",
              listValue(representation, size, 50_000),
              "other",
              listValue(representation, size, 50_000),
              "target",
              target);
    }
  }

  @Benchmark
  public Object listProgram(ListProgramState state) {
    return state.program.eval(state.vars);
  }

  @State(Scope.Benchmark)
  public static class DirectMapState {
    @Param({"adapt", "contains", "find", "size", "equal", "equalWrapped", "equalCross", "iterate"})
    public String operation;

    @Param({"16", "1024"})
    public int size;

    ProtoTypeRegistry adapter;
    Map<String, Long> nativeMap;
    Mapper map;
    Val other;
    Mapper wrappedMap;
    Val wrappedOther;
    Val key;

    @Setup
    public void init() {
      adapter = ProtoTypeRegistry.newRegistry();
      nativeMap = stringMap(size);
      map = (Mapper) adapter.nativeToValue(nativeMap);
      other = adapter.nativeToValue(stringMap(size));
      wrappedMap = wrappedStringMap(adapter, size);
      wrappedOther = wrappedStringMap(adapter, size);
      key = adapter.nativeToValue("key-" + (size - 1));
    }
  }

  @Benchmark
  public Val directMap(DirectMapState state) {
    return switch (state.operation) {
      case "adapt" -> state.adapter.nativeToValue(state.nativeMap);
      case "contains" -> state.map.contains(state.key);
      case "find" -> state.map.find(state.key);
      case "size" -> state.map.size();
      case "equal" -> state.map.equal(state.other);
      case "equalWrapped" -> state.wrappedMap.equal(state.wrappedOther);
      case "equalCross" -> state.map.equal(state.wrappedOther);
      case "iterate" -> consumeIterator(state.map.iterator());
      default -> throw new IllegalArgumentException(state.operation);
    };
  }

  @State(Scope.Benchmark)
  public static class MapProgramState {
    @Param({"contains", "size", "index", "equal"})
    public String operation;

    @Param({"16", "1024"})
    public int size;

    Program program;
    Map<Object, Object> vars;

    @Setup
    public void init() {
      String expression =
          switch (operation) {
            case "contains" -> "key in attrs";
            case "size" -> "size(attrs) == expectedSize";
            case "index" -> "attrs[key] == target";
            case "equal" -> "attrs == other";
            default -> throw new IllegalArgumentException(operation);
          };
      program =
          program(
              expression,
              Decls.newVar("attrs", Decls.newMapType(Decls.String, Decls.Int)),
              Decls.newVar("other", Decls.newMapType(Decls.String, Decls.Int)),
              Decls.newVar("key", Decls.String),
              Decls.newVar("target", Decls.Int),
              Decls.newVar("expectedSize", Decls.Int));
      vars =
          mapOf(
              "attrs",
              stringMap(size),
              "other",
              stringMap(size),
              "key",
              "key-" + (size - 1),
              "target",
              (long) size - 1,
              "expectedSize",
              (long) size);
    }
  }

  @Benchmark
  public Object mapProgram(MapProgramState state) {
    return state.program.eval(state.vars);
  }

  @State(Scope.Benchmark)
  public static class ProtoMapProgramState {
    @Param({"contains", "lookup", "lookupRepeated", "equal"})
    public String operation;

    @Param({"16", "1024"})
    public int size;

    Program program;
    Map<Object, Object> vars;

    @Setup
    public void init() {
      String expression =
          switch (operation) {
            case "contains" -> "key in msg.map_string_uint64";
            case "lookup" -> "msg.map_string_uint64[key] == target";
            case "lookupRepeated" ->
                "msg.map_string_uint64[key] == target && msg.map_string_uint64[key] == target";
            case "equal" -> "msg.map_string_uint64 == other.map_string_uint64";
            default -> throw new IllegalArgumentException(operation);
          };
      Env env =
          newEnv(
              types(TestAllTypes.getDefaultInstance()),
              declarations(
                  Decls.newVar("msg", Decls.newObjectType(TEST_ALL_TYPES)),
                  Decls.newVar("other", Decls.newObjectType(TEST_ALL_TYPES)),
                  Decls.newVar("key", Decls.String),
                  Decls.newVar("target", Decls.Uint)));
      program = program(env, expression);
      TestAllTypes message = protoMessage(size);
      vars =
          mapOf(
              "msg",
              message,
              "other",
              protoMessage(size),
              "key",
              "key-" + (size - 1),
              "target",
              (long) size - 1);
    }
  }

  @Benchmark
  public Object protoMapProgram(ProtoMapProgramState state) {
    return state.program.eval(state.vars);
  }

  private static Val consumeIterator(IteratorT iterator) {
    Val value = intOf(0);
    while (iterator.hasNext().booleanValue()) {
      value = iterator.next();
    }
    return value;
  }

  private static Val consumeIndexed(Lister list) {
    Val value = intOf(0);
    int size = list.nativeSize();
    for (int i = 0; i < size; i++) {
      value = list.nativeGetAt(i);
    }
    return value;
  }

  private static Object listValue(String representation, int size, int offset) {
    return switch (representation) {
      case "intArray" -> {
        int[] values = new int[size];
        for (int i = 0; i < size; i++) {
          values[i] = offset + i;
        }
        yield values;
      }
      case "longArray" -> {
        long[] values = new long[size];
        for (int i = 0; i < size; i++) {
          values[i] = offset + i;
        }
        yield values;
      }
      case "doubleArray" -> {
        double[] values = new double[size];
        for (int i = 0; i < size; i++) {
          values[i] = offset + i;
        }
        yield values;
      }
      case "javaList" -> {
        Long[] values = new Long[size];
        for (int i = 0; i < size; i++) {
          values[i] = (long) offset + i;
        }
        yield java.util.Arrays.asList(values);
      }
      default -> throw new IllegalArgumentException(representation);
    };
  }

  private static Map<String, Long> stringMap(int size) {
    Map<String, Long> values = new HashMap<>(size * 4 / 3 + 1);
    for (long i = 0; i < size; i++) {
      values.put("key-" + i, i);
    }
    return values;
  }

  private static Mapper wrappedStringMap(ProtoTypeRegistry adapter, int size) {
    Map<Val, Val> values = new HashMap<>(size * 4 / 3 + 1);
    for (long i = 0; i < size; i++) {
      values.put(adapter.nativeToValue("key-" + i), adapter.nativeToValue(i));
    }
    return (Mapper) MapT.newWrappedMap(adapter, values);
  }

  private static TestAllTypes protoMessage(int size) {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    for (int i = 0; i < size; i++) {
      builder.putMapStringUint64("key-" + i, i);
    }
    return builder.build();
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
}
