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

import static org.projectnessie.cel.common.types.BoolT.boolOf;
import static org.projectnessie.cel.common.types.DurationT.DurationType;
import static org.projectnessie.cel.common.types.DurationT.durationOf;
import static org.projectnessie.cel.common.types.Err.errDurationOverflow;
import static org.projectnessie.cel.common.types.Err.errTimestampOverflow;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.IntT.IntNegOne;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;

import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.projectnessie.cel.common.types.Overflow.OverflowException;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Adder;
import org.projectnessie.cel.common.types.traits.Comparer;
import org.projectnessie.cel.common.types.traits.Receiver;
import org.projectnessie.cel.common.types.traits.Subtractor;
import org.projectnessie.cel.common.types.traits.Trait;

/**
 * Timestamp type implementation which supports add, compare, and subtract operations. Timestamps
 * are also capable of participating in dynamic function dispatch to instance methods.
 */
public final class TimestampT extends BaseVal implements Adder, Comparer, Receiver, Subtractor {

  /**
   * The number of seconds between year 1 and year 1970. This is borrowed from
   * https://golang.org/src/time/time.go.
   */
  public static final long unixToInternal =
      (1969L * 365 + 1969 / 4 - 1969 / 100 + 1969 / 400) * (60 * 60 * 24);

  /** Number of seconds between `0001-01-01T00:00:00Z` and the Unix epoch. */
  public static final long minUnixTime = -62135596800L;
  /** Number of seconds between `9999-12-31T23:59:59.999999999Z` and the Unix epoch. */
  public static final long maxUnixTime = 253402300799L;

  public static final ZoneId ZoneIdZ = ZoneId.of("Z");

  public static final Map<String, Function<ZonedDateTime, Val>> timestampZeroArgOverloads;
  public static final Map<String, BiFunction<ZonedDateTime, Val, Val>> timestampOneArgOverloads;

  static {
    timestampZeroArgOverloads = new HashMap<>();
    timestampZeroArgOverloads.put(Overloads.TimeGetFullYear, TimestampT::timestampGetFullYear);
    timestampZeroArgOverloads.put(Overloads.TimeGetMonth, TimestampT::timestampGetMonth);
    timestampZeroArgOverloads.put(Overloads.TimeGetDayOfYear, TimestampT::timestampGetDayOfYear);
    timestampZeroArgOverloads.put(
        Overloads.TimeGetDate, TimestampT::timestampGetDayOfMonthOneBased);
    timestampZeroArgOverloads.put(
        Overloads.TimeGetDayOfMonth, TimestampT::timestampGetDayOfMonthZeroBased);
    timestampZeroArgOverloads.put(Overloads.TimeGetDayOfWeek, TimestampT::timestampGetDayOfWeek);
    timestampZeroArgOverloads.put(Overloads.TimeGetHours, TimestampT::timestampGetHours);
    timestampZeroArgOverloads.put(Overloads.TimeGetMinutes, TimestampT::timestampGetMinutes);
    timestampZeroArgOverloads.put(Overloads.TimeGetSeconds, TimestampT::timestampGetSeconds);
    timestampZeroArgOverloads.put(
        Overloads.TimeGetMilliseconds, TimestampT::timestampGetMilliseconds);

    timestampOneArgOverloads = new HashMap<>();
    timestampOneArgOverloads.put(Overloads.TimeGetFullYear, TimestampT::timestampGetFullYearWithTz);
    timestampOneArgOverloads.put(Overloads.TimeGetMonth, TimestampT::timestampGetMonthWithTz);
    timestampOneArgOverloads.put(
        Overloads.TimeGetDayOfYear, TimestampT::timestampGetDayOfYearWithTz);
    timestampOneArgOverloads.put(
        Overloads.TimeGetDate, TimestampT::timestampGetDayOfMonthOneBasedWithTz);
    timestampOneArgOverloads.put(
        Overloads.TimeGetDayOfMonth, TimestampT::timestampGetDayOfMonthZeroBasedWithTz);
    timestampOneArgOverloads.put(
        Overloads.TimeGetDayOfWeek, TimestampT::timestampGetDayOfWeekWithTz);
    timestampOneArgOverloads.put(Overloads.TimeGetHours, TimestampT::timestampGetHoursWithTz);
    timestampOneArgOverloads.put(Overloads.TimeGetMinutes, TimestampT::timestampGetMinutesWithTz);
    timestampOneArgOverloads.put(Overloads.TimeGetSeconds, TimestampT::timestampGetSecondsWithTz);
    timestampOneArgOverloads.put(
        Overloads.TimeGetMilliseconds, TimestampT::timestampGetMillisecondsWithTz);
  }

