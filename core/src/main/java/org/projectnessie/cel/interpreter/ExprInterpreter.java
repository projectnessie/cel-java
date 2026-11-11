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

import static org.projectnessie.cel.interpreter.InterpretablePlanner.newPlanner;
import static org.projectnessie.cel.interpreter.InterpretablePlanner.newUncheckedPlanner;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Reference;
import com.google.api.expr.v1alpha1.Type;
import java.util.Map;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;

final class ExprInterpreter implements Interpreter {
  private final Dispatcher dispatcher;
  private final Container container;
  private final TypeProvider provider;
  private final TypeAdapter adapter;
  private final AttributeFactory attrFactory;

  ExprInterpreter(
      Dispatcher dispatcher,
      Container container,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory) {
    this.dispatcher = dispatcher;
    this.container = container;
    this.provider = provider;
    this.adapter = adapter;
    this.attrFactory = attrFactory;
  }

  @Override
  public Interpretable newInterpretable(CheckedExpr checked, InterpretableDecorator... decorators) {
    InterpretablePlanner p =
        newPlanner(dispatcher, provider, adapter, attrFactory, container, checked, decorators);
    return p.plan(checked.getExpr());
  }

  @Override
  public Interpretable newInterpretable(
      Expr expr,
      Map<Long, Reference> refMap,
      Map<Long, Type> typeMap,
      InterpretableDecorator... decorators) {
    InterpretablePlanner p =
        newPlanner(
            dispatcher, provider, adapter, attrFactory, container, refMap, typeMap, decorators);
    return p.plan(expr);
  }

  @Override
  public Interpretable newUncheckedInterpretable(Expr expr, InterpretableDecorator... decorators) {
    InterpretablePlanner p =
        newUncheckedPlanner(dispatcher, provider, adapter, attrFactory, container, decorators);
    return p.plan(expr);
  }
}
