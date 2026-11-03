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

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.DoubleT;
import org.projectnessie.cel.common.types.UintT;
import org.projectnessie.cel.common.types.ref.Val;

class ConstantSetMembershipSupportTest {

  @Test
  void preservesErrorUnknownEmptyAndTypeCheckOrder() {
    Val error = newErr("carried");
    Val unknown = unknownOf(7L);
    Set<Val> values = Set.of(stringOf("value"));

    assertThat(evaluate(error, "string", values)).isSameAs(error);
    assertThat(evaluate(unknown, "string", values)).isSameAs(unknown);
    assertThat(evaluate(uintOf(1), null, Set.of())).isSameAs(False);
    assertThat(evaluate(uintOf(1), "string", values).type().typeName()).isEqualTo("error");
  }

  @Test
  void delegatesExactEqualityAndHashingToTheStoredValSet() {
    DoubleT nan = doubleOf(Double.NaN);
    Set<Val> nanSet = Set.of(nan);
    assertThat(evaluate(nan, "double", nanSet)).isSameAs(True);
    assertThat(evaluate(doubleOf(Double.NaN), "double", nanSet)).isSameAs(False);

    for (double stored : new double[] {-0.0d, 0.0d}) {
      Set<Val> zeroSet = Set.of(doubleOf(stored));
      for (double needle : new double[] {-0.0d, 0.0d}) {
        Val needleValue = doubleOf(needle);
        assertThat(evaluate(needleValue, "double", zeroSet).booleanValue())
            .isEqualTo(zeroSet.contains(needleValue));
      }
    }

    UintT highBit = uintOf(Long.MIN_VALUE);
    assertThat(evaluate(highBit, "uint", Set.of(highBit))).isSameAs(True);
  }

  private static Val evaluate(Val needle, String typeName, Set<Val> values) {
    return ConstantSetMembershipSupport.evaluate(needle, typeName, values);
  }
}