  /** TimestampType singleton. */
  public static final TypeValue TimestampType =
      TypeValue.newTypeValue(
          "google.protobuf.Timestamp",
          Trait.AdderType,
          Trait.ComparerType,
          Trait.ReceiverType,
          Trait.SubtractorType);

  private final ZonedDateTime t;

  private TimestampT(ZonedDateTime t) {
    this.t = t;
  }

  public static TimestampT timestampOf(String s) {
    // TODO is this correct??
    ZonedDateTime inst = TimestampT.rfc3339nanoFormatter().parse(s, ZonedDateTime::from);
    // TODO want this??
    //    long unitTime = TimeUnit.MILLISECONDS.toSeconds(inst.toEpochMilli());
    //    if (unitTime < minUnixTime || unitTime > maxUnixTime) {
    //      return errTimestampOverflow;
    //    }
    return timestampOf(inst);
  }

  public static TimestampT timestampOf(Timestamp t) {
    LocalDateTime ldt = LocalDateTime.ofEpochSecond(t.getSeconds(), t.getNanos(), ZoneOffset.UTC);
    ZonedDateTime zdt = ZonedDateTime.of(ldt, ZoneIdZ);
    return new TimestampT(zdt);
  }

  public static TimestampT timestampOf(ZonedDateTime t) {
    // Note that this function does not valiate that time.Time is in our supported range.
    return new TimestampT(t);
  }

  /** Add implements traits.Adder.Add. */
  @Override
  public Val add(Val other) {
    if (other.type() == DurationType) {
      return ((DurationT) other).add(this);
    }
    return noSuchOverload(this, "add", other);
  }

  /** Compare implements traits.Comparer.Compare. */
  @Override
  public Val compare(Val other) {
    if (TimestampType != other.type()) {
      return noSuchOverload(this, "compare", other);
    }
    ZonedDateTime ts1 = t;
    ZonedDateTime ts2 = ((TimestampT) other).t;
    if (ts1.isBefore(ts2)) {
      return IntNegOne;
    }
    if (ts1.isAfter(ts2)) {
      return IntOne;
    }
    return IntZero;
  }

