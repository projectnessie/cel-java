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

import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.divideByZero;
import static org.projectnessie.cel.common.types.Err.errIntOverflow;
import static org.projectnessie.cel.common.types.Err.errTimestampOverflow;
import static org.projectnessie.cel.common.types.Err.modulusByZero;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Err.rangeError;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.ZoneIdZ;
import static org.projectnessie.cel.common.types.TimestampT.maxUnixTime;
import static org.projectnessie.cel.common.types.TimestampT.minUnixTime;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import com.google.protobuf.Any;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.Value;
import java.time.Instant;
import java.util.Objects;
import org.projectnessie.cel.common.types.Overflow.OverflowException;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Adder;
import org.projectnessie.cel.common.types.traits.Comparer;
import org.projectnessie.cel.common.types.traits.Divider;
import org.projectnessie.cel.common.types.traits.Modder;
import org.projectnessie.cel.common.types.traits.Multiplier;
import org.projectnessie.cel.common.types.traits.Negater;
import org.projectnessie.cel.common.types.traits.Subtractor;
import org.projectnessie.cel.common.types.traits.Trait;

/** Int type that implements ref.Val as well as comparison and math operators. */
public final class IntT extends BaseVal
    implements Adder, Comparer, Divider, Modder, Multiplier, Negater, Subtractor {

  /** IntType singleton. */
  public static final Type IntType =
      TypeT.newTypeValue(
          TypeEnum.Int,
          Trait.AdderType,
          Trait.ComparerType,
          Trait.DividerType,
          Trait.ModderType,
          Trait.MultiplierType,
          Trait.NegatorType,
          Trait.SubtractorType);

  /** Int constants used for comparison results. IntZero is the zero-value for Int */
  public static final IntT IntZero = new IntT(0);

  public static final IntT IntOne = new IntT(1);
  public static final IntT IntNegOne = new IntT(-1);
  /** maxIntJSON is defined as the Number.MAX_SAFE_INTEGER value per EcmaScript 6. */
  public static final long maxIntJSON = (1L << 53) - 1;
  /** minIntJSON is defined as the Number.MIN_SAFE_INTEGER value per EcmaScript 6. */
  public static final long minIntJSON = -maxIntJSON;

  private final long i;

  private IntT(long i) {
    this.i = i;
  }

  public static IntT intOfCompare(int compareToResult) {
    if (compareToResult < 0) {
      return IntNegOne;
    } else if (compareToResult > 0) {
      return IntOne;
    } else {
      return IntZero;
    }
  }

  public static IntT intOf(long i) {
    if (i == 0L) {
      return IntZero;
    }
    if (i == 1L) {
      return IntOne;
    }
    if (i == -1L) {
      return IntNegOne;
    }
    return new IntT(i);
  }

  @Override
  public long intValue() {
    return i;
  }

  /** Add implements traits.Adder.Add. */
  @Override
  public Val add(Val other) {
    if (!(other instanceof IntT)) {
      return noSuchOverload(this, "add", other);
    }
    try {
      return IntT.intOf(Overflow.addInt64Checked(i, ((IntT) other).i));
    } catch (OverflowException e) {
      return errIntOverflow;
    }
  }

  /** Compare implements traits.Comparer.Compare. */
  @Override
  public Val compare(Val other) {
    if (!(other instanceof IntT)) {
      return noSuchOverload(this, "compare", other);
    }
    return IntT.intOf(Long.compare(i, ((IntT) other).i));
  }

  /** ConvertToNative implements ref.Val.ConvertToNative. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == Long.class || typeDesc == long.class || typeDesc == Object.class) {
      return (T) Long.valueOf(i);
    }
    if (typeDesc == Integer.class || typeDesc == int.class) {
      // TODO overflow check
      return (T) Integer.valueOf((int) i);
    }
    if (typeDesc == Any.class) {
      return (T) Any.pack(Int64Value.of(i));
    }
    if (typeDesc == Int64Value.class) {
      return (T) Int64Value.of(i);
    }
    if (typeDesc == Int32Value.class) {
      return (T) Int32Value.of((int) i);
    }
    if (typeDesc == Val.class || typeDesc == IntT.class) {
      return (T) this;
    }
    if (typeDesc == Value.class) {
      // The proto-to-JSON conversion rules would convert all 64-bit integer values to JSON
      // decimal strings. Because CEL ints might come from the automatic widening of 32-bit
      // values in protos, the JSON type is chosen dynamically based on the value.
      //
      // - Integers -2^53-1 < n < 2^53-1 are encoded as JSON numbers.
      // - Integers outside this range are encoded as JSON strings.
      //
      // The integer to float range represents the largest interval where such a conversion
      // can round-trip accurately. Thus, conversions from a 32-bit source can expect a JSON
      // number as with protobuf. Those consuming JSON from a 64-bit source must be able to
      // handle either a JSON number or a JSON decimal string. To handle these cases safely
      // the string values must be explicitly converted to int() within a CEL expression;
      // however, it is best to simply stay within the JSON number range when building JSON
      // objects in CEL.
      if (i >= minIntJSON && i <= maxIntJSON) {
        return (T) Value.newBuilder().setNumberValue(i).build();
      }
      // Proto3 to JSON conversion requires string-formatted int64 values
      // since the conversion to floating point would result in truncation.
      return (T) Value.newBuilder().setStringValue(Long.toString(i)).build();
    }
    if (typeDesc == Enum.class) {
      return (T) (Integer) (int) i;
    }

    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", IntType, typeDesc.getName()));
  }

  /** ConvertToType implements ref.Val.ConvertToType. */
  @Override
  public Val convertToType(Type typeValue) {
    switch (typeValue.typeEnum()) {
      case Int:
        return this;
      case Uint:
        if (i < 0) {
          return rangeError(i, "uint");
        }
        return uintOf(i);
      case Double:
        return doubleOf(i);
      case String:
        return stringOf(Long.toString(i));
      case Timestamp:
        // The maximum positive value that can be passed to time.Unix is math.MaxInt64 minus the
        // number of seconds between year 1 and year 1970. See comments on unixToInternal.
        if (i < minUnixTime || i > maxUnixTime) {
          return errTimestampOverflow;
        }
        return timestampOf(Instant.ofEpochSecond(i).atZone(ZoneIdZ));
      case Type:
        return IntType;
    }
    return newTypeConversionError(IntType, typeValue);
  }

  /** Divide implements traits.Divider.Divide. */
  @Override
  public Val divide(Val other) {
    if (!(other instanceof IntT)) {
      return noSuchOverload(this, "divide", other);
    }
    long otherInt = ((IntT) other).i;
    if (otherInt == 0L) {
      return divideByZero();
    }
    try {
      return IntT.intOf(Overflow.divideInt64Checked(i, ((IntT) other).i));
    } catch (OverflowException e) {
      return errIntOverflow;
    }
  }

  /** Equal implements ref.Val.Equal. */
  @Override
  public Val equal(Val other) {
    if (!(other instanceof IntT)) {
      return noSuchOverload(this, "equal", other);
    }
    return boolOf(i == ((IntT) other).i);
  }

  /** Modulo implements traits.Modder.Modulo. */
  @Override
  public Val modulo(Val other) {
    if (!(other instanceof IntT)) {
      return noSuchOverload(this, "modulo", other);
    }
    long otherInt = ((IntT) other).i;
    if (otherInt == 0L) {
      return modulusByZero();
    }
    try {
      return IntT.intOf(Overflow.moduloInt64Checked(i, ((IntT) other).i));
    } catch (OverflowException e) {
      return errIntOverflow;
    }
  }

  /** Multiply implements traits.Multiplier.Multiply. */
  @Override
  public Val multiply(Val other) {
    if (!(other instanceof IntT)) {
      return noSuchOverload(this, "multiply", other);
    }
    try {
      return IntT.intOf(Overflow.multiplyInt64Checked(i, ((IntT) other).i));
    } catch (OverflowException e) {
      return errIntOverflow;
    }
  }

  /** Negate implements traits.Negater.Negate. */
  @Override
  public Val negate() {
    try {
      return IntT.intOf(Overflow.negateInt64Checked(i));
    } catch (OverflowException e) {
      return errIntOverflow;
    }
  }

  /** Subtract implements traits.Subtractor.Subtract. */
  @Override
  public Val subtract(Val other) {
    if (!(other instanceof IntT)) {
      return noSuchOverload(this, "subtract", other);
    }
    try {
      return IntT.intOf(Overflow.subtractInt64Checked(i, ((IntT) other).i));
    } catch (OverflowException e) {
      return errIntOverflow;
    }
  }

  /** Type implements ref.Val.Type. */
  @Override
  public Type type() {
    return IntType;
  }

  /** Value implements ref.Val.Value. */
  @Override
  public Object value() {
    return i;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IntT intT = (IntT) o;
    return i == intT.i;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), i);
  }

  /**
   * isJSONSafe indicates whether the int is safely representable as a floating point value in JSON.
   */
  public boolean isJSONSafe() {
    return i >= minIntJSON && i <= maxIntJSON;
  }
}
