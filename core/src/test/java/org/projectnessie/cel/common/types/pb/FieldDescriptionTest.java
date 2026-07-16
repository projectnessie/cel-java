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
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.pb.Db.newDb;

import com.google.api.expr.test.v1.proto3.TestAllTypesProto.NestedTestAllTypes;
import com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes;
import com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.NestedEnum;
import com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.NestedMessage;
import com.google.api.expr.v1alpha1.Type;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Duration;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.TimestampT;
import org.projectnessie.cel.common.types.traits.Mapper;

public class FieldDescriptionTest {

  @Test
  void fieldDescription() {
    Db pbdb = newDb();
    NestedTestAllTypes msg = NestedTestAllTypes.getDefaultInstance();
    String msgName = msg.getDescriptorForType().getFullName();
    pbdb.registerMessage(msg);
    PbTypeDescription td = pbdb.describeType(msgName);
    assertThat(td).isNotNull();

    FieldDescription fd = td.fieldByName("payload");
    assertThat(fd).isNotNull();
    assertThat(fd).extracting(FieldDescription::name).isEqualTo("payload");
    assertThat(fd).extracting(FieldDescription::isOneof).isEqualTo(false);
    assertThat(fd).extracting(FieldDescription::isMap).isEqualTo(false);
    assertThat(fd).extracting(FieldDescription::isMessage).isEqualTo(true);
    assertThat(fd).extracting(FieldDescription::isEnum).isEqualTo(false);
    assertThat(fd).extracting(FieldDescription::isList).isEqualTo(false);
    // Access the field by its Go struct name and check to see that it's index
    // matches the one determined by the TypeDescription utils.
    Type got = fd.checkedType();
    Type wanted =
        Type.newBuilder().setMessageType("google.api.expr.test.v1.proto3.TestAllTypes").build();
    assertThat(got).isEqualTo(wanted);
  }

  static class GetFromTestCase {
    String field;
    Object want;

    GetFromTestCase field(String field) {
      this.field = field;
      return this;
    }

    GetFromTestCase want(Object want) {
      this.want = want;
      return this;
    }

    @Override
    public String toString() {
      return field;
    }
  }

  @SuppressWarnings("unused")
  static GetFromTestCase[] getFromTestCases() {
    return new GetFromTestCase[] {
      new GetFromTestCase().field("single_uint64").want(ULong.valueOf(12L)),
      new GetFromTestCase().field("single_duration").want(java.time.Duration.ofSeconds(1234)),
      new GetFromTestCase()
          .field("single_timestamp")
          .want(Instant.ofEpochSecond(12345).atZone(TimestampT.ZoneIdZ)),
      new GetFromTestCase().field("single_bool_wrapper").want(false),
      new GetFromTestCase().field("single_int32_wrapper").want(42),
      new GetFromTestCase().field("single_int64_wrapper").want(NullValue.NULL_VALUE),
      new GetFromTestCase()
          .field("single_nested_message")
          .want(NestedMessage.newBuilder().setBb(123).build()),
      new GetFromTestCase().field("standalone_enum").want(NestedEnum.BAR.getValueDescriptor()),
      new GetFromTestCase().field("single_value").want("hello world"),
      new GetFromTestCase()
          .field("single_struct")
          .want(
              Struct.newBuilder()
                  .putFields("null", Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build())
                  .build()),
      new GetFromTestCase()
          .field("repeated_uint32")
          .want(Arrays.asList(ULong.valueOf(1L), ULong.valueOf(2L), ULong.valueOf(3L))),
    };
  }

  @ParameterizedTest
  @MethodSource("getFromTestCases")
  void getFrom(GetFromTestCase tc) {
    Db pbdb = newDb();
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    builder.setSingleUint64(12);
    builder.setSingleDuration(Duration.newBuilder().setSeconds(1234));
    builder.setSingleTimestamp(Timestamp.newBuilder().setSeconds(12345).setNanos(0));
    builder.setSingleBoolWrapper(BoolValue.of(false));
    builder.setSingleInt32Wrapper(Int32Value.of(42));
    builder.setStandaloneEnum(NestedEnum.BAR);
    builder.setSingleNestedMessage(NestedMessage.newBuilder().setBb(123));
    builder.setSingleValue(Value.newBuilder().setStringValue("hello world"));
    builder.setSingleStruct(
        Struct.newBuilder()
            .putFields("null", Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()));
    builder.addRepeatedUint32(1);
    builder.addRepeatedUint32(2);
    builder.addRepeatedUint32(3);
    TestAllTypes msg = builder.build();
    String msgName = msg.getDescriptorForType().getFullName();
    pbdb.registerMessage(msg);
    PbTypeDescription td = pbdb.describeType(msgName);
    assertThat(td).isNotNull();

    FieldDescription f = td.fieldByName(tc.field);
    assertThat(f).isNotNull();
    Object got = f.getFrom(pbdb, msg);
    assertThat(got).isEqualTo(tc.want);
  }