  /** ConvertToNative implements ref.Val.ConvertToNative. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == ZonedDateTime.class) {
      return (T) t;
    }
    if (typeDesc == Date.class) {
      return (T) new Date(toEpochMillis());
    }
    if (typeDesc == Calendar.class) {
      Calendar c = Calendar.getInstance(TimeZone.getTimeZone(ZoneIdZ.getId()));
      c.setTimeInMillis(toEpochMillis());
      return (T) c;
    }
    if (typeDesc == OffsetDateTime.class) {
      return (T) t.toOffsetDateTime();
    }
    if (typeDesc == LocalDateTime.class) {
      return (T) t.toLocalDateTime();
    }
    if (typeDesc == LocalDate.class) {
      return (T) t.toLocalDate();
    }
    if (typeDesc == LocalTime.class) {
      return (T) t.toLocalTime();
    }

    if (typeDesc == Any.class) {
      return (T) Any.pack(toPbTimestamp());
    }
    if (typeDesc == Timestamp.class || typeDesc == Object.class) {
      return (T) toPbTimestamp();
    }

    if (typeDesc == Val.class || typeDesc == TimestampT.class) {
      return (T) this;
    }

    //    if (typeDesc == Value.class) { // jsonValueType
    //      return (T) StringValue.of(jsonFormatter().format(t));
    //    }
    //		// If the timestamp is already assignable to the desired type return it.
    //		if reflect.TypeOf(t.Time).AssignableTo(typeDesc) {
    //			return t.Time, nil
    //		}
    //		if reflect.TypeOf(t).AssignableTo(typeDesc) {
    //			return t, nil
    //		}
    //		switch typeDesc {
    //		case jsonValueType:
    //			// CEL follows the proto3 to JSON conversion which formats as an RFC 3339 encoded JSON
    //			// string.
    //			v := t.ConvertToType(StringType)
    //			if IsError(v) {
    //				return nil, v.(*Err)
    //			}
    //			return structpb.NewStringValue(string(v.(String))), nil
    //		case timestampValueType:
    //			// Unwrap the underlying tpb.Timestamp.
    //			return tpb.New(t.Time), nil
    //		}
    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", TimestampType, typeDesc.getName()));
  }

  private long toEpochMillis() {
    return TimeUnit.SECONDS.toMillis(t.toEpochSecond())
        + TimeUnit.NANOSECONDS.toMillis(t.getNano());
  }

  private Timestamp toPbTimestamp() {
    return Timestamp.newBuilder().setSeconds(t.toEpochSecond()).setNanos(t.getNano()).build();
  }

  /** ConvertToType implements ref.Val.ConvertToType. */
  @Override
  public Val convertToType(Type typeValue) {
    if (typeValue == StringType) {
      // TODO verify the pattern is the same as Go's time.RFC3339Nano
      DateTimeFormatter df = rfc3339nanoFormatter();
      return stringOf(df.format(t));
    }
    if (typeValue == IntType) {
      return intOf(t.toEpochSecond());
    }
    if (typeValue == TimestampType) {
      return this;
    }
    if (typeValue == TypeType) {
      return TimestampType;
    }
    return newTypeConversionError(TimestampType, typeValue);
  }

  private static final DateTimeFormatterBuilder jsonFormatterBuilder =
      new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

  private static final DateTimeFormatterBuilder rfc3339nanoFormatterBuilder =
      new DateTimeFormatterBuilder()
          .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
          .parseLenient()
          .optionalStart()
          .appendPattern(".nnnnnnnnn")
          .optionalEnd()
          .appendPattern("XXX");

  static DateTimeFormatter jsonFormatter() {
    return jsonFormatterBuilder.toFormatter();
  }

  static DateTimeFormatter rfc3339nanoFormatter() {
    return rfc3339nanoFormatterBuilder.toFormatter();
  }

  /** Equal implements ref.Val.Equal. */
  @Override
  public Val equal(Val other) {
    if (TimestampType != other.type()) {
      return noSuchOverload(this, "equal", other);
    }
    return boolOf(t.equals(((TimestampT) other).t));
  }

  /** Receive implements traits.Reciever.Receive. */
  @Override
  public Val receive(String function, String overload, Val... args) {
    switch (args.length) {
      case 0:
        Function<ZonedDateTime, Val> f0 = timestampZeroArgOverloads.get(function);
        if (f0 != null) {
          return f0.apply(t);
        }
        break;
      case 1:
        BiFunction<ZonedDateTime, Val, Val> f1 = timestampOneArgOverloads.get(function);
        if (f1 != null) {
          return f1.apply(t, args[0]);
        }
        break;
    }
    return noSuchOverload(this, function, overload, args);
  }

  /** Subtract implements traits.Subtractor.Subtract. */
  @Override
  public Val subtract(Val other) {
    if (other.type() == DurationType) {
      Duration d = (Duration) other.value();
      try {
        return timestampOf(Overflow.subtractTimeDurationChecked(t, d));
      } catch (OverflowException e) {
        return errTimestampOverflow;
      }
    }
    if (other.type() == TimestampType) {
      ZonedDateTime o = (ZonedDateTime) other.value();
      try {
        return durationOf(Overflow.subtractTimeChecked(t, o));
      } catch (OverflowException e) {
        return errDurationOverflow;
      }
    }
    return noSuchOverload(this, "subtract", other);
  }

  /** Type implements ref.Val.Type. */
  @Override
  public Type type() {
    return TimestampType;
  }

