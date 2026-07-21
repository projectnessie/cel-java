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

import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newRegistry;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;

import com.google.api.expr.v1alpha1.Type;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Int32Value;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes.NestedMessage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.pb.PbObjectT;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.AttributeFactory.NamespacedAttribute;
import org.projectnessie.cel.interpreter.Interpretable.EvalAttr;
import org.projectnessie.cel.interpreter.Interpretable.EvalTestOnly;

@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FieldAccessBench {

  @State(Scope.Benchmark)
  public static class FieldAccessState {
    final TestAllTypes message;
    final DynamicMessage dynamicMessage;
    final PbObjectT object;
    final PbObjectT dynamicObject;
    final PbObjectT absentObject;
    final StringT int32Name = stringOf("single_int32");
    final StringT optionalBoolName = stringOf("optional_bool");
    final StringT nestedMessageName = stringOf("single_nested_message");
    final StringT uint32Name = stringOf("single_uint32");
    final StringT enumName = stringOf("standalone_enum");
    final StringT wrapperName = stringOf("single_int32_wrapper");
    final StringT repeatedName = stringOf("repeated_int32");
    final StringT mapName = stringOf("map_string_string");
    final Activation activation;
    final NamespacedAttribute int32Attribute;
    final NamespacedAttribute nestedInt32Attribute;
    final Interpretable presence;
    final FieldType int32;
    final FieldType optionalBool;
    final FieldType nestedMessage;
    final FieldType uint32;
    final FieldType enumField;
    final FieldType wrapper;
    final FieldType repeated;
    final FieldType map;

    public FieldAccessState() {
      Map<String, String> mapValues = new HashMap<>();
      for (int i = 0; i < 16; i++) {
        mapValues.put("key-" + i, "value-" + i);
      }

      TestAllTypes.Builder builder =
          TestAllTypes.newBuilder()
              .setSingleInt32(50_000)
              .setSingleInt64(0x51a7_19b3_42c5L)
              .setSingleFloat(12_345.25f)
              .setSingleDouble(12_345.25d)
              .setOptionalBool(false)
              .setSingleNestedMessage(NestedMessage.newBuilder().setBb(50_000))
              .setSingleUint32(-1)
              .setSingleUint64(-1L)
              .setStandaloneEnumValue(12_345)
              .setSingleInt32Wrapper(Int32Value.of(50_000))
              .putAllMapStringString(mapValues);
      for (int i = 0; i < 16; i++) {
        builder.addRepeatedInt32(50_000 + i);
      }
      message = builder.build();
      dynamicMessage =
          DynamicMessage.newBuilder(message.getDescriptorForType()).mergeFrom(message).build();

      TypeRegistry registry = newRegistry(TestAllTypes.getDefaultInstance());
      object = (PbObjectT) registry.nativeToValue(message);
      dynamicObject = (PbObjectT) registry.nativeToValue(dynamicMessage);
      absentObject = (PbObjectT) registry.nativeToValue(TestAllTypes.getDefaultInstance());

      String messageType = message.getDescriptorForType().getFullName();
      int32 = registry.findFieldType(messageType, "single_int32");
      optionalBool = registry.findFieldType(messageType, "optional_bool");
      nestedMessage = registry.findFieldType(messageType, "single_nested_message");
      uint32 = registry.findFieldType(messageType, "single_uint32");
      enumField = registry.findFieldType(messageType, "standalone_enum");
      wrapper = registry.findFieldType(messageType, "single_int32_wrapper");
      repeated = registry.findFieldType(messageType, "repeated_int32");
      map = registry.findFieldType(messageType, "map_string_string");

      Type outerType = registry.findType(messageType);
      Type nestedType = registry.findType("cel.expr.conformance.proto3.TestAllTypes.NestedMessage");
      AttributeFactory attributes =
          newAttributeFactory(Container.defaultContainer, registry, registry);

      int32Attribute = attributes.absoluteAttribute(1, "message");
      int32Attribute.addQualifier(attributes.newQualifier(outerType, 2, "single_int32"));

      nestedInt32Attribute = attributes.absoluteAttribute(3, "message");
      nestedInt32Attribute.addQualifier(
          attributes.newQualifier(outerType, 4, "single_nested_message"));
      nestedInt32Attribute.addQualifier(attributes.newQualifier(nestedType, 5, "bb"));

      EvalAttr presenceOperand = new EvalAttr(registry, attributes.absoluteAttribute(6, "message"));
      presence = new EvalTestOnly(7, presenceOperand, optionalBoolName, optionalBool);
      activation = newActivation(Map.of("message", message));
    }
  }

  @Benchmark
  public int directGeneratedInt32(FieldAccessState state) {
    return state.message.getSingleInt32();
  }

  @Benchmark
  public Object rawGeneratedInt32(FieldAccessState state) {
    return state.int32.getFrom.getFrom(state.message);
  }

  @Benchmark
  public Object rawDynamicFallbackInt32(FieldAccessState state) {
    return state.int32.getFrom.getFrom(state.dynamicMessage);
  }

  @Benchmark
  public Val objectGeneratedInt32(FieldAccessState state) {
    return state.object.get(state.int32Name);
  }

  @Benchmark
  public Val objectDynamicInt32(FieldAccessState state) {
    return state.dynamicObject.get(state.int32Name);
  }

  @Benchmark
  public Object checkedTerminalInt32(FieldAccessState state) {
    return state.int32Attribute.resolve(state.activation);
  }

  @Benchmark
  public Object checkedNestedInt32(FieldAccessState state) {
    return state.nestedInt32Attribute.resolve(state.activation);
  }

  @Benchmark
  public boolean rawPresencePresent(FieldAccessState state) {
    return state.optionalBool.isSet.isSet(state.message);
  }

  @Benchmark
  public boolean rawPresenceAbsent(FieldAccessState state) {
    return state.optionalBool.isSet.isSet(TestAllTypes.getDefaultInstance());
  }

  @Benchmark
  public Val checkedPresence(FieldAccessState state) {
    return state.presence.eval(state.activation);
  }

  @Benchmark
  public Val objectPresencePresent(FieldAccessState state) {
    return state.object.isSet(state.optionalBoolName);
  }

  @Benchmark
  public Val objectPresenceAbsent(FieldAccessState state) {
    return state.absentObject.isSet(state.optionalBoolName);
  }

  @Benchmark
  public Object rawNestedMessage(FieldAccessState state) {
    return state.nestedMessage.getFrom.getFrom(state.message);
  }

  @Benchmark
  public Val objectNestedMessage(FieldAccessState state) {
    return state.object.get(state.nestedMessageName);
  }

  @Benchmark
  public Object rawUnsigned(FieldAccessState state) {
    return state.uint32.getFrom.getFrom(state.message);
  }

  @Benchmark
  public Val objectUnsigned(FieldAccessState state) {
    return state.object.get(state.uint32Name);
  }

  @Benchmark
  public Object rawEnum(FieldAccessState state) {
    return state.enumField.getFrom.getFrom(state.message);
  }

  @Benchmark
  public Val objectEnum(FieldAccessState state) {
    return state.object.get(state.enumName);
  }

  @Benchmark
  public Object rawWrapper(FieldAccessState state) {
    return state.wrapper.getFrom.getFrom(state.message);
  }

  @Benchmark
  public Val objectWrapper(FieldAccessState state) {
    return state.object.get(state.wrapperName);
  }

  @Benchmark
  public Object rawRepeated(FieldAccessState state) {
    return state.repeated.getFrom.getFrom(state.message);
  }

  @Benchmark
  public Val objectRepeated(FieldAccessState state) {
    return state.object.get(state.repeatedName);
  }

  @Benchmark
  public Object rawMap(FieldAccessState state) {
    return state.map.getFrom.getFrom(state.message);
  }

  @Benchmark
  public Val objectMap(FieldAccessState state) {
    return state.object.get(state.mapName);
  }
}
