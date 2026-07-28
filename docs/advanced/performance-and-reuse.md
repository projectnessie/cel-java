# Performance and reuse

The largest reliable performance improvement is architectural: compile a stable expression once
and evaluate the resulting program many times.

## Recommended lifecycle

1. Build one `ScriptCompiler` for a stable set of declarations, types, libraries, container, and
   regular-expression engine.
2. Compile each distinct expression once.
3. Cache the resulting script or program according to the application’s expression lifecycle.
4. Evaluate it with stable, representation-appropriate inputs.

Parsing, checking, type registration, and program planning are compilation work. Keeping them out
of the request path usually matters more than tuning an individual operator.

`ScriptCompiler` is reusable after construction, and each compiled `Script` is reusable and can be
evaluated concurrently when configured custom components support their concurrent access and
caller inputs remain stable. Its builder is not thread-safe. The same distinction applies to
low-level environment configuration versus an already-created `Program`.

## Planning and optimization

CEL-Java has two independent optimization mechanisms:

- `EvalOption.OptOptimize` performs program-creation-time specialization such as folding eligible
  constant operations.
- Native evaluation lets eligible checked expression shapes operate directly on supported Java
  representations, with semantic fallback for unsupported shapes. Its exact built-in string
  `matches` path also compiles a literal pattern once while constructing the program.

`ScriptCompiler` enables `OptOptimize` by default. Native evaluation is also eligible by default;
it is not generated Java, bytecode generation, JNI, or machine code. Either mechanism can apply
without the other.

Options that require interpreter-wide observation or modification—including state tracking,
exhaustive evaluation, partial evaluation, custom decorators, and explicit
`OptDisableNativeEval`—select the established interpreter plan. See
[Optimization mechanisms](../internals/optimization-mechanisms.md) and
[Native evaluation](../internals/native-evaluation.md).

## Choose representations deliberately

Adapters and type providers affect both semantics and cost:

- Pass ordinary Java scalars, lists, arrays, and maps directly when their built-in CEL mapping is
  correct.
- Register Protocol Buffer or Jackson object types once on the compiler rather than rebuilding
  registries for each expression.
- Keep checked aggregate element types precise. Exact list, map, and object-field paths can avoid
  generic conversion work.
- Prefer field providers that expose exact native fields rather than materializing complete object
  maps.

Do not mutate input maps, collections, arrays, messages, or object graphs while evaluation is in
progress. Besides being unsupported, mutation prevents the evaluator from treating a value as a
stable input and can produce internally inconsistent results.

## Regular expressions

A literal regular-expression pattern is compiled once during program construction when the exact
standard string `matches` call is eligible for native checked planning. This specialization is
independent of `OptOptimize`. Established planning and dynamic patterns compile through the
selected engine during evaluation.

The default engine is Java regular expressions for compatibility. `RegexEngine.RE2` provides the
RE2 syntax and non-backtracking execution model. Engine selection changes accepted syntax and must
be part of the compiler’s stable configuration; see
[Compatibility and limitations](../reference/compatibility-and-limitations.md).

## Cold and warm costs

Measure the lifecycle your application actually uses:

- **cold compilation** includes source parsing, checking, and program creation;
- **cold evaluation** includes the first execution after program creation;
- **steady-state evaluation** measures repeated execution of the same program after JVM warm-up;
- **allocation rate** can reveal conversion or aggregation work even when elapsed time is noisy.

Do not compare a cached program on one side with parse-and-compile-per-operation on the other. For
JMH, use separate states for compiler construction, expression compilation, and program evaluation;
consume results and use enough forks and warm-up to expose JVM compilation effects.

The repository’s benchmark module is an investigation tool, not part of the normal `check`
lifecycle. Hosted CI runners are useful for detecting compilation and correctness regressions but
are generally too variable for publishing fine-grained performance claims.

## Avoid misleading micro-optimizations

- Prefer typed `Script.execute(...)` methods for result validation and clarity. Primitive-return
  convenience alone does not remove the expression’s dominant evaluation work.
- Do not bypass checked evaluation solely to reduce compilation cost; checking establishes types,
  overloads, and native-evaluation eligibility.
- Do not retain mutable evaluation state across calls to avoid allocation.
- Do not assume a native-eligible expression is always faster for every input size; fallback and
  conversion boundaries matter.

## Resource control is separate

CEL is intentionally side-effect constrained, but that does not make arbitrary expressions cheap.
Large inputs, nested comprehensions, dynamic regular expressions, or expensive custom overloads
can consume substantial CPU and memory.

Parser recursion and source-size limits constrain parsing only. CEL-Java does not currently expose
a general evaluation step, time, memory, or comprehension-iteration budget. Applications accepting
untrusted expressions or unbounded inputs must enforce their own admission rules, input-size
limits, deadlines, and isolation appropriate to the threat model.
