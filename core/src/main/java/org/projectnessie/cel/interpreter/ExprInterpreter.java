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

/** Default {@link Interpreter} implementation. */
final class ExprInterpreter implements Interpreter {
  private final Dispatcher dispatcher;
  private final Container container;
  private final TypeProvider provider;
  private final TypeAdapter adapter;
  private final AttributeFactory attrFactory;
  private final PlanningPolicy planningPolicy;

  ExprInterpreter(
      Dispatcher dispatcher,
      Container container,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      PlanningPolicy planningPolicy) {
    this.dispatcher = dispatcher;
    this.container = container;
    this.provider = provider;
    this.adapter = adapter;
    this.attrFactory = attrFactory;
    this.planningPolicy = planningPolicy;
  }

  @Override
  public Interpretable newInterpretable(CheckedExpr checked, InterpretableDecorator... decorators) {
    return newInterpretable(
        checked.getExpr(), checked.getReferenceMapMap(), checked.getTypeMapMap(), decorators);
  }

  @Override
  public Interpretable newInterpretable(
      Expr expr,
      Map<Long, Reference> refMap,
      Map<Long, Type> typeMap,
      InterpretableDecorator... decorators) {
    return checkedPlanner(refMap, typeMap, decorators).plan(expr);
  }

  @Override
  public Interpretable newUncheckedInterpretable(Expr expr, InterpretableDecorator... decorators) {
    return uncheckedPlanner(decorators).plan(expr);
  }

  Planner checkedPlanner(CheckedExpr checked, InterpretableDecorator... decorators) {
    return checkedPlanner(checked.getReferenceMapMap(), checked.getTypeMapMap(), decorators);
  }

  Planner checkedPlanner(
      Map<Long, Reference> refMap, Map<Long, Type> typeMap, InterpretableDecorator... decorators) {
    return new Planner(
        dispatcher,
        provider,
        adapter,
        attrFactory,
        container,
        refMap,
        typeMap,
        effectiveCheckedPolicy(decorators),
        decorators);
  }

  Planner uncheckedPlanner(InterpretableDecorator... decorators) {
    return new Planner(
        dispatcher,
        provider,
        adapter,
        attrFactory,
        container,
        new HashMap<>(),
        new HashMap<>(),
        PlanningPolicy.ESTABLISHED_ONLY,
        decorators);
  }

  private PlanningPolicy effectiveCheckedPolicy(InterpretableDecorator[] decorators) {
    return decorators.length == 0 ? planningPolicy : PlanningPolicy.ESTABLISHED_ONLY;
  }
}
