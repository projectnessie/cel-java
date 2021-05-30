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
package org.projectnessie.cel.common;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class Errors {
  private final List<Error> errors = new ArrayList<>();
  private final Source source;

  public Errors(Source source) {
    this.source = source;
  }

  /** ReportError records an error at a source location. */
  public void reportError(Location l, String format, Object... args) {
    Error err = new Error(l, String.format(format, args));
    errors.add(err);
  }

  /** GetErrors returns the list of observed errors. */
  public List<Error> getErrors() {
    return errors;
  }

  /**
   * Append takes an Errors object as input creates a new Errors object with the current and input
   * errors.
   */
  public Errors append(List<Error> errors) {
    Errors errs = new Errors(source);
    errs.errors.addAll(this.errors);
    errs.errors.addAll(errors);
    return errs;
  }

  @Override
  public String toString() {
    return toDisplayString();
  }

  /** ToDisplayString returns the error set to a newline delimited string. */
  public String toDisplayString() {
    return errors.stream()
        .sorted()
        .map(e -> e.toDisplayString(source))
        .collect(Collectors.joining("\n"));
  }

  public void syntaxError(Location l, String msg) {
    reportError(l, "Syntax error: %s", msg);
  }
}
