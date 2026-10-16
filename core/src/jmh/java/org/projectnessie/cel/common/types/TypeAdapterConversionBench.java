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

import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newRegistry;

import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.protobuf.Any;
import com.google.protobuf.Value;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;

@Warmup(iterations = 1, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@SuppressWarnings({"deprecation", "removal"})
public class TypeAdapterConversionBench {

  @State(Scope.Thread)
  public static class ConversionState {
    TypeRegistry registry;

    boolean primitiveBoolean;
    long primitiveLong;
    double primitiveDouble;

    Object nativeBoolean;
    Object nativeLong;
    Object nativeDouble;
    Object nativeString;
    Object nativeList;
    Object nativeMap;
    Object nativeMessage;

    BoolT boolValue;
    IntT intValue;
    UintT uintValue;
    DoubleT doubleValue;
    Val stringValue;
    Val listValue;
    Val mapValue;
    Val messageValue;

    @Setup
    public void init() {
      ParsedExpr parsedExpr = ParsedExpr.getDefaultInstance();
      registry = newRegistry(parsedExpr);

      primitiveBoolean = true;
      primitiveLong = 42L;
      primitiveDouble = 42.5d;

      nativeBoolean = Boolean.TRUE;
      nativeLong = Long.valueOf(42L);
      nativeDouble = Double.valueOf(42.5d);
      nativeString = "forty-two";
      nativeList = List.of(1L, 2L, 3L);
      nativeMap = Map.of("one", 1L, "two", 2L);
      nativeMessage = parsedExpr;

      boolValue = BoolT.True;
      intValue = intOf(42L);
      uintValue = uintOf(42L);
      doubleValue = doubleOf(42.5d);
      stringValue = stringOf("forty-two");
      listValue = registry.nativeToValue(nativeList);
      mapValue = registry.nativeToValue(nativeMap);
      messageValue = registry.nativeToValue(nativeMessage);
    }
  }

  @Benchmark
  public Val boxedNativeToValueBoolean(ConversionState state) {
    return state.registry.nativeToValue(state.nativeBoolean);
  }

  @Benchmark
  public Val boxedNativeToValueLong(ConversionState state) {
    return state.registry.nativeToValue(state.nativeLong);
  }

  @Benchmark
  public Val boxedNativeToValueDouble(ConversionState state) {
    return state.registry.nativeToValue(state.nativeDouble);
  }

  @Benchmark
  public Val boxedNativeToValueString(ConversionState state) {
    return state.registry.nativeToValue(state.nativeString);
  }

  @Benchmark
  public Val boxedNativeToValueList(ConversionState state) {
    return state.registry.nativeToValue(state.nativeList);
  }

  @Benchmark
  public Val boxedNativeToValueMap(ConversionState state) {
    return state.registry.nativeToValue(state.nativeMap);
  }

  @Benchmark
  public Val boxedNativeToValueMessage(ConversionState state) {
    return state.registry.nativeToValue(state.nativeMessage);
  }

  @Benchmark
  public Val primitiveNativeToValueBoolean(ConversionState state) {
    return state.registry.nativeToValue(state.primitiveBoolean);
  }

  @Benchmark
  public Val primitiveNativeToValueLong(ConversionState state) {
    return state.registry.nativeToValue(state.primitiveLong);
  }

  @Benchmark
  public Val primitiveNativeToValueDouble(ConversionState state) {
    return state.registry.nativeToValue(state.primitiveDouble);
  }

  @Benchmark
  public Boolean directConvertBoolToBoolean(ConversionState state) {
    return state.boolValue.convertToNative(Boolean.class);
  }

  @Benchmark
  public Long directConvertIntToLong(ConversionState state) {
    return state.intValue.convertToNative(Long.class);
  }

  @Benchmark
  public Long directConvertUintToLong(ConversionState state) {
    return state.uintValue.convertToNative(Long.class);
  }

  @Benchmark
  public Double directConvertDoubleToDouble(ConversionState state) {
    return state.doubleValue.convertToNative(Double.class);
  }

  @Benchmark
  public String directConvertStringToString(ConversionState state) {
    return state.stringValue.convertToNative(String.class);
  }

  @Benchmark
  public List<?> directConvertListToList(ConversionState state) {
    return state.listValue.convertToNative(List.class);
  }

  @Benchmark
  public Map<?, ?> directConvertMapToMap(ConversionState state) {
    return state.mapValue.convertToNative(Map.class);
  }

  @Benchmark
  public Value directConvertBoolToProtoValue(ConversionState state) {
    return state.boolValue.convertToNative(Value.class);
  }

  @Benchmark
  public Any directConvertIntToAny(ConversionState state) {
    return state.intValue.convertToNative(Any.class);
  }

  @Benchmark
  public ParsedExpr directConvertMessageToGenerated(ConversionState state) {
    return state.messageValue.convertToNative(ParsedExpr.class);
  }

  @Benchmark
  public Boolean adapterConvertBoolToBoolean(ConversionState state) {
    return state.registry.valueToNative(state.boolValue, Boolean.class);
  }

  @Benchmark
  public Long adapterConvertIntToLong(ConversionState state) {
    return state.registry.valueToNative(state.intValue, Long.class);
  }

  @Benchmark
  public Double adapterConvertDoubleToDouble(ConversionState state) {
    return state.registry.valueToNative(state.doubleValue, Double.class);
  }

  @Benchmark
  public String adapterConvertStringToString(ConversionState state) {
    return state.registry.valueToNative(state.stringValue, String.class);
  }

  @Benchmark
  public List<?> adapterConvertListToList(ConversionState state) {
    return state.registry.valueToNative(state.listValue, List.class);
  }

  @Benchmark
  public Map<?, ?> adapterConvertMapToMap(ConversionState state) {
    return state.registry.valueToNative(state.mapValue, Map.class);
  }

  @Benchmark
  public ParsedExpr adapterConvertMessageToGenerated(ConversionState state) {
    return state.registry.valueToNative(state.messageValue, ParsedExpr.class);
  }

  @Benchmark
  public boolean adapterBooleanValue(ConversionState state) {
    return state.registry.valueToBoolean(state.boolValue);
  }

  @Benchmark
  public long adapterIntValue(ConversionState state) {
    return state.registry.valueToLong(state.intValue);
  }

  @Benchmark
  public long adapterUintValue(ConversionState state) {
    return state.registry.valueToLong(state.uintValue);
  }

  @Benchmark
  public double adapterDoubleValue(ConversionState state) {
    return state.registry.valueToDouble(state.doubleValue);
  }

  @Benchmark
  public boolean directBooleanValue(ConversionState state) {
    return state.boolValue.booleanValue();
  }

  @Benchmark
  public long directIntValue(ConversionState state) {
    return state.intValue.intValue();
  }

  @Benchmark
  public long directUintValue(ConversionState state) {
    return state.uintValue.intValue();
  }

  @Benchmark
  public double directDoubleValue(ConversionState state) {
    return state.doubleValue.doubleValue();
  }
}
