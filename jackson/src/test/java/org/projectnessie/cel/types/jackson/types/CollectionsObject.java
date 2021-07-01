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
package org.projectnessie.cel.types.jackson.types;

import static java.util.Arrays.asList;

import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.common.ULong;

public class CollectionsObject {
  public Map<String, Boolean> stringBooleanMap;
  public Map<Byte, Short> byteShortMap;
  public Map<Integer, Long> intLongMap;
  public Map<ULong, Timestamp> ulongTimestampMap;
  public Map<ULong, ZonedDateTime> ulongZonedDateTimeMap;
  public Map<String, Duration> stringProtoDurationMap;
  public Map<String, java.time.Duration> stringJavaDurationMap;
  public Map<String, ByteString> stringBytesMap;
  public Map<Float, Double> floatDoubleMap;

  public List<String> stringList;
  public List<Boolean> booleanList;
  public List<Byte> byteList;
  public List<Short> shortList;
  public List<Integer> intList;
  public List<Long> longList;
  public List<ULong> ulongList;
  public List<Timestamp> timestampList;
  public List<ZonedDateTime> zonedDateTimeList;
  public List<Duration> durationList;
  public List<java.time.Duration> javaDurationList;
  public List<ByteString> bytesList;
  public List<Float> floatList;
  public List<Double> doubleList;

  public Map<String, InnerType> stringInnerMap;
  public List<InnerType> innerTypes;

  public AnEnum anEnum;
  public List<AnEnum> anEnumList;
  public Map<AnEnum, String> anEnumStringMap;
  public Map<String, AnEnum> stringAnEnumMap;

  public static final List<String> ALL_PROPERTIES =
      asList(
          "stringBooleanMap",
          "byteShortMap",
          "intLongMap",
          "ulongTimestampMap",
          "ulongZonedDateTimeMap",
          "stringProtoDurationMap",
          "stringJavaDurationMap",
          "stringBytesMap",
          "floatDoubleMap",
          "stringList",
          "booleanList",
          "byteList",
          "shortList",
          "intList",
          "longList",
          "ulongList",
          "timestampList",
          "zonedDateTimeList",
          "durationList",
          "javaDurationList",
          "bytesList",
          "floatList",
          "doubleList",
          "stringInnerMap",
          "innerTypes",
          "anEnum",
          "anEnumList",
          "anEnumStringMap",
          "stringAnEnumMap");
}
