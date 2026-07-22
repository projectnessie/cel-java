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
package org.projectnessie.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.EvalOption.OptExhaustiveEval;
import static org.projectnessie.cel.EvalOption.OptTrackState;
import static org.projectnessie.cel.ProgramOption.customDecorator;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.ProgramOption.functions;
import static org.projectnessie.cel.checker.Decls.Int;
import static org.projectnessie.cel.checker.Decls.newFunction;
import static org.projectnessie.cel.checker.Decls.newOverload;
import static org.projectnessie.cel.checker.Decls.newVar;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.Activation;
import org.projectnessie.cel.interpreter.ActivationFunction;
import org.projectnessie.cel.interpreter.Interpretable;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableCall;
import org.projectnessie.cel.interpreter.functions.Overload;

class NativePlanRootTest {

  @TestFactory
  Stream<DynamicTest> scalarConstantAndIdentifierRootsBelongOnlyToTheIntegratedPlanner() {
    Map<String, Object> nullInput = new HashMap<>();
    nullInput.put("n", null);
    List<RootCase> cases =
        List.of(
            new RootCase("x", Map.of("x", 41L)),
            new RootCase("s", Map.of("s", "value")),
            new RootCase("d", Map.of("d", 1.5d)),
            new RootCase("b", Map.of("b", true)),
            new RootCase("n", nullInput),
            new RootCase("41", Map.of()),
            new RootCase("'value'", Map.of()),
            new RootCase("1.5", Map.of()),
            new RootCase("true", Map.of()),
            new RootCase("null", Map.of()));

    return integratedRootTests(cases);
  }

  @TestFactory
  Stream<DynamicTest> strictScalarOperationRootsBelongOnlyToTheIntegratedPlanner() {
    Map<String, Object> values = Map.of("x", 42L, "d", 42.5d, "s", "value", "b", false);
    return integratedRootTests(
        Stream.of(
                "!b",
                "-x",
                "-d",
                "x + 1",
                "x - 1",
                "x * 2",
                "x / 2",
                "x % 2",
                "d + 1.0",
                "d - 1.0",
                "d * 2.0",
                "d / 2.0",
                "s + '-suffix'",
                "b < true",
                "b <= true",
                "b > true",
                "b >= true",
                "x < 43",
                "x <= 42",
                "x > 41",
                "x >= 42",
                "d < 43.0",
                "d <= 42.5",
                "d > 41.0",
                "d >= 42.5",
                "s < 'z'",
                "s <= 'value'",
                "s > 'a'",
                "s >= 'value'")
            .map(expression -> new RootCase(expression, values))
            .toList());
  }

  @TestFactory
  Stream<DynamicTest> scalarControlRootsBelongOnlyToTheIntegratedPlanner() {
    Map<String, Object> values = new HashMap<>();
    values.put("x", 42L);
    values.put("y", 41L);
    values.put("d", 42.5d);
    values.put("e", 42.5d);
    values.put("s", "value");
    values.put("t", "other");
    values.put("b", true);
    values.put("c", false);
    values.put("n", null);

    return integratedRootTests(
        Stream.of(
                "b == c",
                "b != c",
                "x == y",
                "x != y",
                "d == e",
                "d != e",
                "s == t",
                "s != t",
                "n == null",
                "n != null",
                "b && c",
                "b || c",
                "b ? c : true",
                "b ? x : y",
                "b ? d : e",
                "b ? s : t",
                "b ? n : null")
            .map(expression -> new RootCase(expression, values))
            .toList());
  }

