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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;

public class Unescape {

  /**
   * Unescape takes a quoted string, unquotes, and unescapes it.
   *
   * <p>This function performs escaping compatible with GoogleSQL.
   */
  public static String unescape(String value, boolean isBytes) {
    // All strings normalize newlines to the \n representation.
    value = value.replaceAll("\r\n", "\n").replaceAll("\r", "\n");
    int n = value.length();

    // Nothing to unescape / decode.
    if (n < 2) {
      return value; // fmt.Errorf("unable to unescape string")
    }

    // Raw string preceded by the 'r|R' prefix.
    boolean isRawLiteral = false;
    if (value.charAt(0) == 'r' || value.charAt(0) == 'R') {
      value = value.substring(1);
      n = value.length();
      isRawLiteral = true;
    }

    // Quoted string of some form, must have same first and last char.
    if (value.charAt(0) != value.charAt(n - 1)
        || (value.charAt(0) != '"' && value.charAt(0) != '\'')) {
      return value; // fmt.Errorf("unable to unescape string")
    }

    // Normalize the multi-line CEL string representation to a standard
    // Go quoted string.
    // TODO remove the substring()s here (and update i + n accordingly)
    if (n >= 6) {
      if (value.startsWith("'''")) {
        if (!value.endsWith("'''")) {
          return value; // fmt.Errorf("unable to unescape string")
        }
        value = "\"" + value.substring(3, n - 3) + "\"";
        n = value.length();
      } else if (value.startsWith("\"\"\"")) {
        if (!value.endsWith("\"\"\"")) {
          return value; // fmt.Errorf("unable to unescape string")
        }
        value = "\"" + value.substring(3, n - 3) + "\"";
        n = value.length();
      }
    }
    value = value.substring(1, n - 1);
    n = n - 2;
    // If there is nothing to escape, then return.
    if (isRawLiteral || value.indexOf('\\') == -1) {
      return value;
    }

    // Otherwise the string contains escape characters.
    ByteBuffer buf = ByteBuffer.allocate(value.length() * 3 / 2);
    for (int i = 0; i < n; i++) {
      char c = value.charAt(i);
      if (c == '\\') {
        // \ escape sequence
        i++;
        if (i == n) {
          throw new IllegalArgumentException(
              "unable to unescape string, found '\\' as last character");
        }
        c = value.charAt(i);

        switch (c) {
          case 'a':
            buf.put((byte) 7);
            break; // BEL
          case 'b':
            buf.put((byte) '\b');
            break;
          case 'f':
            buf.put((byte) '\f');
            break;
          case 'n':
            buf.put((byte) '\n');
            break;
          case 'r':
            buf.put((byte) '\r');
            break;
          case 't':
            buf.put((byte) '\t');
            break;
          case 'v':
            buf.put((byte) 11);
            break; // VT
          case '\\':
            buf.put((byte) '\\');
            break;
          case '\'':
            buf.put((byte) '\'');
            break;
          case '"':
            buf.put((byte) '\"');
            break;
          case '`':
            buf.put((byte) '`');
            break;
          case '?':
            buf.put((byte) '?');
            break;

            // 4. Unicode escape sequences, reproduced from `strconv/quote.go`
          case 'x':
          case 'X':
          case 'u':
          case 'U':
            int nHex = 0;
            switch (c) {
              case 'x':
              case 'X':
                nHex = 2;
                break;
              case 'u':
                nHex = 4;
                if (isBytes) {
                  throw unableToUnescapeString();
                }
                break;
              case 'U':
                nHex = 8;
                if (isBytes) {
                  throw unableToUnescapeString();
                }
                break;
            }
            if (n - nHex < i) {
              throw unableToUnescapeString();
            }
            int v = 0;
            for (int j = 0; j < nHex; j++) {
              i++;
              c = value.charAt(i);
              int nib = unhex(c);
              if (nib == -1) {
                throw unableToUnescapeString();
              }
              v = (v << 4) | nib;
            }
            unescapedChar(isBytes, buf, v);
            break;

            // 5. Octal escape sequences, must be three digits \[0-3][0-7][0-7]
          case '0':
          case '1':
          case '2':
          case '3':
            if (n - 3 < i) {
              throw unableToUnescapeOctalSequence();
            }
            v = (c - '0');
            for (int j = 0; j < 2; j++) {
              i++;
              c = value.charAt(i);
              if (c < '0' || c > '7') {
                throw unableToUnescapeOctalSequence();
              }
              v = (v << 3) | (c - '0');
            }
            unescapedChar(isBytes, buf, v);
            break;

            // Unknown escape sequence.
          default:
            throw unableToUnescapeString();
        }
      } else {
        // not an escape sequence
        if (c <= 127) {
          buf.put((byte) c);
        } else {
          try {
            ByteBuffer bb =
                encoderThreadLocal.get().reset().encode(CharBuffer.wrap(new char[] {c}));
            buf.put(bb);
          } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("illegal UTF8 character " + ((int) c));
          }
        }
      }
    }
    buf.flip();
    CharBuffer cb = StandardCharsets.UTF_8.decode(buf);
    return cb.toString();
  }

  private static IllegalArgumentException unableToUnescapeOctalSequence() {
    return new IllegalArgumentException("unable to unescape octal sequence in string");
  }

  private static IllegalArgumentException unableToUnescapeString() {
    return new IllegalArgumentException("unable to unescape string");
  }

  private static void unescapedChar(boolean isBytes, ByteBuffer buf, int v) {
    if (isBytes) {
      buf.put((byte) v);
    } else {
      char[] cc = Character.toChars(v);
      try {
        ByteBuffer bb = encoderThreadLocal.get().reset().encode(CharBuffer.wrap(cc));
        buf.put(bb);
      } catch (CharacterCodingException e) {
        throw new IllegalArgumentException("illegal UTF8 character " + v);
      }
    }
  }

  static int unhex(char b) {
    if (b >= '0' && b <= '9') {
      return b - '0';
    } else if (b >= 'a' && b <= 'f') {
      return b - 'a' + 10;
    } else if (b >= 'A' && b <= 'F') {
      return b - 'A' + 10;
    }
    return -1;
  }

  private static final ThreadLocal<CharsetEncoder> encoderThreadLocal =
      ThreadLocal.withInitial(StandardCharsets.UTF_8::newEncoder);
}
