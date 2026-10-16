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
package org.projectnessie.cel.tools;

import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.OperationAbortedException.Phase;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.Program.EvalResult;
import org.projectnessie.cel.ResourceLimits;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.internal.CheckedControlledOperation;
import org.projectnessie.cel.internal.OperationController;
import org.projectnessie.cel.interpreter.ActivationFunction;

/**
 * A compiled CEL expression that can be evaluated repeatedly with different inputs.
 *
 * <p>Create scripts with a reusable {@link ScriptCompiler}. A script retains its compiler's type
 * adapter and executable {@link Program}; it does not retain an activation supplied to an execution
 * call.
 *
 * <p>Execution adapts ordinary CEL results to the requested Java class. Request {@link Val} to
 * receive the CEL value without native conversion. CEL error values are reported as {@link
 * ScriptExecutionException}. Unknown values can be returned only when the requested class is {@link
 * Val} or {@link Object}; otherwise they are reported as {@code ScriptExecutionException}.
 *
 * <p>A script may be evaluated concurrently when its configured custom adapters, providers,
 * functions, and libraries support the concurrent access they receive. Each input map and every
 * value reachable from it must remain stable for the duration of its evaluation.
 *
 * @see ScriptCompiler#compile(String)
 */
public final class Script {
  private final Env env;
  private final Program prg;

  Script(Env env, Program prg) {
    this.env = env;
    this.prg = prg;
  }

  /**
   * Evaluates this script using the legacy function-based activation.
   *
   * @param resultType requested Java result class, or {@link Val} to skip native conversion
   * @param arguments function that resolves a variable name
   * @param <T> requested result type
   * @return the evaluated and converted result
   * @throws NullPointerException if {@code resultType} or {@code arguments} is {@code null}
   * @throws ScriptExecutionException if CEL evaluation produces an error or produces an unknown
   *     value that cannot be returned as {@code resultType}
   * @deprecated Use {@link #executeWithActivation(Class, ActivationFunction)} so absence and a
   *     binding whose value is {@code null} can be distinguished.
   */
  @Deprecated(forRemoval = true)
  public <T> T execute(Class<T> resultType, Function<String, Object> arguments)
      throws ScriptException {
    return evaluate(resultType, arguments);
  }

  /**
   * Evaluates this script using variables from a Java map.
   *
   * <p>The map is not modified. It and all reachable values must not be mutated while evaluation is
   * in progress. Values are converted through the type adapter configured on the compiler.
   *
   * @param resultType requested Java result class, or {@link Val} to skip native conversion
   * @param arguments variable names and their Java values
   * @param <T> requested result type
   * @return the evaluated and converted result
   * @throws NullPointerException if {@code resultType} or {@code arguments} is {@code null}
   * @throws ScriptExecutionException if CEL evaluation produces an error or produces an unknown
   *     value that cannot be returned as {@code resultType}
   */
  public <T> T execute(Class<T> resultType, Map<String, Object> arguments) throws ScriptException {
    return executeWithActivation(resultType, arguments);
  }

  /**
   * Creates a cancellation-only execution handle for map-backed variables.
   *
   * @param resultType requested Java result class, or {@link Val} to skip native conversion
   * @param arguments variable names and their Java values
   * @param <T> requested result type
   * @return a lazy one-shot execution handle
   * @throws NullPointerException if an argument is {@code null}
   */
  public <T> CancelableScriptExecution<T> executeCancelable(
      Class<T> resultType, Map<String, Object> arguments) {
    return executeCancelable(resultType, arguments, ResourceLimits.unlimited());
  }

  /**
   * Creates a controlled execution handle for map-backed variables.
   *
   * <p>The map and reachable values must remain stable until execution completes.
   *
   * @param resultType requested Java result class, or {@link Val} to skip native conversion
   * @param arguments variable names and their Java values
   * @param limits immutable limits for evaluation and result conversion
   * @param <T> requested result type
   * @return a lazy one-shot execution handle
   * @throws NullPointerException if an argument is {@code null}
   */
  public <T> CancelableScriptExecution<T> executeCancelable(
      Class<T> resultType, Map<String, Object> arguments, ResourceLimits limits) {
    return newControlledExecution(resultType, arguments, limits);
  }

