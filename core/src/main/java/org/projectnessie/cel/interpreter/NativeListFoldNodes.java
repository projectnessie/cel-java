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
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import java.util.ArrayList;
import java.util.Arrays;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.functions.Overload;

final class NativeScalarListFold extends EvalListFold implements NativeScalarListFoldCapability {
  final Interpretable range;
  private final NativeListTraversalPlan traversal;
  private final NativeScalarKind inputKind;
  final NativeBooleanCapability predicate;
  final Interpretable nativeTransform;
  private final NativeScalarKind outputKind;
  private final TypeAdapter adapter;
  private final String variable;

  NativeScalarListFold(
      long id,
      String variable,
      Interpretable range,
      NativeListTraversalPlan traversal,
      Interpretable establishedFilter,
      Interpretable establishedTransform,
      NativeScalarKind inputKind,
      NativeBooleanCapability predicate,
      Interpretable nativeTransform,
      NativeScalarKind outputKind,
      TypeAdapter adapter) {
    super(id, variable, "", range, establishedFilter, establishedTransform, adapter);
    this.range = requireNonNull(range, "range");
    this.traversal = requireNonNull(traversal, "traversal");
    this.inputKind = requireNonNull(inputKind, "inputKind");
    this.predicate = predicate;
    this.nativeTransform = requireNonNull(nativeTransform, "nativeTransform");
    this.outputKind = requireNonNull(outputKind, "outputKind");
    this.adapter = requireNonNull(adapter, "adapter");
    this.variable = requireNonNull(variable, "variable");
  }

  @Override
  public NativeScalarKind elementKind() {
    return outputKind;
  }

  @Override
  public long evalSize(Activation activation) {
    NativeListFoldEvaluation evaluation =
        new NativeListFoldEvaluation(activation, variable, this, -1);
    NativeScalarLoopKernel.evaluate(traversal, inputKind, activation, evaluation, evaluation);
    return evaluation.position;
  }

  @Override
  public boolean evalBooleanAt(Activation activation, int index) {
    return evaluateAt(activation, index).booleanValue;
  }

  @Override
  public long evalIntAt(Activation activation, int index) {
    return evaluateAt(activation, index).intValue;
  }

  @Override
  public long evalUintAt(Activation activation, int index) {
    return evaluateAt(activation, index).uintValue;
  }

  @Override
  public double evalDoubleAt(Activation activation, int index) {
    return evaluateAt(activation, index).doubleValue;
  }

  @Override
  public String evalStringAt(Activation activation, int index) {
    return requireNonNull(
        evaluateAt(activation, index).stringValue, "native string expression returned null");
  }

  @Override
  public void evalNullAt(Activation activation, int index) {
    evaluateAt(activation, index);
  }

  @Override
  public boolean evalStringContains(Activation activation, NativeStringCapability needle) {
    String needleValue = null;
    Val slowNeedle = null;
    try {
      needleValue = needle.evalString(activation);
    } catch (ValueSignal valueSignal) {
      slowNeedle = valueSignal.value;
    }

    NativeStringMembershipEvaluation evaluation =
        new NativeStringMembershipEvaluation(activation, variable, this, needleValue, slowNeedle);
    try {
      NativeScalarLoopKernel.evaluate(traversal, inputKind, activation, evaluation, evaluation);
    } catch (ValueSignal valueSignal) {
      if (evaluation.exceptionalNeedle()) {
        throw signal(evaluation.slowNeedle());
      }
      throw valueSignal;
    }
    return evaluation.finish();
  }

  @Override
  public NativeIntAggregateValues evalIntValues(Activation activation) {
    if (outputKind != NativeScalarKind.INT) {
      throw new IllegalStateException("integer values requested from " + outputKind + " list fold");
    }
    NativeIntListFoldEvaluation evaluation =
        new NativeIntListFoldEvaluation(activation, variable, this);
    NativeScalarLoopKernel.evaluate(traversal, inputKind, activation, evaluation, evaluation);
    return evaluation.values();
  }

