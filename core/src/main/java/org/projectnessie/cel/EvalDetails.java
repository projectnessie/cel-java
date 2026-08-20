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
package org.projectnessie.cel;

import org.projectnessie.cel.interpreter.EvalState;

/**
 * Additional information associated with one program evaluation.
 *
 * <p>{@link Program#eval(Object)} always returns details containing a non-null, evaluation-owned
 * state. The public constructor retains its supplied value, including {@code null}, for
 * compatibility with directly constructed instances. Evaluation states are mutable and not
 * thread-safe; inspect or modify a returned state only with appropriate external synchronization.
 */
public final class EvalDetails {
  private final EvalState state;

  /**
   * Creates evaluation details containing the supplied state.
   *
   * @param state evaluation state; may be {@code null} for compatibility with existing direct use
   */
  public EvalDetails(EvalState state) {
    this.state = state;
  }

  /**
   * Returns the evaluation state supplied at construction.
   *
   * <p>Results produced by {@link Program#eval(Object)} always return a non-null state. It is empty
   * unless state tracking or exhaustive evaluation was requested.
   *
   * @return evaluation state, or {@code null} only when a directly constructed instance was given a
   *     null state
   */
  public EvalState getState() {
    return state;
  }
}
