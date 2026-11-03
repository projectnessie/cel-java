/*
 * Copyright (C) 2026 The Authors of CEL-Java
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
package org.projectnessie.cel.types.jackson3;

import com.google.api.expr.v1alpha1.Type;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.projectnessie.cel.common.types.ref.ExactAggregateFieldProvider;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.StandardScalarFieldProvider;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;

/** Distinct runtime type for the opt-in exact aggregate contract. */
final class ExactJackson3Registry
    implements TypeRegistry,
        StandardScalarTypeAdapter,
        StandardScalarFieldProvider,
        ExactAggregateTypeAdapter,
        ExactAggregateFieldProvider {
  private final Jackson3Registry delegate;

  ExactJackson3Registry(Jackson3Registry delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public TypeRegistry copy() {
    return new ExactJackson3Registry((Jackson3Registry) delegate.copy());
  }

  @Override
  public void register(Object value) {
    delegate.register(value);
  }

  @Override
  public void registerType(org.projectnessie.cel.common.types.ref.Type... types) {
    delegate.registerType(types);
  }

  @Override
  public Val enumValue(String enumName) {
    return delegate.enumValue(enumName);
  }

  @Override
  public Val findIdent(String identName) {
    return delegate.findIdent(identName);
  }

  @Override
  public Type findType(String typeName) {
    return delegate.findType(typeName);
  }

  @Override
  public FieldType findFieldType(String messageType, String fieldName) {
    FieldType fieldType = delegate.findFieldType(messageType, fieldName);
    if (fieldType == null
        || (fieldType.type.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE
            && fieldType.type.getTypeKindCase() != Type.TypeKindCase.MAP_TYPE)) {
      return fieldType;
    }
    return new FieldType(
        fieldType.type,
        fieldType.isSet,
        target -> canonicalAggregate(fieldType.getFrom.getFrom(target)));
  }

  private static Object canonicalAggregate(Object value) {
    return value instanceof Optional<?> optional ? optional.orElse(null) : value;
  }

  @Override
  public Val newValue(String typeName, Map<String, Val> fields) {
    return delegate.newValue(typeName, fields);
  }

  @Override
  public Val nativeToValue(Object value) {
    return delegate.nativeToValue(value);
  }

  @Override
  public Val nativeToValue(boolean value) {
    return delegate.nativeToValue(value);
  }

  @Override
  public Val nativeToValue(byte value) {
    return delegate.nativeToValue(value);
  }

  @Override
  public Val nativeToValue(short value) {
    return delegate.nativeToValue(value);
  }

  @Override
  public Val nativeToValue(int value) {
    return delegate.nativeToValue(value);
  }

  @Override
  public Val nativeToValue(long value) {
    return delegate.nativeToValue(value);
  }

  @Override
  public Val nativeToValue(float value) {
    return delegate.nativeToValue(value);
  }

  @Override
  public Val nativeToValue(double value) {
    return delegate.nativeToValue(value);
  }
}
