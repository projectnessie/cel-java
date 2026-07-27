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
package org.projectnessie.cel;

/**
 * Regular-expression engine used by the standard CEL {@code matches} function.
 *
 * <p>Both engines perform substring matching unless the expression contains anchors. The engine is
 * selected for a {@link Program} with {@link ProgramOption#regexEngine(RegexEngine)} and remains
 * fixed for the lifetime of that program.
 */
public enum RegexEngine {
  /**
   * Use {@link java.util.regex.Pattern}.
   *
   * <p>This is the default to retain compatibility with earlier CEL-Java releases, including their
   * support for Java-specific constructs. Java's engine uses backtracking, so applications that
   * accept untrusted expressions or patterns should consider {@link #RE2}.
   */
  JAVA,

  /**
   * Use <a href="https://github.com/google/re2j">RE2/J</a>.
   *
   * <p>RE2/J supports the RE2 regular-expression dialect and rejects Java-specific constructs such
   * as look-around and backreferences. Its non-backtracking execution avoids the catastrophic
   * backtracking behavior possible with some Java regular expressions. This choice does not impose
   * an overall evaluation or input-size limit.
   */
  RE2
}
