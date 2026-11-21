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
package org.projectnessie.cel.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.BoolT.True;

import org.junit.jupiter.api.Test;

class EvalStateTest {

  @Test
  void emptyStateSupportsReadsAndResetBeforeFirstValue() {
    EvalState state = EvalState.newEvalState();

    assertThat(state.ids()).isEmpty();
    assertThat(state.value(1L)).isNull();
    state.reset();
    assertThat(state.ids()).isEmpty();
  }

  @Test
  void stateRemainsMutable() {
    EvalState state = EvalState.newEvalState();

    state.setValue(1L, True);
    assertThat(state.ids()).containsExactly(1L);
    assertThat(state.value(1L)).isSameAs(True);

    state.reset();
    assertThat(state.ids()).isEmpty();
    assertThat(state.value(1L)).isNull();
  }
}
