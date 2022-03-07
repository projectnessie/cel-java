/*
 * Copyright (C) 2022 The Authors of CEL-Java
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
package org.projectnessie.cel.extension;

import java.util.*;
import java.util.regex.Pattern;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.interpreter.functions.Overload;

/**
 * StringsLib provides a {@link org.projectnessie.cel.EnvOption} to configure extended functions for
 * string manipulation. As a general note, all indices are zero-based. The implementation is ported
 * from <a href=https://github.com/google/cel-go/blob/master/ext/strings.go>cel-go</a>.
 *
 * <p>Note: Currently the overloading isn't supported.
 *
 * <h3>CharAt</h3>
 *
 * <p>Returns the character at the given position. If the position is negative, or greater than the
 * length of the string, the function will produce an error:
 *
 * <pre>    {@code <string>.charAt(<int>) -> <string>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code 'hello'.charAt(4) // return 'o'}</pre>
 *
 * <pre>    {@code 'hello'.charAt(5) // return ''}</pre>
 *
 * <pre>    {@code 'hello'.charAt(-1) // error}</pre>
 *
 * <h3>IndexOf</h3>
 *
 * <p>Returns the integer index of the first occurrence of the search string. If the search string
 * is not found the function returns -1.
 *
 * <pre>    {@code <string>.indexOf(<string>) -> <int>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code 'hello mellow'.indexOf('') // returns 0}</pre>
 *
 * <pre>    {@code 'hello mellow'.indexOf('ello') // returns 1}</pre>
 *
 * <pre>    {@code 'hello mellow'.indexOf('jello') // returns -1}</pre>
 *
 * <h3>LastIndexOf</h3>
 *
 * <p>Returns the integer index at the start of the last occurrence of the search string. If the
 * search string is not found the function returns -1.
 *
 * <pre>    {@code <string>.lastIndexOf(<string>) -> <int>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code 'hello mellow'.lastIndexOf('') // returns 12}</pre>
 *
 * <pre>    {@code 'hello mellow'.lastIndexOf('ello') // returns 7}</pre>
 *
 * <pre>    {@code 'hello mellow'.lastIndexOf('jello') // returns -1}</pre>
 *
 * <h3>LowerAscii</h4>
 *
 * <p>Returns a new string where all ASCII characters are lower-cased.
 *
 * <p>This function does not perform Unicode case-mapping for characters outside the ASCII range.
 *
 * <pre>    {@code <string>.lowerAscii() -> <string>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code 'TacoCat'.lowerAscii() // returns 'tacocat'}</pre>
 *
 * <pre>    {@code 'TacoCÆt Xii'.lowerAscii() // returns 'tacocÆt xii'}</pre>
 *
 * <h3>Replace</h3>
 *
 * <p>Returns a new string based on the target, which replaces the occurrences of a search string
 * with a replacement string if present.
 *
 * <pre>    {@code <string>.replace(<string>, <string>) -> <string>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code 'hello hello'.replace('he', 'we') // returns 'wello wello'}</pre>
 *
 * <h3>Split</h3>
 *
 * <p>Returns a list of strings split from the input by the given separator.
 *
 * <pre>    {@code <string>.split(<string>) -> <list<string>>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code hello hello hello'.split(' ')     // returns ['hello', 'hello', 'hello']}</pre>
 *
 * <h3>Substring</h3>
 *
 * <p>Returns the substring given a numeric range corresponding to character positions.
 *
 * <p>Character offsets are 0-based with an inclusive start range.
 *
 * <pre>    {@code <string>.substring(<int>) -> <string>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code 'tacocat'.substring(4) // returns 'cat'}</pre>
 *
 * <pre>    {@code 'tacocat'.substring(-1) // error} </pre>
 *
 * <h3>Trim</h3>
 *
 * <p>Returns a new string which removes the leading and trailing whitespace in the target string.
 * The trim function uses the Unicode definition of whitespace which does not include the zero-width
 * spaces. See: <a href="https://en.wikipedia.org/wiki/Whitespace_character#Unicode">Unicode</a>
 *
 * <pre>    {@code <string>.trim() -> <string>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code ' \ttrim\n '.trim() // returns 'trim'}</pre>
 *
 * <h3>UpperAscii</h3>
 *
 * <p>Returns a new string where all ASCII characters are upper-cased.
 *
 * <p>This function does not perform Unicode case-mapping for characters outside the ASCII range.
 *
 * <pre>    {@code <string>.upperAscii() -> <string>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code 'TacoCat'.upperAscii() // returns 'TACOCAT'}</pre>
 *
 * <pre>    {@code 'TacoCÆt Xii'.upperAscii() // returns 'TACOCÆT XII'}</pre>
 */
