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
 * Interpretable can accept a given Activation and produce a value along with an accompanying
 * EvalState which can be used to inspect whether additional data might be necessary to complete the
 * evaluation.
 */
public interface Interpretable {
  /** ID value corresponding to the expression node. */
  long id();

  /** Eval an Activation to produce an output. */
  Val eval(Activation activation);

  /** InterpretableConst interface for tracking whether the Interpretable is a constant value. */
  interface InterpretableConst extends Interpretable {
    /** Value returns the constant value of the instruction. */
    Val value();
  }

  /** InterpretableAttribute interface for tracking whether the Interpretable is an attribute. */
  interface InterpretableAttribute extends Interpretable, Qualifier, Attribute {
    /** Attr returns the Attribute value. */
    Attribute attr();

    /** Adapter returns the type adapter to be used for adapting resolved Attribute values. */
    TypeAdapter adapter();

    /**
     * AddQualifier proxies the Attribute.AddQualifier method.
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

    /** Resolve returns the value of the Attribute given the current Activation. */
    @Override
    Object resolve(Activation act);
  }

  /**
   * InterpretableCall interface for inspecting Interpretable instructions related to function
   * calls.
   */
  interface InterpretableCall extends Interpretable {

    /**
     * Function returns the function name as it appears in text or mangled operator name as it
     * appears in the operators.go file.
     */
    String function();

    /**
     * OverloadID returns the overload id associated with the function specialization. Overload ids
     * are stable across language boundaries and can be treated as synonymous with a unique function
     * signature.
     */
    String overloadID();

    /**
     * Args returns the normalized arguments to the function overload. For receiver-style functions,
     * the receiver target is arg 0.
     */
    Interpretable[] args();
  }

  /** NewConstValue creates a new constant valued Interpretable. */
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
