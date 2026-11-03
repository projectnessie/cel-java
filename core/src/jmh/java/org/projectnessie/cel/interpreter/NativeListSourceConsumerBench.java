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
package org.projectnessie.cel.interpreter;

import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
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
import org.projectnessie.cel.Ast;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

/**
 * End-to-end benchmark for exact list-source consumers.
 *
 * <p>The cases deliberately pair each consumer with two representative host shapes instead of
 * taking the full consumer-by-representation Cartesian product. Index cases use a conditional at
 * size zero so all configured sizes remain semantically comparable without benchmarking an
 * exception path.
 */
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class NativeListSourceConsumerBench {
  @State(Scope.Benchmark)
  public static class ConsumerState {
    @Param({
      "arraySize",
      "setSize",
      "arrayConstantIndex",
      "listConstantIndex",
      "collectionDynamicIndex",
      "setDynamicIndex",
      "arrayExists",
      "setExists"
    })
    public String consumer;

    @Param({"0", "1", "16", "1024"})
    public int size;

    Program exactNative;
    Program exactNativeDisabled;
    Program general;
    Map<String, Object> variables;
    Object values;
    int index;
    long needle;

    @Setup
    public void setup() {
      String expression =
          switch (consumer) {
            case "arraySize", "setSize" -> "size(values)";
            case "arrayConstantIndex", "listConstantIndex" -> "size(values) == 0 ? -1 : values[0]";
            case "collectionDynamicIndex", "setDynamicIndex" ->
                "size(values) == 0 ? -1 : values[index]";
            case "arrayExists", "setExists" -> "values.exists(value, value == needle)";
            default -> throw new IllegalArgumentException("unknown consumer " + consumer);
          };
      values = values(representation(consumer), size);
      index = size == 0 ? 0 : size - 1;
      needle = size == 0 ? -1L : size - 1L;
      variables = Map.of("values", values, "index", (long) index, "needle", needle);

      Env exactEnv =
          newEnv(
              customTypeAdapter(new ExactAdapter()),
              declarations(
                  Decls.newVar("values", Decls.newListType(Decls.Int)),
                  Decls.newVar("index", Decls.Int),
                  Decls.newVar("needle", Decls.Int)));
      Env generalEnv =
          newEnv(
              declarations(
                  Decls.newVar("values", Decls.newListType(Decls.Int)),
                  Decls.newVar("index", Decls.Int),
                  Decls.newVar("needle", Decls.Int)));
      Ast exactAst = compile(exactEnv, expression);
      exactNative = exactEnv.program(exactAst);
      exactNativeDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));
      general = generalEnv.program(compile(generalEnv, expression));
    }
  }

  @Benchmark
  public Object exactNative(ConsumerState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object exactNativeDisabled(ConsumerState state) {
    return state.exactNativeDisabled.eval(state.variables);
  }

  @Benchmark
  public Object generalAdapter(ConsumerState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public Object javaCeiling(ConsumerState state) {
    return switch (state.consumer) {
      case "arraySize", "setSize" -> state.size;
      case "arrayConstantIndex", "listConstantIndex" ->
          state.size == 0 ? -1L : valueAt(state.values, 0);
      case "collectionDynamicIndex", "setDynamicIndex" ->
          state.size == 0 ? -1L : valueAt(state.values, state.index);
      case "arrayExists", "setExists" -> anyMatches(state.values, state.needle);
      default -> throw new IllegalArgumentException("unknown consumer " + state.consumer);
    };
  }

  private static Ast compile(Env env, String expression) {
    var compiled = env.compile(expression);
    if (compiled.hasIssues()) {
      throw new IllegalStateException(compiled.getIssues().toString());
    }
    return compiled.getAst();
  }

  private static String representation(String consumer) {
    if (consumer.startsWith("array")) {
      return "array";
    }
    if (consumer.startsWith("list")) {
      return "list";
    }
    if (consumer.startsWith("collection")) {
      return "collection";
    }
    return "set";
  }

  private static Object values(String representation, int size) {
    return switch (representation) {
      case "array" -> {
        long[] values = new long[size];
        for (int i = 0; i < size; i++) {
          values[i] = i;
        }
        yield values;
      }
      case "list" -> {
        List<Long> values = new ArrayList<>(size);
        addValues(values, size);
        yield values;
      }
      case "collection" -> {
        Collection<Long> values = new ArrayDeque<>(size);
        addValues(values, size);
        yield values;
      }
      case "set" -> {
        Collection<Long> values = new LinkedHashSet<>(hashCapacity(size));
        addValues(values, size);
        yield values;
      }
      default -> throw new IllegalArgumentException("unknown representation " + representation);
    };
  }

  private static void addValues(Collection<Long> values, int size) {
    for (long i = 0; i < size; i++) {
      values.add(i);
    }
  }

  private static long valueAt(Object values, int index) {
    if (values instanceof long[] array) {
      return array[index];
    }
    if (values instanceof List<?> list) {
      return (Long) list.get(index);
    }
    Iterator<?> iterator = ((Collection<?>) values).iterator();
    for (int current = 0; iterator.hasNext(); current++) {
      Object value = iterator.next();
      if (current == index) {
        return (Long) value;
      }
    }
    throw new AssertionError("index out of range");
  }

  private static boolean anyMatches(Object values, long needle) {
    if (values instanceof long[] array) {
      for (long value : array) {
        if (value == needle) {
          return true;
        }
      }
      return false;
    }
    for (Object value : (Collection<?>) values) {
      if (((Long) value) == needle) {
        return true;
      }
    }
    return false;
  }

  private static int hashCapacity(int size) {
    return Math.max(16, size * 4 / 3 + 1);
  }

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }
}
