# Using Jackson-described Java objects

The Jackson registries expose readable Java bean properties as CEL object fields. Use the registry
matching the application's Jackson major version:

| Jackson line | CEL-Java artifact | Registry |
| --- | --- | --- |
| Jackson 2 | `cel-jackson` | `org.projectnessie.cel.types.jackson.JacksonRegistry` |
| Jackson 3 | `cel-jackson3` | `org.projectnessie.cel.types.jackson3.Jackson3Registry` |

Use only the matching registry and `ObjectMapper` type. The Jackson 2 and Jackson 3 packages are not
interchangeable.

Add the selected Jackson integration alongside the regular CEL-Java dependency set:

```kotlin
dependencies {
  implementation(platform("org.projectnessie.cel:cel-bom:0.8.0"))
  implementation("org.projectnessie.cel:cel-tools")
  implementation("org.projectnessie.cel:cel-generated-pb")

  // Select one:
  implementation("org.projectnessie.cel:cel-jackson")
  // implementation("org.projectnessie.cel:cel-jackson3")
}
```

Choose exactly one of `cel-generated-pb` and `cel-generated-pb3` as described in the
[artifact reference](../reference/artifacts.md).

## Configure a compiler

This Jackson 2 example registers a Java type before compiling a field selection:

```java
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptCompiler;
import org.projectnessie.cel.types.jackson.JacksonRegistry;

record Request(String owner, List<String> roles) {}

TypeRegistry registry = JacksonRegistry.newRegistry();
ScriptCompiler compiler =
    ScriptCompiler.newBuilder()
        .registry(registry)
        .withTypes(Request.class)
        .withDeclarations(
            Decls.newVar("request", Decls.newObjectType(Request.class.getName())))
        .build();

Script script = compiler.compile(
    "request.owner == principal && request.roles.exists(role, role == 'editor')");

boolean allowed =
    script.execute(
        Boolean.class,
        Map.of("request", new Request("alice", List.of("editor")), "principal", "alice"));
```

For Jackson 3, replace the registry import and factory with `Jackson3Registry.newRegistry()`.

Registration and declaration serve different purposes. `withTypes(Request.class)` discovers the
object schema. `Decls.newVar(...)` tells the checker that the `request` variable has that registered
type. Runtime discovery of an unregistered object cannot retroactively make its fields available to
an already-running checker.

## Use application mapper configuration

Pass a configured mapper when CEL field names must follow the application's supported Jackson
property configuration:

```java
com.fasterxml.jackson.databind.ObjectMapper mapper =
    new com.fasterxml.jackson.databind.ObjectMapper()
        .setPropertyNamingStrategy(
            com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);

TypeRegistry registry = JacksonRegistry.newRegistry(mapper);
```

The Jackson 2 factory calls `ObjectMapper.copy()`. The Jackson 3 factory rebuilds the mapper into a
registry-owned mapper. Later changes to the caller's mapper do not affect the registry. Jackson
extension objects that Jackson itself shares between mapper copies or builds must remain stable.

Supported configuration includes ordinary bean-property naming, mix-ins, visibility, and modules
that modify property discovery. The registry does not claim to reproduce arbitrary custom
serializer output as CEL fields.

Directly and mutually recursive object schemas are supported. Discovery publishes a schema graph
only after the complete graph initializes successfully; a failed discovery does not leave a partial
graph and can be retried.

## Supported Java shapes

The registry reads host objects; it does not construct Jackson-described objects for CEL object
literals.

| Java shape | CEL view |
| --- | --- |
| `boolean` / `Boolean` | `bool` |
| integral primitives and wrappers | `int` |
| `ULong` | `uint` |
| `float` / `double` and wrappers | `double` |
| `String` | `string` |
| `byte[]` / Protobuf `ByteString` | `bytes` |
| Java or Protobuf duration | `duration` |
| `Instant`, `ZonedDateTime`, or Protobuf timestamp | `timestamp` |
| `int[]`, `long[]`, `double[]` | typed CEL list |
| reference array | recursively typed CEL list |
| `Collection<E>` | `list(E)` |
| `Map<K,V>` | `map(K,V)` |
| `Optional<T>` | contained checked type |
| Java enum | CEL `int` ordinal |

Map keys must map to CEL `bool`, `int`, `uint`, or `string`. `boolean[]`, `short[]`, `char[]`, and
`float[]` are unsupported. Container properties for which Jackson supplies no element, key, or
value type are rejected during registration. A `long[]` is inferred as signed `list<int>`.

A null property is absent for CEL presence testing and reads as CEL null. An empty
`Optional` also adapts to CEL null, but its Java representation and property-presence behavior are
not interchangeable with a null property. Both differ from a missing activation binding and an
unknown value. See
[types and values](../concepts/types-and-values.md).

Jackson enum serialization names do not change CEL enum representation: registered Java enums use
their integer ordinal.

## Errors and unsupported operations

Invalid or unknown enum values produce CEL errors. Unsupported property shapes and failed object
discovery are Java configuration failures. `registerType(Type...)` and `newValue(...)` throw
`UnsupportedOperationException`, because this registry does not define CEL object-literal
construction for arbitrary Java objects.

At the high-level API, parse and check failures are `ScriptCreateException`, while CEL error
results are `ScriptExecutionException`. Read
[errors, unknowns, and partial state](../concepts/errors-unknowns-and-partial-state.md) for the full
distinction.

## Ownership, reuse, and exact mode

`copy()` snapshots mapper configuration and creates independent type-registration state.
`ScriptCompiler.Builder.build()` also copies the supplied registry, then installs `withTypes()`
entries in the compiler-owned copy.

Configure a registry before sharing it. Reusable registries and scripts support concurrent callers,
but mapper extensions, custom adapters, and all values reachable from an evaluation input must
remain stable for the access they receive. Do not mutate input collections or object state while an
evaluation is running.

`newExactAggregateRegistry()` and its mapper-taking overload opt into a stricter, recursively
homogeneous aggregate representation contract for aggregate activation values and eligible object
fields. For values that satisfy that contract, exact and general registries preserve the same CEL
semantics. A detected contract violation produces a CEL error; it does not retry through general
adaptation. Exact mode does not promise that a particular expression will use an optimization; use
it only when the application can satisfy its documented null, key, value, optional, and cycle
constraints. See [native evaluation](../internals/native-evaluation.md).

## Integration checklist

- Select `cel-jackson` or `cel-jackson3` to match the application's mapper.
- Supply configured mapper state before registry construction.
- Register every object and enum type needed by the checker before compiling.
- Declare activation variables with their registered object names.
- Keep mapper extensions, registries, and evaluation inputs stable while shared.
- Test naming rules, recursion, null/presence behavior, container shapes, enums, and failures.

See [testing CEL integrations](testing-cel-integrations.md) for suggested contract tests.
