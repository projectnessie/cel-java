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
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.BoolT;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.functions.Overload;

abstract class NativeListIndex extends NativeScalarAttr {
  final NativeListSourceCapability source;
  final int index;
  private final NativeIntCapability dynamicIndex;
  final boolean directArrayAccess;

  NativeListIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      Attribute partialAttribute,
      NativeListSourceCapability source,
      long index,
      boolean directArrayAccess) {
    super(id, adapter, establishedAttribute, partialAttribute);
    this.source = source;
    this.index = Math.toIntExact(index);
    this.dynamicIndex = null;
    this.directArrayAccess = directArrayAccess;
  }

  NativeListIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListSourceCapability source,
      NativeIntCapability dynamicIndex,
      boolean directArrayAccess) {
    super(id, adapter, establishedAttribute, null);
    this.source = source;
    this.index = 0;
    this.dynamicIndex = dynamicIndex;
    this.directArrayAccess = directArrayAccess;
  }

  final Object resolveTarget(Activation activation) {
    Object target;
    try {
      target = source.evalRaw(activation);
    } catch (ValueSignal valueSignal) {
      target = valueSignal.value;
    } catch (Exception e) {
      throw signal(newErr(e, e.toString()));
    }
    if (target instanceof Val value && isError(value)) {
      throw NativeSupport.propagatedError(value);
    }
    return target;
  }

  final int selectedIndex(Activation activation, Object target) {
    if (dynamicIndex == null) {
      return index;
    }
    if (target instanceof Val sourceValue && isUnknownOrError(sourceValue)) {
      throw signal(sourceValue);
    }
    try {
      long evaluatedIndex = dynamicIndex.evalInt(activation);
      if (evaluatedIndex < Integer.MIN_VALUE || evaluatedIndex > Integer.MAX_VALUE) {
        int size = NativeListSources.size(source, target, directArrayAccess);
        throw signal(
            newErr(
                "invalid_argument: index '%d' out of range in list of size '%d'",
                evaluatedIndex, size));
      }
      return (int) evaluatedIndex;
    } catch (ValueSignal valueSignal) {
      throw valueSignal;
    } catch (Exception failure) {
      throw signal(newErr(failure, failure.toString()));
    }
  }
}

final class NativeBooleanListIndex extends NativeListIndex implements NativeBooleanCapability {
  NativeBooleanListIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      Attribute partialAttribute,
      NativeListSourceCapability source,
      long index,
      boolean directArrayAccess) {
    super(id, adapter, establishedAttribute, partialAttribute, source, index, directArrayAccess);
  }

  NativeBooleanListIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListSourceCapability source,
      NativeIntCapability dynamicIndex,
      boolean directArrayAccess) {
    super(id, adapter, establishedAttribute, source, dynamicIndex, directArrayAccess);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    Object target = resolveTarget(activation);
    return NativeListSources.booleanAt(
        source, target, selectedIndex(activation, target), directArrayAccess, adapter);
  }
}

final class NativeIntListIndex extends NativeListIndex implements NativeIntCapability {
  NativeIntListIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      Attribute partialAttribute,
      NativeListSourceCapability source,
      long index,
      boolean directArrayAccess) {
    super(id, adapter, establishedAttribute, partialAttribute, source, index, directArrayAccess);
  }

  NativeIntListIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListSourceCapability source,
      NativeIntCapability dynamicIndex,
      boolean directArrayAccess) {
    super(id, adapter, establishedAttribute, source, dynamicIndex, directArrayAccess);
  }

  @Override
  public long evalInt(Activation activation) {
    if (usesPartialAttribute(activation)) {
      return NativeSupport.intValue(adapter, resolveNative(activation));
    }
    Object target = resolveTarget(activation);
    return NativeListSources.intAt(
        source, target, selectedIndex(activation, target), directArrayAccess, adapter);
  }
}

final class NativeUintListIndex extends NativeListIndex implements NativeUintCapability {
  NativeUintListIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      Attribute partialAttribute,
      NativeListSourceCapability source,
      long index,
      boolean directArrayAccess) {
    super(id, adapter, establishedAttribute, partialAttribute, source, index, directArrayAccess);
  }

  NativeUintListIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListSourceCapability source,
      NativeIntCapability dynamicIndex,
      boolean directArrayAccess) {
    super(id, adapter, establishedAttribute, source, dynamicIndex, directArrayAccess);
  }

  @Override
  public long evalUint(Activation activation) {
    if (usesPartialAttribute(activation)) {
      return NativeSupport.uintValue(adapter, resolveNative(activation));
    }
    Object target = resolveTarget(activation);
    return NativeListSources.uintAt(
        source, target, selectedIndex(activation, target), directArrayAccess, adapter);
  }
}