  @TestFactory
  Stream<DynamicTest> mapSelectorRootsBelongOnlyToTheIntegratedPlanner() {
    Map<String, Object> nullValues = new HashMap<>();
    nullValues.put("answer", null);
    List<RootCase> cases =
        List.of(
            new RootCase("bools.answer", Map.of("bools", Map.of("answer", true))),
            new RootCase("bools['answer']", Map.of("bools", Map.of("answer", true))),
            new RootCase("ints.answer", Map.of("ints", Map.of("answer", 41L))),
            new RootCase("ints['answer']", Map.of("ints", Map.of("answer", 41L))),
            new RootCase("doubles.answer", Map.of("doubles", Map.of("answer", -0.0d))),
            new RootCase("doubles['answer']", Map.of("doubles", Map.of("answer", -0.0d))),
            new RootCase("strings.answer", Map.of("strings", Map.of("answer", "value"))),
            new RootCase("strings['answer']", Map.of("strings", Map.of("answer", "value"))),
            new RootCase("nulls.answer", Map.of("nulls", nullValues)),
            new RootCase("nulls['answer']", Map.of("nulls", nullValues)));

    return integratedRootTests(mapSelectorEnv(), cases);
  }

  @TestFactory
  Stream<DynamicTest> topLevelListConsumerRootsBelongOnlyToTheIntegratedPlanner() {
    List<Object> nullValues = new ArrayList<>();
    nullValues.add(null);
    List<RootCase> cases =
        List.of(
            new RootCase("flags[1]", Map.of("flags", List.of(true, false))),
            new RootCase("numbers[1]", Map.of("numbers", new long[] {7L, 41L})),
            new RootCase("doubles[1]", Map.of("doubles", new double[] {1.0d, -0.0d})),
            new RootCase("words[1]", Map.of("words", new String[] {"zero", "value"})),
            new RootCase("nulls[0]", Map.of("nulls", nullValues)),
            new RootCase(
                "wordTarget in words",
                Map.of("wordTarget", "value", "words", new String[] {"zero", "value"})));

    return integratedRootTests(listEnv(), cases);
  }

  @TestFactory
  Stream<DynamicTest> scalarListLiteralConsumerRootsBelongOnlyToTheIntegratedPlanner() {
    Map<String, Object> values =
        Map.of(
            "b", true, "c", false, "x", 41L, "y", 42L, "d", 1.0d, "e", -0.0d, "s", "value", "t",
            "other");
    return integratedRootTests(
        List.of(
            new RootCase("[b, c][1]", values),
            new RootCase("[x, y][0]", values),
            new RootCase("[d, e][1]", values),
            new RootCase("[s, t][0]", values),
            new RootCase("size([x, y])", values),
            new RootCase("[x, y].size()", values),
            new RootCase("s in [t, s]", values)));
  }

  @TestFactory
  Stream<DynamicTest> canonicalQuantifierRootsBelongOnlyToTheIntegratedPlanner() {
    return integratedRootTests(
        listEnv(),
        List.of(
            new RootCase(
                "flags.exists(value, value)", Map.of("flags", new Boolean[] {false, true})),
            new RootCase("flags.all(value, value)", Map.of("flags", new Boolean[] {true, true})),
            new RootCase(
                "flags.exists_one(value, value)", Map.of("flags", new Boolean[] {false, true})),
            new RootCase(
                "numbers.exists(value, value == 2)", Map.of("numbers", new long[] {1L, 2L})),
            new RootCase("numbers.all(value, value > 0)", Map.of("numbers", new long[] {1L, 2L})),
            new RootCase(
                "numbers.exists_one(value, value == 2)", Map.of("numbers", new long[] {1L, 2L})),
            new RootCase(
                "doubles.exists(value, value == 1.5)",
                Map.of("doubles", new double[] {1.0d, 1.5d})),
            new RootCase(
                "doubles.all(value, value > 0.0)", Map.of("doubles", new double[] {1.0d, 1.5d})),
            new RootCase(
                "doubles.exists_one(value, value == 1.5)",
                Map.of("doubles", new double[] {1.0d, 1.5d})),
            new RootCase(
                "words.exists(value, value == 'value')",
                Map.of("words", new String[] {"other", "value"})),
            new RootCase(
                "words.all(value, value != 'absent')",
                Map.of("words", new String[] {"other", "value"})),
            new RootCase(
                "words.exists_one(value, value == 'value')",
                Map.of("words", new String[] {"other", "value"}))));
  }

