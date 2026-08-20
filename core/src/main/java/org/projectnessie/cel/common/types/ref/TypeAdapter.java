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

/**
 * Converts between host-language Java values and runtime CEL values.
 *
 * <p>An adapter is part of an environment's type integration and may be reused by every program
 * created from that environment. Configure any mutable adapter state before sharing it. {@link
 * #nativeToValue(Object)} should return a non-null CEL value, including a CEL error value for an
 * unsupported runtime input when appropriate; Java {@code null} as input normally represents CEL
 * null, subject to the concrete adapter's contract.
 *
 * <p>The primitive overloads delegate to the object conversion by default. Implementations may
 * override them, but each override must have the same CEL semantics as the boxed representation.
 * CEL-to-Java conversion methods throw when the requested target representation is incompatible.
 */
@FunctionalInterface
public interface TypeAdapter {
  /**
   * Converts {@code value} to a CEL value.
   *
   * @param value host value, possibly {@code null} if supported by the adapter
   * @return the corresponding non-null CEL value
   */
  Val nativeToValue(Object value);

  /**
   * Converts a primitive boolean to a CEL value.
   *
   * @param value primitive value
   * @return the corresponding CEL value
   */
  default Val nativeToValue(boolean value) {
    return nativeToValue(Boolean.valueOf(value));
  }

  /**
   * Converts a primitive byte to a CEL value.
   *
   * @param value primitive value
   * @return the corresponding CEL value
   */
  default Val nativeToValue(byte value) {
    return nativeToValue(Byte.valueOf(value));
  }

  /**
   * Converts a primitive short to a CEL value.
   *
   * @param value primitive value
   * @return the corresponding CEL value
   */
  default Val nativeToValue(short value) {
    return nativeToValue(Short.valueOf(value));
  }

  /**
   * Converts a primitive int to a CEL value.
   *
   * @param value primitive value
   * @return the corresponding CEL value
   */
  default Val nativeToValue(int value) {
    return nativeToValue(Integer.valueOf(value));
  }

  /**
   * Converts a primitive character according to this adapter's boxed {@link Character} contract.
   *
   * @param value primitive value
   * @return the corresponding CEL value
   */
  default Val nativeToValue(char value) {
    return nativeToValue(Character.valueOf(value));
  }

  /**
   * Converts a primitive long to a CEL value.
   *
   * @param value primitive value
   * @return the corresponding CEL value
   */
  default Val nativeToValue(long value) {
    return nativeToValue(Long.valueOf(value));
  }

  /**
   * Converts a primitive float to a CEL value.
   *
   * @param value primitive value
   * @return the corresponding CEL value
   */
  default Val nativeToValue(float value) {
    return nativeToValue(Float.valueOf(value));
  }

  /**
   * Converts a primitive double to a CEL value.
   *
   * @param value primitive value
   * @return the corresponding CEL value
   */
  default Val nativeToValue(double value) {
    return nativeToValue(Double.valueOf(value));
  }

  /**
   * Converts a CEL value to the requested Java representation.
   *
   * @param value CEL value to convert
   * @param targetType requested Java class or primitive class
   * @return the converted Java value
   * @throws RuntimeException if the conversion is unsupported or out of range
   */
  default <T> T valueToNative(Val value, Class<T> targetType) {
    return TypeAdapterSupport.valueToNative(this, value, targetType);
  }

  /**
   * Converts a CEL boolean value to a primitive boolean.
   *
   * @param value CEL value to convert
   * @return the primitive value
   * @throws RuntimeException if {@code value} is not convertible to a Java boolean
   */
  default boolean valueToBoolean(Val value) {
    return TypeAdapterSupport.valueToBoolean(value);
  }

  /**
   * Converts a CEL int or uint value to a range-checked primitive {@code int}.
   *
   * @param value CEL value to convert
   * @return the primitive value
   * @throws RuntimeException if {@code value} is not an integer or is outside the Java {@code int}
   *     range
   */
  default int valueToInt(Val value) {
    return TypeAdapterSupport.valueToInt(value);
  }

  /**
   * Converts a CEL integer value to its primitive long representation.
   *
   * <p>CEL signed integers use a Java {@code long}. Unsigned values preserve their raw unsigned
   * bits in the returned {@code long}.
   *
   * @param value CEL value to convert
   * @return the primitive value or raw unsigned bits
   * @throws RuntimeException if {@code value} is not convertible to a Java {@code long}
   */
  default long valueToLong(Val value) {
    return TypeAdapterSupport.valueToLong(value);
  }

  /**
   * Converts a CEL double value to a primitive double.
   *
   * @param value CEL value to convert
   * @return the primitive value
   * @throws RuntimeException if {@code value} is not convertible to a Java {@code double}
   */
  default double valueToDouble(Val value) {
    return TypeAdapterSupport.valueToDouble(value);
  }
}