final class NativeDoubleListIndex extends NativeListIndex implements NativeDoubleCapability {
  NativeDoubleListIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      Attribute partialAttribute,
      NativeListSourceCapability source,
      long index,
      boolean directArrayAccess) {
    super(id, adapter, establishedAttribute, partialAttribute, source, index, directArrayAccess);
  }

  NativeDoubleListIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListSourceCapability source,
      NativeIntCapability dynamicIndex,
      boolean directArrayAccess) {
    super(id, adapter, establishedAttribute, source, dynamicIndex, directArrayAccess);
  }

  @Override
  public double evalDouble(Activation activation) {
    if (usesPartialAttribute(activation)) {
      return NativeSupport.doubleValue(adapter, resolveNative(activation));
    }
    Object target = resolveTarget(activation);
    return NativeListSources.doubleAt(
        source, target, selectedIndex(activation, target), directArrayAccess, adapter);
  }
}

final class NativeStringListIndex extends NativeListIndex implements NativeStringCapability {
  NativeStringListIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      Attribute partialAttribute,
      NativeListSourceCapability source,
      long index,
      boolean directArrayAccess) {
    super(id, adapter, establishedAttribute, partialAttribute, source, index, directArrayAccess);
  }

  NativeStringListIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListSourceCapability source,
      NativeIntCapability dynamicIndex,
      boolean directArrayAccess) {
    super(id, adapter, establishedAttribute, source, dynamicIndex, directArrayAccess);
  }

  @Override
  public String evalString(Activation activation) {
    if (usesPartialAttribute(activation)) {
      return NativeSupport.stringValue(adapter, resolveNative(activation));
    }
    Object target = resolveTarget(activation);
    return NativeListSources.stringAt(
        source, target, selectedIndex(activation, target), directArrayAccess, adapter);
  }
}

final class NativeNullListIndex extends NativeListIndex implements NativeNullCapability {
  NativeNullListIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      Attribute partialAttribute,
      NativeListSourceCapability source,
      long index,
      boolean directArrayAccess) {
    super(id, adapter, establishedAttribute, partialAttribute, source, index, directArrayAccess);
  }

  NativeNullListIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListSourceCapability source,
      NativeIntCapability dynamicIndex,
      boolean directArrayAccess) {
    super(id, adapter, establishedAttribute, source, dynamicIndex, directArrayAccess);
  }

  @Override
  public void evalNull(Activation activation) {
    Object target = resolveTarget(activation);
    NativeListSources.nullAt(
        source, target, selectedIndex(activation, target), directArrayAccess, adapter);
  }
}

final class NativeStringListMembership extends EvalBinary implements NativeBooleanCapability {
  private final NativeStringCapability nativeNeedle;
  private final NativeListSourceCapability nativeList;
  private final boolean directArrayAccess;

  NativeStringListMembership(
      long id,
      Interpretable needle,
      Interpretable list,
      Overload implementation,
      boolean directArrayAccess) {
    super(
        id,
        Operator.In.id,
        Overloads.InList,
        needle,
        list,
        implementation.operandTrait,
        implementation.binary);
    this.nativeNeedle = (NativeStringCapability) needle;
    this.nativeList = (NativeListSourceCapability) list;
    this.directArrayAccess = directArrayAccess;
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    String needle = null;
    Val slowNeedle = null;
    try {
      needle = nativeNeedle.evalString(activation);
    } catch (ValueSignal valueSignal) {
      slowNeedle = valueSignal.value;
    }

    Object list;
    try {
      list = nativeList.evalRaw(activation);
    } catch (ValueSignal valueSignal) {
      list = valueSignal.value;
    }

    if (slowNeedle == null && directArrayAccess && list instanceof String[] values) {
      needle = requireNonNull(needle, "native string capability returned null");
      for (String element : values) {
        if (needle.equals(element)) {
          return true;
        }
      }
      return false;
    }

    Val needleValue = slowNeedle != null ? slowNeedle : stringOf(needle);
    Val listValue = nativeList.materializeResolvedList(list);
    return NativeScalarContinuations.booleanResult(evalPrepared(needleValue, listValue));
  }
}

