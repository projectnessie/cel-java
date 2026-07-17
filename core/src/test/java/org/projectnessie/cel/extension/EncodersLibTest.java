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
import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.extension.EncodersLib.encoders;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.Program.EvalResult;
import org.projectnessie.cel.common.types.Err;

class EncodersLibTest {

  @Test
  void encodesBase64Bytes() {
    assertEvaluates("base64.encode(b'hello')", stringOf("aGVsbG8="));
  }

  @Test
  void decodesBase64String() {
    assertEvaluates("base64.decode('aGVsbG8=')", bytesOf("hello"));
  }

  @Test
  void decodesBase64StringWithoutPadding() {
    assertEvaluates("base64.decode('aGVsbG8')", bytesOf("hello"));
  }

  @Test
  void rejectsInvalidBase64String() {
    EvalResult result = evaluate("base64.decode('not valid base64')");

    assertThat(result.getVal()).isInstanceOf(Err.class);
    assertThat(result.getVal().toString()).contains("invalid base64 string");
  }

  private static void assertEvaluates(String expression, Object expectedValue) {
    assertThat(evaluate(expression).getVal()).isEqualTo(expectedValue);
  }

  private static EvalResult evaluate(String expression) {
    Env env = newEnv(encoders());
    Env.AstIssuesTuple parsed = env.parse(expression);
    assertThat(parsed.hasIssues()).isFalse();

    Env.AstIssuesTuple checked = env.check(parsed.getAst());
    assertThat(checked.hasIssues()).isFalse();

    Program program = env.program(checked.getAst());
    return program.eval(Map.of());
  }
}
