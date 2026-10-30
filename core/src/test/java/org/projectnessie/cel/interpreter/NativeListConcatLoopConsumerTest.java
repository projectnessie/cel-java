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
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Dispatcher.extendDispatcher;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.functions.Overload;

class NativeListConcatLoopConsumerTest {
  private static final ExactAdapter ADAPTER = new ExactAdapter();
  private static final Env ENV =
      newEnv(
          customTypeAdapter(ADAPTER),
          declarations(
              Decls.newVar("left", Decls.newListType(Decls.Int)),
              Decls.newVar("right", Decls.newListType(Decls.Int))));

  @Test
  void exactStandardMacroGlueRetainsNativeConcatLoopConsumers() {
    assertNativeQuantifier("(left + right).all(value, value > 0)", NativeQuantifierFold.class);
    assertNativeQuantifier("(left + right).exists(value, value > 0)", NativeQuantifierFold.class);
    assertNativeQuantifier(
        "(left + right).exists_one(value, value > 0)", NativeExistsOneFold.class);

    assertNativeListFold("(left + right).map(value, value + 1)");
    assertNativeListFold("(left + right).map(value, value > 0, value + 1)");
    assertNativeListFold("(left + right).filter(value, value > 0)");
  }

  @Test
  void intrinsicQuantifierGlueReplacementsRejectNativeFusion() {
    // The established planner lowers these operators to dedicated nodes and does not invoke the
    // custom implementations. Their supported contract here is a provenance veto, not replacement
    // evaluation semantics.
    assertQuantifierFallback(
        "(left + right).all(value, value > 0)",
        Overload.binary(Overloads.LogicalAnd, (left, right) -> True));
    assertQuantifierFallback(
        "(left + right).exists(value, value > 0)",
        Overload.binary(Overloads.LogicalOr, (left, right) -> False));
    assertQuantifierFallback(
        "(left + right).exists_one(value, value > 0)",
        Overload.binary(Overloads.Equals, (left, right) -> False));
    assertQuantifierFallback(
        "(left + right).exists_one(value, value > 0)",
        Overload.ternary(Overloads.Conditional, (condition, truthy, falsy) -> falsy));
  }

  @Test
  void dispatchedQuantifierGlueReplacementsRejectFusionAndRetainSemantics() {
    Interpretable all =
        assertQuantifierFallback(
            "(left + right).all(value, value > 0)",
            Overload.unary(Overloads.NotStrictlyFalse, ignored -> False));
    assertThat(all.eval(newActivation(Map.of("left", List.of(-1L), "right", List.of(-2L)))))
        .isEqualTo(True);

    Interpretable exists =
        assertQuantifierFallback(
            "(left + right).exists(value, value > 0)",
            Overload.unary(Overloads.LogicalNot, ignored -> False));
    assertThat(exists.eval(newActivation(Map.of("left", List.of(1L), "right", List.of(2L)))))
        .isEqualTo(False);

    Interpretable existsOne =
        assertQuantifierFallback(
            "(left + right).exists_one(value, value > 0)",
            Overload.binary(Overloads.AddInt64, (left, right) -> intOf(0L)));
    assertThat(existsOne.eval(newActivation(Map.of("left", List.of(1L), "right", List.of(-1L)))))
        .isEqualTo(False);
  }

  @Test
  void macroListGlueReplacementsRejectNativeFusion() {
    Overload addList = Overload.binary(Overloads.AddList, (left, right) -> left);
    assertListFoldAndConcatFallback("(left + right).map(value, value + 1)", addList);
    assertListFoldAndConcatFallback("(left + right).map(value, value > 0, value + 1)", addList);
    assertListFoldAndConcatFallback("(left + right).filter(value, value > 0)", addList);

    Overload conditional =
        Overload.ternary(Overloads.Conditional, (condition, truthy, falsy) -> falsy);
    assertListFoldFallback("(left + right).map(value, value > 0, value + 1)", conditional);
    assertListFoldFallback("(left + right).filter(value, value > 0)", conditional);
  }

  private static void assertNativeQuantifier(
      String expression, Class<? extends Interpretable> expectedRoot) {
    Interpretable root = root(plan(expression));
    assertThat(root).as(expression).isExactlyInstanceOf(expectedRoot);
    assertConcatRange(((EvalFold) root).iterRange, expression);
  }

  private static void assertNativeListFold(String expression) {
    Interpretable root = root(plan(expression));
    assertThat(root).as(expression).isExactlyInstanceOf(NativeScalarListFold.class);
    assertConcatRange(((NativeScalarListFold) root).iterRange, expression);
  }

  private static Interpretable assertQuantifierFallback(String expression, Overload replacement) {
    Interpretable root = root(plan(expression, replacement));
    assertThat(root).as(expression).isExactlyInstanceOf(EvalFold.class);
    assertConcatRange(((EvalFold) root).iterRange, expression);
    return root;
  }

  private static void assertListFoldFallback(String expression, Overload replacement) {
    Interpretable root = root(plan(expression, replacement));
    assertThat(root).as(expression).isExactlyInstanceOf(EvalListFold.class);
    assertConcatRange(((EvalListFold) root).iterRange, expression);
  }

  private static void assertListFoldAndConcatFallback(String expression, Overload replacement) {
    Interpretable root = root(plan(expression, replacement));
    assertThat(root).as(expression).isExactlyInstanceOf(EvalListFold.class);
    // The checked concat and the macro append share AddList. Replacing it necessarily vetoes both
    // native nodes, so a concat-range test cannot isolate only the macro-glue provenance check.
    assertThat(((EvalListFold) root).iterRange)
        .as(expression)
        .isExactlyInstanceOf(EvalBinary.class);
  }

  private static void assertConcatRange(Interpretable range, String expression) {
    assertThat(range).as(expression).isInstanceOf(NativeListConcat.class);
    assertThat(((NativeListConcat) range).sourceCount).as(expression).isEqualTo(2);
  }

  private static Interpretable root(Interpretable plan) {
    return plan instanceof NativeIsland island ? island.root() : plan;
  }

  private static Interpretable plan(String expression, Overload... replacements) {
    var compiled = ENV.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();

    Dispatcher standards = newDispatcher();
    standards.add(standardOverloads());
    Dispatcher dispatcher = replacements.length == 0 ? standards : extendDispatcher(standards);
    dispatcher.add(replacements);
    for (Overload replacement : replacements) {
      assertThat(dispatcher.findOverload(replacement.operator)).isSameAs(replacement);
    }

    TypeAdapter adapter = ENV.getTypeAdapter();
    AttributeFactory attributes =
        newAttributeFactory(defaultContainer, adapter, ENV.getTypeProvider());
    Interpreter interpreter =
        newInterpreter(
            dispatcher, defaultContainer, ENV.getTypeProvider(), adapter, attributes, true);
    return interpreter.newInterpretable(astToCheckedExpr(compiled.getAst()));
  }

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }
}
