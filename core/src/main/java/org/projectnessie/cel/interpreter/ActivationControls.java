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
package org.projectnessie.cel.interpreter;

import java.util.Objects;
import org.projectnessie.cel.OperationAbortedException.Phase;
import org.projectnessie.cel.internal.OperationController;
import org.projectnessie.cel.internal.OperationScope;
import org.projectnessie.cel.interpreter.Activation.PartialActivation;

/**
 * Internal activation integration for controlled evaluation.
 *
 * <p>This type is public only for communication between CEL-Java packages. It is not a supported
 * application API.
 */
public final class ActivationControls {
  private ActivationControls() {}

  /** Wraps a root activation while preserving partial-activation behavior. */
  public static Activation controlled(Activation activation, OperationController controller) {
    Objects.requireNonNull(activation, "activation");
    Objects.requireNonNull(controller, "controller");
    if (!controller.isControlled()) {
      return activation;
    }
    if (activation instanceof PartialActivation partial) {
      return new ControlledPartialActivation(partial, controller);
    }
    return new ControlledActivation(activation, controller);
  }

  /** Returns the controller carried by an activation or active operation scope. */
  public static OperationController controller(Activation activation) {
    Activation current = activation;
    while (current != null) {
      if (current instanceof ControlledActivationCarrier carrier) {
        return carrier.controller();
      }
      current = current.parent();
    }
    return OperationScope.current();
  }

  private interface ControlledActivationCarrier {
    OperationController controller();
  }

  private static class ControlledActivation implements Activation, ControlledActivationCarrier {
    final Activation delegate;
    private final OperationController controller;

    ControlledActivation(Activation delegate, OperationController controller) {
      this.delegate = delegate;
      this.controller = controller;
    }

    @Override
    public OperationController controller() {
      return controller;
    }

    @Override
    public Activation parent() {
      return delegate.parent();
    }

    @Override
    public Object resolve(String name) {
      controller.checkpointNow(Phase.EVALUATE);
      var value = delegate.resolve(name);
      controller.checkpointNow(Phase.EVALUATE);
      return value;
    }

    @SuppressWarnings("removal")
    @Override
    public ResolvedValue resolveName(String name) {
      controller.checkpointNow(Phase.EVALUATE);
      var value = delegate.resolveName(name);
      controller.checkpointNow(Phase.EVALUATE);
      return value;
    }
  }

  private static final class ControlledPartialActivation extends ControlledActivation
      implements PartialActivation {
    private final PartialActivation partial;

    ControlledPartialActivation(PartialActivation delegate, OperationController controller) {
      super(delegate, controller);
      partial = delegate;
    }

    @Override
    public AttributePattern[] unknownAttributePatterns() {
      return partial.unknownAttributePatterns();
    }
  }
}
