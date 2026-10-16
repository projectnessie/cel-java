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
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import java.util.Collection;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

interface NativeObjectLoopConsumer {
  boolean test(NativeLoopBinding binding);
}

/** Evaluation-independent traversal plan for one certified exact {@code list<message>} source. */
record NativeObjectListTraversal(
    NativeListSourceCapability source, CheckedValueMaterializer materializer) {
  NativeObjectListTraversal {
    requireNonNull(source, "source");
    requireNonNull(materializer, "materializer");
  }

  boolean traverse(
      Activation activation, NativeLoopBinding binding, NativeObjectLoopConsumer consumer) {
    Object raw;
    try {
      raw = source.evalRaw(activation);
    } catch (ValueSignal failure) {
      raw = failure.value;
    } catch (Exception failure) {
      throw signal(newErr(failure, failure.toString()));
    }
    if (raw instanceof Val value && (isError(value) || isUnknown(value))) {
      throw signal(value);
    }

    try {
      if (raw instanceof Object[] values && !(raw instanceof Val[])) {
        for (Object value : values) {
          binding.setExactObject(value, materializer);
          if (consumer.test(binding)) {
            return true;
          }
        }
        return false;
      }
      if (raw instanceof Collection<?> values) {
        for (Object value : values) {
          binding.setExactObject(value, materializer);
          if (consumer.test(binding)) {
            return true;
          }
        }
        return false;
      }

      Val materialized = source.materializeResolvedList(raw);
      if (isError(materialized) || isUnknown(materialized)) {
        throw signal(materialized);
      }
      throw signal(
          newErr(
              "exact object-list value of Java type '%s' has an unsupported representation",
              raw == null ? "null" : raw.getClass().getName()));
    } catch (ValueSignal failure) {
      throw failure;
    } catch (Exception failure) {
      throw signal(newErr(failure, failure.toString()));
    }
  }
}

abstract class NativeObjectLocalNode extends AbstractEval implements Coster {
  final String variable;
  final TypeAdapter adapter;
  final FieldType fieldType;
  private final Interpretable established;

  NativeObjectLocalNode(
      long id,
      String variable,
      TypeAdapter adapter,
      FieldType fieldType,
      Interpretable established) {
    super(id);
    this.variable = requireNonNull(variable, "variable");
    this.adapter = requireNonNull(adapter, "adapter");
    this.fieldType = requireNonNull(fieldType, "fieldType");
    this.established = requireNonNull(established, "established");
  }

  final NativeLoopBinding exactBinding(Activation activation) {
    return NativeLoopBinding.findExactObject(activation, variable);
  }

  final Val evalEstablished(Activation activation) {
    return established.eval(activation);
  }

  @Override
  public final Val eval(Activation activation) {
    return evalEstablished(activation);
  }

  @Override
  public final Cost cost() {
    return estimateCost(established);
  }
}

final class NativeStringObjectField extends NativeObjectLocalNode
    implements NativeStringCapability {
  NativeStringObjectField(
      long id,
      String variable,
      TypeAdapter adapter,
      FieldType fieldType,
      Interpretable established) {
    super(id, variable, adapter, fieldType, established);
  }

  @Override
  public String evalString(Activation activation) {
    NativeLoopBinding binding = exactBinding(activation);
    if (binding == null) {
      return NativeSupport.stringValue(adapter, evalEstablished(activation));
    }
    Object target = binding.exactObjectValue();
    try {
      return NativeSupport.stringValue(adapter, fieldType.getFrom.getFrom(target));
    } catch (ValueSignal failure) {
      throw failure;
    } catch (Exception failure) {
      throw signal(newErr(failure, failure.toString()));
    }
  }
}

