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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class TestJsonList {

  @Test
  @Disabled("IMPLEMENT ME")
  void JsonListValueAdd() {
    //		reg := newRegistry(t)
    //		listA := NewJSONList(reg, &structpb.ListValue{Values: []*structpb.Value{
    //			structpb.NewStringValue("hello"),
    //			structpb.NewNumberValue(1)}})
    //		listB := NewJSONList(reg, &structpb.ListValue{Values: []*structpb.Value{
    //			structpb.NewNumberValue(2),
    //			structpb.NewNumberValue(3)}})
    //		list := listA.add(listB).(traits.Lister)
    //		nativeVal = list.convertToNative(jsonListValueType)
    //		if err != nil {
    //			t.Error(err)
    //		}
    //		expected := &structpb.ListValue{Values: []*structpb.Value{
    //			structpb.NewStringValue("hello"),
    //			structpb.NewNumberValue(1),
    //			structpb.NewNumberValue(2),
    //			structpb.NewNumberValue(3)}}
    //		if !proto.equal(nativeVal.(proto.Message), expected) {
    //			t.Errorf("Concatenated lists did not combine as expected."+
    //				" Got '%v', expected '%v'", nativeVal, expected)
    //		}
    //		listC := NewStringList(reg, []string{"goodbye", "world"})
    //		list = list.add(listC).(traits.Lister)
    //		nativeVal, err = list.convertToNative(jsonListValueType)
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
    //		if !proto.equal(nativeVal.(proto.Message), expected) {
    //			t.Errorf("Concatenated lists did not combine as expected."+
    //				" Got '%v', expected '%v'", nativeVal, expected)
    //		}
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void JsonListValueContains_SingleElemType() {
    //		list := NewJSONList(newRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
    //			structpb.NewNumberValue(3.3),
    //			structpb.NewNumberValue(1)}})
    //		if !list.Contains(Double(1)).(Bool) {
    //			t.Error("Expected value list to contain number '1'")
    //		}
    //		if list.Contains(Double(2)).(Bool) {
    //			t.Error("Expected value list to not contain number '2'")
    //		}
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void JsonListValueContains_MixedElemType() {
    //		list := NewJSONList(newRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
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
    //		if !isError(list.Contains(Double(2))) {
    //			t.Error("Expected value list to not contain number '2' and error", list)
    //		}
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void JsonListValueConvertToNative_Json() {
    //		list := NewJSONList(newRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
    //			structpb.NewStringValue("hello"),
    //			structpb.NewNumberValue(1)}})
    //		listVal = list.convertToNative(jsonListValueType)
    //		if err != nil {
    //			t.Error(err)
    //		}
    //		if listVal != list.value().(proto.Message) {
    //			t.Error("List did not convert to its underlying representation.")
    //		}
    //
    //		val = list.convertToNative(jsonValueType)
    //		if err != nil {
    //			t.Error(err)
    //		}
    //		if !proto.equal(val.(proto.Message),
    //			&structpb.Value{Kind: &structpb.Value_ListValue{
    //				ListValue: listVal.(*structpb.ListValue)}}) {
    //			t.Errorf("Messages were not equal, got '%v'", val)
    //		}
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void JsonListValueConvertToNative_Slice() {
    //		reg := newRegistry(t)
    //		list := NewJSONList(reg, &structpb.ListValue{Values: []*structpb.Value{
    //			structpb.NewStringValue("hello"),
    //			structpb.NewNumberValue(1)}})
    //		listVal = list.convertToNative(reflect.TypeOf([]*structpb.Value{}))
    //		if err != nil {
    //			t.Error(err)
    //		}
    //		for i, v := range listVal.([]*structpb.Value) {
    //			if !list.Get(Int(i)).equal(reg.NativeToValue(v)).(Bool) {
    //				t.Errorf("elem[%d] Got '%v', expected '%v'",
    //					i, v, list.Get(Int(i)))
    //			}
    //		}
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void JsonListValueConvertToNative_Any() {
    //		list := NewJSONList(newRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
    //			structpb.NewStringValue("hello"),
    //			structpb.NewNumberValue(1)}})
    //		anyVal = list.convertToNative(anyValueType)
    //		if err != nil {
    //			t.Fatalf("list.convertToNative() failed: %v", err)
    //		}
    //		unpackedAny = anyVal.(*anypb.Any).UnmarshalNew()
    //		if err != nil {
    //			t.Fatalf("anyVal.UnmarshalNew() failed: %v", err)
    //		}
    //		if !proto.equal(unpackedAny, list.value().(proto.Message)) {
    //			t.Errorf("Messages were not equal, got '%v'", unpackedAny)
    //		}
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void JsonListValueConvertToType() {
    //		list := NewJSONList(newRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
    //			structpb.NewStringValue("hello"),
    //			structpb.NewNumberValue(1)}})
    //		if list.convertToType(TypeType) != ListType {
    //			t.Error("Json list type was not a list.")
    //		}
    //		if list.convertToType(ListType) != list {
    //			t.Error("Json list not convertible to itself.")
    //		}
    //		if !isError(list.convertToType(MapType)) {
    //			t.Error("Got map, expected error.")
    //		}
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void JsonListValueEqual() {
    //		listA := NewJSONList(newRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
    //			structpb.NewNumberValue(-3),
    //			structpb.NewStringValue("hello")},
    //		})
    //		listB := NewJSONList(newRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
    //			structpb.NewNumberValue(2),
    //			structpb.NewStringValue("hello")}})
    //		if listA.equal(listB).(Bool) || listB.equal(listA).(Bool) {
    //			t.Error("Lists with different elements considered equal.")
    //		}
    //		if !listA.equal(listA).(Bool) {
    //			t.Error("List was not equal to itself.")
    //		}
    //		if listA.add(listA).equal(listB).(Bool) {
    //			t.Error("Lists of different size were equal.")
    //		}
    //		if !isError(listA.equal(True)) {
    //			t.Error("Equality of different type returned non-error.")
    //		}
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void JsonListValueGet_OutOfRange() {
    //		list := NewJSONList(newRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
    //			structpb.NewStringValue("hello"),
    //			structpb.NewNumberValue(1)}})
    //		if !isError(list.Get(Int(-1))) {
    //			t.Error("Negative index did not result in error.")
    //		}
    //		if !isError(list.Get(Int(2))) {
    //			t.Error("Index out of range did not result in error.")
    //		}
    //		if !isError(list.Get(Uint(1))) {
    //			t.Error("Index of incorrect type did not result in error.")
    //		}
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void JsonListValueIterator() {
    //		list := NewJSONList(newRegistry(t), &structpb.ListValue{Values: []*structpb.Value{
    //			structpb.NewStringValue("hello"),
    //			structpb.NewNumberValue(1),
    //			structpb.NewNumberValue(2),
    //			structpb.NewNumberValue(3)}})
    //		it := list.Iterator()
    //		for i := Int(0); it.HasNext() != False; i++ {
    //			v := it.Next()
    //			if v.equal(list.Get(i)) != True {
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
  }
}
