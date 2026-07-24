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
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DynamicProtoMapView extends AbstractMap<String, Long> {
  private final List<?> entries;
  private final FieldDescriptor keyDescriptor;
  private final FieldDescriptor valueDescriptor;
  private volatile Map<String, Long> canonical;

  DynamicProtoMapView(FieldDescriptor field, List<?> entries) {
    this.entries = entries;
    this.keyDescriptor = field.getMessageType().findFieldByNumber(1);
    this.valueDescriptor = field.getMessageType().findFieldByNumber(2);
  }

  @Override
  public Long get(Object key) {
    if (!(key instanceof String)) {
      return null;
    }
    Map<String, Long> materialized = canonical;
    if (materialized != null) {
      return materialized.get(key);
    }
    for (int i = entries.size() - 1; i >= 0; i--) {
      Object entry = entries.get(i);
      if (key.equals(ProtoMapSupport.key(entry, keyDescriptor))) {
        return (Long) ProtoMapSupport.value(entry, valueDescriptor);
      }
    }
    return null;
  }

  @Override
  public boolean containsKey(Object key) {
    if (!(key instanceof String)) {
      return false;
    }
    Map<String, Long> materialized = canonical;
    if (materialized != null) {
      return materialized.containsKey(key);
    }
    for (int i = entries.size() - 1; i >= 0; i--) {
      if (key.equals(ProtoMapSupport.key(entries.get(i), keyDescriptor))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Set<Entry<String, Long>> entrySet() {
    return canonical().entrySet();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Long> canonical() {
    Map<String, Long> result = canonical;
    if (result != null) {
      return result;
    }
    synchronized (this) {
      result = canonical;
      if (result == null) {
        result =
            (Map<String, Long>)
                (Map<?, ?>) ProtoMapSupport.canonicalMap(entries, keyDescriptor, valueDescriptor);
        canonical = result;
      }
      return result;
    }
  }
}
