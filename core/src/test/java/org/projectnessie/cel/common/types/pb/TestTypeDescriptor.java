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
import static org.projectnessie.cel.Util.mapOf;
import static org.projectnessie.cel.common.types.pb.Db.newDb;

import com.google.api.expr.v1alpha1.Type;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import google.expr.proto3.test.TestAllTypesOuterClass.NestedTestAllTypes;
import google.expr.proto3.test.TestAllTypesOuterClass.TestAllTypes;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.TimestampT;

public class TestTypeDescriptor {

  @ParameterizedTest
  @ValueSource(
      strings = {
        ".google.protobuf.Any",
        ".google.protobuf.BoolValue",
        ".google.protobuf.BytesValue",
        ".google.protobuf.DoubleValue",
        ".google.protobuf.FloatValue",
        ".google.protobuf.Int32Value",
        ".google.protobuf.Int64Value",
        ".google.protobuf.ListValue",
        ".google.protobuf.Struct",
        ".google.protobuf.Value"
      })
  void typeDescriptor(String typeName) {
    Db pbdb = newDb();
    assertThat(pbdb.describeType(typeName)).isNotNull();
  }

  @Test
  void fieldMap() {
    Db pbdb = newDb();
    NestedTestAllTypes msg = NestedTestAllTypes.getDefaultInstance();
    pbdb.registerMessage(msg);
    TypeDescription td = pbdb.describeType(msg.getDescriptorForType().getFullName());
    assertThat(td).isNotNull();

    assertThat(td.fieldMap()).hasSize(2);
  }

  static class MaybeUnwrapTestCase {
    Message in;
    Object out;

    MaybeUnwrapTestCase in(Message in) {
      this.in = in;
      return this;
    }

    MaybeUnwrapTestCase out(Object out) {
      this.out = out;
      return this;
    }

    @Override
    public String toString() {
      return in.toString();
    }
  }

  @SuppressWarnings("unused")
  static MaybeUnwrapTestCase[] maybeUnwrapTestCases() {
    return new MaybeUnwrapTestCase[] {
      new MaybeUnwrapTestCase().in(UnwrapContext.get().msgDesc.zero()).out(NullValue.NULL_VALUE),
      // TODO new
      // MaybeUnwrapTestCase().in(UnwrapContext.get().msgDesc.newReflect().Interface()).out(NullValue.NULL_VALUE),
      new MaybeUnwrapTestCase().in(anyMsg(BoolValue.of(true))).out(true),
      new MaybeUnwrapTestCase().in(anyMsg(Value.newBuilder().setNumberValue(4.5).build())).out(4.5),
      new MaybeUnwrapTestCase()
          .in(dynMsg(anyMsg(Value.newBuilder().setNumberValue(4.5).build())))
          .out(4.5),
      new MaybeUnwrapTestCase()
          .in(dynMsg(anyMsg(TestAllTypes.newBuilder().setSingleFloat(123.0f).build())))
          .out(TestAllTypes.newBuilder().setSingleFloat(123.0f).build()),
      new MaybeUnwrapTestCase().in(dynMsg(ListValue.getDefaultInstance())).out(jsonList()),
      new MaybeUnwrapTestCase().in(BoolValue.of(true)).out(true),
      new MaybeUnwrapTestCase().in(BoolValue.of(false)).out(false),
      new MaybeUnwrapTestCase()
          .in(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build())
          .out(NullValue.NULL_VALUE),
      new MaybeUnwrapTestCase().in(Value.getDefaultInstance()).out(NullValue.NULL_VALUE),
      new MaybeUnwrapTestCase().in(Value.newBuilder().setNumberValue(1.5).build()).out(1.5d),
      new MaybeUnwrapTestCase().in(StringValue.of("hello world")).out("hello world"),
      new MaybeUnwrapTestCase()
          .in(
              Value.newBuilder()
                  .setListValue(
                      jsonList(
                          Value.newBuilder().setBoolValue(true).build(),
                          Value.newBuilder().setNumberValue(1.0).build()))
                  .build())
          .out(
              jsonList(
                  Value.newBuilder().setBoolValue(true).build(),
                  Value.newBuilder().setNumberValue(1.0).build())),
      new MaybeUnwrapTestCase()
          .in(
              Value.newBuilder()
                  .setStructValue(
                      jsonStruct(
                          mapOf("hello", Value.newBuilder().setStringValue("world").build())))
                  .build())
          .out(jsonStruct(mapOf("hello", Value.newBuilder().setStringValue("world").build()))),
      new MaybeUnwrapTestCase().in(BoolValue.of(false)).out(false),
      new MaybeUnwrapTestCase().in(BoolValue.of(true)).out(true),
      new MaybeUnwrapTestCase()
          .in(BytesValue.of(ByteString.copyFromUtf8("hello")))
          .out(ByteString.copyFromUtf8("hello")),
      new MaybeUnwrapTestCase().in(DoubleValue.of(-4.2)).out(-4.2),
      new MaybeUnwrapTestCase().in(FloatValue.of(4.5f)).out(4.5f),
      new MaybeUnwrapTestCase().in(Int32Value.of(123)).out(123),
      new MaybeUnwrapTestCase().in(Int64Value.of(456)).out(456L),
      new MaybeUnwrapTestCase().in(StringValue.of("goodbye")).out("goodbye"),
      new MaybeUnwrapTestCase().in(UInt32Value.of(1234)).out(1234),
      new MaybeUnwrapTestCase().in(UInt64Value.of(5678)).out(5678L),
      new MaybeUnwrapTestCase()
          .in(Timestamp.newBuilder().setSeconds(12345).setNanos(0).build())
          .out(Instant.ofEpochSecond(12345).atZone(TimestampT.ZoneIdZ)),
      new MaybeUnwrapTestCase()
          .in(Duration.newBuilder().setSeconds(345).build())
          .out(java.time.Duration.ofSeconds(345))
    };
  }

