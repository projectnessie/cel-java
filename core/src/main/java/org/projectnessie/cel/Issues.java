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
 * Issues defines methods for inspecting the error details of parse and check calls.
 *
 * <p>Note: in the future, non-fatal warnings and notices may be inspectable via the Issues struct.
 */
public class Issues {

  private final Errors errs;

  private Issues(Errors errs) {
    this.errs = errs;
  }

  /** NewIssues returns an Issues struct from a common.Errors object. */
  public static Issues newIssues(Errors errs) {
    return new Issues(errs);
  }

  /** NewIssues returns an Issues struct from a common.Errors object. */
  public static Issues noIssues(Source source) {
    return new Issues(new Errors(source));
  }

  /** Err returns an error value if the issues list contains one or more errors. */
  public RuntimeException err() {
    if (!errs.hasErrors()) {
      return null;
    }
    return new RuntimeException(toString());
  }

  public boolean hasIssues() {
    return errs.hasErrors();
  }

  /** Errors returns the collection of errors encountered in more granular detail. */
  public List<CELError> getErrors() {
    return errs.getErrors();
  }

  /** Append collects the issues from another Issues struct into a new Issues object. */
  public Issues append(Issues other) {
    return newIssues(errs.append(other.getErrors()));
  }

  /** String converts the issues to a suitable display string. */
  @Override
  public String toString() {
    return errs.toDisplayString();
  }
}
