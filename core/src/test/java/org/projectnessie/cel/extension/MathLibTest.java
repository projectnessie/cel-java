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

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.extension.MathLib.math;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.Program.EvalResult;
import org.projectnessie.cel.common.types.Err;

class MathLibTest {

  @Test
  void findsGreatestScalar() {
    assertEvaluates("math.greatest(5.4, 10, 3u, -5.0, 9223372036854775807)", intOf(Long.MAX_VALUE));
  }

  @Test
  void findsLeastListElement() {
    assertEvaluates("math.least([5.4, 10u, 3u, 1u, 3.5])", uintOf(1));
  }

  @Test
  void roundsHalfAwayFromZero() {
    assertEvaluates("math.round(-1.5)", doubleOf(-2.0));
  }

  @Test
  void shiftsSignedIntsLogicallyRight() {
    assertEvaluates("math.bitShiftRight(-1024, 3)", intOf(2305843009213693824L));
  }

  @Test
  void rejectsNegativeShiftOffset() {
    EvalResult result = evaluate("math.bitShiftLeft(1u, -1)");

    assertThat(result.getVal()).isInstanceOf(Err.class);
    assertThat(result.getVal().toString()).contains("negative offset");
  }

  private static void assertEvaluates(String expression, Object expectedValue) {
    assertThat(evaluate(expression).getVal()).isEqualTo(expectedValue);
  }

  private static EvalResult evaluate(String expression) {
    Env env = newEnv(math());
    Env.AstIssuesTuple parsed = env.parse(expression);
    assertThat(parsed.hasIssues()).isFalse();

    Env.AstIssuesTuple checked = env.check(parsed.getAst());
    assertThat(checked.hasIssues()).isFalse();

    Program program = env.program(checked.getAst());
    return program.eval(Map.of());
  }
}