  private NativeListFoldEvaluation evaluateAt(Activation activation, int index) {
    NativeListFoldEvaluation evaluation =
        new NativeListFoldEvaluation(activation, variable, this, index);
    try {
      NativeScalarLoopKernel.evaluate(traversal, inputKind, activation, evaluation, evaluation);
    } catch (ValueSignal valueSignal) {
      throw isError(valueSignal.value) ? propagatedError(valueSignal.value) : valueSignal;
    }
    evaluation.finishIndex();
    return evaluation;
  }

  boolean include(NativeLoopBinding binding) {
    if (predicate == null) {
      return true;
    }
    try {
      return predicate.evalBoolean(binding);
    } catch (ValueSignal valueSignal) {
      throw signal(noSuchOverload(null, Operator.Conditional.id, valueSignal.value));
    }
  }

  void evaluateTransform(NativeListFoldEvaluation evaluation, boolean selected) {
    try {
      switch (outputKind) {
        case BOOLEAN -> {
          boolean value = ((NativeBooleanCapability) nativeTransform).evalBoolean(evaluation);
          if (selected) {
            evaluation.booleanValue = value;
          }
        }
        case INT -> {
          long value = ((NativeIntCapability) nativeTransform).evalInt(evaluation);
          if (selected) {
            evaluation.intValue = value;
          }
        }
        case UINT -> {
          long value = ((NativeUintCapability) nativeTransform).evalUint(evaluation);
          if (selected) {
            evaluation.uintValue = value;
          }
        }
        case DOUBLE -> {
          double value = ((NativeDoubleCapability) nativeTransform).evalDouble(evaluation);
          if (selected) {
            evaluation.doubleValue = value;
          }
        }
        case STRING -> {
          String value = ((NativeStringCapability) nativeTransform).evalString(evaluation);
          if (selected) {
            evaluation.stringValue = value;
          }
        }
        case NULL -> ((NativeNullCapability) nativeTransform).evalNull(evaluation);
      }
    } catch (ValueSignal valueSignal) {
      if (isError(valueSignal.value) || isUnknown(valueSignal.value)) {
        throw valueSignal;
      }
      if (selected) {
        evaluation.slowValue = valueSignal.value;
      }
    }
  }

  private static ValueSignal propagatedError(Val value) {
    return NativeSupport.propagatedError(value);
  }
}

final class NativeStringMembershipEvaluation extends NativeLoopBinding
    implements NativeScalarLoopConsumer {
  private final NativeScalarListFold source;
  private final String needle;
  private final Val slowNeedle;
  private final boolean exceptionalNeedle;
  private long position;
  private long firstMatch = -1L;
  private ArrayList<NativePositionedValue> slowValues;
  private ArrayList<Val> constructedValues;

  NativeStringMembershipEvaluation(
      Activation parent,
      String variable,
      NativeScalarListFold source,
      String needle,
      Val slowNeedle) {
    super(parent, variable);
    this.source = requireNonNull(source, "source");
    this.needle = needle;
    this.slowNeedle = slowNeedle;
    this.exceptionalNeedle = isError(slowNeedle) || isUnknown(slowNeedle);
    if (slowNeedle != null && !exceptionalNeedle) {
      constructedValues = new ArrayList<>();
    }
  }

  @Override
  public boolean test(NativeLoopBinding ignored) {
    if (!source.include(this)) {
      return false;
    }
    try {
      recordOutput(((NativeStringCapability) source.nativeTransform).evalString(this));
    } catch (ValueSignal valueSignal) {
      if (isError(valueSignal.value) || isUnknown(valueSignal.value)) {
        throw valueSignal;
      }
      recordOutput(valueSignal.value);
    }
    return false;
  }

  boolean exceptionalNeedle() {
    return exceptionalNeedle;
  }

  Val slowNeedle() {
    return slowNeedle;
  }

  private void recordOutput(String value) {
    if (exceptionalNeedle) {
      return;
    }
    if (slowNeedle != null) {
      constructedValues.add(stringOf(value));
    } else if (firstMatch == -1L && needle.equals(value)) {
      firstMatch = position;
    }
    position++;
  }

  private void recordOutput(Val value) {
    if (exceptionalNeedle) {
      return;
    }
    if (slowNeedle != null) {
      constructedValues.add(value);
    } else {
      if (slowValues == null) {
        slowValues = new ArrayList<>();
      }
      slowValues.add(new NativePositionedValue(position, value));
    }
    position++;
  }

  boolean finish() {
    if (exceptionalNeedle) {
      throw signal(slowNeedle);
    }
    if (slowNeedle != null) {
      for (Val value : constructedValues) {
        if (slowNeedle.equal(value) == True) {
          return true;
        }
      }
      return false;
    }
    if (slowValues != null) {
      Val needleValue = stringOf(needle);
      for (NativePositionedValue slowValue : slowValues) {
        if (firstMatch != -1L && slowValue.position() >= firstMatch) {
          break;
        }
        if (needleValue.equal(slowValue.value()) == True) {
          return true;
        }
      }
    }
    return firstMatch != -1L;
  }
}

