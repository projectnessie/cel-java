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

import static org.projectnessie.cel.common.types.UnknownT.UnknownType;

import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.Val;

/** Err type which extends the built-in go error and implements ref.Val. */
public class Err extends BaseVal {

  /** ErrType singleton. */
  public static final TypeValue ErrType = TypeValue.newTypeValue("error");

  /** errIntOverflow is an error representing integer overflow. */
  public static final Val errIntOverflow = newErr("integer overflow");
  /** errUintOverflow is an error representing unsigned integer overflow. */
  public static final Val errUintOverflow = newErr("unsigned integer overflow");
  /** errDurationOverflow is an error representing duration overflow. */
  public static final Val errDurationOverflow = newErr("duration overflow");
  /** errTimestampOverflow is an error representing timestamp overflow. */
  public static final Val errTimestampOverflow = newErr("timestamp overflow");
  /** NoSuchOverloadErr returns a new types.Err instance with a no such overload message. */
  public static final Val noSuchOverloadErr = newErr("no such overload");

  private final String error;

  private Err(String error) {
    this.error = error;
  }

  /**
   * NewErr creates a new Err described by the format string and args. TODO: Audit the use of this
   * function and standardize the error messages and codes.
   */
  public static Val newErr(String format, Object... args) {
    return new Err(String.format(format, args));
  }

  /**
   * UnsupportedRefValConversionErr returns a types.NewErr instance with a no such conversion
   * message that indicates that the native value could not be converted to a CEL ref.Val.
   */
  public static Val unsupportedRefValConversionErr(Object val) {
    return newErr("unsupported conversion to ref.Val: (%s)%s", val.getClass().getSimpleName(), val);
  }

  /**
   * MaybeNoSuchOverloadErr returns the error or unknown if the input ref.Val is one of these types,
   * else a new no such overload error.
   */
  public static Val maybeNoSuchOverloadErr(Val val) {
    return valOrErr(val, "no such overload");
  }

  /**
   * ValOrErr either returns the existing error or create a new one. TODO: Audit the use of this
   * function and standardize the error messages and codes.
   */
  public static Val valOrErr(Val val, String format, Object... args) {
    if (val == null) {
      return newErr(format, args);
    }
    if (val.type() == ErrType || val.type() == UnknownType) {
      return val;
    }
    return newErr(format, args);
  }

  /** ConvertToNative implements ref.Val.ConvertToNative. */
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    throw new UnsupportedOperationException(error);
  }

  /** ConvertToType implements ref.Val.ConvertToType. */
  @Override
  public Val convertToType(Type typeVal) {
    // Errors are not convertible to other representations.
    return this;
  }

  /** Equal implements ref.Val.Equal. */
  @Override
  public Val equal(Val other) {
    // An error cannot be equal to any other value, so it returns itself.
    return this;
  }

  /** String implements fmt.Stringer. */
  @Override
  public String toString() {
    return error;
  }

  /** Type implements ref.Val.Type. */
  @Override
  public Type type() {
    return ErrType;
  }

  /** Value implements ref.Val.Value. */
  @Override
  public Object value() {
    return error;
  }

  /**
   * IsError returns whether the input element ref.Type or ref.Val is equal to the ErrType
   * singleton.
   */
  public static boolean isError(Val val) {
    return val.type() == ErrType;
  }
}
