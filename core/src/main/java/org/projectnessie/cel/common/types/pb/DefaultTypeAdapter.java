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

import static org.projectnessie.cel.common.types.Err.anyWithEmptyType;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.unknownType;
import static org.projectnessie.cel.common.types.pb.PbTypeDescription.typeNameFromMessage;
import static org.projectnessie.cel.common.types.ref.TypeAdapterSupport.maybeNativeToValue;

import com.google.api.expr.v1alpha1.Value;
import com.google.protobuf.Message;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.Types;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

/** defaultTypeAdapter converts go native types to CEL values. */
public final class DefaultTypeAdapter implements TypeAdapter {
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
      PbTypeDescription type = db.describeType(typeName);
      if (type == null) {
        return unknownType(typeName);
      }
      value = type.maybeUnwrap(db, msg);
      if (value instanceof Message) {
        value = type.maybeUnwrap(db, value);
      }
      return a.nativeToValue(value);
    }
    return newErr("unsupported conversion from '%s' to value", value.getClass());
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
          return Types.getTypeByName(v.getTypeValue());
        case UINT64_VALUE:
          return ULong.valueOf(v.getUint64Value());
        case OBJECT_VALUE:
          return v.getObjectValue();
      }
    }

    return value;
  }
}
