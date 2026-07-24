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
package org.projectnessie.cel;

import static org.projectnessie.cel.Env.newCustomEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;

import com.google.protobuf.DynamicMessage;
import dev.cel.expr.conformance.proto3.TestAllTypes;
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
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.TypeRegistry;

/** End-to-end exact protobuf repeated-field and map consumer benchmark. */
@Warmup(iterations = 2, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ExactProtoAggregateBench {
  private static final String TYPE = TestAllTypes.getDescriptor().getFullName();

  @State(Scope.Benchmark)
  public static class ExactProtoState {
    @Param({"generated", "dynamic"})
    public String representation;

    @Param({"repeatedSize", "repeatedIndex", "mapSize", "mapHit", "mapMiss", "mapMembership"})
    public String operation;

    @Param({"exactNative", "exactDisabled", "default", "direct"})
    public String mode;

    @Param({"1", "16", "1024"})
    public int size;

    Program program;
    Map<String, Object> input;
    Object message;
    String key;
    List<?> directRepeated;
    Map<?, ?> directMap;

    @Setup
    public void setup() throws Exception {
      TestAllTypes.Builder builder = TestAllTypes.newBuilder();
      for (int i = 0; i < size; i++) {
        builder.addRepeatedInt64(i);
        builder.putMapStringInt64("key-" + i, i);
      }
      TestAllTypes generated = builder.build();
      message =
          representation.equals("generated")
              ? generated
              : DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), generated.toByteString());
      key = "key-" + (size - 1);
      input = Map.of("msg", message, "index", (long) size - 1, "key", key);
      TypeRegistry registry =
          mode.equals("default")
              ? ProtoTypeRegistry.newRegistry(TestAllTypes.getDefaultInstance())
              : ProtoTypeRegistry.newExactAggregateRegistry(TestAllTypes.getDefaultInstance());
      if (mode.equals("direct")) {
        directRepeated =
            (List<?>) registry.findFieldType(TYPE, "repeated_int64").getFrom.getFrom(message);
        directMap =
            (Map<?, ?>) registry.findFieldType(TYPE, "map_string_int64").getFrom.getFrom(message);
      } else {
        Env env =
            newCustomEnv(
                registry,
                List.of(
                    Library.StdLib(),
                    declarations(
                        Decls.newVar("msg", Decls.newObjectType(TYPE)),
                        Decls.newVar("index", Decls.Int),
                        Decls.newVar("key", Decls.String))));
        Ast ast = compile(env, expression(operation));
        program =
            mode.equals("exactDisabled")
                ? env.program(ast, evalOptions(OptDisableNativeEval))
                : env.program(ast);
      }
    }
  }

  @Benchmark
  public Object evaluate(ExactProtoState state) {
    if (!state.mode.equals("direct")) {
      return state.program.eval(state.input);
    }
    return switch (state.operation) {
      case "repeatedSize" -> state.directRepeated.size();
      case "repeatedIndex" -> state.directRepeated.get(state.size - 1);
      case "mapSize" -> state.directMap.size();
      case "mapHit" -> state.directMap.get(state.key);
      case "mapMiss" -> state.directMap.get("missing");
      case "mapMembership" -> state.directMap.containsKey(state.key);
      default -> throw new IllegalArgumentException(state.operation);
    };
  }

  @State(Scope.Benchmark)
  public static class RepresentationState {
    @Param({"generated", "dynamic"})
    public String representation;

    @Param({"bool", "int32", "int64", "uint32", "uint64", "float", "double", "string"})
    public String family;

    @Param({"size", "lateIndex"})
    public String operation;

    @Param({"exactNative", "exactDisabled", "default", "direct"})
    public String mode;

    @Param({"1", "1024"})
    public int size;

    Program program;
    Map<String, Object> input;
    List<?> direct;

    @Setup
    public void setup() throws Exception {
      TestAllTypes.Builder builder = TestAllTypes.newBuilder();
      for (int i = 0; i < size; i++) {
        switch (family) {
          case "bool" -> builder.addRepeatedBool((i & 1) == 0);
          case "int32" -> builder.addRepeatedInt32(i);
          case "int64" -> builder.addRepeatedInt64(i);
          case "uint32" -> builder.addRepeatedUint32(i);
          case "uint64" -> builder.addRepeatedUint64(i);
          case "float" -> builder.addRepeatedFloat(i);
          case "double" -> builder.addRepeatedDouble(i);
          case "string" -> builder.addRepeatedString("value-" + i);
          default -> throw new IllegalArgumentException(family);
        }
      }
      TestAllTypes generated = builder.build();
      Object message =
          representation.equals("generated")
              ? generated
              : DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), generated.toByteString());
      String fieldName = "repeated_" + family;
      input = Map.of("msg", message, "index", (long) size - 1);
      TypeRegistry registry =
          mode.equals("default")
              ? ProtoTypeRegistry.newRegistry(TestAllTypes.getDefaultInstance())
              : ProtoTypeRegistry.newExactAggregateRegistry(TestAllTypes.getDefaultInstance());
      if (mode.equals("direct")) {
        direct = (List<?>) registry.findFieldType(TYPE, fieldName).getFrom.getFrom(message);
      } else {
        Env env =
            newCustomEnv(
                registry,
                List.of(
                    Library.StdLib(),
                    declarations(
                        Decls.newVar("msg", Decls.newObjectType(TYPE)),
                        Decls.newVar("index", Decls.Int))));
        String expression =
            operation.equals("size")
                ? "size(msg." + fieldName + ")"
                : "msg." + fieldName + "[index]";
        Ast ast = compile(env, expression);
        program =
            mode.equals("exactDisabled")
                ? env.program(ast, evalOptions(OptDisableNativeEval))
                : env.program(ast);
      }
    }
  }

  @Benchmark
  public Object representationFamily(RepresentationState state) {
    if (!state.mode.equals("direct")) {
      return state.program.eval(state.input);
    }
    return state.operation.equals("size") ? state.direct.size() : state.direct.get(state.size - 1);
  }

  private static String expression(String operation) {
    return switch (operation) {
      case "repeatedSize" -> "size(msg.repeated_int64)";
      case "repeatedIndex" -> "msg.repeated_int64[index]";
      case "mapSize" -> "size(msg.map_string_int64)";
      case "mapHit" -> "msg.map_string_int64[key]";
      case "mapMiss" -> "msg.map_string_int64['missing']";
      case "mapMembership" -> "key in msg.map_string_int64";
      default -> throw new IllegalArgumentException(operation);
    };
  }

  private static Ast compile(Env env, String expression) {
    var compiled = env.compile(expression);
    if (compiled.hasIssues()) {
      throw new IllegalStateException(compiled.getIssues().toString());
    }
    return compiled.getAst();
  }
}
