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
package org.projectnessie.cel.checker;

import static java.util.Collections.singletonList;
import static org.projectnessie.cel.checker.CheckerEnv.newCheckerEnv;
import static org.projectnessie.cel.checker.CheckerEnv.newStandardCheckerEnv;
import static org.projectnessie.cel.common.containers.Container.name;
import static org.projectnessie.cel.common.containers.Container.newContainer;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newRegistry;

import com.google.api.expr.v1alpha1.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.TypeProvider;
import org.projectnessie.cel.common.types.ref.Val;

public class CheckerEnvTest {

  @Test
  void overlappingIdentifier() {
    CheckerEnv env = newStandardCheckerEnv(Container.defaultContainer, newRegistry());
    Assertions.assertThatThrownBy(() -> env.add(Decls.newVar("int", Decls.newTypeType(null))))
        .hasMessage("overlapping identifier for name 'int'");
  }

  @Test
  void overlappingMacro() {
    CheckerEnv env = newStandardCheckerEnv(Container.defaultContainer, newRegistry());
    Assertions.assertThatThrownBy(
            () ->
                env.add(
                    Decls.newFunction(
                        "has", Decls.newOverload("has", singletonList(Decls.String), Decls.Bool))))
        .hasMessage("overlapping macro for name 'has' with 1 args");
  }

  @Test
  void overlappingOverload() {
    CheckerEnv env = newStandardCheckerEnv(Container.defaultContainer, newRegistry());
    Type paramA = Decls.newTypeParamType("A");
    List<String> typeParamAList = singletonList("A");
    Assertions.assertThatThrownBy(
            () ->
                env.add(
                    Decls.newFunction(
                        Overloads.TypeConvertDyn,
                        Decls.newParameterizedOverload(
                            Overloads.ToDyn, singletonList(paramA), Decls.Dyn, typeParamAList))))
        .hasMessage(
            "overlapping overload for name 'dyn' (type '(type_param: \"A\") -> dyn' with overloadId: 'to_dyn' cannot be distinguished from '(type_param: \"A\") -> dyn' with overloadId: 'to_dyn')");
  }

  @Test
  void lexicalIdentifierShadowsContainerQualifiedIdentifier() {
    CheckerEnv env = newStandardCheckerEnv(newContainer(name("com.example")), newRegistry());
    env.add(Decls.newVar("com.example.y", Decls.Int));
    env.add(Decls.newVar("y", Decls.Bool));

    Assertions.assertThat(env.lookupIdent("y").getName()).isEqualTo("com.example.y");

    env = env.enterScope();
    env.add(Decls.newVar("y", Decls.String));

    Assertions.assertThat(env.lookupIdent("y").getName()).isEqualTo("y");
    Assertions.assertThat(env.lookupIdent(".com.example.y").getName()).isEqualTo("com.example.y");
  }

  @Test
  void overloadsWithDifferentArityOrStyleDoNotOverlap() {
    CheckerEnv env = newStandardCheckerEnv(Container.defaultContainer, newRegistry());

    env.add(
        Decls.newFunction(
            "custom",
            Decls.newOverload("custom_string", singletonList(Decls.String), Decls.String),
            Decls.newOverload(
                "custom_string_string", Arrays.asList(Decls.String, Decls.String), Decls.String),
            Decls.newInstanceOverload(
                "custom_receiver_string",
                Arrays.asList(Decls.String, Decls.String),
                Decls.String)));
  }

  @Test
  void providerIdentifiersAreCachedDuringConcurrentLookups() throws Exception {
    int concurrency = 8;
    int identifiersPerThread = 500;
    AtomicInteger providerLookups = new AtomicInteger();
    TypeProvider provider =
        new TypeProvider() {
          @Override
          public Val enumValue(String enumName) {
            throw new AssertionError("unexpected enum lookup");
          }

          @Override
          public Val findIdent(String identName) {
            throw new AssertionError("unexpected identifier lookup");
          }

          @Override
          public Type findType(String typeName) {
            providerLookups.incrementAndGet();
            return Decls.String;
          }

          @Override
          public FieldType findFieldType(String messageType, String fieldName) {
            throw new AssertionError("unexpected field lookup");
          }

          @Override
          public Val newValue(String typeName, Map<String, Val> fields) {
            throw new AssertionError("unexpected value construction");
          }
        };
    CheckerEnv env = newCheckerEnv(Container.defaultContainer, provider);
    CountDownLatch ready = new CountDownLatch(concurrency);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(concurrency);
    try {
      List<Future<Object>> futures =
          java.util.stream.IntStream.range(0, concurrency)
              .mapToObj(
                  thread ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            Assertions.assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
                            for (int i = 0; i < identifiersPerThread; i++) {
                              String name = "type_" + thread + '_' + i;
                              Assertions.assertThat(env.lookupIdent(name).getName())
                                  .isEqualTo(name);
                            }
                            return null;
                          }))
              .toList();
      Assertions.assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (Future<Object> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
      Assertions.assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    }

    int expectedLookups = concurrency * identifiersPerThread;
    Assertions.assertThat(providerLookups).hasValue(expectedLookups);
    for (int thread = 0; thread < concurrency; thread++) {
      for (int i = 0; i < identifiersPerThread; i++) {
        String name = "type_" + thread + '_' + i;
        Assertions.assertThat(env.lookupIdent(name).getName()).isEqualTo(name);
      }
    }
    Assertions.assertThat(providerLookups).hasValue(expectedLookups);
  }
}