  @TestFactory
  Stream<DynamicTest> canonicalListFoldConsumersBelongOnlyToTheIntegratedPlanner() {
    return integratedRootTests(
        listEnv(),
        List.of(
            new RootCase(
                "size(numbers.filter(value, value > 0))",
                Map.of("numbers", new long[] {-1L, 1L, 2L})),
            new RootCase(
                "numbers.map(value, value + 1).size()", Map.of("numbers", new long[] {1L, 2L})),
            new RootCase(
                "size(numbers.map(value, value > 0, value + 1))",
                Map.of("numbers", new long[] {-1L, 1L, 2L})),
            new RootCase(
                "flags.filter(value, value)[0]", Map.of("flags", new Boolean[] {false, true})),
            new RootCase(
                "numbers.map(value, value + 1)[0]", Map.of("numbers", new long[] {1L, 2L})),
            new RootCase(
                "doubles.map(value, value + 0.5)[0]", Map.of("doubles", new double[] {1.0d, 2.0d})),
            new RootCase(
                "words.map(value, value + '!')[0]", Map.of("words", new String[] {"cel", "java"})),
            new RootCase("numbers.map(value, null)[0]", Map.of("numbers", new long[] {1L, 2L})),
            new RootCase(
                "numbers.map(value, value > 0, value + 1)[0]",
                Map.of("numbers", new long[] {-1L, 1L, 2L}))));
  }

  @TestFactory
  Stream<DynamicTest> canonicalMappedStringMembershipBelongsOnlyToTheIntegratedPlanner() {
    return integratedRootTests(
        listEnv(),
        List.of(
            new RootCase(
                "wordTarget in words.map(value, value)",
                Map.of("wordTarget", "cel", "words", new String[] {"other", "cel"})),
            new RootCase(
                "wordTarget in words.map(value, value + '!')",
                Map.of("wordTarget", "cel!", "words", new String[] {"other", "cel"})),
            new RootCase(
                "wordTarget in words.map(value, value != 'skip', value + '!')",
                Map.of("wordTarget", "cel!", "words", new String[] {"other", "skip", "cel"})),
            new RootCase(
                "wordTarget in words.filter(value, value != 'skip')",
                Map.of("wordTarget", "cel", "words", new String[] {"other", "skip", "cel"}))));
  }

  @TestFactory
  Stream<DynamicTest> canonicalMappedIntegerQuantifiersBelongOnlyToTheIntegratedPlanner() {
    Map<String, Object> input = Map.of("numbers", new long[] {1L, 2L, 3L});
    return integratedRootTests(
        listEnv(),
        Stream.of(
                "numbers.map(value, value + 1).exists(mapped, mapped == 3)",
                "numbers.map(value, value + 1).all(mapped, mapped > 1)",
                "numbers.map(value, value + 1).exists_one(mapped, mapped == 3)",
                "numbers.map(value, value > 1, value + 1).exists(mapped, mapped == 3)",
                "numbers.map(value, value > 1, value + 1).all(mapped, mapped > 2)",
                "numbers.map(value, value > 1, value + 1).exists_one(mapped, mapped == 3)",
                "numbers.filter(value, value > 1).exists(mapped, mapped == 2)",
                "numbers.filter(value, value > 1).all(mapped, mapped > 1)",
                "numbers.filter(value, value > 1).exists_one(mapped, mapped == 2)")
            .map(expression -> new RootCase(expression, input))
            .toList());
  }

