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
package org.projectnessie.cel;

import org.projectnessie.cel.common.types.ref.Val;

/**
 * A reusable executable CEL program created from an {@link Ast}.
 *
 * <p>Programs created by {@link Env} are fully configured before they are returned and may be
 * evaluated concurrently, subject to the thread-safety of configured custom adapters, providers,
 * functions, decorators, global activations, and caller inputs. Reuse a program rather than parsing
 * and planning the same expression for every input.
 *
 * <p>{@link #eval(Object)} is an unrestricted fast path. Use {@link #evalCancelable(Object,
 * ResourceLimits)} for cooperative elapsed-time, executing-thread CPU/allocation, and cancellation
 * controls. Neither path imposes a result-size or retained-heap limit, so hosts accepting untrusted
 * expressions or inputs remain responsible for suitable admission policy and isolation.
 */
public interface Program {

  /**
   * Creates an evaluation result.
   *
   * @param val CEL result value
   * @param evalDetails evaluation details; retained as supplied, including {@code null}, for
   *     compatibility with direct use
   * @return the result
   */
  static EvalResult newEvalResult(Val val, EvalDetails evalDetails) {
    return new EvalResult(val, evalDetails);
  }

  /**
   * Evaluates this program against the supplied variables.
   *
   * <p>{@code vars} may be an {@link org.projectnessie.cel.interpreter.Activation} or a Java map
   * from variable names to values accepted by the configured type adapter. The lower-level
   * activation factory also accepts {@link java.util.function.Function} and {@link
   * org.projectnessie.cel.interpreter.ActivationFunction}.
   *
   * <p>The caller retains ownership of {@code vars} and every value reachable from it. Some input
   * adapters retain live views of mutable Java values, so mutations completed before a later call
   * to {@code eval} may be visible to that evaluation. Do not mutate the input object or any
   * reachable value while this method is running. Concurrent evaluations may share input only when
   * it remains effectively immutable for the duration of all evaluations or the caller otherwise
   * provides safe independent ownership.
   *
   * <p>The returned result always contains non-null {@link EvalDetails} and a non-null, mutable
   * {@link org.projectnessie.cel.interpreter.EvalState}. Each evaluation owns a distinct state, so
   * mutating one result's state does not affect this program or another result. The state is empty
   * for ordinary evaluation. {@link EvalOption#OptTrackState} records intermediate values, while
   * {@link EvalOption#OptExhaustiveEval} additionally disables short-circuit evaluation.
   *
   * <p>A CEL evaluation error is returned as an error {@link Val}. An unexpected internal Java
   * failure is thrown as a {@link RuntimeException}.
   *
   * @param vars input accepted by {@link
   *     org.projectnessie.cel.interpreter.Activation#newActivation(Object)}
   * @return the CEL value and per-evaluation details
   * @throws RuntimeException if the activation is invalid or an unexpected Java failure occurs
   */
  EvalResult eval(Object vars);

  /**
   * Creates a cancellation-only one-shot evaluation handle.
   *
   * <p>The returned handle performs evaluation synchronously on the thread calling {@link
   * CancelableEval#eval()}. Handle creation and prior executor queueing do not count as execution.
   *
   * @param vars evaluation input
   * @return one-shot controlled evaluation
   * @throws UnsupportedOperationException if a third-party program implementation does not support
   *     controlled evaluation
   */
  default CancelableEval evalCancelable(Object vars) {
    return evalCancelable(vars, ResourceLimits.unlimited());
  }

  /**
   * Creates a one-shot evaluation handle with cooperative cancellation and resource limits.
   *
   * <p>Cancellation and limit exhaustion throw {@link OperationAbortedException}; they are not CEL
   * error values. Limits are measured from the start of {@link CancelableEval#eval()} and are
   * checked cooperatively.
   *
   * @param vars evaluation input
   * @param limits immutable limits for this invocation
   * @return one-shot controlled evaluation
   * @throws NullPointerException if {@code limits} is {@code null}
   * @throws UnsupportedOperationException if a third-party program implementation does not support
   *     controlled evaluation
   */
  default CancelableEval evalCancelable(Object vars, ResourceLimits limits) {
    return ProgramControls.newEvaluation(this, vars, limits);
  }

  /**
   * Value and details associated with an evaluation result.
   *
   * <p>{@link Program#eval(Object)} returns non-null details and state. The public {@link
   * Program#newEvalResult(Val, EvalDetails)} factory retains directly supplied values, including
   * {@code null}, for compatibility. Instances are immutable, but the referenced {@link
   * EvalDetails} and its state may be mutable.
   */
  final class EvalResult {
    private final Val val;
    private final EvalDetails evalDetails;

    private EvalResult(Val val, EvalDetails evalDetails) {
      this.val = val;
      this.evalDetails = evalDetails;
    }

    /**
     * Returns the CEL result value.
     *
     * @return result value, or {@code null} if supplied to {@link Program#newEvalResult(Val,
     *     EvalDetails)}
     */
    public Val getVal() {
      return val;
    }

    /**
     * Returns the details owned by this result.
     *
     * @return evaluation details, or {@code null} if supplied to {@link Program#newEvalResult(Val,
     *     EvalDetails)}
     */
    public EvalDetails getEvalDetails() {
      return evalDetails;
    }
  }
}
