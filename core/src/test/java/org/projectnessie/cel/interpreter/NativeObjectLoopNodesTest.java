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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.Activation.newHierarchicalActivation;
import static org.projectnessie.cel.interpreter.Activation.newPartialActivation;

import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;

class NativeObjectLoopNodesTest {
  private static final String MESSAGE_TYPE = TestAllTypes.getDescriptor().getFullName();
  private static final TypeRegistry REGISTRY =
      ProtoTypeRegistry.newExactAggregateRegistry(TestAllTypes.getDefaultInstance());
  private static final ExactAggregateTypeAdapter EXACT_ADAPTER =
      (ExactAggregateTypeAdapter) REGISTRY;
  private static final TypeAdapter ADAPTER = REGISTRY;
  private static final CheckedValueMaterializer MESSAGE_MATERIALIZER =
      new CheckedValueMaterializer(EXACT_ADAPTER, Decls.newObjectType(MESSAGE_TYPE));
  private static final FieldType STRING_FIELD =
      REGISTRY.findFieldType(MESSAGE_TYPE, "single_string");

  @Test
  void exactBindingsMaterializeLazilyAndPreserveNearestNameShadowing() {
    TestAllTypes message = message("outer");
    NativeLoopBinding outer =
        new NativeLoopBinding(newActivation(Map.of("value", "root")), "value");
    outer.setExactObject(message, MESSAGE_MATERIALIZER);

    assertThat(NativeLoopBinding.findExactObject(outer, "value")).isSameAs(outer);
    assertThat(outer.exactObjectValue()).isSameAs(message);
    assertThat(outer.resolve(".value")).isEqualTo("root");
    Object materialized = outer.resolve("value");
    assertThat(outer.resolve("value")).isSameAs(materialized);

    NativeLoopBinding inherited = new NativeLoopBinding(outer, "other");
    inherited.setExactObject(message("inner"), MESSAGE_MATERIALIZER);
    assertThat(NativeLoopBinding.findExactObject(inherited, "value")).isSameAs(outer);

    NativeLoopBinding scalarShadow = new NativeLoopBinding(outer, "value");
    scalarShadow.setInt(1L);
    assertThat(NativeLoopBinding.findExactObject(scalarShadow, "value")).isNull();
    scalarShadow.setExactObject(message("replacement"), MESSAGE_MATERIALIZER);
    assertThat(NativeLoopBinding.findExactObject(scalarShadow, "value")).isSameAs(scalarShadow);
    scalarShadow.setObject(message("general"));
    assertThat(NativeLoopBinding.findExactObject(scalarShadow, "value")).isNull();

    outer.setExactObject(null, MESSAGE_MATERIALIZER);
    assertThat(catchThrowableOfType(ValueSignal.class, outer::exactObjectValue).value)
        .isInstanceOf(Err.class);
    outer.setExactObject(True, MESSAGE_MATERIALIZER);
    assertThat(catchThrowableOfType(ValueSignal.class, outer::exactObjectValue).value)
        .isInstanceOf(Err.class);
  }

  @Test
  void exactTraversalPreservesEncounterOrderAndResolvesSourceOnce() {
    for (Object values :
        List.of(
            new Object[] {message("one"), message("two")},
            List.of(message("one"), message("two")))) {
      AtomicInteger resolutions = new AtomicInteger();
      TestListSource source =
          new TestListSource(
              ignored -> {
                resolutions.incrementAndGet();
                return values;
              });
      NativeObjectListTraversal traversal =
          new NativeObjectListTraversal(source, MESSAGE_MATERIALIZER);
      NativeLoopBinding binding = new NativeLoopBinding(emptyActivation(), "item");
      NativeStringObjectField field =
          new NativeStringObjectField(1L, "item", ADAPTER, STRING_FIELD, new EvalConst(1L, False));
      List<String> visited = new ArrayList<>();

      assertThat(
              traversal.traverse(
                  emptyActivation(),
                  binding,
                  current -> {
                    visited.add(field.evalString(current));
                    return false;
                  }))
          .isFalse();

      assertThat(visited).containsExactly("one", "two");
      assertThat(resolutions).hasValue(1);
    }
  }

