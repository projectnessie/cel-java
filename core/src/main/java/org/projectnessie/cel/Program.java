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

import org.projectnessie.cel.common.types.ref.Val;

/** Program is an evaluable view of an Ast. */
public interface Program {

  static EvalResult newEvalResult(Val val, EvalDetails evalDetails) {
    return new EvalResult(val, evalDetails);
  }

  /**
   * Eval returns the result of an evaluation of the Ast and environment against the input vars.
   *
   * <p>The vars value may either be an `interpreter.Activation` or a `map[string]interface{}`.
   *
   * <p>If the `OptTrackState` or `OptExhaustiveEval` flags are used, the `details` response will be
   * non-nil. Given this caveat on `details`, the return state from evaluation will be:
   *
   * <ul>
   *   <li>`val`, `details`, `nil` - Successful evaluation of a non-error result.
   *   <li>`val`, `details`, `err` - Successful evaluation to an error result.
   *   <li>`nil`, `details`, `err` - Unsuccessful evaluation.
   * </ul>
   *
   * <p>An unsuccessful evaluation is typically the result of a series of incompatible `EnvOption`
   * or `ProgramOption` values used in the creation of the evaluation environment or executable
   * program.
   */
  EvalResult eval(Object vars);

  final class EvalResult {
    private final Val val;
    private final EvalDetails evalDetails;

    private EvalResult(Val val, EvalDetails evalDetails) {
      this.val = val;
      this.evalDetails = evalDetails;
    }

    public Val getVal() {
      return val;
    }

    public EvalDetails getEvalDetails() {
      return evalDetails;
    }
  }
}
