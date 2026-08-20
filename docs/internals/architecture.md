# Architecture

CEL-Java separates the public integration API, the CEL front end, program planning and evaluation,
and host-value adapters. This page describes those boundaries and the dependencies between them.
For the sequence followed by one expression, see the
[evaluation pipeline](evaluation-pipeline.md).

## Runtime layers

The main runtime layers are:

1. The `cel-tools` API provides `ScriptCompiler` and `Script`, the preferred entry point for most
   applications. A configured compiler creates reusable scripts and translates lower-level CEL
   results into its high-level result and exception contract.
2. `cel-core` contains parsing, checking, ASTs, environments, program construction, the
   interpreter, CEL values, and extension contracts. Applications that need detailed control can
   use `Env`, `Ast`, and `Program` directly.
3. Type registries and adapters connect Java representations to CEL values and type metadata.
   Protobuf support is part of the core runtime; `cel-jackson` and `cel-jackson3` provide separate
   Jackson 2 and Jackson 3 integrations.
4. Generated CEL schema classes are published separately as `cel-generated-pb` and
   `cel-generated-pb3`. This lets applications choose the compatible Protobuf family.

The dependency direction is intentionally inward: the tools and integration modules depend on the
core contracts; the core interpreter does not depend on either Jackson integration or on the
high-level tools API.

## Published artifacts

| Artifact | Role |
| --- | --- |
| `cel-core` | CEL front end, checked AST, program API, interpreter, standard types, and Protobuf integration |
| `cel-tools` | High-level script compilation and execution API |
| `cel-jackson` | Jackson 2 type registry and adapter |
| `cel-jackson3` | Jackson 3 type registry and adapter |
| `cel-generated-pb` | Generated CEL schema classes for the current Protobuf dependency family |
| `cel-generated-pb3` | Generated CEL schema classes for the Protobuf 3 dependency family |
| `cel-standalone` | Bundled artifact with selected dependencies relocated to reduce class-path conflicts |
| `cel-bom` | Dependency-management coordinates for aligned CEL-Java artifacts |

The conformance, benchmark, and Quarkus smoke-test projects validate the implementation; they are
not additional runtime layers.

## Long-lived and per-evaluation objects

The ownership model is easiest to understand by separating configuration from evaluation:

- A `ScriptCompiler` snapshots its builder configuration and may compile multiple source strings.
- An `Env` owns the declarations, macros, container, libraries, and type services used to parse and
  check expressions. Its checker configuration freezes when checking first starts.
- An `Ast` contains the expression and source information. A checked AST additionally contains the
  type and reference metadata used by planning.
- A `Program`, or the high-level `Script` that contains it, holds the reusable executable plan.
- An `Activation` supplies variables for one evaluation. Evaluation details and tracked state are
  also owned by that evaluation.

Reusable compilers, scripts, and programs may be used concurrently subject to the thread-safety of
configured custom components and caller-owned inputs. Input objects and values reachable from them
must not be mutated while an evaluation is running. See
[performance and reuse](../advanced/performance-and-reuse.md) for the operational consequences.

## Front end and evaluator

Parsing produces a syntax tree and source-position information; parsing also expands configured CEL
macros. Checking resolves names and overloads and records a CEL type for each checked expression.
The planner then turns the AST into an `Interpretable` tree.

The planner can mix established interpreter nodes with eligible specialized nodes. Planning is a
program-construction step, not code generation. At evaluation time the tree reads values from an
activation, invokes registered overloads where needed, and returns a CEL `Val`.

The established evaluator remains the semantic fallback for shapes that cannot be specialized and
for features that require its decorators or state model. See
[optimization mechanisms](optimization-mechanisms.md) and
[native evaluation](native-evaluation.md).

## Extension boundaries

CEL-Java exposes several kinds of extension, each at a different layer:

- `TypeAdapter` converts host values to CEL values.
- `TypeProvider` resolves type names, fields, and enum identifiers.
- `TypeRegistry` combines those roles and registers application types.
- CEL libraries contribute declarations, macros, functions, and overload implementations.
- Program options and interpreter decorators control advanced planning and evaluation behavior.

An extension is part of the correctness boundary: its null handling, equality, field-presence,
thread-safety, and error behavior must agree with CEL semantics. The native planner only specializes
an extension-backed operation when it has enough checked metadata and a supported representation
contract; otherwise evaluation remains on the established path. See
[extension points](../advanced/extension-points.md) and
[types and values](../concepts/types-and-values.md).

## Related documentation

- [Execution lifecycle](../concepts/execution-lifecycle.md)
- [Environments and programs](../advanced/environments-and-programs.md)
- [Errors, unknowns, and partial state](../concepts/errors-unknowns-and-partial-state.md)
- [Compatibility and limitations](../reference/compatibility-and-limitations.md)

