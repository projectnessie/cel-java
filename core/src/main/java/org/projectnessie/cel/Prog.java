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
import static org.projectnessie.cel.Program.newEvalResult;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.Activation.newHierarchicalActivation;
import static org.projectnessie.cel.interpreter.EvalState.newEvalState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.projectnessie.cel.common.types.Err.ErrException;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.Activation;
import org.projectnessie.cel.interpreter.AttributeFactory;
import org.projectnessie.cel.interpreter.Coster;
import org.projectnessie.cel.interpreter.Dispatcher;
import org.projectnessie.cel.interpreter.EvalState;
import org.projectnessie.cel.interpreter.Interpretable;
import org.projectnessie.cel.interpreter.InterpretableDecorator;
import org.projectnessie.cel.interpreter.Interpreter;

/** prog is the internal implementation of the Program interface. */
class Prog implements Program, Coster {
  static final EvalState emptyEvalState = newEvalState();

  Env e;
  final Set<EvalOption> evalOpts = EnumSet.noneOf(EvalOption.class);
  final List<InterpretableDecorator> decorators = new ArrayList<>();
  Activation defaultVars;
  Dispatcher dispatcher;
  Interpreter interpreter;
  Interpretable interpretable;
  AttributeFactory attrFactory;
  final EvalState state;

  Prog(Env e, Dispatcher dispatcher) {
    this.e = e;
    this.dispatcher = dispatcher;
    this.state = newEvalState();
  }

  Prog(
      Env e,
      Set<EvalOption> evalOpts,
      Activation defaultVars,
      Dispatcher dispatcher,
      Interpreter interpreter,
      EvalState state) {
    this.e = e;
    this.evalOpts.addAll(evalOpts);
    this.defaultVars = defaultVars;
    this.dispatcher = dispatcher;
    this.interpreter = interpreter;
    this.state = state;
  }

  /** Eval implements the Program interface method. */
  @Override
  public EvalResult eval(Object input) {
    Val v;

    EvalDetails evalDetails = new EvalDetails(state);

    try {
      // Build a hierarchical activation if there are default vars set.
      Activation vars = newActivation(input);

      if (defaultVars != null) {
        vars = newHierarchicalActivation(defaultVars, vars);
      }

      v = interpretable.eval(vars);
    } catch (ErrException e) {
      v = e.getErr();
    } catch (Exception e) {
      throw new RuntimeException(String.format("internal error: %s", e.getMessage()), e);
    }

    // The output of an internal Eval may have a value (`v`) that is a types.Err. This step
    // translates the CEL value to a Go error response. This interface does not quite match the
    // RPC signature which allows for multiple errors to be returned, but should be sufficient.
    // NOTE: Unlike the Go implementation, errors are handled differently in the Java
    // implementation.
    //    if (isError(v)) {
    //      throw new EvalException(v);
    //    }

    return newEvalResult(v, evalDetails);
  }

  // Cost implements the Coster interface method.
  @Override
  public Cost cost() {
    return estimateCost(interpretable);
  }
}
