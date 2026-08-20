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

import static org.projectnessie.cel.common.types.IntT.intOf;

import org.projectnessie.cel.common.types.ref.Val;

/** Capability for values that support CEL index access such as {@code a[b]}. */
public interface Indexer {
  /**
   * Returns the value at {@code index}.
   *
   * @param index list index or map key
   * @return the selected value, a CEL error or unknown value, or Java {@code null} when an
   *     implementation such as {@link Mapper} uses null to report an absent key
   */
  Val get(Val index);

  /**
   * Returns the value at a native integer index.
   *
   * <p>The default delegates to {@link #get(Val)} with a CEL int. Implementations may override it
   * to avoid constructing that index, but must preserve the same result, error, and unknown
   * semantics.
   *
   * @param index zero-based Java index
   * @return the selected value, or the same absence, CEL error, or unknown result as {@code get}
   */
  default Val nativeGetAt(int index) {
    return get(intOf(index));
  }
}
