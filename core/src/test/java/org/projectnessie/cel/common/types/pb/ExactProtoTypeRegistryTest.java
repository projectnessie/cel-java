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
package org.projectnessie.cel.common.types.pb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.StringT.stringOf;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.ref.ExactAggregateFieldProvider;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.proto.tests.ProtoTestTypes;

class ExactProtoTypeRegistryTest {
  private static final String TYPE = TestAllTypes.getDescriptor().getFullName();
  private static final String PROTO2_TYPE =
      dev.cel.expr.conformance.proto2.TestAllTypes.getDescriptor().getFullName();

  @Test
  void exactFactoryAndCopyPreserveProtoIdentityAndMode() {
    TypeRegistry registry =
        ProtoTypeRegistry.newExactAggregateRegistry(TestAllTypes.getDefaultInstance());

    assertThat(registry)
        .isInstanceOf(ProtoTypeRegistry.class)
        .isInstanceOf(ExactAggregateTypeAdapter.class)
        .isInstanceOf(ExactAggregateFieldProvider.class);
    assertThat(registry.copy())
        .isNotSameAs(registry)
        .isInstanceOf(ProtoTypeRegistry.class)
        .isInstanceOf(ExactAggregateTypeAdapter.class)
        .isInstanceOf(ExactAggregateFieldProvider.class);
    assertThat(ProtoTypeRegistry.newRegistry(TestAllTypes.getDefaultInstance()))
        .isNotInstanceOf(ExactAggregateFieldProvider.class);

    TypeRegistry copy = registry.copy();
    ProtoTestTypes.EventA additional = ProtoTestTypes.EventA.getDefaultInstance();
    registry.register(additional);
    assertThat(registry.findType(additional.getDescriptorForType().getFullName())).isNotNull();
    assertThat(copy.findType(additional.getDescriptorForType().getFullName())).isNull();
  }

  @Test
  void certificationIsFieldSelective() {
    TypeRegistry registry =
        ProtoTypeRegistry.newExactAggregateRegistry(TestAllTypes.getDefaultInstance());
    ExactAggregateFieldProvider exact = (ExactAggregateFieldProvider) registry;

    for (String field :
        List.of(
            "repeated_bool",
            "repeated_int32",
            "repeated_int64",
            "repeated_sint32",
            "repeated_sint64",
            "repeated_sfixed32",
            "repeated_sfixed64",
            "repeated_uint32",
            "repeated_uint64",
            "repeated_fixed32",
            "repeated_fixed64",
            "repeated_float",
            "repeated_double",
            "repeated_string")) {
      assertThat(exact.isExactAggregateField(TYPE, field, registry.findFieldType(TYPE, field).type))
          .as(field)
          .isTrue();
    }
    for (String field :
        List.of("map_string_bool", "map_string_string", "map_string_int64", "map_string_double")) {
      assertThat(exact.isExactAggregateField(TYPE, field, registry.findFieldType(TYPE, field).type))
          .as(field)
          .isTrue();
    }
    assertThat(
            exact.isExactAggregateField(
                TYPE, "repeated_bytes", registry.findFieldType(TYPE, "repeated_bytes").type))
        .isFalse();
    for (String field :
        List.of(
            "map_string_uint64",
            "map_string_float",
            "map_string_int32",
            "map_string_enum",
            "map_string_message",
            "map_bool_int32")) {
      assertThat(exact.isExactAggregateField(TYPE, field, registry.findFieldType(TYPE, field).type))
          .as(field)
          .isFalse();
    }
    assertThat(
            exact.isExactAggregateField(
                TYPE, "single_int64", registry.findFieldType(TYPE, "single_int64").type))
        .isFalse();
  }

  @Test
  void ordinaryRepeatedMessagesRemainUncertifiedForProto2AndProto3() {
    TypeRegistry proto3 =
        ProtoTypeRegistry.newExactAggregateRegistry(TestAllTypes.getDefaultInstance());
    TypeRegistry proto2 =
        ProtoTypeRegistry.newExactAggregateRegistry(
            dev.cel.expr.conformance.proto2.TestAllTypes.getDefaultInstance());

    for (String field : List.of("repeated_nested_message", "repeated_lazy_message")) {
      assertExactAggregateField(proto3, TYPE, field, false);
      assertExactAggregateField(proto2, PROTO2_TYPE, field, false);
    }
  }

  @Test
  void unsupportedAndGoogleProtobufFieldsRemainUncertified() {
    TypeRegistry registry =
        ProtoTypeRegistry.newExactAggregateRegistry(TestAllTypes.getDefaultInstance());
    ExactAggregateFieldProvider exact = (ExactAggregateFieldProvider) registry;

    for (String field :
        List.of(
            "repeated_any",
            "repeated_duration",
            "repeated_timestamp",
            "repeated_struct",
            "repeated_value",
            "repeated_int64_wrapper",
            "repeated_int32_wrapper",
            "repeated_double_wrapper",
            "repeated_float_wrapper",
            "repeated_uint64_wrapper",
            "repeated_uint32_wrapper",
            "repeated_string_wrapper",
            "repeated_bool_wrapper",
            "repeated_bytes_wrapper",
            "repeated_list_value",
            "repeated_null_value",
            "repeated_bytes",
            "repeated_nested_enum",
            "map_string_message",
            "map_bool_int32",
            "single_nested_message",
            "standalone_message",
            "single_int64")) {
      assertExactAggregateField(registry, TYPE, field, false);
    }

    assertThat(exact.isExactAggregateField(TYPE, "missing_field", Decls.Int)).isFalse();
    assertThat(
            exact.isExactAggregateField(
                TYPE, "repeated_nested_message", Decls.newListType(Decls.String)))
        .isFalse();
  }

