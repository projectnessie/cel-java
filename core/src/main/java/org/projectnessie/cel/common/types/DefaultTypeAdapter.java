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
package org.projectnessie.cel.common.types;

import static org.projectnessie.cel.common.types.BoolT.BoolType;
import static org.projectnessie.cel.common.types.BoolT.boolOf;
import static org.projectnessie.cel.common.types.BytesT.BytesType;
import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.DoubleT.DoubleType;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.DurationT.durationOf;
import static org.projectnessie.cel.common.types.Err.anyWithEmptyType;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.unknownType;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.ListT.ListType;
import static org.projectnessie.cel.common.types.ListT.newGenericArrayList;
import static org.projectnessie.cel.common.types.ListT.newJSONList;
import static org.projectnessie.cel.common.types.ListT.newStringArrayList;
import static org.projectnessie.cel.common.types.ListT.newValArrayList;
import static org.projectnessie.cel.common.types.MapT.MapType;
import static org.projectnessie.cel.common.types.MapT.newJSONStruct;
import static org.projectnessie.cel.common.types.MapT.newMaybeWrappedMap;
import static org.projectnessie.cel.common.types.NullT.NullType;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.ZoneIdZ;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.TypeT.TypeType;
import static org.projectnessie.cel.common.types.UintT.UintType;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.pb.TypeDescription.typeNameFromMessage;

import com.google.api.expr.v1alpha1.Value;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.EnumValue;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.pb.Db;
import org.projectnessie.cel.common.types.pb.TypeDescription;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

/** defaultTypeAdapter converts go native types to CEL values. */
public class DefaultTypeAdapter implements TypeAdapter {
  /** DefaultTypeAdapter adapts canonical CEL types from their equivalent Go values. */
  public static final DefaultTypeAdapter Instance = new DefaultTypeAdapter(Db.defaultDb);

  private final Db db;

  private DefaultTypeAdapter(Db db) {
    this.db = db;
  }

  /** NativeToValue implements the ref.TypeAdapter interface. */
  @Override
  public Val nativeToValue(Object value) {
    Val val = nativeToValue(db, this, value);
    if (val != null) {
      return val;
    }
    return Err.unsupportedRefValConversionErr(value);
  }

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

  /**
   * nativeToValue returns the converted (ref.Val, true) of a conversion is found, otherwise (nil,
   * false)
   */
  public static Val nativeToValue(Db db, TypeAdapter a, Object value) {
    Val v = maybeNativeToValue(a, value);
    if (v != null) {
      return v;
    }

    // additional specializations may be added upon request / need.
    if (value instanceof Val) {
      return (Val) value;
    }
    if (value instanceof Message) {
      Message msg = (Message) value;
      String typeName = typeNameFromMessage(msg);
      if (typeName.isEmpty()) {
        return anyWithEmptyType();
      }
      TypeDescription type = db.describeType(typeName);
      if (type == null) {
        return unknownType(typeName);
      }
      value = type.maybeUnwrap(db, msg);
      if (value instanceof Message) {
        value = type.maybeUnwrap(db, (Message) value);
      }
      return a.nativeToValue(value);
    }
    return newErr("unsupported conversion from '%s' to value", value.getClass());
  }

  static Val maybeNativeToValue(TypeAdapter a, Object value) {
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

  static Object maybeUnwrapValue(Object value) {
    if (value instanceof Value) {
      Value v = (Value) value;
      switch (v.getKindCase()) {
        case BOOL_VALUE:
          return v.getBoolValue();
        case BYTES_VALUE:
          return v.getBytesValue();
        case DOUBLE_VALUE:
          return v.getDoubleValue();
        case INT64_VALUE:
          return v.getInt64Value();
        case LIST_VALUE:
          return v.getListValue();
        case NULL_VALUE:
          return v.getNullValue();
        case MAP_VALUE:
          return v.getMapValue();
        case STRING_VALUE:
          return v.getStringValue();
        case TYPE_VALUE:
          return typeNameToTypeValue.get(v.getTypeValue());
        case UINT64_VALUE:
          return ULong.valueOf(v.getUint64Value());
        case OBJECT_VALUE:
          return v.getObjectValue();
      }
    }

    return value;
  }

  private static final Map<String, TypeT> typeNameToTypeValue = new HashMap<>();

  static {
    typeNameToTypeValue.put("bool", BoolType);
    typeNameToTypeValue.put("bytes", BytesType);
    typeNameToTypeValue.put("double", DoubleType);
    typeNameToTypeValue.put("null_type", NullType);
    typeNameToTypeValue.put("int", IntType);
    typeNameToTypeValue.put("list", ListType);
    typeNameToTypeValue.put("map", MapType);
    typeNameToTypeValue.put("string", StringType);
    typeNameToTypeValue.put("type", TypeType);
    typeNameToTypeValue.put("uint", UintType);
  }
}
