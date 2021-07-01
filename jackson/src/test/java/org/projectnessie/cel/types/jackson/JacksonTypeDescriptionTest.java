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
package org.projectnessie.cel.types.jackson;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.types.jackson.JacksonRegistry.newRegistry;

import com.fasterxml.jackson.databind.JavaType;
import com.google.api.expr.v1alpha1.Type.ListType;
import com.google.api.expr.v1alpha1.Type.MapType;
import com.google.api.expr.v1alpha1.Type.TypeKindCase;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.IntT;
import org.projectnessie.cel.common.types.ListT;
import org.projectnessie.cel.common.types.MapT;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.ObjectT;
import org.projectnessie.cel.common.types.TypeT;
import org.projectnessie.cel.common.types.pb.Checked;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.types.jackson.types.AnEnum;
import org.projectnessie.cel.types.jackson.types.CollectionsObject;
import org.projectnessie.cel.types.jackson.types.InnerType;

class JacksonTypeDescriptionTest {

  @Test
  void basics() {
    JacksonRegistry reg = (JacksonRegistry) newRegistry();

    reg.register(CollectionsObject.class);
    com.google.api.expr.v1alpha1.Type t = reg.findType(CollectionsObject.class.getName());
    assertThat(t)
        .extracting(
            com.google.api.expr.v1alpha1.Type::getMessageType,
            com.google.api.expr.v1alpha1.Type::getTypeKindCase)
        .containsExactly(CollectionsObject.class.getName(), TypeKindCase.MESSAGE_TYPE);

    JacksonTypeDescription td = reg.typeDescription(CollectionsObject.class);
    assertThat(td)
        .extracting(
            JacksonTypeDescription::pbType,
            JacksonTypeDescription::reflectType,
            JacksonTypeDescription::name,
            JacksonTypeDescription::type)
        .containsExactly(
            t,
            CollectionsObject.class,
            CollectionsObject.class.getName(),
            TypeT.newObjectTypeValue(CollectionsObject.class.getName()));

    // check that the nested-class `InnerType` has been implicitly registered

    JacksonTypeDescription tdInner = reg.typeDescription(InnerType.class);
    assertThat(tdInner)
        .extracting(
            JacksonTypeDescription::pbType,
            JacksonTypeDescription::reflectType,
            JacksonTypeDescription::name,
            JacksonTypeDescription::type)
        .containsExactly(
            com.google.api.expr.v1alpha1.Type.newBuilder()
                .setMessageType(InnerType.class.getName())
                .build(),
            InnerType.class,
            InnerType.class.getName(),
            TypeT.newObjectTypeValue(InnerType.class.getName()));

    //

    assertThat(reg)
        .extracting(
            r -> r.findIdent(CollectionsObject.class.getName()),
            r -> r.findIdent(InnerType.class.getName()),
            r -> r.findIdent(AnEnum.class.getName() + '.' + AnEnum.ENUM_VALUE_2.name()))
        .containsExactly(
            TypeT.newObjectTypeValue(CollectionsObject.class.getName()),
            TypeT.newObjectTypeValue(InnerType.class.getName()),
            intOf(AnEnum.ENUM_VALUE_2.ordinal()));

    assertThatThrownBy(() -> reg.typeDescription(AnEnum.class))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> reg.enumDescription(InnerType.class))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void types() {
    JacksonRegistry reg = (JacksonRegistry) newRegistry();
    reg.register(CollectionsObject.class);

    // verify the map-type-fields

    checkMapType(
        reg,
        "stringBooleanMap",
        String.class,
        Checked.checkedString,
        Boolean.class,
        Checked.checkedBool);
    checkMapType(
        reg, "byteShortMap", Byte.class, Checked.checkedInt, Short.class, Checked.checkedInt);
    checkMapType(
        reg, "intLongMap", Integer.class, Checked.checkedInt, Long.class, Checked.checkedInt);
    checkMapType(
        reg,
        "ulongTimestampMap",
        ULong.class,
        Checked.checkedUint,
        Timestamp.class,
        Checked.checkedTimestamp);
    checkMapType(
        reg,
        "ulongZonedDateTimeMap",
        ULong.class,
        Checked.checkedUint,
        ZonedDateTime.class,
        Checked.checkedTimestamp);
    checkMapType(
        reg,
        "stringProtoDurationMap",
        String.class,
        Checked.checkedString,
        Duration.class,
        Checked.checkedDuration);
    checkMapType(
        reg,
        "stringJavaDurationMap",
        String.class,
        Checked.checkedString,
        java.time.Duration.class,
        Checked.checkedDuration);
    checkMapType(
        reg,
        "stringBytesMap",
        String.class,
        Checked.checkedString,
        ByteString.class,
        Checked.checkedBytes);
    checkMapType(
        reg,
        "floatDoubleMap",
        Float.class,
        Checked.checkedDouble,
        Double.class,
        Checked.checkedDouble);

    // verify the list-type-fields

    checkListType(reg, "stringList", String.class, Checked.checkedString);
    checkListType(reg, "booleanList", Boolean.class, Checked.checkedBool);
    checkListType(reg, "byteList", Byte.class, Checked.checkedInt);
    checkListType(reg, "shortList", Short.class, Checked.checkedInt);
    checkListType(reg, "intList", Integer.class, Checked.checkedInt);
    checkListType(reg, "longList", Long.class, Checked.checkedInt);
    checkListType(reg, "ulongList", ULong.class, Checked.checkedUint);
    checkListType(reg, "timestampList", Timestamp.class, Checked.checkedTimestamp);
    checkListType(reg, "zonedDateTimeList", ZonedDateTime.class, Checked.checkedTimestamp);
    checkListType(reg, "durationList", Duration.class, Checked.checkedDuration);
    checkListType(reg, "javaDurationList", java.time.Duration.class, Checked.checkedDuration);
    checkListType(reg, "bytesList", ByteString.class, Checked.checkedBytes);
    checkListType(reg, "floatList", Float.class, Checked.checkedDouble);
    checkListType(reg, "doubleList", Double.class, Checked.checkedDouble);
  }

