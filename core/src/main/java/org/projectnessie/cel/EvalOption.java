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

/**
 * EvalOption indicates an evaluation option that may affect the evaluation behavior or information
 * in the output result.
 */
public enum EvalOption {

  /** OptTrackState will cause the runtime to return an immutable EvalState value in the Result. */
  OptTrackState(1),

  /** OptExhaustiveEval causes the runtime to disable short-circuits and track state. */
  OptExhaustiveEval(2 | OptTrackState.mask),

  /**
   * OptOptimize precomputes functions and operators with constants as arguments at program creation
   * time. This flag is useful when the expression will be evaluated repeatedly against a series of
   * different inputs.
   */
  OptOptimize(4),

  /**
   * OptPartialEval enables the evaluation of a partial state where the input data that may be known
   * to be missing, either as top-level variables, or somewhere within a variable's object member
   * graph.
   *
   * <p>By itself, OptPartialEval does not change evaluation behavior unless the input to the
   * Program Eval is an PartialVars.
   */
  OptPartialEval(8);

  private final int mask;

  EvalOption(int mask) {
    this.mask = mask;
  }

  public int getMask() {
    return mask;
  }
}
