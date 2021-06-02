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

import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.valOrErr;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;

import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Comparer;
import org.projectnessie.cel.common.types.traits.Negater;
import org.projectnessie.cel.common.types.traits.Trait;

/** Bool type that implements ref.Val and supports comparison and negation. */
public class BoolT extends BaseVal implements Comparer, Negater {

  /** BoolType singleton. */
  public static final TypeValue BoolType =
      TypeValue.newTypeValue("bool", Trait.ComparerType, Trait.NegatorType);

  private final boolean b;

  private BoolT(boolean b) {
    this.b = b;
  }

  public static BoolT boolOf(boolean b) {
    return b ? True : False;
  }

  /** Boolean constants */
  public static final BoolT False = new BoolT(false);

  public static final BoolT True = new BoolT(true);

  @Override
  public boolean booleanValue() {
    return b;
  }

  /** Compare implements the traits.Comparer interface method. */
  @Override
  public Val compare(Val other) {
    if (!(other instanceof BoolT)) {
      return valOrErr(other, "no such overload");
    }
    return IntT.intOf(Boolean.compare(b, ((BoolT) other).b));
  }

  /** ConvertToNative implements the ref.Val interface method. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == Boolean.class || typeDesc == boolean.class) {
      return (T) Boolean.valueOf(b);
    }

    //		switch typeDesc.Kind() {
    //		case reflect.Bool:
    //			return reflect.ValueOf(b).Convert(typeDesc).Interface(), nil
    //		case reflect.Ptr:
    //			switch typeDesc {
    //			case anyValueType:
    //				// Primitives must be wrapped to a wrapperspb.BoolValue before being packed into an Any.
    //				return anypb.New(wrapperspb.Bool(bool(b)))
    //			case boolWrapperType:
    //				// Convert the bool to a wrapperspb.BoolValue.
    //				return wrapperspb.Bool(bool(b)), nil
    //			case jsonValueType:
    //				// Return the bool as a new structpb.Value.
    //				return structpb.NewBoolValue(bool(b)), nil
    //			default:
    //				if typeDesc.Elem().Kind() == reflect.Bool {
    //					p := bool(b)
    //					return &p, nil
    //				}
    //			}
    //		case reflect.Interface:
    //			bv := b.Value()
    //			if reflect.TypeOf(bv).Implements(typeDesc) {
    //				return bv, nil
    //			}
    //			if reflect.TypeOf(b).Implements(typeDesc) {
    //				return b, nil
    //			}
    //		}
    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", BoolType, typeDesc.getName()));
  }

  /** ConvertToType implements the ref.Val interface method. */
  @Override
  public Val convertToType(Type typeVal) {
    if (typeVal == StringType) {
      return stringOf(Boolean.toString(b));
    }
    if (typeVal == BoolType) {
      return this;
    }
    if (typeVal == TypeType) {
      return BoolType;
    }
    return newErr("type conversion error from '%s' to '%s'", BoolType, typeVal);
  }

  /** Equal implements the ref.Val interface method. */
  @Override
  public Val equal(Val other) {
    if (!(other instanceof BoolT)) {
      return valOrErr(other, "no such overload");
    }
    return boolOf(b == ((BoolT) other).b);
  }

  /** Negate implements the traits.Negater interface method. */
  @Override
  public Val negate() {
    return boolOf(!b);
  }

  /** Type implements the ref.Val interface method. */
  @Override
  public Type type() {
    return BoolType;
  }

  /** Value implements the ref.Val interface method. */
  @Override
  public Object value() {
    return b;
  }

  /** IsBool returns whether the input ref.Val or ref.Type is equal to BoolType. */
  public static boolean isBool(Object elem) {
    if (elem instanceof Type) {
      return elem == BoolType;
    }
    if (elem instanceof Val) {
      return isBool(((Val) elem).type());
    }
    return false;
  }
}
