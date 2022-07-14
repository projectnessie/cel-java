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

import static com.google.protobuf.NullValue.NULL_VALUE;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.Util.mapOf;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.DurationT.durationOf;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.ListT.newGenericArrayList;
import static org.projectnessie.cel.common.types.MapT.newMaybeWrappedMap;
import static org.projectnessie.cel.common.types.NullT.NullValue;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.ZoneIdZ;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newEmptyRegistry;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newRegistry;

import com.google.api.expr.test.v1.proto3.TestAllTypesProto.GlobalEnum;
import com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes;
import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Indexer;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.test.proto3.OutOfOrderEnumOuterClass;

public class ProviderTest {

  @Test
  void typeRegistryCopy() {
    TypeRegistry reg = newEmptyRegistry();
    TypeRegistry reg2 = reg.copy();
    assertThat(reg).isEqualTo(reg2);

    reg = newRegistry();
    reg2 = reg.copy();
    assertThat(reg).isEqualTo(reg2);
  }

  @Test
  void typeRegistryEnumValue() {
    ProtoTypeRegistry reg = newEmptyRegistry();
    reg.registerDescriptor(GlobalEnum.getDescriptor().getFile());
    reg.registerDescriptor(OutOfOrderEnumOuterClass.getDescriptor().getFile());

    Val enumVal = reg.enumValue("google.api.expr.test.v1.proto3.GlobalEnum.GOO");
    assertThat(enumVal).extracting(Val::intValue).isEqualTo((long) GlobalEnum.GOO.getNumber());

    Val enumVal2 = reg.findIdent("google.api.expr.test.v1.proto3.GlobalEnum.GOO");
    assertThat(enumVal2.equal(enumVal)).isSameAs(True);

    // Previously, we checked `getIndex` on the `EnumValueDescriptor`, which is the same as the
    // `ordinal` value on the enum.
    // Test the case where the protobuf-defined value for the enum differs from the generated Java
    // enum's ordinal() function.
    assertThat(OutOfOrderEnumOuterClass.OutOfOrderEnum.TWO.ordinal())
        .isEqualTo(OutOfOrderEnumOuterClass.OutOfOrderEnum.TWO.getValueDescriptor().getIndex())
        .isNotEqualTo(OutOfOrderEnumOuterClass.OutOfOrderEnum.TWO.getNumber());
    // Check that we correctly get the protobuf-defined number.
    Val enumVal3 = reg.enumValue("org.projectnessie.cel.test.proto3.OutOfOrderEnum.TWO");
    assertThat(enumVal3)
        .extracting(Val::intValue)
        .isEqualTo((long) OutOfOrderEnumOuterClass.OutOfOrderEnum.TWO.getNumber());

    // Test also with the case where there's a gap in the enum (FOUR is not defined).
    assertThat(OutOfOrderEnumOuterClass.OutOfOrderEnum.FIVE.ordinal())
        .isEqualTo(OutOfOrderEnumOuterClass.OutOfOrderEnum.FIVE.getValueDescriptor().getIndex())
        .isNotEqualTo(OutOfOrderEnumOuterClass.OutOfOrderEnum.FIVE.getNumber());
    // Check that we correctly get the protobuf-defined number.
    Val enumVal4 = reg.enumValue("org.projectnessie.cel.test.proto3.OutOfOrderEnum.FIVE");
    assertThat(enumVal4)
        .extracting(Val::intValue)
        .isEqualTo((long) OutOfOrderEnumOuterClass.OutOfOrderEnum.FIVE.getNumber());
  }

  @Test
  void typeRegistryFindType() {
    ProtoTypeRegistry reg = newEmptyRegistry();
    reg.registerDescriptor(GlobalEnum.getDescriptor().getFile());

    String msgTypeName = "google.api.expr.test.v1.proto3.TestAllTypes";
    assertThat(reg.findType(msgTypeName)).isNotNull();
    // assertThat(reg.findType(msgTypeName + "Undefined")).isNotNull(); ... this doesn't exist in
    // protobuf-java
    assertThat(reg.findFieldType(msgTypeName, "single_bool")).isNotNull();
    assertThat(reg.findFieldType(msgTypeName, "double_bool")).isNull();
  }

