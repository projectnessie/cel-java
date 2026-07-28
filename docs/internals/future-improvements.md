# Future Improvements

This page is an inventory of possible investigation areas. It is not a roadmap, release commitment,
or statement that every item would improve every workload. Any change must preserve CEL semantics,
public API compatibility where required, and the established fallback path.

## Broader native eligibility

Additional expression and host-representation shapes could become eligible for native planning.
Each candidate needs:

- authoritative checked type and overload provenance;
- an explicit representation and ownership contract;
- parity for errors, unknowns, null, absence, equality, and evaluation order;
- fallback behavior for unsupported values; and
- differential tests against established planning.

Coverage should expand only where the planner can prove those conditions. A larger specialized
surface is not automatically better if it adds cold cost or uncommon branches without reducing
representative evaluation work.

## Evaluation frames and lazy memoization

The interpreter could investigate an evaluation-owned frame for values that are read repeatedly
within one execution, with lazy memoization for eligible blocks.

The hard requirements are more important than the cache structure:

- state must be isolated per evaluation and safe when one program is evaluated concurrently;
- a value must not be evaluated before normal control flow reaches it;
- short-circuit, error, unknown, and side-effecting custom-overload behavior must be preserved;
- retained state must not outlive the evaluation accidentally; and
- frame lookup and initialization must justify their cost on representative expressions.

This is an evaluator design question, not an invitation to memoize arbitrary custom functions or
caller-owned mutable inputs.

## Resource controls and cancellation

`Program` does not impose CPU, memory, result-size, or latency budgets. Hosts evaluating untrusted
expressions remain responsible for isolation and policy limits.

A future resource-control API would need precise, deterministic semantics:

- what is counted and at which interpreter boundaries;
- how limits interact with macros, comprehensions, regex operations, and custom overloads;
- whether cancellation is cooperative and how frequently it is checked;
- how a limit result is represented without confusing it with a CEL error or unknown; and
- how overhead affects ordinary trusted evaluations.

The existing heuristic `Coster` ranges estimate plan cost; they are not execution budgets or
measured time.

## Planning and eligibility diagnostics

Advanced users could benefit from supported diagnostics that explain high-level planning decisions,
such as why established planning was selected or which optimization families were eligible.

Such diagnostics should avoid making internal node classes a compatibility contract. They would
also need a stable vocabulary, bounded output, and a clear distinction between semantic
configuration and implementation-detail eligibility.

## Exact-representation contracts

The exact aggregate interfaces could evolve to cover more integration types or make capability
negotiation easier for custom registries. Any extension must specify:

- accepted Java representations and canonical key/element forms;
- mutability and lifetime requirements;
- field presence and null behavior;
- checked-type authority and registration rules; and
- behavior when a runtime value violates the declared contract.

General-purpose registry behavior should remain available rather than silently inheriting a
stricter contract.

## Cold-start and retained-state work

Program-construction work and retained prepared objects should be measured independently from warm
evaluation. Possible investigations include reducing planner setup for small or one-shot programs,
sharing immutable metadata safely, and avoiding preparation that is unlikely to be used.

Evaluation must remain lazy where CEL control flow makes an error or expensive operation
unobservable. An optimization that improves repeated evaluation but materially worsens one-shot
usage should have an explicit lifecycle and configuration story.

## Extension-aware optimization

Libraries and custom adapters might eventually expose explicit capabilities that allow safe
specialization without assuming standard behavior. A viable contract would need versioning,
thread-safety, error/unknown propagation, and a reliable established fallback.

The planner must not specialize a custom extension merely because its name resembles a standard
function or field.

## Public configuration and extension surfaces

Some public configuration types expose implementation-shaped contracts. For example,
`ProgramOption` is usable through factory methods but cannot be implemented outside its package,
and some low-level expression-builder signatures refer to non-public helper types.

A future API cleanup could make the intended boundary explicit by either:

- exposing a complete, stable extension contract with no package-private types; or
- making factory-produced tokens and internal builders unambiguously non-SPI surfaces while
  providing purpose-specific public factories for supported use cases.

Such work must preserve source compatibility where practical and should not expose mutable planner
state merely to make an existing functional-interface signature implementable.

## AST interchange fidelity

Protocol conversion is useful for tools and interoperability, but not every internal expression or
source-information detail is currently presented as a durable serialized-script contract. Future
work could define and test explicit round-trip guarantees for:

- parsed and checked expressions;
- source line offsets and macro-call metadata;
- newer comprehension representations;
- checked type/reference maps; and
- compatibility across protocol and CEL-Java versions.

The result would need a stated versioning policy and fixtures shared by every supported generated
Protobuf line. It should not imply that an executable `Program`, configured registry, custom
function, or host object is serializable.

## Relocation-aware standalone Javadocs

The standalone sources JAR now aggregates the maintained core, tools, Jackson 2, and Jackson 3
sources for IDE navigation and debugging. Generated Protobuf and third-party sources remain
excluded. Java source imports are not rewritten to match the dependency packages relocated in the
compiled standalone artifact.

Producing useful relocation-aware standalone Javadocs remains an optional distribution
improvement. Any implementation would need to describe the compiled package names accurately and
be verified from a consumer build rather than inferred from the main binary alone.

## Evaluation criteria

For any candidate:

1. define the semantic contract and fallback first;
2. add focused parity tests, including errors, unknowns, null, absence, and boundary values;
3. verify concurrent reuse and caller-input ownership;
4. measure cold construction, warm throughput, and allocation separately; and
5. document configuration and limitations without promising workload-independent gains.

See [design alternatives](design-alternatives.md) for the rationale behind the current boundaries
and [performance and reuse](../advanced/performance-and-reuse.md) for current application guidance.
