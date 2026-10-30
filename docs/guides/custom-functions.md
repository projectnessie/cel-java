# Adding custom functions

A custom `Library` keeps a CEL declaration and its runtime implementation together. The declaration
lets the checker select a stable overload identifier; the program option registers the
implementation under that identifier.

## A complete unary function

This library adds `app.stringLength(string) -> int`:

```java
import java.util.List;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.interpreter.functions.Overload;

public final class StringLengthLibrary implements Library {
  private static final String FUNCTION = "app.stringLength";
  private static final String OVERLOAD = "app_string_length_string";

  @Override
  public List<EnvOption> getCompileOptions() {
    return List.of(
        EnvOption.declarations(
            Decls.newFunction(
                FUNCTION,
                Decls.newOverload(OVERLOAD, List.of(Decls.String), Decls.Int))));
  }

  @Override
  public List<ProgramOption> getProgramOptions() {
    return List.of(
        ProgramOption.functions(
            Overload.unary(
                OVERLOAD,
                value -> {
                  if (!(value instanceof StringT)) {
                    return Err.newErr("app.stringLength requires a string");
                  }
                  String text = value.convertToNative(String.class);
                  int length = text.codePointCount(0, text.length());
                  return org.projectnessie.cel.common.types.IntT.intOf(length);
                })));
  }
}
```

Install the library in a reusable high-level compiler:

```java
import java.util.Map;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptCompiler;

ScriptCompiler compiler =
    ScriptCompiler.newBuilder()
        .withLibraries(new StringLengthLibrary())
        .withDeclarations(Decls.newVar("name", Decls.String))
        .build();

Script script = compiler.compile("app.stringLength(name) >= 3");
boolean result = script.execute(Boolean.class, Map.of("name", "CEL"));
```

Checked calls dispatch by the overload identifier selected by the checker. Keep function names and
overload identifiers stable once expressions depend on them. The checker prevents the wrong
argument type in ordinary `ScriptCompiler` use; the runtime guard keeps the implementation from
turning an unexpected value into an unrelated Java cast failure. This example defines length as a
Unicode code-point count; an application extension must document such host-specific semantics.

## Library responsibilities

`getCompileOptions()` can contribute declarations and macros. `getProgramOptions()` contributes
runtime overloads, globals, or other evaluation configuration. A useful library:

- declares every callable overload and registers a matching runtime implementation;
- uses unique, namespaced function names and stable overload identifiers;
- returns non-null option lists, options, and CEL `Val` results;
- returns a CEL error value for expected evaluation failures instead of Java null;
- preserves incoming CEL error and unknown behavior when implementing more complex dispatch;
- keeps implementation objects immutable or thread-safe;
- documents supported types, errors, determinism, side effects, and portability.

Registering the same dispatcher identifier twice in one program configuration is an error.

## Trust and resource behavior

Custom functions are host code invoked during expression evaluation. They can perform I/O, block,
allocate, mutate external state, or throw Java exceptions unless their implementation prevents it.
That makes them part of the host's trust and resource boundary.

For policy evaluation, prefer deterministic, side-effect-free operations over already-bounded
inputs. Propagate an explicit host deadline to operations that must call external services; an
overall executor timeout does not by itself prove that a blocked function has stopped. See
[using CEL for authorization](authorization.md).

## Macros and lower-level installation

Macros transform syntax before checking and need substantially more compatibility care than an
ordinary function. Use the public `ExprHelper` APIs and pair macro expansion with declarations and
runtime behavior where necessary. Test parse shape, checking, evaluation, and error locations.

A `Library` can also be installed through `Library.Lib(library)` as an `EnvOption` when using
`Env` directly. The high-level `ScriptCompiler.Builder.withLibraries(...)` path is preferable for
ordinary custom libraries. The bundled extension factories have a different installation shape;
see [bundled extensions](extensions.md).

## Testing a library

Test declaration/implementation agreement, every overload, boundary inputs, CEL errors, unknown
propagation, concurrent calls, and any stated determinism or resource bound. Behavioral tests
should compile source through the same host configuration used by the application. See
[testing CEL integrations](testing-cel-integrations.md).
