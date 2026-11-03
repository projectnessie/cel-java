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

import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableAttribute;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableConst;

/**
 * InterpretableDecorator is a functional interface for decorating or replacing Interpretable
 * expression nodes at construction time.
 */
@FunctionalInterface
public interface InterpretableDecorator {
  /**
   * Decorates or replaces one plan node.
   *
   * <p>Decorators are invoked during planning and must preserve the node's CEL semantics unless
   * their documented purpose explicitly changes evaluation behavior.
   */
  Interpretable decorate(Interpretable i);

  /** Receives the value produced for an expression identifier. */
  @FunctionalInterface
  interface EvalObserver {
    /** Records one evaluated expression value. */
    void observe(long id, Val v);
  }

  /** Returns a decorator that reports every evaluated node to the observer. */
  static InterpretableDecorator decObserveEval(EvalObserver observer) {
    return i -> {
      if ((i instanceof EvalWatch)
          || (i instanceof EvalWatchAttr)
          || (i instanceof EvalWatchConst)) {
        // these instruction are already watching, return straight-away.
        return i;
      }
      if (i instanceof InterpretableAttribute) {
        return new EvalWatchAttr((InterpretableAttribute) i, observer);
      }
      if (i instanceof InterpretableConst) {
        return new EvalWatchConst((InterpretableConst) i, observer);
      }
      return new EvalWatch(i, observer);
    };
  }

  /**
   * Returns a decorator that evaluates all branches for exhaustive-state observation.
   *
   * <p>This intentionally replaces ordinary short-circuit behavior and should be used only for
   * exhaustive evaluation/state collection.
   */
  static InterpretableDecorator decDisableShortcircuits() {
    return i -> {
      if (i instanceof EvalOr expr) {
        return new EvalExhaustiveOr(expr.id, expr.lhs, expr.rhs);
      }
      if (i instanceof EvalAnd expr) {
        return new EvalExhaustiveAnd(expr.id, expr.lhs, expr.rhs);
      }
      if (i instanceof EvalFold expr) {
        return new EvalExhaustiveFold(
            expr.id,
            expr.accu,
            expr.accuVar,
            expr.iterRange,
            expr.iterVar,
            expr.iterVar2,
            expr.cond,
            expr.step,
            expr.result);
      }
      if (i instanceof EvalListFold fold) {
        return new EvalExhaustiveListFold(fold);
      }
      if (i instanceof EvalMapFold fold) {
        return new EvalExhaustiveMapFold(fold);
      }
      if (i instanceof InterpretableAttribute expr) {
        if (expr.attr() instanceof ConditionalAttribute) {
          return new EvalExhaustiveConditional(
              i.id(), expr.adapter(), (ConditionalAttribute) expr.attr());
        }
      }
      return i;
    };
  }

  /**
   * Returns the built-in plan optimizer.
   *
   * <ul>
   *   <li>Build list and map values with constant elements.
   *   <li>Evaluate supported constant calls and conversions.
   *   <li>Convert eligible membership calls to constant-set lookups.
   * </ul>
   *
   * <p>Unsupported or semantically unsafe shapes retain their existing nodes. The optimizer does
   * not guarantee that a particular expression is folded or specialized.
   */
  static InterpretableDecorator decOptimize() {
    return BuiltInOptimizer.INSTANCE;
  }
}
