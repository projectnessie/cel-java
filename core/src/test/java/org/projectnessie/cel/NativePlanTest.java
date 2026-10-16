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
  void preservesPartialActivationUnknowns() {
    Ast ast = compile("x + 1");
    Prog nativeProgram = (Prog) env.program(ast);
    Prog interpreterProgram =
        (Prog) env.program(ast, evalOptions(OptPartialEval, OptDisableNativeEval));
    Object vars = partialVars(Map.of("x", 41L), attributePattern("x"));

    assertEquivalent(nativeProgram.eval(vars).getVal(), interpreterProgram.eval(vars).getVal());
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
  void fallsBackWhenAnyUnsupportedProgramFeatureOrExpressionIsPresent() {
    Ast supported = compile("x + 1");
    Prog decorated = (Prog) env.program(supported, customDecorator(interpretable -> interpretable));
    Prog nativeDisabled = (Prog) env.program(supported, evalOptions(OptDisableNativeEval));
    Prog optimized = (Prog) env.program(supported, evalOptions(OptOptimize));
    Prog unsupported = (Prog) env.program(compile("[x, y]"));
    Prog unchecked = (Prog) env.program(env.parse("x + 1").getAst());

    assertCurrentPlan(decorated);
    assertCurrentPlan(nativeDisabled);
    assertIntegratedPlan(optimized, "x + 1 with OptOptimize");
    assertCurrentPlan(unsupported);
    assertCurrentPlan(unchecked);
  }

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
