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

public class TestList {

  //  @Test
  //	void BaseListAdd_Empty() {
  //		reg := newTestRegistry(t)
  //		list := NewDynamicList(reg, []bool{true})
  //		if list.Add(NewDynamicList(reg, []bool{})) != list {
  //			t.Error("Adding an empty list created new list.")
  //		}
  //		if NewDynamicList(reg, []string{}).Add(list) != list {
  //			t.Error("Adding list to empty created a new list.")
  //		}
  //	}
  //
  //  @Test
  //	void BaseListAdd_Error() {
  //		if !IsError(NewDynamicList(newTestRegistry(t), []bool{}).Add(String("error"))) {
  //			t.Error("Addind a non-list value to a list unexpected succeeds.")
  //		}
  //	}
  //
  //  @Test
  //	void BaseListContains() {
  //		list := NewDynamicList(newTestRegistry(t), []float32{1.0, 2.0, 3.0})
  //		if list.Contains(Double(5)) != False {
  //			t.Error("List contains did not return false")
  //		}
  //		if list.Contains(Double(3)) != True {
  //			t.Error("List contains did not succeed")
  //		}
  //		list = NewDynamicList(newTestRegistry(t), []interface{}{1.0, 2, 3.0})
  //		if list.Contains(Int(2)) != True {
  //			t.Error("List contains did not succeed")
  //		}
  //		if list.Contains(Double(3)) != True {
  //			t.Error("List contains did not succeed")
  //		}
  //	}
  //
  //  @Test
  //	void BaseListContains_NonBool() {
  //		list := NewDynamicList(newTestRegistry(t), []interface{}{1.0, 2, 3.0})
  //		if !IsError(list.Contains(Int(3))) {
  //			t.Error("List contains succeeded with wrong type")
  //		}
  //		if !reflect.DeepEqual(list.Contains(Unknown{1}), Unknown{1}) {
  //			t.Error("List ")
  //		}
  //	}
  //
  //  @Test
  //	void BaseListConvertToNative() {
  //		list := NewDynamicList(newTestRegistry(t), []float64{1.0, 2.0})
  //		if protoList, err := list.ConvertToNative(reflect.TypeOf([]float32{})); err != nil {
  //			t.Error(err)
  //		} else if !reflect.DeepEqual(protoList, []float32{1.0, 2.0}) {
  //			t.Errorf("Could not convert to []float32: %v", protoList)
  //		}
  //	}
  //
  //  @Test
  //	void BaseListConvertToNative_Any() {
  //		list := NewDynamicList(newTestRegistry(t), []float64{1.0, 2.0})
  //		val, err := list.ConvertToNative(anyValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		jsonVal := &structpb.ListValue{}
  //		err = protojson.Unmarshal([]byte("[1.0, 2.0]"), jsonVal)
  //		if err != nil {
  //			t.Fatalf("protojson.Unmarshal() failed: %v", err)
  //		}
  //		want, err := anypb.New(jsonVal)
  //		if err != nil {
  //			t.Fatalf("anypb.New() failed: %v", err)
  //		}
  //		if !proto.Equal(val.(proto.Message), want) {
  //			t.Errorf("Got %v, wanted %v", val, want)
  //		}
  //	}
  //
  //  @Test
  //	void BaseListConvertToNative_Json() {
  //		list := NewDynamicList(newTestRegistry(t), []float64{1.0, 2.0})
  //		val, err := list.ConvertToNative(jsonListValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want := &structpb.ListValue{}
  //		err = protojson.Unmarshal([]byte("[1.0, 2.0]"), want)
  //		if err != nil {
  //			t.Fatalf("protojson.Unmarshal() failed: %v", err)
  //		}
  //		if !proto.Equal(val.(proto.Message), want) {
  //			t.Errorf("Got %v, wanted %v", val, want)
  //		}
  //	}
  //
  //  @Test
  //	void BaseListConvertToType() {
  //		list := NewDynamicList(newTestRegistry(t), []string{"h", "e", "l", "l", "o"})
  //		if list.ConvertToType(ListType) != list {
  //			t.Error("List was not convertible to itself.")
  //		}
  //		if list.ConvertToType(TypeType) != ListType {
  //			t.Error("Unable to obtain the proper type from the list.")
  //		}
  //		if !IsError(list.ConvertToType(MapType)) {
  //			t.Error("List was able to convert to unexpected type.")
  //		}
  //	}
  //
  //  @Test
  //	void BaseListEqual() {
  //		listA := NewDynamicList(newTestRegistry(t), []string{"h", "e", "l", "l", "o"})
  //		listB := NewDynamicList(newTestRegistry(t), []string{"h", "e", "l", "p", "!"})
  //		if listA.Equal(listB) != False {
  //			t.Error("Lists with different contents returned equal.")
  //		}
  //	}
  //
  //  @Test
  //	void BaseListGet() {
  //		validateList123(t, NewDynamicList(newTestRegistry(t), []int32{1, 2, 3}).(traits.Lister))
  //	}
  //
  //  @Test
  //	void ValueListGet() {
  //		validateList123(t, NewRefValList(newTestRegistry(t), []ref.Val{Int(1), Int(2), Int(3)}))
  //	}
  //
  //  @Test
  //	void BaseListIterator() {
  //		validateIterator123(t, NewDynamicList(newTestRegistry(t), []int32{1, 2, 3}).(traits.Lister))
  //	}
  //
  //  @Test
  //	void ValueListValue_Iterator() {
  //		validateIterator123(t, NewRefValList(newTestRegistry(t), []ref.Val{Int(1), Int(2), Int(3)}))
  //	}
  //
  //  @Test
  //	void BaseListNestedList() {
  //		reg := newTestRegistry(t)
  //		listUint32 := []uint32{1, 2}
  //		nestedUint32 := NewDynamicList(reg, []interface{}{listUint32})
  //		listUint64 := []uint64{1, 2}
  //		nestedUint64 := NewDynamicList(reg, []interface{}{listUint64})
  //		if nestedUint32.Equal(nestedUint64) != True {
  //			t.Error("Could not find nested list")
  //		}
  //		if nestedUint32.Contains(NewDynamicList(reg, listUint64)) != True ||
  //			nestedUint64.Contains(NewDynamicList(reg, listUint32)) != True {
  //			t.Error("Could not find type compatible nested lists")
  //		}
  //	}
  //
  //  @Test
  //	void BaseListSize() {
  //		reg := newTestRegistry(t)
  //		listUint32 := []uint32{1, 2}
  //		nestedUint32 := NewDynamicList(reg, []interface{}{listUint32})
  //		if nestedUint32.Size() != IntOne {
  //			t.Error("List indicates the incorrect size.")
  //		}
  //		if nestedUint32.Get(IntZero).(traits.Sizer).Size() != Int(2) {
  //			t.Error("Nested list indicates the incorrect size.")
  //		}
  //	}
  //
  //  @Test
  //	void ConcatListAdd() {
  //		reg := newTestRegistry(t)
  //		listA := NewDynamicList(reg, []float32{1.0, 2.0})
  //		listB := NewStringList(reg, []string{"3"})
  //		list := listA.Add(listB).(traits.Lister).Add(listA).
  //			Value().([]interface{})
  //		expected := []interface{}{
  //			float64(1.0),
  //			float64(2.0),
  //			string("3"),
  //			float64(1.0),
  //			float64(2.0)}
  //		if len(list) != len(expected) {
  //			t.Errorf("Got '%v', expected '%v'", list, expected)
  //		} else {
  //			for i := 0; i < len(list); i++ {
  //				if expected[i] != list[i] {
  //					t.Errorf("elem[%d] Got '%v', expected '%v'",
  //						i, list[i], expected[i])
  //				}
  //			}
  //		}
  //		// Zero length input list
  //		listConcat := listA.Add(listB).(traits.Lister)
  //		same := listConcat.Add(NewStringList(reg, []string{}))
  //		if !reflect.DeepEqual(listConcat, same) {
  //			t.Error("Adding an empty list to a concat list did not return the concat list")
  //		}
  //		// Zero length operand list
  //		same = NewDynamicList(reg, []bool{}).Add(listConcat)
  //		if !reflect.DeepEqual(listConcat, same) {
  //			t.Error("Adding a concat list to an empty list did not return the concat list")
  //		}
  //	}
  //
  //  @Test
  //	void ConcatListConvertToNative_Json() {
  //		reg := newTestRegistry(t)
  //		listA := NewDynamicList(reg, []float32{1.0, 2.0})
  //		listB := NewDynamicList(reg, []string{"3"})
  //		list := listA.Add(listB)
  //		jsonVal, err := list.ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Fatalf("Got error '%v', expected value", err)
  //		}
  //		jsonBytes, err := protojson.Marshal(jsonVal.(proto.Message))
  //		if err != nil {
  //			t.Fatalf("protojson.Marshal(%v) failed: %v", jsonVal, err)
  //		}
  //		jsonTxt := string(jsonBytes)
  //		outList := []interface{}{}
  //		err = json.Unmarshal(jsonBytes, &outList)
  //		if err != nil {
  //			t.Fatalf("json.Unmarshal(%q) failed: %v", jsonTxt, err)
  //		}
  //		if !reflect.DeepEqual(outList, []interface{}{1.0, 2.0, "3"}) {
  //			t.Errorf("got json '%v', expected %v", outList, []interface{}{1.0, 2.0, "3"})
  //		}
  //		// Test proto3 to JSON conversion.
  //		listC := NewDynamicList(reg, []*dpb.Duration{{Seconds: 100}})
  //		listConcat := listA.Add(listC)
  //		jsonVal, err = listConcat.ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Fatal(err)
  //		}
  //		jsonBytes, err = protojson.Marshal(jsonVal.(proto.Message))
  //		if err != nil {
  //			t.Fatalf("protojson.Marshal(%v) failed: %v", jsonVal, err)
  //		}
  //		jsonTxt = string(jsonBytes)
  //		outList = []interface{}{}
  //		err = json.Unmarshal(jsonBytes, &outList)
  //		if err != nil {
  //			t.Fatalf("json.Unmarshal(%q) failed: %v", jsonTxt, err)
  //		}
  //		if !reflect.DeepEqual(outList, []interface{}{1.0, 2.0, "100s"}) {
  //			t.Errorf("got json '%v', expected %v", outList, []interface{}{1.0, 2.0, "100s"})
  //		}
  //	}
  //
  //  @Test
  //	void ConcatListConvertToNative_ListInterface() {
  //		reg := newTestRegistry(t)
  //		listA := NewDynamicList(reg, []float32{1.0, 2.0})
  //		listB := NewStringList(reg, []string{"3.0"})
  //		list := listA.Add(listB)
  //		iface, err := list.ConvertToNative(reflect.TypeOf([]interface{}{}))
  //		if err != nil {
  //			t.Errorf("Got '%v', expected '%v'", err, list)
  //		}
  //		want := []interface{}{1.0, 2.0, "3.0"}
  //		if !reflect.DeepEqual(iface, want) {
  //			t.Errorf("Got '%v', expected '%v'", iface, want)
  //		}
  //	}
  //
  //  @Test
  //	void ConcatListConvertToType() {
  //		reg := newTestRegistry(t)
  //		listA := NewDynamicList(reg, []float32{1.0, 2.0})
  //		listB := NewDynamicList(reg, []*dpb.Duration{{Seconds: 100}})
  //		list := listA.Add(listB)
  //		if list.ConvertToType(ListType) != list {
  //			t.Error("List conversion to list failed.")
  //		}
  //		if list.ConvertToType(TypeType) != ListType {
  //			t.Error("List conversion to type failed.")
  //		}
  //		if !IsError(list.ConvertToType(MapType)) {
  //			t.Error("List conversion to map unexpectedly succeeded.")
  //		}
  //	}
  //
  //  @Test
  //	void ConcatListContains() {
  //		reg := newTestRegistry(t)
  //		listA := NewDynamicList(reg, []float32{1.0, 2.0})
  //		listB := NewDynamicList(reg, []string{"3"})
  //		listConcat := listA.Add(listB).(traits.Lister)
  //		if listConcat.Contains(String("3")) != True {
  //			t.Error("Concatenated list did not contain value in 'next' list.")
  //		}
  //		if listConcat.Contains(Double(2.0)) != True {
  //			t.Error("Concatenated list did not contain value in 'prev' list.")
  //		}
  //		homogList := NewDynamicList(reg, []string{"3"}).Add(
  //			NewStringList(reg, []string{"2", "1"})).(traits.Lister)
  //		if homogList.Contains(String("4")) != False {
  //			t.Error("Concatenated homogeneous list did not return false.")
  //		}
  //	}
  //
  //  @Test
  //	void ConcatListContains_NonBool() {
  //		reg := newTestRegistry(t)
  //		listA := NewDynamicList(reg, []float32{1.0, 2.0})
  //		listB := NewDynamicList(reg, []string{"3"})
  //		listConcat := listA.Add(listB).(traits.Lister)
  //		if !IsError(listConcat.Contains(String("4"))) {
  //			t.Error("Contains did not error with list of mixed types an not found input.")
  //		}
  //	}
  //
  //  @Test
  //	void ConcatListValue_Equal() {
  //		reg := newTestRegistry(t)
  //		listA := NewDynamicList(reg, []float32{1.0, 2.0})
  //		listB := NewDynamicList(reg, []float64{3.0})
  //		list := listA.Add(listB)
  //		// Note the internal type of list raw and concat list are slightly different.
  //		listRaw := NewDynamicList(reg, []interface{}{
  //			float32(1.0), float64(2.0), float64(3.0)})
  //		if listRaw.Equal(list) != True ||
  //			list.Equal(listRaw) != True {
  //			t.Errorf("Concat list and raw list were not equal, got '%v', expected '%v'",
  //				list.Value(),
  //				listRaw.Value())
  //		}
  //		if list.Equal(listA) == True ||
  //			listRaw.Equal(listA) == True {
  //			t.Errorf("Lists of unequal length considered equal")
  //		}
  //	}
  //
  //  @Test
  //	void ConcatListValue_Get() {
  //		reg := newTestRegistry(t)
  //		listA := NewDynamicList(reg, []float32{1.0, 2.0})
  //		listB := NewDynamicList(reg, []float64{3.0})
  //		list := listA.Add(listB).(traits.Lister)
  //		if getElem(t, list, 0) != Double(1.0) ||
  //			getElem(t, list, 1) != Double(2.0) ||
  //			getElem(t, list, 2) != Double(3.0) {
  //			t.Errorf("List values by index did not match expectations")
  //		}
  //		if val := list.Get(Int(-1)); !IsError(val) {
  //			t.Errorf("Should not have been able to read a negative index")
  //		}
  //		if val := list.Get(Int(3)); !IsError(val) {
  //			t.Errorf("Should not have been able to read beyond end of list")
  //		}
  //	}
  //
  //  @Test
  //	void ConcatListValue_Iterator() {
  //		reg := newTestRegistry(t)
  //		listA := NewDynamicList(reg, []float32{1.0, 2.0})
  //		listB := NewDynamicList(reg, []float64{3.0})
  //		list := listA.Add(listB).(traits.Lister)
  //		it := list.Iterator()
  //		var i = int64(0)
  //		for ; it.HasNext() == True; i++ {
  //			elem := it.Next()
  //			if getElem(t, list, Int(i)) != elem {
  //				t.Errorf(
  //					"List iterator returned incorrect value: list[%d]: %v", i, elem)
  //			}
  //		}
  //		if it.Next() != nil {
  //			t.Errorf("List iterator attempted to continue beyond list size")
  //		}
  //		if i != 3 {
  //			t.Errorf("Iterator did not iterate until last value")
  //		}
  //	}
  //
  //  @Test
  //	void StringListAdd_Empty() {
  //		reg := newTestRegistry(t)
  //		list := NewStringList(reg, []string{"hello"})
  //		if list.Add(NewStringList(reg, []string{})) != list {
  //			t.Error("Adding empty lists resulted in new list creation.")
  //		}
  //		if NewStringList(reg, []string{}).Add(list) != list {
  //			t.Error("Adding empty lists resulted in new list creation.")
  //		}
  //	}
  //
  //  @Test
  //	void StringListAdd_Error() {
  //		reg := newTestRegistry(t)
  //		if !IsError(NewStringList(reg, []string{}).Add(True)) {
  //			t.Error("Got list, expected error.")
  //		}
  //	}
  //
  //  @Test
  //	void StringListAdd_Heterogenous() {
  //		reg := newTestRegistry(t)
  //		listA := NewStringList(reg, []string{"hello"})
  //		listB := NewDynamicList(reg, []int32{1, 2, 3})
  //		list := listA.Add(listB).(traits.Lister).Value().([]interface{})
  //		expected := []interface{}{"hello", int64(1), int64(2), int64(3)}
  //		if len(list) != len(expected) {
  //			t.Errorf("Unexpected list size. Got '%d', expected 4", len(list))
  //		}
  //		for i, v := range expected {
  //			if list[i] != v {
  //				t.Errorf("elem[%d] Got '%v', expected '%v'", i, list[i], v)
  //			}
  //		}
  //	}
  //
  //  @Test
  //	void StringListAdd_StringLists() {
  //		reg := newTestRegistry(t)
  //		listA := NewStringList(reg, []string{"hello"})
  //		listB := NewStringList(reg, []string{"world", "!"})
  //		list := listA.Add(listB).(traits.Lister)
  //		if list.Size() != Int(3) {
  //			t.Error("Combined list did not have correct size.")
  //		}
  //		expected := []string{"hello", "world", "!"}
  //		for i, v := range expected {
  //			if list.Get(Int(i)).Equal(String(v)) != True {
  //				t.Errorf("elem[%d] Got '%v', expected '%v'", i, list.Get(Int(i)), v)
  //			}
  //		}
  //	}
  //
  //  @Test
  //	void StringListConvertToNative() {
  //		reg := newTestRegistry(t)
  //		list := NewStringList(reg, []string{"h", "e", "l", "p"})
  //		val, err := list.ConvertToNative(reflect.TypeOf([]string{}))
  //		if err != nil {
  //			t.Error("Unable to convert string list to itself.")
  //		}
  //		if !reflect.DeepEqual(val, []string{"h", "e", "l", "p"}) {
  //			t.Errorf(`Got %v, expected ["h", "e", "l", "p"]`, val)
  //		}
  //	}
  //
  //  @Test
  //	void StringListConvertToNative_ListInterface() {
  //		reg := newTestRegistry(t)
  //		list := NewStringList(reg, []string{"h", "e", "l", "p"})
  //		val, err := list.ConvertToNative(reflect.TypeOf([]interface{}{}))
  //		if err != nil {
  //			t.Error("Unable to convert string list to itself.")
  //		}
  //		want := []interface{}{"h", "e", "l", "p"}
  //		if !reflect.DeepEqual(val.([]interface{}), want) {
  //			for i, e := range val.([]interface{}) {
  //				t.Logf("val[%d] %v(%T)", i, e, e)
  //			}
  //			for i, e := range want {
  //				t.Logf("want[%d] %v(%T)", i, e, e)
  //			}
  //			t.Errorf(`Got %v(%T), expected %v(%T)`, val, val, want, want)
  //		}
  //	}
  //
  //  @Test
  //	void StringListConvertToNative_Error() {
  //		reg := newTestRegistry(t)
  //		list := NewStringList(reg, []string{"h", "e", "l", "p"})
  //		_, err := list.ConvertToNative(jsonStructType)
  //		if err == nil {
  //			t.Error("Conversion of list to unsupported type did not error.")
  //		}
  //	}
  //
  //  @Test
  //	void StringListConvertToNative_Json() {
  //		reg := newTestRegistry(t)
  //		list := NewStringList(reg, []string{"h", "e", "l", "p"})
  //		jsonVal, err := list.ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Errorf("Got '%v', expected '%v'", err, jsonVal)
  //		}
  //		jsonBytes, err := protojson.Marshal(jsonVal.(proto.Message))
  //		if err != nil {
  //			t.Fatalf("protojson.Marshal(%v) failed: %v", jsonVal, err)
  //		}
  //		jsonTxt := string(jsonBytes)
  //		outList := []interface{}{}
  //		err = json.Unmarshal(jsonBytes, &outList)
  //		if err != nil {
  //			t.Fatalf("json.Unmarshal(%q) failed: %v", jsonTxt, err)
  //		}
  //		if !reflect.DeepEqual(outList, []interface{}{"h", "e", "l", "p"}) {
  //			t.Errorf("got json '%v', expected %v", jsonTxt, outList)
  //		}
  //
  //		jsonList, err := list.ConvertToNative(jsonListValueType)
  //		if err != nil {
  //			t.Errorf("Got '%v', expected '%v'", err, jsonList)
  //		}
  //		jsonListBytes, err := protojson.Marshal(jsonList.(proto.Message))
  //		if err != nil {
  //			t.Fatalf("protojson.Marshal(%v) failed: %v", jsonVal, err)
  //		}
  //		jsonListTxt := string(jsonListBytes)
  //		if jsonTxt != jsonListTxt {
  //			t.Errorf("Json value and list value not equal.")
  //		}
  //	}
  //
  //  @Test
  //	void StringListGet_OutOfRange() {
  //		reg := newTestRegistry(t)
  //		list := NewStringList(reg, []string{"hello", "world"})
  //		if !IsError(list.Get(Int(-1))) {
  //			t.Error("Negative index did not return error.")
  //		}
  //		if !IsError(list.Get(Int(2))) {
  //			t.Error("Index out of range did not return error.")
  //		}
  //		if !IsError(list.Get(Uint(1))) {
  //			t.Error("Invalid index type did not return error.")
  //		}
  //	}
  //
  //  @Test
  //	void ValueListAdd() {
  //		reg := newTestRegistry(t)
  //		listA := NewRefValList(reg, []ref.Val{String("hello")})
  //		listB := NewRefValList(reg, []ref.Val{String("world")})
  //		listConcat := listA.Add(listB).(traits.Lister)
  //		if listConcat.Contains(String("goodbye")) != False {
  //			t.Error("Homogeneous concatenated value list did not return false on missing input")
  //		}
  //		if listConcat.Contains(String("hello")) != True {
  //			t.Error("Homogeneous concatenated value list did not return true on valid input")
  //		}
  //	}
  //
  //  @Test
  //	void ValueListConvertToNative_Json() {
  //		reg := newTestRegistry(t)
  //		list := NewRefValList(reg, []ref.Val{String("hello"), String("world")})
  //		jsonVal, err := list.ConvertToNative(jsonListValueType)
  //		if err != nil {
  //			t.Errorf("Got '%v', expected '%v'", err, jsonVal)
  //		}
  //		jsonBytes, err := protojson.Marshal(jsonVal.(proto.Message))
  //		if err != nil {
  //			t.Fatalf("protojson.Marshal(%v) failed: %v", jsonVal, err)
  //		}
  //		jsonTxt := string(jsonBytes)
  //		outList := []interface{}{}
  //		err = json.Unmarshal(jsonBytes, &outList)
  //		if err != nil {
  //			t.Fatalf("json.Unmarshal(%q) failed: %v", jsonTxt, err)
  //		}
  //		if !reflect.DeepEqual(outList, []interface{}{"hello", "world"}) {
  //			t.Errorf("got json '%v', expected %v", jsonTxt, outList)
  //		}
  //	}
  //
  //	func getElem(t *testing.T, list traits.Indexer, index Int) interface{} {
  //		t.Helper()
  //		val := list.Get(index)
  //		if IsError(val) {
  //			t.Errorf("Error reading list index %d, %v", index, val)
  //			return nil
  //		}
  //		return val
  //	}
  //
  //  @Test
  //	func validateList123(t *testing.T, list traits.Lister) {
  //		t.Helper()
  //		if getElem(t, list, 0) != Int(1) ||
  //			getElem(t, list, 1) != Int(2) ||
  //			getElem(t, list, 2) != Int(3) {
  //			t.Errorf("List values by index did not match expectations")
  //		}
  //		if val := list.Get(Int(-1)); !IsError(val) {
  //			t.Errorf("Should not have been able to read a negative index")
  //		}
  //		if val := list.Get(Int(3)); !IsError(val) {
  //			t.Errorf("Should not have been able to read beyond end of list")
  //		}
  //		if !IsError(list.Get(Uint(3))) {
  //			t.Error("Invalid index type did not result in error")
  //		}
  //	}
  //
  //  @Test
  //	func validateIterator123(t *testing.T, list traits.Lister) {
  //		t.Helper()
  //		it := list.Iterator()
  //		var i = int64(0)
  //		for ; it.HasNext() == True; i++ {
  //			elem := it.Next()
  //			if getElem(t, list, Int(i)) != elem {
  //				t.Errorf(
  //					"List iterator returned incorrect value: list[%d]: %v", i, elem)
  //			}
  //		}
  //		if it.Next() != nil {
  //			t.Errorf("List iterator attempted to continue beyond list size")
  //		}
  //		if i != 3 {
  //			t.Errorf("Iterator did not iterate until last value")
  //		}
  //	}

}
