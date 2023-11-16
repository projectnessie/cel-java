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
import static org.projectnessie.cel.common.types.DoubleT.DoubleType;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.divideByZero;
import static org.projectnessie.cel.common.types.Err.errUintOverflow;
import static org.projectnessie.cel.common.types.Err.modulusByZero;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Err.rangeError;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.IntT.intOfCompare;
import static org.projectnessie.cel.common.types.IntT.maxIntJSON;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.Types.boolOf;

import com.google.protobuf.Any;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import java.math.BigInteger;
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

  @Override
  public double doubleValue() {
    return (double) i;
  }

  /** ConvertToNative implements ref.Val.ConvertToNative. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == Long.class || typeDesc == long.class || typeDesc == Object.class) {
      // no "is negative" check here, because there is no Java representation of an
      // unsigned long, reusing Java's signed long
      return (T) Long.valueOf(i);
    }
    if (typeDesc == Integer.class || typeDesc == int.class) {
      if (i < Integer.MIN_VALUE || i > Integer.MAX_VALUE) {
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

  /** Compare implements traits.Comparer.Compare. */
  @Override
  public Val compare(Val other) {
    switch (other.type().typeEnum()) {
      case Int:
        if (other.type().typeEnum() == TypeEnum.Err) {
          return other;
        }
        if (other.intValue() < 0L) {
          // the other int is < 0, so any uint is greater
          return IntOne;
        }
        if (i < 0L) {
          // this uint is > Long.MAX_VALUE, so it MUST be greater than any signed int
          return IntOne;
        }
        return intOfCompare(Long.compareUnsigned(i, other.intValue()));
      case Double:
        if (other.type().typeEnum() == TypeEnum.Err) {
          return other;
        }
        if (other.doubleValue() < 0d) {
          // the other int is < 0, so any uint is greater
          return IntOne;
        }
        DoubleT cmp = (DoubleT) convertToType(DoubleType);
        return cmp.compare(other);
      case Uint:
        Val converted = other.convertToType(type());
        if (converted.type().typeEnum() == TypeEnum.Err) {
          return converted;
        }
        return intOfCompare(Long.compareUnsigned(i, ((UintT) converted).i));
      default:
        return noSuchOverload(this, "compare", other);
    }
  }

  /** Equal implements ref.Val.Equal. */
  @Override
  public Val equal(Val other) {
    switch (other.type().typeEnum()) {
      case Int:
        if (other.type().typeEnum() == TypeEnum.Err) {
          return other;
        }
        if (other.intValue() < 0L) {
          // the other int is < 0, so no uint can be equal
          return False;
        }
        if (i < 0L) {
          // this uint is > Long.MAX_VALUE, so it CANNOT be equal
          return False;
        }
        return boolOf(i == other.intValue());
      case Double:
        return other.equal(this);
      case Uint:
      case String:
        Val converted = other.convertToType(type());
        if (converted.type().typeEnum() == TypeEnum.Err) {
          return converted;
        }
        return boolOf(i == converted.intValue());
      case Null:
      case Bytes:
      case List:
      case Map:
        return False;
      default:
        return noSuchOverload(this, "equal", other);
    }
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
    if (!(o instanceof Val)) {
      return false;
    }
    // Defer to CEL's equal functionality to allow heterogeneous numeric map keys
    return equal((Val) o).booleanValue();
  }

  @Override
  public int hashCode() {
    // Used to allow heterogeneous numeric map keys
    return (int) i;
  }

  public String toString() {
    return String.format("%s{%s}", type().typeName(), Long.toUnsignedString(i));
  }
}
