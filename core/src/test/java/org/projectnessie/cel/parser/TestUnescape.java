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
package org.projectnessie.cel.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.parser.Unescape.unescape;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TestUnescape {

  @Test
  void unescapeSingleQuote() {
    String text = unescape("'hello'", false);
    assertThat(text).isEqualTo("hello");
  }

  @Test
  void unescapeDoubleQuote() {
    String text = unescape("\"\"", false);
    assertThat(text).isEqualTo("");
  }

  @Test
  void unescapeEscapedQuote() {
    // The argument to unescape is dquote-backslash-dquote-dquote where both
    // the backslash and inner double-quote are escaped.
    String text = unescape("\"\\\\\\\"\"", false);
    assertThat(text).isEqualTo("\\\"");
  }

  @Test
  void unescapeEscapedEscape() {
    String text = unescape("\"\\\\\"", false);
    assertThat(text).isEqualTo("\\");
  }

  @Test
  void unescapeTripleSingleQuote() {
    String text = unescape("'''x''x'''", false);
    assertThat(text).isEqualTo("x''x");
  }

  @Test
  void unescapeTripleDoubleQuote() {
    String text = unescape("\"\"\"x\"\"x\"\"\"", false);
    assertThat(text).isEqualTo("x\"\"x");
  }

  @Test
  void unescapeMultiOctalSequence() {
    // Octal 303 -> Code point 195 (Ã)
    // Octal 277 -> Code point 191 (¿)
    String text = unescape("\"\303\277\"", false);
    assertThat(text).isEqualTo("Ã¿");
  }

  @Test
  void unescapeOctalSequence() {
    // Octal 377 -> Code point 255 (ÿ)
    String text = unescape("\"\377\"", false);
    assertThat(text).isEqualTo("ÿ");
  }

  @Test
  void unescapeUnicodeSequence() {
    String text = unescape("\"\u263A\u263A\"", false);
    assertThat(text).isEqualTo("☺☺");
  }

  @Test
  void unescapeLegalEscapes() {
    String text = unescape("\"\\a\\b\\f\\n\\r\\t\\v\\'\\\"\\\\\\? Legal escapes\"", false);
    assertThat(text).isEqualTo("\007\b\f\n\r\t\013'\"\\? Legal escapes");
  }

  @Test
  void unescapeIllegalEscapes() {
    // The first escape sequences are legal, but the '\>' is not.
    assertThatThrownBy(
            () -> unescape("\"\\a\\b\\f\\n\\r\\t\\v\\'\\\"\\\\\\? Illegal escape \\>\"", false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unescapeBytesAscii() {
    String bs = unescape("\"abc\"", true);
    assertThat(bs)
        .isEqualTo(
            new String(new byte[] {(byte) 0x61, (byte) 0x62, (byte) 0x63}, StandardCharsets.UTF_8));
  }

  @Test
  void unescapeBytesUnicode() {
    String bs = unescape("\"ÿ\"", true);
    assertThat(bs)
        .isEqualTo(new String(new byte[] {(byte) 0xc3, (byte) 0xbf}, StandardCharsets.UTF_8));
  }

  @Test
  void unescapeBytesOctal() {
    String bs = unescape("\"\\303\\277\"", true);
    assertThat(bs)
        .isEqualTo(new String(new byte[] {(byte) 0xc3, (byte) 0xbf}, StandardCharsets.UTF_8));
  }

  @Test
  void unescapeBytesOctalMax() {
    String bs = unescape("\"\\377\"", true);
    assertThat(bs).isEqualTo(new String(new byte[] {(byte) 0xff}, StandardCharsets.UTF_8));
  }

  @Test
  void unescapeBytesQuoting() {
    String bs = unescape("'''\"Kim\\t\"'''", true);
    assertThat(bs).isEqualTo(new String(new char[] {0x22, 0x4b, 0x69, 0x6d, 0x09, 0x22}));
  }

  @Test
  void unescapeBytesHex() {
    String bs = unescape("\"\\xc3\\xbf\"", true);
    assertThat(bs)
        .isEqualTo(new String(new byte[] {(byte) 0xc3, (byte) 0xbf}, StandardCharsets.UTF_8));
  }

  @Test
  void unescapeBytesHexMax() {
    String bs = unescape("\"\\xff\"", true);
    assertThat(bs).isEqualTo(new String(new byte[] {(byte) 0xff}, StandardCharsets.UTF_8));
  }

  @Test
  void unescapeBytesUnicodeEscape() {
    assertThatThrownBy(() -> unescape("\"\\u00ff\"", true))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
