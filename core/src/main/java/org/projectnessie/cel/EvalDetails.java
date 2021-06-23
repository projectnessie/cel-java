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

/** EvalDetails holds additional information observed during the Eval() call. */
public final class EvalDetails {
  private final EvalState state;

  public EvalDetails(EvalState state) {
    this.state = state;
  }

  /**
   * State of the evaluation, non-nil if the OptTrackState or OptExhaustiveEval is specified within
   * EvalOptions.
   */
  public EvalState getState() {
    return state;
  }
}
