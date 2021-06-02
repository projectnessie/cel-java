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
import static org.projectnessie.cel.common.types.DoubleT.DoubleType;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.errUintOverflow;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.valOrErr;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;

import org.projectnessie.cel.common.types.Overflow.OverflowException;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
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
  public static final TypeValue UintType =
      TypeValue.newTypeValue(
          "uint",
          Trait.AdderType,
          Trait.ComparerType,
          Trait.DividerType,
          Trait.ModderType,
          Trait.MultiplierType,
          Trait.SubtractorType);

  /** Uint constants */
  public static final UintT UintZero = new UintT(0);

  private final long i;

  private UintT(long i) {
    this.i = i;
  }

  public static UintT uintOf(long i) {
    if (i == 0L) {
      return UintZero;
    }
    return new UintT(i);
  }

  @Override
  public long intValue() {
    return i;
  }

  /** Add implements traits.Adder.Add. */
  @Override
  public Val add(Val other) {
    if (!(other instanceof UintT)) {
      return valOrErr(other, "no such overload");
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
    if (!(other instanceof UintT)) {
      return valOrErr(other, "no such overload");
    }
    return intOf(Long.compareUnsigned(i, ((UintT) other).i));
  }

  /** ConvertToNative implements ref.Val.ConvertToNative. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == Long.class || typeDesc == long.class) {
      // TODO overflow check
      return (T) Long.valueOf(i);
    }
    if (typeDesc == Integer.class || typeDesc == int.class) {
      // TODO overflow check
      return (T) Integer.valueOf((int) i);
    }

    //		switch typeDesc.Kind() {
    //		case reflect.Uint, reflect.Uint32, reflect.Uint64:
    //			return reflect.ValueOf(i).Convert(typeDesc).Interface(), nil
    //		case reflect.Ptr:
    //			switch typeDesc {
    //			case anyValueType:
    //				// Primitives must be wrapped before being set on an Any field.
    //				return anypb.New(wrapperspb.UInt64(uint64(i)))
    //			case jsonValueType:
    //				// JSON can accurately represent 32-bit uints as floating point values.
    //				if i.isJSONSafe() {
    //					return structpb.NewNumberValue(float64(i)), nil
    //				}
    //				// Proto3 to JSON conversion requires string-formatted uint64 values
    //				// since the conversion to floating point would result in truncation.
    //				return structpb.NewStringValue(strconv.FormatUint(uint64(i), 10)), nil
    //			case uint32WrapperType:
    //				// Convert the value to a wrapperspb.UInt32Value (with truncation).
    //				return wrapperspb.UInt32(uint32(i)), nil
    //			case uint64WrapperType:
    //				// Convert the value to a wrapperspb.UInt64Value.
    //				return wrapperspb.UInt64(uint64(i)), nil
    //			}
    //			switch typeDesc.Elem().Kind() {
    //			case reflect.Uint32:
    //				v := uint32(i)
    //				p := reflect.New(typeDesc.Elem())
    //				p.Elem().Set(reflect.ValueOf(v).Convert(typeDesc.Elem()))
    //				return p.Interface(), nil
    //			case reflect.Uint64:
    //				v := uint64(i)
    //				p := reflect.New(typeDesc.Elem())
    //				p.Elem().Set(reflect.ValueOf(v).Convert(typeDesc.Elem()))
    //				return p.Interface(), nil
    //			}
    //		case reflect.Interface:
    //			iv := i.Value()
    //			if reflect.TypeOf(iv).Implements(typeDesc) {
    //				return iv, nil
    //			}
    //			if reflect.TypeOf(i).Implements(typeDesc) {
    //				return i, nil
    //			}
    //		}
    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", UintType, typeDesc.getName()));
  }

  /** ConvertToType implements ref.Val.ConvertToType. */
  @Override
  public Val convertToType(Type typeValue) {
    if (typeValue == IntType) {
      if (i < 0L) {
        return newErr("range error converting %s to int", Long.toUnsignedString(i));
      }
      return intOf(i);
    }
    if (typeValue == UintType) {
      return this;
    }
    if (typeValue == DoubleType) {
      if (i < 0L) {
        // same restriction in Java for uint-->double as uint-->int
        return newErr("range error converting %s to double", Long.toUnsignedString(i));
      }
      return doubleOf((double) i);
    }
    if (typeValue == StringType) {
      return stringOf(Long.toUnsignedString(i));
    }
    if (typeValue == TypeType) {
      return UintType;
    }
    return newErr("type conversion error from '%s' to '%s'", UintType, typeValue);
  }

  /** Divide implements traits.Divider.Divide. */
  @Override
  public Val divide(Val other) {
    if (!(other instanceof UintT)) {
      return valOrErr(other, "no such overload");
    }
    long otherInt = ((UintT) other).i;
    if (otherInt == 0L) {
      return newErr("divide by zero");
    }
    return uintOf(i / otherInt);
  }

  /** Equal implements ref.Val.Equal. */
  @Override
  public Val equal(Val other) {
    if (!(other instanceof UintT)) {
      return valOrErr(other, "no such overload");
    }
    return boolOf(i == ((UintT) other).i);
  }

  /** Modulo implements traits.Modder.Modulo. */
  @Override
  public Val modulo(Val other) {
    if (!(other instanceof UintT)) {
      return valOrErr(other, "no such overload");
    }
    long otherInt = ((UintT) other).i;
    if (otherInt == 0L) {
      return newErr("modulus by zero");
    }
    return uintOf(i % otherInt);
  }

  /** Multiply implements traits.Multiplier.Multiply. */
  @Override
  public Val multiply(Val other) {
    if (!(other instanceof UintT)) {
      return valOrErr(other, "no such overload");
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
    if (!(other instanceof UintT)) {
      return valOrErr(other, "no such overload");
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

  /**
   * isJSONSafe indicates whether the uint is safely representable as a floating point value in
   * JSON.
   */
  public boolean isJSONSafe() {
    return i >= 0 && i <= IntT.maxIntJSON;
  }
}