  @Test
  void typeRegistryNewValue() {
    TypeRegistry reg = newRegistry(ParsedExpr.getDefaultInstance());
    Val sourceInfo =
        reg.newValue(
            "google.api.expr.v1alpha1.SourceInfo",
            mapOf(
                "location", stringOf("TestTypeRegistryNewValue"),
                "line_offsets", newGenericArrayList(reg, new Long[] {0L, 2L}),
                "positions", newMaybeWrappedMap(reg, mapOf(1L, 2, 3L, 4))));
    assertThat(sourceInfo).matches(v -> !Err.isError(v));
    Message info = (Message) sourceInfo.value();
    SourceInfo srcInfo = SourceInfo.newBuilder().mergeFrom(info).build();
    assertThat(srcInfo)
        .extracting(
            SourceInfo::getLocation, SourceInfo::getLineOffsetsList, SourceInfo::getPositionsMap)
        .containsExactly("TestTypeRegistryNewValue", asList(0, 2), mapOf(1L, 2L, 3L, 4L));
  }

  @Test
  void typeRegistryNewValue_OneofFields() {
    TypeRegistry reg =
        newRegistry(CheckedExpr.getDefaultInstance(), ParsedExpr.getDefaultInstance());
    Val exp =
        reg.newValue(
            "google.api.expr.v1alpha1.CheckedExpr",
            mapOf(
                "expr",
                reg.newValue(
                    "google.api.expr.v1alpha1.Expr",
                    mapOf(
                        "const_expr",
                        reg.newValue(
                            "google.api.expr.v1alpha1.Constant",
                            mapOf("string_value", stringOf("oneof")))))));

    assertThat(exp).matches(v -> !Err.isError(v));
    CheckedExpr ce = exp.convertToNative(CheckedExpr.class);
    assertThat(ce)
        .extracting(CheckedExpr::getExpr)
        .extracting(Expr::getConstExpr)
        .extracting(Constant::getStringValue)
        .isEqualTo("oneof");
  }

  @Test
  void typeRegistryNewValue_WrapperFields() {
    TypeRegistry reg = newRegistry(TestAllTypes.getDefaultInstance());
    Val exp =
        reg.newValue(
            "google.api.expr.test.v1.proto3.TestAllTypes",
            mapOf("single_int32_wrapper", intOf(123)));
    assertThat(exp).matches(v -> !Err.isError(v));
    TestAllTypes ce = exp.convertToNative(TestAllTypes.class);
    assertThat(ce)
        .extracting(TestAllTypes::getSingleInt32Wrapper)
        .extracting(Int32Value::getValue)
        .isEqualTo(123);
  }

  @Test
  void typeRegistryGetters() {
    TypeRegistry reg = newRegistry(ParsedExpr.getDefaultInstance());
    Val sourceInfo =
        reg.newValue(
            "google.api.expr.v1alpha1.SourceInfo",
            mapOf(
                "location", stringOf("TestTypeRegistryGetFieldValue"),
                "line_offsets", newGenericArrayList(reg, new Long[] {0L, 2L}),
                "positions", newMaybeWrappedMap(reg, mapOf(1L, 2L, 3L, 4L))));
    assertThat(sourceInfo).matches(v -> !Err.isError(v));
    Indexer si = (Indexer) sourceInfo;

    Val loc = si.get(stringOf("location"));
    assertThat(loc.equal(stringOf("TestTypeRegistryGetFieldValue"))).isSameAs(True);

    Val pos = si.get(stringOf("positions"));
    assertThat(pos.equal(newMaybeWrappedMap(reg, mapOf(1, 2, 3, 4)))).isSameAs(True);

    Val posKeyVal = ((Indexer) pos).get(intOf(1));
    assertThat(posKeyVal.intValue()).isEqualTo(2);

    Val offsets = si.get(stringOf("line_offsets"));
    assertThat(offsets).matches(v -> !Err.isError(v));
    Val offset1 = ((Lister) offsets).get(intOf(1));
    assertThat(offset1).matches(v -> !Err.isError(v)).isEqualTo(intOf(2));
  }

