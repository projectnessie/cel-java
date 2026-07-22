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
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.NullT.NullValue;
import static org.projectnessie.cel.common.types.StringT.stringOf;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.MapT;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeAdapterSupport;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;

class AggregateSourceSemanticsTest {

  @Test
  void listBackedValuesRemainLiveWhileGenericCollectionsAreSnapshotted() {
    List<Object> listSource = new ArrayList<>(List.of("list-before", "list-second"));
    Set<Object> collectionSource =
        new LinkedHashSet<>(List.of("collection-before", "collection-second"));

    Lister liveList = (Lister) DefaultTypeAdapter.Instance.nativeToValue(listSource);
    Lister snapshottedCollection =
        (Lister) DefaultTypeAdapter.Instance.nativeToValue(collectionSource);

    listSource.set(0, "list-after");
    collectionSource.clear();
    collectionSource.add("collection-after");

    assertThat(liveList.nativeSize()).isEqualTo(2);
    assertThat(liveList.get(intOf(0))).isEqualTo(stringOf("list-after"));
    assertThat(snapshottedCollection.nativeSize()).isEqualTo(2);
    assertThat(listValues(snapshottedCollection))
        .containsExactly("collection-before", "collection-second");
  }

  @Test
  void genericCollectionUsesToArrayRatherThanIteratorOrder() {
    DivergentCollection source =
        new DivergentCollection(List.of("iterator-first", "iterator-second"));
    RecordingAdapter adapter = new RecordingAdapter();

    Lister list = (Lister) adapter.nativeToValue(source);

    assertThat(source.toArrayCalls).isEqualTo(1);
    assertThat(source.iteratorCalls).isZero();
    assertThat(adapter.adaptationCount("iterator-first")).isZero();
    assertThat(adapter.adaptationCount("iterator-second")).isZero();

    assertThat(listValues(list)).containsExactly("iterator-second", "iterator-first");
    assertThat(source.toArrayCalls).isEqualTo(1);
    assertThat(source.iteratorCalls).isZero();
    assertThat(adapter.adaptationCount("iterator-first")).isEqualTo(1);
    assertThat(adapter.adaptationCount("iterator-second")).isEqualTo(1);
  }

  @Test
  void setSnapshotPreservesToArrayEncounterOrderAndDefersElementAdaptation() {
    String first = distinctString("first");
    String second = distinctString("second");
    String third = distinctString("third");
    LinkedHashSet<Object> source = new LinkedHashSet<>(List.of(first, second, third));
    RecordingAdapter adapter = new RecordingAdapter();

    Lister list = (Lister) adapter.nativeToValue(source);
    source.clear();
    source.add("replacement");

    assertThat(adapter.adaptationCount(first)).isZero();
    assertThat(adapter.adaptationCount(second)).isZero();
    assertThat(adapter.adaptationCount(third)).isZero();
    assertThat(listValues(list)).containsExactly("first", "second", "third");
    assertThat(adapter.adaptationCount(first)).isEqualTo(1);
    assertThat(adapter.adaptationCount(second)).isEqualTo(1);
    assertThat(adapter.adaptationCount(third)).isEqualTo(1);
  }

  @Test
  void mapSnapshotsAdaptedKeysAndRetainsValuesForLazyAdaptation() {
    Long firstKey = 1L;
    Long secondKey = 17L;
    Long thirdKey = 33L;
    String firstValue = distinctString("one");
    String secondValue = distinctString("seventeen");
    String thirdValue = distinctString("thirty-three");
    CountingLinkedHashMap<Object, Object> source = new CountingLinkedHashMap<>();
    source.put(firstKey, firstValue);
    source.put(secondKey, secondValue);
    source.put(thirdKey, thirdValue);
    RecordingAdapter adapter = new RecordingAdapter();

    MapT map = (MapT) adapter.nativeToValue(source);

    // The first pass determines whether the map is already Val-backed and stops at the first raw
    // key. The second pass snapshots all adapted keys.
    assertThat(source.entrySetCalls).isEqualTo(2);
    assertThat(source.iteratorCalls).isEqualTo(2);
    assertThat(adapter.adaptationCount(firstKey)).isEqualTo(1);
    assertThat(adapter.adaptationCount(secondKey)).isEqualTo(1);
    assertThat(adapter.adaptationCount(thirdKey)).isEqualTo(1);
    assertThat(adapter.adaptationCount(firstValue)).isZero();
    assertThat(adapter.adaptationCount(secondValue)).isZero();
    assertThat(adapter.adaptationCount(thirdValue)).isZero();

    // The adapted HashMap owns key encounter order rather than retaining the source map's
    // iteration mechanism. Derive the expectation from the same documented implementation type
    // instead of pinning an unspecified JDK HashMap order.
    Map<Val, Object> expectedKeySnapshot = new HashMap<>(source.size() * 4 / 3 + 1);
    expectedKeySnapshot.put(intOf(1), firstValue);
    expectedKeySnapshot.put(intOf(17), secondValue);
    expectedKeySnapshot.put(intOf(33), thirdValue);
    assertThat(mapKeys(map))
        .containsExactlyElementsOf(
            expectedKeySnapshot.keySet().stream().map(Val::intValue).toList());
    assertThat(map.contains(intOf(17))).isSameAs(True);
    assertThat(adapter.adaptationCount(secondValue)).isZero();

    source.clear();
    source.put(99L, "replacement");
    assertThat(map.nativeSize()).isEqualTo(3);
    assertThat(map.find(intOf(99))).isNull();
    assertThat(map.find(intOf(17))).isEqualTo(stringOf("seventeen"));
    assertThat(map.find(intOf(17))).isEqualTo(stringOf("seventeen"));
    assertThat(adapter.adaptationCount(secondValue)).isEqualTo(2);
    assertThat(adapter.adaptationCount(firstValue)).isZero();
    assertThat(adapter.adaptationCount(thirdValue)).isZero();
  }

