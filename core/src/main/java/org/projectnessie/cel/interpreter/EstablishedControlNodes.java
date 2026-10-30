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

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;

import java.util.Objects;
import org.projectnessie.cel.common.types.OptionalT;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Receiver;
import org.projectnessie.cel.common.types.traits.Trait;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableCall;

class EvalOr extends AbstractEvalLhsRhs {
  // TODO combine with EvalExhaustiveOr
  EvalOr(long id, Interpretable lhs, Interpretable rhs) {
    super(id, lhs, rhs);
  }

  @Override
  public Val eval(Activation ctx) {
    // short-circuit lhs.
    Val lVal = lhs.eval(ctx);
    if (lVal == True) {
      return True;
    }
    // short-circuit on rhs.
    Val rVal = rhs.eval(ctx);
    if (rVal == True) {
      return True;
    }
    return LogicalValueSupport.combine(lVal, rVal, false);
  }

  @Override
  public Cost cost() {
    return Interpretable.calShortCircuitBinaryOpsCost(lhs, rhs);
  }

  @Override
  public String toString() {
    return "EvalOr{" + "id=" + id + ", lhs=" + lhs + ", rhs=" + rhs + '}';
  }
}

class EvalAnd extends AbstractEvalLhsRhs {
  // TODO combine with EvalExhaustiveAnd
  EvalAnd(long id, Interpretable lhs, Interpretable rhs) {
    super(id, lhs, rhs);
  }

  @Override
  public Val eval(Activation ctx) {
    // short-circuit lhs.
    Val lVal = lhs.eval(ctx);
    if (lVal == False) {
      return False;
    }
    // short-circuit on rhs.
    Val rVal = rhs.eval(ctx);
    if (rVal == False) {
      return False;
    }
    return LogicalValueSupport.combine(lVal, rVal, true);
  }

  @Override
  public Cost cost() {
    return Interpretable.calShortCircuitBinaryOpsCost(lhs, rhs);
  }

  @Override
  public String toString() {
    return "EvalAnd{" + "id=" + id + ", lhs=" + lhs + ", rhs=" + rhs + '}';
  }
}

class EvalOptionalOr extends AbstractEvalLhsRhs implements InterpretableCall {
  private final String function;
  private final String overload;
  private final boolean returnValue;

  EvalOptionalOr(
      long id,
      String function,
      String overload,
      Interpretable lhs,
      Interpretable rhs,
      boolean returnValue) {
    super(id, lhs, rhs);
    this.function = Objects.requireNonNull(function);
    this.overload = Objects.requireNonNull(overload);
    this.returnValue = returnValue;
  }

  @Override
  public Val eval(Activation ctx) {
    Val left = lhs.eval(ctx);
    if (isUnknownOrError(left)) {
      return left;
    }
    if (left instanceof OptionalT optional && optional.hasValue()) {
      return returnValue ? optional.getValue() : optional;
    }

    Val right = rhs.eval(ctx);
    if (isUnknownOrError(right)) {
      return right;
    }
    if (left.type().hasTrait(Trait.ReceiverType)) {
      return ((Receiver) left).receive(function, overload, right);
    }
    return noSuchOverload(left, function, overload, new Val[] {right});
  }

  @Override
  public Cost cost() {
    return Interpretable.calShortCircuitBinaryOpsCost(lhs, rhs);
  }

  @Override
  public String function() {
    return function;
  }

  @Override
  public String overloadID() {
    return overload;
  }

  @Override
  public Interpretable[] args() {
    return new Interpretable[] {lhs, rhs};
  }

  @Override
  public String toString() {
    return "EvalOptionalOr{"
        + "id="
        + id
        + ", lhs="
        + lhs
        + ", rhs="
        + rhs
        + ", function='"
        + function
        + '\''
        + ", overload='"
        + overload
        + '\''
        + ", returnValue="
        + returnValue
        + '}';
  }
}
