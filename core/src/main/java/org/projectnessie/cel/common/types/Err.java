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

import static java.lang.String.format;
import static org.projectnessie.cel.common.types.UnknownT.UnknownType;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;

/** Err type which extends the built-in go error and implements ref.Val. */
public final class Err extends BaseVal {

  /** ErrType singleton. */
  public static final Type ErrType = TypeT.newTypeValue(TypeEnum.Err);

  /** errIntOverflow is an error representing integer overflow. */
  public static final Val errIntOverflow = newErr("integer overflow");
  /** errUintOverflow is an error representing unsigned integer overflow. */
  public static final Val errUintOverflow = newErr("unsigned integer overflow");
  /** errDurationOverflow is an error representing duration overflow. */
  public static final Val errDurationOverflow = newErr("duration overflow");
  /** errDurationOutOfRange is an error representing duration out of range. */
  public static final Val errDurationOutOfRange = newErr("duration out of range");
  /** errTimestampOverflow is an error representing timestamp overflow. */
  public static final Val errTimestampOverflow = newErr("timestamp overflow");
  /** errTimestampOutOfRange is an error representing duration out of range. */
  public static final Val errTimestampOutOfRange = newErr("timestamp out of range");

  private final String error;
  private final Throwable cause;

  private Err(String error) {
    this(error, null);
  }

  private Err(String error, Throwable cause) {
    this.error = error;
    this.cause = cause;
  }

  public static Val noSuchOverload(Val val, String function, Val other) {
    String otName =
        (other != null) ? ((other instanceof Type) ? (Type) other : other.type()).typeName() : "*";
    if (val != null) {
      Type vt = (val instanceof Type) ? (Type) val : val.type();
      return valOrErr(other, "no such overload: %s.%s(%s)", vt.typeName(), function, otName);
    } else {
      return valOrErr(other, "no such overload: *.%s(%s)", function, otName);
    }
  }

  public static Val noSuchOverload(Val val, String function, Type argA, Type argB) {
    return newErr(
        "no such overload: %s.%s(%s,%s,...)", val.type().typeName(), function, argA, argB);
  }

  public static Val noSuchOverload(Val val, String function, String overload, Val[] args) {
    return newErr(
        "no such overload: %s.%s[%s](%s)",
        val.type().typeName(),
        function,
        overload,
        Arrays.stream(args).map(a -> a.type().typeName()).collect(Collectors.joining(", ")));
  }

  /**
   * MaybeNoSuchOverloadErr returns the error or unknown if the input ref.Val is one of these types,
   * else a new no such overload error.
   */
  public static Val maybeNoSuchOverloadErr(Val val) {
    return valOrErr(val, "no such overload");
  }

  /**
   * NewErr creates a new Err described by the format string and args. TODO: Audit the use of this
   * function and standardize the error messages and codes.
   */
  public static Val newErr(String format, Object... args) {
    return new Err(format(format, args));
  }

  /**
   * NewErr creates a new Err described by the format string and args. TODO: Audit the use of this
   * function and standardize the error messages and codes.
   */
  public static Val newErr(Throwable cause, String format, Object... args) {
    if (cause instanceof ErrException) {
      return ((ErrException) cause).getErr();
    }
    return new Err(format(format, args), cause);
  }

  /**
   * UnsupportedRefValConversionErr returns a types.NewErr instance with a no such conversion
   * message that indicates that the native value could not be converted to a CEL ref.Val.
   */
  public static Val unsupportedRefValConversionErr(Object val) {
    return newErr("unsupported conversion to ref.Val: (%s)%s", val.getClass().getSimpleName(), val);
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

  public static Val noSuchField(Object field) {
    return newErr("no such field '%s'", field);
  }

  public static Val unknownType(Object field) {
    return newErr("unknown type '%s'", field);
  }

  public static Val anyWithEmptyType() {
    return newErr("conversion error: got Any with empty type-url");
  }

  public static Val divideByZero() {
    return newErr("divide by zero");
  }

  public static Val noMoreElements() {
    return newErr("no more elements");
  }

  public static Val modulusByZero() {
    return newErr("modulus by zero");
  }

  public static Val rangeError(Object from, Object to) {
    return newErr("range error converting %s to %s", from, to);
  }

  public static Val newTypeConversionError(Object from, Object to) {
    return newErr("type conversion error from '%s' to '%s'", from, to);
  }

  public static RuntimeException noSuchAttributeException(Object context) {
    return new ErrException("undeclared reference to '%s' (in container '')", context);
  }

  public static Val noSuchKey(Object key) {
    return newErr("no such key: %s", key);
  }

  public static RuntimeException noSuchKeyException(Object key) {
    return new ErrException("no such key: %s", key);
  }

  public static RuntimeException indexOutOfBoundsException(Object i) {
    return new IllegalStateException(format("index out of bounds: %s", i));
  }

  public static final class ErrException extends IllegalArgumentException {
    private final String format;
    private final Object[] args;

    public ErrException(String format, Object... args) {
      super(format(format, args));
      this.format = format;
      this.args = args;
    }

    public Val getErr() {
      return newErr(format, args);
    }
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

  @Override
  public boolean booleanValue() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long intValue() {
    throw new UnsupportedOperationException();
  }

  /**
   * IsError returns whether the input element ref.Type or ref.Val is equal to the ErrType
   * singleton.
   */
  public static boolean isError(Val val) {
    return val != null && val.type() == ErrType;
  }

  public boolean hasCause() {
    return cause != null;
  }

  public Throwable getCause() {
    return cause;
  }

  public RuntimeException toRuntimeException() {
    if (cause != null) throw new RuntimeException(this.error, this.cause);
    throw new RuntimeException(this.error);
  }

  public static void throwErrorAsIllegalStateException(Val val) {
    if (val instanceof Err) {
      Err e = (Err) val;
      if (e.cause != null) {
        throw new IllegalStateException(e.error, e.cause);
      } else {
        throw new IllegalStateException(e.error);
      }
    }
  }
}
