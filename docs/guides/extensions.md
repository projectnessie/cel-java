# Bundled extension libraries

CEL-Java includes opt-in libraries beyond the CEL standard library. Enabling one changes the
language surface accepted by the checker, so install only the extensions intended by the
application. Expressions that use them may not be portable to other CEL implementations or hosts.

## Extension catalog

| Library | Factory | Main CEL surface |
| --- | --- | --- |
| `EncodersLib` | `EncodersLib.encoders()` | `base64.encode(bytes)`, `base64.decode(string)` |
| `MathLib` | `MathLib.math()` | min/max, rounding, numeric predicates, absolute/sign, and bit operations |
| `NetworkLib` | `NetworkLib.network()` | IP/CIDR parsing, formatting, classification, containment, masking, and prefixes |
| `OptionalLib` | `OptionalLib.optionals()` | optional construction, access, fallback, selection/indexing, `optMap`, `optFlatMap` |
| `ProtoLib` | `ProtoLib.proto()` | `proto.hasExt`, `proto.getExt` |
| `StringsLib` | `StringsLib.strings()` | indexing, search, join, case conversion, replace, reverse, split, substring, trim, format, quote |

`base64.decode` returns a CEL error for invalid input. Network parsing and operations likewise
report invalid values through CEL errors. Optional access distinguishes an absent optional from CEL
null. Consult each library's Javadocs for its complete overload and error contract.

### Math

The math library includes `math.greatest`, `math.least`, `math.ceil`, `math.floor`, `math.round`,
`math.trunc`, `math.abs`, `math.sign`, `math.isNaN`, `math.isInf`, `math.isFinite`,
`math.bitAnd`, `math.bitOr`, `math.bitXor`, `math.bitNot`, `math.bitShiftLeft`, and
`math.bitShiftRight`.

### Network

The network library introduces IP and CIDR values. It provides `ip`, `cidr`, `isIP`,
`ip.isCanonical`, string conversion, address-family and classification methods, CIDR containment,
masking, network-address access, and prefix length.

### Strings

String receiver functions include `charAt`, `indexOf`, `join`, `lastIndexOf`, `lowerAscii`,
`replace`, `reverse`, `split`, `substring`, `trim`, `upperAscii`, and `format`; the library also
provides `strings.quote`.

### Optionals

The optional library provides `optional.none()`, `optional.of(value)`, and
`optional.ofNonZeroValue(value)`, plus the receiver methods `hasValue()`, `value()`, `or(...)`,
`orValue(...)`, `optMap(...)`, and `optFlatMap(...)`.

It also enables optional selection and indexing:

```cel
request.?owner.orValue("anonymous")
labels[?"environment"].hasValue()
```

Optional-aware aggregate entries use the `?` prefix and omit entries whose optional has no value.
Calling `value()` on `optional.none()` produces a CEL error. A present optional containing CEL null
is distinct from an absent optional.

## Install bundled extensions

The consistent public installation path for these factories is the lower-level `Env` API:

```java
import java.util.Map;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.extension.MathLib;
import org.projectnessie.cel.extension.StringsLib;

Env env = Env.newEnv(StringsLib.strings(), MathLib.math());
Env.AstIssuesTuple compiled = env.compile("math.greatest(2, 7) == 7 && ' CEL '.trim() == 'CEL'");
if (compiled.hasIssues()) {
  throw compiled.getIssues().err();
}

Program program = env.program(compiled.getAst());
Val result = program.eval(Map.of()).getVal();
```

`Env.newEnv()` installs the CEL standard library before applying the supplied environment options.
Install all extensions and types before compiling and before sharing the environment. Environment
checking freezes configuration that affects the checker.

`ScriptCompiler.Builder.withLibraries(...)` accepts `Library` instances. The bundled factory
methods above return `EnvOption`, not `Library`, so calls such as
`withLibraries(StringsLib.strings())` do not type-check. Use `Env` with the factories for a
consistent installation path. `withLibraries(...)` is the convenient high-level path for a custom
class that directly implements `Library`; see [custom functions](custom-functions.md).

## Protobuf extension fields

`ProtoLib` operates on Protobuf extension fields. Its extension-name argument is a fully qualified
field name. Expressions that use an extension identifier rather than a string literal also need
the descriptor declaring that extension registered with the type provider. See
[using Protobuf values](protobuf.md).

## Portability and trust

Bundled extensions are opt-in and not assumed to exist in another CEL host. Record enabled
extensions as part of the application's policy contract and test policy sources with that exact
configuration.

Extension operations run inside evaluation. Apply the same input bounds and host resource controls
as for standard functions; installing a library does not create a resource sandbox. See
[using CEL for authorization](authorization.md) for higher-consequence use cases.
