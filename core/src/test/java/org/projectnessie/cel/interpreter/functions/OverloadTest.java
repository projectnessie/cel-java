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
package org.projectnessie.cel.interpreter.functions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.ListT.newGenericArrayList;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Trait;

class OverloadTest {
  @Test
  void standardOverloadsHaveStableEntriesInCallerOwnedArrays() {
    Overload[] first = Overload.standardOverloads();
    Overload[] second = Overload.standardOverloads();

    assertThat(first).isNotSameAs(second).isNotEmpty().hasSameSizeAs(second);
    for (int i = 0; i < first.length; i++) {
      assertThat(first[i]).isSameAs(second[i]);
    }

    Arrays.fill(first, null);
    Overload[] third = Overload.standardOverloads();
    assertThat(third).doesNotContainNull();
    for (int i = 0; i < third.length; i++) {
      assertThat(third[i]).isSameAs(second[i]);
    }
  }

  @Test
  void standardIteratorOverloadsDispatchThroughVal() {
    Overload iteratorOverload = standardOverload(Overloads.Iterator);
    Overload hasNextOverload = standardOverload(Overloads.HasNext);
    Overload nextOverload = standardOverload(Overloads.Next);

    assertThat(iteratorOverload.operandTrait).isEqualTo(Trait.IterableType);
    assertThat(hasNextOverload.operandTrait).isEqualTo(Trait.IteratorType);
    assertThat(nextOverload.operandTrait).isEqualTo(Trait.IteratorType);

    Val cursor =
        iteratorOverload.unary.invoke(
            newGenericArrayList(DefaultTypeAdapter.Instance, new Object[] {1L}));
    assertThat(cursor).isInstanceOf(IteratorT.class);
    assertThat(cursor.type()).isSameAs(IteratorT.IteratorType);
    assertThat(cursor.type().hasTrait(Trait.IteratorType)).isTrue();
    assertThat(hasNextOverload.unary.invoke(cursor)).isSameAs(True);
    assertThat(nextOverload.unary.invoke(cursor)).isEqualTo(intOf(1));
    assertThat(hasNextOverload.unary.invoke(cursor)).isSameAs(False);
    assertThat(nextOverload.unary.invoke(cursor))
        .matches(Err::isError)
        .hasToString("no more elements");
  }

  private static Overload standardOverload(String operator) {
    return Arrays.stream(Overload.standardOverloads())
        .filter(overload -> overload.operator.equals(operator))
        .findFirst()
        .orElseThrow();
  }
}
