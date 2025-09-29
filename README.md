# Java implementation of Common-Expression-Language (CEL)

[![CI](https://github.com/projectnessie/cel-java/actions/workflows/main.yml/badge.svg)](https://github.com/projectnessie/cel-java/actions/workflows/main.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.projectnessie.cel/cel-core)](https://search.maven.org/artifact/org.projectnessie.cel/cel-core)

This is a Java port of the [Common-Expression-Language (CEL)](https://opensource.google/projects/cel).

The CEL specification can be found [here](https://github.com/google/cel-spec).

## Getting started

The easiest way to get started is to add a dependency to your Maven project
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.projectnessie.cel</groupId>
      <artifactId>cel-bom</artifactId>
      <version>0.5.3</version>
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
or Gradle project.
```groovy
dependencies {
  implementation(enforcedPlatform("org.projectnessie.cel:cel-bom:0.5.3"))
  implementation("org.projectnessie.cel:cel-tools")
}
```

(Note: `cel-bom` is available for CEL-Java version 0.3.0 and newer.)

The `cel-tools` artifact provides a simple entry point `ScriptHost` to produce `Script` instances.
A very simple start:

```java
import com.google.api.expr.v1alpha1.Decl;
import java.util.HashMap;
import java.util.Map;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptHost;

public class MyClass {
  public void myScriptUsage() {
    // Build the script factory
    ScriptHost scriptHost = ScriptHost.newBuilder().build();

    // create the script, will be parsed and checked
    Script script = scriptHost.buildScript("x + ' ' + y")
        .withDeclarations(
            // Variable declarations - we need `x` and `y` in this example
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

## Protobuf and Jackson and plain Java objects

Protobuf (via `com.google.protobuf:protobuf-java`) objects and schema is supported out of the box.

### Protobuf example

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

    String checkName = "test";

    Map<String, Object> arguments = new HashMap<>();
    arguments.put("inp", pojo);
    arguments.put("checkName", checkName);

    Boolean result = script.execute(Boolean.class, arguments);

    return result;
  }
}
```

### Jackson example

It is also possible to use plain Java and Jackson objects as arguments by using the 
`org.projectnessie.cel.types.jackson.JacksonRegistry` in `org.projectnessie.cel:cel-jackson`.

Code sample similar to the one above. It takes a user-provided object type `MyInput`.
```java
import org.projectnessie.cel.types.jackson.JacksonRegistry;

public class MyClass {
  public Boolean evalWithJacksonObject(MyInput input, String checkName) {
    // Build the script factory
    ScriptHost scriptHost = ScriptHost.newBuilder()
        // IMPORTANT: use the Jackson registry
        .registry(JacksonRegistry.newRegistry())
        .build();

    // Create the script, will be parsed and checked.
    // It checks whether the property `name` in the "Jackson-ized" class `MyInput` is
    // equal to the value of `checkName`.
    Script script = scriptHost.buildScript("inp.name == checkName")
        // Variable declarations - we need `inp` +  `checkName` in this example
        .withDeclarations(
            // types for Jackson need the fully qualified class name 
            Decls.newVar("inp", Decls.newObjectType(MyInput.class.getName())),
            Decls.newVar("checkName", Decls.String))
        // Register our Jackson object input type (as a java.lang.Class)
        .withTypes(MyInput.class)
        .build();

    Map<String, Object> arguments = new HashMap<>();
    arguments.put("inp", input);
    arguments.put("checkName", checkName);

    Boolean result = script.execute(Boolean.class, arguments);

    return result;
  }
}
```

Note that the Jackson field-names are used as property names in CEL-Java. It is not necessary to
annotate "plain Java" classes with Jackson annotations.

To use the `JacksonRegistry` in your application code, add the `cel-jackson` dependency in
addition to `cel-core` or `cel-tools`.

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.projectnessie.cel</groupId>
      <artifactId>cel-bom</artifactId>
      <version>0.5.3</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>org.projectnessie.cel</groupId>
    <artifactId>cel-jackson</artifactId>
  </dependency>
  <dependency>
    <groupId>org.projectnessie.cel</groupId>
    <artifactId>cel-tools</artifactId>
  </dependency>
</dependencies>
```
or Gradle project.
```groovy
dependencies {
  implementation(enforcedPlatform("org.projectnessie.cel:cel-bom:0.5.3"))
  implementation("org.projectnessie.cel:cel-tools")
  implementation("org.projectnessie.cel:cel-jackson")
}
```

## Dependency-free artifact

The `org.projectnessie.cel:cel-standalone` contains everything from CEL-Java and has no dependencies.
It comes with relocated protobuf dependencies.

Using `cel-standalone` is especially useful when your project requires different versions of
`protobuf-java`.

If you need CEL-Java's Jackson functionality, include the Jackson dependencies in your project.

Use _either_ `cel-tools` _or_ `cel-standalone` - never both!

## Motivation to have a CEL-Java port

The [Common Expression Language](https://github.com/google/cel-spec/) allows simple computations
against data structures.

[Project Nessie](https://projectnessie.org/) aims to use CEL to enforce security policies and
for various filtering expressions.

This Java implementation of CEL is based on the [CEL-Go](https://github.com/google/cel-go)
implementation.   

Typed data structures should be defined using protobuf, but arbitrary data structures using
Java wrapper data types (like `java.lang.Long`/`Double`/`String`), lists (`java.util.List`) and maps
(`java.util.Map`) work, too.

The following example expression (from the [CEL-Go codelab exercise7](https://github.com/google/cel-go/blob/master/codelab/solution/codelab.go))
```groovy
jwt.extra_claims.exists(c, c.startsWith('group'))
  && jwt.extra_claims.filter(c, c.startsWith('group'))
    .all(c, jwt.extra_claims[c]
    .all(g, g.endsWith('@acme.co')))
```
can be used to check whether the 'extra_claims' map of a JWT contains an entry with a key starting
with `group` and a value ending with `@acme.co`.

The JWT argument can be expressed using a non-protobuf data structure representing the JSON-web-token:
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

## Unsigned 64-bit `uint`

Note that the [CEL type system](https://github.com/google/cel-spec/blob/master/doc/langdef.md#values)
has 2 64-bit integer types: a signed 64-bit integer `int` and an unsigned 64-bit integer `uint`.
Objects/fields of different types must be explicitly casted in CEL. The "primitive" Java wrapper
type class for the 64-bit unsigned `uint` in CEL-Java is `org.projectnessie.cel.common.ULong`.
If you do not explicitly define a `uint` type or indirectly use `uint` via protobuf, you will
probably never notice it.

## Arbitrary Java classes

CEL-Java does *not* support access arbitrary Java classes. This means, you cannot access
"standard Java functionality" from a CEL expression nor is it intended or planned to do so.

CEL is intentionally non-turing-complete, this means it ends in a finite amount of time, has no
loops or other "blocking" operations.

You can however provide own custom functionality as a library, which then provides functions
to CEL scripts running in environments that have been configured to use that library.

## Adding custom functions

Custom functions can be easily added by implementing the [`org.projectnessie.cel.Library`](https://github.com/projectnessie/cel-java/blob/main/core/src/main/java/org/projectnessie/cel/Library.java)
interface. The interface provides the necessary declarations (function definitions) via
`List<EnvOption> getCompileOptions()` and the function implementations via 
`List<ProgramOption> getProgramOptions()`. Examples are
[here (`StdLibrary` class)](./core/src/main/java/org/projectnessie/cel/Library.java),
[here (`StringsLb` class)](./core/src/main/java/org/projectnessie/cel/extension/StringsLib.java),
[here (`MyLib` class)](./tools/src/test/java/org/projectnessie/cel/tools/ScriptHostTest.java),
[here](https://github.com/google/cel-go/blob/master/ext/encoders.go) and
[here](https://github.com/google/cel-go/blob/master/ext/strings.go)

## Building and testing CEL-Java

The CEL-Java repo uses git submodules to pull in required APIs from Google and the CEL-spec.
Those submodules are required to build the CEL-Java project.

You need to run `git submodule init` and `git submodule update` after a fresh clone of this repo.

Build requirements:
* Java 21 or newer, it's a Gradle-wrapper build (it's fast ;) )

Runtime requirements:
* Java 8 or newer

`./gradlew publishToMavenLocal` deploy the current development to the local Maven repo, in
case you want to pull it the CEL-Java "snapshot" artifacts another project.

`./gradlew test` builds the production code and runs the unit tests.

The project uses the Google Java code style and uses the Spotless plugin. Run
`./gradlew spotlessApply` to fix formatting issues.

To run the CEL-spec conformance tests, Go, the bazel build tool plus toolchains are required.
Form the CEL-Java repo, just run `conformance/run-conformance-tests.sh`. That script performs
the necessary Gradle and bazel builds.

## CEL-Java implementation specifics

### Not yet implemented

* JSON extension ([see spec](https://github.com/google/cel-spec/blob/master/doc/langdef.md#json-data-conversion) and for example `nonFinite` in `com_github_golang_protobuf/jsonpb/decode.go`, around line 441)
* Encoders extension ([like in Go](https://github.com/google/cel-go/blob/master/ext/encoders.go)),
  not difficult to port to Java, it's just work to be done at some point.

### Unsigned integer

Java does not have a native (primitive) type "unsigned int/long" or `uint32`/`uint64`.
Support for the CEL type `uint` is therefore a bit more work in Java.

To maintain conformance to the CEL-spec, the CEL-Java implementation treats CEL's `uint` type
differently. This means, that for example the expression `123 == 123u` is *not* true, but
`123u == 123u` and `123 == 123` are.

TL;DR If you have a `uint32`/`uint64` in your protobuf objects or use `uint`s in your CEL
expression, you *must* wrap those with the `org.projectnessie.cel.common.ULong` type.

### Unclear double-to-int rounding behavior

Rounding/truncating of numeric values, especially when converting the CEL type `double` to
`int` or `uint`. The CEL spec says: _CEL provides no way to control the finer points of
floating-point arithmetic, such as expression evaluation, **rounding mode**, or exception handling.
However, any two not-a-number values will compare equal even if their underlying properties are
different._ ([see spec](https://github.com/google/cel-spec/blob/master/doc/langdef.md#numeric-values)).

The technical situation is ambiguous. The CEL-Go unit test
`common/types/double_test.go/TestDoubleConvertToType` asserts on the result `-5` for the CEL
expression `int(-4.5)`, because CEL-Go uses the `math.Round(float64)` function.

Since the CEL-spec is not clear, and the CEL-conformance-tests assert on double-to-int "truncation"
(aka think Java-ish: `double doubleValue; long res = (long) doubleValue;`), the CEL-Java
implementation just implements the functionality that passes the CEL-spec conformance tests.

(Note: the implementation of Go's `math.Round(float64)` behaves differently to Java's
`Math.round(double)` (or `Math.rint()`) and a 1:1 port of the CEL-Go behavior is rather not
that trivial.)

Note: The CEL-Go implementation does not pass the CEL-spec conformance tests:

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
