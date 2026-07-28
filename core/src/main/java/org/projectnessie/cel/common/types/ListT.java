/*
 * Copyright (C) 2021 The Authors of CEL-Java
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
package org.projectnessie.cel.common.types;

import static java.util.Arrays.asList;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noMoreElements;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Err.valOrErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.Types.boolOf;

import com.google.protobuf.Any;
import com.google.protobuf.ListValue;
import com.google.protobuf.Value;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Trait;

/**
 * Base class and factories for CEL list values.
 *
 * <p>Factories adapt elements lazily where possible. Array-backed lists retain their source array,
 * and {@link #newGenericList(TypeAdapter, List)} retains a live Java-list view. Callers must keep
 * retained sources stable while a CEL operation or program evaluation consumes them.
 */
public abstract class ListT extends BaseVal implements Lister {
  /** ListType singleton. */
  public static final Type ListType =
      TypeT.newTypeValue(
          TypeEnum.List,
          Trait.AdderType,
          Trait.ContainerType,
          Trait.IndexerType,
          Trait.IterableType,
          Trait.SizerType);

  /** Creates a CEL list backed by a string array. */
  public static Val newStringArrayList(String[] value) {
    return newGenericArrayList(v -> stringOf((String) v), value);
  }

  /** Creates a CEL list backed by an object array and adapting elements on access. */
  public static Val newGenericArrayList(TypeAdapter adapter, Object[] value) {
    return new GenericListT(adapter, value);
  }

  /**
   * Returns a CEL list backed by a live view of {@code value}.
   *
   * <p>Mutations completed between CEL operations are visible through the returned value. The
   * caller must not mutate the list while a CEL operation or program evaluation is consuming it.
   */
  public static Val newGenericList(TypeAdapter adapter, List<?> value) {
    return new ListBackedListT(adapter, value);
  }

  /** Creates a CEL list backed by an {@code int[]} array. */
  public static Val newIntArrayList(TypeAdapter adapter, int[] value) {
    return new IntArrayListT(adapter, value);
  }

  /** Creates a CEL list backed by a {@code long[]} array. */
  public static Val newLongArrayList(TypeAdapter adapter, long[] value) {
    return new LongArrayListT(adapter, value);
  }

  /** Creates a CEL list backed by a {@code double[]} array. */
  public static Val newDoubleArrayList(TypeAdapter adapter, double[] value) {
    return new DoubleArrayListT(adapter, value);
  }

  /** Creates a CEL list backed by an array of already adapted values. */
  public static Val newValArrayList(TypeAdapter adapter, Val[] value) {
    return new ValListT(adapter, value);
  }

  @Override
  public Type type() {
    return ListType;
  }

  abstract static class BaseListT extends ListT {
    protected final TypeAdapter adapter;

    BaseListT(TypeAdapter adapter) {
      this.adapter = adapter;
    }

    abstract int currentSize();

    @SuppressWarnings({"removal", "unchecked"})
    @Override
    public <T> T convertToNative(Class<T> typeDesc) {
      if (typeDesc.isArray()) {
        Object array = toJavaArray(typeDesc);

        return (T) array;
      }
      if (typeDesc == List.class || typeDesc == Object.class) {
        return (T) toJavaList();
      }
      if (typeDesc == ListValue.class) {
        return (T) toPbListValue();
      }
      if (typeDesc == Value.class) {
        return (T) toPbValue();
      }
      if (typeDesc == Any.class) {
        ListValue v = toPbListValue();
        //        Descriptor anyDesc = Any.getDescriptor();
        //        FieldDescriptor anyFieldTypeUrl = anyDesc.findFieldByName("type_url");
        //        FieldDescriptor anyFieldValue = anyDesc.findFieldByName("value");
        //        DynamicMessage dyn = DynamicMessage.newBuilder(Any.getDefaultInstance())
        //            .setField(anyFieldTypeUrl, )
        //            .setField(anyFieldValue, v.toByteString())
        //            .build();

        //        return (T) dyn;
        //        return (T)
        // Any.newBuilder().setTypeUrl("type.googleapis.com/google.protobuf.ListValue").setValue(dyn.toByteString()).build();
        return (T)
            Any.newBuilder()
                .setTypeUrl("type.googleapis.com/google.protobuf.ListValue")
                .setValue(v.toByteString())
                .build();
      }
      throw new IllegalArgumentException(
          String.format("Unsupported conversion of '%s' to '%s'", ListType, typeDesc.getName()));
    }

    private Value toPbValue() {
      return Value.newBuilder().setListValue(toPbListValue()).build();
    }

