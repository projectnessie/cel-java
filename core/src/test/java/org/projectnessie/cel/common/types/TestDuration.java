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

public class TestDuration {

  //  @Test
  //	void DurationAdd() {
  //		dur := &dpb.Duration{Seconds: 7506}
  //		d := Duration{Duration: dur.AsDuration()}
  //		if !d.Add(d).Equal(Duration{(&dpb.Duration{Seconds: 15012}).AsDuration()}).(Bool) {
  //			t.Error("Adding duration and itself did not double it.")
  //		}
  //		if lhs, rhs := time.Duration(math.MaxInt64), time.Duration(1);
  // !IsError(durationOf(lhs).Add(durationOf(rhs))) {
  //			t.Errorf("Expected adding %d and %d to result in overflow.", lhs, rhs)
  //		}
  //		if lhs, rhs := time.Duration(math.MinInt64), time.Duration(-1);
  // !IsError(durationOf(lhs).Add(durationOf(rhs))) {
  //			t.Errorf("Expected adding %d and %d to result in overflow.", lhs, rhs)
  //		}
  //		if lhs, rhs := time.Duration(math.MaxInt64-1), time.Duration(1);
  // !durationOf(lhs).Add(durationOf(rhs)).Equal(durationOf(math.MaxInt64)).(Bool) {
  //			t.Errorf("Expected adding %d and %d to yield %d", lhs, rhs, math.MaxInt64)
  //		}
  //		if lhs, rhs := time.Duration(math.MinInt64+1), time.Duration(-1);
  // !durationOf(lhs).Add(durationOf(rhs)).Equal(durationOf(math.MinInt64)).(Bool) {
  //			t.Errorf("Expected adding %d and %d to yield %d", lhs, rhs, math.MaxInt64)
  //		}
  //	}
  //
  //  @Test
  //	void DurationCompare() {
  //		d := Duration{(&dpb.Duration{Seconds: 7506}).AsDuration()}
  //		lt := Duration{(&dpb.Duration{Seconds: -10}).AsDuration()}
  //		if d.Compare(lt).(Int) != IntOne {
  //			t.Error("Larger duration was not considered greater than smaller one.")
  //		}
  //		if lt.Compare(d).(Int) != IntNegOne {
  //			t.Error("Smaller duration was not less than larger one.")
  //		}
  //		if d.Compare(d).(Int) != IntZero {
  //			t.Error("Durations were not considered equal.")
  //		}
  //		if !IsError(d.Compare(False)) {
  //			t.Error("Got comparison result, expected error.")
  //		}
  //	}
  //
  //  @Test
  //	void DurationConvertToNative() {
  //		dur := Duration{Duration: duration(7506, 1000)}
  //		val, err := dur.ConvertToNative(reflect.TypeOf(&dpb.Duration{}))
  //		if err != nil ||
  //			!proto.Equal(val.(proto.Message), &dpb.Duration{Seconds: 7506, Nanos: 1000}) {
  //			t.Errorf("Got '%v', expected backing proto message value", err)
  //		}
  //		val, err = dur.ConvertToNative(reflect.TypeOf(Duration{}))
  //		if err != nil {
  //			t.Fatalf("ConvertToNative() failed: %v", err)
  //		}
  //		if !reflect.DeepEqual(val, dur) {
  //			t.Errorf("got value %v, wanted %v", val, dur)
  //		}
  //		val, err = dur.ConvertToNative(reflect.TypeOf(time.Duration(0)))
  //		if err != nil {
  //			t.Fatalf("ConvertToNative() failed: %v", err)
  //		}
  //		if !reflect.DeepEqual(val, dur.Duration) {
  //			t.Errorf("got value %v, wanted %v", val, dur.Duration)
  //		}
  //	}
  //
  //  @Test
  //	void DurationConvertToNative_Any() {
  //		d := Duration{Duration: duration(7506, 1000)}
  //		val, err := d.ConvertToNative(anyValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want, err := anypb.New(dpb.New(d.Duration))
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if !proto.Equal(val.(proto.Message), want) {
  //			t.Errorf("Got '%v', wanted %v", val, want)
  //		}
  //	}
  //
  //  @Test
  //	void DurationConvertToNative_Error() {
  //		val, err := Duration{Duration: duration(7506, 1000)}.ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Errorf("Got error: '%v', expected value", err)
  //		}
  //		json := val.(*structpb.Value)
  //		want := structpb.NewStringValue("7506.000001s")
  //		if !proto.Equal(json, want) {
  //			t.Errorf("Got %v, wanted %v", json, want)
  //		}
  //	}
  //
  //  @Test
  //	void DurationConvertToNative_Json() {
  //		val, err := Duration{Duration: duration(7506, 1000)}.ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want := structpb.NewStringValue("7506.000001s")
  //		if !proto.Equal(val.(proto.Message), want) {
  //			t.Errorf("Got '%v', wanted %v", val, want)
  //		}
  //	}
  //
  //  @Test
  //	void DurationConvertToType_Identity() {
  //		d := Duration{Duration: duration(7506, 1000)}
  //		str := d.ConvertToType(StringType).(String)
  //		if str != "7506.000001s" {
  //			t.Errorf("Got '%v', wanted 7506.000001s", str)
  //		}
  //		i := d.ConvertToType(IntType).(Int)
  //		if i != Int(7506000001000) {
  //			t.Errorf("Got '%v', wanted 7506000001000", i)
  //		}
  //		if !d.ConvertToType(DurationType).Equal(d).(Bool) {
  //			t.Errorf("Got '%v', wanted identity", d.ConvertToType(DurationType))
  //		}
  //		if d.ConvertToType(TypeType) != DurationType {
  //			t.Errorf("Got '%v', expected duration type", d.ConvertToType(TypeType))
  //		}
  //		if !IsError(d.ConvertToType(UintType)) {
  //			t.Errorf("Got value, expected error.")
  //		}
  //	}
  //
  //  @Test
  //	void DurationNegate() {
  //		neg := Duration{Duration: duration(1234, 1)}.Negate()
  //		want := duration(-1234, -1)
  //		if neg.Value().(time.Duration) != want {
  //			t.Errorf("Got %v, expected %v", neg, want)
  //		}
  //		if v := time.Duration(math.MinInt64); !IsError(durationOf(v).Negate()) {
  //			t.Errorf("Expected negating %d to result in overflow.", v)
  //		}
  //		if v := time.Duration(math.MaxInt64);
  // !durationOf(v).Negate().Equal(durationOf(time.Duration(math.MinInt64 + 1))).(Bool) {
  //			t.Errorf("Expected negating %d to yeild %d", v, time.Duration(math.MinInt64+1))
  //		}
  //	}
  //
  //  @Test
  //	void DurationGetHours() {
  //		d := Duration{Duration: duration(7506, 0)}
  //		hr := d.Receive(overloads.TimeGetHours, overloads.DurationToHours, []ref.Val{})
  //		if !hr.Equal(Int(2)).(Bool) {
  //			t.Error("Expected 2 hours, got", hr)
  //		}
  //	}
  //
  //  @Test
  //	void DurationGetMinutes() {
  //		d := Duration{Duration: duration(7506, 0)}
  //		min := d.Receive(overloads.TimeGetMinutes, overloads.DurationToMinutes, []ref.Val{})
  //		if !min.Equal(Int(125)).(Bool) {
  //			t.Error("Expected 5 minutes, got", min)
  //		}
  //	}
  //
  //  @Test
  //	void DurationGetSeconds() {
  //		d := Duration{Duration: duration(7506, 0)}
  //		sec := d.Receive(overloads.TimeGetSeconds, overloads.DurationToSeconds, []ref.Val{})
  //		if !sec.Equal(Int(7506)).(Bool) {
  //			t.Error("Expected 6 seconds, got", sec)
  //		}
  //	}
  //
  //  @Test
  //	void DurationGetMilliseconds() {
  //		d := Duration{Duration: duration(7506, 0)}
  //		sec := d.Receive(overloads.TimeGetMilliseconds, overloads.DurationToMilliseconds, []ref.Val{})
  //		if !sec.Equal(Int(7506000)).(Bool) {
  //			t.Error("Expected 6 seconds, got", sec)
  //		}
  //	}
  //
  //  @Test
  //	void DurationSubtract() {
  //		d := Duration{Duration: duration(7506, 0)}
  //		if !d.Subtract(d).ConvertToType(IntType).Equal(IntZero).(Bool) {
  //			t.Error("Subtracting a duration from itself did not equal zero.")
  //		}
  //		if lhs, rhs := time.Duration(math.MaxInt64), time.Duration(-1);
  // !IsError(durationOf(lhs).Subtract(durationOf(rhs))) {
  //			t.Errorf("Expected subtracting %d and %d to result in overflow.", lhs, rhs)
  //		}
  //		if lhs, rhs := time.Duration(math.MinInt64), time.Duration(1);
  // !IsError(durationOf(lhs).Subtract(durationOf(rhs))) {
  //			t.Errorf("Expected subtracting %d and %d to result in overflow.", lhs, rhs)
  //		}
  //		if lhs, rhs := time.Duration(math.MaxInt64-1), time.Duration(-1);
  // !durationOf(lhs).Subtract(durationOf(rhs)).Equal(durationOf(math.MaxInt64)).(Bool) {
  //			t.Errorf("Expected subtracting %d and %d to yield %d", lhs, rhs, math.MaxInt64)
  //		}
  //		if lhs, rhs := time.Duration(math.MinInt64+1), time.Duration(1);
  // !durationOf(lhs).Subtract(durationOf(rhs)).Equal(durationOf(math.MinInt64)).(Bool) {
  //			t.Errorf("Expected subtracting %d and %d to yield %d", lhs, rhs, math.MinInt64)
  //		}
  //	}
  //
  //	func duration(seconds, nanos int64) time.Duration {
  //		return time.Duration(seconds)*time.Second + time.Duration(nanos)
  //	}

}
