# API reference

The generated Javadocs are the detailed API reference. This page identifies the intended entry
points and the packages behind each integration.

## Preferred application API

Start with:

- [`ScriptCompiler`](../../tools/src/main/java/org/projectnessie/cel/tools/ScriptCompiler.java) —
  immutable configuration for compiling one or more expressions;
- [`Script`](../../tools/src/main/java/org/projectnessie/cel/tools/Script.java) — reusable,
  application-facing evaluation;
- [`ScriptException`](../../tools/src/main/java/org/projectnessie/cel/tools/ScriptException.java) —
  compilation and execution failure base type.

`ScriptHost` is deprecated. New code should configure a `ScriptCompiler`, then pass expression
source to `compile(...)`.

The relevant package is `org.projectnessie.cel.tools` from `cel-tools`.

## Low-level lifecycle

Use these APIs when the tools layer does not expose a required mode:

- [`Env`](../../core/src/main/java/org/projectnessie/cel/Env.java) — parsing, checking,
  environment extension, program creation, and residual ASTs;
- [`EnvOption`](../../core/src/main/java/org/projectnessie/cel/EnvOption.java) — environment
  configuration;
- [`Ast`](../../core/src/main/java/org/projectnessie/cel/Ast.java) — parsed or checked expression;
- [`Program`](../../core/src/main/java/org/projectnessie/cel/Program.java) — reusable executable;
- [`ProgramOption`](../../core/src/main/java/org/projectnessie/cel/ProgramOption.java) and
  [`EvalOption`](../../core/src/main/java/org/projectnessie/cel/EvalOption.java) — planning and
  evaluation modes;
- [`CEL`](../../core/src/main/java/org/projectnessie/cel/CEL.java) — AST conversions, partial
  activations, attribute patterns, and utility factories.

The primary package is `org.projectnessie.cel` from `cel-core`.

## Types and values

Important contracts in `org.projectnessie.cel.common.types.ref` are:

- `Val` — runtime CEL value;
- `Type` — CEL runtime type descriptor;
- `TypeAdapter` — Java/CEL conversion;
- `TypeProvider` — object type, identifier, and field lookup;
- `TypeRegistry` — provider with native type registration and copying.

Runtime types and traits live in `org.projectnessie.cel.common.types` and
`org.projectnessie.cel.common.types.traits`. Prefer adapters and providers over directly depending
on concrete runtime-value classes.

## Protocol Buffers and Jackson

- Protocol Buffer support is in `org.projectnessie.cel.common.types.pb`, including
  `ProtoTypeRegistry`.
- Jackson 2 support is in `org.projectnessie.cel.types.jackson`, including `JacksonRegistry`.
- Jackson 3 support is in `org.projectnessie.cel.types.jackson3`, including `Jackson3Registry`.

See [Protocol Buffers](../guides/protobuf.md), [Jackson](../guides/jackson.md), and
[Supported types](supported-types.md).

## Functions and libraries

- `Library`, `EnvOption`, and `ProgramOption` package reusable language configuration.
- `org.projectnessie.cel.interpreter.functions.Overload` implements runtime function overloads.
- bundled extensions live under `org.projectnessie.cel.extension`.

See [Bundled extensions](../guides/extensions.md), [Custom functions](../guides/custom-functions.md),
and [Extension points](../advanced/extension-points.md).

## Generate Javadocs locally

The repository uses per-module Gradle Javadoc tasks:

```shell
./gradlew :cel-core:javadoc :cel-tools:javadoc :cel-jackson:javadoc :cel-jackson3:javadoc
```

Output is written below each module’s `build/docs/javadoc/` directory.

Building requires the repository’s Git submodules and a Java 21 toolchain; published libraries
target Java 17. See [Contributing](../../CONTRIBUTING.md) for repository setup and verification.

## Stability guidance

Public visibility alone does not make every low-level class a recommended extension point. In
particular:

- use factory methods for `ProgramOption`;
- do not depend on public signatures whose parameter types are package-private;
- treat parser, checker, planner, interpreter decorator, and protocol-conversion helpers as
  low-level surfaces;
- do not assume internal optimization classes or native-eligibility rules are stable API.

The guides in this documentation identify supported application patterns. When those guides and a
low-level implementation detail differ, prefer the documented application pattern.