/**
 * Exact host-set membership for the scalar kinds whose Java representation has a CEL-compatible
 * {@link java.util.Set#contains(Object)} operation.
 */
final class NativeExactSetMembership extends EvalBinary implements NativeBooleanCapability {
  private final NativeScalarKind kind;
  private final NativeListSourceCapability source;

  NativeExactSetMembership(
      long id,
      Interpretable needle,
      Interpretable list,
      Overload implementation,
      NativeScalarKind kind) {
    super(
        id,
        Operator.In.id,
        Overloads.InList,
        needle,
        list,
        implementation.operandTrait,
        implementation.binary);
    this.kind = kind;
    this.source = (NativeListSourceCapability) list;
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    boolean booleanNeedle = false;
    long integerNeedle = 0L;
    double doubleNeedle = 0.0d;
    String stringNeedle = null;
    Val slowNeedle = null;
    try {
      switch (kind) {
        case BOOLEAN -> booleanNeedle = ((NativeBooleanCapability) lhs).evalBoolean(activation);
        case INT -> integerNeedle = ((NativeIntCapability) lhs).evalInt(activation);
        case UINT -> integerNeedle = ((NativeUintCapability) lhs).evalUint(activation);
        case DOUBLE -> doubleNeedle = ((NativeDoubleCapability) lhs).evalDouble(activation);
        case STRING -> stringNeedle = ((NativeStringCapability) lhs).evalString(activation);
        case NULL -> throw new IllegalStateException("null Set membership is not specialized");
      }
    } catch (ValueSignal valueSignal) {
      slowNeedle = valueSignal.value;
    }

    Object raw;
    try {
      raw = source.evalRaw(activation);
    } catch (ValueSignal valueSignal) {
      raw = valueSignal.value;
    }

    if (slowNeedle == null) {
      try {
        int direct =
            NativeListSources.exactSetContains(
                raw, kind, booleanNeedle, integerNeedle, doubleNeedle, stringNeedle);
        if (direct != NativeListSources.UNSUPPORTED) {
          return direct != 0;
        }
      } catch (ValueSignal valueSignal) {
        throw valueSignal;
      } catch (Exception failure) {
        throw signal(newErr(failure, failure.toString()));
      }
    }

    Val needleValue =
        slowNeedle != null
            ? slowNeedle
            : switch (kind) {
              case BOOLEAN -> boolOf(booleanNeedle);
              case INT -> intOf(integerNeedle);
              case UINT -> uintOf(integerNeedle);
              case DOUBLE -> doubleOf(doubleNeedle);
              case STRING -> stringOf(stringNeedle);
              case NULL ->
                  throw new IllegalStateException("null Set membership is not specialized");
            };
    Val listValue;
    try {
      listValue = source.materializeResolvedList(raw);
    } catch (ValueSignal valueSignal) {
      listValue = valueSignal.value;
    }
    return NativeScalarContinuations.booleanResult(evalPrepared(needleValue, listValue));
  }
}

/** Encounter-order CEL list equality over two exact host aggregate sources. */
final class NativeExactListEquality extends EvalEq implements NativeBooleanCapability {
  private final NativeListSourceCapability leftSource;
  private final NativeListSourceCapability rightSource;
  private final NativeScalarKind kind;
  private final TypeAdapter adapter;

  NativeExactListEquality(
      long id,
      Interpretable left,
      Interpretable right,
      NativeScalarKind kind,
      TypeAdapter adapter) {
    super(id, left, right);
    this.leftSource = (NativeListSourceCapability) left;
    this.rightSource = (NativeListSourceCapability) right;
    this.kind = kind;
    this.adapter = adapter;
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public boolean evalBoolean(Activation activation) {
    Object leftRaw;
    Object rightRaw;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftRaw = leftSource.evalRaw(activation);
    } catch (ValueSignal valueSignal) {
      leftRaw = null;
      leftSlow = valueSignal.value;
    }
    try {
      rightRaw = rightSource.evalRaw(activation);
    } catch (ValueSignal valueSignal) {
      rightRaw = null;
      rightSlow = valueSignal.value;
    }

    if (leftSlow == null && rightSlow == null) {
      try {
        int direct =
            NativeListSources.exactListEquals(
                leftSource, leftRaw, rightSource, rightRaw, kind, adapter);
        if (direct != NativeListSources.UNSUPPORTED) {
          return direct != 0;
        }
      } catch (ValueSignal valueSignal) {
        throw valueSignal;
      } catch (Exception failure) {
        throw signal(newErr(failure, failure.toString()));
      }
    }

    Val leftValue = leftSlow != null ? leftSlow : leftSource.materializeResolvedList(leftRaw);
    Val rightValue = rightSlow != null ? rightSlow : rightSource.materializeResolvedList(rightRaw);
    return NativeScalarContinuations.booleanResult(evalPrepared(leftValue, rightValue));
  }
}

