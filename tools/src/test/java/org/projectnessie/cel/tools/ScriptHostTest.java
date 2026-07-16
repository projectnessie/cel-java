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
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.StringT.stringOf;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.IntT;
import org.projectnessie.cel.common.types.ListT;
import org.projectnessie.cel.common.types.MapT;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.functions.Overload;
import org.projectnessie.cel.toolstests.Dummy;

class ScriptHostTest {
  private static final String AUTHORIZATION_EXPRESSION =
      "resource.service == \"storage.googleapis.com\""
          + " && resource.type == \"storage.googleapis.com/Object\""
          + " && resource.name.startsWith(\"projects/_/buckets/example/objects/reports/\")"
          + " && request.time < timestamp(\"2026-08-01T00:00:00Z\")";

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
  void function() throws Exception {
    ScriptHost scriptHost = ScriptHost.newBuilder().build();

    // create the script, will be parsed and checked
    Script script =
        scriptHost
            .buildScript("x + ' ' + y")
            // Variable declarations - we need `x` and `y` in this example
            .withDeclarations(Decls.newVar("x", Decls.String), Decls.newVar("y", Decls.String))
            .build();

    String result =
        script.execute(
            String.class,
            arg -> {
              if ("x".equals(arg)) {
                return "hello";
              } else if ("y".equals(arg)) {
                return "world";
              } else {
                return null;
              }
            });

    assertThat(result).isEqualTo("hello world");
  }

  @Test
  void executeValReturnsCelNativeResult() throws Exception {
    ScriptHost scriptHost = ScriptHost.newBuilder().build();

    Script listScript = scriptHost.buildScript("[1, 2, 3]").build();
    Val list = listScript.execute(Val.class, Collections.emptyMap());
    assertThat(list).isInstanceOf(ListT.class);
    assertThat((Object[]) list.value()).containsExactly(1L, 2L, 3L);

    Script mapScript = scriptHost.buildScript("{\"a\": 1, \"b\": 2}").build();
    Val map = mapScript.execute(Val.class, Collections.emptyMap());
    assertThat(map).isInstanceOf(MapT.class);
    Map<String, Long> expectedMap = new HashMap<>();
    expectedMap.put("a", 1L);
    expectedMap.put("b", 2L);
    assertThat(map.value()).isEqualTo(expectedMap);
  }