  private void checkListType(
      JacksonRegistry reg,
      String prop,
      Class<?> valueClass,
      com.google.api.expr.v1alpha1.Type valueType) {
    JacksonFieldType ft =
        (JacksonFieldType) reg.findFieldType(CollectionsObject.class.getName(), prop);
    assertThat(ft).isNotNull();
    JavaType javaType = ft.propertyWriter().getType();

    assertThat(javaType).extracting(JavaType::isCollectionLikeType).isEqualTo(true);
    assertThat(javaType.getContentType()).extracting(JavaType::getRawClass).isSameAs(valueClass);

    assertThat(ft.type)
        .extracting(com.google.api.expr.v1alpha1.Type::getListType)
        .extracting(ListType::getElemType)
        .isSameAs(valueType);
  }

  private void checkMapType(
      JacksonRegistry reg,
      String prop,
      Class<?> keyClass,
      com.google.api.expr.v1alpha1.Type keyType,
      Class<?> valueClass,
      com.google.api.expr.v1alpha1.Type valueType) {
    JacksonFieldType ft =
        (JacksonFieldType) reg.findFieldType(CollectionsObject.class.getName(), prop);
    assertThat(ft).isNotNull();
    JavaType javaType = ft.propertyWriter().getType();

    assertThat(javaType).extracting(JavaType::isMapLikeType).isEqualTo(true);
    assertThat(javaType.getKeyType()).extracting(JavaType::getRawClass).isSameAs(keyClass);
    assertThat(javaType.getContentType()).extracting(JavaType::getRawClass).isSameAs(valueClass);

    assertThat(ft.type)
        .extracting(com.google.api.expr.v1alpha1.Type::getMapType)
        .extracting(MapType::getKeyType, MapType::getValueType)
        .containsExactly(keyType, valueType);
  }

  @Test
  void unknownProperties() {
    CollectionsObject collectionsObject = new CollectionsObject();

    JacksonRegistry reg = (JacksonRegistry) newRegistry();
    reg.register(CollectionsObject.class);

    Val collectionsVal = reg.nativeToValue(collectionsObject);
    assertThat(collectionsVal).isInstanceOf(ObjectT.class);
    ObjectT obj = (ObjectT) collectionsVal;

    Val x = obj.isSet(stringOf("bart"));
    assertThat(x)
        .isInstanceOf(Err.class)
        .extracting(e -> (Err) e)
        .extracting(Err::value)
        .isEqualTo("no such field 'bart'");

    x = obj.get(stringOf("bart"));
    assertThat(x)
        .isInstanceOf(Err.class)
        .extracting(e -> (Err) e)
        .extracting(Err::value)
        .isEqualTo("no such field 'bart'");
  }

