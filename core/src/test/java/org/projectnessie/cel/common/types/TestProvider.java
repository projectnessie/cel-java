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

public class TestProvider {

  //  @Test
  //	void TypeRegistryCopy() {
  //		reg := NewEmptyRegistry()
  //		reg2 := reg.Copy()
  //		if !reflect.DeepEqual(reg, reg2) {
  //			t.Fatal("type registry copy did not produce equivalent values.")
  //		}
  //		reg = newTestRegistry(t)
  //		reg2 = reg.Copy()
  //		if !reflect.DeepEqual(reg, reg2) {
  //			t.Fatal("type registry copy did not produce equivalent values.")
  //		}
  //	}
  //
  //  @Test
  //	void TypeRegistryEnumValue() {
  //		reg := newTestRegistry(t)
  //		err := reg.RegisterDescriptor(proto3pb.GlobalEnum_GOO.Descriptor().ParentFile())
  //		if err != nil {
  //			t.Fatalf("RegisterDescriptor() failed: %v", err)
  //		}
  //		enumVal := reg.EnumValue("google.expr.proto3.test.GlobalEnum.GOO")
  //		if Int(proto3pb.GlobalEnum_GOO.Number()) != enumVal.(Int) {
  //			t.Errorf("enum values were not equal between registry and proto: %v", enumVal)
  //		}
  //		enumVal2, found := reg.FindIdent("google.expr.proto3.test.GlobalEnum.GOO")
  //		if !found {
  //			t.Fatal("Ident not found google.expr.proto3.test.GlobalEnum.GOO")
  //		}
  //		if enumVal.(Int) != enumVal2.(Int) {
  //			t.Errorf("got enum value %v, wanted %v", enumVal2, enumVal)
  //		}
  //	}
  //
  //  @Test
  //	void TypeRegistryFindType() {
  //		reg := newTestRegistry(t)
  //		err := reg.RegisterDescriptor(proto3pb.GlobalEnum_GOO.Descriptor().ParentFile())
  //		if err != nil {
  //			t.Fatalf("RegisterDescriptor() failed: %v", err)
  //		}
  //		msgTypeName := ".google.expr.proto3.test.TestAllTypes"
  //		_, found := reg.FindType(msgTypeName)
  //		if !found {
  //			t.Fatalf("FindType() did not find: %q", msgTypeName)
  //		}
  //		_, found = reg.FindType(msgTypeName + "Undefined")
  //		if found {
  //			t.Fatalf("FindType() found: %q", msgTypeName+"Undefined")
  //		}
  //		_, found = reg.FindFieldType(msgTypeName, "single_bool")
  //		if !found {
  //			t.Fatalf("FindFieldType() did not find: %q, %s", msgTypeName, "single_bool")
  //		}
  //		_, found = reg.FindFieldType(msgTypeName, "double_bool")
  //		if found {
  //			t.Fatalf("FindFieldType() found: %q, %s", msgTypeName, "double_bool")
  //		}
  //	}
  //
  //  @Test
  //	void TypeRegistryNewValue() {
  //		reg := newTestRegistry(t, &exprpb.ParsedExpr{})
  //		sourceInfo := reg.NewValue(
  //			"google.api.expr.v1alpha1.SourceInfo",
  //			map[string]ref.Val{
  //				"location":     String("TestTypeRegistryNewValue"),
  //				"line_offsets": NewDynamicList(reg, []int64{0, 2}),
  //				"positions":    NewDynamicMap(reg, map[int64]int64{1: 2, 2: 4}),
  //			})
  //		if IsError(sourceInfo) {
  //			t.Error(sourceInfo)
  //		} else {
  //			info := sourceInfo.Value().(proto.Message)
  //			srcInfo := &exprpb.SourceInfo{}
  //			proto.Merge(srcInfo, info)
  //			if srcInfo.Location != "TestTypeRegistryNewValue" ||
  //				!reflect.DeepEqual(srcInfo.LineOffsets, []int32{0, 2}) ||
  //				!reflect.DeepEqual(srcInfo.Positions, map[int64]int32{1: 2, 2: 4}) {
  //				t.Errorf("Source info not properly configured: %v", info)
  //			}
  //		}
  //	}
  //
  //  @Test
  //	void TypeRegistryNewValue_OneofFields() {
  //		reg := newTestRegistry(t, &exprpb.CheckedExpr{}, &exprpb.ParsedExpr{})
  //		exp := reg.NewValue(
  //			"google.api.expr.v1alpha1.CheckedExpr",
  //			map[string]ref.Val{
  //				"expr": reg.NewValue(
  //					"google.api.expr.v1alpha1.Expr",
  //					map[string]ref.Val{
  //						"const_expr": reg.NewValue(
  //							"google.api.expr.v1alpha1.Constant",
  //							map[string]ref.Val{
  //								"string_value": String("oneof"),
  //							}),
  //					}),
  //			})
  //		if IsError(exp) {
  //			t.Fatalf("reg.NewValue() creation failed: %v", exp)
  //		}
  //		e, err := exp.ConvertToNative(reflect.TypeOf(&exprpb.CheckedExpr{}))
  //		if err != nil {
  //			t.Fatalf("ConvertToNative() failed: %v", err)
  //		}
  //		ce := e.(*exprpb.CheckedExpr)
  //		if ce.GetExpr().GetConstExpr().GetStringValue() != "oneof" {
  //			t.Errorf("Expr with oneof could not be created: %v", ce)
  //		}
  //	}
  //
  //  @Test
  //	void TypeRegistryNewValue_WrapperFields() {
  //		reg := newTestRegistry(t, &proto3pb.TestAllTypes{})
  //		exp := reg.NewValue(
  //			"google.expr.proto3.test.TestAllTypes",
  //			map[string]ref.Val{
  //				"single_int32_wrapper": Int(123),
  //			})
  //		if IsError(exp) {
  //			t.Fatalf("reg.NewValue() creation failed: %v", exp)
  //		}
  //		e, err := exp.ConvertToNative(reflect.TypeOf(&proto3pb.TestAllTypes{}))
  //		if err != nil {
  //			t.Fatalf("ConvertToNative() failed: %v", err)
  //		}
  //		ce := e.(*proto3pb.TestAllTypes)
  //		if ce.GetSingleInt32Wrapper().GetValue() != int32(123) {
  //			t.Errorf("single_int32_wrapper value %v not set to 123", ce)
  //		}
  //	}
  //
  //  @Test
  //	void TypeRegistryGetters() {
  //		reg := newTestRegistry(t, &exprpb.ParsedExpr{})
  //		if sourceInfo := reg.NewValue(
  //			"google.api.expr.v1alpha1.SourceInfo",
  //			map[string]ref.Val{
  //				"location":     String("TestTypeRegistryGetFieldValue"),
  //				"line_offsets": NewDynamicList(reg, []int64{0, 2}),
  //				"positions":    NewDynamicMap(reg, map[int64]int64{1: 2, 2: 4}),
  //			}); IsError(sourceInfo) {
  //			t.Error(sourceInfo)
  //		} else {
  //			si := sourceInfo.(traits.Indexer)
  //			if loc := si.Get(String("location")); IsError(loc) {
  //				t.Error(loc)
  //			} else if loc.(String) != "TestTypeRegistryGetFieldValue" {
  //				t.Errorf("Expected %s, got %s", "TestTypeRegistryGetFieldValue", loc)
  //			}
  //			if pos := si.Get(String("positions")); IsError(pos) {
  //				t.Error(pos)
  //			} else if pos.Equal(NewDynamicMap(reg, map[int64]int32{1: 2, 2: 4})) != True {
  //				t.Errorf("Expected map[int64]int32, got %v", pos)
  //			} else if posKeyVal := pos.(traits.Indexer).Get(Int(1)); IsError(posKeyVal) {
  //				t.Error(posKeyVal)
  //			} else if posKeyVal.(Int) != 2 {
  //				t.Error("Expected value to be int64, not int32")
  //			}
  //			if offsets := si.Get(String("line_offsets")); IsError(offsets) {
  //				t.Error(offsets)
  //			} else if offset1 := offsets.(traits.Lister).Get(Int(1)); IsError(offset1) {
  //				t.Error(offset1)
  //			} else if offset1.(Int) != 2 {
  //				t.Errorf("Expected index 1 to be value 2, was %v", offset1)
  //			}
  //		}
  //	}
  //
  //  @Test
  //	void ConvertToNative() {
  //		reg := newTestRegistry(t, &exprpb.ParsedExpr{})
  //
  //		// Core type conversion tests.
  //		expectValueToNative(t, True, true)
  //		expectValueToNative(t, True, True)
  //		expectValueToNative(t, NewDynamicList(reg, []Bool{True, False}), []interface{}{true, false})
  //		expectValueToNative(t, NewDynamicList(reg, []Bool{True, False}), []ref.Val{True, False})
  //		expectValueToNative(t, Int(-1), int32(-1))
  //		expectValueToNative(t, Int(2), int64(2))
  //		expectValueToNative(t, Int(-1), Int(-1))
  //		expectValueToNative(t, NewDynamicList(reg, []Int{4}), []interface{}{int64(4)})
  //		expectValueToNative(t, NewDynamicList(reg, []Int{5}), []ref.Val{Int(5)})
  //		expectValueToNative(t, Uint(3), uint32(3))
  //		expectValueToNative(t, Uint(4), uint64(4))
  //		expectValueToNative(t, Uint(5), Uint(5))
  //		expectValueToNative(t, NewDynamicList(reg, []Uint{4}), []interface{}{uint64(4)})
  //		expectValueToNative(t, NewDynamicList(reg, []Uint{5}), []ref.Val{Uint(5)})
  //		expectValueToNative(t, Double(5.5), float32(5.5))
  //		expectValueToNative(t, Double(-5.5), float64(-5.5))
  //		expectValueToNative(t, NewDynamicList(reg, []Double{-5.5}), []interface{}{-5.5})
  //		expectValueToNative(t, NewDynamicList(reg, []Double{-5.5}), []ref.Val{Double(-5.5)})
  //		expectValueToNative(t, Double(-5.5), Double(-5.5))
  //		expectValueToNative(t, String("hello"), "hello")
  //		expectValueToNative(t, String("hello"), String("hello"))
  //		expectValueToNative(t, NullValue, structpb.NullValue_NULL_VALUE)
  //		expectValueToNative(t, NullValue, NullValue)
  //		expectValueToNative(t, NewDynamicList(reg, []Null{NullValue}),
  // []interface{}{structpb.NullValue_NULL_VALUE})
  //		expectValueToNative(t, NewDynamicList(reg, []Null{NullValue}), []ref.Val{NullValue})
  //		expectValueToNative(t, Bytes("world"), []byte("world"))
  //		expectValueToNative(t, Bytes("world"), Bytes("world"))
  //		expectValueToNative(t, NewDynamicList(reg, []Bytes{Bytes("hello")}),
  // []interface{}{[]byte("hello")})
  //		expectValueToNative(t, NewDynamicList(reg, []Bytes{Bytes("hello")}),
  // []ref.Val{Bytes("hello")})
  //		expectValueToNative(t, NewDynamicList(reg, []int64{1, 2, 3}), []int32{1, 2, 3})
  //		expectValueToNative(t, Duration{Duration: time.Duration(500)}, time.Duration(500))
  //		expectValueToNative(t, Duration{Duration: time.Duration(500)}, Duration{Duration:
  // time.Duration(500)})
  //		expectValueToNative(t, Timestamp{Time: time.Unix(12345, 0)}, time.Unix(12345, 0))
  //		expectValueToNative(t, Timestamp{Time: time.Unix(12345, 0)}, Timestamp{Time: time.Unix(12345,
  // 0)})
  //		expectValueToNative(t, NewDynamicMap(reg,
  //			map[int64]int64{1: 1, 2: 1, 3: 1}),
  //			map[int32]int32{1: 1, 2: 1, 3: 1})
  //
  //		// Null conversion tests.
  //		expectValueToNative(t, Null(structpb.NullValue_NULL_VALUE), structpb.NullValue_NULL_VALUE)
  //
  //		// Proto conversion tests.
  //		parsedExpr := &exprpb.ParsedExpr{}
  //		expectValueToNative(t, reg.NativeToValue(parsedExpr), parsedExpr)
  //	}
  //
  //  @Test
  //	void NativeToValue_Any() {
  //		reg := newTestRegistry(t, &exprpb.ParsedExpr{})
  //		// NullValue
  //		anyValue, err := NullValue.ConvertToNative(anyValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		expectNativeToValue(t, anyValue, NullValue)
  //
  //		// Json Struct
  //		anyValue, err = anypb.New(
  //			structpb.NewStructValue(
  //				&structpb.Struct{
  //					Fields: map[string]*structpb.Value{
  //						"a": structpb.NewStringValue("world"),
  //						"b": structpb.NewStringValue("five!"),
  //					},
  //				},
  //			),
  //		)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		expected := NewJSONStruct(reg, &structpb.Struct{
  //			Fields: map[string]*structpb.Value{
  //				"a": structpb.NewStringValue("world"),
  //				"b": structpb.NewStringValue("five!"),
  //			},
  //		})
  //		expectNativeToValue(t, anyValue, expected)
  //
  //		//Json List
  //		anyValue, err = anypb.New(structpb.NewListValue(
  //			&structpb.ListValue{
  //				Values: []*structpb.Value{
  //					structpb.NewStringValue("world"),
  //					structpb.NewStringValue("five!"),
  //				},
  //			},
  //		))
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		expectedList := NewJSONList(reg, &structpb.ListValue{
  //			Values: []*structpb.Value{
  //				structpb.NewStringValue("world"),
  //				structpb.NewStringValue("five!"),
  //			}})
  //		expectNativeToValue(t, anyValue, expectedList)
  //
  //		// Object
  //		pbMessage := exprpb.ParsedExpr{
  //			SourceInfo: &exprpb.SourceInfo{
  //				LineOffsets: []int32{1, 2, 3}}}
  //		anyValue, err = anypb.New(&pbMessage)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		expectNativeToValue(t, anyValue, reg.NativeToValue(&pbMessage))
  //	}
  //
  //  @Test
  //	void NativeToValue_Json() {
  //		reg := newTestRegistry(t, &exprpb.ParsedExpr{})
  //		// Json primitive conversion test.
  //		expectNativeToValue(t, structpb.NewBoolValue(false), False)
  //		expectNativeToValue(t, structpb.NewNumberValue(1.1), Double(1.1))
  //		expectNativeToValue(t, structpb.NewNullValue(), Null(structpb.NullValue_NULL_VALUE))
  //		expectNativeToValue(t, structpb.NewStringValue("hello"), String("hello"))
  //
  //		// Json list conversion.
  //		expectNativeToValue(t,
  //			structpb.NewListValue(
  //				&structpb.ListValue{
  //					Values: []*structpb.Value{
  //						structpb.NewStringValue("world"),
  //						structpb.NewStringValue("five!"),
  //					},
  //				},
  //			),
  //			NewJSONList(reg, &structpb.ListValue{
  //				Values: []*structpb.Value{
  //					structpb.NewStringValue("world"),
  //					structpb.NewStringValue("five!"),
  //				},
  //			}))
  //
  //		// Json struct conversion.
  //		expectNativeToValue(t,
  //			structpb.NewStructValue(
  //				&structpb.Struct{
  //					Fields: map[string]*structpb.Value{
  //						"a": structpb.NewStringValue("world"),
  //						"b": structpb.NewStringValue("five!"),
  //					},
  //				},
  //			),
  //			NewJSONStruct(reg, &structpb.Struct{
  //				Fields: map[string]*structpb.Value{
  //					"a": structpb.NewStringValue("world"),
  //					"b": structpb.NewStringValue("five!"),
  //				},
  //			}))
  //
  //		// Proto conversion test.
  //		parsedExpr := &exprpb.ParsedExpr{}
  //		expectNativeToValue(t, parsedExpr, reg.NativeToValue(parsedExpr))
  //	}
  //
  //  @Test
  //	void NativeToValue_Wrappers() {
  //		// Wrapper conversion test.
  //		expectNativeToValue(t, wrapperspb.Bool(true), True)
  //		expectNativeToValue(t, &wrapperspb.BoolValue{}, False)
  //		expectNativeToValue(t, &wrapperspb.BytesValue{}, Bytes{})
  //		expectNativeToValue(t, wrapperspb.Bytes([]byte("hi")), Bytes("hi"))
  //		expectNativeToValue(t, &wrapperspb.DoubleValue{}, Double(0.0))
  //		expectNativeToValue(t, wrapperspb.Double(6.4), Double(6.4))
  //		expectNativeToValue(t, &wrapperspb.FloatValue{}, Double(0.0))
  //		expectNativeToValue(t, wrapperspb.Float(3.0), Double(3.0))
  //		expectNativeToValue(t, &wrapperspb.Int32Value{}, IntZero)
  //		expectNativeToValue(t, wrapperspb.Int32(-32), Int(-32))
  //		expectNativeToValue(t, &wrapperspb.Int64Value{}, IntZero)
  //		expectNativeToValue(t, wrapperspb.Int64(-64), Int(-64))
  //		expectNativeToValue(t, &wrapperspb.StringValue{}, String(""))
  //		expectNativeToValue(t, wrapperspb.String("hello"), String("hello"))
  //		expectNativeToValue(t, &wrapperspb.UInt32Value{}, Uint(0))
  //		expectNativeToValue(t, wrapperspb.UInt32(32), Uint(32))
  //		expectNativeToValue(t, &wrapperspb.UInt64Value{}, Uint(0))
  //		expectNativeToValue(t, wrapperspb.UInt64(64), Uint(64))
  //	}
  //
  //  @Test
  //	void NativeToValue_Primitive() {
  //		reg := newTestRegistry(t)
  //
  //		// Core type conversions.
  //		expectNativeToValue(t, true, True)
  //		expectNativeToValue(t, int(-10), Int(-10))
  //		expectNativeToValue(t, int32(-1), Int(-1))
  //		expectNativeToValue(t, int64(2), Int(2))
  //		expectNativeToValue(t, uint(6), Uint(6))
  //		expectNativeToValue(t, uint32(3), Uint(3))
  //		expectNativeToValue(t, uint64(4), Uint(4))
  //		expectNativeToValue(t, float32(5.5), Double(5.5))
  //		expectNativeToValue(t, float64(-5.5), Double(-5.5))
  //		expectNativeToValue(t, "hello", String("hello"))
  //		expectNativeToValue(t, []byte("world"), Bytes("world"))
  //		expectNativeToValue(t, time.Duration(500), Duration{Duration: time.Duration(500)})
  //		expectNativeToValue(t, time.Unix(12345, 0), Timestamp{Time: time.Unix(12345, 0)})
  //		expectNativeToValue(t, dpb.New(time.Duration(500)), Duration{Duration: time.Duration(500)})
  //		expectNativeToValue(t, tpb.New(time.Unix(12345, 0)), Timestamp{Time: time.Unix(12345, 0)})
  //		expectNativeToValue(t, []int32{1, 2, 3}, NewDynamicList(reg, []int32{1, 2, 3}))
  //		expectNativeToValue(t, map[int32]int32{1: 1, 2: 1, 3: 1},
  //			NewDynamicMap(reg, map[int32]int32{1: 1, 2: 1, 3: 1}))
  //
  //		// Pointers to core types.
  //		pBool := true
  //		expectNativeToValue(t, &pBool, True)
  //		pDub32 := float32(2.5)
  //		pDub64 := float64(-1000.2)
  //		expectNativeToValue(t, &pDub32, Double(2.5))
  //		expectNativeToValue(t, &pDub64, Double(-1000.2))
  //		pInt := int(1)
  //		pInt32 := int32(2)
  //		pInt64 := int64(-1000)
  //		expectNativeToValue(t, &pInt, Int(1))
  //		expectNativeToValue(t, &pInt32, Int(2))
  //		expectNativeToValue(t, &pInt64, Int(-1000))
  //		pStr := "hello"
  //		expectNativeToValue(t, &pStr, String("hello"))
  //		pUint := uint(1)
  //		pUint32 := uint32(2)
  //		pUint64 := uint64(1000)
  //		expectNativeToValue(t, &pUint, Uint(1))
  //		expectNativeToValue(t, &pUint32, Uint(2))
  //		expectNativeToValue(t, &pUint64, Uint(1000))
  //
  //		// Pointers to ref.Val extensions of core types.
  //		rBool := True
  //		expectNativeToValue(t, &rBool, True)
  //		rDub := Double(32.1)
  //		expectNativeToValue(t, &rDub, rDub)
  //		rInt := Int(-12)
  //		expectNativeToValue(t, &rInt, rInt)
  //		rStr := String("hello")
  //		expectNativeToValue(t, &rStr, rStr)
  //		rUint := Uint(12405)
  //		expectNativeToValue(t, &rUint, rUint)
  //		rBytes := Bytes([]byte("hello"))
  //		expectNativeToValue(t, &rBytes, rBytes)
  //
  //		// Extensions to core types.
  //		expectNativeToValue(t, testInt32(1), Int(1))
  //		expectNativeToValue(t, testInt64(-100), Int(-100))
  //		expectNativeToValue(t, testUint32(2), Uint(2))
  //		expectNativeToValue(t, testUint64(3), Uint(3))
  //		expectNativeToValue(t, testFloat32(4.5), Double(4.5))
  //		expectNativeToValue(t, testFloat64(-5.1), Double(-5.1))
  //
  //		// Null conversion test.
  //		expectNativeToValue(t, nil, NullValue)
  //		expectNativeToValue(t, structpb.NullValue_NULL_VALUE, Null(structpb.NullValue_NULL_VALUE))
  //	}
  //
  //  @Test
  //	void UnsupportedConversion() {
  //		reg := newTestRegistry(t)
  //		if val := reg.NativeToValue(nonConvertible{}); !IsError(val) {
  //			t.Error("Expected error when converting non-proto struct to proto", val)
  //		}
  //	}
  //
  //  @Test
  //	func expectValueToNative(t *testing.T, in ref.Val, out interface{}) {
  //		t.Helper()
  //		if val, err := in.ConvertToNative(reflect.TypeOf(out)); err != nil {
  //			t.Error(err)
  //		} else {
  //			var equals bool
  //			switch val.(type) {
  //			case []byte:
  //				equals = bytes.Equal(val.([]byte), out.([]byte))
  //			case proto.Message:
  //				equals = proto.Equal(val.(proto.Message), out.(proto.Message))
  //			case bool, int32, int64, uint32, uint64, float32, float64, string:
  //				equals = val == out
  //			default:
  //				equals = reflect.DeepEqual(val, out)
  //			}
  //			if !equals {
  //				t.Errorf("Unexpected conversion from expr to proto.\n"+
  //					"expected: %T, actual: %T", val, out)
  //			}
  //		}
  //	}
  //
  //  @Test
  //	func expectNativeToValue(t *testing.T, in interface{}, out ref.Val) {
  //		t.Helper()
  //		reg := newTestRegistry(t, &exprpb.ParsedExpr{})
  //		if val := reg.NativeToValue(in); IsError(val) {
  //			t.Error(val)
  //		} else {
  //			if val.Equal(out) != True {
  //				t.Errorf("Unexpected conversion from expr to proto.\n"+
  //					"expected: %T, actual: %T", val, out)
  //			}
  //		}
  //	}
  //
  //  @Test
  //	func BenchmarkNativeToValue(b *testing.B) {
  //		reg, err := NewRegistry()
  //		if err != nil {
  //			b.Fatalf("NewRegistry() failed: %v", err)
  //		}
  //		inputs := []interface{}{
  //			true,
  //			false,
  //			float32(-1.2),
  //			float64(-2.4),
  //			1,
  //			int32(2),
  //			int64(3),
  //			"",
  //			"hello",
  //			String("hello world"),
  //		}
  //		for _, in := range inputs {
  //			input := in
  //			b.Run(fmt.Sprintf("%T/%v", in, in), func(b *testing.B) {
  //				for i := 0; i < b.N; i++ {
  //					reg.NativeToValue(input)
  //				}
  //			})
  //		}
  //	}
  //
  //  @Test
  //	func BenchmarkTypeProvider_NewValue(b *testing.B) {
  //		reg, err := NewRegistry(&exprpb.ParsedExpr{})
  //		if err != nil {
  //			b.Fatalf("NewRegistry() failed: %v", err)
  //		}
  //		for i := 0; i < b.N; i++ {
  //			reg.NewValue(
  //				"google.api.expr.v1.SourceInfo",
  //				map[string]ref.Val{
  //					"Location":    String("BenchmarkTypeProvider_NewValue"),
  //					"LineOffsets": NewDynamicList(reg, []int64{0, 2}),
  //					"Positions":   NewDynamicMap(reg, map[int64]int64{1: 2, 2: 4}),
  //				})
  //		}
  //	}
  //
  //	// Helper types useful for testing extensions of primitive types.
  //	type nonConvertible struct {
  //		Field string
  //	}
  //	type testInt32 int32
  //	type testInt64 int64
  //	type testUint32 uint32
  //	type testUint64 uint64
  //	type testFloat32 float32
  //	type testFloat64 float64
  //
  //	func newTestRegistry(t *testing.T, types ...proto.Message) ref.TypeRegistry {
  //		t.Helper()
  //		reg, err := NewRegistry(types...)
  //		if err != nil {
  //			t.Fatalf("NewRegistry(%v) failed: %v", types, err)
  //		}
  //		return reg
  //	}

}