public class StringsLib implements Library {

  private static final String CHAR_AT = "charAt";
  private static final String INDEX_OF = "indexOf";
  private static final String LAST_INDEX_OF = "lastIndexOf";
  private static final String LOWER_ASCII = "lowerAscii";
  private static final String REPLACE = "replace";
  private static final String SPLIT = "split";
  private static final String SUBSTR = "substring";
  private static final String TRIM_SPACE = "trim";
  private static final String UPPER_ASCII = "upperAscii";

  // whitespace characters definition from
  // https://en.wikipedia.org/wiki/Whitespace_character#Unicode
  private static final Set<Character> UNICODE_WHITE_SPACES =
      new HashSet<>(
          Arrays.asList(
              (char) 0x0009,
              (char) 0x000A,
              (char) 0x000B,
              (char) 0x000C,
              (char) 0x000D,
              (char) 0x0020,
              (char) 0x0085,
              (char) 0x00A0,
              (char) 0x1680,
              (char) 0x2000,
              (char) 0x2001,
              (char) 0x2002,
              (char) 0x2003,
              (char) 0x2004,
              (char) 0x2005,
              (char) 0x2006,
              (char) 0x2007,
              (char) 0x2008,
              (char) 0x2009,
              (char) 0x200A,
              (char) 0x2028,
              (char) 0x2029,
              (char) 0x202F,
              (char) 0x205F,
              (char) 0x3000));

  public static EnvOption strings() {
    return Library.Lib(new StringsLib());
  }

  @Override
  public List<EnvOption> getCompileOptions() {
    List<EnvOption> list = new ArrayList<>();
    EnvOption option =
        EnvOption.declarations(
            Decls.newFunction(
                CHAR_AT,
                Decls.newInstanceOverload(
                    "string_char_at_int", Arrays.asList(Decls.String, Decls.Int), Decls.String)),
            Decls.newFunction(
                INDEX_OF,
                Decls.newInstanceOverload(
                    "string_index_of_string",
                    Arrays.asList(Decls.String, Decls.String),
                    Decls.Int)),
            Decls.newFunction(
                LAST_INDEX_OF,
                Decls.newInstanceOverload(
                    "string_last_index_of_string",
                    Arrays.asList(Decls.String, Decls.String),
                    Decls.Int)),
            Decls.newFunction(
                LOWER_ASCII,
                Decls.newInstanceOverload(
                    "string_lower_ascii", Arrays.asList(Decls.String), Decls.String)),
            Decls.newFunction(
                REPLACE,
                Decls.newInstanceOverload(
                    "string_replace_string_string",
                    Arrays.asList(Decls.String, Decls.String, Decls.String),
                    Decls.String)),
            Decls.newFunction(
                SPLIT,
                Decls.newInstanceOverload(
                    "string_split_string", Arrays.asList(Decls.String, Decls.String), Decls.Dyn)),
            Decls.newFunction(
                SUBSTR,
                Decls.newInstanceOverload(
                    "string_substring_int", Arrays.asList(Decls.String, Decls.Int), Decls.String)),
            Decls.newFunction(
                TRIM_SPACE,
                Decls.newInstanceOverload(
                    "string_trim", Arrays.asList(Decls.String), Decls.String)),
            Decls.newFunction(
                UPPER_ASCII,
                Decls.newInstanceOverload(
                    "string_upper_ascii", Arrays.asList(Decls.String), Decls.String)));
    list.add(option);
    return list;
  }