  @TestFactory
  Stream<DynamicTest> mapSelectorsPreserveEstablishedCompatibilityResults() {
    Map<String, Object> nullValue = new HashMap<>();
    nullValue.put("answer", null);
    Map<String, Object> nullSource = new HashMap<>();
    nullSource.put("ints", null);
    List<CompatibilityCase> cases =
        List.of(
            new CompatibilityCase("native value", Map.of("ints", Map.of("answer", 41L))),
            new CompatibilityCase("boxed value", Map.of("ints", Map.of("answer", 41))),
            new CompatibilityCase("wrapped value", Map.of("ints", Map.of("answer", intOf(41L)))),
            new CompatibilityCase(
                "wrapped source",
                Map.of("ints", DefaultTypeAdapter.Instance.nativeToValue(Map.of("answer", 41L)))),
            new CompatibilityCase(
                "carried error", Map.of("ints", Map.of("answer", newErr("carried")))),
            new CompatibilityCase(
                "carried unknown", Map.of("ints", Map.of("answer", unknownOf(51L)))),
            new CompatibilityCase("wrong value", Map.of("ints", Map.of("answer", "wrong"))),
            new CompatibilityCase("present null value", Map.of("ints", nullValue)),
            new CompatibilityCase("absent key", Map.of("ints", Map.of("other", 41L))),
            new CompatibilityCase("source error", Map.of("ints", newErr("source"))),
            new CompatibilityCase("source unknown", Map.of("ints", unknownOf(52L))),
            new CompatibilityCase("wrong source", Map.of("ints", "wrong source")),
            new CompatibilityCase("present null source", nullSource),
            new CompatibilityCase("absent source", Map.of()));

    return Stream.of("ints.answer", "ints['answer']")
        .flatMap(
            expression ->
                cases.stream()
                    .map(
                        testCase ->
                            DynamicTest.dynamicTest(
                                expression + ": " + testCase.label(),
                                () -> {
                                  Env env = mapSelectorEnv();
                                  Ast ast = compile(env, expression);
                                  Prog integrated = (Prog) env.program(ast);
                                  Prog established =
                                      (Prog) env.program(ast, evalOptions(OptDisableNativeEval));

                                  assertIntegratedRoot(integrated, established);
                                  assertEquivalent(
                                      integrated.eval(testCase.input()).getVal(),
                                      established.eval(testCase.input()).getVal());
                                })));
  }

  @TestFactory
  Stream<DynamicTest> mapSelectorRootsResolveTheirSourceOnceWithoutRepeatedKeyLookup() {
    return Stream.of("ints.answer", "ints['answer']")
        .map(
            expression ->
                DynamicTest.dynamicTest(
                    expression,
                    () -> {
                      Env env = mapSelectorEnv();
                      Ast ast = compile(env, expression);
                      Prog integrated = (Prog) env.program(ast);
                      CountingMap values = new CountingMap();
                      values.put("answer", 41L);
                      AtomicInteger resolutions = new AtomicInteger();
                      Activation activation =
                          newActivation(
                              (ActivationFunction)
                                  name -> {
                                    resolutions.incrementAndGet();
                                    return name.equals("ints") ? values : ActivationFunction.ABSENT;
                                  });

                      assertThat(integrated.interpretable.getClass().getSimpleName())
                          .isEqualTo("NativeIsland");
                      assertThat(integrated.interpretable.eval(activation).intValue())
                          .isEqualTo(41L);
                      assertThat(resolutions).hasValue(1);
                      assertThat(values.gets()).isEqualTo(expression.indexOf('[') < 0 ? 1 : 0);
                    }));
  }

