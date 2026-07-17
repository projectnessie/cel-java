/*
 * Copyright (C) 2026 The Authors of CEL-Java
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

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.NullT.NullValue;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.extension.OptionalLib.optionals;

import com.google.api.expr.v1alpha1.Type;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.Err;

class OptionalLibTest {

  @Test
  void declaresOptionalNone() {
    assertCheckedType("[optional.none(), optional.of(1)]", Decls.newListType(optional(Decls.Int)));
  }

  @Test
  void promotesOptionalTypeParameterToDyn() {
    assertCheckedType(
        "[optional.of(1), optional.of(dyn(1))]", Decls.newListType(optional(Decls.Dyn)));
  }

  @Test
  void promotesTernaryOptionalTypeParameterToDyn() {
    assertCheckedType("true ? optional.of(dyn(1)) : optional.of(1)", optional(Decls.Dyn));
  }

  @Test
  void keepsNullableOptionalType() {
    assertCheckedType("[optional.of(1), null][0]", optional(Decls.Int));
  }

  @Test
  void evaluatesPresentNull() {
    assertEvaluates("optional.of(null).hasValue()", True);
    assertEvaluates("optional.of(null).value()", NullValue);
  }

  @Test
  void evaluatesAbsentForNullZeroAndEmptyValues() {
    assertEvaluates("optional.ofNonZeroValue(null).hasValue()", False);
    assertEvaluates("optional.ofNonZeroValue(false).hasValue()", False);
    assertEvaluates("optional.ofNonZeroValue(0).hasValue()", False);
    assertEvaluates("optional.ofNonZeroValue(0u).hasValue()", False);
    assertEvaluates("optional.ofNonZeroValue(0.0).hasValue()", False);
    assertEvaluates("optional.ofNonZeroValue('').hasValue()", False);
    assertEvaluates("optional.ofNonZeroValue([]).hasValue()", False);
    assertEvaluates("optional.ofNonZeroValue({}).hasValue()", False);
  }

  @Test
  void evaluatesPresentForNonZeroValues() {
    assertEvaluates("optional.ofNonZeroValue(true).value()", True);
    assertEvaluates("optional.ofNonZeroValue(42).value()", intOf(42));
    assertEvaluates("optional.ofNonZeroValue('x').hasValue()", True);
  }

  @Test
  void evaluatesOrAndOrValue() {
    assertEvaluates("optional.none().or(optional.none()).orValue(42)", intOf(42));
    assertEvaluates("optional.none().or(optional.of(21)).orValue(42)", intOf(21));
    assertEvaluates("optional.of(7).or(optional.of(21)).orValue(42)", intOf(7));
  }

  @Test
  void evaluatesOptionalEquality() {
    assertEvaluates("optional.none() == optional.none()", True);
    assertEvaluates("optional.none() == optional.of(1)", False);
    assertEvaluates("optional.of(1) == optional.none()", False);
    assertEvaluates("optional.of(1) == optional.of(1)", True);
    assertEvaluates("optional.none() != optional.none()", False);
    assertEvaluates("optional.none() != optional.of(1)", True);
    assertEvaluates("optional.of(1) != optional.none()", True);
    assertEvaluates("optional.of(1) != optional.of(1)", False);
  }

  @Test
  void evaluatesOptionalTypeIdentifier() {
    assertEvaluates("type(optional.none()) == optional_type", True);
  }

  @Test
  void evaluatesOptMap() {
    assertEvaluates("optional.of(1).optMap(x, x + 1).value()", intOf(2));
    assertEvaluates("optional.ofNonZeroValue(0).optMap(x, x / 0).hasValue()", False);
  }

  @Test
  void evaluatesOptFlatMap() {
    assertEvaluates("optional.of(1).optFlatMap(x, optional.of(x + 1)).value()", intOf(2));
    assertEvaluates(
        "optional.ofNonZeroValue(0).optFlatMap(x, optional.of(x / 0)).hasValue()", False);
  }

  @Test
  void evaluatesOptionalSelectAndIndex() {
    assertEvaluates("{}.?c.hasValue()", False);
    assertEvaluates("{'c': 'x'}.?c.value()", stringOf("x"));
    assertEvaluates("[][?0].hasValue()", False);
    assertEvaluates("['foo'][?0].value()", stringOf("foo"));
  }

  @Test
  void evaluatesOptionalChaining() {
    assertEvaluates(
        "optional.of({'c': {}}).c.missing.or(optional.of(['list-value'])[0]).orValue('default value')",
        stringOf("list-value"));
    assertEvaluates(
        "has(optional.of({'c': {'entry': 'hello world'}}).c)"
            + " && !has(optional.of({'c': {'entry': 'hello world'}}).c.missing)",
        True);
  }

  @Test
  void evaluatesOptionalAggregateEntries() {
    assertEvaluates("[?{}.?c, ?optional.of(42), ?optional.none()].size()", intOf(1));
    assertEvaluates("{?'foo': optional.none()}.size()", intOf(0));
  }

  @Test
  void absentValueReturnsError() {
    assertThat(evaluate("optional.none().value()").getVal()).isInstanceOf(Err.class);
  }

  private static void assertCheckedType(String expression, Type expectedType) {
    Env env = newEnv(optionals());
    Env.AstIssuesTuple parsed = env.parse(expression);
    assertThat(parsed.hasIssues()).isFalse();

    Env.AstIssuesTuple checked = env.check(parsed.getAst());
    assertThat(checked.hasIssues()).isFalse();
    assertThat(checked.getAst().getResultType()).isEqualTo(expectedType);
  }

  private static void assertEvaluates(String expression, Object expectedValue) {
    assertThat(evaluate(expression).getVal()).describedAs(expression).isEqualTo(expectedValue);
  }

  private static Program.EvalResult evaluate(String expression) {
    Env env = newEnv(optionals());
    Env.AstIssuesTuple parsed = env.parse(expression);
    assertThat(parsed.hasIssues()).isFalse();

    Env.AstIssuesTuple checked = env.check(parsed.getAst());
    assertThat(checked.hasIssues()).describedAs(checked.getIssues().toString()).isFalse();

    Program program = env.program(checked.getAst());
    return program.eval(Map.of());
  }

  private static Type optional(Type type) {
    return Decls.newAbstractType("optional_type", singletonList(type));
  }
}
