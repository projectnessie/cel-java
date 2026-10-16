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

import static org.projectnessie.cel.common.types.TimestampT.maxUnixTime;
import static org.projectnessie.cel.common.types.TimestampT.minUnixTime;

import java.math.BigInteger;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

/**
 * Checked arithmetic used by CEL numeric, duration, and timestamp value implementations.
 *
 * <p>Methods return the computed Java value when it is representable in the corresponding CEL
 * domain and throw {@link OverflowException} otherwise. Unsigned integer operands and results use
 * the raw bits of a Java {@code long}.
 *
 * <p>This is low-level value-runtime infrastructure. Application code normally observes overflow as
 * a CEL error value produced by the calling CEL operation.
 */
public final class Overflow {
  /** Stackless exception used internally to signal a CEL arithmetic overflow. */
  public static final class OverflowException extends RuntimeException {
    OverflowException() {
      super("overflow", null, false, false);
    }
  }

  /** Shared stackless overflow signal used by the checked operations in this class. */
  public static final OverflowException overflowException = new OverflowException();

  /**
   * Adds two signed CEL int values.
   *
   * @return the signed sum
   * @throws OverflowException if the sum is outside the signed 64-bit range
   */
  public static long addInt64Checked(long x, long y) {
    if ((y > 0 && x > Long.MAX_VALUE - y) || (y < 0 && x < Long.MIN_VALUE - y)) {
      throw overflowException;
    }
    return x + y;
  }

  /**
   * Subtracts two signed CEL int values.
   *
   * @return {@code x - y}
   * @throws OverflowException if the difference is outside the signed 64-bit range
   */
  public static long subtractInt64Checked(long x, long y) {
    if ((y < 0 && x > Long.MAX_VALUE + y) || (y > 0 && x < Long.MIN_VALUE + y)) {
      throw overflowException;
    }
    return x - y;
  }

  /**
   * Negates a signed CEL int value.
   *
   * @return {@code -x}
   * @throws OverflowException if {@code x} is {@link Long#MIN_VALUE}
   */
  public static long negateInt64Checked(long x) {
    // In twos complement, negating MinInt64 would result in a valid of MaxInt64+1.
    if (x == Long.MIN_VALUE) {
      throw overflowException;
    }
    return -x;
  }

  /**
   * Multiplies two signed CEL int values.
   *
   * @return {@code x * y}
   * @throws OverflowException if the product is outside the signed 64-bit range
   */
  public static long multiplyInt64Checked(long x, long y) {
    // Detecting multiplication overflow is more complicated than the others. The first two detect
    // attempting to negate MinInt64, which would result in MaxInt64+1. The other four detect normal
    // overflow conditions.
    if ((x == -1 && y == Long.MIN_VALUE)
        || (y == -1 && x == Long.MIN_VALUE)
        ||
        // x is positive, y is positive
        (x > 0 && y > 0 && x > Long.MAX_VALUE / y)
        ||
        // x is positive, y is negative
        (x > 0 && y < 0 && y < Long.MIN_VALUE / x)
        ||
        // x is negative, y is positive
        (x < 0 && y > 0 && x < Long.MIN_VALUE / y)
        ||
        // x is negative, y is negative
        (x < 0 && y < 0 && y < Long.MAX_VALUE / x)) {
      throw overflowException;
    }
    return x * y;
  }

  /**
   * Divides two signed CEL int values.
   *
   * @return {@code x / y}
   * @throws OverflowException if dividing {@link Long#MIN_VALUE} by {@code -1}
   * @throws ArithmeticException if {@code y} is zero
   */
  public static long divideInt64Checked(long x, long y) {
    // In twos complement, negating MinInt64 would result in a valid of MaxInt64+1.
    if (x == Long.MIN_VALUE && y == -1) {
      throw overflowException;
    }
    return x / y;
  }

  /**
   * Computes the remainder of two signed CEL int values.
   *
   * @return {@code x % y}
   * @throws OverflowException if dividing {@link Long#MIN_VALUE} by {@code -1}
   * @throws ArithmeticException if {@code y} is zero
   */
  public static long moduloInt64Checked(long x, long y) {
    // In twos complement, negating MinInt64 would result in a valid of MaxInt64+1.
    if (x == Long.MIN_VALUE && y == -1) {
      throw overflowException;
    }
    return x % y;
  }

