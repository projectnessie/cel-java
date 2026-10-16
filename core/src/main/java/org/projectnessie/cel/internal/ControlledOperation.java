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
package org.projectnessie.cel.internal;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.projectnessie.cel.CancelableOperation;
import org.projectnessie.cel.OperationAbortedException.Phase;

/**
 * Internal one-shot controlled operation.
 *
 * <p>This type is public only for communication between CEL-Java modules. It is not a supported
 * application API.
 */
public final class ControlledOperation<T> implements CancelableOperation<T> {
  private enum State {
    NEW,
    RUNNING,
    COMPLETED,
    ABORTED
  }

  private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
  private final OperationController controller;
  private final Phase initialPhase;
  private final Supplier<T> action;

  /** Creates a one-shot operation. */
  public ControlledOperation(
      OperationController controller, Phase initialPhase, Supplier<T> action) {
    this.controller = Objects.requireNonNull(controller, "controller");
    this.initialPhase = Objects.requireNonNull(initialPhase, "initialPhase");
    this.action = Objects.requireNonNull(action, "action");
  }

  @Override
  public T execute() {
    if (!state.compareAndSet(State.NEW, State.RUNNING)) {
      throw new IllegalStateException("controlled CEL operation can only be executed once");
    }
    var completed = false;
    try {
      if (OperationScope.current() == controller) {
        var result = action.get();
        controller.checkpointNow();
        completed = true;
        return result;
      } else {
        controller.begin(initialPhase);
        try (var ignored = OperationScope.install(controller)) {
          var result = action.get();
          controller.checkpointNow();
          completed = true;
          return result;
        }
      }
    } finally {
      state.set(completed ? State.COMPLETED : State.ABORTED);
    }
  }

  @Override
  public void cancel() {
    controller.cancel();
  }

  /** Returns this operation's controller for evaluation activation propagation. */
  public OperationController controller() {
    return controller;
  }
}
