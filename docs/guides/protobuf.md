# Using Protobuf values

CEL-Java can type-check and evaluate expressions over generated and dynamic Protobuf messages. The
checker needs the message descriptors before compilation; evaluation then adapts message instances
using the same registry.

## Select one generated-code artifact

CEL-Java does not choose a Protobuf runtime line transitively. Add `cel-tools` (or `cel-core`) and
exactly one of the generated-code artifacts:

```kotlin
dependencies {
  implementation(platform("org.projectnessie.cel:cel-bom:0.8.0"))
  implementation("org.projectnessie.cel:cel-tools")

  // Current Protobuf runtime:
  implementation("org.projectnessie.cel:cel-generated-pb")

  // Alternatively, for a Protobuf 3.25.x application:
  // implementation("org.projectnessie.cel:cel-generated-pb3")
}
```

Do not put `cel-generated-pb` and `cel-generated-pb3` on the same classpath. They contain the same
generated CEL classes compiled for different Protobuf runtime lines. See the
[artifact reference](../reference/artifacts.md) for the full module matrix.

## Register and use an application message

Register a representative message, normally its generated default instance, before compiling an
expression that refers to its fields:

```java
import java.util.Map;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptCompiler;

ScriptCompiler compiler =
    ScriptCompiler.newBuilder()
        .withDeclarations(
            Decls.newVar(
                "request",
                Decls.newObjectType(
                    com.example.api.AccessRequest.getDescriptor().getFullName())),
            Decls.newVar("principal", Decls.String))
        .withTypes(com.example.api.AccessRequest.getDefaultInstance())
        .build();

Script script = compiler.compile("request.owner == principal");

boolean allowed =
    script.execute(
        Boolean.class,
        Map.of(
            "request",
            com.example.api.AccessRequest.newBuilder().setOwner("alice").build(),
            "principal",
            "alice"));
```

`withTypes()` registers the root message, its file descriptor, and transitive descriptor
dependencies in the compiler-owned registry. The supplied default instance also selects the
generated representation for that message type. A `DynamicMessage` can be supplied instead when
dynamic representation is required.

Message schemas and variable declarations are different things: registration teaches the checker
what a message type contains, while `Decls.newVar()` gives an expression a variable of that type.

## Registry choices and ownership

`ProtoTypeRegistry.newRegistry(messages...)` is the general-purpose registry. It includes CEL
standard runtime types and Protobuf well-known types. Use it directly when you need to configure a
registry before creating a compiler:

```java
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;

ProtoTypeRegistry registry =
    ProtoTypeRegistry.newRegistry(com.example.api.AccessRequest.getDefaultInstance());

ScriptCompiler compiler = ScriptCompiler.newBuilder().registry(registry).build();
```

`ScriptCompiler.Builder.build()` copies the supplied registry. The resulting compiler owns that
copy, so later registrations on the caller's registry are not visible to it. Registry copies have
independent registration state.

Finish registry configuration before sharing it for compilation or evaluation. Registry caches may
be populated during evaluation, but registration and copying are configuration operations rather
than concurrent mutation APIs. As with every evaluation input, a message and all reachable values
must remain stable for the duration of a call. Generated and dynamic Protobuf messages are normally
immutable, which naturally satisfies that requirement.

### Exact aggregate registries

`ProtoTypeRegistry.newExactAggregateRegistry(messages...)` is an opt-in representation contract for
supported aggregate activation values and fields. It accepts certified Java representations for
those aggregates; unsupported field shapes and scalar fields keep their ordinary behavior.

Choose exact mode only when the application can honor its stricter representation contract. It does
not change CEL semantics for conforming values and does not guarantee that a particular expression
will use an optimization. A detected contract violation produces a CEL error rather than falling
back to general adaptation. The general registry remains the default. See
[performance and reuse](../advanced/performance-and-reuse.md) and
[native evaluation](../internals/native-evaluation.md) before selecting an exact registry for
performance reasons.

## Protobuf-to-CEL representation

The important mappings are:

| Protobuf shape | CEL view |
| --- | --- |
| singular scalar | corresponding CEL scalar |
| repeated field | `list(T)` |
| map field | `map(K, V)` |
| message | registered CEL object type |
| enum constant or field | CEL `int` |
| wrapper well-known type | wrapped scalar, or CEL null when unset |
| `Timestamp` | CEL `timestamp` |
| `Duration` | CEL `duration` |
| `Any` | its registered payload type when unpacking is possible |

Protobuf presence, CEL null, and an absent activation binding are distinct concepts. Consult
[types and values](../concepts/types-and-values.md) before depending on presence-sensitive
behavior.

The registry can also construct registered message values for CEL object literals. Invalid type
names, fields, or conversions become CEL error values. At the high-level `Script` API, CEL error
results are reported as `ScriptExecutionException`; parse and check failures are
`ScriptCreateException`. See [errors, unknowns, and partial state](../concepts/errors-unknowns-and-partial-state.md).

## Protobuf extensions

The bundled Protobuf extension library adds `proto.hasExt` and `proto.getExt`. Descriptors that
declare extension fields must be registered when expressions refer to those fields by identifier.
See [bundled extensions](extensions.md) for installation and portability notes.

## Integration checklist

- Put exactly one generated-code artifact on the classpath.
- Register every application message schema before compiling field selections.
- Use the same generated or dynamic representation expected at runtime.
- Configure registries before sharing them; do not mutate evaluation inputs concurrently.
- Test well-known types, presence, enum values, repeated fields, maps, and error cases used by the
  application.
- Exercise every supported Protobuf runtime line in a separate test classpath.

See [testing CEL integrations](testing-cel-integrations.md) for a broader contract-test strategy.
