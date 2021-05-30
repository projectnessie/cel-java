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

import java.util.Objects;

// TODO rename this class!
public final class Error implements Comparable<Error> {
  private static final char dot = '.';
  private static final char ind = '^';
  // private static final char wideDot = '\uFF0E'; // result of Go's width.Widen(".")
  // private static final char wideInd = '\uFF3E'; // result of Go's width.Widen("^")
  private final Location location;
  private final String message;

  public Error(Location location, String message) {
    this.location = location;
    this.message = message;
  }

  @Override
  public int compareTo(Error o) {
    int r = location.compareTo(o.location);
    if (r == 0) {
      r = message.compareTo(o.message);
    }
    return r;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Error error = (Error) o;
    return Objects.equals(location, error.location) && Objects.equals(message, error.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(location, message);
  }

  @Override
  public String toString() {
    return "Error{" + "location=" + location + ", message='" + message + '\'' + '}';
  }

  /** ToDisplayString decorates the error message with the source location. */
  public String toDisplayString(Source source) {
    StringBuilder result =
        new StringBuilder(
            String.format(
                "ERROR: %s:%d:%d: %s",
                source.description(),
                location.line(),
                location.column() + 1, // add one to the 0-based column for display
                message));

    String snippet = source.snippet(location.line());
    if (snippet != null) {
      snippet = snippet.replace('\t', ' ');
      result.append("\n | ").append(snippet);

      // The original Go code does some wild-guessing about the displayed width of a character,
      // but it blindly assumes that a UTF-8 _encoding_ length > 1 byte means that the character
      // needs two columns to display. That's not correct... think: ä ö ü ß € etc etc etc
      // If we want have nicer (wide) dots, we might think of interpreting the string in a more
      // sophisticated way, maybe use jline's WCWidth, but that one is also quite rudimentary wrt
      // code-blocks (e.g. doesn't know about emojis).
      result.append("\n | ");
      for (int i = 0; i < location.column(); i++) {
        result.append(dot);
      }
      result.append(ind);
    }
    return result.toString();
  }
}
