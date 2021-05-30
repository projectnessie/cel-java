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
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.cel.common.Source;
import org.projectnessie.cel.parser.Parser.ParseResult;
import org.projectnessie.cel.pb.Expr;

class TestUnparser {
  @SuppressWarnings("unused")
  static String[][] unparseIdenticalSource() {
    return new String[][] {
      new String[] {"call_add", "a + b - c"},
      new String[] {"call_and", "a && b && c && d && e"},
      new String[] {"call_and_or", "a || b && (c || d) && e"},
      new String[] {"call_cond", "a ? b : c"},
      new String[] {"call_index", "a[1][\"b\"]"},
      new String[] {"call_index_eq", "x[\"a\"].single_int32 == 23"},
      new String[] {"call_mul", "a * (b / c) % 0"},
      new String[] {"call_mul_add", "a + b * c"},
      new String[] {"call_mul_add_nested", "(a + b) * c / (d - e)"},
      new String[] {"call_mul_nested", "a * b / c % 0"},
      new String[] {"call_not", "!true"},
      new String[] {"call_neg", "-num"},
      new String[] {"call_or", "a || b || c || d || e"},
      new String[] {"call_neg_mult", "-(1 * 2)"},
      new String[] {"call_neg_add", "-(1 + 2)"},
      new String[] {"calc_distr_paren", "(1 + 2) * 3"},
      new String[] {"calc_distr_noparen", "1 + 2 * 3"},
      new String[] {"cond_tern_simple", "(x > 5) ? (x - 5) : 0"},
      new String[] {"cond_tern_neg_expr", "-((x > 5) ? (x - 5) : 0)"},
      new String[] {"cond_tern_neg_term", "-x ? (x - 5) : 0"},
      new String[] {"func_global", "size(a ? (b ? c : d) : e)"},
      new String[] {"func_member", "a.hello(\"world\")"},
      new String[] {"func_no_arg", "zero()"},
      new String[] {"func_one_arg", "one(\"a\")"},
      new String[] {"func_two_args", "and(d, 32u)"},
      new String[] {"func_var_args", "max(a, b, 100)"},
      new String[] {"func_neq", "x != \"a\""},
      new String[] {"func_in", "a in b"},
      new String[] {"list_empty", "[]"},
      new String[] {"list_one", "[1]"},
      new String[] {"list_many", "[\"hello, world\", \"goodbye, world\", \"sure, why not?\"]"},
      new String[] {"lit_bytes", "b\"\\xc3\\x83\\xc2\\xbf\""},
      new String[] {"lit_double", "-42.101"},
      new String[] {"lit_false", "false"},
      new String[] {"lit_int", "-405069"},
      new String[] {"lit_null", "null"},
      new String[] {"lit_string", "\"hello:\\t'world'\""},
      new String[] {"lit_true", "true"},
      new String[] {"lit_uint", "42u"},
      new String[] {"ident", "my_ident"},
      new String[] {"macro_has", "has(hello.world)"},
      new String[] {"map_empty", "{}"},
      new String[] {"map_lit_key", "{\"a\": a.b.c, b\"b\": bytes(a.b.c)}"},
      new String[] {"map_expr_key", "{a: a, b: a.b, c: a.b.c, a ? b : c: false, a || b: true}"},
      new String[] {"msg_empty", "v1alpha1.Expr{}"},
      new String[] {
        "msg_fields", "v1alpha1.Expr{id: 1, call_expr: v1alpha1.Call_Expr{function: \"name\"}}"
      },
      new String[] {"select", "a.b.c"},
      new String[] {"idx_idx_sel", "a[b][c].name"},
      new String[] {"sel_expr_target", "(a + b).name"},
      new String[] {"sel_cond_target", "(a ? b : c).name"},
      new String[] {"idx_cond_target", "(a ? b : c)[0]"},
      new String[] {"cond_conj", "(a1 && a2) ? b : c"},
      new String[] {"cond_disj_conj", "a ? (b1 || b2) : (c1 && c2)"},
      new String[] {"call_cond_target", "(a ? b : c).method(d)"},
      new String[] {"cond_flat", "false && !true || false"},
      new String[] {"cond_paren", "false && (!true || false)"},
      new String[] {"cond_cond", "(false && !true || false) ? 2 : 3"},
      new String[] {"cond_binop", "(x < 5) ? x : 5"},
      new String[] {"cond_binop_binop", "(x > 5) ? (x - 5) : 0"},
      new String[] {"cond_cond_binop", "(x > 5) ? ((x > 10) ? (x - 10) : 5) : 0"},
      // new String[]{"comp_all",           "[1, 2, 3].all(x, x > 0)"},
      // new String[]{"comp_exists",        "[1, 2, 3].exists(x, x > 0)"},
      // new String[]{"comp_map",           "[1, 2, 3].map(x, x >= 2, x * 4)"},
      // new String[]{"comp_exists_one",    "[1, 2, 3].exists_one(x, x >= 2)"},
    };
  }

