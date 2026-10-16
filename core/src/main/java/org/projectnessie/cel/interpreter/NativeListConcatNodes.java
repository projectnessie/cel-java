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

import static org.projectnessie.cel.common.types.Err.errIntOverflow;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Overflow.addInt64Checked;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import java.util.ArrayDeque;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.Overflow.OverflowException;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.functions.Overload;

/**
 * Checked list concatenation retained as an ordinary established node except when an immediate
 * structural consumer can use its exact sources directly.
 */
final class NativeListConcat extends EvalBinary {
  final int sourceCount;

  NativeListConcat(long id, Interpretable left, Interpretable right, Overload implementation) {
    super(
        id,
        Operator.Add.id,
        Overloads.AddList,
        left,
        right,
        implementation.operandTrait,
        implementation.binary);
    this.sourceCount = Math.addExact(sourceCount(left), sourceCount(right));
  }

  private static int sourceCount(Interpretable operand) {
    if (operand instanceof NativeListConcat concat) {
      return concat.sourceCount;
    }
    if (operand instanceof NativeListSourceCapability source && source.exactListSource()) {
      return 1;
    }
    throw new IllegalArgumentException("list concat operand is not an exact list source");
  }
}

/** Flattens and resolves exact concat sources before applying structural list operations. */
final class NativeListConcatKernel {
  private static final Object FAILED = new Object();

  private NativeListConcatKernel() {}

  static NativeListSourceCapability[] collectSources(NativeListConcat concat) {
    NativeListSourceCapability[] sources = new NativeListSourceCapability[concat.sourceCount];
    ArrayDeque<Interpretable> pending = new ArrayDeque<>();
    pending.push(concat);
    int sourceIndex = 0;
    while (!pending.isEmpty()) {
      Interpretable operand = pending.pop();
      if (operand instanceof NativeListConcat nested) {
        pending.push(nested.rhs);
        pending.push(nested.lhs);
      } else if (operand instanceof NativeListSourceCapability source && source.exactListSource()) {
        if (sourceIndex == sources.length) {
          return null;
        }
        sources[sourceIndex++] = source;
      } else {
        return null;
      }
    }
    return sourceIndex == sources.length ? sources : null;
  }

  static long size(NativeListSourceCapability[] sources, Activation activation) {
    if (sources.length == 2) {
      ResolvedPair pair = resolvePair(sources, activation);
      return (long) pair.leftSize + pair.rightSize;
    }
    return resolveMany(sources, activation).totalSize;
  }

  /**
   * Resolves and sizes every concat source without visiting an element.
   *
   * <p>The returned state can subsequently traverse the resolved sources without replaying any
   * source expression. Resolution preserves the same earliest-source failure precedence as the
   * existing structural concat consumers.
   */
  static NativeResolvedListTraversal resolveTraversal(
      NativeListSourceCapability[] sources, Activation activation) {
    if (sources.length == 2) {
      return new ResolvedPairTraversal(sources, resolvePair(sources, activation));
    }
    return new ResolvedSourcesTraversal(sources, resolveMany(sources, activation));
  }

  static Selection select(NativeListSourceCapability[] sources, Activation activation, long index) {
    if (sources.length == 2) {
      return selectPair(sources, resolvePair(sources, activation), index);
    }
    return selectMany(sources, resolveMany(sources, activation), index);
  }

  static Selection select(
      NativeListSourceCapability[] sources,
      Activation activation,
      NativeIntCapability dynamicIndex) {
    if (sources.length == 2) {
      ResolvedPair resolved = resolvePair(sources, activation);
      return selectPair(sources, resolved, evalIndex(dynamicIndex, activation));
    }
    ResolvedSources resolved = resolveMany(sources, activation);
    return selectMany(sources, resolved, evalIndex(dynamicIndex, activation));
  }

  static Selection selectMaterializedElement(
      NativeListSourceCapability[] sources, Activation activation, long index) {
    if (sources.length == 2) {
      return selectPair(sources, resolvePair(sources, activation, true), index);
    }
    return selectMany(sources, resolveMany(sources, activation, true), index);
  }

  static Selection selectMaterializedElement(
      NativeListSourceCapability[] sources,
      Activation activation,
      NativeIntCapability dynamicIndex) {
    if (sources.length == 2) {
      ResolvedPair resolved = resolvePair(sources, activation, true);
      return selectPair(sources, resolved, evalIndex(dynamicIndex, activation));
    }
    ResolvedSources resolved = resolveMany(sources, activation, true);
    return selectMany(sources, resolved, evalIndex(dynamicIndex, activation));
  }

