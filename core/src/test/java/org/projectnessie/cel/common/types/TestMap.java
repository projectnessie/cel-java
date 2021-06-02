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

public class TestMap {

  //	type testStruct struct {
  //		M       string
  //		Details []string
  //	}
  //
  //  @Test
  //	void DynamicMapContains() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]map[int32]float32{
  //			"nested": {1: -1.0, 2: 2.0},
  //			"empty":  {}}).(traits.Mapper)
  //		if mapValue.Contains(String("nested")) != True {
  //			t.Error("Expected key 'nested' contained in map.")
  //		}
  //		if mapValue.Contains(String("unknown")) != False {
  //			t.Error("Expected key 'unknown' not contained in map.")
  //		}
  //		if !IsError(mapValue.Contains(Int(123))) {
  //			t.Error("Expected key of Int type would error with 'no such overload'.")
  //		}
  //		if !reflect.DeepEqual(mapValue.Contains(Unknown{1}), Unknown{1}) {
  //			t.Error("Expected Unknown key in would yield Unknown key out.")
  //		}
  //	}
  //
  //  @Test
  //	void StringMapContains() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewStringStringMap(reg, map[string]string{
  //			"first":  "hello",
  //			"second": "world"}).(traits.Mapper)
  //		if mapValue.Contains(String("first")) != True {
  //			t.Error("Expected key 'first' contained in map.")
  //		}
  //		if mapValue.Contains(String("third")) != False {
  //			t.Error("Expected key 'third' not contained in map.")
  //		}
  //		if !IsError(mapValue.Contains(Int(123))) {
  //			t.Error("Expected key of Int type would error with 'no such overload'.")
  //		}
  //		if !reflect.DeepEqual(mapValue.Contains(Unknown{1}), Unknown{1}) {
  //			t.Error("Expected Unknown key in would yield Unknown key out.")
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapConvertToNative_Any() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]map[string]float32{
  //			"nested": {"1": -1.0}})
  //		val, err := mapValue.ConvertToNative(anyValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		jsonMap := &structpb.Struct{}
  //		err = protojson.Unmarshal([]byte(`{"nested":{"1":-1}}`), jsonMap)
  //		if err != nil {
  //			t.Fatalf("protojson.Unmarshal() failed: %v", err)
  //		}
  //		want, err := anypb.New(jsonMap)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if !proto.Equal(val.(proto.Message), want) {
  //			t.Errorf("Got %v, wanted %v", val, want)
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapConvertToNative_Error() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]map[string]float32{
  //			"nested": {"1": -1.0}})
  //		val, err := mapValue.ConvertToNative(reflect.TypeOf(""))
  //		if err == nil {
  //			t.Errorf("Got '%v', expected error", val)
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapConvertToNative_Json() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]map[string]float32{
  //			"nested": {"1": -1.0}})
  //		json, err := mapValue.ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		jsonBytes, err := protojson.Marshal(json.(proto.Message))
  //		if err != nil {
  //			t.Fatalf("protojson.Marshal(%v) failed: %v", json, err)
  //		}
  //		jsonTxt := string(jsonBytes)
  //		if jsonTxt != `{"nested":{"1":-1}}` {
  //			t.Error(jsonTxt)
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapConvertToNative_Struct() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]interface{}{
  //			"m":       "hello",
  //			"details": []string{"world", "universe"},
  //		})
  //		ts, err := mapValue.ConvertToNative(reflect.TypeOf(testStruct{}))
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want := testStruct{M: "hello", Details: []string{"world", "universe"}}
  //		if !reflect.DeepEqual(ts, want) {
  //			t.Errorf("Got %v, wanted %v", ts, want)
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapConvertToNative_StructPtr() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]interface{}{
  //			"m":       "hello",
  //			"details": []string{"world", "universe"},
  //		})
  //		ts, err := mapValue.ConvertToNative(reflect.TypeOf(&testStruct{}))
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want := &testStruct{M: "hello", Details: []string{"world", "universe"}}
  //		if !reflect.DeepEqual(ts, want) {
  //			t.Errorf("Got %v, wanted %v", ts, want)
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapConvertToNative_StructPtrPtr() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]interface{}{
  //			"m":       "hello",
  //			"details": []string{"world", "universe"},
  //		})
  //		ptr := &testStruct{}
  //		ts, err := mapValue.ConvertToNative(reflect.TypeOf(&ptr))
  //		if err == nil {
  //			t.Errorf("Got %v, wanted error", ts)
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapConvertToNative_Struct_InvalidFieldError() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]interface{}{
  //			"m":       "hello",
  //			"details": []string{"world", "universe"},
  //			"invalid": "invalid field",
  //		})
  //		ts, err := mapValue.ConvertToNative(reflect.TypeOf(&testStruct{}))
  //		if err == nil {
  //			t.Errorf("Got %v, wanted error", ts)
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapConvertToNative_Struct_EmptyFieldError() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]interface{}{
  //			"m":       "hello",
  //			"details": []string{"world", "universe"},
  //			"":        "empty field",
  //		})
  //		ts, err := mapValue.ConvertToNative(reflect.TypeOf(&testStruct{}))
  //		if err == nil {
  //			t.Errorf("Got %v, wanted error", ts)
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapConvertToNative_Struct_PrivateFieldError() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]interface{}{
  //			"message": "hello",
  //			"details": []string{"world", "universe"},
  //			"private": "private field",
  //		})
  //		ts, err := mapValue.ConvertToNative(reflect.TypeOf(&testStruct{}))
  //		if err == nil {
  //			t.Errorf("Got %v, wanted error", ts)
  //		}
  //	}
  //
  //  @Test
  //	void StringMapConvertToNative() {
  //		reg := newTestRegistry(t)
  //		strMap := map[string]string{
  //			"first":  "hello",
  //			"second": "world",
  //		}
  //		mapValue := NewStringStringMap(reg, strMap)
  //		val, err := mapValue.ConvertToNative(reflect.TypeOf(strMap))
  //		if err != nil {
  //			t.Fatalf("ConvertToNative(map[string]string) failed: %v", err)
  //		}
  //		if !reflect.DeepEqual(val.(map[string]string), strMap) {
  //			t.Errorf("got not-equal, wanted equal for %v == %v", val, strMap)
  //		}
  //		val, err = mapValue.ConvertToNative(reflect.TypeOf(mapValue))
  //		if err != nil {
  //			t.Fatalf("ConvertToNative(baseMap) failed: %v", err)
  //		}
  //		if !reflect.DeepEqual(val, mapValue) {
  //			t.Errorf("got not-equal, wanted equal for %v == %v", val, mapValue)
  //		}
  //		jsonVal, err := mapValue.ConvertToNative(jsonStructType)
  //		if err != nil {
  //			t.Fatalf("ConvertToNative(jsonStructType) failed: %v", err)
  //		}
  //		jsonBytes, err := protojson.Marshal(jsonVal.(proto.Message))
  //		if err != nil {
  //			t.Fatalf("protojson.Marshal() failed: %v", err)
  //		}
  //		jsonTxt := string(jsonBytes)
  //		outMap := map[string]interface{}{}
  //		err = json.Unmarshal(jsonBytes, &outMap)
  //		if err != nil {
  //			t.Fatalf("json.Unmarshal(%q) failed: %v", jsonTxt, err)
  //		}
  //		if !reflect.DeepEqual(outMap, map[string]interface{}{
  //			"first":  "hello",
  //			"second": "world",
  //		}) {
  //			t.Errorf("got json '%v', expected %v", jsonTxt, outMap)
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapConvertToType() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]string{"key": "value"})
  //		if mapValue.ConvertToType(MapType) != mapValue {
  //			t.Error("Map could not be converted to a map.")
  //		}
  //		if mapValue.ConvertToType(TypeType) != MapType {
  //			t.Error("Map type was not listed as a map.")
  //		}
  //		if !IsError(mapValue.ConvertToType(ListType)) {
  //			t.Error("Map conversion to unsupported type was not an error.")
  //		}
  //	}
  //
  //  @Test
  //	void StringMapConvertToType() {
  //		reg := newTestRegistry(t)
  //		mapValue := reg.NativeToValue(map[string]string{"key": "value"})
  //		if mapValue.ConvertToType(MapType) != mapValue {
  //			t.Error("Map could not be converted to a map.")
  //		}
  //		if mapValue.ConvertToType(TypeType) != MapType {
  //			t.Error("Map type was not listed as a map.")
  //		}
  //		if !IsError(mapValue.ConvertToType(ListType)) {
  //			t.Error("Map conversion to unsupported type was not an error.")
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapEqual_True() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]map[int32]float32{
  //			"nested": {1: -1.0, 2: 2.0},
  //			"empty":  {}})
  //		if mapValue.Equal(mapValue) != True {
  //			t.Error("Map value was not equal to itself")
  //		}
  //		if nestedVal := mapValue.Get(String("nested")); IsError(nestedVal) {
  //			t.Error(nestedVal)
  //		} else if mapValue.Equal(nestedVal) == True ||
  //			nestedVal.Equal(mapValue) == True {
  //			t.Error("Same length, but different key names did not result in error")
  //		}
  //	}
  //
  //  @Test
  //	void StringMapEqual_True() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewStringStringMap(reg, map[string]string{
  //			"first":  "hello",
  //			"second": "world"})
  //		if mapValue.Equal(mapValue) != True {
  //			t.Error("Map value was not equal to itself")
  //		}
  //		equivDyn := NewDynamicMap(reg, map[string]string{
  //			"second": "world",
  //			"first":  "hello"})
  //		if mapValue.Equal(equivDyn) != True {
  //			t.Error("Map value equality was key-order dependent")
  //		}
  //		equivJSON := NewJSONStruct(reg, &structpb.Struct{
  //			Fields: map[string]*structpb.Value{
  //				"first":  structpb.NewStringValue("hello"),
  //				"second": structpb.NewStringValue("world"),
  //			}})
  //		if mapValue.Equal(equivJSON) != True && equivJSON.Equal(mapValue) != True {
  //			t.Error("Map value was not equivalent to json struct")
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapEqual_NotTrue() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]map[int32]float32{
  //			"nested": {1: -1.0, 2: 2.0},
  //			"empty":  {}})
  //		other := NewDynamicMap(reg, map[string]map[int64]float64{
  //			"nested": {1: -1.0, 2: 2.0, 3: 3.14},
  //			"empty":  {}})
  //		if mapValue.Equal(other) != False {
  //			t.Error("Inequal map values were deemed equal.")
  //		}
  //		other = NewDynamicMap(reg, map[string]map[int64]float64{
  //			"nested": {1: -1.0, 2: 2.0, 3: 3.14},
  //			"absent": {}})
  //		if mapValue.Equal(other) != False {
  //			t.Error("Inequal map keys were deemed equal.")
  //		}
  //	}
  //
  //  @Test
  //	void StringMapEqual_NotTrue() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewStringStringMap(reg, map[string]string{
  //			"first":  "hello",
  //			"second": "world"})
  //		if mapValue.Equal(mapValue) != True {
  //			t.Error("Map value was not equal to itself")
  //		}
  //		other := NewStringStringMap(reg, map[string]string{
  //			"second": "world",
  //			"first":  "goodbye"})
  //		if mapValue.Equal(other) != False {
  //			t.Error("Map of same size with same keys and different values not false")
  //		}
  //		other = NewStringStringMap(reg, map[string]string{
  //			"first": "hello"})
  //		if mapValue.Equal(other) != False {
  //			t.Error("Equality between maps of different size did not return false")
  //		}
  //		other = NewStringStringMap(reg, map[string]string{
  //			"first": "hello",
  //			"third": "goodbye"})
  //		if mapValue.Equal(other) != False {
  //			t.Error("Equality between maps of different size did not return false")
  //		}
  //		other = NewDynamicMap(reg, map[string]interface{}{
  //			"first":  "hello",
  //			"second": 1})
  //		if !IsError(mapValue.Equal(other)) {
  //			t.Error("Equality between maps of different value types did not error")
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapGet() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]map[int32]float32{
  //			"nested": {1: -1.0, 2: 2.0},
  //			"empty":  {}}).(traits.Mapper)
  //		if nestedVal := mapValue.Get(String("nested")); IsError(nestedVal) {
  //			t.Error(nestedVal)
  //		} else if floatVal := nestedVal.(traits.Indexer).Get(Int(1)); IsError(floatVal) {
  //			t.Error(floatVal)
  //		} else if floatVal.Equal(Double(-1.0)) != True {
  //			t.Error("Nested map access of float property not float64")
  //		}
  //		e, isError := mapValue.Get(String("absent")).(*Err)
  //		if !isError || e.Error() != "no such key: absent" {
  //			t.Errorf("Got %v, wanted no such key: absent.", e)
  //		}
  //	}
  //
  //  @Test
  //	void StringMapGet() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewStringStringMap(reg, map[string]string{
  //			"first":  "hello",
  //			"second": "world"}).(traits.Mapper)
  //		val := mapValue.Get(String("first"))
  //		if val.Equal(String("hello")) != True {
  //			t.Errorf("Got '%v', wanted 'hello'", val)
  //		}
  //		if !IsError(mapValue.Get(Int(1))) {
  //			t.Error("Got real value, wanted error")
  //		}
  //		if !IsError(mapValue.Get(String("third"))) {
  //			t.Error("Got real value, wanted error")
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapIterator() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]map[int32]float32{
  //			"nested": {1: -1.0, 2: 2.0},
  //			"empty":  {}}).(traits.Mapper)
  //		it := mapValue.Iterator()
  //		var i = 0
  //		var fieldNames []interface{}
  //		for ; it.HasNext() == True; i++ {
  //			fieldName := it.Next()
  //			if value := mapValue.Get(fieldName); IsError(value) {
  //				t.Error(value)
  //			} else {
  //				fieldNames = append(fieldNames, fieldName)
  //			}
  //		}
  //		if len(fieldNames) != 2 {
  //			t.Errorf("Did not find the correct number of fields: %v", fieldNames)
  //		}
  //		if it.Next() != nil {
  //			t.Error("Iterator ran off the end of the field names")
  //		}
  //	}
  //
  //  @Test
  //	void StringMapIterator() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewStringStringMap(reg, map[string]string{
  //			"first":  "hello",
  //			"second": "world"}).(traits.Mapper)
  //		it := mapValue.Iterator()
  //		var i = 0
  //		var fieldNames []interface{}
  //		for ; it.HasNext() == True; i++ {
  //			fieldName := it.Next()
  //			if value := mapValue.Get(fieldName); IsError(value) {
  //				t.Error(value)
  //			} else {
  //				fieldNames = append(fieldNames, fieldName)
  //			}
  //		}
  //		if len(fieldNames) != 2 {
  //			t.Errorf("Did not find the correct number of fields: %v", fieldNames)
  //		}
  //		fieldsMap := map[string]bool{
  //			"first":  false,
  //			"second": false,
  //		}
  //		expectedMap := map[string]bool{
  //			"first":  true,
  //			"second": true,
  //		}
  //		for _, fieldName := range fieldNames {
  //			key := string(fieldName.(String))
  //			if _, found := fieldsMap[key]; found {
  //				fieldsMap[key] = true
  //			}
  //		}
  //		if !reflect.DeepEqual(fieldsMap, expectedMap) {
  //			t.Errorf("Got '%v', wanted '%v'", fieldsMap, expectedMap)
  //		}
  //		if it.Next() != nil {
  //			t.Error("Iterator ran off the end of the field names")
  //		}
  //	}
  //
  //  @Test
  //	void DynamicMapSize() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewDynamicMap(reg, map[string]int{
  //			"first":  1,
  //			"second": 2}).(traits.Mapper)
  //		if mapValue.Size() != Int(2) {
  //			t.Errorf("Got '%v', expected 2", mapValue.Size())
  //		}
  //	}
  //
  //  @Test
  //	void StringMapSize() {
  //		reg := newTestRegistry(t)
  //		mapValue := NewStringStringMap(reg, map[string]string{
  //			"first":  "hello",
  //			"second": "world"}).(traits.Mapper)
  //		if mapValue.Size() != Int(2) {
  //			t.Errorf("Got '%v', expected 2", mapValue.Size())
  //		}
  //	}
  //
  //  @Test
  //	void ProtoMap() {
  //		strMap := map[string]string{
  //			"hello":   "world",
  //			"goodbye": "for now",
  //			"welcome": "back",
  //		}
  //		msg := &proto3pb.TestAllTypes{MapStringString: strMap}
  //		reg := newTestRegistry(t, msg)
  //		obj := reg.NativeToValue(msg).(traits.Indexer)
  //
  //		// Test a simple proto map of string string.
  //		field := obj.Get(String("map_string_string"))
  //		mapVal, ok := field.(traits.Mapper)
  //		if !ok {
  //			t.Fatalf("obj.Get('map_string_string') did not return map: (%T)%v", field, field)
  //		}
  //		// CEL type conversion tests.
  //		if mapVal.ConvertToType(MapType) != mapVal {
  //			t.Errorf("got %v, wanted map type", mapVal.ConvertToType(MapType))
  //		}
  //		if mapVal.ConvertToType(TypeType) != MapType {
  //			t.Errorf("got %v, wanted type type", mapVal.ConvertToType(TypeType))
  //		}
  //		conv := mapVal.ConvertToType(ListType)
  //		if !IsError(conv) {
  //			t.Errorf("ConvertToType(ListType) got %v, wanted error", conv)
  //		}
  //		// Size test
  //		if mapVal.Size() != Int(len(strMap)) {
  //			t.Errorf("wanted map size %d, got %d", mapVal.Size(), len(strMap))
  //		}
  //		// Contains, Find, and Get tests.
  //		for k, v := range strMap {
  //			if mapVal.Contains(reg.NativeToValue(k)) != True {
  //				t.Errorf("missing key: %v", k)
  //			}
  //			kv := mapVal.Get(reg.NativeToValue(k))
  //			if kv.Equal(reg.NativeToValue(v)) != True {
  //				t.Errorf("got key (%v) value %v wanted %v", k, kv, v)
  //			}
  //		}
  //		// Equality test
  //		refStrMap := reg.NativeToValue(strMap)
  //		if refStrMap.Equal(mapVal) != True || mapVal.Equal(refStrMap) != True {
  //			t.Errorf("got cel ref.Val %v != ref.Val %v", refStrMap, mapVal)
  //		}
  //		// Iterator test
  //		it := mapVal.Iterator()
  //		mapValCopy := map[ref.Val]ref.Val{}
  //		for it.HasNext() == True {
  //			key := it.Next()
  //			mapValCopy[key] = mapVal.Get(key)
  //		}
  //		mapVal2 := reg.NativeToValue(mapValCopy)
  //		if mapVal2.Equal(mapVal) != True || mapVal.Equal(mapVal2) != True {
  //			t.Errorf("got cel ref.Val %v != ref.Val %v", mapVal2, mapVal)
  //		}
  //		convMap, err := mapVal.ConvertToNative(reflect.TypeOf(strMap))
  //		if err != nil {
  //			t.Fatalf("mapVal.ConvertToNative() failed: %v", err)
  //		}
  //		if !reflect.DeepEqual(strMap, convMap) {
  //			t.Errorf("got map %v, wanted %v", convMap, strMap)
  //		}
  //		// Inequality tests.
  //		strNeMap := map[string]string{
  //			"hello":   "world",
  //			"goodbye": "forever",
  //			"welcome": "back",
  //		}
  //		mapNeVal := reg.NativeToValue(strNeMap)
  //		if mapNeVal.Equal(mapVal) != False || mapVal.Equal(mapNeVal) != False {
  //			t.Error("mapNeVal.Equal(mapVal) returned true, wanted false")
  //		}
  //		strNeMap = map[string]string{
  //			"hello":   "world",
  //			"goodbe":  "for now",
  //			"welcome": "back",
  //		}
  //		mapNeVal = reg.NativeToValue(strNeMap)
  //		if mapNeVal.Equal(mapVal) != False || mapVal.Equal(mapNeVal) != False {
  //			t.Error("mapNeVal.Equal(mapVal) returned true, wanted false")
  //		}
  //		mapNeVal = reg.NativeToValue(map[string]string{})
  //		if mapNeVal.Equal(mapVal) != False || mapVal.Equal(mapNeVal) != False {
  //			t.Error("mapNeVal.Equal(mapVal) returned true, wanted false")
  //		}
  //		mapNeMap := map[int64]int64{
  //			1: 9,
  //			2: 1,
  //			3: 1,
  //		}
  //		mapNeVal = reg.NativeToValue(mapNeMap)
  //		if !IsError(mapNeVal.Equal(mapVal)) || !IsError(mapVal.Equal(mapNeVal)) {
  //			t.Error("mapNeVal.Equal(mapVal) returned non-error, wanted error")
  //		}
  //	}
  //
  //  @Test
  //	void ProtoMapConvertToNative() {
  //		strMap := map[string]string{
  //			"hello":   "world",
  //			"goodbye": "for now",
  //			"welcome": "back",
  //		}
  //		msg := &proto3pb.TestAllTypes{MapStringString: strMap}
  //		reg := newTestRegistry(t, msg)
  //		obj := reg.NativeToValue(msg).(traits.Indexer)
  //		// Test a simple proto map of string string.
  //		field := obj.Get(String("map_string_string"))
  //		mapVal, ok := field.(traits.Mapper)
  //		if !ok {
  //			t.Fatalf("obj.Get('map_string_string') did not return map: (%T)%v", field, field)
  //		}
  //		convMap, err := mapVal.ConvertToNative(reflect.TypeOf(map[string]interface{}{}))
  //		if err != nil {
  //			t.Fatalf("mapVal.ConvertToNative() failed: %v", err)
  //		}
  //		for k, v := range convMap.(map[string]interface{}) {
  //			if strMap[k] != v {
  //				t.Errorf("got differing values for key %q: got %v, wanted: %v", k, strMap[k], v)
  //			}
  //		}
  //		mapVal2 := reg.NativeToValue(convMap)
  //		if mapVal2.Equal(mapVal) != True || mapVal.Equal(mapVal2) != True {
  //			t.Errorf("mapVal2.Equal(mapVal) returned false, wanted true")
  //		}
  //		convMap, err = mapVal.ConvertToNative(anyValueType)
  //		if err != nil {
  //			t.Fatalf("mapVal.ConvertToNative() failed: %v", err)
  //		}
  //		mapVal3 := reg.NativeToValue(convMap)
  //		if mapVal3.Equal(mapVal) != True || mapVal.Equal(mapVal3) != True {
  //			t.Errorf("mapVal3.Equal(mapVal) returned false, wanted true")
  //		}
  //		convMap, err = mapVal.ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Fatalf("mapVal.ConvertToNative() failed: %v", err)
  //		}
  //		mapVal4 := reg.NativeToValue(convMap)
  //		if mapVal4.Equal(mapVal) != True || mapVal.Equal(mapVal4) != True {
  //			t.Errorf("mapVal4.Equal(mapVal) returned false, wanted true")
  //		}
  //		convMap, err = mapVal.ConvertToNative(reflect.TypeOf(&pb.Map{}))
  //		if err != nil {
  //			t.Fatalf("mapVal.ConvertToNative() failed: %v", err)
  //		}
  //		mapVal5 := reg.NativeToValue(convMap)
  //		if mapVal5.Equal(mapVal) != True || mapVal.Equal(mapVal5) != True {
  //			t.Errorf("mapVal5.Equal(mapVal) returned false, wanted true")
  //		}
  //		var mapper traits.Mapper = mapVal
  //		convMap, err = mapVal.ConvertToNative(reflect.TypeOf(mapper))
  //		if err != nil {
  //			t.Fatalf("mapVal.ConvertToNative() failed: %v", err)
  //		}
  //		mapVal6 := reg.NativeToValue(convMap)
  //		if mapVal6.Equal(mapVal) != True || mapVal.Equal(mapVal6) != True {
  //			t.Errorf("mapVal6.Equal(mapVal) returned false, wanted true")
  //		}
  //		_, err = mapVal.ConvertToNative(jsonListValueType)
  //		if err == nil {
  //			t.Fatalf("mapVal.ConvertToNative() succeeded for invalid type")
  //		}
  //		_, err = mapVal.ConvertToNative(reflect.TypeOf(map[int32]string{}))
  //		if err == nil {
  //			t.Fatalf("mapVal.ConvertToNative() succeeded for invalid type")
  //		}
  //		_, err = mapVal.ConvertToNative(reflect.TypeOf(map[string]int64{}))
  //		if err == nil {
  //			t.Fatalf("mapVal.ConvertToNative() succeeded for invalid type")
  //		}
  //	}
  //
  //  @Test
  //	void ProtoMapConvertToNative_NestedProto() {
  //		nestedTypeMap := map[int64]*proto3pb.NestedTestAllTypes{
  //			1: {
  //				Payload: &proto3pb.TestAllTypes{
  //					SingleBoolWrapper: wrapperspb.Bool(true),
  //				},
  //			},
  //			2: {
  //				Child: &proto3pb.NestedTestAllTypes{
  //					Payload: &proto3pb.TestAllTypes{
  //						SingleTimestamp: tpb.New(time.Now()),
  //					},
  //				},
  //			},
  //		}
  //		msg := &proto3pb.TestAllTypes{MapInt64NestedType: nestedTypeMap}
  //		reg := newTestRegistry(t, msg)
  //		obj := reg.NativeToValue(msg).(traits.Indexer)
  //		// Test a simple proto map of string string.
  //		field := obj.Get(String("map_int64_nested_type"))
  //		mapVal, ok := field.(traits.Mapper)
  //		if !ok {
  //			t.Fatalf("obj.Get('map_int64_nested_type') did not return map: (%T)%v", field, field)
  //		}
  //		convMap, err := mapVal.ConvertToNative(reflect.TypeOf(map[int32]interface{}{}))
  //		if err != nil {
  //			t.Fatalf("mapVal.ConvertToNative() failed: %v", err)
  //		}
  //		for k, v := range convMap.(map[int32]interface{}) {
  //			if !proto.Equal(nestedTypeMap[int64(k)], v.(proto.Message)) {
  //				t.Errorf("got differing values for key %q: got %v, wanted: %v", k, nestedTypeMap[int64(k)],
  // v)
  //			}
  //		}
  //		convMap, err = mapVal.ConvertToNative(reflect.TypeOf(map[int32]proto.Message{}))
  //		if err != nil {
  //			t.Fatalf("mapVal.ConvertToNative() failed: %v", err)
  //		}
  //		for k, v := range convMap.(map[int32]proto.Message) {
  //			if !proto.Equal(nestedTypeMap[int64(k)], v) {
  //				t.Errorf("got differing values for key %q: got %v, wanted: %v", k, nestedTypeMap[int64(k)],
  // v)
  //			}
  //		}
  //	}

}
