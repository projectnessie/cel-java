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
import static org.projectnessie.cel.common.types.Err.errDurationOverflow;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.TimestampType;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;

import com.google.protobuf.Any;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.projectnessie.cel.common.types.Overflow.OverflowException;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Adder;
import org.projectnessie.cel.common.types.traits.Comparer;
import org.projectnessie.cel.common.types.traits.Negater;
import org.projectnessie.cel.common.types.traits.Receiver;
import org.projectnessie.cel.common.types.traits.Subtractor;
import org.projectnessie.cel.common.types.traits.Trait;

/**
 * Duration type that implements ref.Val and supports add, compare, negate, and subtract operators.
 * This type is also a receiver which means it can participate in dispatch to receiver functions.
 */
public final class DurationT extends BaseVal
    implements Adder, Comparer, Negater, Receiver, Subtractor {

  /** DurationType singleton. */
  public static final TypeValue DurationType =
      TypeValue.newTypeValue(
          "google.protobuf.Duration",
          Trait.AdderType,
          Trait.ComparerType,
          Trait.NegatorType,
          Trait.ReceiverType,
          Trait.SubtractorType);

  public static final Map<String, Function<Duration, Val>> durationZeroArgOverloads;

  static {
    durationZeroArgOverloads = new HashMap<>();
    durationZeroArgOverloads.put(Overloads.TimeGetHours, DurationT::timeGetHours);
    durationZeroArgOverloads.put(Overloads.TimeGetMinutes, DurationT::timeGetMinutes);
    durationZeroArgOverloads.put(Overloads.TimeGetSeconds, DurationT::timeGetSeconds);
    durationZeroArgOverloads.put(Overloads.TimeGetMilliseconds, DurationT::timeGetMilliseconds);
  }

  private final Duration d;

  private DurationT(Duration d) {
    this.d = d;
  }

  public static DurationT durationOf(String s) {
    Duration dur;
    try {
      dur = Duration.parse("PT" + s);
    } catch (DateTimeParseException e) {
      dur = Duration.parse("P" + s);
    }
    return durationOf(dur);
  }

  public static DurationT durationOf(com.google.protobuf.Duration d) {
    return new DurationT(Duration.ofSeconds(d.getSeconds(), d.getNanos()));
  }

  public static DurationT durationOf(Duration d) {
    return new DurationT(d);
  }

  /** Add implements traits.Adder.Add. */
  @Override
  public Val add(Val other) {
    if (other.type() == DurationType) {
      try {
        return durationOf(Overflow.addDurationChecked(d, ((DurationT) other).d));
      } catch (OverflowException e) {
        return errDurationOverflow;
      }
    }
    if (other.type() == TimestampType) {
      try {
        return timestampOf(Overflow.addTimeDurationChecked((ZonedDateTime) other.value(), d));
      } catch (OverflowException e) {
        return errDurationOverflow;
      }
    }
    return noSuchOverload(this, "add", other);
  }

  /** Compare implements traits.Comparer.Compare. */
  @Override
  public Val compare(Val other) {
    if (!(other instanceof DurationT)) {
      return noSuchOverload(this, "compare", other);
    }
    Duration o = ((DurationT) other).d;
    return IntT.intOf(d.compareTo(o));
  }

  /** ConvertToNative implements ref.Val.ConvertToNative. */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (Duration.class.isAssignableFrom(typeDesc)) {
      return (T) d;
    }
    if (com.google.protobuf.Duration.class == typeDesc || typeDesc == Object.class) {
      return (T) pbVal();
    }
    if (Any.class == typeDesc) {
      return (T) Any.pack(pbVal());
    }
    if (Long.class == typeDesc) {
      return (T) Long.valueOf(toJavaLong());
    }
    if (String.class == typeDesc) {
      return (T) toPbString();
    }
    if (typeDesc == Val.class || typeDesc == DurationT.class) {
      return (T) this;
    }
    //		// If the duration is already assignable to the desired type return it.
    //		if reflect.TypeOf(d.Duration).AssignableTo(typeDesc) {
    //			return d.Duration, nil
    //		}
    //		if reflect.TypeOf(d).AssignableTo(typeDesc) {
    //			return d, nil
    //		}
    //		switch typeDesc {
    //		case jsonValueType:
    //			// CEL follows the proto3 to JSON conversion.
    //			// Note, using jsonpb would wrap the result in extra double quotes.
    //			v := d.ConvertToType(StringType)
    //			if IsError(v) {
    //				return nil, v.(*Err)
    //			}
    //			return structpb.NewStringValue(string(v.(String))), nil
    //		}
    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", DurationType, typeDesc.getName()));
  }

  private com.google.protobuf.Duration pbVal() {
    return com.google.protobuf.Duration.newBuilder()
        .setSeconds(d.getSeconds())
        .setNanos(d.getNano())
        .build();
  }

  private long toJavaLong() {
    return TimeUnit.SECONDS.toNanos(d.getSeconds()) + d.getNano();
  }

  private String toPbString() {
    // 7506.000001s
    return String.format("%d.%06ds", d.getSeconds(), TimeUnit.NANOSECONDS.toMicros(d.getNano()));
  }

  /** ConvertToType implements ref.Val.ConvertToType. */
  @Override
  public Val convertToType(Type typeValue) {
    if (typeValue == StringType) {
      return stringOf(toPbString());
    }
    if (typeValue == IntType) {
      return IntT.intOf(toJavaLong());
    }
    if (typeValue == DurationType) {
      return this;
    }
    if (typeValue == TypeType) {
      return DurationType;
    }
    return newTypeConversionError(DurationType, typeValue);
  }

  /** Equal implements ref.Val.Equal. */
  @Override
  public Val equal(Val other) {
    if (!(other instanceof DurationT)) {
      return noSuchOverload(this, "equal", other);
    }
    return boolOf(d.equals(((DurationT) other).d));
  }

  /** Negate implements traits.Negater.Negate. */
  @Override
  public Val negate() {
    try {
      return durationOf(Overflow.negateDurationChecked(d));
    } catch (OverflowException e) {
      return errDurationOverflow;
    }
  }

  /** Receive implements traits.Receiver.Receive. */
  @Override
  public Val receive(String function, String overload, Val... args) {
    if (args.length == 0) {
      Function<Duration, Val> f = durationZeroArgOverloads.get(function);
      if (f != null) {
        return f.apply(d);
      }
    }
    return noSuchOverload(this, function, overload, args);
  }

  /** Subtract implements traits.Subtractor.Subtract. */
  @Override
  public Val subtract(Val other) {
    if (!(other instanceof DurationT)) {
      return noSuchOverload(this, "subtract", other);
    }
    try {
      return durationOf(Overflow.subtractDurationChecked(d, ((DurationT) other).d));
    } catch (OverflowException e) {
      return errDurationOverflow;
    }
  }

  /** Type implements ref.Val.Type. */
  @Override
  public Type type() {
    return DurationType;
  }

  /** Value implements ref.Val.Value. */
  @Override
  public Object value() {
    return d;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DurationT durationT = (DurationT) o;
    return Objects.equals(d, durationT.d);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), d);
  }

  public static Val timeGetHours(Duration duration) {
    return IntT.intOf(TimeUnit.SECONDS.toHours(duration.getSeconds()));
  }

  public static Val timeGetMinutes(Duration duration) {
    return IntT.intOf(TimeUnit.SECONDS.toMinutes(duration.getSeconds()));
  }

  public static Val timeGetSeconds(Duration duration) {
    return IntT.intOf(duration.getSeconds());
  }

  public static Val timeGetMilliseconds(Duration duration) {
    return IntT.intOf(
        TimeUnit.SECONDS.toMillis(duration.getSeconds())
            + TimeUnit.NANOSECONDS.toMillis(duration.getNano()));
  }
}