    private ListValue toPbListValue() {
      ListValue.Builder list = ListValue.newBuilder();
      int s = nativeSize();
      for (int i = 0; i < s; i++) {
        Val v = getUnchecked(i);
        Value e = adapter.valueToNative(v, Value.class);
        list.addValues(e);
      }
      return list.build();
    }

    private List<Object> toJavaList() {
      return asList((Object[]) toJavaArray(Object[].class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Object toJavaArray(Class<T> typeDesc) {
      int s = nativeSize();
      Class compType = typeDesc.getComponentType();
      if (compType == Enum.class) {
        // Note: cannot create `Enum` values of the right type here.
        compType = Object.class;
      }
      Object array = Array.newInstance(compType, s);

      for (int i = 0; i < s; i++) {
        Val v = getUnchecked(i);
        Object e = adapter.valueToNative(v, compType);
        Array.set(array, i, e);
      }
      return array;
    }

    @Override
    public Val convertToType(Type typeValue) {
      return switch (typeValue.typeEnum()) {
        case List -> this;
        case Type -> ListType;
        default -> newTypeConversionError(ListType, typeValue);
      };
    }

    @Override
    public IteratorT iterator() {
      return new ArrayListIteratorT(nativeSize());
    }

    abstract Val getUnchecked(int index);

    static Val elementAt(Lister list, int index) {
      return list instanceof BaseListT baseList
          ? baseList.getUnchecked(index)
          : list.nativeGetAt(index);
    }

    final Object nativeListElement(Val value, Class<?> componentType) {
      // A Java Long is a CEL int when adapted again. Preserve CEL uint's type while still avoiding
      // embedded Val instances in raw Object arrays produced by concatenation.
      return componentType == Object.class && value instanceof UintT
          ? adapter.valueToNative(value, ULong.class)
          : adapter.valueToNative(value, componentType);
    }

    @Override
    public Val nativeGetAt(int index) {
      int currentSize = nativeSize();
      if (index < 0 || index >= currentSize) {
        return newErr(
            "invalid_argument: index '%d' out of range in list of size '%d'", index, currentSize);
      }
      return getUnchecked(index);
    }

    @Override
    public Val equal(Val other) {
      if (!(other instanceof ListT o)) {
        return False;
      }
      int currentSize = nativeSize();
      if (currentSize != o.nativeSize()) {
        return False;
      }
      for (int i = 0; i < currentSize; i++) {
        Val e1 = getUnchecked(i);
        if (isError(e1)) {
          return e1;
        }
        Val e2 = elementAt(o, i);
        if (isError(e2)) {
          return e2;
        }
        if (!e1.type().equals(e2.type())) {
          e2 = e2.convertToType(e2.type());
          if (e2.type().typeEnum() == TypeEnum.Err) {
            return noSuchOverload(e1, Operator.Equals.id, e2);
          }
        }
        if (e1.equal(e2) != True) {
          return False;
        }
      }
      return True;
    }

    @Override
    public Val contains(Val value) {
      int currentSize = nativeSize();
      for (int i = 0; i < currentSize; i++) {
        Val elem = getUnchecked(i);
        if (value.equal(elem) == True) {
          return True;
        }
      }
      return False;
    }

    @Override
    public Val size() {
      return intOf(nativeSize());
    }

    @Override
    public final int nativeSize() {
      return currentSize();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Val val)) {
        return false;
      }
      return equal(val) == True;
    }

    @Override
    public int hashCode() {
      int result = 1;
      int currentSize = nativeSize();
      for (int i = 0; i < currentSize; i++) {
        result = 31 * result + getUnchecked(i).hashCode();
      }
      return result;
    }

    int checkedIndex(Val index, int size) {
      switch (index.type().typeEnum()) {
        case Int:
        case Uint:
          break;
        case Double:
          double od = index.doubleValue();
          if (Math.rint(od) != od) {
            throw new InvalidIndexException(newErr("invalid_argument"));
          }
          break;
        default:
          throw new InvalidIndexException(
              valOrErr(index, "unsupported index type '%s' in list", index.type()));
      }
      long longIndex = index.intValue();
      if (longIndex < 0 || longIndex >= size) {
        // Note: the conformance tests assert on 'invalid_argument'
        throw new InvalidIndexException(
            newErr(
                "invalid_argument: index '%d' out of range in list of size '%d'", longIndex, size));
      }
      return (int) longIndex;
    }

    private final class ArrayListIteratorT extends BaseIteratorT {
      private final int size;
      private int index;

      private ArrayListIteratorT(int size) {
        this.size = size;
      }

      @Override
      public Val hasNext() {
        return boolOf(index < size);
      }

      @Override
      public Val next() {
        if (index < size) {
          return getUnchecked(index++);
        }
        return noMoreElements();
      }
    }
  }

  private static final class InvalidIndexException extends RuntimeException {
    private final Val error;

