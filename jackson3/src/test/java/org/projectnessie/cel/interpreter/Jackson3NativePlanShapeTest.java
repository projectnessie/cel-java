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
import static org.projectnessie.cel.EnvOption.customTypeProvider;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.types.jackson3.Jackson3Registry;

class Jackson3NativePlanShapeTest {
  @Test
  void plansExactListFieldSizeInEnabledAndEstablishedModes() {
    TypeRegistry registry = Jackson3Registry.newExactAggregateRegistry();
    var env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(Input.class),
            declarations(Decls.newVar("input", Decls.newObjectType(Input.class.getName()))));
    AstIssuesTuple result = env.compile("size(input.numbers)");
    assertThat(result.hasIssues()).withFailMessage(result.getIssues()::toString).isFalse();
    var checked = astToCheckedExpr(result.getAst());

    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    AttributeFactory attributes = newAttributeFactory(defaultContainer, registry, registry);
    Interpreter establishedInterpreter =
        newInterpreter(dispatcher, defaultContainer, registry, registry, attributes, false);
    Interpreter enabledInterpreter =
        newInterpreter(dispatcher, defaultContainer, registry, registry, attributes, true);

    Interpretable enabled =
        ((ExprInterpreter) enabledInterpreter).checkedPlanner(checked).plan(checked.getExpr());
    Interpretable established = establishedInterpreter.newInterpretable(checked);

    assertThat(enabled).isExactlyInstanceOf(NativeIsland.class);
    Interpretable enabledRoot = ((NativeIsland) enabled).root();
    assertThat(enabledRoot).isExactlyInstanceOf(NativeListSourceSize.class);
    assertThat(((NativeListSourceSize) enabledRoot).arg)
        .isExactlyInstanceOf(NativeExactListFieldAttr.class);

