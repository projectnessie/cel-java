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
package org.projectnessie.cel.common;

import java.math.BigInteger;

/**
 * Java representation of the complete unsigned 64-bit range used by CEL {@code uint}.
 *
 * <p>The wrapped {@code long} stores the unsigned bit pattern. {@link #longValue()} therefore
 * returns that bit pattern and may be negative for values greater than {@link Long#MAX_VALUE};
 * {@link #toString()}, comparison, and floating-point conversions use unsigned magnitude.
 */
public final class ULong extends Number implements Comparable<ULong> {
  private final long ulong;

  private ULong(long ulong) {
    this.ulong = ulong;
  }

  /** Returns an unsigned value backed by the supplied 64-bit pattern. */
  public static ULong valueOf(long ulong) {
    return new ULong(ulong);
  }

  @Override
  public int compareTo(ULong o) {
    return Long.compareUnsigned(ulong, o.ulong);
  }

  @Override
  public int hashCode() {
    return (int) ulong;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ULong)) {
      return false;
    }
    return ((ULong) obj).ulong == ulong;
  }

  @Override
  public String toString() {
    return Long.toUnsignedString(ulong);
  }

  @Override
  public int intValue() {
    return (int) ulong;
  }

  @Override
  public long longValue() {
    return ulong;
  }

  @Override
  public float floatValue() {
    return new BigInteger(toString()).floatValue();
  }

  @Override
  public double doubleValue() {
    return new BigInteger(toString()).doubleValue();
  }
}
