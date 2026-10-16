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
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;

import com.google.protobuf.Any;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.IntT;
import org.projectnessie.cel.common.types.ListT;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.traits.Indexer;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.extension.StringsLib;

@SuppressWarnings("removal")
class TypeAdapterTest {

  private static final TypeAdapter DEFAULT_ADAPTER = DefaultTypeAdapter.Instance;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void nestedMapsUseCallingAdapterForJavaAndProtobufExports(boolean dynamic) throws Exception {
    TypeAdapter adapter =
        new TypeAdapter() {
          @Override
          public Val nativeToValue(Object value) {
            return DEFAULT_ADAPTER.nativeToValue(value);
          }

          @Override
          public <T> T valueToNative(Val value, Class<T> targetType) {
            if (value instanceof StringT) {
              value = stringOf(((String) value.value()).toUpperCase(Locale.ROOT));
            } else if (value instanceof IntT) {
              value = intOf(value.intValue() + 100);
            }
            return TypeAdapter.super.valueToNative(value, targetType);
          }
        };
    Map<String, Object> raw = Map.of("items", List.of(1L, Map.of("leaf", 2L)));
    Val value = adapter.nativeToValue(raw);
    Map<String, Object> expectedJava = Map.of("ITEMS", List.of(101L, Map.of("LEAF", 102L)));
    assertThat((Object) adapter.valueToNative(value, Map.class)).isEqualTo(expectedJava);
    assertThat(adapter.valueToNative(value, Object.class)).isEqualTo(expectedJava);
    assertThat(value.value()).isEqualTo(raw);
    Struct expectedProto =
        Struct.newBuilder()
            .putFields(
                "ITEMS",
                Value.newBuilder()
                    .setListValue(
                        ListValue.newBuilder()
                            .addValues(Value.newBuilder().setNumberValue(101))
                            .addValues(
                                Value.newBuilder()
                                    .setStructValue(
                                        Struct.newBuilder()
                                            .putFields(
                                                "LEAF",
                                                Value.newBuilder().setNumberValue(102).build()))))
                    .build())
            .build();
    assertThat(adapter.valueToNative(value, Struct.class)).isEqualTo(expectedProto);
    assertThat(adapter.valueToNative(value, Value.class))
        .isEqualTo(Value.newBuilder().setStructValue(expectedProto).build());
    assertThat(adapter.valueToNative(value, Any.class).unpack(Struct.class))
        .isEqualTo(expectedProto);
    Val numericKeys = adapter.nativeToValue(Map.of(1L, 2L));
    assertThat((Object) adapter.valueToNative(numericKeys, Map.class))
        .isEqualTo(Map.of(101L, 102L));

    TestAllTypes generated = TestAllTypes.newBuilder().putMapBoolInt64(true, 1L).build();
    Message message =
        dynamic
            ? DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), generated.toByteString())
            : generated;
    ProtoTypeRegistry registry = ProtoTypeRegistry.newRegistry(message);
    Val protoMap = ((Indexer) registry.nativeToValue(message)).get(stringOf("map_bool_int64"));
    assertThat((Object) adapter.valueToNative(protoMap, Map.class)).isEqualTo(Map.of(true, 101L));
  }

  @ParameterizedTest
  @ValueSource(strings = {"boxed", "objects", "list", "primitive"})
  void concatenationDoesNotInvokeOutboundHooks(String source) {
    AtomicInteger conversions = new AtomicInteger();
    TypeAdapter adapter =
        new TypeAdapter() {
          @Override
          public Val nativeToValue(Object value) {
            return value instanceof Val val
                ? val
                : TypeAdapterSupport.maybeNativeToValue(this, value);
          }

          @Override
          public <T> T valueToNative(Val value, Class<T> targetType) {
            if (value instanceof IntT) {
              conversions.incrementAndGet();
              value = intOf(value.intValue() + 100);
            }
            return TypeAdapter.super.valueToNative(value, targetType);
          }
        };
    Object input =
        switch (source) {
          case "boxed" -> new Long[] {1L};
          case "objects" -> new Object[] {1L};
          case "list" -> List.of(1L);
          case "primitive" -> new long[] {1L};
          default -> throw new AssertionError(source);
        };
    Env env =
        Env.newEnv(
            EnvOption.customTypeAdapter(adapter),
            EnvOption.declarations(Decls.newVar("xs", Decls.newListType(Decls.Int))));
    Val result =
        env.program(env.compile("(xs + [2])[1] == 2").getAst()).eval(Map.of("xs", input)).getVal();
    assertThat(result).isSameAs(True);
    Val concatenated =
        env.program(env.compile("xs + [2]").getAst()).eval(Map.of("xs", input)).getVal();
    assertThat(((Lister) concatenated).get(intOf(0))).isEqualTo(intOf(1));
    assertThat(((Lister) concatenated).get(intOf(1))).isEqualTo(intOf(2));
    assertThat(conversions).hasValue(0);
    assertThat(adapter.valueToNative(concatenated, Long[].class)).containsExactly(101L, 102L);
    assertThat(conversions).hasValue(2);
  }

  @Test
  void typedArrayConcatenationPreservesCelElementTypes() {
    Val[] suffix = {uintOf(2), NullT.NullValue, StringType, Err.newErr("element error")};
    Lister left = (Lister) DEFAULT_ADAPTER.nativeToValue(new Long[] {1L});
    Lister combined = (Lister) left.add(ListT.newValArrayList(DEFAULT_ADAPTER, suffix));
    assertThat(combined.get(intOf(0))).isEqualTo(intOf(1));
    for (int i = 0; i < suffix.length; i++) {
      assertThat(combined.get(intOf(i + 1))).isSameAs(suffix[i]);
    }
    Lister unsigned =
        (Lister) left.add(ListT.newValArrayList(DEFAULT_ADAPTER, new Val[] {uintOf(-1L)}));
    Val repeated = unsigned.add(left);
    assertThat((Object[]) repeated.value()).containsExactly(1L, ULong.valueOf(-1L), 1L);
    assertThat(DEFAULT_ADAPTER.valueToNative(repeated, Object[].class))
        .containsExactly(1L, -1L, 1L);
    assertThat((Object[]) ListT.newValArrayList(DEFAULT_ADAPTER, new Val[] {uintOf(2)}).value())
        .containsExactly(2L);
  }

  @Test
  void stringArraysPreserveLegacyNullElementAdaptation() {
    Val list = DEFAULT_ADAPTER.nativeToValue(new String[] {null});
    Val element = ((ListT) list).get(intOf(0));

    assertThat(element).isInstanceOf(StringT.class);
    assertThat(element.value()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "array",
        "list",
        "factory",
        "['a', 'b']",
        "'a,b'.split(',')",
        "'a,b'.split(',', 2)"
      })
  void stringListsUseConfiguredOutboundAdapter(String source) throws Exception {
    TypeAdapter adapter =
        new TypeAdapter() {
          @Override
          public Val nativeToValue(Object value) {
            return TypeAdapterSupport.maybeNativeToValue(this, value);
          }

          @Override
          public <T> T valueToNative(Val value, Class<T> targetType) {
            if (value instanceof StringT) {
              return DEFAULT_ADAPTER.valueToNative(
                  stringOf(((String) value.value()).toUpperCase(Locale.ROOT)), targetType);
            }
            return TypeAdapter.super.valueToNative(value, targetType);
          }
        };
    Env env = Env.newEnv(EnvOption.customTypeAdapter(adapter), StringsLib.strings());
    Val value =
        switch (source) {
          case "array" -> adapter.nativeToValue(new String[] {"a", "b"});
          case "list" -> adapter.nativeToValue(List.of("a", "b"));
          case "factory" -> ListT.newStringArrayList(new String[] {"a", "b"});
          default -> env.program(env.compile(source).getAst()).eval(java.util.Map.of()).getVal();
        };
    assertThat(adapter.valueToNative(value, String[].class)).containsExactly("A", "B");
    assertThat((Object) adapter.valueToNative(value, List.class)).isEqualTo(List.of("A", "B"));
    assertThat(adapter.valueToNative(value, Object.class)).isEqualTo(List.of("A", "B"));
    ListValue expected =
        ListValue.newBuilder()
            .addValues(Value.newBuilder().setStringValue("A"))
            .addValues(Value.newBuilder().setStringValue("B"))
            .build();
    assertThat(adapter.valueToNative(value, ListValue.class)).isEqualTo(expected);
    assertThat(adapter.valueToNative(value, Value.class))
        .isEqualTo(Value.newBuilder().setListValue(expected).build());
    assertThat(adapter.valueToNative(value, Any.class).unpack(ListValue.class)).isEqualTo(expected);
  }

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
  void unknownLongConversionUsesTheUnknownValueContract() {
    assertThat(DEFAULT_ADAPTER.valueToLong(unknownOf(42L))).isEqualTo(42L);
    assertThat(DEFAULT_ADAPTER.valueToNative(unknownOf(42L), long.class)).isEqualTo(42L);
    assertThatThrownBy(() -> DEFAULT_ADAPTER.valueToLong(unknownOf(1L, 2L)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("native type conversion error from 'unknown' to 'long'");
    assertThatThrownBy(() -> DEFAULT_ADAPTER.valueToNative(unknownOf(1L, 2L), long.class))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("native type conversion error from 'unknown' to 'long'");
  }

  @Test
  void unknownArrayConversionsThroughAdapterAreCompleteAndDefensive() {
    Val unknown = unknownOf(3L, 1L, 2L);

    long[] array = DEFAULT_ADAPTER.valueToNative(unknown, long[].class);
    long[] object = (long[]) DEFAULT_ADAPTER.valueToNative(unknown, Object.class);
    assertThat(array).containsExactly(1L, 2L, 3L);
    assertThat(object).containsExactly(1L, 2L, 3L);

    array[0] = 99L;
    object[0] = 98L;
    assertThat(DEFAULT_ADAPTER.valueToNative(unknown, long[].class)).containsExactly(1L, 2L, 3L);
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
