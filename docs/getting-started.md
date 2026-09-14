# Getting started

CEL-Java lets an application parse, type-check, and repeatedly evaluate Common
Expression Language (CEL) expressions against Java values. The `cel-tools`
module provides the shortest path from an expression to a reusable script.

## Add the dependencies

Import the CEL-Java BOM, add `cel-tools`, and select exactly one generated
Protobuf artifact:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.projectnessie.cel</groupId>
      <artifactId>cel-bom</artifactId>
      <version>0.8.0</version>
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
  <dependency>
    <groupId>org.projectnessie.cel</groupId>
    <artifactId>cel-generated-pb</artifactId>
  </dependency>
</dependencies>
```

The equivalent Gradle configuration is:

```groovy
dependencies {
  implementation enforcedPlatform("org.projectnessie.cel:cel-bom:0.8.0")
  implementation "org.projectnessie.cel:cel-tools"
  implementation "org.projectnessie.cel:cel-generated-pb"
}
```

Use `cel-generated-pb3` instead of `cel-generated-pb` when the application
uses the Protobuf 3 line. Do not add both generated artifacts. See
[Artifacts and dependency selection](reference/artifacts.md) for the complete
module guide.

## Compile and evaluate an expression

This Java 17 example declares one input, compiles an expression, and evaluates
it with a Java value:

```java
import java.util.Map;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptCompiler;
import org.projectnessie.cel.tools.ScriptException;

public final class GreetingExample {
  public static void main(String[] args) throws ScriptException {
    ScriptCompiler compiler =
        ScriptCompiler.newBuilder()
            .withDeclarations(Decls.newVar("name", Decls.String))
            .build();

    Script script = compiler.compile("'Hello, ' + name");

    String greeting =
        script.execute(
            String.class, Map.<String, Object>of("name", "Ada"));

    System.out.println(greeting);
  }
}
```

The declaration tells the checker that `name` is a CEL string. It does not
supply a value. The map passed to `execute()` supplies that value for one
evaluation.

`compile()` parses and type-checks the expression. Keep the resulting `Script`
and reuse it when the same expression is evaluated with different inputs:

```java
String first =
    script.execute(
        String.class, Map.<String, Object>of("name", "Ada"));
String second =
    script.execute(
        String.class, Map.<String, Object>of("name", "Grace"));
```

The compiler and compiled script may also be shared by concurrent callers,
provided that custom adapters, providers, functions, and other supplied
components are safe to share. Do not mutate input values while an evaluation
is using them.

## Choose a result form

Most applications should request the Java result type they need:

```java
String greeting =
    script.execute(
        String.class, Map.<String, Object>of("name", "Ada"));
```

Request the CEL runtime representation when code needs to inspect an ordinary
CEL value or handle an unknown value itself:

```java
import org.projectnessie.cel.common.types.ref.Val;

Val value =
    script.execute(
        Val.class, Map.<String, Object>of("name", "Ada"));
```

Typed execution converts a successful CEL value to the requested Java type.
Raw execution returns a `Val` without that conversion. `Script` still reports
CEL error values as `ScriptExecutionException`; use the lower-level `Program`
API when code must inspect those values directly. See
[Types and values](concepts/types-and-values.md) and
[Errors, unknowns, and partial state](concepts/errors-unknowns-and-partial-state.md)
before using raw values.

## Handle failures

`ScriptCompiler.compile()` throws `ScriptCreateException` when parsing or
type-checking fails. It carries structured checker issues in addition to its
message.

`Script.execute()` throws `ScriptExecutionException` when evaluation produces
a CEL error, or when a typed result cannot represent an unknown value.
Unexpected failures in application-supplied components and conversion code
can still surface as Java runtime exceptions.

CEL evaluation is not a resource sandbox. CEL-Java offers cooperative
cancellation and optional measured/structural limits, but applications with
untrusted expressions or inputs must still define admission, input, function,
isolation, and fail-closed policies. See
[Resource controls and cancellation](advanced/resource-controls-and-cancellation.md).

## Where to go next

- [Execution lifecycle](concepts/execution-lifecycle.md) explains compilation,
  reuse, the lower-level `Env` and `Program` APIs, and configuration ownership.
- [Types and values](concepts/types-and-values.md) explains declarations,
  runtime bindings, Java conversion, null, and absence.
- [Errors, unknowns, and partial state](concepts/errors-unknowns-and-partial-state.md)
  explains the different failure and incomplete-information states.
- [Protobuf integration](guides/protobuf.md) and
  [Jackson integration](guides/jackson.md) cover application object types.
- [Environments and programs](advanced/environments-and-programs.md) covers
  the lower-level API in more depth.
- [Resource controls and cancellation](advanced/resource-controls-and-cancellation.md)
  covers one-shot controlled compilation and evaluation.
- [Configuration options](reference/configuration-options.md) lists the
  supported compiler and evaluator options.
