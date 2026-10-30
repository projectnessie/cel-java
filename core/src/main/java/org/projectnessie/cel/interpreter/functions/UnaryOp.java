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
package org.projectnessie.cel.interpreter.functions;

import org.projectnessie.cel.common.types.ref.Val;

/**
 * Runtime implementation of a one-argument CEL function overload.
 *
 * <p>Implementations registered on a reusable program must be thread-safe.
 */
@FunctionalInterface
public interface UnaryOp {
  /**
   * Invokes the function.
   *
   * @param val evaluated argument
   * @return the non-null CEL result, which may be a CEL error or unknown value
   */
  Val invoke(Val val);
}
