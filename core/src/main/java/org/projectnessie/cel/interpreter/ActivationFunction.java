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

/**
 * Resolves CEL variable names to caller-supplied Java values.
 *
 * <p>Return {@link #ABSENT} by identity when a name has no binding. Java {@code null} is a present
 * binding representing CEL null and is therefore distinct from absence. The resolver is retained by
 * an activation created with {@link Activation#newActivation(Object)} and may be called
 * concurrently when a program is shared; implementations must provide the corresponding thread
 * safety and should not mutate evaluation inputs.
 */
@FunctionalInterface
public interface ActivationFunction {
  /**
   * Identity sentinel returned for an unresolved variable.
   *
   * <p>Do not expose this object as an actual variable value.
   */
  Object ABSENT =
      new Object() {
        @Override
        public String toString() {
          return "<ABSENT_SENTINEL>";
        }
      };

  /**
   * Resolves a value by qualified CEL variable name.
   *
   * <p>Return the exact {@link #ABSENT} singleton when unresolved; consumers compare it by
   * identity. Java {@code null} means the name is present with a CEL null value.
   *
   * @param name qualified variable name requested by the evaluator
   * @return the resolved Java or CEL value, {@code null} for a present null, or {@link #ABSENT}
   */
  Object resolve(String name);
}