  @Test
  void getFromUnsignedMapEntries() {
    Db pbdb = newDb();
    TestAllTypes msg =
        TestAllTypes.newBuilder()
            .putMapStringUint64("large", -1L)
            .putMapUint64String(-1L, "large")
            .putMapUint64Uint64(-1L, Long.MIN_VALUE)
            .build();
    String msgName = msg.getDescriptorForType().getFullName();
    pbdb.registerMessage(msg);
    PbTypeDescription td = pbdb.describeType(msgName);
    assertThat(td).isNotNull();

    assertThat(td.fieldByName("map_string_uint64").getFrom(pbdb, msg))
        .isEqualTo(Collections.singletonMap("large", ULong.valueOf(-1L)));
    assertThat(td.fieldByName("map_uint64_string").getFrom(pbdb, msg))
        .isEqualTo(Collections.singletonMap(ULong.valueOf(-1L), "large"));
    assertThat(td.fieldByName("map_uint64_uint64").getFrom(pbdb, msg))
        .isEqualTo(Collections.singletonMap(ULong.valueOf(-1L), ULong.valueOf(Long.MIN_VALUE)));

    DynamicMessage dynMsg =
        DynamicMessage.newBuilder(msg.getDescriptorForType()).mergeFrom(msg).build();
    assertThat(td.fieldByName("map_string_uint64").getFrom(pbdb, dynMsg))
        .isEqualTo(Collections.singletonMap("large", ULong.valueOf(-1L)));
    assertThat(td.fieldByName("map_uint64_string").getFrom(pbdb, dynMsg))
        .isEqualTo(Collections.singletonMap(ULong.valueOf(-1L), "large"));
    assertThat(td.fieldByName("map_uint64_uint64").getFrom(pbdb, dynMsg))
        .isEqualTo(Collections.singletonMap(ULong.valueOf(-1L), ULong.valueOf(Long.MIN_VALUE)));
  }

  @Test
  void getFieldProtoMapSupportsRepeatedLookup() {
    Db pbdb = newDb();
    ProtoTypeRegistry registry = ProtoTypeRegistry.newRegistry(TestAllTypes.getDefaultInstance());
    TestAllTypes msg =
        TestAllTypes.newBuilder()
            .putMapUint64String(1L, "one")
            .putMapUint64String(2L, "two")
            .putMapUint64String(-1L, "large")
            .build();
    pbdb.registerMessage(msg);
    PbTypeDescription td = pbdb.describeType(msg.getDescriptorForType().getFullName());
    assertThat(td).isNotNull();

    FieldDescription field = td.fieldByName("map_uint64_string");
    assertThat(field).isNotNull();

    Mapper map = (Mapper) field.getField(msg, registry);
    assertThat(map.find(uintOf(2)).equal(stringOf("two"))).isSameAs(True);
    assertThat(map.find(uintOf(-1L)).equal(stringOf("large"))).isSameAs(True);
    assertThat(map.find(uintOf(1)).equal(stringOf("one"))).isSameAs(True);
    assertThat(map.find(uintOf(42))).isNull();
    assertThat(map.contains(uintOf(2))).isSameAs(True);
    assertThat(map.contains(uintOf(42))).isSameAs(False);
  }

  static class TestCase {
    Message msg;
    String field;
    boolean isSet;

    TestCase msg(Message msg) {
      this.msg = msg;
      return this;
    }

    TestCase field(String field) {
      this.field = field;
      return this;
    }

    TestCase isSet(boolean set) {
      isSet = set;
      return this;
    }

    @Override
    public String toString() {
      return (msg != null ? (msg.getDescriptorForType().toString() + '.') : "null")
          + field
          + " "
          + isSet;
    }
  }

  @SuppressWarnings("unused")
  static TestCase[] isSetTestCases() {
    return new TestCase[] {
      new TestCase()
          .msg(TestAllTypes.newBuilder().setSingleBool(true).build())
          .field("single_bool")
          .isSet(true),
      new TestCase().msg(TestAllTypes.getDefaultInstance()).field("single_bool").isSet(false),
      new TestCase()
          .msg(TestAllTypes.newBuilder().setSingleBool(false).build())
          .field("single_bool")
          .isSet(false),
      new TestCase().msg(TestAllTypes.getDefaultInstance()).field("single_bool").isSet(false),
      new TestCase().msg(TestAllTypes.getDefaultInstance()).field("single_bool").isSet(false),
      new TestCase().msg(null).field("single_any").isSet(false)
    };
  }

  @ParameterizedTest
  @MethodSource("isSetTestCases")
  void isSet(TestCase tc) {
    Db pbdb = newDb();
    TestAllTypes msg = TestAllTypes.getDefaultInstance();
    String msgName = msg.getDescriptorForType().getFullName();
    pbdb.registerMessage(msg);
    PbTypeDescription td = pbdb.describeType(msgName);
    assertThat(td).isNotNull();

    FieldDescription f = td.fieldByName(tc.field);
    assertThat(f).isNotNull();
    assertThat(f.isSet(tc.msg)).isEqualTo(tc.isSet);
  }
}
