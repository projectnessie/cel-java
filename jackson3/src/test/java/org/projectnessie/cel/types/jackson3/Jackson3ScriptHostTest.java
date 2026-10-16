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
package org.projectnessie.cel.types.jackson3;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.StringT.stringOf;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.ObjectT;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptCompiler;
import org.projectnessie.cel.tools.ScriptHost;
import org.projectnessie.cel.types.jackson3.types.AnEnum;
import org.projectnessie.cel.types.jackson3.types.ArrayObject;
import org.projectnessie.cel.types.jackson3.types.ClassWithEnum;
import org.projectnessie.cel.types.jackson3.types.ClassWithEnum.ClassEnum;
import org.projectnessie.cel.types.jackson3.types.InnerType;
import org.projectnessie.cel.types.jackson3.types.MetaTest;
import org.projectnessie.cel.types.jackson3.types.MyPojo;
import org.projectnessie.cel.types.jackson3.types.ObjectListEnum;

@SuppressWarnings("deprecation")
public class Jackson3ScriptHostTest {

  @Test
  void scriptCompilerConfiguresJacksonTypesBeforeCompilingSources() throws Exception {
    for (TypeRegistry registry :
        List.of(Jackson3Registry.newRegistry(), Jackson3Registry.newExactAggregateRegistry())) {
      ScriptCompiler compiler =
          ScriptCompiler.newBuilder()
              .registry(registry)
              .withDeclarations(
                  Decls.newVar("param", Decls.newObjectType(MetaTest.class.getName())))
              .withTypes(MetaTest.class)
              .build();
      MetaTest value = MetaTest.builder().author("author").build();

      assertThat(compiler.compile("param.author").execute(String.class, Map.of("param", value)))
          .isEqualTo("author");
      assertThat(
              compiler
                  .compile("param.author == 'author'")
                  .execute(Boolean.class, Map.of("param", value)))
          .isTrue();
    }
  }

  @Test
  void repeatedTypesAreIdempotentForGeneralAndExactRegistries() throws Exception {
    for (TypeRegistry registry :
        List.of(Jackson3Registry.newRegistry(), Jackson3Registry.newExactAggregateRegistry())) {
      ScriptHost host = ScriptHost.newBuilder().registry(registry).build();
      for (int i = 0; i < 3; i++) {
        Script script =
            host.buildScript("param.author")
                .withDeclarations(
                    Decls.newVar("param", Decls.newObjectType(MetaTest.class.getName())))
                .withTypes(MetaTest.class)
                .build();
        assertThat(
                script.executeWithActivation(
                    String.class, Map.of("param", MetaTest.builder().author("author").build())))
            .isEqualTo("author");
      }
    }
  }

  @Test
  void standaloneEnum() throws Exception {
    ScriptHost scriptHost =
        ScriptHost.newBuilder().registry(Jackson3Registry.newRegistry()).build();
    String enumConstant = AnEnum.class.getName() + "." + AnEnum.ENUM_VALUE_2.name();

    Script script =
        scriptHost
            .buildScript("value == " + enumConstant)
            .withDeclarations(Decls.newVar("value", Decls.Int))
            .withTypes(AnEnum.class)
            .build();

    assertThat(
            script.executeWithActivation(Boolean.class, singletonMap("value", AnEnum.ENUM_VALUE_2)))
        .isTrue();
  }

  @Test
  void arraysUseTheirAdaptedCelTypes() throws Exception {
    ScriptHost scriptHost =
        ScriptHost.newBuilder().registry(Jackson3Registry.newRegistry()).build();
    String enumConstant = AnEnum.class.getName() + "." + AnEnum.ENUM_VALUE_2.name();
    Script script =
        scriptHost
            .buildScript(
                "param.bytes == b'root'"
                    + " && param.ints[1] == 2"
                    + " && param.longs[0] == 3"
                    + " && param.doubles[0] == 4.5"
                    + " && param.strings[0] == 'string'"
                    + " && param.boxedInts[0] == 5"
                    + " && param.uints[0] == 6u"
                    + " && param.enums[0] == "
                    + enumConstant
                    + " && param.objects[0].intProp == 7"
                    + " && param.dynamic[0] == 'dynamic'"
                    + " && param.values[0] == 'value'"
                    + " && param.nestedInts[0][1] == 9"
                    + " && param.nestedBytes[0] == b'bytes'")
            .withDeclarations(
                Decls.newVar("param", Decls.newObjectType(ArrayObject.class.getName())))
            .withTypes(ArrayObject.class)
            .build();

    assertThat(script.executeWithActivation(Boolean.class, singletonMap("param", arrayObject())))
        .isTrue();
  }

