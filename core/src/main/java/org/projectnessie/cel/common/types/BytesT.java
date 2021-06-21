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

import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.IntT.intOfCompare;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.Types.boolOf;

import com.google.api.expr.v1alpha1.Constant;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Value;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.projectnessie.cel.common.debug.Debug;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Adder;
import org.projectnessie.cel.common.types.traits.Comparer;
import org.projectnessie.cel.common.types.traits.Sizer;
import org.projectnessie.cel.common.types.traits.Trait;
import org.projectnessie.cel.parser.Unescape;

/** Bytes type that implements ref.Val and supports add, compare, and size operations. */
public final class BytesT extends BaseVal implements Adder, Comparer, Sizer {
  /** BytesType singleton. */
  public static final Type BytesType =
      TypeT.newTypeValue(TypeEnum.Bytes, Trait.AdderType, Trait.ComparerType, Trait.SizerType);

  public static BytesT bytesOf(byte[] b) {
    return new BytesT(b);
  }

  public static Val bytesOf(ByteString value) {
    return bytesOf(value.toByteArray());
  }

  public static BytesT bytesOf(String s) {
    return new BytesT(s.getBytes(StandardCharsets.UTF_8));
  }

  private final byte[] b;

  private BytesT(byte[] b) {
    this.b = b;
  }

  /** Add implements traits.Adder interface method by concatenating byte sequences. */
  @Override
  public Val add(Val other) {
    if (!(other instanceof BytesT)) {
      return noSuchOverload(this, "add", other);
    }
    byte[] o = ((BytesT) other).b;
    byte[] n = Arrays.copyOf(b, b.length + o.length);
    System.arraycopy(o, 0, n, b.length, o.length);
    return bytesOf(n);
  }

  /** Compare implments traits.Comparer interface method by lexicographic ordering. */
  @Override
  public Val compare(Val other) {
    if (!(other instanceof BytesT)) {
      return noSuchOverload(this, "compare", other);
    }
    byte[] o = ((BytesT) other).b;
    // unsigned !!!
    int l = b.length;
    int ol = o.length;
    int cl = Math.min(l, ol);
    for (int i = 0; i < cl; i++) {
      byte b1 = b[i];
      byte b2 = o[i];
      int cmpUns = Byte.toUnsignedInt(b1) - Byte.toUnsignedInt(b2);
      if (cmpUns != 0) {
        return intOfCompare(cmpUns);
      }
    }
    return intOfCompare(l - ol);
  }

  /** ConvertToNative implements the ref.Val interface method. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == ByteString.class || typeDesc == Object.class) {
      return (T) ByteString.copyFrom(this.b);
    }
    if (typeDesc == byte[].class) {
      return (T) b;
    }
    if (typeDesc == String.class) {
      try {
        return (T) Unescape.toUtf8(ByteBuffer.wrap(b));
      } catch (Exception e) {
        throw new RuntimeException("invalid UTF-8 in bytes, cannot convert to string");
      }
    }
    if (typeDesc == Any.class) {
      return (T) Any.pack(BytesValue.of(ByteString.copyFrom(this.b)));
    }
    if (typeDesc == BytesValue.class) {
      return (T) BytesValue.of(ByteString.copyFrom(this.b));
    }
    if (typeDesc == ByteBuffer.class) {
      return (T) ByteBuffer.wrap(this.b);
    }
    if (typeDesc == Val.class || typeDesc == BytesT.class) {
      return (T) this;
    }
    if (typeDesc == Value.class) {
      // CEL follows the proto3 to JSON conversion by encoding bytes to a string via base64.
      // The encoding below matches the golang 'encoding/json' behavior during marshaling,
      // which uses base64.StdEncoding.
      return (T)
          Value.newBuilder().setStringValue(Base64.getEncoder().encodeToString(this.b)).build();
    }
    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", BytesType, typeDesc.getName()));
  }

  /** ConvertToType implements the ref.Val interface method. */
  @Override
  public Val convertToType(Type typeValue) {
    switch (typeValue.typeEnum()) {
      case String:
        try {
          return stringOf(Unescape.toUtf8(ByteBuffer.wrap(b)));
        } catch (Exception e) {
          return newErr(e, "invalid UTF-8 in bytes, cannot convert to string");
        }
      case Bytes:
        return this;
      case Type:
        return BytesType;
    }
    return newTypeConversionError(BytesType, typeValue);
  }

  /** Equal implements the ref.Val interface method. */
  @Override
  public Val equal(Val other) {
    if (!(other instanceof BytesT)) {
      return noSuchOverload(this, "equal", other);
    }
    return boolOf(Arrays.equals(b, ((BytesT) other).b));
  }

  /** Size implements the traits.Sizer interface method. */
  @Override
  public Val size() {
    return IntT.intOf(b.length);
  }

  /** Type implements the ref.Val interface method. */
  @Override
  public Type type() {
    return BytesType;
  }

  /** Value implements the ref.Val interface method. */
  @Override
  public Object value() {
    return b;
  }

  @Override
  public String toString() {
    return "bytes{"
        + Debug.formatLiteral(Constant.newBuilder().setBytesValue(ByteString.copyFrom(b)).build())
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BytesT bytesT = (BytesT) o;
    return Arrays.equals(b, bytesT.b);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Arrays.hashCode(b);
    return result;
  }
}
