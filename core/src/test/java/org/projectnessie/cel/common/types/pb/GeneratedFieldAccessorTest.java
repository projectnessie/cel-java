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
package org.projectnessie.cel.common.types.pb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.pb.Db.newDb;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Int32Value;
import com.google.protobuf.NullValue;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes.NestedEnum;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.MapT;
import org.projectnessie.cel.common.types.ref.FieldGetter;
import org.projectnessie.cel.common.types.ref.FieldTester;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.traits.Lister;

class GeneratedFieldAccessorTest {

  @Test
  void bindsSupportedGeneratedScalarGetters() {
    TestAllTypes message =
        TestAllTypes.newBuilder()
            .setSingleInt32(12)
            .setSingleInt64(13L)
            .setSingleSint32(14)
            .setSingleSint64(15L)
            .setSingleSfixed32(16)
            .setSingleSfixed64(17L)
            .setSingleFloat(18.25f)
            .setSingleDouble(19.25d)
            .setSingleBool(true)
            .setSingleString("twenty")
            .setSingleBytes(ByteString.copyFromUtf8("twenty-one"))
            .build();
    Db db = newDb();
    db.registerMessage(TestAllTypes.getDefaultInstance());
    PbTypeDescription type = db.describeType(message.getDescriptorForType().getFullName());

    for (String fieldName :
        List.of(
            "single_int32",
            "single_int64",
            "single_sint32",
            "single_sint64",
            "single_sfixed32",
            "single_sfixed64",
            "single_float",
            "single_double",
            "single_bool",
            "single_string",
            "single_bytes")) {
      FieldDescription field = type.fieldByName(fieldName);
      FieldGetter getter = GeneratedFieldAccessor.create(type, field);
      assertThat(getter).as(fieldName).isNotNull();
      assertThat(getter.getFrom(message))
          .as(fieldName)
          .isEqualTo(message.getField(field.descriptor()));
    }
  }

  @Test
  void exposesPrimitiveGettersOnlyForAllocationFreeScalarFields() {
    TestAllTypes message =
        TestAllTypes.newBuilder()
            .setSingleBool(true)
            .setSingleInt32(50_021)
            .setSingleInt64(50_022L)
            .setStandaloneEnumValue(12_345)
            .setSingleFloat(-0.0f)
            .setSingleDouble(Double.NaN)
            .build();
    ProtoTypeRegistry registry = ProtoTypeRegistry.newRegistry(TestAllTypes.getDefaultInstance());
    String typeName = TestAllTypes.getDescriptor().getFullName();

    FieldGetter.Primitive boolGetter = primitiveGetter(registry, typeName, "single_bool");
    FieldGetter.Primitive int32Getter = primitiveGetter(registry, typeName, "single_int32");
    FieldGetter.Primitive int64Getter = primitiveGetter(registry, typeName, "single_int64");
    FieldGetter.Primitive enumGetter = primitiveGetter(registry, typeName, "standalone_enum");
    FieldGetter.Primitive floatGetter = primitiveGetter(registry, typeName, "single_float");
    FieldGetter.Primitive doubleGetter = primitiveGetter(registry, typeName, "single_double");

    assertThat(boolGetter.optimizedTargetType()).isEqualTo(TestAllTypes.class);
    assertThat(boolGetter.getBooleanFrom(message)).isTrue();
    assertThat(int32Getter.getLongFrom(message)).isEqualTo(50_021L);
    assertThat(int64Getter.getLongFrom(message)).isEqualTo(50_022L);
    assertThat(enumGetter.getLongFrom(message)).isEqualTo(12_345L);
    assertThat(Float.floatToRawIntBits((float) floatGetter.getDoubleFrom(message)))
        .isEqualTo(Float.floatToRawIntBits(-0.0f));
    assertThat(doubleGetter.getDoubleFrom(message)).isNaN();

    DynamicMessage dynamic =
        DynamicMessage.newBuilder(message.getDescriptorForType()).mergeFrom(message).build();
    assertThat(int64Getter.getFrom(dynamic)).isEqualTo(50_022L);

    for (String fieldName :
        List.of(
            "single_uint32",
            "single_uint64",
            "single_string",
            "single_int32_wrapper",
            "optional_null_value")) {
      assertThat(registry.findFieldType(typeName, fieldName).getFrom)
          .as(fieldName)
          .isNotInstanceOf(FieldGetter.Primitive.class);
    }
  }

