/*
 * Copyright (C) 2026 The Authors of CEL-Java
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

/**
 * One-shot controlled execution and result conversion of a compiled script.
 *
 * <p>{@link #execute()} runs synchronously on its calling thread. Another thread may call {@link
 * #cancel()}; cancellation is cooperative and throws {@link
 * org.projectnessie.cel.OperationAbortedException} from {@code execute()} when observed.
 * Cancellation does not interrupt the execution thread. The handle is not reusable; the compiled
 * {@link Script} remains reusable.
 *
 * @param <T> requested native result type
 */
public interface CancelableScriptExecution<T> {
  /**
   * Evaluates and converts the result once on the calling thread.
   *
   * @return converted evaluation result
   * @throws ScriptException if CEL produces an error or cannot be converted as requested
   * @throws org.projectnessie.cel.OperationAbortedException if a control outcome is observed
   * @throws IllegalStateException if the handle has already been claimed
   */
  T execute() throws ScriptException;

  /**
   * Requests cooperative cancellation.
   *
   * <p>This method is thread-safe and idempotent. It does not interrupt the execution thread.
   */
  void cancel();
}
