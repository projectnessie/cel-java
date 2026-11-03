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
package org.projectnessie.cel.standalone.smoke;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.RegexEngine;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptCompiler;
import org.projectnessie.cel.types.jackson.JacksonRegistry;

class StandaloneJackson2SmokeTest {
  @Test
  void evaluatesScriptWithExplicitJackson2AndGeneratedProtobufDependency() throws Exception {
    assertThat(Class.forName("com.fasterxml.jackson.databind.ObjectMapper")).isNotNull();
    assertThat(Class.forName("com.google.api.expr.v1alpha1.Decl")).isNotNull();

    Script script =
        ScriptCompiler.newBuilder()
            .registry(JacksonRegistry.newRegistry())
            .withDeclarations(Decls.newVar("input", Decls.newObjectType(Input.class.getName())))
            .withTypes(Input.class)
            .build()
            .compile("input.name == 'reports' && input.labels.exists(label, label == 'finance')");

    assertThat(script.execute(Boolean.class, singletonMap("input", new Input("reports")))).isTrue();

    Script re2 =
        ScriptCompiler.newBuilder()
            .regexEngine(RegexEngine.RE2)
            .build()
            .compile("'abc'.matches('b')");
    assertThat(re2.execute(Boolean.class, Map.of())).isTrue();
  }

  public static final class Input {
    private final String name;
    private final List<String> labels;

    public Input(String name) {
      this.name = name;
      this.labels = asList("finance", "quarterly");
    }

    public String getName() {
      return name;
    }

    public List<String> getLabels() {
      return labels;
    }
  }
}
