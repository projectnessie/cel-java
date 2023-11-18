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
import static org.projectnessie.cel.common.types.Types.boolOf;

import java.util.Objects;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeDescription;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.FieldTester;
import org.projectnessie.cel.common.types.traits.Indexer;

public abstract class ObjectT extends BaseVal implements FieldTester, Indexer, TypeAdapter {
  protected final TypeAdapter adapter;
  protected final Object value;
  protected final TypeDescription typeDesc;
  protected final Type typeValue;

  protected ObjectT(TypeAdapter adapter, Object value, TypeDescription typeDesc, Type typeValue) {
    this.adapter = adapter;
    this.value = value;
    this.typeDesc = typeDesc;
    this.typeValue = typeValue;
  }

  @Override
  public Val convertToType(Type typeVal) {
    switch (typeVal.typeEnum()) {
      case Type:
        return typeValue;
      case Object:
        if (type().typeName().equals(typeVal.typeName())) {
          return this;
        }
        break;
    }
    return newTypeConversionError(typeDesc.name(), typeVal);
  }

  @Override
  public Val equal(Val other) {
    if (other.type().typeEnum() != TypeEnum.Object) {
      return False;
    }
    if (!typeDesc.name().equals(other.type().typeName())) {
      return False;
    }

    return boolOf(this.value.equals(other.value()));
  }

  @Override
  public Type type() {
    return typeValue;
  }

  @Override
  public Object value() {
    return value;
  }

  @Override
  public Val nativeToValue(Object value) {
    return adapter.nativeToValue(value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ObjectT objectT = (ObjectT) o;
    return Objects.equals(value, objectT.value)
        && Objects.equals(typeDesc, objectT.typeDesc)
        && Objects.equals(typeValue, objectT.typeValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), value, typeDesc, typeValue);
  }
}
