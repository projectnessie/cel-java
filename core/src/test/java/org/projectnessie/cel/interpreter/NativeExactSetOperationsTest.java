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

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

class NativeExactSetOperationsTest {
  private final ExactAdapter adapter = new ExactAdapter();
  private final Env exactEnv =
      newEnv(
          customTypeAdapter(adapter),
          declarations(
              Decls.newVar("stringNeedle", Decls.String),
              Decls.newVar("strings", Decls.newListType(Decls.String)),
              Decls.newVar("boolNeedle", Decls.Bool),
              Decls.newVar("bools", Decls.newListType(Decls.Bool)),
              Decls.newVar("intNeedle", Decls.Int),
              Decls.newVar("ints", Decls.newListType(Decls.Int)),
              Decls.newVar("uintNeedle", Decls.Uint),
              Decls.newVar("uints", Decls.newListType(Decls.Uint)),
              Decls.newVar("doubleNeedle", Decls.Double),
              Decls.newVar("doubles", Decls.newListType(Decls.Double)),
              Decls.newVar("leftInts", Decls.newListType(Decls.Int)),
              Decls.newVar("rightInts", Decls.newListType(Decls.Int)),
              Decls.newVar("rightStrings", Decls.newListType(Decls.String)),
              Decls.newVar("rightBools", Decls.newListType(Decls.Bool)),
              Decls.newVar("rightUints", Decls.newListType(Decls.Uint)),
              Decls.newVar("leftDoubles", Decls.newListType(Decls.Double)),
              Decls.newVar("rightDoubles", Decls.newListType(Decls.Double))));

  @TestFactory
  Stream<DynamicTest> exactSetMembershipPreservesCelScalarSemantics() {
    return Stream.of(
            new MembershipCase(
                "string",
                "stringNeedle in strings",
                Map.of("stringNeedle", "two", "strings", linkedSet("one", "two")),
                true),
            new MembershipCase(
                "boolean",
                "boolNeedle in bools",
                Map.of("boolNeedle", true, "bools", linkedSet(false, true)),
                true),
            new MembershipCase(
                "signed long",
                "intNeedle in ints",
                Map.of("intNeedle", 22L, "ints", linkedSet(11L, 22L)),
                true),
            new MembershipCase(
                "unsigned boxed long",
                "uintNeedle in uints",
                Map.of("uintNeedle", ULong.valueOf(-1L), "uints", linkedSet(-1L, 1L)),
                true),
            new MembershipCase(
                "unsigned ULong",
                "uintNeedle in uints",
                Map.of(
                    "uintNeedle",
                    ULong.valueOf(-1L),
                    "uints",
                    linkedSet(ULong.valueOf(-1L), ULong.valueOf(1L))),
                true),
            new MembershipCase(
                "ordinary double",
                "doubleNeedle in doubles",
                Map.of("doubleNeedle", 2.5d, "doubles", linkedSet(1.5d, 2.5d)),
                true),
            new MembershipCase(
                "NaN never equals itself",
                "doubleNeedle in doubles",
                Map.of("doubleNeedle", Double.NaN, "doubles", linkedSet(Double.NaN)),
                false),
            new MembershipCase(
                "positive zero finds negative zero",
                "doubleNeedle in doubles",
                Map.of("doubleNeedle", 0.0d, "doubles", linkedSet(-0.0d)),
                true),
            new MembershipCase(
                "negative zero finds positive zero",
                "doubleNeedle in doubles",
                Map.of("doubleNeedle", -0.0d, "doubles", linkedSet(0.0d)),
                true),
            new MembershipCase(
                "hash set",
                "intNeedle in ints",
                Map.of("intNeedle", 22L, "ints", new HashSet<>(List.of(11L, 22L))),
                true),
            new MembershipCase(
                "immutable set",
                "intNeedle in ints",
                Map.of("intNeedle", 22L, "ints", Set.of(11L, 22L)),
                true))
        .map(
            testCase ->
                DynamicTest.dynamicTest(
                    testCase.name(),
                    () ->
                        assertEquivalent(
                            exactEnv,
                            testCase.expression(),
                            testCase.input(),
                            testCase.expected())));
  }

