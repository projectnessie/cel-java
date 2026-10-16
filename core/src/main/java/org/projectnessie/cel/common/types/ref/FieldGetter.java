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
 * Reads a field from a host object.
 *
 * <p>A getter is associated with a field through {@link FieldType}. The supplied target is an
 * instance accepted by the owning {@link TypeProvider}; the returned Java value is subsequently
 * adapted through the environment's {@link TypeAdapter}. A getter may return Java {@code null} for
 * a present CEL null value. Implementations must be safe for every concurrent use supported by the
 * provider and must not mutate the target.
 */
@FunctionalInterface
public interface FieldGetter {
  /**
   * Reads the field value from {@code target}.
   *
   * @param target host object containing the field
   * @return the field's host representation, possibly {@code null}
   */
  Object getFrom(Object target);

  /**
   * Optional allocation-free primitive access implemented by trusted field getters.
   *
   * <p>The primitive methods are used only when the checked field kind and {@link
   * #optimizedTargetType()} are compatible. Implementations override only the supported primitive
   * accessors; other accessors retain their fail-fast defaults.
   */
  interface Primitive extends FieldGetter {
    /**
     * Returns the runtime target type for which the primitive accessors are valid.
     *
     * @return the exact or base host type accepted by the optimized accessors
     */
    Class<?> optimizedTargetType();

    /**
     * Reads a CEL boolean without boxing.
     *
     * @param target compatible host object
     * @return field value
     * @throws UnsupportedOperationException unless boolean access is implemented
     */
    default boolean getBooleanFrom(Object target) {
      throw new UnsupportedOperationException("boolean field access is not supported");
    }

    /**
     * Reads a CEL signed integer without boxing.
     *
     * @param target compatible host object
     * @return field value as a CEL signed integer
     * @throws UnsupportedOperationException unless signed-integer access is implemented
     */
    default long getLongFrom(Object target) {
      throw new UnsupportedOperationException("integer field access is not supported");
    }

    /**
     * Reads a CEL double without boxing.
     *
     * @param target compatible host object
     * @return field value
     * @throws UnsupportedOperationException unless double access is implemented
     */
    default double getDoubleFrom(Object target) {
      throw new UnsupportedOperationException("double field access is not supported");
    }
  }
}
