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

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import static org.projectnessie.cel.common.types.TimestampT.parseTz;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.TypeT.TypeType;

import com.google.protobuf.Any;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.cel.common.types.ref.Val;

public class TimestampTest {

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

  @Test
  void parseTimezone() {
    ZoneId zoneUTC = ZoneId.of("UTC");

    assertThat(
            asList(
                TimestampT.parseTz("-0"),
                TimestampT.parseTz("+0"),
                TimestampT.parseTz("0"),
                TimestampT.parseTz("-00"),
                TimestampT.parseTz("+00"),
                TimestampT.parseTz("00"),
                TimestampT.parseTz("-0:0"),
                TimestampT.parseTz("+0:0:0"),
                TimestampT.parseTz("0:0:0"),
                TimestampT.parseTz("-00:0:0"),
                TimestampT.parseTz("+0:00:0"),
                TimestampT.parseTz("+00:00:0")))
        .containsExactly(
            zoneUTC, zoneUTC, zoneUTC, zoneUTC, zoneUTC, zoneUTC, zoneUTC, zoneUTC, zoneUTC,
            zoneUTC, zoneUTC, zoneUTC);

    assertThatThrownBy(() -> TimestampT.parseTz("+19")).isInstanceOf(DateTimeException.class);
    assertThatThrownBy(() -> TimestampT.parseTz("+1:61")).isInstanceOf(DateTimeException.class);
    assertThatThrownBy(() -> TimestampT.parseTz("+1:60:0")).isInstanceOf(DateTimeException.class);
    assertThatThrownBy(() -> TimestampT.parseTz("+1:1:60")).isInstanceOf(DateTimeException.class);

    assertThat(
            asList(
                TimestampT.parseTz("-1"),
                TimestampT.parseTz("+1"),
                TimestampT.parseTz("1"),
                TimestampT.parseTz("-01:2:30"),
                TimestampT.parseTz("+02:30"),
                TimestampT.parseTz("0:1:3")))
        .containsExactly(
            ZoneId.ofOffset("UTC", ZoneOffset.ofHoursMinutesSeconds(-1, 0, 0)),
            ZoneId.ofOffset("UTC", ZoneOffset.ofHoursMinutesSeconds(1, 0, 0)),
            ZoneId.ofOffset("UTC", ZoneOffset.ofHoursMinutesSeconds(1, 0, 0)),
            ZoneId.ofOffset("UTC", ZoneOffset.ofHoursMinutesSeconds(-1, -2, -30)),
            ZoneId.ofOffset("UTC", ZoneOffset.ofHoursMinutesSeconds(2, 30, 0)),
            ZoneId.ofOffset("UTC", ZoneOffset.ofHoursMinutesSeconds(0, 1, 3)));
  }

  static class ParseTestCase {
    String tz;
    final String timestamp;
    final int[] tuple;
    final LocalDateTime ldt;

    ParseTestCase(String timestamp, int[] tuple) {
      this.timestamp = timestamp;
      this.tuple = tuple;
      this.ldt = LocalDateTime.of(tuple[0], tuple[1], tuple[2], tuple[3], tuple[4], tuple[5]);
    }

    ParseTestCase withTZ(String tz) {
      ParseTestCase copy = new ParseTestCase(timestamp, tuple);
      copy.tz = tz;
      return copy;
    }

    @Override
    public String toString() {
      return "timestamp='" + timestamp + '\'' + ", tz='" + tz + '\'';
    }
  }

  static final int numTimeZones = 5;
  static final int numOffsets = 5;
  static final int numDateTimes = 10;

