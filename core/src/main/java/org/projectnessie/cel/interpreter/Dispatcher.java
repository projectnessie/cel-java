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

import java.util.HashMap;
import org.projectnessie.cel.interpreter.functions.Overload;

/**
 * Mutable registry that resolves checked overload identifiers to runtime implementations.
 *
 * <p>Configure a dispatcher before planning interpretable expressions. A child dispatcher created
 * by {@link #extendDispatcher(Dispatcher)} keeps its registrations separate and falls back to its
 * parent. Dispatcher mutation and concurrent lookup must not race.
 */
public interface Dispatcher {
  /**
   * Adds runtime overload implementations.
   *
   * @throws IllegalArgumentException if an identifier is already registered in this dispatcher
   */
  void add(Overload... overloads);

  /** Returns the overload for an identifier, or {@code null} when none is registered. */
  Overload findOverload(String overload);

  /** Returns a snapshot of identifiers visible through this dispatcher, including its parents. */
  String[] overloadIds();

  /** Returns an empty mutable dispatcher without a parent. */
  static Dispatcher newDispatcher() {
    return new DefaultDispatcher(null, new HashMap<>());
  }

  /**
   * Returns an empty mutable dispatcher that falls back to {@code parent}.
   *
   * <p>Registrations in the returned child do not mutate the parent.
   */
  static Dispatcher extendDispatcher(Dispatcher parent) {
    return new DefaultDispatcher(parent, new HashMap<>());
  }
}
