# Java implementation of Common-Expression-Language (CEL)

[![CI](https://github.com/projectnessie/cel-java/actions/workflows/main.yml/badge.svg)](https://github.com/projectnessie/cel-java/actions/workflows/main.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.projectnessie.cel/cel-core)](https://search.maven.org/artifact/org.projectnessie.cel/cel-core)

This is a Java port of the [Common-Expression-Language (CEL)](https://opensource.google/projects/cel).
The CEL specification can be found [here](https://github.com/google/cel-spec).

## Contents

- [Getting started](#getting-started)
- [Usage](#usage)
  - [Basic scripts](#basic-scripts)
  - [Protobuf objects](#protobuf-objects)
  - [Jackson objects](#jackson-objects)
  - [Authorization-style expressions](#authorization-style-expressions)
  - [Custom functions](#custom-functions)
- [Artifacts](#artifacts)
  - [Which artifact should I use?](#which-artifact-should-i-use)
  - [Dependency-free artifact](#dependency-free-artifact)
- [Implementation notes](#implementation-notes)
  - [Motivation](#motivation)
  - [Arbitrary Java classes](#arbitrary-java-classes)
  - [Unsigned 64-bit `uint`](#unsigned-64-bit-uint)
  - [Native image and package verification](#native-image-and-package-verification)
  - [Not yet implemented](#not-yet-implemented)
  - [Unclear double-to-int rounding behavior](#unclear-double-to-int-rounding-behavior)
- [Building and testing CEL-Java](#building-and-testing-cel-java)

## Getting started

The easiest way to get started is to add the CEL-Java BOM and `cel-tools` to your project.

Maven:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.projectnessie.cel</groupId>
      <artifactId>cel-bom</artifactId>
      <version>0.6.2</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>org.projectnessie.cel</groupId>
    <artifactId>cel-tools</artifactId>
  </dependency>
</dependencies>
```

Gradle:

```groovy
dependencies {
  implementation(enforcedPlatform("org.projectnessie.cel:cel-bom:0.6.2"))
  implementation("org.projectnessie.cel:cel-tools")
}
```

The `cel-bom` artifact is available for CEL-Java version 0.3.0 and newer.

## Usage

### Basic scripts

The `cel-tools` artifact provides `ScriptHost` as a simple entry point for producing reusable
`Script` instances.

```java
import java.util.HashMap;
import java.util.Map;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptHost;

public class MyClass {
  public void myScriptUsage() {
    ScriptHost scriptHost = ScriptHost.newBuilder().build();

    Script script = scriptHost.buildScript("x + ' ' + y")
        .withDeclarations(
            Decls.newVar("x", Decls.String),
            Decls.newVar("y", Decls.String))
        .build();

    Map<String, Object> arguments = new HashMap<>();
    arguments.put("x", "hello");
    arguments.put("y", "world");

    String result = script.execute(String.class, arguments);

    System.out.println(result); // Prints "hello world"
  }
}
```

### Protobuf objects

Protobuf objects and schemas are supported out of the box via `com.google.protobuf:protobuf-java`.

```protobuf
syntax = "proto3";

message MyPojo {
  string Property1 = 1;
}
```

```java
public class MyClass {
  public Boolean evalWithProtobuf() {
    ScriptHost scriptHost = ScriptHost.newBuilder().build();

    Script script =
        scriptHost
            .buildScript("inp.Property1 == checkName")
            .withDeclarations(
                // protobuf types need the type's full name
                Decls.newVar("inp", Decls.newObjectType(MyPojo.getDescriptor().getFullName())),
                Decls.newVar("checkName", Decls.String))
            // protobuf types need the default instance
            .withTypes(MyPojo.getDefaultInstance())
            .build();

    MyPojo pojo = MyPojo.newBuilder().setProperty1("test").build();

    Map<String, Object> arguments = new HashMap<>();
    arguments.put("inp", pojo);
    arguments.put("checkName", "test");

    return script.execute(Boolean.class, arguments);
  }
}
```

### Jackson objects

Plain Java objects can also be exposed through Jackson's bean/property model by using the
`org.projectnessie.cel.types.jackson3.Jackson3Registry` from `org.projectnessie.cel:cel-jackson3`.
Use this registry when the object is not a protobuf message and CEL-Java should read properties the
same way Jackson would serialize them, including JavaBean getters, records, fields, and Jackson
annotations such as `@JsonProperty`.

```java
import org.projectnessie.cel.types.jackson3.Jackson3Registry;

public class MyClass {
  public Boolean evalWithJacksonObject(MyInput input, String checkName) {
    ScriptHost scriptHost = ScriptHost.newBuilder()
        .registry(Jackson3Registry.newRegistry())
        .build();

    Script script = scriptHost.buildScript("inp.name == checkName")
        .withDeclarations(
            // types for Jackson need the fully qualified class name
            Decls.newVar("inp", Decls.newObjectType(MyInput.class.getName())),
            Decls.newVar("checkName", Decls.String))
        // Register the Jackson object input type as a java.lang.Class.
        .withTypes(MyInput.class)
        .build();

    Map<String, Object> arguments = new HashMap<>();
    arguments.put("inp", input);
    arguments.put("checkName", checkName);

    return script.execute(Boolean.class, arguments);
  }
}
```

Jackson field names are used as CEL-Java property names. It is not necessary to annotate plain Java
classes with Jackson annotations.

To use Jackson 3, add `cel-jackson3` in addition to `cel-tools` or `cel-core`:

```groovy
dependencies {
  implementation(enforcedPlatform("org.projectnessie.cel:cel-bom:0.6.2"))
  implementation("org.projectnessie.cel:cel-tools")
  implementation("org.projectnessie.cel:cel-jackson3")
}
```

Jackson 2 support is similar:

- Use `JacksonRegistry` from `org.projectnessie.cel.types.jackson.JacksonRegistry`.
- Use `org.projectnessie.cel:cel-jackson` instead of `org.projectnessie.cel:cel-jackson3`.

### Authorization-style expressions

CEL-Java can be embedded behind an application-owned authorization decision point. CEL evaluates the
expression, but the application remains responsible for defining available attributes, principal and
role semantics, resource inheritance, and the final fail-closed decision.

An authorization expression can be compiled once and evaluated many times with different arguments:

```java
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptException;
import org.projectnessie.cel.tools.ScriptHost;

public class AuthorizationExample {
  private static final String EXPRESSION =
      "resource.service == \"storage.googleapis.com\""
          + " && resource.type == \"storage.googleapis.com/Object\""
          + " && resource.name.startsWith(\"projects/_/buckets/example/objects/reports/\")"
          + " && request.time < timestamp(\"2026-08-01T00:00:00Z\")";

  private final Script condition;

  public AuthorizationExample() throws ScriptException {
    condition =
        ScriptHost.newBuilder()
            .build()
            .buildScript(EXPRESSION)
            .withDeclarations(
                Decls.newVar("resource.service", Decls.String),
                Decls.newVar("resource.type", Decls.String),
                Decls.newVar("resource.name", Decls.String),
                Decls.newVar("request.time", Decls.Timestamp))
            .build();
  }

  public boolean grants(String service, String type, String name, Instant requestTime) {
    Map<String, Object> arguments = new HashMap<>();
    arguments.put("resource.service", service);
    arguments.put("resource.type", type);
    arguments.put("resource.name", name);
    arguments.put("request.time", requestTime);

    try {
      return Boolean.TRUE.equals(condition.execute(Boolean.class, arguments));
    } catch (ScriptException | RuntimeException e) {
      return false;
    }
  }
}
```

For authorization uses, parse/check failures while building the `Script`, runtime errors while
evaluating it, unknown results, and non-boolean results should be treated as non-granting unless the
host application intentionally defines different behavior. The broad `RuntimeException` catch above
is intentional at the authorization boundary because native conversion failures and unexpected
evaluation failures should not grant access.

### Custom functions

Custom functions can be added by implementing the
[`org.projectnessie.cel.Library`](./core/src/main/java/org/projectnessie/cel/Library.java)
interface. The interface provides declarations via `List<EnvOption> getCompileOptions()` and
runtime implementations via `List<ProgramOption> getProgramOptions()`.

Examples are:

- [`StdLibrary`](./core/src/main/java/org/projectnessie/cel/Library.java)
- [`StringsLib`](./core/src/main/java/org/projectnessie/cel/extension/StringsLib.java)
- [`MyLib` in `ScriptHostTest`](./tools/src/test/java/org/projectnessie/cel/tools/ScriptHostTest.java)
- CEL-Go examples for [encoders](https://github.com/google/cel-go/blob/master/ext/encoders.go) and
  [strings](https://github.com/google/cel-go/blob/master/ext/strings.go)

Receiver-style functions are declared with `Decls.newInstanceOverload(...)`; the receiver value is
passed to the runtime function as the first argument. This is the right place for application-specific
helpers such as resource-name parsing. Such helpers are host extensions, not portable CEL standard
functions.

`ScriptHost` currently builds scripts with CEL's standard library. Hosts that need stricter function
or macro subsets can use the lower-level `Env.newCustomEnv(...)` API; `ScriptHost` does not
currently expose a no-standard-library construction option.

## Artifacts

### Which artifact should I use?

| Need | Use |
| --- | --- |
| Normal embedding with `ScriptHost` | `cel-tools` |
| Dependency isolation / relocated protobuf dependencies | `cel-standalone` |
| Jackson 3 object access | `cel-tools` or `cel-core` plus `cel-jackson3` |
| Jackson 2 object access | `cel-tools` or `cel-core` plus `cel-jackson` |

Use either `cel-tools` or `cel-standalone`, never both.

### Dependency-free artifact

The `org.projectnessie.cel:cel-standalone` artifact contains everything from CEL-Java and has no
dependencies. It comes with relocated protobuf dependencies.

Using `cel-standalone` is especially useful when your project requires different versions of
`protobuf-java`.

If you need CEL-Java's Jackson functionality, include the Jackson dependencies in your project.

## Implementation notes

### Motivation

The [Common Expression Language](https://github.com/google/cel-spec/) allows simple computations
against data structures.

[Project Nessie](https://projectnessie.org/) aims to use CEL to enforce security policies and for
various filtering expressions.

This Java implementation of CEL is based on the [CEL-Go](https://github.com/google/cel-go)
implementation.

Typed data structures should be defined using protobuf, but arbitrary data structures using Java
wrapper data types, lists, and maps work too.

For example, this expression from the
[CEL-Go codelab exercise7](https://github.com/google/cel-go/blob/master/codelab/solution/codelab.go)
checks whether the `extra_claims` map of a JWT contains an entry with a key starting with `group`
and a value ending with `@acme.co`:

```groovy
jwt.extra_claims.exists(c, c.startsWith('group'))
  && jwt.extra_claims.filter(c, c.startsWith('group'))
    .all(c, jwt.extra_claims[c]
    .all(g, g.endsWith('@acme.co')))
```

The JWT argument can be represented as a map:

```java
import java.util.List;
import java.util.Map;

Map<String, Object> jwt = Map.of(
    "jwt", Map.of(
            "sub", "serviceAccount:delegate@acme.co",
            "aud", "my-project",
            "iss", "auth.acme.com:12350",
            "extra_claims", Map.of(
                "group1", List.of("admin@acme.co", "analyst@acme.co"),
                "labels", List.of("metadata", "prod", "pii"),
                "groupN", List.of("forever@acme.co")
            )
        )
    );
```

### Arbitrary Java classes

CEL-Java does not support access to arbitrary Java classes. This means you cannot access standard
Java functionality from a CEL expression, nor is it intended or planned to do so.

CEL is intentionally non-Turing-complete: it ends in a finite amount of time and has no loops or
other blocking operations.

Use [custom functions](#custom-functions) to provide application-owned functionality to CEL scripts.

### Unsigned 64-bit `uint`

The [CEL type system](https://github.com/google/cel-spec/blob/master/doc/langdef.md#values) has two
64-bit integer types: signed `int` and unsigned `uint`. Objects and fields of different types must
be explicitly cast in CEL. The Java wrapper type for CEL-Java's unsigned `uint` is
`org.projectnessie.cel.common.ULong`.

If you do not explicitly define a `uint` type or indirectly use `uint` via protobuf, you will
probably never notice it.

Java does not have a native primitive `uint32` or `uint64`. To maintain conformance to the CEL spec,
CEL-Java treats CEL's `uint` type differently from `int`. For example, `123 == 123u` is not true,
but `123u == 123u` and `123 == 123` are.

If you have a `uint32` or `uint64` in protobuf objects, or use `uint`s in CEL expressions, wrap those
values with `org.projectnessie.cel.common.ULong`.

### Native image and package verification

Native-image and package behavior must be verified in the consuming application's exact build.
`cel-standalone` can reduce dependency conflicts by relocating protobuf dependencies, but it does not
prove Quarkus native-image or package compatibility for every application.

Before using CEL conditions in release-critical authorization paths, run JVM condition tests, the
consuming project's normal build, dependency tree review for protobuf/ANTLR/Jackson conflicts, and
package/native-image verification if native execution is part of the release path.

### Not yet implemented

- JSON extension ([see spec](https://github.com/google/cel-spec/blob/master/doc/langdef.md#json-data-conversion)
  and for example `nonFinite` in `com_github_golang_protobuf/jsonpb/decode.go`, around line 441)
- Encoders extension ([like in Go](https://github.com/google/cel-go/blob/master/ext/encoders.go)),
  not difficult to port to Java, but work to be done at some point.

### Unclear double-to-int rounding behavior

Rounding/truncating of numeric values, especially when converting the CEL type `double` to `int` or
`uint`, is ambiguous. The CEL spec says: _CEL provides no way to control the finer points of
floating-point arithmetic, such as expression evaluation, **rounding mode**, or exception handling.
However, any two not-a-number values will compare equal even if their underlying properties are
different._ ([see spec](https://github.com/google/cel-spec/blob/master/doc/langdef.md#numeric-values)).

The CEL-Go unit test `common/types/double_test.go/TestDoubleConvertToType` asserts on `-5` for the
CEL expression `int(-4.5)`, because CEL-Go uses `math.Round(float64)`.

Since the CEL spec is not clear, and the CEL conformance tests assert on double-to-int truncation
(Java-like: `double doubleValue; long res = (long) doubleValue;`), CEL-Java implements the behavior
that passes the CEL-spec conformance tests.

Go's `math.Round(float64)` behaves differently from Java's `Math.round(double)` or `Math.rint()`,
so a 1:1 port of the CEL-Go behavior is not trivial.

The CEL-Go implementation does not pass these CEL-spec conformance tests:

```text
--- FAIL: TestSimpleFile/conversions/int/double_truncate (0.01s)
    simple_test.go:219: double_truncate: Eval got [int64_value:2], want [int64_value:1]
--- FAIL: TestSimpleFile/conversions/int/double_truncate_neg (0.01s)
    simple_test.go:219: double_truncate_neg: Eval got [int64_value:-8], want [int64_value:-7]
--- FAIL: TestSimpleFile/conversions/int/double_half_pos (0.01s)
    simple_test.go:219: double_half_pos: Eval got [int64_value:12], want [int64_value:11]
--- FAIL: TestSimpleFile/conversions/int/double_half_neg (0.01s)
    simple_test.go:219: double_half_neg: Eval got [int64_value:-4], want [int64_value:-3]
--- FAIL: TestSimpleFile/conversions/uint/double_truncate (0.01s)
    simple_test.go:219: double_truncate: Eval got [uint64_value:2], want [uint64_value:1]
--- FAIL: TestSimpleFile/conversions/uint/double_half (0.01s)
    simple_test.go:219: double_half: Eval got [uint64_value:26], want [uint64_value:25]
```

## Building and testing CEL-Java

The CEL-Java repo uses git submodules to pull in required APIs from Google and the CEL spec.
Those submodules are required to build the CEL-Java project.

Run `git submodule init` and `git submodule update` after a fresh clone.

Build requirements:

- Java 21 or newer
- Gradle wrapper from this repository

Runtime requirements:

- Java 8 or newer

`./gradlew publishToMavenLocal` deploys the current development version to the local Maven
repository, in case you want to use CEL-Java snapshot artifacts from another project.

`./gradlew test` builds the production code and runs the unit tests.

The project uses Google Java style and the Spotless plugin. Run `./gradlew spotlessApply` to fix
formatting issues.

To run the CEL-spec conformance tests, Go, Bazel, and their toolchains are required. From the
CEL-Java repo, run `conformance/run-conformance-tests.sh`. That script performs the necessary Gradle
and Bazel builds.
