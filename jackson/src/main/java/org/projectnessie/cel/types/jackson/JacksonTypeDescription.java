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
package org.projectnessie.cel.types.jackson;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.TypeT;
import org.projectnessie.cel.common.types.pb.Checked;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeDescription;

final class JacksonTypeDescription implements TypeDescription {

  private final JavaType javaType;
  private final String name;
  private final Type type;
  private final com.google.api.expr.v1alpha1.Type pbType;

  private final Map<String, PropertyWriter> properties;
  private final Map<String, FieldType> fieldTypes;

  JacksonTypeDescription(JavaType javaType, JsonSerializer<Object> ser) {
    this.javaType = javaType;
    this.name = javaType.getRawClass().getName();
    this.type = TypeT.newObjectTypeValue(name);
    this.pbType = com.google.api.expr.v1alpha1.Type.newBuilder().setMessageType(name).build();

    properties = new HashMap<>();
    fieldTypes = new HashMap<>();

    Iterator<PropertyWriter> propIter = ser.properties();
    while (propIter.hasNext()) {
      PropertyWriter pw = propIter.next();
      String n = pw.getName();
      properties.put(n, pw);

      FieldType ft =
          new FieldType(
              findTypeForJacksonType(pw.getType()),
              target -> fromObject(target, n) != null,
              target -> fromObject(target, n));
      fieldTypes.put(n, ft);
    }
  }

  private com.google.api.expr.v1alpha1.Type findTypeForJacksonType(JavaType type) {
    Class<?> rawClass = type.getRawClass();
    if (rawClass == boolean.class || rawClass == Boolean.class) {
      return Checked.checkedBool;
    } else if (rawClass == long.class
        || rawClass == Long.class
        || rawClass == int.class
        || rawClass == Integer.class
        || rawClass == short.class
        || rawClass == Short.class
        || rawClass == byte.class
        || rawClass == Byte.class) {
      return Checked.checkedInt;
    } else if (rawClass == ULong.class) {
      return Checked.checkedUint;
    } else if (rawClass == byte[].class || rawClass == ByteString.class) {
      return Checked.checkedBytes;
    } else if (rawClass == double.class
        || rawClass == Double.class
        || rawClass == float.class
        || rawClass == Float.class) {
      return Checked.checkedDouble;
    } else if (rawClass == String.class) {
      return Checked.checkedString;
    } else if (rawClass == Duration.class || rawClass == java.time.Duration.class) {
      return Checked.checkedDuration;
    } else if (rawClass == Timestamp.class
        || Instant.class.isAssignableFrom(rawClass)
        || ZonedDateTime.class.isAssignableFrom(rawClass)) {
      return Checked.checkedTimestamp;
    } else if (Map.class.isAssignableFrom(rawClass)) {
      com.google.api.expr.v1alpha1.Type keyType = findTypeForJacksonType(type.getKeyType());
      com.google.api.expr.v1alpha1.Type valueType = findTypeForJacksonType(type.getContentType());
      return Decls.newMapType(keyType, valueType);
    } else if (List.class.isAssignableFrom(rawClass)) {
      com.google.api.expr.v1alpha1.Type valueType = findTypeForJacksonType(type.getContentType());
      return Decls.newListType(valueType);
    } else {
      throw new UnsupportedOperationException(String.format("Unsupported Java Type '%s'", type));
    }
  }

  boolean hasProperty(String property) {
    return properties.containsKey(property);
  }

  Object fromObject(Object value, String property) {
    PropertyWriter pw = properties.get(property);

    if (pw instanceof BeanPropertyWriter) {
      try {
        return ((BeanPropertyWriter) pw).get(value);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else if (pw == null) {
      return null;
    } else {
      throw new UnsupportedOperationException(
          String.format(
              "Unknown property-writer '%s' for property '%s'", pw.getClass().getName(), property));
    }
  }

  Type type() {
    return type;
  }

  com.google.api.expr.v1alpha1.Type pbType() {
    return pbType;
  }

  FieldType fieldType(String fieldName) {
    return fieldTypes.get(fieldName);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Class<?> reflectType() {
    return javaType.getRawClass();
  }

  @Override
  public String toString() {
    return "JacksonTypeDescription{name: '" + name() + "', reflectType: " + reflectType() + '}';
  }
}
