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
import org.projectnessie.cel.common.types.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

public class TestActivation {

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
    assertThat(activation.resolveName("a")).isSameAs(True);
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
    Object first = a.resolveName("now");
    Object second = a.resolveName("now");
    assertThat(first).isSameAs(second);
  }

  @Test
  void hierarchicalActivation() {
    // compose a parent with more properties than the child
    Map<String, Object> parentMap = new HashMap<>();
    parentMap.put("a", stringOf("world"));
    parentMap.put("b", intOf(-42));
    Activation parent = newActivation(parentMap);
    // compose the child such that it shadows the parent
    Map<String, Object> childMap = new HashMap<>();
    childMap.put("a", True);
    childMap.put("c", stringOf("universe"));
    Activation child = newActivation(childMap);
    Activation combined = newHierarchicalActivation(parent, child);

    // Resolve the shadowed child value.
    assertThat(combined.resolveName("a")).isSameAs(True);
    // Resolve the parent only value.
    assertThat(combined.resolveName("b")).isEqualTo(intOf(-42));
    // Resolve the child only value.
    assertThat(combined.resolveName("c")).isEqualTo(stringOf("universe"));
  }
}
