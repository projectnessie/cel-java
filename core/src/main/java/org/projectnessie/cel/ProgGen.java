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

import static org.projectnessie.cel.CEL.estimateCost;
import static org.projectnessie.cel.Prog.emptyEvalState;
import static org.projectnessie.cel.interpreter.EvalState.newEvalState;

import org.projectnessie.cel.interpreter.Coster;
import org.projectnessie.cel.interpreter.EvalState;

final class ProgGen implements Program, Coster {
  private final ProgFactory factory;

  ProgGen(ProgFactory factory) {
    this.factory = factory;
  }

  /** Eval implements the Program interface method. */
  public EvalResult eval(Object input) {
    // The factory based Eval() differs from the standard evaluation model in that it generates a
    // new EvalState instance for each call to ensure that unique evaluations yield unique stateful
    // results.
    EvalState state = newEvalState();

    // Generate a new instance of the interpretable using the factory configured during the call to
    // newProgram(). It is incredibly unlikely that the factory call will generate an error given
    // the factory test performed within the Program() call.
    Program p = factory.apply(state);

    // Evaluate the input, returning the result and the 'state' within EvalDetails.
    return p.eval(input);
  }

  /** Cost implements the Coster interface method. */
  @Override
  public Cost cost() {
    // Use an empty state value since no evaluation is performed.
    Program p = factory.apply(emptyEvalState);
    return estimateCost(p);
  }
}