  @Test
  void convertToNative() {
    TypeRegistry reg = newRegistry(ParsedExpr.getDefaultInstance());

    // Core type conversion tests.
    expectValueToNative(True, true);
    expectValueToNative(True, True);
    expectValueToNative(
        newGenericArrayList(reg, new Val[] {True, False}), new Object[] {true, false});
    expectValueToNative(newGenericArrayList(reg, new Val[] {True, False}), new Val[] {True, False});
    expectValueToNative(intOf(-1), -1);
    expectValueToNative(intOf(2), 2L);
    expectValueToNative(intOf(-1), -1);
    expectValueToNative(newGenericArrayList(reg, new Val[] {intOf(4)}), new Object[] {4L});
    expectValueToNative(newGenericArrayList(reg, new Val[] {intOf(5)}), new Val[] {intOf(5)});
    expectValueToNative(uintOf(3), ULong.valueOf(3));
    expectValueToNative(uintOf(4), ULong.valueOf(4));
    expectValueToNative(uintOf(5), 5);
    expectValueToNative(
        newGenericArrayList(reg, new Val[] {uintOf(4)}), new Object[] {4L}); // loses "ULong" here
    expectValueToNative(newGenericArrayList(reg, new Val[] {uintOf(5)}), new Val[] {uintOf(5)});
    expectValueToNative(doubleOf(5.5d), 5.5f);
    expectValueToNative(doubleOf(-5.5d), -5.5d);
    expectValueToNative(newGenericArrayList(reg, new Val[] {doubleOf(-5.5)}), new Object[] {-5.5});
    expectValueToNative(
        newGenericArrayList(reg, new Val[] {doubleOf(-5.5)}), new Val[] {doubleOf(-5.5)});
    expectValueToNative(doubleOf(-5.5), doubleOf(-5.5));
    expectValueToNative(stringOf("hello"), "hello");
    expectValueToNative(stringOf("hello"), stringOf("hello"));
    expectValueToNative(NullValue, NULL_VALUE);
    expectValueToNative(NullValue, NullValue);
    expectValueToNative(newGenericArrayList(reg, new Val[] {NullValue}), new Object[] {null});
    expectValueToNative(newGenericArrayList(reg, new Val[] {NullValue}), new Val[] {NullValue});
    expectValueToNative(bytesOf("world"), "world".getBytes(StandardCharsets.UTF_8));
    expectValueToNative(bytesOf("world"), "world".getBytes(StandardCharsets.UTF_8));
    expectValueToNative(
        newGenericArrayList(reg, new Val[] {bytesOf("hello")}),
        new Object[] {ByteString.copyFromUtf8("hello")});
    expectValueToNative(
        newGenericArrayList(reg, new Val[] {bytesOf("hello")}), new Val[] {bytesOf("hello")});
    expectValueToNative(
        newGenericArrayList(reg, new Val[] {intOf(1), intOf(2), intOf(3)}),
        new Object[] {1L, 2L, 3L});
    expectValueToNative(durationOf(Duration.ofSeconds(500)), Duration.ofSeconds(500));
    expectValueToNative(
        durationOf(Duration.ofSeconds(500)),
        com.google.protobuf.Duration.newBuilder().setSeconds(500).build());
    expectValueToNative(durationOf(Duration.ofSeconds(500)), durationOf(Duration.ofSeconds(500)));
    expectValueToNative(
        timestampOf(Timestamp.newBuilder().setSeconds(12345).build()),
        Instant.ofEpochSecond(12345, 0).atZone(ZoneIdZ));
    expectValueToNative(
        timestampOf(Timestamp.newBuilder().setSeconds(12345).build()),
        timestampOf(Timestamp.newBuilder().setSeconds(12345).build()));
    expectValueToNative(
        timestampOf(Timestamp.newBuilder().setSeconds(12345).build()),
        Timestamp.newBuilder().setSeconds(12345).build());
    expectValueToNative(
        newMaybeWrappedMap(reg, mapOf(1L, 1L, 2L, 1L, 3L, 1L)), mapOf(1L, 1L, 2L, 1L, 3L, 1L));

    // Null conversion tests.
    expectValueToNative(NullValue, NULL_VALUE);

    // Proto conversion tests.
    ParsedExpr parsedExpr = ParsedExpr.getDefaultInstance();
    expectValueToNative(reg.nativeToValue(parsedExpr), parsedExpr);
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void nativeToValue_Any() {
    //    		TypeRegistry reg = newRegistry(ParsedExpr.getDefaultInstance())
    //    		// NullValue
    //    		Any anyValue = NullValue.convertToNative(Any.class);
    //    		expectNativeToValue(anyValue, NullValue);
    //
    //    		// Json Struct
    //    		anyValue = anypb.New(
    //    			structpb.NewStructValue(
    //    				&structpb.Struct{
    //    					Fields: map[string]*structpb.Value{
    //    						"a": structpb.NewStringValue("world"),
    //    						"b": structpb.NewStringValue("five!"),
    //    					},
    //    				},
    //    			),
    //    		);
    //    		expected = newJSONStruct(reg, &structpb.Struct{
    //    			Fields: map[string]*structpb.Value{
    //    				"a": structpb.NewStringValue("world"),
    //    				"b": structpb.NewStringValue("five!"),
    //    			},
    //    		})
    //    		expectNativeToValue(anyValue, expected)
    //
    //    		//Json List
    //    		anyValue = anypb.New(structpb.NewListValue(
    //    			&structpb.ListValue{
    //    				Values: []*structpb.Value{
    //    					structpb.NewStringValue("world"),
    //    					structpb.NewStringValue("five!"),
    //    				},
    //    			},
    //    		));
    //    		expectedList = newJSONList(reg, &structpb.ListValue{
    //    			Values: []*structpb.Value{
    //    				structpb.NewStringValue("world"),
    //    				structpb.NewStringValue("five!"),
    //    			}})
    //    		expectNativeToValue(anyValue, expectedList)
    //
    //    		// Object
    //    		pbMessage = exprpb.ParsedExpr{
    //    			SourceInfo: &exprpb.SourceInfo{
    //    				LineOffsets: []int32{1, 2, 3}}}
    //    		anyValue = anypb.New(&pbMessage);
    //    		expectNativeToValue(anyValue, reg.nativeToValue(&pbMessage))
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void nativeToValue_Json() {
    //    		TypeRegistry reg = newRegistry(ParsedExpr.getDefaultInstance())
    //    		// Json primitive conversion test.
    //    		expectNativeToValue(BoolValue.of(false), False);
    //    		expectNativeToValue(Value.newBuilder().setNumberValue(1.1d).build(), doubleOf(1.1));
    //    		expectNativeToValue(com.google.protobuf.NullValue.forNumber(0), NullValue);
    //    		expectNativeToValue(StringValue.of("hello"), stringOf("hello"));
    //
    //    		// Json list conversion.
    //    		expectNativeToValue(
    //						ListValue.newBuilder()
    //						.addValues(Value.newBuilder().setStringValue("world"))
    //						.addValues(Value.newBuilder().setStringValue("five!"))
    //						.build(),
    //    			newJSONList(reg, ListValue.newBuilder()
    //							.addValues(Value.newBuilder().setStringValue("world"))
    //							.addValues(Value.newBuilder().setStringValue("five!"))
    //							.build()));
    //
    //    		// Json struct conversion.
    //    		expectNativeToValue(
    //						Struct.newBuilder()
    //							.putFields("a", Value.newBuilder().setStringValue("world").build())
    //							.putFields("b", Value.newBuilder().setStringValue("five!").build())
    //							.build(),
    //    			newJSONStruct(reg, Struct.newBuilder()
    //							.putFields("a", Value.newBuilder().setStringValue("world").build())
    //							.putFields("b", Value.newBuilder().setStringValue("five!").build())
    //							.build()));
    //
    //    		// Proto conversion test.
    //    		ParsedExpr parsedExpr = ParsedExpr.getDefaultInstance();
    //    		expectNativeToValue(parsedExpr, reg.nativeToValue(parsedExpr));
  }

  @Test
  void nativeToValue_Wrappers() {
    // Wrapper conversion test.
    expectNativeToValue(BoolValue.of(true), True);
    expectNativeToValue(BoolValue.getDefaultInstance(), False);
    expectNativeToValue(BytesValue.getDefaultInstance(), bytesOf(""));
    expectNativeToValue(BytesValue.of(ByteString.copyFromUtf8("hi")), bytesOf("hi"));
    expectNativeToValue(DoubleValue.getDefaultInstance(), doubleOf(0.0));
    expectNativeToValue(DoubleValue.of(6.4), doubleOf(6.4));
    expectNativeToValue(FloatValue.getDefaultInstance(), doubleOf(0.0));
    expectNativeToValue(FloatValue.of(3.0f), doubleOf(3.0));
    expectNativeToValue(Int32Value.getDefaultInstance(), IntZero);
    expectNativeToValue(Int32Value.of(-32), intOf(-32));
    expectNativeToValue(Int64Value.getDefaultInstance(), IntZero);
    expectNativeToValue(Int64Value.of(-64), intOf(-64));
    expectNativeToValue(StringValue.getDefaultInstance(), stringOf(""));
    expectNativeToValue(StringValue.of("hello"), stringOf("hello"));
    expectNativeToValue(UInt32Value.getDefaultInstance(), uintOf(0));
    expectNativeToValue(UInt32Value.of(32), uintOf(32));
    expectNativeToValue(UInt64Value.getDefaultInstance(), uintOf(0));
    expectNativeToValue(UInt64Value.of(64), uintOf(64));
  }

  @Test
  void nativeToValue_Primitive() {
    TypeRegistry reg = newEmptyRegistry();

    // Core type conversions.
    expectNativeToValue(true, True);
    expectNativeToValue(-10, intOf(-10));
    expectNativeToValue(-1, intOf(-1));
    expectNativeToValue(2L, intOf(2));
    expectNativeToValue(ULong.valueOf(6), uintOf(6));
    expectNativeToValue(ULong.valueOf(3), uintOf(3));
    expectNativeToValue(ULong.valueOf(4), uintOf(4));
    expectNativeToValue(5.5f, doubleOf(5.5));
    expectNativeToValue(-5.5d, doubleOf(-5.5));
    expectNativeToValue("hello", stringOf("hello"));
    expectNativeToValue("world".getBytes(StandardCharsets.UTF_8), bytesOf("world"));
    expectNativeToValue(Duration.ofSeconds(500), durationOf(Duration.ofSeconds(500)));
    expectNativeToValue(
        new Date(TimeUnit.SECONDS.toMillis(12345)),
        timestampOf(Timestamp.newBuilder().setSeconds(12345).build()));
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(TimeUnit.SECONDS.toMillis(12345));
    expectNativeToValue(
        new Date(TimeUnit.SECONDS.toMillis(12345)),
        timestampOf(Timestamp.newBuilder().setSeconds(12345).build()));
    expectNativeToValue(
        Instant.ofEpochSecond(12345).atZone(ZoneIdZ),
        timestampOf(Timestamp.newBuilder().setSeconds(12345).build()));
    expectNativeToValue(Duration.ofSeconds(500), durationOf(Duration.ofSeconds(500)));
    expectNativeToValue(new int[] {1, 2, 3}, newGenericArrayList(reg, new Object[] {1, 2, 3}));
    expectNativeToValue(mapOf(1, 1, 2, 1, 3, 1), newMaybeWrappedMap(reg, mapOf(1, 1, 2, 1, 3, 1)));

    // Null conversion test.
    expectNativeToValue(null, NullValue);
    expectNativeToValue(NULL_VALUE, NullValue);
  }

  @Test
  void unsupportedConversion() {
    TypeRegistry reg = newEmptyRegistry();
    Val val = reg.nativeToValue(new nonConvertible());
    assertThat(val).matches(Err::isError);
  }

  static class nonConvertible {}

  static void expectValueToNative(Val in, Object out) {
    Object val = in.convertToNative(out.getClass());
    assertThat(val).isNotNull();

    if (val instanceof byte[]) {
      assertThat((byte[]) val).containsExactly((byte[]) in.value());
    } else {
      assertThat(val).isEqualTo(out);
    }
  }

  static void expectNativeToValue(Object in, Val out) {
    TypeRegistry reg = newRegistry(ParsedExpr.getDefaultInstance());
    Val val = reg.nativeToValue(in);
    assertThat(val).matches(v -> !Err.isError(v));
    assertThat(val.equal(out)).isSameAs(True);
  }
}
