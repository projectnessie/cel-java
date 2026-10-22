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
import static org.projectnessie.cel.common.types.Err.rangeError;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.ListT.newDoubleArrayList;
import static org.projectnessie.cel.common.types.ListT.newGenericArrayList;
import static org.projectnessie.cel.common.types.ListT.newGenericList;
import static org.projectnessie.cel.common.types.ListT.newIntArrayList;
import static org.projectnessie.cel.common.types.ListT.newJSONList;
import static org.projectnessie.cel.common.types.ListT.newLongArrayList;
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
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.BoolT;
import org.projectnessie.cel.common.types.DoubleT;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.IntT;
import org.projectnessie.cel.common.types.ListT;
import org.projectnessie.cel.common.types.MapT;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.UintT;
import org.projectnessie.cel.common.types.UnknownT;

/**
 * Standard conversion helpers for {@link TypeAdapter} implementations.
 *
 * <p>{@link #maybeNativeToValue(TypeAdapter, Object)} recognizes the built-in Java, Protobuf, and
 * aggregate representations handled by CEL-Java. Custom adapters commonly call it first and handle
 * a {@code null} return as “not recognized.” The primitive methods and CEL-to-Java methods provide
 * the default semantics used by {@link TypeAdapter}.
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
    NativeToValueExact.put(
        com.google.protobuf.Duration.class,
        (a, value) -> durationOf((com.google.protobuf.Duration) value));
    NativeToValueExact.put(Timestamp.class, (a, value) -> timestampOf((Timestamp) value));
    NativeToValueExact.put(ZonedDateTime.class, (a, value) -> timestampOf((ZonedDateTime) value));
    NativeToValueExact.put(Instant.class, (a, value) -> timestampOf((Instant) value));
    NativeToValueExact.put(int[].class, (a, value) -> newIntArrayList(a, (int[]) value));
    NativeToValueExact.put(long[].class, (a, value) -> newLongArrayList(a, (long[]) value));
    NativeToValueExact.put(double[].class, (a, value) -> newDoubleArrayList(a, (double[]) value));
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
      return newGenericList(a, (List<?>) value);
    }
    if (value instanceof Collection) {
      return newGenericArrayList(a, ((Collection<?>) value).toArray());
    }
    if (value instanceof Optional<?> optional) {
      return optional.map(a::nativeToValue).orElse(NullT.NullValue);
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

  /**
   * Returns the standard CEL boolean value.
   *
   * @param value primitive value
   * @return CEL boolean
   */
  public static Val nativeToValue(boolean value) {
    return boolOf(value);
  }

  /**
   * Returns a CEL int value.
   *
   * @param value primitive value
   * @return CEL int
   */
  public static Val nativeToValue(byte value) {
    return intOf(value);
  }

  /**
   * Returns a CEL int value.
   *
   * @param value primitive value
   * @return CEL int
   */
  public static Val nativeToValue(short value) {
    return intOf(value);
  }

  /**
   * Returns a CEL int value.
   *
   * @param value primitive value
   * @return CEL int
   */
  public static Val nativeToValue(int value) {
    return intOf(value);
  }

  /**
   * Returns a CEL int value.
   *
   * @param value primitive value
   * @return CEL int
   */
  public static Val nativeToValue(long value) {
    return intOf(value);
  }

  /**
   * Returns a CEL double value.
   *
   * @param value primitive value
   * @return CEL double
   */
  public static Val nativeToValue(float value) {
    return doubleOf(value);
  }

  /**
   * Returns a CEL double value.
   *
   * @param value primitive value
   * @return CEL double
   */
  public static Val nativeToValue(double value) {
    return doubleOf(value);
  }

  /**
   * Converts a CEL value to the requested Java representation using primitive specializations where
   * available.
   *
   * @param adapter adapter whose primitive conversion methods define the conversion semantics
   * @param value CEL value to convert
   * @param targetType requested Java class or primitive class
   * @return the converted Java value
   * @throws RuntimeException if conversion is unsupported or out of range
   */
  @SuppressWarnings("unchecked")
  public static <T> T valueToNative(TypeAdapter adapter, Val value, Class<T> targetType) {
    if (targetType == boolean.class) {
      return (T) Boolean.valueOf(adapter.valueToBoolean(value));
    }
    if (targetType == int.class) {
      return (T) Integer.valueOf(adapter.valueToInt(value));
    }
    if (targetType == long.class) {
      return (T) Long.valueOf(adapter.valueToLong(value));
    }
    if (targetType == double.class) {
      return (T) Double.valueOf(adapter.valueToDouble(value));
    }
    if (value instanceof ListT list) {
      return ListT.toNative(adapter, list, targetType);
    }
    if (value instanceof MapT map) {
      return map.toNative(adapter, targetType);
    }
    return legacyValueToNative(value, targetType);
  }

  /**
   * Converts a CEL boolean to a Java primitive.
   *
   * @param value CEL value to convert
   * @return primitive boolean
   * @throws RuntimeException if {@code value} is not convertible to boolean
   */
  public static boolean valueToBoolean(Val value) {
    if (value instanceof BoolT boolValue) {
      return boolValue.booleanValue();
    }
    return legacyValueToNative(value, boolean.class);
  }

  /**
   * Converts a CEL int or uint to a range-checked Java {@code int}.
   *
   * @param value CEL value to convert
   * @return primitive value
   * @throws RuntimeException if {@code value} is not an integer or is outside the Java {@code int}
   *     range
   */
  public static int valueToInt(Val value) {
    if (value instanceof IntT || value instanceof UintT) {
      long longValue = value.intValue();
      if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
        Err.throwErrorAsIllegalStateException(rangeError(longValue, "Java int"));
      }
      return (int) longValue;
    }
    return legacyValueToNative(value, int.class);
  }

  /**
   * Converts a CEL int or uint to Java {@code long} bits.
   *
   * <p>For CEL uint, the returned primitive preserves the unsigned value's raw bits.
   *
   * @param value CEL value to convert
   * @return primitive signed value or raw unsigned bits
   * @throws RuntimeException if {@code value} is not convertible to {@code long}
   */
  public static long valueToLong(Val value) {
    if (value instanceof IntT || value instanceof UintT || value instanceof UnknownT) {
      return value.intValue();
    }
    return legacyValueToNative(value, long.class);
  }

  /**
   * Converts a CEL double to a Java primitive.
   *
   * @param value CEL value to convert
   * @return primitive double
   * @throws RuntimeException if {@code value} is not convertible to {@code double}
   */
  public static double valueToDouble(Val value) {
    if (value instanceof DoubleT doubleValue) {
      return doubleValue.doubleValue();
    }
    return legacyValueToNative(value, double.class);
  }

  @SuppressWarnings("removal")
  private static <T> T legacyValueToNative(Val value, Class<T> targetType) {
    return value.convertToNative(targetType);
  }
}
