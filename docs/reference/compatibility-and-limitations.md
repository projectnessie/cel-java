# Compatibility and limitations

This page collects boundaries that affect dependency selection, expression portability, and
security assumptions.

## Java and build requirements

Published CEL-Java libraries target Java 17. The repository build uses a Java 21 toolchain while
compiling library bytecode with a Java 17 release target. The two Quarkus smoke applications target
Java 21.

## Supported releases

Only the latest published CEL-Java release receives security fixes. Older releases are not
supported; see the [security policy](../../SECURITY.md).

CEL-Java does not promise binary compatibility between every pre-1.0 release. Keep CEL-Java modules
on one version through the BOM and test application expressions when upgrading.

## CEL language and protocol baseline

The repository’s conformance module runs test data from the pinned CEL specification submodule.
The current language-definition baseline used for enum behavior is CEL-Spec v0.25.2.

Protocol representations such as `ParsedExpr` and `CheckedExpr` are useful interchange structures,
but an `Ast` is not advertised as a version-independent serialized-script format. Checked
expressions require their reference and type maps, and newer expression forms or source-position
details may not round-trip through older protocol consumers.

Pin compatible protocol and CEL-Java versions and test the particular syntax/features used before
persisting or exchanging ASTs.

## Protocol Buffer runtime selection

Regular modular deployments must include exactly one generated artifact:

- `cel-generated-pb` for the current Protobuf runtime;
- `cel-generated-pb3` for the Protobuf 3 runtime line.

Both define the same generated CEL protocol classes. They cannot coexist on one classpath. See
[Artifacts](artifacts.md).

Protocol Buffer enums are CEL `int` values under the project’s language baseline. The optional
strong-enum interpretation from newer CEL work is not enabled because it is mutually incompatible
with integer enum semantics.

## Regular expressions

CEL-Java defaults to `RegexEngine.JAVA` to retain compatibility with existing CEL-Java expressions.
Java’s regular-expression syntax and backtracking behavior are therefore the default semantics of
the standard `matches` function.

`RegexEngine.RE2` opts into RE2/J. It provides RE2 syntax and non-backtracking execution, but some
Java constructs—such as backreferences and lookaround—are not part of RE2. Switching engines can
make an existing pattern invalid or change its accepted syntax. Configure and test the engine as
part of the application’s expression contract.

## Numeric conversions

CEL numeric types are distinct:

- `int` is signed 64-bit;
- `uint` is unsigned 64-bit;
- `double` is IEEE-754 double precision.

CEL-Java follows its pinned CEL conformance behavior for conversions, including truncation of
in-range finite `double` values toward zero when converting to `int` or `uint`. Out-of-range,
infinite, and NaN conversions produce CEL errors.

Integer result conversions to Java `int` and 32-bit wrappers are range checked. Conversion from CEL
`double` to Java `float` follows Java narrowing-cast behavior and can produce infinity for a finite
value outside the float range.

## Java object access

The default type system does not expose arbitrary Java classes through unrestricted reflection.
Use explicitly registered Protocol Buffer messages, a Jackson registry, or a custom
adapter/provider.

Jackson 2 and Jackson 3 are separate integration modules. Register every root or recursively
referenced application class that the chosen registry requires before checking. Jackson property
names—not necessarily Java field names—are the CEL field names.

## Native evaluation

Native evaluation means direct evaluation over supported Java-native representations. It does not
mean Java source generation, bytecode generation, JNI, ahead-of-time compilation, or CPU-native
code.

It is an internal planning choice for eligible checked expressions, with fallback to established
CEL value evaluation. Applications must not depend on a particular node being native-eligible.
Options that require complete interpreter observation select established planning. See
[Native evaluation](../internals/native-evaluation.md).

## Resource safety

CEL restricts side effects and does not provide general recursion, but that does not make every
expression or input cheap. Input-dependent comprehensions, large aggregate traversal, dynamic
regular expressions, and custom overloads can consume significant CPU and memory.

CEL-Java has parser recursion and source-size limits. It does not currently provide a general
evaluation step limit, deadline, heap limit, or comprehension-iteration budget. The cost API
reports a heuristic interval; it does not enforce one.

Applications that accept untrusted expressions or unbounded input must apply their own controls:
expression admission and feature policy, input-size limits, timeouts or cancellation at an
appropriate isolation boundary, and limits around custom functions. A thread interruption alone is
not documented as a complete evaluation-cancellation mechanism.

## Input mutation

Inputs to `Program.eval(...)` or `Script.execute(...)`, including values reachable from a supplied
map, must not be mutated while evaluation is running. CEL-Java may retain or lazily adapt aggregates
and object metadata. Concurrent collection classes do not create a stable logical snapshot.

## Parser options

The low-level parser defaults to recursion depth 250 and 100,000 Unicode code points. Its
`errorRecoveryLimit` property is currently stored but not enforced. None of these settings limits
runtime evaluation.

## Extension availability

CEL-Java includes optional, string, math, network, encoder, and Protocol Buffer extension
libraries; see [Bundled extensions](../guides/extensions.md). The CEL JSON conversion extension is
not currently provided as a bundled library.
