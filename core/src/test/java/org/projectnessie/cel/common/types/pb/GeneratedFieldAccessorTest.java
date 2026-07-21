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
import static org.projectnessie.cel.common.types.pb.Db.newDb;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes.NestedEnum;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.ref.FieldGetter;
import org.projectnessie.cel.common.types.ref.FieldType;

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
  void excludesFieldsThatNeedSpecializedNormalization() {
    Db db = newDb();
    db.registerMessage(TestAllTypes.getDefaultInstance());
    PbTypeDescription type = db.describeType(TestAllTypes.getDescriptor().getFullName());

    for (String fieldName :
        List.of(
            "single_uint32",
            "standalone_enum",
            "single_nested_message",
            "single_int32_wrapper",
            "repeated_int32",
            "map_string_string")) {
      assertThat(GeneratedFieldAccessor.create(type, type.fieldByName(fieldName)))
          .as(fieldName)
          .isNull();
    }
  }

  @Test
  void generatedRegistryGetterFallsBackForDynamicMessages() {
    TestAllTypes generated =
        TestAllTypes.newBuilder().setSingleInt32(42).setStandaloneEnum(NestedEnum.BAR).build();
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
  }
}
