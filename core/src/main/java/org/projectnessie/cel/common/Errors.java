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

/**
 * Mutable diagnostic collection for one CEL source.
 *
 * <p>Instances are operation-local and are not thread-safe. {@link #getErrors()} exposes the live
 * list for historical compatibility.
 */
public class Errors {
  private final List<CELError> errors = new ArrayList<>();
  private final Source source;

  /** Creates an empty diagnostic collection for the supplied source. */
  public Errors(Source source) {
    this.source = source;
  }

  /** Records a formatted error at a source location. */
  public void reportError(Location l, String format, Object... args) {
    reportError(null, l, format, args);
  }

  /** Records a formatted error and its originating exception at a source location. */
  public void reportError(Exception e, Location l, String format, Object... args) {
    CELError err = new CELError(e, l, String.format(format, args));
    errors.add(err);
  }

  /** Returns the live mutable list of recorded diagnostics. */
  public List<CELError> getErrors() {
    return errors;
  }

  /** Returns whether at least one diagnostic has been recorded. */
  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  /** Returns a new collection containing the current diagnostics followed by the supplied ones. */
  public Errors append(List<CELError> errors) {
    Errors errs = new Errors(source);
    errs.errors.addAll(this.errors);
    errs.errors.addAll(errors);
    return errs;
  }

  @Override
  public String toString() {
    return toDisplayString();
  }

  /** Returns source-decorated diagnostics sorted and separated by newlines. */
  public String toDisplayString() {
    return errors.stream()
        .sorted()
        .map(e -> e.toDisplayString(source))
        .collect(Collectors.joining("\n"));
  }

  /** Records a syntax-error diagnostic. */
  public void syntaxError(Location l, String msg) {
    reportError(l, "Syntax error: %s", msg);
  }
}
