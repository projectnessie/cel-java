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

public class TestDouble {

  //  @Test
  //	void DoubleAdd() {
  //		if !Double(4).Add(Double(-3.5)).Equal(Double(0.5)).(Bool) {
  //			t.Error("Adding two doubles did not match expected value.")
  //		}
  //		if !IsError(Double(-1).Add(String("-1"))) {
  //			t.Error("Adding non-double to double was not an error.")
  //		}
  //	}
  //
  //  @Test
  //	void DoubleCompare() {
  //		lt := Double(-1300)
  //		gt := Double(204)
  //		if !lt.Compare(gt).Equal(IntNegOne).(Bool) {
  //			t.Error("Comparison did not yield - 1")
  //		}
  //		if !gt.Compare(lt).Equal(IntOne).(Bool) {
  //			t.Error("Comparison did not yield 1")
  //		}
  //		if !gt.Compare(gt).Equal(IntZero).(Bool) {
  //			t.Error(("Comparison did not yield 0"))
  //		}
  //		if !IsError(gt.Compare(TypeType)) {
  //			t.Error("Types not comparable")
  //		}
  //	}
  //
  //  @Test
  //	void DoubleConvertToNative_Any() {
  //		val, err := Double(math.MaxFloat64).ConvertToNative(anyValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want, err := anypb.New(wrapperspb.Double(1.7976931348623157e+308))
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if !proto.Equal(val.(proto.Message), want) {
  //			t.Errorf("Got '%v', wanted %v", val, want)
  //		}
  //	}
  //
  //  @Test
  //	void DoubleConvertToNative_Error() {
  //		val, err := Double(-10000).ConvertToNative(reflect.TypeOf(""))
  //		if err == nil {
  //			t.Errorf("Got '%v', expected error", val)
  //		}
  //	}
  //
  //  @Test
  //	void DoubleConvertToNative_Float32() {
  //		val, err := Double(3.1415).ConvertToNative(reflect.TypeOf(float32(0)))
  //		if err != nil {
  //			t.Error(err)
  //		} else if val.(float32) != 3.1415 {
  //			t.Errorf("Got '%v', wanted 3.1415", val)
  //		}
  //	}
  //
  //  @Test
  //	void DoubleConvertToNative_Float64() {
  //		val, err := Double(30000000.1).ConvertToNative(reflect.TypeOf(float64(0)))
  //		if err != nil {
  //			t.Error(err)
  //		} else if val.(float64) != 30000000.1 {
  //			t.Errorf("Got '%v', wanted 330000000.1", val)
  //		}
  //	}
  //
  //  @Test
  //	void DoubleConvertToNative_Json() {
  //		val, err := Double(-1.4).ConvertToNative(jsonValueType)
  //		pbVal := structpb.NewNumberValue(-1.4)
  //		if err != nil {
  //			t.Error(err)
  //		} else if !proto.Equal(val.(proto.Message), pbVal) {
  //			t.Errorf("Got '%v', expected -1.4", val)
  //		}
  //
  //		val, err = Double(math.NaN()).ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Error(err)
  //		} else {
  //			v := val.(*structpb.Value)
  //			if !math.IsNaN(v.GetNumberValue()) {
  //				t.Errorf("Got '%v', expected NaN", val)
  //			}
  //		}
  //
  //		val, err = Double(math.Inf(-1)).ConvertToNative(jsonValueType)
  //		pbVal = structpb.NewNumberValue(math.Inf(-1))
  //		if err != nil {
  //			t.Error(err)
  //		} else if !proto.Equal(val.(proto.Message), pbVal) {
  //			t.Errorf("Got '%v', expected -Infinity", val)
  //		}
  //		val, err = Double(math.Inf(0)).ConvertToNative(jsonValueType)
  //		pbVal = structpb.NewNumberValue(math.Inf(0))
  //		if err != nil {
  //			t.Error(err)
  //		} else if !proto.Equal(val.(proto.Message), pbVal) {
  //			t.Errorf("Got '%v', expected Infinity", val)
  //		}
  //	}
  //
  //  @Test
  //	void DoubleConvertToNative_Ptr_Float32() {
  //		ptrType := float32(0)
  //		val, err := Double(3.1415).ConvertToNative(reflect.TypeOf(&ptrType))
  //		if err != nil {
  //			t.Error(err)
  //		} else if *val.(*float32) != 3.1415 {
  //			t.Errorf("Got '%v', wanted 3.1415", val)
  //		}
  //	}
  //
  //  @Test
  //	void DoubleConvertToNative_Ptr_Float64() {
  //		ptrType := float64(0)
  //		val, err := Double(30000000.1).ConvertToNative(reflect.TypeOf(&ptrType))
  //		if err != nil {
  //			t.Error(err)
  //		} else if *val.(*float64) != 30000000.1 {
  //			t.Errorf("Got '%v', wanted 330000000.1", val)
  //		}
  //	}
  //
  //  @Test
  //	void DoubleConvertToNative_Wrapper() {
  //		val, err := Double(3.1415).ConvertToNative(floatWrapperType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want := wrapperspb.Float(3.1415)
  //		if !proto.Equal(val.(proto.Message), want) {
  //			t.Errorf("Got '%v', wanted %v", val, want)
  //		}
  //
  //		val, err = Double(math.MaxFloat64).ConvertToNative(doubleWrapperType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want2 := wrapperspb.Double(1.7976931348623157e+308)
  //		if !proto.Equal(val.(proto.Message), want2) {
  //			t.Errorf("Got '%v', wanted %v", val, want2)
  //		}
  //	}
  //
  //  @Test
  //	void DoubleConvertToType() {
  //		if !Double(-4.5).ConvertToType(IntType).Equal(Int(-5)).(Bool) {
  //			t.Error("Unsuccessful type conversion to int")
  //		}
  //		if !IsError(Double(-4.5).ConvertToType(UintType)) {
  //			t.Error("Got uint, expected error")
  //		}
  //		if !Double(-4.5).ConvertToType(DoubleType).Equal(Double(-4.5)).(Bool) {
  //			t.Error("Unsuccessful type conversion to double")
  //		}
  //		if !Double(-4.5).ConvertToType(StringType).Equal(String("-4.5")).(Bool) {
  //			t.Error("Unsuccessful type conversion to string")
  //		}
  //		if !Double(-4.5).ConvertToType(TypeType).Equal(DoubleType).(Bool) {
  //			t.Error("Unsuccessful type conversion to type")
  //		}
  //		if !IsError(Double(-4.5).ConvertToType(TimestampType)) {
  //			t.Error("Got value, expected error")
  //		}
  //	}
  //
  //  @Test
  //	void DoubleDivide() {
  //		if !Double(3).Divide(Double(1.5)).Equal(Double(2)).(Bool) {
  //			t.Error("Dividing two doubles did not match expectations.")
  //		}
  //		var z float64 // Avoid 0.0 since const div by zero is an error.
  //		if !Double(1.1).Divide(Double(0)).Equal(Double(1.1 / z)).(Bool) {
  //			t.Error("Division by zero did not match infinity.")
  //		}
  //		if !IsError(Double(1.1).Divide(IntNegOne)) {
  //			t.Error("Division permitted without express type-conversion.")
  //		}
  //	}
  //
  //  @Test
  //	void DoubleEqual() {
  //		if !IsError(Double(0).Equal(False)) {
  //			t.Error("Double equal to non-double resulted in non-error.")
  //		}
  //	}
  //
  //  @Test
  //	void DoubleMultiply() {
  //		if !Double(1.1).Multiply(Double(-1.2)).Equal(Double(-1.32)).(Bool) {
  //			t.Error("Multiplying two doubles did not match expectations.")
  //		}
  //		if !IsError(Double(1.1).Multiply(IntNegOne)) {
  //			t.Error("Multiplication permitted without express type-conversion.")
  //		}
  //	}
  //
  //  @Test
  //	void DoubleNegate() {
  //		if !Double(1.1).Negate().Equal(Double(-1.1)).(Bool) {
  //			t.Error("Negating double value did not succeed")
  //		}
  //	}
  //
  //  @Test
  //	void DoubleSubtract() {
  //		if !Double(4).Subtract(Double(-3.5)).Equal(Double(7.5)).(Bool) {
  //			t.Error("Subtracting two doubles did not match expected value.")
  //		}
  //		if !IsError(Double(1.1).Subtract(IntNegOne)) {
  //			t.Error("Subtraction permitted without express type-conversion.")
  //		}
  //	}

}
