/*
 * Copyright (C) 2023 The Authors of CEL-Java
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

import java.util.Objects;

public final class ResolvedValue {
  public static final ResolvedValue NULL_VALUE = new ResolvedValue(null, true);
  public static final ResolvedValue ABSENT = new ResolvedValue(null, false);

  public static ResolvedValue resolvedValue(Object value) {
    return new ResolvedValue(Objects.requireNonNull(value), true);
  }

  private final Object value;
  private final boolean present;

  private ResolvedValue(Object value, boolean present) {
    this.value = value;
    this.present = present;
  }

  public Object value() {
    return value;
  }

  public boolean present() {
    return present;
  }
}
