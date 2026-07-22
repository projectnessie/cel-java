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
import static org.projectnessie.cel.common.operators.Operator.LogicalAnd;
import static org.projectnessie.cel.common.operators.Operator.LogicalOr;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.IterableT;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Sizer;
import org.projectnessie.cel.common.types.traits.Trait;

enum NativeQuantifier {
  ALL(true, false),
  EXISTS(false, true);

  final boolean initialValue;
  final boolean shortCircuitValue;

  NativeQuantifier(boolean initialValue, boolean shortCircuitValue) {
    this.initialValue = initialValue;
    this.shortCircuitValue = shortCircuitValue;
  }
}

interface NativeScalarLoopConsumer {
  default void prepareCapacity(int capacity) {}

  boolean test(NativeLoopBinding binding);
}

final class NativeScalarLoopKernel {
  private NativeScalarLoopKernel() {}

  static boolean evaluate(
      NativeListSourceCapability range,
      NativeScalarKind elementKind,
      Activation activation,
      NativeLoopBinding binding,
      NativeScalarLoopConsumer consumer) {
    Object target;
    try {
      target = range.evalRaw(activation);
    } catch (ValueSignal valueSignal) {
      target = valueSignal.value;
    }

    return NativeListSources.traverseResolved(range, target, elementKind, binding, consumer);
  }

  static boolean evaluateMaterialized(
      Val foldRange, NativeLoopBinding binding, NativeScalarLoopConsumer consumer) {
    if (isError(foldRange) || isUnknown(foldRange)) {
      throw signal(foldRange);
    }
    if (!foldRange.type().hasTrait(Trait.IterableType)) {
      throw signal(newErr("got '%s', expected iterable type", foldRange.getClass().getName()));
    }
    if (foldRange instanceof Sizer sizer) {
      consumer.prepareCapacity(sizer.nativeSize());
    }
    IteratorT iterator = ((IterableT) foldRange).iterator();
    while (iterator.hasNext() == True) {
      binding.setObject(iterator.next());
      if (consumer.test(binding)) {
        return true;
      }
    }
    return false;
  }
}

abstract class NativeScalarLoopFold extends EvalFold
    implements NativeBooleanCapability, NativeScalarLoopConsumer {
  private final NativeListSourceCapability range;
  private final NativeScalarKind elementKind;
  final NativeBooleanCapability predicate;
  final TypeAdapter adapter;
  final String variable;

  NativeScalarLoopFold(
      long id,
      String accumulator,
      Interpretable accumulatorInitial,
      String variable,
      Interpretable range,
      Interpretable condition,
      Interpretable step,
      Interpretable result,
      NativeScalarKind elementKind,
      NativeBooleanCapability predicate,
      TypeAdapter adapter) {
    super(id, accumulator, accumulatorInitial, variable, "", range, condition, step, result);
    this.range = (NativeListSourceCapability) range;
    this.elementKind = requireNonNull(elementKind, "elementKind");
    this.predicate = requireNonNull(predicate, "predicate");
    this.adapter = requireNonNull(adapter, "adapter");
    this.variable = requireNonNull(variable, "variable");
  }

  final boolean evaluate(Activation activation, NativeLoopBinding binding) {
    return NativeScalarLoopKernel.evaluate(range, elementKind, activation, binding, this);
  }

  @Override
  public abstract boolean test(NativeLoopBinding binding);
}

final class NativeQuantifierFold extends NativeScalarLoopFold {
  private final NativeQuantifier quantifier;

  NativeQuantifierFold(
      long id,
      String accumulator,
      Interpretable accumulatorInitial,
      String variable,
      Interpretable range,
      Interpretable condition,
      Interpretable step,
      Interpretable result,
      NativeScalarKind elementKind,
      NativeBooleanCapability predicate,
      NativeQuantifier quantifier,
      TypeAdapter adapter) {
    super(
        id,
        accumulator,
        accumulatorInitial,
        variable,
        range,
        condition,
        step,
        result,
        elementKind,
        predicate,
        adapter);
    this.quantifier = requireNonNull(quantifier, "quantifier");
  }

  @Override
  public boolean test(NativeLoopBinding binding) {
    try {
      return predicate.evalBoolean(binding) == quantifier.shortCircuitValue;
    } catch (ValueSignal valueSignal) {
      binding.record(valueSignal.value, quantifier);
      return false;
    }
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    NativeLoopBinding binding = new NativeLoopBinding(activation, variable);
    if (evaluate(activation, binding)) {
      return quantifier.shortCircuitValue;
    }
    return binding.finish(quantifier);
  }
}

final class NativeExistsOneFold extends NativeScalarLoopFold {
  NativeExistsOneFold(
      long id,
      String accumulator,
      Interpretable accumulatorInitial,
      String variable,
      Interpretable range,
      Interpretable condition,
      Interpretable step,
      Interpretable result,
      NativeScalarKind elementKind,
      NativeBooleanCapability predicate,
      TypeAdapter adapter) {
    super(
        id,
        accumulator,
        accumulatorInitial,
        variable,
        range,
        condition,
        step,
        result,
        elementKind,
        predicate,
        adapter);
  }

