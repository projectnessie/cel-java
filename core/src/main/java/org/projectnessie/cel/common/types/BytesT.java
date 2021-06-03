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

import static org.projectnessie.cel.common.types.BoolT.boolOf;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;

import com.google.api.expr.v1alpha1.Constant;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.projectnessie.cel.common.debug.Debug;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Adder;
import org.projectnessie.cel.common.types.traits.Comparer;
import org.projectnessie.cel.common.types.traits.Sizer;
import org.projectnessie.cel.common.types.traits.Trait;

/** Bytes type that implements ref.Val and supports add, compare, and size operations. */
public final class BytesT extends BaseVal implements Adder, Comparer, Sizer {
  /** BytesType singleton. */
  public static final TypeValue BytesType =
      TypeValue.newTypeValue("bytes", Trait.AdderType, Trait.ComparerType, Trait.SizerType);

  private final byte[] b;

  private BytesT(byte[] b) {
    this.b = b;
  }

  public static BytesT bytesOf(byte[] b) {
    return new BytesT(b);
  }

  public static BytesT bytesOf(String s) {
    return new BytesT(s.getBytes(StandardCharsets.UTF_8));
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
    return IntT.intOf(ByteBuffer.wrap(b).compareTo(ByteBuffer.wrap(o)));
  }

  /** ConvertToNative implements the ref.Val interface method. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == byte[].class) {
      return (T) b;
    }
    if (typeDesc == String.class) {
      try {
        return (T) new String(b, StandardCharsets.UTF_8);
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
    if (typeDesc == ByteString.class || typeDesc == Object.class) {
      return (T) ByteString.copyFrom(this.b);
    }
    if (typeDesc == ByteBuffer.class) {
      return (T) ByteBuffer.wrap(this.b);
    }
    if (typeDesc == Val.class || typeDesc == BytesT.class) {
      return (T) this;
    }
    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", BytesType, typeDesc.getName()));
    //		switch typeDesc.Kind() {
    //		case reflect.Array, reflect.Slice:
    //			return reflect.ValueOf(b).Convert(typeDesc).Interface(), nil
    //		case reflect.Ptr:
    //			switch typeDesc {
    //			case jsonValueType:
    //				// CEL follows the proto3 to JSON conversion by encoding bytes to a string via base64.
    //				// The encoding below matches the golang 'encoding/json' behavior during marshaling,
    //				// which uses base64.StdEncoding.
    //				str := base64.StdEncoding.EncodeToString([]byte(b))
    //				return structpb.NewStringValue(str), nil
    //			}
    //		case reflect.Interface:
    //			bv := b.Value()
    //			if reflect.TypeOf(bv).Implements(typeDesc) {
    //				return bv, nil
    //			}
    //			if reflect.TypeOf(b).Implements(typeDesc) {
    //				return b, nil
    //			}
    //		}
  }

  /** ConvertToType implements the ref.Val interface method. */
  @Override
  public Val convertToType(Type typeValue) {
    if (typeValue == StringType) {
      try {
        return stringOf(new String(b, StandardCharsets.UTF_8));
      } catch (Exception e) {
        return newErr("invalid UTF-8 in bytes, cannot convert to string");
      }
    }
    if (typeValue == BytesType) {
      return this;
    }
    if (typeValue == TypeType) {
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
