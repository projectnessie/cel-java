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
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;

import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.ref.Val;

class EvalOr extends AbstractEvalLhsRhs {
  // TODO combine with EvalExhaustiveOr
  EvalOr(long id, Interpretable lhs, Interpretable rhs) {
    super(id, lhs, rhs);
  }

  @SuppressWarnings("DuplicatedCode")
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
    // return if both sides are bool false.
    if (lVal == False && rVal == False) {
      return False;
    }
    // TODO: return both values as a set if both are unknown or error.
    // prefer left unknown to right unknown.
    if (isUnknown(lVal)) {
      return lVal;
    }
    if (isUnknown(rVal)) {
      return rVal;
    }
    // If the left-hand side is non-boolean return it as the error.
    if (isError(lVal)) {
      return lVal;
    }
    return noSuchOverload(lVal, Operator.LogicalOr.id, rVal);
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
    // return if both sides are bool true.
    if (lVal == True && rVal == True) {
      return True;
    }
    // TODO: return both values as a set if both are unknown or error.
    // prefer left unknown to right unknown.
    if (isUnknown(lVal)) {
      return lVal;
    }
    if (isUnknown(rVal)) {
      return rVal;
    }
    // If the left-hand side is non-boolean return it as the error.
    if (isError(lVal)) {
      return lVal;
    }
    return noSuchOverload(lVal, Operator.LogicalAnd.id, rVal);
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
