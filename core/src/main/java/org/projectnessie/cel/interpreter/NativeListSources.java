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

import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.interpreter.AttributeFactory.refResolve;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Sizer;

/**
 * Shared representation dispatch for a resolved list source.
 *
 * <p>The kernel never resolves a source expression. Unsupported or general representations are
 * materialized through the source capability using the already-resolved value.
 */
final class NativeListSources {
  static final int UNSUPPORTED = -1;

  private NativeListSources() {}

  /**
   * Tests membership against an exact host {@link Set} without changing CEL's scalar semantics.
   *
   * <p>The return value is {@link #UNSUPPORTED} when the resolved representation does not match the
   * exact representation for {@code kind}. The caller must then materialize this already-resolved
   * value and continue through the established implementation.
   */
  static int exactSetContains(
      Object raw,
      NativeScalarKind kind,
      boolean booleanNeedle,
      long integerNeedle,
      double doubleNeedle,
      String stringNeedle) {
    if (!(raw instanceof Set<?> values)) {
      return UNSUPPORTED;
    }
    if (values.isEmpty()) {
      return 0;
    }
    boolean contains;
    switch (kind) {
      case BOOLEAN -> contains = values.contains(booleanNeedle);
      case INT -> contains = values.contains(integerNeedle);
      case UINT ->
          contains =
              values.contains(integerNeedle) || values.contains(ULong.valueOf(integerNeedle));
      case DOUBLE -> {
        if (Double.isNaN(doubleNeedle)) {
          return 0;
        }
        contains =
            doubleNeedle == 0.0d
                ? values.contains(0.0d) || values.contains(-0.0d)
                : values.contains(doubleNeedle);
      }
      case STRING -> contains = values.contains(stringNeedle);
      case NULL -> {
        return UNSUPPORTED;
      }
      default -> throw new IllegalStateException("Unhandled scalar kind " + kind);
    }
    return contains ? 1 : 0;
  }

  static int exactListEquals(
      NativeListSourceCapability leftSource,
      Object leftRaw,
      NativeListSourceCapability rightSource,
      Object rightRaw,
      NativeScalarKind kind,
      TypeAdapter adapter) {
    ExactCursor left = ExactCursor.create(leftRaw);
    ExactCursor right = ExactCursor.create(rightRaw);
    if (left == null || right == null) {
      return UNSUPPORTED;
    }
    if (left.size != right.size) {
      return 0;
    }
    for (int i = 0; i < left.size; i++) {
      boolean equal =
          switch (kind) {
            case BOOLEAN ->
                left.nextBoolean(leftSource, adapter) == right.nextBoolean(rightSource, adapter);
            case INT ->
                left.nextInteger(leftSource, adapter, false)
                    == right.nextInteger(rightSource, adapter, false);
            case UINT ->
                left.nextInteger(leftSource, adapter, true)
                    == right.nextInteger(rightSource, adapter, true);
            case DOUBLE ->
                left.nextDouble(leftSource, adapter) == right.nextDouble(rightSource, adapter);
            case STRING ->
                left.nextString(leftSource, adapter).equals(right.nextString(rightSource, adapter));
            case NULL -> throw new IllegalStateException("null list equality is not specialized");
          };
      if (!equal) {
        return 0;
      }
    }
    return 1;
  }

  static int size(NativeListSourceCapability source, Object raw, boolean directArrayAccess) {
    if (source.exactListSource()) {
      int size = directSize(raw);
      if (size >= 0) {
        return size;
      }
    } else if (directArrayAccess) {
      int size = directArraySize(raw);
      if (size >= 0) {
        return size;
      }
    }
    Val materialized = materializedList(source, raw);
    if (materialized instanceof Sizer sizer) {
      return sizer.nativeSize();
    }
    throw signal(newErr("got '%s', expected list type", materialized.getClass().getName()));
  }

