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
package org.projectnessie.cel;

import java.util.Objects;
import org.projectnessie.cel.OperationAbortedException.Phase;
import org.projectnessie.cel.internal.ControlledOperation;
import org.projectnessie.cel.internal.OperationController;
import org.projectnessie.cel.internal.OperationScope;

final class ProgramControls {
  private ProgramControls() {}

  static CancelableEval newEvaluation(Program program, Object vars, ResourceLimits limits) {
    Objects.requireNonNull(program, "program");
    Objects.requireNonNull(limits, "limits");
    if (!(program instanceof ControllableProgram controllable)) {
      throw new UnsupportedOperationException(
          "controlled evaluation is not implemented by " + program.getClass().getName());
    }
    var active = OperationScope.current();
    var controller =
        active.isControlled() && active.limits() == limits
            ? active
            : new OperationController(limits);
    var operation =
        new ControlledOperation<>(
            controller, Phase.EVALUATE, () -> controllable.evalControlled(vars, controller));
    return new CancelableEval() {
      @Override
      public Program.EvalResult eval() {
        return operation.execute();
      }

      @Override
      public void cancel() {
        operation.cancel();
      }
    };
  }
}
