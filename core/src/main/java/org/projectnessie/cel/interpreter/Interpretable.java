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

import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.Coster.costOf;

import java.util.Arrays;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Receiver;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.AttributeFactory.Qualifier;
import org.projectnessie.cel.interpreter.Coster.Cost;

/**
 * One executable node in a low-level CEL evaluation plan.
 *
 * <p>Evaluating a node against an {@link Activation} returns a CEL {@link Val}. Evaluation-state
 * recording is added separately through an {@link InterpretableDecorator}; it is not returned by
 * this interface.
 */
public interface Interpretable {
  /** Returns the source expression identifier represented by this node. */
  long id();

  /** Evaluates this node against an activation. */
  Val eval(Activation activation);

  /** Executable node that always returns one constant CEL value. */
  interface InterpretableConst extends Interpretable {
    /** Returns the constant value. */
    Val value();
  }

  /** Executable node backed by an activation attribute and optional qualifier path. */
  interface InterpretableAttribute extends Interpretable, Qualifier, Attribute {
    /** Returns the underlying attribute. */
    Attribute attr();

    /** Returns the adapter used for resolved attribute values. */
    TypeAdapter adapter();

    /**
     * Adds a qualifier to the underlying attribute.
     *
     * <p>Note, this method may mutate the current attribute state. If the desire is to clone the
     * Attribute, the Attribute should first be copied before adding the qualifier. Attributes are
     * not copyable by default, so this is a capable that would need to be added to the
     * AttributeFactory or specifically to the underlying Attribute implementation.
     */
    @Override
    Attribute addQualifier(Qualifier qualifier);

    /**
     * Qualify replicates the Attribute.Qualify method to permit extension and interception of
     * object qualification.
     */
    @Override
    Object qualify(Activation vars, Object obj);

    /** Resolves the attribute against the activation. */
    @Override
    Object resolve(Activation act);
  }

  /** Executable function-call node exposed for planning decorators and advanced inspection. */
  interface InterpretableCall extends Interpretable {

    /** Returns the source function name or internal operator identifier. */
    String function();

    /** Returns the overload identifier selected by checking or planning. */
    String overloadID();

    /**
     * Returns normalized arguments to the function overload. For receiver-style functions, the
     * receiver target is arg 0.
     */
    Interpretable[] args();
  }

  /** Creates an executable constant node for an expression identifier. */
  static InterpretableConst newConstValue(long id, Val val) {
    return new EvalConst(id, val);
  }

  static Cost calShortCircuitBinaryOpsCost(Interpretable lhs, Interpretable rhs) {
    Cost l = estimateCost(lhs);
    Cost r = estimateCost(rhs);
    return costOf(l.min, l.max + r.max + 1);
  }

  static Cost sumOfCost(Interpretable[] interps) {
    long min = 0L;
    long max = 0L;
    for (Interpretable in : interps) {
      Cost t = estimateCost(in);
      min += t.min;
      max += t.max;
    }
    return costOf(min, max);
  }

  static Val receiveVarArgs(Receiver receiver, String function, String overload, Val[] argVals) {
    return switch (argVals.length) {
      case 1 -> receiver.receive(function, overload);
      case 2 -> receiver.receive(function, overload, argVals[1]);
      case 3 -> receiver.receive(function, overload, argVals[1], argVals[2]);
      case 4 -> receiver.receive(function, overload, argVals[1], argVals[2], argVals[3]);
      default ->
          receiver.receive(function, overload, Arrays.copyOfRange(argVals, 1, argVals.length));
    };
  }

  static Cost calExhaustiveBinaryOpsCost(Interpretable lhs, Interpretable rhs) {
    Cost l = estimateCost(lhs);
    Cost r = estimateCost(rhs);
    return Cost.OneOne.add(l).add(r);
  }
}
