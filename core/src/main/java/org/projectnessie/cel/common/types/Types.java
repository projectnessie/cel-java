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

import static org.projectnessie.cel.common.types.BytesT.BytesType;
import static org.projectnessie.cel.common.types.DoubleT.DoubleType;
import static org.projectnessie.cel.common.types.DurationT.DurationType;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.ListT.ListType;
import static org.projectnessie.cel.common.types.MapT.MapType;
import static org.projectnessie.cel.common.types.NullT.NullType;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.TimestampT.TimestampType;
import static org.projectnessie.cel.common.types.TypeT.TypeType;
import static org.projectnessie.cel.common.types.UintT.UintType;

import java.util.HashMap;
import java.util.Map;
import org.projectnessie.cel.common.types.ref.Type;

public final class Types {

  private Types() {}

  private static final Map<String, Type> typeNameToTypeValue = new HashMap<>();

  static {
    typeNameToTypeValue.put(BoolT.BoolType.typeName(), BoolT.BoolType);
    typeNameToTypeValue.put(BytesType.typeName(), BytesType);
    typeNameToTypeValue.put(DoubleType.typeName(), DoubleType);
    typeNameToTypeValue.put(DurationType.typeName(), DurationType);
    typeNameToTypeValue.put(NullType.typeName(), NullType);
    typeNameToTypeValue.put(IntType.typeName(), IntType);
    typeNameToTypeValue.put(ListType.typeName(), ListType);
    typeNameToTypeValue.put(MapType.typeName(), MapType);
    typeNameToTypeValue.put(StringType.typeName(), StringType);
    typeNameToTypeValue.put(TimestampType.typeName(), TimestampType);
    typeNameToTypeValue.put(TypeType.typeName(), TypeType);
    typeNameToTypeValue.put(UintType.typeName(), UintType);
  }

  public static Type getTypeByName(String typeName) {
    return typeNameToTypeValue.get(typeName);
  }

  public static BoolT boolOf(boolean b) {
    return b ? BoolT.True : BoolT.False;
  }

  static int numericHashCode(long value) {
    return numericHashCode((double) value);
  }

  static int unsignedNumericHashCode(long value) {
    double doubleValue = value >= 0 ? (double) value : (double) (value & Long.MAX_VALUE) + 0x1.0p63;
    return numericHashCode(doubleValue);
  }

  static int numericHashCode(double value) {
    // CEL numeric equality compares signed and unsigned integers after conversion to double when
    // either operand is a double. Use the same canonical representation for Java hash-based maps.
    // Canonicalize signed zero because CEL considers -0.0 and 0.0 equal.
    return Double.hashCode(value == 0.0d ? 0.0d : value);
  }
}
