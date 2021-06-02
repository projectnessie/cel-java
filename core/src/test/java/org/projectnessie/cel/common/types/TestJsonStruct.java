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

public class TestJsonStruct {

  //  @Test
  //	void JsonStructContains() {
  //		mapVal := NewJSONStruct(newTestRegistry(t), &structpb.Struct{Fields:
  // map[string]*structpb.Value{
  //			"first":  structpb.NewStringValue("hello"),
  //			"second": structpb.NewNumberValue(1)}})
  //		if !mapVal.Contains(String("first")).(Bool) {
  //			t.Error("Expected map to contain key 'first'", mapVal)
  //		}
  //		if mapVal.Contains(String("firs")).(Bool) {
  //			t.Error("Expected map contained non-existent key", mapVal)
  //		}
  //	}
  //
  //  @Test
  //	void JsonStructConvertToNative_Json() {
  //		structVal := &structpb.Struct{Fields: map[string]*structpb.Value{
  //			"first":  structpb.NewStringValue("hello"),
  //			"second": structpb.NewNumberValue(1)}}
  //		mapVal := NewJSONStruct(newTestRegistry(t), structVal)
  //		val, err := mapVal.ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if !proto.Equal(val.(proto.Message),
  //			&structpb.Value{Kind: &structpb.Value_StructValue{StructValue: structVal}}) {
  //			t.Errorf("Got '%v', expected '%v'", val, structVal)
  //		}
  //
  //		strVal, err := mapVal.ConvertToNative(jsonStructType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if !proto.Equal(strVal.(proto.Message), structVal) {
  //			t.Errorf("Got '%v', expected '%v'", strVal, structVal)
  //		}
  //	}
  //
  //  @Test
  //	void JsonStructConvertToNative_Any() {
  //		structVal := &structpb.Struct{
  //			Fields: map[string]*structpb.Value{
  //				"first":  structpb.NewStringValue("hello"),
  //				"second": structpb.NewNumberValue(1)}}
  //		mapVal := NewJSONStruct(newTestRegistry(t), structVal)
  //		anyVal, err := mapVal.ConvertToNative(anyValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		unpackedAny, err := anyVal.(*anypb.Any).UnmarshalNew()
  //		if err != nil {
  //			t.Fatalf("anyVal.UnmarshalNew() failed: %v", err)
  //		}
  //		if !proto.Equal(unpackedAny, mapVal.Value().(proto.Message)) {
  //			t.Errorf("Messages were not equal, got '%v'", unpackedAny)
  //		}
  //	}
  //
  //  @Test
  //	void JsonStructConvertToNative_Map() {
  //		structVal := &structpb.Struct{Fields: map[string]*structpb.Value{
  //			"first":  structpb.NewStringValue("hello"),
  //			"second": structpb.NewStringValue("world"),
  //		}}
  //		mapVal := NewJSONStruct(newTestRegistry(t), structVal)
  //		val, err := mapVal.ConvertToNative(reflect.TypeOf(map[string]string{}))
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if val.(map[string]string)["first"] != "hello" {
  //			t.Error("Could not find key 'first' in map", val)
  //		}
  //	}
  //
  //  @Test
  //	void JsonStructConvertToType() {
  //		mapVal := NewJSONStruct(newTestRegistry(t),
  //			&structpb.Struct{Fields: map[string]*structpb.Value{
  //				"first":  structpb.NewStringValue("hello"),
  //				"second": structpb.NewNumberValue(1)}})
  //		if mapVal.ConvertToType(MapType) != mapVal {
  //			t.Error("Map could not be converted to a map.")
  //		}
  //		if mapVal.ConvertToType(TypeType) != MapType {
  //			t.Error("Map did not indicate itself as map type.")
  //		}
  //		if !IsError(mapVal.ConvertToType(ListType)) {
  //			t.Error("Got list, expected error.")
  //		}
  //	}
  //
  //  @Test
  //	void JsonStructEqual() {
  //		reg := newTestRegistry(t)
  //		mapVal := NewJSONStruct(reg,
  //			&structpb.Struct{Fields: map[string]*structpb.Value{
  //				"first":  structpb.NewStringValue("hello"),
  //				"second": structpb.NewNumberValue(4)}})
  //		if mapVal.Equal(mapVal) != True {
  //			t.Error("Map was not equal to itself.")
  //		}
  //		if mapVal.Equal(NewJSONStruct(reg, &structpb.Struct{})) != False {
  //			t.Error("Map with key-value pairs was equal to empty map")
  //		}
  //		if !IsError(mapVal.Equal(String(""))) {
  //			t.Error("Map equal to a non-map type returned non-error.")
  //		}
  //
  //		other := NewJSONStruct(reg,
  //			&structpb.Struct{Fields: map[string]*structpb.Value{
  //				"first":  structpb.NewStringValue("hello"),
  //				"second": structpb.NewNumberValue(1)}})
  //		if mapVal.Equal(other) != False {
  //			t.Errorf("Got equals 'true', expected 'false' for '%v' == '%v'",
  //				mapVal, other)
  //		}
  //		other = NewJSONStruct(reg,
  //			&structpb.Struct{Fields: map[string]*structpb.Value{
  //				"first": structpb.NewStringValue("hello"),
  //				"third": structpb.NewNumberValue(4)}})
  //		if mapVal.Equal(other) != False {
  //			t.Errorf("Got equals 'true', expected 'false' for '%v' == '%v'",
  //				mapVal, other)
  //		}
  //		mismatch := NewDynamicMap(reg,
  //			map[int]interface{}{
  //				1: "hello",
  //				2: "world"})
  //		if !IsError(mapVal.Equal(mismatch)) {
  //			t.Error("Key type mismatch did not result in error")
  //		}
  //	}
  //
  //  @Test
  //	void JsonStructGet() {
  //		if !IsError(NewJSONStruct(newTestRegistry(t), &structpb.Struct{}).Get(Int(1))) {
  //			t.Error("Structs may only have string keys.")
  //		}
  //
  //		reg := newTestRegistry(t)
  //		mapVal := NewJSONStruct(reg,
  //			&structpb.Struct{Fields: map[string]*structpb.Value{
  //				"first":  structpb.NewStringValue("hello"),
  //				"second": structpb.NewNumberValue(4)}})
  //
  //		s := mapVal.Get(String("first"))
  //		if s.Equal(String("hello")) != True {
  //			t.Errorf("Got %v, wanted 'hello'", s)
  //		}
  //
  //		d := mapVal.Get(String("second"))
  //		if d.Equal(Double(4.0)) != True {
  //			t.Errorf("Got %v, wanted '4.0'", d)
  //		}
  //
  //		e, isError := mapVal.Get(String("third")).(*Err)
  //		if !isError || e.Error() != "no such key: third" {
  //			t.Errorf("Got %v, wanted no such key: third", e)
  //		}
  //	}

}
