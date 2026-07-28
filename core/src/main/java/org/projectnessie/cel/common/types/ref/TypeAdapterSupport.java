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
import static org.projectnessie.cel.common.types.Err.newErr;
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
import static org.projectnessie.cel.common.types.MapT.newCheckedMap;
import static org.projectnessie.cel.common.types.MapT.newJSONStruct;
import static org.projectnessie.cel.common.types.MapT.newMaybeWrappedMap;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.ZoneIdZ;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import com.google.api.expr.v1alpha1.Type;
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
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.UintT;

/** Helper class for {@link TypeAdapter} implementations to convert between Java and CEL types. */
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
    if (value.getClass().isArray()) {
      return newErr("unsupported Java array type '%s'", value.getClass().getTypeName());
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

  static Val nativeAggregateToValue(TypeAdapter adapter, Object value, Type checkedType) {
    if (checkedType == null) {
      return newErr("exact aggregate materialization requires a checked CEL type");
    }
    return switch (checkedType.getTypeKindCase()) {
      case LIST_TYPE ->
          checkedList(adapter, value, checkedType.getListType().getElemType(), checkedType);
      case MAP_TYPE ->
          checkedMap(
              adapter,
              value,
              checkedType.getMapType().getKeyType(),
              checkedType.getMapType().getValueType(),
              checkedType);
      default -> checkedScalar(adapter, value, checkedType);
    };
  }

  private static Val checkedList(
      TypeAdapter adapter, Object value, Type elementType, Type checkedType) {
    if (value == null) {
      return incompatible(null, checkedType);
    }
    if (value instanceof long[] values) {
      if (elementType.getTypeKindCase() != Type.TypeKindCase.PRIMITIVE) {
        return incompatible(value, checkedType);
      }
      return switch (elementType.getPrimitive()) {
        case INT64 -> newLongArrayList(adapter, values);
        case UINT64 -> newGenericArrayList(adapter, unsignedValues(values));
        default -> incompatible(value, checkedType);
      };
    }
    TypeAdapter elementAdapter = new CheckedTypeAdapter(adapter, elementType);
    if (value instanceof int[] values
        && elementType.getTypeKindCase() == Type.TypeKindCase.PRIMITIVE
        && elementType.getPrimitive() == Type.PrimitiveType.INT64) {
      return newIntArrayList(adapter, values);
    }
    if (value instanceof double[] values
        && elementType.getTypeKindCase() == Type.TypeKindCase.PRIMITIVE
        && elementType.getPrimitive() == Type.PrimitiveType.DOUBLE) {
      return newDoubleArrayList(adapter, values);
    }
    if (value instanceof String[] values
        && elementType.getTypeKindCase() == Type.TypeKindCase.PRIMITIVE
        && elementType.getPrimitive() == Type.PrimitiveType.STRING) {
      return newGenericArrayList(elementAdapter, values);
    }
    if (value instanceof Val[]) {
      return incompatible(value, checkedType);
    }
    if (value instanceof Object[] values) {
      return newGenericArrayList(elementAdapter, values);
    }
    if (value instanceof List<?> values) {
      return newGenericList(elementAdapter, values);
    }
    if (value instanceof Collection<?> values) {
      return newGenericArrayList(elementAdapter, values.toArray());
    }
    return incompatible(value, checkedType);
  }

  private static ULong[] unsignedValues(long[] values) {
    ULong[] unsigned = new ULong[values.length];
    for (int i = 0; i < values.length; i++) {
      unsigned[i] = ULong.valueOf(values[i]);
    }
    return unsigned;
  }

  private static Val checkedMap(
      TypeAdapter adapter, Object value, Type keyType, Type valueType, Type checkedType) {
    if (value == null) {
      return incompatible(null, checkedType);
    }
    if (!(value instanceof Map<?, ?> values)) {
      return incompatible(value, checkedType);
    }
    return newCheckedMap(
        new CheckedTypeAdapter(adapter, keyType),
        new CheckedTypeAdapter(adapter, valueType),
        values);
  }

  private static Val checkedScalar(TypeAdapter adapter, Object value, Type checkedType) {
    if (value instanceof Val) {
      return incompatible(value, checkedType);
    }
    return switch (checkedType.getTypeKindCase()) {
      case NULL -> value == null ? NullT.NullValue : incompatible(value, checkedType);
      case DYN -> adapter.nativeToValue(value);
      case WRAPPER ->
          value == null
              ? NullT.NullValue
              : checkedPrimitive(value, checkedType.getWrapper(), checkedType);
      case PRIMITIVE -> checkedPrimitive(value, checkedType.getPrimitive(), checkedType);
      case LIST_TYPE, MAP_TYPE -> throw new IllegalStateException("aggregate handled separately");
      case WELL_KNOWN, MESSAGE_TYPE, TYPE, ABSTRACT_TYPE ->
          value != null ? adapter.nativeToValue(value) : incompatible(null, checkedType);
      case FUNCTION, TYPE_PARAM, ERROR, TYPEKIND_NOT_SET -> incompatible(value, checkedType);
    };
  }

  private static Val checkedPrimitive(
      Object value, Type.PrimitiveType primitive, Type checkedType) {
    return switch (primitive) {
      case BOOL -> value instanceof Boolean bool ? boolOf(bool) : incompatible(value, checkedType);
      case INT64 ->
          value instanceof Byte
                  || value instanceof Short
                  || value instanceof Integer
                  || value instanceof Long
              ? intOf(((Number) value).longValue())
              : incompatible(value, checkedType);
      case UINT64 ->
          value instanceof ULong unsigned
              ? uintOf(unsigned.longValue())
              : value instanceof Long bits ? uintOf(bits) : incompatible(value, checkedType);
      case DOUBLE ->
          value instanceof Float || value instanceof Double
              ? doubleOf(((Number) value).doubleValue())
              : incompatible(value, checkedType);
      case STRING ->
          value instanceof String string ? stringOf(string) : incompatible(value, checkedType);
      case BYTES ->
          value instanceof byte[] bytes
              ? bytesOf(bytes)
              : value instanceof ByteString bytes
                  ? bytesOf(bytes)
                  : incompatible(value, checkedType);
      default -> incompatible(value, checkedType);
    };
  }

  private static Val incompatible(Object value, Type checkedType) {
    return newErr(
        "exact aggregate value of Java type '%s' is incompatible with checked CEL type '%s'",
        value == null ? "null" : value.getClass().getName(), checkedType);
  }

  private static final class CheckedTypeAdapter implements TypeAdapter {
    private final TypeAdapter adapter;
    private final Type checkedType;

    private CheckedTypeAdapter(TypeAdapter adapter, Type checkedType) {
      this.adapter = adapter;
      this.checkedType = checkedType;
    }

    @Override
    public Val nativeToValue(Object value) {
      return nativeAggregateToValue(adapter, value, checkedType);
    }
  }

  public static Val nativeToValue(boolean value) {
    return boolOf(value);
  }

  public static Val nativeToValue(byte value) {
    return intOf(value);
  }

  public static Val nativeToValue(short value) {
    return intOf(value);
  }

  public static Val nativeToValue(int value) {
    return intOf(value);
  }

  public static Val nativeToValue(long value) {
    return intOf(value);
  }

  public static Val nativeToValue(float value) {
    return doubleOf(value);
  }

  public static Val nativeToValue(double value) {
    return doubleOf(value);
  }

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
    return legacyValueToNative(value, targetType);
  }

  public static boolean valueToBoolean(Val value) {
    if (value instanceof BoolT boolValue) {
      return boolValue.booleanValue();
    }
    return legacyValueToNative(value, boolean.class);
  }

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

  public static long valueToLong(Val value) {
    if (value instanceof IntT || value instanceof UintT) {
      return value.intValue();
    }
    return legacyValueToNative(value, long.class);
  }

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
