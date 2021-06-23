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
import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.ListT.ListType;
import static org.projectnessie.cel.common.types.ListT.newGenericArrayList;
import static org.projectnessie.cel.common.types.ListT.newStringArrayList;
import static org.projectnessie.cel.common.types.NullT.NullValue;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TypeT.TypeType;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Adder;
import org.projectnessie.cel.common.types.traits.Container;
import org.projectnessie.cel.common.types.traits.Indexer;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Sizer;

@TestInstance(Lifecycle.PER_CLASS)
public abstract class ListTest<CONSTRUCT> {

  static class TestData<CONSTRUCT> {
    TestData(String name) {
      this.name = name;
    }

    final String name;
    CONSTRUCT input;
    Val[] validate;
    TypeAdapter typeAdapter = DefaultTypeAdapter.Instance;

    TestData<CONSTRUCT> copy() {
      return new TestData<CONSTRUCT>(name).input(input).validate(validate).typeAdapter(typeAdapter);
    }

    TestData<CONSTRUCT> copyAndAdd(CONSTRUCT input, Val... inputValidate) {
      assertThat(sizeOf(input)).isEqualTo(inputValidate.length);
      CONSTRUCT added = sourceAdd(input);
      Val[] valid = Arrays.copyOf(validate, validate.length + inputValidate.length);
      System.arraycopy(inputValidate, 0, valid, validate.length, inputValidate.length);
      return copy().input(added).validate(valid);
    }

    @Override
    public String toString() {
      return name;
    }

    TestData<CONSTRUCT> input(CONSTRUCT input) {
      this.input = input;
      return this;
    }

    TestData<CONSTRUCT> validate(Val... validate) {
      this.validate = validate;
      return this;
    }

    TestData<CONSTRUCT> typeAdapter(TypeAdapter typeAdapter) {
      this.typeAdapter = typeAdapter;
      return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    CONSTRUCT sourceAdd(CONSTRUCT add) {
      int srcSize = sizeOf(input);
      int addSize = sizeOf(add);

      Object newOne;
      if (isList(input)) {
        newOne = new ArrayList<>();
        for (int i = 0; i < addSize; i++) {
          ((List) newOne).add(getAt(add, i));
        }
      } else {
        newOne = Arrays.copyOf((Object[]) input, srcSize + addSize);
        for (int i = 0; i < addSize; i++) {
          Array.set(newOne, srcSize + i, getAt(add, i));
        }
      }

      return (CONSTRUCT) newOne;
    }

    int sourceSize() {
      return sizeOf(input);
    }

    @SuppressWarnings("rawtypes")
    static int sizeOf(Object listOrArray) {
      if (isList(listOrArray)) {
        return ((Collection) listOrArray).size();
      }
      return Array.getLength(listOrArray);
    }

    private static boolean isList(Object listOrArray) {
      return listOrArray instanceof List;
    }

    Object sourceGet(int index) {
      return getAt(input, index);
    }

    @SuppressWarnings("rawtypes")
    static Object getAt(Object listOrArray, int index) {
      if (isList(listOrArray)) {
        return ((List) listOrArray).get(index);
      }
      return Array.get(listOrArray, index);
    }
  }

  public static class StringArrayListTest extends ListTest<String[]> {
    @Override
    Val constructList(TypeAdapter typeAdapter, String[] input) {
      return newStringArrayList(input);
    }

    @Override
    List<TestData<String[]>> testDataSets() {
      return asList(
          new TestData<String[]>("empty").input(new String[0]).validate(),
          //
          new TestData<String[]>("one-empty").input(new String[] {""}).validate(stringOf("")),
          //
          new TestData<String[]>("one").input(new String[] {"one"}).validate(stringOf("one")),
          //
          new TestData<String[]>("three")
              .input(new String[] {"one", "two", "three"})
              .validate(stringOf("one"), stringOf("two"), stringOf("three")));
    }
  }

  public static class GenericArrayListTest extends ListTest<Object[]> {
    @Override
    Val constructList(TypeAdapter typeAdapter, Object[] input) {
      return newGenericArrayList(typeAdapter, input);
    }