  @ParameterizedTest
  @MethodSource("maybeUnwrapTestCases")
  void maybeUnwrap(MaybeUnwrapTestCase tc) {
    UnwrapContext c = UnwrapContext.get();

    String typeName = tc.in.getDescriptorForType().getFullName();
    TypeDescription td = c.pbdb.describeType(typeName);
    assertThat(td).isNotNull();
    Object val = td.maybeUnwrap(c.pbdb, tc.in);
    assertThat(val).isNotNull();

    assertThat(val).isEqualTo(tc.out);
  }

  @SuppressWarnings("unused")
  static UnwrapTestCase[] benchmarkTypeDescriptionMaybeUnwrapCases() {
    return UnwrapTestCase.values();
  }

  @ParameterizedTest
  @EnumSource
  void benchmarkTypeDescriptionMaybeUnwrap(UnwrapTestCase tc) {
    UnwrapContext c = UnwrapContext.get();

    Message msg = tc.message();

    String typeName = msg.getDescriptorForType().getFullName();
    TypeDescription td = c.pbdb.describeType(typeName);
    assertThat(td).isNotNull();

    td.maybeUnwrap(c.pbdb, msg);
  }

  @Test
  void checkedType() {
    Db pbdb = newDb();
    TestAllTypes msg = TestAllTypes.getDefaultInstance();
    String msgName = msg.getDescriptorForType().getFullName();
    pbdb.registerMessage(msg);
    TypeDescription td = pbdb.describeType(msgName);
    assertThat(td).isNotNull();

    FieldDescription field = td.fieldByName("map_string_string");
    assertThat(field).isNotNull();

    Type mapType = Decls.newMapType(Decls.String, Decls.String);
    assertThat(field.checkedType()).isEqualTo(mapType);

    field = td.fieldByName("repeated_nested_message");
    assertThat(field).isNotNull();
    Type listType =
        Decls.newListType(
            Decls.newObjectType("google.expr.proto3.test.TestAllTypes.NestedMessage"));
    assertThat(field.checkedType()).isEqualTo(listType);
  }

  static Message dynMsg(Message msg) {
    return DynamicMessage.newBuilder(msg.getDescriptorForType()).mergeFrom(msg).build();
  }

  static Any anyMsg(Message msg) {
    return Any.pack(msg);
  }

  static ListValue jsonList(Value... elems) {
    return ListValue.newBuilder().addAllValues(Arrays.asList(elems)).build();
  }

  static Struct jsonStruct(Map<Object, Object> entries) {
    Struct.Builder b = Struct.newBuilder();
    entries.forEach((k, v) -> b.putFields(k.toString(), (Value) v));
    return b.build();
  }
}
