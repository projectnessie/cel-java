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
 * One-shot, cooperatively cancellable CEL operation.
 *
 * <p>{@link #execute()} performs work synchronously on its calling thread. Another thread may call
 * {@link #cancel()}; cancellation is observed at the next CEL-Java checkpoint. Repeated or
 * concurrent calls to {@code execute()} throw {@link IllegalStateException}.
 *
 * <p>Cancellation before execution is observed at operation entry. Cancellation racing successful
 * completion may either abort the operation or leave its completed result unchanged. Calling {@code
 * cancel()} after completion has no effect on that result. Cancellation does not interrupt the
 * execution thread.
 *
 * @param <T> operation result type
 */
public interface CancelableOperation<T> {
  /**
   * Executes this operation once on the calling thread.
   *
   * @return the completed operation result
   * @throws OperationAbortedException if cancellation, interruption, or a configured limit is
   *     observed
   * @throws IllegalStateException if this handle has already been claimed for execution
   */
  T execute();

  /**
   * Requests cooperative cancellation.
   *
   * <p>This method is thread-safe and idempotent. It does not interrupt the execution thread.
   */
  void cancel();
}
