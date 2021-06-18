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

import java.util.Objects;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;

/**
 * Unknown type implementation which collects expression ids which caused the current value to
 * become unknown.
 */
public final class UnknownT extends BaseVal {
  /** UnknownType singleton. */
  public static final Type UnknownType = TypeT.newTypeValue(TypeEnum.Unknown);

  public static UnknownT unknownOf(long value) {
    return new UnknownT(value);
  }

  private final long value;

  private UnknownT(long value) {
    this.value = value;
  }

  /** ConvertToNative implements ref.Val.ConvertToNative. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (Long.class.isAssignableFrom(typeDesc)
        || long.class.isAssignableFrom(typeDesc)
        || typeDesc == Object.class) {
      return (T) (Long) value;
    }
    if (typeDesc == Val.class || typeDesc == UnknownT.class) {
      return (T) this;
    }
    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", UnknownType, typeDesc.getName()));
  }

  @Override
  public long intValue() {
    return value;
  }

  /** ConvertToType implements ref.Val.ConvertToType. */
  @Override
  public Val convertToType(Type typeVal) {
    return this;
  }

  /** Equal implements ref.Val.Equal. */
  @Override
  public Val equal(Val other) {
    return this;
  }

  /** Type implements ref.Val.Type. */
  @Override
  public Type type() {
    return UnknownType;
  }

  /** Value implements ref.Val.Value. */
  @Override
  public Object value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UnknownT unknownT = (UnknownT) o;
    return value == unknownT.value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), value);
  }

  /**
   * IsUnknown returns whether the element ref.Type or ref.Val is equal to the UnknownType
   * singleton.
   */
  public static boolean isUnknown(Object val) {
    return val != null && val.getClass() == UnknownT.class;
  }
}