  static boolean booleanAt(
      NativeListSourceCapability source,
      Object raw,
      int index,
      boolean directArrayAccess,
      TypeAdapter adapter) {
    Object selected = selected(source, raw, index, directArrayAccess);
    if (source.exactListSource() && !(selected instanceof Boolean)) {
      selected = source.materializeResolvedElement(selected);
    }
    return NativeSupport.booleanValue(adapter, selected);
  }

  static long intAt(
      NativeListSourceCapability source,
      Object raw,
      int index,
      boolean directArrayAccess,
      TypeAdapter adapter) {
    if (directArrayAccess) {
      if (raw instanceof long[] values) {
        checkIndex(index, values.length);
        return values[index];
      }
      if (raw instanceof int[] values) {
        checkIndex(index, values.length);
        return values[index];
      }
    }
    Object selected = selected(source, raw, index, directArrayAccess);
    if (source.exactListSource()
        && (selected instanceof Byte
            || selected instanceof Short
            || selected instanceof Integer
            || selected instanceof Long)) {
      return ((Number) selected).longValue();
    }
    if (source.exactListSource()) {
      selected = source.materializeResolvedElement(selected);
    }
    return NativeSupport.intValue(adapter, selected);
  }

  static long uintAt(
      NativeListSourceCapability source,
      Object raw,
      int index,
      boolean directArrayAccess,
      TypeAdapter adapter) {
    if (source.exactListSource() && directArrayAccess && raw instanceof long[] values) {
      checkIndex(index, values.length);
      return values[index];
    }
    Object selected = selected(source, raw, index, directArrayAccess);
    if (source.exactListSource()) {
      if (selected instanceof Long bits) {
        return bits;
      }
      if (selected instanceof ULong unsigned) {
        return unsigned.longValue();
      }
      selected = source.materializeResolvedElement(selected);
    }
    return NativeSupport.uintValue(adapter, selected);
  }

  static double doubleAt(
      NativeListSourceCapability source,
      Object raw,
      int index,
      boolean directArrayAccess,
      TypeAdapter adapter) {
    if (directArrayAccess && raw instanceof double[] values) {
      checkIndex(index, values.length);
      return values[index];
    }
    Object selected = selected(source, raw, index, directArrayAccess);
    if (source.exactListSource() && (selected instanceof Float || selected instanceof Double)) {
      return ((Number) selected).doubleValue();
    }
    if (source.exactListSource()) {
      selected = source.materializeResolvedElement(selected);
    }
    return NativeSupport.doubleValue(adapter, selected);
  }

  static String stringAt(
      NativeListSourceCapability source,
      Object raw,
      int index,
      boolean directArrayAccess,
      TypeAdapter adapter) {
    Object selected = selected(source, raw, index, directArrayAccess);
    if (source.exactListSource() && !(selected instanceof String)) {
      selected = source.materializeResolvedElement(selected);
    }
    return NativeSupport.stringValue(adapter, selected);
  }

  static void nullAt(
      NativeListSourceCapability source,
      Object raw,
      int index,
      boolean directArrayAccess,
      TypeAdapter adapter) {
    Object selected = selected(source, raw, index, directArrayAccess);
    if (source.exactListSource() && selected != null) {
      selected = source.materializeResolvedElement(selected);
    }
    NativeSupport.nullValue(adapter, selected);
  }