  @Test
  void traversalAndAccessFailuresBecomeCelValuesWithoutReplay() {
    for (Val terminal : List.of(unknownOf(1L), Err.newErr("source failed"))) {
      AtomicInteger resolutions = new AtomicInteger();
      TestListSource source =
          new TestListSource(
              ignored -> {
                resolutions.incrementAndGet();
                return terminal;
              });
      ValueSignal failure =
          catchThrowableOfType(
              ValueSignal.class,
              () ->
                  new NativeObjectListTraversal(source, MESSAGE_MATERIALIZER)
                      .traverse(
                          emptyActivation(),
                          new NativeLoopBinding(emptyActivation(), "item"),
                          ignored -> false));
      assertThat(failure.value).isSameAs(terminal);
      assertThat(resolutions).hasValue(1);
    }

    for (Object invalid : List.of(new Val[] {True}, new int[] {1})) {
      AtomicInteger resolutions = new AtomicInteger();
      TestListSource source =
          new TestListSource(
              ignored -> {
                resolutions.incrementAndGet();
                return invalid;
              });
      ValueSignal failure =
          catchThrowableOfType(
              ValueSignal.class,
              () ->
                  new NativeObjectListTraversal(source, MESSAGE_MATERIALIZER)
                      .traverse(
                          emptyActivation(),
                          new NativeLoopBinding(emptyActivation(), "item"),
                          ignored -> false));
      assertThat(failure.value).isInstanceOf(Err.class);
      assertThat(resolutions).hasValue(1);
    }

    FieldType throwingField =
        new FieldType(
            Decls.String,
            ignored -> true,
            ignored -> {
              throw new IllegalStateException("getter failed");
            });
    NativeStringObjectField field =
        new NativeStringObjectField(1L, "item", ADAPTER, throwingField, new EvalConst(1L, False));
    NativeLoopBinding binding = new NativeLoopBinding(emptyActivation(), "item");
    binding.setExactObject(message("value"), MESSAGE_MATERIALIZER);
    assertThat(catchThrowableOfType(ValueSignal.class, () -> field.evalString(binding)).value)
        .isInstanceOf(Err.class);

    FieldType terminalField =
        new FieldType(Decls.String, ignored -> true, ignored -> unknownOf(2L));
    NativeStringObjectField terminal =
        new NativeStringObjectField(1L, "item", ADAPTER, terminalField, new EvalConst(1L, False));
    assertThat(catchThrowableOfType(ValueSignal.class, () -> terminal.evalString(binding)).value)
        .isEqualTo(unknownOf(2L));

    FieldType nullField = new FieldType(Decls.String, ignored -> true, ignored -> null);
    NativeStringObjectField nullValue =
        new NativeStringObjectField(1L, "item", ADAPTER, nullField, new EvalConst(1L, False));
    assertThat(catchThrowableOfType(ValueSignal.class, () -> nullValue.evalString(binding)).value)
        .isSameAs(org.projectnessie.cel.common.types.NullT.NullValue);

    FieldType throwingPresence =
        new FieldType(
            Decls.String,
            ignored -> {
              throw new IllegalStateException("presence failed");
            },
            ignored -> "unused");
    NativeObjectFieldPresence presence =
        new NativeObjectFieldPresence(
            1L, "item", ADAPTER, throwingPresence, new EvalConst(1L, False));
    assertThat(catchThrowableOfType(ValueSignal.class, () -> presence.evalBoolean(binding)).value)
        .isInstanceOf(Err.class);
  }

