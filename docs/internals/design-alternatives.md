# Design Alternatives

This page records durable rationale for the current evaluator architecture. It describes design
trade-offs, not a performance guarantee or a commitment that the implementation can never change.

## Specialized interpreter nodes instead of generated code

The current planner creates Java interpreter objects specialized from checked CEL metadata. It does
not generate Java source, JVM bytecode, JNI bindings, or machine code.

This keeps specialization inside the existing interpreter lifecycle:

- the same checked type and overload metadata drives both general and specialized nodes;
- CEL values, errors, and unknowns can cross explicit interpreter boundaries;
- type adapters, providers, libraries, and program options remain part of planning; and
- the implementation does not need a separate code-generation, loading, or native-toolchain
  lifecycle.

Generated code could move different work to build or program-construction time, but would also add
code-cache ownership, loading, diagnostics, deployment, and semantic-parity concerns. It is not what
CEL-Java calls native evaluation.

## Mixed plans instead of an all-or-nothing evaluator

Native eligibility is deliberately local to expression shapes. Specialized scalar islands and
aggregate consumers can coexist with established interpreter nodes in one plan.

An all-or-nothing alternate evaluator would make one unsupported extension, child, or expression
shape disqualify an entire program. Mixed planning instead provides a CEL-value bridge at the
boundary and retains the established node exactly where it is needed. The cost is a more explicit
capability and boundary model inside the planner.

## Checked provenance instead of syntax-based assumptions

The planner requires checked types and resolved overload provenance for specializations whose
correctness depends on a standard CEL operation. It does not infer standard semantics from a
function name or source shape alone.

Syntax-only matching would be simpler, but could accidentally specialize a custom overload or use
the wrong host representation. Checked provenance makes declining an optimization preferable to
changing observable behavior.

## Explicit exact aggregates instead of stricter global adaptation

General type registries accept a broad range of application values and preserve their established
adaptation behavior. Exact aggregate registries are a separate, explicit contract for integrations
that can expose planner-readable list and map fields.

Making the exact contract universal would silently narrow accepted representations and could change
behavior for existing adapters. Keeping it opt-in allows a host to choose the stricter contract
where its data model satisfies it, while the general path remains the compatibility boundary.
Native scalar-field access uses the separate `StandardScalarFieldProvider` capability and does not
depend on exact aggregate mode.

## Separate constant optimization and native planning

`OptOptimize` controls supported constant-argument rewrites. Native planning controls checked
runtime specialization. They compose, but neither is defined as a prerequisite for the other.

A single optimization switch would be superficially simpler, but would couple two mechanisms with
different costs and diagnostic uses. Separate controls let an advanced caller compare or disable
one mechanism without implicitly changing the other.

## Established planning for stateful evaluation modes

Tracking, exhaustive evaluation, partial evaluation, and custom decorators use the established
planning path. These modes depend on intermediate-state recording, altered short-circuit behavior,
unknown-attribute handling, or arbitrary node decoration.

Applying native specialization without an equivalent contract for each of those behaviors could
make evaluation state incomplete or alter when branches and errors are observed. The current
boundary favors semantic completeness over partial support for those modes.

## Caller-owned live inputs instead of universal snapshots

Programs evaluate caller-owned activations and adapters may expose live views of Java values.
CEL-Java therefore requires inputs and reachable values to remain stable while evaluation is
running.

Snapshotting every input would give the evaluator stronger isolation, but would impose copying,
identity, ordering, and failure semantics on all integrations and could duplicate large object
graphs before any expression reads them. The current ownership rule keeps that policy with the host.
Integrations may still copy or normalize values at their own boundary when their use case requires
it.

## Prepared program state instead of per-call reconstruction

Plans, folded constants, and eligible literal regex patterns are prepared when the program is
created and retained for reuse. Reconstructing them for every evaluation would reduce retained
program state, but would repeat source-independent work for each input.

This choice makes the compiler/program lifecycle visible: applications should reuse compiled
programs where possible and measure cold construction separately from warm evaluation.

## Related documentation

- [Architecture](architecture.md)
- [Native evaluation](native-evaluation.md)
- [Optimization mechanisms](optimization-mechanisms.md)
- [Future improvements](future-improvements.md)