  @Test
  void executeObjectStillConvertsToNativeResult() throws Exception {
    Script script = ScriptHost.newBuilder().build().buildScript("[1, 2, 3]").build();

    Object result = script.execute(Object.class, Collections.emptyMap());

    assertThat(result).isEqualTo(Arrays.asList(1L, 2L, 3L));
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

  @Test
  void library() throws Exception {
    class MyLib implements Library {
      @Override
      public List<EnvOption> getCompileOptions() {
        return Collections.singletonList(
            EnvOption.declarations(
                Decls.newFunction(
                    "foo", Decls.newOverload("foo_void", Collections.emptyList(), Decls.Int))));
      }

      @Override
      public List<ProgramOption> getProgramOptions() {
        return Collections.singletonList(
            ProgramOption.functions(Overload.function("foo", e -> IntT.intOf(42))));
      }
    }

    ScriptHost scriptHost = ScriptHost.newBuilder().build();

    Script script = scriptHost.buildScript("foo()").withLibraries(new MyLib()).build();

    assertThat(script.execute(Integer.class, Collections.emptyMap())).isEqualTo(42);
  }

  @Test
  void readmeExample() throws Exception {
    ScriptHost scriptHost = ScriptHost.newBuilder().build();

    Script script =
        scriptHost
            .buildScript("inp.Property1 == checkName")
            .withDeclarations(
                Decls.newVar(
                    "inp", Decls.newObjectType(Dummy.MyPojo.getDescriptor().getFullName())),
                Decls.newVar("checkName", Decls.String))
            .withTypes(Dummy.MyPojo.getDefaultInstance())
            .build();

    Dummy.MyPojo pojo = Dummy.MyPojo.newBuilder().setProperty1("test").build();

    String checkName = "test";

    Map<String, Object> arguments = new HashMap<>();
    arguments.put("inp", pojo);
    arguments.put("checkName", checkName);

    assertThat(script.execute(Boolean.class, arguments)).isTrue();
  }

  @Test
  void authorizationExpressionExample() throws Exception {
    Script script = authorizationScript();

    assertThat(grants(script, authorizationArguments("storage.googleapis.com"))).isTrue();
    assertThat(grants(script, authorizationArguments("compute.googleapis.com"))).isFalse();
  }

  @Test
  void authorizationExpressionCompileOnceEvaluateMany() throws Exception {
    Script script = authorizationScript();

    assertThat(
            grants(
                script,
                authorizationArguments(
                    "storage.googleapis.com",
                    "storage.googleapis.com/Object",
                    "projects/_/buckets/example/objects/reports/q1.csv",
                    Instant.parse("2026-07-31T23:59:59Z"))))
        .isTrue();
    assertThat(
            grants(
                script,
                authorizationArguments(
                    "compute.googleapis.com",
                    "storage.googleapis.com/Object",
                    "projects/_/buckets/example/objects/reports/q1.csv",
                    Instant.parse("2026-07-31T23:59:59Z"))))
        .isFalse();
    assertThat(
            grants(
                script,
                authorizationArguments(
                    "storage.googleapis.com",
                    "storage.googleapis.com/Bucket",
                    "projects/_/buckets/example/objects/reports/q1.csv",
                    Instant.parse("2026-07-31T23:59:59Z"))))
        .isFalse();
    assertThat(
            grants(
                script,
                authorizationArguments(
                    "storage.googleapis.com",
                    "storage.googleapis.com/Object",
                    "projects/_/buckets/example/objects/private/q1.csv",
                    Instant.parse("2026-07-31T23:59:59Z"))))
        .isFalse();
    assertThat(
            grants(
                script,
                authorizationArguments(
                    "storage.googleapis.com",
                    "storage.googleapis.com/Object",
                    "projects/_/buckets/example/objects/reports/q1.csv",
                    Instant.parse("2026-08-01T00:00:00Z"))))
        .isFalse();
  }

  @Test
  void authorizationExpressionRejectsNonBooleanResult() throws Exception {
    Script script = ScriptHost.newBuilder().build().buildScript("\"not a decision\"").build();

    assertThat(grants(script, Collections.emptyMap())).isFalse();
  }

  @Test
  void authorizationExpressionRuntimeErrorIsNonGranting() throws Exception {
    Script script = authorizationScript();
    Map<String, Object> arguments = authorizationArguments("storage.googleapis.com");
    arguments.remove("resource.name");

    assertThatThrownBy(() -> script.execute(Boolean.class, arguments))
        .isInstanceOf(ScriptExecutionException.class);
    assertThat(grants(script, arguments)).isFalse();
  }

  @Test
  void authorizationExpressionCheckFailureIsDeterministic() {
    assertThatThrownBy(
            () ->
                ScriptHost.newBuilder()
                    .build()
                    .buildScript("resource.name == missing")
                    .withDeclarations(Decls.newVar("resource.name", Decls.String))
                    .build())
        .isInstanceOf(ScriptCreateException.class)
        .hasMessageStartingWith("check failed:");
  }

  @Test
  void customGlobalFunctionLibrary() throws Exception {
    Script script =
        ScriptHost.newBuilder()
            .build()
            .buildScript("getAttribute(\"environment\", \"unknown\") == \"prod\"")
            .withLibraries(new AttributeLibrary("prod"))
            .build();

    assertThat(grants(script, Collections.emptyMap())).isTrue();

    Script defaultScript =
        ScriptHost.newBuilder()
            .build()
            .buildScript("getAttribute(\"missing\", \"unknown\") == \"prod\"")
            .withLibraries(new AttributeLibrary("prod"))
            .build();

    assertThat(grants(defaultScript, Collections.emptyMap())).isFalse();
  }

  @Test
  void customGlobalFunctionErrorIsNonGranting() throws Exception {
    Script script =
        ScriptHost.newBuilder()
            .build()
            .buildScript("getAttribute(\"error\", \"unknown\") == \"prod\"")
            .withLibraries(new AttributeLibrary("prod"))
            .build();

    assertThatThrownBy(() -> script.execute(Boolean.class, Collections.emptyMap()))
        .isInstanceOf(ScriptExecutionException.class)
        .hasMessageContaining("forced getAttribute error");
    assertThat(grants(script, Collections.emptyMap())).isFalse();
  }

  @Test
  void customReceiverFunctionLibrary() throws Exception {
    Script script =
        ScriptHost.newBuilder()
            .build()
            .buildScript(
                "resource.name.extractAfter(\"projects/_/buckets/example/objects/\")"
                    + " == \"reports/q1.csv\"")
            .withDeclarations(Decls.newVar("resource.name", Decls.String))
            .withLibraries(new ExtractAfterLibrary())
            .build();

    assertThat(
            grants(
                script,
                Collections.singletonMap(
                    "resource.name", "projects/_/buckets/example/objects/reports/q1.csv")))
        .isTrue();
    assertThat(
            grants(
                script,
                Collections.singletonMap(
                    "resource.name", "projects/_/buckets/example/objects/private/q1.csv")))
        .isFalse();
  }

  @Test
  void customReceiverFunctionErrorIsNonGranting() throws Exception {
    Script script =
        ScriptHost.newBuilder()
            .build()
            .buildScript("resource.name.extractAfter(\"error\") == \"reports/q1.csv\"")
            .withDeclarations(Decls.newVar("resource.name", Decls.String))
            .withLibraries(new ExtractAfterLibrary())
            .build();

    assertThat(
            grants(
                script,
                Collections.singletonMap(
                    "resource.name", "projects/_/buckets/example/objects/reports/q1.csv")))
        .isFalse();
    assertThatThrownBy(
            () ->
                script.execute(
                    Boolean.class,
                    Collections.singletonMap(
                        "resource.name", "projects/_/buckets/example/objects/reports/q1.csv")))
        .isInstanceOf(ScriptExecutionException.class)
        .hasMessageContaining("forced extractAfter error");
  }

  private static Script authorizationScript() throws ScriptCreateException {
    return ScriptHost.newBuilder()
        .build()
        .buildScript(AUTHORIZATION_EXPRESSION)
        .withDeclarations(
            Decls.newVar("resource.service", Decls.String),
            Decls.newVar("resource.type", Decls.String),
            Decls.newVar("resource.name", Decls.String),
            Decls.newVar("request.time", Decls.Timestamp))
        .build();
  }

  private static Map<String, Object> authorizationArguments(String service) {
    return authorizationArguments(
        service,
        "storage.googleapis.com/Object",
        "projects/_/buckets/example/objects/reports/q1.csv",
        Instant.parse("2026-07-31T23:59:59Z"));
  }

  private static Map<String, Object> authorizationArguments(
      String service, String type, String name, Instant requestTime) {
    Map<String, Object> arguments = new HashMap<>();
    arguments.put("resource.service", service);
    arguments.put("resource.type", type);
    arguments.put("resource.name", name);
    arguments.put("request.time", requestTime);
    return arguments;
  }

  private static boolean grants(Script script, Map<String, Object> arguments) {
    try {
      return Boolean.TRUE.equals(script.execute(Boolean.class, arguments));
    } catch (ScriptException | RuntimeException e) {
      return false;
    }
  }

  private static final class AttributeLibrary implements Library {
    private final String environment;

    private AttributeLibrary(String environment) {
      this.environment = environment;
    }

    @Override
    public List<EnvOption> getCompileOptions() {
      return Collections.singletonList(
          EnvOption.declarations(
              Decls.newFunction(
                  "getAttribute",
                  Decls.newOverload(
                      "get_attribute_string_string",
                      Arrays.asList(Decls.String, Decls.String),
                      Decls.String))));
    }

    @Override
    public List<ProgramOption> getProgramOptions() {
      return Collections.singletonList(
          ProgramOption.functions(
              Overload.binary(
                  "getAttribute",
                  (keyVal, defaultVal) -> {
                    if (!(keyVal instanceof StringT) || !(defaultVal instanceof StringT)) {
                      return newErr("invalid arguments to getAttribute");
                    }
                    String key = keyVal.convertToNative(String.class);
                    if ("error".equals(key)) {
                      return newErr("forced getAttribute error");
                    }
                    if ("environment".equals(key)) {
                      return stringOf(environment);
                    }
                    return defaultVal;
                  })));
    }
  }

  private static final class ExtractAfterLibrary implements Library {
    @Override
    public List<EnvOption> getCompileOptions() {
      return Collections.singletonList(
          EnvOption.declarations(
              Decls.newFunction(
                  "extractAfter",
                  Decls.newInstanceOverload(
                      "string_extract_after_string",
                      Arrays.asList(Decls.String, Decls.String),
                      Decls.String))));
    }

    @Override
    public List<ProgramOption> getProgramOptions() {
      return Collections.singletonList(
          ProgramOption.functions(
              Overload.binary(
                  "extractAfter",
                  (valueVal, prefixVal) -> {
                    if (!(valueVal instanceof StringT) || !(prefixVal instanceof StringT)) {
                      return newErr("invalid arguments to extractAfter");
                    }
                    String value = valueVal.convertToNative(String.class);
                    String prefix = prefixVal.convertToNative(String.class);
                    if ("error".equals(prefix)) {
                      return newErr("forced extractAfter error");
                    }
                    if (value.startsWith(prefix)) {
                      return stringOf(value.substring(prefix.length()));
                    }
                    return stringOf("");
                  })));
    }
  }
}
