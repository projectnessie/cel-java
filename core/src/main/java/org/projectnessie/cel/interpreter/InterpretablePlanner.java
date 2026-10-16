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
import org.projectnessie.cel.RegexEngine;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;

/**
 * Creates executable {@link Interpretable} plans from protobuf CEL expressions.
 *
 * <p>Checked planners use reference and type metadata to resolve calls, types, and names during
 * planning. Unchecked planners defer more resolution to evaluation. Most applications should obtain
 * programs through {@link org.projectnessie.cel.Env}.
 */
public interface InterpretablePlanner {
  /** Plans an expression or throws when the expression cannot be planned. */
  Interpretable plan(Expr expr);

  /**
   * Creates a checked planner using the Java regex engine.
   *
   * <p>The dispatcher, provider, adapter, attribute factory, container, and checked metadata
   * resolve functions, types, and namespaced identifiers at plan time rather than at runtime since
   * it only needs to be done once.
   */
  static InterpretablePlanner newPlanner(
      Dispatcher disp,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      Container cont,
      CheckedExpr checked,
      InterpretableDecorator... decorators) {
    return newPlanner(
        disp, provider, adapter, attrFactory, cont, checked, RegexEngine.JAVA, decorators);
  }

  /**
   * Creates a checked planner that uses {@code regexEngine} for the built-in CEL {@code matches}
   * function.
   *
   * @throws NullPointerException if {@code regexEngine} is {@code null}
   */
  static InterpretablePlanner newPlanner(
      Dispatcher disp,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      Container cont,
      CheckedExpr checked,
      RegexEngine regexEngine,
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
        regexEngine,
        decorators);
  }

  /** Creates a checked planner from reference and type maps using the Java regex engine. */
  static InterpretablePlanner newPlanner(
      Dispatcher disp,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      Container cont,
      Map<Long, Reference> refMap,
      Map<Long, Type> typeMap,
      InterpretableDecorator... decorators) {
    return newPlanner(
        disp, provider, adapter, attrFactory, cont, refMap, typeMap, RegexEngine.JAVA, decorators);
  }

  /**
   * Creates a checked planner from metadata that uses {@code regexEngine} for the built-in CEL
   * {@code matches} function.
   *
   * @throws NullPointerException if {@code regexEngine} is {@code null}
   */
  static InterpretablePlanner newPlanner(
      Dispatcher disp,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      Container cont,
      Map<Long, Reference> refMap,
      Map<Long, Type> typeMap,
      RegexEngine regexEngine,
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
        regexEngine,
        decorators);
  }

  /**
   * Creates an unchecked planner using the Java regex engine.
   *
   * <p>The planner uses the dispatcher, provider, adapter, attribute factory, and container where
   * possible. Namespaces in select expressions are resolved lazily during evaluation.
   */
  static InterpretablePlanner newUncheckedPlanner(
      Dispatcher disp,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      Container cont,
      InterpretableDecorator... decorators) {
    return newUncheckedPlanner(
        disp, provider, adapter, attrFactory, cont, RegexEngine.JAVA, decorators);
  }

  /**
   * Creates an unchecked planner that uses {@code regexEngine} for the built-in CEL {@code matches}
   * function.
   *
   * @throws NullPointerException if {@code regexEngine} is {@code null}
   */
  static InterpretablePlanner newUncheckedPlanner(
      Dispatcher disp,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      Container cont,
      RegexEngine regexEngine,
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
        regexEngine,
        decorators);
  }
}