    private InvalidIndexException(Val error) {
      this.error = error;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

  static final class GenericListT extends BaseListT {
    private final Object[] array;

    GenericListT(TypeAdapter adapter, Object[] array) {
      super(adapter);
      this.array = array;
    }

    @Override
    int currentSize() {
      return array.length;
    }

    @Override
    public Object value() {
      return array;
    }

    @Override
    public Val add(Val other) {
      if (!(other instanceof Lister otherList)) {
        return noSuchOverload(this, "add", other);
      }
      int otherSize = otherList.nativeSize();
      Object[] newArray = Arrays.copyOf(array, array.length + otherSize);
      Class<?> componentType = array.getClass().getComponentType();
      for (int i = 0; i < otherSize; i++) {
        Val otherValue = elementAt(otherList, i);
        newArray[array.length + i] =
            componentType != Object.class && componentType.isInstance(otherValue)
                ? otherValue
                : nativeListElement(otherValue, componentType);
      }
      return new GenericListT(adapter, newArray);
    }

    @Override
    public Val get(Val index) {
      int i;
      try {
        i = checkedIndex(index, array.length);
      } catch (InvalidIndexException e) {
        return e.error;
      }

      return getUnchecked(i);
    }

    @Override
    Val getUnchecked(int index) {
      return adapter.nativeToValue(array[index]);
    }

    @Override
    public String toString() {
      return "GenericListT{"
          + "array="
          + Arrays.toString(array)
          + ", adapter="
          + adapter
          + ", size="
          + nativeSize()
          + '}';
    }
  }

  static final class ListBackedListT extends BaseListT {
    private final List<?> list;

    ListBackedListT(TypeAdapter adapter, List<?> list) {
      super(adapter);
      this.list = list;
    }

    @Override
    int currentSize() {
      return list.size();
    }

    @Override
    public Object value() {
      return list;
    }

    @Override
    public Val add(Val other) {
      if (!(other instanceof Lister otherList)) {
        return noSuchOverload(this, "add", other);
      }
      int thisSize = nativeSize();
      int otherSize = otherList.nativeSize();
      Object[] newArray = new Object[thisSize + otherSize];
      for (int i = 0; i < thisSize; i++) {
        newArray[i] = list.get(i);
      }
      for (int i = 0; i < otherSize; i++) {
        newArray[thisSize + i] = nativeListElement(elementAt(otherList, i), Object.class);
      }
      return new GenericListT(adapter, newArray);
    }

    @Override
    public Val get(Val index) {
      int i;
      try {
        i = checkedIndex(index, nativeSize());
      } catch (InvalidIndexException e) {
        return e.error;
      }

      return getUnchecked(i);
    }

    @Override
    Val getUnchecked(int index) {
      return adapter.nativeToValue(list.get(index));
    }
  }

  static final class ValListT extends BaseListT {
    private final Val[] array;

    ValListT(TypeAdapter adapter, Val[] array) {
      super(adapter);
      this.array = array;
    }

    @Override
    int currentSize() {
      return array.length;
    }

    @Override
    public Object value() {
      Object[] nativeArray = new Object[array.length];
      for (int i = 0; i < array.length; i++) {
        nativeArray[i] = array[i].value();
      }
      return nativeArray;
    }

    @Override
    public Val add(Val other) {
      if (!(other instanceof Lister otherLister)) {
        return noSuchOverload(this, "add", other);
      }
      if (other instanceof ValListT valListT) {
        Val[] otherArray = valListT.array;
        Val[] newArray = Arrays.copyOf(array, array.length + otherArray.length);
        System.arraycopy(otherArray, 0, newArray, array.length, otherArray.length);
        return new ValListT(adapter, newArray);
      } else {
        int otherSize = otherLister.nativeSize();
        Val[] newArray = Arrays.copyOf(array, array.length + otherSize);
        for (int i = 0; i < otherSize; i++) {
          newArray[array.length + i] = elementAt(otherLister, i);
        }
        return new ValListT(adapter, newArray);
      }
    }

    @Override
    public Val get(Val index) {
      int i;
      try {
        i = checkedIndex(index, array.length);
      } catch (InvalidIndexException e) {
        return e.error;
      }
      return getUnchecked(i);
    }

    @Override
    Val getUnchecked(int index) {
      return array[index];
    }

    @Override
    public String toString() {
      return "ValListT{"
          + "array="
          + Arrays.toString(array)
          + ", adapter="
          + adapter
          + ", size="
          + nativeSize()
          + '}';
    }
  }

  abstract static class PrimitiveArrayListT extends BaseListT {
    PrimitiveArrayListT(TypeAdapter adapter) {
      super(adapter);
    }

    @Override
    public Val add(Val other) {
      if (!(other instanceof Lister otherLister)) {
        return noSuchOverload(this, "add", other);
      }
      int thisSize = nativeSize();
      int otherSize = otherLister.nativeSize();
      Val[] newArray = new Val[thisSize + otherSize];
      for (int i = 0; i < thisSize; i++) {
        newArray[i] = getUnchecked(i);
      }
      for (int i = 0; i < otherSize; i++) {
        newArray[thisSize + i] = elementAt(otherLister, i);
      }
      return new ValListT(adapter, newArray);
    }
  }

  static final class IntArrayListT extends PrimitiveArrayListT {
    private final int[] array;

    IntArrayListT(TypeAdapter adapter, int[] array) {
      super(adapter);
      this.array = array;
    }

    @Override
    int currentSize() {
      return array.length;
    }

    @Override
    public Object value() {
      return array;
    }

    @Override
    public Val get(Val index) {
      int i;
      try {
        i = checkedIndex(index, array.length);
      } catch (InvalidIndexException e) {
        return e.error;
      }
      return getUnchecked(i);
    }

    @Override
    Val getUnchecked(int index) {
      return intOf(array[index]);
    }

    @Override
    public Val contains(Val value) {
      if (value instanceof IntT intValue) {
        long needle = intValue.intValue();
        if (needle >= Integer.MIN_VALUE && needle <= Integer.MAX_VALUE) {
          int intNeedle = (int) needle;
          for (int element : array) {
            if (element == intNeedle) {
              return True;
            }
          }
          return False;
        }
        return False;
      }
      return super.contains(value);
    }

    @Override
    public Val equal(Val other) {
      if (other instanceof IntArrayListT intList) {
        return boolOf(Arrays.equals(array, intList.array));
      }
      if (other instanceof LongArrayListT longList) {
        if (array.length != longList.array.length) {
          return False;
        }
        for (int i = 0; i < array.length; i++) {
          if (array[i] != longList.array[i]) {
            return False;
          }
        }
        return True;
      }
      return super.equal(other);
    }
  }

  static final class LongArrayListT extends PrimitiveArrayListT {
    private final long[] array;

    LongArrayListT(TypeAdapter adapter, long[] array) {
      super(adapter);
      this.array = array;
    }

    @Override
    int currentSize() {
      return array.length;
    }

    @Override
    public Object value() {
      return array;
    }

    @Override
    public Val get(Val index) {
      int i;
      try {
        i = checkedIndex(index, array.length);
      } catch (InvalidIndexException e) {
        return e.error;
      }
      return getUnchecked(i);
    }

    @Override
    Val getUnchecked(int index) {
      return intOf(array[index]);
    }

    @Override
    public Val contains(Val value) {
      if (value instanceof IntT intValue) {
        long needle = intValue.intValue();
        for (long element : array) {
          if (element == needle) {
            return True;
          }
        }
        return False;
      }
      return super.contains(value);
    }

    @Override
    public Val equal(Val other) {
      if (other instanceof LongArrayListT longList) {
        return boolOf(Arrays.equals(array, longList.array));
      }
      if (other instanceof IntArrayListT intList) {
        return intList.equal(this);
      }
      return super.equal(other);
    }
  }

  static final class DoubleArrayListT extends PrimitiveArrayListT {
    private final double[] array;

    DoubleArrayListT(TypeAdapter adapter, double[] array) {
      super(adapter);
      this.array = array;
    }

    @Override
    int currentSize() {
      return array.length;
    }

    @Override
    public Object value() {
      return array;
    }

    @Override
    public Val get(Val index) {
      int i;
      try {
        i = checkedIndex(index, array.length);
      } catch (InvalidIndexException e) {
        return e.error;
      }
      return getUnchecked(i);
    }

    @Override
    Val getUnchecked(int index) {
      return doubleOf(array[index]);
    }

    @Override
    public Val contains(Val value) {
      if (value instanceof DoubleT doubleValue) {
        double needle = doubleValue.doubleValue();
        for (double element : array) {
          if (element == needle) {
            return True;
          }
        }
        return False;
      }
      return super.contains(value);
    }

    @Override
    public Val equal(Val other) {
      if (other instanceof DoubleArrayListT doubleList) {
        if (array.length != doubleList.array.length) {
          return False;
        }
        for (int i = 0; i < array.length; i++) {
          if (array[i] != doubleList.array[i]) {
            return False;
          }
        }
        return True;
      }
      return super.equal(other);
    }
  }

  /** NewJSONList returns a traits.Lister based on structpb.ListValue instance. */
  public static Val newJSONList(TypeAdapter adapter, ListValue l) {
    return newGenericList(adapter, l.getValuesList());
  }
}
