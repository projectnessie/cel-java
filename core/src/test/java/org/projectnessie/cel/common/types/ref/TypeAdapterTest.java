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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;

@SuppressWarnings("removal")
class TypeAdapterTest {

  private static final TypeAdapter DEFAULT_ADAPTER = DefaultTypeAdapter.Instance;

  @Test
  void primitiveInboundDefaultsPreserveExactWrapperType() {
    TrackingAdapter adapter = new TrackingAdapter();

    adapter.nativeToValue(true);
    assertThat(adapter.lastNativeValue).isExactlyInstanceOf(Boolean.class).isEqualTo(true);

    adapter.nativeToValue((byte) 1);
    assertThat(adapter.lastNativeValue).isExactlyInstanceOf(Byte.class).isEqualTo((byte) 1);

    adapter.nativeToValue((short) 2);
    assertThat(adapter.lastNativeValue).isExactlyInstanceOf(Short.class).isEqualTo((short) 2);

    adapter.nativeToValue(3);
    assertThat(adapter.lastNativeValue).isExactlyInstanceOf(Integer.class).isEqualTo(3);

    adapter.nativeToValue('4');
    assertThat(adapter.lastNativeValue).isExactlyInstanceOf(Character.class).isEqualTo('4');

    adapter.nativeToValue(5L);
    assertThat(adapter.lastNativeValue).isExactlyInstanceOf(Long.class).isEqualTo(5L);

    adapter.nativeToValue(6.0f);
    assertThat(adapter.lastNativeValue).isExactlyInstanceOf(Float.class).isEqualTo(6.0f);

    adapter.nativeToValue(7.0d);
    assertThat(adapter.lastNativeValue).isExactlyInstanceOf(Double.class).isEqualTo(7.0d);
  }

  @Test
  void builtInPrimitiveInboundConversions() {
    assertThat(DEFAULT_ADAPTER.nativeToValue(true)).isSameAs(True);
    assertThat(DEFAULT_ADAPTER.nativeToValue(false)).isSameAs(False);
    assertThat(DEFAULT_ADAPTER.nativeToValue((byte) -1)).isEqualTo(intOf(-1));
    assertThat(DEFAULT_ADAPTER.nativeToValue((short) 2)).isEqualTo(intOf(2));
    assertThat(DEFAULT_ADAPTER.nativeToValue(3)).isEqualTo(intOf(3));
    assertThat(DEFAULT_ADAPTER.nativeToValue(4L)).isEqualTo(intOf(4));
    assertThat(DEFAULT_ADAPTER.nativeToValue(5.25f)).isEqualTo(doubleOf(5.25));
    assertThat(DEFAULT_ADAPTER.nativeToValue(6.5d)).isEqualTo(doubleOf(6.5));
    assertThat(DEFAULT_ADAPTER.nativeToValue('x')).matches(Err::isError);
  }

  @Test
  void primitiveClassTokenDispatchesThroughPrimitiveOverride() {
    TypeAdapter adapter =
        new TypeAdapter() {
          @Override
          public Val nativeToValue(Object value) {
            return DEFAULT_ADAPTER.nativeToValue(value);
          }

          @Override
          public int valueToInt(Val value) {
            return 42;
          }
        };

    assertThat(adapter.valueToNative(intOf(1), int.class)).isEqualTo(42);
    assertThat(adapter.valueToNative(intOf(1), Integer.class)).isEqualTo(1);
  }

  @Test
  void primitiveOutboundConversionsMatchLegacyConversions() {
    assertThat(DEFAULT_ADAPTER.valueToBoolean(True)).isEqualTo(True.convertToNative(boolean.class));
    assertThat(DEFAULT_ADAPTER.valueToNative(False, boolean.class))
        .isEqualTo(False.convertToNative(boolean.class));

    assertThat(DEFAULT_ADAPTER.valueToInt(intOf(Integer.MIN_VALUE)))
        .isEqualTo(intOf(Integer.MIN_VALUE).convertToNative(int.class));
    assertThat(DEFAULT_ADAPTER.valueToNative(intOf(Integer.MAX_VALUE), int.class))
        .isEqualTo(intOf(Integer.MAX_VALUE).convertToNative(int.class));

    assertThat(DEFAULT_ADAPTER.valueToLong(intOf(Long.MIN_VALUE)))
        .isEqualTo(intOf(Long.MIN_VALUE).convertToNative(long.class));
    assertThat(DEFAULT_ADAPTER.valueToNative(uintOf(-1), long.class))
        .isEqualTo(uintOf(-1).convertToNative(long.class));

    assertThat(DEFAULT_ADAPTER.valueToDouble(doubleOf(Double.NEGATIVE_INFINITY)))
        .isEqualTo(doubleOf(Double.NEGATIVE_INFINITY).convertToNative(double.class));
    assertThat(DEFAULT_ADAPTER.valueToNative(doubleOf(Double.NaN), double.class)).isNaN();
  }

  @Test
  void primitiveOutboundConversionsPreserveRangeAndWrongTypeFailures() {
    assertThatThrownBy(() -> DEFAULT_ADAPTER.valueToInt(intOf((long) Integer.MAX_VALUE + 1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("range error converting 2147483648 to Java int");
    assertThatThrownBy(
            () -> DEFAULT_ADAPTER.valueToNative(intOf((long) Integer.MIN_VALUE - 1), int.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("range error converting -2147483649 to Java int");

    assertThatThrownBy(() -> DEFAULT_ADAPTER.valueToBoolean(intOf(1)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("native type conversion error from 'int' to 'boolean'");
    assertThatThrownBy(() -> DEFAULT_ADAPTER.valueToInt(doubleOf(1)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("native type conversion error from 'double' to 'int'");
    assertThatThrownBy(() -> DEFAULT_ADAPTER.valueToLong(True))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("native type conversion error from 'bool' to 'long'");
    assertThatThrownBy(() -> DEFAULT_ADAPTER.valueToDouble(intOf(1)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("native type conversion error from 'int' to 'double'");
  }

  @Test
  void customValUsesLegacyConversionFallback() {
    LegacyVal value = new LegacyVal();

    assertThat(DEFAULT_ADAPTER.valueToNative(value, String.class)).isEqualTo("legacy");
    assertThat(value.lastTargetType).isEqualTo(String.class);
    assertThat(DEFAULT_ADAPTER.valueToBoolean(value)).isTrue();
    assertThat(value.lastTargetType).isEqualTo(boolean.class);
  }

  private static final class TrackingAdapter implements TypeAdapter {
    private Object lastNativeValue;

    @Override
    public Val nativeToValue(Object value) {
      lastNativeValue = value;
      return NullT.NullValue;
    }
  }

  private static final class LegacyVal implements Val {
    private Class<?> lastTargetType;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convertToNative(Class<T> typeDesc) {
      lastTargetType = typeDesc;
      if (typeDesc == String.class) {
        return (T) "legacy";
      }
      if (typeDesc == boolean.class) {
        return (T) Boolean.TRUE;
      }
      throw new AssertionError("unexpected target type " + typeDesc);
    }

    @Override
    public Val convertToType(Type typeValue) {
      return this;
    }

    @Override
    public Val equal(Val other) {
      return other == this ? True : False;
    }

    @Override
    public Type type() {
      return StringType;
    }

    @Override
    public Object value() {
      return "legacy";
    }

    @Override
    public boolean booleanValue() {
      return true;
    }

    @Override
    public long intValue() {
      return 0;
    }

    @Override
    public double doubleValue() {
      return 0;
    }
  }
}
