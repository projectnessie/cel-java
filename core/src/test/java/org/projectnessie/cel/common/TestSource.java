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

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TestSource {
  static final String unexpectedSnippet = "got snippet '%s', want '%s'";
  static final String snippetFound = "snippet found at line %d, wanted none";

  /** the error description method. */
  @Test
  void description() {
    String contents = "example content\nsecond line";

    Source source = Source.newStringSource(contents, "description-test");

    assertThat(source)
        .extracting(Source::content, Source::description)
        .containsExactly(
            // Verify the content
            contents,
            // Verify the description
            "description-test");

    // Assert that the snippets on lines 1 & 2 are what was expected.
    assertThat(source)
        .extracting(s -> s.snippet(2), s -> s.snippet(1))
        .containsExactly("second line", "example content");
  }

  /** make sure that the offsets accurately reflect the location of a character in source. */
  @Test
  void emptyContents() {
    Source source = Source.newStringSource("", "empty-test");

    assertThat(source.snippet(1)).isEqualTo("");

    String str2 = source.snippet(2);
    assertThat(str2).withFailMessage(snippetFound, 2).isNull();
    assertThat(str2).withFailMessage(unexpectedSnippet, str2, null).isNull();
  }

  /** snippets from a single line source. */
  @Test
  void snippetSingleline() {
    Source source = Source.newStringSource("hello, world", "one-line-test");

    assertThat(source.snippet(1)).isEqualTo("hello, world");

    String str2 = source.snippet(2);
    assertThat(str2).withFailMessage(snippetFound, 2).isNull();
    assertThat(str2).withFailMessage(unexpectedSnippet, str2, null).isNull();
  }

  /** snippets of text from a multiline source. */
  @Test
  void snippetMultiline() {
    List<String> testLines = Arrays.asList("", "", "hello", "world", "", "my", "bub", "", "");

    Source source = Source.newStringSource(String.join("\n", testLines), "mulit-line-test");

    assertThat(source.snippet(testLines.size() + 1)).isNull();
    assertThat(source.snippet(0)).isNull();

    for (int i = 1; i <= testLines.size(); i++) {
      String testLine = testLines.get(i - 1);

      String str = source.snippet(i);
      assertThat(str)
          .withFailMessage("Line #%d, expect '%s', got '%s'", i, testLine, str)
          .isEqualTo(testLine);
    }
  }

  /** make sure that the offsets accurately reflect the location of a character in source. */
  @Test
  void locationOffset() {
    String contents = "c.d &&\n\t b.c.arg(10) &&\n\t test(10)";
    Source source = Source.newStringSource(contents, "offset-test");
    assertThat(source.lineOffsets()).containsExactly(7, 24, 35);

    // Ensure that selecting a set of characters across multiple lines works as
    // expected.
    int charStart = source.locationOffset(Location.newLocation(1, 2));
    int charEnd = source.locationOffset(Location.newLocation(3, 2));
    assertThat(contents.substring(charStart, charEnd)).isEqualTo("d &&\n\t b.c.arg(10) &&\n\t ");
    assertThat(source.locationOffset(Location.newLocation(4, 0)))
        .withFailMessage("Character offset was out of range of source, but still found.")
        .isEqualTo(-1);
  }

  /**
   * Ensure there is no panic when passing nil, NewInfoSource should use proto v2 style accessors.
   */
  @Test
  @Disabled("IMPLEMENT Source.newInfoSource()")
  void noPanicOnNil() {
    // TODO: _ = NewInfoSource(nil)
  }
}
