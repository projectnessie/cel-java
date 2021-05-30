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
package org.projectnessie.cel.pb;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public class Constant {
  public static NullValue nullValue() {
    return new NullValue();
  }

  public static class NullValue extends Constant {
    public NullValue() {}

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return 0;
    }
  }

  public static BoolValue boolValue(boolean value) {
    return new BoolValue(value);
  }

  public static class BoolValue extends Constant {
    public final boolean value;

    public BoolValue(boolean value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BoolValue boolValue = (BoolValue) o;
      return value == boolValue.value;
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }

  public static Int64Value int64Value(long value) {
    return new Int64Value(value);
  }

  public static class Int64Value extends Constant {
    public final long value;

    public Int64Value(long value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Int64Value that = (Int64Value) o;
      return value == that.value;
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }

  public static Uint64Value uint64Value(long value) {
    return new Uint64Value(value);
  }

  public static class Uint64Value extends Constant {
    public final long value;

    public Uint64Value(long value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Uint64Value that = (Uint64Value) o;
      return value == that.value;
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }

  public static DoubleValue doubleValue(double value) {
    return new DoubleValue(value);
  }

  public static class DoubleValue extends Constant {
    public final double value;

    public DoubleValue(double value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      DoubleValue that = (DoubleValue) o;
      return Double.compare(that.value, value) == 0;
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }

  public static StringValue stringValue(String value) {
    return new StringValue(value);
  }

  public static class StringValue extends Constant {
    public final String value;

    public StringValue(String value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      StringValue that = (StringValue) o;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }

  public static BytesValue bytesValue(byte[] value) {
    return new BytesValue(value);
  }

  public static class BytesValue extends Constant {
    public final byte[] value;

    public BytesValue(byte[] value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BytesValue that = (BytesValue) o;
      return Arrays.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(value);
    }
  }

  public static DurationValue durationValue(Duration value) {
    return new DurationValue(value);
  }

  public static class DurationValue extends Constant {
    public final Duration value;

    public DurationValue(Duration value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      DurationValue that = (DurationValue) o;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }

  public static TimestampValue timestampValue(Instant value) {
    return new TimestampValue(value);
  }

  public static class TimestampValue extends Constant {
    public final Instant value;

    public TimestampValue(Instant value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TimestampValue that = (TimestampValue) o;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }
}