record NativePositionedValue(long position, Val value) {
  NativePositionedValue {
    requireNonNull(value, "value");
  }
}

final class NativeIntListFoldEvaluation extends NativeLoopBinding
    implements NativeScalarLoopConsumer {
  private final NativeScalarListFold source;
  private long[] values = new long[0];
  private Val[] slowValues;
  private int size;

  NativeIntListFoldEvaluation(Activation parent, String variable, NativeScalarListFold source) {
    super(parent, variable);
    this.source = requireNonNull(source, "source");
  }

  @Override
  public void prepareCapacity(int capacity) {
    if (source.predicate == null && capacity > 0) {
      values = new long[capacity];
    }
  }

  @Override
  public boolean test(NativeLoopBinding ignored) {
    if (!source.include(this)) {
      return false;
    }
    ensureCapacity(size + 1);
    try {
      values[size] = ((NativeIntCapability) source.nativeTransform).evalInt(this);
    } catch (ValueSignal valueSignal) {
      if (isError(valueSignal.value) || isUnknown(valueSignal.value)) {
        throw valueSignal;
      }
      if (slowValues == null) {
        slowValues = new Val[values.length];
      }
      slowValues[size] = valueSignal.value;
    }
    size++;
    return false;
  }

  private void ensureCapacity(int required) {
    if (required <= values.length) {
      return;
    }
    int capacity =
        values.length == 0
            ? 10
            : source.predicate != null
                ? Math.multiplyExact(values.length, 2)
                : Math.addExact(values.length, Math.max(1, values.length >> 1));
    capacity = Math.max(required, capacity);
    values = Arrays.copyOf(values, capacity);
    if (slowValues != null) {
      slowValues = Arrays.copyOf(slowValues, capacity);
    }
  }

  NativeIntAggregateValues values() {
    return new NativeIntAggregateValues(values, slowValues, size);
  }
}

record NativeIntAggregateValues(long[] values, Val[] slowValues, int size) {
  NativeIntAggregateValues {
    requireNonNull(values, "values");
  }

  void set(int index, NativeLoopBinding binding) {
    Val slowValue = slowValues != null ? slowValues[index] : null;
    if (slowValue != null) {
      binding.setObject(slowValue);
    } else {
      binding.setInt(values[index]);
    }
  }
}

final class NativeListFoldEvaluation extends NativeLoopBinding implements NativeScalarLoopConsumer {
  private final NativeScalarListFold source;
  private final int selectedIndex;
  long position;
  boolean booleanValue;
  long intValue;
  long uintValue;
  double doubleValue;
  String stringValue;
  Val slowValue;

  NativeListFoldEvaluation(
      Activation parent, String variable, NativeScalarListFold source, int selectedIndex) {
    super(parent, variable);
    this.source = requireNonNull(source, "source");
    this.selectedIndex = selectedIndex;
  }

  @Override
  public boolean test(NativeLoopBinding ignored) {
    if (!source.include(this)) {
      return false;
    }
    boolean selected = position == selectedIndex;
    source.evaluateTransform(this, selected);
    position++;
    return false;
  }

