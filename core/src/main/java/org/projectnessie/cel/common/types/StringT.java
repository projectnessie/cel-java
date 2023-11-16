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

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.DurationT.durationOf;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.IntT.intOfCompare;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import com.google.protobuf.Any;
import com.google.protobuf.StringValue;
import com.google.protobuf.Value;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Adder;
import org.projectnessie.cel.common.types.traits.Comparer;
import org.projectnessie.cel.common.types.traits.Matcher;
import org.projectnessie.cel.common.types.traits.Receiver;
import org.projectnessie.cel.common.types.traits.Sizer;
import org.projectnessie.cel.common.types.traits.Trait;

/** String type implementation which supports addition, comparison, matching, and size functions. */
public final class StringT extends BaseVal implements Adder, Comparer, Matcher, Receiver, Sizer {

  /** StringType singleton. */
  public static final Type StringType =
      TypeT.newTypeValue(
          TypeEnum.String,
          Trait.AdderType,
          Trait.ComparerType,
          Trait.MatcherType,
          Trait.ReceiverType,
          Trait.SizerType);

  private static final Map<String, BiFunction<String, Val, Val>> stringOneArgOverloads;

  static {
    stringOneArgOverloads = new HashMap<>();
    stringOneArgOverloads.put(Overloads.Contains, StringT::stringContains);
    stringOneArgOverloads.put(Overloads.EndsWith, StringT::stringEndsWith);
    stringOneArgOverloads.put(Overloads.StartsWith, StringT::stringStartsWith);
  }

  public static StringT stringOf(String s) {
    return new StringT(s);
  }

  private final String s;

  private StringT(String s) {
    this.s = s;
  }

  /** Add implements traits.Adder.Add. */
  @Override
  public Val add(Val other) {
    if (!(other instanceof StringT)) {
      return noSuchOverload(this, "add", other);
    }
    return new StringT(s + ((StringT) other).s);
  }

  /** ConvertToNative implements ref.Val.ConvertToNative. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == String.class || typeDesc == Object.class) {
      return (T) s;
    }
    if (typeDesc == byte[].class) {
      return (T) s.getBytes(StandardCharsets.UTF_8);
    }
    if (typeDesc == Any.class) {
      return (T) Any.pack(StringValue.of(s));
    }
    if (typeDesc == StringValue.class) {
      return (T) StringValue.of(s);
    }
    if (typeDesc == Val.class || typeDesc == StringT.class) {
      return (T) this;
    }
    if (typeDesc == Value.class) {
      return (T) Value.newBuilder().setStringValue(s).build();
    }
    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", StringType, typeDesc.getName()));
  }

  /** ConvertToType implements ref.Val.ConvertToType. */
  @Override
  public Val convertToType(Type typeVal) {
    try {
      switch (typeVal.typeEnum()) {
        case Int:
          return intOf(Long.parseLong(s));
        case Uint:
          return uintOf(Long.parseUnsignedLong(s));
        case Double:
          return doubleOf(Double.parseDouble(s));
        case Bool:
          if ("true".equalsIgnoreCase(s)) {
            return True;
          }
          if ("false".equalsIgnoreCase(s)) {
            return False;
          }
          break;
        case Bytes:
          return bytesOf(s.getBytes(StandardCharsets.UTF_8));
        case Duration:
          return durationOf(s).rangeCheck();
        case Timestamp:
          return timestampOf(s).rangeCheck();
        case String:
          return this;
        case Type:
          return StringType;
      }
      return newTypeConversionError(StringType, typeVal);
    } catch (Exception e) {
      return newErr(
          e, "error during type conversion from '%s' to %s: %s", StringType, typeVal, e.toString());
    }
  }

  /** Compare implements traits.Comparer.Compare. */
  @Override
  public Val compare(Val other) {
    switch (other.type().typeEnum()) {
      case String:
        return intOfCompare(s.compareTo(((StringT) other).s));
      case Null:
        return False;
      default:
        return noSuchOverload(this, "compare", other);
    }
  }

  /** Equal implements ref.Val.Equal. */
  @Override
  public Val equal(Val other) {
    switch (other.type().typeEnum()) {
      case String:
        return boolOf(s.equals(((StringT) other).s));
      case Int:
      case Uint:
      case Double:
      case Bool:
        return boolOf(s.equals(((StringT) other.convertToType(StringType)).s));
      case Null:
        return False;
      default:
        return noSuchOverload(this, "equal", other);
    }
  }

  /** Match implements traits.Matcher.Match. */
  @Override
  public Val match(Val pattern) {
    if (!(pattern instanceof StringT)) {
      return noSuchOverload(this, "match", pattern);
    }
    try {
      Pattern p = Pattern.compile(((StringT) pattern).s);
      java.util.regex.Matcher m = p.matcher(s);
      return boolOf(m.find());
    } catch (Exception e) {
      return newErr(e, "%s", e.getMessage());
    }
  }

  /** Receive implements traits.Reciever.Receive. */
  @Override
  public Val receive(String function, String overload, Val... args) {
    if (args.length == 1) {
      BiFunction<String, Val, Val> f = stringOneArgOverloads.get(function);
      if (f != null) {
        return f.apply(s, args[0]);
      }
    }
    return noSuchOverload(this, function, overload, args);
  }

  /** Size implements traits.Sizer.Size. */
  @Override
  public Val size() {
    return intOf(s.codePointCount(0, s.length()));
  }

  /** Type implements ref.Val.Type. */
  @Override
  public Type type() {
    return StringType;
  }

  /** Value implements ref.Val.Value. */
  @Override
  public Object value() {
    return s;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StringT stringT = (StringT) o;
    return Objects.equals(s, stringT.s);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), s);
  }

  static Val stringContains(String s, Val sub) {
    if (!(sub instanceof StringT)) {
      return noSuchOverload(StringType, "contains", sub);
    }
    return boolOf(s.contains(((StringT) sub).s));
  }

  static Val stringEndsWith(String s, Val suf) {
    if (!(suf instanceof StringT)) {
      return noSuchOverload(StringType, "endsWith", suf);
    }
    return boolOf(s.endsWith(((StringT) suf).s));
  }

  static Val stringStartsWith(String s, Val pre) {
    if (!(pre instanceof StringT)) {
      return noSuchOverload(StringType, "startsWith", pre);
    }
    return boolOf(s.startsWith(((StringT) pre).s));
  }
}
