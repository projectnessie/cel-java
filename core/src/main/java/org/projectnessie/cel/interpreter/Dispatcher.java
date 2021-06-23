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
package org.projectnessie.cel.interpreter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.interpreter.functions.Overload;

/** Dispatcher resolves function calls to their appropriate overload. */
public interface Dispatcher {
  /** Add one or more overloads, returning an error if any Overload has the same Overload#Name. */
  void add(Overload... overloads);

  /** FindOverload returns an Overload definition matching the provided name. */
  Overload findOverload(String overload);

  /** OverloadIds returns the set of all overload identifiers configured for dispatch. */
  String[] overloadIds();

  /** NewDispatcher returns an empty Dispatcher instance. */
  static Dispatcher newDispatcher() {
    return new DefaultDispatcher(null, new HashMap<>());
  }

  /**
   * ExtendDispatcher returns a Dispatcher which inherits the overloads of its parent, and provides
   * an isolation layer between built-ins and extension functions which is useful for forward
   * compatibility.
   */
  static Dispatcher extendDispatcher(Dispatcher parent) {
    return new DefaultDispatcher(parent, new HashMap<>());
  }

  /** defaultDispatcher struct which contains an overload map. */
  final class DefaultDispatcher implements Dispatcher {
    private final Dispatcher parent;
    private final Map<String, Overload> overloads;

    DefaultDispatcher(Dispatcher parent, Map<String, Overload> overloads) {
      this.parent = parent;
      this.overloads = overloads;
    }

    /** Add implements the Dispatcher.Add interface method. */
    @Override
    public void add(Overload... overloads) {
      for (Overload o : overloads) {

        // add the overload unless an overload of the same name has already been provided.
        if (this.overloads.containsKey(o.operator)) {
          throw new IllegalArgumentException(
              String.format("overload already exists '%s'", o.operator));
        }
        // index the overload by function name.
        this.overloads.put(o.operator, o);
      }
    }

    /** FindOverload implements the Dispatcher.FindOverload interface method. */
    @Override
    public Overload findOverload(String overload) {
      Overload o = overloads.get(overload);
      if (o != null) {
        return o;
      }
      return parent != null ? parent.findOverload(overload) : null;
    }

    /** OverloadIds implements the Dispatcher interface method. */
    @Override
    public String[] overloadIds() {
      List<String> r = new ArrayList<>(overloads.keySet());
      if (parent != null) {
        Collections.addAll(r, parent.overloadIds());
      }
      return r.toArray(new String[0]);
    }
  }
}
