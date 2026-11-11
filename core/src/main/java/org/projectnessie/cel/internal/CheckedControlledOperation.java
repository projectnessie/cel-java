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
package org.projectnessie.cel.internal;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.projectnessie.cel.OperationAbortedException.Phase;

/**
 * Internal one-shot controlled operation that preserves a checked exception type.
 *
 * <p>This type is public only for communication between CEL-Java modules. It is not a supported
 * application API.
 */
public final class CheckedControlledOperation<T, E extends Exception> {
  private final AtomicBoolean claimed = new AtomicBoolean();
  private final OperationController controller;
  private final Phase initialPhase;
  private final CheckedOperationAction<T, E> action;

  /** Creates a one-shot checked operation. */
  public CheckedControlledOperation(
      OperationController controller, Phase initialPhase, CheckedOperationAction<T, E> action) {
    this.controller = Objects.requireNonNull(controller, "controller");
    this.initialPhase = Objects.requireNonNull(initialPhase, "initialPhase");
    this.action = Objects.requireNonNull(action, "action");
  }

  /** Executes synchronously on the calling thread. */
  public T execute() throws E {
    if (!claimed.compareAndSet(false, true)) {
      throw new IllegalStateException("controlled CEL operation can only be executed once");
    }
    controller.begin(initialPhase);
    try (var ignored = OperationScope.install(controller)) {
      var result = action.execute();
      controller.checkpointNow();
      return result;
    }
  }

  /** Requests cooperative cancellation. */
  public void cancel() {
    controller.cancel();
  }
}
