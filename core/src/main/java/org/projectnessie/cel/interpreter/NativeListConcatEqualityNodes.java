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
import static org.projectnessie.cel.common.types.Err.errIntOverflow;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Overflow.addInt64Checked;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import java.util.Collection;
import java.util.Iterator;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.Overflow.OverflowException;
import org.projectnessie.cel.common.types.ref.TypeAdapter;

/**
 * One equality operand retained as its original expression and ordered exact list sources.
 *
 * <p>The planner can use {@link #from(Interpretable)} for both sides and require at least one
 * operand for which {@link #concatenated()} is true.
 */
final class NativeListConcatEqualityOperand {
  private final Interpretable expression;
  private final NativeListSourceCapability[] sources;
  private final boolean concatenated;

  private NativeListConcatEqualityOperand(
      Interpretable expression, NativeListSourceCapability[] sources, boolean concatenated) {
    this.expression = requireNonNull(expression, "expression");
    this.sources = sources.clone();
    this.concatenated = concatenated;
  }

  static NativeListConcatEqualityOperand from(Interpretable expression) {
    if (expression instanceof NativeListConcat concat) {
      NativeListSourceCapability[] sources = NativeListConcatKernel.collectSources(concat);
      return sources != null
          ? new NativeListConcatEqualityOperand(expression, sources, true)
          : null;
    }
    if (expression instanceof NativeListSourceCapability source && source.exactListSource()) {
      return new NativeListConcatEqualityOperand(
          expression, new NativeListSourceCapability[] {source}, false);
    }
    return null;
  }

  Interpretable expression() {
    return expression;
  }

  NativeListSourceCapability[] sources() {
    return sources.clone();
  }

  boolean concatenated() {
    return concatenated;
  }
}

/** Shared immutable plan for exact scalar equality over two segmented list operands. */
final class NativeListConcatEqualityPlan {
  private static final Object FAILED = new Object();

  private final NativeListSourceCapability[] sources;
  private final int leftSourceCount;
  private final NativeScalarKind kind;
  private final TypeAdapter adapter;

  NativeListConcatEqualityPlan(
      NativeListSourceCapability[] leftSources,
      NativeListSourceCapability[] rightSources,
      NativeScalarKind kind,
      TypeAdapter adapter) {
    requireNonNull(leftSources, "leftSources");
    requireNonNull(rightSources, "rightSources");
    if (leftSources.length == 0 || rightSources.length == 0) {
      throw new IllegalArgumentException("list equality operands require at least one source");
    }
    this.leftSourceCount = leftSources.length;
    this.sources =
        new NativeListSourceCapability[Math.addExact(leftSources.length, rightSources.length)];
    System.arraycopy(leftSources, 0, sources, 0, leftSources.length);
    System.arraycopy(rightSources, 0, sources, leftSources.length, rightSources.length);
    for (NativeListSourceCapability source : sources) {
      if (source == null || !source.exactListSource()) {
        throw new IllegalArgumentException("list equality requires exact list sources");
      }
    }
    this.kind = requireNonNull(kind, "kind");
    if (kind == NativeScalarKind.NULL) {
      throw new IllegalArgumentException("null list equality is not specialized");
    }
    this.adapter = requireNonNull(adapter, "adapter");
  }

  NativeListConcatEqualityPlan(
      NativeListConcatEqualityOperand left,
      NativeListConcatEqualityOperand right,
      NativeScalarKind kind,
      TypeAdapter adapter) {
    this(
        requireNonNull(left, "left").sources(),
        requireNonNull(right, "right").sources(),
        kind,
        adapter);
  }

  boolean eval(Activation activation) {
    Resolved resolved = resolve(activation);
    if (resolved.leftSize != resolved.rightSize) {
      return false;
    }

    SegmentedCursor left =
        new SegmentedCursor(sources, resolved.rawValues, resolved.sizes, 0, leftSourceCount);
    SegmentedCursor right =
        new SegmentedCursor(
            sources, resolved.rawValues, resolved.sizes, leftSourceCount, sources.length);
    try {
      for (long remaining = resolved.leftSize; remaining > 0; remaining--) {
        boolean equal =
            switch (kind) {
              case BOOLEAN -> left.nextBoolean(adapter) == right.nextBoolean(adapter);
              case INT -> left.nextInteger(adapter, false) == right.nextInteger(adapter, false);
              case UINT -> left.nextInteger(adapter, true) == right.nextInteger(adapter, true);
              case DOUBLE -> left.nextDouble(adapter) == right.nextDouble(adapter);
              case STRING -> left.nextString(adapter).equals(right.nextString(adapter));
              case NULL -> throw new IllegalStateException("null list equality is not specialized");
            };
        if (!equal) {
          return false;
        }
      }
      return true;
    } catch (ValueSignal failure) {
      throw failure;
    } catch (Exception failure) {
      throw signal(newErr(failure, failure.toString()));
    }
  }

  int leftSourceCount() {
    return leftSourceCount;
  }

  int rightSourceCount() {
    return sources.length - leftSourceCount;
  }

