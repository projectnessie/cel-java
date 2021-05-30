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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** TestErrors reporting and recording. */
class TestErrors {

  @Test
  void errors() {
    Source source = Source.newStringSource("a.b\n&&arg(missing, paren", "errors-test");
    Errors errors = new Errors(source);
    errors.reportError(Location.newLocation(1, 1), "No such field");
    assertThat(errors.getErrors()).withFailMessage("first error not recorded").hasSize(1);
    errors.reportError(Location.newLocation(2, 20), "Syntax error, missing paren");
    assertThat(errors.getErrors()).withFailMessage("second error not recorded").hasSize(2);

    assertThat(errors.toDisplayString())
        .isEqualTo(
            "ERROR: errors-test:1:2: No such field\n"
                + " | a.b\n"
                + " | .^\n"
                + "ERROR: errors-test:2:21: Syntax error, missing paren\n"
                + " | &&arg(missing, paren\n"
                + " | ....................^");
  }

  @Test
  void wideAndNarrowCharacters() {
    Source source = Source.newStringSource("你好吗\n我a很好\n", "errors-test");
    Errors errors = new Errors(source);
    errors.reportError(Location.newLocation(2, 3), "Unexpected character '好'");

    assertThat(errors.toDisplayString())
        .isEqualTo("ERROR: errors-test:2:4: Unexpected character '好'\n" + " | 我a很好\n" + " | ...^");
  }

  @Test
  @Disabled(
      "Assuming display-width by UTF8 encoding length is just wrong, https://github.com/google/cel-go/blob/7ac350ef44842d52e86d06baa04ad6d80cc4b885/common/error.go#L54")
  void wideAndNarrowCharactersEmojis() {
    Source source =
        Source.newStringSource("      '😁' in ['😁', '😑', '😦'] && in.😁", "errors-test");
    Errors errors = new Errors(source);
    errors.reportError(
        Location.newLocation(1, 32),
        "Syntax error: extraneous input 'in' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}");
    errors.reportError(
        Location.newLocation(1, 35), "Syntax error: token recognition error at: '😁'");
    errors.reportError(Location.newLocation(1, 36), "Syntax error: missing IDENTIFIER at '<EOF>'");

    assertThat(errors.toDisplayString())
        .isEqualTo(
            "ERROR: errors-test:1:33: Syntax error: extraneous input 'in' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
                + " |       '😁' in ['😁', '😑', '😦'] && in.😁\n"
                + " | .......．.......．....．....．......^\n"
                + "ERROR: errors-test:1:36: Syntax error: token recognition error at: '😁'\n"
                + " |       '😁' in ['😁', '😑', '😦'] && in.😁\n"
                + " | .......．.......．....．....．.........＾\n"
                + "ERROR: errors-test:1:37: Syntax error: missing IDENTIFIER at '<EOF>'\n"
                + " |       '😁' in ['😁', '😑', '😦'] && in.😁\n"
                + " | .......．.......．....．....．.........．^");
  }

  @Test
  void wideAndNarrowCharactersEmojisPlain() {
    Source source =
        Source.newStringSource("      '😁' in ['😁', '😑', '😦'] && in.😁", "errors-test");
    Errors errors = new Errors(source);
    errors.reportError(
        Location.newLocation(1, 32),
        "Syntax error: extraneous input 'in' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}");
    errors.reportError(
        Location.newLocation(1, 35), "Syntax error: token recognition error at: '😁'");
    errors.reportError(Location.newLocation(1, 36), "Syntax error: missing IDENTIFIER at '<EOF>'");

    assertThat(errors.toDisplayString())
        .isEqualTo(
            "ERROR: errors-test:1:33: Syntax error: extraneous input 'in' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
                + " |       '😁' in ['😁', '😑', '😦'] && in.😁\n"
                + " | ................................^\n"
                + "ERROR: errors-test:1:36: Syntax error: token recognition error at: '😁'\n"
                + " |       '😁' in ['😁', '😑', '😦'] && in.😁\n"
                + " | ...................................^\n"
                + "ERROR: errors-test:1:37: Syntax error: missing IDENTIFIER at '<EOF>'\n"
                + " |       '😁' in ['😁', '😑', '😦'] && in.😁\n"
                + " | ....................................^");
  }
}