  @SuppressWarnings("unused")
  static List<ParseTestCase> timestampParsingTestCases() {
    ThreadLocalRandom rand = ThreadLocalRandom.current();

    List<ParseTestCase> testCases = new ArrayList<>(numDateTimes * numTimeZones);

    testCases.add(
        new ParseTestCase("2009-02-13T23:31:30Z", new int[] {2009, 2, 13, 23, 31, 30})
            .withTZ("Australia/Sydney"));
    testCases.add(
        new ParseTestCase("2009-02-13T23:31:30Z", new int[] {2009, 2, 13, 23, 31, 30})
            .withTZ("+11:00"));
    // time-zones unknown to ZoneId
    testCases.add(
        new ParseTestCase("2009-02-13T23:31:30Z", new int[] {2009, 2, 13, 23, 31, 30})
            .withTZ("CST"));
    testCases.add(
        new ParseTestCase("2009-02-13T23:31:30Z", new int[] {2009, 2, 13, 23, 31, 30})
            .withTZ("MIT"));

    // Collect a couple of random time zones and date-times.
    List<TimeZone> availableTimeZones =
        Arrays.stream(TimeZone.getAvailableIDs())
            .map(TimeZone::getTimeZone)
            .collect(Collectors.toList());

    for (int j = 0; j < numDateTimes; j++) {
      int year = rand.nextInt(1970, 2200);
      int month = rand.nextInt(1, 13);
      int day = rand.nextInt(1, 28);
      int hour = rand.nextInt(0, 24);
      int minute = rand.nextInt(0, 60);
      int second = rand.nextInt(0, 60);
      String dateTime =
          String.format("%04d-%02d-%02dT%02d:%02d:%02dZ", year, month, day, hour, minute, second);
      int[] tuple = new int[] {year, month, day, hour, minute, second};

      ParseTestCase noTzTestCase = new ParseTestCase(dateTime, tuple);

      for (int i = 0; i < numTimeZones; i++) {
        TimeZone tz = availableTimeZones.remove(rand.nextInt(availableTimeZones.size()));
        testCases.add(noTzTestCase.withTZ(tz.getID()));
      }

      for (int i = 0; i < numOffsets; i++) {
        int offsetHours = rand.nextInt(-18, 19);
        String id = String.format("%+d:%02d", offsetHours, 0);
        testCases.add(noTzTestCase.withTZ(id));
      }
    }

    return testCases;
  }

  @ParameterizedTest
  @MethodSource("timestampParsingTestCases")
  void timestampParsing(ParseTestCase tc) {
    Val ts = stringOf(tc.timestamp).convertToType(TimestampType);

    ZonedDateTime value = (ZonedDateTime) ts.value();
    LocalDateTime dtZlocal = value.withZoneSameLocal(ZoneIdZ).toLocalDateTime();

    // Verify that the values in ParseTestCase are fine

    assertThat(tc.tuple)
        .containsExactly(
            dtZlocal.getYear(),
            dtZlocal.getMonthValue(),
            dtZlocal.getDayOfMonth(),
            dtZlocal.getHour(),
            dtZlocal.getMinute(),
            dtZlocal.getSecond());

    assertThat(tc.tuple)
        .containsExactly(
            value.getYear(),
            value.getMonthValue(),
            value.getDayOfMonth(),
            value.getHour(),
            value.getMinute(),
            value.getSecond());

    // check the timestampGetXyz methods (without a time-zone), (assuming UTC)

    assertThat(
            asList(
                TimestampT.timestampGetFullYear(value),
                TimestampT.timestampGetMonth(value),
                TimestampT.timestampGetDayOfMonthOneBased(value),
                TimestampT.timestampGetDayOfMonthZeroBased(value),
                TimestampT.timestampGetHours(value),
                TimestampT.timestampGetMinutes(value),
                TimestampT.timestampGetSeconds(value),
                TimestampT.timestampGetDayOfWeek(value),
                TimestampT.timestampGetDayOfYear(value)))
        .containsExactly(
            intOf(dtZlocal.getYear()),
            intOf(dtZlocal.getMonthValue() - 1),
            intOf(dtZlocal.getDayOfMonth()),
            intOf(dtZlocal.getDayOfMonth() - 1),
            intOf(dtZlocal.getHour()),
            intOf(dtZlocal.getMinute()),
            intOf(dtZlocal.getSecond()),
            intOf(dtZlocal.getDayOfWeek().getValue()),
            intOf(dtZlocal.getDayOfYear() - 1));

    // check the timestampGetXyzWithTu methods (with a time-zone)

    ZoneId zoneId = parseTz(tc.tz);

    ZonedDateTime atZone = value.withZoneSameInstant(zoneId);
    Val tzVal = stringOf(tc.tz);

    assertThat(
            asList(
                TimestampT.timestampGetFullYearWithTz(value, tzVal),
                TimestampT.timestampGetMonthWithTz(value, tzVal),
                TimestampT.timestampGetDayOfMonthOneBasedWithTz(value, tzVal),
                TimestampT.timestampGetDayOfMonthZeroBasedWithTz(value, tzVal),
                TimestampT.timestampGetHoursWithTz(value, tzVal),
                TimestampT.timestampGetMinutesWithTz(value, tzVal),
                TimestampT.timestampGetSecondsWithTz(value, tzVal),
                TimestampT.timestampGetDayOfWeekWithTz(value, tzVal),
                TimestampT.timestampGetDayOfYearWithTz(value, tzVal)))
        .containsExactly(
            intOf(atZone.getYear()),
            intOf(atZone.getMonthValue() - 1),
            intOf(atZone.getDayOfMonth()),
            intOf(atZone.getDayOfMonth() - 1),
            intOf(atZone.getHour()),
            intOf(atZone.getMinute()),
            intOf(atZone.getSecond()),
            intOf(atZone.getDayOfWeek().getValue()),
            intOf(atZone.getDayOfYear() - 1));
  }