  private static ArrayObject arrayObject() {
    ArrayObject value = new ArrayObject();
    value.bytes = "root".getBytes(StandardCharsets.UTF_8);
    value.ints = new int[] {1, 2};
    value.longs = new long[] {3};
    value.doubles = new double[] {4.5d};
    value.strings = new String[] {"string"};
    value.boxedInts = new Integer[] {5};
    value.uints = new ULong[] {ULong.valueOf(6)};
    value.enums = new AnEnum[] {AnEnum.ENUM_VALUE_2};
    InnerType object = new InnerType();
    object.intProp = 7;
    value.objects = new InnerType[] {object};
    value.dynamic = new Object[] {"dynamic"};
    value.values = new Val[] {stringOf("value")};
    value.nestedInts = new int[][] {{8, 9}};
    value.nestedBytes = new byte[][] {"bytes".getBytes(StandardCharsets.UTF_8)};
    return value;
  }

  @Test
  void simple() throws Exception {
    ScriptHost scriptHost =
        ScriptHost.newBuilder().registry(Jackson3Registry.newRegistry()).build();

    Script script =
        scriptHost
            .buildScript("param.author == 'foo@bar.baz'")
            .withDeclarations(Decls.newVar("param", Decls.newObjectType(MetaTest.class.getName())))
            .withTypes(MetaTest.class)
            .build();

    MetaTest cmMatch = MetaTest.builder().author("foo@bar.baz").build();
    MetaTest cmNoMatch = MetaTest.builder().author("foo@foo.foo").build();

    assertThat(script.executeWithActivation(Boolean.class, singletonMap("param", cmMatch)))
        .isTrue();
    assertThat(script.executeWithActivation(Boolean.class, singletonMap("param", cmNoMatch)))
        .isFalse();

    script =
        scriptHost
            .buildScript("param")
            .withDeclarations(Decls.newVar("param", Decls.newObjectType(MetaTest.class.getName())))
            .withTypes(MetaTest.class)
            .build();

    assertThat(script.executeWithActivation(Object.class, singletonMap("param", cmMatch)))
        .isEqualTo(cmMatch);
    assertThat(script.executeWithActivation(ObjectT.class, singletonMap("param", cmMatch)).value())
        .isEqualTo(cmMatch);
  }

  @Test
  void readmeExample() throws Exception {
    ScriptHost scriptHost =
        ScriptHost.newBuilder().registry(Jackson3Registry.newRegistry()).build();

    Script script =
        scriptHost
            .buildScript("inp.property == checkName")
            .withDeclarations(
                Decls.newVar("inp", Decls.newObjectType(MyPojo.class.getName())),
                Decls.newVar("checkName", Decls.String))
            .withTypes(MyPojo.class)
            .build();

    MyPojo pojo = new MyPojo();
    pojo.setProperty("test");

    String checkName = "test";

    Map<String, Object> arguments = new HashMap<>();
    arguments.put("inp", pojo);
    arguments.put("checkName", checkName);

    assertThat(script.executeWithActivation(Boolean.class, arguments)).isTrue();
  }

  @Test
  void complexInput() throws Exception {
    ScriptHost scriptHost =
        ScriptHost.newBuilder().registry(Jackson3Registry.newRegistry()).build();

    Script script =
        scriptHost
            .buildScript(
                "param.entries[0].type == org.projectnessie.cel.types.jackson3.types.ClassWithEnum.ClassEnum.VAL_2")
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

    assertThat(script.executeWithActivation(Boolean.class, singletonMap("param", val))).isTrue();

    // same as above, but use the 'container'

    script =
        scriptHost
            .buildScript("param.entries[0].type == ClassWithEnum.ClassEnum.VAL_2")
            .withDeclarations(
                Decls.newVar("param", Decls.newObjectType(ObjectListEnum.class.getName())))
            .withContainer("org.projectnessie.cel.types.jackson3.types")
            .withTypes(ObjectListEnum.class)
            .build();

    assertThat(script.executeWithActivation(Boolean.class, singletonMap("param", val))).isTrue();

    // return the enum

    script =
        scriptHost
            .buildScript("param.entries[0].type")
            .withDeclarations(
                Decls.newVar("param", Decls.newObjectType(ObjectListEnum.class.getName())))
            .withContainer("org.projectnessie.cel.types.jackson3.types")
            .withTypes(ObjectListEnum.class)
            .build();

    assertThat(script.executeWithActivation(Integer.class, singletonMap("param", val)))
        .isEqualTo(ClassEnum.VAL_2.ordinal());
  }
}
