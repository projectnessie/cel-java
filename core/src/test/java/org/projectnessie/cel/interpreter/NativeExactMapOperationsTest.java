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
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.projectnessie.cel.common.types.ref.Val;

class NativeExactMapOperationsTest {
  private final Env env =
      newEnv(
          customTypeAdapter(new ExactAdapter()),
          declarations(
              Decls.newVar("ints", Decls.newMapType(Decls.String, Decls.Int)),
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
              Decls.newVar("key", Decls.String),
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
  void numericKeysAndMapEqualityRemainEstablishedCompatible() {
    Map<String, Object> input = input();

    assertEquivalent("numeric[1]", input);
    assertEquivalent("1 in numeric", input);
    assertEquivalent("ints == ints", input);
    assertEquivalent("maps == maps", input);
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
    return Map.of(
        "ints", Map.of("one", 1L, "two", 2L),
        "bools", Map.of("one", true),
        "uints", Map.of("high", -1L, "one", -1L),
        "doubles", Map.of("one", -0.0d, "nan", Double.NaN, "positiveZero", 0.0d),
        "texts", Map.of("one", "value"),
        "strings", Map.of(false, "no", true, "yes"),
        "nulls", nulls,
        "lists", Map.of("numbers", List.of(10L, 20L, 30L)),
        "maps", Map.of("nested", Map.of(true, ULong.valueOf(-1L))),
        "numeric", Map.of(1L, 11L));
  }

  private Val assertEquivalent(String expression, Object input) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    Program nativeProgram = env.program(compiled.getAst());
    Program establishedProgram = env.program(compiled.getAst(), evalOptions(OptDisableNativeEval));
    Val nativeValue = nativeProgram.eval(input).getVal();
    Val establishedValue = establishedProgram.eval(input).getVal();

    assertThat(nativeValue.getClass()).isEqualTo(establishedValue.getClass());
    assertThat(nativeValue.type()).isEqualTo(establishedValue.type());
    assertThat(nativeValue.toString()).isEqualTo(establishedValue.toString());
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

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }
}