  @Test
  void rawConsumersUseEstablishedDelegatesWithoutAnExactBinding() {
    NativeStringObjectField field =
        new NativeStringObjectField(
            1L,
            "item",
            ADAPTER,
            STRING_FIELD,
            new EvalConst(1L, org.projectnessie.cel.common.types.StringT.stringOf("fallback")));
    NativeObjectFieldPresence presence =
        new NativeObjectFieldPresence(2L, "item", ADAPTER, STRING_FIELD, new EvalConst(2L, True));

    assertThat(field.evalString(emptyActivation())).isEqualTo("fallback");
    assertThat(presence.evalBoolean(emptyActivation())).isTrue();

    NativeLoopBinding scalarShadow = new NativeLoopBinding(emptyActivation(), "item");
    scalarShadow.setInt(1L);
    assertThat(field.evalString(scalarShadow)).isEqualTo("fallback");
    assertThat(presence.evalBoolean(scalarShadow)).isTrue();
  }

  @Test
  void objectAllShortCircuitsAndExistsOneAlwaysScans() {
    TestListSource source =
        new TestListSource(
            ignored -> new Object[] {message("one"), message("match"), message("three")});
    AtomicInteger allCalls = new AtomicInteger();
    TestPredicate allPredicate =
        new TestPredicate(
            binding -> {
              int call = allCalls.incrementAndGet();
              if (call == 1) {
                throw new ValueSignal(unknownOf(42L));
              }
              return false;
            });
    NativeObjectAllFold all = allFold(source, allPredicate);
    assertThat(all.evalBoolean(emptyActivation())).isFalse();
    assertThat(allCalls).hasValue(2);

    AtomicInteger existsCalls = new AtomicInteger();
    TestPredicate existsPredicate =
        new TestPredicate(
            binding -> {
              existsCalls.incrementAndGet();
              return ((TestAllTypes) binding.exactObjectValue()).getSingleString().equals("match");
            });
    NativeObjectExistsOneFold existsOne = existsOneFold(source, existsPredicate);
    assertThat(existsOne.evalBoolean(emptyActivation())).isTrue();
    assertThat(existsCalls).hasValue(3);
  }

  @Test
  void invalidVisitedElementsParticipateInQuantifierPendingRules() {
    AtomicInteger allCalls = new AtomicInteger();
    NativeObjectAllFold all =
        allFold(
            new TestListSource(ignored -> new Object[] {null, message("false")}),
            new TestPredicate(
                binding -> {
                  allCalls.incrementAndGet();
                  binding.exactObjectValue();
                  return false;
                }));
    assertThat(all.evalBoolean(emptyActivation())).isFalse();
    assertThat(allCalls).hasValue(2);

    for (Object[] values :
        List.of(
            new Object[] {null, message("match"), message("other")},
            new Object[] {message("match"), null, message("other")})) {
      AtomicInteger existsCalls = new AtomicInteger();
      NativeObjectExistsOneFold existsOne =
          existsOneFold(
              new TestListSource(ignored -> values),
              new TestPredicate(
                  binding -> {
                    existsCalls.incrementAndGet();
                    return ((TestAllTypes) binding.exactObjectValue())
                        .getSingleString()
                        .equals("match");
                  }));
      assertThat(
              catchThrowableOfType(
                      ValueSignal.class, () -> existsOne.evalBoolean(emptyActivation()))
                  .value)
          .isInstanceOf(Err.class);
      assertThat(existsCalls).hasValue(3);
    }
  }

  @Test
  void partialActivationUsesTheCompleteFallbackIncludingHierarchicalChildren() {
    AtomicInteger predicateCalls = new AtomicInteger();
    TestListSource source = new TestListSource(ignored -> new Object[] {message("value")});
    NativeObjectAllFold fold =
        allFold(
            source,
            new TestPredicate(
                ignored -> {
                  predicateCalls.incrementAndGet();
                  return true;
                }),
            False);
    Activation partial = newPartialActivation(Map.of());

    assertThat(fold.evalBoolean(partial)).isFalse();
    assertThat(predicateCalls).hasValue(0);
    assertThat(fold.evalBoolean(newHierarchicalActivation(newActivation(Map.of()), partial)))
        .isFalse();
    assertThat(predicateCalls).hasValue(0);
  }

