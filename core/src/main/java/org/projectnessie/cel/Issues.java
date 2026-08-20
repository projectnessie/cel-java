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

import java.util.List;
import org.projectnessie.cel.common.CELError;
import org.projectnessie.cel.common.Errors;
import org.projectnessie.cel.common.Source;

/**
 * Diagnostics produced while parsing or type-checking a CEL expression.
 *
 * <p>Currently this type reports errors. Call {@link #hasIssues()} before using an AST returned in
 * the same {@link Env.AstIssuesTuple}. This wrapper retains its underlying {@link Errors}; it is
 * mutable through the live list returned by {@link #getErrors()} and is not thread-safe.
 */
public final class Issues {

  private final Errors errs;

  private Issues(Errors errs) {
    this.errs = errs;
  }

  /**
   * Wraps an existing error collection without copying it.
   *
   * @param errs error collection
   * @return issues backed by {@code errs}
   */
  public static Issues newIssues(Errors errs) {
    return new Issues(errs);
  }

  /**
   * Creates empty diagnostics associated with a source.
   *
   * @param source source used to format any subsequently added errors
   * @return empty issues
   */
  public static Issues noIssues(Source source) {
    return new Issues(new Errors(source));
  }

  /**
   * Creates an exception containing the formatted diagnostics when errors are present.
   *
   * @return formatted runtime exception, or {@code null} when there are no issues
   */
  public RuntimeException err() {
    if (!errs.hasErrors()) {
      return null;
    }
    return new RuntimeException(toString());
  }

  /**
   * Reports whether errors are present.
   *
   * @return {@code true} when at least one error is present
   */
  public boolean hasIssues() {
    return errs.hasErrors();
  }

  /**
   * Returns the live mutable error list.
   *
   * <p>Changes to this list change this {@code Issues} instance. Callers that need an immutable
   * snapshot should use {@link List#copyOf(java.util.Collection)}.
   *
   * @return mutable errors in encounter order
   */
  public List<CELError> getErrors() {
    return errs.getErrors();
  }

  /**
   * Returns new diagnostics containing this instance's errors followed by {@code other}'s errors.
   *
   * <p>Neither input is modified. The returned list contains the same {@link CELError} references.
   *
   * @param other diagnostics to append
   * @return combined diagnostics
   */
  public Issues append(Issues other) {
    return newIssues(errs.append(other.getErrors()));
  }

  /**
   * Formats errors with source locations for display.
   *
   * @return newline-delimited diagnostic text, or an empty string when no errors are present
   */
  @Override
  public String toString() {
    return errs.toDisplayString();
  }
}
