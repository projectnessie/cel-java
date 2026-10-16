# CEL-Java documentation

CEL-Java is a Java implementation of the
[Common Expression Language (CEL)](https://github.com/google/cel-spec). It parses, checks, and
evaluates CEL expressions against Java, Protocol Buffer, and optionally Jackson values.

If you are new to CEL-Java, start with [Getting started](getting-started.md). If you already know
CEL and need a particular integration or runtime detail, use the map below.

## Start here

- [Getting started](getting-started.md) — dependencies, a first compiled expression, and common
  failures
- [Execution lifecycle](concepts/execution-lifecycle.md) — source, checking, program creation, and
  evaluation
- [Types and values](concepts/types-and-values.md) — CEL types and their Java representations
- [Errors, unknowns, and partial state](concepts/errors-unknowns-and-partial-state.md) — runtime
  errors, unknown values, and partial evaluation

## Integration guides

- [Protocol Buffers](guides/protobuf.md)
- [Jackson](guides/jackson.md)
- [Bundled extensions](guides/extensions.md)
- [Authorization expressions](guides/authorization.md)
- [Custom functions](guides/custom-functions.md)
- [Testing CEL integrations](guides/testing-cel-integrations.md)
- [Native-image applications](guides/native-image.md)

## Advanced use

- [Environments and programs](advanced/environments-and-programs.md) — the low-level lifecycle,
  reuse, partial evaluation, and concurrency contracts
- [Extension points](advanced/extension-points.md) — libraries, adapters, providers, registries,
  activations, and runtime overloads
- [Performance and reuse](advanced/performance-and-reuse.md) — compile-once/evaluate-many design,
  planning modes, and measurement
- [Resource controls and cancellation](advanced/resource-controls-and-cancellation.md) —
  cooperative cancellation, elapsed/CPU/allocation budgets, and AST admission

## Internals

- [Architecture](internals/architecture.md)
- [Evaluation pipeline](internals/evaluation-pipeline.md)
- [Optimization mechanisms](internals/optimization-mechanisms.md)
- [Native evaluation](internals/native-evaluation.md)
- [Design alternatives](internals/design-alternatives.md)
- [Future improvements](internals/future-improvements.md)

The internals pages explain the implementation. They are not additional API contracts.

## Reference

- [Artifacts](reference/artifacts.md)
- [Configuration options](reference/configuration-options.md)
- [Supported types](reference/supported-types.md)
- [Compatibility and limitations](reference/compatibility-and-limitations.md)
- [API reference](reference/api-reference.md)

## Project documentation

- [Project overview and installation](../README.md)
- [Contributing](../CONTRIBUTING.md)
- [Security policy](../SECURITY.md)
- [CEL conformance tooling](../conformance/README.md)
