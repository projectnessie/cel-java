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

import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Mapper;

/**
 * Characterizes host aggregate representations before introducing an exact aggregate-source SPI.
 *
 * <p>The zero-sized cases expose fixed source and adaptation overhead. Comparing raw traversal with
 * the two adapted traversals exposes element-proportional adaptation and callback costs. The shared
 * kernel deliberately uses a reusable typed consumer in a real summing operation rather than timing
 * an otherwise unused callback.
 */
@Warmup(iterations = 3, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class AggregateSourceBench {

  @State(Scope.Thread)
  public static class ListLikeState {
    @Param({"longArray", "objectArray", "list", "collection", "set"})
    public String representation;

    @Param({"0", "1", "16", "1024"})
    public int size;

    TypeRegistry adapter;
    Object raw;
    LongAggregateSource source;
    AdaptingSumConsumer consumer;
    long needle;

    @Setup
    public void setup() {
      adapter = newRegistry();
      raw = listLike(representation, size);
      source = source(representation, raw);
      consumer = new AdaptingSumConsumer(adapter);
      needle = size == 0 ? -1L : size - 1L;
    }
  }

  @Benchmark
  public Val establishedListLikeAdaptation(ListLikeState state) {
    return state.adapter.nativeToValue(state.raw);
  }

  @Benchmark
  public int structuralSize(ListLikeState state) {
    return state.source.size();
  }

  @Benchmark
  public long structuralIndexLast(ListLikeState state) {
    return state.size == 0 ? -1L : state.source.valueAt(state.size - 1);
  }

  @Benchmark
  public long structuralTraversal(ListLikeState state) {
    return state.source.rawSum();
  }

  @Benchmark
  public boolean structuralMembershipLast(ListLikeState state) {
    return state.source.contains(state.needle);
  }

  @Benchmark
  public long adaptedTraversalDirectSpecialized(ListLikeState state) {
    TypeRegistry adapter = state.adapter;
    return switch (state.representation) {
      case "longArray" -> {
        long sum = 0L;
        for (long value : (long[]) state.raw) {
          sum += adapter.nativeToValue(value).intValue();
        }
        yield sum;
      }
      case "objectArray" -> {
        long sum = 0L;
        for (Object value : (Object[]) state.raw) {
          sum += adapter.nativeToValue(value).intValue();
        }
        yield sum;
      }
      case "list" -> {
        long sum = 0L;
        List<?> values = (List<?>) state.raw;
        for (Object value : values) {
          sum += adapter.nativeToValue(value).intValue();
        }
        yield sum;
      }
      case "collection", "set" -> {
        long sum = 0L;
        for (Object value : (Collection<?>) state.raw) {
          sum += adapter.nativeToValue(value).intValue();
        }
        yield sum;
      }
      default -> throw new IllegalArgumentException(state.representation);
    };
  }

  @Benchmark
  public long adaptedTraversalSharedKernel(ListLikeState state) {
    AdaptingSumConsumer consumer = state.consumer;
    consumer.reset();
    state.source.forEach(consumer);
    return consumer.sum();
  }

  @State(Scope.Thread)
  public static class MapState {
    @Param({"0", "1", "16", "1024"})
    public int size;

    TypeRegistry adapter;
    Map<String, Long> raw;
    Mapper adapted;
    String lookupKey;
    Val adaptedLookupKey;

    @Setup
    public void setup() {
      adapter = newRegistry();
      raw = map(size);
      lookupKey = size == 0 ? "missing" : "key-" + (size - 1);
      adaptedLookupKey = adapter.nativeToValue(lookupKey);
      adapted = (Mapper) adapter.nativeToValue(raw);
    }
  }

  @Benchmark
  public Val establishedMapAdaptation(MapState state) {
    return state.adapter.nativeToValue(state.raw);
  }

  /**
   * Prototypes the current adapted-key snapshot without adapting values. Values remain raw until a
   * lookup or traversal consumes them.
   */
  @Benchmark
  public Map<Val, Object> mapAdaptedKeySnapshot(MapState state) {
    Map<Val, Object> snapshot = new HashMap<>(hashCapacity(state.raw.size()));
    for (Map.Entry<String, Long> entry : state.raw.entrySet()) {
      snapshot.put(state.adapter.nativeToValue(entry.getKey()), entry.getValue());
    }
    return snapshot;
  }

  @Benchmark
  public int mapStructuralSize(MapState state) {
    return state.raw.size();
  }

  @Benchmark
  public Object mapRawLookup(MapState state) {
    return state.raw.get(state.lookupKey);
  }

  @Benchmark
  public boolean mapRawMembership(MapState state) {
    return state.raw.containsKey(state.lookupKey);
  }

  /**
   * The map was key-snapshotted during setup, so this isolates lookup plus one lazy value
   * adaptation.
   */
  @Benchmark
  public Val mapLookupWithLazyValueAdaptation(MapState state) {
    return state.adapted.find(state.adaptedLookupKey);
  }

  @Benchmark
  public Val mapAdaptedMembership(MapState state) {
    return state.adapted.contains(state.adaptedLookupKey);
  }

  @Benchmark
  public long mapRawTraversal(MapState state) {
    long sum = 0L;
    for (Long value : state.raw.values()) {
      sum += value;
    }
    return sum;
  }

  /** Traverses the adapted-key snapshot and forces lazy adaptation of every corresponding value. */
  @Benchmark
  public long mapTraversalWithLazyValueAdaptation(MapState state) {
    long sum = 0L;
    IteratorT keys = state.adapted.iterator();
    while (keys.hasNext().booleanValue()) {
      Val value = state.adapted.find(keys.next());
      sum += value.intValue();
    }
    return sum;
  }

  private interface LongAggregateSource {
    int size();

    long valueAt(int index);

    long rawSum();

    boolean contains(long needle);

    void forEach(LongElementConsumer consumer);
  }

  private interface LongElementConsumer {
    void acceptLong(long value);

    void acceptObject(Object value);
  }

  private static final class AdaptingSumConsumer implements LongElementConsumer {
    private final TypeRegistry adapter;
    private long sum;

    private AdaptingSumConsumer(TypeRegistry adapter) {
      this.adapter = adapter;
    }

    void reset() {
      sum = 0L;
    }

    long sum() {
      return sum;
    }

    @Override
    public void acceptLong(long value) {
      sum += adapter.nativeToValue(value).intValue();
    }

    @Override
    public void acceptObject(Object value) {
      sum += adapter.nativeToValue(value).intValue();
    }
  }

  private record LongArraySource(long[] values) implements LongAggregateSource {

    @Override
    public int size() {
      return values.length;
    }

    @Override
    public long valueAt(int index) {
      return values[index];
    }

    @Override
    public long rawSum() {
      long sum = 0L;
      for (long value : values) {
        sum += value;
      }
      return sum;
    }

    @Override
    public boolean contains(long needle) {
      for (long value : values) {
        if (value == needle) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void forEach(LongElementConsumer consumer) {
      for (long value : values) {
        consumer.acceptLong(value);
      }
    }
  }

  private record ObjectArraySource(Long[] values) implements LongAggregateSource {

    @Override
    public int size() {
      return values.length;
    }

    @Override
    public long valueAt(int index) {
      return values[index];
    }

    @Override
    public long rawSum() {
      long sum = 0L;
      for (Long value : values) {
        sum += value;
      }
      return sum;
    }

    @Override
    public boolean contains(long needle) {
      for (Long value : values) {
        if (value == needle) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void forEach(LongElementConsumer consumer) {
      for (Long value : values) {
        consumer.acceptObject(value);
      }
    }
  }

  private record ListSource(List<Long> values) implements LongAggregateSource {

    @Override
    public int size() {
      return values.size();
    }

    @Override
    public long valueAt(int index) {
      return values.get(index);
    }

    @Override
    public long rawSum() {
      long sum = 0L;
      for (Long value : values) {
        sum += value;
      }
      return sum;
    }

    @Override
    public boolean contains(long needle) {
      return values.contains(needle);
    }

    @Override
    public void forEach(LongElementConsumer consumer) {
      for (Long value : values) {
        consumer.acceptObject(value);
      }
    }
  }

  private record CollectionSource(Collection<Long> values) implements LongAggregateSource {

    @Override
    public int size() {
      return values.size();
    }

    @Override
    public long valueAt(int index) {
      int current = 0;
      for (Long value : values) {
        if (current++ == index) {
          return value;
        }
      }
      throw new IndexOutOfBoundsException(index);
    }

    @Override
    public long rawSum() {
      long sum = 0L;
      for (Long value : values) {
        sum += value;
      }
      return sum;
    }

    @Override
    public boolean contains(long needle) {
      return values.contains(needle);
    }

    @Override
    public void forEach(LongElementConsumer consumer) {
      for (Long value : values) {
        consumer.acceptObject(value);
      }
    }
  }

  private static Object listLike(String representation, int size) {
    return switch (representation) {
      case "longArray" -> {
        long[] values = new long[size];
        for (int i = 0; i < size; i++) {
          values[i] = i;
        }
        yield values;
      }
      case "objectArray" -> {
        Long[] values = new Long[size];
        for (int i = 0; i < size; i++) {
          values[i] = (long) i;
        }
        yield values;
      }
      case "list" -> {
        List<Long> values = new ArrayList<>(size);
        addValues(values, size);
        yield values;
      }
      case "collection" -> {
        Collection<Long> values = new ArrayDeque<>(Math.max(1, size));
        addValues(values, size);
        yield values;
      }
      case "set" -> {
        Set<Long> values = new LinkedHashSet<>(hashCapacity(size));
        addValues(values, size);
        yield values;
      }
      default -> throw new IllegalArgumentException(representation);
    };
  }

  @SuppressWarnings("unchecked")
  private static LongAggregateSource source(String representation, Object raw) {
    return switch (representation) {
      case "longArray" -> new LongArraySource((long[]) raw);
      case "objectArray" -> new ObjectArraySource((Long[]) raw);
      case "list" -> new ListSource((List<Long>) raw);
      case "collection", "set" -> new CollectionSource((Collection<Long>) raw);
      default -> throw new IllegalArgumentException(representation);
    };
  }

  private static void addValues(Collection<Long> values, int size) {
    for (long i = 0; i < size; i++) {
      values.add(i);
    }
  }

  private static Map<String, Long> map(int size) {
    Map<String, Long> values = new LinkedHashMap<>(hashCapacity(size));
    for (long i = 0; i < size; i++) {
      values.put("key-" + i, i);
    }
    return values;
  }

  private static int hashCapacity(int size) {
    return size * 4 / 3 + 1;
  }
}
