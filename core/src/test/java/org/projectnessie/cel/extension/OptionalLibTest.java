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
import static org.projectnessie.cel.extension.OptionalLib.optionals;

import com.google.api.expr.v1alpha1.Type;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.checker.Decls;

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

  private static void assertCheckedType(String expression, Type expectedType) {
    Env env = newEnv(optionals());
    Env.AstIssuesTuple parsed = env.parse(expression);
    assertThat(parsed.hasIssues()).isFalse();

    Env.AstIssuesTuple checked = env.check(parsed.getAst());
    assertThat(checked.hasIssues()).isFalse();
    assertThat(checked.getAst().getResultType()).isEqualTo(expectedType);
  }

  private static Type optional(Type type) {
    return Decls.newAbstractType("optional_type", singletonList(type));
  }
}
