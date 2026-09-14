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
package org.projectnessie.cel.interpreter.functions;

import org.projectnessie.cel.common.types.ref.Val;

/**
 * Runtime implementation of a three-argument CEL function overload.
 *
 * <p>For receiver-style calls, {@code first} is the receiver. Implementations registered on a
 * reusable program must be thread-safe.
 */
@FunctionalInterface
public interface TernaryOp {
  /**
   * Invokes the function.
   *
   * @param first evaluated first argument or receiver
   * @param second evaluated second argument
   * @param third evaluated third argument
   * @return the non-null CEL result, which may be a CEL error or unknown value
   */
  Val invoke(Val first, Val second, Val third);
}
