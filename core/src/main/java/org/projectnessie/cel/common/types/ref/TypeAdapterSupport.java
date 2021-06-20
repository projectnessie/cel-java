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
package org.projectnessie.cel.common.types.ref;

import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.DurationT.durationOf;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.ListT.newGenericArrayList;
import static org.projectnessie.cel.common.types.ListT.newJSONList;
import static org.projectnessie.cel.common.types.ListT.newStringArrayList;
import static org.projectnessie.cel.common.types.ListT.newValArrayList;
import static org.projectnessie.cel.common.types.MapT.newJSONStruct;
import static org.projectnessie.cel.common.types.MapT.newMaybeWrappedMap;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.ZoneIdZ;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.EnumValue;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.DoubleT;
import org.projectnessie.cel.common.types.IntT;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;

/**
 * Helper class for {@link TypeAdapter} implementations to convert from a Java type to a CEL type.
 */
public final class TypeAdapterSupport {
  private TypeAdapterSupport() {}

  private static final Map<Class<?>, BiFunction<TypeAdapter, Object, Val>> NativeToValueExact =
      new IdentityHashMap<>();

  static {
    NativeToValueExact.put(Boolean.class, (a, value) -> boolOf((Boolean) value));
    NativeToValueExact.put(byte[].class, (a, value) -> bytesOf(((byte[]) value)));
    NativeToValueExact.put(Float.class, (a, value) -> doubleOf(((Float) value).doubleValue()));
    NativeToValueExact.put(Double.class, (a, value) -> doubleOf((Double) value));
    NativeToValueExact.put(Byte.class, (a, value) -> intOf((Byte) value));
    NativeToValueExact.put(Short.class, (a, value) -> intOf((Short) value));
    NativeToValueExact.put(Integer.class, (a, value) -> intOf((Integer) value));
    NativeToValueExact.put(ULong.class, (a, value) -> uintOf(((ULong) value).longValue()));
    NativeToValueExact.put(Long.class, (a, value) -> intOf((Long) value));
    NativeToValueExact.put(String.class, (a, value) -> stringOf((String) value));
    NativeToValueExact.put(Duration.class, (a, value) -> durationOf((Duration) value));
    NativeToValueExact.put(ZonedDateTime.class, (a, value) -> timestampOf((ZonedDateTime) value));
    NativeToValueExact.put(Instant.class, (a, value) -> timestampOf((Instant) value));
    NativeToValueExact.put(
        // TODO maybe add specialized ListT for int[]
        int[].class,
        (a, value) ->
            newValArrayList(
                DefaultTypeAdapter.Instance,
                Arrays.stream((int[]) value).mapToObj(IntT::intOf).toArray(Val[]::new)));
    NativeToValueExact.put(
        // TODO maybe add specialized ListT for long[]
        long[].class,
        (a, value) ->
            newValArrayList(
                DefaultTypeAdapter.Instance,
                Arrays.stream((long[]) value).mapToObj(IntT::intOf).toArray(Val[]::new)));
    NativeToValueExact.put(
        // TODO maybe add specialized ListT for double[]
        double[].class,
        (a, value) ->
            newValArrayList(
                DefaultTypeAdapter.Instance,
                Arrays.stream((double[]) value).mapToObj(DoubleT::doubleOf).toArray(Val[]::new)));
    NativeToValueExact.put(String[].class, (a, value) -> newStringArrayList((String[]) value));
    NativeToValueExact.put(Val[].class, (a, value) -> newValArrayList(a, (Val[]) value));
    NativeToValueExact.put(NullValue.class, (a, value) -> NullT.NullValue);
    NativeToValueExact.put(ListValue.class, (a, value) -> newJSONList(a, (ListValue) value));
    NativeToValueExact.put(
        UInt32Value.class, (a, value) -> uintOf(((UInt32Value) value).getValue()));
    NativeToValueExact.put(
        UInt64Value.class, (a, value) -> uintOf(((UInt64Value) value).getValue()));
    NativeToValueExact.put(Struct.class, (a, value) -> newJSONStruct(a, (Struct) value));
    NativeToValueExact.put(EnumValue.class, (a, value) -> intOf(((EnumValue) value).getNumber()));
    NativeToValueExact.put(
        EnumValueDescriptor.class,
        (a, value) -> {
          EnumValueDescriptor e = (EnumValueDescriptor) value;
          return intOf(e.getNumber());
        });
  }

  public static Val maybeNativeToValue(TypeAdapter a, Object value) {
    if (value == null) {
      return NullT.NullValue;
    }

    BiFunction<TypeAdapter, Object, Val> conv = NativeToValueExact.get(value.getClass());
    if (conv != null) {
      return conv.apply(a, value);
    }

    if (value instanceof Object[]) {
      return newGenericArrayList(a, (Object[]) value);
    }
    if (value instanceof List) {
      return newGenericArrayList(a, ((List<?>) value).toArray());
    }
    if (value instanceof Map) {
      return newMaybeWrappedMap(a, (Map<?, ?>) value);
    }

    if (value instanceof ByteString) {
      return bytesOf((ByteString) value);
    }

    if (value instanceof Instant) {
      return timestampOf(((Instant) value).atZone(ZoneIdZ));
    }
    if (value instanceof ZonedDateTime) {
      return timestampOf((ZonedDateTime) value);
    }
    if (value instanceof Date) {
      return timestampOf(((Date) value).toInstant().atZone(ZoneIdZ));
    }
    if (value instanceof Calendar) {
      return timestampOf(((Calendar) value).toInstant().atZone(ZoneIdZ));
    }

    return null;
  }
}
