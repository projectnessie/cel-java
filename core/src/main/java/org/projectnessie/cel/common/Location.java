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

public interface Location extends Comparable<Location> {
  Location NoLocation = newLocation(-1, -1);

  // NewLocation creates a new location.
  static Location newLocation(int line, int column) {
    return new SourceLocation(line, column);
  }

  /** 1-based line number within source. */
  int line();

  /** 0-based column number within source. */
  int column();
}

class SourceLocation implements Location {
  private final int line;
  private final int column;

  public SourceLocation(int line, int column) {
    this.line = line;
    this.column = column;
  }

  @Override
  public int compareTo(Location o) {
    int r = Integer.compare(line, o.line());
    if (r == 0) {
      r = Integer.compare(column, o.column());
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
    SourceLocation that = (SourceLocation) o;
    return line == that.line && column == that.column;
  }

  @Override
  public int hashCode() {
    return Objects.hash(line, column);
  }

  @Override
  public String toString() {
    return "line=" + line + ", column=" + column;
  }

  @Override
  public int line() {
    return line;
  }

  @Override
  public int column() {
    return column;
  }
}
