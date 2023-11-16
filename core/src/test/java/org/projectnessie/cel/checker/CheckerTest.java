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
package org.projectnessie.cel.checker;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.projectnessie.cel.checker.CheckerEnv.newCheckerEnv;
import static org.projectnessie.cel.checker.CheckerEnv.newStandardCheckerEnv;
import static org.projectnessie.cel.checker.Printer.print;
import static org.projectnessie.cel.common.Source.newTextSource;

import com.google.api.expr.v1alpha1.Decl;
import com.google.api.expr.v1alpha1.Type;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.cel.checker.Checker.CheckResult;
import org.projectnessie.cel.common.Source;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.parser.Parser;
import org.projectnessie.cel.parser.Parser.ParseResult;

public class CheckerTest {

  static class TestCase {
    // I contains the input expression to be parsed. */
    String i;
    // R contains the result output. */
    String r;

    /** Type is the expected type of the expression */
    Type type;

    /** Container is the container name to use for test. */
    String container = "";

    /** Env is the environment to use for testing. */
    env env;

    /** Error is the expected error for negative test cases. */
    String error;

    /** DisableStdEnv indicates whether the standard functions should be disabled. */
    boolean disableStdEnv;

    /**
     * HomogeneousAggregateLiterals indicates whether list and map literals must have homogeneous
     * element types, false by default.
     */
    boolean homogeneousAggregateLiterals;

    String disabled;

    @Override
    public String toString() {
      return i;
    }

    TestCase disabled(String reason) {
      this.disabled = reason;
      return this;
    }

    TestCase i(String i) {
      this.i = i;
      return this;
    }

    TestCase r(String r) {
      this.r = r;
      return this;
    }

    TestCase type(Type type) {
      this.type = type;
      return this;
    }

    TestCase container(String container) {
      this.container = container;
      return this;
    }

    TestCase env(env env) {
      this.env = env;
      return this;
    }

    TestCase error(String error) {
      this.error = error;
      return this;
    }

    TestCase disableStdEnv() {
      this.disableStdEnv = true;
      return this;
    }

    TestCase homogeneousAggregateLiterals() {
      this.homogeneousAggregateLiterals = true;
      return this;
    }
  }

  static class env {
    Decl[] idents;
    Decl[] functions;

    env idents(Decl... idents) {
      this.idents = idents;
      return this;
    }

    env functions(Decl... functions) {
      this.functions = functions;
      return this;
    }
  }

  static final Map<String, env> testEnvs =
      Collections.singletonMap(
          "default",
          new env()
              .functions(
                  Decls.newFunction(
                      "fg_s", Decls.newOverload("fg_s_0", Collections.emptyList(), Decls.String)),
                  Decls.newFunction(
                      "fi_s_s",
                      Decls.newInstanceOverload(
                          "fi_s_s_0", singletonList(Decls.String), Decls.String)))
              .idents(
                  Decls.newVar("is", Decls.String),
                  Decls.newVar("ii", Decls.Int),
                  Decls.newVar("iu", Decls.Uint),
                  Decls.newVar("iz", Decls.Bool),
                  Decls.newVar("ib", Decls.Bytes),
                  Decls.newVar("id", Decls.Double),
                  Decls.newVar("ix", Decls.Null)));