  private static FieldGetter.Primitive primitiveGetter(
      ProtoTypeRegistry registry, String typeName, String fieldName) {
    assertThat(registry.findFieldType(typeName, fieldName).getFrom)
        .as(fieldName)
        .isInstanceOf(FieldGetter.Primitive.class);
    return (FieldGetter.Primitive) registry.findFieldType(typeName, fieldName).getFrom;
  }

  @Test
  void bindsFieldsWithSpecializedNormalization() {
    Db db = newDb();
    db.registerMessage(TestAllTypes.getDefaultInstance());
    PbTypeDescription type = db.describeType(TestAllTypes.getDescriptor().getFullName());

    for (String fieldName :
        List.of(
            "single_uint32",
            "standalone_enum",
            "single_int32_wrapper",
            "repeated_int32",
            "map_string_string",
            "single_any",
            "single_duration",
            "single_timestamp")) {
      assertThat(GeneratedFieldAccessor.create(type, type.fieldByName(fieldName)))
          .as(fieldName)
          .isNotNull();
    }

    assertThat(GeneratedFieldAccessor.create(type, type.fieldByName("optional_null_value")))
        .isNull();
    assertThat(GeneratedFieldAccessor.create(type, type.fieldByName("repeated_null_value")))
        .isNotNull();
    assertThat(GeneratedFieldAccessor.create(type, type.fieldByName("single_nested_message")))
        .isNull();
    assertThat(
            GeneratedFieldAccessor.createForObject(type, type.fieldByName("single_nested_message")))
        .isNotNull();
  }

  @Test
  void bindsOnlyGeneratedPresenceMethods() {
    Db db = newDb();
    db.registerMessage(TestAllTypes.getDefaultInstance());
    PbTypeDescription type = db.describeType(TestAllTypes.getDescriptor().getFullName());
    TestAllTypes present =
        TestAllTypes.newBuilder()
            .setOptionalBool(false)
            .setSingleNestedMessage(TestAllTypes.NestedMessage.getDefaultInstance())
            .setSingleInt32Wrapper(Int32Value.getDefaultInstance())
            .build();

    for (String fieldName :
        List.of("optional_bool", "single_nested_message", "single_int32_wrapper")) {
      FieldTester tester = GeneratedFieldAccessor.createTester(type, type.fieldByName(fieldName));
      assertThat(tester).as(fieldName).isNotNull();
      assertThat(tester.isSet(TestAllTypes.getDefaultInstance())).as(fieldName).isFalse();
      assertThat(tester.isSet(present)).as(fieldName).isTrue();
    }

    for (String fieldName :
        List.of("single_int32", "standalone_enum", "repeated_int32", "map_string_string")) {
      assertThat(GeneratedFieldAccessor.createTester(type, type.fieldByName(fieldName)))
          .as(fieldName)
          .isNull();
    }
  }

