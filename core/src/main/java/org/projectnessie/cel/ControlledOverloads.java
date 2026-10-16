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

import org.projectnessie.cel.OperationAbortedException.Phase;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.internal.OperationCheckpoints;
import org.projectnessie.cel.interpreter.functions.Overload;

/** Adds controlled-operation boundaries around application and extension overloads. */
final class ControlledOverloads {
  private ControlledOverloads() {}

  static Overload[] wrap(Overload[] overloads) {
    var wrapped = new Overload[overloads.length];
    for (int i = 0; i < overloads.length; i++) {
      wrapped[i] = wrap(overloads[i]);
    }
    return wrapped;
  }

  private static Overload wrap(Overload overload) {
    return Overload.overload(
        overload.operator,
        overload.operandTrait,
        overload.unary == null ? null : value -> controlled(() -> overload.unary.invoke(value)),
        overload.binary == null
            ? null
            : (left, right) -> controlled(() -> overload.binary.invoke(left, right)),
        overload.ternary == null
            ? null
            : (first, second, third) ->
                controlled(() -> overload.ternary.invoke(first, second, third)),
        overload.quaternary == null
            ? null
            : (first, second, third, fourth) ->
                controlled(() -> overload.quaternary.invoke(first, second, third, fourth)),
        overload.quinary == null
            ? null
            : (first, second, third, fourth, fifth) ->
                controlled(() -> overload.quinary.invoke(first, second, third, fourth, fifth)),
        overload.function == null
            ? null
            : arguments -> controlled(() -> overload.function.invoke(arguments)));
  }

  private static Val controlled(Invocation invocation) {
    OperationCheckpoints.checkpointNow(Phase.EVALUATE);
    var result = invocation.invoke();
    OperationCheckpoints.checkpointNow(Phase.EVALUATE);
    return result;
  }

  @FunctionalInterface
  private interface Invocation {
    Val invoke();
  }
}
