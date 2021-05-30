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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.cel.common.Error;
import org.projectnessie.cel.common.Location;
import org.projectnessie.cel.common.Source;
import org.projectnessie.cel.common.debug.Debug;
import org.projectnessie.cel.parser.Parser.ParseResult;
import org.projectnessie.cel.pb.Expr;
import org.projectnessie.cel.pb.Expr.Const;
import org.projectnessie.cel.pb.Expr.StructExpr;
import org.projectnessie.cel.pb.SourceInfo;

class TestParser {
  @SuppressWarnings("unused")
  public static String[][] testCases() {
    return new String[][] {
      new String[] {
        "0", "\"A\"", "\"A\"^#1:*expr.Constant_StringValue#", "", "",
      },
      new String[] {
        "1", "true", "true^#1:*expr.Constant_BoolValue#", "", "",
      },
      new String[] {
        "2", "false", "false^#1:*expr.Constant_BoolValue#", "", "",
      },
      new String[] {
        "3", "0", "0^#1:*expr.Constant_Int64Value#", "", "",
      },
      new String[] {
        "4", "42", "42^#1:*expr.Constant_Int64Value#", "", "",
      },
      new String[] {
        "5", "0u", "0u^#1:*expr.Constant_Uint64Value#", "", "",
      },
      new String[] {
        "6", "23u", "23u^#1:*expr.Constant_Uint64Value#", "", "",
      },
      new String[] {
        "7", "24u", "24u^#1:*expr.Constant_Uint64Value#", "", "",
      },
      new String[] {
        "8", "-1", "-1^#1:*expr.Constant_Int64Value#", "", "",
      },
      new String[] {
        "9",
        "4--4",
        "_-_(\n"
            + "  4^#1:*expr.Constant_Int64Value#,\n"
            + "  -4^#3:*expr.Constant_Int64Value#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "10",
        "4--4.1",
        "_-_(\n"
            + "  4^#1:*expr.Constant_Int64Value#,\n"
            + "  -4.1^#3:*expr.Constant_DoubleValue#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "11", "b\"abc\"", "b\"abc\"^#1:*expr.Constant_BytesValue#", "", "",
      },
      new String[] {
        "12", "23.39", "23.39^#1:*expr.Constant_DoubleValue#", "", "",
      },
      new String[] {
        "13",
        "!a",
        "!_(\n" + "  a^#2:*expr.Expr_IdentExpr#\n" + ")^#1:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "14", "null", "null^#1:*expr.Constant_NullValue#", "", "",
      },
      new String[] {
        "15", "a", "a^#1:*expr.Expr_IdentExpr#", "", "",
      },
      new String[] {
        "16",
        "a?b:c",
        "_?_:_(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#,\n"
            + "  c^#4:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "17",
        "a || b",
        "_||_(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#2:*expr.Expr_IdentExpr#\n"
            + ")^#3:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "18",
        "a || b || c || d || e || f ",
        "_||_(\n"
            + "  _||_(\n"
            + "    _||_(\n"
            + "      a^#1:*expr.Expr_IdentExpr#,\n"
            + "      b^#2:*expr.Expr_IdentExpr#\n"
            + "    )^#3:*expr.Expr_CallExpr#,\n"
            + "    c^#4:*expr.Expr_IdentExpr#\n"
            + "  )^#5:*expr.Expr_CallExpr#,\n"
            + "  _||_(\n"
            + "    _||_(\n"
            + "      d^#6:*expr.Expr_IdentExpr#,\n"
            + "      e^#8:*expr.Expr_IdentExpr#\n"
            + "    )^#9:*expr.Expr_CallExpr#,\n"
            + "    f^#10:*expr.Expr_IdentExpr#\n"
            + "  )^#11:*expr.Expr_CallExpr#\n"
            + ")^#7:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "19",
        "a && b",
        "_&&_(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#2:*expr.Expr_IdentExpr#\n"
            + ")^#3:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "20",
        "a && b && c && d && e && f && g",
        "_&&_(\n"
            + "  _&&_(\n"
            + "    _&&_(\n"
            + "      a^#1:*expr.Expr_IdentExpr#,\n"
            + "      b^#2:*expr.Expr_IdentExpr#\n"
            + "    )^#3:*expr.Expr_CallExpr#,\n"
            + "    _&&_(\n"
            + "      c^#4:*expr.Expr_IdentExpr#,\n"
            + "      d^#6:*expr.Expr_IdentExpr#\n"
            + "    )^#7:*expr.Expr_CallExpr#\n"
            + "  )^#5:*expr.Expr_CallExpr#,\n"
            + "  _&&_(\n"
            + "    _&&_(\n"
            + "      e^#8:*expr.Expr_IdentExpr#,\n"
            + "      f^#10:*expr.Expr_IdentExpr#\n"
            + "    )^#11:*expr.Expr_CallExpr#,\n"
            + "    g^#12:*expr.Expr_IdentExpr#\n"
            + "  )^#13:*expr.Expr_CallExpr#\n"
            + ")^#9:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "21",
        "a && b && c && d || e && f && g && h",
        "_||_(\n"
            + "  _&&_(\n"
            + "    _&&_(\n"
            + "      a^#1:*expr.Expr_IdentExpr#,\n"
            + "      b^#2:*expr.Expr_IdentExpr#\n"
            + "    )^#3:*expr.Expr_CallExpr#,\n"
            + "    _&&_(\n"
            + "      c^#4:*expr.Expr_IdentExpr#,\n"
            + "      d^#6:*expr.Expr_IdentExpr#\n"
            + "    )^#7:*expr.Expr_CallExpr#\n"
            + "  )^#5:*expr.Expr_CallExpr#,\n"
            + "  _&&_(\n"
            + "    _&&_(\n"
            + "      e^#8:*expr.Expr_IdentExpr#,\n"
            + "      f^#9:*expr.Expr_IdentExpr#\n"
            + "    )^#10:*expr.Expr_CallExpr#,\n"
            + "    _&&_(\n"
            + "      g^#11:*expr.Expr_IdentExpr#,\n"
            + "      h^#13:*expr.Expr_IdentExpr#\n"
            + "    )^#14:*expr.Expr_CallExpr#\n"
            + "  )^#12:*expr.Expr_CallExpr#\n"
            + ")^#15:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "22",
        "a + b",
        "_+_(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "23",
        "a - b",
        "_-_(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "24",
        "a * b",
        "_*_(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "25",
        "a / b",
        "_/_(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "26",
        "a % b",
        "_%_(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "27",
        "a in b",
        "@in(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "28",
        "a == b",
        "_==_(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "29",
        "a != b",
        "_!=_(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "30",
        "a > b",
        "_>_(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "31",
        "a >= b",
        "_>=_(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "32",
        "a < b",
        "_<_(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "33",
        "a <= b",
        "_<=_(\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "34", "a.b", "a^#1:*expr.Expr_IdentExpr#.b^#2:*expr.Expr_SelectExpr#", "", "",
      },
      new String[] {
        "35",
        "a.b.c",
        "a^#1:*expr.Expr_IdentExpr#.b^#2:*expr.Expr_SelectExpr#.c^#3:*expr.Expr_SelectExpr#",
        "",
        "",
      },
      new String[] {
        "36",
        "a[b]",
        "_[_](\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "37", "foo{ }", "foo{}^#2:*expr.Expr_StructExpr#", "", "",
      },
      new String[] {
        "38",
        "foo{ a:b }",
        "foo{\n"
            + "  a:b^#4:*expr.Expr_IdentExpr#^#3:*expr.Expr_CreateStruct_Entry#\n"
            + "}^#2:*expr.Expr_StructExpr#",
        "",
        "",
      },
      new String[] {
        "39",
        "foo{ a:b, c:d }",
        "foo{\n"
            + "  a:b^#4:*expr.Expr_IdentExpr#^#3:*expr.Expr_CreateStruct_Entry#,\n"
            + "  c:d^#6:*expr.Expr_IdentExpr#^#5:*expr.Expr_CreateStruct_Entry#\n"
            + "}^#2:*expr.Expr_StructExpr#",
        "",
        "",
      },
      new String[] {
        "40", "{}", "{}^#1:*expr.Expr_StructExpr#", "", "",
      },
      new String[] {
        "41",
        "{a:b, c:d}",
        "{\n"
            + "  a^#3:*expr.Expr_IdentExpr#:b^#4:*expr.Expr_IdentExpr#^#2:*expr.Expr_CreateStruct_Entry#,\n"
            + "  c^#6:*expr.Expr_IdentExpr#:d^#7:*expr.Expr_IdentExpr#^#5:*expr.Expr_CreateStruct_Entry#\n"
            + "}^#1:*expr.Expr_StructExpr#",
        "",
        "",
      },
      new String[] {
        "42", "[]", "[]^#1:*expr.Expr_ListExpr#", "", "",
      },
      new String[] {
        "43", "[a]", "[\n" + "  a^#2:*expr.Expr_IdentExpr#\n" + "]^#1:*expr.Expr_ListExpr#", "", "",
      },
      new String[] {
        "44",
        "[a, b, c]",
        "[\n"
            + "  a^#2:*expr.Expr_IdentExpr#,\n"
            + "  b^#3:*expr.Expr_IdentExpr#,\n"
            + "  c^#4:*expr.Expr_IdentExpr#\n"
            + "]^#1:*expr.Expr_ListExpr#",
        "",
        "",
      },
      new String[] {
        "45", "(a)", "a^#1:*expr.Expr_IdentExpr#", "", "",
      },
      new String[] {
        "46", "((a))", "a^#1:*expr.Expr_IdentExpr#", "", "",
      },
      new String[] {
        "47", "a()", "a()^#1:*expr.Expr_CallExpr#", "", "",
      },
      new String[] {
        "48",
        "a(b)",
        "a(\n" + "  b^#2:*expr.Expr_IdentExpr#\n" + ")^#1:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "49",
        "a(b, c)",
        "a(\n"
            + "  b^#2:*expr.Expr_IdentExpr#,\n"
            + "  c^#3:*expr.Expr_IdentExpr#\n"
            + ")^#1:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "50", "a.b()", "a^#1:*expr.Expr_IdentExpr#.b()^#2:*expr.Expr_CallExpr#", "", "",
      },
      new String[] {
        "51",
        "a.b(c)",
        "a^#1:*expr.Expr_IdentExpr#.b(\n"
            + "  c^#3:*expr.Expr_IdentExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "a^#1[1,0]#.b(\n" + "  c^#3[1,4]#\n" + ")^#2[1,3]#",
      },
      new String[] {
        "52",
        "*@a | b",
        "",
        "ERROR: <input>:1:1: Syntax error: extraneous input '*' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
            + " | *@a | b\n"
            + " | ^\n"
            + "ERROR: <input>:1:2: Syntax error: token recognition error at: '@'\n"
            + " | *@a | b\n"
            + " | .^\n"
            + "ERROR: <input>:1:5: Syntax error: token recognition error at: '| '\n"
            + " | *@a | b\n"
            + " | ....^\n"
            + "ERROR: <input>:1:7: Syntax error: extraneous input 'b' expecting <EOF>\n"
            + " | *@a | b\n"
            + " | ......^",
        "",
      },
      new String[] {
        "53",
        "a | b",
        "",
        "ERROR: <input>:1:3: Syntax error: token recognition error at: '| '\n"
            + " | a | b\n"
            + " | ..^\n"
            + "ERROR: <input>:1:5: Syntax error: extraneous input 'b' expecting <EOF>\n"
            + " | a | b\n"
            + " | ....^",
        "",
      },
      new String[] {
        "54",
        "has(m.f)",
        "m^#2:*expr.Expr_IdentExpr#.f~test-only~^#4:*expr.Expr_SelectExpr#",
        "",
        "m^#2[1,4]#.f~test-only~^#4[1,3]#",
      },
      new String[] {
        "55",
        "m.exists_one(v, f)",
        "__comprehension__(\n"
            + "  // Variable\n"
            + "  v,\n"
            + "  // Target\n"
            + "  m^#1:*expr.Expr_IdentExpr#,\n"
            + "  // Accumulator\n"
            + "  __result__,\n"
            + "  // Init\n"
            + "  0^#5:*expr.Constant_Int64Value#,\n"
            + "  // LoopCondition\n"
            + "  true^#7:*expr.Constant_BoolValue#,\n"
            + "  // LoopStep\n"
            + "  _?_:_(\n"
            + "    f^#4:*expr.Expr_IdentExpr#,\n"
            + "    _+_(\n"
            + "      __result__^#8:*expr.Expr_IdentExpr#,\n"
            + "      1^#6:*expr.Constant_Int64Value#\n"
            + "    )^#9:*expr.Expr_CallExpr#,\n"
            + "    __result__^#10:*expr.Expr_IdentExpr#\n"
            + "  )^#11:*expr.Expr_CallExpr#,\n"
            + "  // Result\n"
            + "  _==_(\n"
            + "    __result__^#12:*expr.Expr_IdentExpr#,\n"
            + "    1^#6:*expr.Constant_Int64Value#\n"
            + "  )^#13:*expr.Expr_CallExpr#)^#14:*expr.Expr_ComprehensionExpr#",
        "",
        "",
      },
      new String[] {
        "56",
        "m.map(v, f)",
        "__comprehension__(\n"
            + "  // Variable\n"
            + "  v,\n"
            + "  // Target\n"
            + "  m^#1:*expr.Expr_IdentExpr#,\n"
            + "  // Accumulator\n"
            + "  __result__,\n"
            + "  // Init\n"
            + "  []^#6:*expr.Expr_ListExpr#,\n"
            + "  // LoopCondition\n"
            + "  true^#7:*expr.Constant_BoolValue#,\n"
            + "  // LoopStep\n"
            + "  _+_(\n"
            + "    __result__^#5:*expr.Expr_IdentExpr#,\n"
            + "    [\n"
            + "      f^#4:*expr.Expr_IdentExpr#\n"
            + "    ]^#8:*expr.Expr_ListExpr#\n"
            + "  )^#9:*expr.Expr_CallExpr#,\n"
            + "  // Result\n"
            + "  __result__^#5:*expr.Expr_IdentExpr#)^#10:*expr.Expr_ComprehensionExpr#",
        "",
        "",
      },
      new String[] {
        "57",
        "m.map(v, p, f)",
        "__comprehension__(\n"
            + "  // Variable\n"
            + "  v,\n"
            + "  // Target\n"
            + "  m^#1:*expr.Expr_IdentExpr#,\n"
            + "  // Accumulator\n"
            + "  __result__,\n"
            + "  // Init\n"
            + "  []^#7:*expr.Expr_ListExpr#,\n"
            + "  // LoopCondition\n"
            + "  true^#8:*expr.Constant_BoolValue#,\n"
            + "  // LoopStep\n"
            + "  _?_:_(\n"
            + "    p^#4:*expr.Expr_IdentExpr#,\n"
            + "    _+_(\n"
            + "      __result__^#6:*expr.Expr_IdentExpr#,\n"
            + "      [\n"
            + "        f^#5:*expr.Expr_IdentExpr#\n"
            + "      ]^#9:*expr.Expr_ListExpr#\n"
            + "    )^#10:*expr.Expr_CallExpr#,\n"
            + "    __result__^#6:*expr.Expr_IdentExpr#\n"
            + "  )^#11:*expr.Expr_CallExpr#,\n"
            + "  // Result\n"
            + "  __result__^#6:*expr.Expr_IdentExpr#)^#12:*expr.Expr_ComprehensionExpr#",
        "",
        "",
      },
      new String[] {
        "58",
        "m.filter(v, p)",
        "__comprehension__(\n"
            + "  // Variable\n"
            + "  v,\n"
            + "  // Target\n"
            + "  m^#1:*expr.Expr_IdentExpr#,\n"
            + "  // Accumulator\n"
            + "  __result__,\n"
            + "  // Init\n"
            + "  []^#6:*expr.Expr_ListExpr#,\n"
            + "  // LoopCondition\n"
            + "  true^#7:*expr.Constant_BoolValue#,\n"
            + "  // LoopStep\n"
            + "  _?_:_(\n"
            + "    p^#4:*expr.Expr_IdentExpr#,\n"
            + "    _+_(\n"
            + "      __result__^#5:*expr.Expr_IdentExpr#,\n"
            + "      [\n"
            + "        v^#3:*expr.Expr_IdentExpr#\n"
            + "      ]^#8:*expr.Expr_ListExpr#\n"
            + "    )^#9:*expr.Expr_CallExpr#,\n"
            + "    __result__^#5:*expr.Expr_IdentExpr#\n"
            + "  )^#10:*expr.Expr_CallExpr#,\n"
            + "  // Result\n"
            + "  __result__^#5:*expr.Expr_IdentExpr#)^#11:*expr.Expr_ComprehensionExpr#",
        "",
        "",
      },
      new String[] {
        "59",
        "x * 2",
        "_*_(\n"
            + "  x^#1:*expr.Expr_IdentExpr#,\n"
            + "  2^#3:*expr.Constant_Int64Value#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "60",
        "x * 2u",
        "_*_(\n"
            + "  x^#1:*expr.Expr_IdentExpr#,\n"
            + "  2u^#3:*expr.Constant_Uint64Value#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "61",
        "x * 2.0",
        "_*_(\n"
            + "  x^#1:*expr.Expr_IdentExpr#,\n"
            + "  2^#3:*expr.Constant_DoubleValue#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "62", "\"\\u2764\"", "\"❤\"^#1:*expr.Constant_StringValue#", "", "",
      },
      new String[] {
        "63", "\"❤\"", "\"❤\"^#1:*expr.Constant_StringValue#", "", "",
      },
      new String[] {
        "64",
        "! false",
        "!_(\n" + "  false^#2:*expr.Constant_BoolValue#\n" + ")^#1:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "65",
        "-a",
        "-_(\n" + "  a^#2:*expr.Expr_IdentExpr#\n" + ")^#1:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "66",
        "a.b(5)",
        "a^#1:*expr.Expr_IdentExpr#.b(\n"
            + "  5^#3:*expr.Constant_Int64Value#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "67",
        "a[3]",
        "_[_](\n"
            + "  a^#1:*expr.Expr_IdentExpr#,\n"
            + "  3^#3:*expr.Constant_Int64Value#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "68",
        "SomeMessage{foo: 5, bar: \"xyz\"}",
        "SomeMessage{\n"
            + "  foo:5^#4:*expr.Constant_Int64Value#^#3:*expr.Expr_CreateStruct_Entry#,\n"
            + "  bar:\"xyz\"^#6:*expr.Constant_StringValue#^#5:*expr.Expr_CreateStruct_Entry#\n"
            + "}^#2:*expr.Expr_StructExpr#",
        "",
        "",
      },
      new String[] {
        "69",
        "[3, 4, 5]",
        "[\n"
            + "  3^#2:*expr.Constant_Int64Value#,\n"
            + "  4^#3:*expr.Constant_Int64Value#,\n"
            + "  5^#4:*expr.Constant_Int64Value#\n"
            + "]^#1:*expr.Expr_ListExpr#",
        "",
        "",
      },
      new String[] {
        "70",
        "[3, 4, 5,]",
        "[\n"
            + "  3^#2:*expr.Constant_Int64Value#,\n"
            + "  4^#3:*expr.Constant_Int64Value#,\n"
            + "  5^#4:*expr.Constant_Int64Value#\n"
            + "]^#1:*expr.Expr_ListExpr#",
        "",
        "",
      },
      new String[] {
        "71",
        "{foo: 5, bar: \"xyz\"}",
        "{\n"
            + "  foo^#3:*expr.Expr_IdentExpr#:5^#4:*expr.Constant_Int64Value#^#2:*expr.Expr_CreateStruct_Entry#,\n"
            + "  bar^#6:*expr.Expr_IdentExpr#:\"xyz\"^#7:*expr.Constant_StringValue#^#5:*expr.Expr_CreateStruct_Entry#\n"
            + "}^#1:*expr.Expr_StructExpr#",
        "",
        "",
      },
      new String[] {
        "72",
        "{foo: 5, bar: \"xyz\", }",
        "{\n"
            + "  foo^#3:*expr.Expr_IdentExpr#:5^#4:*expr.Constant_Int64Value#^#2:*expr.Expr_CreateStruct_Entry#,\n"
            + "  bar^#6:*expr.Expr_IdentExpr#:\"xyz\"^#7:*expr.Constant_StringValue#^#5:*expr.Expr_CreateStruct_Entry#\n"
            + "}^#1:*expr.Expr_StructExpr#",
        "",
        "",
      },
      new String[] {
        "73",
        "a > 5 && a < 10",
        "_&&_(\n"
            + "  _>_(\n"
            + "    a^#1:*expr.Expr_IdentExpr#,\n"
            + "    5^#3:*expr.Constant_Int64Value#\n"
            + "  )^#2:*expr.Expr_CallExpr#,\n"
            + "  _<_(\n"
            + "    a^#4:*expr.Expr_IdentExpr#,\n"
            + "    10^#6:*expr.Constant_Int64Value#\n"
            + "  )^#5:*expr.Expr_CallExpr#\n"
            + ")^#7:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "74",
        "a < 5 || a > 10",
        "_||_(\n"
            + "  _<_(\n"
            + "    a^#1:*expr.Expr_IdentExpr#,\n"
            + "    5^#3:*expr.Constant_Int64Value#\n"
            + "  )^#2:*expr.Expr_CallExpr#,\n"
            + "  _>_(\n"
            + "    a^#4:*expr.Expr_IdentExpr#,\n"
            + "    10^#6:*expr.Constant_Int64Value#\n"
            + "  )^#5:*expr.Expr_CallExpr#\n"
            + ")^#7:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "75",
        "{",
        "",
        "ERROR: <input>:1:2: Syntax error: mismatched input '<EOF>' expecting {'[', '{', '}', '(', '.', ',', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
            + " | {\n"
            + " | .^",
        "",
      },
      new String[] {
        "76",
        "[] + [1,2,3,] + [4]",
        "_+_(\n"
            + "  _+_(\n"
            + "    []^#1:*expr.Expr_ListExpr#,\n"
            + "    [\n"
            + "      1^#4:*expr.Constant_Int64Value#,\n"
            + "      2^#5:*expr.Constant_Int64Value#,\n"
            + "      3^#6:*expr.Constant_Int64Value#\n"
            + "    ]^#3:*expr.Expr_ListExpr#\n"
            + "  )^#2:*expr.Expr_CallExpr#,\n"
            + "  [\n"
            + "    4^#9:*expr.Constant_Int64Value#\n"
            + "  ]^#8:*expr.Expr_ListExpr#\n"
            + ")^#7:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "77",
        "{1:2u, 2:3u}",
        "{\n"
            + "  1^#3:*expr.Constant_Int64Value#:2u^#4:*expr.Constant_Uint64Value#^#2:*expr.Expr_CreateStruct_Entry#,\n"
            + "  2^#6:*expr.Constant_Int64Value#:3u^#7:*expr.Constant_Uint64Value#^#5:*expr.Expr_CreateStruct_Entry#\n"
            + "}^#1:*expr.Expr_StructExpr#",
        "",
        "",
      },
      new String[] {
        "78",
        "TestAllTypes{single_int32: 1, single_int64: 2}",
        "TestAllTypes{\n"
            + "  single_int32:1^#4:*expr.Constant_Int64Value#^#3:*expr.Expr_CreateStruct_Entry#,\n"
            + "  single_int64:2^#6:*expr.Constant_Int64Value#^#5:*expr.Expr_CreateStruct_Entry#\n"
            + "}^#2:*expr.Expr_StructExpr#",
        "",
        "",
      },
      new String[] {
        "79",
        "TestAllTypes(){single_int32: 1, single_int64: 2}",
        "",
        "ERROR: <input>:1:13: expected a qualified name\n"
            + " | TestAllTypes(){single_int32: 1, single_int64: 2}\n"
            + " | ............^",
        "",
      },
      new String[] {
        "80",
        "size(x) == x.size()",
        "_==_(\n"
            + "  size(\n"
            + "    x^#2:*expr.Expr_IdentExpr#\n"
            + "  )^#1:*expr.Expr_CallExpr#,\n"
            + "  x^#4:*expr.Expr_IdentExpr#.size()^#5:*expr.Expr_CallExpr#\n"
            + ")^#3:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "81",
        "1 + $",
        "",
        "ERROR: <input>:1:5: Syntax error: token recognition error at: '$'\n"
            + " | 1 + $\n"
            + " | ....^\n"
            + "ERROR: <input>:1:6: Syntax error: mismatched input '<EOF>' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
            + " | 1 + $\n"
            + " | .....^",
        "",
      },
      new String[] {
        "82",
        "1 + 2\n" + "3 +",
        "",
        "ERROR: <input>:2:1: Syntax error: mismatched input '3' expecting {<EOF>, '==', '!=', 'in', '<', '<=', '>=', '>', '&&', '||', '[', '{', '.', '-', '?', '+', '*', '/', '%'}\n"
            + " | 3 +\n"
            + " | ^",
        "",
      },
      new String[] {
        "83", "\"\\\"\"", "\"\\\"\"^#1:*expr.Constant_StringValue#", "", "",
      },
      new String[] {
        "84",
        "[1,3,4][0]",
        "_[_](\n"
            + "  [\n"
            + "    1^#2:*expr.Constant_Int64Value#,\n"
            + "    3^#3:*expr.Constant_Int64Value#,\n"
            + "    4^#4:*expr.Constant_Int64Value#\n"
            + "  ]^#1:*expr.Expr_ListExpr#,\n"
            + "  0^#6:*expr.Constant_Int64Value#\n"
            + ")^#5:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "85",
        "1.all(2, 3)",
        "",
        "ERROR: <input>:1:7: argument must be a simple name\n" + " | 1.all(2, 3)\n" + " | ......^",
        "",
      },
      new String[] {
        "86",
        "x[\"a\"].single_int32 == 23",
        "_==_(\n"
            + "  _[_](\n"
            + "    x^#1:*expr.Expr_IdentExpr#,\n"
            + "    \"a\"^#3:*expr.Constant_StringValue#\n"
            + "  )^#2:*expr.Expr_CallExpr#.single_int32^#4:*expr.Expr_SelectExpr#,\n"
            + "  23^#6:*expr.Constant_Int64Value#\n"
            + ")^#5:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "87",
        "x.single_nested_message != null",
        "_!=_(\n"
            + "  x^#1:*expr.Expr_IdentExpr#.single_nested_message^#2:*expr.Expr_SelectExpr#,\n"
            + "  null^#4:*expr.Constant_NullValue#\n"
            + ")^#3:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "88",
        "false && !true || false ? 2 : 3",
        "_?_:_(\n"
            + "  _||_(\n"
            + "    _&&_(\n"
            + "      false^#1:*expr.Constant_BoolValue#,\n"
            + "      !_(\n"
            + "        true^#3:*expr.Constant_BoolValue#\n"
            + "      )^#2:*expr.Expr_CallExpr#\n"
            + "    )^#4:*expr.Expr_CallExpr#,\n"
            + "    false^#5:*expr.Constant_BoolValue#\n"
            + "  )^#6:*expr.Expr_CallExpr#,\n"
            + "  2^#8:*expr.Constant_Int64Value#,\n"
            + "  3^#9:*expr.Constant_Int64Value#\n"
            + ")^#7:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "89",
        "b\"abc\" + B\"def\"",
        "_+_(\n"
            + "  b\"abc\"^#1:*expr.Constant_BytesValue#,\n"
            + "  b\"def\"^#3:*expr.Constant_BytesValue#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "90",
        "1 + 2 * 3 - 1 / 2 == 6 % 1",
        "_==_(\n"
            + "  _-_(\n"
            + "    _+_(\n"
            + "      1^#1:*expr.Constant_Int64Value#,\n"
            + "      _*_(\n"
            + "        2^#3:*expr.Constant_Int64Value#,\n"
            + "        3^#5:*expr.Constant_Int64Value#\n"
            + "      )^#4:*expr.Expr_CallExpr#\n"
            + "    )^#2:*expr.Expr_CallExpr#,\n"
            + "    _/_(\n"
            + "      1^#7:*expr.Constant_Int64Value#,\n"
            + "      2^#9:*expr.Constant_Int64Value#\n"
            + "    )^#8:*expr.Expr_CallExpr#\n"
            + "  )^#6:*expr.Expr_CallExpr#,\n"
            + "  _%_(\n"
            + "    6^#11:*expr.Constant_Int64Value#,\n"
            + "    1^#13:*expr.Constant_Int64Value#\n"
            + "  )^#12:*expr.Expr_CallExpr#\n"
            + ")^#10:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "91",
        "1 + +",
        "",
        "ERROR: <input>:1:5: Syntax error: mismatched input '+' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
            + " | 1 + +\n"
            + " | ....^\n"
            + "ERROR: <input>:1:6: Syntax error: mismatched input '<EOF>' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
            + " | 1 + +\n"
            + " | .....^",
        "",
      },
      new String[] {
        "92",
        "\"abc\" + \"def\"",
        "_+_(\n"
            + "  \"abc\"^#1:*expr.Constant_StringValue#,\n"
            + "  \"def\"^#3:*expr.Constant_StringValue#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "93",
        "{\"a\": 1}.\"a\"",
        "",
        "ERROR: <input>:1:10: Syntax error: mismatched input '\"a\"' expecting IDENTIFIER\n"
            + " | {\"a\": 1}.\"a\"\n"
            + " | .........^",
        "",
      },
      new String[] {
        "94", "\"\\xC3\\XBF\"", "\"Ã¿\"^#1:*expr.Constant_StringValue#", "", "",
      },
      new String[] {
        "95", "\"\\303\\277\"", "\"Ã¿\"^#1:*expr.Constant_StringValue#", "", "",
      },
      new String[] {
        "96", "\"hi\\u263A \\u263Athere\"", "\"hi☺ ☺there\"^#1:*expr.Constant_StringValue#", "", "",
      },
      new String[] {
        "97", "\"\\U000003A8\\?\"", "\"Ψ?\"^#1:*expr.Constant_StringValue#", "", "",
      },
      new String[] {
        "98",
        "\"\\a\\b\\f\\n\\r\\t\\v'\\\"\\\\\\? Legal escapes\"",
        "\"\\a\\b\\f\\n\\r\\t\\v'\\\"\\\\? Legal escapes\"^#1:*expr.Constant_StringValue#",
        "",
        "",
      },
      new String[] {
        "99",
        "\"\\xFh\"",
        "",
        "ERROR: <input>:1:1: Syntax error: token recognition error at: '\"\\xFh'\n"
            + " | \"\\xFh\"\n"
            + " | ^\n"
            + "ERROR: <input>:1:6: Syntax error: token recognition error at: '\"'\n"
            + " | \"\\xFh\"\n"
            + " | .....^\n"
            + "ERROR: <input>:1:7: Syntax error: mismatched input '<EOF>' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
            + " | \"\\xFh\"\n"
            + " | ......^",
        "",
      },
      new String[] {
        "100",
        "\"\\a\\b\\f\\n\\r\\t\\v\\'\\\"\\\\\\? Illegal escape \\>\"",
        "",
        "ERROR: <input>:1:1: Syntax error: token recognition error at: '\"\\a\\b\\f\\n\\r\\t\\v\\'\\\"\\\\\\? Illegal escape \\>'\n"
            + " | \"\\a\\b\\f\\n\\r\\t\\v\\'\\\"\\\\\\? Illegal escape \\>\"\n"
            + " | ^\n"
            + "ERROR: <input>:1:42: Syntax error: token recognition error at: '\"'\n"
            + " | \"\\a\\b\\f\\n\\r\\t\\v\\'\\\"\\\\\\? Illegal escape \\>\"\n"
            + " | .........................................^\n"
            + "ERROR: <input>:1:43: Syntax error: mismatched input '<EOF>' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
            + " | \"\\a\\b\\f\\n\\r\\t\\v\\'\\\"\\\\\\? Illegal escape \\>\"\n"
            + " | ..........................................^",
        "",
      },
      new String[] {
        "101",
        "\"😁\" in [\"😁\", \"😑\", \"😦\"]",
        "@in(\n"
            + "  \"😁\"^#1:*expr.Constant_StringValue#,\n"
            + "  [\n"
            + "    \"😁\"^#4:*expr.Constant_StringValue#,\n"
            + "    \"😑\"^#5:*expr.Constant_StringValue#,\n"
            + "    \"😦\"^#6:*expr.Constant_StringValue#\n"
            + "  ]^#3:*expr.Expr_ListExpr#\n"
            + ")^#2:*expr.Expr_CallExpr#",
        "",
        "",
      },
      new String[] {
        "102",
        "      '😁' in ['😁', '😑', '😦']\n" + "  && in.😁",
        "",
        "ERROR: <input>:2:6: Syntax error: extraneous input 'in' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
            + " |   && in.\uD83D\uDE01\n"
            + " | .....^\n"
            + "ERROR: <input>:2:9: Syntax error: token recognition error at: '\uD83D'\n"
            + " |   && in.\uD83D\uDE01\n"
            + " | ........^\n"
            + "ERROR: <input>:2:10: Syntax error: token recognition error at: '\uDE01'\n"
            + " |   && in.\uD83D\uDE01\n"
            + " | .........^\n"
            + "ERROR: <input>:2:11: Syntax error: missing IDENTIFIER at '<EOF>'\n"
            + " |   && in.\uD83D\uDE01\n"
            + " | ..........^",
        "",
      },
      new String[] {
        "103", "as", "", "ERROR: <input>:1:1: reserved identifier: as\n" + " | as\n" + " | ^", "",
      },
      new String[] {
        "104",
        "break",
        "",
        "ERROR: <input>:1:1: reserved identifier: break\n" + " | break\n" + " | ^",
        "",
      },
      new String[] {
        "105",
        "const",
        "",
        "ERROR: <input>:1:1: reserved identifier: const\n" + " | const\n" + " | ^",
        "",
      },
      new String[] {
        "106",
        "continue",
        "",
        "ERROR: <input>:1:1: reserved identifier: continue\n" + " | continue\n" + " | ^",
        "",
      },
      new String[] {
        "107",
        "else",
        "",
        "ERROR: <input>:1:1: reserved identifier: else\n" + " | else\n" + " | ^",
        "",
      },
      new String[] {
        "108",
        "for",
        "",
        "ERROR: <input>:1:1: reserved identifier: for\n" + " | for\n" + " | ^",
        "",
      },
      new String[] {
        "109",
        "function",
        "",
        "ERROR: <input>:1:1: reserved identifier: function\n" + " | function\n" + " | ^",
        "",
      },
      new String[] {
        "110", "if", "", "ERROR: <input>:1:1: reserved identifier: if\n" + " | if\n" + " | ^", "",
      },
      new String[] {
        "111",
        "import",
        "",
        "ERROR: <input>:1:1: reserved identifier: import\n" + " | import\n" + " | ^",
        "",
      },
      new String[] {
        "112",
        "in",
        "",
        "ERROR: <input>:1:1: Syntax error: mismatched input 'in' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
            + " | in\n"
            + " | ^\n"
            + "ERROR: <input>:1:3: Syntax error: mismatched input '<EOF>' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
            + " | in\n"
            + " | ..^",
        "",
      },
      new String[] {
        "113",
        "let",
        "",
        "ERROR: <input>:1:1: reserved identifier: let\n" + " | let\n" + " | ^",
        "",
      },
      new String[] {
        "114",
        "loop",
        "",
        "ERROR: <input>:1:1: reserved identifier: loop\n" + " | loop\n" + " | ^",
        "",
      },
      new String[] {
        "115",
        "package",
        "",
        "ERROR: <input>:1:1: reserved identifier: package\n" + " | package\n" + " | ^",
        "",
      },
      new String[] {
        "116",
        "namespace",
        "",
        "ERROR: <input>:1:1: reserved identifier: namespace\n" + " | namespace\n" + " | ^",
        "",
      },
      new String[] {
        "117",
        "return",
        "",
        "ERROR: <input>:1:1: reserved identifier: return\n" + " | return\n" + " | ^",
        "",
      },
      new String[] {
        "118",
        "var",
        "",
        "ERROR: <input>:1:1: reserved identifier: var\n" + " | var\n" + " | ^",
        "",
      },
      new String[] {
        "119",
        "void",
        "",
        "ERROR: <input>:1:1: reserved identifier: void\n" + " | void\n" + " | ^",
        "",
      },
      new String[] {
        "120",
        "while",
        "",
        "ERROR: <input>:1:1: reserved identifier: while\n" + " | while\n" + " | ^",
        "",
      },
      new String[] {
        "121",
        "[1, 2, 3].map(var, var * var)",
        "",
        "ERROR: <input>:1:14: argument is not an identifier\n"
            + " | [1, 2, 3].map(var, var * var)\n"
            + " | .............^\n"
            + "ERROR: <input>:1:15: reserved identifier: var\n"
            + " | [1, 2, 3].map(var, var * var)\n"
            + " | ..............^\n"
            + "ERROR: <input>:1:20: reserved identifier: var\n"
            + " | [1, 2, 3].map(var, var * var)\n"
            + " | ...................^\n"
            + "ERROR: <input>:1:26: reserved identifier: var\n"
            + " | [1, 2, 3].map(var, var * var)\n"
            + " | .........................^",
        "",
      },
      new String[] {
        "122",
        "func{{a}}",
        "",
        "ERROR: <input>:1:6: Syntax error: extraneous input '{' expecting {'}', ',', IDENTIFIER}\n"
            + " | func{{a}}\n"
            + " | .....^\n"
            + "ERROR: <input>:1:8: Syntax error: mismatched input '}' expecting ':'\n"
            + " | func{{a}}\n"
            + " | .......^\n"
            + "ERROR: <input>:1:9: Syntax error: extraneous input '}' expecting <EOF>\n"
            + " | func{{a}}\n"
            + " | ........^",
        "",
      },
      new String[] {
        "123",
        "msg{:a}",
        "",
        "ERROR: <input>:1:5: Syntax error: extraneous input ':' expecting {'}', ',', IDENTIFIER}\n"
            + " | msg{:a}\n"
            + " | ....^\n"
            + "ERROR: <input>:1:7: Syntax error: mismatched input '}' expecting ':'\n"
            + " | msg{:a}\n"
            + " | ......^",
        "",
      },
      new String[] {
        "124",
        "{a}",
        "",
        "ERROR: <input>:1:3: Syntax error: mismatched input '}' expecting {'==', '!=', 'in', '<', '<=', '>=', '>', '&&', '||', '[', '{', '(', '.', '-', '?', ':', '+', '*', '/', '%'}\n"
            + " | {a}\n"
            + " | ..^",
        "",
      },
      new String[] {
        "125",
        "{:a}",
        "",
        "ERROR: <input>:1:2: Syntax error: extraneous input ':' expecting {'[', '{', '}', '(', '.', ',', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
            + " | {:a}\n"
            + " | .^\n"
            + "ERROR: <input>:1:4: Syntax error: mismatched input '}' expecting {'==', '!=', 'in', '<', '<=', '>=', '>', '&&', '||', '[', '{', '(', '.', '-', '?', ':', '+', '*', '/', '%'}\n"
            + " | {:a}\n"
            + " | ...^",
        "",
      },
      new String[] {
        "126",
        "ind[a{b}]",
        "",
        "ERROR: <input>:1:8: Syntax error: mismatched input '}' expecting ':'\n"
            + " | ind[a{b}]\n"
            + " | .......^",
        "",
      },
      new String[] {
        "127",
        "--",
        "",
        "ERROR: <input>:1:3: Syntax error: mismatched input '<EOF>' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
            + " | --\n"
            + " | ..^\n"
            + "ERROR: <input>:1:3: Syntax error: no viable alternative at input '-'\n"
            + " | --\n"
            + " | ..^",
        "",
      },
      new String[] {
        "128",
        "?",
        "",
        "ERROR: <input>:1:1: Syntax error: mismatched input '?' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
            + " | ?\n"
            + " | ^\n"
            + "ERROR: <input>:1:2: Syntax error: mismatched input '<EOF>' expecting {'[', '{', '(', '.', '-', '!', 'true', 'false', 'null', NUM_FLOAT, NUM_INT, NUM_UINT, STRING, BYTES, IDENTIFIER}\n"
            + " | ?\n"
            + " | .^",
        "",
      },
      new String[] {
        "129",
        "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[\n"
            + "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[\n"
            + "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[\n"
            + "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[['too many']]]]]]]]]]]]]]]]]]]]]]]]]]]]\n"
            + "]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]\n"
            + "]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]\n"
            + "]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]",
        "",
        "ERROR: <input>:-1:0: expression recursion limit exceeded: 250",
        "",
      },
    };
  }

