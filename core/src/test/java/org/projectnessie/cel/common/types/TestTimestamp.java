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

import com.google.protobuf.Any;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.ref.Val;

public class TestTimestamp {

  @Test
  void timestampAdd() {
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
        .isEqualTo("no such overload: google.protobuf.Timestamp.add(google.protobuf.Timestamp)");

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

  @Test
  void timestampConvertToNative_Any() {
    // 1970-01-01T02:05:06Z
    TimestampT ts = timestampOf(Instant.ofEpochSecond(7506).atZone(ZoneIdZ));
    Any val = ts.convertToNative(Any.class);
    Any want = Any.pack(Timestamp.newBuilder().setSeconds(7506).build());
    assertThat(val).isEqualTo(want);
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void timestampConvertToNative_JSON() {
    TimestampT ts = timestampOf(Instant.ofEpochSecond(7506).atZone(ZoneIdZ));

    // JSON
    Object val = ts.convertToNative(Value.class);
    Object want = StringValue.of("1970-01-01T02:05:06Z");
    assertThat(val).isEqualTo(want);
  }

  @Test
  void timestampConvertToNative() {
    // 1970-01-01T02:05:06Z
    TimestampT ts = timestampOf(Instant.ofEpochSecond(7506).atZone(ZoneIdZ));

    Object val = ts.convertToNative(Timestamp.class);
    Object want;
    want = Timestamp.newBuilder().setSeconds(7506).build();
    assertThat(val).isEqualTo(want);

    val = ts.convertToNative(Any.class);
    want = Any.pack(Timestamp.newBuilder().setSeconds(7506).build());
    assertThat(val).isEqualTo(want);

    val = ts.convertToNative(ZonedDateTime.class);
    assertThat(val).isSameAs(ts.value());

    val = ts.convertToNative(Date.class);
    want = new Date(TimeUnit.SECONDS.toMillis(7506));
    assertThat(val).isEqualTo(want);
  }

  @Test
  void timestampSubtract() {
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
  void timestampGetDayOfYear() {
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
  void timestampGetMonth() {
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
  void timestampGetHours() {
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
  void timestampGetMinutes() {
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
  void timestampGetSeconds() {
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
