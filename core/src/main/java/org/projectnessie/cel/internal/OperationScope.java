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

/**
 * Internal thread-confined construction-operation scope.
 *
 * <p>This type is public only for communication between CEL-Java packages. It is not a supported
 * application API.
 */
public final class OperationScope implements AutoCloseable {
  private static final ThreadLocal<OperationController> CURRENT = new ThreadLocal<>();

  private final OperationController previous;

  private OperationScope(OperationController controller) {
    previous = CURRENT.get();
    CURRENT.set(controller);
  }

  /** Installs a controller until the returned scope is closed. */
  public static OperationScope install(OperationController controller) {
    return new OperationScope(controller);
  }

  /** Returns the current controller or the shared unrestricted controller. */
  public static OperationController current() {
    var controller = CURRENT.get();
    return controller != null ? controller : OperationController.none();
  }

  @Override
  public void close() {
    if (previous == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(previous);
    }
  }
}
