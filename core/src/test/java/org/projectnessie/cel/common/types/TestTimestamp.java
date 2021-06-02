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

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DurationT.DurationType;
import static org.projectnessie.cel.common.types.DurationT.durationOf;
import static org.projectnessie.cel.common.types.Err.errDurationOverflow;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.TimestampType;
import static org.projectnessie.cel.common.types.TimestampT.ZoneIdZ;
import static org.projectnessie.cel.common.types.TimestampT.maxUnixTime;
import static org.projectnessie.cel.common.types.TimestampT.minUnixTime;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.ref.Val;

public class TestTimestamp {

  @Test
  void TimestampAdd() {
    TimestampT ts = defaultTS();
    Val val =
        ts.add(
            durationOf(
                Duration.ofNanos(
                    TimeUnit.SECONDS.toNanos(3600) + TimeUnit.MILLISECONDS.toNanos(1))));
    assertThat(val.convertToType(TypeType)).isSameAs(TimestampType);
    TimestampT expected =
        timestampOf(Instant.ofEpochMilli(TimeUnit.SECONDS.toMillis(11106) + 1).atZone(ZoneIdZ));
    assertThat(expected.compare(val)).isSameAs(IntZero);
    assertThat(ts.add(expected))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");

    ZonedDateTime max999 = Instant.ofEpochSecond(maxUnixTime, 999999999).atZone(ZoneIdZ);
    ZonedDateTime max998 = Instant.ofEpochSecond(maxUnixTime, 999999998).atZone(ZoneIdZ);
    ZonedDateTime min0 = Instant.ofEpochSecond(minUnixTime, 0).atZone(ZoneIdZ);
    ZonedDateTime min1 = Instant.ofEpochSecond(minUnixTime, 1).atZone(ZoneIdZ);

    assertThat(timestampOf(max999).add(durationOf(Duration.ofNanos(1))))
        .isSameAs(errDurationOverflow);
    assertThat(timestampOf(min0).add(durationOf(Duration.ofNanos(-1))))
        .isSameAs(errDurationOverflow);

    assertThat(timestampOf(max999).add(durationOf(Duration.ofNanos(-1))).equal(timestampOf(max998)))
        .isSameAs(True);
    assertThat(timestampOf(min0).add(durationOf(Duration.ofNanos(1))).equal(timestampOf(min1)))
        .isSameAs(True);
  }

