# Extension points

CEL-Java separates language declarations, Java-to-CEL conversion, type metadata, runtime overloads,
and variable lookup. Choose the narrowest extension point that matches the integration.

## Extension-point map

| Need | Extension point |
| --- | --- |
| Package declarations and runtime overloads together | `Library` |
| Convert additional Java values into CEL values | `TypeAdapter` |
| Resolve CEL object types, identifiers, and fields | `TypeProvider` |
| Register and construct native object types | `TypeRegistry` |
| Resolve variables lazily | `ActivationFunction` |
| Implement a declared function overload | `Overload` |
| Implement a new CEL value kind | `Val` and the relevant trait interfaces |

The parser, checker, planner, and internal decorator classes are low-level implementation surfaces.
Use them only when the public extension points cannot express the requirement.

## Libraries

A [`Library`](../../core/src/main/java/org/projectnessie/cel/Library.java) contributes compile-time
`EnvOption`s and runtime `ProgramOption`s. It is the most cohesive way to ship a reusable language
extension because the checker declaration and evaluator implementation remain paired.

```java
Library library =
    new Library() {
      @Override
      public List<EnvOption> getCompileOptions() {
        return List.of(EnvOption.declarations(myFunctionDeclaration));
      }

      @Override
      public List<ProgramOption> getProgramOptions() {
        return List.of(ProgramOption.functions(myRuntimeOverload));
      }
    };
```

Install libraries through `ScriptCompiler.Builder.withLibraries(...)` or `Library.Lib(...)` on an
`Env`. Options contributed by a library are applied in order. Duplicate runtime overload IDs are
rejected, so use stable, globally unique overload IDs.

The [bundled extensions guide](../guides/extensions.md) covers the extensions distributed with
CEL-Java. Some bundled extension factories currently expose an `EnvOption` directly instead of a
`Library`; install those while constructing an `Env`.

## Type adapters

A `TypeAdapter` maps Java values to `Val`. Custom adapters should normally delegate standard
representations to
[`TypeAdapterSupport.maybeNativeToValue(...)`](../../core/src/main/java/org/projectnessie/cel/common/types/ref/TypeAdapterSupport.java)
before handling application-specific types.

Adapters are used recursively for aggregate elements. They should:

- be deterministic for a stable input;
- return a CEL error for unsupported or invalid values instead of throwing incidental conversion
  exceptions;
- avoid mutating caller-owned inputs;
- be safe for concurrent reads once installed.

An adapter controls runtime conversion; it does not by itself teach the checker about an object
type or its fields.

## Type providers and registries

A `TypeProvider` supplies type declarations, identifier values, field metadata, and object
construction. A `TypeRegistry` combines provider behavior with native type registration and
copying.

Use an existing registry when possible:

- `ProtoTypeRegistry` for Protocol Buffer messages;
- the Jackson 2 or Jackson 3 registry for explicitly registered Jackson-backed classes.

`EnvOption.types(...)` mutates the registry currently installed in the environment during
configuration. A low-level custom environment can retain a caller-supplied registry. By contrast,
a `ScriptCompiler.Builder.registry(...)` value is copied at `build()`, then configured types are
registered on the owned copy. Registry implementations and the metadata objects they retain should
be treated as configure-before-share unless their own contract says otherwise.

Providers can expose an exact native field accessor for a checked field. Exact access lets the
evaluator avoid converting a complete object when it only needs one field, while preserving the
same CEL value and error semantics. See the [Protocol Buffers](../guides/protobuf.md) and
[Jackson](../guides/jackson.md) guides.

## Runtime overloads

Function declarations identify overloads by ID. `ProgramOption.functions(...)` installs matching
`Overload` implementations in the dispatcher.

Keep declaration and implementation signatures aligned:

- the same overload ID;
- the same arity;
- CEL argument and result types matching the Java-side value handling;
- explicit CEL errors for domain and conversion failures.

Prefer a `Library` for reusable functions. For a small application-local function, see
[Custom functions](../guides/custom-functions.md).

## Activations and lazy variable resolution

An `ActivationFunction` resolves a variable name at evaluation time. Its result represents either
an absent binding or a present binding whose value may itself be Java `null`. This distinction is
important because CEL null is a value while an absent name is unresolved and ordinarily produces a
CEL error. A CEL unknown is separate and is introduced deliberately through partial evaluation.

Resolvers may run more than once and may be called concurrently when a program is shared. Avoid
hidden mutable per-evaluation state in a shared resolver; wrap state in a per-evaluation activation
instead.

During a controlled operation, CEL-Java checks cancellation and finite budgets immediately before
and after synchronous resolver, regular-expression, and selected adapter/provider boundaries.
Elapsed time therefore includes a blocking callback. Thread CPU and allocation counters include
only work performed on the operation’s execution thread. CEL-Java cannot cooperatively stop
arbitrary callback code while that code retains control, and work delegated to another thread is
not charged to the executing-thread counters. Extension implementations should provide their own
bounded or interruptible behavior when they can block or perform large indivisible work.

## Custom values and traits

`Val` is CEL-Java’s runtime value contract. Trait interfaces such as `Adder`, `Indexer`, `Sizer`,
`IterableT`, and `FieldTester` opt a value into operators and functions.

Implementing a new `Val` is the most invasive extension:

- conversions must preserve CEL numeric, null, error, and unknown semantics;
- equality returns a CEL value and may propagate errors or unknowns;
- trait advertisement must agree with the interfaces actually implemented;
- conversion to requested Java and Protocol Buffer representations must be explicit;
- values used by shared programs should be immutable or safe for concurrent reads.

Prefer adapting an application type into an existing CEL value or exposing it through a
`TypeProvider` before introducing a new runtime value kind.

## API boundaries

`EnvOption` and `ProgramOption` are primarily factory-produced configuration tokens. Although
`EnvOption` is implementable as a public function, direct mutation of `Env` internals is not
available. `ProgramOption` refers to package-private construction state and is not an external SPI;
use its static factories.

Likewise, expression-builder classes whose public signatures contain package-private types are not
portable application extension points. Prefer source parsing, public AST conversion APIs, macros,
libraries, and declared overloads.