/** Encounter-order CEL list inequality over two exact host aggregate sources. */
final class NativeExactListInequality extends EvalNe implements NativeBooleanCapability {
  private final NativeListSourceCapability leftSource;
  private final NativeListSourceCapability rightSource;
  private final NativeScalarKind kind;
  private final TypeAdapter adapter;

  NativeExactListInequality(
      long id,
      Interpretable left,
      Interpretable right,
      NativeScalarKind kind,
      TypeAdapter adapter) {
    super(id, left, right);
    this.leftSource = (NativeListSourceCapability) left;
    this.rightSource = (NativeListSourceCapability) right;
    this.kind = kind;
    this.adapter = adapter;
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public boolean evalBoolean(Activation activation) {
    Object leftRaw;
    Object rightRaw;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftRaw = leftSource.evalRaw(activation);
    } catch (ValueSignal valueSignal) {
      leftRaw = null;
      leftSlow = valueSignal.value;
    }
    try {
      rightRaw = rightSource.evalRaw(activation);
    } catch (ValueSignal valueSignal) {
      rightRaw = null;
      rightSlow = valueSignal.value;
    }

    if (leftSlow == null && rightSlow == null) {
      try {
        int direct =
            NativeListSources.exactListEquals(
                leftSource, leftRaw, rightSource, rightRaw, kind, adapter);
        if (direct != NativeListSources.UNSUPPORTED) {
          return direct == 0;
        }
      } catch (ValueSignal valueSignal) {
        throw valueSignal;
      } catch (Exception failure) {
        throw signal(newErr(failure, failure.toString()));
      }
    }

    Val leftValue = leftSlow != null ? leftSlow : leftSource.materializeResolvedList(leftRaw);
    Val rightValue = rightSlow != null ? rightSlow : rightSource.materializeResolvedList(rightRaw);
    return NativeScalarContinuations.booleanResult(evalPrepared(leftValue, rightValue));
  }
}

abstract class NativeScalarListLiteral extends EvalList
    implements NativeScalarListLiteralCapability {
  final Interpretable[] nativeElements;
  private final boolean constantElements;

  NativeScalarListLiteral(
      long id, Interpretable[] elements, boolean constantElements, TypeAdapter adapter) {
    super(id, establishedElements(elements, adapter), adapter);
    this.nativeElements = elements.clone();
    this.constantElements = constantElements;
  }

  @Override
  public final int evalSize(Activation activation) {
    if (!constantElements) {
      for (int i = 0; i < nativeElements.length; i++) {
        evalDiscarded(activation, i);
      }
    }
    return nativeElements.length;
  }

  abstract void evalDiscarded(Activation activation, int index);

  final void checkLiteralIndex(int index) {
    if (index < 0 || index >= nativeElements.length) {
      throw signal(
          newErr(
              "invalid_argument: index '%d' out of range in list of size '%d'",
              index, nativeElements.length));
    }
  }

  final ValueSignal propagatedError(Val value) {
    return NativeSupport.propagatedError(value);
  }

  private static Interpretable[] establishedElements(
      Interpretable[] elements, TypeAdapter adapter) {
    Interpretable[] established = elements.clone();
    for (int i = 0; i < established.length; i++) {
      if (NativeIsland.supports(established[i])) {
        established[i] = new NativeIsland(established[i], adapter);
      }
    }
    return established;
  }
}

