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

public final class Overflow {
  public static class OverflowException extends RuntimeException {
    OverflowException() {
      super("overflow", null, false, false);
    }
  }

  public static final OverflowException overflowException = new OverflowException();

  /**
   * addInt64Checked performs addition with overflow detection of two int64, returning the result of
   * the addition if no overflow occurred as the first return value and a bool indicating whether no
   * overflow occurred as the second return value.
   */
  public static long addInt64Checked(long x, long y) {
    if ((y > 0 && x > Long.MAX_VALUE - y) || (y < 0 && x < Long.MIN_VALUE - y)) {
      throw overflowException;
    }
    return x + y;
  }

  /**
   * subtractInt64Checked performs subtraction with overflow detection of two int64, returning the
   * result of the subtraction if no overflow occurred as the first return value and a bool
   * indicating whether no overflow occurred as the second return value.
   */
  public static long subtractInt64Checked(long x, long y) {
    if ((y < 0 && x > Long.MAX_VALUE + y) || (y > 0 && x < Long.MIN_VALUE + y)) {
      throw overflowException;
    }
    return x - y;
  }

  /**
   * negateInt64Checked performs negation with overflow detection of an int64, returning the result
   * of the negation if no overflow occurred as the first return value and a bool indicating whether
   * no overflow occurred as the second return value.
   */
  public static long negateInt64Checked(long x) {
    // In twos complement, negating MinInt64 would result in a valid of MaxInt64+1.
    if (x == Long.MIN_VALUE) {
      throw overflowException;
    }
    return -x;
  }

  /**
   * multiplyInt64Checked performs multiplication with overflow detection of two int64, returning
   * the result of the multiplication if no overflow occurred as the first return value and a bool
   * indicating whether no overflow occurred as the second return value.
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
   * divideInt64Checked performs division with overflow detection of two int64, returning the result
   * of the division if no overflow occurred as the first return value and a bool indicating whether
   * no overflow occurred as the second return value.
   */
  public static long divideInt64Checked(long x, long y) {
    // In twos complement, negating MinInt64 would result in a valid of MaxInt64+1.
    if (x == Long.MIN_VALUE && y == -1) {
      throw overflowException;
    }
    return x / y;
  }

  /**
   * moduloInt64Checked performs modulo with overflow detection of two int64, returning the result
   * of the modulo if no overflow occurred as the first return value and a bool indicating whether
   * no overflow occurred as the second return value.
   */
  public static long moduloInt64Checked(long x, long y) {
    // In twos complement, negating MinInt64 would result in a valid of MaxInt64+1.
    if (x == Long.MIN_VALUE && y == -1) {
      throw overflowException;
    }
    return x % y;
  }

  /**
   * addUint64Checked performs addition with overflow detection of two uint64, returning the result
   * of the addition if no overflow occurred as the first return value and a bool indicating whether
   * no overflow occurred as the second return value.
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
   * subtractUint64Checked performs subtraction with overflow detection of two uint64, returning the
   * result of the subtraction if no overflow occurred as the first return value and a bool
   * indicating whether no overflow occurred as the second return value.
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
   * multiplyUint64Checked performs multiplication with overflow detection of two uint64, returning
   * the result of the multiplication if no overflow occurred as the first return value and a bool
   * indicating whether no overflow occurred as the second return value.
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
   * addDurationChecked performs addition with overflow detection of two time.Duration, returning
   * the result of the addition if no overflow occurred as the first return value and a bool
   * indicating whether no overflow occurred as the second return value.
   */
  public static Duration addDurationChecked(Duration x, Duration y) {
    try {
      return x.plus(y);
    } catch (ArithmeticException e) {
      throw overflowException;
    }
  }

  /**
   * subtractDurationChecked performs subtraction with overflow detection of two time.Duration,
   * returning the result of the subtraction if no overflow occurred as the first return value and a
   * bool indicating whether no overflow occurred as the second return value.
   */
  public static Duration subtractDurationChecked(Duration x, Duration y) {
    try {
      return x.minus(y);
    } catch (ArithmeticException e) {
      throw overflowException;
    }
  }

  /**
   * negateDurationChecked performs negation with overflow detection of a time.Duration, returning
   * the result of the negation if no overflow occurred as the first return value and a bool
   * indicating whether no overflow occurred as the second return value.
   */
  public static Duration negateDurationChecked(Duration x) {
    try {
      return x.negated();
    } catch (ArithmeticException e) {
      throw overflowException;
    }
  }

  /**
   * addDurationChecked performs addition with overflow detection of a time.Time and time.Duration,
   * returning the result of the addition if no overflow occurred as the first return value and a
   * bool indicating whether no overflow occurred as the second return value.
   */
  public static ZonedDateTime addTimeDurationChecked(ZonedDateTime x, Duration y) {
    try {
      return checkTimeOverflow(x.plus(y));
    } catch (ArithmeticException e) {
      throw overflowException;
    }
  }

  /**
   * subtractTimeChecked performs subtraction with overflow detection of two time.Time, returning
   * the result of the subtraction if no overflow occurred as the first return value and a bool
   * indicating whether no overflow occurred as the second return value.
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
   * subtractTimeDurationChecked performs subtraction with overflow detection of a time.Time and
   * time.Duration, returning the result of the subtraction if no overflow occurred as the first
   * return value and a bool indicating whether no overflow occurred as the second return value.
   */
  public static ZonedDateTime subtractTimeDurationChecked(ZonedDateTime x, Duration y) {
    try {
      return checkTimeOverflow(x.minus(y));
    } catch (ArithmeticException e) {
      throw overflowException;
    }
  }

  /**
   * Checks whether the given timestamp overflowed in the bounds of "Go", that is less than {@link
   * TimestampT#minUnixTime} or greater than {@link TimestampT#maxUnixTime}.
   */
  public static ZonedDateTime checkTimeOverflow(ZonedDateTime x) {
    long s = x.toEpochSecond();
    if (s < minUnixTime || s > maxUnixTime) {
      throw overflowException;
    }
    return x;
  }
}
