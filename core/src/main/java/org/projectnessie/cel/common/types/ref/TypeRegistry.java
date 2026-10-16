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
package org.projectnessie.cel.common.types.ref;

/**
 * Mutable adapter and provider to which host types can be registered.
 *
 * <p>Not every {@link TypeProvider} supports registration. A registry combines provider and adapter
 * behavior so registered types can be checked, constructed, and converted consistently.
 *
 * <p>Unless an implementation documents otherwise, complete registry mutation before sharing the
 * registry for concurrent checking or evaluation. Use {@link #copy()} when independently mutable
 * configuration is required after a configured registry has been reused.
 */
public interface TypeRegistry extends TypeAdapter, TypeProvider {

  /**
   * Copies this registry.
   *
   * <p>The returned registry has independently mutable registration state. Registered immutable
   * type metadata may be shared internally by an implementation.
   *
   * @return an independently configurable registry
   */
  TypeRegistry copy();

  /**
   * Registers a type via a materialized object, which the provider can turn into a type.
   *
   * <p>Repeated registration of an equivalent materialized type must succeed without changing the
   * existing type's behavior. Implementations define materialized-type equivalence because the
   * supported host representations are implementation-specific. Distinct representations of the
   * same logical type need not be equivalent.
   *
   * @param t implementation-supported materialized type, descriptor, or instance
   */
  void register(Object t);

  /**
   * RegisterType registers a type value with the provider which ensures the provider is aware of
   * how to map the type to an identifier.
   *
   * <p>Registries which support this operation accept equivalent repeated definitions. If a type is
   * provided more than once with an alternative definition, the call fails without installing any
   * type from that call. Implementations may reject type-value registration entirely with {@link
   * UnsupportedOperationException}.
   *
   * @param types runtime CEL type values to register
   * @throws IllegalArgumentException if a supported registry already contains a conflicting
   *     definition
   * @throws NullPointerException if this registry supports type-value registration and {@code
   *     types} or any element is null
   * @throws UnsupportedOperationException if this registry does not support type-value registration
   */
  void registerType(Type... types);
}
