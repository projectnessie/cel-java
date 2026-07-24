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
  void plansIdentifierAndComputedCheckedStringMapKeysSymmetrically() {
    assertDynamicMapPlanShape("input.numbers[key]", NativeStringIdent.class);
    assertDynamicMapPlanShape("input.numbers[input.lookupKey]", NativeStringAttr.class);
    assertDynamicMapPlanShape("input.numbers[prefix + suffix]", NativeStringConcat.class);
  }

  private static void assertDynamicMapPlanShape(
      String expression, Class<? extends NativeStringCapability> keyShape) {
    TypeRegistry registry = JacksonRegistry.newExactAggregateRegistry();
    var env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(Input.class),
            declarations(
                Decls.newVar("input", Decls.newObjectType(Input.class.getName())),
                Decls.newVar("key", Decls.String),
                Decls.newVar("prefix", Decls.String),
                Decls.newVar("suffix", Decls.String)));
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

    assertThat(enabled).as(expression).isExactlyInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) enabled).root()).isExactlyInstanceOf(NativeIntMapIndex.class);
    NativeIntMapIndex enabledRoot = (NativeIntMapIndex) ((NativeIsland) enabled).root();
    assertThat(enabledRoot.source).isExactlyInstanceOf(NativeExactMapFieldAttr.class);
    assertThat(enabledRoot.dynamicKey).isExactlyInstanceOf(keyShape);
    assertThat(established).isNotInstanceOf(NativeIsland.class);
  }

  @SuppressWarnings("unused")
  public static final class Input {
    private final Map<String, Long> numbers = Map.of("one", 1L);
    private final String lookupKey = "one";

    public Map<String, Long> getNumbers() {
      return numbers;
    }

    public String getLookupKey() {
      return lookupKey;
    }
  }
}