  @Override
  public boolean test(NativeLoopBinding binding) {
    try {
      binding.record(predicate.evalBoolean(binding));
    } catch (ValueSignal valueSignal) {
      binding.record(valueSignal.value);
    } catch (RuntimeException failure) {
      binding.record(failure);
    }
    return false;
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    NativeLoopBinding binding = new NativeLoopBinding(activation, variable);
    evaluate(activation, binding);
    return binding.finishExistsOne();
  }
}

abstract class NativeIntMappedLoopFold extends EvalFold implements NativeBooleanCapability {
  final NativeScalarListFoldCapability source;
  final NativeBooleanCapability predicate;
  final String variable;

  NativeIntMappedLoopFold(
      long id,
      String accumulator,
      Interpretable accumulatorInitial,
      String variable,
      Interpretable range,
      Interpretable condition,
      Interpretable step,
      Interpretable result,
      NativeBooleanCapability predicate) {
    super(id, accumulator, accumulatorInitial, variable, "", range, condition, step, result);
    this.source = (NativeScalarListFoldCapability) range;
    this.predicate = requireNonNull(predicate, "predicate");
    this.variable = requireNonNull(variable, "variable");
  }

  final NativeIntAggregateValues evaluateSource(Activation activation) {
    return source.evalIntValues(activation);
  }
}

final class NativeIntMappedQuantifierFold extends NativeIntMappedLoopFold {
  private final NativeQuantifier quantifier;

  NativeIntMappedQuantifierFold(
      long id,
      String accumulator,
      Interpretable accumulatorInitial,
      String variable,
      Interpretable range,
      Interpretable condition,
      Interpretable step,
      Interpretable result,
      NativeBooleanCapability predicate,
      NativeQuantifier quantifier) {
    super(id, accumulator, accumulatorInitial, variable, range, condition, step, result, predicate);
    this.quantifier = requireNonNull(quantifier, "quantifier");
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    NativeIntAggregateValues values = evaluateSource(activation);
    NativeLoopBinding binding = new NativeLoopBinding(activation, variable);
    for (int i = 0; i < values.size(); i++) {
      values.set(i, binding);
      try {
        if (predicate.evalBoolean(binding) == quantifier.shortCircuitValue) {
          return quantifier.shortCircuitValue;
        }
      } catch (ValueSignal valueSignal) {
        binding.record(valueSignal.value, quantifier);
      }
    }
    return binding.finish(quantifier);
  }
}

final class NativeIntMappedExistsOneFold extends NativeIntMappedLoopFold {
  NativeIntMappedExistsOneFold(
      long id,
      String accumulator,
      Interpretable accumulatorInitial,
      String variable,
      Interpretable range,
      Interpretable condition,
      Interpretable step,
      Interpretable result,
      NativeBooleanCapability predicate) {
    super(id, accumulator, accumulatorInitial, variable, range, condition, step, result, predicate);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    NativeIntAggregateValues values = evaluateSource(activation);
    NativeLoopBinding binding = new NativeLoopBinding(activation, variable);
    for (int i = 0; i < values.size(); i++) {
      values.set(i, binding);
      try {
        binding.record(predicate.evalBoolean(binding));
      } catch (ValueSignal valueSignal) {
        binding.record(valueSignal.value);
      } catch (RuntimeException failure) {
        binding.record(failure);
      }
    }
    return binding.finishExistsOne();
  }
}

class NativeLoopBinding implements Activation {
  private final Activation parent;
  private final String variable;
  private NativeScalarKind valueKind;
  private Object objectValue;
  private long intValue;
  private double doubleValue;
  private String stringValue;
  private Val pending;
  private long matches;

  NativeLoopBinding(Activation parent, String variable) {
    this.parent = requireNonNull(parent, "parent");
    this.variable = requireNonNull(variable, "variable");
  }

  boolean matches(String name) {
    return !name.startsWith(".") && variable.equals(name);
  }

  void setInt(long value) {
    valueKind = NativeScalarKind.INT;
    intValue = value;
    objectValue = null;
  }

  void setUint(long value) {
    valueKind = NativeScalarKind.UINT;
    intValue = value;
    objectValue = null;
  }

  void setDouble(double value) {
    valueKind = NativeScalarKind.DOUBLE;
    doubleValue = value;
    objectValue = null;
  }

  void setString(String value) {
    valueKind = NativeScalarKind.STRING;
    stringValue = value;
    objectValue = value;
  }

  void setObject(Object value) {
    valueKind = null;
    objectValue = value;
  }

  boolean booleanValue(TypeAdapter adapter) {
    return NativeSupport.booleanValue(adapter, objectValue);
  }

  long intValue(TypeAdapter adapter) {
    return valueKind == NativeScalarKind.INT
        ? intValue
        : NativeSupport.intValue(adapter, objectValue);
  }

  long uintValue(TypeAdapter adapter) {
    return valueKind == NativeScalarKind.UINT
        ? intValue
        : NativeSupport.uintValue(adapter, objectValue);
  }