    @Override
    List<TestData<Object[]>> testDataSets() {
      return asList(
          new TestData<Object[]>("empty").input(new Object[0]).validate(),
          //
          new TestData<Object[]>("one-empty").input(new Object[] {""}).validate(stringOf("")),
          //
          new TestData<Object[]>("one").input(new Object[] {"one"}).validate(stringOf("one")),
          //
          new TestData<Object[]>("one-int").input(new Object[] {1}).validate(intOf(1)),
          //
          new TestData<Object[]>("val-double-string-int")
              .input(new Object[] {doubleOf(42.42d), stringOf("string"), intOf(42)})
              .validate(doubleOf(42.42d), stringOf("string"), intOf(42)),
          //
          new TestData<Object[]>("mixed")
              .input(new Object[] {"one", stringOf("two"), "three", NullValue, uintOf(99)})
              .validate(stringOf("one"), stringOf("two"), stringOf("three"), NullValue, uintOf(99)),
          //
          new TestData<Object[]>("three")
              .input(new Object[] {"one", "two", "three"})
              .validate(stringOf("one"), stringOf("two"), stringOf("three")));
    }
  }

  @SuppressWarnings("unused")
  abstract List<TestData<CONSTRUCT>> testDataSets();

  abstract Val constructList(TypeAdapter typeAdapter, CONSTRUCT input);

  // Traits: Val, Adder, Container, Indexer, IterableT, Sizer

  @ParameterizedTest
  @MethodSource("testDataSets")
  void listConstruction(TestData<CONSTRUCT> tc) {
    Val val = constructList(tc.typeAdapter, tc.input);

    Lister list = checkList(tc, val);

    // add list to itself
    TestData<CONSTRUCT> doubleTc = tc.copyAndAdd(tc.input, tc.validate);
    Val doubleListVal = list.add(list);
    assertThat(doubleListVal).isInstanceOf(Lister.class);

    checkList(doubleTc, doubleListVal);
  }

  Lister checkList(TestData<CONSTRUCT> tc, Val listVal) {
    assertThat(listVal)
        .isInstanceOf(Lister.class)
        .isInstanceOf(Sizer.class)
        .isInstanceOf(Indexer.class)
        .isInstanceOf(Container.class)
        .isInstanceOf(IterableT.class)
        .isInstanceOf(Adder.class)
        .isInstanceOf(Val.class);

    Lister list = (Lister) listVal;

    assertThat(list.convertToType(ListType)).isSameAs(list);
    assertThat(list.convertToType(TypeType)).isSameAs(ListType);
    assertThat(list.convertToType(IntType)).matches(Err::isError);

    // Sizer.size()
    int size = tc.sourceSize();
    assertThat(list.size()).isEqualTo(intOf(tc.sourceSize()));

    for (int i = 0; i < size; i++) {
      Object src = tc.sourceGet(i);
      Val srcVal = tc.typeAdapter.nativeToValue(src);

      // Indexer.get()
      Val elem = list.get(intOf(i));
      Object nat = elem.convertToNative((src instanceof Val) ? (Class) Val.class : src.getClass());

      assertThat(src).isOfAnyClassIn(nat.getClass());
      assertThat(srcVal.type()).isSameAs(elem.type());
      assertThat(src).isEqualTo(nat);
      assertThat(nat).isEqualTo(src);
      assertThat(srcVal).isEqualTo(elem);
      assertThat(elem).isEqualTo(srcVal);
      assertThat(srcVal.equal(elem)).isSameAs(True);
      assertThat(elem.equal(srcVal)).isSameAs(True);

      // Container.contains()
      assertThat(list.contains(elem)).isSameAs(True);
      assertThat(list.contains(srcVal)).isSameAs(True);
    }

    // non-existence checks
    // NOTE: a .contains() that does *not* return True on a list having different types does
    // return an error. So the assertion here must be `!= True`.
    assertThat(list.contains(intOf(987654321))).isNotSameAs(True);
    assertThat(list.contains(stringOf("this-is-not-in-the-list"))).isNotSameAs(True);

    // IterableT.iterate()
    IteratorT iter = list.iterator();
    assertThat(iter).isNotNull();
    List<Val> collected = new ArrayList<>();
    for (int index = 0; iter.hasNext() == True; index++) {
      Val next = iter.next();
      collected.add(next);

      // compare n-th element from iterator with element at index 'n' in list
      assertThat(next.equal(list.get(intOf(index)))).isSameAs(True);
    }
    assertThat(collected).hasSize(size);
    for (int i = 0; i < size; i++) {
      assertThat(list.get(intOf(i)).equal(collected.get(i))).isSameAs(True);
    }
    for (int i = 0; i < 3; i++) {
      assertThat(iter.hasNext()).isSameAs(False);
      assertThat(iter.next()).matches(Err::isError);
    }

    // Adder.add()
    assertThat(list.add(NullValue)).matches(Err::isError);
    assertThat(list.add(stringOf("foo"))).matches(Err::isError);

    return list;
  }

  // TODO JSON to list
  // TODO list to JSON

  // TODO Any to list
  // TODO list to Any

}
