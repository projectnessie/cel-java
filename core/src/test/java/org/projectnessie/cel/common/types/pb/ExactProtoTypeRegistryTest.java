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
import static org.projectnessie.cel.common.types.StringT.stringOf;

import com.google.protobuf.DynamicMessage;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.ref.ExactAggregateFieldProvider;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.proto.tests.ProtoTestTypes;

class ExactProtoTypeRegistryTest {
  private static final String TYPE = TestAllTypes.getDescriptor().getFullName();

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
    assertThat(
            exact.isExactAggregateField(
                TYPE, "map_string_int64", registry.findFieldType(TYPE, "map_string_int64").type))
        .isTrue();
    assertThat(
            exact.isExactAggregateField(
                TYPE, "repeated_bytes", registry.findFieldType(TYPE, "repeated_bytes").type))
        .isFalse();
    assertThat(
            exact.isExactAggregateField(
                TYPE, "map_string_uint64", registry.findFieldType(TYPE, "map_string_uint64").type))
        .isFalse();
    assertThat(
            exact.isExactAggregateField(
                TYPE, "single_int64", registry.findFieldType(TYPE, "single_int64").type))
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
            .putMapStringInt64("answer", 42L)
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
      assertThat(value(registry, message, "map_string_int64")).isEqualTo(Map.of("answer", 42L));
    }

    assertThat(value(registry, generated, "map_string_int64")).isInstanceOf(Map.class);
    assertThat(value(registry, dynamic, "map_string_int64"))
        .isInstanceOf(DynamicProtoMapView.class);
    assertThat(
            ((PbObjectT) registry.nativeToValue(generated))
                .get(stringOf("single_uint32"))
                .intValue())
        .isZero();
  }

  private static Object value(TypeRegistry registry, Object message, String fieldName) {
    FieldType field = registry.findFieldType(TYPE, fieldName);
    assertThat(field).isNotNull();
    return field.getFrom.getFrom(message);
  }
}
