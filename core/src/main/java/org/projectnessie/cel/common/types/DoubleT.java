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

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Err.rangeError;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.IntT.intOfCompare;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import com.google.protobuf.Any;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Value;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Adder;
import org.projectnessie.cel.common.types.traits.Comparer;
import org.projectnessie.cel.common.types.traits.Divider;
import org.projectnessie.cel.common.types.traits.Multiplier;
import org.projectnessie.cel.common.types.traits.Negater;
import org.projectnessie.cel.common.types.traits.Subtractor;
import org.projectnessie.cel.common.types.traits.Trait;

/** Double type that implements ref.Val, comparison, and mathematical operations. */
public final class DoubleT extends BaseVal
    implements Adder, Comparer, Divider, Multiplier, Negater, Subtractor {
  /** DoubleType singleton. */
  public static final Type DoubleType =
      TypeT.newTypeValue(
          TypeEnum.Double,
          Trait.AdderType,
          Trait.ComparerType,
          Trait.DividerType,
          Trait.MultiplierType,
          Trait.NegatorType,
          Trait.SubtractorType);

  public static DoubleT doubleOf(double d) {
    return new DoubleT(d);
  }

  private final double d;

  private DoubleT(double d) {
    this.d = d;
  }

  @Override
  public double doubleValue() {
    return d;
  }

  /** Add implements traits.Adder.Add. */
  @Override
  public Val add(Val other) {
    if (!(other instanceof DoubleT)) {
      return noSuchOverload(this, "add", other);
    }
    return doubleOf(d + ((DoubleT) other).d);
  }

  /** ConvertToNative implements ref.Val.ConvertToNative. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == Double.class || typeDesc == double.class || typeDesc == Object.class) {
      return (T) Double.valueOf(d);
    }
    if (typeDesc == Float.class || typeDesc == float.class) {
      // TODO needs overflow check
      return (T) Float.valueOf((float) d);
    }
    if (typeDesc == Any.class) {
      return (T) Any.pack(DoubleValue.of(d));
    }
    if (typeDesc == DoubleValue.class) {
      return (T) DoubleValue.of(d);
    }
    if (typeDesc == FloatValue.class) {
      // TODO needs overflow check
      return (T) FloatValue.of((float) d);
    }
    if (typeDesc == Val.class || typeDesc == DoubleT.class) {
      return (T) this;
    }
    if (typeDesc == Value.class) {
      return (T) Value.newBuilder().setNumberValue(d).build();
    }
    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", DoubleType, typeDesc.getName()));
  }

  private static final BigInteger MAX_UINT64 =
      BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

  /** ConvertToType implements ref.Val.ConvertToType. */
  @Override
  public Val convertToType(Type typeValue) {
    // NOTE: the original Go test assert on `intOf(-5)`, because Go's implementation uses
    // the Go `math.Round(float64)` function. The implementation of Go's `math.Round(float64)`
    // behaves differently to Java's `Math.round(double)` (or `Math.rint()`).
    // Further, the CEL-spec conformance tests assert on a different behavior and therefore those
    // conformance-tests fail against the Go implementation.
    // Even more complicated: the CEL-spec says: "CEL provides no way to control the finer points
    // of floating-point arithmetic, such as expression evaluation, rounding mode, or exception
    // handling. However, any two not-a-number values will compare equal even if their underlying
    // properties are different."
    // (see https://github.com/google/cel-spec/blob/master/doc/langdef.md#numeric-values)
    switch (typeValue.typeEnum()) {
      case Int:
        if (!Double.isFinite(d)) {
          return rangeError(d, "int");
        }
        long r = (long) d; // ?? Math.round(d);
        if (r == Long.MIN_VALUE || r == Long.MAX_VALUE) {
          return rangeError(d, "int");
        }
        return intOf(r);
      case Uint:
        if (!Double.isFinite(d)) {
          return rangeError(d, "uint");
        }
        // hack to support uint64
        BigDecimal dec = new BigDecimal(d);
        BigInteger bi = dec.toBigInteger();
        if (d < 0 || bi.compareTo(MAX_UINT64) > 0) {
          return rangeError(d, "uint");
        }
        return uintOf(bi.longValue());
      case Double:
        return this;
      case String:
        return stringOf(Double.toString(d));
      case Type:
        return DoubleType;
    }
    return newTypeConversionError(DoubleType, typeValue);
  }

  /** Divide implements traits.Divider.Divide. */
  @Override
  public Val divide(Val other) {
    if (!(other instanceof DoubleT)) {
      return noSuchOverload(this, "divide", other);
    }
    return doubleOf(d / ((DoubleT) other).d);
  }

  /** Compare implements traits.Comparer.Compare. */
  @Override
  public Val compare(Val other) {
    switch (other.type().typeEnum()) {
      case Uint:
      case Int:
      case Double:
        Val converted = other.convertToType(type());
        if (converted.type().typeEnum() == TypeEnum.Err) {
          return converted;
        }
        double od = ((DoubleT) converted).d;
        if (d == od) {
          // work around for special case of -0.0d == 0.0d (IEEE 754)
          return IntZero;
        }
        return intOfCompare(Double.compare(d, od));
      default:
        return noSuchOverload(this, "compare", other);
    }
  }

  /** Equal implements ref.Val.Equal. */
  @Override
  public Val equal(Val other) {
    switch (other.type().typeEnum()) {
      case Uint:
      case Int:
      case Double:
      case String:
        Val converted = other.convertToType(type());
        if (converted.type().typeEnum() == TypeEnum.Err) {
          return converted;
        }
        double o = ((DoubleT) converted).d;
        // TODO: Handle NaNs properly.
        return boolOf(d == o);
      case Null:
      case Bytes:
      case List:
      case Map:
        return False;
      default:
        return noSuchOverload(this, "equal", other);
    }
  }

  /** Multiply implements traits.Multiplier.Multiply. */
  @Override
  public Val multiply(Val other) {
    if (!(other instanceof DoubleT)) {
      return noSuchOverload(this, "multiply", other);
    }
    return doubleOf(d * ((DoubleT) other).d);
  }

  /** Negate implements traits.Negater.Negate. */
  @Override
  public Val negate() {
    return doubleOf(-d);
  }

  /** Subtract implements traits.Subtractor.Subtract. */
  @Override
  public Val subtract(Val other) {
    if (!(other instanceof DoubleT)) {
      return noSuchOverload(this, "subtract", other);
    }
    return doubleOf(d - ((DoubleT) other).d);
  }

  /** Type implements ref.Val.Type. */
  @Override
  public Type type() {
    return DoubleType;
  }

  /** Value implements ref.Val.Value. */
  @Override
  public Object value() {
    return d;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Val)) {
      return false;
    }
    // Defer to CEL's equal functionality to allow heterogeneous numeric map keys
    return equal((Val) o).booleanValue();
  }

  @Override
  public int hashCode() {
    // Used to allow heterogeneous numeric map keys
    return (int) d;
  }
}