  private static long evalIndex(NativeIntCapability dynamicIndex, Activation activation) {
    try {
      return dynamicIndex.evalInt(activation);
    } catch (ValueSignal failure) {
      if (isUnknownOrError(failure.value)) {
        throw failure;
      }
      throw signal(newErr("unsupported index type '%s' in list", failure.value.type()));
    } catch (Exception failure) {
      throw signal(newErr(failure, failure.toString()));
    }
  }

  private static ResolvedPair resolvePair(
      NativeListSourceCapability[] sources, Activation activation) {
    return resolvePair(sources, activation, false);
  }

  private static ResolvedPair resolvePair(
      NativeListSourceCapability[] sources, Activation activation, boolean materializedElement) {
    NativeListSourceCapability leftSource = sources[0];
    NativeListSourceCapability rightSource = sources[1];
    Object leftRaw = null;
    Object rightRaw = null;
    ValueSignal leftFailure = null;
    ValueSignal rightFailure = null;

    try {
      leftRaw = leftSource.evalRaw(activation);
    } catch (ValueSignal failure) {
      leftFailure = failure;
    } catch (Exception failure) {
      leftFailure = signal(newErr(failure, failure.toString()));
    }
    try {
      rightRaw = rightSource.evalRaw(activation);
    } catch (ValueSignal failure) {
      rightFailure = failure;
    } catch (Exception failure) {
      rightFailure = signal(newErr(failure, failure.toString()));
    }

    int leftSize = 0;
    int rightSize = 0;
    if (leftFailure == null) {
      try {
        leftSize =
            materializedElement
                ? NativeListSources.materializedElementSize(leftSource, leftRaw)
                : NativeListSources.size(leftSource, leftRaw, true);
      } catch (ValueSignal failure) {
        leftFailure = failure;
      } catch (Exception failure) {
        leftFailure = signal(newErr(failure, failure.toString()));
      }
    }
    if (rightFailure == null) {
      try {
        rightSize =
            materializedElement
                ? NativeListSources.materializedElementSize(rightSource, rightRaw)
                : NativeListSources.size(rightSource, rightRaw, true);
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
    return new ResolvedPair(leftRaw, rightRaw, leftSize, rightSize);
  }

  private static ResolvedSources resolveMany(
      NativeListSourceCapability[] sources, Activation activation) {
    return resolveMany(sources, activation, false);
  }

  private static ResolvedSources resolveMany(
      NativeListSourceCapability[] sources, Activation activation, boolean materializedElement) {
    Object[] rawValues = new Object[sources.length];
    int[] sizes = new int[sources.length];
    ValueSignal earliestFailure = null;
    int earliestFailureIndex = Integer.MAX_VALUE;

    for (int i = 0; i < sources.length; i++) {
      try {
        rawValues[i] = sources[i].evalRaw(activation);
      } catch (ValueSignal failure) {
        rawValues[i] = FAILED;
        if (i < earliestFailureIndex) {
          earliestFailure = failure;
          earliestFailureIndex = i;
        }
      } catch (Exception failure) {
        rawValues[i] = FAILED;
        if (i < earliestFailureIndex) {
          earliestFailure = signal(newErr(failure, failure.toString()));
          earliestFailureIndex = i;
        }
      }
    }

    long totalSize = 0L;
    boolean overflowed = false;
    for (int i = 0; i < sources.length; i++) {
      if (rawValues[i] == FAILED) {
        continue;
      }
      try {
        sizes[i] =
            materializedElement
                ? NativeListSources.materializedElementSize(sources[i], rawValues[i])
                : NativeListSources.size(sources[i], rawValues[i], true);
        if (!overflowed) {
          totalSize = addInt64Checked(totalSize, sizes[i]);
        }
      } catch (ValueSignal failure) {
        if (i < earliestFailureIndex) {
          earliestFailure = failure;
          earliestFailureIndex = i;
        }
      } catch (OverflowException failure) {
        overflowed = true;
        if (i < earliestFailureIndex) {
          earliestFailure = signal(errIntOverflow);
          earliestFailureIndex = i;
        }
      } catch (Exception failure) {
        if (i < earliestFailureIndex) {
          earliestFailure = signal(newErr(failure, failure.toString()));
          earliestFailureIndex = i;
        }
      }
    }

    if (earliestFailure != null) {
      throw earliestFailure;
    }
    return new ResolvedSources(rawValues, sizes, totalSize);
  }

  private static Selection selectPair(
      NativeListSourceCapability[] sources, ResolvedPair resolved, long index) {
    long size = (long) resolved.leftSize + resolved.rightSize;
    if (index < 0 || index >= size) {
      throw signal(
          newErr("invalid_argument: index '%d' out of range in list of size '%d'", index, size));
    }
    return index < resolved.leftSize
        ? new Selection(sources[0], resolved.leftRaw, Math.toIntExact(index))
        : new Selection(sources[1], resolved.rightRaw, Math.toIntExact(index - resolved.leftSize));
  }

  private static Selection selectMany(
      NativeListSourceCapability[] sources, ResolvedSources resolved, long index) {
    if (index < 0 || index >= resolved.totalSize) {
      throw signal(
          newErr(
              "invalid_argument: index '%d' out of range in list of size '%d'",
              index, resolved.totalSize));
    }
    long localIndex = index;
    for (int i = 0; i < sources.length; i++) {
      int sourceSize = resolved.sizes[i];
      if (localIndex < sourceSize) {
        return new Selection(sources[i], resolved.rawValues[i], Math.toIntExact(localIndex));
      }
      localIndex -= sourceSize;
    }
    throw new IllegalStateException("validated concat index did not select a source");
  }

  private static boolean traverseSource(
      NativeListSourceCapability source,
      Object raw,
      NativeScalarKind elementKind,
      NativeLoopBinding binding,
      NativeScalarLoopConsumer consumer) {
    try {
      return NativeListSources.traverseResolved(source, raw, elementKind, binding, consumer, false);
    } catch (ValueSignal failure) {
      throw failure;
    } catch (Exception failure) {
      throw signal(newErr(failure, failure.toString()));
    }
  }

  private static void prepareTotalCapacity(NativeScalarLoopConsumer consumer, long totalSize) {
    if (totalSize <= Integer.MAX_VALUE) {
      consumer.prepareCapacity((int) totalSize);
    }
  }

  private record ResolvedPair(Object leftRaw, Object rightRaw, int leftSize, int rightSize) {}

  private record ResolvedSources(Object[] rawValues, int[] sizes, long totalSize) {}

  private record ResolvedPairTraversal(NativeListSourceCapability[] sources, ResolvedPair resolved)
      implements NativeResolvedListTraversal {
    @Override
    public boolean traverse(
        NativeScalarKind elementKind,
        NativeLoopBinding binding,
        NativeScalarLoopConsumer consumer) {
      prepareTotalCapacity(consumer, (long) resolved.leftSize + resolved.rightSize);
      return traverseSource(sources[0], resolved.leftRaw, elementKind, binding, consumer)
          || traverseSource(sources[1], resolved.rightRaw, elementKind, binding, consumer);
    }
  }

  private record ResolvedSourcesTraversal(
      NativeListSourceCapability[] sources, ResolvedSources resolved)
      implements NativeResolvedListTraversal {
    @Override
    public boolean traverse(
        NativeScalarKind elementKind,
        NativeLoopBinding binding,
        NativeScalarLoopConsumer consumer) {
      prepareTotalCapacity(consumer, resolved.totalSize);
      for (int i = 0; i < sources.length; i++) {
        if (traverseSource(sources[i], resolved.rawValues[i], elementKind, binding, consumer)) {
          return true;
        }
      }
      return false;
    }
  }

  record Selection(NativeListSourceCapability source, Object raw, int index) {}
}

final class NativeListConcatSize extends EvalUnary implements NativeIntCapability {
  private final NativeListSourceCapability[] sources;

  NativeListConcatSize(
      long id,
      String function,
      String overload,
      NativeListConcat concat,
      NativeListSourceCapability[] sources,
      Overload implementation) {
    super(id, function, overload, concat, implementation.operandTrait, implementation.unary);
    this.sources = sources;
  }

  @Override
  public long evalInt(Activation activation) {
    return NativeListConcatKernel.size(sources, activation);
  }

  int sourceCount() {
    return sources.length;
  }
}

abstract class NativeListConcatIndex extends NativeScalarAttr {
  private final NativeListConcat concat;
  private final NativeListSourceCapability[] sources;
  private final long constantIndex;
  private final NativeIntCapability dynamicIndex;

  NativeListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      NativeListSourceCapability[] sources,
      long index) {
    super(id, adapter, establishedAttribute, null);
    this.concat = concat;
    this.sources = sources;
    this.constantIndex = index;
    this.dynamicIndex = null;
  }

  NativeListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      NativeListSourceCapability[] sources,
      NativeIntCapability dynamicIndex) {
    super(id, adapter, establishedAttribute, null);
    this.concat = concat;
    this.sources = sources;
    this.constantIndex = 0L;
    this.dynamicIndex = dynamicIndex;
  }

