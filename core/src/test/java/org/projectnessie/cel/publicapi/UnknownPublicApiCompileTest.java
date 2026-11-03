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
package org.projectnessie.cel.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;

import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.UnknownT;
import org.projectnessie.cel.common.types.ref.Val;

/** Compile-time fixture for the public unknown-provenance value contract. */
class UnknownPublicApiCompileTest {

  @Test
  void provenanceFactoriesAccessorAndMergeRemainPublic() {
    UnknownT singleton = unknownOf(1L);
    UnknownT multiple = unknownOf(3L, 2L, 1L);
    UnknownT merged = singleton.merge(multiple);
    Val publicValue = merged;

    assertThat(singleton.expressionIds()).containsExactly(1L);
    assertThat(multiple.expressionIds()).containsExactly(1L, 2L, 3L);
    assertThat(merged).isSameAs(multiple);
    assertThat(publicValue).isSameAs(merged);
  }
}
