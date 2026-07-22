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

import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.functions.Overload;

/**
 * Checked list concatenation retained as an ordinary established node except when an immediate
 * structural consumer can use its exact sources directly.
 */
final class NativeListConcat extends EvalBinary {
  final NativeListSourceCapability leftSource;
  final NativeListSourceCapability rightSource;

  NativeListConcat(long id, Interpretable left, Interpretable right, Overload implementation) {
    super(
        id,
        Operator.Add.id,
        Overloads.AddList,
        left,
        right,
        implementation.operandTrait,
        implementation.binary);
    this.leftSource = (NativeListSourceCapability) left;
    this.rightSource = (NativeListSourceCapability) right;
  }
}

/** Resolves both concat operands before applying structural list operations. */
final class NativeListConcatKernel {
  private NativeListConcatKernel() {}

  static ResolvedConcat resolve(NativeListConcat concat, Activation activation) {
    Object leftRaw = null;
    Object rightRaw = null;
    ValueSignal leftFailure = null;
    ValueSignal rightFailure = null;

    try {
      leftRaw = concat.leftSource.evalRaw(activation);
    } catch (ValueSignal failure) {
      leftFailure = failure;
    } catch (Exception failure) {
      leftFailure = signal(newErr(failure, failure.toString()));
    }
    try {
      rightRaw = concat.rightSource.evalRaw(activation);
    } catch (ValueSignal failure) {
      rightFailure = failure;
    } catch (Exception failure) {
      rightFailure = signal(newErr(failure, failure.toString()));
    }

    int leftSize = 0;
    int rightSize = 0;
    if (leftFailure == null) {
      try {
        leftSize = NativeListSources.size(concat.leftSource, leftRaw, true);
      } catch (ValueSignal failure) {
        leftFailure = failure;
      } catch (Exception failure) {
        leftFailure = signal(newErr(failure, failure.toString()));
      }
    }
    if (rightFailure == null) {
      try {
        rightSize = NativeListSources.size(concat.rightSource, rightRaw, true);
      } catch (ValueSignal failure) {
        rightFailure = failure;
      } catch (Exception failure) {
        rightFailure = signal(newErr(failure, failure.toString()));
      }
    }

    if (leftFailure != null) {
      throw leftFailure;
    }
    if (rightFailure != null) {
      throw rightFailure;
    }
    return new ResolvedConcat(leftRaw, rightRaw, leftSize, rightSize);
  }

  static Selection select(NativeListConcat concat, Activation activation, int index) {
    ResolvedConcat resolved = resolve(concat, activation);
    long size = (long) resolved.leftSize + resolved.rightSize;
    if (index < 0 || index >= size) {
      throw signal(
          newErr("invalid_argument: index '%d' out of range in list of size '%d'", index, size));
    }
    return index < resolved.leftSize
        ? new Selection(concat.leftSource, resolved.leftRaw, index)
        : new Selection(concat.rightSource, resolved.rightRaw, index - resolved.leftSize);
  }

  record ResolvedConcat(Object leftRaw, Object rightRaw, int leftSize, int rightSize) {}

  record Selection(NativeListSourceCapability source, Object raw, int index) {}
}

final class NativeListConcatSize extends EvalUnary implements NativeIntCapability {
  private final NativeListConcat concat;

  NativeListConcatSize(
      long id, String function, String overload, NativeListConcat concat, Overload implementation) {
    super(id, function, overload, concat, implementation.operandTrait, implementation.unary);
    this.concat = concat;
  }

  @Override
  public long evalInt(Activation activation) {
    NativeListConcatKernel.ResolvedConcat resolved =
        NativeListConcatKernel.resolve(concat, activation);
    return (long) resolved.leftSize() + resolved.rightSize();
  }
}

abstract class NativeListConcatIndex extends NativeScalarAttr {
  final NativeListConcat concat;
  final int index;

  NativeListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      int index) {
    super(id, adapter, establishedAttribute, null);
    this.concat = concat;
    this.index = index;
  }

  final NativeListConcatKernel.Selection select(Activation activation) {
    return NativeListConcatKernel.select(concat, activation, index);
  }
}

final class NativeBooleanListConcatIndex extends NativeListConcatIndex
    implements NativeBooleanCapability {
  NativeBooleanListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      int index) {
    super(id, adapter, establishedAttribute, concat, index);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    NativeListConcatKernel.Selection selected = select(activation);
    return NativeListSources.booleanAt(
        selected.source(), selected.raw(), selected.index(), true, adapter);
  }
}

final class NativeIntListConcatIndex extends NativeListConcatIndex implements NativeIntCapability {
  NativeIntListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      int index) {
    super(id, adapter, establishedAttribute, concat, index);
  }

  @Override
  public long evalInt(Activation activation) {
    NativeListConcatKernel.Selection selected = select(activation);
    return NativeListSources.intAt(
        selected.source(), selected.raw(), selected.index(), true, adapter);
  }
}

final class NativeUintListConcatIndex extends NativeListConcatIndex
    implements NativeUintCapability {
  NativeUintListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      int index) {
    super(id, adapter, establishedAttribute, concat, index);
  }

  @Override
  public long evalUint(Activation activation) {
    NativeListConcatKernel.Selection selected = select(activation);
    return NativeListSources.uintAt(
        selected.source(), selected.raw(), selected.index(), true, adapter);
  }
}

final class NativeDoubleListConcatIndex extends NativeListConcatIndex
    implements NativeDoubleCapability {
  NativeDoubleListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      int index) {
    super(id, adapter, establishedAttribute, concat, index);
  }

  @Override
  public double evalDouble(Activation activation) {
    NativeListConcatKernel.Selection selected = select(activation);
    return NativeListSources.doubleAt(
        selected.source(), selected.raw(), selected.index(), true, adapter);
  }
}

final class NativeStringListConcatIndex extends NativeListConcatIndex
    implements NativeStringCapability {
  NativeStringListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      int index) {
    super(id, adapter, establishedAttribute, concat, index);
  }

  @Override
  public String evalString(Activation activation) {
    NativeListConcatKernel.Selection selected = select(activation);
    return NativeListSources.stringAt(
        selected.source(), selected.raw(), selected.index(), true, adapter);
  }
}

final class NativeNullListConcatIndex extends NativeListConcatIndex
    implements NativeNullCapability {
  NativeNullListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      int index) {
    super(id, adapter, establishedAttribute, concat, index);
  }

  @Override
  public void evalNull(Activation activation) {
    NativeListConcatKernel.Selection selected = select(activation);
    NativeListSources.nullAt(selected.source(), selected.raw(), selected.index(), true, adapter);
  }
}