  final NativeListConcatKernel.Selection select(Activation activation) {
    return dynamicIndex == null
        ? NativeListConcatKernel.select(sources, activation, constantIndex)
        : NativeListConcatKernel.select(sources, activation, dynamicIndex);
  }

  final int sourceCount() {
    return concat.sourceCount;
  }
}

final class NativeValueListConcatIndex extends AbstractEval implements Coster {
  private final Attribute establishedAttribute;
  private final NativeListSourceCapability[] sources;
  private final long constantIndex;
  private final NativeIntCapability dynamicIndex;
  private final int sourceCount;

  NativeValueListConcatIndex(
      long id,
      Attribute establishedAttribute,
      NativeListConcat concat,
      NativeListSourceCapability[] sources,
      long index) {
    super(id);
    this.establishedAttribute = establishedAttribute;
    this.sources = sources;
    this.constantIndex = index;
    this.dynamicIndex = null;
    this.sourceCount = concat.sourceCount;
  }

  NativeValueListConcatIndex(
      long id,
      Attribute establishedAttribute,
      NativeListConcat concat,
      NativeListSourceCapability[] sources,
      NativeIntCapability dynamicIndex) {
    super(id);
    this.establishedAttribute = establishedAttribute;
    this.sources = sources;
    this.constantIndex = 0L;
    this.dynamicIndex = dynamicIndex;
    this.sourceCount = concat.sourceCount;
  }

