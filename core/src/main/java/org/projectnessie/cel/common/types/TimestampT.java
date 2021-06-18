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

import static org.projectnessie.cel.common.types.DurationT.durationOf;
import static org.projectnessie.cel.common.types.Err.errDurationOverflow;
import static org.projectnessie.cel.common.types.Err.errTimestampOutOfRange;
import static org.projectnessie.cel.common.types.Err.errTimestampOverflow;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.IntT.IntNegOne;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.Types.boolOf;

import com.google.protobuf.Any;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.time.zone.ZoneRulesException;
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
import org.projectnessie.cel.common.types.ref.TypeEnum;
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

  /** TimestampType singleton. */
  public static final Type TimestampType =
      TypeT.newTypeValue(
          TypeEnum.Timestamp,
          Trait.AdderType,
          Trait.ComparerType,
          Trait.ReceiverType,
          Trait.SubtractorType);

  /** Number of seconds between `0001-01-01T00:00:00Z` and the Unix epoch. */
  public static final long minUnixTime = -62135596800L;
  /** Number of seconds between `9999-12-31T23:59:59.999999999Z` and the Unix epoch. */
  public static final long maxUnixTime = 253402300799L;

  public static final ZoneId ZoneIdZ = ZoneId.of("Z");

  public static TimestampT timestampOf(String s) {
    // String parsing is a bit more complex here.
    // If the fraction of the second is 3 digits long, it's considered as milliseconds.
    // If the fraction of the second is 6 digits long, it's considered as microseconds.
    // If the fraction of the second is 9 digits long, it's considered as nanoseconds.
    // This is mostly to help with Java's behavior across different Java versions with String
    // representations, which can have 3 (fraction as millis), 6 (fraction as micros) or
    // 9 (fraction as nanos) digits. I.e. this implementation accepts these format patterns:
    //  yyyy-mm-ddThh:mm:ssZ
    //  yyyy-mm-ddThh:mm:ss.mmmZ
    //  yyyy-mm-ddThh:mm:ss.uuuuuuZ
    //  yyyy-mm-ddThh:mm:ss.nnnnnnnnnZ
    try {
      return timestampOf(TimestampT.rfc3339nanoFormatter().parse(s, ZonedDateTime::from));
    } catch (DateTimeParseException e) {
      try {
        if (e.getErrorIndex() < 20) {
          // Example: 2021-06-18T08:57:30Z - length=20
          // If the error index is 0..19 (including the second field), just don't try other formats.
          throw e;
        }
        return timestampOf(TimestampT.rfc3339microFormatter().parse(s, ZonedDateTime::from));
      } catch (DateTimeParseException e2) {
        return timestampOf(TimestampT.rfc3339milliFormatter().parse(s, ZonedDateTime::from));
      }
    }
  }

  public static TimestampT timestampOf(Instant t) {
    return new TimestampT(ZonedDateTime.ofInstant(t, ZoneIdZ));
  }

  public static TimestampT timestampOf(Timestamp t) {
    LocalDateTime ldt = LocalDateTime.ofEpochSecond(t.getSeconds(), t.getNanos(), ZoneOffset.UTC);
    ZonedDateTime zdt = ZonedDateTime.of(ldt, ZoneIdZ);
    return new TimestampT(zdt);
  }

  public static TimestampT timestampOf(ZonedDateTime t) {
    // Note that this function does not validate that time.Time is in our supported range.
    return new TimestampT(t);
  }

  private static final Map<String, Function<ZonedDateTime, Val>> timestampZeroArgOverloads;
  private static final Map<String, BiFunction<ZonedDateTime, Val, Val>> timestampOneArgOverloads;

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

  private final ZonedDateTime t;

  private TimestampT(ZonedDateTime t) {
    this.t = t;
  }

  public Val rangeCheck() {
    long unitTime = t.toEpochSecond();
    if (unitTime < minUnixTime || unitTime > maxUnixTime) {
      return errTimestampOutOfRange;
    }
    return this;
  }

  /** Add implements traits.Adder.Add. */
  @Override
  public Val add(Val other) {
    if (other.type().typeEnum() == TypeEnum.Duration) {
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
    if (typeDesc == Instant.class) {
      return (T) t.toInstant();
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
    if (typeDesc == Value.class) {
      // CEL follows the proto3 to JSON conversion which formats as an RFC 3339 encoded JSON string.
      return (T) StringValue.of(jsonFormatter().format(t));
    }

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
    switch (typeValue.typeEnum()) {
      case String:
        DateTimeFormatter df = (t.getNano() > 0L) ? rfc3339nanoFormatter() : rfc3339formatter();
        return stringOf(df.format(t));
      case Int:
        return intOf(t.toEpochSecond());
      case Timestamp:
        return this;
      case Type:
        return TimestampType;
    }
    return newTypeConversionError(TimestampType, typeValue);
  }

  private static final DateTimeFormatterBuilder jsonFormatterBuilder =
      new DateTimeFormatterBuilder()
          .appendValue(ChronoField.YEAR, 4, 5, SignStyle.EXCEEDS_PAD)
          .appendLiteral('-')
          .appendValue(ChronoField.MONTH_OF_YEAR, 2)
          .appendLiteral('-')
          .appendValue(ChronoField.DAY_OF_MONTH, 2)
          .appendLiteral('T')
          .appendValue(ChronoField.HOUR_OF_DAY, 2)
          .appendLiteral(':')
          .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
          .appendLiteral(':')
          .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
          .appendLiteral('Z');

  private static final DateTimeFormatterBuilder rfc3339formatterBuilder =
      new DateTimeFormatterBuilder()
          .parseLenient()
          .appendValue(ChronoField.YEAR, 4, 5, SignStyle.EXCEEDS_PAD)
          .parseStrict()
          .appendLiteral('-')
          .appendValue(ChronoField.MONTH_OF_YEAR, 2)
          .appendLiteral('-')
          .appendValue(ChronoField.DAY_OF_MONTH, 2)
          .appendLiteral('T')
          .appendValue(ChronoField.HOUR_OF_DAY, 2)
          .appendLiteral(':')
          .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
          .appendLiteral(':')
          .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
          .appendOffset("+HH:MM", "Z");

  private static final DateTimeFormatterBuilder rfc3339nanoFormatterBuilder =
      new DateTimeFormatterBuilder()
          .parseLenient()
          .appendValue(ChronoField.YEAR, 4, 5, SignStyle.EXCEEDS_PAD)
          .parseStrict()
          .appendLiteral('-')
          .appendValue(ChronoField.MONTH_OF_YEAR, 2)
          .appendLiteral('-')
          .appendValue(ChronoField.DAY_OF_MONTH, 2)
          .appendLiteral('T')
          .appendValue(ChronoField.HOUR_OF_DAY, 2)
          .appendLiteral(':')
          .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
          .appendLiteral(':')
          .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
          .parseStrict()
          .appendLiteral('.')
          .appendValue(ChronoField.NANO_OF_SECOND, 9, 9, SignStyle.NOT_NEGATIVE)
          .appendOffset("+HH:MM", "Z");

  private static final DateTimeFormatterBuilder rfc3339microFormatterBuilder =
      new DateTimeFormatterBuilder()
          .parseLenient()
          .appendValue(ChronoField.YEAR, 4, 5, SignStyle.EXCEEDS_PAD)
          .parseStrict()
          .appendLiteral('-')
          .appendValue(ChronoField.MONTH_OF_YEAR, 2)
          .appendLiteral('-')
          .appendValue(ChronoField.DAY_OF_MONTH, 2)
          .appendLiteral('T')
          .appendValue(ChronoField.HOUR_OF_DAY, 2)
          .appendLiteral(':')
          .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
          .appendLiteral(':')
          .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
          .parseStrict()
          .optionalStart()
          .appendLiteral('.')
          .appendValue(ChronoField.MICRO_OF_SECOND, 6, 6, SignStyle.NOT_NEGATIVE)
          .optionalEnd()
          .appendOffset("+HH:MM", "Z");

  private static final DateTimeFormatterBuilder rfc3339milliFormatterBuilder =
      new DateTimeFormatterBuilder()
          .parseLenient()
          .appendValue(ChronoField.YEAR, 4, 5, SignStyle.EXCEEDS_PAD)
          .parseStrict()
          .appendLiteral('-')
          .appendValue(ChronoField.MONTH_OF_YEAR, 2)
          .appendLiteral('-')
          .appendValue(ChronoField.DAY_OF_MONTH, 2)
          .appendLiteral('T')
          .appendValue(ChronoField.HOUR_OF_DAY, 2)
          .appendLiteral(':')
          .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
          .appendLiteral(':')
          .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
          .parseStrict()
          .optionalStart()
          .appendLiteral('.')
          .appendValue(ChronoField.MILLI_OF_SECOND, 3, 3, SignStyle.NOT_NEGATIVE)
          .optionalEnd()
          .appendOffset("+HH:MM", "Z");

  static DateTimeFormatter jsonFormatter() {
    return jsonFormatterBuilder.toFormatter();
  }

  static DateTimeFormatter rfc3339nanoFormatter() {
    return rfc3339nanoFormatterBuilder.toFormatter();
  }

  static DateTimeFormatter rfc3339microFormatter() {
    return rfc3339microFormatterBuilder.toFormatter();
  }

  static DateTimeFormatter rfc3339milliFormatter() {
    return rfc3339milliFormatterBuilder.toFormatter();
  }

  static DateTimeFormatter rfc3339formatter() {
    return rfc3339formatterBuilder.toFormatter();
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
    switch (other.type().typeEnum()) {
      case Duration:
        Duration d = (Duration) other.value();
        try {
          return timestampOf(Overflow.subtractTimeDurationChecked(t, d));
        } catch (OverflowException e) {
          return errTimestampOverflow;
        }
      case Timestamp:
        ZonedDateTime o = (ZonedDateTime) other.value();
        try {
          return durationOf(Overflow.subtractTimeChecked(t, o)).rangeCheck();
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
    return intOf(t.getYear());
  }

  static Val timestampGetMonth(ZonedDateTime t) {
    // CEL spec indicates that the month should be 0-based, but the Time value
    // for Month() is 1-based. */
    return intOf(t.getMonthValue() - 1);
  }

  static Val timestampGetDayOfYear(ZonedDateTime t) {
    return intOf(t.getDayOfYear() - 1);
  }

  static Val timestampGetDayOfMonthZeroBased(ZonedDateTime t) {
    return intOf(t.getDayOfMonth() - 1);
  }

  static Val timestampGetDayOfMonthOneBased(ZonedDateTime t) {
    return intOf(t.getDayOfMonth());
  }

  static Val timestampGetDayOfWeek(ZonedDateTime t) {
    return intOf(t.getDayOfWeek().getValue());
  }

  static Val timestampGetHours(ZonedDateTime t) {
    return intOf(t.getHour());
  }

  static Val timestampGetMinutes(ZonedDateTime t) {
    return intOf(t.getMinute());
  }

  static Val timestampGetSeconds(ZonedDateTime t) {
    return intOf(t.getSecond());
  }

  static Val timestampGetMilliseconds(ZonedDateTime t) {
    return intOf(TimeUnit.NANOSECONDS.toMillis(t.getNano()));
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
    if (tz.type().typeEnum() != TypeEnum.String) {
      return noSuchOverload(TimestampType, "_op_with_timezone", tz);
    }
    String val = (String) tz.value();
    try {
      ZoneId zoneId = parseTz(val);
      ZonedDateTime z = t.withZoneSameInstant(zoneId);
      return funct.apply(z);
    } catch (Exception e) {
      return newErr(e, "no conversion of '%s' to time-zone '%s': %s", t, val, e);
    }
  }

  static ZoneId parseTz(String tz) {
    if (tz.isEmpty()) {
      throw new DateTimeException("time-zone must not be empty");
    }

    char first = tz.charAt(0);
    if (first == '-' || first == '+' || (first >= '0' && first <= '9')) {
      boolean negate = false;

      int[] i = new int[] {0};

      if (first == '-') {
        negate = true;
        i[0]++;
      } else if (first == '+') {
        i[0]++;
      }

      int hours = parseNumber(tz, i, false);
      int minutes = parseNumber(tz, i, true);
      int seconds = parseNumber(tz, i, true);

      if (hours > 18 || minutes > 59 || seconds > 59) {
        throw new DateTimeException(
            String.format("invalid hour/minute/second value in time zone: '%s'", tz));
      }

      int totalSeconds = hours * 60 * 60 + minutes * 60 + seconds;
      if (negate) {
        totalSeconds = -totalSeconds;
      }

      return ZoneId.ofOffset("UTC", ZoneOffset.ofTotalSeconds(totalSeconds));
    }

    try {
      return ZoneId.of(tz);
    } catch (ZoneRulesException e) {
      // Some time-zones (CST, AST, etc) are not known to ZoneId, so fallback to TimeZone here.
      return TimeZone.getTimeZone(tz).toZoneId();
    }
  }

  private static int parseNumber(String tz, int[] i, boolean skipColon) {
    if (skipColon) {
      if (i[0] < tz.length()) {
        char c = tz.charAt(i[0]);
        if (c == ':') {
          i[0]++;
        }
      }
    }

    if (i[0] < tz.length()) {
      char c = tz.charAt(i[0]);
      if (c >= '0' && c <= '9') {
        int dig1 = c - '0';
        i[0]++;

        if (i[0] < tz.length()) {
          c = tz.charAt(i[0]);
          if (c >= '0' && c <= '9') {
            i[0]++;
            int dig2 = c - '0';
            return dig1 * 10 + dig2;
          } else if (c != ':') {
            throw new DateTimeException(
                String.format("unexpected character '%s' at index %d", c, i[0]));
          }
        }

        return dig1;
      } else {
        throw new DateTimeException(
            String.format("unexpected character '%s' at index %d", c, i[0]));
      }
    }

    return 0;
  }
}