  @TestFactory
  Stream<DynamicTest> nullMapSelectorsDistinguishPresentNullFromAnAbsentKey() {
    return Stream.of("nulls.answer", "nulls['answer']")
        .map(
            expression ->
                DynamicTest.dynamicTest(
                    expression,
                    () -> {
                      Env env = mapSelectorEnv();
                      Ast ast = compile(env, expression);
                      Prog integrated = (Prog) env.program(ast);
                      Prog established = (Prog) env.program(ast, evalOptions(OptDisableNativeEval));
                      Map<String, Object> presentNull = new HashMap<>();
                      presentNull.put("answer", null);

                      Val actualPresent = integrated.eval(Map.of("nulls", presentNull)).getVal();
                      Val expectedPresent = established.eval(Map.of("nulls", presentNull)).getVal();
                      Val actualAbsent =
                          integrated
                              .eval(Map.of("nulls", Map.of("other", "not selected")))
                              .getVal();
                      Val expectedAbsent =
                          established
                              .eval(Map.of("nulls", Map.of("other", "not selected")))
                              .getVal();

                      assertIntegratedRoot(integrated, established);
                      assertEquivalent(actualPresent, expectedPresent);
                      assertThat(actualPresent).isSameAs(NullT.NullValue);
                      assertEquivalent(actualAbsent, expectedAbsent);
                      assertThat(actualAbsent).isInstanceOf(Err.class);
                    }));
  }

  private static void assertIntegratedRoot(Prog integrated, Prog established) {
    assertThat(integrated.interpretable.getClass().getSimpleName()).isEqualTo("NativeIsland");
    assertThat(established.interpretable.getClass().getSimpleName()).isNotEqualTo("NativeIsland");
    assertThat(containsNativeNode(established.interpretable)).isFalse();
  }

  private static void assertEquivalent(Val actual, Val expected) {
    assertThat(actual.getClass()).isEqualTo(expected.getClass());
    assertThat(actual.type()).isSameAs(expected.type());
    assertThat(actual.toString()).isEqualTo(expected.toString());
    if (actual instanceof Err actualError && expected instanceof Err expectedError) {
      assertThat(actualError.hasCause()).isEqualTo(expectedError.hasCause());
      if (actualError.hasCause()) {
        assertThat(actualError.getCause().getClass())
            .isEqualTo(expectedError.getCause().getClass());
        assertThat(actualError.getCause().getMessage())
            .isEqualTo(expectedError.getCause().getMessage());
      }
      return;
    }
    if (actual.value() instanceof Double actualDouble
        && expected.value() instanceof Double expectedDouble) {
      assertThat(Double.doubleToRawLongBits(actualDouble))
          .isEqualTo(Double.doubleToRawLongBits(expectedDouble));
    } else {
      assertThat(actual.value()).isEqualTo(expected.value());
    }
  }

  private static Stream<DynamicTest> integratedRootTests(List<RootCase> cases) {
    return integratedRootTests(env(DefaultTypeAdapter.Instance), cases);
  }

  private static Stream<DynamicTest> integratedRootTests(Env env, List<RootCase> cases) {
    return cases.stream()
        .map(
            testCase ->
                DynamicTest.dynamicTest(
                    testCase.expression(),
                    () -> {
                      Ast ast = compile(env, testCase.expression());
                      Prog integrated = (Prog) env.program(ast);
                      Prog established = (Prog) env.program(ast, evalOptions(OptDisableNativeEval));

                      assertIntegratedRoot(integrated, established);
                      assertThat(integrated.interpretable.id()).isEqualTo(ast.getExpr().getId());
                      assertThat(integrated.cost()).isEqualTo(established.cost());
                      Val actual = integrated.eval(testCase.input()).getVal();
                      Val expected = established.eval(testCase.input()).getVal();
                      assertEquivalent(actual, expected);
                    }));
  }

  @Test
  void storesEitherEvaluatorAsOneInterpretableWithTheCheckedRootIdentityAndCost() {
    Env env = env(DefaultTypeAdapter.Instance);
    Ast ast = compile(env, "x + 1");
    Prog nativeProgram = (Prog) env.program(ast);
    Prog currentProgram = (Prog) env.program(ast, evalOptions(OptDisableNativeEval));

    assertThat(nativeProgram.interpretable.getClass().getSimpleName()).isEqualTo("NativeIsland");
    assertThat(currentProgram.interpretable.getClass().getSimpleName())
        .isNotEqualTo("NativeIsland");
    assertThat(nativeProgram.interpretable.id()).isEqualTo(ast.getExpr().getId());
    assertThat(currentProgram.interpretable.id()).isEqualTo(ast.getExpr().getId());
    assertThat(nativeProgram.cost()).isEqualTo(currentProgram.cost());
  }

