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

import static org.projectnessie.cel.checker.Types.typeKey;

import com.google.api.expr.v1alpha1.Type;
import java.util.HashMap;
import java.util.Map;

public class Mapping {

  private final Map<String, Type> mapping;

  private Mapping(Map<String, Type> mapping) {
    this.mapping = mapping;
  }

  static Mapping newMapping() {
    return new Mapping(new HashMap<>());
  }

  void add(Type from, Type to) {
    mapping.put(typeKey(from), to);
  }

  Type find(Type from) {
    return mapping.get(typeKey(from));
  }

  Mapping copy() {
    return new Mapping(new HashMap<>(mapping));
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder("{");

    mapping.forEach((k, v) -> result.append(k).append(" => ").append(v));

    result.append("}");
    return result.toString();
  }
}
