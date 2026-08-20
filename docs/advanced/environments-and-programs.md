# Environments and programs

[`ScriptCompiler`](../../tools/src/main/java/org/projectnessie/cel/tools/ScriptCompiler.java) is the
preferred application-facing API. The lower-level `Env`, `Ast`, and `Program` APIs are useful when
an integration needs to control parsing, checking, program options, partial evaluation, or
evaluation details directly.

## The low-level lifecycle

A low-level CEL integration has four stages:

1. Create and configure an `Env`.
2. Parse source into an AST and check that AST against the environment.
3. Create a `Program` from the checked AST.
4. Evaluate the reusable program with an activation.

```java
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.checker.Decls.newVar;
import static org.projectnessie.cel.checker.Decls.String;

import java.util.Map;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.Program;

Env env = newEnv(declarations(newVar("name", String)));

AstIssuesTuple parsed = env.parse("\"hello, \" + name");
if (parsed.hasIssues()) {
  throw parsed.getIssues().err();
}

AstIssuesTuple checked = env.check(parsed.getAst());
if (checked.hasIssues()) {
  throw checked.getIssues().err();
}

Program program = env.program(checked.getAst());
Object result = program.eval(Map.of("name", "Ada")).getVal().value();
```

Applications should normally present parse and check diagnostics to the expression author instead
of collapsing them into a generic exception.

## Environment configuration freezes at checking

An `Env` is mutable while it is being configured. The first check that initializes its checker
freezes its configuration. Built-in `EnvOption`s and `Env.setFeature(...)` then reject further
configuration with `IllegalStateException`.

This boundary prevents a cached checker from observing a mixture of old and new declarations,
types, macros, or features. Configure the complete environment before sharing it. To derive another
configuration, call `Env.extend(...)`. The returned environment copies its collections and a
configured `TypeRegistry`; a custom adapter or provider that is not a registry remains shared and
subject to that component’s ownership contract.

Parsing alone does not freeze an environment. An operation that rejects an invalid AST before
checker initialization may also fail without freezing it. Do not use those details as a
configuration protocol: finish configuration first, then check.

## Reuse and concurrency

An environment whose configuration is complete may be reused for parsing and checking. A checked
`Program` is intended to be reused for many evaluations and can be evaluated concurrently.
`ScriptCompiler` follows the same model: its builder is mutable and not thread-safe, while the
built compiler is an immutable configuration that can compile independent sources.

Those guarantees do not make caller-owned objects immutable:

- Do not mutate activation maps, lists, maps nested in activation values, registered descriptors,
  custom adapters, custom providers, or library state while CEL-Java may read them.
- Complete mutable custom-provider and custom-registry configuration before sharing the
  environment, compiler, or program.
- Treat each `EvalResult`, `EvalDetails`, and `EvalState` as evaluation-owned mutable state. Do not
  share one result’s state between evaluations.

Changing inputs concurrently with evaluation is unsupported even if the particular Java
collection is concurrent. A concurrent collection prevents collection corruption; it does not give
an expression a stable logical snapshot.

## Activations

`Program.eval(Object)` accepts any input recognized by
[`Activation.newActivation`](../../core/src/main/java/org/projectnessie/cel/interpreter/Activation.java),
including:

- a `Map<String, ?>`;
- an existing `Activation`;
- an `ActivationFunction` resolver.

Program globals configured with `ProgramOption.globals(...)` provide defaults. Per-evaluation
bindings shadow globals with the same name.

Map-backed activations do not modify the supplied map. A `Supplier<?>` used as a binding is
evaluated lazily and its successful result is memoized in that activation instance.
Failures are not cached and may be retried by a later lookup. Resolver implementations must
distinguish “binding absent” from “binding present with Java `null`” using
`ActivationFunction.ABSENT`; Java `null` is a present binding whose value is CEL null.

A map passed directly to `Program.eval(...)` receives a new activation for that evaluation.
Program globals are converted to an activation during program construction, and a caller can reuse
an existing activation; suppliers in those longer-lived activations remain memoized across every
use of the same activation.

## Partial evaluation and residual expressions

Partial evaluation marks selected variables or qualified attributes as unknown:

```java
import static org.projectnessie.cel.CEL.partialVars;
import static org.projectnessie.cel.CEL.attributePattern;
import static org.projectnessie.cel.EvalOption.OptPartialEval;
import static org.projectnessie.cel.EvalOption.OptTrackState;
import static org.projectnessie.cel.ProgramOption.evalOptions;

var vars = partialVars(input, attributePattern("request").qualString("headers"));
var program = env.program(ast, evalOptions(OptPartialEval, OptTrackState));
var result = program.eval(vars);
var residual = env.residualAst(ast, result.getEvalDetails());
```

`OptPartialEval` propagates unknown attributes through evaluation. `OptTrackState` is required when
the recorded state will be used to construct a residual AST. Tracking and partial evaluation select
the established interpreter path and add runtime overhead.

Configure `AttributePattern`s completely before sharing them. Their public builder-style qualifier
methods are the supported construction API.

## `ScriptCompiler` and the deprecated `ScriptHost`

`ScriptCompiler` makes source text the final input:

```java
var compiler =
    ScriptCompiler.newBuilder()
        .withDeclarations(...)
        .withTypes(...)
        .withLibraries(...)
        .build();

var scriptA = compiler.compile("request.user == owner");
var scriptB = compiler.compile("resource.public");
```

Its builder snapshots declarations, types, libraries, and the configured registry when `build()`
is called. This makes a single compiler suitable for compiling many scripts with one stable
configuration.

`ScriptHost` is deprecated. Its source-first, cumulative builder can unintentionally mix
per-script configuration with host-wide registry state. Existing callers can keep using it, but
new code should use `ScriptCompiler`.

## AST interchange

`Ast` wraps the parsed or checked expression plus source information. Conversion helpers can expose
the protocol representation, but that does not make every internal AST detail a stable
cross-version serialization format. In particular, newer comprehension forms and source-position
metadata may require representation-specific handling.

For durable interchange, pin compatible CEL-Java and CEL protocol versions, test round trips with
the exact expression features you use, and preserve the checked type/reference maps alongside a
checked expression.
