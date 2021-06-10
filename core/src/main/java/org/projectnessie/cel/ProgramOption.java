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

import static org.projectnessie.cel.interpreter.Activation.newActivation;

import java.util.Collections;
import org.projectnessie.cel.interpreter.InterpretableDecorator;
import org.projectnessie.cel.interpreter.functions.Overload;

@FunctionalInterface
public interface ProgramOption {
  Prog apply(Prog prog);

  /**
   * CustomDecorator appends an InterpreterDecorator to the program.
   *
   * <p>InterpretableDecorators can be used to inspect, alter, or replace the Program plan.
   */
  static ProgramOption customDecorator(InterpretableDecorator dec) {
    return p -> {
      p.decorators.add(dec);
      return p;
    };
  }

  /** Functions adds function overloads that extend or override the set of CEL built-ins. */
  static ProgramOption functions(Overload... funcs) {
    return p -> {
      p.dispatcher.add(funcs);
      return p;
    };
  }

  /**
   * Globals sets the global variable values for a given program. These values may be shadowed by
   * variables with the same name provided to the Eval() call.
   *
   * <p>The vars value may either be an `interpreter.Activation` instance or a
   * `map[string]interface{}`.
   */
  static ProgramOption globals(Object vars) {
    return p -> {
      p.defaultVars = newActivation(vars);
      return p;
    };
  }

  /** EvalOptions sets one or more evaluation options which may affect the evaluation or Result. */
  static ProgramOption evalOptions(EvalOption... opts) {
    return p -> {
      Collections.addAll(p.evalOpts, opts);
      return p;
    };
  }
}