  double doubleValue(TypeAdapter adapter) {
    return valueKind == NativeScalarKind.DOUBLE
        ? doubleValue
        : NativeSupport.doubleValue(adapter, objectValue);
  }

  String stringValue(TypeAdapter adapter) {
    if (valueKind != NativeScalarKind.STRING) {
      return NativeSupport.stringValue(adapter, objectValue);
    }
    if (stringValue == null) {
      throw signal(stringOf(null));
    }
    return stringValue;
  }

  void record(Val value, NativeQuantifier quantifier) {
    pending =
        logicalSlow(
            pending != null ? pending : boolOf(quantifier.initialValue),
            value,
            quantifier == NativeQuantifier.ALL);
  }

  boolean finish(NativeQuantifier quantifier) {
    if (pending != null) {
      throw signal(pending);
    }
    return quantifier.initialValue;
  }

  void record(boolean value) {
    if (value) {
      if (pending == null) {
        matches++;
      } else if (isError(pending)) {
        pending = wrapPropagatedError(pending).value;
      }
    }
  }

  void record(Val value) {
    pending = isError(value) ? wrapPropagatedError(value).value : value;
  }

  void record(RuntimeException failure) {
    pending = newErr(failure, failure.toString());
  }

  boolean finishExistsOne() {
    if (pending != null) {
      throw signal(pending);
    }
    return matches == 1;
  }

  @Override
  public Activation parent() {
    return parent;
  }

  @Override
  public Object resolve(String name) {
    if (matches(name)) {
      if (valueKind == null) {
        return objectValue;
      }
      return switch (valueKind) {
        case INT -> intValue;
        case UINT -> ULong.valueOf(intValue);
        case DOUBLE -> doubleValue;
        case STRING -> stringValue != null ? stringValue : stringOf(null);
        case BOOLEAN, NULL -> objectValue;
      };
    }
    return parent.resolve(name.startsWith(".") ? name.substring(1) : name);
  }

  @SuppressWarnings("removal")
  @Override
  public ResolvedValue resolveName(String name) {
    return ResolvedValue.mapTo(resolve(name));
  }

  @SuppressWarnings("DuplicatedCode")
  private static Val logicalSlow(Val left, Val right, boolean and) {
    Val shortCircuit = boolOf(!and);
    Val identity = boolOf(and);
    if (left == shortCircuit || right == shortCircuit) {
      return shortCircuit;
    }
    if (left == identity && right == identity) {
      return identity;
    }
    if (isUnknown(left)) {
      return left;
    }
    if (isUnknown(right)) {
      return right;
    }
    if (isError(left)) {
      return left;
    }
    return noSuchOverload(left, and ? LogicalAnd.id : LogicalOr.id, right);
  }

  private static ValueSignal wrapPropagatedError(Val value) {
    return NativeSupport.propagatedError(value);
  }
}

final class NativeBooleanLocalIdent extends EvalIdent implements NativeBooleanCapability {
  NativeBooleanLocalIdent(long id, String name, TypeAdapter adapter) {
    super(id, name, adapter);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return activation instanceof NativeLoopBinding binding
        ? binding.booleanValue(adapter)
        : NativeSupport.booleanValue(adapter, resolveRaw(activation));
  }
}

final class NativeIntLocalIdent extends EvalIdent implements NativeIntCapability {
  NativeIntLocalIdent(long id, String name, TypeAdapter adapter) {
    super(id, name, adapter);
  }

  @Override
  public long evalInt(Activation activation) {
    return activation instanceof NativeLoopBinding binding
        ? binding.intValue(adapter)
        : NativeSupport.intValue(adapter, resolveRaw(activation));
  }
}

final class NativeUintLocalIdent extends EvalIdent implements NativeUintCapability {
  NativeUintLocalIdent(long id, String name, TypeAdapter adapter) {
    super(id, name, adapter);
  }

  @Override
  public long evalUint(Activation activation) {
    return activation instanceof NativeLoopBinding binding
        ? binding.uintValue(adapter)
        : NativeSupport.uintValue(adapter, resolveRaw(activation));
  }
}

final class NativeDoubleLocalIdent extends EvalIdent implements NativeDoubleCapability {
  NativeDoubleLocalIdent(long id, String name, TypeAdapter adapter) {
    super(id, name, adapter);
  }

  @Override
  public double evalDouble(Activation activation) {
    return activation instanceof NativeLoopBinding binding
        ? binding.doubleValue(adapter)
        : NativeSupport.doubleValue(adapter, resolveRaw(activation));
  }
}

final class NativeStringLocalIdent extends EvalIdent implements NativeStringCapability {
  NativeStringLocalIdent(long id, String name, TypeAdapter adapter) {
    super(id, name, adapter);
  }

  @Override
  public String evalString(Activation activation) {
    return activation instanceof NativeLoopBinding binding
        ? binding.stringValue(adapter)
        : NativeSupport.stringValue(adapter, resolveRaw(activation));
  }
}