  @Test
  void adaptsANativeSuccessExactlyOnceAtTheExecutableRoot() {
    CountingAdapter adapter = new CountingAdapter();
    Env env = env(adapter);
    Prog program = (Prog) env.program(compile(env, "x + 1"));
    Interpretable root = program.interpretable;

    adapter.reset();
    assertThat(root.eval(newActivation(Map.of("x", 41L))).intValue()).isEqualTo(42L);
    assertThat(adapter.count()).isEqualTo(1);

    adapter.reset();
    assertThat(program.eval(Map.of("x", 41L)).getVal().intValue()).isEqualTo(42L);
    assertThat(adapter.count()).isEqualTo(1);
  }

  @Test
  void returnsCarriedCompatibilityValuesWithoutRootReadaptation() {
    CountingAdapter adapter = new CountingAdapter();
    Env env = env(adapter);
    Prog intProgram = (Prog) env.program(compile(env, "x"));
    Prog stringProgram = (Prog) env.program(compile(env, "s"));
    Val error = newErr("carried error");
    Val unknown = unknownOf(42L);
    Val nullString = stringOf(null);

    assertCarried(intProgram, adapter, "x", error);
    assertCarried(intProgram, adapter, "x", unknown);
    assertCarried(stringProgram, adapter, "s", nullString);
  }

  @Test
  void statefulProgramFactoriesAlwaysCreateAnExecutableRootForEvalAndCost() {
    Env env = env(DefaultTypeAdapter.Instance);
    Ast ast = compile(env, "x + 1");
    Program current = env.program(ast, evalOptions(OptDisableNativeEval));
    Program tracked = env.program(ast, evalOptions(OptTrackState));
    Program exhaustive = env.program(ast, evalOptions(OptExhaustiveEval));

    assertThat(tracked.eval(Map.of("x", 41L)).getVal().intValue()).isEqualTo(42L);
    assertThat(exhaustive.eval(Map.of("x", 41L)).getVal().intValue()).isEqualTo(42L);
    assertThat(estimateCost(tracked)).isEqualTo(estimateCost(current));
    assertThat(estimateCost(exhaustive)).isEqualTo(estimateCost(current));
  }

  @Test
  void onlyTheEmptyEvalOptionSubsetPermitsNativePlanning() {
    EvalOption[] values = EvalOption.values();
    assertThat(values).hasSize(5);
    Env env =
        newEnv(
            declarations(
                newVar("x", Int),
                newFunction("opaque", newOverload("opaque_int", List.of(Int), Int))));
    Ast ast = compile(env, "opaque(x + 1)");
    Overload opaque = Overload.unary("opaque_int", value -> value);

    for (int mask = 0; mask < 1 << values.length; mask++) {
      Set<EvalOption> subset = EnumSet.noneOf(EvalOption.class);
      for (int bit = 0; bit < values.length; bit++) {
        if ((mask & (1 << bit)) != 0) {
          subset.add(values[bit]);
        }
      }

      assertThat(CEL.nativePlanningPermitted(subset, List.of()))
          .as(subset.toString())
          .isEqualTo(subset.isEmpty());

      Program program =
          env.program(ast, functions(opaque), evalOptions(subset.toArray(EvalOption[]::new)));
      assertThat(program.eval(Map.of("x", 41L)).getVal().intValue())
          .as(subset.toString())
          .isEqualTo(42L);

      if (program instanceof Prog prog) {
        assertThat(containsNativeNode(prog.interpretable))
            .as(subset.toString())
            .isEqualTo(subset.isEmpty());
      }
    }
  }