  /**
   * @param num just the index of the test case
   * @param i contains the input expression to be parsed.
   * @param p contains the type/id adorned debug output of the expression tree.
   * @param e contains the expected error output for a failed parse, or "" if the parse is expected
   *     to be successful.
   * @param l contains the expected source adorned debug output of the expression tree.
   */
  @ParameterizedTest
  @MethodSource("testCases")
  void parseTest(String num, String i, String p, String e, String l) {
    Parser parser = new Parser(Options.builder().macros(Macro.AllMacros).build());
    Source src = Source.newTextSource(i);
    ParseResult parseResult = parser.parse(src);

    String actualErr = parseResult.errors.toDisplayString();
    assertThat(actualErr).isEqualTo(e);
    // Hint for my future self and others: if the above "isEqualTo" fails but the strings look
    // similar,
    // look into the char[] representation... unicode can be very surprising.

    String actualWithKind = Debug.toAdornedDebugString(parseResult.expr, new KindAndIdAdorner());
    assertThat(actualWithKind).isEqualTo(p);

    if (!l.isEmpty()) {
      String actualWithLocation =
          Debug.toAdornedDebugString(parseResult.expr, new LocationAdorner(parseResult.sourceInfo));
      assertThat(actualWithLocation).isEqualTo(l);
    }
  }

