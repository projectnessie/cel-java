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

import static java.math.RoundingMode.HALF_EVEN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.projectnessie.cel.common.types.IntT.intOf;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Indexer;
import org.projectnessie.cel.common.types.traits.Sizer;
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
 * <pre>    {@code 'hello'.charAt(4)  // return 'o'}</pre>
 *
 * <pre>    {@code 'hello'.charAt(5)  // return ''}</pre>
 *
 * <pre>    {@code 'hello'.charAt(-1) // error}</pre>
 *
 * <h3>IndexOf</h3>
 *
 * <p>Returns the integer index of the first occurrence of the search string. If the search string
 * is not found the function returns -1.
 *
 * <p>The function also accepts an optional position from which to begin the substring search. If
 * the substring is the empty string, the index where the search starts is returned (zero or
 * custom).
 *
 * <pre>    {@code <string>.indexOf(<string>) -> <int>}</pre>
 *
 * <pre>    {@code <string>.indexOf(<string>, <int>) -> <int>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code 'hello mellow'.indexOf('')         // returns 0}</pre>
 *
 * <pre>    {@code 'hello mellow'.indexOf('ello')     // returns 1}</pre>
 *
 * <pre>    {@code 'hello mellow'.indexOf('jello')    // returns -1}</pre>
 *
 * <pre>    {@code 'hello mellow'.indexOf('', 2)      // returns 2}</pre>
 *
 * <pre>    {@code 'hello mellow'.indexOf('ello', 2)  // returns 7}</pre>
 *
 * <pre>    {@code 'hello mellow'.indexOf('ello', 20) // error}</pre>
 *
 * <h3>Join</h3>
 *
 * <p>Returns a new string where the elements of string list are concatenated.
 *
 * <p>The function also accepts an optional separator which is placed between elements in the
 * resulting string.
 *
 * <pre>    {@code <list<string>>.join() -> <string>}</pre>
 *
 * <pre>    {@code <list<string>>.join(<string>) -> <string>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code ['hello', 'mellow'].join()    // returns 'hellomellow'}</pre>
 *
 * <pre>    {@code ['hello', 'mellow'].join(' ') // returns 'hello mellow'}</pre>
 *
 * <pre>    {@code [].join()                     // returns ''}</pre>
 *
 * <pre>    {@code [].join('/')                  // returns ''}</pre>
 *
 * <h3>LastIndexOf</h3>
 *
 * <p>Returns the integer index at the start of the last occurrence of the search string. If the
 * search string is not found the function returns -1.
 *
 * <p>The function also accepts an optional position which represents the last index to be
 * considered as the beginning of the substring match. If the substring is the empty string, the
 * index where the search starts is returned (string length or custom).
 *
 * <pre>    {@code <string>.lastIndexOf(<string>) -> <int>}</pre>
 *
 * <pre>    {@code <string>.lastIndexOf(<string>, <int>) -> <int>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code 'hello mellow'.lastIndexOf('')         // returns 12}</pre>
 *
 * <pre>    {@code 'hello mellow'.lastIndexOf('ello')     // returns 7}</pre>
 *
 * <pre>    {@code 'hello mellow'.lastIndexOf('jello')    // returns -1}</pre>
 *
 * <pre>    {@code 'hello mellow'.lastIndexOf('ello', 6)  // returns 1}</pre>
 *
 * <pre>    {@code 'hello mellow'.lastIndexOf('ello', -1) // error}</pre>
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
 * <pre>    {@code 'TacoCat'.lowerAscii()     // returns 'tacocat'}</pre>
 *
 * <pre>    {@code 'TacoCÆt Xii'.lowerAscii() // returns 'tacocÆt xii'}</pre>
 *
 * <h3>Replace</h3>
 *
 * <p>Returns a new string based on the target, which replaces the occurrences of a search string
 * with a replacement string if present. The function accepts an optional limit on the number of
 * substring replacements to be made.
 *
 * <p>When the replacement limit is 0, the result is the original string. When the limit is a
 * negative number, the function behaves the same as replace all.
 *
 * <pre>    {@code <string>.replace(<string>, <string>) -> <string>}</pre>
 *
 * <pre>    {@code <string>.replace(<string>, <string>, <int>) -> <string>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code 'hello hello'.replace('he', 'we')     // returns 'wello wello'}</pre>
 *
 * <pre>    {@code 'hello hello'.replace('he', 'we', -1) // returns 'wello wello'}</pre>
 *
 * <pre>    {@code 'hello hello'.replace('he', 'we', 1)  // returns 'wello hello'}</pre>
 *
 * <pre>    {@code 'hello hello'.replace('he', 'we', 0)  // returns 'hello hello'}</pre>
 *
 * <h3>Split</h3>
 *
 * <p>Returns a list of strings split from the input by the given separator. The function accepts an
 * optional argument specifying a limit on the number of substrings produced by the split.
 *
 * <p>When the split limit is 0, the result is an empty list. When the limit is 1, the result is the
 * target string to split. When the limit is a negative number, the function behaves the same as
 * split all.
 *
 * <pre>    {@code <string>.split(<string>) -> <list<string>>}</pre>
 *
 * <pre>    {@code <string>.split(<string>, <int>) -> <list<string>>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code 'hello hello hello'.split(' ')     // returns ['hello', 'hello', 'hello']}</pre>
 *
 * <pre>    {@code 'hello hello hello'.split(' ', 0)  // returns []}</pre>
 *
 * <pre>    {@code 'hello hello hello'.split(' ', 1)  // returns ['hello hello hello']}</pre>
 *
 * <pre>    {@code 'hello hello hello'.split(' ', 2)  // returns ['hello', 'hello hello']}</pre>
 *
 * <pre>    {@code 'hello hello hello'.split(' ', -1) // returns ['hello', 'hello', 'hello']}</pre>
 *
 * <h3>Substring</h3>
 *
 * <p>Returns the substring given a numeric range corresponding to character positions. Optionally
 * may omit the trailing range for a substring from a given character position until the end of a
 * string.
 *
 * <p>Character offsets are 0-based with an inclusive start range. It is an error to specify an end
 * range that is lower than the start range, or for either the start or end index to be negative or
 * exceed the string length.
 *
 * <pre>    {@code <string>.substring(<int>) -> <string>}</pre>
 *
 * <pre>    {@code <string>.substring(<int>,<int>)-><string>}</pre>
 *
 * <h4>Examples:</h4>
 *
 * <pre>    {@code 'tacocat'.substring(4)    // returns 'cat'}</pre>
 *
 * <pre>    {@code 'tacocat'.substring(-1)   // error}</pre>
 *
 * <pre>    {@code 'tacocat'.substring(0,4)  // returns 'taco'}</pre>
 *
 * <pre>    {@code 'tacocat'.substring(2, 1) // error}</pre>
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
 * <pre>    {@code 'TacoCat'.upperAscii()     // returns 'TACOCAT'}</pre>
 *
 * <pre>    {@code 'TacoCÆt Xii'.upperAscii() // returns 'TACOCÆT XII'}</pre>
 */
public class StringsLib implements Library {

  private static final String CHAR_AT = "charAt";
  private static final String INDEX_OF = "indexOf";
  private static final String JOIN = "join";
  private static final String LAST_INDEX_OF = "lastIndexOf";
  private static final String LOWER_ASCII = "lowerAscii";
  private static final String REPLACE = "replace";
  private static final String REVERSE = "reverse";
  private static final String SPLIT = "split";
  private static final String SUBSTR = "substring";
  private static final String TRIM_SPACE = "trim";
  private static final String UPPER_ASCII = "upperAscii";
  private static final String FORMAT = "format";
  private static final String QUOTE = "strings.quote";

  // whitespace characters definition from
  // https://en.wikipedia.org/wiki/Whitespace_character#Unicode
  private static final Set<Character> UNICODE_WHITE_SPACES =
      Set.of(
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
          (char) 0x3000);

  public static EnvOption strings() {
    return Library.Lib(new StringsLib());
  }

  @Override
  public List<EnvOption> getCompileOptions() {
    EnvOption option =
        EnvOption.declarations(
            Decls.newFunction(
                CHAR_AT,
                Decls.newInstanceOverload(
                    "string_char_at_int", List.of(Decls.String, Decls.Int), Decls.String)),
            Decls.newFunction(
                INDEX_OF,
                Decls.newInstanceOverload(
                    "string_index_of_string", List.of(Decls.String, Decls.String), Decls.Int),
                Decls.newInstanceOverload(
                    "string_index_of_string_int",
                    List.of(Decls.String, Decls.String, Decls.Int),
                    Decls.Int)),
            Decls.newFunction(
                JOIN,
                Decls.newInstanceOverload(
                    "list_join", List.of(Decls.newListType(Decls.String)), Decls.String),
                Decls.newInstanceOverload(
                    "list_join_string",
                    List.of(Decls.newListType(Decls.String), Decls.String),
                    Decls.String)),
            Decls.newFunction(
                LAST_INDEX_OF,
                Decls.newInstanceOverload(
                    "string_last_index_of_string", List.of(Decls.String, Decls.String), Decls.Int),
                Decls.newInstanceOverload(
                    "string_last_index_of_string_int",
                    List.of(Decls.String, Decls.String, Decls.Int),
                    Decls.Int)),
            Decls.newFunction(
                LOWER_ASCII,
                Decls.newInstanceOverload(
                    "string_lower_ascii", List.of(Decls.String), Decls.String)),
            Decls.newFunction(
                REPLACE,
                Decls.newInstanceOverload(
                    "string_replace_string_string",
                    List.of(Decls.String, Decls.String, Decls.String),
                    Decls.String),
                Decls.newInstanceOverload(
                    "string_replace_string_string_int",
                    List.of(Decls.String, Decls.String, Decls.String, Decls.Int),
                    Decls.String)),
            Decls.newFunction(
                REVERSE,
                Decls.newInstanceOverload("string_reverse", List.of(Decls.String), Decls.String)),
            Decls.newFunction(
                SPLIT,
                Decls.newInstanceOverload(
                    "string_split_string", List.of(Decls.String, Decls.String), Decls.Dyn),
                Decls.newInstanceOverload(
                    "string_split_string_int",
                    List.of(Decls.String, Decls.String, Decls.Int),
                    Decls.Dyn)),
            Decls.newFunction(
                SUBSTR,
                Decls.newInstanceOverload(
                    "string_substring_int", List.of(Decls.String, Decls.Int), Decls.String),
                Decls.newInstanceOverload(
                    "string_substring_int_int",
                    List.of(Decls.String, Decls.Int, Decls.Int),
                    Decls.String)),
            Decls.newFunction(
                TRIM_SPACE,
                Decls.newInstanceOverload("string_trim", List.of(Decls.String), Decls.String)),
            Decls.newFunction(
                UPPER_ASCII,
                Decls.newInstanceOverload(
                    "string_upper_ascii", List.of(Decls.String), Decls.String)),
            Decls.newFunction(
                FORMAT,
                Decls.newInstanceOverload(
                    "string_format",
                    List.of(Decls.String, Decls.newListType(Decls.Dyn)),
                    Decls.String)),
            Decls.newFunction(
                QUOTE, Decls.newOverload("strings_quote", List.of(Decls.String), Decls.String)));
    return List.of(option);
  }

  @Override
  public List<ProgramOption> getProgramOptions() {
    ProgramOption functions =
        ProgramOption.functions(
            Overload.binary(CHAR_AT, Guards.callInStrIntOutStr(StringsLib::charAt)),
            Overload.overload(
                INDEX_OF,
                null,
                null,
                Guards.callInStrStrOutInt(StringsLib::indexOf),
                Guards.callInStrStrIntOutIntTernary(StringsLib::indexOfOffset),
                values ->
                    values.length == 3
                        ? Guards.callInStrStrIntOutInt(StringsLib::indexOfOffset).invoke(values)
                        : Err.maybeNoSuchOverloadErr(null)),
            Overload.overload(
                JOIN,
                null,
                Guards.callInStrArrayOutStr(StringsLib::join),
                Guards.callInStrArrayStrOutStr(StringsLib::joinSepartor),
                null),
            Overload.overload(
                LAST_INDEX_OF,
                null,
                null,
                Guards.callInStrStrOutInt(StringsLib::lastIndexOf),
                Guards.callInStrStrIntOutIntTernary(StringsLib::lastIndexOfOffset),
                values ->
                    values.length == 3
                        ? Guards.callInStrStrIntOutInt(StringsLib::lastIndexOfOffset).invoke(values)
                        : Err.maybeNoSuchOverloadErr(null)),
            Overload.unary(LOWER_ASCII, Guards.callInStrOutStr(StringsLib::lowerASCII)),
            Overload.overload(
                REPLACE,
                null,
                null,
                null,
                Guards.callInStrStrStrOutStrTernary(StringsLib::replace),
                Guards.callInStrStrStrIntOutStrQuaternary(StringsLib::replaceN),
                values -> {
                  if (values.length == 3) {
                    return Guards.callInStrStrStrOutStr(StringsLib::replace).invoke(values);
                  }
                  if (values.length == 4) {
                    return Guards.callInStrStrStrIntOutStr(StringsLib::replaceN).invoke(values);
                  }
                  return Err.maybeNoSuchOverloadErr(null);
                }),
            Overload.unary(REVERSE, Guards.callInStrOutStr(StringsLib::reverse)),
            Overload.overload(
                SPLIT,
                null,
                null,
                Guards.callInStrStrOutStrArr(StringsLib::split),
                Guards.callInStrStrIntOutStrArrTernary(StringsLib::splitN),
                values ->
                    values.length == 3
                        ? Guards.callInStrStrIntOutStrArr(StringsLib::splitN).invoke(values)
                        : Err.maybeNoSuchOverloadErr(null)),
            Overload.overload(
                SUBSTR,
                null,
                null,
                Guards.callInStrIntOutStr(StringsLib::substr),
                Guards.callInStrIntIntOutStrTernary(StringsLib::substrRange),
                values ->
                    values.length == 3
                        ? Guards.callInStrIntIntOutStr(StringsLib::substrRange).invoke(values)
                        : Err.maybeNoSuchOverloadErr(null)),
            Overload.unary(TRIM_SPACE, Guards.callInStrOutStr(StringsLib::trimSpace)),
            Overload.unary(UPPER_ASCII, Guards.callInStrOutStr(StringsLib::upperASCII)),
            Overload.binary(FORMAT, StringsLib::format),
            Overload.unary(QUOTE, Guards.callInStrOutStr(StringsLib::quote)));
    return List.of(functions);
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

  static int indexOfOffset(String str, String substr, int offset) {
    if (offset < 0 || offset > str.length()) {
      throw new IndexOutOfBoundsException("String index out of range: " + offset);
    }
    return str.indexOf(substr, offset);
  }

  static String join(String[] strs) {
    StringBuilder stringBuilder = new StringBuilder();
    Arrays.stream(strs).forEach(stringBuilder::append);
    return stringBuilder.toString();
  }

  static String joinSepartor(String[] strs, String seperator) {
    return String.join(seperator, strs);
  }

  static int lastIndexOf(String str, String substr) {
    return str.lastIndexOf(substr);
  }

  static int lastIndexOfOffset(String str, String substr, int offset) {
    if (offset < 0 || offset > str.length()) {
      throw new IndexOutOfBoundsException("String index out of range: " + offset);
    }
    return str.lastIndexOf(substr, offset);
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

  static String reverse(String str) {
    return new StringBuilder(str).reverse().toString();
  }

  static String quote(String str) {
    StringBuilder quoted = new StringBuilder(str.length() + 2);
    quoted.append('"');
    for (int offset = 0; offset < str.length(); ) {
      int codePoint = str.codePointAt(offset);
      offset += Character.charCount(codePoint);
      switch (codePoint) {
        case '\u0007':
          quoted.append("\\a");
          break;
        case '\b':
          quoted.append("\\b");
          break;
        case '\f':
          quoted.append("\\f");
          break;
        case '\n':
          quoted.append("\\n");
          break;
        case '\r':
          quoted.append("\\r");
          break;
        case '\t':
          quoted.append("\\t");
          break;
        case '\u000b':
          quoted.append("\\v");
          break;
        case '\\':
          quoted.append("\\\\");
          break;
        case '"':
          quoted.append("\\\"");
          break;
        default:
          quoted.appendCodePoint(codePoint);
      }
    }
    return quoted.append('"').toString();
  }

  /**
   * replace first n non-overlapping instance of {old} replaced by {replacement}. It works as <a
   * ref="https://pkg.go.dev/strings#Replace">strings.Replace in Go</a> to have consistent behavior
   * as cel in Go
   *
   * <p>if {@code n == 0}, there is no change to the string
   *
   * <p>if {@code n < 0}, there is no limit on the number of replacement
   *
   * <p>if {old} is empty, it matches at the beginning of the string and after each UTF-8 sequence,
   * yielding up to k+1 replacements for a k-rune string
   */
  static String replaceN(String str, String old, String replacement, int n) {
    if (n == 0 || old.equals(replacement)) {
      return str;
    }
    if (n < 0) {
      return str.replace(old, replacement);
    }
    StringBuilder stringBuilder = new StringBuilder();
    int index = 0;
    int count = 0;

    for (; count < n && index < str.length(); count++) {
      if (old.isEmpty()) {
        stringBuilder.append(replacement).append(str, index, index + 1);
        index++;
      } else {
        int found = str.indexOf(old, index);
        if (found == -1) {
          // not found, append to the end
          stringBuilder.append(str, index, str.length());
          return stringBuilder.toString();
        }
        if (found > index) {
          stringBuilder.append(str, index, found);
        }
        stringBuilder.append(replacement);
        index = found + old.length();
      }
    }
    if (index < str.length()) {
      stringBuilder.append(str, index, str.length());
    }
    return stringBuilder.toString();
  }

  static String[] split(String str, String separator) {
    return str.split(Pattern.quote(separator));
  }

  /**
   * SplitN slices s into substrings separated by sep and returns an array of the substrings between
   * those separators. The count determines the number of substrings to return:
   *
   * <p>If {@code n > 0}, at most n substrings; the last substring will be the unsplit remainder.
   *
   * <p>If {@code n == 0}, the result is empty array
   *
   * <p>If {@code n < 0}, all substrings
   *
   * <p>If sep is empty, splits after each UTF-8 sequence.
   *
   * <p>If both s and sep are empty, Split returns an empty array.
   */
  static String[] splitN(String s, String sep, int n) {
    if (n < 0) {
      return split(s, sep);
    }
    if (n == 0) {
      return new String[0];
    }
    if (n == 1) {
      return new String[] {s};
    }
    if (sep.isEmpty()) {
      return explode(s, n);
    }

    int index = 0;
    int count = 0;
    List<String> list = new ArrayList<>();
    for (; index < s.length() && count < n - 1; count++) {
      int found = s.indexOf(sep, index);
      if (found < 0) {
        break;
      }
      list.add(s.substring(index, found));
      index = found + sep.length();
    }
    if (index <= s.length()) {
      list.add(s.substring(index));
    }

    return list.toArray(new String[0]);
  }

  /**
   * explode splits s into an array of UTF-8 strings, one string per Unicode character up to a
   * maximum of n (n < 0 means no limit).
   *
   * <p>ported from <a href="https://github.com/golang/go/blob/master/src/strings/strings.go">Go:
   * strings.explode()</a>
   */
  private static String[] explode(String s, int n) {
    if (n < 0 || n > s.length()) {
      n = s.length();
    }

    String[] arr = new String[n];
    for (int i = 0; i < n - 1; i++) {
      arr[i] = s.substring(i, i + 1);
    }
    if (n > 0) {
      arr[n - 1] = s.substring(n - 1);
    }
    return arr;
  }

  static String substr(String str, int start) {
    return str.substring(start);
  }

  static String substrRange(String str, int start, int end) {
    if (start < 0 || start > str.length()) {
      throw new IndexOutOfBoundsException("String index out of range: " + start);
    }
    if (end < 0 || end > str.length()) {
      throw new IndexOutOfBoundsException("String index out of range: " + end);
    }
    if (start > end) {
      throw new IndexOutOfBoundsException(
          String.format("invalid substring range. start: %d, end: %d", start, end));
    }
    return str.substring(start, end);
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

  private static Val format(Val pattern, Val args) {
    if (!(pattern instanceof StringT) || !(args instanceof Sizer) || !(args instanceof Indexer)) {
      return Err.maybeNoSuchOverloadErr(null);
    }
    try {
      return StringT.stringOf(
          formatPattern((String) pattern.value(), (Sizer) args, (Indexer) args));
    } catch (FormatException e) {
      return Err.newErr("%s", e.getMessage());
    }
  }

  private static String formatPattern(String pattern, Sizer argsSizer, Indexer argsIndexer) {
    int argCount = Math.toIntExact(argsSizer.size().intValue());
    int argIndex = 0;
    StringBuilder out = new StringBuilder(pattern.length());
    for (int i = 0; i < pattern.length(); i++) {
      char ch = pattern.charAt(i);
      if (ch != '%') {
        out.append(ch);
        continue;
      }
      if (++i >= pattern.length()) {
        throw new FormatException("could not parse formatting clause: missing formatting clause");
      }
      ch = pattern.charAt(i);
      if (ch == '%') {
        out.append('%');
        continue;
      }
      int precision = -1;
      if (ch == '.') {
        int precisionStart = ++i;
        while (i < pattern.length() && Character.isDigit(pattern.charAt(i))) {
          i++;
        }
        if (precisionStart == i || i >= pattern.length()) {
          throw new FormatException("could not parse formatting clause: malformed precision");
        }
        precision = Integer.parseInt(pattern.substring(precisionStart, i));
        ch = pattern.charAt(i);
      }
      if ("sdboxXfe".indexOf(ch) < 0) {
        throw new FormatException(
            "could not parse formatting clause: unrecognized formatting clause \"%s\"", ch);
      }
      if (argIndex >= argCount) {
        throw new FormatException("index %d out of range", argIndex);
      }
      Val arg = argsIndexer.get(intOf(argIndex++));
      out.append(formatValue(ch, precision, arg));
    }
    return out.toString();
  }

  private static String formatValue(char clause, int precision, Val arg) {
    return switch (clause) {
      case 's' -> renderStringClause(arg);
      case 'd' -> renderDecimalClause(arg);
      case 'b' -> renderBinaryClause(arg);
      case 'o' -> renderOctalClause(arg);
      case 'x', 'X' -> renderHexClause(arg, clause == 'X');
      case 'f' -> renderFixedPointClause(arg, precision >= 0 ? precision : 6);
      case 'e' -> renderScientificClause(arg, precision >= 0 ? precision : 6);
      default ->
          throw new FormatException(
              "could not parse formatting clause: unrecognized formatting clause \"%s\"", clause);
    };
  }

  private static String renderStringClause(Val value) {
    return switch (value.type().typeEnum()) {
      case String -> value.value().toString();
      case Bool -> Boolean.toString(value.booleanValue());
      case Bytes ->
          new String(DefaultTypeAdapter.Instance.valueToNative(value, byte[].class), UTF_8);
      case Int -> Long.toString(value.intValue());
      case Uint -> Long.toUnsignedString(value.intValue());
      case Double -> renderDouble(value.doubleValue());
      case Null -> "null";
      case Type, Duration, Timestamp -> value.convertToType(StringT.StringType).value().toString();
      case List -> renderList(value);
      case Map -> renderMap(value);
      default ->
          throw new FormatException(
              "error during formatting: string clause can only be used on strings, bools, bytes, ints, doubles, maps, lists, types, durations, and timestamps, was given %s",
              value.type().typeName());
    };
  }

  private static String renderDecimalClause(Val value) {
    switch (value.type().typeEnum()) {
      case Int:
        return Long.toString(value.intValue());
      case Uint:
        return Long.toUnsignedString(value.intValue());
      case Double:
        double d = value.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
          return renderDouble(d);
        }
        break;
      default:
    }
    throw new FormatException(
        "error during formatting: decimal clause can only be used on integers, was given %s",
        value.type().typeName());
  }

  private static String renderBinaryClause(Val value) {
    return switch (value.type().typeEnum()) {
      case Int -> Long.toBinaryString(value.intValue());
      case Uint -> Long.toUnsignedString(value.intValue(), 2);
      case Bool -> value.booleanValue() ? "1" : "0";
      default ->
          throw new FormatException(
              "error during formatting: only integers and bools can be formatted as binary, was given %s",
              value.type().typeName());
    };
  }

  private static String renderOctalClause(Val value) {
    return switch (value.type().typeEnum()) {
      case Int -> Long.toOctalString(value.intValue());
      case Uint -> Long.toUnsignedString(value.intValue(), 8);
      default ->
          throw new FormatException(
              "error during formatting: octal clause can only be used on integers, was given %s",
              value.type().typeName());
    };
  }

  private static String renderHexClause(Val value, boolean upperCase) {
    String hex =
        switch (value.type().typeEnum()) {
          case Int -> Long.toHexString(value.intValue());
          case Uint -> Long.toUnsignedString(value.intValue(), 16);
          case String -> bytesToHex(value.value().toString().getBytes(UTF_8));
          case Bytes -> bytesToHex(DefaultTypeAdapter.Instance.valueToNative(value, byte[].class));
          default ->
              throw new FormatException(
                  "error during formatting: only integers, byte buffers, and strings can be formatted as hex, was given %s",
                  value.type().typeName());
        };
    return upperCase ? hex.toUpperCase(Locale.ROOT) : hex;
  }

  private static String renderFixedPointClause(Val value, int precision) {
    switch (value.type().typeEnum()) {
      case Int:
      case Uint:
      case Double:
        double d = value.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
          return renderDouble(d);
        }
        return BigDecimal.valueOf(d).setScale(precision, HALF_EVEN).toPlainString();
      default:
        throw new FormatException(
            "error during formatting: fixed-point clause can only be used on doubles, was given %s",
            value.type().typeName());
    }
  }

  private static String renderScientificClause(Val value, int precision) {
    switch (value.type().typeEnum()) {
      case Int:
      case Uint:
      case Double:
        double d = value.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
          return renderDouble(d);
        }
        return String.format(Locale.ROOT, "%." + precision + "e", d);
      default:
        throw new FormatException(
            "error during formatting: scientific clause can only be used on doubles, was given %s",
            value.type().typeName());
    }
  }

  private static String renderList(Val value) {
    Sizer sizer = (Sizer) value;
    Indexer indexer = (Indexer) value;
    int size = Math.toIntExact(sizer.size().intValue());
    StringBuilder out = new StringBuilder("[");
    for (int i = 0; i < size; i++) {
      if (i > 0) {
        out.append(", ");
      }
      out.append(renderStringClause(indexer.get(intOf(i))));
    }
    return out.append(']').toString();
  }

  private static String renderMap(Val value) {
    org.projectnessie.cel.common.types.IteratorT iterator =
        ((org.projectnessie.cel.common.types.IterableT) value).iterator();
    Indexer indexer = (Indexer) value;
    List<String> entries = new ArrayList<>();
    while (iterator.hasNext().booleanValue()) {
      Val key = iterator.next();
      entries.add(renderStringClause(key) + ": " + renderStringClause(indexer.get(key)));
    }
    Collections.sort(entries);
    return "{" + String.join(", ", entries) + "}";
  }

  private static String renderDouble(double value) {
    if (Double.isNaN(value)) {
      return "NaN";
    }
    if (value == Double.POSITIVE_INFINITY) {
      return "Infinity";
    }
    if (value == Double.NEGATIVE_INFINITY) {
      return "-Infinity";
    }
    return Double.toString(value);
  }

  private static String bytesToHex(byte[] bytes) {
    char[] hex = new char[bytes.length * 2];
    char[] digits = "0123456789abcdef".toCharArray();
    for (int i = 0; i < bytes.length; i++) {
      int b = bytes[i] & 0xff;
      hex[i * 2] = digits[b >>> 4];
      hex[i * 2 + 1] = digits[b & 0x0f];
    }
    return new String(hex);
  }

  private static final class FormatException extends RuntimeException {
    FormatException(String message, Object... args) {
      super(String.format(Locale.ROOT, message, args));
    }
  }
}