  @Test
  void registryNormalizesGeneratedFieldValues() {
    TestAllTypes message =
        TestAllTypes.newBuilder()
            .setSingleUint32(-1)
            .setSingleFixed32(Integer.MIN_VALUE)
            .setSingleUint64(Long.MIN_VALUE)
            .setStandaloneEnumValue(12_345)
            .setSingleNestedMessage(TestAllTypes.NestedMessage.newBuilder().setBb(50_000))
            .setSingleInt32Wrapper(Int32Value.of(50_000))
            .addAllRepeatedUint32(List.of(0, Integer.MAX_VALUE, Integer.MIN_VALUE, -1))
            .addAllRepeatedFixed32(List.of(0, Integer.MAX_VALUE, Integer.MIN_VALUE, -1))
            .addRepeatedNestedEnumValue(12_345)
            .addRepeatedNullValue(NullValue.NULL_VALUE)
            .putMapStringString("key", "value")
            .putMapUint32Uint64(-1, Long.MIN_VALUE)
            .putMapStringEnumValue("unknown", 12_345)
            .putMapBoolNullValue(true, NullValue.NULL_VALUE)
            .build();
    ProtoTypeRegistry registry = ProtoTypeRegistry.newRegistry(TestAllTypes.getDefaultInstance());
    String typeName = message.getDescriptorForType().getFullName();

    assertThat(registry.findFieldType(typeName, "single_uint32").getFrom.getFrom(message))
        .isEqualTo(ULong.valueOf(0xffff_ffffL));
    assertThat(registry.findFieldType(typeName, "single_fixed32").getFrom.getFrom(message))
        .isEqualTo(ULong.valueOf(0x8000_0000L));
    assertThat(registry.findFieldType(typeName, "single_uint64").getFrom.getFrom(message))
        .isEqualTo(ULong.valueOf(Long.MIN_VALUE));
    assertThat(registry.findFieldType(typeName, "standalone_enum").getFrom.getFrom(message))
        .isEqualTo(12_345);
    assertThat(registry.findFieldType(typeName, "single_nested_message").getFrom.getFrom(message))
        .isEqualTo(message.getSingleNestedMessage());
    assertThat(registry.findFieldType(typeName, "single_int32_wrapper").getFrom.getFrom(message))
        .isEqualTo(Int32Value.of(50_000));
    assertThat(
            registry
                .findFieldType(typeName, "single_int32_wrapper")
                .getFrom
                .getFrom(TestAllTypes.getDefaultInstance()))
        .isEqualTo(NullValue.NULL_VALUE);
    assertThat(registry.findFieldType(typeName, "repeated_uint32").getFrom.getFrom(message))
        .isEqualTo(
            List.of(
                ULong.valueOf(0L),
                ULong.valueOf(0x7fff_ffffL),
                ULong.valueOf(0x8000_0000L),
                ULong.valueOf(0xffff_ffffL)));
    assertThat(registry.findFieldType(typeName, "repeated_fixed32").getFrom.getFrom(message))
        .isEqualTo(
            List.of(
                ULong.valueOf(0L),
                ULong.valueOf(0x7fff_ffffL),
                ULong.valueOf(0x8000_0000L),
                ULong.valueOf(0xffff_ffffL)));
    assertThat(registry.findFieldType(typeName, "repeated_nested_enum").getFrom.getFrom(message))
        .isEqualTo(List.of(12_345));

    Object mapValue =
        registry.findFieldType(typeName, "map_string_string").getFrom.getFrom(message);
    assertThat(mapValue).isInstanceOf(MapT.class);
    assertThat(((MapT) mapValue).get(stringOf("key"))).isEqualTo(stringOf("value"));
    MapT unsignedMap =
        (MapT) registry.findFieldType(typeName, "map_uint32_uint64").getFrom.getFrom(message);
    assertThat(unsignedMap.get(uintOf(0xffff_ffffL))).isEqualTo(uintOf(Long.MIN_VALUE));
    MapT enumMap =
        (MapT) registry.findFieldType(typeName, "map_string_enum").getFrom.getFrom(message);
    assertThat(enumMap.get(stringOf("unknown"))).isEqualTo(intOf(12_345));
    MapT nullMap =
        (MapT) registry.findFieldType(typeName, "map_bool_null_value").getFrom.getFrom(message);
    assertThat(nullMap.get(True)).isEqualTo(intOf(0));

    PbObjectT object = (PbObjectT) registry.nativeToValue(message);
    assertThat(object.get(stringOf("single_uint32"))).isEqualTo(uintOf(0xffff_ffffL));
    assertThat(object.get(stringOf("single_fixed32"))).isEqualTo(uintOf(0x8000_0000L));
    assertThat(object.get(stringOf("standalone_enum"))).isEqualTo(intOf(12_345));
    assertThat(object.get(stringOf("single_int32_wrapper"))).isEqualTo(intOf(50_000));
    assertThat(((Lister) object.get(stringOf("repeated_null_value"))).get(intOf(0)))
        .isEqualTo(intOf(0));

    DynamicMessage dynamic =
        DynamicMessage.newBuilder(message.getDescriptorForType()).mergeFrom(message).build();
    assertThat(registry.findFieldType(typeName, "single_uint32").getFrom.getFrom(dynamic))
        .isEqualTo(ULong.valueOf(0xffff_ffffL));
    assertThat(registry.findFieldType(typeName, "single_fixed32").getFrom.getFrom(dynamic))
        .isEqualTo(ULong.valueOf(0x8000_0000L));
    assertThat(registry.findFieldType(typeName, "repeated_uint32").getFrom.getFrom(dynamic))
        .isEqualTo(
            List.of(
                ULong.valueOf(0L),
                ULong.valueOf(0x7fff_ffffL),
                ULong.valueOf(0x8000_0000L),
                ULong.valueOf(0xffff_ffffL)));
    MapT dynamicUnsignedMap =
        (MapT) registry.findFieldType(typeName, "map_uint32_uint64").getFrom.getFrom(dynamic);
    assertThat(dynamicUnsignedMap.get(uintOf(0xffff_ffffL))).isEqualTo(uintOf(Long.MIN_VALUE));
    PbObjectT dynamicObject = (PbObjectT) registry.nativeToValue(dynamic);
    assertThat(dynamicObject.get(stringOf("single_uint32"))).isEqualTo(uintOf(0xffff_ffffL));
  }