  @Test
  void nullElementsAndValuesRemainDistinctFromAbsentKeys() {
    List<Object> nullableElements = new ArrayList<>();
    nullableElements.add(null);
    nullableElements.add("present");
    Lister list = (Lister) DefaultTypeAdapter.Instance.nativeToValue(nullableElements);
    Map<Object, Object> source = new LinkedHashMap<>();
    source.put("null", null);
    MapT map = (MapT) DefaultTypeAdapter.Instance.nativeToValue(source);

    assertThat(list.get(intOf(0))).isSameAs(NullValue);
    assertThat(map.contains(stringOf("null"))).isSameAs(True);
    assertThat(map.find(stringOf("null"))).isSameAs(NullValue);
    assertThat(map.contains(stringOf("absent"))).isSameAs(False);
    assertThat(map.find(stringOf("absent"))).isNull();
  }

  @Test
  void nullAndCelEquivalentDuplicateMapKeysAreRejectedDuringAdaptation() {
    Map<Object, Object> nullKey = new LinkedHashMap<>();
    nullKey.put(null, "value");
    Map<Object, Object> duplicateNumericKeys = new LinkedHashMap<>();
    duplicateNumericKeys.put(1L, "int");
    duplicateNumericKeys.put(1.0d, "double");

    assertThat(DefaultTypeAdapter.Instance.nativeToValue(nullKey)).matches(Err::isError);
    assertThat(DefaultTypeAdapter.Instance.nativeToValue(duplicateNumericKeys))
        .matches(Err::isError);
  }

  @Test
  void listEqualityStopsAtTheFirstLeftOrRightError() {
    Val leftError = Err.newErr("left failed");
    Val rightError = Err.newErr("right failed");
    String leftTail = distinctString("left tail");
    String rightHead = distinctString("right head");
    RecordingAdapter adapter = new RecordingAdapter();
    Lister left = (Lister) adapter.nativeToValue(new Object[] {leftError, leftTail});
    Lister right = (Lister) adapter.nativeToValue(new Object[] {rightHead, rightError});
    adapter.clear();

    assertThat(left.equal(right)).isSameAs(leftError);
    assertThat(adapter.adaptationCount(leftError)).isEqualTo(1);
    assertThat(adapter.adaptationCount(rightHead)).isZero();
    assertThat(adapter.adaptationCount(leftTail)).isZero();
    assertThat(adapter.adaptationCount(rightError)).isZero();

    Lister successfulLeft = (Lister) adapter.nativeToValue(new Object[] {rightHead});
    Lister failingRight = (Lister) adapter.nativeToValue(new Object[] {rightError});
    adapter.clear();

    assertThat(successfulLeft.equal(failingRight)).isSameAs(rightError);
    assertThat(adapter.adaptationCount(rightHead)).isEqualTo(1);
    assertThat(adapter.adaptationCount(rightError)).isEqualTo(1);
  }

  private static List<Object> listValues(Lister list) {
    List<Object> values = new ArrayList<>();
    for (int i = 0; i < list.nativeSize(); i++) {
      values.add(list.get(intOf(i)).value());
    }
    return values;
  }

  private static String distinctString(String value) {
    // RecordingAdapter deliberately counts adaptations by object identity.
    return new String(value.toCharArray());
  }

  private static List<Long> mapKeys(MapT map) {
    List<Long> keys = new ArrayList<>();
    IteratorT iterator = map.iterator();
    while (iterator.hasNext() == True) {
      keys.add(iterator.next().intValue());
    }
    return keys;
  }

  private static final class RecordingAdapter implements TypeAdapter {
    private final List<Object> adaptations = new ArrayList<>();

    @Override
    public Val nativeToValue(Object value) {
      adaptations.add(value);
      Val adapted = TypeAdapterSupport.maybeNativeToValue(this, value);
      return adapted != null ? adapted : DefaultTypeAdapter.Instance.nativeToValue(value);
    }

    int adaptationCount(Object value) {
      int count = 0;
      for (Object adapted : adaptations) {
        if (adapted == value) {
          count++;
        }
      }
      return count;
    }

    void clear() {
      adaptations.clear();
    }
  }

  private static final class DivergentCollection extends AbstractCollection<Object> {
    private final List<Object> iteratorOrder;
    private int iteratorCalls;
    private int toArrayCalls;

    private DivergentCollection(List<Object> iteratorOrder) {
      this.iteratorOrder = iteratorOrder;
    }

    @Override
    public Iterator<Object> iterator() {
      iteratorCalls++;
      return iteratorOrder.iterator();
    }

    @Override
    public int size() {
      return iteratorOrder.size();
    }

    @Override
    public Object[] toArray() {
      toArrayCalls++;
      Object[] values = iteratorOrder.toArray();
      for (int left = 0, right = values.length - 1; left < right; left++, right--) {
        Object value = values[left];
        values[left] = values[right];
        values[right] = value;
      }
      return values;
    }
  }

  private static final class CountingLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
    private int entrySetCalls;
    private int iteratorCalls;

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
      entrySetCalls++;
      Set<Map.Entry<K, V>> entries = super.entrySet();
      return new AbstractSet<>() {
        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
          iteratorCalls++;
          return entries.iterator();
        }

        @Override
        public int size() {
          return entries.size();
        }
      };
    }
  }
}
