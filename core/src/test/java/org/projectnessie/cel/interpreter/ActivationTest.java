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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.Activation.newHierarchicalActivation;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

public class ActivationTest {

  @Test
  void activation() {
    Activation act = newActivation(Collections.singletonMap("a", True));
    assertThat(act).isNotNull();
    assertThat(newActivation(act)).isNotNull();
    assertThatThrownBy(() -> newActivation(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "activation input must be an activation or map[string]interface: got java.lang.String");
  }

  @Test
  void resolve() {
    Activation activation = newActivation(Collections.singletonMap("a", True));
    assertThat(activation.resolveName("a").present()).isTrue();
    assertThat(activation.resolveName("a").value()).isSameAs(True);
  }

  @Test
  void resolveLazy() {
    AtomicReference<Val> v = new AtomicReference<>();
    Supplier<Val> now =
        () -> {
          if (v.get() == null) {
            v.set(DefaultTypeAdapter.Instance.nativeToValue(ZonedDateTime.now()));
          }
          return v.get();
        };
    Map<String, Object> map = new HashMap<>();
    map.put("now", now);
    Activation a = newActivation(map);
    ResolvedValue first = a.resolveName("now");
    ResolvedValue second = a.resolveName("now");
    assertThat(first.present()).isTrue();
    assertThat(second.present()).isTrue();
    assertThat(first.value()).isSameAs(second.value());
  }

  @Test
  void hierarchicalActivation() {
    // compose a parent with more properties than the child
    Map<String, Object> parentMap = new HashMap<>();
    parentMap.put("a", stringOf("world"));
    parentMap.put("b", intOf(-42));
    parentMap.put("d", stringOf("child value for d"));
    Activation parent = new Activation.FunctionActivation(parentMap::get);
    // compose the child such that it shadows the parent
    Map<String, Object> childMap = new HashMap<>();
    childMap.put("a", True);
    childMap.put("c", stringOf("universe"));
    childMap.put("d", ResolvedValue.NULL_VALUE);
    Activation child = new Activation.FunctionActivation(childMap::get);
    Activation combined = newHierarchicalActivation(parent, child);

    // Resolve the shadowed child value.
    assertThat(combined.resolveName("a")).isEqualTo(ResolvedValue.resolvedValue(True));
    // Resolve the parent only value.
    assertThat(combined.resolveName("b")).isEqualTo(ResolvedValue.resolvedValue(intOf(-42)));
    // Resolve the child only value.
    assertThat(combined.resolveName("c"))
        .isEqualTo(ResolvedValue.resolvedValue(stringOf("universe")));
    // Resolve the child value as null without looking to parent.
    assertThat(combined.resolveName("d")).isSameAs(ResolvedValue.NULL_VALUE);
    // Absent
    assertThat(combined.resolveName("e")).isSameAs(ResolvedValue.ABSENT);
  }
}
