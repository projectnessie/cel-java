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

import static org.projectnessie.cel.common.types.BoolT.BoolType;
import static org.projectnessie.cel.common.types.BoolT.boolOf;
import static org.projectnessie.cel.common.types.BytesT.BytesType;
import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.DoubleT.DoubleType;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.DurationT.DurationType;
import static org.projectnessie.cel.common.types.DurationT.durationOf;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.valOrErr;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.TimestampT.TimestampType;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;
import static org.projectnessie.cel.common.types.UintT.UintType;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Adder;
import org.projectnessie.cel.common.types.traits.Comparer;
import org.projectnessie.cel.common.types.traits.Matcher;
import org.projectnessie.cel.common.types.traits.Receiver;
import org.projectnessie.cel.common.types.traits.Sizer;
import org.projectnessie.cel.common.types.traits.Trait;

/** String type implementation which supports addition, comparison, matching, and size functions. */
public class StringT extends BaseVal implements Adder, Comparer, Matcher, Receiver, Sizer {

  /** StringType singleton. */
  public static final TypeValue StringType =
      TypeValue.newTypeValue(
          "string",
          Trait.AdderType,
          Trait.ComparerType,
          Trait.MatcherType,
          Trait.ReceiverType,
          Trait.SizerType);

  public static final Map<String, BiFunction<String, Val, Val>> stringOneArgOverloads;

  static {
    stringOneArgOverloads = new HashMap<>();
    stringOneArgOverloads.put(Overloads.Contains, StringT::stringContains);
    stringOneArgOverloads.put(Overloads.EndsWith, StringT::stringEndsWith);
    stringOneArgOverloads.put(Overloads.StartsWith, StringT::stringStartsWith);
  }

  private final String s;

  private StringT(String s) {
    this.s = s;
  }

  public static StringT stringOf(String s) {
    return new StringT(s);
  }

  /** Add implements traits.Adder.Add. */
  @Override
  public Val add(Val other) {
    if (!(other instanceof StringT)) {
      return valOrErr(other, "no such overload");
    }
    return new StringT(s + ((StringT) other).s);
  }

  /** Compare implements traits.Comparer.Compare. */
  @Override
  public Val compare(Val other) {
    if (!(other instanceof StringT)) {
      return valOrErr(other, "no such overload");
    }

    return intOf(s.compareTo(((StringT) other).s));
  }

  /** ConvertToNative implements ref.Val.ConvertToNative. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == String.class) {
      return (T) s;
    }
    if (typeDesc == byte[].class) {
      return (T) s.getBytes(StandardCharsets.UTF_8);
    }

    //		switch typeDesc.Kind() {
    //		case reflect.String:
    //			if reflect.TypeOf(s).AssignableTo(typeDesc) {
    //				return s, nil
    //			}
    //			return s.Value(), nil
    //		case reflect.Ptr:
    //			switch typeDesc {
    //			case anyValueType:
    //				// Primitives must be wrapped before being set on an Any field.
    //				return anypb.New(wrapperspb.String(string(s)))
    //			case jsonValueType:
    //				// Convert to a protobuf representation of a JSON String.
    //				return structpb.NewStringValue(string(s)), nil
    //			case stringWrapperType:
    //				// Convert to a wrapperspb.StringValue.
    //				return wrapperspb.String(string(s)), nil
    //			}
    //			if typeDesc.Elem().Kind() == reflect.String {
    //				p := s.Value().(string)
    //				return &p, nil
    //			}
    //		case reflect.Interface:
    //			sv := s.Value()
    //			if reflect.TypeOf(sv).Implements(typeDesc) {
    //				return sv, nil
    //			}
    //			if reflect.TypeOf(s).Implements(typeDesc) {
    //				return s, nil
    //			}
    //		}
    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", StringType, typeDesc.getName()));
  }

  /** ConvertToType implements ref.Val.ConvertToType. */
  @Override
  public Val convertToType(Type typeVal) {
    try {
      if (typeVal == IntType) {
        return intOf(Long.parseLong(s));
      }
      if (typeVal == UintType) {
        return uintOf(Long.parseUnsignedLong(s));
      }
      if (typeVal == DoubleType) {
        return doubleOf(Double.parseDouble(s));
      }
      if (typeVal == BoolType) {
        return boolOf(Boolean.parseBoolean(s));
      }
      if (typeVal == BytesType) {
        return bytesOf(s.getBytes(StandardCharsets.UTF_8));
      }
      if (typeVal == DurationType) {
        return durationOf(s);
      }
      if (typeVal == TimestampType) {
        return timestampOf(s);
      }
      if (typeVal == StringType) {
        return this;
      }
      if (typeVal == TypeType) {
        return StringType;
      }
      return newErr("type conversion error from '%s' to '%s'", StringType, typeVal);
    } catch (Exception e) {
      return newErr(
          "error during type conversion from '%s' to %s: %s", StringType, typeVal, e.toString());
    }
  }

  /** Equal implements ref.Val.Equal. */
  @Override
  public Val equal(Val other) {
    if (!(other instanceof StringT)) {
      return valOrErr(other, "no such overload");
    }
    return boolOf(s.equals(((StringT) other).s));
  }

  /** Match implements traits.Matcher.Match. */
  @Override
  public Val match(Val pattern) {
    if (!(pattern instanceof StringT)) {
      return valOrErr(pattern, "no such overload");
    }
    try {
      Pattern p = Pattern.compile(((StringT) pattern).s);
      java.util.regex.Matcher m = p.matcher(s);
      return boolOf(m.find());
    } catch (Exception e) {
      return newErr("%s", e.getMessage());
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
    return newErr("no such overload");
  }

  /** Size implements traits.Sizer.Size. */
  @Override
  public Val size() {
    return intOf(s.length());
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

  static Val stringContains(String s, Val sub) {
    if (!(sub instanceof StringT)) {
      return valOrErr(sub, "no such overload");
    }
    return boolOf(s.contains(((StringT) sub).s));
  }

  static Val stringEndsWith(String s, Val suf) {
    if (!(suf instanceof StringT)) {
      return valOrErr(suf, "no such overload");
    }
    return boolOf(s.endsWith(((StringT) suf).s));
  }

  static Val stringStartsWith(String s, Val pre) {
    if (!(pre instanceof StringT)) {
      return valOrErr(pre, "no such overload");
    }
    return boolOf(s.startsWith(((StringT) pre).s));
  }
}
