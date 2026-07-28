# Types and values

CEL has a type system for checking expressions and a runtime value model for
evaluation. CEL-Java connects both to Java, but a compile-time declaration and
a runtime Java value serve different purposes.

## Declarations describe expression inputs

A declaration gives the checker a name and CEL type:

```java
ScriptCompiler compiler =
    ScriptCompiler.newBuilder()
        .withDeclarations(
            Decls.newVar("enabled", Decls.Bool),
            Decls.newVar("attempts", Decls.Int),
            Decls.newVar(
                "labels",
                Decls.newMapType(Decls.String, Decls.String)))
        .build();
```

Declarations do not hold evaluation values. Supply those values when the
compiled script is executed:

```java
Map<String, Object> bindings =
    Map.of(
        "enabled", true,
        "attempts", 3L,
        "labels", Map.of("environment", "production"));

boolean result = script.execute(Boolean.class, bindings);
```

Frequently used declaration types include `Decls.Bool`, `Decls.Bytes`,
`Decls.Double`, `Decls.Int`, `Decls.String`, `Decls.Uint`, `Decls.Duration`,
`Decls.Timestamp`, and `Decls.Dyn`. Build aggregate types with
`Decls.newListType()` and `Decls.newMapType()`. Registered message or object
types use their registered, fully qualified names.

Use `Dyn` deliberately. It defers type decisions to runtime and therefore
gives the checker fewer opportunities to reject an invalid expression.

## Java values are adapted at runtime

The configured `TypeAdapter` converts Java inputs to CEL runtime values and
successful CEL results back to requested Java types. The default environment
supports the standard scalar and aggregate representations used by CEL-Java.
Type registries add application object types such as Protobuf messages or
Jackson-described classes.

The declared CEL type and the supplied Java representation must agree. A
declaration such as `Decls.Int` describes a CEL signed integer; the adapter
determines which Java representations it accepts for that value. Consult
[Supported types](../reference/supported-types.md) when choosing boundary
types.

Object integration is registry-specific:

- [Protobuf integration](../guides/protobuf.md) explains message registration
  and field access.
- [Jackson integration](../guides/jackson.md) explains Java-class discovery
  and configured object mapping.

## CEL runtime values

Every runtime CEL value implements `Val`. This includes ordinary values as
well as CEL errors and unknowns.

The tools API normally converts a successful value to the requested Java
type:

```java
boolean allowed = script.execute(Boolean.class, bindings);
```

Request `Val.class` when the caller needs CEL-level inspection:

```java
Val raw = script.execute(Val.class, bindings);
```

Raw values expose the runtime representation and avoid the final Java result
conversion. They do not make an invalid expression valid, and their
conversion methods can still fail when the requested native representation is
incompatible.

Prefer a specific Java result type at ordinary application boundaries. Use
raw `Val` results for interpreters, diagnostic tools, partial evaluation, and
other code that intentionally handles unknowns. `Script` reports CEL errors as
`ScriptExecutionException` even when `Val.class` is requested; use the
lower-level `Program` API when code must inspect an `Err` value directly.

## Absence and null are different

An absent binding means that the activation cannot resolve a variable name.
It is different from a present binding whose value is Java `null`; the
standard adapter represents the latter as CEL null.

`ActivationFunction` makes this distinction explicit:

```java
ActivationFunction activation =
    name -> {
      if (name.equals("optionalValue")) {
        return null; // Present value: CEL null.
      }
      return ActivationFunction.ABSENT;
    };
```

The older `java.util.function.Function<String, Object>` activation form
cannot express the same distinction: its `null` result means absence. Prefer
`ActivationFunction` for new code that needs to supply CEL null.

An activation backed by a map distinguishes a missing key from a key mapped
to `null`. Some Java map factories, including `Map.of()`, reject null values;
use a map implementation that permits null when this distinction is needed.

## Mutable values and lazy access

CEL-Java may retain aggregate inputs and access their contents during
evaluation instead of eagerly copying the entire object graph. Supplier-based
bindings may also be resolved lazily and memoized by the activation.

Consequently:

- do not mutate a bindings map or reachable list, map, message builder, or
  object while evaluation is using it;
- make lazy suppliers safe for the way the compiled program is shared;
- publish immutable inputs where practical.

These rules preserve a stable view of one evaluation. They do not imply that
all host objects are defensively copied.

See [Execution lifecycle](execution-lifecycle.md) for ownership and reuse,
and [Errors, unknowns, and partial state](errors-unknowns-and-partial-state.md)
for the difference between missing inputs, CEL errors, and unknown values.
