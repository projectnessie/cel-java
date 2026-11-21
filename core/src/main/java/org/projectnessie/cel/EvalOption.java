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

/**
 * Built-in evaluation modes selected through {@link ProgramOption#evalOptions(EvalOption...)}.
 *
 * <p>Options are additive. Every built-in option except {@link #OptOptimize} selects the
 * established interpreter planning path; custom decorators do so as well. With no such option, the
 * planner may select native evaluation for eligible checked expression shapes and falls back to the
 * established evaluator elsewhere.
 */
public enum EvalOption {

  /**
   * Causes the runtime to record intermediate expression values in the result's mutable,
   * evaluation-owned {@link org.projectnessie.cel.interpreter.EvalState}.
   *
   * <p>State tracking adds evaluation overhead and selects established interpreter planning.
   */
  OptTrackState(1),

  /**
   * Causes the runtime to disable short-circuit evaluation and record intermediate expression
   * values in the result's mutable, evaluation-owned state.
   *
   * <p>This option includes {@link #OptTrackState} behavior and selects established interpreter
   * planning. Because normally skipped branches are evaluated, their functions and errors may also
   * be observed.
   */
  OptExhaustiveEval(2 | OptTrackState.mask),

  /**
   * Enables program-creation-time optimization of functions and operators whose arguments are
   * constant.
   *
   * <p>This is useful for programs evaluated repeatedly. It does not guarantee that a particular
   * expression is folded, and it is independent of whether eligible nodes use native evaluation.
   */
  OptOptimize(4),

  /**
   * Enables partial evaluation for variables or qualified attributes whose values are marked
   * unknown.
   *
   * <p>By itself this does not change a result. Supply a {@link
   * org.projectnessie.cel.interpreter.Activation.PartialActivation}, for example from {@link
   * CEL#partialVars(Object, org.projectnessie.cel.interpreter.AttributePattern...)}, to mark
   * unknown input. Combine with {@link #OptTrackState} when creating a residual AST through {@link
   * Env#residualAst(Ast, EvalDetails)}. Partial evaluation selects established interpreter
   * planning.
   */
  OptPartialEval(8),

  /**
   * Forces checked expressions to use established interpreter planning.
   *
   * <p>Without this option, the planner may evaluate eligible expression nodes directly over
   * supported Java-native representations and falls back when a shape is not eligible. Native
   * evaluation does not mean generated Java, bytecode, JNI, or machine code.
   */
  OptDisableNativeEval(16);

  private final int mask;

  EvalOption(int mask) {
    this.mask = mask;
  }

  /**
   * Returns the internal bit mask associated with this option.
   *
   * @return option mask
   */
  public int getMask() {
    return mask;
  }
}