  private Resolved resolve(Activation activation) {
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

    long leftSize = 0L;
    long rightSize = 0L;
    boolean leftOverflowed = false;
    boolean rightOverflowed = false;
    for (int i = 0; i < sources.length; i++) {
      if (rawValues[i] == FAILED) {
        continue;
      }
      try {
        int size = NativeListSources.size(sources[i], rawValues[i], true);
        sizes[i] = size;
        if (i < leftSourceCount) {
          if (!leftOverflowed) {
            leftSize = addInt64Checked(leftSize, size);
          }
        } else if (!rightOverflowed) {
          rightSize = addInt64Checked(rightSize, size);
        }
      } catch (ValueSignal failure) {
        if (i < earliestFailureIndex) {
          earliestFailure = failure;
          earliestFailureIndex = i;
        }
      } catch (OverflowException failure) {
        if (i < leftSourceCount) {
          leftOverflowed = true;
        } else {
          rightOverflowed = true;
        }
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
    return new Resolved(rawValues, sizes, leftSize, rightSize);
  }

  private record Resolved(Object[] rawValues, int[] sizes, long leftSize, long rightSize) {}

  /**
   * Encounter-order cursor over a contiguous range of already-resolved list sources.
   *
   * <p>It keeps collection iterators local to one evaluation and never indexes a general collection
   * repeatedly.
   */
  private static final class SegmentedCursor {
    private final NativeListSourceCapability[] sources;
    private final Object[] rawValues;
    private final int[] sizes;
    private final int endSource;
    private int sourceIndex;
    private int elementIndex;
    private Iterator<?> iterator;

    private SegmentedCursor(
        NativeListSourceCapability[] sources,
        Object[] rawValues,
        int[] sizes,
        int startSource,
        int endSource) {
      this.sources = sources;
      this.rawValues = rawValues;
      this.sizes = sizes;
      this.sourceIndex = startSource;
      this.endSource = endSource;
    }

    boolean nextBoolean(TypeAdapter adapter) {
      Object value = nextObject();
      if (value instanceof Boolean bool) {
        return bool;
      }
      return NativeSupport.booleanValue(adapter, currentSource().materializeResolvedElement(value));
    }

    long nextInteger(TypeAdapter adapter, boolean unsigned) {
      advanceToValue();
      Object raw = rawValues[sourceIndex];
      int current = elementIndex++;
      if (!unsigned && raw instanceof int[] values) {
        return values[current];
      }
      if (raw instanceof long[] values) {
        return values[current];
      }

      Object value = objectAt(raw, current);
      NativeListSourceCapability source = sources[sourceIndex];
      if (unsigned) {
        if (value instanceof Long bits) {
          return bits;
        }
        if (value instanceof ULong uint) {
          return uint.longValue();
        }
        return NativeSupport.uintValue(adapter, source.materializeResolvedElement(value));
      }
      if (value instanceof Byte
          || value instanceof Short
          || value instanceof Integer
          || value instanceof Long) {
        return ((Number) value).longValue();
      }
      return NativeSupport.intValue(adapter, source.materializeResolvedElement(value));
    }

    double nextDouble(TypeAdapter adapter) {
      advanceToValue();
      Object raw = rawValues[sourceIndex];
      int current = elementIndex++;
      if (raw instanceof double[] values) {
        return values[current];
      }
      Object value = objectAt(raw, current);
      if (value instanceof Float || value instanceof Double) {
        return ((Number) value).doubleValue();
      }
      return NativeSupport.doubleValue(adapter, currentSource().materializeResolvedElement(value));
    }

    String nextString(TypeAdapter adapter) {
      Object value = nextObject();
      if (value instanceof String string) {
        return string;
      }
      return NativeSupport.stringValue(adapter, currentSource().materializeResolvedElement(value));
    }

    private Object nextObject() {
      advanceToValue();
      return objectAt(rawValues[sourceIndex], elementIndex++);
    }

    private Object objectAt(Object raw, int current) {
      if (raw instanceof Collection<?> collection) {
        if (iterator == null) {
          iterator = collection.iterator();
        }
        return iterator.next();
      }
      if (raw instanceof Object[] values) {
        return values[current];
      }
      if (raw instanceof int[] values) {
        return values[current];
      }
      if (raw instanceof long[] values) {
        return values[current];
      }
      if (raw instanceof double[] values) {
        return values[current];
      }
      throw new IllegalStateException("unsupported exact list source " + raw.getClass());
    }

    private NativeListSourceCapability currentSource() {
      return sources[sourceIndex];
    }

    private void advanceToValue() {
      while (sourceIndex < endSource && elementIndex == sizes[sourceIndex]) {
        sourceIndex++;
        elementIndex = 0;
        iterator = null;
      }
      if (sourceIndex == endSource) {
        throw new IllegalStateException("list equality cursor exhausted");
      }
    }
  }
}

/** Encounter-order CEL equality over exact scalar list-concatenation sources. */
final class NativeListConcatEquality extends EvalEq implements NativeBooleanCapability {
  private final NativeListConcatEqualityPlan plan;

  NativeListConcatEquality(
      long id,
      NativeListConcatEqualityOperand left,
      NativeListConcatEqualityOperand right,
      NativeScalarKind kind,
      TypeAdapter adapter) {
    super(
        id, requireNonNull(left, "left").expression(), requireNonNull(right, "right").expression());
    this.plan = new NativeListConcatEqualityPlan(left, right, kind, adapter);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return plan.eval(activation);
  }

  int leftSourceCount() {
    return plan.leftSourceCount();
  }

  int rightSourceCount() {
    return plan.rightSourceCount();
  }
}

/** Encounter-order CEL inequality over exact scalar list-concatenation sources. */
final class NativeListConcatInequality extends EvalNe implements NativeBooleanCapability {
  private final NativeListConcatEqualityPlan plan;

  NativeListConcatInequality(
      long id,
      NativeListConcatEqualityOperand left,
      NativeListConcatEqualityOperand right,
      NativeScalarKind kind,
      TypeAdapter adapter) {
    super(
        id, requireNonNull(left, "left").expression(), requireNonNull(right, "right").expression());
    this.plan = new NativeListConcatEqualityPlan(left, right, kind, adapter);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return !plan.eval(activation);
  }

  int leftSourceCount() {
    return plan.leftSourceCount();
  }

  int rightSourceCount() {
    return plan.rightSourceCount();
  }
}
