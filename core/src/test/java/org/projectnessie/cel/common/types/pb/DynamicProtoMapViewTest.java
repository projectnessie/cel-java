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

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.StringT.stringOf;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.MapT;
import org.projectnessie.cel.common.types.ref.TypeRegistry;

class DynamicProtoMapViewTest {
  private static final String TYPE = TestAllTypes.getDescriptor().getFullName();

  @Test
  @SuppressWarnings("removal")
  void exactViewAndDefaultMapUseLastDuplicateAndUniqueLogicalSize() {
    DynamicMessage message =
        dynamicMap(
            entry("first", 1L),
            entry("duplicate", 2L),
            entry("middle", 3L),
            entry("duplicate", 4L),
            entry("last", 5L));

    TypeRegistry exact =
        ProtoTypeRegistry.newExactAggregateRegistry(TestAllTypes.getDefaultInstance());
    @SuppressWarnings("unchecked")
    Map<String, Long> exactMap =
        (Map<String, Long>) exact.findFieldType(TYPE, "map_string_int64").getFrom.getFrom(message);

    assertThat(exactMap).containsEntry("duplicate", 4L).hasSize(4);
    assertThat(exactMap.keySet()).containsExactly("first", "middle", "duplicate", "last");
    assertThat(exactMap).isEqualTo(Map.of("first", 1L, "middle", 3L, "duplicate", 4L, "last", 5L));

    ProtoTypeRegistry established =
        ProtoTypeRegistry.newRegistry(TestAllTypes.getDefaultInstance());
    MapT establishedMap =
        (MapT) established.findFieldType(TYPE, "map_string_int64").getFrom.getFrom(message);
    assertThat(establishedMap.nativeSize()).isEqualTo(4);
    assertThat(establishedMap.get(stringOf("duplicate")).intValue()).isEqualTo(4L);
    Map<?, ?> value = (Map<?, ?>) establishedMap.value();
    Map<?, ?> nativeValue = establishedMap.convertToNative(Map.class);
    assertThat(value).isEqualTo(Map.of("first", 1L, "middle", 3L, "duplicate", 4L, "last", 5L));
    assertThat(nativeValue)
        .isEqualTo(Map.of("first", 1L, "middle", 3L, "duplicate", 4L, "last", 5L));
    assertThat(new ArrayList<Object>(value.keySet()))
        .containsExactly("first", "middle", "duplicate", "last");
    assertThat(new ArrayList<Object>(nativeValue.keySet()))
        .containsExactly("first", "middle", "duplicate", "last");

    List<String> keys = new ArrayList<>();
    var iterator = establishedMap.iterator();
    while (iterator.hasNext().booleanValue()) {
      keys.add((String) iterator.next().value());
    }
    assertThat(keys).containsExactly("first", "middle", "duplicate", "last");
  }

  @Test
  void duplicateWireEntriesParseToTheSameGeneratedAndDynamicLogicalMap() throws Exception {
    DynamicMessage raw =
        dynamicMap(entry("duplicate", 1L), entry("other", 2L), entry("duplicate", 3L));
    byte[] wire = raw.toByteArray();

    TestAllTypes generated = TestAllTypes.parseFrom(wire);
    DynamicMessage dynamic = DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), wire);
    TypeRegistry exact =
        ProtoTypeRegistry.newExactAggregateRegistry(TestAllTypes.getDefaultInstance());

    assertThat(generated.getMapStringInt64Map()).isEqualTo(Map.of("duplicate", 3L, "other", 2L));
    assertThat(exact.findFieldType(TYPE, "map_string_int64").getFrom.getFrom(dynamic))
        .isEqualTo(generated.getMapStringInt64Map());
  }

  @Test
  void lookupDoesNotRequireCanonicalizationAndRejectsNonStringKeys() {
    FieldDescriptor field = mapField();
    DynamicProtoMapView view =
        new DynamicProtoMapView(field, List.of(entry("duplicate", 1L), entry("duplicate", 2L)));

    assertThat(view.get("duplicate")).isEqualTo(2L);
    assertThat(view.containsKey("duplicate")).isTrue();
    assertThat(view.get(1L)).isNull();
    assertThat(view.containsKey(1L)).isFalse();
    assertThat(view).hasSize(1);
  }

  @Test
  void immutableViewSupportsConcurrentLookupAndCanonicalTraversal() {
    DynamicProtoMapView view =
        new DynamicProtoMapView(
            mapField(),
            List.of(entry("first", 1L), entry("duplicate", 2L), entry("duplicate", 3L)));

    IntStream.range(0, 1_000)
        .parallel()
        .forEach(
            ignored -> {
              assertThat(view.get("duplicate")).isEqualTo(3L);
              assertThat(view).hasSize(2);
              assertThat(view.hashCode())
                  .isEqualTo(Map.of("first", 1L, "duplicate", 3L).hashCode());
            });

    assertThat(view).containsEntry("first", 1L).containsEntry("duplicate", 3L);
    assertThat(view.keySet()).containsExactly("first", "duplicate");
  }

  @Test
  void nonIntegerValuesPreserveDuplicateAndFloatingPointSemantics() {
    FieldDescriptor stringField = mapField("map_string_string");
    DynamicProtoMapView strings =
        new DynamicProtoMapView(
            stringField,
            List.of(
                entry(stringField, "first", "value"),
                entry(stringField, "duplicate", "old"),
                entry(stringField, "duplicate", "")));

    assertThat(strings.get("duplicate")).isEqualTo("");
    assertThat(strings).hasSize(2);
    assertThat(strings.keySet()).containsExactly("first", "duplicate");
    assertThat(strings.get("duplicate")).isEqualTo("");

    FieldDescriptor doubleField = mapField("map_string_double");
    DynamicProtoMapView doubles =
        new DynamicProtoMapView(
            doubleField,
            List.of(
                entry(doubleField, "negativeZero", -0.0d),
                entry(doubleField, "positiveZero", 0.0d),
                entry(doubleField, "nan", Double.NaN)));

    assertThat(Double.doubleToRawLongBits((double) doubles.get("negativeZero")))
        .isEqualTo(Double.doubleToRawLongBits(-0.0d));
    assertThat(Double.doubleToRawLongBits((double) doubles.get("positiveZero")))
        .isEqualTo(Double.doubleToRawLongBits(0.0d));
    assertThat((double) doubles.get("nan")).isNaN();
    assertThat(doubles).hasSize(3);
    assertThat(Double.doubleToRawLongBits((double) doubles.get("negativeZero")))
        .isEqualTo(Double.doubleToRawLongBits(-0.0d));
  }

  private static DynamicMessage dynamicMap(DynamicMessage... entries) {
    return DynamicMessage.newBuilder(TestAllTypes.getDescriptor())
        .setField(mapField(), List.of(entries))
        .build();
  }

  private static DynamicMessage entry(String key, long value) {
    FieldDescriptor field = mapField();
    return entry(field, key, value);
  }

  private static DynamicMessage entry(FieldDescriptor field, String key, Object value) {
    return DynamicMessage.newBuilder(field.getMessageType())
        .setField(field.getMessageType().findFieldByNumber(1), key)
        .setField(field.getMessageType().findFieldByNumber(2), value)
        .build();
  }

  private static FieldDescriptor mapField() {
    return mapField("map_string_int64");
  }

  private static FieldDescriptor mapField(String name) {
    return TestAllTypes.getDescriptor().findFieldByName(name);
  }
}