  @SuppressWarnings("unused")
  static TestCase[] checkTestCases() {
    return new TestCase[] {
      // Java added
      new TestCase()
          .i("a.pancakes")
          .env(new env().idents(Decls.newVar("a", Decls.Int)))
          .error(
              "ERROR: <input>:1:2: type 'int' does not support field selection\n"
                  + " | a.pancakes\n"
                  + " | .^"),
      // Const types
      new TestCase().i("\"A\"").r("\"A\"~string").type(Decls.String),
      new TestCase().i("12").r("12~int").type(Decls.Int),
      new TestCase().i("12u").r("12u~uint").type(Decls.Uint),
      new TestCase().i("true").r("true~bool").type(Decls.Bool),
      new TestCase().i("false").r("false~bool").type(Decls.Bool),
      new TestCase().i("12.23").r("12.23~double").type(Decls.Double),
      new TestCase().i("null").r("null~null").type(Decls.Null),
      new TestCase().i("b\"ABC\"").r("b\"ABC\"~bytes").type(Decls.Bytes),
      // Ident types
      new TestCase().i("is").r("is~string^is").type(Decls.String).env(testEnvs.get("default")),
      new TestCase().i("ii").r("ii~int^ii").type(Decls.Int).env(testEnvs.get("default")),
      new TestCase().i("iu").r("iu~uint^iu").type(Decls.Uint).env(testEnvs.get("default")),
      new TestCase().i("iz").r("iz~bool^iz").type(Decls.Bool).env(testEnvs.get("default")),
      new TestCase().i("id").r("id~double^id").type(Decls.Double).env(testEnvs.get("default")),
      new TestCase().i("ix").r("ix~null^ix").type(Decls.Null).env(testEnvs.get("default")),
      new TestCase().i("ib").r("ib~bytes^ib").type(Decls.Bytes).env(testEnvs.get("default")),
      new TestCase().i("id").r("id~double^id").type(Decls.Double).env(testEnvs.get("default")),
      new TestCase().i("[]").r("[]~list(dyn)").type(Decls.newListType(Decls.Dyn)),
      new TestCase().i("[1]").r("[1~int]~list(int)").type(Decls.newListType(Decls.Int)),
      new TestCase()
          .i("[1, \"A\"]")
          .r("[1~int, \"A\"~string]~list(dyn)")
          .type(Decls.newListType(Decls.Dyn)),
      new TestCase()
          .i("foo")
          .r("foo~!error!")
          .type(Decls.Error)
          .error(
              "ERROR: <input>:1:1: undeclared reference to 'foo' (in container '')\n"
                  + " | foo\n"
                  + " | ^"),
      // Call resolution
      new TestCase()
          .i("fg_s()")
          .r("fg_s()~string^fg_s_0")
          .type(Decls.String)
          .env(testEnvs.get("default")),
      new TestCase()
          .i("is.fi_s_s()")
          .r("is~string^is.fi_s_s()~string^fi_s_s_0")
          .type(Decls.String)
          .env(testEnvs.get("default")),
      new TestCase()
          .i("1 + 2")
          .r("_+_(1~int, 2~int)~int^add_int64")
          .type(Decls.Int)
          .env(testEnvs.get("default")),
      new TestCase()
          .i("1 + ii")
          .r("_+_(1~int, ii~int^ii)~int^add_int64")
          .type(Decls.Int)
          .env(testEnvs.get("default")),
      new TestCase()
          .i("[1] + [2]")
          .r("_+_([1~int]~list(int), [2~int]~list(int))~list(int)^add_list")
          .type(Decls.newListType(Decls.Int))
          .env(testEnvs.get("default")),
      new TestCase()
          .i("[] + [1,2,3,] + [4]")
          .type(Decls.newListType(Decls.Int))
          .r(
              "_+_(\n"
                  + "	_+_(\n"
                  + "		[]~list(int),\n"
                  + "		[1~int, 2~int, 3~int]~list(int))~list(int)^add_list,\n"
                  + "		[4~int]~list(int))\n"
                  + "~list(int)^add_list"),
      new TestCase()
          .i("[1, 2u] + []")
          .r(
              "_+_(\n"
                  + "	[\n"
                  + "		1~int,\n"
                  + "		2u~uint\n"
                  + "	]~list(dyn),\n"
                  + "	[]~list(dyn)\n"
                  + ")~list(dyn)^add_list")
          .type(Decls.newListType(Decls.Dyn)),
      new TestCase()
          .i("{1:2u, 2:3u}")
          .type(Decls.newMapType(Decls.Int, Decls.Uint))
          .r("{1~int : 2u~uint, 2~int : 3u~uint}~map(int, uint)"),
      new TestCase()
          .i("{\"a\":1, \"b\":2}.a")
          .type(Decls.Int)
          .r("{\"a\"~string : 1~int, \"b\"~string : 2~int}~map(string, int).a~int"),
      new TestCase()
          .i("{1:2u, 2u:3}")
          .type(Decls.newMapType(Decls.Dyn, Decls.Dyn))
          .r("{1~int : 2u~uint, 2u~uint : 3~int}~map(dyn, dyn)"),
      new TestCase()
          .i("TestAllTypes{single_int32: 1, single_int64: 2}")
          .container("google.api.expr.test.v1.proto3")
          .r(
              "google.api.expr.test.v1.proto3.TestAllTypes{\n"
                  + "	single_int32 : 1~int,\n"
                  + "	single_int64 : 2~int\n"
                  + "}~google.api.expr.test.v1.proto3.TestAllTypes^google.api.expr.test.v1.proto3.TestAllTypes")
          .type(Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")),
      new TestCase()
          .i("TestAllTypes{single_int32: 1u}")
          .container("google.api.expr.test.v1.proto3")
          .error(
              "ERROR: <input>:1:26: expected type of field 'single_int32' is 'int' but provided type is 'uint'\n"
                  + " | TestAllTypes{single_int32: 1u}\n"
                  + " | .........................^"),
      new TestCase()
          .i("TestAllTypes{single_int32: 1, undefined: 2}")
          .container("google.api.expr.test.v1.proto3")
          .error(
              "ERROR: <input>:1:40: undefined field 'undefined'\n"
                  + " | TestAllTypes{single_int32: 1, undefined: 2}\n"
                  + " | .......................................^"),
      new TestCase()
          .i("size(x) == x.size()")
          .r(
              "_==_(size(x~list(int)^x)~int^size_list, x~list(int)^x.size()~int^list_size)\n"
                  + "  ~bool^equals")
          .env(new env().idents(Decls.newVar("x", Decls.newListType(Decls.Int))))
          .type(Decls.Bool),
      new TestCase()
          .i("int(1u) + int(uint(\"1\"))")
          .r(
              "_+_(int(1u~uint)~int^uint64_to_int64,\n"
                  + "      int(uint(\"1\"~string)~uint^string_to_uint64)~int^uint64_to_int64)\n"
                  + "  ~int^add_int64")
          .type(Decls.Int),
      new TestCase()
          .i("false && !true || false ? 2 : 3")
          .r(
              "_?_:_(_||_(_&&_(false~bool, !_(true~bool)~bool^logical_not)~bool^logical_and,\n"
                  + "            false~bool)\n"
                  + "        ~bool^logical_or,\n"
                  + "      2~int,\n"
                  + "      3~int)\n"
                  + "  ~int^conditional")
          .type(Decls.Int),
      new TestCase()
          .i("b\"abc\" + b\"def\"")
          .r("_+_(b\"abc\"~bytes, b\"def\"~bytes)~bytes^add_bytes")
          .type(Decls.Bytes),
      new TestCase()
          .i("1.0 + 2.0 * 3.0 - 1.0 / 2.20202 != 66.6")
          .r(
              "_!=_(_-_(_+_(1.0~double, _*_(2.0~double, 3.0~double)~double^multiply_double)\n"
                  + "           ~double^add_double,\n"
                  + "           _/_(1.0~double, 2.20202~double)~double^divide_double)\n"
                  + "       ~double^subtract_double,\n"
                  + "      66.6~double)\n"
                  + "  ~bool^not_equals")
          .type(Decls.Bool),
      new TestCase()
          .i("null == null && null != null")
          .r(
              "_&&_(\n"
                  + "	_==_(\n"
                  + "		null~null,\n"
                  + "		null~null\n"
                  + "	)~bool^equals,\n"
                  + "	_!=_(\n"
                  + "		null~null,\n"
                  + "		null~null\n"
                  + "	)~bool^not_equals\n"
                  + ")~bool^logical_and")
          .type(Decls.Bool),
      new TestCase()
          .i("1 == 1 && 2 != 1")
          .r(
              "_&&_(\n"
                  + "	_==_(\n"
                  + "		1~int,\n"
                  + "		1~int\n"
                  + "	)~bool^equals,\n"
                  + "	_!=_(\n"
                  + "		2~int,\n"
                  + "		1~int\n"
                  + "	)~bool^not_equals\n"
                  + ")~bool^logical_and")
          .type(Decls.Bool),
      new TestCase()
          .i("1 + 2 * 3 - 1 / 2 == 6 % 1")
          .r(
              " _==_(_-_(_+_(1~int, _*_(2~int, 3~int)~int^multiply_int64)~int^add_int64, _/_(1~int, 2~int)~int^divide_int64)~int^subtract_int64, _%_(6~int, 1~int)~int^modulo_int64)~bool^equals")
          .type(Decls.Bool),
      new TestCase()
          .i("\"abc\" + \"def\"")
          .r("_+_(\"abc\"~string, \"def\"~string)~string^add_string")
          .type(Decls.String),
      new TestCase()
          .i("1u + 2u * 3u - 1u / 2u == 6u % 1u")
          .r(
              "_==_(_-_(_+_(1u~uint, _*_(2u~uint, 3u~uint)~uint^multiply_uint64)\n"
                  + "\t         ~uint^add_uint64,\n"
                  + "\t         _/_(1u~uint, 2u~uint)~uint^divide_uint64)\n"
                  + "\t     ~uint^subtract_uint64,\n"
                  + "\t    _%_(6u~uint, 1u~uint)~uint^modulo_uint64)\n"
                  + "\t~bool^equals")
          .type(Decls.Bool),
      new TestCase()
          .i("x.single_int32 != null")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x",
                          Decls.newObjectType("google.api.expr.test.v1.proto3.Proto2Message"))))
          .error(
              "ERROR: <input>:1:2: [internal] unexpected failed resolution of 'google.api.expr.test.v1.proto3.Proto2Message'\n"
                  + " | x.single_int32 != null\n"
                  + " | .^"),
      new TestCase()
          .i("x.single_value + 1 / x.single_struct.y == 23")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .r(
              "_==_(\n"
                  + "\t\t\t_+_(\n"
                  + "\t\t\t  x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_value~dyn,\n"
                  + "\t\t\t  _/_(\n"
                  + "\t\t\t\t1~int,\n"
                  + "\t\t\t\tx~google.api.expr.test.v1.proto3.TestAllTypes^x.single_struct~map(string, dyn).y~dyn\n"
                  + "\t\t\t  )~int^divide_int64\n"
                  + "\t\t\t)~int^add_int64,\n"
                  + "\t\t\t23~int\n"
                  + "\t\t  )~bool^equals")
          .type(Decls.Bool),
      new TestCase()
          .i("x.single_value[23] + x.single_struct['y']")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .r(
              "_+_(\n"
                  + "_[_](\n"
                  + "  x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_value~dyn,\n"
                  + "  23~int\n"
                  + ")~dyn^index_list|index_map,\n"
                  + "_[_](\n"
                  + "  x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_struct~map(string, dyn),\n"
                  + "  \"y\"~string\n"
                  + ")~dyn^index_map\n"
                  + ")~dyn^add_int64|add_uint64|add_double|add_string|add_bytes|add_list|add_timestamp_duration|add_duration_timestamp|add_duration_duration")
          .type(Decls.Dyn),
      new TestCase()
          .i("TestAllTypes.NestedEnum.BAR != 99")
          .container("google.api.expr.test.v1.proto3")
          .r(
              "_!=_(google.api.expr.test.v1.proto3.TestAllTypes.NestedEnum.BAR\n"
                  + "     ~int^google.api.expr.test.v1.proto3.TestAllTypes.NestedEnum.BAR,\n"
                  + "    99~int)\n"
                  + "~bool^not_equals")
          .type(Decls.Bool),
      new TestCase()
          .i("size([] + [1])")
          .r("size(_+_([]~list(int), [1~int]~list(int))~list(int)^add_list)~int^size_list")
          .type(Decls.Int)
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x",
                          Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))),
      new TestCase()
          .i(
              "x[\"claims\"][\"groups\"][0].name == \"dummy\"\n"
                  + "&& x.claims[\"exp\"] == y[1].time\n"
                  + "&& x.claims.structured == {'key': z}\n"
                  + "&& z == 1.0")
          .r(
              "_&&_(\n"
                  + "	_&&_(\n"
                  + "		_==_(\n"
                  + "			_[_](\n"
                  + "				_[_](\n"
                  + "					_[_](\n"
                  + "						x~map(string, dyn)^x,\n"
                  + "						\"claims\"~string\n"
                  + "					)~dyn^index_map,\n"
                  + "					\"groups\"~string\n"
                  + "				)~list(dyn)^index_map,\n"
                  + "				0~int\n"
                  + "			)~dyn^index_list.name~dyn,\n"
                  + "			\"dummy\"~string\n"
                  + "		)~bool^equals,\n"
                  + "		_==_(\n"
                  + "			_[_](\n"
                  + "				x~map(string, dyn)^x.claims~dyn,\n"
                  + "				\"exp\"~string\n"
                  + "			)~dyn^index_map,\n"
                  + "			_[_](\n"
                  + "				y~list(dyn)^y,\n"
                  + "				1~int\n"
                  + "			)~dyn^index_list.time~dyn\n"
                  + "		)~bool^equals\n"
                  + "	)~bool^logical_and,\n"
                  + "	_&&_(\n"
                  + "		_==_(\n"
                  + "			x~map(string, dyn)^x.claims~dyn.structured~dyn,\n"
                  + "		  {\n"
                  + "				\"key\"~string:z~dyn^z\n"
                  + "			}~map(string, dyn)\n"
                  + "		)~bool^equals,\n"
                  + "		_==_(\n"
                  + "			z~dyn^z,\n"
                  + "			1.0~double\n"
                  + "		)~bool^equals\n"
                  + "	)~bool^logical_and\n"
                  + ")~bool^logical_and")
          .env(
              new env()
                  .idents(
                      Decls.newVar("x", Decls.newObjectType("google.protobuf.Struct")),
                      Decls.newVar("y", Decls.newObjectType("google.protobuf.ListValue")),
                      Decls.newVar("z", Decls.newObjectType("google.protobuf.Value"))))
          .type(Decls.Bool),
      new TestCase()
          .i("x + y")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x",
                          Decls.newListType(
                              Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))),
                      Decls.newVar("y", Decls.newListType(Decls.Int))))
          .error(
              "ERROR: <input>:1:3: found no matching overload for '_+_' applied to '(list(google.api.expr.test.v1.proto3.TestAllTypes), list(int))'\n"
                  + " | x + y\n"
                  + " | ..^"),
      new TestCase()
          .i("x[1u]")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x",
                          Decls.newListType(
                              Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))))
          .error(
              "ERROR: <input>:1:2: found no matching overload for '_[_]' applied to '(list(google.api.expr.test.v1.proto3.TestAllTypes), uint)'\n"
                  + " | x[1u]\n"
                  + " | .^"),
      new TestCase()
          .i("(x + x)[1].single_int32 == size(x)")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x",
                          Decls.newListType(
                              Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))))
          .r(
              "_==_(_[_](_+_(x~list(google.api.expr.test.v1.proto3.TestAllTypes)^x,\n"
                  + "                x~list(google.api.expr.test.v1.proto3.TestAllTypes)^x)\n"
                  + "            ~list(google.api.expr.test.v1.proto3.TestAllTypes)^add_list,\n"
                  + "           1~int)\n"
                  + "       ~google.api.expr.test.v1.proto3.TestAllTypes^index_list\n"
                  + "       .\n"
                  + "       single_int32\n"
                  + "       ~int,\n"
                  + "      size(x~list(google.api.expr.test.v1.proto3.TestAllTypes)^x)~int^size_list)\n"
                  + "  ~bool^equals")
          .type(Decls.Bool),
      new TestCase()
          .i("x.repeated_int64[x.single_int32] == 23")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .r(
              "_==_(_[_](x~google.api.expr.test.v1.proto3.TestAllTypes^x.repeated_int64~list(int),\n"
                  + "           x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_int32~int)\n"
                  + "       ~int^index_list,\n"
                  + "      23~int)\n"
                  + "  ~bool^equals")
          .type(Decls.Bool),
      new TestCase()
          .i("size(x.map_int64_nested_type) == 0")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .r(
              "_==_(size(x~google.api.expr.test.v1.proto3.TestAllTypes^x.map_int64_nested_type\n"
                  + "            ~map(int, google.api.expr.test.v1.proto3.NestedTestAllTypes))\n"
                  + "       ~int^size_map,\n"
                  + "      0~int)\n"
                  + "  ~bool^equals")
          .type(Decls.Bool),
      new TestCase()
          .i("x.all(y, y == true)")
          .env(new env().idents(Decls.newVar("x", Decls.Bool)))
          .r(
              "__comprehension__(\n"
                  + "// Variable\n"
                  + "y,\n"
                  + "// Target\n"
                  + "x~bool^x,\n"
                  + "// Accumulator\n"
                  + "__result__,\n"
                  + "// Init\n"
                  + "true~bool,\n"
                  + "// LoopCondition\n"
                  + "@not_strictly_false(\n"
                  + "	__result__~bool^__result__\n"
                  + ")~bool^not_strictly_false,\n"
                  + "// LoopStep\n"
                  + "_&&_(\n"
                  + "	__result__~bool^__result__,\n"
                  + "	_==_(\n"
                  + "	y~!error!^y,\n"
                  + "	true~bool\n"
                  + "	)~bool^equals\n"
                  + ")~bool^logical_and,\n"
                  + "// Result\n"
                  + "__result__~bool^__result__)~bool")
          .error(
              "ERROR: <input>:1:1: expression of type 'bool' cannot be range of a comprehension (must be list, map, or dynamic)\n"
                  + " | x.all(y, y == true)\n"
                  + " | ^"),
      new TestCase()
          .i("x.repeated_int64.map(x, double(x))")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .r(
              "__comprehension__(\n"
                  + "// Variable\n"
                  + "x,\n"
                  + "// Target\n"
                  + "x~google.api.expr.test.v1.proto3.TestAllTypes^x.repeated_int64~list(int),\n"
                  + "// Accumulator\n"
                  + "__result__,\n"
                  + "// Init\n"
                  + "[]~list(double),\n"
                  + "// LoopCondition\n"
                  + "true~bool,\n"
                  + "// LoopStep\n"
                  + "_+_(\n"
                  + "  __result__~list(double)^__result__,\n"
                  + "  [\n"
                  + "    double(\n"
                  + "      x~int^x\n"
                  + "    )~double^int64_to_double\n"
                  + "  ]~list(double)\n"
                  + ")~list(double)^add_list,\n"
                  + "// Result\n"
                  + "__result__~list(double)^__result__)~list(double)")
          .type(Decls.newListType(Decls.Double)),
      new TestCase()
          .i("x.repeated_int64.map(x, x > 0, double(x))")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .r(
              "__comprehension__(\n"
                  + "// Variable\n"
                  + "x,\n"
                  + "// Target\n"
                  + "x~google.api.expr.test.v1.proto3.TestAllTypes^x.repeated_int64~list(int),\n"
                  + "// Accumulator\n"
                  + "__result__,\n"
                  + "// Init\n"
                  + "[]~list(double),\n"
                  + "// LoopCondition\n"
                  + "true~bool,\n"
                  + "// LoopStep\n"
                  + "_?_:_(\n"
                  + "  _>_(\n"
                  + "    x~int^x,\n"
                  + "    0~int\n"
                  + "  )~bool^greater_int64,\n"
                  + "  _+_(\n"
                  + "    __result__~list(double)^__result__,\n"
                  + "    [\n"
                  + "      double(\n"
                  + "        x~int^x\n"
                  + "      )~double^int64_to_double\n"
                  + "    ]~list(double)\n"
                  + "  )~list(double)^add_list,\n"
                  + "  __result__~list(double)^__result__\n"
                  + ")~list(double)^conditional,\n"
                  + "// Result\n"
                  + "__result__~list(double)^__result__)~list(double)")
          .type(Decls.newListType(Decls.Double)),
      new TestCase()
          .i("x[2].single_int32 == 23")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x",
                          Decls.newMapType(
                              Decls.String,
                              Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))))
          .error(
              "ERROR: <input>:1:2: found no matching overload for '_[_]' applied to '(map(string, google.api.expr.test.v1.proto3.TestAllTypes), int)'\n"
                  + " | x[2].single_int32 == 23\n"
                  + " | .^"),
      new TestCase()
          .i("x[\"a\"].single_int32 == 23")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x",
                          Decls.newMapType(
                              Decls.String,
                              Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))))
          .r(
              "_==_(_[_](x~map(string, google.api.expr.test.v1.proto3.TestAllTypes)^x, \"a\"~string)\n"
                  + "~google.api.expr.test.v1.proto3.TestAllTypes^index_map\n"
                  + ".\n"
                  + "single_int32\n"
                  + "~int,\n"
                  + "23~int)\n"
                  + "~bool^equals")
          .type(Decls.Bool),
      new TestCase()
          .i("x.single_nested_message.bb == 43 && has(x.single_nested_message)")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          // Our implementation code is expanding the macro
          .r(
              "_&&_(\n"
                  + "  _==_(\n"
                  + "    x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_nested_message~google.api.expr.test.v1.proto3.TestAllTypes.NestedMessage.bb~int,\n"
                  + "    43~int\n"
                  + "  )~bool^equals,\n"
                  + "  x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_nested_message~test-only~~bool\n"
                  + ")~bool^logical_and")
          .type(Decls.Bool),
      new TestCase()
          .i(
              "x.single_nested_message.undefined == x.undefined && has(x.single_int32) && has(x.repeated_int32)")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .error(
              "ERROR: <input>:1:24: undefined field 'undefined'\n"
                  + " | x.single_nested_message.undefined == x.undefined && has(x.single_int32) && has(x.repeated_int32)\n"
                  + " | .......................^\n"
                  + "ERROR: <input>:1:39: undefined field 'undefined'\n"
                  + " | x.single_nested_message.undefined == x.undefined && has(x.single_int32) && has(x.repeated_int32)\n"
                  + " | ......................................^"),
      new TestCase()
          .i("x.single_nested_message != null")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .r(
              "_!=_(x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_nested_message\n"
                  + "~google.api.expr.test.v1.proto3.TestAllTypes.NestedMessage,\n"
                  + "null~null)\n"
                  + "~bool^not_equals")
          .type(Decls.Bool),
      new TestCase()
          .i("x.single_int64 != null")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .r(
              "_!=_(x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_int64~int,null~null)~bool^not_equals")
          .type(Decls.Bool),
      new TestCase()
          .i("x.single_int64_wrapper == null")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .r(
              "_==_(x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_int64_wrapper\n"
                  + "~wrapper(int),\n"
                  + "null~null)\n"
                  + "~bool^equals")
          .type(Decls.Bool),
      new TestCase()
          .i(
              "x.single_bool_wrapper\n"
                  + "&& x.single_bytes_wrapper == b'hi'\n"
                  + "&& x.single_double_wrapper != 2.0\n"
                  + "&& x.single_float_wrapper == 1.0\n"
                  + "&& x.single_int32_wrapper != 2\n"
                  + "&& x.single_int64_wrapper == 1\n"
                  + "&& x.single_string_wrapper == 'hi'\n"
                  + "&& x.single_uint32_wrapper == 1u\n"
                  + "&& x.single_uint64_wrapper != 42u")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .r(
              "_&&_(\n"
                  + "	_&&_(\n"
                  + "		_&&_(\n"
                  + "		_&&_(\n"
                  + "			x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_bool_wrapper~wrapper(bool),\n"
                  + "			_==_(\n"
                  + "			x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_bytes_wrapper~wrapper(bytes),\n"
                  + "			b\"hi\"~bytes\n"
                  + "			)~bool^equals\n"
                  + "		)~bool^logical_and,\n"
                  + "		_!=_(\n"
                  + "			x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_double_wrapper~wrapper(double),\n"
                  + "			2.0~double\n"
                  + "		)~bool^not_equals\n"
                  + "		)~bool^logical_and,\n"
                  + "		_&&_(\n"
                  + "		_==_(\n"
                  + "			x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_float_wrapper~wrapper(double),\n"
                  + "			1.0~double\n"
                  + "		)~bool^equals,\n"
                  + "		_!=_(\n"
                  + "			x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_int32_wrapper~wrapper(int),\n"
                  + "			2~int\n"
                  + "		)~bool^not_equals\n"
                  + "		)~bool^logical_and\n"
                  + "	)~bool^logical_and,\n"
                  + "	_&&_(\n"
                  + "		_&&_(\n"
                  + "		_==_(\n"
                  + "			x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_int64_wrapper~wrapper(int),\n"
                  + "			1~int\n"
                  + "		)~bool^equals,\n"
                  + "		_==_(\n"
                  + "			x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_string_wrapper~wrapper(string),\n"
                  + "			\"hi\"~string\n"
                  + "		)~bool^equals\n"
                  + "		)~bool^logical_and,\n"
                  + "		_&&_(\n"
                  + "		_==_(\n"
                  + "			x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_uint32_wrapper~wrapper(uint),\n"
                  + "			1u~uint\n"
                  + "		)~bool^equals,\n"
                  + "		_!=_(\n"
                  + "			x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_uint64_wrapper~wrapper(uint),\n"
                  + "			42u~uint\n"
                  + "		)~bool^not_equals\n"
                  + "		)~bool^logical_and\n"
                  + "	)~bool^logical_and\n"
                  + ")~bool^logical_and")
          .type(Decls.Bool),
      new TestCase()
          .i(
              "x.single_bool_wrapper == google.protobuf.BoolValue{value: true}\n"
                  + "&& x.single_bytes_wrapper == google.protobuf.BytesValue{value: b'hi'}\n"
                  + "&& x.single_double_wrapper != google.protobuf.DoubleValue{value: 2.0}\n"
                  + "&& x.single_float_wrapper == google.protobuf.FloatValue{value: 1.0}\n"
                  + "&& x.single_int32_wrapper != google.protobuf.Int32Value{value: -2}\n"
                  + "&& x.single_int64_wrapper == google.protobuf.Int64Value{value: 1}\n"
                  + "&& x.single_string_wrapper == google.protobuf.StringValue{value: 'hi'}\n"
                  + "&& x.single_string_wrapper == google.protobuf.Value{string_value: 'hi'}\n"
                  + "&& x.single_uint32_wrapper == google.protobuf.UInt32Value{value: 1u}\n"
                  + "&& x.single_uint64_wrapper != google.protobuf.UInt64Value{value: 42u}")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .type(Decls.Bool),
      new TestCase()
          .i(
              "x.single_bool_wrapper == google.protobuf.BoolValue{value: true}\n"
                  + "&& x.single_bytes_wrapper == google.protobuf.BytesValue{value: b'hi'}\n"
                  + "&& x.single_double_wrapper != google.protobuf.DoubleValue{value: 2.0}\n"
                  + "&& x.single_float_wrapper == google.protobuf.FloatValue{value: 1.0}\n"
                  + "&& x.single_int32_wrapper != google.protobuf.Int32Value{value: -2}\n"
                  + "&& x.single_int64_wrapper == google.protobuf.Int64Value{value: 1}\n"
                  + "&& x.single_string_wrapper == google.protobuf.StringValue{value: 'hi'}\n"
                  + "&& x.single_string_wrapper == google.protobuf.Value{string_value: 'hi'}")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .type(Decls.Bool),
      new TestCase()
          .i("x.repeated_int64.exists(y, y > 10) && y < 5")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .error(
              "ERROR: <input>:1:39: undeclared reference to 'y' (in container '')\n"
                  + " | x.repeated_int64.exists(y, y > 10) && y < 5\n"
                  + " | ......................................^"),
      new TestCase()
          .i(
              "x.repeated_int64.all(e, e > 0) && x.repeated_int64.exists(e, e < 0) && x.repeated_int64.exists_one(e, e == 0)")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .r(
              "_&&_(\n"
                  + "\t\t\t_&&_(\n"
                  + "\t\t\t  __comprehension__(\n"
                  + "\t\t\t\t// Variable\n"
                  + "\t\t\t\te,\n"
                  + "\t\t\t\t// Target\n"
                  + "\t\t\t\tx~google.api.expr.test.v1.proto3.TestAllTypes^x.repeated_int64~list(int),\n"
                  + "\t\t\t\t// Accumulator\n"
                  + "\t\t\t\t__result__,\n"
                  + "\t\t\t\t// Init\n"
                  + "\t\t\t\ttrue~bool,\n"
                  + "\t\t\t\t// LoopCondition\n"
                  + "\t\t\t\t@not_strictly_false(\n"
                  + "\t\t\t\t  __result__~bool^__result__\n"
                  + "\t\t\t\t)~bool^not_strictly_false,\n"
                  + "\t\t\t\t// LoopStep\n"
                  + "\t\t\t\t_&&_(\n"
                  + "\t\t\t\t  __result__~bool^__result__,\n"
                  + "\t\t\t\t  _>_(\n"
                  + "\t\t\t\t\te~int^e,\n"
                  + "\t\t\t\t\t0~int\n"
                  + "\t\t\t\t  )~bool^greater_int64\n"
                  + "\t\t\t\t)~bool^logical_and,\n"
                  + "\t\t\t\t// Result\n"
                  + "\t\t\t\t__result__~bool^__result__)~bool,\n"
                  + "\t\t\t  __comprehension__(\n"
                  + "\t\t\t\t// Variable\n"
                  + "\t\t\t\te,\n"
                  + "\t\t\t\t// Target\n"
                  + "\t\t\t\tx~google.api.expr.test.v1.proto3.TestAllTypes^x.repeated_int64~list(int),\n"
                  + "\t\t\t\t// Accumulator\n"
                  + "\t\t\t\t__result__,\n"
                  + "\t\t\t\t// Init\n"
                  + "\t\t\t\tfalse~bool,\n"
                  + "\t\t\t\t// LoopCondition\n"
                  + "\t\t\t\t@not_strictly_false(\n"
                  + "\t\t\t\t  !_(\n"
                  + "\t\t\t\t\t__result__~bool^__result__\n"
                  + "\t\t\t\t  )~bool^logical_not\n"
                  + "\t\t\t\t)~bool^not_strictly_false,\n"
                  + "\t\t\t\t// LoopStep\n"
                  + "\t\t\t\t_||_(\n"
                  + "\t\t\t\t  __result__~bool^__result__,\n"
                  + "\t\t\t\t  _<_(\n"
                  + "\t\t\t\t\te~int^e,\n"
                  + "\t\t\t\t\t0~int\n"
                  + "\t\t\t\t  )~bool^less_int64\n"
                  + "\t\t\t\t)~bool^logical_or,\n"
                  + "\t\t\t\t// Result\n"
                  + "\t\t\t\t__result__~bool^__result__)~bool\n"
                  + "\t\t\t)~bool^logical_and,\n"
                  + "\t\t\t__comprehension__(\n"
                  + "\t\t\t  // Variable\n"
                  + "\t\t\t  e,\n"
                  + "\t\t\t  // Target\n"
                  + "\t\t\t  x~google.api.expr.test.v1.proto3.TestAllTypes^x.repeated_int64~list(int),\n"
                  + "\t\t\t  // Accumulator\n"
                  + "\t\t\t  __result__,\n"
                  + "\t\t\t  // Init\n"
                  + "\t\t\t  0~int,\n"
                  + "\t\t\t  // LoopCondition\n"
                  + "\t\t\t  true~bool,\n"
                  + "\t\t\t  // LoopStep\n"
                  + "\t\t\t  _?_:_(\n"
                  + "\t\t\t\t_==_(\n"
                  + "\t\t\t\t  e~int^e,\n"
                  + "\t\t\t\t  0~int\n"
                  + "\t\t\t\t)~bool^equals,\n"
                  + "\t\t\t\t_+_(\n"
                  + "\t\t\t\t  __result__~int^__result__,\n"
                  + "\t\t\t\t  1~int\n"
                  + "\t\t\t\t)~int^add_int64,\n"
                  + "\t\t\t\t__result__~int^__result__\n"
                  + "\t\t\t  )~int^conditional,\n"
                  + "\t\t\t  // Result\n"
                  + "\t\t\t  _==_(\n"
                  + "\t\t\t\t__result__~int^__result__,\n"
                  + "\t\t\t\t1~int\n"
                  + "\t\t\t  )~bool^equals)~bool\n"
                  + "\t\t  )~bool^logical_and")
          .type(Decls.Bool),
      new TestCase()
          .i("x.all(e, 0)")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .error(
              "ERROR: <input>:1:1: expression of type 'google.api.expr.test.v1.proto3.TestAllTypes' cannot be range of a comprehension (must be list, map, or dynamic)\n"
                  + " | x.all(e, 0)\n"
                  + " | ^\n"
                  + "ERROR: <input>:1:6: found no matching overload for '_&&_' applied to '(bool, int)'\n"
                  + " | x.all(e, 0)\n"
                  + " | .....^"),
      new TestCase()
          .i("lists.filter(x, x > 1.5)")
          .r(
              "__comprehension__(\n"
                  + "\t\t\t// Variable\n"
                  + "\t\t\tx,\n"
                  + "\t\t\t// Target\n"
                  + "\t\t\tlists~dyn^lists,\n"
                  + "\t\t\t// Accumulator\n"
                  + "\t\t\t__result__,\n"
                  + "\t\t\t// Init\n"
                  + "\t\t\t[]~list(dyn),\n"
                  + "\t\t\t// LoopCondition\n"
                  + "\t\t\ttrue~bool,\n"
                  + "\t\t\t// LoopStep\n"
                  + "\t\t\t_?_:_(\n"
                  + "\t\t\t  _>_(\n"
                  + "\t\t\t\tx~dyn^x,\n"
                  + "\t\t\t\t1.5~double\n"
                  + "\t\t\t  )~bool^greater_double,\n"
                  + "\t\t\t  _+_(\n"
                  + "\t\t\t\t__result__~list(dyn)^__result__,\n"
                  + "\t\t\t\t[\n"
                  + "\t\t\t\t  x~dyn^x\n"
                  + "\t\t\t\t]~list(dyn)\n"
                  + "\t\t\t  )~list(dyn)^add_list,\n"
                  + "\t\t\t  __result__~list(dyn)^__result__\n"
                  + "\t\t\t)~list(dyn)^conditional,\n"
                  + "\t\t\t// Result\n"
                  + "\t\t\t__result__~list(dyn)^__result__)~list(dyn)")
          .type(Decls.newListType(Decls.Dyn))
          .env(new env().idents(Decls.newVar("lists", Decls.Dyn))),
      new TestCase()
          .i("google.api.expr.test.v1.proto3.TestAllTypes")
          .r(
              "google.api.expr.test.v1.proto3.TestAllTypes\n"
                  + "\t~type(google.api.expr.test.v1.proto3.TestAllTypes)\n"
                  + "\t^google.api.expr.test.v1.proto3.TestAllTypes")
          .type(
              Decls.newTypeType(
                  Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))),
      new TestCase()
          .i("proto3.TestAllTypes")
          .container("google.api.expr.test.v1")
          .r(
              "google.api.expr.test.v1.proto3.TestAllTypes\n"
                  + "\t~type(google.api.expr.test.v1.proto3.TestAllTypes)\n"
                  + "\t^google.api.expr.test.v1.proto3.TestAllTypes")
          .type(
              Decls.newTypeType(
                  Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))),
      new TestCase()
          .i("1 + x")
          .error(
              "ERROR: <input>:1:5: undeclared reference to 'x' (in container '')\n"
                  + " | 1 + x\n"
                  + " | ....^"),
      new TestCase()
          .i(
              "x == google.protobuf.Any{\n"
                  + "\t\t\t\ttype_url:'types.googleapis.com/google.api.expr.test.v1.proto3.TestAllTypes'\n"
                  + "\t\t\t} && x.single_nested_message.bb == 43\n"
                  + "\t\t\t|| x == google.api.expr.test.v1.proto3.TestAllTypes{}\n"
                  + "\t\t\t|| y < x\n"
                  + "\t\t\t|| x >= x")
          .env(
              new env()
                  .idents(
                      Decls.newVar("x", Decls.Any),
                      Decls.newVar("y", Decls.newWrapperType(Decls.Int))))
          .r(
              "_||_(\n"
                  + "\t_||_(\n"
                  + "\t\t_&&_(\n"
                  + "\t\t\t_==_(\n"
                  + "\t\t\t\tx~any^x,\n"
                  + "\t\t\t\tgoogle.protobuf.Any{\n"
                  + "\t\t\t\t\ttype_url:\"types.googleapis.com/google.api.expr.test.v1.proto3.TestAllTypes\"~string\n"
                  + "\t\t\t\t}~any^google.protobuf.Any\n"
                  + "\t\t\t)~bool^equals,\n"
                  + "\t\t\t_==_(\n"
                  + "\t\t\t\tx~any^x.single_nested_message~dyn.bb~dyn,\n"
                  + "\t\t\t\t43~int\n"
                  + "\t\t\t)~bool^equals\n"
                  + "\t\t)~bool^logical_and,\n"
                  + "\t\t_==_(\n"
                  + "\t\t\tx~any^x,\n"
                  + "\t\t\tgoogle.api.expr.test.v1.proto3.TestAllTypes{}~google.api.expr.test.v1.proto3.TestAllTypes^google.api.expr.test.v1.proto3.TestAllTypes\n"
                  + "\t\t)~bool^equals\n"
                  + "\t)~bool^logical_or,\n"
                  + "\t_||_(\n"
                  + "\t\t_<_(\n"
                  + "\t\t\ty~wrapper(int)^y,\n"
                  + "\t\t\tx~any^x\n"
                  + "\t\t)~bool^less_int64,\n"
                  + "\t\t_>=_(\n"
                  + "\t\t\tx~any^x,\n"
                  + "\t\t\tx~any^x\n"
                  + "\t\t)~bool^greater_equals_bool|greater_equals_int64|greater_equals_uint64|greater_equals_double|greater_equals_string|greater_equals_bytes|greater_equals_timestamp|greater_equals_duration\n"
                  + "\t)~bool^logical_or\n"
                  + ")~bool^logical_or")
          .type(Decls.Bool),
      new TestCase()
          .i("x")
          .container("container")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "container.x",
                          Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .r("container.x~google.api.expr.test.v1.proto3.TestAllTypes^container.x")
          .type(Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")),
      new TestCase()
          .i("list == .type([1]) && map == .type({1:2u})")
          .r(
              "_&&_(_==_(list~type(list(dyn))^list,\n"
                  + "           type([1~int]~list(int))~type(list(int))^type)\n"
                  + "       ~bool^equals,\n"
                  + "      _==_(map~type(map(dyn, dyn))^map,\n"
                  + "            type({1~int : 2u~uint}~map(int, uint))~type(map(int, uint))^type)\n"
                  + "        ~bool^equals)\n"
                  + "  ~bool^logical_and")
          .type(Decls.Bool),
      new TestCase()
          .i("myfun(1, true, 3u) + 1.myfun(false, 3u).myfun(true, 42u)")
          .env(
              new env()
                  .functions(
                      Decls.newFunction(
                          "myfun",
                          Decls.newInstanceOverload(
                              "myfun_instance",
                              asList(Decls.Int, Decls.Bool, Decls.Uint),
                              Decls.Int),
                          Decls.newOverload(
                              "myfun_static",
                              asList(Decls.Int, Decls.Bool, Decls.Uint),
                              Decls.Int))))
          .r(
              "_+_(\n"
                  + "    \t\t  myfun(\n"
                  + "    \t\t    1~int,\n"
                  + "    \t\t    true~bool,\n"
                  + "    \t\t    3u~uint\n"
                  + "    \t\t  )~int^myfun_static,\n"
                  + "    \t\t  1~int.myfun(\n"
                  + "    \t\t    false~bool,\n"
                  + "    \t\t    3u~uint\n"
                  + "    \t\t  )~int^myfun_instance.myfun(\n"
                  + "    \t\t    true~bool,\n"
                  + "    \t\t    42u~uint\n"
                  + "    \t\t  )~int^myfun_instance\n"
                  + "    \t\t)~int^add_int64")
          .type(Decls.Int),
      new TestCase()
          .i("size(x) > 4")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))
                  .functions(
                      Decls.newFunction(
                          "size",
                          Decls.newOverload(
                              "size_message",
                              singletonList(
                                  Decls.newObjectType(
                                      "google.api.expr.test.v1.proto3.TestAllTypes")),
                              Decls.Int))))
          .type(Decls.Bool),
      new TestCase()
          .i("x.single_int64_wrapper + 1 != 23")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .r(
              "_!=_(_+_(x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_int64_wrapper\n"
                  + "~wrapper(int),\n"
                  + "1~int)\n"
                  + "~int^add_int64,\n"
                  + "23~int)\n"
                  + "~bool^not_equals")
          .type(Decls.Bool),
      new TestCase()
          .i("x.single_int64_wrapper + y != 23")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")),
                      Decls.newVar("y", Decls.newObjectType("google.protobuf.Int32Value"))))
          .r(
              "_!=_(\n"
                  + "\t_+_(\n"
                  + "\t  x~google.api.expr.test.v1.proto3.TestAllTypes^x.single_int64_wrapper~wrapper(int),\n"
                  + "\t  y~wrapper(int)^y\n"
                  + "\t)~int^add_int64,\n"
                  + "\t23~int\n"
                  + "  )~bool^not_equals")
          .type(Decls.Bool),
      new TestCase()
          .i("1 in [1, 2, 3]")
          .r(
              "@in(\n"
                  + "    \t\t  1~int,\n"
                  + "    \t\t  [\n"
                  + "    \t\t    1~int,\n"
                  + "    \t\t    2~int,\n"
                  + "    \t\t    3~int\n"
                  + "    \t\t  ]~list(int)\n"
                  + "    \t\t)~bool^in_list")
          .type(Decls.Bool),
      new TestCase()
          .i("1 in dyn([1, 2, 3])")
          .r(
              "@in(\n"
                  + "\t\t\t1~int,\n"
                  + "\t\t\tdyn(\n"
                  + "\t\t\t  [\n"
                  + "\t\t\t\t1~int,\n"
                  + "\t\t\t\t2~int,\n"
                  + "\t\t\t\t3~int\n"
                  + "\t\t\t  ]~list(int)\n"
                  + "\t\t\t)~dyn^to_dyn\n"
                  + "\t\t  )~bool^in_list|in_map")
          .type(Decls.Bool),
      new TestCase()
          .i("type(null) == null_type")
          .r(
              "_==_(\n"
                  + "    \t\t  type(\n"
                  + "    \t\t    null~null\n"
                  + "    \t\t  )~type(null)^type,\n"
                  + "    \t\t  null_type~type(null)^null_type\n"
                  + "    \t\t)~bool^equals")
          .type(Decls.Bool),
      new TestCase()
          .i("type(type) == type")
          .r(
              "_==_(\n"
                  + "\t\t  type(\n"
                  + "\t\t    type~type(type())^type\n"
                  + "\t\t  )~type(type(type()))^type,\n"
                  + "\t\t  type~type(type())^type\n"
                  + "\t\t)~bool^equals")
          .type(Decls.Bool),
      // Homogeneous aggregate type restriction tests.
      new TestCase()
          .i("name in [1, 2u, 'string']")
          .env(
              new env()
                  .idents(Decls.newVar("name", Decls.String))
                  .functions(
                      Decls.newFunction(
                          Operator.In.id,
                          Decls.newOverload(
                              Overloads.InList,
                              asList(Decls.String, Decls.newListType(Decls.String)),
                              Decls.Bool))))
          .homogeneousAggregateLiterals()
          .disableStdEnv()
          .r(
              "@in(\n"
                  + "\t\t\tname~string^name,\n"
                  + "\t\t\t[\n"
                  + "\t\t\t\t1~int,\n"
                  + "\t\t\t\t2u~uint,\n"
                  + "\t\t\t\t\"string\"~string\n"
                  + "\t\t\t]~list(string)\n"
                  + "\t\t)~bool^in_list")
          .error(
              "ERROR: <input>:1:13: expected type 'int' but found 'uint'\n"
                  + " | name in [1, 2u, 'string']\n"
                  + " | ............^"),
      new TestCase()
          .i("name in [1, 2, 3]")
          .env(
              new env()
                  .idents(Decls.newVar("name", Decls.String))
                  .functions(
                      Decls.newFunction(
                          Operator.In.id,
                          Decls.newOverload(
                              Overloads.InList,
                              asList(Decls.String, Decls.newListType(Decls.String)),
                              Decls.Bool))))
          .homogeneousAggregateLiterals()
          .disableStdEnv()
          .r(
              "@in(\n"
                  + "\t\t\tname~string^name,\n"
                  + "\t\t\t[\n"
                  + "\t\t\t\t1~int,\n"
                  + "\t\t\t\t2~int,\n"
                  + "\t\t\t\t3~int\n"
                  + "\t\t\t]~list(int)\n"
                  + "\t\t)~!error!")
          .error(
              "ERROR: <input>:1:6: found no matching overload for '@in' applied to '(string, list(int))'\n"
                  + " | name in [1, 2, 3]\n"
                  + " | .....^"),
      new TestCase()
          .i("name in [\"1\", \"2\", \"3\"]")
          .env(
              new env()
                  .idents(Decls.newVar("name", Decls.String))
                  .functions(
                      Decls.newFunction(
                          Operator.In.id,
                          Decls.newOverload(
                              Overloads.InList,
                              asList(Decls.String, Decls.newListType(Decls.String)),
                              Decls.Bool))))
          .homogeneousAggregateLiterals()
          .disableStdEnv()
          .r(
              "@in(\n"
                  + "\t\t\tname~string^name,\n"
                  + "\t\t\t[\n"
                  + "\t\t\t\t\"1\"~string,\n"
                  + "\t\t\t\t\"2\"~string,\n"
                  + "\t\t\t\t\"3\"~string\n"
                  + "\t\t\t]~list(string)\n"
                  + "\t\t)~bool^in_list")
          .type(Decls.Bool),
      new TestCase()
          .i("([[[1]], [[2]], [[3]]][0][0] + [2, 3, {'four': {'five': 'six'}}])[3]")
          .r(
              "_[_](\n"
                  + "\t\t\t_+_(\n"
                  + "\t\t\t\t_[_](\n"
                  + "\t\t\t\t\t_[_](\n"
                  + "\t\t\t\t\t\t[\n"
                  + "\t\t\t\t\t\t\t[\n"
                  + "\t\t\t\t\t\t\t\t[\n"
                  + "\t\t\t\t\t\t\t\t\t1~int\n"
                  + "\t\t\t\t\t\t\t\t]~list(int)\n"
                  + "\t\t\t\t\t\t\t]~list(list(int)),\n"
                  + "\t\t\t\t\t\t\t[\n"
                  + "\t\t\t\t\t\t\t\t[\n"
                  + "\t\t\t\t\t\t\t\t\t2~int\n"
                  + "\t\t\t\t\t\t\t\t]~list(int)\n"
                  + "\t\t\t\t\t\t\t]~list(list(int)),\n"
                  + "\t\t\t\t\t\t\t[\n"
                  + "\t\t\t\t\t\t\t\t[\n"
                  + "\t\t\t\t\t\t\t\t\t3~int\n"
                  + "\t\t\t\t\t\t\t\t]~list(int)\n"
                  + "\t\t\t\t\t\t\t]~list(list(int))\n"
                  + "\t\t\t\t\t\t]~list(list(list(int))),\n"
                  + "\t\t\t\t\t\t0~int\n"
                  + "\t\t\t\t\t)~list(list(int))^index_list,\n"
                  + "\t\t\t\t\t0~int\n"
                  + "\t\t\t\t)~list(int)^index_list,\n"
                  + "\t\t\t\t[\n"
                  + "\t\t\t\t\t2~int,\n"
                  + "\t\t\t\t\t3~int,\n"
                  + "\t\t\t\t\t{\n"
                  + "\t\t\t\t\t\t\"four\"~string:{\n"
                  + "\t\t\t\t\t\t\t\"five\"~string:\"six\"~string\n"
                  + "\t\t\t\t\t\t}~map(string, string)\n"
                  + "\t\t\t\t\t}~map(string, map(string, string))\n"
                  + "\t\t\t\t]~list(dyn)\n"
                  + "\t\t\t)~list(dyn)^add_list,\n"
                  + "\t\t\t3~int\n"
                  + "\t\t)~dyn^index_list")
          .type(Decls.Dyn),
      new TestCase()
          .i("[1] + [dyn('string')]")
          .r(
              "_+_(\n"
                  + "\t\t\t[\n"
                  + "\t\t\t\t1~int\n"
                  + "\t\t\t]~list(int),\n"
                  + "\t\t\t[\n"
                  + "\t\t\t\tdyn(\n"
                  + "\t\t\t\t\t\"string\"~string\n"
                  + "\t\t\t\t)~dyn^to_dyn\n"
                  + "\t\t\t]~list(dyn)\n"
                  + "\t\t)~list(dyn)^add_list")
          .type(Decls.newListType(Decls.Dyn)),
      new TestCase()
          .i("[dyn('string')] + [1]")
          .r(
              "_+_(\n"
                  + "\t\t\t[\n"
                  + "\t\t\t\tdyn(\n"
                  + "\t\t\t\t\t\"string\"~string\n"
                  + "\t\t\t\t)~dyn^to_dyn\n"
                  + "\t\t\t]~list(dyn),\n"
                  + "\t\t\t[\n"
                  + "\t\t\t\t1~int\n"
                  + "\t\t\t]~list(int)\n"
                  + "\t\t)~list(dyn)^add_list")
          .type(Decls.newListType(Decls.Dyn)),
      new TestCase()
          .i("[].map(x, [].map(y, x in y && y in x))")
          .error(
              "ERROR: <input>:1:33: found no matching overload for '@in' applied to '(type_param: \"_var2\", type_param: \"_var0\")'\n"
                  + " | [].map(x, [].map(y, x in y && y in x))\n"
                  + " | ................................^"),
      new TestCase()
          .i("args.user[\"myextension\"].customAttributes.filter(x, x.name == \"hobbies\")")
          .r(
              "__comprehension__(\n"
                  + "\t\t\t// Variable\n"
                  + "\t\t\tx,\n"
                  + "\t\t\t// Target\n"
                  + "\t\t\t_[_](\n"
                  + "\t\t\targs~map(string, dyn)^args.user~dyn,\n"
                  + "\t\t\t\"myextension\"~string\n"
                  + "\t\t\t)~dyn^index_map.customAttributes~dyn,\n"
                  + "\t\t\t// Accumulator\n"
                  + "\t\t\t__result__,\n"
                  + "\t\t\t// Init\n"
                  + "\t\t\t[]~list(dyn),\n"
                  + "\t\t\t// LoopCondition\n"
                  + "\t\t\ttrue~bool,\n"
                  + "\t\t\t// LoopStep\n"
                  + "\t\t\t_?_:_(\n"
                  + "\t\t\t_==_(\n"
                  + "\t\t\t\tx~dyn^x.name~dyn,\n"
                  + "\t\t\t\t\"hobbies\"~string\n"
                  + "\t\t\t)~bool^equals,\n"
                  + "\t\t\t_+_(\n"
                  + "\t\t\t\t__result__~list(dyn)^__result__,\n"
                  + "\t\t\t\t[\n"
                  + "\t\t\t\tx~dyn^x\n"
                  + "\t\t\t\t]~list(dyn)\n"
                  + "\t\t\t)~list(dyn)^add_list,\n"
                  + "\t\t\t__result__~list(dyn)^__result__\n"
                  + "\t\t\t)~list(dyn)^conditional,\n"
                  + "\t\t\t// Result\n"
                  + "\t\t\t__result__~list(dyn)^__result__)~list(dyn)")
          .env(new env().idents(Decls.newVar("args", Decls.newMapType(Decls.String, Decls.Dyn))))
          .type(Decls.newListType(Decls.Dyn)),
      new TestCase()
          .i("a.b + 1 == a[0]")
          .r(
              "_==_(\n"
                  + "\t\t\t_+_(\n"
                  + "\t\t\t  a~dyn^a.b~dyn,\n"
                  + "\t\t\t  1~int\n"
                  + "\t\t\t)~int^add_int64,\n"
                  + "\t\t\t_[_](\n"
                  + "\t\t\t  a~dyn^a,\n"
                  + "\t\t\t  0~int\n"
                  + "\t\t\t)~dyn^index_list|index_map\n"
                  + "\t\t  )~bool^equals")
          .env(new env().idents(Decls.newVar("a", Decls.newTypeParamType("T"))))
          .type(Decls.Bool),
      new TestCase()
          .i(
              "!has(pb2.single_int64)\n"
                  + "\t\t&& !has(pb2.repeated_int32)\n"
                  + "\t\t&& !has(pb2.map_string_string)\n"
                  + "\t\t&& !has(pb3.single_int64)\n"
                  + "\t\t&& !has(pb3.repeated_int32)\n"
                  + "\t\t&& !has(pb3.map_string_string)")
          .env(
              new env()
                  .idents(
                      Decls.newVar(
                          "pb2",
                          Decls.newObjectType("google.api.expr.test.v1.proto2.TestAllTypes")),
                      Decls.newVar(
                          "pb3",
                          Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes"))))
          .r(
              "_&&_(\n"
                  + "\t_&&_(\n"
                  + "\t  _&&_(\n"
                  + "\t\t!_(\n"
                  + "\t\t  pb2~google.api.expr.test.v1.proto2.TestAllTypes^pb2.single_int64~test-only~~bool\n"
                  + "\t\t)~bool^logical_not,\n"
                  + "\t\t!_(\n"
                  + "\t\t  pb2~google.api.expr.test.v1.proto2.TestAllTypes^pb2.repeated_int32~test-only~~bool\n"
                  + "\t\t)~bool^logical_not\n"
                  + "\t  )~bool^logical_and,\n"
                  + "\t  !_(\n"
                  + "\t\tpb2~google.api.expr.test.v1.proto2.TestAllTypes^pb2.map_string_string~test-only~~bool\n"
                  + "\t  )~bool^logical_not\n"
                  + "\t)~bool^logical_and,\n"
                  + "\t_&&_(\n"
                  + "\t  _&&_(\n"
                  + "\t\t!_(\n"
                  + "\t\t  pb3~google.api.expr.test.v1.proto3.TestAllTypes^pb3.single_int64~test-only~~bool\n"
                  + "\t\t)~bool^logical_not,\n"
                  + "\t\t!_(\n"
                  + "\t\t  pb3~google.api.expr.test.v1.proto3.TestAllTypes^pb3.repeated_int32~test-only~~bool\n"
                  + "\t\t)~bool^logical_not\n"
                  + "\t  )~bool^logical_and,\n"
                  + "\t  !_(\n"
                  + "\t\tpb3~google.api.expr.test.v1.proto3.TestAllTypes^pb3.map_string_string~test-only~~bool\n"
                  + "\t  )~bool^logical_not\n"
                  + "\t)~bool^logical_and\n"
                  + "  )~bool^logical_and")
          .type(Decls.Bool),
      new TestCase()
          .i("TestAllTypes{}.repeated_nested_message")
          .container("google.api.expr.test.v1.proto2")
          .r(
              "google.api.expr.test.v1.proto2.TestAllTypes{}~google.api.expr.test.v1.proto2.TestAllTypes^\n"
                  + "\t\tgoogle.api.expr.test.v1.proto2.TestAllTypes.repeated_nested_message\n"
                  + "\t\t~list(google.api.expr.test.v1.proto2.TestAllTypes.NestedMessage)")
          .type(
              Decls.newListType(
                  Decls.newObjectType(
                      "google.api.expr.test.v1.proto2.TestAllTypes.NestedMessage"))),
      new TestCase()
          .i("TestAllTypes{}.repeated_nested_message")
          .container("google.api.expr.test.v1.proto3")
          .r(
              "google.api.expr.test.v1.proto3.TestAllTypes{}~google.api.expr.test.v1.proto3.TestAllTypes^\n"
                  + "\t\tgoogle.api.expr.test.v1.proto3.TestAllTypes.repeated_nested_message\n"
                  + "\t\t~list(google.api.expr.test.v1.proto3.TestAllTypes.NestedMessage)")
          .type(
              Decls.newListType(
                  Decls.newObjectType(
                      "google.api.expr.test.v1.proto3.TestAllTypes.NestedMessage"))),
      new TestCase()
          .i("base64.encode('hello')")
          .env(
              new env()
                  .functions(
                      Decls.newFunction(
                          "base64.encode",
                          Decls.newOverload(
                              "base64_encode_string", singletonList(Decls.String), Decls.String))))
          .r("base64.encode(\n" + "\t\t\t\"hello\"~string\n" + "\t\t)~string^base64_encode_string")
          .type(Decls.String),
      new TestCase()
          .i("encode('hello')")
          .container("base64")
          .env(
              new env()
                  .functions(
                      Decls.newFunction(
                          "base64.encode",
                          Decls.newOverload(
                              "base64_encode_string", singletonList(Decls.String), Decls.String))))
          .r("base64.encode(\n" + "\t\t\t\"hello\"~string\n" + "\t\t)~string^base64_encode_string")
          .type(Decls.String)
    };
  }

  @ParameterizedTest
  @MethodSource("checkTestCases")
  void check(TestCase tc) {
    Assumptions.assumeTrue(tc.disabled == null, tc.disabled);

    Source src = newTextSource(tc.i);
    ParseResult parsed = Parser.parseAllMacros(src);
    assertThat(parsed.getErrors().getErrors())
        .withFailMessage(parsed.getErrors()::toDisplayString)
        .isEmpty();

    TypeRegistry reg =
        ProtoTypeRegistry.newRegistry(
            com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes.getDefaultInstance(),
            com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.getDefaultInstance());
    Container cont = Container.newContainer(Container.name(tc.container));
    CheckerEnv env = newStandardCheckerEnv(cont, reg);
    if (tc.disableStdEnv) {
      env = newCheckerEnv(cont, reg);
    }
    if (tc.homogeneousAggregateLiterals) {
      env.enableDynamicAggregateLiterals(false);
    }
    if (tc.env != null) {
      if (tc.env.idents != null) {
        for (Decl ident : tc.env.idents) {
          env.add(ident);
        }
      }
      if (tc.env.functions != null) {
        for (Decl fn : tc.env.functions) {
          env.add(fn);
        }
      }
    }

    CheckResult checkResult = Checker.Check(parsed, src, env);
    if (checkResult.hasErrors()) {
      String errorString = checkResult.getErrors().toDisplayString();
      if (tc.error != null) {
        assertThat(errorString).isEqualTo(tc.error);
      } else {
        fail(String.format("Unexpected type-check errors: %s", errorString));
      }
    } else if (tc.error != null) {
      assertThat(tc.error)
          .withFailMessage(String.format("Expected error not thrown: %s", tc.error))
          .isNull();
    }

    Type actual = checkResult.getCheckedExpr().getTypeMapMap().get(parsed.getExpr().getId());
    if (tc.error == null) {
      if (actual == null || !actual.equals(tc.type)) {
        fail(String.format("Type Error: '%s' vs expected '%s'", actual, tc.type));
      }
    }

    if (tc.r != null) {
      String actualStr =
          print(checkResult.getCheckedExpr().getExpr(), checkResult.getCheckedExpr());
      String actualCmp = actualStr.replaceAll("[ \n\t]", "");
      String rCmp = tc.r.replaceAll("[ \n\t]", "");
      assertThat(actualCmp).isEqualTo(rCmp);
    }
  }
}