  /**
   * Adds two CEL uint values represented as raw {@code long} bits.
   *
   * @return the raw bits of the unsigned sum
   * @throws OverflowException if the sum is outside the unsigned 64-bit range
   */
  public static long addUint64Checked(long x, long y) {
    // hopefully faster than using BigInteger...
    long xU = x >>> 32;
    long xL = x & 0xffffffffL;
    long yU = y >>> 32;
    long yL = y & 0xffffffffL;

    long rL = xL + yL;
    long rU = xU + yU;
    if (rL > 0xffffffffL) {
      // carry
      rU++;
    }

    if (rU > 0xffffffffL) {
      throw overflowException;
    }

    return rU << 32 | (rL & 0xffffffffL);
  }

  /**
   * Subtracts two CEL uint values represented as raw {@code long} bits.
   *
   * @return the raw bits of the unsigned difference
   * @throws OverflowException if {@code y} is greater than {@code x} as an unsigned value
   */
  public static long subtractUint64Checked(long x, long y) {
    // hopefully faster than using BigInteger...
    long xU = x >>> 32;
    long xL = x & 0xffffffffL;
    long yU = y >>> 32;
    long yL = y & 0xffffffffL;

    long rU = xU - yU;
    long rL = xL - yL;
    if (rL < 0L) {
      rU--;
    }
    if (rU < 0L) {
      throw overflowException;
    }

    return rU << 32 | (rL & 0xffffffffL);
  }

  /**
   * Multiplies two CEL uint values represented as raw {@code long} bits.
   *
   * @return the raw bits of the unsigned product
   * @throws OverflowException if the product is outside the unsigned 64-bit range
   */
  public static long multiplyUint64Checked(long x, long y) {
    // Sloooow, but works.
    BigInteger r = BigInteger.valueOf(x).multiply(BigInteger.valueOf(y));
    if (r.bitLength() > 64) {
      throw overflowException;
    }
    return r.longValue();
  }

  /**
   * Adds two durations.
   *
   * @return the sum
   * @throws OverflowException if the Java duration result overflows
   */
  public static Duration addDurationChecked(Duration x, Duration y) {
    try {
      return x.plus(y);
    } catch (ArithmeticException e) {
      throw overflowException;
    }
  }

  /**
   * Subtracts two durations.
   *
   * @return {@code x - y}
   * @throws OverflowException if the Java duration result overflows
   */
  public static Duration subtractDurationChecked(Duration x, Duration y) {
    try {
      return x.minus(y);
    } catch (ArithmeticException e) {
      throw overflowException;
    }
  }

  /**
   * Negates a duration.
   *
   * @return the negated duration
   * @throws OverflowException if the Java duration result overflows
   */
  public static Duration negateDurationChecked(Duration x) {
    try {
      return x.negated();
    } catch (ArithmeticException e) {
      throw overflowException;
    }
  }

  /**
   * Adds a duration to a timestamp and enforces the CEL timestamp range.
   *
   * @return the resulting timestamp
   * @throws OverflowException if Java arithmetic or the CEL timestamp range overflows
   */
  public static ZonedDateTime addTimeDurationChecked(ZonedDateTime x, Duration y) {
    try {
      return checkTimeOverflow(x.plus(y));
    } catch (ArithmeticException e) {
      throw overflowException;
    }
  }

  /**
   * Subtracts two timestamps.
   *
   * @return the duration from {@code y} to {@code x}
   * @throws OverflowException if the Java duration result overflows
   */
  public static Duration subtractTimeChecked(ZonedDateTime x, ZonedDateTime y) {
    try {
      Duration d = Duration.ofSeconds(x.toEpochSecond());
      d = d.plus(x.get(ChronoField.NANO_OF_SECOND), ChronoUnit.NANOS);
      d = d.minus(y.toEpochSecond(), ChronoUnit.SECONDS);
      d = d.minus(y.get(ChronoField.NANO_OF_SECOND), ChronoUnit.NANOS);
      return d;
    } catch (ArithmeticException e) {
      throw overflowException;
    }
  }

  /**
   * Subtracts a duration from a timestamp and enforces the CEL timestamp range.
   *
   * @return the resulting timestamp
   * @throws OverflowException if Java arithmetic or the CEL timestamp range overflows
   */
  public static ZonedDateTime subtractTimeDurationChecked(ZonedDateTime x, Duration y) {
    try {
      return checkTimeOverflow(x.minus(y));
    } catch (ArithmeticException e) {
      throw overflowException;
    }
  }

  /**
   * Validates a timestamp against the CEL timestamp range.
   *
   * @param x timestamp to validate
   * @return {@code x}
   * @throws OverflowException if its epoch second is outside {@link TimestampT#minUnixTime} through
   *     {@link TimestampT#maxUnixTime}
   */
  public static ZonedDateTime checkTimeOverflow(ZonedDateTime x) {
    long s = x.toEpochSecond();
    if (s < minUnixTime || s > maxUnixTime) {
      throw overflowException;
    }
    return x;
  }
}
