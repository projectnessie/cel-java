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
 * One-shot controlled compilation of a CEL script.
 *
 * <p>{@link #compile()} executes synchronously on its calling thread. Another thread may call
 * {@link #cancel()}; cancellation is cooperative and throws {@link
 * org.projectnessie.cel.OperationAbortedException} from {@code compile()} when observed.
 * Cancellation does not interrupt the execution thread. The handle is not reusable.
 */
public interface CancelableScriptCompilation {
  /**
   * Compiles once on the calling thread.
   *
   * @return a reusable compiled script
   * @throws ScriptCreateException if parsing or type checking reports diagnostics
   * @throws org.projectnessie.cel.OperationAbortedException if a control outcome is observed
   * @throws IllegalStateException if the handle has already been claimed
   */
  Script compile() throws ScriptCreateException;

  /**
   * Requests cooperative cancellation.
   *
   * <p>This method is thread-safe and idempotent. It does not interrupt the execution thread.
   */
  void cancel();
}
