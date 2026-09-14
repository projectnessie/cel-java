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
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.checker.Decls.Int;
import static org.projectnessie.cel.checker.Decls.newListType;
import static org.projectnessie.cel.checker.Decls.newVar;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.Overloads.InList;
import static org.projectnessie.cel.common.types.Overloads.StringToInt;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Decl;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.common.types.BoolT;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Mapper;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableConst;
import org.projectnessie.cel.interpreter.functions.Overload;

class NativeOptimizeCompositionTest {
  private static final Decl X = newVar("x", Int);
  private static final Decl VALUES = newVar("values", newListType(Int));

  @Test
  void foldsAndComposesAllFourOptimizerTransforms() {
    Plans conversion = plans("int(\"42\")");
    assertThat(conversion.optimized).isInstanceOf(NativeIntConst.class);
    assertRepeatedIdentityAndParity(conversion, emptyActivation());

    Plans list = plans("[int(\"1\"), 2]");
    assertThat(list.optimized).isInstanceOf(NativeConstantIntListLiteral.class);
    assertRepeatedIdentityAndParity(list, emptyActivation());

    Plans map = plans("{\"answer\": int(\"42\")}");
    assertThat(map.optimized).isInstanceOf(InterpretableConst.class);
    assertThat(map.optimized).isNotInstanceOf(NativeScalarListLiteralCapability.class);
    assertThat(((Mapper) map.optimized.eval(emptyActivation())).get(stringOf("answer")))
        .isEqualTo(intOf(42));
    assertRepeatedIdentityAndParity(map, emptyActivation());

    Plans membership = plans("x in [int(\"1\"), 2, 3]", X);
    assertThat(membership.optimized).isInstanceOf(EvalSetMembership.class);
    assertParity(membership, newActivation(Map.of("x", 1L)));
    assertParity(membership, newActivation(Map.of("x", 9L)));
  }

