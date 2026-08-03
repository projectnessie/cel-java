# CEL-Java

[![CI](https://github.com/projectnessie/cel-java/actions/workflows/main.yml/badge.svg)](https://github.com/projectnessie/cel-java/actions/workflows/main.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.projectnessie.cel/cel-core)](https://search.maven.org/artifact/org.projectnessie.cel/cel-core)

CEL-Java is a Java implementation of the
[Common Expression Language (CEL)](https://github.com/google/cel-spec). It provides parsing, type
checking, and reusable expression evaluation for Java, Protocol Buffer, and Jackson-backed values.

CEL is well suited to application-owned policy conditions, validation rules, filters, and other
small expressions evaluated against structured data. The host application defines the available
types, variables, and functions and remains responsible for authorization policy, resource limits,
and failure handling.

## Documentation

- [Getting started](docs/getting-started.md)
- [Concepts](docs/index.md#start-here)
- [Integration guides](docs/index.md#integration-guides)
  - [Protocol Buffers](docs/guides/protobuf.md)
  - [Jackson](docs/guides/jackson.md)
  - [Bundled extensions](docs/guides/extensions.md)
  - [Authorization expressions](docs/guides/authorization.md)
  - [Custom functions](docs/guides/custom-functions.md)
- [Advanced use](docs/index.md#advanced-use)
  - [Resource controls and cancellation](docs/advanced/resource-controls-and-cancellation.md)
- [Internals and optimization](docs/index.md#internals)
  - [Optimization mechanisms](docs/internals/optimization-mechanisms.md)
  - [Native evaluation](docs/internals/native-evaluation.md)
- [Reference](docs/index.md#reference)
  - [Artifacts](docs/reference/artifacts.md)
  - [Configuration options](docs/reference/configuration-options.md)
  - [Supported types](docs/reference/supported-types.md)
  - [Compatibility and limitations](docs/reference/compatibility-and-limitations.md)

The [documentation index](docs/index.md) is the complete map.

## Installation

Use the CEL-Java BOM, `cel-tools`, and exactly one generated Protobuf artifact.

Gradle:

```groovy
dependencies {
  implementation(enforcedPlatform("org.projectnessie.cel:cel-bom:0.8.0"))
  implementation("org.projectnessie.cel:cel-generated-pb")
  implementation("org.projectnessie.cel:cel-tools")
}
```

Maven:

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
    <artifactId>cel-generated-pb</artifactId>
  </dependency>
  <dependency>
    <groupId>org.projectnessie.cel</groupId>
    <artifactId>cel-tools</artifactId>
  </dependency>
</dependencies>
```

Use `cel-generated-pb3` instead of `cel-generated-pb` when the application must remain on the
Protobuf 3 runtime line. The two artifacts define the same generated class names and must not be on
one classpath together.

See [Artifacts](docs/reference/artifacts.md) for Jackson modules, `cel-core`, and the shaded
`cel-standalone` option.

## First expression

Configure a reusable `ScriptCompiler`, compile each distinct source once, and evaluate the resulting
`Script` repeatedly:

```java
import java.util.Map;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptCompiler;
import org.projectnessie.cel.tools.ScriptException;

public final class Greeting {
  private final Script script;

  public Greeting() throws ScriptException {
    var firstDeclaration = Decls.newVar("first", Decls.String);
    var lastDeclaration = Decls.newVar("last", Decls.String);
    var builder = ScriptCompiler.newBuilder().withDeclarations(firstDeclaration, lastDeclaration);
    script = builder.build().compile("first + ' ' + last");
  }

  public String greet(String first, String last) throws ScriptException {
    return script.execute(String.class, Map.of("first", first, "last", last));
  }
}
```

`ScriptCompiler` and a compiled `Script` are reusable after construction. Their custom adapters,
providers, functions, and libraries must support the concurrent access they receive. Input maps and
all reachable values must not be mutated while evaluation is running.

The lower-level `Env` and `Program` APIs expose partial evaluation, state tracking, custom
environments, and detailed planning control. See
[Execution lifecycle](docs/concepts/execution-lifecycle.md) and
[Environments and programs](docs/advanced/environments-and-programs.md).

## Highlights

- CEL parsing, type checking, evaluation, and CEL-Spec conformance tests
- Java 17 runtime target
- Protocol Buffer 3 and 4 (current-runtime) generated artifact choices
- Jackson 2 and Jackson 3 object integrations
- reusable custom function libraries
- bundled optional, string, math, network, encoder, and Protocol Buffer extensions
- partial evaluation, unknown values, residual expressions, and evaluation-state tracking
- program-creation-time constant optimization
- native evaluation over eligible Java-native representations with semantic fallback
- shaded standalone artifact for dependency isolation
- GraalVM/Quarkus native-image smoke coverage

Here, **native evaluation** means direct interpretation over supported Java values. It does not mean
generated Java, generated bytecode, JNI, or CPU-native compiled expressions. See
[Native evaluation](docs/internals/native-evaluation.md).

## Security and resource use

CEL restricts side effects and general recursion, but an expression can still be expensive for
large inputs or through comprehensions, regular expressions, and custom functions. CEL-Java
provides cooperative cancellation, elapsed and executing-thread CPU/allocation budgets, structural
AST admission, and parser recursion/source-size limits. These controls are not process isolation
and do not preempt arbitrary host callbacks.

Applications accepting untrusted expressions or inputs should combine those controls with an
admission policy, bounded inputs and functions, deployment isolation, and fail-closed handling
appropriate to the use case. See
[Resource controls and cancellation](docs/advanced/resource-controls-and-cancellation.md) and
[Compatibility and limitations](docs/reference/compatibility-and-limitations.md).

Report vulnerabilities according to the [security policy](SECURITY.md).

## Building

Clone with the `googleapis` and `cel-spec` Git submodules, or initialize them after cloning:

```shell
git submodule update --init
```

The repository build requires Java 21; published libraries target Java 17.

```shell
./gradlew spotlessApply check
```

The project uses Google Java style through Spotless. JMH benchmarks are available for targeted
investigation but are not run as part of the normal `check` lifecycle.

See [Contributing](CONTRIBUTING.md) for contribution and formatting guidance and
[Testing CEL integrations](docs/guides/testing-cel-integrations.md) for application-level tests.