final class NativeObjectFieldPresence extends NativeObjectLocalNode
    implements NativeBooleanCapability {
  NativeObjectFieldPresence(
      long id,
      String variable,
      TypeAdapter adapter,
      FieldType fieldType,
      Interpretable established) {
    super(id, variable, adapter, fieldType, established);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    NativeLoopBinding binding = exactBinding(activation);
    if (binding == null) {
      return NativeSupport.booleanValue(adapter, evalEstablished(activation));
    }
    Object target = binding.exactObjectValue();
    try {
      return fieldType.isSet.isSet(target);
    } catch (ValueSignal failure) {
      throw failure;
    } catch (Exception failure) {
      throw signal(newErr(failure, failure.toString()));
    }
  }
}

abstract class NativeObjectLoopFold extends EvalFold
    implements NativeBooleanCapability, NativeObjectLoopConsumer {
  private final NativeObjectListTraversal traversal;
  final NativeBooleanCapability predicate;
  final TypeAdapter adapter;
  final String variable;

  NativeObjectLoopFold(
      long id,
      String accumulator,
      Interpretable accumulatorInitial,
      String variable,
      Interpretable range,
      NativeObjectListTraversal traversal,
      Interpretable condition,
      Interpretable step,
      Interpretable result,
      NativeBooleanCapability predicate,
      TypeAdapter adapter) {
    super(id, accumulator, accumulatorInitial, variable, "", range, condition, step, result);
    this.traversal = requireNonNull(traversal, "traversal");
    this.predicate = requireNonNull(predicate, "predicate");
    this.adapter = requireNonNull(adapter, "adapter");
    this.variable = requireNonNull(variable, "variable");
  }

  final boolean useEstablished(Activation activation) {
    return NativeLoopBinding.hasPartialActivation(activation);
  }

  final boolean evalEstablishedBoolean(Activation activation) {
    return NativeSupport.booleanValue(adapter, super.eval(activation));
  }

  final boolean evaluate(Activation activation, NativeLoopBinding binding) {
    return traversal.traverse(activation, binding, this);
  }
}

final class NativeObjectAllFold extends NativeObjectLoopFold {
  NativeObjectAllFold(
      long id,
      String accumulator,
      Interpretable accumulatorInitial,
      String variable,
      Interpretable range,
      NativeObjectListTraversal traversal,
      Interpretable condition,
      Interpretable step,
      Interpretable result,
      NativeBooleanCapability predicate,
      TypeAdapter adapter) {
    super(
        id,
        accumulator,
        accumulatorInitial,
        variable,
        range,
        traversal,
        condition,
        step,
        result,
        predicate,
        adapter);
  }

  @Override
  public boolean test(NativeLoopBinding binding) {
    try {
      return !predicate.evalBoolean(binding);
    } catch (ValueSignal failure) {
      binding.record(failure.value, NativeQuantifier.ALL);
      return false;
    }
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    if (useEstablished(activation)) {
      return evalEstablishedBoolean(activation);
    }
    NativeLoopBinding binding = new NativeLoopBinding(activation, variable);
    if (evaluate(activation, binding)) {
      return false;
    }
    return binding.finish(NativeQuantifier.ALL);
  }
}

final class NativeObjectExistsOneFold extends NativeObjectLoopFold {
  NativeObjectExistsOneFold(
      long id,
      String accumulator,
      Interpretable accumulatorInitial,
      String variable,
      Interpretable range,
      NativeObjectListTraversal traversal,
      Interpretable condition,
      Interpretable step,
      Interpretable result,
      NativeBooleanCapability predicate,
      TypeAdapter adapter) {
    super(
        id,
        accumulator,
        accumulatorInitial,
        variable,
        range,
        traversal,
        condition,
        step,
        result,
        predicate,
        adapter);
  }

  @Override
  public boolean test(NativeLoopBinding binding) {
    try {
      binding.record(predicate.evalBoolean(binding));
    } catch (ValueSignal failure) {
      binding.record(failure.value);
    } catch (RuntimeException failure) {
      binding.record(failure);
    }
    return false;
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    if (useEstablished(activation)) {
      return evalEstablishedBoolean(activation);
    }
    NativeLoopBinding binding = new NativeLoopBinding(activation, variable);
    evaluate(activation, binding);
    return binding.finishExistsOne();
  }
}
