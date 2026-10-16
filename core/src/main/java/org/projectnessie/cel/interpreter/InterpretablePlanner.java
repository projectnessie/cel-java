/*
 * Copyright (C) 2021 The Authors of CEL-Java
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

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Reference;
import com.google.api.expr.v1alpha1.Type;
import java.util.HashMap;
import java.util.Map;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;

/** interpretablePlanner creates an Interpretable evaluation plan from a proto Expr value. */
public interface InterpretablePlanner {
  /** Plan generates an Interpretable value (or error) from the input proto Expr. */
  Interpretable plan(Expr expr);

  /**
   * newPlanner creates an interpretablePlanner which references a Dispatcher, TypeProvider,
   * TypeAdapter, Container, and CheckedExpr value. These pieces of data are used to resolve
   * functions, types, and namespaced identifiers at plan time rather than at runtime since it only
   * needs to be done once and may be semi-expensive to compute.
   */
  static InterpretablePlanner newPlanner(
      Dispatcher disp,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      Container cont,
      CheckedExpr checked,
      InterpretableDecorator... decorators) {
    return new Planner(
        disp,
        provider,
        adapter,
        attrFactory,
        cont,
        checked.getReferenceMapMap(),
        checked.getTypeMapMap(),
        PlanningPolicy.ESTABLISHED_ONLY,
        decorators);
  }

  /**
   * newPlanner creates an interpretablePlanner from checked expression metadata without requiring a
   * CheckedExpr wrapper.
   */
  static InterpretablePlanner newPlanner(
      Dispatcher disp,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      Container cont,
      Map<Long, Reference> refMap,
      Map<Long, Type> typeMap,
      InterpretableDecorator... decorators) {
    return new Planner(
        disp,
        provider,
        adapter,
        attrFactory,
        cont,
        refMap,
        typeMap,
        PlanningPolicy.ESTABLISHED_ONLY,
        decorators);
  }

  /**
   * newUncheckedPlanner creates an interpretablePlanner which references a Dispatcher,
   * TypeProvider, TypeAdapter, and Container to resolve functions and types at plan time.
   * Namespaces present in Select expressions are resolved lazily at evaluation time.
   */
  static InterpretablePlanner newUncheckedPlanner(
      Dispatcher disp,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      Container cont,
      InterpretableDecorator... decorators) {
    return new Planner(
        disp,
        provider,
        adapter,
        attrFactory,
        cont,
        new HashMap<>(),
        new HashMap<>(),
        PlanningPolicy.ESTABLISHED_ONLY,
        decorators);
  }
}