  @TestFactory
  Stream<DynamicTest> exactListEqualityUsesEncounterOrderAcrossSetAndListSources() {
    return Stream.of(
            new EqualityCase(
                "set-set same order",
                "leftInts == rightInts",
                Map.of(
                    "leftInts", linkedSet(1L, 2L, 3L),
                    "rightInts", linkedSet(1L, 2L, 3L)),
                true),
            new EqualityCase(
                "set-set different order",
                "leftInts == rightInts",
                Map.of(
                    "leftInts", linkedSet(1L, 2L, 3L),
                    "rightInts", linkedSet(3L, 2L, 1L)),
                false),
            new EqualityCase(
                "set-list",
                "leftInts == rightInts",
                Map.of(
                    "leftInts", linkedSet(1L, 2L, 3L),
                    "rightInts", List.of(1L, 2L, 3L)),
                true),
            new EqualityCase(
                "list-set",
                "leftInts == rightInts",
                Map.of(
                    "leftInts", List.of(1L, 2L, 3L),
                    "rightInts", linkedSet(1L, 2L, 3L)),
                true),
            new EqualityCase(
                "not-equal uses encounter order",
                "leftInts != rightInts",
                Map.of(
                    "leftInts", linkedSet(1L, 2L, 3L),
                    "rightInts", linkedSet(3L, 2L, 1L)),
                true),
            new EqualityCase(
                "string",
                "strings == rightStrings",
                Map.of(
                    "strings", linkedSet("one", "two"),
                    "rightStrings", List.of("one", "two")),
                true),
            new EqualityCase(
                "boolean",
                "bools == rightBools",
                Map.of(
                    "bools", linkedSet(false, true),
                    "rightBools", List.of(false, true)),
                true),
            new EqualityCase(
                "unsigned boxed long and ULong",
                "uints == rightUints",
                Map.of(
                    "uints", linkedSet(-1L, 1L),
                    "rightUints", List.of(ULong.valueOf(-1L), ULong.valueOf(1L))),
                true))
        .map(
            testCase ->
                DynamicTest.dynamicTest(
                    testCase.name(),
                    () ->
                        assertEquivalent(
                            exactEnv,
                            testCase.expression(),
                            testCase.input(),
                            testCase.expected())));
  }

  @Test
  void exactDoubleListEqualityPreservesNanAndSignedZeroSemantics() {
    assertEquivalent(
        exactEnv,
        "leftDoubles == rightDoubles",
        Map.of("leftDoubles", linkedSet(Double.NaN), "rightDoubles", linkedSet(Double.NaN)),
        false);
    assertEquivalent(
        exactEnv,
        "leftDoubles == rightDoubles",
        Map.of("leftDoubles", linkedSet(0.0d), "rightDoubles", linkedSet(-0.0d)),
        true);
  }

  @Test
  void exactMembershipUsesSetContainsButNativeDisabledEvaluationDoesNot() {
    CountingSet<Long> nativeValues = new CountingSet<>(List.of(11L, 22L, 33L));
    assertThat(
            program(exactEnv, "intNeedle in ints", false)
                .eval(Map.of("intNeedle", 22L, "ints", nativeValues))
                .getVal()
                .booleanValue())
        .isTrue();
    assertThat(nativeValues.containsCalls).isEqualTo(1);
    assertThat(nativeValues.iteratorCalls).isZero();

    CountingSet<Long> establishedValues = new CountingSet<>(List.of(11L, 22L, 33L));
    assertThat(
            program(exactEnv, "intNeedle in ints", true)
                .eval(Map.of("intNeedle", 22L, "ints", establishedValues))
                .getVal()
                .booleanValue())
        .isTrue();
    assertThat(establishedValues.containsCalls).isZero();
    assertThat(establishedValues.iteratorCalls).isGreaterThan(0);
  }

  @Test
  void exactMembershipHandlesEmptyMissAndNonSetFallbackWithoutReplayingTheSource() {
    assertEquivalent(
        exactEnv, "intNeedle in ints", Map.of("intNeedle", 22L, "ints", Set.<Long>of()), false);
    assertEquivalent(
        exactEnv, "intNeedle in ints", Map.of("intNeedle", 22L, "ints", Set.of(11L, 33L)), false);

    AtomicInteger resolutions = new AtomicInteger();
    ActivationFunction activation =
        name -> {
          if (name.equals("intNeedle")) {
            return 22L;
          }
          if (name.equals("ints")) {
            resolutions.incrementAndGet();
            return List.of(11L, 22L, 33L);
          }
          return ActivationFunction.ABSENT;
        };
    Val result = program(exactEnv, "intNeedle in ints", false).eval(activation).getVal();

    assertThat(result.booleanValue()).isTrue();
    assertThat(resolutions).hasValue(1);
  }

  @Test
  void exactListEqualityDoesNotDelegateToSetEquals() {
    Set<Long> left = new EqualsRejectingSet<>(List.of(11L, 22L, 33L));
    Set<Long> right = new EqualsRejectingSet<>(List.of(11L, 22L, 33L));

    assertEquivalent(
        exactEnv, "leftInts == rightInts", Map.of("leftInts", left, "rightInts", right), true);
  }

