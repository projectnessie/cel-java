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
package org.projectnessie.cel.checker;

import static java.util.Collections.emptyMap;

import com.google.api.expr.v1alpha1.Type;
import java.util.HashMap;
import java.util.Map;

public class Mapping {

  private final Map<String, Type> mapping;
  private final Map<Type, String> typeKeys;

  private Mapping(Map<String, Type> srcMapping, Map<Type, String> srcTypeKeys) {
    // Looks overly complicated, but prevents a bunch of j.u.HashMap.resize() operations.
    // The copy() operation is called very often when a script's being checked, so this saves
    // quite a lot.
    // The formula "* 4 / 3 + 1" prevents the HashMap from resizing, assuming the default-load-factor
    // of .75 (-> 3/4).
    this.mapping = new HashMap<>(srcMapping.size() * 4 / 3 + 1);
    this.mapping.putAll(srcMapping);
    this.typeKeys = new HashMap<>(srcTypeKeys.size() * 4 / 3 + 1);
    this.typeKeys.putAll(srcTypeKeys);
  }

  static Mapping newMapping() {
    return new Mapping(emptyMap(), emptyMap());
  }

  private String keyForType(Type t) {
    // The lookup by `Type` called very often when a script's being checked, so this saves
    // quite a lot.
    return typeKeys.computeIfAbsent(t, Types::typeKey);
  }

  void add(Type from, Type to) {
    mapping.put(keyForType(from), to);
  }

  Type find(Type from) {
    return mapping.get(keyForType(from));
  }

  Mapping copy() {
    return new Mapping(mapping, typeKeys);
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder("{");

    mapping.forEach((k, v) -> result.append(k).append(" => ").append(v));

    result.append("}");
    return result.toString();
  }
}
