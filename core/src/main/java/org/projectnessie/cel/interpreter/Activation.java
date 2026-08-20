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

import java.util.Map;
import java.util.function.Function;

/**
 * Supplies variable bindings to a CEL evaluation.
 *
 * <p>An activation distinguishes an absent binding ({@link ActivationFunction#ABSENT}) from a
 * present CEL null binding (Java {@code null}). Values may be Java host representations or already
 * adapted {@link org.projectnessie.cel.common.types.ref.Val} instances; the program's type adapter
 * converts host values when they are consumed.
 *
 * <p>Activations and their backing objects may be retained for the lifetime of a program or
 * evaluation. Callers must not mutate retained maps or bound values while evaluation is in
 * progress. Custom implementations and resolver functions may be called concurrently when a program
 * is shared and are responsible for their own thread safety.
 */
public interface Activation extends ActivationFunction {
  @SuppressWarnings("removal")
  @Override
  default Object resolve(String name) {
    var resolved = resolveName(name);
    if (resolved != null) {
      return resolved.present() ? resolved.value() : ABSENT;
    }
    return ABSENT;
  }

  /**
   * Resolves a name using the legacy wrapper representation.
   *
   * <p>Replace with {@link #resolve(String)}.
   *
   * @param name qualified variable name
   * @return the legacy resolved-value wrapper
   * @deprecated use {@link #resolve(String)}
   */
  @SuppressWarnings({"DeprecatedIsStillUsed", "removal"})
  @Deprecated(forRemoval = true)
  ResolvedValue resolveName(String name);

  /**
   * Returns this activation's parent, if its implementation exposes one.
   *
   * @return the parent activation, or {@code null}
   */
  default Activation parent() {
    return null;
  }

  /**
   * Returns an activation with no variable bindings.
   *
   * @return a reusable empty activation
   */
  static Activation emptyActivation() {
    // This call cannot fail.
    return newActivation(Map.of());
  }

  /**
   * Creates an activation from a supported binding source.
   *
   * <p>The input {@code bindings} may be an {@link Activation}, a {@link Map}, a {@link Function},
   * or an {@link ActivationFunction}. An activation is returned unchanged. Map keys are qualified
   * CEL variable names. An {@code ActivationFunction} uses {@link ActivationFunction#ABSENT} for
   * absence and Java {@code null} for a present null. The legacy {@code Function} form treats both
   * a Java {@code null} result and a legacy absent wrapper as absence.
   *
   * <p>The activation retains a map input without copying it, but does not modify it. The caller
   * must not mutate the map while the activation may be resolved.
   *
   * <p>A map value may be a no-argument {@link java.util.function.Supplier} for lazy resolution.
   * The activation invokes the supplier when the binding is first resolved and memoizes a
   * successful result, including {@code null}, in activation-owned state. Concurrent resolutions
   * share that result. If the supplier throws, the exception is propagated without memoizing a
   * result, so a later resolution retries it. A supplier returned by a supplier is the resolved
   * value and is not invoked by the activation.
   *
   * <p>Values which are not represented as ref.Val types on input may be adapted to a ref.Val using
   * the ref.TypeAdapter configured in the environment.
   *
   * @param bindings supported binding source
   * @return an activation backed by {@code bindings}
   * @throws NullPointerException if {@code bindings} is {@code null}
   * @throws IllegalArgumentException if {@code bindings} has an unsupported type
   */
  @SuppressWarnings({"rawtypes", "unchecked", "removal"})
  static Activation newActivation(Object bindings) {
    if (bindings == null) {
      throw new NullPointerException("bindings must be non-nil");
    }
    if (bindings instanceof Activation activation) {
      return activation;
    }
    if (bindings instanceof Map map) {
      return new MapActivation(map);
    }
    if (bindings instanceof Function func) {
      bindings = (ActivationFunction) name -> ResolvedValue.mapLegacy(func.apply(name));
    }
    if (bindings instanceof ActivationFunction activationFunction) {
      return new FunctionActivation(activationFunction);
    }
    throw new IllegalArgumentException(
        String.format(
            "activation input must be an activation or map[string]interface: got %s",
            bindings.getClass().getName()));
  }

  /**
   * Creates an activation that resolves against {@code child} before {@code parent}.
   *
   * <p>The returned activation retains both inputs. A name beginning with {@code .} bypasses the
   * child and is resolved directly against the parent after the leading dot is removed.
   *
   * @param parent non-null fallback activation
   * @param child non-null preferred activation
   * @return a hierarchical activation
   */
  static Activation newHierarchicalActivation(Activation parent, Activation child) {
    return new HierarchicalActivation(parent, child);
  }

  /**
   * Creates an activation that marks matching attribute paths as unknown.
   *
   * <p>{@code bindings} accepts every source supported by {@link #newActivation(Object)}. The
   * pattern array is copied, but each mutable {@link AttributePattern} is retained. Complete
   * pattern construction before creating or sharing the activation. Callers receive a cloned array
   * from {@link PartialActivation#unknownAttributePatterns()}.
   *
   * @param bindings supported binding source
   * @param unknowns non-null attribute patterns that should produce CEL unknown values
   * @return a partial activation
   * @throws NullPointerException if {@code bindings} or {@code unknowns} is {@code null}
   */
  static PartialActivation newPartialActivation(Object bindings, AttributePattern... unknowns) {
    Activation a = newActivation(bindings);
    return new PartActivation(a, unknowns);
  }

  /** Activation that supplies attribute patterns for partial evaluation. */
  interface PartialActivation extends Activation {

    /**
     * Returns the attribute patterns whose matching accesses are unknown.
     *
     * <p>The returned array is a new shallow copy. Its mutable pattern elements remain shared with
     * this activation.
     *
     * @return a copy of the unknown-pattern array
     */
    AttributePattern[] unknownAttributePatterns();
  }
}
