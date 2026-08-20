# Configuration options

Configuration belongs to one of three stages:

1. compiler or environment construction;
2. program creation;
3. evaluation.

Keeping those stages separate makes reuse and concurrency behavior predictable.

## `ScriptCompiler.Builder`

`ScriptCompiler` is the preferred entry point for most integrations.

| Method | Effect |
| --- | --- |
| `registry(TypeRegistry)` | Copies the supplied registry when `build()` is called |
| `withDeclarations(...)` | Adds variables and function declarations |
| `withTypes(...)` | Registers native type descriptions on the compiler-owned registry |
| `withLibraries(...)` | Adds `Library` compile and program options |
| `withContainer(String)` | Sets the CEL namespace used for name resolution |
| `regexEngine(RegexEngine)` | Chooses Java regex or RE2; default is `JAVA` |
| `disableOptimize()` | Omits `EvalOption.OptOptimize`; does not disable native evaluation |

The builder accumulates values and is not thread-safe. `build()` snapshots its state. The resulting
compiler is immutable and reusable.

`ScriptCompiler` installs the standard library and enables `OptOptimize` by default. Use the
low-level `Env` and parser APIs when an application needs a custom standard-library subset,
parser-level control, state tracking, partial evaluation, a custom plan decorator, or explicit
native-evaluation disablement. Parser limits are available through `Parser.parse(Options, Source)`,
not through `Env.parse(...)`.

## `EnvOption`

`EnvOption`s configure an `Env` before checking starts.

| Factory | Effect |
| --- | --- |
| `declarations(...)` | Appends variable and function declarations |
| `types(...)` | Registers types on the configured `TypeRegistry` |
| `customTypeAdapter(...)` | Replaces Java-to-CEL value conversion |
| `customTypeProvider(...)` | Replaces type, identifier, and field lookup |
| `container(...)` | Extends the CEL name-resolution container |
| `abbrevs(...)` | Adds simple-name aliases for fully qualified names |
| `macros(...)` | Appends parser macros |
| `clearMacros()` | Removes all macros configured before that option |
| `features(...)` | Enables environment feature flags |
| `homogeneousAggregateLiterals()` | Rejects heterogeneous list and map literals while checking |

`Env.newEnv(...)` installs the CEL standard library before caller options.
`Env.newCustomEnv(...)` starts without it. Libraries can be installed with
`Library.Lib(library)`, which contributes both their environment and program options.

Options are applied in order. For example, apply `customTypeProvider(...)` before `types(...)`, and
`clearMacros()` before replacement `macros(...)`.

The first check that initializes the checker freezes built-in environment configuration. Use
`Env.extend(...)` to derive another environment rather than changing a frozen one.

## `ProgramOption`

`ProgramOption`s configure creation of a reusable `Program`.

| Factory | Effect |
| --- | --- |
| `evalOptions(...)` | Enables one or more `EvalOption` modes |
| `functions(...)` | Adds runtime `Overload` implementations |
| `globals(...)` | Adds default bindings shadowed by per-evaluation inputs |
| `regexEngine(...)` | Selects the engine used by the standard `matches` function |
| `customDecorator(...)` | Adds a low-level interpreter-plan decorator |

Duplicate overload IDs are rejected. A custom decorator selects established interpreter planning
instead of native planning.

`ProgramOption` is an opaque token, not an external functional SPI: its abstract method refers to
package-private construction state. Use the static factories.

## `EvalOption`

Options are additive.

| Option | Behavior | Planning consequence |
| --- | --- | --- |
| `OptOptimize` | Specializes eligible constant operations at program creation | Compatible with native evaluation |
| `OptTrackState` | Records intermediate values in evaluation-owned `EvalState` | Established interpreter |
| `OptExhaustiveEval` | Evaluates normally skipped branches and tracks state | Established interpreter |
| `OptPartialEval` | Propagates configured unknown attributes | Established interpreter |
| `OptDisableNativeEval` | Explicitly disables native planning | Established interpreter |

`OptExhaustiveEval` can observe function calls and errors in branches that normal short-circuit
evaluation would skip. It is a diagnostic/evaluation mode, not just more detailed tracing.

`OptPartialEval` requires a partial activation to have an effect. Combine it with `OptTrackState`
when calling `Env.residualAst(...)`.

## Regular-expression engine

`RegexEngine.JAVA` is the default for compatibility with earlier CEL-Java versions.
`RegexEngine.RE2` uses RE2/J for RE2 syntax and non-backtracking matching. Select the engine on the
compiler or program before program creation; a prepared constant pattern is tied to that engine.

See [Compatibility and limitations](compatibility-and-limitations.md) before changing an existing
application.

## Low-level parser options

[`org.projectnessie.cel.parser.Options`](../../core/src/main/java/org/projectnessie/cel/parser/Options.java)
is used with the low-level parser APIs.

| Builder method | Default | Meaning |
| --- | ---: | --- |
| `maxRecursionDepth(int)` | 250 | Maximum parser recursion depth |
| `expressionSizeCodePointLimit(int)` | 100,000 | Maximum source size in Unicode code points |
| `errorRecoveryLimit(int)` | 30 | Stored for compatibility; the current parser does not enforce it |
| `macros(...)` | none | Adds or replaces macros by parser lookup key |

Passing `-1` stores an effectively unbounded integer value for the three numeric settings. Parser
limits do not limit evaluation time, memory, input aggregate size, comprehension iterations, or
custom-function work.

The higher-level `Env` parser assembles its parser configuration from the environment’s macro set
and standard parser defaults.

## Per-operation resource limits

`ResourceLimits` configures a single `...Cancelable(...)` operation rather than a reusable
compiler, environment, program, or script. Its builder accepts optional elapsed-time,
executing-thread CPU-time, executing-thread allocated-byte, post-expansion AST-node, AST-depth, and
AST-metadata limits.

The handle owns an immutable snapshot of those limits. Creating a handle does not start its
measured budgets; its synchronous `execute()`, `compile()`, or `eval()` method does. Overloads
without a `ResourceLimits` argument still provide explicit cancellation but do not impose finite
budgets.

Measured limits are cooperative and can overshoot between checkpoints. Allocated bytes are
cumulative allocation by the execution thread, not live or retained heap. See
[Resource controls and cancellation](../advanced/resource-controls-and-cancellation.md) for the
failure model, JVM counter requirements, and examples.
