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

import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.InterpretableDecorator.decDisableShortcircuits;
import static org.projectnessie.cel.interpreter.InterpretableDecorator.decObserveEval;
import static org.projectnessie.cel.interpreter.InterpretableDecorator.decOptimize;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Reference;
import com.google.api.expr.v1alpha1.Type;
import java.util.Map;
import org.projectnessie.cel.RegexEngine;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;
import org.projectnessie.cel.interpreter.functions.Overload;

/**
 * Low-level factory for reusable {@link Interpretable} expression plans.
 *
 * <p>Checked planning consumes checker reference and type metadata. Unchecked planning performs
 * runtime dispatch and should be reserved for intentionally unchecked expressions. An interpreter
 * retains its dispatcher, namespace container, adapter, provider, attribute factory, planning
 * policy, and regex engine; configure those dependencies before sharing it.
 *
 * <p>Native planning, when permitted, selects planner specializations over supported Java-native
 * representations. It is not code generation, bytecode generation, JNI, or machine-code
 * compilation, and unsupported expression shapes use the established evaluator.
 */
public interface Interpreter {
  /** Creates an {@link Interpretable} from a checked expression and optional decorators. */
  Interpretable newInterpretable(CheckedExpr checked, InterpretableDecorator... decorators);

  /**
   * Creates an {@link Interpretable} directly from an expression and its checked metadata without
   * constructing a {@link CheckedExpr} wrapper.
   */
  default Interpretable newInterpretable(
      Expr expr,
      Map<Long, Reference> refMap,
      Map<Long, Type> typeMap,
      InterpretableDecorator... decorators) {
    CheckedExpr checked =
        CheckedExpr.newBuilder()
            .setExpr(expr)
            .putAllReferenceMap(refMap)
            .putAllTypeMap(typeMap)
            .build();
    return newInterpretable(checked, decorators);
  }

  /** Creates an {@link Interpretable} from a parsed expression and optional decorators. */
  Interpretable newUncheckedInterpretable(Expr expr, InterpretableDecorator... decorators);

  /**
   * Returns a decorator that records expression values in {@code state}.
   *
   * <p>The decorator and state are evaluation-specific and not thread-safe. Do not share them
   * between concurrent evaluations.
   */
  static InterpretableDecorator trackState(EvalState state) {
    return decObserveEval(state::setValue);
  }

  /**
   * Returns a decorator that disables short-circuiting and records expression values in {@code
   * state}.
   *
   * <p>The decorator and state are evaluation-specific and not thread-safe. Evaluating normally
   * skipped branches may expose their function side effects or errors.
   */
  static InterpretableDecorator exhaustiveEval(EvalState state) {
    InterpretableDecorator ex = decDisableShortcircuits();
    InterpretableDecorator obs = trackState(state);
    return i -> {
      Interpretable iDec = ex.decorate(i);
      return obs.decorate(iDec);
    };
  }

  /**
   * Returns the established-plan decorator for constant folding and constant-data optimization.
   *
   * <p>This decorator is independent of native planning and does not guarantee that a particular
   * expression is rewritten.
   */
  static InterpretableDecorator optimize() {
    return decOptimize();
  }

  /** Builds an established-only interpreter using the Java regular-expression engine. */
  static Interpreter newInterpreter(
      Dispatcher dispatcher,
      Container container,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory) {
    return newInterpreter(dispatcher, container, provider, adapter, attrFactory, RegexEngine.JAVA);
  }

  /**
   * Builds an Interpreter that uses {@code regexEngine} for the built-in CEL {@code matches}
   * function.
   *
   * <p>This factory retains established-only planning. Use the overload with {@code
   * allowNativePlanning} to permit native specializations for eligible checked expressions.
   *
   * @throws NullPointerException if {@code regexEngine} is {@code null}
   */
  static Interpreter newInterpreter(
      Dispatcher dispatcher,
      Container container,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      RegexEngine regexEngine) {
    return new ExprInterpreter(
        dispatcher,
        container,
        provider,
        adapter,
        attrFactory,
        PlanningPolicy.ESTABLISHED_ONLY,
        regexEngine);
  }

  /**
   * Builds an interpreter with planning-time permission to use native specializations for eligible
   * checked expressions.
   *
   * <p>Native planning remains disabled for unchecked expressions and whenever decorators are
   * supplied directly to {@link #newInterpretable}.
   */
  static Interpreter newInterpreter(
      Dispatcher dispatcher,
      Container container,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      boolean allowNativePlanning) {
    return newInterpreter(
        dispatcher,
        container,
        provider,
        adapter,
        attrFactory,
        allowNativePlanning,
        RegexEngine.JAVA);
  }

  /**
   * Builds an Interpreter with planning-time permission to use native specializations and a
   * regular-expression engine for the standard CEL {@code matches} function.
   *
   * <p>The engine is fixed for every {@link Interpretable} generated by the returned interpreter.
   * Native planning remains disabled for unchecked expressions and whenever decorators are supplied
   * directly to {@link #newInterpretable}.
   *
   * @throws NullPointerException if {@code regexEngine} is {@code null}
   */
  static Interpreter newInterpreter(
      Dispatcher dispatcher,
      Container container,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      boolean allowNativePlanning,
      RegexEngine regexEngine) {
    return new ExprInterpreter(
        dispatcher,
        container,
        provider,
        adapter,
        attrFactory,
        PlanningPolicy.nativeSpecialization(allowNativePlanning),
        regexEngine);
  }

  /** Builds an established-only interpreter with all standard CEL runtime overloads. */
  static Interpreter newStandardInterpreter(
      Container container, TypeProvider provider, TypeAdapter adapter, AttributeFactory resolver) {
    return newStandardInterpreter(container, provider, adapter, resolver, RegexEngine.JAVA);
  }

  /**
   * Builds a standard Interpreter using {@code regexEngine} for the built-in CEL {@code matches}
   * function.
   *
   * @throws NullPointerException if {@code regexEngine} is {@code null}
   */
  static Interpreter newStandardInterpreter(
      Container container,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory resolver,
      RegexEngine regexEngine) {
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(Overload.standardOverloads());
    return newInterpreter(dispatcher, container, provider, adapter, resolver, false, regexEngine);
  }
}
