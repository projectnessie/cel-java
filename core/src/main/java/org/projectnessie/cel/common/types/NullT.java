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

import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.StringT.stringOf;

import com.google.protobuf.Any;
import com.google.protobuf.Value;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;

/** Null type implementation. */
public final class NullT extends BaseVal {

  /** NullType singleton. */
  public static final Type NullType = TypeT.newTypeValue(TypeEnum.Null);
  /** NullValue singleton. */
  public static final NullT NullValue = new NullT();

  private static final Value PbValue =
      Value.newBuilder().setNullValue(com.google.protobuf.NullValue.NULL_VALUE).build();
  private static final Any PbAny = Any.pack(PbValue);

  /** ConvertToNative implements ref.Val.ConvertToNative. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == Integer.class || typeDesc == int.class) {
      return (T) (Integer) 0;
    }
    if (typeDesc == Any.class) {
      return (T) PbAny;
    }
    if (typeDesc == Value.class) {
      return (T) PbValue;
    }
    if (typeDesc == com.google.protobuf.NullValue.class) {
      return (T) com.google.protobuf.NullValue.NULL_VALUE;
    }
    if (typeDesc == Val.class || typeDesc == NullT.class) {
      return (T) this;
    }
    if (typeDesc == Object.class) {
      return null;
    }
    //		switch typeDesc.Kind() {
    //		case reflect.Interface:
    //			nv := n.Value()
    //			if reflect.TypeOf(nv).Implements(typeDesc) {
    //				return nv, nil
    //			}
    //			if reflect.TypeOf(n).Implements(typeDesc) {
    //				return n, nil
    //			}
    //		}
    // If the type conversion isn't supported return an error.
    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", NullType, typeDesc.getName()));
  }

  /** ConvertToType implements ref.Val.ConvertToType. */
  @Override
  public Val convertToType(Type typeValue) {
    switch (typeValue.typeEnum()) {
      case String:
        return stringOf("null");
      case Null:
        return this;
      case Type:
        return NullType;
    }
    return newTypeConversionError(NullType, typeValue);
  }

  /** Equal implements ref.Val.Equal. */
  @Override
  public Val equal(Val other) {
    if (NullType != other.type()) {
      return noSuchOverload(this, "equal", other);
    }
    return True;
  }

  /** Type implements ref.Val.Type. */
  @Override
  public Type type() {
    return NullType;
  }

  /** Value implements ref.Val.Value. */
  @Override
  public Object value() {
    return com.google.protobuf.NullValue.NULL_VALUE;
  }

  @Override
  public String toString() {
    return "null";
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public boolean equals(Object obj) {
    return obj.getClass() == NullT.class;
  }
}
