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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.common.types.BoolT;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;

public class StringsTest {

  @ParameterizedTest
  @MethodSource("testCases")
  public void testStrings(TestData testData) {
    testExpression(testData);
  }

  static Stream<TestData> testCases() {
    return Stream.of(
        new TestData("'tacocat'.charAt(3) == 'o'"),
        new TestData("'tacocat'.charAt(7) == ''"),
        new TestData("'©αT'.charAt(0) == '©' && '©αT'.charAt(1) == 'α' && '©αT'.charAt(2) == 'T'"),
        new TestData(
            "'tacocat'.charAt(30) == ''",
            "String index out of range: 30",
            false,
            new TreeMap<Integer, String>(Collections.reverseOrder()) {
              {
                put(18, "Index 30 out of bounds for length 7");
              }
            }),
        new TestData("'tacocat'.indexOf('') == 0"),
        new TestData("'tacocat'.indexOf('ac') == 1"),
        new TestData("'tacocat'.indexOf('none') == -1"),
        /*
            new TestData("'tacocat'.indexOf('', 3) == 3"),
            new TestData("'tacocat'.indexOf('a', 3) == 5"),
            new TestData("'tacocat'.indexOf('at', 3) == 5"),
        */
        new TestData("'ta©o©αT'.indexOf('©') == 2"),
        /*
            new TestData("'ta©o©αT'.indexOf('©', 3) == 4"),
            new TestData("'ta©o©αT'.indexOf('©αT', 3) == 4"),
            new TestData("'ta©o©αT'.indexOf('©α', 5) == -1"),
        */
        new TestData("'tacocat'.lastIndexOf('') == 7"),
        new TestData("'tacocat'.lastIndexOf('at') == 5"),
        new TestData("'tacocat'.lastIndexOf('none') == -1"),
        /*
            new TestData("'tacocat'.lastIndexOf('', 3) == 3"),
            new TestData("'tacocat'.lastIndexOf('a', 3) == 1"),
        */
        new TestData("'ta©o©αT'.lastIndexOf('©') == 4"),
        /*
            new TestData("'ta©o©αT'.lastIndexOf('©', 3) == 2"),
            new TestData("'ta©o©αT'.lastIndexOf('©α', 4) == 4"),
        */
        new TestData("'TacoCat'.lowerAscii() == 'tacocat'"),
        new TestData("'TacoCÆt Xii'.lowerAscii() == 'tacocÆt xii'"),
        new TestData("'hello hello'.replace('he', 'we') == 'wello wello'"),
        new TestData("\"12 days 12 hours\".replace(\"{0}\", \"2\") == \"12 days 12 hours\""),
        new TestData("\"{0} days {0} hours\".replace(\"{0}\", \"2\") == \"2 days 2 hours\""),
        /*
            new TestData("\"{0} days {0} hours\".replace(\"{0}\", \"2\", 1).replace(\"{0}\", \"23\") == \"2 days 23 hours\""),
        */
        new TestData("\"1 ©αT taco\".replace(\"αT\", \"o©α\") == \"1 ©o©α taco\""),
        new TestData("'hello hello hello'.split(' ') == ['hello', 'hello', 'hello']"),
        new TestData("\"hello world\".split(\" \") == [\"hello\", \"world\"]"),
        /*
            new TestData("\"hello world events!\".split(\" \", 0) == []"),
            new TestData("\"hello world events!\".split(\" \", 1) == [\"hello world events!\"]"),
            new TestData("\"o©o©o©o\".split(\"©\", -1) == [\"o\", \"o\", \"o\", \"o\"]"),
        */
        new TestData("\"tacocat\".substring(4) == \"cat\""),
        new TestData("\"tacocat\".substring(7) == \"\""),
        /*
            new TestData("\"tacocat\".substring(0, 4) == \"taco\""),
            new TestData("\"tacocat\".substring(4, 4) == \"\""),
            new TestData("'ta©o©αT'.substring(2, 6) == \"©o©α\""),
            new TestData("'ta©o©αT'.substring(7, 7) == \"\""),
        */
        new TestData("'TacoCat'.upperAscii() == 'TACOCAT'"),
        new TestData("'TacoCÆt Xii'.upperAscii() == 'TACOCÆT XII'"),
        new TestData("\" \\f\\n\\r\\t\\vtext  \".trim() == \"text\""),
        new TestData("\"\u0085\u00a0\u1680text\".trim() == \"text\""),
        new TestData(
            "\"text\u2000\u2001\u2002\u2003\u2004\u2004\u2006\u2007\u2008\u2009\".trim() == \"text\""),
        new TestData("\"\u200atext\u2028\u2029\u202F\u205F\u3000\".trim() == \"text\""),
        // Trim test with whitespace-like characters not included.
        new TestData(
            "\"\u180etext\u200b\u200c\u200d\u2060\ufeff\".trim() == \"\u180etext\u200b\u200c\u200d\u2060\ufeff\""),

        // Error test cases based on checked expression usage.
        /*
                new TestData("'tacocat'.indexOf('a', 30) == -1", "String index out of range: 30"),
                new TestData("'tacocat'.lastIndexOf('a', -1) == -1", "String index out of range: -1"),
                new TestData("'tacocat'.lastIndexOf('a', 30) == -1", "String index out of range: 30"),
        */

        new TestData(
            "\"tacocat\".substring(40) == \"cat\"",
            "String index out of range: -33",
            false,
            new TreeMap<Integer, String>(Collections.reverseOrder()) {
              {
                put(14, "begin 40, end 7, length 7");
                put(18, "Range [40, 7) out of bounds for length 7");
              }
            }),
        new TestData(
            "\"tacocat\".substring(-1) == \"cat\"",
            "String index out of range: -1",
            false,
            new TreeMap<Integer, String>(Collections.reverseOrder()) {
              {
                put(14, "begin -1, end 7, length 7");
                put(18, "Range [-1, 7) out of bounds for length 7");
              }
            }),
        /*
                new TestData("\"tacocat\".substring(1, 50) == \"cat\"", "String index out of range: 50"),
                new TestData("\"tacocat\".substring(49, 50) == \"cat\"", "String index out of range: 49"),
                new TestData(
                    "\"tacocat\".substring(4, 3) == \"\"", "invalid substring range. start: 4, end: 3"),
        */

        // Valid parse-only expressions which should generate runtime errors.
        new TestData("42.charAt(2) == \"\"", "no matching overload", true),
        new TestData("'hello'.charAt(true) == \"\"", "no matching overload", true),
        new TestData("24.indexOf('2') == 0", "no matching overload", true),
        new TestData("'hello'.indexOf(true) == 1", "no matching overload", true),
        new TestData("42.indexOf('4', 0) == 0", "no matching overload", true),
        new TestData("'42'.indexOf(4, 0) == 0", "no matching overload", true),
        new TestData("'42'.indexOf('4', '0') == 0", "no matching overload", true),
        new TestData("'42'.indexOf('4', 0, 1) == 0", "no matching overload", true),
        new TestData("24.lastIndexOf('2') == 0", "no matching overload", true),
        new TestData("'hello'.lastIndexOf(true) == 1", "no matching overload", true),
        new TestData("42.lastIndexOf('4', 0) == 0", "no matching overload", true),
        new TestData("'42'.lastIndexOf(4, 0) == 0", "no matching overload", true),
        new TestData("'42'.lastIndexOf('4', '0') == 0", "no matching overload", true),
        new TestData("'42'.lastIndexOf('4', 0, 1) == 0", "no matching overload", true),
        new TestData("42.replace(2, 1) == \"41\"", "no matching overload", true),
        new TestData("\"42\".replace(2, 1) == \"41\"", "no matching overload", true),
        new TestData("\"42\".replace(\"2\", 1) == \"41\"", "no matching overload", true),
        new TestData("42.replace(\"2\", \"1\", 1) == \"41\"", "no matching overload", true),
        new TestData("\"42\".replace(2, \"1\", 1) == \"41\"", "no matching overload", true),
        new TestData("\"42\".replace(\"2\", 1, 1) == \"41\"", "no matching overload", true),
        new TestData("\"42\".replace(\"2\", \"1\", \"1\") == \"41\"", "no matching overload", true),
        new TestData(
            "\"42\".replace(\"2\", \"1\", 1, false) == \"41\"", "no matching overload", true),
        new TestData("42.split(\"2\") == [\"4\"]", "no matching overload", true),
        new TestData("42.split(\"\") == [\"4\", \"2\"]", "no matching overload", true),
        new TestData("\"42\".split(2) == [\"4\"]", "no matching overload", true),
        new TestData("42.split(\"2\", \"1\") == [\"4\"]", "no matching overload", true),
        new TestData("\"42\".split(2, 1) == [\"4\"]", "no matching overload", true),
        new TestData("\"42\".split(\"2\", \"1\") == [\"4\"]", "no matching overload", true),
        new TestData("\"42\".split(\"2\", 1, 1) == [\"4\"]", "no matching overload", true),
        new TestData("'hello'.substring(1, 2, 3) == \"\"", "no matching overload", true),
        new TestData("30.substring(true, 3) == \"\"", "no matching overload", true),
        new TestData("\"tacocat\".substring(true, 3) == \"\"", "no matching overload", true),
        new TestData("\"tacocat\".substring(0, false) == \"\"", "no matching overload", true));
  }