  @ParameterizedTest
  @MethodSource("unparseIdenticalSource")
  void unparseIdentical(String name, String in) {
    Parser parser = new Parser(Options.builder().build());

    ParseResult p = parser.parse(Source.newTextSource(in));
    if (!p.errors.getErrors().isEmpty()) {
      fail(p.errors.toDisplayString());
    }

    String out = Unparser.unparse(p.expr, p.sourceInfo);
    assertThat(out).isEqualTo(in);

    ParseResult p2 = parser.parse(Source.newTextSource(out));
    if (!p2.errors.getErrors().isEmpty()) {
      fail(p2.errors.toDisplayString());
    }

    Expr before = p.expr;
    Expr after = p2.expr;
    assertThat(before).isEqualTo(after);
  }

  @SuppressWarnings("unused")
  static Object[] unparseEquivalentSource() {
    return new Object[][] {
      new Object[] {"call_add", new String[] {"a+b-c", "a + b - c"}},
      new Object[] {"call_cond", new String[] {"a ? b          : c", "a ? b : c"}},
      new Object[] {"call_index", new String[] {"a[  1  ][\"b\"]", "a[1][\"b\"]"}},
      new Object[] {
        "call_or_and", new String[] {"(false && !true) || false", "false && !true || false"}
      },
      new Object[] {"call_not_not", new String[] {"!!true", "true"}},
      new Object[] {"lit_quote_bytes", new String[] {"b'aaa\"\\'bbb'", "b\"aaa\\\"'bbb\""}},
      new Object[] {
        "lit_quote_bytes2",
        new String[] {"b\"\\141\\141\\141\\042\\047\\142\\142\\142\"", "b\"aaa\\\"'bbb\""}
      },
      new Object[] {"select", new String[] {"a . b . c", "a.b.c"}},
      new Object[] {
        "lit_unprintable",
        new String[] {
          "b'\\000\\001\\002\\003\\004\\005\\006\\007\\010\\032\\033\\034\\035\\036\\037\\040abcdef012345'",
          "b\"\\x00\\x01\\x02\\x03\\x04\\x05\\x06\\a\\b\\x1a\\x1b\\x1c\\x1d\\x1e\\x1f abcdef012345\""
        }
      }
    };
  }

  @ParameterizedTest
  @MethodSource("unparseEquivalentSource")
  void unparseEquivalent(String name, String[] in) {
    Parser parser = new Parser(Options.builder().build());

    ParseResult p = parser.parse(Source.newTextSource(in[0]));
    if (!p.errors.getErrors().isEmpty()) {
      fail(p.errors.toDisplayString());
    }
    String out = Unparser.unparse(p.expr, p.sourceInfo);
    assertThat(out).isEqualTo(in[1]);

    ParseResult p2 = parser.parse(Source.newTextSource(out));
    if (!p2.errors.getErrors().isEmpty()) {
      fail(p2.errors.toDisplayString());
    }
    Expr before = p.expr;
    Expr after = p2.expr;
    assertThat(before).isEqualTo(after);
  }
}