final class NativeBooleanListLiteral extends NativeScalarListLiteral
    implements NativeBooleanListLiteralCapability {
  NativeBooleanListLiteral(
      long id, Interpretable[] elements, boolean constantElements, TypeAdapter adapter) {
    super(id, elements, constantElements, adapter);
  }

  @Override
  public boolean evalBooleanAt(Activation activation, int index) {
    boolean selected = false;
    Val selectedSlow = null;
    for (int i = 0; i < nativeElements.length; i++) {
      try {
        boolean value = ((NativeBooleanCapability) nativeElements[i]).evalBoolean(activation);
        if (i == index) {
          selected = value;
        }
      } catch (ValueSignal valueSignal) {
        if (isError(valueSignal.value)) {
          throw propagatedError(valueSignal.value);
        }
        if (isUnknown(valueSignal.value)) {
          throw valueSignal;
        }
        if (i == index) {
          selectedSlow = valueSignal.value;
        }
      }
    }
    checkLiteralIndex(index);
    if (selectedSlow != null) {
      throw signal(selectedSlow);
    }
    return selected;
  }

  @Override
  void evalDiscarded(Activation activation, int index) {
    try {
      ((NativeBooleanCapability) nativeElements[index]).evalBoolean(activation);
    } catch (ValueSignal valueSignal) {
      if (isUnknownOrError(valueSignal.value)) {
        throw valueSignal;
      }
    }
  }
}

final class NativeIntListLiteral extends NativeScalarListLiteral
    implements NativeIntListLiteralCapability {
  NativeIntListLiteral(
      long id, Interpretable[] elements, boolean constantElements, TypeAdapter adapter) {
    super(id, elements, constantElements, adapter);
  }

  @Override
  public long evalIntAt(Activation activation, int index) {
    long selected = 0L;
    Val selectedSlow = null;
    for (int i = 0; i < nativeElements.length; i++) {
      try {
        long value = ((NativeIntCapability) nativeElements[i]).evalInt(activation);
        if (i == index) {
          selected = value;
        }
      } catch (ValueSignal valueSignal) {
        if (isError(valueSignal.value)) {
          throw propagatedError(valueSignal.value);
        }
        if (isUnknown(valueSignal.value)) {
          throw valueSignal;
        }
        if (i == index) {
          selectedSlow = valueSignal.value;
        }
      }
    }
    checkLiteralIndex(index);
    if (selectedSlow != null) {
      throw signal(selectedSlow);
    }
    return selected;
  }

  @Override
  void evalDiscarded(Activation activation, int index) {
    try {
      ((NativeIntCapability) nativeElements[index]).evalInt(activation);
    } catch (ValueSignal valueSignal) {
      if (isUnknownOrError(valueSignal.value)) {
        throw valueSignal;
      }
    }
  }
}

final class NativeUintListLiteral extends NativeScalarListLiteral
    implements NativeUintListLiteralCapability {
  NativeUintListLiteral(
      long id, Interpretable[] elements, boolean constantElements, TypeAdapter adapter) {
    super(id, elements, constantElements, adapter);
  }

  @Override
  public long evalUintAt(Activation activation, int index) {
    long selected = 0L;
    Val selectedSlow = null;
    for (int i = 0; i < nativeElements.length; i++) {
      try {
        long value = ((NativeUintCapability) nativeElements[i]).evalUint(activation);
        if (i == index) {
          selected = value;
        }
      } catch (ValueSignal valueSignal) {
        if (isError(valueSignal.value)) {
          throw propagatedError(valueSignal.value);
        }
        if (isUnknown(valueSignal.value)) {
          throw valueSignal;
        }
        if (i == index) {
          selectedSlow = valueSignal.value;
        }
      }
    }
    checkLiteralIndex(index);
    if (selectedSlow != null) {
      throw signal(selectedSlow);
    }
    return selected;
  }

  @Override
  void evalDiscarded(Activation activation, int index) {
    try {
      ((NativeUintCapability) nativeElements[index]).evalUint(activation);
    } catch (ValueSignal valueSignal) {
      if (isUnknownOrError(valueSignal.value)) {
        throw valueSignal;
      }
    }
  }
}

