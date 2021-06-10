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
package org.projectnessie.cel.interpreter;

import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.InterpretableDecorator.decDisableShortcircuits;
import static org.projectnessie.cel.interpreter.InterpretableDecorator.decObserveEval;
import static org.projectnessie.cel.interpreter.InterpretableDecorator.decOptimize;
import static org.projectnessie.cel.interpreter.InterpretablePlanner.newPlanner;
import static org.projectnessie.cel.interpreter.InterpretablePlanner.newUncheckedPlanner;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Expr;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;
import org.projectnessie.cel.interpreter.functions.Overload;

/** Interpreter generates a new Interpretable from a checked or unchecked expression. */
public interface Interpreter {
  /**
   * NewInterpretable creates an Interpretable from a checked expression and an optional list of
   * InterpretableDecorator values.
   */
  Interpretable newInterpretable(CheckedExpr checked, InterpretableDecorator... decorators);

  /**
   * NewUncheckedInterpretable returns an Interpretable from a parsed expression and an optional
   * list of InterpretableDecorator values.
   */
  Interpretable newUncheckedInterpretable(Expr expr, InterpretableDecorator... decorators);

  /**
   * TrackState decorates each expression node with an observer which records the value associated
   * with the given expression id. EvalState must be provided to the decorator. This decorator is
   * not thread-safe, and the EvalState must be reset between Eval() calls.
   */
  static InterpretableDecorator trackState(EvalState state) {
    return decObserveEval(state::setValue);
  }

  /**
   * ExhaustiveEval replaces operations that short-circuit with versions that evaluate expressions
   * and couples this behavior with the TrackState() decorator to provide insight into the
   * evaluation state of the entire expression. EvalState must be provided to the decorator. This
   * decorator is not thread-safe, and the EvalState must be reset between Eval() calls.
   */
  static InterpretableDecorator exhaustiveEval(EvalState state) {
    InterpretableDecorator ex = decDisableShortcircuits();
    InterpretableDecorator obs = trackState(state);
    return i -> {
      Interpretable iDec = ex.decorate(i);
      return obs.decorate(iDec);
    };
  }

  /**
   * Optimize will pre-compute operations such as list and map construction and optimize call
   * arguments to set membership tests. The set of optimizations will increase over time.
   */
  static InterpretableDecorator optimize() {
    return decOptimize();
  }

  /**
   * NewInterpreter builds an Interpreter from a Dispatcher and TypeProvider which will be used
   * throughout the Eval of all Interpretable instances gerenated from it.
   */
  static Interpreter newInterpreter(
      Dispatcher dispatcher,
      Container container,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory) {
    return new ExprInterpreter(dispatcher, container, provider, adapter, attrFactory);
  }

  /**
   * NewStandardInterpreter builds a Dispatcher and TypeProvider with support for all of the CEL
   * builtins defined in the language definition.
   */
  static Interpreter newStandardInterpreter(
      Container container, TypeProvider provider, TypeAdapter adapter, AttributeFactory resolver) {
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(Overload.standardOverloads());
    return newInterpreter(dispatcher, container, provider, adapter, resolver);
  }

  class ExprInterpreter implements Interpreter {
    private final Dispatcher dispatcher;
    private final Container container;
    private final TypeProvider provider;
    private final TypeAdapter adapter;
    private final AttributeFactory attrFactory;

    ExprInterpreter(
        Dispatcher dispatcher,
        Container container,
        TypeProvider provider,
        TypeAdapter adapter,
        AttributeFactory attrFactory) {
      this.dispatcher = dispatcher;
      this.container = container;
      this.provider = provider;
      this.adapter = adapter;
      this.attrFactory = attrFactory;
    }

    /** NewIntepretable implements the Interpreter interface method. */
    @Override
    public Interpretable newInterpretable(
        CheckedExpr checked, InterpretableDecorator... decorators) {
      InterpretablePlanner p =
          newPlanner(dispatcher, provider, adapter, attrFactory, container, checked, decorators);
      return p.plan(checked.getExpr());
    }

    /** NewUncheckedIntepretable implements the Interpreter interface method. */
    @Override
    public Interpretable newUncheckedInterpretable(
        Expr expr, InterpretableDecorator... decorators) {
      InterpretablePlanner p =
          newUncheckedPlanner(dispatcher, provider, adapter, attrFactory, container, decorators);
      return p.plan(expr);
    }
  }
}
