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
package org.projectnessie.cel.common.types;

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.TypeT.TypeType;
import static org.projectnessie.cel.common.types.TypeT.newObjectTypeValue;

import com.google.protobuf.Message;
import java.util.Objects;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Container;
import org.projectnessie.cel.common.types.traits.FieldTester;
import org.projectnessie.cel.common.types.traits.Indexer;
import org.projectnessie.cel.common.types.traits.Mapper;
import org.projectnessie.cel.common.types.traits.Receiver;
import org.projectnessie.cel.common.types.traits.Sizer;
import org.projectnessie.cel.common.types.traits.Trait;

/** Runtime value for CEL optional_type values. */
public final class OptionalT extends BaseVal implements FieldTester, Indexer, Receiver {
  public static final String OptionalTypeName = "optional_type";
  public static final Type OptionalType =
      newObjectTypeValue(
          OptionalTypeName, Trait.FieldTesterType, Trait.IndexerType, Trait.ReceiverType);

  private static final OptionalT None = new OptionalT(null, false);

  private final Val value;
  private final boolean present;

  private OptionalT(Val value, boolean present) {
    this.value = value;
    this.present = present;
  }

  public static OptionalT none() {
    return None;
  }

  public static OptionalT of(Val value) {
    return new OptionalT(Objects.requireNonNull(value, "value"), true);
  }

  public static OptionalT ofNonZeroValue(Val value) {
    return isZeroValue(value) ? none() : of(value);
  }

  public static Val optionalSelect(Val operand, Val field) {
    return optionalAccess(operand, field);
  }

  public static Val optionalIndex(Val operand, Val index) {
    return optionalAccess(operand, index);
  }

  public boolean hasValue() {
    return present;
  }

  public Val getValue() {
    return value;
  }

  @Override
  @SuppressWarnings("removal")
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == Val.class || typeDesc == OptionalT.class) {
      return typeDesc.cast(this);
    }
    if (typeDesc == Object.class) {
      return typeDesc.cast(value());
    }
    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", OptionalType, typeDesc.getName()));
  }

  @Override
  public Val convertToType(Type typeValue) {
    if (typeValue.equals(OptionalType)) {
      return this;
    }
    if (typeValue == TypeType) {
      return OptionalType;
    }
    return newTypeConversionError(OptionalType, typeValue);
  }

  @Override
  public Val equal(Val other) {
    if (!(other instanceof OptionalT optional)) {
      return False;
    }
    if (!present || !optional.present) {
      return present == optional.present ? True : False;
    }
    return value.equal(optional.value);
  }

  @Override
  public Type type() {
    return OptionalType;
  }

  @Override
  public Object value() {
    return present ? value.value() : null;
  }

  @Override
  public Val isSet(Val field) {
    if (!present) {
      return False;
    }
    if (value instanceof OptionalT) {
      return ((OptionalT) value).isSet(field);
    }
    if (value instanceof FieldTester) {
      Val present = ((FieldTester) value).isSet(field);
      return isMissingAccess(present) ? False : present;
    }
    if (value instanceof Container) {
      return ((Container) value).contains(field);
    }
    return noSuchOverload(value, "has", field);
  }

  @Override
  public Val get(Val index) {
    return present ? optionalAccess(value, index) : none();
  }

  @Override
  public Val receive(String function, String overload, Val... args) {
    return switch (function) {
      case "hasValue" ->
          args.length == 0
              ? (present ? True : False)
              : noSuchOverload(this, function, overload, args);
      case "value" -> value(args, function, overload);
      case "or" -> or(args, function, overload);
      case "orValue" -> orValue(args, function, overload);
      default -> noSuchOverload(this, function, overload, args);
    };
  }

  private Val value(Val[] args, String function, String overload) {
    if (args.length != 0) {
      return noSuchOverload(this, function, overload, args);
    }
    return present ? value : newErr("optional.none() has no value");
  }

  private Val or(Val[] args, String function, String overload) {
    if (args.length != 1 || !(args[0] instanceof OptionalT)) {
      return noSuchOverload(this, function, overload, args);
    }
    return present ? this : args[0];
  }

  private Val orValue(Val[] args, String function, String overload) {
    if (args.length != 1) {
      return noSuchOverload(this, function, overload, args);
    }
    return present ? value : args[0];
  }

  private static boolean isZeroValue(Val value) {
    return switch (value.type().typeEnum()) {
      case Null -> true;
      case Bool -> value == False || !value.booleanValue();
      case Int, Uint -> value.intValue() == 0L;
      case Double -> value.doubleValue() == 0.0d;
      case String, Bytes, List, Map ->
          value.type().hasTrait(Trait.SizerType) && ((Sizer) value).size().equal(IntZero) == True;
      case Object ->
          value.value() instanceof Message && ((Message) value.value()).getAllFields().isEmpty();
      default -> false;
    };
  }

  private static Val optionalAccess(Val operand, Val index) {
    if (operand instanceof OptionalT) {
      return ((OptionalT) operand).get(index);
    }
    if (operand instanceof FieldTester && index.type().typeEnum() == TypeEnum.String) {
      Val present = ((FieldTester) operand).isSet(index);
      if (present == False) {
        return none();
      }
      if (present != True) {
        return isMissingAccess(present) ? none() : present;
      }
    }
    if (operand instanceof Mapper) {
      Val value = ((Mapper) operand).find(index);
      return value == null ? none() : of(value);
    }
    if (operand instanceof Indexer) {
      Val value = ((Indexer) operand).get(index);
      return isMissingAccess(value) ? none() : of(value);
    }
    return noSuchOverload(operand, "optional access", index);
  }

  private static boolean isMissingAccess(Val value) {
    if (!(value instanceof Err)) {
      return false;
    }
    String error = value.toString();
    return error.startsWith("no such key")
        || error.startsWith("no such field")
        || error.startsWith("invalid_argument")
        || error.startsWith("index out of bounds");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Val)) {
      return false;
    }
    return equal((Val) o) == True;
  }

  @Override
  public int hashCode() {
    return present ? Objects.hash(OptionalType, value) : Objects.hash(OptionalType);
  }

  @Override
  public String toString() {
    return present ? String.format("optional.of(%s)", value) : "optional.none()";
  }
}
