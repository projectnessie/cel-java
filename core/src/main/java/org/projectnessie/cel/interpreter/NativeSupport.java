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

import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.Err.ErrException;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

final class NativeSupport {
  private NativeSupport() {}

  static Val adapt(TypeAdapter adapter, Object value) {
    return adapter.nativeToValue(value);
  }

  static long intValue(TypeAdapter adapter, Object value) {
    if (value instanceof Long longValue) {
      return longValue;
    }
    Val val = adapt(adapter, value);
    return NativeScalarContinuations.intResult(val);
  }

  static long uintValue(TypeAdapter adapter, Object value) {
    if (value instanceof ULong unsigned) {
      return unsigned.longValue();
    }
    Val val = adapt(adapter, value);
    return NativeScalarContinuations.uintResult(val);
  }

  static boolean booleanValue(TypeAdapter adapter, Object value) {
    if (value instanceof Boolean booleanValue) {
      return booleanValue;
    }
    Val val = adapt(adapter, value);
    return NativeScalarContinuations.booleanResult(val);
  }

  static double doubleValue(TypeAdapter adapter, Object value) {
    if (value instanceof Double doubleValue) {
      return doubleValue;
    }
    Val val = adapt(adapter, value);
    return NativeScalarContinuations.doubleResult(val);
  }

  static String stringValue(TypeAdapter adapter, Object value) {
    if (value instanceof String stringValue) {
      return stringValue;
    }
    Val val = adapt(adapter, value);
    if (val instanceof StringT) {
      String stringValue = (String) val.value();
      if (stringValue != null) {
        return stringValue;
      }
    }
    throw signal(val);
  }

  static void nullValue(TypeAdapter adapter, Object value) {
    if (value == null) {
      return;
    }
    Val val = adapt(adapter, value);
    if (val == NullT.NullValue) {
      return;
    }
    throw signal(val);
  }

  static ValueSignal propagatedError(Val value) {
    ErrException error = new ErrException("message: %s", value);
    return signal(newErr(error, error.toString()));
  }
}
