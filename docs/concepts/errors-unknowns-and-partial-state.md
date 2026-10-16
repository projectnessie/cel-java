# Errors, unknowns, and partial state

Several distinct conditions can prevent an expression from producing an
ordinary value. Keeping them separate is important for both application
behavior and diagnostics.

| Condition | Meaning |
| --- | --- |
| Parse or check issue | The source is syntactically invalid or does not type-check in the configured environment. |
| CEL error | Evaluation reached an invalid CEL operation, such as an incompatible operation or a missing required binding. |
| Unknown | Evaluation intentionally lacks enough information to determine a value. |
| Absent binding | An activation cannot resolve a variable name. |
| CEL null | A present CEL value, not a missing binding. |
| Java exception | Host integration, conversion, or an internal component failed outside the CEL value model. |

## Construction diagnostics

`ScriptCompiler.compile()` throws `ScriptCreateException` when parsing or
checking fails. The exception exposes the associated `Issues`; use those
structured issues when an application needs source locations or multiple
diagnostics.

The lower-level `Env` API returns an `AstIssuesTuple`:

```java
Env.AstIssuesTuple compiled = env.compile(source);
if (compiled.hasIssues()) {
  throw compiled.getIssues().err();
}
```

Do not construct a `Program` from an AST whose compilation reported issues.

## CEL errors

A lower-level `Program` returns a CEL error as an `Err` value in
`Program.EvalResult`; it does not turn that CEL result into a checked Java
exception:

```java
Program.EvalResult result = program.eval(bindings);
Val value = result.getVal();
if (value instanceof Err) {
  // Inspect or propagate the CEL error.
}
```

The tools API is intentionally more application-oriented.
`Script.execute()` translates a CEL error result into
`ScriptExecutionException`.

Unexpected failures in an application-supplied function, adapter, provider,
or conversion path are Java failures rather than CEL errors and can surface
as runtime exceptions. Code should not treat every Java exception as a
user-expression error.

## Unknown values

An unknown means that evaluation deliberately proceeded with incomplete
information. It is a CEL runtime value and carries the expression IDs that
contributed to the unknown result. Callers can inspect
`UnknownT.expressionIds()` when that provenance is needed.

With `Script`, request `Val.class` or `Object.class` to receive an unknown
result for CEL-level handling. A more specific requested Java result type
cannot represent an unknown and causes `ScriptExecutionException`.

Unknown is not the same as absence. An ordinary absent variable generally
causes a CEL error. Partial evaluation turns selected attribute accesses into
unknowns so that the rest of the expression can still be evaluated.

## Partial evaluation

The lower-level API supports partial activations, state tracking, and residual
expressions. This example knows `known` but marks `pending` as unknown:

```java
import static org.projectnessie.cel.CEL.attributePattern;
import static org.projectnessie.cel.CEL.partialVars;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptPartialEval;
import static org.projectnessie.cel.EvalOption.OptTrackState;
import static org.projectnessie.cel.ProgramOption.evalOptions;

import java.util.Map;
import org.projectnessie.cel.Ast;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.UnknownT;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.Activation.PartialActivation;

Env env =
    Env.newEnv(
        declarations(
            Decls.newVar("known", Decls.Bool),
            Decls.newVar("pending", Decls.Bool)));

Env.AstIssuesTuple compiled = env.compile("known && pending");
if (compiled.hasIssues()) {
  throw compiled.getIssues().err();
}

Program program =
    env.program(
        compiled.getAst(),
        evalOptions(OptTrackState, OptPartialEval));

PartialActivation input =
    partialVars(
        Map.<String, Object>of("known", true),
        attributePattern("pending"));

Program.EvalResult result = program.eval(input);
Val value = result.getVal();

if (UnknownT.isUnknown(value)) {
  Ast residual =
      env.residualAst(compiled.getAst(), result.getEvalDetails());
  // Store, display, or compile the residual AST as appropriate.
}
```

`OptPartialEval` enables unknown-aware evaluation. `OptTrackState` records the
per-expression values needed by `residualAst()`. Each evaluation returns a
distinct mutable state object; do not share that state between evaluations or
mutate it concurrently.

A residual AST contains the work that could not be decided from the known
inputs. Residual construction may assign new expression identifiers and, for
a checked source AST, reparse and recheck the rewritten expression. Treat the
residual as an artifact of the same configured CEL environment: declarations,
types, functions, macros, and extensions still matter.

## Choosing the appropriate boundary

Use typed `Script.execute()` when the application expects either a concrete
Java result or a checked script exception. Use raw `Val`, `Program`, and
evaluation details when the application is itself implementing CEL-aware
diagnostics, unknown propagation, or partial evaluation.

Neither error handling nor partial evaluation supplies execution resource
limits. The embedding application remains responsible for bounding work when
expressions or data are untrusted.

See [Types and values](types-and-values.md) for null and activation semantics,
[Execution lifecycle](execution-lifecycle.md) for program ownership, and
[Configuration options](../reference/configuration-options.md) for evaluation
options.
