# Native Evaluation

Native evaluation is a checked-expression interpreter optimization. During program construction,
the planner may choose specialized interpreter nodes that operate directly on supported Java-native
representations.

Despite the name, native evaluation is **not**:

- generated Java source;
- generated JVM bytecode;
- JNI or a native library;
- CPU machine code;
- ahead-of-time (AOT) compilation; or
- a GraalVM native image.

It remains ordinary Java code executed by CEL-Java's interpreter.

## Checked planning

The checker records a CEL type and resolved reference or overload for expression IDs. The planner
uses that metadata to prove that a node has the operation and representation expected by a
specialization.

Checked planning can select native nodes only when:

- native planning is permitted by the program options;
- no incompatible interpreter decorator is installed;
- the node has a supported checked type and expression shape;
- a call resolves to the exact supported overload; and
- its children, type adapter, and type provider expose the required capabilities.

An unchecked AST has no type and reference maps and therefore uses established planning.

## Scalar islands

A native scalar island is a maximal supported typed result subtree. Inside the island, values can
remain in representations such as Java `long`, `double`, `boolean`, `String`, or the native null
capability. An island boundary converts that island's result to a CEL `Val`; when a native island is
the program root, that conversion happens at the program boundary.

More generally, specialized and established nodes can coexist in one plan. Supported native
capabilities compose across a subtree, while general CEL-value methods remain available at
established boundaries. An unsupported child therefore does not require a second all-or-nothing
evaluator for the whole expression.

Native capabilities do not bypass CEL value behavior. Internal signaling carries CEL errors and
unknowns across primitive-returning methods so that the terminal boundary can return the correct
CEL value. Java `null`, CEL null, absence, errors, and unknowns remain distinct concepts.

## Aggregate operations

The planner also has capabilities for supported list and map sources, literals, concatenations,
field values, traversals, folds, indexing, membership, equality, and related consumers. These nodes
can pass a supported aggregate representation to another native consumer without first wrapping
each element as a general CEL collection value.

Checked aggregate and element types remain authoritative. The planner declines specialization when
the representation is ambiguous, the key or element contract is unsupported, or the operation
would lose CEL equality, error, unknown, or iteration semantics.

### Exact aggregate registries

The `ExactAggregateTypeAdapter` and `ExactAggregateFieldProvider` contracts are an explicit opt-in
for integrations that can expose supported host representations exactly. The Protobuf, Jackson 2,
and Jackson 3 registries provide `newExactAggregateRegistry` factories.

The exact contract is stricter than general-purpose adaptation. It relies on checked types,
registered object types, canonical supported map keys and aggregate elements, and input values that
remain stable during evaluation. A detected representation-contract violation is a CEL error, not a
request to retry through general adaptation. Use the integration-specific documentation and
Javadocs to verify the accepted representation set.

The general registries remain available when compatibility with a wider variety of host values is
more important than this optimization contract. Selecting a general registry does not change CEL
semantics; it can simply make fewer native aggregate shapes eligible.

## Constant regex

For an eligible exact standard string `matches` call with a literal pattern, the planner can compile
the pattern once while constructing the program. A non-literal pattern uses the general runtime
path.

Invalid literal patterns still have lazy CEL behavior: their error is retained during planning and
is surfaced only if evaluation reaches the call after evaluating its left operand successfully.
The configured Java or RE2/J regex engine remains part of the program's semantics.

## Fallback

Within a native-permitted checked plan, fallback happens per expression shape. When a native
specialization is not eligible, the planner keeps an established interpreter node or bridges a
supported child result at a CEL `Val` boundary. Fallback is therefore normal operation, not a
planning failure.

Unchecked ASTs, stateful evaluation modes, explicit native disablement, and custom decorators
select established planning for the complete program before per-shape eligibility is considered.

This design also preserves extension behavior. A custom overload, adapter, provider, or decorator is
not assumed to have standard semantics merely because its source syntax resembles a supported
built-in operation.

Native eligibility is an implementation detail. Applications must not rely on a particular
expression being specialized, and a future release may change the exact set of eligible shapes
while preserving CEL results.

## Interaction with `OptOptimize`

Native planning and `EvalOption.OptOptimize` are separate mechanisms:

- native planning specializes supported checked runtime operations;
- `OptOptimize` folds a bounded set of supported constant operations during program construction.

When `OptOptimize` is the only selected evaluation option, CEL-Java composes its built-in folds with
native checked planning. Disabling `OptOptimize` through `ScriptCompiler.disableOptimize()` does
not disable native planning.

Use `EvalOption.OptDisableNativeEval` in the low-level program configuration to force established
planning. State tracking, exhaustive evaluation, partial evaluation, and custom decorators also
select established planning.

## Warm and cold trade-offs

Specialization, constant preparation, and capability checks add work during program construction.
They can reduce dispatch, conversion, and allocation work during later evaluations. Consequently:

- an application that compiles once and evaluates repeatedly can amortize planning work;
- an application that constructs a program for every input also pays the cold planning cost every
  time;
- prepared constants and specialized nodes remain reachable for the program lifetime; and
- the useful balance depends on expression shape, registry, options, and input distribution.

Reuse compiled scripts or programs and measure both cold and warm lifecycles when performance
matters. CEL-Java does not promise that native evaluation is faster for every expression.

## Related documentation

- [Optimization mechanisms](optimization-mechanisms.md)
- [Evaluation pipeline](evaluation-pipeline.md)
- [Protobuf integration](../guides/protobuf.md)
- [Jackson integration](../guides/jackson.md)
- [Native-image deployment](../guides/native-image.md)