  @Override
  public List<ProgramOption> getProgramOptions() {
    List<ProgramOption> list = new ArrayList<>();
    ProgramOption functions =
        ProgramOption.functions(
            Overload.binary(CHAR_AT, Guards.callInStrIntOutStr(StringsLib::charAt)),
            Overload.binary(INDEX_OF, Guards.callInStrStrOutInt(StringsLib::indexOf)),
            Overload.binary(LAST_INDEX_OF, Guards.callInStrStrOutInt(StringsLib::lastIndexOf)),
            Overload.unary(LOWER_ASCII, Guards.callInStrOutStr(StringsLib::lowerASCII)),
            Overload.function(REPLACE, Guards.callInStrStrStrOutStr(StringsLib::replace)),
            Overload.binary(SPLIT, Guards.callInStrStrOutStrArr(StringsLib::split)),
            Overload.binary(SUBSTR, Guards.callInStrIntOutStr(StringsLib::substr)),
            Overload.unary(TRIM_SPACE, Guards.callInStrOutStr(StringsLib::trimSpace)),
            Overload.unary(UPPER_ASCII, Guards.callInStrOutStr(StringsLib::upperASCII)));
    list.add(functions);
    return list;
  }

  static String charAt(String str, int index) {
    if (str.length() == index) {
      return "";
    }
    return String.valueOf(str.charAt(index));
  }

  static int indexOf(String str, String substr) {
    return str.indexOf(substr);
  }

  static int lastIndexOf(String str, String substr) {
    return str.lastIndexOf(substr);
  }

  static String lowerASCII(String str) {
    StringBuilder stringBuilder = new StringBuilder();
    for (char c : str.toCharArray()) {
      if (c >= 'A' && c <= 'Z') {
        stringBuilder.append(Character.toLowerCase(c));
      } else {
        stringBuilder.append(c);
      }
    }
    return stringBuilder.toString();
  }

  static String replace(String str, String old, String replacement) {
    return str.replace(old, replacement);
  }

  static String[] split(String str, String separator) {
    return str.split(Pattern.quote(separator));
  }

  static String substr(String str, int start) {
    return str.substring(start);
  }

  static String trimSpace(String str) {
    char[] chars = str.toCharArray();
    int start = 0;
    int end = str.length() - 1;
    while (start < str.length()) {
      if (!isWhiteSpace(chars[start])) {
        break;
      }
      start++;
    }
    while (end > start) {
      if (!isWhiteSpace(chars[end])) {
        break;
      }
      end--;
    }

    return str.substring(start, end + 1);
  }

  /**
   * test if given character is whitespace as defined by <a
   * href="https://en.wikipedia.org/wiki/Whitespace_character#Unicode">Unicode</a>
   *
   * <p>Java functions like {@link java.lang.Character#isWhitespace(char)} or {@link
   * java.lang.Character#isWhitespace(int)} use different whitespace definition hence they can't be
   * used here.
   *
   * @param ch the character to be tested
   * @return true if the character is a Unicode whitespace character; false otherwise.
   */
  private static boolean isWhiteSpace(char ch) {
    // cel-go 'trim' extension function uses strings.TrimSpace()
    return UNICODE_WHITE_SPACES.contains(ch);
  }

  static String upperASCII(String str) {
    StringBuilder stringBuilder = new StringBuilder();
    for (char c : str.toCharArray()) {
      if (c >= 'a' && c <= 'z') {
        stringBuilder.append(Character.toUpperCase(c));
      } else {
        stringBuilder.append(c);
      }
    }
    return stringBuilder.toString();
  }
}
