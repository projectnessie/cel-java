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
package org.projectnessie.cel.common.types.traits;

import org.projectnessie.cel.common.types.ref.Val;

/** Capability for values that support the CEL {@code size()} operation. */
public interface Sizer {
  /**
   * Returns the number of elements or the length as a CEL int.
   *
   * @return a non-negative CEL int, or a CEL error or unknown value
   */
  Val size();

  /**
   * Returns the same size as a Java {@code int}.
   *
   * <p>The default converts {@link #size()}; implementations may override it to avoid constructing
   * a CEL integer but must preserve the same numeric result.
   *
   * @return the non-negative size
   * @throws ArithmeticException if the size cannot be represented as a Java {@code int}
   * @throws RuntimeException if {@link #size()} returns a value that cannot be converted to an
   *     integer
   */
  default int nativeSize() {
    return Math.toIntExact(size().intValue());
  }
}
