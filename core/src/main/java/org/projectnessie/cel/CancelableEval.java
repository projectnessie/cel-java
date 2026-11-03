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
 * One-shot controlled evaluation of a reusable {@link Program}.
 *
 * <p>The handle is not reusable; create an independent handle for each evaluation. The underlying
 * program remains reusable and may have multiple concurrently executing handles.
 */
public interface CancelableEval extends CancelableOperation<Program.EvalResult> {
  /**
   * Evaluates once on the calling thread.
   *
   * @return the ordinary CEL evaluation result
   * @throws OperationAbortedException if a control outcome is observed
   * @throws IllegalStateException if this handle has already been claimed for evaluation
   */
  Program.EvalResult eval();

  @Override
  default Program.EvalResult execute() {
    return eval();
  }
}
