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

import java.util.Objects;
import org.projectnessie.cel.Issues;
import org.projectnessie.cel.common.CELError;

/**
 * Reports parse or type-check failure while compiling a {@link Script}.
 *
 * <p>The structured diagnostics are available through {@link #getIssues()}. Exceptions attached to
 * individual diagnostics are also attached to this exception as suppressed exceptions.
 */
public final class ScriptCreateException extends ScriptException {

  private final Issues issues;

  /**
   * Creates an exception for a failed script compilation stage.
   *
   * @param message description of the failed stage
   * @param issues structured parse or type-check diagnostics
   * @throws NullPointerException if {@code issues} is {@code null}
   */
  public ScriptCreateException(String message, Issues issues) {
    super(String.format("%s: %s", message, issues));
    this.issues = issues;
    issues.getErrors().stream()
        .map(CELError::getException)
        .filter(Objects::nonNull)
        .forEach(this::addSuppressed);
  }

  /**
   * Returns the structured diagnostics that caused compilation to fail.
   *
   * @return compilation diagnostics
   */
  public Issues getIssues() {
    return issues;
  }
}
