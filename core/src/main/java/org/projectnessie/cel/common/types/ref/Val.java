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
 * Val interface defines the functions supported by all expression values. Val implementations may
 * specialize the behavior of the value through the addition of traits.
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
   * ConvertToType supports type conversions between value types supported by the expression
   * language.
   */
  Val convertToType(Type typeValue);

  /**
   * Equal returns true if the `other` value has the same type and content as the implementing
   * struct.
   */
  Val equal(Val other);

  /** Type returns the TypeValue of the value. */
  Type type();

  /**
   * Value returns the raw value of the instance which may not be directly compatible with the
   * expression language types.
   */
  Object value();

  boolean booleanValue();

  long intValue();

  double doubleValue();
}