  @Test
  void constantListsRetainOnlyExistingTypedConsumerGates() {
    Plans size = plans("size([int(\"1\"), 2])");
    assertThat(size.optimized).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) size.optimized).root()).isInstanceOf(NativeListLiteralSize.class);
    assertParity(size, emptyActivation());

    Plans literalIndex = plans("[int(\"1\"), 2][1]");
    assertThat(literalIndex.optimized).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) literalIndex.optimized).root())
        .isInstanceOf(NativeIntListLiteralIndex.class);
    assertParity(literalIndex, emptyActivation());

    Plans foldedIndex = plans("values[int(\"1\")]", VALUES);
    assertThat(foldedIndex.optimized).isNotInstanceOf(NativeIntListLiteralIndex.class);
    assertParity(foldedIndex, newActivation(Map.of("values", new long[] {1L, 2L})));
  }

  @Test
  void replacementResultsFoldButCannotClaimNativeCapabilities() {
    Overload mismatched = Overload.unary(StringToInt, ignored -> stringOf("replacement"));
    Plans conversion = plans("int(\"ignored\")", new Decl[0], mismatched);
    assertThat(conversion.optimized).isInstanceOf(InterpretableConst.class);
    assertThat(conversion.optimized).isNotInstanceOf(NativeIntCapability.class);
    assertThat(conversion.optimized.eval(emptyActivation())).isEqualTo(stringOf("replacement"));

    Plans list = plans("[int(\"ignored\")]", new Decl[0], mismatched);
    assertThat(list.optimized).isInstanceOf(InterpretableConst.class);
    assertThat(list.optimized).isNotInstanceOf(NativeIntListLiteralCapability.class);
    assertThat(((Lister) list.optimized.eval(emptyActivation())).nativeGetAt(0))
        .isEqualTo(stringOf("replacement"));

    Overload nullResult = Overload.unary(StringToInt, ignored -> null);
    Plans nullable = plans("int(\"ignored\")", new Decl[0], nullResult);
    assertThat(nullable.optimized).isInstanceOf(InterpretableConst.class);
    assertThat(nullable.optimized).isNotInstanceOf(NativeIntCapability.class);
    assertThat(nullable.optimized.eval(emptyActivation())).isNull();
  }

  @Test
  void membershipReplacementKeepsEstablishedOptimizerBehavior() {
    Overload replacement = Overload.binary(InList, (left, right) -> BoolT.False);
    Plans membership = plans("x in [1, 2]", new Decl[] {X}, replacement);

    assertThat(membership.established.eval(newActivation(Map.of("x", 1L)))).isSameAs(BoolT.True);
    assertThat(membership.optimized.eval(newActivation(Map.of("x", 1L)))).isSameAs(BoolT.True);
  }

  @Test
  void sameCheckedAstSelectsTheFourExpectedPolicies() {
    PlanInputs inputs = inputs("x + 1", new Decl[] {X});
    InterpretableDecorator custom = node -> node;

    assertThat(inputs.enabled.checkedPlanner(inputs.checked).policy())
        .isSameAs(PlanningPolicy.NATIVE_SPECIALIZATION_PERMITTED);
    assertThat(inputs.enabled.checkedPlanner(inputs.checked, Interpreter.optimize()).policy())
        .isSameAs(PlanningPolicy.NATIVE_OPTIMIZED);
    assertThat(inputs.disabled.checkedPlanner(inputs.checked, Interpreter.optimize()).policy())
        .isSameAs(PlanningPolicy.ESTABLISHED_ONLY);
    assertThat(inputs.enabled.checkedPlanner(inputs.checked, custom).policy())
        .isSameAs(PlanningPolicy.ESTABLISHED_ONLY);

    Activation activation = newActivation(Map.of("x", 41L));
    Val expected =
        inputs.disabled.newInterpretable(inputs.checked, Interpreter.optimize()).eval(activation);
    assertThat(inputs.enabled.newInterpretable(inputs.checked).eval(activation))
        .isEqualTo(expected);
    assertThat(
            inputs
                .enabled
                .newInterpretable(inputs.checked, Interpreter.optimize())
                .eval(activation))
        .isEqualTo(expected);
    assertThat(inputs.enabled.newInterpretable(inputs.checked, custom).eval(activation))
        .isEqualTo(expected);
  }

  private static Plans plans(String expression, Decl... declarations) {
    return plans(expression, declarations, new Overload[0]);
  }

  private static Plans plans(String expression, Decl[] declarations, Overload... replacements) {
    PlanInputs inputs = inputs(expression, declarations, replacements);
    return new Plans(
        inputs.disabled.newInterpretable(inputs.checked, Interpreter.optimize()),
        inputs.enabled.newInterpretable(inputs.checked, Interpreter.optimize()));
  }

  private static PlanInputs inputs(
      String expression, Decl[] declarations, Overload... replacements) {
    Env env = newEnv(declarations(declarations));
    AstIssuesTuple compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).withFailMessage(compiled.getIssues()::toString).isFalse();
    CheckedExpr checked = astToCheckedExpr(compiled.getAst());

    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    dispatcher.add(replacements);
    AttributeFactory attributes =
        newAttributeFactory(defaultContainer, env.getTypeAdapter(), env.getTypeProvider());
    ExprInterpreter disabled =
        (ExprInterpreter)
            newInterpreter(
                dispatcher,
                defaultContainer,
                env.getTypeProvider(),
                env.getTypeAdapter(),
                attributes,
                false);
    ExprInterpreter enabled =
        (ExprInterpreter)
            newInterpreter(
                dispatcher,
                defaultContainer,
                env.getTypeProvider(),
                env.getTypeAdapter(),
                attributes,
                true);
    return new PlanInputs(checked, disabled, enabled);
  }

  private static void assertRepeatedIdentityAndParity(Plans plans, Activation activation) {
    Val first = plans.optimized.eval(activation);
    assertThat(plans.optimized.eval(activation)).isSameAs(first);
    assertThat(first).isEqualTo(plans.established.eval(activation));
  }

  private static void assertParity(Plans plans, Activation activation) {
    assertThat(plans.optimized.eval(activation)).isEqualTo(plans.established.eval(activation));
  }

  private record Plans(Interpretable established, Interpretable optimized) {}

  private record PlanInputs(
      CheckedExpr checked, ExprInterpreter disabled, ExprInterpreter enabled) {}
}
