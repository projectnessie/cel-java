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
 * Tests whether a field is present on a host object.
 *
 * <p>This host-object callback is used by {@link FieldType}; it is distinct from the CEL value
 * trait {@link org.projectnessie.cel.common.types.traits.FieldTester}. Implementations must use the
 * presence semantics of the represented host type and must not mutate the target.
 */
@FunctionalInterface
public interface FieldTester {
  /**
   * Tests field presence on {@code target}.
   *
   * @param target host object accepted by the owning {@link TypeProvider}
   * @return whether the field is present
   */
  boolean isSet(Object target);
}
