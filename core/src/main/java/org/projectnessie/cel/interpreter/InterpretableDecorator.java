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

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Interpretable.newConstValue;

import java.util.HashSet;
import java.util.Set;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.Util;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.interpreter.AttributeFactory.ConditionalAttribute;
import org.projectnessie.cel.interpreter.Interpretable.EvalAnd;
import org.projectnessie.cel.interpreter.Interpretable.EvalExhaustiveAnd;
import org.projectnessie.cel.interpreter.Interpretable.EvalExhaustiveConditional;
import org.projectnessie.cel.interpreter.Interpretable.EvalExhaustiveFold;
import org.projectnessie.cel.interpreter.Interpretable.EvalExhaustiveOr;
import org.projectnessie.cel.interpreter.Interpretable.EvalFold;
import org.projectnessie.cel.interpreter.Interpretable.EvalList;
import org.projectnessie.cel.interpreter.Interpretable.EvalMap;
import org.projectnessie.cel.interpreter.Interpretable.EvalOr;
import org.projectnessie.cel.interpreter.Interpretable.EvalSetMembership;
import org.projectnessie.cel.interpreter.Interpretable.EvalWatch;
import org.projectnessie.cel.interpreter.Interpretable.EvalWatchAttr;
import org.projectnessie.cel.interpreter.Interpretable.EvalWatchConst;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableAttribute;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableCall;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableConst;

/**
 * InterpretableDecorator is a functional interface for decorating or replacing Interpretable
 * expression nodes at construction time.
 */
@FunctionalInterface
public interface InterpretableDecorator {
  Interpretable func(Interpretable i);

  /** evalObserver is a functional interface that accepts an expression id and an observed value. */
  @FunctionalInterface
  interface EvalObserver {
    void func(long id, Val v);
  }

  /** decObserveEval records evaluation state into an EvalState object. */
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
   * decDisableShortcircuits ensures that all branches of an expression will be evaluated, no
   * short-circuiting.
   */
  static InterpretableDecorator decDisableShortcircuits() {
    return i -> {
      if (i instanceof EvalOr) {
        EvalOr expr = (EvalOr) i;
        return new EvalExhaustiveOr(expr.id, expr.lhs, expr.rhs);
      }
      if (i instanceof EvalAnd) {
        EvalAnd expr = (EvalAnd) i;
        return new EvalExhaustiveAnd(expr.id, expr.lhs, expr.rhs);
      }
      if (i instanceof EvalFold) {
        EvalFold expr = (EvalFold) i;
        return new EvalExhaustiveFold(
            expr.id,
            expr.accu,
            expr.accuVar,
            expr.iterRange,
            expr.iterVar,
            expr.cond,
            expr.step,
            expr.result);
      }
      if (i instanceof InterpretableAttribute) {
        InterpretableAttribute expr = (InterpretableAttribute) i;
        if (expr.attr() instanceof ConditionalAttribute) {
          return new EvalExhaustiveConditional(
              i.id(), expr.adapter(), (ConditionalAttribute) expr.attr());
        }
      }
      return i;
    };
  }

  /**
   * decOptimize optimizes the program plan by looking for common evaluation patterns and
   * conditionally precomputating the result.
   *
   * <ul>
   *   <li>build list and map values with constant elements.
   *   <li>convert 'in' operations to set membership tests if possible.
   * </ul>
   */
  static InterpretableDecorator decOptimize() {
    return i -> {
      if (i instanceof EvalList) {
        return maybeBuildListLiteral(i, (EvalList) i);
      }
      if (i instanceof EvalMap) {
        return maybeBuildMapLiteral(i, (EvalMap) i);
      }
      if (i instanceof InterpretableCall) {
        InterpretableCall inst = (InterpretableCall) i;
        if (inst.overloadID().equals(Overloads.InList)) {
          return maybeOptimizeSetMembership(i, inst);
        }
        if (Overloads.isTypeConversionFunction(inst.function())) {
          return maybeOptimizeConstUnary(i, inst);
        }
      }
      return i;
    };
  }

  static Interpretable maybeOptimizeConstUnary(Interpretable i, InterpretableCall call) {
    Interpretable[] args = call.args();
    if (args.length != 1) {
      return i;
    }
    if (!(args[0] instanceof InterpretableConst)) {
      return i;
    }
    Val val = call.eval(emptyActivation());
    if (isError(val)) {
      throw new IllegalStateException(val.toString());
    }
    return newConstValue(call.id(), val);
  }

  static Interpretable maybeBuildListLiteral(Interpretable i, EvalList l) {
    for (Interpretable elem : l.elems) {
      if (!(elem instanceof InterpretableConst)) {
        return i;
      }
    }
    return newConstValue(l.id(), l.eval(emptyActivation()));
  }

  static Interpretable maybeBuildMapLiteral(Interpretable i, EvalMap mp) {
    for (int idx = 0; idx < mp.keys.length; idx++) {
      if (!(mp.keys[idx] instanceof InterpretableConst)) {
        return i;
      }
      if (!(mp.vals[idx] instanceof InterpretableConst)) {
        return i;
      }
    }
    return newConstValue(mp.id(), mp.eval(emptyActivation()));
  }

  /**
   * maybeOptimizeSetMembership may convert an 'in' operation against a list to map key membership
   * test if the following conditions are true:
   *
   * <ul>
   *   <li>the list is a constant with homogeneous element types.
   *   <li>the elements are all of primitive type.
   * </ul>
   */
  static Interpretable maybeOptimizeSetMembership(Interpretable i, InterpretableCall inlist) {
    Interpretable[] args = inlist.args();
    Interpretable lhs = args[0];
    Interpretable rhs = args[1];
    if (!(rhs instanceof InterpretableConst)) {
      return i;
    }
    InterpretableConst l = (InterpretableConst) rhs;
    // When the incoming binary call is flagged with as the InList overload, the value will
    // always be convertible to a `traits.Lister` type.
    Lister list = (Lister) l.value();
    if (list.size() == IntZero) {
      return newConstValue(inlist.id(), False);
    }
    IteratorT it = list.iterator();
    Type typ = null;
    Set<Val> valueSet = new HashSet<>();
    while (it.hasNext() == True) {
      Val elem = it.next();
      if (!Util.isPrimitiveType(elem)) {
        // Note, non-primitive type are not yet supported.
        return i;
      }
      if (typ == null) {
        typ = elem.type();
      } else if (!typ.typeName().equals(elem.type().typeName())) {
        return i;
      }
      valueSet.add(elem);
    }
    return new EvalSetMembership(inlist, lhs, typ.typeName(), valueSet);
  }
}