  @SuppressWarnings("resource")
  @Test
  void immutableObjectFoldCanBeEvaluatedConcurrently() throws Exception {
    TestListSource source = new TestListSource(activation -> activation.resolve("values"));
    NativeObjectExistsOneFold fold =
        existsOneFold(
            source,
            new TestPredicate(
                binding ->
                    ((TestAllTypes) binding.exactObjectValue()).getSingleString().equals("match")));
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        boolean expected = (i & 1) == 0;
        Object[] values =
            expected
                ? new Object[] {message("match"), message("other")}
                : new Object[] {message("first"), message("second")};
        results.add(
            executor.submit(() -> fold.evalBoolean(newActivation(Map.of("values", values)))));
      }
      for (int i = 0; i < results.size(); i++) {
        assertThat(results.get(i).get(5, SECONDS)).isEqualTo((i & 1) == 0);
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  private static NativeObjectAllFold allFold(
      TestListSource source, NativeBooleanCapability predicate) {
    return allFold(source, predicate, True);
  }

  private static NativeObjectAllFold allFold(
      TestListSource source, NativeBooleanCapability predicate, Val fallbackResult) {
    return new NativeObjectAllFold(
        1L,
        "accumulator",
        new EvalConst(2L, True),
        "item",
        source,
        new NativeObjectListTraversal(source, MESSAGE_MATERIALIZER),
        new EvalConst(3L, False),
        new EvalConst(4L, True),
        new EvalConst(5L, fallbackResult),
        predicate,
        ADAPTER);
  }

  private static NativeObjectExistsOneFold existsOneFold(
      TestListSource source, NativeBooleanCapability predicate) {
    return new NativeObjectExistsOneFold(
        1L,
        "accumulator",
        new EvalConst(2L, True),
        "item",
        source,
        new NativeObjectListTraversal(source, MESSAGE_MATERIALIZER),
        new EvalConst(3L, False),
        new EvalConst(4L, True),
        new EvalConst(5L, True),
        predicate,
        ADAPTER);
  }

  private static TestAllTypes message(String value) {
    return TestAllTypes.newBuilder().setSingleString(value).build();
  }

  private static final class TestPredicate extends AbstractEval implements NativeBooleanCapability {
    private final Function<NativeLoopBinding, Boolean> predicate;

    private TestPredicate(Function<NativeLoopBinding, Boolean> predicate) {
      super(10L);
      this.predicate = predicate;
    }

    @Override
    public Val eval(Activation activation) {
      try {
        return ADAPTER.nativeToValue(evalBoolean(activation));
      } catch (ValueSignal failure) {
        return failure.value;
      }
    }

    @Override
    public boolean evalBoolean(Activation activation) {
      return predicate.apply((NativeLoopBinding) activation);
    }
  }

  private static final class TestListSource extends AbstractEval
      implements NativeListSourceCapability {
    private final CheckedAggregateMaterializer materializer =
        new CheckedAggregateMaterializer(
            EXACT_ADAPTER, Decls.newListType(Decls.newObjectType(MESSAGE_TYPE)));
    private final Function<Activation, Object> values;

    private TestListSource(Function<Activation, Object> values) {
      super(20L);
      this.values = values;
    }

    @Override
    public Val eval(Activation activation) {
      try {
        return materializeResolvedList(evalRaw(activation));
      } catch (ValueSignal failure) {
        return failure.value;
      }
    }

    @Override
    public Object evalRaw(Activation activation) {
      return values.apply(activation);
    }

    @Override
    public Val materializeResolvedList(Object value) {
      return materializer.materialize(value);
    }

    @Override
    public Val materializeResolvedElement(Object value) {
      return materializer.materializeListElement(value);
    }

    @Override
    public boolean exactListSource() {
      return true;
    }
  }
}
