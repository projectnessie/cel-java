/*
 * Copyright (C) 2023 The Authors of CEL-Java
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
package org.projectnessie.cel;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptException;
import org.projectnessie.cel.tools.ScriptHost;

public class ListContainsTest {
  @Test
  public void testInForList() throws ScriptException {
    ScriptHost scriptHost = ScriptHost.newBuilder().build();
    Script script = scriptHost
            .buildScript("!(this in dyn(rules)['in']) ? 'value must be in list' : ''")
            .withDeclarations(
                // Variable declarations - we need `this` and `rules` in this example
                Decls.newVar("this", Decls.String),
                Decls.newVar("rules", Decls.newListType(Decls.String)))
            .build();

    Map<String, Object> arguments = new HashMap<>();
    arguments.put("this", "nothello");
    arguments.put("rules", new String[] {"hello", "world"});

    String result = script.execute(String.class, arguments);
    assertThat(result).isEqualTo("value must be in list");
  }
}