  @Test
  void generalAdapterDoesNotUseCustomSetContainsSemantics() {
    Env generalEnv =
        newEnv(
            declarations(
                Decls.newVar("stringNeedle", Decls.String),
                Decls.newVar("strings", Decls.newListType(Decls.String))));
    TreeSet<String> caseInsensitive = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    caseInsensitive.add("VALUE");

    assertThat(caseInsensitive.contains("value")).isTrue();
    assertEquivalent(
        generalEnv,
        "stringNeedle in strings",
        Map.of("stringNeedle", "value", "strings", caseInsensitive),
        false);
  }

  @Test
  void membershipResolvesNeedleBeforeSourceExactlyOnceEvenWhenBothFail() {
    Program nativeProgram = program(exactEnv, "stringNeedle in strings", false);
    List<String> resolutions = new ArrayList<>();
    AtomicInteger needleResolutions = new AtomicInteger();
    AtomicInteger sourceResolutions = new AtomicInteger();
    ActivationFunction activation =
        name -> {
          resolutions.add(name);
          if (name.equals("stringNeedle")) {
            needleResolutions.incrementAndGet();
            return newErr("needle failed");
          }
          if (name.equals("strings")) {
            sourceResolutions.incrementAndGet();
            return newErr("source failed");
          }
          return ActivationFunction.ABSENT;
        };

    Val result = nativeProgram.eval(activation).getVal();

    assertThat(result).isInstanceOf(Err.class);
    assertThat(result.toString()).contains("needle failed");
    assertThat(resolutions).containsExactly("stringNeedle", "strings");
    assertThat(needleResolutions).hasValue(1);
    assertThat(sourceResolutions).hasValue(1);
  }

  @Test
  void equalityResolvesLeftBeforeRightExactlyOnceEvenWhenBothFail() {
    Program nativeProgram = program(exactEnv, "leftInts == rightInts", false);
    List<String> resolutions = new ArrayList<>();
    ActivationFunction activation =
        name -> {
          resolutions.add(name);
          return newErr(name + " failed");
        };

    Val result = nativeProgram.eval(activation).getVal();

    assertThat(result).isInstanceOf(Err.class);
    assertThat(result.toString()).contains("leftInts failed");
    assertThat(resolutions).containsExactly("leftInts", "rightInts");
  }

  private void assertEquivalent(
      Env env, String expression, Map<String, ?> input, boolean expected) {
    Val nativeValue = program(env, expression, false).eval(input).getVal();
    Val establishedValue = program(env, expression, true).eval(input).getVal();

    assertThat(nativeValue.getClass()).isEqualTo(establishedValue.getClass());
    assertThat(nativeValue.type()).isEqualTo(establishedValue.type());
    assertThat(nativeValue.toString()).isEqualTo(establishedValue.toString());
    assertThat(nativeValue.booleanValue()).isEqualTo(expected);
    assertThat(establishedValue.booleanValue()).isEqualTo(expected);
  }

  private static Program program(Env env, String expression, boolean disableNative) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    return disableNative
        ? env.program(compiled.getAst(), evalOptions(OptDisableNativeEval))
        : env.program(compiled.getAst());
  }

  @SafeVarargs
  private static <E> LinkedHashSet<E> linkedSet(E... elements) {
    return new LinkedHashSet<>(List.of(elements));
  }

  private record MembershipCase(
      String name, String expression, Map<String, ?> input, boolean expected) {}

  private record EqualityCase(
      String name, String expression, Map<String, ?> input, boolean expected) {}

  private static class CountingSet<E> extends AbstractSet<E> {
    private final LinkedHashSet<E> delegate;
    int containsCalls;
    int iteratorCalls;

    CountingSet(List<E> elements) {
      this.delegate = new LinkedHashSet<>(elements);
    }

    @Override
    public Iterator<E> iterator() {
      iteratorCalls++;
      return delegate.iterator();
    }

    @Override
    public int size() {
      return delegate.size();
    }

    @Override
    public boolean contains(Object value) {
      containsCalls++;
      return delegate.contains(value);
    }
  }

  private static final class EqualsRejectingSet<E> extends CountingSet<E> {
    EqualsRejectingSet(List<E> elements) {
      super(elements);
    }

    @Override
    public boolean equals(Object other) {
      throw new AssertionError("CEL list equality must not delegate to Set.equals()");
    }
  }

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }
}
