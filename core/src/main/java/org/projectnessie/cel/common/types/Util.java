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

public final class Util {

  /** IsUnknownOrError returns whether the input element ref.Val is an ErrType or UnknonwType. */
  public static boolean isUnknownOrError(Val val) {
    switch (val.type().typeEnum()) {
      case Unknown:
      case Err:
        return true;
    }
    return false;
  }

  /**
   * IsPrimitiveType returns whether the input element ref.Val is a primitive type. Note, primitive
   * types do not include well-known types such as Duration and Timestamp.
   */
  public static boolean isPrimitiveType(Val val) {
    switch (val.type().typeEnum()) {
      case Bool:
      case Bytes:
      case Double:
      case Int:
      case String:
      case Uint:
        return true;
    }
    return false;
  }
}
