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

/** Converts native Java values of varying type and complexity to and from equivalent CEL values. */
@FunctionalInterface
public interface TypeAdapter {
  /** Converts the input {@code value} to a CEL {@link Val}. */
  Val nativeToValue(Object value);

  /** Converts a primitive boolean to a CEL value. */
  default Val nativeToValue(boolean value) {
    return nativeToValue(Boolean.valueOf(value));
  }

  /** Converts a primitive byte to a CEL value. */
  default Val nativeToValue(byte value) {
    return nativeToValue(Byte.valueOf(value));
  }

  /** Converts a primitive short to a CEL value. */
  default Val nativeToValue(short value) {
    return nativeToValue(Short.valueOf(value));
  }

  /** Converts a primitive int to a CEL value. */
  default Val nativeToValue(int value) {
    return nativeToValue(Integer.valueOf(value));
  }

  /** Converts a primitive char to a CEL value. */
  default Val nativeToValue(char value) {
    return nativeToValue(Character.valueOf(value));
  }

  /** Converts a primitive long to a CEL value. */
  default Val nativeToValue(long value) {
    return nativeToValue(Long.valueOf(value));
  }

  /** Converts a primitive float to a CEL value. */
  default Val nativeToValue(float value) {
    return nativeToValue(Float.valueOf(value));
  }

  /** Converts a primitive double to a CEL value. */
  default Val nativeToValue(double value) {
    return nativeToValue(Double.valueOf(value));
  }

  /** Converts a CEL value to the requested native Java representation. */
  default <T> T valueToNative(Val value, Class<T> targetType) {
    return TypeAdapterSupport.valueToNative(this, value, targetType);
  }

  /** Converts a CEL boolean value to a primitive boolean. */
  default boolean valueToBoolean(Val value) {
    return TypeAdapterSupport.valueToBoolean(value);
  }

  /** Converts a CEL integer value to a range-checked primitive int. */
  default int valueToInt(Val value) {
    return TypeAdapterSupport.valueToInt(value);
  }

  /**
   * Converts a CEL integer value to its primitive long representation.
   *
   * <p>CEL signed integers use a Java {@code long}. Unsigned values preserve their raw unsigned
   * bits in the returned {@code long}.
   */
  default long valueToLong(Val value) {
    return TypeAdapterSupport.valueToLong(value);
  }

  /** Converts a CEL double value to a primitive double. */
  default double valueToDouble(Val value) {
    return TypeAdapterSupport.valueToDouble(value);
  }
}
