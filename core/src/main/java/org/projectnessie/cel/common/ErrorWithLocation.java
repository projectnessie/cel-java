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

/**
 * Runtime exception carrying an optional CEL source location.
 *
 * <p>Parser macro implementations may throw this exception to turn a failed rewrite into a
 * source-aware parse diagnostic. A {@code null} location asks the parser to use the location of the
 * macro call.
 */
public final class ErrorWithLocation extends RuntimeException {
  private final Location location;

  /** Creates an exception with an optional source location and diagnostic message. */
  public ErrorWithLocation(Location location, String message) {
    super(message);
    this.location = location;
  }

  /**
   * Returns the explicit source location, or {@code null} when the caller location should be used.
   */
  public Location getLocation() {
    return location;
  }
}