  @Test
  void customDecoratorsDisableNativePlanningBeforeAnyNodeIsBuilt() {
    Env env = env(DefaultTypeAdapter.Instance);
    Ast ast = compile(env, "x + 1");
    List<String> observed = new ArrayList<>();

    Prog program =
        (Prog)
            env.program(
                ast,
                customDecorator(
                    node -> {
                      observed.add(node.getClass().getName());
                      return node;
                    }));

    assertThat(CEL.nativePlanningPermitted(Set.of(), List.of(node -> node))).isFalse();
    assertThat(observed).noneMatch(name -> name.contains("Native"));
    assertThat(containsNativeNode(program.interpretable)).isFalse();
  }

  private static boolean containsNativeNode(Interpretable node) {
    if (node.getClass().getSimpleName().startsWith("Native")) {
      return true;
    }
    if (node instanceof InterpretableCall call) {
      for (Interpretable argument : call.args()) {
        if (containsNativeNode(argument)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void assertCarried(
      Prog program, CountingAdapter adapter, String variable, Val carried) {
    adapter.reset();
    assertThat(program.interpretable.eval(newActivation(Map.of(variable, carried))))
        .isSameAs(carried);
    assertThat(adapter.count()).isEqualTo(1);

    adapter.reset();
    assertThat(program.eval(Map.of(variable, carried)).getVal()).isSameAs(carried);
    assertThat(adapter.count()).isEqualTo(1);
  }

  private static Env env(StandardScalarTypeAdapter adapter) {
    return newEnv(
        customTypeAdapter(adapter),
        declarations(
            Decls.newVar("x", Decls.Int),
            Decls.newVar("y", Decls.Int),
            Decls.newVar("s", Decls.String),
            Decls.newVar("t", Decls.String),
            Decls.newVar("d", Decls.Double),
            Decls.newVar("e", Decls.Double),
            Decls.newVar("b", Decls.Bool),
            Decls.newVar("c", Decls.Bool),
            Decls.newVar("n", Decls.Null)));
  }

  private static Env mapSelectorEnv() {
    return newEnv(
        declarations(
            Decls.newVar("bools", Decls.newMapType(Decls.String, Decls.Bool)),
            Decls.newVar("ints", Decls.newMapType(Decls.String, Decls.Int)),
            Decls.newVar("doubles", Decls.newMapType(Decls.String, Decls.Double)),
            Decls.newVar("strings", Decls.newMapType(Decls.String, Decls.String)),
            Decls.newVar("nulls", Decls.newMapType(Decls.String, Decls.Null))));
  }

  private static Env listEnv() {
    return newEnv(
        declarations(
            Decls.newVar("flags", Decls.newListType(Decls.Bool)),
            Decls.newVar("numbers", Decls.newListType(Decls.Int)),
            Decls.newVar("doubles", Decls.newListType(Decls.Double)),
            Decls.newVar("words", Decls.newListType(Decls.String)),
            Decls.newVar("nulls", Decls.newListType(Decls.Null)),
            Decls.newVar("wordTarget", Decls.String)));
  }

  private static Ast compile(Env env, String expression) {
    AstIssuesTuple result = env.compile(expression);
    assertThat(result.hasIssues()).as(expression).isFalse();
    return result.getAst();
  }

  private static final class CountingAdapter implements StandardScalarTypeAdapter {
    private final AtomicInteger conversions = new AtomicInteger();

    @Override
    public Val nativeToValue(Object value) {
      conversions.incrementAndGet();
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }

    void reset() {
      conversions.set(0);
    }

    int count() {
      return conversions.get();
    }
  }

  private static final class CountingMap extends HashMap<String, Object> {
    private int gets;

    @Override
    public Object get(Object key) {
      gets++;
      return super.get(key);
    }

    int gets() {
      return gets;
    }
  }

  private record RootCase(String expression, Map<String, Object> input) {}

  private record CompatibilityCase(String label, Map<String, Object> input) {}
}
