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
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.extension.NetworkLib.network;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.Program.EvalResult;
import org.projectnessie.cel.common.types.Err;

class NetworkLibTest {

  @Test
  void canonicalizesIpStrings() {
    assertEvaluates("string(ip('2001:db8::68'))", stringOf("2001:db8::68"));
  }

  @Test
  void checksIpProperties() {
    assertEvaluates("ip('192.168.0.1').family()", intOf(4));
    assertEvaluates("ip('fe80::1').isLinkLocalUnicast()", True);
  }

  @Test
  void checksCidrContainment() {
    assertEvaluates("cidr('192.168.0.0/24').containsIP('192.168.0.1')", True);
    assertEvaluates("cidr('192.168.0.0/24').containsCIDR('192.168.0.0/23')", False);
  }

  @Test
  void rejectsInvalidIpLiterals() {
    EvalResult result = evaluate("ip('192.168.0.1.0')");

    assertThat(result.getVal()).isInstanceOf(Err.class);
    assertThat(result.getVal().toString()).contains("parse error");
  }

  private static void assertEvaluates(String expression, Object expectedValue) {
    assertThat(evaluate(expression).getVal()).isEqualTo(expectedValue);
  }

  private static EvalResult evaluate(String expression) {
    Env env = newEnv(network());
    Env.AstIssuesTuple parsed = env.parse(expression);
    assertThat(parsed.hasIssues()).isFalse();

    Env.AstIssuesTuple checked = env.check(parsed.getAst());
    assertThat(checked.hasIssues()).isFalse();

    Program program = env.program(checked.getAst());
    return program.eval(Map.of());
  }
}