  @Test
  void expressionSizeCodePointLimit() {
    assertThatThrownBy(
            () ->
                new Parser(
                    Options.builder()
                        .macros(Macro.AllMacros)
                        .expressionSizeCodePointLimit(-2)
                        .build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("expression size code point limit must be greater than or equal to -1: -2");

    Parser p =
        new Parser(
            Options.builder().macros(Macro.AllMacros).expressionSizeCodePointLimit(2).build());
    Source src = Source.newTextSource("foo");
    ParseResult parseResult = p.parse(src);
    assertThat(parseResult.errors.getErrors())
        .containsExactly(
            new Error(
                Location.newLocation(-1, -1),
                "expression code point size exceeds limit: size: 3, limit 2"));
  }

  interface Metadata {
    Location getLocation(long exprID);
  }

  //  var _ metadata = &locationAdorner{}

  static class KindAndIdAdorner implements Debug.Adorner {

    @Override
    public String getMetadata(Object elem) {
      if (elem instanceof Expr) {
        Expr e = (Expr) elem;
        if (e instanceof Const) {
          return String.format(
              "^#%d:*expr.Constant_%s#", e.id, ((Const) e).value.getClass().getSimpleName());
        } else {
          return String.format("^#%d:*expr.Expr_%s#", e.id, e.getClass().getSimpleName());
        }
      } else if (elem instanceof StructExpr.Entry) {
        StructExpr.Entry entry = (StructExpr.Entry) elem;
        return String.format("^#%d:%s#", entry.id, "*expr.Expr_CreateStruct_Entry");
      }
      return "";
    }
  }

  static class LocationAdorner implements Debug.Adorner {
    final SourceInfo sourceInfo;

    LocationAdorner(SourceInfo sourceInfo) {
      this.sourceInfo = sourceInfo;
    }

    public Location getLocation(long exprID) {
      long pos = sourceInfo.getPosition(exprID);
      if (pos >= 0) {
        int line = 1;
        for (int lineOffset : sourceInfo.getLineOffsets()) {
          if (lineOffset > pos) {
            break;
          } else {
            line++;
          }
        }
        long column = pos;
        if (line > 1) {
          column = pos - sourceInfo.getLineOffset(line - 2);
        }
        return Location.newLocation(line, (int) column);
      }
      return null;
    }

    @Override
    public String getMetadata(Object elem) {
      long elemID;
      if (elem instanceof Expr) {
        elemID = ((Expr) elem).id;
      } else if (elem instanceof StructExpr.Entry) {
        elemID = ((StructExpr.Entry) elem).id;
      } else {
        throw new IllegalArgumentException(elem.getClass().getName());
      }
      Location location = getLocation(elemID);
      return String.format("^#%d[%d,%d]#", elemID, location.line(), location.column());
    }
  }
}
