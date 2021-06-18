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
import static org.projectnessie.cel.common.types.Err.errUintOverflow;
import static org.projectnessie.cel.common.types.Err.modulusByZero;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Err.rangeError;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.IntT.maxIntJSON;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.Types.boolOf;

import com.google.protobuf.Any;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import java.math.BigInteger;
import java.util.Objects;
import org.projectnessie.cel.common.ULong;
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
import org.projectnessie.cel.common.types.traits.Subtractor;
import org.projectnessie.cel.common.types.traits.Trait;

/** Uint type implementation which supports comparison and math operators. */
public final class UintT extends BaseVal
    implements Adder, Comparer, Divider, Modder, Multiplier, Subtractor {

  /** UintType singleton. */
  public static final Type UintType =
      TypeT.newTypeValue(
          TypeEnum.Uint,
          Trait.AdderType,
          Trait.ComparerType,
          Trait.DividerType,
          Trait.ModderType,
          Trait.MultiplierType,
          Trait.SubtractorType);

  /** Uint constants */
  public static final UintT UintZero = new UintT(0);

  public static UintT uintOf(ULong i) {
    return uintOf(i.longValue());
  }

  public static UintT uintOf(long i) {
    if (i == 0L) {
      return UintZero;
    }
    return new UintT(i);
  }

  private final long i;

  private UintT(long i) {
    this.i = i;
  }

  @Override
  public long intValue() {
    return i;
  }

  /** Add implements traits.Adder.Add. */
  @Override
  public Val add(Val other) {
    if (other.type() != UintType) {
      return noSuchOverload(this, "add", other);
    }
    try {
      return uintOf(Overflow.addUint64Checked(i, ((UintT) other).i));
    } catch (OverflowException e) {
      return errUintOverflow;
    }
  }

  /** Compare implements traits.Comparer.Compare. */
  @Override
  public Val compare(Val other) {
    if (other.type() != UintType) {
      return noSuchOverload(this, "compare", other);
    }
    return intOf(Long.compareUnsigned(i, ((UintT) other).i));
  }

  /** ConvertToNative implements ref.Val.ConvertToNative. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == Long.class || typeDesc == long.class || typeDesc == Object.class) {
      if (i < 0) {
        Err.throwErrorAsIllegalStateException(rangeError(i, "Java long"));
      }
      return (T) Long.valueOf(i);
    }
    if (typeDesc == Integer.class || typeDesc == int.class) {
      if (i < 0 || i > Integer.MAX_VALUE) {
        Err.throwErrorAsIllegalStateException(rangeError(i, "Java int"));
      }
      return (T) Integer.valueOf((int) i);
    }
    if (typeDesc == ULong.class) {
      return (T) ULong.valueOf(i);
    }
    if (typeDesc == Any.class) {
      return (T) Any.pack(UInt64Value.of(i));
    }
    if (typeDesc == UInt64Value.class) {
      return (T) UInt64Value.of(i);
    }
    if (typeDesc == UInt32Value.class) {
      return (T) UInt32Value.of((int) i);
    }
    if (typeDesc == Val.class || typeDesc == UintT.class) {
      return (T) this;
    }
    if (typeDesc == Value.class) {
      if (i <= maxIntJSON) {
        // JSON can accurately represent 32-bit uints as floating point values.
        return (T) Value.newBuilder().setNumberValue(i).build();
      } else {
        // Proto3 to JSON conversion requires string-formatted uint64 values
        // since the conversion to floating point would result in truncation.
        return (T) Value.newBuilder().setStringValue(Long.toUnsignedString(i)).build();
      }
    }

    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", UintType, typeDesc.getName()));
  }

  /** ConvertToType implements ref.Val.ConvertToType. */
  @Override
  public Val convertToType(Type typeValue) {
    switch (typeValue.typeEnum()) {
      case Int:
        if (i < 0L) {
          return rangeError(Long.toUnsignedString(i), "int");
        }
        return intOf(i);
      case Uint:
        return this;
      case Double:
        if (i < 0L) {
          return doubleOf(new BigInteger(Long.toUnsignedString(i)).doubleValue());
        }
        return doubleOf(i);
      case String:
        return stringOf(Long.toUnsignedString(i));
      case Type:
        return UintType;
    }
    return newTypeConversionError(UintType, typeValue);
  }

  /** Divide implements traits.Divider.Divide. */
  @Override
  public Val divide(Val other) {
    if (other.type() != UintType) {
      return noSuchOverload(this, "divide", other);
    }
    long otherInt = ((UintT) other).i;
    if (otherInt == 0L) {
      return divideByZero();
    }
    return uintOf(i / otherInt);
  }

  /** Equal implements ref.Val.Equal. */
  @Override
  public Val equal(Val other) {
    if (other.type() != UintType) {
      return noSuchOverload(this, "equal", other);
    }
    return boolOf(i == ((UintT) other).i);
  }

  /** Modulo implements traits.Modder.Modulo. */
  @Override
  public Val modulo(Val other) {
    if (other.type() != UintType) {
      return noSuchOverload(this, "modulo", other);
    }
    long otherInt = ((UintT) other).i;
    if (otherInt == 0L) {
      return modulusByZero();
    }
    return uintOf(i % otherInt);
  }

  /** Multiply implements traits.Multiplier.Multiply. */
  @Override
  public Val multiply(Val other) {
    if (other.type() != UintType) {
      return noSuchOverload(this, "multiply", other);
    }
    try {
      return uintOf(Overflow.multiplyUint64Checked(i, ((UintT) other).i));
    } catch (OverflowException e) {
      return errUintOverflow;
    }
  }

  /** Subtract implements traits.Subtractor.Subtract. */
  @Override
  public Val subtract(Val other) {
    if (other.type() != UintType) {
      return noSuchOverload(this, "subtract", other);
    }
    try {
      return uintOf(Overflow.subtractUint64Checked(i, ((UintT) other).i));
    } catch (OverflowException e) {
      return errUintOverflow;
    }
  }

  /** Type implements ref.Val.Type. */
  @Override
  public Type type() {
    return UintType;
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
    UintT uintT = (UintT) o;
    return i == uintT.i;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), i);
  }

  /**
   * isJSONSafe indicates whether the uint is safely representable as a floating point value in
   * JSON.
   */
  public boolean isJSONSafe() {
    return i >= 0 && i <= IntT.maxIntJSON;
  }
}
