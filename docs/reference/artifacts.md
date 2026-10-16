# Artifacts

CEL-Java publishes modules under the Maven group `org.projectnessie.cel`. Use the BOM to keep their
versions aligned.

## Application artifacts

| Artifact | Purpose |
| --- | --- |
| `cel-bom` | Version constraints for the published CEL-Java modules |
| `cel-tools` | Recommended `ScriptCompiler` and `Script` application API; depends on `cel-core` |
| `cel-core` | Parser, checker, runtime, low-level environment/program API, and built-in extensions |
| `cel-generated-pb` | Generated CEL and Google API classes for the current Protobuf runtime |
| `cel-generated-pb3` | The same generated classes for the Protobuf 3 runtime line |
| `cel-jackson` | Jackson 2 type registry and object adapter |
| `cel-jackson3` | Jackson 3 type registry and object adapter |
| `cel-standalone` | Shaded CEL runtime that isolates CEL-Java’s Google/Protobuf and Agrona packages |

Add exactly one of `cel-generated-pb` and `cel-generated-pb3` when using the regular modular
artifacts. They contain the same generated class names and must not appear together on one
classpath.

## Recommended dependency set

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

Replace `0.8.0` with the release selected by your application.

## Choosing the generated Protobuf artifact

Use `cel-generated-pb` unless the application is constrained to Protobuf 3. Use
`cel-generated-pb3` for that older runtime line.

The generated artifact determines the binary signatures of generated CEL protocol classes. It is
not merely a transitive implementation choice, which is why `cel-core`, `cel-tools`, and the
Jackson integrations do not choose one on your behalf.

If another dependency brings in the other generated artifact, exclude it. Do not solve the
conflict by retaining both and relying on classpath order.

## Jackson

Add one Jackson integration alongside the regular dependency set:

```groovy
implementation("org.projectnessie.cel:cel-jackson3")
```

Use `cel-jackson` for Jackson 2 or `cel-jackson3` for Jackson 3. They expose different registry
classes and depend on their corresponding Jackson family. See the [Jackson guide](../guides/jackson.md).

## Standalone artifact

`cel-standalone` combines CEL-Java’s core, tools, generated Protobuf classes, and Jackson integration
classes. It relocates `com.google.*` and `org.agrona.*` into a CEL-Java-owned namespace to avoid
conflicts with application versions of those libraries.

```groovy
implementation("org.projectnessie.cel:cel-standalone:0.8.0")
```

Do not combine `cel-standalone` with `cel-core`, `cel-tools`, or the modular Jackson integration
artifacts. The standalone JAR already contains CEL-Java’s copies of those unrelocated
`org.projectnessie.cel.*` classes.

An application may also use `cel-generated-pb` or `cel-generated-pb3` for its own unrelocated CEL
protocol types. Those classes do not satisfy the standalone API’s Google/Protobuf signatures,
which use the relocated namespace; keep values on the matching side of that boundary.

Jackson itself is not relocated into the standalone JAR. Add the selected Jackson 2 or Jackson 3
runtime dependencies if the application uses the included Jackson registry.

Because the public types used by standalone are compiled against relocated Google/Protobuf types,
code should consistently use the standalone artifact’s API surface. The standalone sources JAR
aggregates the maintained sources from `cel-core`, `cel-tools`, `cel-jackson`, and `cel-jackson3`
for IDE navigation and debugging. It deliberately excludes generated Protobuf and third-party
sources.

Those Java sources are not rewritten when the compiled standalone bytecode relocates dependency
packages. Imports and signatures involving `com.google.*` therefore describe the modular source,
while the standalone classes use `org.projectnessie.cel.relocated.com.google.*`. Treat the sources
as version-matched implementation reference rather than a separately compilable standalone source
distribution, and verify concrete dependency type names against the standalone classes.

## Repository-only modules

`cel-conformance` runs the CEL specification conformance data, and `cel-benchmarks` contains JMH
benchmarks. They are development modules, not application dependencies and not part of the BOM.
