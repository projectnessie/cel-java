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

/**
 * Resolves a list-valued range separately from traversing its elements.
 *
 * <p>The separation lets strict binary consumers resolve both operands and apply CEL failure
 * precedence before any element is visited. A resolved traversal is immutable evaluation-local
 * state and must not replay its source expressions.
 */
interface NativeListTraversalPlan {
  NativeResolvedListTraversal resolve(Activation activation);

  int sourceCount();

  static NativeListTraversalPlan singleSource(NativeListSourceCapability source) {
    return new NativeSingleListTraversalPlan(source);
  }

  /**
   * Returns a traversal plan for every exact leaf in {@code concat}, or {@code null} when the tree
   * cannot be flattened to exact sources.
   */
  static NativeListTraversalPlan concat(NativeListConcat concat) {
    NativeListSourceCapability[] sources = NativeListConcatKernel.collectSources(concat);
    return sources != null ? new NativeConcatListTraversalPlan(sources) : null;
  }
}

interface NativeResolvedListTraversal {
  boolean traverse(
      NativeScalarKind elementKind, NativeLoopBinding binding, NativeScalarLoopConsumer consumer);
}

record NativeSingleListTraversalPlan(NativeListSourceCapability source)
    implements NativeListTraversalPlan {
  NativeSingleListTraversalPlan {
    requireNonNull(source, "source");
  }

  @Override
  public NativeResolvedListTraversal resolve(Activation activation) {
    Object raw;
    try {
      raw = source.evalRaw(activation);
    } catch (ValueSignal valueSignal) {
      raw = valueSignal.value;
    }
    return new NativeResolvedSingleListTraversal(source, raw);
  }

  @Override
  public int sourceCount() {
    return 1;
  }
}

record NativeResolvedSingleListTraversal(NativeListSourceCapability source, Object raw)
    implements NativeResolvedListTraversal {
  NativeResolvedSingleListTraversal {
    requireNonNull(source, "source");
  }

  @Override
  public boolean traverse(
      NativeScalarKind elementKind, NativeLoopBinding binding, NativeScalarLoopConsumer consumer) {
    return NativeListSources.traverseResolved(source, raw, elementKind, binding, consumer);
  }
}

record NativeConcatListTraversalPlan(NativeListSourceCapability[] sources)
    implements NativeListTraversalPlan {
  NativeConcatListTraversalPlan {
    requireNonNull(sources, "sources");
    if (sources.length < 2) {
      throw new IllegalArgumentException("concat traversal requires at least two sources");
    }
    sources = sources.clone();
  }

  @Override
  public NativeListSourceCapability[] sources() {
    return sources.clone();
  }

  @Override
  public NativeResolvedListTraversal resolve(Activation activation) {
    return NativeListConcatKernel.resolveTraversal(sources, activation);
  }

  @Override
  public int sourceCount() {
    return sources.length;
  }
}

final class NativeScalarLoopKernel {
  private NativeScalarLoopKernel() {}

  static boolean evaluate(
      NativeListTraversalPlan range,
      NativeScalarKind elementKind,
      Activation activation,
      NativeLoopBinding binding,
      NativeScalarLoopConsumer consumer) {
    return range.resolve(activation).traverse(elementKind, binding, consumer);
  }

  static boolean evaluateMaterialized(
      Val foldRange, NativeLoopBinding binding, NativeScalarLoopConsumer consumer) {
    return evaluateMaterialized(foldRange, binding, consumer, true);
  }