  /**
   * Evaluates this script using a caller-defined variable resolver.
   *
   * <p>The resolver may be called lazily and only for variables reached by the expression. It must
   * remain safe to invoke for the duration of this call.
   *
   * @param resultType requested Java result class, or {@link Val} to skip native conversion
   * @param arguments variable resolver
   * @param <T> requested result type
   * @return the evaluated and converted result
   * @throws NullPointerException if {@code resultType} or {@code arguments} is {@code null}
   * @throws ScriptExecutionException if CEL evaluation produces an error or produces an unknown
   *     value that cannot be returned as {@code resultType}
   */
  public <T> T executeWithActivation(Class<T> resultType, ActivationFunction arguments)
      throws ScriptException {
    return evaluate(resultType, arguments);
  }

  /**
   * Creates a cancellation-only execution handle for a variable resolver.
   *
   * @param resultType requested Java result class, or {@link Val} to skip native conversion
   * @param arguments variable resolver
   * @param <T> requested result type
   * @return a lazy one-shot execution handle
   * @throws NullPointerException if an argument is {@code null}
   */
  public <T> CancelableScriptExecution<T> executeWithActivationCancelable(
      Class<T> resultType, ActivationFunction arguments) {
    return executeWithActivationCancelable(resultType, arguments, ResourceLimits.unlimited());
  }

  /**
   * Creates a controlled execution handle for a variable resolver.
   *
   * @param resultType requested Java result class, or {@link Val} to skip native conversion
   * @param arguments variable resolver
   * @param limits immutable limits for evaluation and result conversion
   * @param <T> requested result type
   * @return a lazy one-shot execution handle
   * @throws NullPointerException if an argument is {@code null}
   */
  public <T> CancelableScriptExecution<T> executeWithActivationCancelable(
      Class<T> resultType, ActivationFunction arguments, ResourceLimits limits) {
    return newControlledExecution(resultType, arguments, limits);
  }

  /**
   * Evaluates this script using variables from a Java map.
   *
   * <p>This overload is equivalent to {@link #execute(Class, Map)}.
   *
   * @param resultType requested Java result class, or {@link Val} to skip native conversion
   * @param arguments variable names and their Java values
   * @param <T> requested result type
   * @return the evaluated and converted result
   * @throws NullPointerException if {@code resultType} or {@code arguments} is {@code null}
   * @throws ScriptExecutionException if CEL evaluation produces an error or produces an unknown
   *     value that cannot be returned as {@code resultType}
   */
  public <T> T executeWithActivation(Class<T> resultType, Map<String, Object> arguments)
      throws ScriptException {
    return evaluate(resultType, arguments);
  }

  @SuppressWarnings("unchecked")
  private <T> T evaluate(Class<T> resultType, Object arguments) throws ScriptExecutionException {
    Objects.requireNonNull(resultType, "resultType missing");
    Objects.requireNonNull(arguments, "arguments missing");

    return convert(resultType, prg.eval(arguments));
  }

  private <T> CancelableScriptExecution<T> newControlledExecution(
      Class<T> resultType, Object arguments, ResourceLimits limits) {
    Objects.requireNonNull(resultType, "resultType missing");
    Objects.requireNonNull(arguments, "arguments missing");
    Objects.requireNonNull(limits, "limits");
    var controller = new OperationController(limits);
    var operation =
        new CheckedControlledOperation<T, ScriptException>(
            controller,
            Phase.EVALUATE,
            () -> {
              var evalResult = prg.evalCancelable(arguments, limits).eval();
              controller.checkpointNow(Phase.RESULT_CONVERSION);
              var result = convert(resultType, evalResult);
              controller.checkpointNow(Phase.RESULT_CONVERSION);
              return result;
            });
    return new CancelableScriptExecution<>() {
      @Override
      public T execute() throws ScriptException {
        return operation.execute();
      }

      @Override
      public void cancel() {
        operation.cancel();
      }
    };
  }

  @SuppressWarnings("unchecked")
  private <T> T convert(Class<T> resultType, EvalResult evalResult)
      throws ScriptExecutionException {
    Val result = evalResult.getVal();

    if (isError(result)) {
      Err err = (Err) result;
      throw new ScriptExecutionException(err.toString(), err.getCause());
    }
    if (isUnknown(result)) {
      if (resultType == Val.class || resultType == Object.class) {
        return (T) result;
      }
      throw new ScriptExecutionException(
          String.format(
              "script returned unknown %s, but expected result type is %s",
              result, resultType.getName()));
    }
    if (resultType == Val.class) {
      return (T) result;
    }

    return env.getTypeAdapter().valueToNative(result, resultType);
  }
}
