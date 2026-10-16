/*
 * Copyright (C) 2026 The Authors of CEL-Java
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

import static java.util.Objects.requireNonNull;

/**
 * Interpreter-owned metadata for one checked overload that may gain a typed implementation.
 *
 * <p>The exact resolved {@code Overload} identity remains the primary provenance key. Function and
 * overload names are additional checked-expression guards, not substitutes for that identity.
 */
record NativeOverloadDescriptor(String function, String overloadId) {
  NativeOverloadDescriptor {
    requireNonNull(function, "function");
    requireNonNull(overloadId, "overloadId");
  }

  boolean matches(String checkedFunction, String checkedOverloadId) {
    return function.equals(checkedFunction) && overloadId.equals(checkedOverloadId);
  }
}
