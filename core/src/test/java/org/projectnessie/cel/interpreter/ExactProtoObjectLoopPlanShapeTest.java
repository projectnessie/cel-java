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
import static org.projectnessie.cel.Env.newCustomEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.TypeRegistry;

class ExactProtoObjectLoopPlanShapeTest {
  private static final String TYPE = TestAllTypes.getDescriptor().getFullName();
  private static final String EXPRESSION =
      "messages.all(a1, messages.exists_one(a2, "
          + "a2.single_string == a1.single_string && has(a2.single_string)))";

  @Test
  void plansNestedExactObjectQuantifiersAndRawProtobufFields() {
    TypeRegistry exact =
        ProtoTypeRegistry.newExactAggregateRegistry(TestAllTypes.getDefaultInstance());
    Interpretable enabled = plan(exact, true);

    assertThat(enabled).isExactlyInstanceOf(NativeIsland.class);
    NativeObjectAllFold outer = (NativeObjectAllFold) ((NativeIsland) enabled).root();
    assertThat(outer.predicate).isExactlyInstanceOf(NativeObjectExistsOneFold.class);
    NativeObjectExistsOneFold inner = (NativeObjectExistsOneFold) outer.predicate;
    assertThat(inner.predicate).isInstanceOf(NativeLogicalAnd.class);
    NativeLogicalAnd predicate = (NativeLogicalAnd) inner.predicate;
    assertThat(predicate.lhs).isExactlyInstanceOf(NativeScalarEq.class);
    NativeScalarEq equality = (NativeScalarEq) predicate.lhs;
    assertThat(equality.lhs).isExactlyInstanceOf(NativeStringObjectField.class);
    assertThat(equality.rhs).isExactlyInstanceOf(NativeStringObjectField.class);
    assertThat(predicate.rhs).isExactlyInstanceOf(NativeObjectFieldPresence.class);

    assertThat(plan(exact, false)).isExactlyInstanceOf(EvalFold.class);
    assertThat(plan(ProtoTypeRegistry.newRegistry(TestAllTypes.getDefaultInstance()), true))
        .isExactlyInstanceOf(EvalFold.class);
  }

  private static Interpretable plan(TypeRegistry registry, boolean nativeEnabled) {
    Env env =
        newCustomEnv(
            registry,
            List.of(
                Library.StdLib(),
                declarations(
                    Decls.newVar("messages", Decls.newListType(Decls.newObjectType(TYPE))))));
    Env.AstIssuesTuple compiled = env.compile(EXPRESSION);
    assertThat(compiled.hasIssues()).withFailMessage(compiled.getIssues()::toString).isFalse();
    var checked = astToCheckedExpr(compiled.getAst());

    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    AttributeFactory attributes = newAttributeFactory(defaultContainer, registry, registry);
    Interpreter interpreter =
        newInterpreter(dispatcher, defaultContainer, registry, registry, attributes, nativeEnabled);
    return ((ExprInterpreter) interpreter).checkedPlanner(checked).plan(checked.getExpr());
  }
}
