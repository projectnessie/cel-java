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
package org.projectnessie.cel.common.types.pb;

import com.google.api.expr.v1alpha1.Type;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.common.types.ref.ExactAggregateFieldProvider;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;

final class ExactProtoTypeRegistry extends ProtoTypeRegistry
    implements ExactAggregateTypeAdapter, ExactAggregateFieldProvider {

  ExactProtoTypeRegistry(
      Map<String, org.projectnessie.cel.common.types.ref.Type> revTypeMap, Db pbdb) {
    super(revTypeMap, pbdb);
  }

  @Override
  ProtoTypeRegistry newCopy(
      Map<String, org.projectnessie.cel.common.types.ref.Type> copiedTypes, Db copiedDb) {
    return new ExactProtoTypeRegistry(copiedTypes, copiedDb);
  }

  @Override
  public boolean isExactAggregateField(String messageType, String fieldName, Type checkedType) {
    FieldDescription field = findFieldDescription(messageType, fieldName);
    return field != null
        && field.checkedType().equals(checkedType)
        && (isExactRepeatedField(field.descriptor()) || isExactMapField(field.descriptor()));
  }

  @Override
  Object normalizeFieldValue(
      FieldDescription field, Object target, Object value, boolean generated) {
    if (isExactRepeatedField(field.descriptor())) {
      return field.exactRepeatedValue(value);
    }
    if (isExactMapField(field.descriptor())) {
      if (value instanceof Map<?, ?>) {
        return value;
      }
      if (value instanceof List<?> entries) {
        return new DynamicProtoMapView(field.descriptor(), entries);
      }
      throw new IllegalArgumentException(
          String.format(
              "unsupported exact protobuf map representation: %s",
              value == null ? "null" : value.getClass().getName()));
    }
    return super.normalizeFieldValue(field, target, value, generated);
  }

  private static boolean isExactRepeatedField(FieldDescriptor field) {
    if (!field.isRepeated() || field.isMapField()) {
      return false;
    }
    return switch (field.getType()) {
      case BOOL,
          INT32,
          INT64,
          SINT32,
          SINT64,
          SFIXED32,
          SFIXED64,
          UINT32,
          UINT64,
          FIXED32,
          FIXED64,
          FLOAT,
          DOUBLE,
          STRING ->
          true;
      default -> false;
    };
  }

  private static boolean isExactMapField(FieldDescriptor field) {
    if (!field.isMapField()) {
      return false;
    }
    FieldDescriptor key = field.getMessageType().findFieldByNumber(1);
    FieldDescriptor value = field.getMessageType().findFieldByNumber(2);
    return key.getType() == FieldDescriptor.Type.STRING
        && value.getType() == FieldDescriptor.Type.INT64;
  }
}
