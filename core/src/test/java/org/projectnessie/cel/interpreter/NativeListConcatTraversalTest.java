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
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.functions.Overload;

class NativeListConcatTraversalTest {
  private static final ExactAdapter ADAPTER = new ExactAdapter();
  private static final Overload ADD = standardAdd();

  @Test
  void resolvesAndSizesThreeSegmentsBeforeVisitingTheFirstElement() {
    List<String> events = new ArrayList<>();
    NativeListSourceCapability first =
        source("first", events, () -> new LoggingCollection("first", events, 1L, 2L));
    NativeListSourceCapability second =
        source("second", events, () -> new LoggingCollection("second", events, 3L));
    NativeListSourceCapability third =
        source("third", events, () -> new LoggingCollection("third", events, 4L, 5L));
    NativeListTraversalPlan plan = NativeListTraversalPlan.concat(concat(first, second, third));

    assertThat(plan).isNotNull();
    assertThat(plan.sourceCount()).isEqualTo(3);
    NativeResolvedListTraversal resolved = plan.resolve(emptyActivation());

    assertThat(events)
        .containsExactly(
            "resolve:first",
            "resolve:second",
            "resolve:third",
            "size:first",
            "size:second",
            "size:third");

    NativeLoopBinding binding = new NativeLoopBinding(emptyActivation(), "value");
    resolved.traverse(
        NativeScalarKind.INT,
        binding,
        ignored -> {
          events.add("predicate:" + binding.intValue(ADAPTER));
          return false;
        });

    assertThat(events.indexOf("predicate:1")).isGreaterThan(events.indexOf("size:third"));
    assertThat(events)
        .filteredOn(event -> event.startsWith("predicate:"))
        .containsExactly("predicate:1", "predicate:2", "predicate:3", "predicate:4", "predicate:5");
  }

  @Test
  void concatBackedListFoldRetainsValuesFromEveryPreparedSegment() {
    NativeListSourceCapability first = source(() -> new long[] {1L, 2L});
    NativeListSourceCapability second = source(() -> new long[] {3L});
    NativeListSourceCapability third = source(() -> new long[] {4L, 5L, 6L});
    NativeListConcat concat = concat(first, second, third);
    NativeListTraversalPlan plan = NativeListTraversalPlan.concat(concat);
    NativeIntLocalIdent transform = new NativeIntLocalIdent(20L, "value", ADAPTER);
    NativeScalarListFold fold =
        new NativeScalarListFold(
            10L,
            "value",
            concat,
            plan,
            null,
            transform,
            NativeScalarKind.INT,
            null,
            transform,
            NativeScalarKind.INT,
            ADAPTER);

    NativeIntAggregateValues values = fold.evalIntValues(emptyActivation());

    assertThat(values.size()).isEqualTo(6);
    assertThat(Arrays.copyOf(values.values(), values.size()))
        .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
  }

  private static NativeListConcat concat(NativeListSourceCapability... sources) {
    NativeListConcat concat = new NativeListConcat(100L, sources[0], sources[1], ADD);
    for (int i = 2; i < sources.length; i++) {
      concat = new NativeListConcat(100L + i, concat, sources[i], ADD);
    }
    return concat;
  }

  private static NativeListSourceCapability source(Supplier<Object> value) {
    return new TestListSource(1L, value);
  }

  private static NativeListSourceCapability source(
      String name, List<String> events, Supplier<Object> value) {
    return new TestListSource(
        name.hashCode(),
        () -> {
          events.add("resolve:" + name);
          return value.get();
        });
  }

  private static Overload standardAdd() {
    return Arrays.stream(Overload.standardOverloads())
        .filter(overload -> overload.operator.equals(Operator.Add.id))
        .findFirst()
        .orElseThrow();
  }

  private static final class TestListSource extends AbstractEval
      implements NativeListSourceCapability {
    private final CheckedAggregateMaterializer materializer =
        new CheckedAggregateMaterializer(ADAPTER, Decls.newListType(Decls.Int));
    private final Supplier<Object> value;

    private TestListSource(long id, Supplier<Object> value) {
      super(id);
      this.value = value;
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
      return value.get();
    }

    @Override
    public Val materializeResolvedList(Object value) {
      return value instanceof Val val && (isError(val) || isUnknown(val))
          ? val
          : materializer.materialize(value);
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

  private static final class LoggingCollection extends AbstractCollection<Long> {
    private final String name;
    private final List<String> events;
    private final List<Long> values;

    private LoggingCollection(String name, List<String> events, Long... values) {
      this.name = name;
      this.events = events;
      this.values = List.of(values);
    }

    @Override
    public Iterator<Long> iterator() {
      return values.iterator();
    }

    @Override
    public int size() {
      events.add("size:" + name);
      return values.size();
    }
  }

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }
}
