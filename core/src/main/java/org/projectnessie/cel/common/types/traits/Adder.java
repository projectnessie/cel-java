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

/** Capability for values that support the CEL binary {@code +} operator. */
public interface Adder {
  /**
   * Adds or concatenates {@code other} to this value according to the implementing CEL type.
   *
   * @param other right-hand operand
   * @return the result, or a CEL error or unknown value when the operation cannot produce a value
   */
  Val add(Val other);
}
