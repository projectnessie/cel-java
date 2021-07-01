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
package org.projectnessie.cel.types.jackson;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptHost;
import org.projectnessie.cel.types.jackson.types.ClassWithEnum;
import org.projectnessie.cel.types.jackson.types.ClassWithEnum.ClassEnum;
import org.projectnessie.cel.types.jackson.types.MetaTest;
import org.projectnessie.cel.types.jackson.types.ObjectListEnum;

public class JacksonScriptHostTest {

  @Test
  void simple() throws Exception {
    ScriptHost scriptHost = ScriptHost.newBuilder().registry(JacksonRegistry.newRegistry()).build();

    Script script =
        scriptHost
            .buildScript("param.author == 'foo@bar.baz'")
            .withDeclarations(Decls.newVar("param", Decls.newObjectType(MetaTest.class.getName())))
            .withTypes(MetaTest.class)
            .build();

    MetaTest cmMatch = MetaTest.builder().author("foo@bar.baz").build();
    MetaTest cmNoMatch = MetaTest.builder().author("foo@foo.foo").build();

    assertThat(script.execute(Boolean.class, singletonMap("param", cmMatch))).isTrue();
    assertThat(script.execute(Boolean.class, singletonMap("param", cmNoMatch))).isFalse();
  }

  @Test
  void complexInput() throws Exception {
    ScriptHost scriptHost = ScriptHost.newBuilder().registry(JacksonRegistry.newRegistry()).build();

    Script script =
        scriptHost
            .buildScript(
                "param.entries[0].type == org.projectnessie.cel.types.jackson.types.ClassWithEnum.ClassEnum.VAL_2")
            .withDeclarations(
                Decls.newVar("param", Decls.newObjectType(ObjectListEnum.class.getName())))
            .withTypes(ObjectListEnum.class)
            .build();

    ObjectListEnum val =
        ObjectListEnum.builder()
            .addEntries(
                ObjectListEnum.Entry.builder()
                    .type(ClassEnum.VAL_2)
                    .holder(new ClassWithEnum("foo"))
                    .build())
            .build();

    assertThat(script.execute(Boolean.class, singletonMap("param", val))).isTrue();

    // same as above, but use the 'container'

    script =
        scriptHost
            .buildScript("param.entries[0].type == ClassWithEnum.ClassEnum.VAL_2")
            .withDeclarations(
                Decls.newVar("param", Decls.newObjectType(ObjectListEnum.class.getName())))
            .withContainer("org.projectnessie.cel.types.jackson.types")
            .withTypes(ObjectListEnum.class)
            .build();

    assertThat(script.execute(Boolean.class, singletonMap("param", val))).isTrue();

    // return the enum

    script =
        scriptHost
            .buildScript("param.entries[0].type")
            .withDeclarations(
                Decls.newVar("param", Decls.newObjectType(ObjectListEnum.class.getName())))
            .withContainer("org.projectnessie.cel.types.jackson.types")
            .withTypes(ObjectListEnum.class)
            .build();

    assertThat(script.execute(Integer.class, singletonMap("param", val)))
        .isEqualTo(ClassEnum.VAL_2.ordinal());
  }
}