final class NativeDoubleListLiteral extends NativeScalarListLiteral
    implements NativeDoubleListLiteralCapability {
  NativeDoubleListLiteral(
      long id, Interpretable[] elements, boolean constantElements, TypeAdapter adapter) {
    super(id, elements, constantElements, adapter);
  }

  @Override
  public double evalDoubleAt(Activation activation, int index) {
    double selected = 0.0d;
    Val selectedSlow = null;
    for (int i = 0; i < nativeElements.length; i++) {
      try {
        double value = ((NativeDoubleCapability) nativeElements[i]).evalDouble(activation);
        if (i == index) {
          selected = value;
        }
      } catch (ValueSignal valueSignal) {
        if (isError(valueSignal.value)) {
          throw propagatedError(valueSignal.value);
        }
        if (isUnknown(valueSignal.value)) {
          throw valueSignal;
        }
        if (i == index) {
          selectedSlow = valueSignal.value;
        }
      }
    }
    checkLiteralIndex(index);
    if (selectedSlow != null) {
      throw signal(selectedSlow);
    }
    return selected;
  }

  @Override
  void evalDiscarded(Activation activation, int index) {
    try {
      ((NativeDoubleCapability) nativeElements[index]).evalDouble(activation);
    } catch (ValueSignal valueSignal) {
      if (isUnknownOrError(valueSignal.value)) {
        throw valueSignal;
      }
    }
  }
}

final class NativeStringListLiteral extends NativeScalarListLiteral
    implements NativeStringListLiteralCapability {
  NativeStringListLiteral(
      long id, Interpretable[] elements, boolean constantElements, TypeAdapter adapter) {
    super(id, elements, constantElements, adapter);
  }

  @Override
  public String evalStringAt(Activation activation, int index) {
    String selected = null;
    Val selectedSlow = null;
    for (int i = 0; i < nativeElements.length; i++) {
      try {
        String value = ((NativeStringCapability) nativeElements[i]).evalString(activation);
        if (i == index) {
          selected = value;
        }
      } catch (ValueSignal valueSignal) {
        if (isError(valueSignal.value)) {
          throw propagatedError(valueSignal.value);
        }
        if (isUnknown(valueSignal.value)) {
          throw valueSignal;
        }
        if (i == index) {
          selectedSlow = valueSignal.value;
        }
      }
    }
    checkLiteralIndex(index);
    if (selectedSlow != null) {
      throw signal(selectedSlow);
    }
    return requireNonNull(selected);
  }

  @Override
  public boolean evalContains(Activation activation, NativeStringCapability needle) {
    String needleValue = null;
    Val slowNeedle = null;
    try {
      needleValue = needle.evalString(activation);
    } catch (ValueSignal valueSignal) {
      slowNeedle = valueSignal.value;
    }
    return slowNeedle != null
        ? evalContainsSlowNeedle(activation, slowNeedle)
        : evalContainsString(activation, requireNonNull(needleValue));
  }

  private boolean evalContainsSlowNeedle(Activation activation, Val needle) {
    if (isError(needle) || isUnknown(needle)) {
      for (Interpretable element : nativeElements) {
        try {
          ((NativeStringCapability) element).evalString(activation);
        } catch (ValueSignal valueSignal) {
          if (isUnknownOrError(valueSignal.value)) {
            break;
          }
        }
      }
      throw signal(needle);
    }

    Val[] values = new Val[nativeElements.length];
    for (int i = 0; i < nativeElements.length; i++) {
      try {
        values[i] = stringOf(((NativeStringCapability) nativeElements[i]).evalString(activation));
      } catch (ValueSignal valueSignal) {
        if (isUnknownOrError(valueSignal.value)) {
          throw valueSignal;
        }
        values[i] = valueSignal.value;
      }
    }
    for (Val value : values) {
      if (needle.equal(value) == BoolT.True) {
        return true;
      }
    }
    return false;
  }

  private boolean evalContainsString(Activation activation, String needle) {
    int firstMatch = -1;
    Val[] slowValues = null;
    for (int i = 0; i < nativeElements.length; i++) {
      try {
        String value = ((NativeStringCapability) nativeElements[i]).evalString(activation);
        if (firstMatch == -1 && needle.equals(value)) {
          firstMatch = i;
        }
      } catch (ValueSignal valueSignal) {
        if (isUnknownOrError(valueSignal.value)) {
          throw valueSignal;
        }
        if (slowValues == null) {
          slowValues = new Val[nativeElements.length];
        }
        slowValues[i] = valueSignal.value;
      }
    }

    if (slowValues != null) {
      StringT needleValue = stringOf(needle);
      int limit = firstMatch == -1 ? nativeElements.length : firstMatch;
      for (int i = 0; i < limit; i++) {
        Val slowValue = slowValues[i];
        if (slowValue != null && needleValue.equal(slowValue) == BoolT.True) {
          return true;
        }
      }
    }
    return firstMatch != -1;
  }

  @Override
  void evalDiscarded(Activation activation, int index) {
    try {
      ((NativeStringCapability) nativeElements[index]).evalString(activation);
    } catch (ValueSignal valueSignal) {
      if (isUnknownOrError(valueSignal.value)) {
        throw valueSignal;
      }
    }
  }
}