  @Test
  void generatedAndDynamicAggregatesUseExactRepresentations() throws Exception {
    TestAllTypes generated =
        TestAllTypes.newBuilder()
            .addRepeatedBool(true)
            .addRepeatedInt32(-1)
            .addRepeatedInt64(1L)
            .addAllRepeatedUint32(List.of(0, Integer.MAX_VALUE, Integer.MIN_VALUE, -1))
            .addAllRepeatedUint64(List.of(0L, Long.MAX_VALUE, Long.MIN_VALUE, -1L))
            .addRepeatedFloat(1.5f)
            .addRepeatedDouble(-0.0d)
            .addRepeatedString("value")
            .putMapStringBool("answer", true)
            .putMapStringBool("default", false)
            .putMapStringString("answer", "value")
            .putMapStringString("default", "")
            .putMapStringInt64("answer", 42L)
            .putMapStringDouble("answer", -0.0d)
            .putMapStringDouble("positiveZero", 0.0d)
            .putMapStringDouble("nan", Double.NaN)
            .build();
    DynamicMessage dynamic =
        DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), generated.toByteString());
    TypeRegistry registry = ProtoTypeRegistry.newExactAggregateRegistry(generated);

    for (Object message : List.of(generated, dynamic)) {
      assertThat(value(registry, message, "repeated_bool")).isEqualTo(List.of(true));
      assertThat(value(registry, message, "repeated_int32")).isEqualTo(List.of(-1));
      assertThat(value(registry, message, "repeated_int64")).isEqualTo(List.of(1L));
      assertThat(value(registry, message, "repeated_uint32"))
          .isEqualTo(
              List.of(
                  ULong.valueOf(0L),
                  ULong.valueOf(0x7fff_ffffL),
                  ULong.valueOf(0x8000_0000L),
                  ULong.valueOf(0xffff_ffffL)));
      assertThat(value(registry, message, "repeated_uint64"))
          .isEqualTo(
              List.of(
                  ULong.valueOf(0L),
                  ULong.valueOf(Long.MAX_VALUE),
                  ULong.valueOf(Long.MIN_VALUE),
                  ULong.valueOf(-1L)));
      assertThat(value(registry, message, "repeated_float")).isEqualTo(List.of(1.5f));
      assertThat(value(registry, message, "repeated_double")).isEqualTo(List.of(-0.0d));
      assertThat(value(registry, message, "repeated_string")).isEqualTo(List.of("value"));
      assertThat(value(registry, message, "map_string_bool"))
          .isEqualTo(Map.of("answer", true, "default", false));
      assertThat(value(registry, message, "map_string_string"))
          .isEqualTo(Map.of("answer", "value", "default", ""));
      assertThat(value(registry, message, "map_string_int64")).isEqualTo(Map.of("answer", 42L));
      @SuppressWarnings("unchecked")
      Map<String, Double> doubles =
          (Map<String, Double>) value(registry, message, "map_string_double");
      assertThat(Double.doubleToRawLongBits(doubles.get("answer")))
          .isEqualTo(Double.doubleToRawLongBits(-0.0d));
      assertThat(Double.doubleToRawLongBits(doubles.get("positiveZero")))
          .isEqualTo(Double.doubleToRawLongBits(0.0d));
      assertThat(doubles.get("nan")).isNaN();
    }

    for (String field :
        List.of("map_string_bool", "map_string_string", "map_string_int64", "map_string_double")) {
      assertThat(value(registry, generated, field)).as(field).isInstanceOf(Map.class);
      assertThat(value(registry, dynamic, field)).as(field).isInstanceOf(DynamicProtoMapView.class);
    }
    assertThat(
            ((PbObjectT) registry.nativeToValue(generated))
                .get(stringOf("single_uint32"))
                .intValue())
        .isZero();
  }

  @Test
  void proto3GeneratedAndDynamicRepeatedMessagesPreserveOrderAndMaterialization() throws Exception {
    TestAllTypes generated =
        TestAllTypes.newBuilder()
            .addRepeatedNestedMessage(TestAllTypes.NestedMessage.newBuilder().setBb(11))
            .addRepeatedNestedMessage(TestAllTypes.NestedMessage.newBuilder().setBb(22))
            .addRepeatedLazyMessage(TestAllTypes.NestedMessage.newBuilder().setBb(31))
            .addRepeatedLazyMessage(TestAllTypes.NestedMessage.newBuilder().setBb(42))
            .build();
    DynamicMessage dynamic =
        DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), generated.toByteString());
    TypeRegistry registry = ProtoTypeRegistry.newExactAggregateRegistry(generated);

    assertRepeatedMessageRepresentations(
        registry,
        TYPE,
        generated,
        dynamic,
        TestAllTypes.NestedMessage.class,
        "repeated_nested_message",
        11,
        22);
    assertRepeatedMessageRepresentations(
        registry,
        TYPE,
        generated,
        dynamic,
        TestAllTypes.NestedMessage.class,
        "repeated_lazy_message",
        31,
        42);
  }

  @Test
  void proto2GeneratedAndDynamicRepeatedMessagesPreserveOrderAndMaterialization() throws Exception {
    dev.cel.expr.conformance.proto2.TestAllTypes generated =
        dev.cel.expr.conformance.proto2.TestAllTypes.newBuilder()
            .addRepeatedNestedMessage(
                dev.cel.expr.conformance.proto2.TestAllTypes.NestedMessage.newBuilder().setBb(11))
            .addRepeatedNestedMessage(
                dev.cel.expr.conformance.proto2.TestAllTypes.NestedMessage.newBuilder().setBb(22))
            .addRepeatedLazyMessage(
                dev.cel.expr.conformance.proto2.TestAllTypes.NestedMessage.newBuilder().setBb(31))
            .addRepeatedLazyMessage(
                dev.cel.expr.conformance.proto2.TestAllTypes.NestedMessage.newBuilder().setBb(42))
            .build();
    DynamicMessage dynamic =
        DynamicMessage.parseFrom(generated.getDescriptorForType(), generated.toByteString());
    TypeRegistry registry = ProtoTypeRegistry.newExactAggregateRegistry(generated);

    assertRepeatedMessageRepresentations(
        registry,
        PROTO2_TYPE,
        generated,
        dynamic,
        dev.cel.expr.conformance.proto2.TestAllTypes.NestedMessage.class,
        "repeated_nested_message",
        11,
        22);
    assertRepeatedMessageRepresentations(
        registry,
        PROTO2_TYPE,
        generated,
        dynamic,
        dev.cel.expr.conformance.proto2.TestAllTypes.NestedMessage.class,
        "repeated_lazy_message",
        31,
        42);
  }

  private static void assertExactAggregateField(
      TypeRegistry registry, String typeName, String fieldName, boolean expected) {
    FieldType field = registry.findFieldType(typeName, fieldName);
    assertThat(field).as("%s.%s", typeName, fieldName).isNotNull();
    assertThat(
            ((ExactAggregateFieldProvider) registry)
                .isExactAggregateField(typeName, fieldName, field.type))
        .as("%s.%s", typeName, fieldName)
        .isEqualTo(expected);
  }

  private static void assertRepeatedMessageRepresentations(
      TypeRegistry registry,
      String typeName,
      Message generated,
      DynamicMessage dynamic,
      Class<?> generatedElementType,
      String fieldName,
      int first,
      int second) {
    FieldType field = registry.findFieldType(typeName, fieldName);
    assertThat(field).as("%s.%s", typeName, fieldName).isNotNull();

    for (Message root : List.of(generated, dynamic)) {
      Object fieldValue = field.getFrom.getFrom(root);
      assertThat(fieldValue).as("%s value", fieldName).isInstanceOf(List.class);
      List<?> values = (List<?>) fieldValue;
      assertThat(values).hasSize(2);
      if (root == generated) {
        assertThat(values).allMatch(generatedElementType::isInstance);
      } else {
        assertThat(values).allMatch(DynamicMessage.class::isInstance);
      }
      assertThat(messageIntField(values.get(0), "bb")).isEqualTo(first);
      assertThat(messageIntField(values.get(1), "bb")).isEqualTo(second);

      Val exactValue =
          ((ExactAggregateTypeAdapter) registry).nativeAggregateToValue(values, field.type);
      assertThat(exactValue).isInstanceOf(Lister.class);
      Lister exactList = (Lister) exactValue;
      assertThat(exactList.nativeSize()).isEqualTo(2);
      assertThat(exactList.nativeGetAt(0)).isInstanceOf(PbObjectT.class);
      assertThat(exactList.nativeGetAt(0).type().typeName())
          .isEqualTo(((Message) values.get(0)).getDescriptorForType().getFullName());
      assertThat(((PbObjectT) exactList.nativeGetAt(0)).get(stringOf("bb")).intValue())
          .isEqualTo(first);
      assertThat(((PbObjectT) exactList.nativeGetAt(1)).get(stringOf("bb")).intValue())
          .isEqualTo(second);

      Val establishedValue = registry.nativeToValue(values);
      assertThat(establishedValue).isInstanceOf(Lister.class);
      assertThat(exactList.equal(establishedValue)).isSameAs(True);
    }
  }

  private static int messageIntField(Object value, String fieldName) {
    Message message = (Message) value;
    return (Integer) message.getField(message.getDescriptorForType().findFieldByName(fieldName));
  }

  private static Object value(TypeRegistry registry, Object message, String fieldName) {
    FieldType field = registry.findFieldType(TYPE, fieldName);
    assertThat(field).isNotNull();
    return field.getFrom.getFrom(message);
  }
}
