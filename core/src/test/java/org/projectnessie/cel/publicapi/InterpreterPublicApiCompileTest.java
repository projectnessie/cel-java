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
package org.projectnessie.cel.publicapi;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.IntT.intOf;

import com.google.api.expr.v1alpha1.CheckedExpr;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.interpreter.Activation;
import org.projectnessie.cel.interpreter.Activation.PartialActivation;
import org.projectnessie.cel.interpreter.AttributeFactory;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.AttributeFactory.NamespacedAttribute;
import org.projectnessie.cel.interpreter.AttributeFactory.Qualifier;
import org.projectnessie.cel.interpreter.AttributePattern;
import org.projectnessie.cel.interpreter.Coster;
import org.projectnessie.cel.interpreter.Coster.Cost;
import org.projectnessie.cel.interpreter.Dispatcher;
import org.projectnessie.cel.interpreter.Interpretable;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableConst;
import org.projectnessie.cel.interpreter.InterpretableDecorator;
import org.projectnessie.cel.interpreter.InterpretablePlanner;
import org.projectnessie.cel.interpreter.Interpreter;

/**
 * Compile-time fixture for the intentional public interpreter contracts.
 *
 * <p>This test deliberately lives outside {@code org.projectnessie.cel.interpreter} and must not
 * name any concrete interpreter implementation.
 */
class InterpreterPublicApiCompileTest {

  @Test
  void retainedContractsAndFactoriesRemainUsableOutsideInterpreterPackage() {
    ProtoTypeRegistry registry = ProtoTypeRegistry.newEmptyRegistry();
    Container container = Container.defaultContainer;
    Dispatcher dispatcher = Dispatcher.newDispatcher();
    Dispatcher extended = Dispatcher.extendDispatcher(dispatcher);
    AttributeFactory attributes =
        AttributeFactory.newAttributeFactory(container, registry, registry);

    Activation empty = Activation.emptyActivation();
    Activation mapped = Activation.newActivation(Map.of("x", 1L));
    Activation hierarchical = Activation.newHierarchicalActivation(mapped, empty);
    PartialActivation partial =
        Activation.newPartialActivation(
            hierarchical, AttributePattern.newAttributePattern("missing"));

    InterpretableConst constant = Interpretable.newConstValue(1L, intOf(1L));
    NamespacedAttribute namespaced = attributes.absoluteAttribute(2L, "x");
    Attribute relative = attributes.relativeAttribute(3L, constant);
    Qualifier qualifier = AttributeFactory.newQualifierStatic(registry, 4L, "field");

    Interpreter standard =
        Interpreter.newStandardInterpreter(container, registry, registry, attributes);
    Interpreter configured =
        Interpreter.newInterpreter(extended, container, registry, registry, attributes);
    InterpretableDecorator optimizer = Interpreter.optimize();
    InterpretablePlanner checked =
        InterpretablePlanner.newPlanner(
            extended, registry, registry, attributes, container, CheckedExpr.getDefaultInstance());
    InterpretablePlanner checkedMaps =
        InterpretablePlanner.newPlanner(
            extended, registry, registry, attributes, container, emptyMap(), emptyMap());
    InterpretablePlanner unchecked =
        InterpretablePlanner.newUncheckedPlanner(
            extended, registry, registry, attributes, container);
    assertThat(partial.unknownAttributePatterns()).hasSize(1);
    assertThat(namespaced.candidateVariableNames()).containsExactly("x");
    assertThat(relative.addQualifier(qualifier)).isSameAs(relative);
    assertThat(constant.value()).isEqualTo(intOf(1L));
    assertThat(standard).isNotNull();
    assertThat(configured).isNotNull();
    assertThat(optimizer).isNotNull();
    assertThat(checked).isNotNull();
    assertThat(checkedMaps).isNotNull();
    assertThat(unchecked).isNotNull();
    assertThat(Coster.costOf(1L, 2L).min).isEqualTo(1L);
    assertThat(Coster.costOf(1L, 2L).max).isEqualTo(2L);
    assertThat(Cost.estimateCost(constant)).isEqualTo(Cost.None);
  }
}
