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

public class TestJsonList {

  //  @Test
  //	void JsonListValueAdd() {
  //		reg := newTestRegistry(t)
  //		listA := NewJSONList(reg, &structpb.ListValue{Values: []*structpb.Value{
  //			structpb.NewStringValue("hello"),
  //			structpb.NewNumberValue(1)}})
  //		listB := NewJSONList(reg, &structpb.ListValue{Values: []*structpb.Value{
  //			structpb.NewNumberValue(2),
  //			structpb.NewNumberValue(3)}})
  //		list := listA.Add(listB).(traits.Lister)
  //		nativeVal, err := list.ConvertToNative(jsonListValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		expected := &structpb.ListValue{Values: []*structpb.Value{
  //			structpb.NewStringValue("hello"),
  //			structpb.NewNumberValue(1),
  //			structpb.NewNumberValue(2),
  //			structpb.NewNumberValue(3)}}
  //		if !proto.Equal(nativeVal.(proto.Message), expected) {
  //			t.Errorf("Concatenated lists did not combine as expected."+
  //				" Got '%v', expected '%v'", nativeVal, expected)
  //		}
  //		listC := NewStringList(reg, []string{"goodbye", "world"})
  //		list = list.Add(listC).(traits.Lister)
  //		nativeVal, err = list.ConvertToNative(jsonListValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		expected = &structpb.ListValue{Values: []*structpb.Value{
  //			structpb.NewStringValue("hello"),
  //			structpb.NewNumberValue(1),
  //			structpb.NewNumberValue(2),
  //			structpb.NewNumberValue(3),
  //			structpb.NewStringValue("goodbye"),
  //			structpb.NewStringValue("world"),
  //		}}
  //		if !proto.Equal(nativeVal.(proto.Message), expected) {
  //			t.Errorf("Concatenated lists did not combine as expected."+
  //				" Got '%v', expected '%v'", nativeVal, expected)
  //		}
  //	}
  //
  //  @Test
  //	void JsonListValueContains_SingleElemType() {
  //		list := NewJSONList(newTestRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
  //			structpb.NewNumberValue(3.3),
  //			structpb.NewNumberValue(1)}})
  //		if !list.Contains(Double(1)).(Bool) {
  //			t.Error("Expected value list to contain number '1'")
  //		}
  //		if list.Contains(Double(2)).(Bool) {
  //			t.Error("Expected value list to not contain number '2'")
  //		}
  //	}
  //
  //  @Test
  //	void JsonListValueContains_MixedElemType() {
  //		list := NewJSONList(newTestRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
  //			structpb.NewStringValue("hello"),
  //			structpb.NewNumberValue(1)}})
  //		if !list.Contains(Double(1)).(Bool) {
  //			t.Error("Expected value list to contain number '1'", list)
  //		}
  //		// Contains is semantically equivalent to unrolling the list and
  //		// applying a series of logical ORs between the first input value
  //		// each element in the list. When the value is present, the result
  //		// can be True. When the value is not present and the list is of
  //		// mixed element type, the result is an error.
  //		if !IsError(list.Contains(Double(2))) {
  //			t.Error("Expected value list to not contain number '2' and error", list)
  //		}
  //	}
  //
  //  @Test
  //	void JsonListValueConvertToNative_Json() {
  //		list := NewJSONList(newTestRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
  //			structpb.NewStringValue("hello"),
  //			structpb.NewNumberValue(1)}})
  //		listVal, err := list.ConvertToNative(jsonListValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if listVal != list.Value().(proto.Message) {
  //			t.Error("List did not convert to its underlying representation.")
  //		}
  //
  //		val, err := list.ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if !proto.Equal(val.(proto.Message),
  //			&structpb.Value{Kind: &structpb.Value_ListValue{
  //				ListValue: listVal.(*structpb.ListValue)}}) {
  //			t.Errorf("Messages were not equal, got '%v'", val)
  //		}
  //	}
  //
  //  @Test
  //	void JsonListValueConvertToNative_Slice() {
  //		reg := newTestRegistry(t)
  //		list := NewJSONList(reg, &structpb.ListValue{Values: []*structpb.Value{
  //			structpb.NewStringValue("hello"),
  //			structpb.NewNumberValue(1)}})
  //		listVal, err := list.ConvertToNative(reflect.TypeOf([]*structpb.Value{}))
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		for i, v := range listVal.([]*structpb.Value) {
  //			if !list.Get(Int(i)).Equal(reg.NativeToValue(v)).(Bool) {
  //				t.Errorf("elem[%d] Got '%v', expected '%v'",
  //					i, v, list.Get(Int(i)))
  //			}
  //		}
  //	}
  //
  //  @Test
  //	void JsonListValueConvertToNative_Any() {
  //		list := NewJSONList(newTestRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
  //			structpb.NewStringValue("hello"),
  //			structpb.NewNumberValue(1)}})
  //		anyVal, err := list.ConvertToNative(anyValueType)
  //		if err != nil {
  //			t.Fatalf("list.ConvertToNative() failed: %v", err)
  //		}
  //		unpackedAny, err := anyVal.(*anypb.Any).UnmarshalNew()
  //		if err != nil {
  //			t.Fatalf("anyVal.UnmarshalNew() failed: %v", err)
  //		}
  //		if !proto.Equal(unpackedAny, list.Value().(proto.Message)) {
  //			t.Errorf("Messages were not equal, got '%v'", unpackedAny)
  //		}
  //	}
  //
  //  @Test
  //	void JsonListValueConvertToType() {
  //		list := NewJSONList(newTestRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
  //			structpb.NewStringValue("hello"),
  //			structpb.NewNumberValue(1)}})
  //		if list.ConvertToType(TypeType) != ListType {
  //			t.Error("Json list type was not a list.")
  //		}
  //		if list.ConvertToType(ListType) != list {
  //			t.Error("Json list not convertible to itself.")
  //		}
  //		if !IsError(list.ConvertToType(MapType)) {
  //			t.Error("Got map, expected error.")
  //		}
  //	}
  //
  //  @Test
  //	void JsonListValueEqual() {
  //		listA := NewJSONList(newTestRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
  //			structpb.NewNumberValue(-3),
  //			structpb.NewStringValue("hello")},
  //		})
  //		listB := NewJSONList(newTestRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
  //			structpb.NewNumberValue(2),
  //			structpb.NewStringValue("hello")}})
  //		if listA.Equal(listB).(Bool) || listB.Equal(listA).(Bool) {
  //			t.Error("Lists with different elements considered equal.")
  //		}
  //		if !listA.Equal(listA).(Bool) {
  //			t.Error("List was not equal to itself.")
  //		}
  //		if listA.Add(listA).Equal(listB).(Bool) {
  //			t.Error("Lists of different size were equal.")
  //		}
  //		if !IsError(listA.Equal(True)) {
  //			t.Error("Equality of different type returned non-error.")
  //		}
  //	}
  //
  //  @Test
  //	void JsonListValueGet_OutOfRange() {
  //		list := NewJSONList(newTestRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
  //			structpb.NewStringValue("hello"),
  //			structpb.NewNumberValue(1)}})
  //		if !IsError(list.Get(Int(-1))) {
  //			t.Error("Negative index did not result in error.")
  //		}
  //		if !IsError(list.Get(Int(2))) {
  //			t.Error("Index out of range did not result in error.")
  //		}
  //		if !IsError(list.Get(Uint(1))) {
  //			t.Error("Index of incorrect type did not result in error.")
  //		}
  //	}
  //
  //  @Test
  //	void JsonListValueIterator() {
  //		list := NewJSONList(newTestRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
  //			structpb.NewStringValue("hello"),
  //			structpb.NewNumberValue(1),
  //			structpb.NewNumberValue(2),
  //			structpb.NewNumberValue(3)}})
  //		it := list.Iterator()
  //		for i := Int(0); it.HasNext() != False; i++ {
  //			v := it.Next()
  //			if v.Equal(list.Get(i)) != True {
  //				t.Errorf("elem[%d] Got '%v', expected '%v'", i, v, list.Get(i))
  //			}
  //		}
  //
  //		if it.HasNext() != False {
  //			t.Error("Iterator indicated more elements were left")
  //		}
  //		if it.Next() != nil {
  //			t.Error("Calling Next() for a complete iterator resulted in a non-nil value.")
  //		}
  //	}
}