  private static void testExpression(TestData testData) {
    Env env =
        Env.newCustomEnv(
            ProtoTypeRegistry.newRegistry(), Arrays.asList(Library.StdLib(), StringsLib.strings()));

    Env.AstIssuesTuple astIssue = env.parse(testData.expression);
    assertThat(astIssue.hasIssues()).isFalse();

    Env.AstIssuesTuple checked = env.check(astIssue.getAst());
    if (testData.isParseOnly()) {
      assertThat(checked.hasIssues()).isTrue();
      assertThat(checked.getIssues().toString()).contains(testData.getErr());
      return;
    } else {
      assertThat(checked.hasIssues()).isFalse();
    }

    Program program = env.program(astIssue.getAst());
    Program.EvalResult result = program.eval(new HashMap<>());

    if (testData.getErr() != null) {
      assertThat(result.getVal() instanceof Err).isTrue();
      assertThat(result.getVal()).extracting(Val::toString).isEqualTo(testData.getErr());
    } else {
      assertThat(result.getVal()).isEqualTo(BoolT.True);
    }
  }

  private static final class TestData {
    private final String expression;
    private final String err;
    private final boolean parseOnly;
    private final Map<Integer, String> versionedErrs;

    TestData(String expression) {
      this(expression, null);
    }

    TestData(String expression, String err) {
      this(expression, err, false);
    }

    TestData(String expression, String err, boolean parseOnly) {
      this(expression, err, parseOnly, null);
    }

    TestData(String expression, String err, boolean parseOnly, Map<Integer, String> versionedErrs) {
      this.expression = expression;
      this.err = err;
      this.parseOnly = parseOnly;
      this.versionedErrs = versionedErrs;
    }

    public String getExpression() {
      return expression;
    }

    public String getErr() {
      // java 14 has different exception messages. The versionedErrs allow the test case to check on
      // different error messages based on Java version.
      // The `versionedErrs` has java version as key and sorted in descending order. It will allow
      // multiple values based on java version. So far it is limited to major versions only.
      // Since java9, Runtime.version() function can be used to get java version.
      // For java8 support, "java.version" system property is still used to get the java version
      String version = System.getProperty("java.version");
      String[] versions = version.split("\\.");
      int major = Integer.parseInt(versions[0]);
      if (this.versionedErrs != null) {
        for (Map.Entry<Integer, String> entry : this.versionedErrs.entrySet()) {
          if (major >= entry.getKey()) {
            return entry.getValue();
          }
        }
      }
      return err;
    }

    public boolean isParseOnly() {
      return parseOnly;
    }

    @Override
    public String toString() {
      return expression;
    }
  }
}
