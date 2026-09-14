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

/**
 * Capability for object values that support field-presence tests used by the CEL {@code has()}
 * macro.
 */
public interface FieldTester {
  /**
   * Tests whether the named field is present according to the object's CEL presence semantics.
   *
   * @param field CEL field-name value
   * @return CEL true if present, false if the field exists but is absent, or a CEL error or unknown
   *     value
   */
  Val isSet(Val field);
}
