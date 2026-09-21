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
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import java.util.Iterator;
import java.util.Map;
import org.projectnessie.cel.OperationAbortedException.Phase;

/** Immutable plan for one directly traversed certified exact map source. */
final class NativeMapTraversalPlan {
  private final NativeMapSourceCapability source;
  private final NativeScalarKind keyKind;
  private final NativeScalarKind valueKind;
  private final CheckedValueMaterializer keyMaterializer;
  private final CheckedValueMaterializer valueMaterializer;

  NativeMapTraversalPlan(
      NativeMapSourceCapability source,
      NativeScalarKind keyKind,
      NativeScalarKind valueKind,
      CheckedValueMaterializer keyMaterializer,
      CheckedValueMaterializer valueMaterializer) {
    this.source = requireNonNull(source, "source");
    this.keyKind = requireNonNull(keyKind, "keyKind");
    this.valueKind = valueKind;
    this.keyMaterializer = requireNonNull(keyMaterializer, "keyMaterializer");
    this.valueMaterializer = valueMaterializer;
  }

  NativeResolvedMapTraversal resolve(Activation activation) {
    Object raw;
    try {
      raw = source.evalRaw(activation);
    } catch (ValueSignal failure) {
      raw = failure.value;
    } catch (Exception failure) {
      if (failure instanceof org.projectnessie.cel.OperationAbortedException aborted) {
        throw aborted;
      }
      raw = newErr(failure, failure.toString());
    }
    return new NativeResolvedMapTraversal(
        NativeMapSources.strictExactMap(raw),
        keyKind,
        valueKind,
        keyMaterializer,
        valueMaterializer);
  }
}

/** Evaluation-local resolved map state. No cursor or entry escapes {@link #traverse}. */
final class NativeResolvedMapTraversal {
  private final Map<?, ?> map;
  private final NativeScalarKind keyKind;
  private final NativeScalarKind valueKind;
  private final CheckedValueMaterializer keyMaterializer;
  private final CheckedValueMaterializer valueMaterializer;

  NativeResolvedMapTraversal(
      Map<?, ?> map,
      NativeScalarKind keyKind,
      NativeScalarKind valueKind,
      CheckedValueMaterializer keyMaterializer,
      CheckedValueMaterializer valueMaterializer) {
    this.map = requireNonNull(map, "map");
    this.keyKind = requireNonNull(keyKind, "keyKind");
    this.valueKind = valueKind;
    this.keyMaterializer = requireNonNull(keyMaterializer, "keyMaterializer");
    this.valueMaterializer = valueMaterializer;
  }

  boolean traverse(
      NativeLoopBinding keyBinding,
      NativeLoopBinding valueBinding,
      NativeMapLoopConsumer consumer) {
    try {
      var controller = ActivationControls.controller(keyBinding);
      Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
      while (iterator.hasNext()) {
        controller.checkpoint(Phase.EVALUATE);
        Map.Entry<?, ?> entry = iterator.next();
        Object rawKey = entry.getKey();
        NativeLoopBinding predicateBinding = keyBinding;
        if (valueBinding != null) {
          Object rawValue = entry.getValue();
          setKey(keyBinding, rawKey);
          valueBinding.setCheckedObject(rawValue, valueKind, valueMaterializer);
          predicateBinding = valueBinding;
        } else {
          setKey(keyBinding, rawKey);
        }
        if (consumer.test(predicateBinding)) {
          return true;
        }
      }
      return false;
    } catch (ValueSignal failure) {
      throw failure;
    } catch (Exception failure) {
      if (failure instanceof org.projectnessie.cel.OperationAbortedException aborted) {
        throw aborted;
      }
      throw signal(newErr(failure, failure.toString()));
    }
  }

  private void setKey(NativeLoopBinding binding, Object rawKey) {
    switch (keyKind) {
      case BOOLEAN -> binding.setObject(keyMaterializer.booleanValue(rawKey));
      case INT -> binding.setInt(keyMaterializer.intValue(rawKey));
      case UINT -> binding.setUint(keyMaterializer.uintValue(rawKey));
      case STRING -> binding.setString(keyMaterializer.stringValue(rawKey));
      case DOUBLE, NULL -> throw new IllegalStateException("unsupported exact map key " + keyKind);
    }
  }
}

interface NativeMapLoopConsumer {
  boolean test(NativeLoopBinding binding);
}

/** Canonical CEL map quantifier over a certified exact raw map. */
final class NativeMapQuantifierFold extends EvalFold
    implements NativeBooleanCapability, NativeMapLoopConsumer {
  private final NativeMapTraversalPlan traversal;
  private final NativeBooleanCapability predicate;
  private final NativeQuantifier quantifier;
  private final boolean existsOne;
  private final String keyVariable;
  private final String valueVariable;

  NativeMapQuantifierFold(
      long id,
      String accumulator,
      Interpretable accumulatorInitial,
      String keyVariable,
      String valueVariable,
      Interpretable range,
      NativeMapTraversalPlan traversal,
      Interpretable condition,
      Interpretable step,
      Interpretable result,
      NativeBooleanCapability predicate,
      NativeQuantifier quantifier,
      boolean existsOne) {
    super(
        id,
        accumulator,
        accumulatorInitial,
        keyVariable,
        valueVariable,
        range,
        condition,
        step,
        result);
    this.traversal = requireNonNull(traversal, "traversal");
    this.predicate = requireNonNull(predicate, "predicate");
    this.quantifier = quantifier;
    this.existsOne = existsOne;
    this.keyVariable = requireNonNull(keyVariable, "keyVariable");
    this.valueVariable = requireNonNull(valueVariable, "valueVariable");
    if (existsOne == (quantifier != null)) {
      throw new IllegalArgumentException("exactly one quantifier mode is required");
    }
  }

  @Override
  public boolean test(NativeLoopBinding binding) {
    if (existsOne) {
      try {
        binding.record(predicate.evalBoolean(binding));
      } catch (ValueSignal failure) {
        binding.record(failure.value);
      } catch (RuntimeException failure) {
        if (failure instanceof org.projectnessie.cel.OperationAbortedException aborted) {
          throw aborted;
        }
        binding.record(failure);
      }
      return false;
    }
    try {
      return predicate.evalBoolean(binding) == quantifier.shortCircuitValue;
    } catch (ValueSignal failure) {
      binding.record(failure.value, quantifier);
      return false;
    }
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    NativeLoopBinding keyBinding = new NativeLoopBinding(activation, keyVariable);
    NativeLoopBinding valueBinding =
        valueVariable.isEmpty() ? null : new NativeLoopBinding(keyBinding, valueVariable);
    boolean shortCircuited = traversal.resolve(activation).traverse(keyBinding, valueBinding, this);
    NativeLoopBinding resultBinding = valueBinding != null ? valueBinding : keyBinding;
    if (existsOne) {
      return resultBinding.finishExistsOne();
    }
    return shortCircuited ? quantifier.shortCircuitValue : resultBinding.finish(quantifier);
  }
}
