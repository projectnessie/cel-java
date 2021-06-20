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
package org.projectnessie.cel.common.types.pb;

import com.google.api.expr.v1alpha1.Type;
import com.google.api.expr.v1alpha1.Type.ListType;
import com.google.api.expr.v1alpha1.Type.MapType;
import com.google.api.expr.v1alpha1.Type.PrimitiveType;
import com.google.api.expr.v1alpha1.Type.WellKnownType;
import com.google.protobuf.Empty;
import com.google.protobuf.Field;
import com.google.protobuf.Field.Kind;
import com.google.protobuf.NullValue;
import java.util.HashMap;
import java.util.Map;

public final class Checked {

  // common types
  public static final Type checkedDyn =
      Type.newBuilder().setDyn(Empty.getDefaultInstance()).build();
  // Wrapper and primitive types.
  public static final Type checkedBool = checkedPrimitive(PrimitiveType.BOOL);
  public static final Type checkedBytes = checkedPrimitive(PrimitiveType.BYTES);
  public static final Type checkedDouble = checkedPrimitive(PrimitiveType.DOUBLE);
  public static final Type checkedInt = checkedPrimitive(PrimitiveType.INT64);
  public static final Type checkedString = checkedPrimitive(PrimitiveType.STRING);
  public static final Type checkedUint = checkedPrimitive(PrimitiveType.UINT64);
  // Well-known type equivalents.
  public static final Type checkedAny = checkedWellKnown(WellKnownType.ANY);
  public static final Type checkedDuration = checkedWellKnown(WellKnownType.DURATION);
  public static final Type checkedTimestamp = checkedWellKnown(WellKnownType.TIMESTAMP);
  // Json-based type equivalents.
  public static final Type checkedNull = Type.newBuilder().setNull(NullValue.NULL_VALUE).build();
  public static final Type checkedListDyn =
      Type.newBuilder().setListType(ListType.newBuilder().setElemType(checkedDyn)).build();
  public static final Type checkedMapStringDyn =
      Type.newBuilder()
          .setMapType(MapType.newBuilder().setKeyType(checkedString).setValueType(checkedDyn))
          .build();

  /** CheckedPrimitives map from proto field descriptor type to expr.Type. */
  public static final Map<Field.Kind, Type> CheckedPrimitives = new HashMap<>();

  static {
    CheckedPrimitives.put(Kind.TYPE_BOOL, checkedBool);
    CheckedPrimitives.put(Kind.TYPE_BYTES, checkedBytes);
    CheckedPrimitives.put(Kind.TYPE_DOUBLE, checkedDouble);
    CheckedPrimitives.put(Kind.TYPE_FLOAT, checkedDouble);
    CheckedPrimitives.put(Kind.TYPE_INT32, checkedInt);
    CheckedPrimitives.put(Kind.TYPE_INT64, checkedInt);
    CheckedPrimitives.put(Kind.TYPE_SINT32, checkedInt);
    CheckedPrimitives.put(Kind.TYPE_SINT64, checkedInt);
    CheckedPrimitives.put(Kind.TYPE_UINT32, checkedUint);
    CheckedPrimitives.put(Kind.TYPE_UINT64, checkedUint);
    CheckedPrimitives.put(Kind.TYPE_FIXED32, checkedUint);
    CheckedPrimitives.put(Kind.TYPE_FIXED64, checkedUint);
    CheckedPrimitives.put(Kind.TYPE_SFIXED32, checkedInt);
    CheckedPrimitives.put(Kind.TYPE_SFIXED64, checkedInt);
    CheckedPrimitives.put(Kind.TYPE_STRING, checkedString);
  }

  /**
   * CheckedWellKnowns map from qualified proto type name to expr.Type for well-known proto types.
   */
  public static final Map<String, Type> CheckedWellKnowns = new HashMap<>();

  static {
    // Wrapper types.
    CheckedWellKnowns.put("google.protobuf.BoolValue", checkedWrap(checkedBool));
    CheckedWellKnowns.put("google.protobuf.BytesValue", checkedWrap(checkedBytes));
    CheckedWellKnowns.put("google.protobuf.DoubleValue", checkedWrap(checkedDouble));
    CheckedWellKnowns.put("google.protobuf.FloatValue", checkedWrap(checkedDouble));
    CheckedWellKnowns.put("google.protobuf.Int64Value", checkedWrap(checkedInt));
    CheckedWellKnowns.put("google.protobuf.Int32Value", checkedWrap(checkedInt));
    CheckedWellKnowns.put("google.protobuf.UInt64Value", checkedWrap(checkedUint));
    CheckedWellKnowns.put("google.protobuf.UInt32Value", checkedWrap(checkedUint));
    CheckedWellKnowns.put("google.protobuf.StringValue", checkedWrap(checkedString));
    // Well-known types.
    CheckedWellKnowns.put("google.protobuf.Any", checkedAny);
    CheckedWellKnowns.put("google.protobuf.Duration", checkedDuration);
    CheckedWellKnowns.put("google.protobuf.Timestamp", checkedTimestamp);
    // Json types.
    CheckedWellKnowns.put("google.protobuf.ListValue", checkedListDyn);
    CheckedWellKnowns.put("google.protobuf.NullValue", checkedNull);
    CheckedWellKnowns.put("google.protobuf.Struct", checkedMapStringDyn);
    CheckedWellKnowns.put("google.protobuf.Value", checkedDyn);
  }

  public static Type checkedMessageType(String name) {
    return Type.newBuilder().setMessageType(name).build();
  }

  static Type checkedPrimitive(PrimitiveType primitive) {
    return Type.newBuilder().setPrimitive(primitive).build();
  }

  static Type checkedWellKnown(WellKnownType wellKnown) {
    return Type.newBuilder().setWellKnown(wellKnown).build();
  }

  static Type checkedWrap(Type t) {
    return Type.newBuilder().setWrapper(t.getPrimitive()).build();
  }
}
