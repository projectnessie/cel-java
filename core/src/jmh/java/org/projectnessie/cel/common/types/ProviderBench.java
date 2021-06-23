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

import static org.projectnessie.cel.Util.mapOf;
import static org.projectnessie.cel.common.types.ListT.newGenericArrayList;
import static org.projectnessie.cel.common.types.MapT.newMaybeWrappedMap;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newRegistry;

import com.google.api.expr.v1alpha1.ParsedExpr;
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
import org.projectnessie.cel.common.types.ref.TypeRegistry;

@Warmup(iterations = 1, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ProviderBench {

  @State(Scope.Benchmark)
  public static class NativeToValueState {
    @Param({"v_true", "v_int64", "v_stringT"})
    public ProviderBenchNativeToValue value;

    TypeRegistry reg;

    @Setup
    public void init() {
      reg = newRegistry();
    }
  }

  @Benchmark
  public void nativeToValue(NativeToValueState val) {
    TypeRegistry reg = newRegistry();

    Object in = val.value.value();

    reg.nativeToValue(in);
  }

  @State(Scope.Benchmark)
  public static class NewValueState {
    TypeRegistry reg = newRegistry(ParsedExpr.getDefaultInstance());
  }

  @Benchmark
  public void newValue(NewValueState state) {
    TypeRegistry reg = state.reg;
    reg.newValue(
        "google.api.expr.v1.SourceInfo",
        mapOf(
            "Location", stringOf("BenchmarkTypeProvider_NewValue"),
            "LineOffsets", newGenericArrayList(reg, new Object[] {0L, 2L}),
            "Positions", newMaybeWrappedMap(reg, mapOf(1L, 2L, 2L, 4L))));
  }
}
