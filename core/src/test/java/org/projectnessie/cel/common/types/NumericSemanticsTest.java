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
import static org.projectnessie.cel.common.types.IntT.IntNegOne;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Comparer;

class NumericSemanticsTest {

  @Test
  void signedUnsignedBoundaries() {
    assertComparison(intOf(-1L), uintOf(0L), IntNegOne);
    assertComparison(intOf(Long.MAX_VALUE), uintOf(Long.MAX_VALUE), IntZero);
    assertComparison(intOf(Long.MAX_VALUE), uintOf(Long.MIN_VALUE), IntNegOne);
    assertComparison(uintOf(-1L), intOf(Long.MAX_VALUE), IntOne);

    assertThat(intOf(-1L).equal(uintOf(-1L))).isSameAs(False);
    assertThat(uintOf(Long.MAX_VALUE).equal(intOf(Long.MAX_VALUE))).isSameAs(True);
    assertThat(uintOf(Long.MIN_VALUE).equal(intOf(Long.MIN_VALUE))).isSameAs(False);
  }

  @Test
  void floatingPointConversionBoundaries() {
    long firstInexactInteger = (1L << 53) + 1L;
    DoubleT roundedInteger = doubleOf((double) firstInexactInteger);
    assertComparison(intOf(firstInexactInteger), roundedInteger, IntZero);
    assertThat(intOf(firstInexactInteger).equal(roundedInteger)).isSameAs(True);

    UintT maxUint = uintOf(-1L);
    DoubleT roundedMaxUint = (DoubleT) maxUint.convertToType(DoubleT.DoubleType);
    assertComparison(maxUint, roundedMaxUint, IntZero);
    assertThat(maxUint.equal(roundedMaxUint)).isSameAs(True);

    assertComparison(intOf(Long.MAX_VALUE), doubleOf(Double.POSITIVE_INFINITY), IntNegOne);
    assertComparison(uintOf(-1L), doubleOf(Double.POSITIVE_INFINITY), IntNegOne);
    assertComparison(intOf(Long.MIN_VALUE), doubleOf(Double.NEGATIVE_INFINITY), IntOne);
  }

  @Test
  void nanAndSignedZero() {
    DoubleT nan = doubleOf(Double.NaN);
    DoubleT zero = doubleOf(0.0d);
    DoubleT negativeZero = doubleOf(-0.0d);

    assertThat(nan.equal(nan)).isSameAs(False);
    assertComparison(nan, nan, IntZero);
    assertComparison(nan, zero, IntOne);
    assertComparison(zero, nan, IntNegOne);

    assertThat(zero.equal(negativeZero)).isSameAs(True);
    assertComparison(zero, negativeZero, IntZero);
    assertComparison(negativeZero, zero, IntZero);
  }

  private static void assertComparison(Val left, Val right, IntT expected) {
    assertThat(((Comparer) left).compare(right)).isSameAs(expected);
    assertThat(((Comparer) right).compare(left))
        .isSameAs(expected == IntZero ? IntZero : expected == IntOne ? IntNegOne : IntOne);
  }
}