    assertThat(established).isExactlyInstanceOf(EvalUnary.class);
    assertThat(((EvalUnary) established).arg)
        .isExactlyInstanceOf(EvalExactAggregateFieldAttr.class);
  }

  @Test
  void plansRepeatedExactListFieldsAsOneNativeConcatConsumer() {
    TypeRegistry registry = Jackson3Registry.newExactAggregateRegistry();
    var env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(Input.class),
            declarations(Decls.newVar("input", Decls.newObjectType(Input.class.getName()))));
    AstIssuesTuple result = env.compile("size(input.numbers + input.numbers + input.numbers)");
    assertThat(result.hasIssues()).withFailMessage(result.getIssues()::toString).isFalse();
    var checked = astToCheckedExpr(result.getAst());

    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    AttributeFactory attributes = newAttributeFactory(defaultContainer, registry, registry);
    Interpreter establishedInterpreter =
        newInterpreter(dispatcher, defaultContainer, registry, registry, attributes, false);
    Interpreter enabledInterpreter =
        newInterpreter(dispatcher, defaultContainer, registry, registry, attributes, true);

    Interpretable enabled =
        ((ExprInterpreter) enabledInterpreter).checkedPlanner(checked).plan(checked.getExpr());
    Interpretable established = establishedInterpreter.newInterpretable(checked);

    assertThat(enabled).isExactlyInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) enabled).root()).isExactlyInstanceOf(NativeListConcatSize.class);
    NativeListConcatSize enabledRoot = (NativeListConcatSize) ((NativeIsland) enabled).root();
    assertThat(enabledRoot.sourceCount()).isEqualTo(3);
    assertThat(established).isExactlyInstanceOf(EvalUnary.class);
    assertThat(((EvalUnary) established).arg).isExactlyInstanceOf(EvalBinary.class);
  }

  @Test
  void plansIdentifierAndComputedCheckedStringMapKeysSymmetrically() {
    assertDynamicMapPlanShape("input.numbersByName[key]", NativeStringIdent.class);
    assertDynamicMapPlanShape("input.numbersByName[input.lookupKey]", NativeStringAttr.class);
    assertDynamicMapPlanShape("input.numbersByName[prefix + suffix]", NativeStringConcat.class);
  }

  @Test
  void plansConstantAndCheckedDynamicExactMapKeysByDeclaredKind() {
    PlanPair constantLookup = planExactMapExpression("input.numbersByInteger[1]");
    assertThat(constantLookup.enabled()).isExactlyInstanceOf(NativeIsland.class);
    NativeIntMapIndex constantLookupRoot =
        (NativeIntMapIndex) ((NativeIsland) constantLookup.enabled()).root();
    assertThat(constantLookupRoot.source).isExactlyInstanceOf(NativeExactMapFieldAttr.class);
    assertThat(constantLookupRoot.dynamicKey).isNull();
    assertThat(constantLookupRoot.celKey.intValue()).isEqualTo(1L);
    assertThat(constantLookup.established()).isNotInstanceOf(NativeIsland.class);

    PlanPair constantMembership = planExactMapExpression("1 in input.numbersByInteger");
    assertThat(constantMembership.enabled()).isExactlyInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) constantMembership.enabled()).root())
        .isExactlyInstanceOf(NativeMapMembership.class);
    assertThat(constantMembership.established()).isNotInstanceOf(NativeIsland.class);

    assertDynamicMapPlanShape("input.numbersByInteger[intKey]", "int", NativeIntIdent.class);
    assertDynamicMapPlanShape("input.numbersByBoolean[boolKey]", "bool", NativeBooleanIdent.class);
  }

  @Test
  void doesNotSpecializeCheckedDynamicKeyAgainstMapWithDynamicDeclaredKey() {
    PlanPair plan = planExactMapExpression("dynamicMap[intKey]");

    assertThat(plan.enabled()).isNotInstanceOf(NativeIsland.class);
    assertThat(plan.established()).isNotInstanceOf(NativeIsland.class);
  }

  private static void assertDynamicMapPlanShape(
      String expression, Class<? extends NativeStringCapability> keyShape) {
    assertDynamicMapPlanShape(expression, "string", keyShape);
  }

  private static void assertDynamicMapPlanShape(
      String expression, String celKeyKind, Class<?> keyShape) {
    PlanPair plan = planExactMapExpression(expression);

    assertThat(plan.enabled()).as(expression).isExactlyInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) plan.enabled()).root()).isExactlyInstanceOf(NativeIntMapIndex.class);
    NativeIntMapIndex enabledRoot = (NativeIntMapIndex) ((NativeIsland) plan.enabled()).root();
    assertThat(enabledRoot.source).isExactlyInstanceOf(NativeExactMapFieldAttr.class);
    assertThat(enabledRoot.dynamicKey).isNotNull();
    assertThat(enabledRoot.dynamicKey.celName()).isEqualTo(celKeyKind);
    assertThat(enabledRoot.dynamicKey.capability()).isExactlyInstanceOf(keyShape);
    assertThat(plan.established()).isNotInstanceOf(NativeIsland.class);
  }

  private static PlanPair planExactMapExpression(String expression) {
    TypeRegistry registry = Jackson3Registry.newExactAggregateRegistry();
    var env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(Input.class),
            declarations(
                Decls.newVar("input", Decls.newObjectType(Input.class.getName())),
                Decls.newVar("key", Decls.String),
                Decls.newVar("prefix", Decls.String),
                Decls.newVar("suffix", Decls.String),
                Decls.newVar("boolKey", Decls.Bool),
                Decls.newVar("intKey", Decls.Int),
                Decls.newVar("dynamicMap", Decls.newMapType(Decls.Dyn, Decls.Int))));
    AstIssuesTuple result = env.compile(expression);
    assertThat(result.hasIssues()).withFailMessage(result.getIssues()::toString).isFalse();
    var checked = astToCheckedExpr(result.getAst());

    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    AttributeFactory attributes = newAttributeFactory(defaultContainer, registry, registry);
    Interpreter establishedInterpreter =
        newInterpreter(dispatcher, defaultContainer, registry, registry, attributes, false);
    Interpreter enabledInterpreter =
        newInterpreter(dispatcher, defaultContainer, registry, registry, attributes, true);

    Interpretable enabled =
        ((ExprInterpreter) enabledInterpreter).checkedPlanner(checked).plan(checked.getExpr());
    Interpretable established = establishedInterpreter.newInterpretable(checked);

    return new PlanPair(enabled, established);
  }

  private record PlanPair(Interpretable enabled, Interpretable established) {}

  @SuppressWarnings("unused")
  public static final class Input {
    private final List<Long> numbers = List.of(1L, 2L);
    private final Map<String, Long> numbersByName = Map.of("one", 1L);
    private final Map<Boolean, Long> numbersByBoolean = Map.of(false, 0L, true, 1L);
    private final Map<Integer, Long> numbersByInteger = Map.of(-1, -1L, 1, 1L);
    private final String lookupKey = "one";

    public List<Long> getNumbers() {
      return numbers;
    }

    public Map<String, Long> getNumbersByName() {
      return numbersByName;
    }

    public Map<Boolean, Long> getNumbersByBoolean() {
      return numbersByBoolean;
    }

    public Map<Integer, Long> getNumbersByInteger() {
      return numbersByInteger;
    }

    public String getLookupKey() {
      return lookupKey;
    }
  }
}
