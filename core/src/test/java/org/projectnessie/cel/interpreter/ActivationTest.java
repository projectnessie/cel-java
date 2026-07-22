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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    assertThat(activation.resolve("a")).isNotSameAs(Activation.ABSENT);
    assertThat(activation.resolve("a")).isSameAs(True);
  }

  @Test
  void resolveNullAndAbsentFromMapActivation() {
    Map<String, Object> map = new HashMap<>();
    map.put("nullValue", null);
    Activation activation = newActivation(map);

    assertThat(activation.resolve("nullValue")).isNull();
    assertThat(activation.resolve("absent")).isSameAs(Activation.ABSENT);
  }

  @Test
  void resolveLazy() {
    AtomicInteger invocations = new AtomicInteger();
    Map<String, Object> map = new HashMap<>();
    map.put(
        "now",
        (Supplier<Val>)
            () -> {
              invocations.incrementAndGet();
              return stringOf("lazy");
            });
    Activation a = newActivation(map);
    Object first = a.resolve("now");
    Object second = a.resolve("now");
    assertThat(invocations).hasValue(1);
    assertThat(first).isNotSameAs(Activation.ABSENT).isSameAs(second).isEqualTo(stringOf("lazy"));
    assertThat(second).isNotSameAs(Activation.ABSENT);
  }

  @SuppressWarnings("removal")
  @Test
  void legacyActivation() {
    Function<String, Object> func =
        name ->
            switch (name) {
              case "a" -> stringOf("one");
              case "b" -> ResolvedValue.resolvedValue(stringOf("two"));
              case "absent" -> ResolvedValue.ABSENT;
              case "null" -> ResolvedValue.NULL_VALUE;
              case "real_null" -> null;
              default -> throw new RuntimeException("unknown activation name: " + name);
            };
    Activation activation = newActivation(func);
    assertThat(activation.resolve("a")).isEqualTo(stringOf("one"));
    assertThat(activation.resolve("b")).isEqualTo(stringOf("two"));
    assertThat(activation.resolve("absent")).isSameAs(ActivationFunction.ABSENT);
    assertThat(activation.resolve("real_null")).isSameAs(ActivationFunction.ABSENT);
    assertThat(activation.resolve("null")).isNull();
    assertThat(activation.resolveName("a")).isEqualTo(ResolvedValue.resolvedValue(stringOf("one")));
    assertThat(activation.resolveName("absent")).isSameAs(ResolvedValue.ABSENT);
    assertThat(activation.resolveName("null")).isSameAs(ResolvedValue.NULL_VALUE);

    assertThat(ResolvedValue.mapTo(null)).isSameAs(ResolvedValue.NULL_VALUE);
    assertThat(ResolvedValue.mapTo(ActivationFunction.ABSENT)).isSameAs(ResolvedValue.ABSENT);
    assertThat(ResolvedValue.mapTo("foo")).isEqualTo(ResolvedValue.resolvedValue("foo"));
    var ref = ResolvedValue.resolvedValue("foo");
    assertThat(ResolvedValue.mapTo(ref)).isSameAs(ref);

    assertThat(ResolvedValue.mapLegacy("foo")).isEqualTo("foo");
    assertThat(ResolvedValue.mapLegacy(null)).isEqualTo(ActivationFunction.ABSENT);
    assertThat(ResolvedValue.mapLegacy(ResolvedValue.resolvedValue("foo"))).isEqualTo("foo");
    assertThat(ResolvedValue.mapLegacy(ResolvedValue.NULL_VALUE)).isNull();
    assertThat(ResolvedValue.mapLegacy(ResolvedValue.ABSENT)).isSameAs(ActivationFunction.ABSENT);
  }

  @ParameterizedTest
  @CsvSource({
    "true,true",
    "true,false",
    "false,true",
    "false,false",
  })
  void hierarchicalActivationMap(boolean parentFunction, boolean childFunction) {
    // compose a parent with more properties than the child
    Map<String, Object> parentMap = new HashMap<>();
    parentMap.put("a", stringOf("world"));
    parentMap.put("b", intOf(-42));
    parentMap.put("d", stringOf("child value for d"));
    Activation parent =
        parentFunction
            ? new Activation.FunctionActivation(
                name -> parentMap.getOrDefault(name, ActivationFunction.ABSENT))
            : new Activation.MapActivation(parentMap);
    // compose the child such that it shadows the parent
    Map<String, Object> childMap = new HashMap<>();
    childMap.put("a", True);
    childMap.put("c", stringOf("universe"));
    childMap.put("d", null);
    Activation child =
        childFunction
            ? new Activation.FunctionActivation(
                name -> childMap.getOrDefault(name, ActivationFunction.ABSENT))
            : new Activation.MapActivation(childMap);

    Activation combined = newHierarchicalActivation(parent, child);

    assertThat(parent.resolve("a")).isEqualTo(stringOf("world"));
    assertThat(parent.resolve("b")).isEqualTo(intOf(-42));
    assertThat(parent.resolve("c")).isSameAs(Activation.ABSENT);
    assertThat(parent.resolve("d")).isEqualTo(stringOf("child value for d"));

    assertThat(child.resolve("a")).isEqualTo(True);
    assertThat(child.resolve("b")).isSameAs(Activation.ABSENT);
    assertThat(child.resolve("c")).isEqualTo(stringOf("universe"));
    assertThat(child.resolve("d")).isNull();

    // Resolve the shadowed child value.
    assertThat(combined.resolve("a")).isEqualTo(True);
    // Resolve the parent only value.
    assertThat(combined.resolve("b")).isEqualTo(intOf(-42));
    // Resolve the child only value.
    assertThat(combined.resolve("c")).isEqualTo(stringOf("universe"));
    // Resolve the child value as null without looking to parent.
    assertThat(combined.resolve("d")).isNull();
    // Absent
    assertThat(combined.resolve("e")).isSameAs(Activation.ABSENT);
  }
}
