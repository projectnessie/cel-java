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
package org.projectnessie.cel.common.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;

import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.ref.Val;

@SuppressWarnings("removal")
class UnknownTest {

  @Test
  void canonicalizesAndDefensivelyExposesExpressionIds() {
    long[] additional = {3L, -1L, 3L, Long.MAX_VALUE, Long.MIN_VALUE, 0L, 7L};
    UnknownT unknown = unknownOf(7L, additional);
    additional[0] = 99L;

    assertThat(unknown.expressionIds())
        .containsExactly(Long.MIN_VALUE, -1L, 0L, 3L, 7L, Long.MAX_VALUE);
    assertThat((long[]) unknown.value())
        .containsExactly(Long.MIN_VALUE, -1L, 0L, 3L, 7L, Long.MAX_VALUE);
    assertThat(unknown.toString())
        .isEqualTo("unknown{-9223372036854775808, -1, 0, 3, 7, 9223372036854775807}");

    long[] exposed = unknown.expressionIds();
    exposed[0] = 99L;
    long[] nativeValue = unknown.convertToNative(long[].class);
    nativeValue[0] = 88L;
    long[] objectValue = (long[]) unknown.convertToNative(Object.class);
    objectValue[0] = 77L;

    assertThat(unknown.expressionIds())
        .containsExactly(Long.MIN_VALUE, -1L, 0L, 3L, 7L, Long.MAX_VALUE);
  }

  @Test
  void singletonCompatibilityConversionsRemainAvailable() {
    UnknownT unknown = unknownOf(42L);

    assertThat(unknown.intValue()).isEqualTo(42L);
    assertThat(unknown.convertToNative(Long.class)).isEqualTo(42L);
    assertThat(unknown.convertToNative(long.class)).isEqualTo(42L);
    assertThat(unknown.convertToNative(Val.class)).isSameAs(unknown);
    assertThat(unknown.convertToNative(UnknownT.class)).isSameAs(unknown);
    assertThat(unknown.toString()).isEqualTo("unknown{42}");
  }

  @Test
  void multiIdScalarConversionsFailClearly() {
    UnknownT unknown = unknownOf(1L, 2L);

    assertThatThrownBy(unknown::intValue)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("unknown contains multiple expression ids");
    assertThatThrownBy(() -> unknown.convertToNative(Long.class))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("native type conversion error from 'unknown' to 'java.lang.Long'");
    assertThatThrownBy(() -> unknown.convertToNative(long.class))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("native type conversion error from 'unknown' to 'long'");
    assertThatThrownBy(() -> unknown.convertToNative(String.class))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("native type conversion error from 'unknown' to 'java.lang.String'");
  }

  @Test
  void mergeUnionsSetsAndReusesSupersetInstances() {
    UnknownT first = unknownOf(1L, 3L);
    UnknownT second = unknownOf(2L, 3L);
    UnknownT merged = first.merge(second);

    assertThat(merged.expressionIds()).containsExactly(1L, 2L, 3L);
    assertThat(merged.merge(first)).isSameAs(merged);
    assertThat(first.merge(merged)).isSameAs(merged);
    assertThat(first.merge(unknownOf(1L, 3L))).isSameAs(first);
    assertThat(first.merge(first)).isSameAs(first);
  }

  @Test
  void mergeHandlesDisjointAndOverlappingSetsInEitherDirection() {
    UnknownT first = unknownOf(1L, 3L, 5L);
    UnknownT overlapping = unknownOf(2L, 3L, 4L);
    UnknownT disjoint = unknownOf(6L, 7L);

    assertThat(first.merge(overlapping).expressionIds()).containsExactly(1L, 2L, 3L, 4L, 5L);
    assertThat(overlapping.merge(first).expressionIds()).containsExactly(1L, 2L, 3L, 4L, 5L);
    assertThat(first.merge(disjoint).expressionIds()).containsExactly(1L, 3L, 5L, 6L, 7L);
  }

  @Test
  void nullInputsAreRejected() {
    assertThatThrownBy(() -> unknownOf(1L, (long[]) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("additionalExpressionIds");
    assertThatThrownBy(() -> unknownOf(1L).merge(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("other");
  }

  @Test
  void javaEqualityUsesProvenanceWhileCelEqualityUsesUnknownType() {
    UnknownT canonical = unknownOf(3L, 1L, 3L);
    UnknownT same = unknownOf(1L, 3L);
    UnknownT different = unknownOf(1L, 2L);

    assertThat(canonical).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(different);
    assertThat(canonical.equal(different)).isSameAs(True);
    assertThat(UnknownT.isUnknown(canonical)).isTrue();
    assertThat(UnknownT.isUnknown(null)).isFalse();
  }
}