  static boolean traverseResolved(
      NativeListSourceCapability source,
      Object raw,
      NativeScalarKind elementKind,
      NativeLoopBinding binding,
      NativeScalarLoopConsumer consumer) {
    if (elementKind == NativeScalarKind.INT && raw instanceof int[] values) {
      consumer.prepareCapacity(values.length);
      for (int value : values) {
        binding.setInt(value);
        if (consumer.test(binding)) {
          return true;
        }
      }
      return false;
    }
    if (elementKind == NativeScalarKind.INT && raw instanceof long[] values) {
      consumer.prepareCapacity(values.length);
      for (long value : values) {
        binding.setInt(value);
        if (consumer.test(binding)) {
          return true;
        }
      }
      return false;
    }
    if (source.exactListSource()
        && elementKind == NativeScalarKind.UINT
        && raw instanceof long[] values) {
      consumer.prepareCapacity(values.length);
      for (long value : values) {
        binding.setUint(value);
        if (consumer.test(binding)) {
          return true;
        }
      }
      return false;
    }
    if (elementKind == NativeScalarKind.DOUBLE && raw instanceof double[] values) {
      consumer.prepareCapacity(values.length);
      for (double value : values) {
        binding.setDouble(value);
        if (consumer.test(binding)) {
          return true;
        }
      }
      return false;
    }
    if (elementKind == NativeScalarKind.STRING && raw instanceof String[] values) {
      consumer.prepareCapacity(values.length);
      for (String value : values) {
        if (value != null || !source.exactListSource()) {
          binding.setString(value);
        } else {
          binding.setObject(source.materializeResolvedElement(null));
        }
        if (consumer.test(binding)) {
          return true;
        }
      }
      return false;
    }
    if (raw instanceof Object[] values) {
      consumer.prepareCapacity(values.length);
      for (Object value : values) {
        setBindingValue(binding, elementKind, value, source);
        if (consumer.test(binding)) {
          return true;
        }
      }
      return false;
    }
    if (source.exactListSource() && raw instanceof Collection<?> values) {
      consumer.prepareCapacity(values.size());
      for (Object value : values) {
        setBindingValue(binding, elementKind, value, source);
        if (consumer.test(binding)) {
          return true;
        }
      }
      return false;
    }
    return NativeScalarLoopKernel.evaluateMaterialized(
        materializedList(source, raw), binding, consumer);
  }

  private static Object selected(
      NativeListSourceCapability source, Object raw, int index, boolean directArrayAccess) {
    try {
      return selectedUnchecked(source, raw, index, directArrayAccess);
    } catch (ValueSignal valueSignal) {
      throw valueSignal;
    } catch (Exception failure) {
      throw signal(newErr(failure, failure.toString()));
    }
  }

  private static Object selectedUnchecked(
      NativeListSourceCapability source, Object raw, int index, boolean directArrayAccess) {
    if (source.exactListSource()) {
      if (raw instanceof Object[] values) {
        checkIndex(index, values.length);
        return values[index];
      }
      if (raw instanceof List<?> values) {
        checkIndex(index, values.size());
        return values.get(index);
      }
      if (raw instanceof Collection<?> values) {
        checkIndex(index, values.size());
        Iterator<?> iterator = values.iterator();
        for (int current = 0; iterator.hasNext(); current++) {
          Object value = iterator.next();
          if (current == index) {
            return value;
          }
        }
        throw signal(newErr("collection size changed during exact aggregate evaluation"));
      }
    } else if (directArrayAccess && raw instanceof Object[] values) {
      checkIndex(index, values.length);
      Object value = values[index];
      if (value != null) {
        return value;
      }
    }
    if (source instanceof NativeRawIdent identifier) {
      return refResolve(identifier.adapter, intOf(index), raw);
    }
    Val materialized = materializedList(source, raw);
    if (materialized instanceof Lister list) {
      return list.nativeGetAt(index);
    }
    throw signal(newErr("got '%s', expected list type", materialized.getClass().getName()));
  }

  private static Val materializedList(NativeListSourceCapability source, Object raw) {
    Val materialized = source.materializeResolvedList(raw);
    if (isError(materialized) || isUnknown(materialized)) {
      throw signal(materialized);
    }
    return materialized;
  }

  private static int directSize(Object raw) {
    int arraySize = directArraySize(raw);
    if (arraySize >= 0) {
      return arraySize;
    }
    return raw instanceof Collection<?> collection ? collection.size() : -1;
  }

