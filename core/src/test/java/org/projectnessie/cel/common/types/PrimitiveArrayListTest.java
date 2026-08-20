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

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.ListT.newDoubleArrayList;
import static org.projectnessie.cel.common.types.ListT.newIntArrayList;
import static org.projectnessie.cel.common.types.ListT.newLongArrayList;
import static org.projectnessie.cel.common.types.ListT.newValArrayList;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;

class PrimitiveArrayListTest {

  @Test
  void exactAndHeterogeneousMembership() {
    Lister intList = list(newIntArrayList(DefaultTypeAdapter.Instance, new int[] {50_000, 50_001}));
    Lister longList =
        list(newLongArrayList(DefaultTypeAdapter.Instance, new long[] {50_000, Long.MAX_VALUE}));
    Lister doubleList =
        list(newDoubleArrayList(DefaultTypeAdapter.Instance, new double[] {-0.0d, 50_000.0d}));

    assertThat(intList.contains(intOf(50_001))).isSameAs(True);
    assertThat(intList.contains(uintOf(50_001))).isSameAs(True);
    assertThat(intList.contains(doubleOf(50_001))).isSameAs(True);
    assertThat(intList.contains(intOf(Long.MAX_VALUE))).isSameAs(False);
    assertThat(longList.contains(intOf(Long.MAX_VALUE))).isSameAs(True);
    assertThat(doubleList.contains(doubleOf(0.0d))).isSameAs(True);
    assertThat(doubleList.contains(doubleOf(Double.NaN))).isSameAs(False);
  }

  @Test
  void exactAndCrossIntegerEquality() {
    Val ints = newIntArrayList(DefaultTypeAdapter.Instance, new int[] {50_000, 50_001});
    Val equalInts = newIntArrayList(DefaultTypeAdapter.Instance, new int[] {50_000, 50_001});
    Val longs = newLongArrayList(DefaultTypeAdapter.Instance, new long[] {50_000, 50_001});
    Val different = newLongArrayList(DefaultTypeAdapter.Instance, new long[] {50_000, 50_002});

    assertThat(ints.equal(equalInts)).isSameAs(True);
    assertThat(ints.equal(longs)).isSameAs(True);
    assertThat(longs.equal(ints)).isSameAs(True);
    assertThat(ints.equal(different)).isSameAs(False);
  }

  @Test
  void doubleEqualityPreservesNanAndSignedZeroSemantics() {
    Val signedZero = newDoubleArrayList(DefaultTypeAdapter.Instance, new double[] {-0.0d});
    Val positiveZero = newDoubleArrayList(DefaultTypeAdapter.Instance, new double[] {0.0d});
    Val nan = newDoubleArrayList(DefaultTypeAdapter.Instance, new double[] {Double.NaN});

    assertThat(signedZero.equal(positiveZero)).isSameAs(True);
    assertThat(nan.equal(nan)).isSameAs(False);
  }

  @Test
  void iterationAndPublicIndexBehaviorRemainUnchanged() {
    Lister values =
        list(newIntArrayList(DefaultTypeAdapter.Instance, new int[] {50_000, 50_001, 50_002}));
    IteratorT iterator = values.iterator();

    assertThat(iterator.next()).isEqualTo(intOf(50_000));
    assertThat(iterator.next()).isEqualTo(intOf(50_001));
    assertThat(iterator.next()).isEqualTo(intOf(50_002));
    assertThat(iterator.hasNext()).isSameAs(False);
    assertThat(values.get(intOf(-1))).matches(Err::isError);
    assertThat(values.get(doubleOf(0.5d))).matches(Err::isError);
    assertThat(values.nativeGetAt(-1)).matches(Err::isError);
    assertThat(values.nativeGetAt(values.nativeSize())).matches(Err::isError);
    assertThat(values.nativeGetAt(1)).isEqualTo(intOf(50_001));
  }

  @Test
  void genericEqualityFallbackRemainsAvailable() {
    Val primitive = newIntArrayList(DefaultTypeAdapter.Instance, new int[] {50_000, 50_001});
    Val generic =
        newValArrayList(DefaultTypeAdapter.Instance, new Val[] {intOf(50_000), intOf(50_001)});

    assertThat(primitive.equal(generic)).isSameAs(True);
    assertThat(generic.equal(primitive)).isSameAs(True);
  }

  private static Lister list(Val value) {
    return (Lister) value;
  }
}
