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
package org.projectnessie.cel.common.types;

import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newRegistry;

import com.google.protobuf.ListValue;
import com.google.protobuf.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;

@Warmup(iterations = 1, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class AdapterAllocationBench {

  @State(Scope.Benchmark)
  public static class NativeValueState {
    @Param({
      "arrayList",
      "linkedHashSet",
      "objectArray",
      "stringArray",
      "intArray",
      "longArray",
      "doubleArray",
      "mapStringInt",
      "mapValVal",
      "listValue"
    })
    public String kind;

    @Param({"10", "1000"})
    public int size;

    TypeRegistry registry;
    Object value;

    @Setup
    public void init() {
      registry = newRegistry();
      value = value(kind, size);
    }
  }

  @Benchmark
  public void nativeToValue(NativeValueState state, Blackhole blackhole) {
    blackhole.consume(state.registry.nativeToValue(state.value));
  }

  private static Object value(String kind, int size) {
    switch (kind) {
      case "arrayList":
        return list(size);
      case "linkedHashSet":
        return set(size);
      case "objectArray":
        return list(size).toArray();
      case "stringArray":
        return stringArray(size);
      case "intArray":
        return intArray(size);
      case "longArray":
        return longArray(size);
      case "doubleArray":
        return doubleArray(size);
      case "mapStringInt":
        return mapStringInt(size);
      case "mapValVal":
        return mapValVal(size);
      case "listValue":
        return listValue(size);
      default:
        throw new IllegalArgumentException("Unknown native value kind: " + kind);
    }
  }

  private static List<Long> list(int size) {
    List<Long> values = new ArrayList<>(size);
    for (long i = 0; i < size; i++) {
      values.add(i);
    }
    return values;
  }

  private static Set<Long> set(int size) {
    Set<Long> values = new LinkedHashSet<>(size * 4 / 3 + 1);
    for (long i = 0; i < size; i++) {
      values.add(i);
    }
    return values;
  }

  private static String[] stringArray(int size) {
    String[] values = new String[size];
    for (int i = 0; i < size; i++) {
      values[i] = "value-" + i;
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

  private static long[] longArray(int size) {
    long[] values = new long[size];
    for (int i = 0; i < size; i++) {
      values[i] = i;
    }
    return values;
  }

  private static double[] doubleArray(int size) {
    double[] values = new double[size];
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

  private static Map<Val, Val> mapValVal(int size) {
    Map<Val, Val> values = new HashMap<>(size * 4 / 3 + 1);
    for (long i = 0; i < size; i++) {
      values.put(stringOf("key-" + i), intOf(i));
    }
    return values;
  }

  private static ListValue listValue(int size) {
    ListValue.Builder values = ListValue.newBuilder();
    for (int i = 0; i < size; i++) {
      values.addValues(Value.newBuilder().setNumberValue(i).build());
    }
    return values.build();
  }
}
