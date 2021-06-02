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

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.BoolT.boolOf;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;

import java.util.Arrays;
import java.util.List;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Trait;

public abstract class ListT extends BaseVal implements Lister {
  /** ListType singleton. */
  public static final TypeValue ListType =
      TypeValue.newTypeValue(
          "list",
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

  public static Val newWrappedList(TypeAdapter adapter, List<?> value) {
    return newGenericArrayList(adapter, value.toArray());
  }

  @Override
  public Type type() {
    return ListType;
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
      Object[] newArray = Arrays.copyOf(array, array.length + 1);
      newArray[array.length] = other.value();
      return new GenericListT(adapter, newArray);
    }

    @Override
    public Val get(Val index) {
      return wrap(array[(int) index.intValue()]);
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
      return array;
    }

    @Override
    public Val add(Val other) {
      Object[] newArray = Arrays.copyOf(array, array.length + 1);
      newArray[array.length] = other;
      return new GenericListT(adapter, newArray);
    }

    @Override
    public Val get(Val index) {
      return wrap(array[(int) index.intValue()]);
    }
  }

  abstract static class BaseListT extends ListT {
    protected final TypeAdapter adapter;
    protected final long size;

    BaseListT(TypeAdapter adapter, long size) {
      this.adapter = adapter;
      this.size = size;
    }

    protected Val wrap(Object v) {
      return adapter.nativeToValue(v);
    }

    @Override
    public <T> T convertToNative(Class<T> typeDesc) {
      throw new UnsupportedOperationException(); // TODO implement
    }

    @Override
    public Val convertToType(Type typeValue) {
      if (typeValue == ListType) {
        return this;
      }
      if (typeValue == TypeType) {
        return ListType;
      }
      return newErr("type conversion error from '%s' to '%s'", ListType, typeValue);
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
        if (!get(intOf(i)).equal(o.get(intOf(i))).booleanValue()) {
          return False;
        }
      }
      return True;
    }

    @Override
    public Val contains(Val value) {
      for (long i = 0; i < size; i++) {
        if (value.equal(get(intOf(i))).booleanValue()) {
          return True;
        }
      }
      return False;
    }

    @Override
    public Val size() {
      return intOf(size);
    }

