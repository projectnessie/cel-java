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

import static java.lang.reflect.Modifier.isPublic;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newEmptyRegistry;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.InterpretablePlanner.newPlanner;
import static org.projectnessie.cel.interpreter.InterpretablePlanner.newUncheckedPlanner;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.Interpreter.newStandardInterpreter;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;

class PlanningPolicyTest {
  private final ProtoTypeRegistry registry = newEmptyRegistry();
  private final Dispatcher dispatcher = newDispatcher();
  private final AttributeFactory attributes =
      newAttributeFactory(defaultContainer, registry, registry);
  private final Expr expression =
      Expr.newBuilder().setId(1L).setConstExpr(Constant.newBuilder().setInt64Value(42L)).build();
  private final CheckedExpr checked =
      CheckedExpr.newBuilder().setExpr(expression).putTypeMap(1L, Decls.Int).build();

  @Test
  void interpreterFactoriesApplyPermissionOnlyToUndecoratedCheckedPlans() {
    ExprInterpreter oldFactory =
        interpreter(newInterpreter(dispatcher, defaultContainer, registry, registry, attributes));
    ExprInterpreter disabled =
        interpreter(
            newInterpreter(dispatcher, defaultContainer, registry, registry, attributes, false));
    ExprInterpreter enabled =
        interpreter(
            newInterpreter(dispatcher, defaultContainer, registry, registry, attributes, true));
    ExprInterpreter standard =
        interpreter(newStandardInterpreter(defaultContainer, registry, registry, attributes));

    assertEstablished(oldFactory.checkedPlanner(checked));
    assertEstablished(disabled.checkedPlanner(checked));
    assertPermitted(enabled.checkedPlanner(checked));
    assertPermitted(enabled.checkedPlanner(emptyMap(), emptyMap()));
    assertOptimized(enabled.checkedPlanner(checked, Interpreter.optimize()));
    assertEstablished(disabled.checkedPlanner(checked, Interpreter.optimize()));
    InterpretableDecorator wrapped = node -> Interpreter.optimize().decorate(node);
    assertEstablished(enabled.checkedPlanner(checked, wrapped));
    assertEstablished(
        enabled.checkedPlanner(checked, Interpreter.optimize(), Interpreter.optimize()));
    assertEstablished(enabled.checkedPlanner(checked, plan -> plan));
    assertEstablished(enabled.checkedPlanner(emptyMap(), emptyMap(), plan -> plan));
    assertEstablished(enabled.uncheckedPlanner());
    assertEstablished(enabled.uncheckedPlanner(plan -> plan));
    assertEstablished(standard.checkedPlanner(checked));

    assertThat(enabled.newInterpretable(checked).eval(emptyActivation()).intValue()).isEqualTo(42L);
    Interpretable optimizedConstant = enabled.newInterpretable(checked, Interpreter.optimize());
    assertThat(optimizedConstant).isInstanceOf(NativeIntConst.class);
    assertThat(optimizedConstant).isInstanceOf(Interpretable.InterpretableConst.class);
    assertThat(enabled.newUncheckedInterpretable(expression).eval(emptyActivation()).intValue())
        .isEqualTo(42L);
  }

  @Test
  void publicPlannerFactoriesRemainEstablishedOnly() {
    Planner checkedWrapper =
        planner(newPlanner(dispatcher, registry, registry, attributes, defaultContainer, checked));
    Planner checkedMaps =
        planner(
            newPlanner(
                dispatcher, registry, registry, attributes, defaultContainer, Map.of(), Map.of()));
    Planner unchecked =
        planner(newUncheckedPlanner(dispatcher, registry, registry, attributes, defaultContainer));

    assertEstablished(checkedWrapper);
    assertEstablished(checkedMaps);
    assertEstablished(unchecked);
    assertThat(checkedWrapper.plan(expression).eval(emptyActivation()).intValue()).isEqualTo(42L);
  }

  @Test
  void extractedImplementationsArePackagePrivate() {
    assertThat(isPublic(ExprInterpreter.class.getModifiers())).isFalse();
    assertThat(isPublic(Planner.class.getModifiers())).isFalse();
    assertThat(isPublic(PlanningPolicy.class.getModifiers())).isFalse();
  }

  private static ExprInterpreter interpreter(Interpreter interpreter) {
    assertThat(interpreter).isInstanceOf(ExprInterpreter.class);
    return (ExprInterpreter) interpreter;
  }

  private static Planner planner(InterpretablePlanner planner) {
    assertThat(planner).isInstanceOf(Planner.class);
    return (Planner) planner;
  }

  private static void assertEstablished(Planner planner) {
    assertThat(planner.policy()).isSameAs(PlanningPolicy.ESTABLISHED_ONLY);
  }

  private static void assertPermitted(Planner planner) {
    assertThat(planner.policy()).isSameAs(PlanningPolicy.NATIVE_SPECIALIZATION_PERMITTED);
  }

  private static void assertOptimized(Planner planner) {
    assertThat(planner.policy()).isSameAs(PlanningPolicy.NATIVE_OPTIMIZED);
  }
}
