# GraalVM native-image integration

GraalVM native image ahead-of-time compiles a Java application into a platform executable. It is
unrelated to CEL-Java's **native evaluation**, which is an interpreter planning strategy over
supported Java representations. Native evaluation does not generate Java source, bytecode,
machine code, or a GraalVM image. See [native evaluation](../internals/native-evaluation.md) for
that runtime concept.

## What CEL-Java provides

`cel-core` packages native-image metadata for CEL-Java classes that require it. The
`cel-standalone` build merges and relocates the relevant reflection metadata with its relocated
dependencies.

The project also contains Quarkus smoke applications for:

- `cel-standalone`; and
- `cel-core` with `cel-generated-pb3` and `cel-jackson3`.

These checks demonstrate specific project configurations. They do not prove that every consuming
application, framework version, Protobuf/Jackson combination, custom type, library, or packaging
strategy is native-image compatible.

## Verify the consuming application

Native-image reachability is application-specific. Build and execute the final application artifact
with representative CEL conditions:

1. Compile the expressions and type registrations used in production.
2. Evaluate ordinary scalars, aggregates, Protobuf or Jackson objects, and custom extensions.
3. Exercise parse/check failures and runtime CEL errors.
4. Verify any framework-managed reflection, serialization, and resource configuration.
5. Repeat the test after dependency, framework, mapper, generated-code, or shading changes.

Application domain classes, custom Jackson behavior, generated Protobuf messages, custom
functions, and other libraries may need framework or application-owned reachability metadata. Use
the native-image diagnostic and metadata facilities provided by the application's build rather than
assuming CEL-Java metadata covers unrelated application types.

## Artifact selection still matters

Choose the CEL-Java modules that match the packaged runtime:

- exactly one of `cel-generated-pb` and `cel-generated-pb3`;
- `cel-jackson` for Jackson 2 or `cel-jackson3` for Jackson 3; or
- `cel-standalone` when its relocated dependency model fits the application.

Review the resolved dependency graph for Protobuf and Jackson conflicts. Native-image analysis does
not make an incompatible JVM classpath compatible. See the [artifact reference](../reference/artifacts.md).

## Project verification task

CEL-Java maintainers can request the repository's Quarkus native smoke builds with:

```bash
./gradlew testNative
```

The smoke projects enable Quarkus native container builds when this task is requested, so a working
container runtime and the required build environment are prerequisites. Application consumers
should use their own framework's native build and test command for release verification.

## Operational expectations

Native-image packaging can change startup, footprint, reflection behavior, and failure surfaces.
CEL-Java does not promise a particular performance result or universal native-image compatibility.
Treat the native executable as a distinct release artifact and run the same policy and integration
contract tests used on the JVM. See [testing CEL integrations](testing-cel-integrations.md).
