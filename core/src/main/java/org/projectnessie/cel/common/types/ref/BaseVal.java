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
package org.projectnessie.cel.common.types.ref;

import static org.projectnessie.cel.common.types.BoolT.BoolType;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DoubleT.DoubleType;
import static org.projectnessie.cel.common.types.IntT.IntType;

public abstract class BaseVal implements Val {

  @Override
  public int hashCode() {
    return value().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Val) {
      return equal((Val) obj) == True;
    }
    return value().equals(obj);
  }

  @Override
  public String toString() {
    return String.format("%s{%s}", type().typeName(), value());
  }

  @Override
  public boolean booleanValue() {
    return convertToType(BoolType).booleanValue();
  }

  @Override
  public long intValue() {
    return convertToType(IntType).intValue();
  }

  @Override
  public double doubleValue() {
    return convertToType(DoubleType).doubleValue();
  }
}
