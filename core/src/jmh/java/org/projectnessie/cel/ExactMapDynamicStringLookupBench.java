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
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;

import com.google.protobuf.DynamicMessage;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.LinkedHashMap;
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
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;

/** End-to-end checked dynamic string-key lookup over exact activation and protobuf maps. */
@Warmup(iterations = 2, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ExactMapDynamicStringLookupBench {
  private static final String TYPE = TestAllTypes.getDescriptor().getFullName();

  @State(Scope.Benchmark)
  public static class LookupState {
    @Param({"activation", "generated", "dynamic"})
    public String source;

    @Param({"constant", "variable", "computed"})
    public String keyShape;

    @Param({"first", "middle", "last", "miss"})
    public String position;

    @Param({"exactNative", "exactDisabled", "general", "direct"})
    public String mode;

    @Param({"1", "16", "1024"})
    public int size;

    Program program;
    Map<String, Object> input;
    Map<String, Long> values;
    Map<?, ?> directMap;
    String key;

    @Setup
    public void setup() throws Exception {
      values = new LinkedHashMap<>();
      for (long i = 0; i < size; i++) {
        values.put("key-" + i, i);
      }
      key =
          switch (position) {
            case "first" -> "key-0";
            case "middle" -> "key-" + (size / 2);
            case "last" -> "key-" + (size - 1);
            case "miss" -> "missing";
            default -> throw new IllegalArgumentException(position);
          };
      int split = key.length() / 2;
      String prefix = key.substring(0, split);
      String suffix = key.substring(split);
      String index =
          switch (keyShape) {
            case "constant" -> "'" + key + "'";
            case "variable" -> "key";
            case "computed" -> "prefix + suffix";
            default -> throw new IllegalArgumentException(keyShape);
          };
      if (source.equals("activation")) {
        directMap = values;
        input = Map.of("values", values, "key", key, "prefix", prefix, "suffix", suffix);
        if (!mode.equals("direct")) {
          Env env =
              newEnv(
                  customTypeAdapter(
                      mode.equals("general") ? DefaultTypeAdapter.Instance : new ExactAdapter()),
                  declarations(
                      Decls.newVar("values", Decls.newMapType(Decls.String, Decls.Int)),
                      Decls.newVar("key", Decls.String),
                      Decls.newVar("prefix", Decls.String),
                      Decls.newVar("suffix", Decls.String)));
          Ast ast = compile(env, "values[" + index + "]");
          program =
              mode.equals("exactDisabled")
                  ? env.program(ast, evalOptions(OptDisableNativeEval))
                  : env.program(ast);
        }
        return;
      }

      TestAllTypes.Builder builder = TestAllTypes.newBuilder();
      values.forEach(builder::putMapStringInt64);
      TestAllTypes generated = builder.build();
      Object message =
          source.equals("generated")
              ? generated
              : DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), generated.toByteString());
      input = Map.of("msg", message, "key", key, "prefix", prefix, "suffix", suffix);
      TypeRegistry registry =
          mode.equals("general")
              ? ProtoTypeRegistry.newRegistry(TestAllTypes.getDefaultInstance())
              : ProtoTypeRegistry.newExactAggregateRegistry(TestAllTypes.getDefaultInstance());
      if (mode.equals("direct")) {
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
                        Decls.newVar("key", Decls.String),
                        Decls.newVar("prefix", Decls.String),
                        Decls.newVar("suffix", Decls.String))));
        Ast ast = compile(env, "msg.map_string_int64[" + index + "]");
        program =
            mode.equals("exactDisabled")
                ? env.program(ast, evalOptions(OptDisableNativeEval))
                : env.program(ast);
      }
    }
  }

  @Benchmark
  public Object lookup(LookupState state) {
    return state.mode.equals("direct")
        ? state.directMap.get(state.key)
        : state.program.eval(state.input);
  }

  private static Ast compile(Env env, String expression) {
    var compiled = env.compile(expression);
    if (compiled.hasIssues()) {
      throw new IllegalStateException(compiled.getIssues().toString());
    }
    return compiled.getAst();
  }

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }
}
