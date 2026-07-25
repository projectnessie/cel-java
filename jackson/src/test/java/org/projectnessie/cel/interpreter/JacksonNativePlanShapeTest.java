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

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.types.jackson.JacksonRegistry;

class JacksonNativePlanShapeTest {
  @Test
  void plansCheckedDynamicStringBooleanAndSignedIntegerMapKeys() {
    assertDynamicMapPlanShape("input.stringNumbers[stringKey]", NativeStringIdent.class);
    assertDynamicMapPlanShape("input.stringNumbers[input.lookupString]", NativeStringAttr.class);
    assertDynamicMapPlanShape("input.stringNumbers[prefix + suffix]", NativeStringConcat.class);
    assertDynamicMapPlanShape("input.booleanNumbers[booleanKey]", NativeBooleanIdent.class);
    assertDynamicMapPlanShape("input.booleanNumbers[input.lookupBoolean]", NativeBooleanAttr.class);
    assertDynamicMapPlanShape("input.booleanNumbers[!booleanKey]", NativeBooleanNot.class);
    assertDynamicMapPlanShape("input.integerNumbers[integerKey]", NativeIntIdent.class);
    assertDynamicMapPlanShape("input.integerNumbers[input.lookupInteger]", NativeIntAttr.class);
    assertDynamicMapPlanShape("input.integerNumbers[integerKey + 0]", NativeIntAdd.class);
  }

  @Test
  void plansConstantSignedIntegerLookupAndMembership() {
    Interpretable lookup = plan("input.integerNumbers[1]");
    assertThat(lookup).isExactlyInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) lookup).root()).isExactlyInstanceOf(NativeIntMapIndex.class);
    NativeIntMapIndex root = (NativeIntMapIndex) ((NativeIsland) lookup).root();
    assertThat(root.source).isExactlyInstanceOf(NativeExactMapFieldAttr.class);
    assertThat(root.dynamicKey).isNull();
    assertThat(root.hostKey).isEqualTo(1L);

    Interpretable membership = plan("1 in input.integerNumbers");
    assertThat(membership).isExactlyInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) membership).root()).isExactlyInstanceOf(NativeMapMembership.class);
  }

  @Test
  void doesNotSpecializeSignedIntegerConstantAgainstDynamicKeyMap() {
    Interpretable enabled = plan("dynamicNumbers[1]");

    assertThat(enabled).isNotInstanceOf(NativeIsland.class);
  }

  private static void assertDynamicMapPlanShape(String expression, Class<?> keyShape) {
    Interpretable enabled = plan(expression);

    assertThat(enabled).as(expression).isExactlyInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) enabled).root()).isExactlyInstanceOf(NativeIntMapIndex.class);
    NativeIntMapIndex enabledRoot = (NativeIntMapIndex) ((NativeIsland) enabled).root();
    assertThat(enabledRoot.source).isExactlyInstanceOf(NativeExactMapFieldAttr.class);
    assertThat(enabledRoot.dynamicKey).isExactlyInstanceOf(NativeMapDynamicKey.class);
    assertThat(enabledRoot.dynamicKey.capability()).isExactlyInstanceOf(keyShape);
    assertThat(enabledRoot.hostKey).isNull();
    assertThat(enabledRoot.celKey).isNull();
  }

  private static Interpretable plan(String expression) {
    TypeRegistry registry = JacksonRegistry.newExactAggregateRegistry();
    var env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(Input.class),
            declarations(
                Decls.newVar("input", Decls.newObjectType(Input.class.getName())),
                Decls.newVar("stringKey", Decls.String),
                Decls.newVar("booleanKey", Decls.Bool),
                Decls.newVar("integerKey", Decls.Int),
                Decls.newVar("prefix", Decls.String),
                Decls.newVar("suffix", Decls.String),
                Decls.newVar("dynamicNumbers", Decls.newMapType(Decls.Dyn, Decls.Int))));
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

    assertThat(established).isNotInstanceOf(NativeIsland.class);
    return enabled;
  }

  @SuppressWarnings("unused")
  public static final class Input {
    private final Map<String, Long> stringNumbers = Map.of("one", 1L);
    private final Map<Boolean, Long> booleanNumbers = Map.of(true, 1L);
    private final Map<Integer, Long> integerNumbers = Map.of(1, 1L);
    private final String lookupString = "one";
    private final boolean lookupBoolean = true;
    private final int lookupInteger = 1;

    public Map<String, Long> getStringNumbers() {
      return stringNumbers;
    }

    public Map<Boolean, Long> getBooleanNumbers() {
      return booleanNumbers;
    }

    public Map<Integer, Long> getIntegerNumbers() {
      return integerNumbers;
    }

    public String getLookupString() {
      return lookupString;
    }

    public boolean isLookupBoolean() {
      return lookupBoolean;
    }

    public int getLookupInteger() {
      return lookupInteger;
    }
  }
}
