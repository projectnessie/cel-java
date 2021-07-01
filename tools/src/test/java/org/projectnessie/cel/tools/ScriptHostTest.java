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
package org.projectnessie.cel.tools;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;

class ScriptHostTest {
  @Test
  void basic() throws Exception {
    ScriptHost scriptHost = ScriptHost.newBuilder().build();

    // create the script, will be parsed and checked
    Script script =
        scriptHost
            .buildScript("x + ' ' + y")
            // Variable declarations - we need `x` and `y` in this example
            .withDeclarations(Decls.newVar("x", Decls.String), Decls.newVar("y", Decls.String))
            .build();

    Map<String, Object> arguments = new HashMap<>();
    arguments.put("x", "hello");
    arguments.put("y", "world");

    String result = script.execute(String.class, arguments);

    assertThat(result).isEqualTo("hello world");
  }

  @Test
  void execFail() throws Exception {
    ScriptHost scriptHost = ScriptHost.newBuilder().build();

    // create the script, will be parsed and checked
    Script script = scriptHost.buildScript("1/0 != 0").build();

    assertThatThrownBy(() -> script.execute(String.class, singletonMap("x", "hello world")))
        .isInstanceOf(ScriptExecutionException.class)
        .hasMessage("divide by zero");
  }

  @Test
  void badSyntax() {
    ScriptHost scriptHost = ScriptHost.newBuilder().build();

    assertThatThrownBy(() -> scriptHost.buildScript("-.,").build())
        .isInstanceOf(ScriptCreateException.class)
        .hasMessageStartingWith(
            "parse failed: ERROR: <input>:1:3: Syntax error: mismatched input ',' expecting IDENTIFIER");
  }

  @Test
  void checkFailure() {
    ScriptHost scriptHost = ScriptHost.newBuilder().build();

    assertThatThrownBy(() -> scriptHost.buildScript("x").build())
        .isInstanceOf(ScriptCreateException.class)
        .hasMessageStartingWith(
            "check failed: ERROR: <input>:1:1: undeclared reference to 'x' (in container '')");
  }
}
