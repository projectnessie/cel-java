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
package org.projectnessie.cel;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.projectnessie.cel.CEL.attributePattern;
import static org.projectnessie.cel.CEL.partialVars;
import static org.projectnessie.cel.Env.newCustomEnv;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.EvalOption.OptOptimize;
import static org.projectnessie.cel.EvalOption.OptPartialEval;
import static org.projectnessie.cel.ProgramOption.customDecorator;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.ProgramOption.functions;
import static org.projectnessie.cel.ProgramOption.globals;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.extension.OptionalLib.optionals;
import static org.projectnessie.cel.interpreter.Activation.newActivation;

import com.google.protobuf.DynamicMessage;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.IntT;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.StandardScalarFieldProvider;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Adder;
import org.projectnessie.cel.common.types.traits.Trait;
import org.projectnessie.cel.interpreter.ActivationFunction;
import org.projectnessie.cel.interpreter.Interpretable;
import org.projectnessie.cel.interpreter.functions.Overload;

class NativePlanTest {
  private final Env env =
      newEnv(
          declarations(
              Decls.newVar("x", Decls.Int),
              Decls.newVar("y", Decls.Int),
              Decls.newVar("d", Decls.Double),
              Decls.newVar("e", Decls.Double),
              Decls.newVar("b", Decls.Bool),
              Decls.newVar("c", Decls.Bool),
              Decls.newVar("s", Decls.String),
              Decls.newVar("n", Decls.Null),
              Decls.newVar("attrs", Decls.newMapType(Decls.String, Decls.Int)),
              Decls.newVar("labels", Decls.newMapType(Decls.String, Decls.String)),
              Decls.newVar("numbers", Decls.newListType(Decls.Int)),
              Decls.newVar("doubles", Decls.newListType(Decls.Double)),
              Decls.newVar("words", Decls.newListType(Decls.String)),
              Decls.newVar("flags", Decls.newListType(Decls.Bool)),
              Decls.newVar("key", Decls.String),
              Decls.newVar("target", Decls.Int),
              Decls.newVar("wordTarget", Decls.String)));

  @Test
  void builtInAdaptersDeclareStandardScalarSemantics() {
    assertThat(DefaultTypeAdapter.Instance).isInstanceOf(StandardScalarTypeAdapter.class);
    assertThat(ProtoTypeRegistry.newRegistry()).isInstanceOf(StandardScalarTypeAdapter.class);
    assertThat(ProtoTypeRegistry.newRegistry()).isInstanceOf(StandardScalarFieldProvider.class);
  }

