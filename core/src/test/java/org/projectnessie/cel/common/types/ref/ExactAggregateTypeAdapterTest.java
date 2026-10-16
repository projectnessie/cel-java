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
package org.projectnessie.cel.common.types.ref;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.checker.Decls.Bytes;
import static org.projectnessie.cel.checker.Decls.Dyn;
import static org.projectnessie.cel.checker.Decls.Int;
import static org.projectnessie.cel.checker.Decls.Null;
import static org.projectnessie.cel.checker.Decls.String;
import static org.projectnessie.cel.checker.Decls.Uint;
import static org.projectnessie.cel.checker.Decls.newListType;
import static org.projectnessie.cel.checker.Decls.newMapType;
import static org.projectnessie.cel.checker.Decls.newWrapperType;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.NullT.NullValue;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.IntT;
import org.projectnessie.cel.common.types.MapT;
import org.projectnessie.cel.common.types.UintT;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.traits.Lister;

class ExactAggregateTypeAdapterTest {
  private static final ExactAggregateTypeAdapter EXACT = DefaultTypeAdapter.Instance::nativeToValue;

  @Test
  void checkedKindDeterminesSignednessForLongArraysAndLists() {
    for (Object source : List.of(new long[] {1L, Long.MIN_VALUE}, List.of(1L, Long.MIN_VALUE))) {
      Lister signed = list(EXACT.nativeAggregateToValue(source, newListType(Int)));
      Lister unsigned = list(EXACT.nativeAggregateToValue(source, newListType(Uint)));

      assertThat(signed.nativeGetAt(0)).isInstanceOf(IntT.class);
      assertThat(signed.nativeGetAt(1)).isInstanceOf(IntT.class);
      assertThat(unsigned.nativeGetAt(0)).isInstanceOf(UintT.class);
      assertThat(unsigned.nativeGetAt(1)).isInstanceOf(UintT.class);
      assertThat(unsigned.nativeGetAt(1)).isEqualTo(uintOf(Long.MIN_VALUE));
    }
  }

  @Test
  void preservesHighBitUnsignedValuesRecursively() {
    Object source = List.of(Map.of("bits", List.of(-1L, Long.MIN_VALUE)));
    Lister outer =
        list(
            EXACT.nativeAggregateToValue(
                source, newListType(newMapType(String, newListType(Uint)))));
    MapT map = (MapT) outer.nativeGetAt(0);
    Lister bits = list(map.find(stringOf("bits")));

    assertThat(bits.nativeGetAt(0)).isEqualTo(uintOf(-1L));
    assertThat(bits.nativeGetAt(1)).isEqualTo(uintOf(Long.MIN_VALUE));
  }

  @Test
  void distinguishesPresentNullMapValuesFromAbsentKeys() {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("present", null);

    MapT map = (MapT) EXACT.nativeAggregateToValue(source, newMapType(String, Null));

    assertThat(map.find(stringOf("present"))).isSameAs(NullValue);
    assertThat(map.find(stringOf("absent"))).isNull();
  }

  @Test
  void rejectsCelEquivalentDuplicateKeys() {
    Map<Object, Object> source = new LinkedHashMap<>();
    source.put((byte) 1, "byte");
    source.put(1L, "long");

    assertThat(EXACT.nativeAggregateToValue(source, newMapType(Int, String)))
        .matches(Err::isError)
        .hasToString("Failed with repeated key");
  }

  @Test
  void rejectsNullMapKeys() {
    Map<Object, Object> source = new LinkedHashMap<>();
    source.put(null, "value");

    assertThat(EXACT.nativeAggregateToValue(source, newMapType(String, String)))
        .matches(Err::isError);
  }

  @Test
  void reportsNestedBoxedTypeMismatchWhenElementIsConsumed() {
    List<Object> source = new ArrayList<>();
    source.add(1L);
    source.add("not an integer");

    Lister values = list(EXACT.nativeAggregateToValue(source, newListType(Int)));

    assertThat(values.nativeGetAt(0)).isInstanceOf(IntT.class);
    assertThat(values.nativeGetAt(1)).matches(Err::isError);
  }

  @Test
  void preservesCheckedWrapperSignednessAndNulls() {
    List<Object> source = new ArrayList<>();
    source.add(Long.MIN_VALUE);
    source.add(null);

    Lister values = list(EXACT.nativeAggregateToValue(source, newListType(newWrapperType(Uint))));

    assertThat(values.nativeGetAt(0)).isEqualTo(uintOf(Long.MIN_VALUE));
    assertThat(values.nativeGetAt(1)).isSameAs(NullValue);
  }

  @Test
  void rejectsEmbeddedValuesEvenForDynamicElements() {
    assertThat(EXACT.nativeAggregateToValue(new Val[] {intOf(1)}, newListType(Dyn)))
        .matches(Err::isError);

    Lister values = list(EXACT.nativeAggregateToValue(new Object[] {intOf(1)}, newListType(Dyn)));
    assertThat(values.nativeGetAt(0)).matches(Err::isError);
  }

  @Test
  void keepsByteArraysAsPrimitiveAndNullableWrapperBytes() {
    Lister primitive =
        list(
            EXACT.nativeAggregateToValue(
                new Object[] {new byte[] {1, 2}, ByteString.copyFromUtf8("three")},
                newListType(Bytes)));
    assertThat(primitive.nativeGetAt(0).value()).isEqualTo(new byte[] {1, 2});
    assertThat(primitive.nativeGetAt(1).value()).isEqualTo(new byte[] {'t', 'h', 'r', 'e', 'e'});

    List<Object> source = new ArrayList<>();
    source.add(new byte[] {4});
    source.add(null);
    Lister wrapper = list(EXACT.nativeAggregateToValue(source, newListType(newWrapperType(Bytes))));
    assertThat(wrapper.nativeGetAt(0).value()).isEqualTo(new byte[] {4});
    assertThat(wrapper.nativeGetAt(1)).isSameAs(NullValue);
  }

  @Test
  void rejectsIncompatibleAggregateRepresentation() {
    assertThat(EXACT.nativeAggregateToValue("not a list", newListType(Int))).matches(Err::isError);
    assertThat(EXACT.nativeAggregateToValue(List.of(1L), newMapType(String, Int)))
        .matches(Err::isError);
  }

  @Test
  void requiresNonNullAggregateCheckedType() {
    assertThatNullPointerException()
        .isThrownBy(() -> EXACT.nativeAggregateToValue(List.of(1L), null))
        .withMessage("checkedType");
    assertThatThrownBy(() -> EXACT.nativeAggregateToValue(List.of(1L), Int))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("checkedType must be a CEL list or map type");
  }

  private static Lister list(Val value) {
    return (Lister) value;
  }
}
