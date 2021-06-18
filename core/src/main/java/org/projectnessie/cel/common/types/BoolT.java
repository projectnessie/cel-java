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

import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.IntT.intOfCompare;
import static org.projectnessie.cel.common.types.StringT.stringOf;

import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Value;
import java.util.Objects;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Comparer;
import org.projectnessie.cel.common.types.traits.Negater;
import org.projectnessie.cel.common.types.traits.Trait;

/** Bool type that implements ref.Val and supports comparison and negation. */
public final class BoolT extends BaseVal implements Comparer, Negater {

  /** BoolType singleton. */
  public static final Type BoolType =
      TypeT.newTypeValue(TypeEnum.Bool, Trait.ComparerType, Trait.NegatorType);
  /** Boolean constants */
  public static final BoolT False = new BoolT(false);

  public static final BoolT True = new BoolT(true);

  private final boolean b;

  BoolT(boolean b) {
    this.b = b;
  }

  @Override
  public boolean booleanValue() {
    return b;
  }

  /** Compare implements the traits.Comparer interface method. */
  @Override
  public Val compare(Val other) {
    if (!(other instanceof BoolT)) {
      return noSuchOverload(this, "compare", other);
    }
    return intOfCompare(Boolean.compare(b, ((BoolT) other).b));
  }

  /** ConvertToNative implements the ref.Val interface method. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == Boolean.class || typeDesc == boolean.class || typeDesc == Object.class) {
      return (T) Boolean.valueOf(b);
    }

    if (typeDesc == Any.class) {
      return (T) Any.pack(BoolValue.of(this.b));
    }
    if (typeDesc == BoolValue.class) {
      return (T) BoolValue.of(this.b);
    }
    if (typeDesc == Val.class || typeDesc == BoolT.class) {
      return (T) this;
    }
    if (typeDesc == Value.class) {
      return (T) Value.newBuilder().setBoolValue(b).build();
    }

    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", BoolType, typeDesc.getName()));
  }

  /** ConvertToType implements the ref.Val interface method. */
  @Override
  public Val convertToType(Type typeVal) {
    switch (typeVal.typeEnum()) {
      case String:
        return stringOf(Boolean.toString(b));
      case Bool:
        return this;
      case Type:
        return BoolType;
    }
    return newTypeConversionError(BoolType, typeVal);
  }

  /** Equal implements the ref.Val interface method. */
  @Override
  public Val equal(Val other) {
    if (!(other instanceof BoolT)) {
      return noSuchOverload(this, "equal", other);
    }
    return Types.boolOf(b == ((BoolT) other).b);
  }

  /** Negate implements the traits.Negater interface method. */
  @Override
  public Val negate() {
    return Types.boolOf(!b);
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BoolT boolT = (BoolT) o;
    return b == boolT.b;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), b);
  }
}
