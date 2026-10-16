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
package org.projectnessie.cel.interpreter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.interpreter.functions.Overload;

/** Default dispatcher backed by an overload map. */
final class DefaultDispatcher implements Dispatcher {
  private final Dispatcher parent;
  private final Map<String, Overload> overloads;

  DefaultDispatcher(Dispatcher parent, Map<String, Overload> overloads) {
    this.parent = parent;
    this.overloads = overloads;
  }

  @Override
  public void add(Overload... overloads) {
    for (Overload overload : overloads) {
      if (this.overloads.containsKey(overload.operator)) {
        throw new IllegalArgumentException(
            String.format("overload already exists '%s'", overload.operator));
      }
      this.overloads.put(overload.operator, overload);
    }
  }

  @Override
  public Overload findOverload(String overload) {
    Overload result = overloads.get(overload);
    if (result != null) {
      return result;
    }
    return parent != null ? parent.findOverload(overload) : null;
  }

  @Override
  public String[] overloadIds() {
    List<String> result = new ArrayList<>(overloads.keySet());
    if (parent != null) {
      Collections.addAll(result, parent.overloadIds());
    }
    return result.toArray(new String[0]);
  }
}
