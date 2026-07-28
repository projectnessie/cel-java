# Optimization Mechanisms

CEL-Java optimizes at several boundaries: checking, program construction, host-value access, and
evaluation. Every optimization is guarded by semantic metadata and supported representation
contracts. A particular expression is not guaranteed to use any specific optimized node.

## Mechanism inventory

| Mechanism | Phase | Eligibility | Correctness guard | Fallback | Intended benefit | Cost or trade-off | Configuration |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Checked overload and type specialization | Program construction | Checked AST with resolved type and reference metadata | Checked type and exact resolved overload identify the CEL operation | Established interpreter node | Avoid repeated dynamic type or overload work | More planning work and a larger specialized plan | Enabled by using a checked AST |
| Constant list and map construction | Program construction | `OptOptimize` and a supported literal whose elements are constant | Planner verifies the complete supported constant shape before replacing it | Original list or map node | Construct a reusable CEL constant once | Retains the folded value for the program lifetime | `EvalOption.OptOptimize`; enabled by default in `ScriptCompiler` |
| Constant conversion folding | Program construction | `OptOptimize` and a supported unary conversion with a constant argument | Exact supported conversion and normal CEL conversion result | Original call when ineligible; an invalid folded conversion fails program construction | Avoid repeating a deterministic conversion | Adds program-construction work; not every conversion is foldable | `EvalOption.OptOptimize` |
| Constant membership set | Program construction | `OptOptimize` and `in` against a homogeneous supported primitive constant list | Exact membership overload and homogeneous constant element type | Original membership operation | Use prepared set membership during repeated evaluation | Stores an additional prepared set; heterogeneous or unsupported lists are unchanged | `EvalOption.OptOptimize` |
| Native scalar islands | Program construction and evaluation | Checked, supported scalar subtree with native planning permitted | Checked types, exact standard-overload provenance, supported child capabilities, and CEL value signaling | Island boundary or established node | Keep intermediate scalar values in Java primitive or `String` form and adapt once at the boundary | Additional specialized nodes; cold planning cost; coverage is shape-dependent | Default when eligible; force established planning with `OptDisableNativeEval` |
| Standard scalar field access | Program construction and evaluation | Provider implements `StandardScalarFieldProvider` and the checked scalar field is supported | Authoritative checked field type and the provider’s standard-representation contract | General field selection | Read a supported scalar field without first materializing a general object value | Capability is registry-specific; Jackson 2 does not provide it | Available with capable providers, including Protobuf and Jackson 3 |
| Literal-pattern regex preparation | Program construction and evaluation | Exact standard string `matches` overload, literal pattern, and eligible native operands | Selected regex engine, exact overload, and preserved evaluation order/error behavior | General regex call | Compile a constant pattern once per program | Prepared pattern is retained; invalid-pattern error must remain lazily observable | Available through eligible native planning; regex engine is configured separately |
| Native list and map consumers | Program construction and evaluation | Supported checked indexing, size, membership, equality, concatenation, traversal, or fold shape | Checked aggregate/element types, supported representation, exact overloads, and continuation semantics | Established aggregate operation or materialized CEL value | Operate over supported host collections with fewer intermediate CEL wrappers | Specialized code and representation checks; unsupported or ambiguous shapes do not qualify | Available through eligible native planning |
| Exact aggregate field access | Configuration, program construction, and evaluation | Registry explicitly implements both exact aggregate contracts and the checked list or map field is supported | Same exact provider/adapter instance, authoritative checked aggregate type, registered host type, and strict representation contract | General registry behavior or established field access | Expose a supported aggregate field in planner-readable host form | Stricter input contract; callers must preserve supported, stable representations | Use an exact aggregate registry for Protobuf, Jackson 2, or Jackson 3 |
| Program and script reuse | Application lifecycle | Repeated evaluation of the same source and configuration | Immutable compiled configuration plus per-call activation/state | Rebuild for each call | Amortize parsing, checking, planning, and prepared constants | Retains the plan and its constants; unsuitable if configuration must change per call | Reuse `ScriptCompiler`, `Script`, or `Program` |

“Fallback” means preserving the established CEL evaluator or the original operation, not accepting a
different result. Optimized and non-optimized paths are required to preserve CEL behavior for
values, errors, unknowns, short-circuiting, and overload selection.

## `OptOptimize` and native planning are independent

`EvalOption.OptOptimize` enables a bounded set of constant-argument rewrites during program
construction. Native planning selects specialized interpreter nodes for eligible checked
expressions. One does not imply the other:

- `OptOptimize` can be used while native evaluation is explicitly disabled.
- Native planning can be used without `OptOptimize`.
- With only `OptOptimize` selected, the native planner composes the built-in folds into the same
  planning pass.

`ScriptCompiler` enables `OptOptimize` by default. Its `disableOptimize()` method disables those
constant rewrites; it does not disable native planning. Advanced callers can use the lower-level
program API with `EvalOption.OptDisableNativeEval` to force the established evaluator.

Other built-in evaluation modes—state tracking, exhaustive evaluation, and partial evaluation—and
custom interpreter decorators select established planning because they require behavior outside the
native planning contract.

## Semantic gates

An optimization is only sound when the planner knows what operation the checker selected. Source
syntax alone is insufficient: a function name can resolve to a custom overload, an aggregate field
can have a representation chosen by a type adapter, and an error in a normally skipped branch must
remain skipped.

The important gates are therefore:

- checked CEL type metadata;
- resolved name and overload metadata;
- exact standard-overload provenance where a standard semantic is required;
- a supported Java representation with stable ownership during evaluation;
- preservation of CEL errors, unknowns, null, field absence, and evaluation order.

If any gate is missing, the planner retains a more general node.

## Measuring the trade-off

Optimizations can move work from repeated evaluation to program construction and can retain prepared
objects for the program lifetime. Measure the lifecycle that matches the application:

- cold measurements include compiler and program construction;
- warm measurements reuse a compiled script or program across representative inputs;
- allocation measurements should distinguish plan-time from per-evaluation allocation.

Avoid inferring production behavior from a single expression. Eligibility depends on checked types,
selected overloads, input registries, expression shape, and evaluation options. See
[performance and reuse](../advanced/performance-and-reuse.md).

## Related documentation

- [Native evaluation](native-evaluation.md)
- [Evaluation pipeline](evaluation-pipeline.md)
- [Configuration options](../reference/configuration-options.md)
- [Design alternatives](design-alternatives.md)