  static boolean evaluateMaterialized(
      Val foldRange,
      NativeLoopBinding binding,
      NativeScalarLoopConsumer consumer,
      boolean prepareCapacity) {
    if (isError(foldRange) || isUnknown(foldRange)) {
      throw signal(foldRange);
    }
    if (!foldRange.type().hasTrait(Trait.IterableType)) {
      throw signal(newErr("got '%s', expected iterable type", foldRange.getClass().getName()));
    }
    if (prepareCapacity && foldRange instanceof Sizer sizer) {
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
  private final NativeListTraversalPlan traversal;
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
      NativeListTraversalPlan traversal,
      Interpretable condition,
      Interpretable step,
      Interpretable result,
      NativeScalarKind elementKind,
      NativeBooleanCapability predicate,
      TypeAdapter adapter) {
    super(id, accumulator, accumulatorInitial, variable, "", range, condition, step, result);
    this.traversal = requireNonNull(traversal, "traversal");
    this.elementKind = requireNonNull(elementKind, "elementKind");
    this.predicate = requireNonNull(predicate, "predicate");
    this.adapter = requireNonNull(adapter, "adapter");
    this.variable = requireNonNull(variable, "variable");
  }

  final boolean evaluate(Activation activation, NativeLoopBinding binding) {
    return NativeScalarLoopKernel.evaluate(traversal, elementKind, activation, binding, this);
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
      NativeListTraversalPlan traversal,
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
        traversal,
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
      NativeListTraversalPlan traversal,
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
        traversal,
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
  private CheckedValueMaterializer checkedMaterializer;
  private Val materializedValue;
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

  static NativeLoopBinding find(Activation activation, String name) {
    Activation current = activation;
    while (current instanceof NativeLoopBinding binding) {
      if (binding.matches(name)) {
        return binding;
      }
      current = binding.parent;
    }
    return null;
  }

  void setInt(long value) {
    clearCheckedValue();
    valueKind = NativeScalarKind.INT;
    intValue = value;
    objectValue = null;
  }

  void setUint(long value) {
    clearCheckedValue();
    valueKind = NativeScalarKind.UINT;
    intValue = value;
    objectValue = null;
  }

  void setDouble(double value) {
    clearCheckedValue();
    valueKind = NativeScalarKind.DOUBLE;
    doubleValue = value;
    objectValue = null;
  }

  void setString(String value) {
    clearCheckedValue();
    valueKind = NativeScalarKind.STRING;
    stringValue = value;
    objectValue = value;
  }

  void setObject(Object value) {
    clearCheckedValue();
    valueKind = null;
    objectValue = value;
  }

  void setCheckedObject(
      Object value, NativeScalarKind kind, CheckedValueMaterializer materializer) {
    checkedMaterializer = requireNonNull(materializer, "materializer");
    materializedValue = null;
    valueKind = requireNonNull(kind, "kind");
    objectValue = value;
  }

  boolean booleanValue(TypeAdapter adapter) {
    if (checkedMaterializer != null) {
      return checkedMaterializer.booleanValue(objectValue);
    }
    return NativeSupport.booleanValue(adapter, objectValue);
  }

  long intValue(TypeAdapter adapter) {
    if (checkedMaterializer != null) {
      return checkedMaterializer.intValue(objectValue);
    }
    return valueKind == NativeScalarKind.INT
        ? intValue
        : NativeSupport.intValue(adapter, objectValue);
  }

  long uintValue(TypeAdapter adapter) {
    if (checkedMaterializer != null) {
      return checkedMaterializer.uintValue(objectValue);
    }
    return valueKind == NativeScalarKind.UINT
        ? intValue
        : NativeSupport.uintValue(adapter, objectValue);
  }

  double doubleValue(TypeAdapter adapter) {
    if (checkedMaterializer != null) {
      return checkedMaterializer.doubleValue(objectValue);
    }
    return valueKind == NativeScalarKind.DOUBLE
        ? doubleValue
        : NativeSupport.doubleValue(adapter, objectValue);
  }

  String stringValue(TypeAdapter adapter) {
    if (checkedMaterializer != null) {
      return checkedMaterializer.stringValue(objectValue);
    }
    if (valueKind != NativeScalarKind.STRING) {
      return NativeSupport.stringValue(adapter, objectValue);
    }
    if (stringValue == null) {
      throw signal(stringOf(null));
    }
    return stringValue;
  }

  void nullValue(TypeAdapter adapter) {
    if (checkedMaterializer != null) {
      checkedMaterializer.nullValue(objectValue);
    } else {
      NativeSupport.nullValue(adapter, objectValue);
    }
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
    if (name.startsWith(".")) {
      Activation root = parent;
      while (root instanceof NativeLoopBinding binding) {
        root = binding.parent;
      }
      return root.resolve(name.substring(1));
    }
    if (matches(name)) {
      if (checkedMaterializer != null) {
        if (materializedValue == null) {
          materializedValue = checkedMaterializer.materialize(objectValue);
        }
        return materializedValue;
      }
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
    return parent.resolve(name);
  }

  private void clearCheckedValue() {
    checkedMaterializer = null;
    materializedValue = null;
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
    NativeLoopBinding binding = NativeLoopBinding.find(activation, name);
    return binding != null
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
    NativeLoopBinding binding = NativeLoopBinding.find(activation, name);
    return binding != null
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
    NativeLoopBinding binding = NativeLoopBinding.find(activation, name);
    return binding != null
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
    NativeLoopBinding binding = NativeLoopBinding.find(activation, name);
    return binding != null
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
    NativeLoopBinding binding = NativeLoopBinding.find(activation, name);
    return binding != null
        ? binding.stringValue(adapter)
        : NativeSupport.stringValue(adapter, resolveRaw(activation));
  }
}

final class NativeNullLocalIdent extends EvalIdent implements NativeNullCapability {
  NativeNullLocalIdent(long id, String name, TypeAdapter adapter) {
    super(id, name, adapter);
  }

  @Override
  public void evalNull(Activation activation) {
    NativeLoopBinding binding = NativeLoopBinding.find(activation, name);
    if (binding != null) {
      binding.nullValue(adapter);
    } else {
      NativeSupport.nullValue(adapter, resolveRaw(activation));
    }
  }
}
