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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.expr.v1alpha1.SourceInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class SourceTest {
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

  @Test
  void textCoordinateBoundsAndRoundTrips() {
    for (String contents : List.of("", "abc", "ab\ncd", "\n\nx\n", "ab\n")) {
      Source source = Source.newStringSource(contents, "round-trip");

      for (int offset = 0; offset <= contents.length(); offset++) {
        Location location = source.offsetLocation(offset);
        assertThat(location)
            .describedAs("location for offset %s in %s", offset, contents)
            .isNotEqualTo(Location.NoLocation);
        assertThat(source.locationOffset(location))
            .describedAs("round trip for offset %s in %s", offset, contents)
            .isEqualTo(offset);
      }

      assertThat(source.offsetLocation(-1)).isEqualTo(Location.NoLocation);
      assertThat(source.offsetLocation(contents.length() + 1)).isEqualTo(Location.NoLocation);
    }
  }

  @Test
  void textLocationBounds() {
    Source source = Source.newStringSource("ab\nc", "bounds");

    assertThat(source.locationOffset(Location.newLocation(1, 0))).isZero();
    assertThat(source.locationOffset(Location.newLocation(1, 2))).isEqualTo(2);
    assertThat(source.locationOffset(Location.newLocation(2, 0))).isEqualTo(3);
    assertThat(source.locationOffset(Location.newLocation(2, 1))).isEqualTo(4);

    assertThat(source.locationOffset(Location.newLocation(-1, 0))).isEqualTo(-1);
    assertThat(source.locationOffset(Location.newLocation(0, 0))).isEqualTo(-1);
    assertThat(source.locationOffset(Location.newLocation(1, -1))).isEqualTo(-1);
    assertThat(source.locationOffset(Location.newLocation(1, 3))).isEqualTo(-1);
    assertThat(source.locationOffset(Location.newLocation(2, 2))).isEqualTo(-1);
    assertThat(source.locationOffset(Location.newLocation(3, 0))).isEqualTo(-1);
    assertThat(source.locationOffset(Location.newLocation(3, 3))).isEqualTo(-1);
    assertThatThrownBy(() -> source.locationOffset(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("location");
  }

  @Test
  void emptyAndFinalNewlineBounds() {
    Source empty = Source.newStringSource("", "empty");
    assertThat(empty.locationOffset(Location.newLocation(1, 0))).isZero();
    assertThat(empty.locationOffset(Location.newLocation(1, 1))).isEqualTo(-1);
    assertThat(empty.locationOffset(Location.newLocation(2, 0))).isEqualTo(-1);

    Source finalNewline = Source.newStringSource("ab\n", "final-newline");
    assertThat(finalNewline.locationOffset(Location.newLocation(1, 2))).isEqualTo(2);
    assertThat(finalNewline.locationOffset(Location.newLocation(2, 0))).isEqualTo(3);
    assertThat(finalNewline.locationOffset(Location.newLocation(2, 1))).isEqualTo(-1);
    assertThat(finalNewline.offsetLocation(3)).isEqualTo(Location.newLocation(2, 0));
  }

  @Test
  void metadataCoordinateBounds() {
    Source source =
        Source.newInfoSource(
            SourceInfo.newBuilder()
                .setLocation("metadata")
                .addAllLineOffsets(List.of(3, 7))
                .build());

    assertThat(source.locationOffset(Location.newLocation(1, 2))).isEqualTo(2);
    assertThat(source.locationOffset(Location.newLocation(1, 3))).isEqualTo(-1);
    assertThat(source.locationOffset(Location.newLocation(2, 0))).isEqualTo(3);
    assertThat(source.locationOffset(Location.newLocation(2, 3))).isEqualTo(6);
    assertThat(source.locationOffset(Location.newLocation(2, 4))).isEqualTo(-1);
    assertThat(source.locationOffset(Location.newLocation(3, 0))).isEqualTo(7);
    assertThat(source.locationOffset(Location.newLocation(3, 100))).isEqualTo(107);
    assertThat(source.locationOffset(Location.newLocation(4, 0))).isEqualTo(-1);

    assertThat(source.offsetLocation(-1)).isEqualTo(Location.NoLocation);
    assertThat(source.offsetLocation(0)).isEqualTo(Location.newLocation(1, 0));
    assertThat(source.offsetLocation(2)).isEqualTo(Location.newLocation(1, 2));
    assertThat(source.offsetLocation(3)).isEqualTo(Location.newLocation(2, 0));
    assertThat(source.offsetLocation(6)).isEqualTo(Location.newLocation(2, 3));
    assertThat(source.offsetLocation(7)).isEqualTo(Location.newLocation(3, 0));
    assertThat(source.offsetLocation(100)).isEqualTo(Location.newLocation(3, 93));
  }

  @Test
  void metadataWithoutLineOffsetsAndIntegerOverflow() {
    Source oneLine = Source.newInfoSource(SourceInfo.newBuilder().build());
    assertThat(oneLine.locationOffset(Location.newLocation(1, Integer.MAX_VALUE)))
        .isEqualTo(Integer.MAX_VALUE);
    assertThat(oneLine.locationOffset(Location.newLocation(2, 0))).isEqualTo(-1);
    assertThat(oneLine.offsetLocation(Integer.MAX_VALUE))
        .isEqualTo(Location.newLocation(1, Integer.MAX_VALUE));

    Source largeLineStart =
        Source.newInfoSource(SourceInfo.newBuilder().addLineOffsets(Integer.MAX_VALUE).build());
    assertThat(largeLineStart.locationOffset(Location.newLocation(2, 0)))
        .isEqualTo(Integer.MAX_VALUE);
    assertThat(largeLineStart.locationOffset(Location.newLocation(2, 1))).isEqualTo(-1);
  }

  @Test
  void lineOffsetsAreImmutable() {
    List<Integer> offsets = new ArrayList<>(List.of(3, 7));
    Source source = new SourceImpl("ab\ndef", "immutable-offsets", offsets);

    offsets.set(0, 4);

    assertThat(source.lineOffsets()).containsExactly(3, 7);
    assertThatThrownBy(() -> source.lineOffsets().set(0, 4))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  /**
   * Ensure there is no panic when passing nil, NewInfoSource should use proto v2 style accessors.
   */
  @Test
  @Disabled("Does not apply in Java")
  void noPanicOnNil() {
    // Not implemented - there's no 'nil' in Java
    //  _ = NewInfoSource(nil)
  }
}