    private class ArrayListIteratorT implements IteratorT {
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
        return null;
      }
    }
  }
}
// public abstract class ListT { implements Val, Adder, Container, Indexer, Iterable<Val>, Sizer,
// Lister {
//	/**ListType singleton. */
//	public static final TypeValue ListType = TypeValue.newTypeValue("list",
//			Trait.AdderType,
//			Trait.ContainerType,
//			Trait.IndexerType,
//			Trait.IterableType,
//			Trait.SizerType);
//
//	/**NewDynamicList returns a traits.Lister with heterogenous elements.
//	 * value should be an array of "native" types, i.e. any type that
//	 * NativeToValue() can convert to a ref.Val. */
//	public static Lister NewDynamicList(TypeAdapter adapter, List<?> refValue) {
//		return new BaseListT(adapter, refValue, refValue.size(), refValue::get);
//	}
//
//	/**NewStringList returns a traits.Lister containing only strings. */
//	public static Lister NewStringList(TypeAdapter adapter, String[] elems) {
//		return new BaseListT(adapter, elems, elems.length, i -> StringT.valueOf(elems[i]));
//	}
//
//	/**NewRefValList returns a traits.Lister with ref.Val elements.
//	 *
//	 * This type specialization is used with list literals within CEL expressions.. */
//	public static Lister NewRefValList(TypeAdapter adapter, Val[] elems) {
//		return new BaseListT(adapter, elems, elems.length, i -> elems[i]);
//	}
//
//	static final class BaseListT extends ListT implements TypeAdapter {
//
//		final TypeAdapter adapter;
//
//		/**baseList points to a list containing elements of any type.
//		 * The `value` is an array of native values, and refValue is its reflection object.
//		 * The `ref.TypeAdapter` enables native type to CEL type conversions.. */
//		final Object value;
//
//		/**size indicates the number of elements within the list.
//		 * Since objects are immutable the size of a list is static.. */
//		final int size;
//
//		/**get returns a value at the specified integer index.
//		 * The index is guaranteed to be checked against the list index range.. */
//	  final IntFunction<Object> get;
//
//		public BaseListT(TypeAdapter adapter, Object value, int size,
//				IntFunction<Object> get) {
//			this.adapter = adapter;
//			this.value = value;
//			this.size = size;
//			this.get = get;
//		}
//
//		/**Add implements the traits.Adder interface method.. */
//		@Override
//		public Val add(Val other) {
//			if (!(other instanceof Lister)) {
//				return Err.maybeNoSuchOverloadErr(other);
//			}
//			Lister otherList = (Lister) other;
//			if (size() == IntT.IntZero) {
//				return other;
//			}
//			if (otherList.size() == IntT.IntZero) {
//				return this;
//			}
//			return new ConcatListT(adapter, this, otherList);
//		}
//
//		/**Contains implements the traits.Container interface method.. */
//		@Override
//		public Val contains(Val value) {
//			if IsUnknownOrError(elem) {
//				return elem
//			}
//			var err ref.Val
//			for i := 0; i < l.size; i++ {
//				val := l.NativeToValue(l.get(i))
//				cmp := elem.Equal(val)
//				b, ok := cmp.(Bool)
//				// When there is an error on the contain check, this is not necessarily terminal.
//				// The contains call could find the element and return True, just as though the user
//				// had written a per-element comparison in an exists() macro or logical ||, e.g.
//				//    list.exists(e, e == elem)
//				if !ok && err == nil {
//					err = ValOrErr(cmp, "no such overload")
//				}
//				if b == True {
//					return True
//				}
//			}
//			if err != nil {
//				return err
//			}
//			return False
//		}
//
//		/**ConvertToNative implements the ref.Val interface method.. */
//		@Override
//		public <T> T convertToNative(Class<T> typeDesc) {
//			// If the underlying list value is assignable to the reflected type return it.
//			if reflect.TypeOf(l.value).AssignableTo(typeDesc) {
//				return l.value, nil
//			}
//			// If the list wrapper is assignable to the desired type return it.
//			if reflect.TypeOf(l).AssignableTo(typeDesc) {
//				return l, nil
//			}
//			// Attempt to convert the list to a set of well known protobuf types.
//			switch typeDesc {
//			case anyValueType:
//				json, err := l.ConvertToNative(jsonListValueType)
//				if err != nil {
//					return nil, err
//				}
//				return anypb.New(json.(proto.Message))
//			case jsonValueType, jsonListValueType:
//				jsonValues, err :=
//					l.ConvertToNative(reflect.TypeOf([]*structpb.Value{}))
//				if err != nil {
//					return nil, err
//				}
//				jsonList := &structpb.ListValue{Values: jsonValues.([]*structpb.Value)}
//				if typeDesc == jsonListValueType {
//					return jsonList, nil
//				}
//				return structpb.NewListValue(jsonList), nil
//			}
//			// Non-list conversion.
//			if typeDesc.Kind() != reflect.Slice && typeDesc.Kind() != reflect.Array {
//				return nil, fmt.Errorf("type conversion error from list to '%v'", typeDesc)
//			}
//
//			// List conversion.
//			// Allow the element ConvertToNative() function to determine whether conversion is possible.
//			otherElemType := typeDesc.Elem()
//			elemCount := l.size
//			nativeList := reflect.MakeSlice(typeDesc, elemCount, elemCount)
//			for i := 0; i < elemCount; i++ {
//				elem := l.NativeToValue(l.get(i))
//				nativeElemVal, err := elem.ConvertToNative(otherElemType)
//				if err != nil {
//					return nil, err
//				}
//				nativeList.Index(i).Set(reflect.ValueOf(nativeElemVal))
//			}
//			return nativeList.Interface(), nil
//		}
//
//		/**ConvertToType implements the ref.Val interface method.. */
//		@Override
//		public Val convertToType(Type typeValue) {
//			if (typeValue == ListType) {
//				return this;
//			}
//			if (typeValue == TypeValue.TypeType) {
//				return ListType;
//			}
//			return newErr("type conversion error from '%s' to '%s'", ListType, typeValue);
//		}
//
//		/**Equal implements the ref.Val interface method.. */
//		@Override
//		public Val equal(Val other) {
//			otherList, ok := other.(traits.Lister)
//			if !ok {
//				return MaybeNoSuchOverloadErr(other)
//			}
//			if l.Size() != otherList.Size() {
//				return False
//			}
//			for i := IntZero; i < l.Size().(Int); i++ {
//				thisElem := l.Get(i)
//				otherElem := otherList.Get(i)
//				elemEq := thisElem.Equal(otherElem)
//				if elemEq != True {
//					return elemEq
//				}
//			}
//			return True
//		}
//
//		/**Get implements the traits.Indexer interface method.. */
//		@Override
//		public Val get(Val index) {
//			i, ok := index.(Int)
//			if !ok {
//				return ValOrErr(index, "unsupported index type '%s' in list", index.Type())
//			}
//			iv := int(i)
//			if iv < 0 || iv >= l.size {
//				return NewErr("index '%d' out of range in list size '%d'", i, l.Size())
//			}
//			elem := l.get(iv)
//			return l.NativeToValue(elem)
//		}
//
//		/**Iterator implements the traits.Iterable interface method.. */
//		@Override
//		public Iterator<Val> iterator() {
//			return newListIterator(l)
//		}
//
//		/**Size implements the traits.Sizer interface method.. */
//		@Override
//		public Val size() {
//			return Int(l.size)
//		}
//
//		/**Type implements the ref.Val interface method.. */
//		@Override
//		public Type type() {
//			return ListType
//		}
//
//		/**Value implements the ref.Val interface method.. */
//		@Override
//		public Object value() {
//			return l.value
//		}
//	}
//
//	static final class ConcatListT extends ListT implements TypeAdapter {
//
//		/**concatList combines two list implementations together into a view.
//		 * The `ref.TypeAdapter` enables native type to CEL type conversions.. */
//		private final TypeAdapter adapter;
//		private final Lister prevList;
//		private final Lister nextList;
//
//		ConcatListT(TypeAdapter adapter,
//				Lister prevList, Lister nextList) {
//			this.adapter = adapter;
//			this.prevList = prevList;
//			this.nextList = nextList;
//		}
//
//		/**Add implements the traits.Adder interface method.. */
//		@Override
//		public Val add(Val other) {
//			otherList, ok := other.(traits.Lister)
//			if !ok {
//				return Err.maybeNoSuchOverloadErr(other);
//			}
//			if l.Size() == IntZero {
//				return other
//			}
//			if otherList.Size() == IntZero {
//				return l
//			}
//			return &concatList{
//				TypeAdapter: l.TypeAdapter,
//				prevList:    l,
//				nextList:    otherList}
//		}
//
//		/**Contains implments the traits.Container interface method.. */
//		@Override
//		public Val contains(Val value) {
//			// The concat list relies on the IsErrorOrUnknown checks against the input element to be
//			// performed by the `prevList` and/or `nextList`.
//			prev := l.prevList.Contains(elem)
//			// Short-circuit the return if the elem was found in the prev list.
//			if prev == True {
//				return prev
//			}
//			// Return if the elem was found in the next list.
//			next := l.nextList.Contains(elem)
//			if next == True {
//				return next
//			}
//			// Handle the case where an error or unknown was encountered before checking next.
//			if IsUnknownOrError(prev) {
//				return prev
//			}
//			// Otherwise, rely on the next value as the representative result.
//			return next
//		}
//
//		/**ConvertToNative implements the ref.Val interface method.. */
//		@Override
//		public <T> T convertToNative(Class<T> typeDesc) {
//			combined := NewDynamicList(l.TypeAdapter, l.Value().([]interface{}))
//			return combined.ConvertToNative(typeDesc)
//		}
//
//		/**ConvertToType implements the ref.Val interface method.. */
//		@Override
//		public Val convertToType(Type typeValue) {
//			if (typeValue == ListType) {
//				return this;
//			}
//			if (typeValue == TypeValue.TypeType) {
//				return ListType;
//			}
//			return newErr("type conversion error from '%s' to '%s'", ListType, typeValue);
//		}
//
//		/**Equal implements the ref.Val interface method.. */
//		@Override
//		public Val equal(Val other) {
//			otherList, ok := other.(traits.Lister)
//			if !ok {
//				return Err.maybeNoSuchOverloadErr(other);
//			}
//			if l.Size() != otherList.Size() {
//				return False
//			}
//			for i := IntZero; i < l.Size().(Int); i++ {
//				thisElem := l.Get(i)
//				otherElem := otherList.Get(i)
//				elemEq := thisElem.Equal(otherElem)
//				if elemEq != True {
//					return elemEq
//				}
//			}
//			return True
//		}
//
//		/**Get implements the traits.Indexer interface method.. */
//		@Override
//		public Val get(Val index) {
//			i, ok := index.(Int)
//			if !ok {
//				return MaybeNoSuchOverloadErr(index)
//			}
//			if i < l.prevList.Size().(Int) {
//				return l.prevList.Get(i)
//			}
//			offset := i - l.prevList.Size().(Int)
//			return l.nextList.Get(offset)
//		}
//
//		/**Iterator implements the traits.Iterable interface method. */
//		@Override
//		public Iterator<Val> iterator() {
//			return newListIterator(this);
//		}
//
//		/**Size implements the traits.Sizer interface method. */
//		@Override
//		public Val size() {
//			return ((IntT)prevList.size()).add(nextList.size());
//		}
//
//		/**Type implements the ref.Val interface method. */
//		@Override
//		public Type type() {
//			return ListType;
//		}
//
//		/**Value implements the ref.Val interface method. */
//		@Override
//		public Object value() {
//			if l.value == nil {
//				merged := make([]interface{}, l.Size().(Int))
//				prevLen := l.prevList.Size().(Int)
//				for i := Int(0); i < prevLen; i++ {
//					merged[i] = l.prevList.Get(i).Value()
//				}
//				nextLen := l.nextList.Size().(Int)
//				for j := Int(0); j < nextLen; j++ {
//					merged[prevLen+j] = l.nextList.Get(j).Value()
//				}
//				l.value = merged
//			}
//			return l.value
//		}
//	}
//
//	public static Iterator<Val> newListIterator(Lister listValue) {
//		return new ListIterator(listValue, listValue.size());
//	}
//
//	static class ListIterator extends BaseIterator {
//		private final Lister listValue;
//		private int  cursor;
//		private final int len;
//
//		public ListIterator(Lister listValue, int len) {
//			this.listValue = listValue;
//			this.len = len;
//		}
//
//		/**HasNext implements the traits.Iterator interface method. */
//		@Override
//		public Val hasNext() {
//			return BoolT.valueOf(cursor < len);
//		}
//
//		/**Next implements the traits.Iterator interface method. */
//		@Override
//		public Val next() {
//			if (((BoolT)hasNext()).booleanValue()) {
//				int index = cursor;
//				cursor++;
//				return listValue.get(IntT.valueOf(index));
//			}
//			return null;
//		}
//	}
// }
