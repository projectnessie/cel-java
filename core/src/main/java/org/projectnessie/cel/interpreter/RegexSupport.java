/*
 * Copyright (C) 2026 The Authors of CEL-Java
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
package org.projectnessie.cel.interpreter;

import static java.util.Objects.requireNonNull;

import org.projectnessie.cel.RegexEngine;

/** Engine-specific regular-expression compilation hidden behind an interpreter-local contract. */
final class RegexSupport {
  private RegexSupport() {}

  static CompiledRegex compile(RegexEngine engine, String expression) {
    requireNonNull(engine, "engine");
    return switch (engine) {
      case JAVA -> {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(expression);
        yield input -> pattern.matcher(input).find();
      }
      case RE2 -> {
        com.google.re2j.Pattern pattern = com.google.re2j.Pattern.compile(expression);
        yield input -> pattern.matcher(input).find();
      }
    };
  }

  static boolean find(RegexEngine engine, String expression, String input) {
    requireNonNull(engine, "engine");
    return switch (engine) {
      case JAVA -> java.util.regex.Pattern.compile(expression).matcher(input).find();
      case RE2 -> com.google.re2j.Pattern.compile(expression).matcher(input).find();
    };
  }

  interface CompiledRegex {
    boolean find(String input);
  }
}
