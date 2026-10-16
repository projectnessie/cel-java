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

import static java.util.Objects.requireNonNull;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import java.util.Iterator;
import java.util.Map;
import org.projectnessie.cel.OperationAbortedException.Phase;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.ref.Val;

/** CEL map-value equality shared by direct exact-map equality consumers. */
final class NativeMapValueEquality {
  private NativeMapValueEquality() {}

  static Val equal(Val value, Val otherValue) {
    if (isError(value)) {
      return value;
    }
    if (isError(otherValue)) {
      return otherValue;
    }

    Val equal = value.equal(otherValue);
    if (equal == True || equal == False) {
      return equal;
    }
    if (!value.type().equals(otherValue.type())) {
      return False;
    }
    equal = value.equal(otherValue);
    return equal instanceof Err ? equal : boolOf(equal == True);
  }
}

/** Immutable exact/exact map equality plan; all resolved state remains evaluation-local. */
final class NativeExactMapEqualityPlan {
  private final NativeMapSourceCapability leftSource;
  private final NativeMapSourceCapability rightSource;
  private final CheckedValueMaterializer keyMaterializer;
  private final CheckedValueMaterializer valueMaterializer;

  NativeExactMapEqualityPlan(
      NativeMapSourceCapability leftSource,
      NativeMapSourceCapability rightSource,
      CheckedValueMaterializer keyMaterializer,
      CheckedValueMaterializer valueMaterializer) {
    this.leftSource = requireNonNull(leftSource, "leftSource");
    this.rightSource = requireNonNull(rightSource, "rightSource");
    this.keyMaterializer = requireNonNull(keyMaterializer, "keyMaterializer");
    this.valueMaterializer = requireNonNull(valueMaterializer, "valueMaterializer");
  }

  boolean eval(Activation activation) {
    var controller = ActivationControls.controller(activation);
    ResolvedOperand left = resolve(leftSource, activation);
    ResolvedOperand right = resolve(rightSource, activation);
    if (left.failure != null) {
      throw signal(left.failure);
    }
    if (right.failure != null) {
      throw signal(right.failure);
    }

    SizedOperand leftSized = size(left.map);
    SizedOperand rightSized = size(right.map);
    if (leftSized.failure != null) {
      throw signal(leftSized.failure);
    }
    if (rightSized.failure != null) {
      throw signal(rightSized.failure);
    }
    if (leftSized.size != rightSized.size) {
      return false;
    }

    try {
      Iterator<? extends Map.Entry<?, ?>> iterator = left.map.entrySet().iterator();
      while (iterator.hasNext()) {
        controller.checkpoint(Phase.EVALUATE);
        Map.Entry<?, ?> entry = iterator.next();
        Object rawKey = entry.getKey();
        Val key = keyMaterializer.materialize(rawKey);
        if (isError(key) || isUnknown(key)) {
          throw signal(key);
        }
        Object rightRaw = NativeMapSources.exactLookup(right.map, rawKey, key);
        if (rightRaw == NativeMapSources.ABSENT) {
          return false;
        }
        Val leftValue = valueMaterializer.materialize(entry.getValue());
        Val rightValue = valueMaterializer.materialize(rightRaw);
        Val equal = NativeMapValueEquality.equal(leftValue, rightValue);
        if (equal != True) {
          return NativeScalarContinuations.booleanResult(equal);
        }
      }
      return true;
    } catch (ValueSignal failure) {
      throw failure;
    } catch (Exception failure) {
      if (failure instanceof org.projectnessie.cel.OperationAbortedException aborted) {
        throw aborted;
      }
      throw signal(newErr(failure, failure.toString()));
    }
  }

  private static ResolvedOperand resolve(NativeMapSourceCapability source, Activation activation) {
    Object raw;
    try {
      raw = source.evalRaw(activation);
    } catch (ValueSignal failure) {
      return new ResolvedOperand(null, failure.value);
    } catch (Exception failure) {
      if (failure instanceof org.projectnessie.cel.OperationAbortedException aborted) {
        throw aborted;
      }
      return new ResolvedOperand(null, newErr(failure, failure.toString()));
    }
    if (raw instanceof Val value && (isError(value) || isUnknown(value))) {
      return new ResolvedOperand(null, value);
    }
    if (raw instanceof Map<?, ?> map) {
      return new ResolvedOperand(map, null);
    }
    return new ResolvedOperand(
        null,
        newErr(
            "got '%s', expected certified exact map type",
            raw == null ? "null" : raw.getClass().getName()));
  }

  private static SizedOperand size(Map<?, ?> map) {
    try {
      return new SizedOperand(map.size(), null);
    } catch (Exception failure) {
      if (failure instanceof org.projectnessie.cel.OperationAbortedException aborted) {
        throw aborted;
      }
      return new SizedOperand(0, newErr(failure, failure.toString()));
    }
  }

  private record ResolvedOperand(Map<?, ?> map, Val failure) {}

  private record SizedOperand(int size, Val failure) {}
}

final class NativeExactMapEquality extends EvalEq implements NativeBooleanCapability {
  private final NativeExactMapEqualityPlan plan;

  NativeExactMapEquality(
      long id,
      Interpretable left,
      Interpretable right,
      CheckedValueMaterializer keyMaterializer,
      CheckedValueMaterializer valueMaterializer) {
    super(id, left, right);
    this.plan =
        new NativeExactMapEqualityPlan(
            (NativeMapSourceCapability) left,
            (NativeMapSourceCapability) right,
            keyMaterializer,
            valueMaterializer);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return plan.eval(activation);
  }
}

final class NativeExactMapInequality extends EvalNe implements NativeBooleanCapability {
  private final NativeExactMapEqualityPlan plan;

  NativeExactMapInequality(
      long id,
      Interpretable left,
      Interpretable right,
      CheckedValueMaterializer keyMaterializer,
      CheckedValueMaterializer valueMaterializer) {
    super(id, left, right);
    this.plan =
        new NativeExactMapEqualityPlan(
            (NativeMapSourceCapability) left,
            (NativeMapSourceCapability) right,
            keyMaterializer,
            valueMaterializer);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return !plan.eval(activation);
  }
}
