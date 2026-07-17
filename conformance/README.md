# Running CEL-Spec conformance tests against CEL-Java

The CEL-Java conformance suite is a JUnit test suite that reads upstream CEL-Spec
simple testdata from the `submodules/cel-spec` Git submodule.

Run it with:

```shell
./gradlew :cel-conformance:test
```

The suite does not require Bazel, Go, a separate conformance server, or the old
upstream `simple_test` binary.

## Test selection

The curated conformance file list and skip list live in:

```text
conformance/src/test/java/org/projectnessie/cel/conformance/SimpleConformanceTest.java
```

Each skip uses the upstream conformance path:

```text
file/section/test
```

or a whole-section path:

```text
file/section
```

Unmatched skips fail the test suite, so stale skips are visible when upstream
testdata changes.

Optional CEL-Spec files such as extension libraries, optionals, and type
deduction are intentionally not enabled by default. Add those separately with
explicit skip reasoning.
