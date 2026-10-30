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
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.Overloads.AddDouble;
import static org.projectnessie.cel.common.types.Overloads.AddInt64;
import static org.projectnessie.cel.common.types.Overloads.AddList;
import static org.projectnessie.cel.common.types.Overloads.AddString;
import static org.projectnessie.cel.common.types.Overloads.InList;
import static org.projectnessie.cel.common.types.Overloads.InMap;
import static org.projectnessie.cel.common.types.Overloads.IndexList;
import static org.projectnessie.cel.common.types.Overloads.IndexMap;
import static org.projectnessie.cel.common.types.Overloads.SizeList;
import static org.projectnessie.cel.common.types.Overloads.SizeListInst;
import static org.projectnessie.cel.common.types.Overloads.SizeMap;
import static org.projectnessie.cel.common.types.Overloads.SizeMapInst;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newEmptyRegistry;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Reference;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.interpreter.Planner.ResolvedFunction;
import org.projectnessie.cel.interpreter.functions.Overload;

class StandardNativeOverloadCatalogTest {
  private static final long CALL_ID = 100L;

  @Test
  void exactImplementationCanOwnMultipleCheckedDescriptors() {
    Overload add = standardImplementation(Operator.Add.id);

    assertThat(StandardNativeOverloadCatalog.descriptors(add))
        .extracting(NativeOverloadDescriptor::overloadId)
        .containsExactly(AddInt64, AddDouble, AddString, AddList);
    assertThat(StandardNativeOverloadCatalog.find(add, Operator.Add.id, AddInt64)).isNotNull();
    assertThat(StandardNativeOverloadCatalog.find(add, Operator.Add.id, AddDouble)).isNotNull();
    assertThat(StandardNativeOverloadCatalog.find(add, "wrong", AddInt64)).isNull();
    assertThat(StandardNativeOverloadCatalog.find(add, Operator.Add.id, "wrong")).isNull();
  }

  @Test
  void aggregateDescriptorsRetainExactFunctionAndImplementationProvenance() {
    Overload index = standardImplementation(Operator.Index.id);
    Overload in = standardImplementation(Operator.In.id);
    Overload size = standardImplementation(org.projectnessie.cel.common.types.Overloads.Size);

    assertThat(StandardNativeOverloadCatalog.descriptors(index))
        .extracting(NativeOverloadDescriptor::overloadId)
        .containsExactly(IndexList, IndexMap);
    assertThat(StandardNativeOverloadCatalog.descriptors(in))
        .extracting(NativeOverloadDescriptor::overloadId)
        .containsExactly(InList, InMap);
    assertThat(StandardNativeOverloadCatalog.descriptors(size))
        .extracting(NativeOverloadDescriptor::overloadId)
        .containsExactly(SizeList, SizeListInst, SizeMap, SizeMapInst);

    Overload replacement = Overload.binary(Operator.Index, (left, right) -> intOf(99L));
    assertThat(StandardNativeOverloadCatalog.find(replacement, Operator.Index.id, IndexList))
        .isNull();
    assertThat(StandardNativeOverloadCatalog.find(index, Operator.In.id, IndexList)).isNull();
  }

  @Test
  void sameNameReplacementIsNotStandardNativeProvenance() {
    Overload replacement = Overload.binary(Operator.Add, (left, right) -> intOf(99L));

    assertThat(StandardNativeOverloadCatalog.descriptors(replacement)).isEmpty();
    assertThat(StandardNativeOverloadCatalog.find(replacement, Operator.Add.id, AddInt64)).isNull();
  }

  @Test
  void resolvedCallCarriesExactImplementationAndCheckedDescriptorOnlyWhenPermitted() {
    Overload add = standardImplementation(Operator.Add.id);
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(Overload.standardOverloads());

    ResolvedFunction permitted = planner(dispatcher, true).resolveFunction(addExpression());
    assertThat(permitted.implementation).isSameAs(add);
    assertThat(permitted.fnName).isEqualTo(Operator.Add.id);
    assertThat(permitted.overloadId).isEqualTo(AddInt64);
    assertThat(permitted.nativeDescriptor())
        .isSameAs(StandardNativeOverloadCatalog.find(add, Operator.Add.id, AddInt64));

    Planner established = planner(dispatcher, false);
    ResolvedFunction disabled = established.resolveFunction(addExpression());
    assertThat(disabled.implementation).isSameAs(add);
    assertThat(disabled.nativeDescriptor()).isNull();
    assertThat(established.plan(addExpression()).eval(emptyActivation())).isEqualTo(intOf(3L));
  }

  @Test
  void replacementRemainsTheEstablishedImplementationButIsNotNativeEligible() {
    Overload standard = standardImplementation(Operator.Add.id);
    Overload replacement = Overload.binary(AddInt64, (left, right) -> intOf(99L));
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standard, replacement);
    Planner planner = planner(dispatcher, true);

    ResolvedFunction resolved = planner.resolveFunction(addExpression());
    assertThat(resolved.implementation).isSameAs(replacement);
    assertThat(resolved.nativeDescriptor()).isNull();
    assertThat(planner.plan(addExpression()).eval(emptyActivation())).isEqualTo(intOf(99L));
  }

  private static Planner planner(Dispatcher dispatcher, boolean nativePlanningPermitted) {
    ProtoTypeRegistry registry = newEmptyRegistry();
    return new Planner(
        dispatcher,
        registry,
        registry,
        newAttributeFactory(defaultContainer, registry, registry),
        defaultContainer,
        Map.of(CALL_ID, Reference.newBuilder().addOverloadId(AddInt64).build()),
        Map.of(),
        PlanningPolicy.nativeSpecialization(nativePlanningPermitted),
        org.projectnessie.cel.RegexEngine.JAVA,
        new InterpretableDecorator[0]);
  }

  private static Expr addExpression() {
    return Expr.newBuilder()
        .setId(CALL_ID)
        .setCallExpr(
            Expr.Call.newBuilder()
                .setFunction(Operator.Add.id)
                .addArgs(
                    Expr.newBuilder()
                        .setId(CALL_ID + 1)
                        .setConstExpr(Constant.newBuilder().setInt64Value(1L)))
                .addArgs(
                    Expr.newBuilder()
                        .setId(CALL_ID + 2)
                        .setConstExpr(Constant.newBuilder().setInt64Value(2L))))
        .build();
  }

  private static Overload standardImplementation(String function) {
    return Arrays.stream(Overload.standardOverloads())
        .filter(overload -> overload.operator.equals(function))
        .findFirst()
        .orElseThrow();
  }
}