  /** Value implements ref.Val.Value. */
  @Override
  public Object value() {
    return t;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TimestampT that = (TimestampT) o;
    return Objects.equals(t, that.t);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), t);
  }

  static Val timestampGetFullYear(ZonedDateTime t) {
    return intOf(t.get(ChronoField.YEAR));
  }

  static Val timestampGetMonth(ZonedDateTime t) {
    // CEL spec indicates that the month should be 0-based, but the Time value
    // for Month() is 1-based. */
    return intOf(t.get(ChronoField.MONTH_OF_YEAR) - 1);
  }

  static Val timestampGetDayOfYear(ZonedDateTime t) {
    return intOf(t.get(ChronoField.DAY_OF_YEAR) - 1);
  }

  static Val timestampGetDayOfMonthZeroBased(ZonedDateTime t) {
    return intOf(t.get(ChronoField.DAY_OF_MONTH) - 1);
  }

  static Val timestampGetDayOfMonthOneBased(ZonedDateTime t) {
    return intOf(t.get(ChronoField.DAY_OF_MONTH));
  }

  static Val timestampGetDayOfWeek(ZonedDateTime t) {
    return intOf(t.get(ChronoField.DAY_OF_WEEK));
  }

  static Val timestampGetHours(ZonedDateTime t) {
    return intOf(t.get(ChronoField.HOUR_OF_DAY));
  }

  static Val timestampGetMinutes(ZonedDateTime t) {
    return intOf(t.get(ChronoField.MINUTE_OF_HOUR));
  }

  static Val timestampGetSeconds(ZonedDateTime t) {
    return intOf(t.get(ChronoField.SECOND_OF_MINUTE));
  }

  static Val timestampGetMilliseconds(ZonedDateTime t) {
    return intOf(t.get(ChronoField.MILLI_OF_SECOND));
  }

  static Val timestampGetFullYearWithTz(ZonedDateTime t, Val tz) {
    return timeZone(tz, TimestampT::timestampGetFullYear, t);
  }

  static Val timestampGetMonthWithTz(ZonedDateTime t, Val tz) {
    return timeZone(tz, TimestampT::timestampGetMonth, t);
  }

  static Val timestampGetDayOfYearWithTz(ZonedDateTime t, Val tz) {
    return timeZone(tz, TimestampT::timestampGetDayOfYear, t);
  }

  static Val timestampGetDayOfMonthZeroBasedWithTz(ZonedDateTime t, Val tz) {
    return timeZone(tz, TimestampT::timestampGetDayOfMonthZeroBased, t);
  }

  static Val timestampGetDayOfMonthOneBasedWithTz(ZonedDateTime t, Val tz) {
    return timeZone(tz, TimestampT::timestampGetDayOfMonthOneBased, t);
  }

  static Val timestampGetDayOfWeekWithTz(ZonedDateTime t, Val tz) {
    return timeZone(tz, TimestampT::timestampGetDayOfWeek, t);
  }

  static Val timestampGetHoursWithTz(ZonedDateTime t, Val tz) {
    return timeZone(tz, TimestampT::timestampGetHours, t);
  }

  static Val timestampGetMinutesWithTz(ZonedDateTime t, Val tz) {
    return timeZone(tz, TimestampT::timestampGetMinutes, t);
  }

  static Val timestampGetSecondsWithTz(ZonedDateTime t, Val tz) {
    return timeZone(tz, TimestampT::timestampGetSeconds, t);
  }

  static Val timestampGetMillisecondsWithTz(ZonedDateTime t, Val tz) {
    return timeZone(tz, TimestampT::timestampGetMilliseconds, t);
  }

  private static Val timeZone(Val tz, Function<ZonedDateTime, Val> funct, ZonedDateTime t) {
    if (tz.type() != StringType) {
      return noSuchOverload(TimestampType, "_op_with_timezone", tz);
    }
    String val = (String) tz.value();
    try {
      ZoneId zoneId = ZoneId.of(val);
      ZonedDateTime z = t.withZoneSameInstant(zoneId);
      return funct.apply(z);
    } catch (Exception e) {
      return Err.newErr("no conversion of '%s' to time-zone '%s': %s", t, val, e);
    }
  }
}
