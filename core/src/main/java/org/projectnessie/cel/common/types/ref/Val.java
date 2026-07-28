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
 * Runtime representation of a CEL value.
 *
 * <p>Implementations provide conversion, equality, type, and underlying-value access. Additional
 * operators are exposed by implementing interfaces from {@link
 * org.projectnessie.cel.common.types.traits} and advertising the same traits through {@link
 * Type#hasTrait(org.projectnessie.cel.common.types.traits.Trait)}.
 *
 * <p>CEL errors and unknowns are values and propagate through these operations. Implementations
 * should return a non-null CEL error or unknown value for an evaluation failure rather than Java
 * {@code null}. Methods that convert a CEL value to a Java representation may instead throw when
 * the requested representation is incompatible. Values supplied to a reusable program must be
 * immutable or safe for the caller's concurrent evaluation pattern.
 */
public interface Val {
  /**
   * Converts the value to a native Java value according to the requested type, or reports an error
   * if the conversion is not feasible.
   *
   * @deprecated Use {@link TypeAdapter#valueToNative(Val, Class)} with the adapter associated with
   *     the evaluation environment. {@code DefaultTypeAdapter.Instance} can be used for
   *     context-free built-in values.
   */
  @Deprecated(forRemoval = true)
  <T> T convertToNative(Class<T> typeDesc);

  /**
   * Converts this value to another CEL type.
   *
   * @param typeValue target CEL type
   * @return the converted value, or a CEL error or unknown value if conversion does not produce a
   *     concrete value
   */
  Val convertToType(Type typeValue);

  /**
   * Evaluates CEL equality between this value and {@code other}.
   *
   * @param other value to compare
   * @return a CEL boolean, error, or unknown value
   */
  Val equal(Val other);

  /**
   * Returns this value's runtime CEL type.
   *
   * @return a stable, non-null type value
   */
  Type type();

  /**
   * Returns the underlying representation.
   *
   * <p>The result is implementation-specific and is not necessarily accepted directly by other CEL
   * operations. Prefer {@link TypeAdapter#valueToNative(Val, Class)} when a particular Java
   * representation is required.
   *
   * @return the underlying representation, possibly {@code null} for CEL null
   */
  Object value();

  /**
   * Returns this value as a primitive boolean.
   *
   * @throws RuntimeException if this value cannot be represented as a boolean
   */
  boolean booleanValue();

  /**
   * Returns this CEL int or uint as Java {@code long} bits.
   *
   * @throws RuntimeException if this value cannot be represented as an integer
   */
  long intValue();

  /**
   * Returns this value as a primitive double.
   *
   * @throws RuntimeException if this value cannot be represented as a double
   */
  double doubleValue();
}