final class NativeBooleanListLiteralIndex extends NativeScalarAttr
    implements NativeBooleanCapability {
  private final NativeBooleanListLiteralCapability list;
  private final int index;

  NativeBooleanListLiteralIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeBooleanListLiteralCapability list,
      int index) {
    super(id, adapter, establishedAttribute, null);
    this.list = list;
    this.index = index;
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return list.evalBooleanAt(activation, index);
  }
}

final class NativeIntListLiteralIndex extends NativeScalarAttr implements NativeIntCapability {
  private final NativeIntListLiteralCapability list;
  private final int index;

  NativeIntListLiteralIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeIntListLiteralCapability list,
      int index) {
    super(id, adapter, establishedAttribute, null);
    this.list = list;
    this.index = index;
  }

  @Override
  public long evalInt(Activation activation) {
    return list.evalIntAt(activation, index);
  }
}

final class NativeUintListLiteralIndex extends NativeScalarAttr implements NativeUintCapability {
  private final NativeUintListLiteralCapability list;
  private final int index;

  NativeUintListLiteralIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeUintListLiteralCapability list,
      int index) {
    super(id, adapter, establishedAttribute, null);
    this.list = list;
    this.index = index;
  }

  @Override
  public long evalUint(Activation activation) {
    return list.evalUintAt(activation, index);
  }
}

final class NativeDoubleListLiteralIndex extends NativeScalarAttr
    implements NativeDoubleCapability {
  private final NativeDoubleListLiteralCapability list;
  private final int index;

  NativeDoubleListLiteralIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeDoubleListLiteralCapability list,
      int index) {
    super(id, adapter, establishedAttribute, null);
    this.list = list;
    this.index = index;
  }

  @Override
  public double evalDouble(Activation activation) {
    return list.evalDoubleAt(activation, index);
  }
}

final class NativeStringListLiteralIndex extends NativeScalarAttr
    implements NativeStringCapability {
  private final NativeStringListLiteralCapability list;
  private final int index;

  NativeStringListLiteralIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeStringListLiteralCapability list,
      int index) {
    super(id, adapter, establishedAttribute, null);
    this.list = list;
    this.index = index;
  }

  @Override
  public String evalString(Activation activation) {
    return list.evalStringAt(activation, index);
  }
}

final class NativeListLiteralSize extends EvalUnary implements NativeIntCapability {
  private final NativeScalarListLiteralCapability list;

  NativeListLiteralSize(
      long id, String function, String overload, Interpretable operand, Overload implementation) {
    super(id, function, overload, operand, implementation.operandTrait, implementation.unary);
    this.list = (NativeScalarListLiteralCapability) operand;
  }

  @Override
  public long evalInt(Activation activation) {
    return list.evalSize(activation);
  }
}

final class NativeListSourceSize extends EvalUnary implements NativeIntCapability {
  private final NativeListSourceCapability source;
  private final boolean directArrayAccess;

  NativeListSourceSize(
      long id,
      String function,
      String overload,
      Interpretable operand,
      Overload implementation,
      boolean directArrayAccess) {
    super(id, function, overload, operand, implementation.operandTrait, implementation.unary);
    this.source = (NativeListSourceCapability) operand;
    this.directArrayAccess = directArrayAccess;
  }

  @Override
  public long evalInt(Activation activation) {
    Object value;
    try {
      value = source.evalRaw(activation);
    } catch (ValueSignal valueSignal) {
      value = valueSignal.value;
    }
    return NativeListSources.size(source, value, directArrayAccess);
  }
}

final class NativeStringListLiteralMembership extends EvalBinary
    implements NativeBooleanCapability {
  private final NativeStringCapability needle;
  private final NativeStringListLiteralCapability list;

  NativeStringListLiteralMembership(
      long id, Interpretable needle, Interpretable list, Overload implementation) {
    super(
        id,
        Operator.In.id,
        Overloads.InList,
        needle,
        list,
        implementation.operandTrait,
        implementation.binary);
    this.needle = (NativeStringCapability) needle;
    this.list = (NativeStringListLiteralCapability) list;
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return list.evalContains(activation, needle);
  }
}
