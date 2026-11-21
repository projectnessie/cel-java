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
 * Capability for values that dynamically dispatch CEL receiver-style method calls.
 *
 * <p>The interpreter invokes this fallback only when no configured overload applicable to the
 * receiver's advertised trait handles the call. Implementations should return a CEL
 * no-such-overload error when the function or overload is unsupported.
 */
public interface Receiver {
  /**
   * Dispatches a receiver-style call on this value.
   *
   * @param function CEL function name
   * @param overload checked overload identifier, which may be empty for an unchecked expression
   * @param args arguments after the receiver
   * @return the call result, or a CEL error or unknown value
   */
  Val receive(String function, String overload, Val... args);
}
