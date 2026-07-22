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

@FunctionalInterface
public interface ActivationFunction {
  Object ABSENT =
      new Object() {
        @Override
        public String toString() {
          return "<ABSENT_SENTINEL>";
        }
      };

  /**
   * Returns a value from the activation by qualified name.
   *
   * <p>Return the exact ABSENT singleton when unresolved; consumers compare it by identity.
   *
   * @return the resolved value, {@code null} if null or the constant/sentinel value {@link #ABSENT}
   *     if the name/value is absent.
   */
  Object resolve(String name);
}