  @Test
  void generatedRegistryGetterFallsBackForDynamicMessages() {
    TestAllTypes generated =
        TestAllTypes.newBuilder()
            .setSingleInt32(42)
            .setStandaloneEnum(NestedEnum.BAR)
            .setSingleNestedMessage(TestAllTypes.NestedMessage.newBuilder().setBb(50_000))
            .build();
    DynamicMessage dynamic =
        DynamicMessage.newBuilder(generated.getDescriptorForType()).mergeFrom(generated).build();
    ProtoTypeRegistry registry = ProtoTypeRegistry.newRegistry(TestAllTypes.getDefaultInstance());

    FieldType scalar =
        registry.findFieldType(generated.getDescriptorForType().getFullName(), "single_int32");
    FieldType enumField =
        registry.findFieldType(generated.getDescriptorForType().getFullName(), "standalone_enum");

    assertThat(scalar.getFrom.getFrom(generated)).isEqualTo(42);
    assertThat(scalar.getFrom.getFrom(dynamic)).isEqualTo(42);
    FieldDescriptor enumDescriptor =
        generated.getDescriptorForType().findFieldByName("standalone_enum");
    assertThat(enumField.getFrom.getFrom(dynamic)).isEqualTo(dynamic.getField(enumDescriptor));

    PbObjectT generatedObject = (PbObjectT) registry.nativeToValue(generated);
    PbObjectT dynamicObject = (PbObjectT) registry.nativeToValue(dynamic);
    assertThat(generatedObject.get(stringOf("single_nested_message")))
        .isEqualTo(registry.nativeToValue(generated.getSingleNestedMessage()));
    assertThat(dynamicObject.get(stringOf("single_nested_message")))
        .isEqualTo(
            registry.nativeToValue(
                dynamic.getField(
                    generated.getDescriptorForType().findFieldByName("single_nested_message"))));
  }

  @Test
  void proto2DefaultsAndPresenceRemainDescriptorDriven() {
    dev.cel.expr.conformance.proto2.TestAllTypes absent =
        dev.cel.expr.conformance.proto2.TestAllTypes.getDefaultInstance();
    dev.cel.expr.conformance.proto2.TestAllTypes present =
        dev.cel.expr.conformance.proto2.TestAllTypes.newBuilder().setSingleInt32(42).build();
    ProtoTypeRegistry registry = ProtoTypeRegistry.newRegistry(absent);
    FieldType field =
        registry.findFieldType(absent.getDescriptorForType().getFullName(), "single_int32");

    assertThat(field.getFrom.getFrom(absent)).isEqualTo(-32);
    assertThat(field.isSet.isSet(absent)).isFalse();
    assertThat(field.getFrom.getFrom(present)).isEqualTo(42);
    assertThat(field.isSet.isSet(present)).isTrue();
  }

  @Test
  void dynamicTypeDescriptionDoesNotBindGeneratedGetter() {
    DynamicMessage zero = DynamicMessage.getDefaultInstance(TestAllTypes.getDescriptor());
    Db db = newDb();
    db.registerMessage(zero);
    PbTypeDescription type = db.describeType(zero.getDescriptorForType().getFullName());

    assertThat(GeneratedFieldAccessor.create(type, type.fieldByName("single_int32"))).isNull();
    assertThat(GeneratedFieldAccessor.createTester(type, type.fieldByName("optional_bool")))
        .isNull();
  }

  @Test
  void generatedRegistrationInvalidatesDescriptorOnlyFieldCache() {
    DynamicMessage dynamicZero = DynamicMessage.getDefaultInstance(TestAllTypes.getDescriptor());
    ProtoTypeRegistry registry = ProtoTypeRegistry.newEmptyRegistry();
    registry.registerMessage(dynamicZero);
    String typeName = dynamicZero.getDescriptorForType().getFullName();

    FieldType descriptorOnly = registry.findFieldType(typeName, "single_int32");
    registry.registerMessage(TestAllTypes.getDefaultInstance());
    FieldType generated = registry.findFieldType(typeName, "single_int32");

    assertThat(generated).isNotSameAs(descriptorOnly);
    assertThat(generated.getFrom.getFrom(TestAllTypes.newBuilder().setSingleInt32(50_000).build()))
        .isEqualTo(50_000);
  }
}