  //  @Test
  //	void TimestampConvertToNative_Any() {
  //		// 1970-01-01T02:05:06Z
  //		TimestampT ts = timestampOf(Instant.ofEpochSecond(7506));
  //		val, err := ts.ConvertToNative(anyValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want, err := anypb.New(tpb.New(ts.Time))
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if !proto.Equal(val.(proto.Message), want) {
  //			t.Errorf("Got '%v', expected '%v'", val, want)
  //		}
  //	}

  //  @Test
  //	void TimestampConvertToNative() {
  //		// 1970-01-01T02:05:06Z
  //		ts := Timestamp{Time: time.Unix(7506, 0).UTC()}
  //		val, err := ts.ConvertToNative(timestampValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		var want interface{}
  //		want = tpb.New(ts.Time)
  //		if !proto.Equal(val.(proto.Message), want.(proto.Message)) {
  //			t.Errorf("Got '%v', expected '%v'", val, want)
  //		}
  //		val, err = ts.ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want = structpb.NewStringValue("1970-01-01T02:05:06Z")
  //		if !proto.Equal(val.(proto.Message), want.(proto.Message)) {
  //			t.Errorf("Got '%v', expected '%v'", val, want)
  //		}
  //		val, err = ts.ConvertToNative(anyValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want, err = anypb.New(tpb.New(ts.Time))
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if !proto.Equal(val.(proto.Message), want.(proto.Message)) {
  //			t.Errorf("Got '%v', expected '%v'", val, want)
  //		}
  //		val, err = ts.ConvertToNative(reflect.TypeOf(Timestamp{}))
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if !reflect.DeepEqual(val, ts) {
  //			t.Errorf("got %v wanted %v", val, ts)
  //		}
  //		val, err = ts.ConvertToNative(reflect.TypeOf(time.Now()))
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want = time.Unix(7506, 0).UTC()
  //		if !reflect.DeepEqual(val, want) {
  //			t.Errorf("got %v wanted %v", val, want)
  //		}
  //	}

  @Test
  void TimestampSubtract() {
    TimestampT ts = defaultTS();
    Val val = ts.subtract(durationOf(Duration.ofSeconds(3600, 1000)));
    assertThat(val.convertToType(TypeType)).isSameAs(TimestampType);

    TimestampT expected = timestampOf(Instant.ofEpochSecond(3905, 999999000).atZone(ZoneIdZ));
    assertThat(expected.compare(val)).isSameAs(IntZero);
    TimestampT ts2 = timestampOf(Instant.ofEpochSecond(6506, 0).atZone(ZoneIdZ));
    val = ts.subtract(ts2);
    assertThat(val.convertToType(TypeType)).isSameAs(DurationType);

    DurationT expectedDur = durationOf(Duration.ofNanos(1000000000000L));
    assertThat(expectedDur.compare(val)).isSameAs(IntZero);
  }

  @Test
  void TimestampGetDayOfYear() {
    // 1970-01-01T02:05:06Z
    TimestampT ts = timestampOf(Instant.ofEpochSecond(7506).atZone(ZoneIdZ));
    Val hr = ts.receive(Overloads.TimeGetDayOfYear, Overloads.TimestampToDayOfYear);
    assertThat(hr).isSameAs(IntZero);
    // 1969-12-31T19:05:06Z
    Val hrTz =
        ts.receive(
            Overloads.TimeGetDayOfYear,
            Overloads.TimestampToDayOfYearWithTz,
            stringOf("America/Phoenix"));
    assertThat(hrTz.equal(intOf(364))).isSameAs(True);
    hrTz =
        ts.receive(
            Overloads.TimeGetDayOfYear, Overloads.TimestampToDayOfYearWithTz, stringOf("-07:00"));
    assertThat(hrTz.equal(intOf(364))).isSameAs(True);
  }

  @Test
  void TimestampGetMonth() {
    // 1970-01-01T02:05:06Z
    TimestampT ts = defaultTS();
    Val hr = ts.receive(Overloads.TimeGetMonth, Overloads.TimestampToMonth);
    assertThat(hr.value()).isEqualTo(0L);
    // 1969-12-31T19:05:06Z
    Val hrTz =
        ts.receive(
            Overloads.TimeGetMonth, Overloads.TimestampToMonthWithTz, stringOf("America/Phoenix"));
    assertThat(hrTz.value()).isEqualTo(11L);
  }

  @Test
  void TimestampGetHours() {
    // 1970-01-01T02:05:06Z
    TimestampT ts = defaultTS();
    Val hr = ts.receive(Overloads.TimeGetHours, Overloads.TimestampToHours);
    assertThat(hr.value()).isEqualTo(2L);
    // 1969-12-31T19:05:06Z
    Val hrTz =
        ts.receive(
            Overloads.TimeGetHours, Overloads.TimestampToHoursWithTz, stringOf("America/Phoenix"));
    assertThat(hrTz.value()).isEqualTo(19L);
  }

  @Test
  void TimestampGetMinutes() {
    // 1970-01-01T02:05:06Z
    TimestampT ts = defaultTS();
    Val min = ts.receive(Overloads.TimeGetMinutes, Overloads.TimestampToMinutes);
    assertThat(min.equal(intOf(5))).isSameAs(True);
    // 1969-12-31T19:05:06Z
    Val minTz =
        ts.receive(
            Overloads.TimeGetMinutes,
            Overloads.TimestampToMinutesWithTz,
            stringOf("America/Phoenix"));
    assertThat(minTz.equal(intOf(5))).isSameAs(True);
  }

  @Test
  void TimestampGetSeconds() {
    // 1970-01-01T02:05:06Z
    TimestampT ts = defaultTS();
    Val sec = ts.receive(Overloads.TimeGetSeconds, Overloads.TimestampToSeconds);
    assertThat(sec.equal(intOf(6))).isSameAs(True);
    // 1969-12-31T19:05:06Z
    Val secTz =
        ts.receive(
            Overloads.TimeGetSeconds,
            Overloads.TimestampToSecondsWithTz,
            stringOf("America/Phoenix"));
    assertThat(secTz.equal(intOf(6))).isSameAs(True);
  }

  private TimestampT defaultTS() {
    return timestampOf(Instant.ofEpochSecond(7506).atZone(ZoneIdZ));
  }
}
