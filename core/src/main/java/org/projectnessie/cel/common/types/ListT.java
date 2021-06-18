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
import java.util.function.Function;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Trait;

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

  public static Val newStringArrayList(String[] value) {
    return newGenericArrayList(v -> stringOf((String) v), value);
  }

  public static Val newGenericArrayList(TypeAdapter adapter, Object[] value) {
    return new GenericListT(adapter, value);
  }

  public static Val newValArrayList(TypeAdapter adapter, Val[] value) {
    return new ValListT(adapter, value);
  }

  @Override
  public Type type() {
    return ListType;
  }

  abstract static class BaseListT extends ListT {
    protected final TypeAdapter adapter;
    protected final long size;

    BaseListT(TypeAdapter adapter, long size) {
      this.adapter = adapter;
      this.size = size;
    }

    @SuppressWarnings("unchecked")
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
      int s = (int) size;
      for (int i = 0; i < s; i++) {
        Val v = get(intOf(i));
        Value e = v.convertToNative(Value.class);
        list.addValues(e);
      }
      return list.build();
    }

    private List<Object> toJavaList() {
      return asList(convertToNative(Object[].class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Object toJavaArray(Class<T> typeDesc) {
      int s = (int) size;
      Class compType = typeDesc.getComponentType();
      if (compType == Enum.class) {
        // Note: cannot create `Enum` values of the right type here.
        compType = Object.class;
      }
      Object array = Array.newInstance(compType, s);

      Function<Object, Object> fixForTarget = Function.identity();

      for (int i = 0; i < s; i++) {
        Val v = get(intOf(i));
        Object e = v.convertToNative(compType);
        e = fixForTarget.apply(e);
        Array.set(array, i, e);
      }
      return array;
    }

    @Override
    public Val convertToType(Type typeValue) {
      switch (typeValue.typeEnum()) {
        case List:
          return this;
        case Type:
          return ListType;
      }
      return newTypeConversionError(ListType, typeValue);
    }

    @Override
    public IteratorT iterator() {
      return new ArrayListIteratorT();
    }

    @Override
    public Val equal(Val other) {
      if (other.type() != ListType) {
        return False;
      }
      ListT o = (ListT) other;
      if (size != o.size().intValue()) {
        return False;
      }
      for (long i = 0; i < size; i++) {
        IntT idx = intOf(i);
        Val e1 = get(idx);
        if (isError(e1)) {
          return e1;
        }
        Val e2 = o.get(idx);
        if (isError(e2)) {
          return e2;
        }
        if (e1.type() != e2.type()) {
          return noSuchOverload(e1, Operator.Equals.id, e2);
        }
        if (e1.equal(e2) != True) {
          return False;
        }
      }
      return True;
    }

    @Override
    public Val contains(Val value) {
      Type firstType = null;
      Type mixedType = null;
      for (long i = 0; i < size; i++) {
        Val elem = get(intOf(i));
        Type elemType = elem.type();
        if (firstType == null) {
          firstType = elemType;
        } else if (!firstType.equals(elemType)) {
          mixedType = elemType;
        }
        if (value.equal(elem) == True) {
          return True;
        }
      }
      if (mixedType != null) {
        return noSuchOverload(value, Operator.In.id, firstType, mixedType);
      }
      return False;
    }

    @Override
    public Val size() {
      return intOf(size);
    }

    private class ArrayListIteratorT extends BaseVal implements IteratorT {
      private long index;

      @Override
      public Val hasNext() {
        return boolOf(index < size);
      }

      @Override
      public Val next() {
        if (index < size) {
          return get(intOf(index++));
        }
        return noMoreElements();
      }

      @Override
      public <T> T convertToNative(Class<T> typeDesc) {
        throw new UnsupportedOperationException("IMPLEMENT ME??");
      }

      @Override
      public Val convertToType(Type typeValue) {
        throw new UnsupportedOperationException("IMPLEMENT ME??");
      }

      @Override
      public Val equal(Val other) {
        throw new UnsupportedOperationException("IMPLEMENT ME??");
      }

      @Override
      public Type type() {
        throw new UnsupportedOperationException("IMPLEMENT ME??");
      }

      @Override
      public Object value() {
        throw new UnsupportedOperationException("IMPLEMENT ME??");
      }
    }
  }

  static final class GenericListT extends BaseListT {
    private final Object[] array;

    GenericListT(TypeAdapter adapter, Object[] array) {
      super(adapter, array.length);
      this.array = array;
    }

    @Override
    public Object value() {
      return array;
    }

    @Override
    public Val add(Val other) {
      if (!(other instanceof Lister)) {
        return noSuchOverload(this, "add", other);
      }
      Lister otherList = (Lister) other;
      Object[] otherArray = (Object[]) otherList.value();
      Object[] newArray = Arrays.copyOf(array, array.length + otherArray.length);
      System.arraycopy(otherArray, 0, newArray, array.length, otherArray.length);
      return new GenericListT(adapter, newArray);
    }

    @Override
    public Val get(Val index) {
      if (!(index instanceof IntT)) {
        return valOrErr(index, "unsupported index type '%s' in list", index.type());
      }
      int sz = array.length;
      int i = (int) index.intValue();
      if (i < 0 || i >= sz) {
        // Note: the conformance tests assert on 'invalid_argument'
        return newErr("invalid_argument: index '%d' out of range in list of size '%d'", i, sz);
      }

      return adapter.nativeToValue(array[i]);
    }

    @Override
    public String toString() {
      return "GenericListT{"
          + "array="
          + Arrays.toString(array)
          + ", adapter="
          + adapter
          + ", size="
          + size
          + '}';
    }
  }

  static final class ValListT extends BaseListT {
    private final Val[] array;

    ValListT(TypeAdapter adapter, Val[] array) {
      super(adapter, array.length);
      this.array = array;
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
      if (!(other instanceof Lister)) {
        return noSuchOverload(this, "add", other);
      }
      if (other instanceof ValListT) {
        Val[] otherArray = ((ValListT) other).array;
        Val[] newArray = Arrays.copyOf(array, array.length + otherArray.length);
        System.arraycopy(otherArray, 0, newArray, array.length, otherArray.length);
        return new ValListT(adapter, newArray);
      } else {
        Lister otherLister = (Lister) other;
        int otherSIze = (int) otherLister.size().intValue();
        Val[] newArray = Arrays.copyOf(array, array.length + otherSIze);
        for (int i = 0; i < otherSIze; i++) {
          newArray[array.length + i] = otherLister.get(intOf(i));
        }
        return new ValListT(adapter, newArray);
      }
    }

    @Override
    public Val get(Val index) {
      if (!(index instanceof IntT)) {
        return valOrErr(index, "unsupported index type '%s' in list", index.type());
      }
      int sz = array.length;
      int i = (int) index.intValue();
      if (i < 0 || i >= sz) {
        // Note: the conformance tests assert on 'invalid_argument'
        return newErr("invalid_argument: index '%d' out of range in list of size '%d'", i, sz);
      }
      return array[i];
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ValListT valListT = (ValListT) o;
      return Arrays.equals(array, valListT.array);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + Arrays.hashCode(array);
      return result;
    }

    @Override
    public String toString() {
      return "ValListT{"
          + "array="
          + Arrays.toString(array)
          + ", adapter="
          + adapter
          + ", size="
          + size
          + '}';
    }
  }

  /** NewJSONList returns a traits.Lister based on structpb.ListValue instance. */
  public static Val newJSONList(TypeAdapter adapter, ListValue l) {
    List<Value> vals = l.getValuesList();
    return newGenericArrayList(adapter, vals.toArray());
  }
}