  void finishIndex() {
    if (selectedIndex < 0 || selectedIndex >= position) {
      throw signal(
          newErr(
              "invalid_argument: index '%d' out of range in list of size '%d'",
              selectedIndex, position));
    }
    if (slowValue != null) {
      throw signal(slowValue);
    }
  }
}

final class NativeStringListFoldMembership extends EvalBinary implements NativeBooleanCapability {
  final NativeStringCapability needle;
  final NativeScalarListFoldCapability source;

  NativeStringListFoldMembership(
      long id, Interpretable needle, Interpretable source, Overload implementation) {
    super(
        id,
        Operator.In.id,
        Overloads.InList,
        needle,
        source,
        implementation.operandTrait,
        implementation.binary);
    this.needle = (NativeStringCapability) needle;
    this.source = (NativeScalarListFoldCapability) source;
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return source.evalStringContains(activation, needle);
  }
}

final class NativeListFoldSize extends EvalUnary implements NativeIntCapability {
  final NativeScalarListFoldCapability source;

  NativeListFoldSize(
      long id, String function, String overload, Interpretable operand, Overload implementation) {
    super(id, function, overload, operand, implementation.operandTrait, implementation.unary);
    this.source = (NativeScalarListFoldCapability) operand;
  }

  @Override
  public long evalInt(Activation activation) {
    return source.evalSize(activation);
  }
}

final class NativeBooleanListFoldIndex extends NativeScalarAttr implements NativeBooleanCapability {
  final NativeScalarListFoldCapability source;
  private final int index;

  NativeBooleanListFoldIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeScalarListFoldCapability source,
      int index) {
    super(id, adapter, establishedAttribute, null);
    this.source = source;
    this.index = index;
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return source.evalBooleanAt(activation, index);
  }
}

final class NativeIntListFoldIndex extends NativeScalarAttr implements NativeIntCapability {
  final NativeScalarListFoldCapability source;
  private final int index;

  NativeIntListFoldIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeScalarListFoldCapability source,
      int index) {
    super(id, adapter, establishedAttribute, null);
    this.source = source;
    this.index = index;
  }

  @Override
  public long evalInt(Activation activation) {
    return source.evalIntAt(activation, index);
  }
}

final class NativeUintListFoldIndex extends NativeScalarAttr implements NativeUintCapability {
  final NativeScalarListFoldCapability source;
  private final int index;

  NativeUintListFoldIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeScalarListFoldCapability source,
      int index) {
    super(id, adapter, establishedAttribute, null);
    this.source = source;
    this.index = index;
  }

  @Override
  public long evalUint(Activation activation) {
    return source.evalUintAt(activation, index);
  }
}

final class NativeDoubleListFoldIndex extends NativeScalarAttr implements NativeDoubleCapability {
  final NativeScalarListFoldCapability source;
  private final int index;

  NativeDoubleListFoldIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeScalarListFoldCapability source,
      int index) {
    super(id, adapter, establishedAttribute, null);
    this.source = source;
    this.index = index;
  }

  @Override
  public double evalDouble(Activation activation) {
    return source.evalDoubleAt(activation, index);
  }
}

final class NativeStringListFoldIndex extends NativeScalarAttr implements NativeStringCapability {
  final NativeScalarListFoldCapability source;
  private final int index;

  NativeStringListFoldIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeScalarListFoldCapability source,
      int index) {
    super(id, adapter, establishedAttribute, null);
    this.source = source;
    this.index = index;
  }

  @Override
  public String evalString(Activation activation) {
    return source.evalStringAt(activation, index);
  }
}

final class NativeNullListFoldIndex extends NativeScalarAttr implements NativeNullCapability {
  final NativeScalarListFoldCapability source;
  private final int index;

  NativeNullListFoldIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeScalarListFoldCapability source,
      int index) {
    super(id, adapter, establishedAttribute, null);
    this.source = source;
    this.index = index;
  }

  @Override
  public void evalNull(Activation activation) {
    source.evalNullAt(activation, index);
  }
}
