# Supported types

CEL-Java adapts Java values at the boundary and evaluates them using CEL’s type and operator
semantics. A Java class is not automatically a CEL object type: Protocol Buffer messages and
Jackson-backed classes must be registered so the checker can resolve their fields.

## Built-in input mappings

The default adapter recognizes these common representations:

| CEL type | Java input representations |
| --- | --- |
| `null_type` | Java `null`, Protobuf `NullValue`, empty `Optional` |
| `bool` | `Boolean` |
| `int` | `Byte`, `Short`, `Integer`, `Long` |
| `uint` | `org.projectnessie.cel.common.ULong`, Protobuf `UInt32Value` and `UInt64Value` |
| `double` | `Float`, `Double` |
| `string` | `String` |
| `bytes` | `byte[]`, Protobuf `ByteString` |
| `duration` | `java.time.Duration`, Protobuf `Duration` |
| `timestamp` | `Instant`, `ZonedDateTime`, `Date`, `Calendar`, Protobuf `Timestamp` |
| `list(T)` | `List`, other `Collection`, object arrays, `int[]`, `long[]`, `double[]`, `String[]` |
| `map(K,V)` | `Map` |
| object | A registered Protocol Buffer message or a value supported by a custom registry |
| `type` | CEL type values and supported Protobuf type values |

A present `Optional` is adapted as its contained value; an empty `Optional` becomes CEL null. This
is distinct from CEL’s optional extension values described in
[Bundled extensions](../guides/extensions.md).

`boolean[]` and primitive-array types other than the ones listed above are not built-in list
representations. An unsupported Java array produces a CEL conversion error. `byte[]` is CEL bytes,
not a list of integers; an object array such as `byte[][]` is treated as a list whose elements are
adapted recursively.

## Aggregate behavior

List elements and map entries are adapted lazily or through checked exact-type paths. Caller-owned
collections may therefore be retained. Do not mutate an array, collection, map, or reachable value
while an evaluation is using it.

For checked variables, CEL-Java validates values against the declared aggregate type. Examples:

- `long[]` can represent `list(int)` or, through the checked unsigned path, `list(uint)`;
- `int[]` represents `list(int)`;
- `double[]` represents `list(double)`;
- map keys and values are checked against the declared `map(K,V)` types.

CEL map keys follow CEL rules. Valid key types are `bool`, `int`, `uint`, and `string`; the Java
map’s key objects must adapt to the declared CEL key type.

Java collection iteration order is not strengthened by CEL-Java. A plain `Map` has no ordering
guarantee; using an insertion-ordered or sorted implementation preserves that implementation’s
encounter order where traversal is observable, but CEL map equality is based on entries rather than
order.

## Protocol Buffer values

The default registry supports registered generated messages, dynamic message metadata understood by
the registry, CEL/Google well-known types, lists, maps, and wrappers. Register a message default
instance or descriptor before checking expressions that access its fields.

Protocol Buffer enum values follow the CEL language-definition baseline used by this project and
are exposed as CEL `int`. Strongly typed enum values are not enabled.

See [Protocol Buffers](../guides/protobuf.md).

## Jackson-backed values

The Jackson integrations expose explicitly registered Java classes through Jackson’s property
model. Supported property shapes include ordinary bean accessors, records, fields, and Jackson
naming/ignore annotations as understood by the configured Jackson version.

CEL-Java does not reflect over arbitrary Java classes through the default adapter. Register classes
with the Jackson 2 or Jackson 3 registry, or supply a custom provider/adapter.

See [Jackson](../guides/jackson.md).

## Result conversion

`Script.execute(Class<T>, ...)` converts the CEL result to the requested Java class. Request `Val`
to receive the CEL runtime value without native conversion.

Common scalar targets include boxed or primitive booleans and numbers, `String`, `byte[]` or
`ByteString`, Java or Protobuf duration/timestamp representations, and compatible object-message
classes. Lists and maps can be converted to compatible Java aggregate representations supported by
the value implementation.

Integer conversions to Java `int` and the corresponding 32-bit wrappers are range checked.
`TypeAdapter.valueToInt(...)`, for example, rejects values outside Java’s `int` range. Conversion
from CEL `double` to Java `float` currently uses Java’s narrowing cast and can produce infinity for
a finite value outside the float range. CEL `uint` uses `ULong` as its lossless Java object
representation; the primitive-long conversion exposes the unsigned raw bits.

CEL errors become `ScriptExecutionException`. Unknowns can be returned only when the requested type
is `Val` or `Object`; requesting an ordinary Java result type reports an execution exception.

## Custom mappings

Use a `TypeAdapter` to convert another Java representation into existing CEL values. Use a
`TypeProvider` or `TypeRegistry` when CEL source must name an object type, select fields, or
construct values. See [Extension points](../advanced/extension-points.md).
