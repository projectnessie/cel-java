# Java implementation of Common-Expression-Language (CEL)

See https://opensource.google/projects/cel
See https://github.com/google/cel-go
See https://github.com/google/cel-spec

## Getting started

CEL-Java is not yet deployed to Maven Central, so you have to build it locally (see below).

The easiest way to get started is to add a dependency to your Maven project
```xml
<dependency>
  <groupId>org.projectnessie.cel</groupId>
  <artifactId>cel-tools</artifactId>
  <version>0.1-SNAPSHOT</version>
</dependency>
```
or Gradle project.
```groovy
dependencies {
    implementation("org.projectnessie.cel:cel-tools:0.1-SNAPSHOT")
}
```

The `cel-tools` artifact provides a simple entry point `ScriptHost` to produce `Script` instances.
A very simple start:

```java
import static java.util.Collections.emptyList;

import com.google.api.expr.v1alpha1.Decl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptHost;

public class MyClass {

  public void myScriptUsage() {
    // Build the script factory
    ScriptHost scriptHost = ScriptHost.newBuilder().build();

    // Variable declarations - we need `x` and `y` in this example
    List<Decl> declarations = singletonList(
        Decls.newVar("x", Decls.String),
        Decls.newVar("y", Decls.String),
        );

    // no custom types (e.g. protobuf message default instances)
    List<Object> types = emptyList();

    // create the script, will be parsed and checked
    Script script = scriptHost.getOrCreateScript("x + ' ' + y", declarations, types);

    Map<String, Object> arguments = new HashMap<>();
    arguments.put("x", "hello");
    arguments.put("y", "world");

    String result = script.execute(String.class, arguments);

    System.out.println(result); // Prints "hello world"
  }
}
```

## Building and testing CEL-Java

The CEL-Java repo uses git submodules to pull in required APIs from Google and the CEL-spec.
Those submodules are required to build the CEL-Java project (aka run a `git submodule init` /
`update`).

Requirements:
* Java 8 or newer, it's a Gradle-wrapper build (it's fast ;) )

`./gradlew test` builds the production code and runs the unit tests.

The project uses the Google Java code style and uses the Spotless plugin. Run
`./gradlew spotlessApply` to fix formatting issues.

`./gradlew check` runs all checks, including unit tests and JMH microbenchmarks.

`./gradlew publishToMavenLocal` deploy the current development to the local Maven repo, in
case you want to pull it the CEL-Java "snapshot" artifacts another project.

To run the CEL-spec conformance tests, Go, the bazel build tool plus toolchains are required.
Form the CEL-Java repo, just run `conformance/run-conformance-tests.sh`. That script performs
the necessary Gradle and bazel builds.

## CEL-Java implementation specifics

### Not yet implemented

* JSON extension ([see spec](https://github.com/google/cel-spec/blob/master/doc/langdef.md#json-data-conversion) and for example `nonFinite` in `com_github_golang_protobuf/jsonpb/decode.go`, around line 441)

### Unsigned integer

Java does not have a native (primitive) type "unsigned int/long" or `uint32`/`uint64`.
Support for the CEL type `uint` is therefore a bit more work in Java.

To maintain conformance to the CEL-spec, the CEL-Java implementation treats CEL's `uint` type
differently. This means, that for example the expression `123 == 123u` is *not* true, but
`123u == 123u` and `123 == 123` are.

TL;DR If you have a `uint32`/`uint64` in your protobuf objects or use `uint`s in your CEL
expression, you *must* wrap those with the `org.projectnessie.cel.common.ULong` type.

### Unclear double-to-int rounding behavior

Rounding/truncating of numeric values, especially when converting the CEL type `double` to
`int` or `uint`. The CEL spec says: _CEL provides no way to control the finer points of
floating-point arithmetic, such as expression evaluation, **rounding mode**, or exception handling.
However, any two not-a-number values will compare equal even if their underlying properties are
different._ ([see spec](https://github.com/google/cel-spec/blob/master/doc/langdef.md#numeric-values)).

The technical situation is ambiguous. The CEL-Go unit test
`common/types/double_test.go/TestDoubleConvertToType` asserts on the result `-5` for the CEL
expression `int(-4.5)`, because CEL-Go uses the `math.Round(float64)` function.

Since the CEL-spec is not clear, and the CEL-conformance-tests assert on double-to-int "truncation"
(aka think Java-ish: `double doubleValue; long res = (long) doubleValue;`), the CEL-Java
implementation just implements the functionality that passes the CEL-spec conformance tests.

(Note: the implementation of Go's `math.Round(float64)` behaves differently to Java's
`Math.round(double)` (or `Math.rint()`) and a 1:1 port of the CEL-Go behavior is rather not
that trivial.)

Note: The CEL-Go implementation does not pass the CEL-spec conformance tests:

    --- FAIL: TestSimpleFile/conversions/int/double_truncate (0.01s)
        simple_test.go:219: double_truncate: Eval got [int64_value:2], want [int64_value:1]
    --- FAIL: TestSimpleFile/conversions/int/double_truncate_neg (0.01s)
        simple_test.go:219: double_truncate_neg: Eval got [int64_value:-8], want [int64_value:-7]
    --- FAIL: TestSimpleFile/conversions/int/double_half_pos (0.01s)
        simple_test.go:219: double_half_pos: Eval got [int64_value:12], want [int64_value:11]
    --- FAIL: TestSimpleFile/conversions/int/double_half_neg (0.01s)
        simple_test.go:219: double_half_neg: Eval got [int64_value:-4], want [int64_value:-3]
    --- FAIL: TestSimpleFile/conversions/uint/double_truncate (0.01s)
        simple_test.go:219: double_truncate: Eval got [uint64_value:2], want [uint64_value:1]
    --- FAIL: TestSimpleFile/conversions/uint/double_half (0.01s)
        simple_test.go:219: double_half: Eval got [uint64_value:26], want [uint64_value:25]
