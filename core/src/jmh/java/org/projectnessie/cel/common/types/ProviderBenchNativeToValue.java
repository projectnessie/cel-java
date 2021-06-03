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

import static org.projectnessie.cel.common.types.StringT.stringOf;

public enum ProviderBenchNativeToValue {
  v_true(true),
  v_false(false),
  v_float32(-1.2f),
  v_float64(-2.4d),
  v_int32(2),
  v_int64(3L),
  v_emptyString(""),
  v_hello("hello"),
  v_stringT(stringOf("hello world"));

  private final Object value;

  ProviderBenchNativeToValue(Object value) {
    this.value = value;
  }

  public Object value() {
    return value;
  }
}