  static Stream<Evaluation> supportedScalarEvaluations() {
    return Stream.of(
        new Evaluation("x", Map.of("x", 41L)),
        new Evaluation("x", Map.of("x", "not an int")),
        new Evaluation("x + 1", Map.of("x", intOf(41L))),
        new Evaluation("d + 1.0", Map.of("d", doubleOf(41.5d))),
        new Evaluation("b && true", Map.of("b", True)),
        new Evaluation("s + '!'", Map.of("s", stringOf("hello"))),
        new Evaluation("s", Map.of("s", stringOf(null))),
        new Evaluation("b ? s : 'unused'", Map.of("b", true, "s", stringOf(null))),
        new Evaluation("s + ' suffix'", Map.of("s", stringOf(null))),
        new Evaluation("'prefix ' + s", Map.of("s", stringOf(null))),
        new Evaluation("x + 1", Map.of("x", 41L)),
        new Evaluation("(x + 1) * (y - 2) / 3 % 5", Map.of("x", 8L, "y", 7L)),
        new Evaluation("-(x + 1)", Map.of("x", 41L)),
        new Evaluation("d / 3.0 + 0.5", Map.of("d", -0.0d)),
        new Evaluation("s + ' world' + '!'", Map.of("s", "hello")),
        new Evaluation("b && x < y", Map.of("b", false, "x", 1L, "y", 2L)),
        new Evaluation("b || x >= y", Map.of("b", true, "x", 1L, "y", 2L)),
        new Evaluation("!(x == y)", Map.of("x", 1L, "y", 2L)),
        new Evaluation("b ? x + 1 : y + 2", Map.of("b", true, "x", 1L, "y", 9L)),
        new Evaluation("d == -0.0", Map.of("d", 0.0d)),
        new Evaluation("d != d", Map.of("d", Double.NaN)),
        new Evaluation("b ? x / y : x + y", Map.of("b", true, "x", 1L, "y", 0L)),
        new Evaluation("b ? x : y", Map.of("b", "not a bool", "x", 1L, "y", 2L)),
        new Evaluation("x / y", Map.of("x", 1L, "y", 0L)),
        new Evaluation("x + 1", Map.of("x", Long.MAX_VALUE)),
        new Evaluation("x + 1", Map.of("x", "not an int")),
        new Evaluation("x + 1", Map.of("x", IntT.IntType)),
        new Evaluation("x", Map.of()));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("supportedScalarEvaluations")
  void evaluatesSupportedScalarTreesLikeTheExistingInterpreter(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @ValueSource(strings = {"s == 'value'", "s < 'value'"})
  void nullBackedStringOperationsUseTheCurrentCompatibilityPath(String expression) {
    Programs programs = programs(expression);
    Object input = Map.of("s", stringOf(null));

    Throwable nativeFailure = catchThrowable(() -> programs.nativeProgram.eval(input));
    Throwable interpreterFailure = catchThrowable(() -> programs.interpreterProgram.eval(input));

    assertThat(nativeFailure).isNotNull();
    assertThat(interpreterFailure).isNotNull();
    assertThat(nativeFailure.getClass()).isEqualTo(interpreterFailure.getClass());
    assertThat(nativeFailure.getMessage()).isEqualTo(interpreterFailure.getMessage());
  }

  @Test
  void preservesNullAndAbsentAsDifferentActivationStates() {
    Map<String, Object> presentNull = new java.util.HashMap<>();
    presentNull.put("n", null);

    Programs programs = programs("n == null");
    assertEquivalent(
        programs.nativeProgram.eval(presentNull).getVal(),
        programs.interpreterProgram.eval(presentNull).getVal());
    assertEquivalent(
        programs.nativeProgram.eval(Map.of()).getVal(),
        programs.interpreterProgram.eval(Map.of()).getVal());
  }

  static Stream<Evaluation> checkedMapSelectorEvaluations() {
    Val adaptedMap = DefaultTypeAdapter.Instance.nativeToValue(Map.of("answer", 50_021L));
    Map<String, Object> presentNull = new java.util.HashMap<>();
    presentNull.put("answer", null);
    Map<String, Object> presentNullString = new java.util.HashMap<>();
    presentNullString.put("name", stringOf(null));
    return Stream.of(
        new Evaluation("attrs.answer", Map.of("attrs", Map.of("answer", 50_021L))),
        new Evaluation(
            "attrs.answer == target",
            Map.of("attrs", Map.of("answer", 50_021L), "target", 50_021L)),
        new Evaluation("labels.name == 'cel'", Map.of("labels", Map.of("name", "cel"))),
        new Evaluation("attrs.missing", Map.of("attrs", Map.of())),
        new Evaluation("attrs.answer", Map.of("attrs", Map.of("answer", "wrong"))),
        new Evaluation("attrs.answer", Map.of("attrs", newErr("map error"))),
        new Evaluation("attrs.answer", Map.of("attrs", unknownOf(87L))),
        new Evaluation("attrs.answer", Map.of("attrs", "wrong container")),
        new Evaluation("attrs.answer", Map.of()),
        new Evaluation("attrs.answer == 50021", Map.of("attrs", adaptedMap)),
        new Evaluation("attrs.answer", Map.of("attrs", presentNull)),
        new Evaluation("labels.name", Map.of("labels", presentNullString)));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("checkedMapSelectorEvaluations")
  void evaluatesCheckedMapSelectorsWithoutTerminalValueAdaptation(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void mapSelectorsPreservePartialQualifierMatchingAndResolutionCounts() {
    Ast ast = compile("attrs.answer == target");
    Prog nativeProgram = (Prog) env.program(ast);
    Prog interpreterProgram =
        (Prog) env.program(ast, evalOptions(OptPartialEval, OptDisableNativeEval));
    Object input =
        partialVars(
            Map.of("attrs", Map.of("answer", 50_021L), "target", 50_021L),
            attributePattern("attrs").qualString("answer"));

    assertIntegratedPlan(nativeProgram, "attrs.answer == target");
    assertEquivalent(nativeProgram.eval(input).getVal(), interpreterProgram.eval(input).getVal());
  }

  @Test
  void dynamicMapKeysRemainOnTheCurrentEvaluator() {
    Ast ast = compile("attrs[key]");
    Prog program = (Prog) env.program(ast);

    assertCurrentPlan(program);
    assertThat(program.eval(Map.of("attrs", Map.of("answer", 50_021L), "key", "answer")).getVal())
        .isEqualTo(intOf(50_021L));
  }

  static Stream<Evaluation> constantListIndexEvaluations() {
    Val adaptedList = DefaultTypeAdapter.Instance.nativeToValue(List.of(7L, 50_021L));
    return Stream.of(
        new Evaluation("numbers[1]", Map.of("numbers", new long[] {7L, 50_021L, 9L})),
        new Evaluation("numbers[1]", Map.of("numbers", new int[] {7, 50_021, 9})),
        new Evaluation(
            "numbers[1] == target", Map.of("numbers", List.of(7L, 50_021L), "target", 50_021L)),
        new Evaluation("doubles[1] == -0.0", Map.of("doubles", new double[] {1.0d, -0.0d})),
        new Evaluation("numbers[-1]", Map.of("numbers", new long[] {7L})),
        new Evaluation("numbers[3]", Map.of("numbers", new long[] {7L})),
        new Evaluation("numbers[0]", Map.of("numbers", List.of("wrong element"))),
        new Evaluation("numbers[0]", Map.of("numbers", "wrong container")),
        new Evaluation("numbers[0]", Map.of("numbers", newErr("list error"))),
        new Evaluation("numbers[0]", Map.of("numbers", unknownOf(96L))),
        new Evaluation("numbers[0]", Map.of()),
        new Evaluation("numbers[1]", Map.of("numbers", adaptedList)));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("constantListIndexEvaluations")
  void evaluatesConstantListIndexesWithoutPrimitiveArrayBoxing(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  static Stream<Evaluation> boolListLiteralIndexEvaluations() {
    return Stream.of(
        new Evaluation("[true, false, true][0]", Map.of()),
        new Evaluation("[b, c, true][0]", Map.of("b", true, "c", false)),
        new Evaluation("[b && true, c || false][1]", Map.of("b", true, "c", false)),
        new Evaluation("[b, c][1] == true", Map.of("b", false, "c", true)),
        new Evaluation("[b, c][-1]", Map.of("b", true, "c", false)),
        new Evaluation("[b, c][2]", Map.of("b", true, "c", false)),
        new Evaluation("[b, c][0]", Map.of("b", newErr("first"), "c", false)),
        new Evaluation("[b, c][0]", Map.of("b", unknownOf(80L), "c", false)),
        new Evaluation(
            "[b, c][0]", partialVars(Map.of("b", true, "c", false), attributePattern("b"))),
        new Evaluation("[b, c][0]", Map.of("b", 42L, "c", false)),
        new Evaluation("[b, c][1]", Map.of("b", 42L, "c", false)),
        new Evaluation("[b, c][0]", Map.of("b", 42L, "c", newErr("later"))),
        new Evaluation("[b && true, c][0]", Map.of("b", 42L, "c", false)),
        new Evaluation("[b, c][0]", Map.of("c", false)));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("boolListLiteralIndexEvaluations")
  void evaluatesCheckedBoolListLiteralIndexesWithoutMaterializingTheList(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void boolListLiteralIndexesPreserveConstructionBoundsAndLocalShortCircuiting() {
    for (String expression : List.of("[b, c][0]", "[b, c][2]", "[b && x > 0, c][0]")) {
      Programs programs = programs(expression);
      List<String> nativeOrder = new ArrayList<>();
      List<String> interpreterOrder = new ArrayList<>();

      assertEquivalent(
          programs
              .nativeProgram
              .eval(orderedBoolListActivation(nativeOrder, false, false))
              .getVal(),
          programs
              .interpreterProgram
              .eval(orderedBoolListActivation(interpreterOrder, false, false))
              .getVal());
      assertThat(nativeOrder).as(expression).containsExactly("b", "c");
      assertThat(interpreterOrder).as(expression).containsExactly("b", "c");
    }

    Programs programs = programs("[b, c][0]");
    List<String> nativeOrder = new ArrayList<>();
    List<String> interpreterOrder = new ArrayList<>();
    assertEquivalent(
        programs
            .nativeProgram
            .eval(orderedBoolListActivation(nativeOrder, newErr("first"), false))
            .getVal(),
        programs
            .interpreterProgram
            .eval(orderedBoolListActivation(interpreterOrder, newErr("first"), false))
            .getVal());
    assertThat(nativeOrder).containsExactly("b");
    assertThat(interpreterOrder).containsExactly("b");
  }

  static Stream<Evaluation> boolListLiteralSizeEvaluations() {
    return Stream.of(
        new Evaluation("size([true, false, true])", Map.of()),
        new Evaluation("size([b, c, true])", Map.of("b", true, "c", false)),
        new Evaluation("[b, c, true].size()", Map.of("b", true, "c", false)),
        new Evaluation("size([b && true, c || false]) + 1", Map.of("b", true, "c", false)),
        new Evaluation("size([b, c])", Map.of("b", 42L, "c", false)),
        new Evaluation("size([b, c])", Map.of("b", 42L, "c", newErr("later"))),
        new Evaluation("size([b, c])", Map.of("b", newErr("first"), "c", false)),
        new Evaluation("size([b, c])", Map.of("b", unknownOf(81L), "c", false)),
        new Evaluation(
            "size([b, c])", partialVars(Map.of("b", true, "c", false), attributePattern("b"))),
        new Evaluation("size([b && true, c])", Map.of("b", 42L, "c", false)),
        new Evaluation("size([b, c])", Map.of("c", false)));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("boolListLiteralSizeEvaluations")
  void evaluatesCheckedBoolListLiteralSizesWithoutMaterializingTheList(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void boolListLiteralSizePreservesElementEvaluationAndEarlyTermination() {
    Programs programs = programs("size([b && x > 0, c])");
    List<String> nativeOrder = new ArrayList<>();
    List<String> interpreterOrder = new ArrayList<>();

    assertEquivalent(
        programs.nativeProgram.eval(orderedBoolListActivation(nativeOrder, false, false)).getVal(),
        programs
            .interpreterProgram
            .eval(orderedBoolListActivation(interpreterOrder, false, false))
            .getVal());
    assertThat(nativeOrder).containsExactly("b", "c");
    assertThat(interpreterOrder).containsExactly("b", "c");

    programs = programs("size([b, c])");
    nativeOrder.clear();
    interpreterOrder.clear();
    assertEquivalent(
        programs
            .nativeProgram
            .eval(orderedBoolListActivation(nativeOrder, newErr("first"), false))
            .getVal(),
        programs
            .interpreterProgram
            .eval(orderedBoolListActivation(interpreterOrder, newErr("first"), false))
            .getVal());
    assertThat(nativeOrder).containsExactly("b");
    assertThat(interpreterOrder).containsExactly("b");
  }

  static Stream<Evaluation> intListLiteralIndexEvaluations() {
    return Stream.of(
        new Evaluation("[x, y, 3][0]", Map.of("x", 50_021L, "y", 7L)),
        new Evaluation("[x + 1, y + 2, 3][1]", Map.of("x", 40L, "y", 40L)),
        new Evaluation("[x + 1, y + 2, 3][1] == target", Map.of("x", 40L, "y", 40L, "target", 42L)),
        new Evaluation("[x, y][-1]", Map.of("x", 1L, "y", 2L)),
        new Evaluation("[x, y][2]", Map.of("x", 1L, "y", 2L)),
        new Evaluation("[x, y][0]", Map.of("x", newErr("first"), "y", 2L)),
        new Evaluation("[x, y][0]", Map.of("x", unknownOf(73L), "y", 2L)),
        new Evaluation("[x, y][0]", Map.of("x", 1L, "y", newErr("second"))),
        new Evaluation("[x, y][0]", Map.of("x", 1L, "y", unknownOf(74L))),
        new Evaluation("[x, y][2]", Map.of("x", newErr("first"), "y", 2L)),
        new Evaluation("[x, y][0]", partialVars(Map.of("x", 1L, "y", 2L), attributePattern("x"))),
        new Evaluation("[x, y][0]", Map.of("x", "wrong", "y", 2L)),
        new Evaluation("[x, y][1]", Map.of("x", "wrong", "y", 2L)),
        new Evaluation("[x, y][0]", Map.of("x", "wrong", "y", newErr("second"))),
        new Evaluation("[x, y][0]", Map.of("y", 2L)),
        new Evaluation("[x + 1, y][1]", Map.of("x", Long.MAX_VALUE, "y", 2L)));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("intListLiteralIndexEvaluations")
  void evaluatesCheckedIntListLiteralIndexesWithoutMaterializingTheList(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void intListLiteralIndexesPreserveElementEvaluationAndIndexErrorOrdering() {
    for (String expression : List.of("[x, y][0]", "[x, y][2]")) {
      Programs programs = programs(expression);
      List<String> nativeOrder = new ArrayList<>();
      List<String> interpreterOrder = new ArrayList<>();

      Val nativeResult =
          programs.nativeProgram.eval(orderedIntListActivation(nativeOrder, false)).getVal();
      Val interpreterResult =
          programs
              .interpreterProgram
              .eval(orderedIntListActivation(interpreterOrder, false))
              .getVal();

      assertEquivalent(nativeResult, interpreterResult);
      assertThat(nativeOrder).as(expression).containsExactly("x", "y");
      assertThat(interpreterOrder).as(expression).containsExactly("x", "y");
    }

    Programs programs = programs("[x, y][0]");
    List<String> nativeOrder = new ArrayList<>();
    List<String> interpreterOrder = new ArrayList<>();
    assertEquivalent(
        programs.nativeProgram.eval(orderedIntListActivation(nativeOrder, true)).getVal(),
        programs
            .interpreterProgram
            .eval(orderedIntListActivation(interpreterOrder, true))
            .getVal());
    assertThat(nativeOrder).containsExactly("x");
    assertThat(interpreterOrder).containsExactly("x");
  }

  static Stream<Evaluation> doubleListLiteralIndexEvaluations() {
    return Stream.of(
        new Evaluation("[d, e, 3.0][0]", Map.of("d", 50_021.25d, "e", 7.5d)),
        new Evaluation("[d + 1.0, e + 2.0, 3.0][1]", Map.of("d", 40.5d, "e", 40.5d)),
        new Evaluation("[d + 1.0, e + 2.0, 3.0][1] == 42.5", Map.of("d", 40.5d, "e", 40.5d)),
        new Evaluation("[d, e][0]", Map.of("d", Double.NaN, "e", 2.0d)),
        new Evaluation("[d, e][0]", Map.of("d", Double.POSITIVE_INFINITY, "e", 2.0d)),
        new Evaluation("[d, e][0]", Map.of("d", -0.0d, "e", 2.0d)),
        new Evaluation("[d, e][-1]", Map.of("d", 1.0d, "e", 2.0d)),
        new Evaluation("[d, e][2]", Map.of("d", 1.0d, "e", 2.0d)),
        new Evaluation("[d, e][0]", Map.of("d", newErr("first"), "e", 2.0d)),
        new Evaluation("[d, e][0]", Map.of("d", unknownOf(74L), "e", 2.0d)),
        new Evaluation(
            "[d, e][0]", partialVars(Map.of("d", 1.0d, "e", 2.0d), attributePattern("d"))),
        new Evaluation("[d, e][0]", Map.of("d", "wrong", "e", 2.0d)),
        new Evaluation("[d, e][1]", Map.of("d", "wrong", "e", 2.0d)),
        new Evaluation("[d, e][0]", Map.of("d", "wrong", "e", newErr("second"))),
        new Evaluation("[d, e][0]", Map.of("e", 2.0d)));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("doubleListLiteralIndexEvaluations")
  void evaluatesCheckedDoubleListLiteralIndexesWithoutMaterializingTheList(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void doubleListLiteralIndexesPreserveElementEvaluationAndIndexErrorOrdering() {
    for (String expression : List.of("[d, e][0]", "[d, e][2]")) {
      Programs programs = programs(expression);
      List<String> nativeOrder = new ArrayList<>();
      List<String> interpreterOrder = new ArrayList<>();

      assertEquivalent(
          programs
              .nativeProgram
              .eval(orderedDoubleListActivation(nativeOrder, 1.0d, 2.0d))
              .getVal(),
          programs
              .interpreterProgram
              .eval(orderedDoubleListActivation(interpreterOrder, 1.0d, 2.0d))
              .getVal());
      assertThat(nativeOrder).as(expression).containsExactly("d", "e");
      assertThat(interpreterOrder).as(expression).containsExactly("d", "e");
    }

    Programs programs = programs("[d, e][1]");
    List<String> nativeOrder = new ArrayList<>();
    List<String> interpreterOrder = new ArrayList<>();
    assertEquivalent(
        programs
            .nativeProgram
            .eval(orderedDoubleListActivation(nativeOrder, "wrong", 2.0d))
            .getVal(),
        programs
            .interpreterProgram
            .eval(orderedDoubleListActivation(interpreterOrder, "wrong", 2.0d))
            .getVal());
    assertThat(nativeOrder).containsExactly("d", "e");
    assertThat(interpreterOrder).containsExactly("d", "e");

    programs = programs("[d, e][0]");
    nativeOrder.clear();
    interpreterOrder.clear();
    assertEquivalent(
        programs
            .nativeProgram
            .eval(orderedDoubleListActivation(nativeOrder, newErr("first"), 2.0d))
            .getVal(),
        programs
            .interpreterProgram
            .eval(orderedDoubleListActivation(interpreterOrder, newErr("first"), 2.0d))
            .getVal());
    assertThat(nativeOrder).containsExactly("d");
    assertThat(interpreterOrder).containsExactly("d");
  }

  static Stream<Evaluation> intListLiteralSizeEvaluations() {
    return Stream.of(
        new Evaluation("size([1, 2, 3])", Map.of()),
        new Evaluation("size([x, y, 3])", Map.of("x", 50_021L, "y", 7L)),
        new Evaluation("[x, y, 3].size()", Map.of("x", 50_021L, "y", 7L)),
        new Evaluation("size([x, y]) + 1", Map.of("x", 50_021L, "y", 7L)),
        new Evaluation(
            "size([x + 1, y + 2, 3]) == target", Map.of("x", 40L, "y", 40L, "target", 3L)),
        new Evaluation("size([x, y])", Map.of("x", newErr("first"), "y", 2L)),
        new Evaluation("size([x, y])", Map.of("x", unknownOf(73L), "y", 2L)),
        new Evaluation("size([x, y])", Map.of("x", 1L, "y", newErr("second"))),
        new Evaluation("size([x, y])", Map.of("x", 1L, "y", unknownOf(74L))),
        new Evaluation(
            "size([x, y])", partialVars(Map.of("x", 1L, "y", 2L), attributePattern("x"))),
        new Evaluation("size([x, y])", Map.of("x", "wrong", "y", 2L)),
        new Evaluation("size([x, y])", Map.of("x", "wrong", "y", newErr("second"))),
        new Evaluation("size([x, y])", Map.of("y", 2L)),
        new Evaluation("size([x + 1, y])", Map.of("x", Long.MAX_VALUE, "y", 2L)));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("intListLiteralSizeEvaluations")
  void evaluatesCheckedIntListLiteralSizesWithoutMaterializingTheList(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void intListLiteralSizePreservesElementEvaluationAndEarlyTermination() {
    Programs programs = programs("size([x, y])");
    List<String> nativeOrder = new ArrayList<>();
    List<String> interpreterOrder = new ArrayList<>();

    assertEquivalent(
        programs.nativeProgram.eval(orderedIntListActivation(nativeOrder, false)).getVal(),
        programs
            .interpreterProgram
            .eval(orderedIntListActivation(interpreterOrder, false))
            .getVal());
    assertThat(nativeOrder).containsExactly("x", "y");
    assertThat(interpreterOrder).containsExactly("x", "y");

    nativeOrder.clear();
    interpreterOrder.clear();
    assertEquivalent(
        programs.nativeProgram.eval(orderedIntListActivation(nativeOrder, true)).getVal(),
        programs
            .interpreterProgram
            .eval(orderedIntListActivation(interpreterOrder, true))
            .getVal());
    assertThat(nativeOrder).containsExactly("x");
    assertThat(interpreterOrder).containsExactly("x");
  }

  static Stream<Evaluation> doubleListLiteralSizeEvaluations() {
    return Stream.of(
        new Evaluation("size([1.0, 2.0, 3.0])", Map.of()),
        new Evaluation("size([d, e, 3.0])", Map.of("d", 50_021.25d, "e", 7.5d)),
        new Evaluation("[d, e, 3.0].size()", Map.of("d", 50_021.25d, "e", 7.5d)),
        new Evaluation("size([d, e]) + 1", Map.of("d", Double.NaN, "e", -0.0d)),
        new Evaluation("size([d, e])", Map.of("d", newErr("first"), "e", 2.0d)),
        new Evaluation("size([d, e])", Map.of("d", unknownOf(75L), "e", 2.0d)),
        new Evaluation(
            "size([d, e])", partialVars(Map.of("d", 1.0d, "e", 2.0d), attributePattern("d"))),
        new Evaluation("size([d, e])", Map.of("d", "wrong", "e", 2.0d)),
        new Evaluation("size([d, e])", Map.of("d", "wrong", "e", newErr("second"))),
        new Evaluation("size([d + 1.0, e])", Map.of("d", "wrong", "e", 2.0d)),
        new Evaluation("size([d, e])", Map.of("e", 2.0d)));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("doubleListLiteralSizeEvaluations")
  void evaluatesCheckedDoubleListLiteralSizesWithoutMaterializingTheList(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  static Stream<Evaluation> stringListLiteralIndexEvaluations() {
    return Stream.of(
        new Evaluation("[s, key, 'last'][0]", Map.of("s", "cel", "key", "other")),
        new Evaluation("[s, key, 'last'][1]", Map.of("s", "zero", "key", "cel")),
        new Evaluation("[s + '-suffix', key][0]", Map.of("s", "cel", "key", "other")),
        new Evaluation(
            "[s, key][1] == wordTarget", Map.of("s", "zero", "key", "cel", "wordTarget", "cel")),
        new Evaluation("[s, key][0]", Map.of("s", stringOf(null), "key", "cel")),
        new Evaluation("[s, key][1]", Map.of("s", stringOf(null), "key", "cel")),
        new Evaluation("[s, key][0]", Map.of("s", 42L, "key", "cel")),
        new Evaluation("[s, key][1]", Map.of("s", 42L, "key", "cel")),
        new Evaluation("[s, key][0]", Map.of("s", 42L, "key", newErr("later"))),
        new Evaluation("[s, key][0]", Map.of("s", newErr("first"), "key", "cel")),
        new Evaluation("[s, key][0]", Map.of("s", unknownOf(78L), "key", "cel")),
        new Evaluation(
            "[s, key][0]", partialVars(Map.of("s", "cel", "key", "other"), attributePattern("s"))),
        new Evaluation("[s, key][-1]", Map.of("s", "cel", "key", "other")),
        new Evaluation("[s, key][2]", Map.of("s", "cel", "key", "other")),
        new Evaluation("[s + '-suffix', key][0]", Map.of("s", 42L, "key", "other")),
        new Evaluation("[s, key][0]", Map.of("key", "cel")));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("stringListLiteralIndexEvaluations")
  void evaluatesCheckedStringListLiteralIndexesWithoutMaterializingTheList(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void stringListLiteralIndexesPreserveConstructionAndBoundsOrdering() {
    for (String expression : List.of("[s, key][0]", "[s, key][2]")) {
      Programs programs = programs(expression);
      List<String> nativeOrder = new ArrayList<>();
      List<String> interpreterOrder = new ArrayList<>();

      assertEquivalent(
          programs
              .nativeProgram
              .eval(
                  orderedStringListLiteralMembershipActivation(
                      nativeOrder, "unused", "cel", "other"))
              .getVal(),
          programs
              .interpreterProgram
              .eval(
                  orderedStringListLiteralMembershipActivation(
                      interpreterOrder, "unused", "cel", "other"))
              .getVal());
      assertThat(nativeOrder).as(expression).containsExactly("s", "key");
      assertThat(interpreterOrder).as(expression).containsExactly("s", "key");
    }

    Programs programs = programs("[s, key][1]");
    List<String> nativeOrder = new ArrayList<>();
    List<String> interpreterOrder = new ArrayList<>();
    assertEquivalent(
        programs
            .nativeProgram
            .eval(orderedStringListLiteralMembershipActivation(nativeOrder, "unused", 42L, "cel"))
            .getVal(),
        programs
            .interpreterProgram
            .eval(
                orderedStringListLiteralMembershipActivation(
                    interpreterOrder, "unused", 42L, "cel"))
            .getVal());
    assertThat(nativeOrder).containsExactly("s", "key");
    assertThat(interpreterOrder).containsExactly("s", "key");

    programs = programs("[s, key][0]");
    nativeOrder.clear();
    interpreterOrder.clear();
    assertEquivalent(
        programs
            .nativeProgram
            .eval(
                orderedStringListLiteralMembershipActivation(
                    nativeOrder, "unused", newErr("first"), "other"))
            .getVal(),
        programs
            .interpreterProgram
            .eval(
                orderedStringListLiteralMembershipActivation(
                    interpreterOrder, "unused", newErr("first"), "other"))
            .getVal());
    assertThat(nativeOrder).containsExactly("s");
    assertThat(interpreterOrder).containsExactly("s");
  }

  @Test
  void nullBackedStringLiteralIndexOperationsUseTheCurrentCompatibilityPath() {
    Programs programs = programs("[s, key][0] + '-suffix'");
    Object input = Map.of("s", stringOf(null), "key", "other");

    Val nativeResult = programs.nativeProgram.eval(input).getVal();
    Val interpreterResult = programs.interpreterProgram.eval(input).getVal();

    assertEquivalent(nativeResult, interpreterResult);
    assertThat(nativeResult).isEqualTo(stringOf("null-suffix"));
  }

  static Stream<Evaluation> stringListLiteralSizeEvaluations() {
    return Stream.of(
        new Evaluation("size(['zero', 'cel', 'last'])", Map.of()),
        new Evaluation("['zero', 'cel', 'last'].size()", Map.of()),
        new Evaluation("size([s, key, 'last'])", Map.of("s", "zero", "key", "cel")),
        new Evaluation("[s, key, 'last'].size()", Map.of("s", "zero", "key", "cel")),
        new Evaluation("size([s, key]) + 1", Map.of("s", stringOf(null), "key", "cel")),
        new Evaluation("size([s, key])", Map.of("s", 42L, "key", "cel")),
        new Evaluation("size([s, key])", Map.of("s", 42L, "key", newErr("later"))),
        new Evaluation("size([s, key])", Map.of("s", newErr("first"), "key", "cel")),
        new Evaluation("size([s, key])", Map.of("s", unknownOf(79L), "key", "cel")),
        new Evaluation(
            "size([s, key])",
            partialVars(Map.of("s", "zero", "key", "cel"), attributePattern("s"))),
        new Evaluation("size([s + '-suffix', key])", Map.of("s", 42L, "key", "cel")),
        new Evaluation("size([s, key])", Map.of("key", "cel")));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("stringListLiteralSizeEvaluations")
  void evaluatesCheckedStringListLiteralSizesWithoutMaterializingTheList(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void stringListLiteralSizePreservesElementEvaluationAndEarlyTermination() {
    Programs programs = programs("size([s, key])");
    List<String> nativeOrder = new ArrayList<>();
    List<String> interpreterOrder = new ArrayList<>();

    assertEquivalent(
        programs
            .nativeProgram
            .eval(
                orderedStringListLiteralMembershipActivation(nativeOrder, "unused", "zero", "cel"))
            .getVal(),
        programs
            .interpreterProgram
            .eval(
                orderedStringListLiteralMembershipActivation(
                    interpreterOrder, "unused", "zero", "cel"))
            .getVal());
    assertThat(nativeOrder).containsExactly("s", "key");
    assertThat(interpreterOrder).containsExactly("s", "key");

    nativeOrder.clear();
    interpreterOrder.clear();
    assertEquivalent(
        programs
            .nativeProgram
            .eval(
                orderedStringListLiteralMembershipActivation(
                    nativeOrder, "unused", newErr("first"), "cel"))
            .getVal(),
        programs
            .interpreterProgram
            .eval(
                orderedStringListLiteralMembershipActivation(
                    interpreterOrder, "unused", newErr("first"), "cel"))
            .getVal());
    assertThat(nativeOrder).containsExactly("s");
    assertThat(interpreterOrder).containsExactly("s");
  }

  @Test
  void unsupportedScalarListLiteralShapesFallBack() {
    for (String expression :
        List.of(
            "[x, y]",
            "[x, y][target]",
            "[x, y][4294967296]",
            "[b, c]",
            "[x, y] == [x, y]",
            "[d, e]",
            "['a', 'b']")) {
      Ast ast = compile(expression);
      Prog program = (Prog) env.program(ast);
      assertCurrentPlan(program, expression);
    }

    for (String expression : List.of("[null][0]", "size([null])", "[null].size()")) {
      Ast ast = compile(expression);
      Prog program = (Prog) env.program(ast);
      Prog established = (Prog) env.program(ast, evalOptions(OptDisableNativeEval));
      assertEstablishedRoot(
          program, expression.contains("[0]") ? "EvalAttr" : "EvalUnary", expression);
      assertEquivalent(program.eval(Map.of()).getVal(), established.eval(Map.of()).getVal());
    }

    Env optionalEnv =
        newEnv(
            optionals(),
            declarations(
                Decls.newVar("x", Decls.Int),
                Decls.newVar("b", Decls.Bool),
                Decls.newVar("wordTarget", Decls.String)));
    Ast optionalAst = compile(optionalEnv, "[?optional.of(x), 2][0]");
    Prog optionalProgram = (Prog) optionalEnv.program(optionalAst);
    assertEstablishedRoot(optionalProgram, "EvalAttr", optionalAst.toString());

    Ast optionalSizeAst = compile(optionalEnv, "size([?optional.of(x), 2])");
    Prog optionalSizeProgram = (Prog) optionalEnv.program(optionalSizeAst);
    assertEstablishedRoot(optionalSizeProgram, "EvalUnary", optionalSizeAst.toString());

    Ast optionalReceiverSizeAst = compile(optionalEnv, "[?optional.of(x), 2].size()");
    Prog optionalReceiverSizeProgram = (Prog) optionalEnv.program(optionalReceiverSizeAst);
    assertEstablishedRoot(
        optionalReceiverSizeProgram, "EvalUnary", optionalReceiverSizeAst.toString());

    Ast optionalMembershipAst = compile(optionalEnv, "wordTarget in [?optional.of('cel'), 'last']");
    Prog optionalMembershipProgram = (Prog) optionalEnv.program(optionalMembershipAst);
    assertEstablishedRoot(
        optionalMembershipProgram, "EvalBinary", optionalMembershipAst.toString());

    Ast optionalStringIndexAst = compile(optionalEnv, "[?optional.of(wordTarget), 'last'][0]");
    Prog optionalStringIndexProgram = (Prog) optionalEnv.program(optionalStringIndexAst);
    assertEstablishedRoot(
        optionalStringIndexProgram, "EvalAttr", optionalStringIndexAst.toString());

    Ast optionalStringSizeAst = compile(optionalEnv, "size([?optional.of(wordTarget), 'last'])");
    Prog optionalStringSizeProgram = (Prog) optionalEnv.program(optionalStringSizeAst);
    assertEstablishedRoot(optionalStringSizeProgram, "EvalUnary", optionalStringSizeAst.toString());

    Ast optionalBoolIndexAst = compile(optionalEnv, "[?optional.of(b), true][0]");
    Prog optionalBoolIndexProgram = (Prog) optionalEnv.program(optionalBoolIndexAst);
    assertEstablishedRoot(optionalBoolIndexProgram, "EvalAttr", optionalBoolIndexAst.toString());

    Ast optionalBoolSizeAst = compile(optionalEnv, "size([?optional.of(b), true])");
    Prog optionalBoolSizeProgram = (Prog) optionalEnv.program(optionalBoolSizeAst);
    assertEstablishedRoot(optionalBoolSizeProgram, "EvalUnary", optionalBoolSizeAst.toString());

    Ast optionalBoolReceiverSizeAst = compile(optionalEnv, "[?optional.of(b), true].size()");
    Prog optionalBoolReceiverSizeProgram = (Prog) optionalEnv.program(optionalBoolReceiverSizeAst);
    assertEstablishedRoot(
        optionalBoolReceiverSizeProgram, "EvalUnary", optionalBoolReceiverSizeAst.toString());
  }

  static Stream<Evaluation> constantStringListIndexEvaluations() {
    String[] raw = {"zero", "cel", null};
    Val adapted = DefaultTypeAdapter.Instance.nativeToValue(raw);
    Val adaptedValues =
        DefaultTypeAdapter.Instance.nativeToValue(
            new Val[] {stringOf("zero"), stringOf("cel"), stringOf(null)});
    List<Object> javaNull = new ArrayList<>();
    javaNull.add("zero");
    javaNull.add(null);
    return Stream.of(
        new Evaluation("words[1]", Map.of("words", raw)),
        new Evaluation("words[1] == wordTarget", Map.of("words", raw, "wordTarget", "cel")),
        new Evaluation("words[1] + '!'", Map.of("words", raw)),
        new Evaluation("words[1] < 'zzz'", Map.of("words", raw)),
        new Evaluation("true ? words[1] : 'unused'", Map.of("words", raw)),
        new Evaluation("words[2]", Map.of("words", raw)),
        new Evaluation("words[2] + ' suffix'", Map.of("words", raw)),
        new Evaluation("true ? words[2] : 'unused'", Map.of("words", raw)),
        new Evaluation("words[1]", Map.of("words", List.of("zero", "cel"))),
        new Evaluation("words[1]", Map.of("words", adapted)),
        new Evaluation("words[1]", Map.of("words", adaptedValues)),
        new Evaluation("words[2]", Map.of("words", adaptedValues)),
        new Evaluation("words[1]", Map.of("words", javaNull)),
        new Evaluation("words[-1]", Map.of("words", raw)),
        new Evaluation("words[3]", Map.of("words", raw)),
        new Evaluation("words[0]", Map.of("words", List.of(42L))),
        new Evaluation("words[0]", Map.of("words", "wrong container")),
        new Evaluation("words[0]", Map.of("words", newErr("list error"))),
        new Evaluation("words[0]", Map.of("words", unknownOf(97L))),
        new Evaluation("words[0]", Map.of()));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("constantStringListIndexEvaluations")
  void evaluatesConstantStringListIndexesLikeTheExistingInterpreter(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  static Stream<Evaluation> listMembershipEvaluations() {
    String[] rawWords = {"zero", "cel", null, "last"};
    Val adaptedWords = DefaultTypeAdapter.Instance.nativeToValue(rawWords);
    Map<String, Object> nullList = new java.util.HashMap<>();
    nullList.put("wordTarget", "cel");
    nullList.put("words", null);
    return Stream.of(
        new Evaluation("wordTarget in words", Map.of("wordTarget", "zero", "words", rawWords)),
        new Evaluation("wordTarget in words", Map.of("wordTarget", "cel", "words", rawWords)),
        new Evaluation("wordTarget in words", Map.of("wordTarget", "absent", "words", rawWords)),
        new Evaluation(
            "wordTarget in words", Map.of("wordTarget", "absent", "words", adaptedWords)),
        new Evaluation(
            "wordTarget in words", Map.of("wordTarget", "cel", "words", List.of("zero", "cel"))),
        new Evaluation(
            "wordTarget in words",
            Map.of("wordTarget", "absent", "words", List.of("zero", newErr("element")))),
        new Evaluation(
            "wordTarget in words",
            Map.of("wordTarget", "absent", "words", List.of("zero", unknownOf(71L)))),
        new Evaluation("wordTarget in words", Map.of("wordTarget", "cel", "words", List.of())),
        new Evaluation(
            "wordTarget in words", Map.of("wordTarget", "cel", "words", "wrong container")),
        new Evaluation(
            "wordTarget in words", Map.of("wordTarget", "cel", "words", newErr("list error"))),
        new Evaluation("wordTarget in words", Map.of("wordTarget", "cel", "words", unknownOf(72L))),
        new Evaluation("wordTarget in words", Map.of("wordTarget", 42L, "words", rawWords)),
        new Evaluation(
            "wordTarget in words", Map.of("wordTarget", newErr("needle"), "words", rawWords)),
        new Evaluation(
            "wordTarget in words", Map.of("wordTarget", newErr("needle"), "words", newErr("list"))),
        new Evaluation(
            "wordTarget in words", Map.of("wordTarget", unknownOf(73L), "words", newErr("list"))),
        new Evaluation("wordTarget in words", nullList),
        new Evaluation("wordTarget in words", Map.of("wordTarget", "cel")),
        new Evaluation(
            "wordTarget in words",
            partialVars(
                Map.of("wordTarget", "cel", "words", rawWords), attributePattern("wordTarget"))),
        new Evaluation(
            "wordTarget in words",
            partialVars(
                Map.of("wordTarget", "cel", "words", rawWords), attributePattern("words"))));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("listMembershipEvaluations")
  void evaluatesListMembershipLikeTheExistingInterpreter(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  static Stream<Evaluation> stringListLiteralMembershipEvaluations() {
    return Stream.of(
        new Evaluation("'cel' in ['zero', 'cel', 'last']", Map.of()),
        new Evaluation("wordTarget in []", Map.of("wordTarget", "cel")),
        new Evaluation("wordTarget in ['zero', 'cel']", Map.of("wordTarget", "cel")),
        new Evaluation("wordTarget in ['zero', 'cel']", Map.of("wordTarget", "absent")),
        new Evaluation(
            "wordTarget in [s, key, 'last']",
            Map.of("wordTarget", "cel", "s", "zero", "key", "cel")),
        new Evaluation(
            "wordTarget in [s + '-suffix', key]",
            Map.of("wordTarget", "cel-suffix", "s", "cel", "key", "last")),
        new Evaluation(
            "wordTarget in [s, key]",
            Map.of("wordTarget", "cel", "s", newErr("first"), "key", "cel")),
        new Evaluation(
            "wordTarget in [s, key]",
            Map.of("wordTarget", "cel", "s", unknownOf(76L), "key", "cel")),
        new Evaluation(
            "wordTarget in [s, key]",
            partialVars(
                Map.of("wordTarget", "cel", "s", "zero", "key", "cel"), attributePattern("s"))),
        new Evaluation(
            "wordTarget in [s, key]",
            Map.of("wordTarget", "cel", "s", "cel", "key", newErr("later"))),
        new Evaluation(
            "wordTarget in [s, key]",
            Map.of("wordTarget", newErr("needle"), "s", "zero", "key", newErr("list"))),
        new Evaluation(
            "wordTarget in [s, key]",
            Map.of("wordTarget", unknownOf(77L), "s", "zero", "key", newErr("list"))),
        new Evaluation("wordTarget in [s]", Map.of("wordTarget", 42L, "s", 42L)),
        new Evaluation("wordTarget in [s, 'cel']", Map.of("wordTarget", "cel", "s", 42L)),
        new Evaluation(
            "wordTarget in [s, 'last']", Map.of("wordTarget", "cel", "s", stringOf(null))),
        new Evaluation(
            "wordTarget in [s, key]",
            Map.of("wordTarget", 42L, "s", "zero", "key", newErr("list"))),
        new Evaluation("wordTarget in [s + '-suffix']", Map.of("wordTarget", "cel", "s", 42L)),
        new Evaluation("wordTarget in [s]", Map.of("wordTarget", "cel")),
        new Evaluation(
            "wordTarget in [s]",
            partialVars(Map.of("wordTarget", "cel", "s", "cel"), attributePattern("wordTarget"))));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("stringListLiteralMembershipEvaluations")
  void evaluatesStringListLiteralMembershipWithoutMaterializingTheList(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void stringListLiteralMembershipPreservesConstructionAndOperandOrdering() {
    Programs programs = programs("wordTarget in [s, key]");
    List<String> nativeOrder = new ArrayList<>();
    List<String> interpreterOrder = new ArrayList<>();

    assertEquivalent(
        programs
            .nativeProgram
            .eval(orderedStringListLiteralMembershipActivation(nativeOrder, "cel", "cel", "last"))
            .getVal(),
        programs
            .interpreterProgram
            .eval(
                orderedStringListLiteralMembershipActivation(
                    interpreterOrder, "cel", "cel", "last"))
            .getVal());
    assertThat(nativeOrder).containsExactly("wordTarget", "s", "key");
    assertThat(interpreterOrder).containsExactly("wordTarget", "s", "key");

    nativeOrder.clear();
    interpreterOrder.clear();
    assertEquivalent(
        programs
            .nativeProgram
            .eval(
                orderedStringListLiteralMembershipActivation(
                    nativeOrder, newErr("needle"), "zero", "last"))
            .getVal(),
        programs
            .interpreterProgram
            .eval(
                orderedStringListLiteralMembershipActivation(
                    interpreterOrder, newErr("needle"), "zero", "last"))
            .getVal());
    assertThat(nativeOrder).containsExactly("wordTarget", "s", "key");
    assertThat(interpreterOrder).containsExactly("wordTarget", "s", "key");

    nativeOrder.clear();
    interpreterOrder.clear();
    assertEquivalent(
        programs
            .nativeProgram
            .eval(
                orderedStringListLiteralMembershipActivation(
                    nativeOrder, "cel", newErr("first"), "last"))
            .getVal(),
        programs
            .interpreterProgram
            .eval(
                orderedStringListLiteralMembershipActivation(
                    interpreterOrder, "cel", newErr("first"), "last"))
            .getVal());
    assertThat(nativeOrder).containsExactly("wordTarget", "s");
    assertThat(interpreterOrder).containsExactly("wordTarget", "s");
  }

  @Test
  void nullBackedStringNeedleUsesTheCurrentLiteralMembershipCompatibilityPath() {
    Programs programs = programs("wordTarget in [s, 'cel']");
    Object input = Map.of("wordTarget", stringOf(null), "s", "zero");

    Throwable nativeFailure = catchThrowable(() -> programs.nativeProgram.eval(input));
    Throwable interpreterFailure = catchThrowable(() -> programs.interpreterProgram.eval(input));

    assertThat(nativeFailure).isNotNull();
    assertThat(interpreterFailure).isNotNull();
    assertThat(nativeFailure.getClass()).isEqualTo(interpreterFailure.getClass());
    assertThat(nativeFailure.getMessage()).isEqualTo(interpreterFailure.getMessage());
  }

  @Test
  void listMembershipEvaluatesBothOperandsFromLeftToRight() {
    Programs programs = programs("wordTarget in words");
    List<String> nativeOrder = new ArrayList<>();
    List<String> interpreterOrder = new ArrayList<>();

    Val nativeResult =
        programs.nativeProgram.eval(orderedMembershipActivation(nativeOrder)).getVal();
    Val interpreterResult =
        programs.interpreterProgram.eval(orderedMembershipActivation(interpreterOrder)).getVal();

    assertEquivalent(nativeResult, interpreterResult);
    assertThat(nativeOrder).containsExactly("wordTarget", "words");
    assertThat(interpreterOrder).containsExactly("wordTarget", "words");
  }

  @Test
  void nullBackedStringNeedleUsesTheCurrentCompatibilityPath() {
    Programs programs = programs("wordTarget in words");
    Object input = Map.of("wordTarget", stringOf(null), "words", new String[] {"zero", null});

    Throwable nativeFailure = catchThrowable(() -> programs.nativeProgram.eval(input));
    Throwable interpreterFailure = catchThrowable(() -> programs.interpreterProgram.eval(input));

    assertThat(nativeFailure).isNotNull();
    assertThat(interpreterFailure).isNotNull();
    assertThat(nativeFailure.getClass()).isEqualTo(interpreterFailure.getClass());
    assertThat(nativeFailure.getMessage()).isEqualTo(interpreterFailure.getMessage());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @ValueSource(strings = {"words[1] == 'value'", "words[1] < 'value'"})
  void nullBackedStringListOperationsUseTheCurrentCompatibilityPath(String expression) {
    Programs programs = programs(expression);
    Object input = Map.of("words", new String[] {"zero", null});

    Throwable nativeFailure = catchThrowable(() -> programs.nativeProgram.eval(input));
    Throwable interpreterFailure = catchThrowable(() -> programs.interpreterProgram.eval(input));

    assertThat(nativeFailure).isNotNull();
    assertThat(interpreterFailure).isNotNull();
    assertThat(nativeFailure.getClass()).isEqualTo(interpreterFailure.getClass());
    assertThat(nativeFailure.getMessage()).isEqualTo(interpreterFailure.getMessage());
  }

  @Test
  void constantListIndexesResolveEachOperandOnce() {
    assertResolutionCounts(
        "numbers[1] == target",
        Map.of("numbers", new long[] {7L, 50_021L}, "target", 50_021L),
        Map.of("numbers", 1, "target", 1));
    assertResolutionCounts(
        "words[1] == wordTarget",
        Map.of("words", new String[] {"zero", "cel"}, "wordTarget", "cel"),
        Map.of("words", 1, "wordTarget", 1));
  }

  @Test
  void listIndexesPreservePartialMatchingAndDynamicIndexesUseIntegratedSources() {
    Ast ast = compile("numbers[1] == target");
    Prog nativeProgram = (Prog) env.program(ast);
    Interpretable current = undecoratedCurrent(nativeProgram, ast);
    Object input =
        partialVars(
            Map.of("numbers", new long[] {7L, 50_021L}, "target", 50_021L),
            attributePattern("numbers").qualInt(1L));

    assertIntegratedPlan(nativeProgram, "numbers[1] == target");
    assertEquivalent(nativeProgram.eval(input).getVal(), current.eval(newActivation(input)));

    Prog dynamic = (Prog) env.program(compile("numbers[x]"));
    assertIntegratedPlan(dynamic, "numbers[x]");

    for (String expression : List.of("numbers[4294967296]", "numbers[-4294967297]")) {
      Prog outOfIntRange = (Prog) env.program(compile(expression));
      assertCurrentPlan(outOfIntRange, expression);
    }

    Ast stringAst = compile("words[1] == wordTarget");
    Prog stringNative = (Prog) env.program(stringAst);
    Interpretable stringCurrent = undecoratedCurrent(stringNative, stringAst);
    Object stringInput =
        partialVars(
            Map.of("words", new String[] {"zero", "cel"}, "wordTarget", "cel"),
            attributePattern("words").qualInt(1L));

    assertIntegratedPlan(stringNative, "words[1] == wordTarget");
    assertEquivalent(
        stringNative.eval(stringInput).getVal(), stringCurrent.eval(newActivation(stringInput)));
  }

  @Test
  void unsupportedMembershipShapesFallBack() {
    Env boolListEnv =
        newEnv(
            declarations(
                Decls.newVar("needle", Decls.Bool),
                Decls.newVar("values", Decls.newListType(Decls.Bool))));
    for (String expression :
        List.of(
            "target in numbers",
            "d in doubles",
            "target in [1, 2]",
            "target in (b ? numbers : numbers)",
            "key in labels")) {
      Ast ast = compile(expression);
      Prog program = (Prog) env.program(ast);
      assertCurrentPlan(program, expression);
    }
    Ast boolAst = compile(boolListEnv, "needle in values");
    Prog boolProgram = (Prog) boolListEnv.program(boolAst);
    assertCurrentPlan(boolProgram);
  }

  @TestFactory
  Stream<DynamicTest> evaluatesGeneratedDynamicAndWrappedProtobufStringSelectors()
      throws Exception {
    String messageType = TestAllTypes.getDescriptor().getFullName();
    Env protoEnv =
        newEnv(
            types(TestAllTypes.getDefaultInstance()),
            declarations(
                Decls.newVar("msg", Decls.newObjectType(messageType)),
                Decls.newVar("target", Decls.Int)));
    TestAllTypes message =
        TestAllTypes.newBuilder().setSingleInt64(50_021L).setSingleString("cel").build();
    DynamicMessage dynamic =
        DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), message.toByteString());
    Val wrapped = protoEnv.getTypeAdapter().nativeToValue(message);

    Map<String, Object> presentNull = new java.util.HashMap<>();
    presentNull.put("msg", null);

    return Stream.of(
        dynamicTest(
            "generated message equality",
            () -> assertExpression(protoEnv, "msg.single_string == 'cel'", Map.of("msg", message))),
        dynamicTest(
            "dynamic message",
            () -> assertExpression(protoEnv, "msg.single_string", Map.of("msg", dynamic))),
        dynamicTest(
            "wrapped message",
            () -> assertExpression(protoEnv, "msg.single_string", Map.of("msg", wrapped))),
        dynamicTest(
            "default message",
            () ->
                assertExpression(
                    protoEnv,
                    "msg.single_string",
                    Map.of("msg", TestAllTypes.getDefaultInstance()))),
        dynamicTest(
            "wrong runtime object",
            () -> assertExpression(protoEnv, "msg.single_string", Map.of("msg", "wrong object"))),
        dynamicTest(
            "message error",
            () ->
                assertExpression(
                    protoEnv, "msg.single_string", Map.of("msg", newErr("message error")))),
        dynamicTest(
            "message unknown",
            () -> assertExpression(protoEnv, "msg.single_string", Map.of("msg", unknownOf(91L)))),
        dynamicTest(
            "absent message", () -> assertExpression(protoEnv, "msg.single_string", Map.of())),
        dynamicTest(
            "present null message",
            () -> assertExpression(protoEnv, "msg.single_string", presentNull)));
  }

  @Test
  void evaluatesGeneratedAndDynamicProtobufPrimitiveSelectors() throws Exception {
    String messageType = TestAllTypes.getDescriptor().getFullName();
    Env protoEnv =
        newEnv(
            types(TestAllTypes.getDefaultInstance()),
            declarations(
                Decls.newVar("msg", Decls.newObjectType(messageType)),
                Decls.newVar("target", Decls.Int)));
    TestAllTypes generated =
        TestAllTypes.newBuilder()
            .setSingleBool(true)
            .setSingleInt64(50_021L)
            .setSingleDouble(-0.0d)
            .build();
    DynamicMessage dynamic =
        DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), generated.toByteString());

    for (Object message : List.of(generated, dynamic)) {
      assertExpression(protoEnv, "msg.single_bool", Map.of("msg", message));
      assertExpression(
          protoEnv, "msg.single_int64 == target", Map.of("msg", message, "target", 50_021L));
      assertExpression(protoEnv, "msg.single_double == -0.0", Map.of("msg", message));
    }
  }

  @Test
  void protobufSelectorsPreservePartialQualifierMatching() {
    String messageType = TestAllTypes.getDescriptor().getFullName();
    Env protoEnv =
        newEnv(
            types(TestAllTypes.getDefaultInstance()),
            declarations(Decls.newVar("msg", Decls.newObjectType(messageType))));
    Ast ast = compile(protoEnv, "msg.single_string == 'cel'");
    Prog nativeProgram = (Prog) protoEnv.program(ast);
    Prog interpreterProgram =
        (Prog) protoEnv.program(ast, evalOptions(OptPartialEval, OptDisableNativeEval));
    Object input =
        partialVars(
            Map.of("msg", TestAllTypes.newBuilder().setSingleString("cel").build()),
            attributePattern("msg").qualString("single_string"));

    assertIntegratedPlan(nativeProgram, "msg.single_string == 'cel'");
    assertEquivalent(nativeProgram.eval(input).getVal(), interpreterProgram.eval(input).getVal());
  }

  @TestFactory
  Stream<DynamicTest> preservesTerminalOrderingAndShortCircuitSuppression() {
    Val error = newErr("terminal error");
    Val unknown = unknownOf(123L);

    return Stream.of(
        dynamicTest(
            "false right operand suppresses left error for logical and",
            () -> assertExpression("b && c", activation(Map.of("b", error, "c", false)))),
        dynamicTest(
            "false right operand suppresses left unknown for logical and",
            () -> assertExpression("b && c", activation(Map.of("b", unknown, "c", false)))),
        dynamicTest(
            "true right operand suppresses left error for logical or",
            () -> assertExpression("b || c", activation(Map.of("b", error, "c", true)))),
        dynamicTest(
            "true right operand suppresses left unknown for logical or",
            () -> assertExpression("b || c", activation(Map.of("b", unknown, "c", true)))),
        dynamicTest(
            "left error precedes right unknown for equality",
            () -> assertExpression("b == c", activation(Map.of("b", error, "c", unknown)))),
        dynamicTest(
            "left unknown precedes right error for equality",
            () -> assertExpression("b == c", activation(Map.of("b", unknown, "c", error)))),
        dynamicTest(
            "conditional propagates an error condition",
            () -> assertExpression("b ? x : y", activation(Map.of("b", error, "x", 1L, "y", 2L)))),
        dynamicTest(
            "conditional propagates an unknown condition",
            () ->
                assertExpression("b ? x : y", activation(Map.of("b", unknown, "x", 1L, "y", 2L)))));
  }

  @Test
  void shortCircuitAndConditionalReachOnlyRequiredIdentifiers() {
    assertResolutionCounts("b && c", Map.of("b", false, "c", true), Map.of("b", 1, "c", 0));
    assertResolutionCounts("b || c", Map.of("b", true, "c", false), Map.of("b", 1, "c", 0));
    assertResolutionCounts(
        "b ? x : y", Map.of("b", true, "x", 1L, "y", 2L), Map.of("b", 1, "x", 1, "y", 0));
    assertResolutionCounts(
        "b ? x : y", Map.of("b", false, "x", 1L, "y", 2L), Map.of("b", 1, "x", 0, "y", 1));
  }

  static Stream<Evaluation> numericBoundaryEvaluations() {
    return Stream.of(
        new Evaluation("x + y", Map.of("x", Long.MAX_VALUE, "y", 1L)),
        new Evaluation("x - y", Map.of("x", Long.MIN_VALUE, "y", 1L)),
        new Evaluation("x * y", Map.of("x", Long.MAX_VALUE, "y", 2L)),
        new Evaluation("-x", Map.of("x", Long.MIN_VALUE)),
        new Evaluation("x / y", Map.of("x", Long.MIN_VALUE, "y", -1L)),
        new Evaluation("x % y", Map.of("x", 10L, "y", 0L)),
        new Evaluation("d + 1.0", Map.of("d", Double.POSITIVE_INFINITY)),
        new Evaluation("d - 1.0", Map.of("d", Double.NEGATIVE_INFINITY)),
        new Evaluation("d < 0.0", Map.of("d", Double.NaN)),
        new Evaluation("d >= -0.0", Map.of("d", 0.0d)),
        new Evaluation("d == d", Map.of("d", Double.NaN)));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("numericBoundaryEvaluations")
  void coversIntegerBoundariesAndDoubleSpecialValues(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void customAdapterAndUntrustedStandardLookingOverloadFallBack() {
    TypeAdapter customAdapter = DefaultTypeAdapter.Instance::nativeToValue;
    Env adapterEnv =
        newEnv(customTypeAdapter(customAdapter), declarations(Decls.newVar("x", Decls.Int)));
    Prog adapterProgram = (Prog) adapterEnv.program(compile(adapterEnv, "x + 1"));
    assertCurrentPlan(adapterProgram);

    Library.StdLibrary standard = new Library.StdLibrary();
    List<EnvOption> compileOptions = new ArrayList<>(standard.getCompileOptions());
    compileOptions.add(
        declarations(
            Decls.newVar("x", Decls.Int),
            Decls.newVar("wordTarget", Decls.String),
            Decls.newVar("words", Decls.newListType(Decls.String)),
            Decls.newVar("target", Decls.Int),
            Decls.newVar("numbers", Decls.newListType(Decls.Int))));
    Env untrustedEnv = newCustomEnv(compileOptions.toArray(EnvOption[]::new));
    Overload add =
        Overload.binary(Operator.Add, Trait.AdderType, (left, right) -> ((Adder) left).add(right));
    Prog untrustedProgram =
        (Prog) untrustedEnv.program(compile(untrustedEnv, "x + 1"), functions(add));
    assertCurrentPlan(untrustedProgram);
    assertThat(untrustedProgram.eval(Map.of("x", 41L)).getVal().intValue()).isEqualTo(42L);

    Ast mappedExistsAst =
        compile(
            untrustedEnv, "numbers.map(value, value + target).exists(mapped, mapped == target)");
    Prog untrustedMappedExists = (Prog) untrustedEnv.program(mappedExistsAst, functions(add));
    assertCurrentPlan(untrustedMappedExists);

    Ast mappedAllAst =
        compile(untrustedEnv, "numbers.map(value, value + target).all(mapped, mapped != target)");
    Prog untrustedMappedAll = (Prog) untrustedEnv.program(mappedAllAst, functions(add));
    assertCurrentPlan(untrustedMappedAll);

    Ast mappedExistsOneAst =
        compile(
            untrustedEnv,
            "numbers.map(value, value + target).exists_one(mapped, mapped == target)");
    Prog untrustedMappedExistsOne = (Prog) untrustedEnv.program(mappedExistsOneAst, functions(add));
    assertCurrentPlan(untrustedMappedExistsOne);

    Overload membership = Overload.binary(Overloads.InList, (left, right) -> False);
    Ast membershipAst = compile(untrustedEnv, "wordTarget in words");
    Prog untrustedMembership = (Prog) untrustedEnv.program(membershipAst, functions(membership));
    assertCurrentPlan(untrustedMembership);
    assertThat(
            untrustedMembership
                .eval(Map.of("wordTarget", "cel", "words", List.of("cel")))
                .getVal()
                .booleanValue())
        .isFalse();

    Ast literalMembershipAst = compile(untrustedEnv, "wordTarget in ['cel']");
    Prog untrustedLiteralMembership =
        (Prog) untrustedEnv.program(literalMembershipAst, functions(membership));
    assertEstablishedRoot(
        untrustedLiteralMembership, "EvalBinary", "literal membership replacement");
    assertThat(untrustedLiteralMembership.eval(Map.of("wordTarget", "cel")).getVal().booleanValue())
        .isFalse();

    Ast mappedMembershipAst = compile(untrustedEnv, "wordTarget in words.map(value, value)");
    Prog untrustedMappedMembership =
        (Prog) untrustedEnv.program(mappedMembershipAst, functions(membership));
    assertCurrentPlan(untrustedMappedMembership);
    assertThat(
            untrustedMappedMembership
                .eval(Map.of("wordTarget", "cel", "words", List.of("cel")))
                .getVal()
                .booleanValue())
        .isFalse();

    Overload index = Overload.binary(Overloads.IndexList, (left, right) -> intOf(99L));
    Ast topLevelIndexAst = compile(untrustedEnv, "numbers[0]");
    Prog untrustedTopLevelIndex = (Prog) untrustedEnv.program(topLevelIndexAst, functions(index));
    assertCurrentPlan(untrustedTopLevelIndex);
    assertThat(untrustedTopLevelIndex.interpretable.getClass().getSimpleName())
        .isNotEqualTo("NativeIsland");
    assertThat(untrustedTopLevelIndex.eval(Map.of("numbers", new long[] {1L})).getVal().intValue())
        .isEqualTo(1L);

    Ast indexAst = compile(untrustedEnv, "[x, 2][0]");
    Prog untrustedIndex = (Prog) untrustedEnv.program(indexAst, functions(index));
    assertEstablishedRoot(untrustedIndex, "EvalAttr", "integer literal index replacement");
    assertThat(untrustedIndex.eval(Map.of("x", 1L)).getVal().intValue()).isEqualTo(1L);

    Ast stringIndexAst = compile(untrustedEnv, "['cel'][0]");
    Prog untrustedStringIndex = (Prog) untrustedEnv.program(stringIndexAst, functions(index));
    assertEstablishedRoot(untrustedStringIndex, "EvalAttr", "string literal index replacement");
    assertThat(untrustedStringIndex.eval(Map.of()).getVal().value()).isEqualTo("cel");

    Ast boolIndexAst = compile(untrustedEnv, "[true, false][0]");
    Prog untrustedBoolIndex = (Prog) untrustedEnv.program(boolIndexAst, functions(index));
    assertEstablishedRoot(untrustedBoolIndex, "EvalAttr", "boolean literal index replacement");
    assertThat(untrustedBoolIndex.eval(Map.of()).getVal().booleanValue()).isTrue();

    Ast mapIndexAst = compile(untrustedEnv, "numbers.map(value, value + target)[0]");
    Prog untrustedMapIndex = (Prog) untrustedEnv.program(mapIndexAst, functions(index));
    assertCurrentPlan(untrustedMapIndex);

    Overload size = Overload.unary(Overloads.SizeList, ignored -> intOf(99L));
    Ast sizeAst = compile(untrustedEnv, "size([x, 2])");
    Prog untrustedSize = (Prog) untrustedEnv.program(sizeAst, functions(size));
    assertEstablishedRoot(untrustedSize, "EvalUnary", "literal global size replacement");
    assertThat(untrustedSize.eval(Map.of("x", 1L)).getVal().intValue()).isEqualTo(99L);

    Ast filterSizeAst = compile(untrustedEnv, "size(numbers.filter(value, value == target))");
    Prog untrustedFilterSize = (Prog) untrustedEnv.program(filterSizeAst, functions(size));
    assertCurrentPlan(untrustedFilterSize);

    Ast mapSizeAst = compile(untrustedEnv, "size(numbers.map(value, value + target))");
    Prog untrustedMapSize = (Prog) untrustedEnv.program(mapSizeAst, functions(size));
    assertCurrentPlan(untrustedMapSize);

    Ast stringSizeAst = compile(untrustedEnv, "size(['cel'])");
    Prog untrustedStringSize = (Prog) untrustedEnv.program(stringSizeAst, functions(size));
    assertEstablishedRoot(
        untrustedStringSize, "EvalUnary", "string literal global size replacement");
    assertThat(untrustedStringSize.eval(Map.of()).getVal().intValue()).isEqualTo(99L);

    Ast boolSizeAst = compile(untrustedEnv, "size([true, false])");
    Prog untrustedBoolSize = (Prog) untrustedEnv.program(boolSizeAst, functions(size));
    assertEstablishedRoot(
        untrustedBoolSize, "EvalUnary", "boolean literal global size replacement");
    assertThat(untrustedBoolSize.eval(Map.of()).getVal().intValue()).isEqualTo(99L);

    Overload receiverSize = Overload.unary(Overloads.SizeListInst, ignored -> intOf(99L));
    Ast receiverSizeAst = compile(untrustedEnv, "[x, 2].size()");
    Prog untrustedReceiverSize =
        (Prog) untrustedEnv.program(receiverSizeAst, functions(receiverSize));
    assertEstablishedRoot(untrustedReceiverSize, "EvalUnary", "literal receiver size replacement");
    assertThat(untrustedReceiverSize.eval(Map.of("x", 1L)).getVal().intValue()).isEqualTo(99L);

    Ast filterReceiverSizeAst =
        compile(untrustedEnv, "numbers.filter(value, value == target).size()");
    Prog untrustedFilterReceiverSize =
        (Prog) untrustedEnv.program(filterReceiverSizeAst, functions(receiverSize));
    assertCurrentPlan(untrustedFilterReceiverSize);

    Ast mapReceiverSizeAst = compile(untrustedEnv, "numbers.map(value, value + target).size()");
    Prog untrustedMapReceiverSize =
        (Prog) untrustedEnv.program(mapReceiverSizeAst, functions(receiverSize));
    assertCurrentPlan(untrustedMapReceiverSize);

    Ast stringReceiverSizeAst = compile(untrustedEnv, "['cel'].size()");
    Prog untrustedStringReceiverSize =
        (Prog) untrustedEnv.program(stringReceiverSizeAst, functions(receiverSize));
    assertEstablishedRoot(
        untrustedStringReceiverSize, "EvalUnary", "string literal receiver size replacement");
    assertThat(untrustedStringReceiverSize.eval(Map.of()).getVal().intValue()).isEqualTo(99L);

    Ast boolReceiverSizeAst = compile(untrustedEnv, "[true, false].size()");
    Prog untrustedBoolReceiverSize =
        (Prog) untrustedEnv.program(boolReceiverSizeAst, functions(receiverSize));
    assertEstablishedRoot(
        untrustedBoolReceiverSize, "EvalUnary", "boolean literal receiver size replacement");
    assertThat(untrustedBoolReceiverSize.eval(Map.of()).getVal().intValue()).isEqualTo(99L);
  }

  @Test
  void customLibraryWithoutNativeDescriptorRemainsOnTheCurrentEvaluator() {
    Env customEnv =
        newEnv(Library.Lib(new IncrementLibrary()), declarations(Decls.newVar("x", Decls.Int)));
    Prog program = (Prog) customEnv.program(compile(customEnv, "increment(x) + 1"));

    assertCurrentPlan(program);
    assertThat(program.eval(Map.of("x", 40L)).getVal().intValue()).isEqualTo(42L);
  }

  @Test
  void scalarAdapterCapabilityDoesNotImplyPrimitiveArraySemantics() {
    StandardScalarTypeAdapter scalarOnlyAdapter =
        value ->
            value instanceof long[] || value instanceof String[]
                ? newErr("custom primitive array")
                : DefaultTypeAdapter.Instance.nativeToValue(value);
    Env adapterEnv =
        newEnv(
            customTypeAdapter(scalarOnlyAdapter),
            declarations(
                Decls.newVar("b", Decls.Bool),
                Decls.newVar("d", Decls.Double),
                Decls.newVar("numbers", Decls.newListType(Decls.Int)),
                Decls.newVar("wordTarget", Decls.String),
                Decls.newVar("words", Decls.newListType(Decls.String))));
    Ast ast = compile(adapterEnv, "numbers[0]");
    Prog nativeProgram = (Prog) adapterEnv.program(ast);
    Interpretable current = undecoratedCurrent(nativeProgram, ast);
    Object input = Map.of("numbers", new long[] {50_021L});

    assertCurrentPlan(nativeProgram, "numbers[0]");
    assertEquivalent(nativeProgram.eval(input).getVal(), current.eval(newActivation(input)));

    Ast membershipAst = compile(adapterEnv, "wordTarget in words");
    Prog membershipProgram = (Prog) adapterEnv.program(membershipAst);
    Interpretable currentMembership = undecoratedCurrent(membershipProgram, membershipAst);
    Object membershipInput = Map.of("wordTarget", "cel", "words", new String[] {"cel"});
    assertCurrentPlan(membershipProgram, "wordTarget in words");
    assertEquivalent(
        membershipProgram.eval(membershipInput).getVal(),
        currentMembership.eval(newActivation(membershipInput)));

    Ast literalIndexAst = compile(adapterEnv, "[1, 2][0]");
    Prog literalIndexProgram = (Prog) adapterEnv.program(literalIndexAst);
    assertEstablishedRoot(literalIndexProgram, "EvalAttr", "scalar-only adapter literal index");

    Ast literalSizeAst = compile(adapterEnv, "size([1, 2])");
    Prog literalSizeProgram = (Prog) adapterEnv.program(literalSizeAst);
    assertEstablishedRoot(literalSizeProgram, "EvalUnary", "scalar-only adapter literal size");

    Ast doubleLiteralIndexAst = compile(adapterEnv, "[d, 2.0][0]");
    Prog doubleLiteralIndexProgram = (Prog) adapterEnv.program(doubleLiteralIndexAst);
    assertEstablishedRoot(
        doubleLiteralIndexProgram, "EvalAttr", "scalar-only adapter double literal index");

    Ast receiverLiteralSizeAst = compile(adapterEnv, "[1, 2].size()");
    Prog receiverLiteralSizeProgram = (Prog) adapterEnv.program(receiverLiteralSizeAst);
    assertEstablishedRoot(
        receiverLiteralSizeProgram, "EvalUnary", "scalar-only adapter receiver literal size");

    Ast literalMembershipAst = compile(adapterEnv, "wordTarget in ['cel']");
    Prog literalMembershipProgram = (Prog) adapterEnv.program(literalMembershipAst);
    assertEstablishedRoot(
        literalMembershipProgram, "EvalBinary", "scalar-only adapter literal membership");

    Ast stringLiteralIndexAst = compile(adapterEnv, "['cel'][0]");
    Prog stringLiteralIndexProgram = (Prog) adapterEnv.program(stringLiteralIndexAst);
    assertEstablishedRoot(
        stringLiteralIndexProgram, "EvalAttr", "scalar-only adapter string literal index");

    Ast stringLiteralSizeAst = compile(adapterEnv, "size(['cel'])");
    Prog stringLiteralSizeProgram = (Prog) adapterEnv.program(stringLiteralSizeAst);
    assertEstablishedRoot(
        stringLiteralSizeProgram, "EvalUnary", "scalar-only adapter string literal size");

    Ast stringLiteralReceiverSizeAst = compile(adapterEnv, "['cel'].size()");
    Prog stringLiteralReceiverSizeProgram = (Prog) adapterEnv.program(stringLiteralReceiverSizeAst);
    assertEstablishedRoot(
        stringLiteralReceiverSizeProgram,
        "EvalUnary",
        "scalar-only adapter string literal receiver size");

    Ast boolLiteralIndexAst = compile(adapterEnv, "[b, true][0]");
    Prog boolLiteralIndexProgram = (Prog) adapterEnv.program(boolLiteralIndexAst);
    assertEstablishedRoot(
        boolLiteralIndexProgram, "EvalAttr", "scalar-only adapter boolean literal index");

    Ast boolLiteralSizeAst = compile(adapterEnv, "size([b, true])");
    Prog boolLiteralSizeProgram = (Prog) adapterEnv.program(boolLiteralSizeAst);
    assertEstablishedRoot(
        boolLiteralSizeProgram, "EvalUnary", "scalar-only adapter boolean literal size");

    Ast boolLiteralReceiverSizeAst = compile(adapterEnv, "[b, true].size()");
    Prog boolLiteralReceiverSizeProgram = (Prog) adapterEnv.program(boolLiteralReceiverSizeAst);
    assertEstablishedRoot(
        boolLiteralReceiverSizeProgram,
        "EvalUnary",
        "scalar-only adapter boolean literal receiver size");

    Ast mappedSizeAst = compile(adapterEnv, "size(numbers.map(value, value + 1))");
    Prog mappedSizeProgram = (Prog) adapterEnv.program(mappedSizeAst);
    assertCurrentPlan(mappedSizeProgram, "scalar-only adapter mapped size");
    assertEquivalent(
        mappedSizeProgram.eval(input).getVal(),
        undecoratedCurrent(mappedSizeProgram, mappedSizeAst).eval(newActivation(input)));

    Ast mappedIndexAst = compile(adapterEnv, "numbers.map(value, value + 1)[0]");
    Prog mappedIndexProgram = (Prog) adapterEnv.program(mappedIndexAst);
    assertCurrentPlan(mappedIndexProgram, "scalar-only adapter mapped index");
    assertEquivalent(
        mappedIndexProgram.eval(input).getVal(),
        undecoratedCurrent(mappedIndexProgram, mappedIndexAst).eval(newActivation(input)));

    Ast mappedMembershipAst = compile(adapterEnv, "wordTarget in words.map(value, value)");
    Prog mappedMembershipProgram = (Prog) adapterEnv.program(mappedMembershipAst);
    assertCurrentPlan(mappedMembershipProgram, "scalar-only adapter mapped membership");
    assertEquivalent(
        mappedMembershipProgram.eval(membershipInput).getVal(),
        undecoratedCurrent(mappedMembershipProgram, mappedMembershipAst)
            .eval(newActivation(membershipInput)));

    Ast mappedQuantifierAst =
        compile(adapterEnv, "numbers.map(value, value + 1).exists(mapped, mapped == 2)");
    Prog mappedQuantifierProgram = (Prog) adapterEnv.program(mappedQuantifierAst);
    assertCurrentPlan(mappedQuantifierProgram, "scalar-only adapter mapped quantifier");
    assertEquivalent(
        mappedQuantifierProgram.eval(input).getVal(),
        undecoratedCurrent(mappedQuantifierProgram, mappedQuantifierAst)
            .eval(newActivation(input)));
  }

  @Test
  void topLevelListIndexesPreservePresentNullSourcesAndTypedSlowPaths() {
    Map<String, Object> nullNumbers = new java.util.HashMap<>();
    nullNumbers.put("numbers", null);
    assertExpression("numbers[0]", nullNumbers);

    Env typedListEnv =
        newEnv(
            declarations(
                Decls.newVar("flags", Decls.newListType(Decls.Bool)),
                Decls.newVar("nulls", Decls.newListType(Decls.Null))));
    for (Evaluation evaluation :
        List.of(
            new Evaluation("flags[0]", Map.of("flags", List.of(42L))),
            new Evaluation("nulls[0]", Map.of("nulls", List.of("not null"))))) {
      Programs programs = programs(typedListEnv, evaluation.expression());
      assertEquivalent(
          programs.nativeProgram.eval(evaluation.input()).getVal(),
          programs.interpreterProgram.eval(evaluation.input()).getVal());
    }
  }

  @Test
  void preservesPartialActivationUnknowns() {
    Ast ast = compile("x + 1");
    Prog nativeProgram = (Prog) env.program(ast);
    Prog interpreterProgram =
        (Prog) env.program(ast, evalOptions(OptPartialEval, OptDisableNativeEval));
    Object vars = partialVars(Map.of("x", 41L), attributePattern("x"));

    assertEquivalent(nativeProgram.eval(vars).getVal(), interpreterProgram.eval(vars).getVal());
  }

  static Stream<Evaluation> canonicalExistsEvaluations() {
    return Stream.of(
        new Evaluation(
            "numbers.exists(value, value == target)",
            Map.of("numbers", new int[] {1, 2, 3}, "target", 2L)),
        new Evaluation(
            "numbers.exists(value, value == target)",
            Map.of("numbers", new long[] {1, 2, 3}, "target", 4L)),
        new Evaluation(
            "numbers.exists(value, value == target)",
            Map.of("numbers", List.of(1L, 2L, 3L), "target", 3L)),
        new Evaluation(
            "numbers.exists(value, value == target)",
            Map.of("numbers", new Object[] {1L, 2L, 3L}, "target", 1L)),
        new Evaluation(
            "doubles.exists(value, value == d)",
            Map.of("doubles", new double[] {-0.0d, Double.NaN}, "d", 0.0d)),
        new Evaluation(
            "words.exists(value, value == wordTarget)",
            Map.of("words", new String[] {"zero", "cel"}, "wordTarget", "cel")),
        new Evaluation(
            "flags.exists(value, value == b)",
            Map.of("flags", new Boolean[] {false, true}, "b", true)),
        new Evaluation(
            "numbers.exists(x, x == target)",
            Map.of("numbers", new long[] {1, 2}, "x", 99L, "target", 2L)),
        new Evaluation(
            "numbers.exists(value, value == target || x / y == 0)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.exists(value, value == target || x / y == 0)",
            Map.of("numbers", new long[] {1}, "target", 2L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.exists(value, value == target || b)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "b", unknownOf(201L))),
        new Evaluation(
            "numbers.exists(value, value == target)",
            Map.of("numbers", newErr("range"), "target", 2L)),
        new Evaluation(
            "numbers.exists(value, value == target)",
            Map.of("numbers", unknownOf(202L), "target", 2L)));
  }

  @ParameterizedTest
  @MethodSource("canonicalExistsEvaluations")
  void evaluatesCanonicalExists(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  static Stream<Evaluation> canonicalIntMapExistsEvaluations() {
    Val adaptedNumbers = DefaultTypeAdapter.Instance.nativeToValue(List.of(1L, 2L, 3L));
    return Stream.of(
        new Evaluation(
            "numbers.map(value, value).exists(mapped, mapped == target)",
            Map.of("numbers", new int[] {1, 2, 3}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + x).exists(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2, 3}, "x", 4L, "target", 7L)),
        new Evaluation(
            "numbers.map(value, value * x).exists(mapped, mapped == target)",
            Map.of("numbers", List.of(1L, 2L, 3L), "x", 3L, "target", 10L)),
        new Evaluation(
            "numbers.map(value, value - x).exists(mapped, mapped == target)",
            Map.of("numbers", new Object[] {1L, 2L, 3L}, "x", 1L, "target", 0L)),
        new Evaluation(
            "numbers.map(value, value + x).exists(mapped, mapped == target)",
            Map.of("numbers", adaptedNumbers, "x", 2L, "target", 5L)),
        new Evaluation(
            "numbers.map(value, value + x).exists(mapped, mapped == target)",
            Map.of("numbers", new long[0], "x", 2L, "target", 2L)),
        new Evaluation(
            "words.map(value, value == s ? x : y).exists(mapped, mapped == target)",
            Map.of(
                "words",
                new String[] {"other", "cel"},
                "s",
                "cel",
                "x",
                3L,
                "y",
                1L,
                "target",
                3L)),
        new Evaluation(
            "doubles.map(value, value == d ? x : y).exists(mapped, mapped == target)",
            Map.of(
                "doubles",
                new double[] {-0.0d, Double.NaN},
                "d",
                0.0d,
                "x",
                3L,
                "y",
                1L,
                "target",
                3L)),
        new Evaluation(
            "flags.map(value, value ? x : y).exists(mapped, mapped == target)",
            Map.of("flags", new Boolean[] {false, true}, "x", 3L, "y", 1L, "target", 3L)),
        new Evaluation(
            "numbers.map(value, value == 2 ? x : value).exists(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "x", "runtime mismatch", "target", 1L)),
        new Evaluation(
            "numbers.map(value, value == 1 ? x : value).exists(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "x", "runtime mismatch", "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + 1).exists(mapped, mapped == target)",
            Map.of("numbers", new Object[] {"runtime mismatch", 2L}, "target", 3L)),
        new Evaluation(
            "numbers.map(value, value + x).exists(mapped, mapped == target || b)",
            Map.of("numbers", new long[] {1, 2}, "x", 1L, "target", 3L, "b", unknownOf(225L))),
        new Evaluation(
            "numbers.map(value, value + 1)" + ".exists(mapped, mapped == target || x / y == 0)",
            Map.of("numbers", new long[] {1, 2}, "target", 3L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.map(value, value + 1)" + ".exists(mapped, mapped == target || x / y == 0)",
            Map.of("numbers", new long[] {1}, "target", 3L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.map(value, value == target ? x / y : value)"
                + ".exists(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.map(value, b ? value : target).exists(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "b", unknownOf(226L))),
        new Evaluation(
            "numbers.map(value, value + x).exists(mapped, mapped == target)",
            Map.of("numbers", newErr("range"), "x", 1L, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + x).exists(mapped, mapped == target)",
            Map.of("numbers", unknownOf(227L), "x", 1L, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + x).exists(mapped, mapped == target)",
            Map.of("numbers", "not a list", "x", 1L, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + x).exists(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + 1).exists(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2})),
        new Evaluation(
            "numbers.map(x, x + target).exists(y, y == x)",
            Map.of("numbers", new long[] {1, 2}, "x", 4L, "target", 2L)));
  }

  @ParameterizedTest
  @MethodSource("canonicalIntMapExistsEvaluations")
  void evaluatesCanonicalIntMapExists(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  static Stream<Evaluation> canonicalIntMapAllEvaluations() {
    Val adaptedNumbers = DefaultTypeAdapter.Instance.nativeToValue(List.of(1L, 2L, 3L));
    return Stream.of(
        new Evaluation(
            "numbers.map(value, value).all(mapped, mapped != target)",
            Map.of("numbers", new int[] {1, 2, 3}, "target", 4L)),
        new Evaluation(
            "numbers.map(value, value + x).all(mapped, mapped != target)",
            Map.of("numbers", new long[] {1, 2, 3}, "x", 4L, "target", 7L)),
        new Evaluation(
            "numbers.map(value, value * x).all(mapped, mapped != target)",
            Map.of("numbers", List.of(1L, 2L, 3L), "x", 3L, "target", 10L)),
        new Evaluation(
            "numbers.map(value, value - x).all(mapped, mapped != target)",
            Map.of("numbers", new Object[] {1L, 2L, 3L}, "x", 1L, "target", 0L)),
        new Evaluation(
            "numbers.map(value, value + x).all(mapped, mapped != target)",
            Map.of("numbers", adaptedNumbers, "x", 2L, "target", 5L)),
        new Evaluation(
            "numbers.map(value, value + x).all(mapped, mapped != target)",
            Map.of("numbers", new long[0], "x", 2L, "target", 2L)),
        new Evaluation(
            "words.map(value, value == s ? x : y).all(mapped, mapped != target)",
            Map.of(
                "words",
                new String[] {"other", "cel"},
                "s",
                "cel",
                "x",
                3L,
                "y",
                1L,
                "target",
                4L)),
        new Evaluation(
            "doubles.map(value, value == d ? x : y).all(mapped, mapped != target)",
            Map.of(
                "doubles",
                new double[] {-0.0d, Double.NaN},
                "d",
                0.0d,
                "x",
                3L,
                "y",
                1L,
                "target",
                3L)),
        new Evaluation(
            "flags.map(value, value ? x : y).all(mapped, mapped != target)",
            Map.of("flags", new Boolean[] {false, true}, "x", 3L, "y", 1L, "target", 4L)),
        new Evaluation(
            "numbers.map(value, value == 2 ? x : value).all(mapped, mapped != target)",
            Map.of("numbers", new long[] {1, 2}, "x", "runtime mismatch", "target", 1L)),
        new Evaluation(
            "numbers.map(value, value == 1 ? x : value).all(mapped, mapped != target)",
            Map.of("numbers", new long[] {1, 2}, "x", "runtime mismatch", "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + 1).all(mapped, mapped != target)",
            Map.of("numbers", new Object[] {"runtime mismatch", 2L}, "target", 3L)),
        new Evaluation(
            "numbers.map(value, value + 1)" + ".all(mapped, mapped != target && x / y == 0)",
            Map.of("numbers", new long[] {1, 2}, "target", 3L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.map(value, value + 1)" + ".all(mapped, mapped != target && x / y == 0)",
            Map.of("numbers", new long[] {1}, "target", 3L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.map(value, value + 1).all(mapped, mapped != target && b)",
            Map.of("numbers", new long[] {1, 2}, "target", 3L, "b", unknownOf(228L))),
        new Evaluation(
            "numbers.map(value, value == target ? x / y : value)"
                + ".all(mapped, mapped != target)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.map(value, b ? value : target).all(mapped, mapped != target)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "b", unknownOf(229L))),
        new Evaluation(
            "numbers.map(value, value + x).all(mapped, mapped != target)",
            Map.of("numbers", newErr("range"), "x", 1L, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + x).all(mapped, mapped != target)",
            Map.of("numbers", unknownOf(230L), "x", 1L, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + x).all(mapped, mapped != target)",
            Map.of("numbers", "not a list", "x", 1L, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + x).all(mapped, mapped != target)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + 1).all(mapped, mapped != target)",
            Map.of("numbers", new long[] {1, 2})),
        new Evaluation(
            "numbers.map(x, x + target).all(y, y != x)",
            Map.of("numbers", new long[] {1, 2}, "x", 4L, "target", 2L)));
  }

  @ParameterizedTest
  @MethodSource("canonicalIntMapAllEvaluations")
  void evaluatesCanonicalIntMapAll(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void canonicalIntMapExistsMatchesCurrentFoldPartialActivationScope() {
    Object input =
        partialVars(
            Map.of("numbers", new long[] {1, 2}, "x", 2L, "target", 4L), attributePattern("x"));

    assertExpression("numbers.map(value, value + x).exists(mapped, mapped == target)", input);
  }

  @Test
  void canonicalIntMapExistsUsesDefaultVariablesThroughBothFoldHierarchies() {
    Ast ast = compile("numbers.map(value, value + x).exists(mapped, mapped == target)");
    Map<String, Object> defaults = Map.of("x", 2L, "target", 4L);
    Prog nativeProgram = (Prog) env.program(ast, globals(defaults));
    Prog interpreterProgram =
        (Prog) env.program(ast, globals(defaults), evalOptions(OptDisableNativeEval));
    Map<String, Object> input = Map.of("numbers", new long[] {1, 2});

    assertIntegratedPlan(nativeProgram, "mapped integer exists with default variables");
    assertEquivalent(nativeProgram.eval(input).getVal(), interpreterProgram.eval(input).getVal());
  }

  @Test
  void canonicalIntMapExistsPreservesTheConstructionThenPredicateBoundary() {
    Programs programs =
        programs("numbers.map(value, value + x)" + ".exists(mapped, mapped == target || y == 0)");
    Map<String, Object> values =
        Map.of("numbers", new long[] {1, 2}, "x", 1L, "target", 3L, "y", 1L);
    List<String> nativeOrder = new ArrayList<>();
    List<String> interpreterOrder = new ArrayList<>();

    Val nativeResult = programs.nativeProgram.eval(orderedActivation(values, nativeOrder)).getVal();
    Val interpreterResult =
        programs.interpreterProgram.eval(orderedActivation(values, interpreterOrder)).getVal();

    assertEquivalent(nativeResult, interpreterResult);
    assertThat(nativeOrder).containsExactly("numbers", "x", "x", "target", "y", "target");
    assertThat(interpreterOrder).containsExactlyElementsOf(nativeOrder);

    assertResolutionCounts(
        "numbers.map(value, value + x).exists(mapped, mapped == target)",
        Map.of("numbers", new long[] {1, 2, 3}, "x", 1L, "target", 2L),
        Map.of("numbers", 1, "x", 3, "target", 1));
    assertResolutionCounts(
        "numbers.map(value, value + x).exists(mapped, mapped == target)",
        Map.of("numbers", new long[] {1, 2, 3}, "x", newErr("transform"), "target", 2L),
        Map.of("numbers", 1, "x", 1, "target", 0));
  }

  @Test
  void canonicalIntMapAllMatchesCurrentFoldScopeAndOrdering() {
    Object partialInput =
        partialVars(
            Map.of("numbers", new long[] {1, 2}, "x", 2L, "target", 5L), attributePattern("x"));
    assertExpression("numbers.map(value, value + x).all(mapped, mapped != target)", partialInput);

    Ast ast = compile("numbers.map(value, value + x).all(mapped, mapped != target)");
    Map<String, Object> defaults = Map.of("x", 2L, "target", 5L);
    Prog nativeProgram = (Prog) env.program(ast, globals(defaults));
    Prog interpreterProgram =
        (Prog) env.program(ast, globals(defaults), evalOptions(OptDisableNativeEval));
    Map<String, Object> input = Map.of("numbers", new long[] {1, 2});
    assertIntegratedPlan(nativeProgram, "mapped integer all with default variables");
    assertEquivalent(nativeProgram.eval(input).getVal(), interpreterProgram.eval(input).getVal());

    Programs programs =
        programs("numbers.map(value, value + x)" + ".all(mapped, mapped != target && y != 0)");
    Map<String, Object> values =
        Map.of("numbers", new long[] {1, 2}, "x", 1L, "target", 3L, "y", 1L);
    List<String> nativeOrder = new ArrayList<>();
    List<String> interpreterOrder = new ArrayList<>();
    assertEquivalent(
        programs.nativeProgram.eval(orderedActivation(values, nativeOrder)).getVal(),
        programs.interpreterProgram.eval(orderedActivation(values, interpreterOrder)).getVal());
    assertThat(nativeOrder).containsExactly("numbers", "x", "x", "target", "y", "target");
    assertThat(interpreterOrder).containsExactlyElementsOf(nativeOrder);

    assertResolutionCounts(
        "numbers.map(value, value + x).all(mapped, mapped != target)",
        Map.of("numbers", new long[] {1, 2, 3}, "x", 1L, "target", 2L),
        Map.of("numbers", 1, "x", 3, "target", 1));
    assertResolutionCounts(
        "numbers.map(value, value + x).all(mapped, mapped != target)",
        Map.of("numbers", new long[] {1, 2, 3}, "x", newErr("transform"), "target", 2L),
        Map.of("numbers", 1, "x", 1, "target", 0));
  }

  static Stream<Evaluation> canonicalIntMapExistsOneEvaluations() {
    Val adaptedNumbers = DefaultTypeAdapter.Instance.nativeToValue(List.of(1L, 2L, 3L));
    return Stream.of(
        new Evaluation(
            "numbers.map(value, value).exists_one(mapped, mapped == target)",
            Map.of("numbers", new int[] {1, 2, 3}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + x).exists_one(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2, 3}, "x", 4L, "target", 8L)),
        new Evaluation(
            "numbers.map(value, value * x).exists_one(mapped, mapped == target)",
            Map.of("numbers", List.of(1L, 2L, 2L), "x", 3L, "target", 6L)),
        new Evaluation(
            "numbers.map(value, value - x).exists_one(mapped, mapped == target)",
            Map.of("numbers", new Object[] {1L, 2L, 3L}, "x", 1L, "target", 0L)),
        new Evaluation(
            "numbers.map(value, value + x).exists_one(mapped, mapped == target)",
            Map.of("numbers", adaptedNumbers, "x", 2L, "target", 5L)),
        new Evaluation(
            "numbers.map(value, value + x).exists_one(mapped, mapped == target)",
            Map.of("numbers", new long[0], "x", 2L, "target", 2L)),
        new Evaluation(
            "words.map(value, value == s ? x : y).exists_one(mapped, mapped == target)",
            Map.of(
                "words",
                new String[] {"other", "cel"},
                "s",
                "cel",
                "x",
                3L,
                "y",
                1L,
                "target",
                3L)),
        new Evaluation(
            "doubles.map(value, value == d ? x : y).exists_one(mapped, mapped == target)",
            Map.of(
                "doubles",
                new double[] {-0.0d, Double.NaN},
                "d",
                0.0d,
                "x",
                3L,
                "y",
                1L,
                "target",
                3L)),
        new Evaluation(
            "flags.map(value, value ? x : y).exists_one(mapped, mapped == target)",
            Map.of("flags", new Boolean[] {false, true}, "x", 3L, "y", 1L, "target", 3L)),
        new Evaluation(
            "numbers.map(value, value == 2 ? x : value).exists_one(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "x", "runtime mismatch", "target", 1L)),
        new Evaluation(
            "numbers.map(value, value == 1 ? x : value).exists_one(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "x", "runtime mismatch", "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + 1).exists_one(mapped, mapped == target)",
            Map.of("numbers", new Object[] {"runtime mismatch", 2L}, "target", 3L)),
        new Evaluation(
            "numbers.map(value, value + 1)" + ".exists_one(mapped, mapped == target || x / y == 0)",
            Map.of("numbers", new long[] {1, 2}, "target", 3L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.map(value, value + 1)" + ".exists_one(mapped, mapped == target || x / y == 0)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.map(value, value == target ? x / y : value)"
                + ".exists_one(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.map(value, b ? value : target).exists_one(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "b", unknownOf(232L))),
        new Evaluation(
            "numbers.map(value, value + x).exists_one(mapped, mapped == target)",
            Map.of("numbers", newErr("range"), "x", 1L, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + x).exists_one(mapped, mapped == target)",
            Map.of("numbers", unknownOf(233L), "x", 1L, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + x).exists_one(mapped, mapped == target)",
            Map.of("numbers", "not a list", "x", 1L, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + x).exists_one(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + 1).exists_one(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2})),
        new Evaluation(
            "numbers.map(x, x + target).exists_one(y, y == x)",
            Map.of("numbers", new long[] {1, 2}, "x", 4L, "target", 2L)));
  }

  @ParameterizedTest
  @MethodSource("canonicalIntMapExistsOneEvaluations")
  void evaluatesCanonicalIntMapExistsOne(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void canonicalIntMapExistsOneMatchesCurrentFoldScopeAndCompleteTraversal() {
    Object partialInput =
        partialVars(
            Map.of("numbers", new long[] {1, 2}, "x", 2L, "target", 4L), attributePattern("x"));
    assertExpression(
        "numbers.map(value, value + x).exists_one(mapped, mapped == target)", partialInput);

    Ast ast = compile("numbers.map(value, value + x).exists_one(mapped, mapped == target)");
    Map<String, Object> defaults = Map.of("x", 2L, "target", 4L);
    Prog nativeProgram = (Prog) env.program(ast, globals(defaults));
    Prog interpreterProgram =
        (Prog) env.program(ast, globals(defaults), evalOptions(OptDisableNativeEval));
    Map<String, Object> input = Map.of("numbers", new long[] {1, 2});
    assertIntegratedPlan(nativeProgram, "mapped integer exists_one with default variables");
    assertEquivalent(nativeProgram.eval(input).getVal(), interpreterProgram.eval(input).getVal());

    Programs programs =
        programs(
            "numbers.map(value, value + x)" + ".exists_one(mapped, mapped == target || y == 0)");
    Map<String, Object> values =
        Map.of("numbers", new long[] {1, 2}, "x", 1L, "target", 3L, "y", 1L);
    List<String> nativeOrder = new ArrayList<>();
    List<String> interpreterOrder = new ArrayList<>();
    assertEquivalent(
        programs.nativeProgram.eval(orderedActivation(values, nativeOrder)).getVal(),
        programs.interpreterProgram.eval(orderedActivation(values, interpreterOrder)).getVal());
    assertThat(nativeOrder).containsExactly("numbers", "x", "x", "target", "y", "target");
    assertThat(interpreterOrder).containsExactlyElementsOf(nativeOrder);

    assertResolutionCounts(
        "numbers.map(value, value + x).exists_one(mapped, mapped == target)",
        Map.of("numbers", new long[] {1, 2, 3}, "x", 1L, "target", 2L),
        Map.of("numbers", 1, "x", 3, "target", 3));
    assertResolutionCounts(
        "numbers.map(value, value + x).exists_one(mapped, mapped == target)",
        Map.of("numbers", new long[] {1, 2, 3}, "x", newErr("transform"), "target", 2L),
        Map.of("numbers", 1, "x", 1, "target", 0));
    assertResolutionCounts(
        "numbers.map(value, value + 1)" + ".exists_one(mapped, mapped == target || x / y == 0)",
        Map.of("numbers", new long[] {1, 2, 3}, "target", 2L, "x", 1L, "y", 0L),
        Map.of("numbers", 1, "target", 3, "x", 2, "y", 2));
  }

  static Stream<Evaluation> canonicalFilteredIntMapAggregateEvaluations() {
    Val adaptedNumbers = DefaultTypeAdapter.Instance.nativeToValue(List.of(1L, 2L, 3L));
    return Stream.of(
        new Evaluation(
            "numbers.map(value, value > 1, value + x).exists(mapped, mapped == target)",
            Map.of("numbers", new int[] {1, 2, 3}, "x", 2L, "target", 4L)),
        new Evaluation(
            "numbers.map(value, value > 1, value + x).all(mapped, mapped != target)",
            Map.of("numbers", new long[] {1, 2, 3}, "x", 2L, "target", 4L)),
        new Evaluation(
            "numbers.map(value, value > 1, value + x).exists_one(mapped, mapped == target)",
            Map.of("numbers", List.of(1L, 2L, 2L), "x", 2L, "target", 4L)),
        new Evaluation(
            "numbers.filter(value, value > x).exists(mapped, mapped == target)",
            Map.of("numbers", new Object[] {1L, 2L, 3L}, "x", 1L, "target", 2L)),
        new Evaluation(
            "numbers.filter(value, value > x).all(mapped, mapped != target)",
            Map.of("numbers", adaptedNumbers, "x", 1L, "target", 1L)),
        new Evaluation(
            "numbers.filter(value, value > x).exists_one(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2, 2}, "x", 1L, "target", 2L)),
        new Evaluation(
            "numbers.filter(value, true).exists(mapped, mapped == target)",
            Map.of("numbers", new Object[] {newErr("accepted element"), 2L}, "target", 2L)),
        new Evaluation(
            "numbers.filter(value, false).all(mapped, mapped != target)",
            Map.of("numbers", new Object[] {newErr("suppressed element")}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, false, value / y).all(mapped, mapped != target)",
            Map.of("numbers", new long[] {1, 2}, "y", 0L, "target", 1L)),
        new Evaluation(
            "numbers.map(value, value != target, value / (value - target))"
                + ".exists(mapped, mapped == 0)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, x / y == 0, value + 1).exists(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "x", 1L, "y", 0L, "target", 2L)),
        new Evaluation(
            "numbers.map(value, b, value + 1).all(mapped, mapped != target)",
            Map.of("numbers", new long[] {1, 2}, "b", unknownOf(234L), "target", 2L)),
        new Evaluation(
            "numbers.map(value, value == target, value / y)"
                + ".exists_one(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "y", 0L)),
        new Evaluation(
            "numbers.map(value, value == target, b ? value : x)"
                + ".exists(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "b", unknownOf(235L), "x", 1L)),
        new Evaluation(
            "numbers.map(value, value != target, value == 1 ? x : value)"
                + ".exists_one(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", "suppressed runtime mismatch")),
        new Evaluation(
            "numbers.map(value, value > 0, value == 1 ? x : value)"
                + ".exists(mapped, mapped == target)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", "accepted runtime mismatch")),
        new Evaluation(
            "words.map(value, value != s, value == wordTarget ? x : y)"
                + ".exists(mapped, mapped == target)",
            Map.of(
                "words",
                new String[] {"skip", "cel"},
                "s",
                "skip",
                "wordTarget",
                "cel",
                "x",
                3L,
                "y",
                1L,
                "target",
                3L)),
        new Evaluation(
            "doubles.map(value, value != d, value > 0.0 ? x : y)"
                + ".all(mapped, mapped != target)",
            Map.of(
                "doubles", new double[] {-0.0d, 1.0d}, "d", 0.0d, "x", 3L, "y", 1L, "target", 1L)),
        new Evaluation(
            "flags.map(value, value, value ? x : y)" + ".exists_one(mapped, mapped == target)",
            Map.of("flags", new Boolean[] {false, true}, "x", 3L, "y", 1L, "target", 3L)),
        new Evaluation(
            "numbers.map(x, x != target, x + target).exists_one(y, y == x)",
            Map.of("numbers", new long[] {1, 2}, "x", 4L, "target", 2L)));
  }

  @ParameterizedTest
  @MethodSource("canonicalFilteredIntMapAggregateEvaluations")
  void evaluatesCanonicalFilteredIntMapAggregates(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void canonicalFilteredIntMapAggregatesPreserveConstructionScopeAndOrdering() {
    Object partialInput =
        partialVars(
            Map.of("numbers", new long[] {1, 2}, "x", 2L, "target", 4L), attributePattern("x"));
    assertExpression(
        "numbers.map(value, value > 0, value + x).exists(mapped, mapped == target)", partialInput);

    Ast ast =
        compile("numbers.map(value, value > y, value + x).exists_one(mapped, mapped == target)");
    Map<String, Object> defaults = Map.of("x", 2L, "y", 0L, "target", 4L);
    Prog nativeProgram = (Prog) env.program(ast, globals(defaults));
    Prog interpreterProgram =
        (Prog) env.program(ast, globals(defaults), evalOptions(OptDisableNativeEval));
    Map<String, Object> input = Map.of("numbers", new long[] {1, 2});
    assertIntegratedPlan(nativeProgram, "filtered mapped integer aggregate");
    assertEquivalent(nativeProgram.eval(input).getVal(), interpreterProgram.eval(input).getVal());

    Programs programs =
        programs("numbers.map(value, value != target, value + x).exists(mapped, mapped == y)");
    Map<String, Object> values =
        Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", 1L, "y", 2L);
    List<String> nativeOrder = new ArrayList<>();
    List<String> interpreterOrder = new ArrayList<>();
    assertEquivalent(
        programs.nativeProgram.eval(orderedActivation(values, nativeOrder)).getVal(),
        programs.interpreterProgram.eval(orderedActivation(values, interpreterOrder)).getVal());
    assertThat(nativeOrder).containsExactly("numbers", "target", "x", "target", "y");
    assertThat(interpreterOrder).containsExactlyElementsOf(nativeOrder);

    assertResolutionCounts(
        "numbers.map(value, value != target, value + x).exists(mapped, mapped == y)",
        Map.of("numbers", new long[] {1, 2, 3}, "target", 2L, "x", 1L, "y", 2L),
        Map.of("numbers", 1, "target", 3, "x", 2, "y", 1));
    assertResolutionCounts(
        "numbers.map(value, false, value + x).all(mapped, mapped != y)",
        Map.of("numbers", new long[] {1, 2, 3}, "x", 1L, "y", 2L),
        Map.of("numbers", 1, "x", 0, "y", 0));
    assertResolutionCounts(
        "numbers.map(value, value > 0, value + x).exists_one(mapped, mapped == y)",
        Map.of("numbers", new long[] {1, 2, 3}, "x", newErr("transform"), "y", 2L),
        Map.of("numbers", 1, "x", 1, "y", 0));
  }

  static Stream<Evaluation> canonicalAllEvaluations() {
    return Stream.of(
        new Evaluation(
            "numbers.all(value, value < target)",
            Map.of("numbers", new int[] {1, 2, 3}, "target", 4L)),
        new Evaluation(
            "numbers.all(value, value != target)",
            Map.of("numbers", new long[] {1, 2, 3}, "target", 3L)),
        new Evaluation(
            "numbers.all(value, value <= target)",
            Map.of("numbers", List.of(1L, 2L, 3L), "target", 3L)),
        new Evaluation(
            "numbers.all(value, value > target)",
            Map.of("numbers", new Object[] {1L, 2L, 3L}, "target", 0L)),
        new Evaluation(
            "numbers.all(value, value == target)", Map.of("numbers", new long[0], "target", 1L)),
        new Evaluation(
            "doubles.all(value, value == d)",
            Map.of("doubles", new double[] {-0.0d, 0.0d}, "d", 0.0d)),
        new Evaluation(
            "words.all(value, value != wordTarget)",
            Map.of("words", new String[] {"zero", "cel"}, "wordTarget", "absent")),
        new Evaluation(
            "flags.all(value, value == b)", Map.of("flags", new boolean[] {true, true}, "b", true)),
        new Evaluation(
            "flags.all(value, value == b)",
            Map.of("flags", new Boolean[] {true, false}, "b", true)),
        new Evaluation(
            "flags.all(value, value)",
            Map.of("flags", new Object[] {newErr("predicate"), unknownOf(206L)})),
        new Evaluation(
            "flags.all(value, value)",
            Map.of("flags", new Object[] {unknownOf(207L), newErr("predicate")})),
        new Evaluation(
            "numbers.all(x, x <= target)",
            Map.of("numbers", new long[] {1, 2}, "x", 99L, "target", 2L)),
        new Evaluation(
            "numbers.all(value, value == target && x / y == 0)",
            Map.of("numbers", new long[] {1, 2}, "target", 1L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.all(value, value == target && x / y == 0)",
            Map.of("numbers", new long[] {1}, "target", 1L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.all(value, value == target && b)",
            Map.of("numbers", new long[] {1, 2}, "target", 1L, "b", unknownOf(203L))),
        new Evaluation(
            "numbers.all(value, value == target && b)",
            Map.of("numbers", new long[] {1}, "target", 1L, "b", unknownOf(204L))),
        new Evaluation(
            "numbers.all(value, value != target)",
            Map.of("numbers", newErr("range"), "target", 2L)),
        new Evaluation(
            "numbers.all(value, value != target)",
            Map.of("numbers", unknownOf(205L), "target", 2L)));
  }

  @ParameterizedTest
  @MethodSource("canonicalAllEvaluations")
  void evaluatesCanonicalAll(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  static Stream<Evaluation> canonicalExistsOneEvaluations() {
    return Stream.of(
        new Evaluation(
            "numbers.exists_one(value, value == target)",
            Map.of("numbers", new int[] {1, 2, 3}, "target", 2L)),
        new Evaluation(
            "numbers.exists_one(value, value == target)",
            Map.of("numbers", new long[] {1, 2, 3}, "target", 4L)),
        new Evaluation(
            "numbers.exists_one(value, value == target)",
            Map.of("numbers", List.of(1L, 2L, 2L), "target", 2L)),
        new Evaluation(
            "numbers.exists_one(value, value == target)",
            Map.of("numbers", new Object[] {1L, 2L, 3L}, "target", 1L)),
        new Evaluation(
            "numbers.exists_one(value, value == target)",
            Map.of("numbers", new long[0], "target", 1L)),
        new Evaluation(
            "doubles.exists_one(value, value == d)",
            Map.of("doubles", new double[] {-0.0d, Double.NaN}, "d", 0.0d)),
        new Evaluation(
            "words.exists_one(value, value == wordTarget)",
            Map.of("words", new String[] {"cel", "other", "cel"}, "wordTarget", "cel")),
        new Evaluation(
            "words.exists_one(value, value == wordTarget)",
            Map.of("words", new String[] {null}, "wordTarget", "cel")),
        new Evaluation(
            "flags.exists_one(value, value == b)",
            Map.of("flags", new Boolean[] {false, true}, "b", true)),
        new Evaluation(
            "flags.exists_one(value, value)",
            Map.of("flags", new Object[] {newErr("predicate"), unknownOf(208L)})),
        new Evaluation(
            "flags.exists_one(value, value)",
            Map.of("flags", new Object[] {unknownOf(209L), newErr("predicate")})),
        new Evaluation(
            "numbers.exists_one(x, x == target)",
            Map.of("numbers", new long[] {1, 2}, "x", 99L, "target", 2L)),
        new Evaluation(
            "numbers.exists_one(value, value == target || x / y == 0)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.exists_one(value, value == target || b)",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "b", unknownOf(210L))),
        new Evaluation(
            "numbers.exists_one(value, value == target)",
            Map.of("numbers", newErr("range"), "target", 2L)),
        new Evaluation(
            "numbers.exists_one(value, value == target)",
            Map.of("numbers", unknownOf(211L), "target", 2L)));
  }

  @ParameterizedTest
  @MethodSource("canonicalExistsOneEvaluations")
  void evaluatesCanonicalExistsOne(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "words.exists(value, value == wordTarget)",
        "words.all(value, value != wordTarget)"
      })
  void canonicalQuantifiersPreserveNullBackedStringFailure(String expression) {
    Programs programs = programs(expression);
    Map<String, Object> input = Map.of("words", new String[] {null}, "wordTarget", "cel");

    Throwable nativeFailure = catchThrowable(() -> programs.nativeProgram.eval(input));
    Throwable interpreterFailure = catchThrowable(() -> programs.interpreterProgram.eval(input));

    assertThat(nativeFailure).isInstanceOf(interpreterFailure.getClass());
    assertThat(nativeFailure.getCause()).isInstanceOf(interpreterFailure.getCause().getClass());
    assertThat(nativeFailure.getCause().getMessage())
        .isEqualTo(interpreterFailure.getCause().getMessage());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "numbers.exists(value, value == target)",
        "numbers.all(value, value != target)",
        "numbers.exists_one(value, value == target)"
      })
  void canonicalQuantifiersMatchCurrentFoldPartialActivationScope(String expression) {
    Ast ast = compile(expression);
    Prog nativeProgram = (Prog) env.program(ast);
    Prog interpreterProgram =
        (Prog) env.program(ast, evalOptions(OptPartialEval, OptDisableNativeEval));
    Object vars =
        partialVars(Map.of("numbers", new long[] {1, 2}, "target", 2L), attributePattern("target"));

    assertIntegratedPlan(nativeProgram, expression);
    assertEquivalent(nativeProgram.eval(vars).getVal(), interpreterProgram.eval(vars).getVal());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "numbers.exists(value, value == target)",
        "numbers.all(value, value <= target)",
        "numbers.exists_one(value, value == target)"
      })
  void canonicalQuantifiersUseDefaultVariablesThroughTheCurrentFoldHierarchy(String expression) {
    Ast ast = compile(expression);
    Prog nativeProgram = (Prog) env.program(ast, globals(Map.of("target", 2L)));
    Prog interpreterProgram =
        (Prog) env.program(ast, globals(Map.of("target", 2L)), evalOptions(OptDisableNativeEval));

    assertIntegratedPlan(nativeProgram, expression);
    assertEquivalent(
        nativeProgram.eval(Map.of("numbers", new long[] {1, 2})).getVal(),
        interpreterProgram.eval(Map.of("numbers", new long[] {1, 2})).getVal());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"numbers.exists(value, value == target)", "numbers.all(value, value != target)"})
  void canonicalQuantifiersResolveRangeOnceAndStopResolvingAfterTheResult(String expression) {
    Programs programs = programs(expression);
    Map<String, Object> values = Map.of("numbers", new long[] {1L, 2L, 3L}, "target", 2L);
    Map<String, AtomicInteger> nativeCounts = new java.util.HashMap<>();
    Map<String, AtomicInteger> interpreterCounts = new java.util.HashMap<>();

    Val nativeResult =
        programs.nativeProgram.eval(countingActivation(values, nativeCounts)).getVal();
    Val interpreterResult =
        programs.interpreterProgram.eval(countingActivation(values, interpreterCounts)).getVal();

    assertEquivalent(nativeResult, interpreterResult);
    assertThat(nativeCounts).containsOnlyKeys("numbers", "target");
    assertThat(count(nativeCounts, "numbers")).isOne();
    assertThat(count(nativeCounts, "target")).isEqualTo(2);
    assertThat(count(interpreterCounts, "numbers")).isEqualTo(count(nativeCounts, "numbers"));
    assertThat(count(interpreterCounts, "target")).isEqualTo(count(nativeCounts, "target"));
  }

  @Test
  void canonicalExistsOneResolvesItsPredicateForEveryElement() {
    Programs programs = programs("numbers.exists_one(value, value == target)");
    Map<String, Object> values = Map.of("numbers", new long[] {1L, 2L, 3L}, "target", 1L);
    Map<String, AtomicInteger> nativeCounts = new java.util.HashMap<>();
    Map<String, AtomicInteger> interpreterCounts = new java.util.HashMap<>();

    Val nativeResult =
        programs.nativeProgram.eval(countingActivation(values, nativeCounts)).getVal();
    Val interpreterResult =
        programs.interpreterProgram.eval(countingActivation(values, interpreterCounts)).getVal();

    assertEquivalent(nativeResult, interpreterResult);
    assertThat(nativeCounts).containsOnlyKeys("numbers", "target");
    assertThat(count(nativeCounts, "numbers")).isOne();
    assertThat(count(nativeCounts, "target")).isEqualTo(3);
    assertThat(count(interpreterCounts, "numbers")).isEqualTo(count(nativeCounts, "numbers"));
    assertThat(count(interpreterCounts, "target")).isEqualTo(count(nativeCounts, "target"));
  }

  @Test
  void canonicalExistsOneContinuesAfterAnExceptionalPredicate() {
    Programs programs = programs("numbers.exists_one(value, value == target || x / y == 0)");
    Map<String, Object> values =
        Map.of("numbers", new long[] {1L, 2L, 3L}, "target", 1L, "x", 1L, "y", 0L);
    Map<String, AtomicInteger> nativeCounts = new java.util.HashMap<>();
    Map<String, AtomicInteger> interpreterCounts = new java.util.HashMap<>();

    Val nativeResult =
        programs.nativeProgram.eval(countingActivation(values, nativeCounts)).getVal();
    Val interpreterResult =
        programs.interpreterProgram.eval(countingActivation(values, interpreterCounts)).getVal();

    assertEquivalent(nativeResult, interpreterResult);
    assertThat(count(nativeCounts, "numbers")).isOne();
    assertThat(count(nativeCounts, "target")).isEqualTo(3);
    assertThat(count(nativeCounts, "x")).isEqualTo(2);
    assertThat(count(nativeCounts, "y")).isEqualTo(2);
    assertThat(count(interpreterCounts, "numbers")).isEqualTo(count(nativeCounts, "numbers"));
    assertThat(count(interpreterCounts, "target")).isEqualTo(count(nativeCounts, "target"));
    assertThat(count(interpreterCounts, "x")).isEqualTo(count(nativeCounts, "x"));
    assertThat(count(interpreterCounts, "y")).isEqualTo(count(nativeCounts, "y"));
  }

  static Stream<Evaluation> canonicalFilterSizeEvaluations() {
    Val adaptedNumbers = DefaultTypeAdapter.Instance.nativeToValue(List.of(1L, 2L, 3L));
    return Stream.of(
        new Evaluation(
            "size(numbers.filter(value, value >= target))",
            Map.of("numbers", new int[] {1, 2, 3}, "target", 2L)),
        new Evaluation(
            "numbers.filter(value, value == target).size()",
            Map.of("numbers", new long[] {1, 2, 3}, "target", 4L)),
        new Evaluation(
            "size(numbers.filter(value, value == target))",
            Map.of("numbers", List.of(1L, 2L, 2L), "target", 2L)),
        new Evaluation(
            "size(numbers.filter(value, value != target))",
            Map.of("numbers", new Object[] {1L, 2L, 3L}, "target", 2L)),
        new Evaluation(
            "size(numbers.filter(value, value <= target))",
            Map.of("numbers", adaptedNumbers, "target", 2L)),
        new Evaluation(
            "size(numbers.filter(value, value == target))",
            Map.of("numbers", new long[0], "target", 1L)),
        new Evaluation(
            "size(doubles.filter(value, value == d))",
            Map.of("doubles", new double[] {-0.0d, Double.NaN}, "d", 0.0d)),
        new Evaluation(
            "size(words.filter(value, value == wordTarget))",
            Map.of("words", new String[] {"cel", "other", "cel"}, "wordTarget", "cel")),
        new Evaluation(
            "size(flags.filter(value, value == b))",
            Map.of("flags", new Boolean[] {false, true}, "b", true)),
        new Evaluation(
            "size(flags.filter(value, value))",
            Map.of("flags", new Object[] {newErr("predicate"), true})),
        new Evaluation(
            "size(flags.filter(value, value))",
            Map.of("flags", new Object[] {unknownOf(212L), true})),
        new Evaluation(
            "size(flags.filter(value, value))", Map.of("flags", new Object[] {"not a bool"})),
        new Evaluation(
            "size(numbers.filter(value, target > 0))",
            Map.of("numbers", new Object[] {newErr("element"), 2L}, "target", 1L)),
        new Evaluation(
            "size(numbers.filter(value, target > 0))",
            Map.of("numbers", new Object[] {unknownOf(222L), 2L}, "target", 1L)),
        new Evaluation(
            "size(numbers.filter(value, target > 0))",
            Map.of("numbers", new Object[] {"runtime mismatch", 2L}, "target", 1L)),
        new Evaluation(
            "size(numbers.filter(x, x == target))",
            Map.of("numbers", new long[] {1, 2}, "x", 99L, "target", 2L)),
        new Evaluation(
            "size(numbers.filter(value, value == target || x / y == 0))",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", 1L, "y", 0L)),
        new Evaluation(
            "size(numbers.filter(value, value == target || b))",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "b", unknownOf(213L))),
        new Evaluation(
            "size(numbers.filter(value, value == target))",
            Map.of("numbers", newErr("range"), "target", 2L)),
        new Evaluation(
            "size(numbers.filter(value, value == target))",
            Map.of("numbers", unknownOf(214L), "target", 2L)));
  }

  @ParameterizedTest
  @MethodSource("canonicalFilterSizeEvaluations")
  void evaluatesCanonicalFilterSize(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void canonicalFilterSizePreservesNullBackedStringFailure() {
    Programs programs = programs("size(words.filter(value, value == wordTarget))");
    Map<String, Object> input = Map.of("words", new String[] {null}, "wordTarget", "cel");

    Throwable nativeFailure = catchThrowable(() -> programs.nativeProgram.eval(input));
    Throwable interpreterFailure = catchThrowable(() -> programs.interpreterProgram.eval(input));

    assertThat(nativeFailure).isInstanceOf(interpreterFailure.getClass());
    assertThat(nativeFailure.getCause()).isInstanceOf(interpreterFailure.getCause().getClass());
    assertThat(nativeFailure.getCause().getMessage())
        .isEqualTo(interpreterFailure.getCause().getMessage());
  }

  @Test
  void canonicalFilterSizeMatchesCurrentFoldPartialActivationScope() {
    Ast ast = compile("size(numbers.filter(value, value == target))");
    Prog nativeProgram = (Prog) env.program(ast);
    Prog interpreterProgram =
        (Prog) env.program(ast, evalOptions(OptPartialEval, OptDisableNativeEval));
    Object vars =
        partialVars(Map.of("numbers", new long[] {1, 2}, "target", 2L), attributePattern("target"));

    assertIntegratedPlan(nativeProgram, "filter size");
    assertEquivalent(nativeProgram.eval(vars).getVal(), interpreterProgram.eval(vars).getVal());
  }

  @Test
  void canonicalFilterSizeUsesDefaultVariablesThroughTheCurrentFoldHierarchy() {
    Ast ast = compile("size(numbers.filter(value, value <= target))");
    Prog nativeProgram = (Prog) env.program(ast, globals(Map.of("target", 2L)));
    Prog interpreterProgram =
        (Prog) env.program(ast, globals(Map.of("target", 2L)), evalOptions(OptDisableNativeEval));

    assertIntegratedPlan(nativeProgram, "filter size");
    assertEquivalent(
        nativeProgram.eval(Map.of("numbers", new long[] {1, 2})).getVal(),
        interpreterProgram.eval(Map.of("numbers", new long[] {1, 2})).getVal());
  }

  @Test
  void canonicalFilterSizeStopsAfterAnExceptionalPredicate() {
    Programs programs = programs("size(numbers.filter(value, value == target || x / y == 0))");
    Map<String, Object> values =
        Map.of("numbers", new long[] {1L, 2L, 3L}, "target", 1L, "x", 1L, "y", 0L);
    Map<String, AtomicInteger> nativeCounts = new java.util.HashMap<>();
    Map<String, AtomicInteger> interpreterCounts = new java.util.HashMap<>();

    Val nativeResult =
        programs.nativeProgram.eval(countingActivation(values, nativeCounts)).getVal();
    Val interpreterResult =
        programs.interpreterProgram.eval(countingActivation(values, interpreterCounts)).getVal();

    assertEquivalent(nativeResult, interpreterResult);
    assertThat(count(nativeCounts, "numbers")).isOne();
    assertThat(count(nativeCounts, "target")).isEqualTo(2);
    assertThat(count(nativeCounts, "x")).isOne();
    assertThat(count(nativeCounts, "y")).isOne();
    assertThat(count(interpreterCounts, "numbers")).isEqualTo(count(nativeCounts, "numbers"));
    assertThat(count(interpreterCounts, "target")).isEqualTo(count(nativeCounts, "target"));
    assertThat(count(interpreterCounts, "x")).isEqualTo(count(nativeCounts, "x"));
    assertThat(count(interpreterCounts, "y")).isEqualTo(count(nativeCounts, "y"));
  }

  static Stream<Evaluation> canonicalMapSizeEvaluations() {
    Val adaptedNumbers = DefaultTypeAdapter.Instance.nativeToValue(List.of(1L, 2L, 3L));
    return Stream.of(
        new Evaluation(
            "size(numbers.map(value, value + target))",
            Map.of("numbers", new int[] {1, 2, 3}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value == target).size()",
            Map.of("numbers", new long[] {1, 2, 3}, "target", 4L)),
        new Evaluation(
            "size(numbers.map(value, value * target))",
            Map.of("numbers", List.of(1L, 2L, 3L), "target", 2L)),
        new Evaluation(
            "size(numbers.map(value, value - target))",
            Map.of("numbers", new Object[] {1L, 2L, 3L}, "target", 2L)),
        new Evaluation(
            "size(numbers.map(value, value <= target))",
            Map.of("numbers", adaptedNumbers, "target", 2L)),
        new Evaluation(
            "size(numbers.map(value, value + target))",
            Map.of("numbers", new long[0], "target", 1L)),
        new Evaluation(
            "size(doubles.map(value, value + d))",
            Map.of("doubles", new double[] {-0.0d, Double.NaN}, "d", 0.0d)),
        new Evaluation(
            "size(words.map(value, value + s))",
            Map.of("words", new String[] {"cel", "other"}, "s", "!")),
        new Evaluation(
            "size(flags.map(value, !value))", Map.of("flags", new Boolean[] {false, true})),
        new Evaluation(
            "size(flags.map(value, !value))", Map.of("flags", new boolean[] {false, true})),
        new Evaluation("size(numbers.map(value, null))", Map.of("numbers", new long[] {1, 2})),
        new Evaluation(
            "size(numbers.map(value, value))",
            Map.of("numbers", new Object[] {"runtime mismatch", null})),
        new Evaluation(
            "size(numbers.map(value, value))",
            Map.of("numbers", new Object[] {newErr("transform"), 2L})),
        new Evaluation(
            "size(numbers.map(value, value))",
            Map.of("numbers", new Object[] {unknownOf(215L), 2L})),
        new Evaluation(
            "size(words.map(value, value))", Map.of("words", new String[] {null, "cel"})),
        new Evaluation(
            "size(words.map(value, value + s))", Map.of("words", new String[] {null}, "s", "!")),
        new Evaluation(
            "size(numbers.map(x, x + target))",
            Map.of("numbers", new long[] {1, 2}, "x", 99L, "target", 2L)),
        new Evaluation(
            "size(numbers.map(value, value == target ? x / y : value))",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", 1L, "y", 0L)),
        new Evaluation(
            "size(numbers.map(value, b ? value : target))",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "b", unknownOf(216L))),
        new Evaluation(
            "size(numbers.map(value, value + target))",
            Map.of("numbers", newErr("range"), "target", 2L)),
        new Evaluation(
            "size(numbers.map(value, value + target))",
            Map.of("numbers", unknownOf(217L), "target", 2L)),
        new Evaluation("size(numbers.map(value, value))", Map.of("numbers", "not a list")),
        new Evaluation(
            "size(numbers.map(value, value + target))", Map.of("numbers", new long[] {1, 2})));
  }

  @ParameterizedTest
  @MethodSource("canonicalMapSizeEvaluations")
  void evaluatesCanonicalMapSize(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void canonicalMapSizeMatchesCurrentFoldPartialActivationScope() {
    Ast ast = compile("size(numbers.map(value, value + target))");
    Prog nativeProgram = (Prog) env.program(ast);
    Prog interpreterProgram =
        (Prog) env.program(ast, evalOptions(OptPartialEval, OptDisableNativeEval));
    Object vars =
        partialVars(Map.of("numbers", new long[] {1, 2}, "target", 2L), attributePattern("target"));

    assertIntegratedPlan(nativeProgram, "map size");
    assertEquivalent(nativeProgram.eval(vars).getVal(), interpreterProgram.eval(vars).getVal());
  }

  @Test
  void canonicalMapSizeUsesDefaultVariablesThroughTheCurrentFoldHierarchy() {
    Ast ast = compile("size(numbers.map(value, value + target))");
    Prog nativeProgram = (Prog) env.program(ast, globals(Map.of("target", 2L)));
    Prog interpreterProgram =
        (Prog) env.program(ast, globals(Map.of("target", 2L)), evalOptions(OptDisableNativeEval));

    assertIntegratedPlan(nativeProgram, "map size");
    assertEquivalent(
        nativeProgram.eval(Map.of("numbers", new long[] {1, 2})).getVal(),
        interpreterProgram.eval(Map.of("numbers", new long[] {1, 2})).getVal());
  }

  @Test
  void canonicalMapSizeEvaluatesEveryTransformUntilAnError() {
    Programs programs =
        programs("size(numbers.map(value, value == target ? x / y : value + target))");
    Map<String, Object> values =
        Map.of("numbers", new long[] {1L, 2L, 3L}, "target", 2L, "x", 1L, "y", 0L);
    Map<String, AtomicInteger> nativeCounts = new java.util.HashMap<>();
    Map<String, AtomicInteger> interpreterCounts = new java.util.HashMap<>();

    Val nativeResult =
        programs.nativeProgram.eval(countingActivation(values, nativeCounts)).getVal();
    Val interpreterResult =
        programs.interpreterProgram.eval(countingActivation(values, interpreterCounts)).getVal();

    assertEquivalent(nativeResult, interpreterResult);
    assertThat(count(nativeCounts, "numbers")).isOne();
    assertThat(count(nativeCounts, "target")).isEqualTo(3);
    assertThat(count(nativeCounts, "x")).isOne();
    assertThat(count(nativeCounts, "y")).isOne();
    assertThat(count(interpreterCounts, "numbers")).isEqualTo(count(nativeCounts, "numbers"));
    assertThat(count(interpreterCounts, "target")).isEqualTo(count(nativeCounts, "target"));
    assertThat(count(interpreterCounts, "x")).isEqualTo(count(nativeCounts, "x"));
    assertThat(count(interpreterCounts, "y")).isEqualTo(count(nativeCounts, "y"));
  }

  static Stream<Evaluation> canonicalFilteredMapSizeEvaluations() {
    Val adaptedNumbers = DefaultTypeAdapter.Instance.nativeToValue(List.of(1L, 2L, 3L));
    return Stream.of(
        new Evaluation(
            "size(numbers.map(value, value >= target, value + target))",
            Map.of("numbers", new int[] {1, 2, 3}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value != target, value == target).size()",
            Map.of("numbers", new long[] {1, 2, 3}, "target", 2L)),
        new Evaluation(
            "size(numbers.map(value, value <= target, value * target))",
            Map.of("numbers", List.of(1L, 2L, 3L), "target", 2L)),
        new Evaluation(
            "size(numbers.map(value, value != target, value - target))",
            Map.of("numbers", new Object[] {1L, 2L, 3L}, "target", 2L)),
        new Evaluation(
            "size(numbers.map(value, value <= target, value == target))",
            Map.of("numbers", adaptedNumbers, "target", 2L)),
        new Evaluation(
            "size(numbers.map(value, value == target, value + target))",
            Map.of("numbers", new long[0], "target", 1L)),
        new Evaluation(
            "size(doubles.map(value, value == d, value + d))",
            Map.of("doubles", new double[] {-0.0d, Double.NaN}, "d", 0.0d)),
        new Evaluation(
            "size(words.map(value, value == wordTarget, value + s))",
            Map.of("words", new String[] {"cel", "other", "cel"}, "wordTarget", "cel", "s", "!")),
        new Evaluation(
            "size(flags.map(value, value == b, !value))",
            Map.of("flags", new Boolean[] {false, true}, "b", true)),
        new Evaluation(
            "size(flags.map(value, value == b, !value))",
            Map.of("flags", new boolean[] {false, true}, "b", true)),
        new Evaluation(
            "size(flags.map(value, value, !value))",
            Map.of("flags", new Object[] {newErr("predicate"), true})),
        new Evaluation(
            "size(flags.map(value, value, !value))",
            Map.of("flags", new Object[] {unknownOf(218L), true})),
        new Evaluation(
            "size(flags.map(value, value, !value))", Map.of("flags", new Object[] {"not a bool"})),
        new Evaluation(
            "size(numbers.map(value, target > 0, value))",
            Map.of("numbers", new Object[] {"runtime mismatch", 2L}, "target", 2L)),
        new Evaluation(
            "size(numbers.map(value, target > 0, value))",
            Map.of("numbers", new Object[] {2L, newErr("transform")}, "target", 1L)),
        new Evaluation(
            "size(numbers.map(value, target > 0, value))",
            Map.of("numbers", new Object[] {2L, unknownOf(219L)}, "target", 1L)),
        new Evaluation(
            "size(words.map(value, target > 0, value))",
            Map.of("words", new String[] {null, "cel"}, "target", 1L)),
        new Evaluation(
            "size(numbers.map(value, target > 0, b ? value : x))",
            Map.of("numbers", new long[] {1, 2}, "target", 1L, "b", false, "x", "not an int")),
        new Evaluation(
            "size(words.map(value, target > 0, b ? value : s))",
            Map.of("words", new String[] {null, "cel"}, "target", 1L, "b", true, "s", "!")),
        new Evaluation(
            "size(words.map(value, target > 0, value + s))",
            Map.of("words", new String[] {null}, "target", 1L, "s", "!")),
        new Evaluation(
            "size(numbers.map(x, x >= target, x + target))",
            Map.of("numbers", new long[] {1, 2}, "x", 99L, "target", 2L)),
        new Evaluation(
            "size(numbers.map(value, value == target, x / y))",
            Map.of("numbers", new long[] {1, 2}, "target", 3L, "x", 1L, "y", 0L)),
        new Evaluation(
            "size(numbers.map(value, value == target, x / y))",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", 1L, "y", 0L)),
        new Evaluation(
            "size(numbers.map(value, value == target, b ? value : x))",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "b", unknownOf(220L), "x", 1L)),
        new Evaluation(
            "size(numbers.map(value, value == target, value + target))",
            Map.of("numbers", newErr("range"), "target", 2L)),
        new Evaluation(
            "size(numbers.map(value, value == target, value + target))",
            Map.of("numbers", unknownOf(221L), "target", 2L)),
        new Evaluation(
            "size(numbers.map(value, value == target, value))",
            Map.of("numbers", "not a list", "target", 2L)),
        new Evaluation(
            "size(numbers.map(value, value == target, value + target))",
            Map.of("numbers", new long[] {1, 2})),
        new Evaluation(
            "size(numbers.map(value, value > 0, value + target))",
            Map.of("numbers", new long[] {1, 2})));
  }

  @ParameterizedTest
  @MethodSource("canonicalFilteredMapSizeEvaluations")
  void evaluatesCanonicalFilteredMapSize(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void canonicalFilteredMapSizeMatchesCurrentFoldPartialActivationScope() {
    Ast ast = compile("size(numbers.map(value, value == target, value + target))");
    Prog nativeProgram = (Prog) env.program(ast);
    Prog interpreterProgram =
        (Prog) env.program(ast, evalOptions(OptPartialEval, OptDisableNativeEval));
    Object vars =
        partialVars(Map.of("numbers", new long[] {1, 2}, "target", 2L), attributePattern("target"));

    assertIntegratedPlan(nativeProgram, "filtered map size");
    assertEquivalent(nativeProgram.eval(vars).getVal(), interpreterProgram.eval(vars).getVal());
  }

  @Test
  void canonicalFilteredMapSizeUsesDefaultVariablesThroughTheCurrentFoldHierarchy() {
    Ast ast = compile("size(numbers.map(value, value <= target, value + target))");
    Prog nativeProgram = (Prog) env.program(ast, globals(Map.of("target", 2L)));
    Prog interpreterProgram =
        (Prog) env.program(ast, globals(Map.of("target", 2L)), evalOptions(OptDisableNativeEval));

    assertIntegratedPlan(nativeProgram, "filtered map size");
    assertEquivalent(
        nativeProgram.eval(Map.of("numbers", new long[] {1, 2})).getVal(),
        interpreterProgram.eval(Map.of("numbers", new long[] {1, 2})).getVal());
  }

  @Test
  void canonicalFilteredMapSizePreservesPredicateThenTransformResolutionOrder() {
    assertResolutionCounts(
        "size(numbers.map(value, value <= target, value == target ? x / y : value + x))",
        Map.of("numbers", new long[] {1L, 2L, 3L}, "target", 2L, "x", 1L, "y", 0L),
        Map.of("numbers", 1, "target", 4, "x", 2, "y", 1));
    assertResolutionCounts(
        "size(numbers.map(value, value == target, value + x))",
        Map.of("numbers", new long[] {1L, 2L, 3L}, "target", 2L, "x", 1L),
        Map.of("numbers", 1, "target", 3, "x", 1));
  }

  static Stream<Evaluation> canonicalMapIndexEvaluations() {
    Val adaptedNumbers = DefaultTypeAdapter.Instance.nativeToValue(List.of(1L, 2L, 3L));
    return Stream.of(
        new Evaluation(
            "numbers.map(value, value + target)[1]",
            Map.of("numbers", new int[] {1, 2, 3}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value == target)[2]",
            Map.of("numbers", new long[] {1, 2, 3}, "target", 3L)),
        new Evaluation(
            "numbers.map(value, value * target)[0]",
            Map.of("numbers", List.of(1L, 2L, 3L), "target", 2L)),
        new Evaluation(
            "numbers.map(value, value - target)[2]",
            Map.of("numbers", new Object[] {1L, 2L, 3L}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value <= target)[1]",
            Map.of("numbers", adaptedNumbers, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + target)[0]", Map.of("numbers", new long[0], "target", 1L)),
        new Evaluation(
            "doubles.map(value, value + d)[1]",
            Map.of("doubles", new double[] {-0.0d, Double.NaN}, "d", 0.0d)),
        new Evaluation(
            "words.map(value, value + s)[1]",
            Map.of("words", new String[] {"cel", "other"}, "s", "!")),
        new Evaluation("flags.map(value, !value)[1]", Map.of("flags", new Boolean[] {false, true})),
        new Evaluation("flags.map(value, !value)[1]", Map.of("flags", new boolean[] {false, true})),
        new Evaluation("numbers.map(value, null)[0]", Map.of("numbers", new long[] {1, 2})),
        new Evaluation(
            "numbers.map(value, b ? value : x)[0]",
            Map.of("numbers", new long[] {1, 2}, "b", false, "x", "not an int")),
        new Evaluation(
            "numbers.map(value, value == target ? x : value)[0]",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", "not an int")),
        new Evaluation(
            "numbers.map(value, value == target ? x : value / y)[0]",
            Map.of("numbers", new long[] {1, 2}, "target", 1L, "x", "not an int", "y", 0L)),
        new Evaluation(
            "numbers.map(value, value == target ? x / y : value)[1]",
            Map.of("numbers", new long[] {1, 2}, "target", 1L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.map(value, value == target ? x / y : value)[0]",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.map(value, value == target ? b : value == x)[0]",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "b", unknownOf(223L), "x", 1L)),
        new Evaluation("words.map(value, value)[0]", Map.of("words", new String[] {null, "cel"})),
        new Evaluation("words.map(value, value)[1]", Map.of("words", new String[] {null, "cel"})),
        new Evaluation(
            "words.map(value, value + s)[0]",
            Map.of("words", new String[] {"cel", null}, "s", "!")),
        new Evaluation(
            "numbers.map(x, x + target)[1]",
            Map.of("numbers", new long[] {1, 2}, "x", 99L, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + target)[0]",
            Map.of("numbers", newErr("range"), "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + target)[0]",
            Map.of("numbers", unknownOf(224L), "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + target)[0]", Map.of("numbers", "not a list", "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + target)[0]", Map.of("numbers", new long[] {1, 2})),
        new Evaluation(
            "numbers.map(value, value + target)[-1]",
            Map.of("numbers", new long[] {1, 2}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + target)[99]",
            Map.of("numbers", new long[] {1, 2}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value + target)[2147483647]",
            Map.of("numbers", new long[] {1, 2}, "target", 2L)));
  }

  @ParameterizedTest
  @MethodSource("canonicalMapIndexEvaluations")
  void evaluatesCanonicalMapIndex(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void canonicalMapIndexMatchesCurrentFoldPartialActivationScope() {
    Ast ast = compile("numbers.map(value, value + target)[1]");
    Prog nativeProgram = (Prog) env.program(ast);
    Prog interpreterProgram =
        (Prog) env.program(ast, evalOptions(OptPartialEval, OptDisableNativeEval));
    Object vars =
        partialVars(Map.of("numbers", new long[] {1, 2}, "target", 2L), attributePattern("target"));

    assertIntegratedPlan(nativeProgram, "map index");
    assertEquivalent(nativeProgram.eval(vars).getVal(), interpreterProgram.eval(vars).getVal());
  }

  @Test
  void canonicalMapIndexUsesDefaultVariablesThroughTheCurrentFoldHierarchy() {
    Ast ast = compile("numbers.map(value, value + target)[1]");
    Prog nativeProgram = (Prog) env.program(ast, globals(Map.of("target", 2L)));
    Prog interpreterProgram =
        (Prog) env.program(ast, globals(Map.of("target", 2L)), evalOptions(OptDisableNativeEval));

    assertIntegratedPlan(nativeProgram, "map index");
    assertEquivalent(
        nativeProgram.eval(Map.of("numbers", new long[] {1, 2})).getVal(),
        interpreterProgram.eval(Map.of("numbers", new long[] {1, 2})).getVal());
  }

  @Test
  void canonicalMapIndexPreservesFullTraversalAndLaterFailurePrecedence() {
    assertResolutionCounts(
        "numbers.map(value, value == target ? x / y : value + x)[0]",
        Map.of("numbers", new long[] {1L, 2L, 3L}, "target", 2L, "x", 1L, "y", 0L),
        Map.of("numbers", 1, "target", 2, "x", 2, "y", 1));
    assertResolutionCounts(
        "numbers.map(value, value + target)[99]",
        Map.of("numbers", new long[] {1L, 2L, 3L}, "target", 2L),
        Map.of("numbers", 1, "target", 3));
  }

  static Stream<Evaluation> canonicalFilteredMapIndexEvaluations() {
    Val adaptedNumbers = DefaultTypeAdapter.Instance.nativeToValue(List.of(1L, 2L, 3L));
    return Stream.of(
        new Evaluation(
            "numbers.map(value, value >= target, value + target)[0]",
            Map.of("numbers", new int[] {1, 2, 3}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value != target, value == target)[1]",
            Map.of("numbers", new long[] {1, 2, 3}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value <= target, value * target)[1]",
            Map.of("numbers", List.of(1L, 2L, 3L), "target", 2L)),
        new Evaluation(
            "numbers.map(value, value != target, value - target)[1]",
            Map.of("numbers", new Object[] {1L, 2L, 3L}, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value <= target, value == target)[1]",
            Map.of("numbers", adaptedNumbers, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value == target, value + target)[0]",
            Map.of("numbers", new long[0], "target", 1L)),
        new Evaluation(
            "doubles.map(value, value == d, value + d)[0]",
            Map.of("doubles", new double[] {-0.0d, Double.NaN}, "d", 0.0d)),
        new Evaluation(
            "words.map(value, value == wordTarget, value + s)[1]",
            Map.of("words", new String[] {"cel", "other", "cel"}, "wordTarget", "cel", "s", "!")),
        new Evaluation(
            "flags.map(value, value == b, !value)[0]",
            Map.of("flags", new Boolean[] {false, true}, "b", true)),
        new Evaluation(
            "flags.map(value, value == b, !value)[0]",
            Map.of("flags", new boolean[] {false, true}, "b", true)),
        new Evaluation(
            "numbers.map(value, value > target, null)[0]",
            Map.of("numbers", new long[] {1, 2}, "target", 1L)),
        new Evaluation(
            "numbers.filter(value, value >= target)[1]",
            Map.of("numbers", new long[] {1, 2, 3}, "target", 2L)),
        new Evaluation(
            "words.filter(value, value == wordTarget)[0]",
            Map.of("words", new String[] {"other", "cel"}, "wordTarget", "cel")),
        new Evaluation(
            "numbers.map(value, value >= target, b ? value : x)[0]",
            Map.of("numbers", new long[] {1, 2}, "target", 1L, "b", false, "x", "not an int")),
        new Evaluation(
            "numbers.map(value, value >= target, value == target ? x : value)[1]",
            Map.of("numbers", new long[] {1, 2}, "target", 1L, "x", "not an int")),
        new Evaluation(
            "numbers.map(value, value >= target, value == target ? x : value)[0]",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", "not an int")),
        new Evaluation(
            "numbers.map(value, value > target, x / y)[0]",
            Map.of("numbers", new long[] {1, 2}, "target", 99L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.map(value, value > 0, value == target ? x / y : value)[0]",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "x", 1L, "y", 0L)),
        new Evaluation(
            "numbers.map(value, value == target ? b : true, value)[0]",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "b", newErr("predicate"))),
        new Evaluation(
            "numbers.map(value, value == target ? b : true, value)[0]",
            Map.of("numbers", new long[] {1, 2}, "target", 2L, "b", unknownOf(225L))),
        new Evaluation(
            "numbers.filter(value, b)[0]",
            Map.of("numbers", new Object[] {newErr("element"), 2L}, "b", true)),
        new Evaluation(
            "numbers.filter(value, b)[0]",
            Map.of("numbers", new Object[] {unknownOf(226L), 2L}, "b", true)),
        new Evaluation(
            "numbers.filter(value, b)[0]",
            Map.of("numbers", new Object[] {newErr("element")}, "b", false)),
        new Evaluation(
            "flags.filter(value, value)[0]",
            Map.of("flags", new Object[] {newErr("predicate"), true})),
        new Evaluation(
            "flags.filter(value, value)[0]", Map.of("flags", new Object[] {unknownOf(227L), true})),
        new Evaluation(
            "flags.filter(value, value)[0]", Map.of("flags", new Object[] {"not a bool"})),
        new Evaluation(
            "words.map(value, b, value)[0]",
            Map.of("words", new String[] {null, "cel"}, "b", true)),
        new Evaluation(
            "words.map(value, b, value)[1]",
            Map.of("words", new String[] {null, "cel"}, "b", true)),
        new Evaluation(
            "words.map(value, b, value + s)[0]",
            Map.of("words", new String[] {"cel", null}, "b", true, "s", "!")),
        new Evaluation(
            "numbers.map(x, x >= target, x + target)[1]",
            Map.of("numbers", new long[] {1, 2, 3}, "x", 99L, "target", 2L)),
        new Evaluation(
            "numbers.map(value, value == target, value + target)[0]",
            Map.of("numbers", newErr("range"), "target", 2L)),
        new Evaluation(
            "numbers.map(value, value == target, value + target)[0]",
            Map.of("numbers", unknownOf(228L), "target", 2L)),
        new Evaluation(
            "numbers.map(value, value == target, value)[0]",
            Map.of("numbers", "not a list", "target", 2L)),
        new Evaluation(
            "numbers.map(value, value == target, value)[0]", Map.of("numbers", new long[] {1, 2})),
        new Evaluation(
            "numbers.map(value, b, value + target)[0]",
            Map.of("numbers", new long[] {1, 2}, "b", true)),
        new Evaluation(
            "numbers.map(value, value > target, value)[-1]",
            Map.of("numbers", new long[] {1, 2}, "target", 1L)),
        new Evaluation(
            "numbers.map(value, value > target, value)[99]",
            Map.of("numbers", new long[] {1, 2}, "target", 1L)),
        new Evaluation(
            "numbers.map(value, value > target, value)[2147483647]",
            Map.of("numbers", new long[] {1, 2}, "target", 1L)));
  }

  @ParameterizedTest
  @MethodSource("canonicalFilteredMapIndexEvaluations")
  void evaluatesCanonicalFilteredMapIndex(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void canonicalFilteredMapIndexMatchesCurrentFoldPartialActivationScope() {
    Ast ast = compile("numbers.map(value, value >= target, value + target)[1]");
    Prog nativeProgram = (Prog) env.program(ast);
    Prog interpreterProgram =
        (Prog) env.program(ast, evalOptions(OptPartialEval, OptDisableNativeEval));
    Object vars =
        partialVars(
            Map.of("numbers", new long[] {1, 2, 3}, "target", 2L), attributePattern("target"));

    assertIntegratedPlan(nativeProgram, "filtered map index");
    assertEquivalent(nativeProgram.eval(vars).getVal(), interpreterProgram.eval(vars).getVal());
  }

  @Test
  void canonicalFilteredMapIndexUsesDefaultVariablesThroughTheCurrentFoldHierarchy() {
    Ast ast = compile("numbers.filter(value, value >= target)[1]");
    Prog nativeProgram = (Prog) env.program(ast, globals(Map.of("target", 2L)));
    Prog interpreterProgram =
        (Prog) env.program(ast, globals(Map.of("target", 2L)), evalOptions(OptDisableNativeEval));

    assertIntegratedPlan(nativeProgram, "filtered map index");
    assertEquivalent(
        nativeProgram.eval(Map.of("numbers", new long[] {1, 2, 3})).getVal(),
        interpreterProgram.eval(Map.of("numbers", new long[] {1, 2, 3})).getVal());
  }

  @Test
  void canonicalFilteredMapIndexPreservesOrderingSuppressionAndAcceptedPositions() {
    assertResolutionCounts(
        "numbers.map(value, value <= target, value == target ? x / y : value + x)[0]",
        Map.of("numbers", new long[] {1L, 2L, 3L}, "target", 2L, "x", 1L, "y", 0L),
        Map.of("numbers", 1, "target", 4, "x", 2, "y", 1));
    assertResolutionCounts(
        "numbers.map(value, value <= target, value + x)[99]",
        Map.of("numbers", new long[] {1L, 2L, 3L}, "target", 2L, "x", 1L),
        Map.of("numbers", 1, "target", 3, "x", 2));
    assertResolutionCounts(
        "numbers.map(value, value > target, value + x)[0]",
        Map.of("numbers", new long[] {1L, 2L}, "target", 99L, "x", 1L),
        Map.of("numbers", 1, "target", 2, "x", 0));
  }

  static Stream<Evaluation> canonicalMappedStringMembershipEvaluations() {
    return Stream.of(
        new Evaluation(
            "wordTarget in words.map(value, value)",
            Map.of("wordTarget", "cel", "words", new String[] {"zero", "cel", "last"})),
        new Evaluation(
            "wordTarget in words.map(value, value + s)",
            Map.of("wordTarget", "cel!", "words", new String[] {"zero", "cel", "last"}, "s", "!")),
        new Evaluation(
            "wordTarget in numbers.map(value, value == target ? s : key)",
            Map.of(
                "wordTarget",
                "cel",
                "numbers",
                new long[] {1, 2, 3},
                "target",
                2L,
                "s",
                "cel",
                "key",
                "other")),
        new Evaluation(
            "wordTarget in words.map(value, value)",
            Map.of("wordTarget", "cel", "words", new String[0])),
        new Evaluation(
            "wordTarget in words.map(value, value == key ? s : value)",
            Map.of(
                "wordTarget",
                "cel",
                "words",
                new String[] {"cel", "later"},
                "key",
                "later",
                "s",
                newErr("later transform"))),
        new Evaluation(
            "wordTarget in words.map(value, value == key ? s : value)",
            Map.of(
                "wordTarget",
                newErr("needle"),
                "words",
                new String[] {"cel", "later"},
                "key",
                "later",
                "s",
                newErr("later transform"))),
        new Evaluation(
            "wordTarget in words.map(value, value == key ? s : value)",
            Map.of(
                "wordTarget",
                unknownOf(229L),
                "words",
                new String[] {"cel", "later"},
                "key",
                "later",
                "s",
                newErr("later transform"))),
        new Evaluation(
            "wordTarget in words.map(value, value)",
            Map.of("wordTarget", 42L, "words", new String[] {"zero", "cel"})),
        new Evaluation(
            "wordTarget in words.map(value, value == key ? s : value)",
            Map.of(
                "wordTarget",
                42L,
                "words",
                new String[] {"zero", "later"},
                "key",
                "later",
                "s",
                newErr("later transform"))),
        new Evaluation(
            "wordTarget in words.map(value, b ? value : s)",
            Map.of(
                "wordTarget", "cel", "words", new String[] {"zero", "cel"}, "b", false, "s", 42L)),
        new Evaluation(
            "wordTarget in words.map(value, value == key ? s : value)",
            Map.of(
                "wordTarget",
                "cel",
                "words",
                new String[] {"cel", "later"},
                "key",
                "later",
                "s",
                42L)),
        new Evaluation(
            "wordTarget in words.map(value, value == key ? s : value)",
            Map.of(
                "wordTarget",
                "cel",
                "words",
                new String[] {"first", "cel"},
                "key",
                "first",
                "s",
                42L)),
        new Evaluation(
            "wordTarget in words.map(value, value)",
            Map.of("wordTarget", "cel", "words", new Object[] {stringOf(null), "cel"})),
        new Evaluation(
            "wordTarget in words.map(value, value)",
            Map.of("wordTarget", "cel", "words", newErr("range"))),
        new Evaluation(
            "wordTarget in words.map(value, value)",
            Map.of("wordTarget", "cel", "words", unknownOf(230L))),
        new Evaluation(
            "wordTarget in words.map(value, value)",
            Map.of("wordTarget", "cel", "words", "not a list")),
        new Evaluation("wordTarget in words.map(value, value)", Map.of("wordTarget", "cel")),
        new Evaluation(
            "wordTarget in words.map(value, value + s)",
            Map.of("wordTarget", "cel", "words", new String[] {"zero", "cel"})),
        new Evaluation(
            "wordTarget in words.map(value, value != key, value + s)",
            Map.of(
                "wordTarget",
                "cel!",
                "words",
                new String[] {"zero", "skip", "cel"},
                "key",
                "skip",
                "s",
                "!")),
        new Evaluation(
            "wordTarget in words.filter(value, value != key)",
            Map.of(
                "wordTarget", "cel", "words", new String[] {"zero", "skip", "cel"}, "key", "skip")),
        new Evaluation(
            "wordTarget in words.map(value, false, s)",
            Map.of(
                "wordTarget",
                "cel",
                "words",
                new String[] {"zero", "cel"},
                "s",
                newErr("suppressed transform"))),
        new Evaluation(
            "wordTarget in words.map(value, value == key ? b : true, value)",
            Map.of(
                "wordTarget",
                "cel",
                "words",
                new String[] {"cel", "later"},
                "key",
                "later",
                "b",
                newErr("later predicate"))),
        new Evaluation(
            "wordTarget in words.map(value, true, value == key ? s : value)",
            Map.of(
                "wordTarget",
                "cel",
                "words",
                new String[] {"cel", "later"},
                "key",
                "later",
                "s",
                newErr("later transform"))),
        new Evaluation(
            "wordTarget in words.filter(value, b)",
            Map.of(
                "wordTarget",
                "cel",
                "words",
                new Object[] {newErr("suppressed element")},
                "b",
                false)),
        new Evaluation(
            "wordTarget in words.filter(value, b)",
            Map.of(
                "wordTarget",
                "cel",
                "words",
                new Object[] {newErr("accepted element")},
                "b",
                true)),
        new Evaluation(
            "wordTarget in words.map(value, b, value)",
            Map.of("wordTarget", "cel", "words", new String[] {"zero", "cel"}, "b", "not a bool")),
        new Evaluation(
            "wordTarget in words.map(value, b, value)",
            Map.of(
                "wordTarget", "cel", "words", new String[] {"zero", "cel"}, "b", unknownOf(231L))),
        new Evaluation(
            "wordTarget in words.map(value, b, value)",
            Map.of(
                "wordTarget",
                newErr("needle"),
                "words",
                new String[] {"zero", "cel"},
                "b",
                newErr("predicate"))),
        new Evaluation(
            "wordTarget in words.filter(value, b)",
            Map.of("wordTarget", "cel", "words", new Object[] {stringOf(null), "cel"}, "b", true)),
        new Evaluation(
            "wordTarget in words.filter(value, b)",
            Map.of("wordTarget", "cel", "words", new Object[] {42L, "cel"}, "b", true)));
  }

  @ParameterizedTest
  @MethodSource("canonicalMappedStringMembershipEvaluations")
  void evaluatesCanonicalMappedStringMembership(Evaluation evaluation) {
    assertExpression(evaluation.expression(), evaluation.input());
  }

  @Test
  void nullBackedStringNeedleUsesTheCurrentMappedMembershipCompatibilityPath() {
    Programs programs = programs("wordTarget in words.map(value, value)");
    Object input = Map.of("wordTarget", stringOf(null), "words", new String[] {"zero", "cel"});

    Throwable nativeFailure = catchThrowable(() -> programs.nativeProgram.eval(input));
    Throwable interpreterFailure = catchThrowable(() -> programs.interpreterProgram.eval(input));

    assertThat(nativeFailure).isNotNull();
    assertThat(interpreterFailure).isNotNull();
    assertThat(nativeFailure.getClass()).isEqualTo(interpreterFailure.getClass());
    assertThat(nativeFailure.getMessage()).isEqualTo(interpreterFailure.getMessage());
  }

  @Test
  void canonicalMappedStringMembershipMatchesCurrentFoldPartialActivationScope() {
    Object input =
        partialVars(
            Map.of("wordTarget", "cel!", "words", new String[] {"zero", "cel"}, "s", "!"),
            attributePattern("s"));

    assertExpression("wordTarget in words.map(value, value + s)", input);
  }

  @Test
  void canonicalMappedStringMembershipUsesDefaultVariablesThroughTheCurrentFoldHierarchy() {
    Ast ast = compile("wordTarget in words.map(value, value != key, value + s)");
    Map<String, Object> defaults = Map.of("key", "skip", "s", "!");
    Prog nativeProgram = (Prog) env.program(ast, globals(defaults));
    Prog interpreterProgram =
        (Prog) env.program(ast, globals(defaults), evalOptions(OptDisableNativeEval));
    Map<String, Object> input =
        Map.of("wordTarget", "cel!", "words", new String[] {"zero", "skip", "cel"});

    assertIntegratedPlan(nativeProgram, "mapped string membership with default variables");
    assertEquivalent(nativeProgram.eval(input).getVal(), interpreterProgram.eval(input).getVal());
  }

  @Test
  void canonicalMappedStringMembershipPreservesOperandAndCompleteTraversalOrdering() {
    List<String> nativeOrder = new ArrayList<>();
    List<String> interpreterOrder = new ArrayList<>();
    Programs programs = programs("wordTarget in words.map(value, value + s)");
    ActivationFunction nativeActivation =
        orderedMappedStringMembershipActivation(nativeOrder, "cel!", new String[] {"zero", "cel"});
    ActivationFunction interpreterActivation =
        orderedMappedStringMembershipActivation(
            interpreterOrder, "cel!", new String[] {"zero", "cel"});

    assertEquivalent(
        programs.nativeProgram.eval(nativeActivation).getVal(),
        programs.interpreterProgram.eval(interpreterActivation).getVal());
    assertThat(nativeOrder).containsExactly("wordTarget", "words", "s", "s");
    assertThat(interpreterOrder).containsExactlyElementsOf(nativeOrder);

    assertResolutionCounts(
        "wordTarget in words.map(value, value == key ? s : value)",
        Map.of(
            "wordTarget",
            "cel",
            "words",
            new String[] {"cel", "other", "last"},
            "key",
            "other",
            "s",
            "mapped"),
        Map.of("wordTarget", 1, "words", 1, "key", 3, "s", 1));
    assertResolutionCounts(
        "wordTarget in words.map(value, value != key, value + s)",
        Map.of(
            "wordTarget",
            "cel!",
            "words",
            new String[] {"zero", "skip", "cel"},
            "key",
            "skip",
            "s",
            "!"),
        Map.of("wordTarget", 1, "words", 1, "key", 3, "s", 2));
  }

  @Test
  void nonCanonicalOrUnsupportedComprehensionsStayOnTheCurrentEvaluator() {
    for (String expression :
        List.of(
            "[1, 2, 3].exists(value, value == target)",
            "[1, 2, 3].all(value, value > 0)",
            "[1, 2, 3].exists_one(value, value == target)",
            "numbers.exists(value, string(value) == s)",
            "numbers.all(value, string(value) == s)",
            "numbers.exists_one(value, string(value) == s)",
            "numbers.exists(value, numbers.exists(other, other == value))",
            "numbers.all(value, numbers.all(other, other == value))",
            "numbers.exists_one(value, numbers.exists_one(other, other == value))",
            "numbers.exists(index, value, value == target)",
            "numbers.all(index, value, value == target)",
            "numbers.exists_one(index, value, value == target)",
            "numbers.filter(value, value == target)",
            "size([1, 2, 3].filter(value, value == target))",
            "size(numbers.filter(value, string(value) == s))",
            "numbers.map(value, value + 1)",
            "size([1, 2, 3].map(value, value + 1))",
            "size(numbers.map(value, string(value) == s))",
            "size([1, 2, 3].map(value, value > 0, value + 1))",
            "size(numbers.map(value, string(value) == s, value + 1))",
            "size(numbers.map(value, value > 0, string(value) == s))",
            "[1, 2, 3].map(value, value + 1)[0]",
            "[1, 2, 3].map(value, value > 0, value + 1)[0]",
            "numbers.map(value, string(value) == s)[0]",
            "numbers.map(value, value > 0, string(value))[0]",
            "numbers.map(value, value + 1)[x]",
            "numbers.map(value, value > 0, value + 1)[x]",
            "numbers.exists(value, numbers.map(other, other + value)[0] > 0)",
            "numbers.exists(value, numbers.map(other, other > value, other + value)[0] > 0)",
            "numbers.exists(value, size(numbers.map(other, other + value)) > 0)",
            "numbers.exists(value, size(numbers.map(other, other > value, other + value)) > 0)",
            "numbers.exists(value, size(numbers.filter(other, other == value)) > 0)",
            "[1, 2, 3].map(value, value + 1).exists(mapped, mapped == target)",
            "words.map(value, value + s).exists(mapped, mapped == wordTarget)",
            "words.map(value, value + s).all(mapped, mapped != wordTarget)",
            "words.map(value, value + s).exists_one(mapped, mapped == wordTarget)",
            "words.map(value, value != s, value + s).exists(mapped, mapped == wordTarget)",
            "words.filter(value, value != s).all(mapped, mapped != wordTarget)",
            "numbers.map(value, string(value)).exists(mapped, mapped == s)",
            "numbers.transformList(index, value, value + index)"
                + ".exists(mapped, mapped == target)",
            "numbers.map(value, value + 1)"
                + ".exists(mapped, numbers.exists(other, other == mapped))",
            "wordTarget in ['zero', 'cel'].map(value, value)",
            "wordTarget in words.map(value, value).map(other, other)",
            "wordTarget in words.map(value, string(target))",
            "target in numbers.map(value, value + 1)",
            "size(numbers.transformList(index, value, value))")) {
      Prog program = (Prog) env.program(compile(expression));
      assertCurrentPlan(program, expression);
    }
  }

  @SuppressWarnings("resource")
  @ParameterizedTest
  @ValueSource(
      strings = {
        "numbers.exists(value, value == target)",
        "numbers.all(value, value <= target)",
        "numbers.exists_one(value, value == target)"
      })
  void canonicalQuantifierPlansCanBeEvaluatedConcurrently(String expression) throws Exception {
    Prog program = (Prog) env.program(compile(expression));
    assertIntegratedPlan(program, expression);
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        long target = i;
        results.add(
            executor.submit(
                () ->
                    program
                        .eval(Map.of("numbers", new long[] {target - 1, target}, "target", target))
                        .getVal()
                        .booleanValue()));
      }
      for (Future<Boolean> result : results) {
        assertThat(result.get(5, SECONDS)).isTrue();
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  @SuppressWarnings("resource")
  @ParameterizedTest
  @ValueSource(
      strings = {
        "numbers.map(value, value + x).exists(mapped, mapped == target)",
        "numbers.map(value, value + x).all(mapped, mapped <= target)",
        "numbers.map(value, value + x).exists_one(mapped, mapped == target)",
        "numbers.map(value, value > x, value + x).exists(mapped, mapped == target)",
        "numbers.map(value, value > x, value + x).all(mapped, mapped <= target)",
        "numbers.filter(value, value > x).exists_one(mapped, mapped == x + 1)"
      })
  void canonicalIntMapQuantifierPlansCanBeEvaluatedConcurrently(String expression)
      throws Exception {
    Prog program = (Prog) env.program(compile(expression));
    assertIntegratedPlan(program, expression);
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        long argument = i;
        results.add(
            executor.submit(
                () ->
                    program
                        .eval(
                            Map.of(
                                "numbers",
                                new long[] {argument - 1, argument, argument + 1},
                                "x",
                                argument,
                                "target",
                                argument * 2L + 1L))
                        .getVal()
                        .booleanValue()));
      }
      for (Future<Boolean> result : results) {
        assertThat(result.get(5, SECONDS)).isTrue();
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  @SuppressWarnings("resource")
  @Test
  void canonicalFilterSizePlansCanBeEvaluatedConcurrently() throws Exception {
    Prog program = (Prog) env.program(compile("size(numbers.filter(value, value == target))"));
    assertIntegratedPlan(program, "filter size");
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Long>> results = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        long target = i;
        results.add(
            executor.submit(
                () ->
                    program
                        .eval(
                            Map.of(
                                "numbers",
                                new long[] {target - 1, target, target},
                                "target",
                                target))
                        .getVal()
                        .intValue()));
      }
      for (Future<Long> result : results) {
        assertThat(result.get(5, SECONDS)).isEqualTo(2L);
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  @SuppressWarnings("resource")
  @Test
  void canonicalMapSizePlansCanBeEvaluatedConcurrently() throws Exception {
    Prog program = (Prog) env.program(compile("size(numbers.map(value, value + target))"));
    assertIntegratedPlan(program, "map size");
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Long>> results = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        long target = i;
        results.add(
            executor.submit(
                () ->
                    program
                        .eval(
                            Map.of(
                                "numbers",
                                new long[] {target - 1, target, target + 1},
                                "target",
                                target))
                        .getVal()
                        .intValue()));
      }
      for (Future<Long> result : results) {
        assertThat(result.get(5, SECONDS)).isEqualTo(3L);
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  @SuppressWarnings("resource")
  @Test
  void canonicalFilteredMapSizePlansCanBeEvaluatedConcurrently() throws Exception {
    Prog program =
        (Prog) env.program(compile("size(numbers.map(value, value >= target, value + target))"));
    assertIntegratedPlan(program, "filtered map size");
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Long>> results = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        long target = i;
        results.add(
            executor.submit(
                () ->
                    program
                        .eval(
                            Map.of(
                                "numbers",
                                new long[] {target - 1, target, target + 1},
                                "target",
                                target))
                        .getVal()
                        .intValue()));
      }
      for (Future<Long> result : results) {
        assertThat(result.get(5, SECONDS)).isEqualTo(2L);
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  @SuppressWarnings("resource")
  @Test
  void canonicalMapIndexPlansCanBeEvaluatedConcurrently() throws Exception {
    Prog program = (Prog) env.program(compile("numbers.map(value, value + target)[1]"));
    assertIntegratedPlan(program, "map index");
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Long>> results = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        long target = i;
        results.add(
            executor.submit(
                () ->
                    program
                        .eval(
                            Map.of(
                                "numbers",
                                new long[] {target - 1, target, target + 1},
                                "target",
                                target))
                        .getVal()
                        .intValue()));
      }
      for (int i = 0; i < results.size(); i++) {
        assertThat(results.get(i).get(5, SECONDS)).isEqualTo((long) i * 2L);
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  @SuppressWarnings("resource")
  @Test
  void canonicalFilteredMapIndexPlansCanBeEvaluatedConcurrently() throws Exception {
    Prog program =
        (Prog) env.program(compile("numbers.map(value, value >= target, value + target)[1]"));
    assertIntegratedPlan(program, "filtered map index");
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Long>> results = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        long target = i;
        results.add(
            executor.submit(
                () ->
                    program
                        .eval(
                            Map.of(
                                "numbers",
                                new long[] {target - 1, target, target + 1},
                                "target",
                                target))
                        .getVal()
                        .intValue()));
      }
      for (int i = 0; i < results.size(); i++) {
        assertThat(results.get(i).get(5, SECONDS)).isEqualTo((long) i * 2L + 1L);
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  @SuppressWarnings("resource")
  @ParameterizedTest
  @ValueSource(
      strings = {
        "wordTarget in words.map(value, value + s)",
        "wordTarget in words.map(value, value != key, value + s)",
        "wordTarget in words.filter(value, value != key)"
      })
  void canonicalMappedStringMembershipPlansCanBeEvaluatedConcurrently(String expression)
      throws Exception {
    Prog program = (Prog) env.program(compile(expression));
    assertIntegratedPlan(program, expression);
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        String target = "value-" + i;
        String needle = expression.contains(".filter") ? target : target + "!";
        results.add(
            executor.submit(
                () ->
                    program
                        .eval(
                            Map.of(
                                "wordTarget",
                                needle,
                                "words",
                                new String[] {"skip", target},
                                "key",
                                "skip",
                                "s",
                                "!"))
                        .getVal()
                        .booleanValue()));
      }
      for (Future<Boolean> result : results) {
        assertThat(result.get(5, SECONDS)).isTrue();
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  @Test
  void usesDefaultVariablesThroughTheExistingActivationHierarchy() {
    Ast ast = compile("x + y");
    Prog nativeProgram = (Prog) env.program(ast, globals(Map.of("x", 40L)));
    Prog interpreterProgram =
        (Prog) env.program(ast, globals(Map.of("x", 40L)), evalOptions(OptDisableNativeEval));

    assertIntegratedPlan(nativeProgram, "x + y");
    assertEquivalent(
        nativeProgram.eval(Map.of("y", 2L)).getVal(),
        interpreterProgram.eval(Map.of("y", 2L)).getVal());
  }

  @Test
  void resolvesEachIdentifierAtTheSamePoint() {
    Programs programs = programs("x + x * x");
    AtomicInteger nativeResolutions = new AtomicInteger();
    AtomicInteger interpreterResolutions = new AtomicInteger();

    Val nativeResult = programs.nativeProgram.eval(countingActivation(nativeResolutions)).getVal();
    Val interpreterResult =
        programs.interpreterProgram.eval(countingActivation(interpreterResolutions)).getVal();

    assertEquivalent(nativeResult, interpreterResult);
    assertThat(nativeResolutions).hasValue(interpreterResolutions.get()).hasValue(3);
  }

  @Test
  void fallsBackWhenAnyProgramFeatureOrExpressionIsUnsupported() {
    Ast supported = compile("x + 1");
    Prog decorated = (Prog) env.program(supported, customDecorator(interpretable -> interpretable));
    Prog nativeDisabled = (Prog) env.program(supported, evalOptions(OptDisableNativeEval));
    Prog optimized = (Prog) env.program(supported, evalOptions(OptOptimize));
    Prog unsupported = (Prog) env.program(compile("[x, y]"));
    Prog unchecked = (Prog) env.program(env.parse("x + 1").getAst());

    assertCurrentPlan(decorated);
    assertCurrentPlan(nativeDisabled);
    assertCurrentPlan(optimized);
    assertCurrentPlan(unsupported);
    assertCurrentPlan(unchecked);
  }

  @SuppressWarnings("resource")
  @Test
  void immutablePlanCanBeEvaluatedConcurrently() throws Exception {
    Prog program = (Prog) env.program(compile("(x + 1) * (y - 2)"));
    assertIntegratedPlan(program, "(x + 1) * (y - 2)");
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Long>> results = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        long value = i;
        results.add(
            executor.submit(
                () -> program.eval(Map.of("x", value, "y", value + 4L)).getVal().intValue()));
      }
      for (int i = 0; i < results.size(); i++) {
        assertThat(results.get(i).get(5, SECONDS)).isEqualTo((long) (i + 1) * (i + 2));
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  @SuppressWarnings("resource")
  @Test
  void topLevelListConsumerPlansCanBeEvaluatedConcurrently() throws Exception {
    Prog index = (Prog) env.program(compile("numbers[1]"));
    Prog membership = (Prog) env.program(compile("wordTarget in words"));
    assertIntegratedPlan(index, "numbers[1]");
    assertIntegratedPlan(membership, "wordTarget in words");

    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        long value = i;
        results.add(
            executor.submit(
                () ->
                    index.eval(Map.of("numbers", new long[] {-1L, value})).getVal().intValue()
                            == value
                        && membership
                            .eval(
                                Map.of(
                                    "wordTarget",
                                    "value-" + value,
                                    "words",
                                    new String[] {"other", "value-" + value}))
                            .getVal()
                            .booleanValue()));
      }
      for (Future<Boolean> result : results) {
        assertThat(result.get(5, SECONDS)).isTrue();
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  @SuppressWarnings("resource")
  @Test
  void scalarListLiteralConsumerPlansCanBeEvaluatedConcurrently() throws Exception {
    Prog index = (Prog) env.program(compile("[x, y][1]"));
    Prog size = (Prog) env.program(compile("size([x, y])"));
    Prog membership = (Prog) env.program(compile("wordTarget in [s, key]"));
    assertIntegratedPlan(index, "[x, y][1]");
    assertIntegratedPlan(size, "size([x, y])");
    assertIntegratedPlan(membership, "wordTarget in [s, key]");

    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        long value = i;
        results.add(
            executor.submit(
                () ->
                    index.eval(Map.of("x", -1L, "y", value)).getVal().intValue() == value
                        && size.eval(Map.of("x", value, "y", value + 1L)).getVal().intValue() == 2L
                        && membership
                            .eval(
                                Map.of(
                                    "wordTarget",
                                    "value-" + value,
                                    "s",
                                    "other",
                                    "key",
                                    "value-" + value))
                            .getVal()
                            .booleanValue()));
      }
      for (Future<Boolean> result : results) {
        assertThat(result.get(5, SECONDS)).isTrue();
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  private Programs programs(String expression) {
    return programs(env, expression);
  }

  private static Programs programs(Env env, String expression) {
    Ast ast = compile(env, expression);
    Prog nativeProgram = (Prog) env.program(ast);
    Prog interpreterProgram = (Prog) env.program(ast, evalOptions(OptDisableNativeEval));
    Interpretable nativePlan = assertIntegratedPlan(nativeProgram, expression);
    assertThat(nativePlan.id()).as(expression).isEqualTo(ast.getExpr().getId());
    assertCurrentPlan(interpreterProgram, expression);
    assertThat(nativeProgram.cost()).as(expression).isEqualTo(interpreterProgram.cost());
    return new Programs(nativeProgram, interpreterProgram);
  }

  private void assertExpression(String expression, Object input) {
    Programs programs = programs(expression);
    assertEquivalent(
        programs.nativeProgram.eval(input).getVal(),
        programs.interpreterProgram.eval(input).getVal());
    assertThat(programs.nativeProgram.cost()).isEqualTo(programs.interpreterProgram.cost());
  }

  private static Interpretable undecoratedCurrent(Prog nativeProgram, Ast ast) {
    return ((Prog) nativeProgram.e.program(ast, evalOptions(OptDisableNativeEval))).interpretable;
  }

  private static Interpretable assertIntegratedPlan(Prog program, String description) {
    assertThat(program.interpretable.getClass().getSimpleName())
        .as(description)
        .isEqualTo("NativeIsland");
    return program.interpretable;
  }

  private static void assertCurrentPlan(Prog program) {
    assertThat(program.interpretable).isNotNull();
    assertThat(program.interpretable.getClass().getSimpleName()).isNotEqualTo("NativeIsland");
  }

  private static void assertCurrentPlan(Prog program, String description) {
    assertThat(program.interpretable).as(description).isNotNull();
    assertThat(program.interpretable.getClass().getSimpleName())
        .as(description)
        .isNotEqualTo("NativeIsland");
  }

  private static void assertEstablishedRoot(
      Prog program, String expectedSimpleName, String description) {
    assertThat(program.interpretable).as(description).isNotNull();
    assertThat(program.interpretable.getClass().getSimpleName())
        .as(description)
        .isEqualTo(expectedSimpleName);
  }

  private static void assertExpression(Env env, String expression, Object input) {
    Programs programs = programs(env, expression);
    assertEquivalent(
        programs.nativeProgram.eval(input).getVal(),
        programs.interpreterProgram.eval(input).getVal());
    assertThat(programs.nativeProgram.cost()).isEqualTo(programs.interpreterProgram.cost());
  }

  private void assertResolutionCounts(
      String expression, Map<String, Object> values, Map<String, Integer> expectedCounts) {
    Programs programs = programs(expression);
    Map<String, AtomicInteger> nativeCounts = new java.util.HashMap<>();
    Map<String, AtomicInteger> interpreterCounts = new java.util.HashMap<>();

    Val nativeResult =
        programs.nativeProgram.eval(countingActivation(values, nativeCounts)).getVal();
    Val interpreterResult =
        programs.interpreterProgram.eval(countingActivation(values, interpreterCounts)).getVal();

    assertEquivalent(nativeResult, interpreterResult);
    for (Map.Entry<String, Integer> expected : expectedCounts.entrySet()) {
      assertThat(count(nativeCounts, expected.getKey())).isEqualTo(expected.getValue());
      assertThat(count(interpreterCounts, expected.getKey())).isEqualTo(expected.getValue());
    }
  }

  private static int count(Map<String, AtomicInteger> counts, String name) {
    AtomicInteger count = counts.get(name);
    return count != null ? count.get() : 0;
  }

  private static ActivationFunction countingActivation(
      Map<String, Object> values, Map<String, AtomicInteger> counts) {
    return name -> {
      counts.computeIfAbsent(name, ignored -> new AtomicInteger()).incrementAndGet();
      return values.getOrDefault(name, ActivationFunction.ABSENT);
    };
  }

  private static ActivationFunction activation(Map<String, Object> values) {
    return name -> values.getOrDefault(name, ActivationFunction.ABSENT);
  }

  private static ActivationFunction orderedActivation(
      Map<String, Object> values, List<String> order) {
    return name -> {
      order.add(name);
      return values.getOrDefault(name, ActivationFunction.ABSENT);
    };
  }

  private static ActivationFunction orderedMembershipActivation(List<String> order) {
    return name -> {
      order.add(name);
      return switch (name) {
        case "wordTarget" -> newErr("needle");
        case "words" -> newErr("list");
        default -> ActivationFunction.ABSENT;
      };
    };
  }

  @SuppressWarnings("SameParameterValue")
  private static ActivationFunction orderedMappedStringMembershipActivation(
      List<String> order, Object needle, Object words) {
    return name -> {
      order.add(name);
      return switch (name) {
        case "wordTarget" -> needle;
        case "words" -> words;
        case "s" -> "!";
        default -> ActivationFunction.ABSENT;
      };
    };
  }

  @SuppressWarnings("SameParameterValue")
  private static ActivationFunction orderedBoolListActivation(
      List<String> order, Object b, Object c) {
    return name -> {
      order.add(name);
      return switch (name) {
        case "b" -> b;
        case "c" -> c;
        case "x" -> 1L;
        default -> ActivationFunction.ABSENT;
      };
    };
  }

  private static ActivationFunction orderedIntListActivation(List<String> order, boolean error) {
    return name -> {
      order.add(name);
      return switch (name) {
        case "x" -> error ? newErr("first") : 1L;
        case "y" -> 2L;
        default -> ActivationFunction.ABSENT;
      };
    };
  }

  @SuppressWarnings("SameParameterValue")
  private static ActivationFunction orderedDoubleListActivation(
      List<String> order, Object d, Object e) {
    return name -> {
      order.add(name);
      return switch (name) {
        case "d" -> d;
        case "e" -> e;
        default -> ActivationFunction.ABSENT;
      };
    };
  }

  private static ActivationFunction orderedStringListLiteralMembershipActivation(
      List<String> order, Object needle, Object first, Object second) {
    return name -> {
      order.add(name);
      return switch (name) {
        case "wordTarget" -> needle;
        case "s" -> first;
        case "key" -> second;
        default -> ActivationFunction.ABSENT;
      };
    };
  }

  private Ast compile(String expression) {
    return compile(env, expression);
  }

  private static Ast compile(Env env, String expression) {
    AstIssuesTuple result = env.compile(expression);
    assertThat(result.hasIssues()).as(expression).isFalse();
    return result.getAst();
  }

  private static ActivationFunction countingActivation(AtomicInteger counter) {
    return name -> {
      counter.incrementAndGet();
      return name.equals("x") ? 3L : ActivationFunction.ABSENT;
    };
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

  private record Evaluation(String expression, Object input) {
    @Override
    @NonNull
    public String toString() {
      return expression;
    }
  }

  private static final class IncrementLibrary implements Library {
    private static final String FUNCTION = "increment";
    private static final String OVERLOAD = "increment_int";

    private final Overload implementation;

    private IncrementLibrary() {
      this.implementation = Overload.unary(OVERLOAD, IncrementLibrary::increment);
    }

    @Override
    public List<EnvOption> getCompileOptions() {
      return List.of(
          declarations(
              Decls.newFunction(
                  FUNCTION, Decls.newOverload(OVERLOAD, List.of(Decls.Int), Decls.Int))));
    }

    @Override
    public List<ProgramOption> getProgramOptions() {
      return List.of(functions(implementation));
    }

    private static Val increment(Val value) {
      return value instanceof IntT
          ? intOf(value.intValue() + 1L)
          : Err.maybeNoSuchOverloadErr(value);
    }
  }

  private record Programs(Prog nativeProgram, Prog interpreterProgram) {}
}
