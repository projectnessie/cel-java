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
package org.projectnessie.cel.interpreter;

import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.throwErrorAsIllegalStateException;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;

import java.util.HashSet;
import java.util.Set;
import org.projectnessie.cel.OperationAbortedException.Phase;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.Util;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.internal.OperationCheckpoints;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableCall;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableConst;

/** Built-in constant folding and set-membership optimizer. */
final class BuiltInOptimizer implements InterpretableDecorator {
  static final BuiltInOptimizer INSTANCE = new BuiltInOptimizer();

  private BuiltInOptimizer() {}

  @Override
  public Interpretable decorate(Interpretable interpretable) {
    if (interpretable instanceof EvalList list) {
      return maybeBuildListLiteral(interpretable, list);
    }
    if (interpretable instanceof EvalMap map) {
      return maybeBuildMapLiteral(interpretable, map);
    }
    if (interpretable instanceof InterpretableCall call) {
      if (call.overloadID().equals(Overloads.InList)) {
        return maybeOptimizeSetMembership(interpretable, call);
      }
      if (Overloads.isTypeConversionFunction(call.function())) {
        return maybeOptimizeConstUnary(interpretable, call);
      }
    }
    return interpretable;
  }

  static Interpretable maybeOptimizeConstUnary(
      Interpretable interpretable, InterpretableCall call) {
    EvalConst folded = foldConstantUnary(call);
    return folded != null ? folded : interpretable;
  }

  static Interpretable maybeBuildListLiteral(Interpretable interpretable, EvalList list) {
    EvalConst folded = foldList(list);
    return folded != null ? folded : interpretable;
  }

  static Interpretable maybeBuildMapLiteral(Interpretable interpretable, EvalMap map) {
    EvalConst folded = foldMap(map);
    return folded != null ? folded : interpretable;
  }

  static EvalConst foldConstantUnary(InterpretableCall call) {
    var controller = OperationCheckpoints.currentController();
    controller.checkpoint(Phase.OPTIMIZE);
    Interpretable[] args = call.args();
    if (args.length != 1 || !(args[0] instanceof InterpretableConst)) {
      return null;
    }
    Val val;
    try (var ignored = controller.overridePhase(Phase.OPTIMIZE)) {
      val = call.eval(ActivationControls.controlled(emptyActivation(), controller));
    }
    throwErrorAsIllegalStateException(val);
    return new EvalConst(call.id(), val);
  }

  static EvalConst foldList(EvalList list) {
    var controller = OperationCheckpoints.currentController();
    for (Interpretable elem : list.elems) {
      controller.checkpoint(Phase.OPTIMIZE);
      if (!(elem instanceof InterpretableConst)) {
        return null;
      }
    }
    try (var ignored = controller.overridePhase(Phase.OPTIMIZE)) {
      return new EvalConst(
          list.id(), list.eval(ActivationControls.controlled(emptyActivation(), controller)));
    }
  }

  static EvalConst foldMap(EvalMap map) {
    var controller = OperationCheckpoints.currentController();
    for (int index = 0; index < map.keys.length; index++) {
      controller.checkpoint(Phase.OPTIMIZE);
      if (!(map.keys[index] instanceof InterpretableConst)
          || !(map.vals[index] instanceof InterpretableConst)) {
        return null;
      }
    }
    try (var ignored = controller.overridePhase(Phase.OPTIMIZE)) {
      return new EvalConst(
          map.id(), map.eval(ActivationControls.controlled(emptyActivation(), controller)));
    }
  }

  /** Converts {@code in} over a homogeneous primitive constant list to constant-set membership. */
  static Interpretable maybeOptimizeSetMembership(
      Interpretable interpretable, InterpretableCall inList) {
    ConstantSet constantSet = constantSet(inList);
    if (constantSet == null) {
      return interpretable;
    }
    Interpretable[] args = inList.args();
    return new EvalSetMembership(inList, args[0], constantSet.typeName(), constantSet.values());
  }

  static ConstantSet constantSet(InterpretableCall inList) {
    Interpretable[] args = inList.args();
    if (args.length != 2) {
      return null;
    }
    return constantSet(args[1]);
  }

  static ConstantSet constantSet(Interpretable rhs) {
    if (!(rhs instanceof InterpretableConst listConstant)) {
      return null;
    }
    // An InList call always has a Lister right operand.
    Lister list = (Lister) listConstant.value();
    if (list.size() == IntZero) {
      return new ConstantSet(null, Set.of());
    }
    IteratorT iterator = list.iterator();
    Type type = null;
    Set<Val> valueSet = new HashSet<>();
    while (iterator.hasNext() == True) {
      Val element = iterator.next();
      if (!Util.isPrimitiveType(element)) {
        return null;
      }
      if (type == null) {
        type = element.type();
      } else if (!type.typeName().equals(element.type().typeName())) {
        return null;
      }
      valueSet.add(element);
    }
    if (type == null) {
      // A custom Lister can report a non-zero size while yielding no elements.
      return null;
    }
    return new ConstantSet(type.typeName(), valueSet);
  }

  record ConstantSet(String typeName, Set<Val> values) {}
}