  @Override
  public Val eval(Activation activation) {
    try {
      NativeListConcatKernel.Selection selected =
          dynamicIndex == null
              ? NativeListConcatKernel.selectMaterializedElement(sources, activation, constantIndex)
              : NativeListConcatKernel.selectMaterializedElement(sources, activation, dynamicIndex);
      return NativeListSources.materializedElementAt(
          selected.source(), selected.raw(), selected.index());
    } catch (ValueSignal failure) {
      return failure.value;
    } catch (Exception failure) {
      return newErr(failure, failure.toString());
    }
  }

  @Override
  public Cost cost() {
    return estimateCost(establishedAttribute);
  }

  int sourceCount() {
    return sourceCount;
  }
}

final class NativeBooleanListConcatIndex extends NativeListConcatIndex
    implements NativeBooleanCapability {
  NativeBooleanListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      NativeListSourceCapability[] sources,
      long index) {
    super(id, adapter, establishedAttribute, concat, sources, index);
  }

  NativeBooleanListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      NativeListSourceCapability[] sources,
      NativeIntCapability dynamicIndex) {
    super(id, adapter, establishedAttribute, concat, sources, dynamicIndex);
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
      NativeListSourceCapability[] sources,
      long index) {
    super(id, adapter, establishedAttribute, concat, sources, index);
  }

  NativeIntListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      NativeListSourceCapability[] sources,
      NativeIntCapability dynamicIndex) {
    super(id, adapter, establishedAttribute, concat, sources, dynamicIndex);
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
      NativeListSourceCapability[] sources,
      long index) {
    super(id, adapter, establishedAttribute, concat, sources, index);
  }

  NativeUintListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      NativeListSourceCapability[] sources,
      NativeIntCapability dynamicIndex) {
    super(id, adapter, establishedAttribute, concat, sources, dynamicIndex);
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
      NativeListSourceCapability[] sources,
      long index) {
    super(id, adapter, establishedAttribute, concat, sources, index);
  }

  NativeDoubleListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      NativeListSourceCapability[] sources,
      NativeIntCapability dynamicIndex) {
    super(id, adapter, establishedAttribute, concat, sources, dynamicIndex);
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
      NativeListSourceCapability[] sources,
      long index) {
    super(id, adapter, establishedAttribute, concat, sources, index);
  }

  NativeStringListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      NativeListSourceCapability[] sources,
      NativeIntCapability dynamicIndex) {
    super(id, adapter, establishedAttribute, concat, sources, dynamicIndex);
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
      NativeListSourceCapability[] sources,
      long index) {
    super(id, adapter, establishedAttribute, concat, sources, index);
  }

  NativeNullListConcatIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeListConcat concat,
      NativeListSourceCapability[] sources,
      NativeIntCapability dynamicIndex) {
    super(id, adapter, establishedAttribute, concat, sources, dynamicIndex);
  }

  @Override
  public void evalNull(Activation activation) {
    NativeListConcatKernel.Selection selected = select(activation);
    NativeListSources.nullAt(selected.source(), selected.raw(), selected.index(), true, adapter);
  }
}
