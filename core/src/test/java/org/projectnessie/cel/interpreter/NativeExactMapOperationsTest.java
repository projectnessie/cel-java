/*
 * Copyright (C) 2026 The Authors of CEL-Java
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
package org.projectnessie.cel.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.CEL.astToCheckedExpr;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

class NativeExactMapOperationsTest {
  private final Env env =
      newEnv(
          customTypeAdapter(new ExactAdapter()),
          declarations(
              Decls.newVar("ints", Decls.newMapType(Decls.String, Decls.Int)),
              Decls.newVar("otherInts", Decls.newMapType(Decls.String, Decls.Int)),
              Decls.newVar("bools", Decls.newMapType(Decls.String, Decls.Bool)),
              Decls.newVar("uints", Decls.newMapType(Decls.String, Decls.Uint)),
              Decls.newVar("doubles", Decls.newMapType(Decls.String, Decls.Double)),
              Decls.newVar("texts", Decls.newMapType(Decls.String, Decls.String)),
              Decls.newVar("strings", Decls.newMapType(Decls.Bool, Decls.String)),
              Decls.newVar("nulls", Decls.newMapType(Decls.String, Decls.Null)),
              Decls.newVar("lists", Decls.newMapType(Decls.String, Decls.newListType(Decls.Int))),
              Decls.newVar(
                  "maps", Decls.newMapType(Decls.String, Decls.newMapType(Decls.Bool, Decls.Uint))),
              Decls.newVar("numeric", Decls.newMapType(Decls.Int, Decls.Int)),
              Decls.newVar("numericNulls", Decls.newMapType(Decls.Int, Decls.Null)),
              Decls.newVar("unsigned", Decls.newMapType(Decls.Uint, Decls.Int)),
              Decls.newVar("otherUnsigned", Decls.newMapType(Decls.Uint, Decls.Int)),
              Decls.newVar("key", Decls.String),
              Decls.newVar("boolKey", Decls.Bool),
              Decls.newVar("intKey", Decls.Int),
              Decls.newVar("suffix", Decls.String)));

  @Test
  void exactMapsSupportSizeStringAndBooleanLookupAndMembership() {
    Map<String, Object> input = input();

    assertEquivalent("size(ints)", input);
    assertEquivalent("ints['one']", input);
    assertEquivalent("'one' in ints", input);
    assertEquivalent("'missing' in ints", input);
    assertEquivalent("strings[true]", input);
    assertEquivalent("true in strings", input);
    assertEquivalent("uints['high']", input);
  }

  @Test
  void lookupDistinguishesPresentNullFromAbsent() {
    Map<String, Object> input = input();

    assertThat(assertEquivalent("nulls['present']", input))
        .isSameAs(org.projectnessie.cel.common.types.NullT.NullValue);
    assertThat(assertEquivalent("'present' in nulls", input).booleanValue()).isTrue();
    assertThat(assertEquivalent("'missing' in nulls", input).booleanValue()).isFalse();
    assertThat(assertEquivalent("nulls['missing']", input)).isInstanceOf(Err.class);
  }

  @Test
  void nestedExactListAndMapValuesRemainComposable() {
    Map<String, Object> input = input();

    assertEquivalent("size(lists['numbers'])", input);
    assertEquivalent("lists['numbers'][1]", input);
    assertEquivalent("size(maps['nested'])", input);
    assertEquivalent("maps['nested'][true]", input);
    assertEquivalent("size(lists['numbers'] + lists['numbers'])", input);
  }

  @Test
  void exactMapSourceResolvesOncePerRetainedOperation() {
    AtomicInteger resolutions = new AtomicInteger();
    ActivationFunction activation =
        name -> {
          if (name.equals("ints")) {
            resolutions.incrementAndGet();
            return Map.of("one", 1L);
          }
          return ActivationFunction.ABSENT;
        };

    assertThat(program("ints['one']", false).eval(activation).getVal().intValue()).isEqualTo(1L);
    assertThat(resolutions).hasValue(1);

    resolutions.set(0);
    assertThat(program("size(ints)", false).eval(activation).getVal().intValue()).isEqualTo(1L);
    assertThat(resolutions).hasValue(1);

    resolutions.set(0);
    assertThat(program("'one' in ints", false).eval(activation).getVal().booleanValue()).isTrue();
    assertThat(resolutions).hasValue(1);
  }

  @Test
  void checkedStringKeysUseTypedExactLookupForEveryScalarKind() {
    Map<String, Object> input = new LinkedHashMap<>(input());
    input.put("key", "one");
    input.put("suffix", "e");

    assertEquivalent("ints[key]", input);
    assertEquivalent("ints['on' + suffix]", input);
    assertEquivalent("bools[key]", input);
    assertEquivalent("uints[key]", input);
    assertEquivalent("doubles[key]", input);
    assertEquivalent("texts[key]", input);

    input.put("key", "present");
    assertThat(assertEquivalent("nulls[key]", input))
        .isSameAs(org.projectnessie.cel.common.types.NullT.NullValue);
    input.put("key", "missing");
    assertThat(assertEquivalent("ints[key]", input)).isInstanceOf(Err.class);
    input.put("key", "nan");
    assertThat(assertEquivalent("doubles[key]", input).doubleValue()).isNaN();
    input.put("key", "positiveZero");
    assertThat(Double.doubleToRawLongBits(assertEquivalent("doubles[key]", input).doubleValue()))
        .isEqualTo(Double.doubleToRawLongBits(0.0d));
  }

  @Test
  void dynamicLookupResolvesSourceBeforeKeyAndEvaluatesEachOnce() {
    AtomicInteger sources = new AtomicInteger();
    AtomicInteger keys = new AtomicInteger();
    Program program = program("ints[key]", false);

    ActivationFunction success =
        name -> {
          if (name.equals("ints")) {
            sources.incrementAndGet();
            return Map.of("one", 1L);
          }
          if (name.equals("key")) {
            keys.incrementAndGet();
            return "one";
          }
          return ActivationFunction.ABSENT;
        };
    assertThat(program.eval(success).getVal().intValue()).isEqualTo(1L);
    assertThat(sources).hasValue(1);
    assertThat(keys).hasValue(1);

    sources.set(0);
    keys.set(0);
    ActivationFunction failedSource =
        name -> {
          if (name.equals("ints")) {
            sources.incrementAndGet();
            return newErr("source failed");
          }
          if (name.equals("key")) {
            keys.incrementAndGet();
            return "one";
          }
          return ActivationFunction.ABSENT;
        };
    assertThat(program.eval(failedSource).getVal()).isInstanceOf(Err.class);
    assertThat(sources).hasValue(1);
    assertThat(keys).hasValue(0);

    sources.set(0);
    keys.set(0);
    ActivationFunction exceptionalSource =
        name -> {
          if (name.equals("ints")) {
            sources.incrementAndGet();
            throw new IllegalStateException("source failed");
          }
          if (name.equals("key")) {
            keys.incrementAndGet();
            return "one";
          }
          return ActivationFunction.ABSENT;
        };
    assertThat(program.eval(exceptionalSource).getVal())
        .isInstanceOf(Err.class)
        .hasToString("java.lang.IllegalStateException: source failed");
    assertThat(sources).hasValue(1);
    assertThat(keys).hasValue(0);

    sources.set(0);
    keys.set(0);
    ActivationFunction exceptionalKey =
        name -> {
          if (name.equals("ints")) {
            sources.incrementAndGet();
            return Map.of("one", 1L);
          }
          if (name.equals("key")) {
            keys.incrementAndGet();
            throw new IllegalStateException("key failed");
          }
          return ActivationFunction.ABSENT;
        };
    assertThat(program.eval(exceptionalKey).getVal())
        .isInstanceOf(Err.class)
        .hasToString("java.lang.IllegalStateException: key failed");
    assertThat(sources).hasValue(1);
    assertThat(keys).hasValue(1);
  }

  @Test
  void dynamicLookupDoesNotMaterializeMapAfterIncompatibleSelection() {
    AtomicInteger gets = new AtomicInteger();
    AtomicInteger presenceChecks = new AtomicInteger();
    AtomicInteger traversals = new AtomicInteger();
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    Map<String, Object> incompatible =
        new AbstractMap<>() {
          @Override
          public Object get(Object key) {
            gets.incrementAndGet();
            return "not an integer";
          }

          @Override
          public boolean containsKey(Object key) {
            presenceChecks.incrementAndGet();
            return true;
          }

          @Override
          public java.util.Set<Entry<String, Object>> entrySet() {
            traversals.incrementAndGet();
            throw new AssertionError("must not materialize");
          }
        };

    assertThat(
            program("ints[key]", false).eval(Map.of("ints", incompatible, "key", "one")).getVal())
        .isInstanceOf(Err.class);
    assertThat(gets).hasValue(1);
    assertThat(presenceChecks).hasValue(0);
    assertThat(traversals).hasValue(0);
  }

  @Test
  void dynamicLookupSuppressesKeyForUnknownNullAndAbsentSources() {
    Program program = program("ints[key]", false);
    AtomicInteger keys = new AtomicInteger();
    Val unknown = unknownOf(92L);

    ActivationFunction unknownSource =
        name -> {
          if (name.equals("ints")) {
            return unknown;
          }
          if (name.equals("key")) {
            keys.incrementAndGet();
            return "one";
          }
          return ActivationFunction.ABSENT;
        };
    assertThat(program.eval(unknownSource).getVal()).isSameAs(unknown);
    assertThat(keys).hasValue(0);

    for (Object source : new Object[] {null, ActivationFunction.ABSENT}) {
      keys.set(0);
      ActivationFunction invalidSource =
          name -> {
            if (name.equals("ints")) {
              return source;
            }
            if (name.equals("key")) {
              keys.incrementAndGet();
              return "one";
            }
            return ActivationFunction.ABSENT;
          };
      assertThat(program.eval(invalidSource).getVal()).isInstanceOf(Err.class);
      assertThat(keys).hasValue(0);
    }
  }

  @Test
  void dynamicLookupRejectsIncompatibleKeysAndPropagatesKeyFailures() {
    Program enabled = program("ints[key]", false);
    Program disabled = program("ints[key]", true);

    for (Object key : List.of(1L, true)) {
      Map<String, Object> input = Map.of("ints", Map.of("one", 1L), "key", key);
      assertThat(enabled.eval(input).getVal()).isInstanceOf(Err.class);
      assertThat(disabled.eval(input).getVal()).isInstanceOf(Err.class);
    }

    ActivationFunction nullKey =
        name -> {
          if (name.equals("ints")) {
            return Map.of("one", 1L);
          }
          if (name.equals("key")) {
            return null;
          }
          return ActivationFunction.ABSENT;
        };
    assertThat(enabled.eval(nullKey).getVal()).isInstanceOf(Err.class);
    assertThat(disabled.eval(nullKey).getVal()).isInstanceOf(Err.class);

    Val keyError = newErr("key error");
    Val keyUnknown = unknownOf(91L);
    for (Val keyFailure : List.of(keyError, keyUnknown)) {
      ActivationFunction activation =
          name -> {
            if (name.equals("ints")) {
              return Map.of("one", 1L);
            }
            if (name.equals("key")) {
              return keyFailure;
            }
            return ActivationFunction.ABSENT;
          };
      assertThat(enabled.eval(activation).getVal()).isSameAs(keyFailure);
      assertThat(disabled.eval(activation).getVal()).isInstanceOf(keyFailure.getClass());
    }
  }

  @Test
  void constantSignedIntegerKeysUseExactLookupAndMembership() {
    Map<String, Object> input = input();

    for (String expression :
        List.of(
            "numeric[1]",
            "numeric[-1]",
            "numeric[9223372036854775807]",
            "1 in numeric",
            "2 in numeric",
            "-1 in numeric",
            "9223372036854775807 in numeric")) {
      assertEquivalent(expression, input);
    }

    assertThat(assertEquivalent("numericNulls[1]", input))
        .isSameAs(org.projectnessie.cel.common.types.NullT.NullValue);
    assertThat(assertEquivalent("1 in numericNulls", input).booleanValue()).isTrue();
    assertThat(assertEquivalent("2 in numericNulls", input).booleanValue()).isFalse();
  }

  @Test
  void signedIntegerKeysAcceptEveryCheckedJavaWrapperWithoutLinearTraversal() {
    for (Object hostKey : List.of((byte) 1, (short) 1, 1, 1L)) {
      Map<String, Object> input = new LinkedHashMap<>(input());
      input.put("numeric", Map.of(hostKey, 11L));
      input.put("intKey", 1L);

      assertThat(assertEquivalent("numeric[1]", input).intValue()).isEqualTo(11L);
      assertThat(assertEquivalent("1 in numeric", input).booleanValue()).isTrue();
      assertThat(assertEquivalent("numeric[intKey]", input).intValue()).isEqualTo(11L);
    }

    Map<String, Object> sortedInput = new LinkedHashMap<>(input());
    TreeMap<Integer, Long> sorted = new TreeMap<>();
    sorted.put(1, 11L);
    sortedInput.put("numeric", sorted);
    sortedInput.put("intKey", 1L);
    assertThat(assertEquivalent("numeric[1]", sortedInput).intValue()).isEqualTo(11L);
    assertThat(assertEquivalent("numeric[intKey]", sortedInput).intValue()).isEqualTo(11L);

    sortedInput.put("intKey", Long.MAX_VALUE);
    assertThat(assertEquivalent("numeric[9223372036854775807]", sortedInput))
        .isInstanceOf(Err.class);
    assertThat(assertEquivalent("9223372036854775807 in numeric", sortedInput).booleanValue())
        .isFalse();
    assertThat(assertEquivalent("numeric[intKey]", sortedInput)).isInstanceOf(Err.class);

    TreeMap<Short, Long> narrow = new TreeMap<>();
    narrow.put((short) 1, 11L);
    sortedInput.put("numeric", narrow);
    sortedInput.put("intKey", 32768L);
    assertThat(assertEquivalent("numeric[32768]", sortedInput)).isInstanceOf(Err.class);
    assertThat(assertEquivalent("32768 in numeric", sortedInput).booleanValue()).isFalse();
    assertThat(assertEquivalent("numeric[intKey]", sortedInput)).isInstanceOf(Err.class);
  }

  @Test
  void comparatorMapWrapperAliasesDoNotLookLikeRepeatedCelKeys() {
    TreeMap<Number, Long> aliases = new TreeMap<>(Comparator.comparingLong(Number::longValue));
    aliases.put(1L, 11L);
    Map<String, Object> input = new LinkedHashMap<>(input());
    input.put("numeric", aliases);
    input.put("intKey", 1L);

    assertThat(assertEquivalent("numeric[1]", input).intValue()).isEqualTo(11L);
    assertThat(assertEquivalent("1 in numeric", input).booleanValue()).isTrue();
    assertThat(assertEquivalent("numeric[intKey]", input).intValue()).isEqualTo(11L);
  }

  @Test
  void classCastFailuresAreNotMisreportedAsMissingExactKeys() {
    Map<Object, Object> getFailure =
        new AbstractMap<>() {
          @Override
          public Object get(Object key) {
            throw new ClassCastException("lookup rejected");
          }

          @Override
          public java.util.Set<Entry<Object, Object>> entrySet() {
            return Collections.emptySet();
          }
        };
    Map<Object, Object> presenceFailure =
        new AbstractMap<>() {
          @Override
          public Object get(Object key) {
            return null;
          }

          @Override
          public boolean containsKey(Object key) {
            throw new ClassCastException("lookup rejected");
          }

          @Override
          public java.util.Set<Entry<Object, Object>> entrySet() {
            return Collections.emptySet();
          }
        };

    for (Map<Object, Object> lookupFailure : List.of(getFailure, presenceFailure)) {
      Map<String, Object> input = new LinkedHashMap<>(input());
      input.put("ints", lookupFailure);
      input.put("numeric", lookupFailure);
      input.put("key", "one");
      input.put("intKey", 1L);

      for (String expression : List.of("ints['one']", "'one' in ints", "ints[key]")) {
        assertThat(program(expression, false).eval(input).getVal())
            .as(expression)
            .isInstanceOf(Err.class)
            .asString()
            .contains("java.lang.ClassCastException: lookup rejected")
            .doesNotContain("no such key");
      }
      assertThat(assertEquivalent("numeric[1]", input)).isInstanceOf(Err.class);
      assertThat(assertEquivalent("1 in numeric", input).booleanValue()).isFalse();
      assertThat(assertEquivalent("numeric[intKey]", input)).isInstanceOf(Err.class);
    }
  }

  @Test
  void signedIntegerLookupRejectsCheaplyDetectedCelEquivalentDuplicates() {
    Map<Object, Object> duplicate = new LinkedHashMap<>();
    duplicate.put((byte) 1, 10L);
    duplicate.put(1L, 11L);
    Map<String, Object> input = new LinkedHashMap<>(input());
    input.put("numeric", duplicate);
    input.put("intKey", 1L);

    for (String expression : List.of("numeric[1]", "1 in numeric", "numeric[intKey]")) {
      assertThat(assertEquivalent(expression, input))
          .as(expression)
          .isInstanceOf(Err.class)
          .hasToString(
              expression.contains(" in ")
                  ? "Failed with repeated key"
                  : "message: Failed with repeated key");
    }

    Map<Object, Object> duplicateNull = new LinkedHashMap<>();
    duplicateNull.put((byte) 1, null);
    duplicateNull.put(1L, null);
    input.put("numericNulls", duplicateNull);
    for (String expression : List.of("numericNulls[1]", "1 in numericNulls")) {
      assertThat(assertEquivalent(expression, input))
          .as(expression)
          .isInstanceOf(Err.class)
          .hasToString(
              expression.contains(" in ")
                  ? "Failed with repeated key"
                  : "message: Failed with repeated key");
    }
  }

  @Test
  void checkedBooleanAndSignedIntegerKeysUseTypedExactLookup() {
    Map<String, Object> input = new LinkedHashMap<>(input());
    input.put("boolKey", true);
    input.put("intKey", -1L);

    assertThat(assertEquivalent("strings[boolKey]", input).value()).isEqualTo("yes");
    assertThat(assertEquivalent("numeric[intKey]", input).intValue()).isEqualTo(-2L);

    input.put("intKey", Long.MIN_VALUE);
    assertThat(assertEquivalent("numeric[intKey]", input).intValue()).isEqualTo(-1L);
    input.put("intKey", Long.MAX_VALUE);
    assertThat(assertEquivalent("numeric[intKey]", input).intValue()).isEqualTo(1L);
  }

  @Test
  void signedIntegerAndDynamicScalarKeysHaveBoundedNativePlanShapes() {
    for (String expression : List.of("numeric[1]", "numeric[-1]")) {
      Plans plans = plans(expression, true);
      assertThat(plans.enabled()).as(expression).isInstanceOf(NativeIsland.class);
      assertThat(((NativeIsland) plans.enabled()).root())
          .as(expression)
          .isInstanceOf(NativeIntMapIndex.class);
      assertThat(plans.established()).as(expression).isNotInstanceOf(NativeIsland.class);
    }

    Plans membership = plans("1 in numeric", true);
    assertThat(((NativeIsland) membership.enabled()).root())
        .isInstanceOf(NativeMapMembership.class);

    Plans integer = plans("numeric[intKey + 0]", true);
    NativeMapIndex integerRoot = (NativeMapIndex) ((NativeIsland) integer.enabled()).root();
    assertThat(integerRoot.dynamicKey.capability()).isInstanceOf(NativeIntCapability.class);
    assertThat(integerRoot.dynamicKey.celName()).isEqualTo("int");

    Plans bool = plans("strings[!boolKey]", true);
    NativeMapIndex boolRoot = (NativeMapIndex) ((NativeIsland) bool.enabled()).root();
    assertThat(boolRoot.dynamicKey.capability()).isInstanceOf(NativeBooleanCapability.class);
    assertThat(boolRoot.dynamicKey.celName()).isEqualTo("bool");

    assertThat(plans("numeric[intKey]", false).enabled()).isNotInstanceOf(NativeIsland.class);
  }

  @Test
  void dynamicSignedIntegerLookupResolvesSourceBeforeKeyExactlyOnce() {
    AtomicInteger sources = new AtomicInteger();
    AtomicInteger keys = new AtomicInteger();
    Program program = program("numeric[intKey]", false);

    ActivationFunction success =
        name -> {
          if (name.equals("numeric")) {
            sources.incrementAndGet();
            return Map.of(1, 11L);
          }
          if (name.equals("intKey")) {
            keys.incrementAndGet();
            return 1L;
          }
          return ActivationFunction.ABSENT;
        };
    assertThat(program.eval(success).getVal().intValue()).isEqualTo(11L);
    assertThat(sources).hasValue(1);
    assertThat(keys).hasValue(1);

    sources.set(0);
    keys.set(0);
    ActivationFunction failedSource =
        name -> {
          if (name.equals("numeric")) {
            sources.incrementAndGet();
            return newErr("source failed");
          }
          if (name.equals("intKey")) {
            keys.incrementAndGet();
            return 1L;
          }
          return ActivationFunction.ABSENT;
        };
    assertThat(program.eval(failedSource).getVal()).isInstanceOf(Err.class);
    assertThat(sources).hasValue(1);
    assertThat(keys).hasValue(0);
  }

  @Test
  void dynamicBooleanAndSignedIntegerKeysRejectIncompatibleActivationValues() {
    for (Map<String, Object> input :
        List.of(
            Map.of("strings", Map.of(true, "yes"), "boolKey", "true"),
            Map.of("numeric", Map.of(1L, 11L), "intKey", true))) {
      String expression = input.containsKey("strings") ? "strings[boolKey]" : "numeric[intKey]";
      Val nativeValue = program(expression, false).eval(input).getVal();
      Val disabledValue = program(expression, true).eval(input).getVal();
      assertThat(nativeValue).as(expression).isInstanceOf(Err.class);
      assertThat(disabledValue).as(expression).isInstanceOf(Err.class);
    }
  }

  @Test
  void mapEqualityRemainsEstablishedCompatible() {
    Map<String, Object> input = input();

    assertEquivalent("ints == otherInts", input);
    assertEquivalent("ints != otherInts", input);
    assertEquivalent("maps == maps", input);

    for (String expression : List.of("ints == otherInts", "ints != otherInts", "maps == maps")) {
      Plans plans = plans(expression, true);
      assertThat(plans.enabled()).as(expression).isInstanceOf(NativeIsland.class);
      assertThat(((NativeIsland) plans.enabled()).root())
          .as(expression)
          .isInstanceOfAny(NativeExactMapEquality.class, NativeExactMapInequality.class);
      assertThat(plans.established()).as(expression).isNotInstanceOf(NativeIsland.class);
    }
  }

  @Test
  void exactMapEqualityCoversSizeMissingValueAndUintRepresentations() {
    Map<String, Object> input = new LinkedHashMap<>(input());

    input.put("otherInts", Map.of("one", 1L));
    assertThat(assertEquivalent("ints == otherInts", input).booleanValue()).isFalse();

    input.put("otherInts", Map.of("one", 1L, "missing", 2L));
    assertThat(assertEquivalent("ints == otherInts", input).booleanValue()).isFalse();

    input.put("otherInts", Map.of("one", 1L, "two", 3L));
    assertThat(assertEquivalent("ints == otherInts", input).booleanValue()).isFalse();

    input.put("unsigned", Map.of(ULong.valueOf(Long.MIN_VALUE), 1L, ULong.valueOf(-1L), 2L));
    input.put("otherUnsigned", Map.of(Long.MIN_VALUE, 1L, -1L, 2L));
    assertThat(assertEquivalent("unsigned == otherUnsigned", input).booleanValue()).isTrue();
  }

  @Test
  void exactMapEqualityEvaluatesBothOperandsOnce() {
    AtomicInteger leftResolutions = new AtomicInteger();
    AtomicInteger rightResolutions = new AtomicInteger();
    ActivationFunction activation =
        name -> {
          if (name.equals("ints")) {
            leftResolutions.incrementAndGet();
            return newErr("left failed");
          }
          if (name.equals("otherInts")) {
            rightResolutions.incrementAndGet();
            return Map.of("one", 1L);
          }
          return ActivationFunction.ABSENT;
        };

    assertThat(program("ints == otherInts", false).eval(activation).getVal())
        .isInstanceOf(Err.class);
    assertThat(leftResolutions).hasValue(1);
    assertThat(rightResolutions).hasValue(1);
  }

  @Test
  void exactMapKeyQuantifiersTraverseWithoutMaterializingTheMap() {
    Map<String, Object> input = input();

    assertThat(assertEquivalent("ints.exists(key, key == 'one')", input).booleanValue()).isTrue();
    assertThat(assertEquivalent("ints.exists(key, key == 'missing')", input).booleanValue())
        .isFalse();
    assertThat(assertEquivalent("ints.all(key, key != 'missing')", input).booleanValue()).isTrue();
    assertThat(assertEquivalent("ints.exists_one(key, key == 'one')", input).booleanValue())
        .isTrue();

    for (String expression :
        List.of(
            "ints.exists(key, key == 'one')",
            "ints.all(key, key != 'missing')",
            "ints.exists_one(key, key == 'one')")) {
      Plans plans = plans(expression, true);
      assertThat(plans.enabled()).as(expression).isInstanceOf(NativeIsland.class);
      assertThat(((NativeIsland) plans.enabled()).root())
          .as(expression)
          .isInstanceOf(NativeMapQuantifierFold.class);
      assertThat(plans.established()).as(expression).isNotInstanceOf(NativeIsland.class);
    }
  }

  @Test
  void exactMapEntryQuantifiersBindKeyAndValueByName() {
    Map<String, Object> input = input();

    assertThat(
            assertEquivalent("ints.exists(key, value, key == 'one' && value == 1)", input)
                .booleanValue())
        .isTrue();
    assertThat(
            assertEquivalent("ints.all(key, value, value > 0 && key != 'missing')", input)
                .booleanValue())
        .isTrue();
    assertThat(assertEquivalent("ints.exists_one(key, value, value == 2)", input).booleanValue())
        .isTrue();
    assertThat(assertEquivalent("nulls.exists(key, value, value == null)", input).booleanValue())
        .isTrue();
  }

  @Test
  void exactMapQuantifierSourceResolvesOnce() {
    AtomicInteger resolutions = new AtomicInteger();
    ActivationFunction activation =
        name -> {
          if (name.equals("ints")) {
            resolutions.incrementAndGet();
            return Map.of("one", 1L, "two", 2L);
          }
          return ActivationFunction.ABSENT;
        };

    assertThat(
            program("ints.exists(key, value, key == 'two' && value == 2)", false)
                .eval(activation)
                .getVal()
                .booleanValue())
        .isTrue();
    assertThat(resolutions).hasValue(1);
  }

  @Test
  void successfulEmbeddedValuesAreRejectedByTheExactContract() {
    assertThat(program("ints['one']", false).eval(Map.of("ints", Map.of("one", intOf(1)))).getVal())
        .isInstanceOf(Err.class);
    assertThat(program("ints['one']", true).eval(Map.of("ints", Map.of("one", intOf(1)))).getVal())
        .isInstanceOf(Err.class);

    Val embeddedList = DefaultTypeAdapter.Instance.nativeToValue(List.of(1L));
    assertThat(
            program("lists['numbers'][0]", false)
                .eval(Map.of("lists", Map.of("numbers", embeddedList)))
                .getVal())
        .isInstanceOf(Err.class);
  }

  @Test
  void materializationAndHostLookupFailuresRemainCelErrors() {
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    Map<String, Object> throwing =
        new AbstractMap<>() {
          @Override
          public Object get(Object key) {
            return "not an integer";
          }

          @Override
          public boolean containsKey(Object key) {
            return true;
          }

          @Override
          public java.util.Set<Entry<String, Object>> entrySet() {
            throw new IllegalStateException("entry traversal failed");
          }
        };

    assertThat(program("ints['one']", false).eval(Map.of("ints", throwing)).getVal())
        .isInstanceOf(Err.class)
        .hasToString("java.lang.IllegalStateException: entry traversal failed");

    Map<String, Object> lookupFailure =
        new AbstractMap<>() {
          @Override
          public Object get(Object key) {
            throw new IllegalStateException("lookup failed");
          }

          @Override
          public java.util.Set<Entry<String, Object>> entrySet() {
            return Collections.emptySet();
          }
        };
    assertThat(
            program("ints[key]", false).eval(Map.of("ints", lookupFailure, "key", "one")).getVal())
        .isInstanceOf(Err.class)
        .hasToString("java.lang.IllegalStateException: lookup failed");

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    Map<String, Object> sizeFailure =
        new AbstractMap<>() {
          @Override
          public java.util.Set<Entry<String, Object>> entrySet() {
            return Collections.emptySet();
          }

          @Override
          public int size() {
            throw new IllegalStateException("size failed");
          }
        };
    assertThat(program("size(ints)", false).eval(Map.of("ints", sizeFailure)).getVal())
        .isInstanceOf(Err.class)
        .hasToString("java.lang.IllegalStateException: size failed");
  }

  private static Map<String, Object> input() {
    Map<String, Object> nulls = new LinkedHashMap<>();
    nulls.put("present", null);
    Map<Long, Object> numericNulls = new LinkedHashMap<>();
    numericNulls.put(1L, null);
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("ints", Map.of("one", 1L, "two", 2L));
    input.put("otherInts", Map.of("one", 1L, "two", 2L));
    input.put("bools", Map.of("one", true));
    input.put("uints", Map.of("high", -1L, "one", -1L));
    input.put("doubles", Map.of("one", -0.0d, "nan", Double.NaN, "positiveZero", 0.0d));
    input.put("texts", Map.of("one", "value"));
    input.put("strings", Map.of(false, "no", true, "yes"));
    input.put("nulls", nulls);
    input.put("lists", Map.of("numbers", List.of(10L, 20L, 30L)));
    input.put("maps", Map.of("nested", Map.of(true, ULong.valueOf(-1L))));
    input.put("numeric", Map.of(Long.MIN_VALUE, -1L, -1L, -2L, 1L, 11L, Long.MAX_VALUE, 1L));
    input.put("numericNulls", numericNulls);
    input.put("unsigned", Map.of(ULong.valueOf(-1L), 1L));
    input.put("otherUnsigned", Map.of(-1L, 1L));
    return input;
  }

  private Val assertEquivalent(String expression, Object input) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    Program nativeProgram = env.program(compiled.getAst());
    Program establishedProgram = env.program(compiled.getAst(), evalOptions(OptDisableNativeEval));
    Val nativeValue = nativeProgram.eval(input).getVal();
    Val establishedValue = establishedProgram.eval(input).getVal();

    assertThat(nativeValue.getClass()).as(expression).isEqualTo(establishedValue.getClass());
    assertThat(nativeValue.type()).as(expression).isEqualTo(establishedValue.type());
    assertThat(nativeValue.toString()).as(expression).isEqualTo(establishedValue.toString());
    if (!(nativeValue instanceof Err)) {
      assertThat(nativeValue.value()).isEqualTo(establishedValue.value());
    }
    return nativeValue;
  }

  private Program program(String expression, boolean disableNative) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    return disableNative
        ? env.program(compiled.getAst(), evalOptions(OptDisableNativeEval))
        : env.program(compiled.getAst());
  }

  private Plans plans(String expression, boolean exactAggregateAdapter) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    var checked = astToCheckedExpr(compiled.getAst());
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    TypeAdapter adapter =
        exactAggregateAdapter ? env.getTypeAdapter() : DefaultTypeAdapter.Instance;
    AttributeFactory attributes =
        newAttributeFactory(defaultContainer, adapter, env.getTypeProvider());
    Interpreter enabledInterpreter =
        newInterpreter(
            dispatcher, defaultContainer, env.getTypeProvider(), adapter, attributes, true);
    Interpreter establishedInterpreter =
        newInterpreter(
            dispatcher, defaultContainer, env.getTypeProvider(), adapter, attributes, false);
    return new Plans(
        enabledInterpreter.newInterpretable(checked),
        establishedInterpreter.newInterpretable(checked));
  }

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }

  private record Plans(Interpretable enabled, Interpretable established) {}
}
