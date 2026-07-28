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
package org.projectnessie.cel.common.types.pb;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.IntT.intOf;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.projectnessie.cel.common.types.ref.FieldGetter;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.Val;

class ProtoTypeRegistryCopyTest {
  private static final String TYPE_NAME = TestAllTypes.getDescriptor().getFullName();

  @Test
  void dbCopyPreservesFileAliasesButNotMutableDescriptions() {
    Db original = Db.newDb();
    FileDescription originalFile = original.registerMessage(TestAllTypes.getDefaultInstance());
    PbTypeDescription originalType = original.describeType(TYPE_NAME);

    Db copy = original.copy();
    FileDescription copiedFile = copy.registerDescriptor(TestAllTypes.getDescriptor().getFile());
    PbTypeDescription copiedType = copy.describeType(TYPE_NAME);

    assertThat(copiedFile).isNotSameAs(originalFile);
    assertThat(copiedType).isNotSameAs(originalType);
    assertThat(copiedType.fieldMap()).isNotSameAs(originalType.fieldMap());
    assertThat(copiedFile.getTypeDescription(TYPE_NAME)).isSameAs(copiedType);
    assertThat(copy.fileDescriptions().stream().anyMatch(file -> file == copiedFile)).isTrue();
    assertThat(copy.fileDescriptions().stream().anyMatch(file -> file == originalFile)).isFalse();
  }

  @Test
  void repeatedGeneratedRegistrationRetainsDefaultBindingAndFieldCache() {
    ProtoTypeRegistry registry = ProtoTypeRegistry.newEmptyRegistry();
    TestAllTypes populated = TestAllTypes.newBuilder().setSingleInt32(41).build();
    registry.registerMessage(populated);
    FieldType initialField = registry.findFieldType(TYPE_NAME, "single_int32");

    registry.registerMessage(TestAllTypes.newBuilder().setSingleInt32(42).build());

    assertThat(registry.findFieldType(TYPE_NAME, "single_int32")).isSameAs(initialField);
    Val value = registry.newValue(TYPE_NAME, Map.of());
    assertThat(value.value()).isEqualTo(TestAllTypes.getDefaultInstance());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void generatedToCopyToDynamicKeepsOriginalBinding(boolean exact) {
    ProtoTypeRegistry original = registry(exact);
    original.registerMessage(TestAllTypes.getDefaultInstance());

    ProtoTypeRegistry copy = original.copy();
    copy.registerMessage(DynamicMessage.getDefaultInstance(TestAllTypes.getDescriptor()));

    assertRepresentation(original, TestAllTypes.class, true);
    assertRepresentation(copy, DynamicMessage.class, false);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void dynamicToCopyToGeneratedKeepsOriginalBinding(boolean exact) {
    ProtoTypeRegistry original = registry(exact);
    original.registerMessage(DynamicMessage.getDefaultInstance(TestAllTypes.getDescriptor()));

    ProtoTypeRegistry copy = original.copy();
    copy.registerMessage(TestAllTypes.getDefaultInstance());

    assertRepresentation(original, DynamicMessage.class, false);
    assertRepresentation(copy, TestAllTypes.class, true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void copiedRegistriesCanBindRepresentationsConcurrently(boolean exact) throws Exception {
    ProtoTypeRegistry original = registry(exact);
    original.registerDescriptor(TestAllTypes.getDescriptor().getFile());
    ProtoTypeRegistry copy = original.copy();
    CyclicBarrier start = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> generated =
          executor.submit(
              () -> {
                await(start);
                original.registerMessage(TestAllTypes.getDefaultInstance());
              });
      Future<?> dynamic =
          executor.submit(
              () -> {
                await(start);
                copy.registerMessage(
                    DynamicMessage.getDefaultInstance(TestAllTypes.getDescriptor()));
              });

      generated.get(10, SECONDS);
      dynamic.get(10, SECONDS);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }

    assertRepresentation(original, TestAllTypes.class, true);
    assertRepresentation(copy, DynamicMessage.class, false);
  }

  private static ProtoTypeRegistry registry(boolean exact) {
    return exact
        ? (ProtoTypeRegistry) ProtoTypeRegistry.newExactAggregateRegistry()
        : ProtoTypeRegistry.newEmptyRegistry();
  }

  private static void assertRepresentation(
      ProtoTypeRegistry registry, Class<? extends Message> representation, boolean optimized) {
    Val value = registry.newValue(TYPE_NAME, Map.of("single_int32", intOf(42)));
    assertThat(value.value()).isInstanceOf(representation);

    FieldType field = registry.findFieldType(TYPE_NAME, "single_int32");
    assertThat(field.getFrom.getFrom(value.value())).isEqualTo(42);
    assertThat(field.getFrom instanceof FieldGetter.Primitive).isEqualTo(optimized);
  }

  private static void await(CyclicBarrier barrier) {
    try {
      barrier.await(10, SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    } catch (BrokenBarrierException | TimeoutException e) {
      throw new AssertionError(e);
    }
  }
}
