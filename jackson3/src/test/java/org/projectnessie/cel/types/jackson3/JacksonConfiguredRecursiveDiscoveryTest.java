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
package org.projectnessie.cel.types.jackson3;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.common.types.StringT.stringOf;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.ObjectT;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptCompiler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class JacksonConfiguredRecursiveDiscoveryTest {

  @Test
  void configuredMapperIsSnapshottedAndPreservedByCopiesAndExactMode() {
    ObjectMapper caller =
        JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    Jackson3Registry registry = (Jackson3Registry) Jackson3Registry.newRegistry(caller);

    assertThat(registry.objectMapper).isNotSameAs(caller);

    registry.register(NamedBean.class);
    assertField(registry, NamedBean.class, "first_name");
    assertThat(registry.findFieldType(NamedBean.class.getName(), "first-name")).isNull();

    TypeRegistry copy = registry.copy();
    assertField(copy, NamedBean.class, "first_name");

    TypeRegistry exact =
        Jackson3Registry.newExactAggregateRegistry(
            JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build());
    exact.register(NamedBean.class);
    assertField(exact, NamedBean.class, "first_name");
    assertField(exact.copy(), NamedBean.class, "first_name");
  }

  @Test
  void configuredMapperUsesMixInsVisibilityAndModules() {
    ObjectMapper mixInMapper =
        JsonMapper.builder().addMixIn(MixInBean.class, RenameMixIn.class).build();
    TypeRegistry mixInRegistry = Jackson3Registry.newRegistry(mixInMapper);
    mixInRegistry.register(MixInBean.class);
    assertField(mixInRegistry, MixInBean.class, "mixed_name");
    assertThat(mixInRegistry.findFieldType(MixInBean.class.getName(), "value")).isNull();

    ObjectMapper visibilityMapper =
        JsonMapper.builder()
            .changeDefaultVisibility(
                visibility -> visibility.with(Visibility.NONE).withFieldVisibility(Visibility.ANY))
            .build();
    TypeRegistry visibilityRegistry = Jackson3Registry.newRegistry(visibilityMapper);
    visibilityRegistry.register(PrivateFieldBean.class);
    assertField(visibilityRegistry, PrivateFieldBean.class, "privateValue");

    SimpleModule module =
        new SimpleModule().setMixInAnnotation(ModuleBean.class, ModuleRenameMixIn.class);
    ObjectMapper moduleMapper = JsonMapper.builder().addModule(module).build();
    TypeRegistry moduleRegistry = Jackson3Registry.newRegistry(moduleMapper);
    moduleRegistry.register(ModuleBean.class);
    assertField(moduleRegistry, ModuleBean.class, "from_module");
    assertField(moduleRegistry.copy(), ModuleBean.class, "from_module");
  }

  @Test
  void directAndMutualRecursionAreCompleteAndUsable() throws Exception {
    TypeRegistry registry = Jackson3Registry.newRegistry();
    registry.register(RecursiveNode.class);

    FieldType next = assertField(registry, RecursiveNode.class, "next");
    assertThat(next.type.getMessageType()).isEqualTo(RecursiveNode.class.getName());

    registry.register(MutualA.class);
    assertThat(assertField(registry, MutualA.class, "b").type.getMessageType())
        .isEqualTo(MutualB.class.getName());
    assertThat(assertField(registry, MutualB.class, "a").type.getMessageType())
        .isEqualTo(MutualA.class.getName());

    RecursiveNode node = new RecursiveNode();
    node.name = "root";
    node.next = node;
    ObjectT value = (ObjectT) registry.nativeToValue(node);
    ObjectT cyclicNext = (ObjectT) value.get(stringOf("next"));
    assertThat(cyclicNext.get(stringOf("name"))).isEqualTo(stringOf("root"));

    Script script =
        ScriptCompiler.newBuilder()
            .registry(Jackson3Registry.newRegistry())
            .withDeclarations(
                Decls.newVar("node", Decls.newObjectType(RecursiveNode.class.getName())))
            .withTypes(RecursiveNode.class)
            .build()
            .compile("node.next.next.name == 'root'");
    assertThat(script.executeWithActivation(Boolean.class, singletonMap("node", node))).isTrue();

    for (TypeRegistry recursiveRegistry :
        List.of(Jackson3Registry.newExactAggregateRegistry(), registry.copy())) {
      recursiveRegistry.register(RecursiveNode.class);
      assertThat(assertField(recursiveRegistry, RecursiveNode.class, "next").type.getMessageType())
          .isEqualTo(RecursiveNode.class.getName());
    }
  }

  @Test
  void concurrentFirstRecursiveDiscoveryPublishesTheCompleteGraph() throws Exception {
    TypeRegistry registry = Jackson3Registry.newRegistry();
    ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      CountDownLatch ready = new CountDownLatch(8);
      CountDownLatch start = new CountDownLatch(1);
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                  registry.register(MutualA.class);
                  return null;
                }));
      }

      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertField(registry, MutualA.class, "b");
    assertField(registry, MutualB.class, "a");
  }

  @Test
  void failedDiscoveryRollsBackTheWholeNewGraphAndCanRetry() {
    TypeRegistry registry = Jackson3Registry.newRegistry();

    assertRegistrationFails(registry, FailingRecursiveRoot.class);
    assertThat(registry.findType(FailingRecursiveRoot.class.getName())).isNull();
    assertThat(registry.findType(RollbackNested.class.getName())).isNull();

    assertRegistrationFails(registry, FailingRecursiveRoot.class);
    assertThat(registry.findType(FailingRecursiveRoot.class.getName())).isNull();
    assertThat(registry.findType(RollbackNested.class.getName())).isNull();
  }

  @Test
  void failedDiscoveryRetainsPreexistingNestedTypes() {
    TypeRegistry registry = Jackson3Registry.newRegistry();
    registry.register(StableNested.class);

    assertRegistrationFails(registry, FailingWithStableNested.class);
    assertThat(registry.findType(FailingWithStableNested.class.getName())).isNull();
    assertField(registry, StableNested.class, "value");
  }

  @Test
  void configuredFactoriesRejectNull() {
    assertThatThrownBy(() -> Jackson3Registry.newRegistry(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("objectMapper");
    assertThatThrownBy(() -> Jackson3Registry.newExactAggregateRegistry(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("objectMapper");
  }

  private static FieldType assertField(
      TypeRegistry registry, Class<?> messageType, String fieldName) {
    assertThat(registry.findType(messageType.getName())).isNotNull();
    FieldType fieldType = registry.findFieldType(messageType.getName(), fieldName);
    assertThat(fieldType).isNotNull();
    return fieldType;
  }

  private static void assertRegistrationFails(TypeRegistry registry, Class<?> type) {
    assertThatThrownBy(() -> registry.register(type))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("boolean[]");
  }

  static final class NamedBean {
    public String firstName;
  }

  static final class MixInBean {
    public String getValue() {
      return "value";
    }
  }

  abstract static class RenameMixIn {
    @JsonProperty("mixed_name")
    abstract String getValue();
  }

  static final class PrivateFieldBean {
    private String privateValue;
  }

  static final class ModuleBean {
    public String getValue() {
      return "value";
    }
  }

  abstract static class ModuleRenameMixIn {
    @JsonProperty("from_module")
    abstract String getValue();
  }

  static final class RecursiveNode {
    public String name;
    public RecursiveNode next;
  }

  static final class MutualA {
    public MutualB b;
  }

  static final class MutualB {
    public MutualA a;
  }

  @JsonPropertyOrder({"nested", "unsupported"})
  static final class FailingRecursiveRoot {
    public RollbackNested nested;
    public boolean[] unsupported;
  }

  static final class RollbackNested {
    public FailingRecursiveRoot parent;
  }

  @JsonPropertyOrder({"nested", "unsupported"})
  static final class FailingWithStableNested {
    public StableNested nested;
    public boolean[] unsupported;
  }

  static final class StableNested {
    public String value;
  }
}
