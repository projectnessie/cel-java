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

import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import com.google.api.expr.v1alpha1.Type;
import com.google.protobuf.ByteString;
import java.util.Objects;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;

/** Evaluation-independent checked aggregate materialization selected by the planner. */
final class CheckedAggregateMaterializer {
  private final ExactAggregateTypeAdapter adapter;
  private final Type checkedType;

  CheckedAggregateMaterializer(ExactAggregateTypeAdapter adapter, Type checkedType) {
    this.adapter = Objects.requireNonNull(adapter, "adapter");
    this.checkedType = Objects.requireNonNull(checkedType, "checkedType");
  }

  Val materialize(Object value) {
    try {
      return adapter.nativeAggregateToValue(value, checkedType);
    } catch (Exception failure) {
      if (failure instanceof org.projectnessie.cel.OperationAbortedException aborted) {
        throw aborted;
      }
      return newErr(failure, failure.toString());
    }
  }

  Val materializeListElement(Object value) {
    Val list = materialize(new Object[] {value});
    return list instanceof Lister lister ? lister.nativeGetAt(0) : list;
  }
}

/**
 * Evaluation-independent checked materialization of one selected exact aggregate child.
 *
 * <p>This mirrors the checked child conversion used while recursively materializing an exact
 * aggregate, but avoids constructing a temporary aggregate around a selected map entry.
 */
final class CheckedValueMaterializer {
  private final ExactAggregateTypeAdapter adapter;
  private final Type checkedType;

  CheckedValueMaterializer(ExactAggregateTypeAdapter adapter, Type checkedType) {
    this.adapter = Objects.requireNonNull(adapter, "adapter");
    this.checkedType = Objects.requireNonNull(checkedType, "checkedType");
  }

  Val materialize(Object value) {
    try {
      if (value instanceof Val) {
        return incompatible(value);
      }
      Val result =
          switch (checkedType.getTypeKindCase()) {
            case LIST_TYPE, MAP_TYPE -> adapter.nativeAggregateToValue(value, checkedType);
            case NULL -> value == null ? NullT.NullValue : incompatible(value);
            case DYN -> adapter.nativeToValue(value);
            case WRAPPER ->
                value == null
                    ? NullT.NullValue
                    : materializePrimitive(value, checkedType.getWrapper());
            case PRIMITIVE -> materializePrimitive(value, checkedType.getPrimitive());
            case WELL_KNOWN, MESSAGE_TYPE, TYPE, ABSTRACT_TYPE ->
                value != null ? adapter.nativeToValue(value) : incompatible(null);
            case FUNCTION, TYPE_PARAM, ERROR, TYPEKIND_NOT_SET -> incompatible(value);
          };
      return result != null ? result : incompatible(value);
    } catch (Exception failure) {
      if (failure instanceof org.projectnessie.cel.OperationAbortedException aborted) {
        throw aborted;
      }
      return newErr(failure, failure.toString());
    }
  }

  boolean booleanValue(Object value) {
    return value instanceof Boolean bool
        ? bool
        : NativeSupport.booleanValue(adapter, materialize(value));
  }

  long intValue(Object value) {
    return value instanceof Byte
            || value instanceof Short
            || value instanceof Integer
            || value instanceof Long
        ? ((Number) value).longValue()
        : NativeSupport.intValue(adapter, materialize(value));
  }

  long uintValue(Object value) {
    if (value instanceof Long bits) {
      return bits;
    }
    return value instanceof ULong unsigned
        ? unsigned.longValue()
        : NativeSupport.uintValue(adapter, materialize(value));
  }

  double doubleValue(Object value) {
    return value instanceof Float || value instanceof Double
        ? ((Number) value).doubleValue()
        : NativeSupport.doubleValue(adapter, materialize(value));
  }

  String stringValue(Object value) {
    return value instanceof String string
        ? string
        : NativeSupport.stringValue(adapter, materialize(value));
  }

  void nullValue(Object value) {
    if (value != null) {
      NativeSupport.nullValue(adapter, materialize(value));
    }
  }

  private Val materializePrimitive(Object value, Type.PrimitiveType primitive) {
    return switch (primitive) {
      case BOOL -> value instanceof Boolean bool ? boolOf(bool) : incompatible(value);
      case INT64 ->
          value instanceof Byte
                  || value instanceof Short
                  || value instanceof Integer
                  || value instanceof Long
              ? intOf(((Number) value).longValue())
              : incompatible(value);
      case UINT64 ->
          value instanceof ULong unsigned
              ? uintOf(unsigned.longValue())
              : value instanceof Long bits ? uintOf(bits) : incompatible(value);
      case DOUBLE ->
          value instanceof Float || value instanceof Double
              ? doubleOf(((Number) value).doubleValue())
              : incompatible(value);
      case STRING -> value instanceof String string ? stringOf(string) : incompatible(value);
      case BYTES ->
          value instanceof byte[] bytes
              ? bytesOf(bytes)
              : value instanceof ByteString bytes ? bytesOf(bytes) : incompatible(value);
      default -> incompatible(value);
    };
  }

  private Val incompatible(Object value) {
    return newErr(
        "exact aggregate value of Java type '%s' is incompatible with checked CEL type '%s'",
        value == null ? "null" : value.getClass().getName(), checkedType);
  }
}
