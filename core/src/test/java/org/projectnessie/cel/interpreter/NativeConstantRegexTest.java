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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.CEL.astToCheckedExpr;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.EvalOption.OptPartialEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.checker.Decls.Bool;
import static org.projectnessie.cel.checker.Decls.String;
import static org.projectnessie.cel.checker.Decls.newVar;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Overloads.MatchesString;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.Activation.newPartialActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.AttributePattern.newAttributePattern;
import static org.projectnessie.cel.interpreter.Dispatcher.extendDispatcher;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Ast;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.functions.Overload;

class NativeConstantRegexTest {
  private final Env env =
      newEnv(
          declarations(newVar("input", String), newVar("pattern", String), newVar("guard", Bool)));

  @Test
  void plansOnlyExactStandardLiteralPattern() {
    Interpretable nativePlan = plan("input.matches('a.*')", true);
    Interpretable establishedPlan = plan("input.matches('a.*')", false);
    Interpretable dynamicPlan = plan("input.matches(pattern)", true);
    Interpretable decoratedPlan = plan("input.matches('a.*')", true, node -> node);

    assertThat(root(nativePlan)).isExactlyInstanceOf(NativeConstantRegex.class);
    assertThat(root(establishedPlan)).isExactlyInstanceOf(EvalBinary.class);
    assertThat(root(dynamicPlan)).isExactlyInstanceOf(EvalBinary.class);
    assertThat(root(decoratedPlan)).isExactlyInstanceOf(EvalBinary.class);
    assertThat(Coster.Cost.estimateCost(nativePlan))
        .isEqualTo(Coster.Cost.estimateCost(establishedPlan));

    Overload replacement = Overload.binary(MatchesString, (left, right) -> False);
    Interpretable replaced = planWithReplacement("input.matches('a.*')", replacement);
    assertThat(root(replaced)).isExactlyInstanceOf(EvalBinary.class);
    assertThat(replaced.eval(newActivation(Map.of("input", "abc"))).booleanValue()).isFalse();
  }

  @Test
  void validPatternsMatchEstablishedEvaluation() {
    for (String expression :
        List.of(
            "input.matches('a.*')",
            "input.matches('^a$')",
            "input.matches('z')",
            "input.matches('')")) {
      for (String input : List.of("", "a", "abc", "zzz")) {
        assertEquivalent(expression, Map.of("input", input));
      }
    }
  }

  @Test
  void invalidPatternFailureRetainsReachabilityAndLeftPrecedence() {
    assertEquivalent("false && input.matches('[')", Map.of("input", "abc"));
    assertEquivalent("true ? true : input.matches('[')", Map.of("input", "abc"));

    Val reached = assertEquivalent("input.matches('[')", Map.of("input", "abc"));
    assertThat(reached).isInstanceOf(Err.class).asString().contains("Unclosed character class");

    Val leftError = newErr("left failed");
    Val leftUnknown = unknownOf(71L);
    assertThat(assertEquivalent("input.matches('[')", Map.of("input", leftError)))
        .isSameAs(leftError);
    assertThat(assertEquivalent("input.matches('[')", Map.of("input", leftUnknown)))
        .isSameAs(leftUnknown);
  }

  @Test
  void exceptionalNativeStringValuesUseEstablishedContinuation() {
    Val result = assertEquivalent("input.matches('a')", Map.of("input", StringT.stringOf(null)));

    assertThat(result).isInstanceOf(Err.class);
  }

  @Test
  void evaluatesInputOnce() {
    Interpretable plan = plan("input.matches('a.*')", true);
    AtomicInteger resolutions = new AtomicInteger();

    Val result =
        plan.eval(
            newActivation(
                (Function<String, Object>)
                    name -> {
                      assertThat(name).isEqualTo("input");
                      resolutions.incrementAndGet();
                      return "abc";
                    }));

    assertThat(result.booleanValue()).isTrue();
    assertThat(resolutions).hasValue(1);
  }

  @Test
  void partialEvaluationPreservesUnknown() {
    Ast ast = compile("input.matches('a.*')");
    Program nativeProgram = env.program(ast, evalOptions(OptPartialEval));
    Program establishedProgram =
        env.program(ast, evalOptions(OptPartialEval, OptDisableNativeEval));
    Activation partial = newPartialActivation(Map.of("input", "abc"), newAttributePattern("input"));

    Val nativeValue = nativeProgram.eval(partial).getVal();
    Val establishedValue = establishedProgram.eval(partial).getVal();
    assertThat(nativeValue).matches(value -> isUnknown(value));
    assertEquivalent(nativeValue, establishedValue, "partial");
  }

  @Test
  void oneProgramSupportsConcurrentEvaluation() throws Exception {
    Interpretable plan = plan("input.matches('value-[0-9]+')", true);
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        String input = "value-" + i;
        results.add(
            executor.submit(() -> plan.eval(newActivation(Map.of("input", input))).booleanValue()));
      }
      for (Future<Boolean> result : results) {
        assertThat(result.get(5, SECONDS)).isTrue();
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  private Val assertEquivalent(String expression, Object variables) {
    Val nativeValue = plan(expression, true).eval(newActivation(variables));
    Val establishedValue = plan(expression, false).eval(newActivation(variables));
    assertEquivalent(nativeValue, establishedValue, expression);
    return nativeValue;
  }

  private static void assertEquivalent(Val nativeValue, Val establishedValue, String description) {
    assertThat(nativeValue.getClass()).as(description).isEqualTo(establishedValue.getClass());
    assertThat(nativeValue.toString()).as(description).isEqualTo(establishedValue.toString());
    assertThat(nativeValue.value()).as(description).isEqualTo(establishedValue.value());
  }

  private Ast compile(String expression) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    return compiled.getAst();
  }

  private Interpretable plan(
      String expression, boolean nativeEnabled, InterpretableDecorator... decorators) {
    Ast ast = compile(expression);
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    return interpreter(dispatcher, nativeEnabled)
        .newInterpretable(astToCheckedExpr(ast), decorators);
  }

  private Interpretable planWithReplacement(String expression, Overload replacement) {
    Ast ast = compile(expression);
    Dispatcher standards = newDispatcher();
    standards.add(standardOverloads());
    Dispatcher dispatcher = extendDispatcher(standards);
    dispatcher.add(replacement);
    return interpreter(dispatcher, true).newInterpretable(astToCheckedExpr(ast));
  }

  private Interpreter interpreter(Dispatcher dispatcher, boolean nativeEnabled) {
    TypeAdapter adapter = env.getTypeAdapter();
    AttributeFactory attributes =
        newAttributeFactory(defaultContainer, adapter, env.getTypeProvider());
    return newInterpreter(
        dispatcher, defaultContainer, env.getTypeProvider(), adapter, attributes, nativeEnabled);
  }

  private static Interpretable root(Interpretable plan) {
    return plan instanceof NativeIsland island ? island.root() : plan;
  }
}