  @Test
  void collectionsObjectEmpty() {
    CollectionsObject collectionsObject = new CollectionsObject();

    JacksonRegistry reg = (JacksonRegistry) newRegistry();
    reg.register(CollectionsObject.class);

    Val collectionsVal = reg.nativeToValue(collectionsObject);
    assertThat(collectionsVal).isInstanceOf(ObjectT.class);
    ObjectT obj = (ObjectT) collectionsVal;

    for (String field : CollectionsObject.ALL_PROPERTIES) {
      assertThat(obj.isSet(stringOf(field))).isSameAs(False);
      assertThat(obj.get(stringOf(field))).isSameAs(NullT.NullValue);
    }
  }

  @Test
  void collectionsObjectTypeTest() throws Exception {
    CollectionsObject collectionsObject = new CollectionsObject();

    // populate (primitive) map types

    collectionsObject.stringBooleanMap = singletonMap("a", true);
    collectionsObject.byteShortMap = singletonMap((byte) 1, (short) 2);
    collectionsObject.intLongMap = singletonMap(1, 2L);
    collectionsObject.ulongTimestampMap =
        singletonMap(ULong.valueOf(1), Timestamp.newBuilder().setSeconds(1).build());
    collectionsObject.ulongZonedDateTimeMap =
        singletonMap(
            ULong.valueOf(1),
            ZonedDateTime.of(LocalDateTime.ofEpochSecond(1, 0, ZoneOffset.UTC), ZoneId.of("UTC")));
    collectionsObject.stringProtoDurationMap =
        singletonMap("a", Duration.newBuilder().setSeconds(1).build());
    collectionsObject.stringJavaDurationMap = singletonMap("a", java.time.Duration.ofSeconds(1));
    collectionsObject.stringBytesMap =
        singletonMap("a", ByteString.copyFrom(new byte[] {(byte) 1}));
    collectionsObject.floatDoubleMap = singletonMap(1f, 2d);

    // populate (primitive) list types

    collectionsObject.stringList = asList("a", "b", "c");
    collectionsObject.booleanList = asList(true, true, false, false);
    collectionsObject.byteList = asList((byte) 1, (byte) 2, (byte) 3);
    collectionsObject.shortList = asList((short) 4, (short) 5, (short) 6);
    collectionsObject.intList = asList(7, 8, 9);
    collectionsObject.longList = asList(10L, 11L, 12L);
    collectionsObject.ulongList = asList(ULong.valueOf(1), ULong.valueOf(2), ULong.valueOf(3));
    collectionsObject.timestampList =
        asList(
            Timestamp.newBuilder().setSeconds(1).build(),
            Timestamp.newBuilder().setSeconds(2).build(),
            Timestamp.newBuilder().setSeconds(3).build());
    collectionsObject.zonedDateTimeList =
        asList(
            ZonedDateTime.of(LocalDateTime.ofEpochSecond(1, 0, ZoneOffset.UTC), ZoneId.of("UTC")),
            ZonedDateTime.of(LocalDateTime.ofEpochSecond(2, 0, ZoneOffset.UTC), ZoneId.of("UTC")),
            ZonedDateTime.of(LocalDateTime.ofEpochSecond(3, 0, ZoneOffset.UTC), ZoneId.of("UTC")));
    collectionsObject.durationList =
        asList(
            Duration.newBuilder().setSeconds(1).build(),
            Duration.newBuilder().setSeconds(2).build(),
            Duration.newBuilder().setSeconds(3).build());
    collectionsObject.javaDurationList =
        asList(
            java.time.Duration.ofSeconds(1),
            java.time.Duration.ofSeconds(2),
            java.time.Duration.ofSeconds(3));
    collectionsObject.bytesList =
        asList(
            ByteString.copyFrom(new byte[] {(byte) 1}),
            ByteString.copyFrom(new byte[] {(byte) 2}),
            ByteString.copyFrom(new byte[] {(byte) 3}));
    collectionsObject.floatList = asList(1f, 2f, 3f);
    collectionsObject.doubleList = asList(1d, 2d, 3d);

    // populate inner/nested type list/map

    InnerType inner1 = new InnerType();
    inner1.intProp = 1;
    inner1.wrappedIntProp = 2;
    collectionsObject.stringInnerMap = singletonMap("a", inner1);

    InnerType inner2 = new InnerType();
    inner2.intProp = 3;
    inner2.wrappedIntProp = 4;
    collectionsObject.innerTypes = asList(inner1, inner2);

    // populate enum-related fields

    collectionsObject.anEnum = AnEnum.ENUM_VALUE_2;
    collectionsObject.anEnumList = asList(AnEnum.ENUM_VALUE_2, AnEnum.ENUM_VALUE_3);
    collectionsObject.anEnumStringMap = singletonMap(AnEnum.ENUM_VALUE_2, "a");
    collectionsObject.stringAnEnumMap = singletonMap("a", AnEnum.ENUM_VALUE_2);

    // prepare registry

    JacksonRegistry reg = (JacksonRegistry) newRegistry();
    reg.register(CollectionsObject.class);

    Val collectionsVal = reg.nativeToValue(collectionsObject);
    assertThat(collectionsVal).isInstanceOf(ObjectT.class);
    ObjectT obj = (ObjectT) collectionsVal;

    // briefly verify all fields

    for (String field : CollectionsObject.ALL_PROPERTIES) {
      assertThat(obj.isSet(stringOf(field))).isSameAs(True);
      assertThat(obj.get(stringOf(field))).isNotNull();

      Val fieldVal = obj.get(stringOf(field));
      Object fieldObj = CollectionsObject.class.getDeclaredField(field).get(collectionsObject);
      if (fieldObj instanceof Map) {
        assertThat(fieldVal).isInstanceOf(MapT.class);
      } else if (fieldObj instanceof List) {
        assertThat(fieldVal).isInstanceOf(ListT.class);
      }

      assertThat(fieldVal.equal(reg.nativeToValue(fieldObj))).isSameAs(True);
    }

    // check a few properties manually/explicitly

    MapT mapVal = (MapT) obj.get(stringOf("intLongMap"));
    assertThat(mapVal)
        .extracting(
            MapT::size,
            m -> m.contains(intOf(42)),
            m -> m.contains(intOf(1)),
            m -> m.contains(intOf(2)),
            m -> m.contains(intOf(3)),
            m -> m.get(intOf(1)))
        .containsExactly(intOf(1), False, True, False, False, intOf(2));

    ListT listVal = (ListT) obj.get(stringOf("ulongList"));
    assertThat(listVal)
        .extracting(
            ListT::size,
            l -> l.contains(uintOf(42)),
            l -> l.contains(uintOf(1)),
            l -> l.contains(uintOf(2)),
            l -> l.contains(uintOf(3)),
            l -> l.get(intOf(0)),
            l -> l.get(intOf(1)),
            l -> l.get(intOf(2)))
        .containsExactly(intOf(3), False, True, True, True, uintOf(1), uintOf(2), uintOf(3));

    mapVal = (MapT) obj.get(stringOf("stringInnerMap"));
    assertThat(mapVal)
        .extracting(MapT::size, m -> m.contains(stringOf("42")), m -> m.contains(stringOf("a")))
        .containsExactly(intOf(1), False, True);
    ObjectT i = (ObjectT) mapVal.get(stringOf("a"));
    assertThat(i)
        .extracting(o -> o.get(stringOf("intProp")), o -> o.get(stringOf("wrappedIntProp")))
        .containsExactly(intOf(1), intOf(2));

    listVal = (ListT) obj.get(stringOf("innerTypes"));
    assertThat(listVal).extracting(ListT::size).isEqualTo(intOf(2));
    i = (ObjectT) listVal.get(intOf(0));
    assertThat(i)
        .extracting(o -> o.get(stringOf("intProp")), o -> o.get(stringOf("wrappedIntProp")))
        .containsExactly(intOf(1), intOf(2));
    i = (ObjectT) listVal.get(intOf(1));
    assertThat(i)
        .extracting(o -> o.get(stringOf("intProp")), o -> o.get(stringOf("wrappedIntProp")))
        .containsExactly(intOf(3), intOf(4));

    // verify enums

    Val x = obj.get(stringOf("anEnum"));
    assertThat(x).isInstanceOf(IntT.class).isEqualTo(intOf(AnEnum.ENUM_VALUE_2.ordinal()));
    listVal = (ListT) obj.get(stringOf("anEnumList"));
    assertThat(listVal)
        .extracting(l -> l.get(intOf(0)), l -> l.get(intOf(1)))
        .containsExactly(
            intOf(AnEnum.ENUM_VALUE_2.ordinal()), intOf(AnEnum.ENUM_VALUE_3.ordinal()));
    mapVal = (MapT) obj.get(stringOf("anEnumStringMap"));
    assertThat(mapVal)
        .extracting(l -> l.get(intOf(AnEnum.ENUM_VALUE_2.ordinal())))
        .isEqualTo(stringOf("a"));
    mapVal = (MapT) obj.get(stringOf("stringAnEnumMap"));
    assertThat(mapVal)
        .extracting(l -> l.get(stringOf("a")))
        .isEqualTo(intOf(AnEnum.ENUM_VALUE_2.ordinal()));
  }
}