  private static int directArraySize(Object raw) {
    if (raw instanceof int[] values) {
      return values.length;
    }
    if (raw instanceof long[] values) {
      return values.length;
    }
    if (raw instanceof double[] values) {
      return values.length;
    }
    return raw instanceof Object[] values ? values.length : -1;
  }

  private static void setBindingValue(
      NativeLoopBinding binding,
      NativeScalarKind kind,
      Object value,
      NativeListSourceCapability source) {
    if (source.exactListSource()) {
      switch (kind) {
        case INT -> {
          if (value instanceof Byte
              || value instanceof Short
              || value instanceof Integer
              || value instanceof Long) {
            binding.setInt(((Number) value).longValue());
            return;
          }
        }
        case UINT -> {
          if (value instanceof Long bits) {
            binding.setUint(bits);
            return;
          }
          if (value instanceof ULong unsigned) {
            binding.setUint(unsigned.longValue());
            return;
          }
        }
        case DOUBLE -> {
          if (value instanceof Float || value instanceof Double) {
            binding.setDouble(((Number) value).doubleValue());
            return;
          }
        }
        case STRING -> {
          if (value instanceof String string) {
            binding.setString(string);
            return;
          }
        }
        case BOOLEAN -> {
          if (value instanceof Boolean) {
            binding.setObject(value);
            return;
          }
        }
        case NULL -> {
          if (value == null) {
            binding.setObject(null);
            return;
          }
        }
      }
      value = source.materializeResolvedElement(value);
    }
    binding.setObject(value);
  }

  private static void checkIndex(int index, int size) {
    if (index < 0 || index >= size) {
      throw signal(
          newErr("invalid_argument: index '%d' out of range in list of size '%d'", index, size));
    }
  }

  private static final class ExactCursor {
    private final Object values;
    private final int size;
    private Iterator<?> iterator;
    private int index;

    private ExactCursor(Object values, int size) {
      this.values = values;
      this.size = size;
    }

    static ExactCursor create(Object values) {
      int size = directSize(values);
      if (size < 0) {
        return null;
      }
      return new ExactCursor(values, size);
    }

    boolean nextBoolean(NativeListSourceCapability source, TypeAdapter adapter) {
      Object value = nextObject();
      if (value instanceof Boolean bool) {
        return bool;
      }
      return NativeSupport.booleanValue(adapter, source.materializeResolvedElement(value));
    }

    long nextInteger(NativeListSourceCapability source, TypeAdapter adapter, boolean unsigned) {
      if (!unsigned && values instanceof int[] ints) {
        return ints[index++];
      }
      if (values instanceof long[] longs) {
        return longs[index++];
      }
      Object value = nextObject();
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

    double nextDouble(NativeListSourceCapability source, TypeAdapter adapter) {
      if (values instanceof double[] doubles) {
        return doubles[index++];
      }
      Object value = nextObject();
      if (value instanceof Float || value instanceof Double) {
        return ((Number) value).doubleValue();
      }
      return NativeSupport.doubleValue(adapter, source.materializeResolvedElement(value));
    }

    String nextString(NativeListSourceCapability source, TypeAdapter adapter) {
      Object value = nextObject();
      if (value instanceof String string) {
        return string;
      }
      return NativeSupport.stringValue(adapter, source.materializeResolvedElement(value));
    }

    private Object nextObject() {
      if (values instanceof Collection<?> collection) {
        if (iterator == null) {
          iterator = collection.iterator();
        }
        index++;
        return iterator.next();
      }
      int current = index++;
      if (values instanceof Object[] objects) {
        return objects[current];
      }
      if (values instanceof int[] ints) {
        return ints[current];
      }
      if (values instanceof long[] longs) {
        return longs[current];
      }
      if (values instanceof double[] doubles) {
        return doubles[current];
      }
      throw new IllegalStateException("unsupported exact list source " + values.getClass());
    }
  }
}
