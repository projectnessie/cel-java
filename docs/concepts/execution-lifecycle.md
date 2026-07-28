# Execution lifecycle

CEL-Java separates expression preparation from evaluation:

1. Configure the types, declarations, functions, macros, and evaluation
   options that expressions may use.
2. Parse and type-check an expression.
3. Build an executable program.
4. Reuse that program with different runtime bindings.

The `cel-tools` API combines steps 2 and 3 in `ScriptCompiler.compile()` and
exposes the executable as a `Script`. The lower-level `cel-core` API exposes
each stage through `Env` and `Program`.

## The preferred tools API

Create and configure a `ScriptCompiler`, then compile each expression once:

```java
ScriptCompiler compiler =
    ScriptCompiler.newBuilder()
        .withDeclarations(Decls.newVar("age", Decls.Int))
        .build();

Script adult = compiler.compile("age >= 18");

boolean first =
    adult.execute(
        Boolean.class, Map.<String, Object>of("age", 21L));
boolean second =
    adult.execute(
        Boolean.class, Map.<String, Object>of("age", 17L));
```

The builder copies declaration, type, and library collections as they are
supplied. `build()` snapshots the scalar configuration, gives the compiler
its own copy of a supplied type registry, and obtains each library's compile
and program options. Custom components that are not registries remain subject
to their own ownership and thread-safety contracts.

`ScriptCompiler.compile()` performs parsing and checking before constructing
the executable script. A successfully compiled `Script` can be reused and
shared. Evaluation-specific bindings and state belong to each call.

## The lower-level environment API

Use `Env` and `Program` when code needs direct access to the AST, CEL runtime
values, partial evaluation, or detailed evaluation state:

```java
import java.util.Map;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;

Env env =
    Env.newEnv(
        EnvOption.declarations(
            Decls.newVar("age", Decls.Int)));

Env.AstIssuesTuple compiled = env.compile("age >= 18");
if (compiled.hasIssues()) {
  throw compiled.getIssues().err();
}

Program program = env.program(compiled.getAst());
Program.EvalResult evaluated =
    program.eval(Map.<String, Object>of("age", 21L));

boolean allowed =
    env.getTypeAdapter().valueToBoolean(evaluated.getVal());
```

`Env.compile()` is equivalent to parsing and then checking the parsed AST.
Callers that need the intermediate form can instead use `parse()` and
`check()` separately.

## Environment configuration and freezing

An `Env` is configured before checking begins. A check that reaches checker
initialization freezes that environment; later configuration calls fail with
`IllegalStateException`. Parsing by itself does not freeze the environment.
Program construction does not freeze it either.

If related configurations are needed, use `Env.extend()` before checking the
derived environment:

```java
Env base =
    Env.newEnv(
        EnvOption.declarations(
            Decls.newVar("request", Decls.Dyn)));

Env specialized =
    base.extend(
        EnvOption.declarations(
            Decls.newVar("tenant", Decls.String)));
```

The derived environment receives copies of declarations, macros, program
options, and the type registry. A non-registry adapter or provider can still
be shared with the original environment, so its own contract remains
important.

Treat configuration as a construction phase:

- finish all configuration before checking;
- publish only fully configured environments, compilers, and programs;
- do not mutate custom components after publication unless they explicitly
  support it.

## Evaluation state and input ownership

Each `Program.eval()` call creates its own `EvalDetails` and mutable
`EvalState`. State is empty during ordinary evaluation and populated when
state-tracking options are enabled. The state object returned for one
evaluation is not shared with another evaluation and is not itself
thread-safe.

Evaluation accepts a map, an `Activation`, an `ActivationFunction`, or the
legacy Java `Function` form. CEL-Java does not mutate a supplied map, but may
retain and read it for the duration of the evaluation. Aggregate values
reachable through a binding can likewise be read lazily. The application must
not mutate bindings or reachable values while evaluation is in progress.

Compiled programs are designed for repeated and concurrent evaluation,
subject to the same stability and thread-safety requirements for
application-supplied values, functions, adapters, providers, and other custom
components.

## Options change evaluation behavior

Program options control state tracking, exhaustive evaluation, constant
optimization, partial evaluation, and explicit native-planning disablement.
Native planning is permitted by default for eligible checked expressions;
incompatible evaluation modes force established planning. Some options affect
both semantics visible to the host application and the state available after
evaluation. For example, residual-expression construction needs partial
evaluation and tracked state.

See [Configuration options](../reference/configuration-options.md) for the
option reference, [Optimization mechanisms](../internals/optimization-mechanisms.md)
for how optimizations compose, and
[Errors, unknowns, and partial state](errors-unknowns-and-partial-state.md)
for partial evaluation.

CEL-Java does not impose a CPU, memory, result-size, or latency budget on an
evaluation. The embedding application is responsible for resource controls
appropriate to its inputs and deployment.