  private TimestampT defaultTS() {
    return timestampOf(Instant.ofEpochSecond(7506).atZone(ZoneIdZ));
  }

  @Test
  void nanoMicroMilliPrecision() {
    long secondsEpoch = 1624006650L;
    String ts0 = "2021-06-18T08:57:30Z";
    int nano3 = 123000000;
    String ts3 = "2021-06-18T08:57:30.123Z";
    int nano4 = 123400000;
    String ts4 = "2021-06-18T08:57:30.1234Z";
    int nano6 = 123456000;
    String ts6 = "2021-06-18T08:57:30.123456Z";
    int nano9 = 123456789;
    String ts9 = "2021-06-18T08:57:30.123456789Z";

    Object z = timestampOf(ts0).value();
    assertThat(z).extracting(x -> ((ZonedDateTime) x).toEpochSecond()).isEqualTo(secondsEpoch);
    assertThat(z).extracting(x -> ((ZonedDateTime) x).getNano()).isEqualTo(0);

    z = timestampOf(ts3).value();
    assertThat(z).extracting(x -> ((ZonedDateTime) x).toEpochSecond()).isEqualTo(secondsEpoch);
    assertThat(z).extracting(x -> ((ZonedDateTime) x).getNano()).isEqualTo(nano3);

    z = timestampOf(ts4).value();
    assertThat(z).extracting(x -> ((ZonedDateTime) x).toEpochSecond()).isEqualTo(secondsEpoch);
    assertThat(z).extracting(x -> ((ZonedDateTime) x).getNano()).isEqualTo(nano4);

    z = timestampOf(ts6).value();
    assertThat(z).extracting(x -> ((ZonedDateTime) x).toEpochSecond()).isEqualTo(secondsEpoch);
    assertThat(z).extracting(x -> ((ZonedDateTime) x).getNano()).isEqualTo(nano6);

    z = timestampOf(ts9).value();
    assertThat(z).extracting(x -> ((ZonedDateTime) x).toEpochSecond()).isEqualTo(secondsEpoch);
    assertThat(z).extracting(x -> ((ZonedDateTime) x).getNano()).isEqualTo(nano9);
  }
}
