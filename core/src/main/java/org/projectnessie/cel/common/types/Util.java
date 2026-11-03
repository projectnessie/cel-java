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
package org.projectnessie.cel.common.types;

import org.projectnessie.cel.common.types.ref.Val;

/** Small predicates shared by built-in CEL value operations. */
public final class Util {

  /** Returns whether a value is a CEL error or unknown. */
  public static boolean isUnknownOrError(Val val) {
    return switch (val.type().typeEnum()) {
      case Unknown, Err -> true;
      default -> false;
    };
  }

  /** Returns whether a value has a primitive CEL type, excluding well-known types. */
  public static boolean isPrimitiveType(Val val) {
    return switch (val.type().typeEnum()) {
      case Bool, Bytes, Double, Int, String, Uint -> true;
      default -> false;
    };
  }
}
