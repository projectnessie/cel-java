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

/**
 * Base checked exception for failures while creating or executing a {@link Script}.
 *
 * <p>{@link ScriptCreateException} reports parse and type-check diagnostics, while {@link
 * ScriptExecutionException} reports CEL error results and incompatible unknown results from the
 * high-level script API.
 */
public abstract class ScriptException extends Exception {

  /**
   * Creates a script exception with a message.
   *
   * @param message detail message
   */
  protected ScriptException(String message) {
    super(message);
  }

  /**
   * Creates a script exception with a message and cause.
   *
   * @param message detail message
   * @param cause underlying cause
   */
  protected ScriptException(String message, Throwable cause) {
    super(message, cause);
  }
}
