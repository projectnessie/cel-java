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

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.MapEntry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProtoMapSupport {
  private ProtoMapSupport() {}

  static Map<Object, Object> canonicalMap(
      List<?> entries, FieldDescriptor keyDescriptor, FieldDescriptor valueDescriptor) {
    Map<Object, Object> result = new LinkedHashMap<>(entries.size() * 4 / 3 + 1);
    for (Object entry : entries) {
      Object key = key(entry, keyDescriptor);
      result.remove(key);
      result.put(key, value(entry, valueDescriptor));
    }
    return Collections.unmodifiableMap(result);
  }

  static Object key(Object entry, FieldDescriptor descriptor) {
    return FieldDescription.normalizeUnsignedValue(
        entryDescriptor(entry, descriptor, 1), rawValue(entry, 1));
  }

  static Object value(Object entry, FieldDescriptor descriptor) {
    return FieldDescription.normalizeUnsignedValue(
        entryDescriptor(entry, descriptor, 2), rawValue(entry, 2));
  }

  private static FieldDescriptor entryDescriptor(
      Object entry, FieldDescriptor fallback, int fieldNumber) {
    if (entry instanceof DynamicMessage dynamic) {
      List<FieldDescriptor> fields = dynamic.getDescriptorForType().getFields();
      if (fields.size() == 2) {
        return fields.get(fieldNumber - 1);
      }
    }
    return fallback;
  }

  private static Object rawValue(Object entry, int fieldNumber) {
    if (entry instanceof Map.Entry<?, ?> mapEntry) {
      return fieldNumber == 1 ? mapEntry.getKey() : mapEntry.getValue();
    }
    if (entry instanceof MapEntry<?, ?> mapEntry) {
      return fieldNumber == 1 ? mapEntry.getKey() : mapEntry.getValue();
    }
    if (entry instanceof DynamicMessage dynamic) {
      List<FieldDescriptor> fields = dynamic.getDescriptorForType().getFields();
      if (fields.size() == 2) {
        return dynamic.getField(fields.get(fieldNumber - 1));
      }
    }
    throw new IllegalArgumentException(
        String.format("Unexpected %s (%s) in list of map fields", entry.getClass(), entry));
  }
}
