# Testing CEL integrations

An integration test should cover the contract between source, checker declarations, registries,
runtime values, and the requested Java result—not only whether one expression returns the expected
value.

## Test through the application-facing API

Compile and evaluate with the same `ScriptCompiler` configuration used in production:

```java
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptCompiler;
import org.projectnessie.cel.tools.ScriptCreateException;

class PolicyContractTest {
  private final ScriptCompiler compiler =
      ScriptCompiler.newBuilder()
          .withDeclarations(
              Decls.newVar("owner", Decls.String),
              Decls.newVar("principal", Decls.String),
              Decls.newVar("roles", Decls.newListType(Decls.String)))
          .build();

  @Test
  void allowsOwnerOrAdministrator() throws Exception {
    Script policy =
        compiler.compile("owner == principal || roles.exists(role, role == 'admin')");

    assertTrue(
        policy.execute(
            Boolean.class,
            Map.of("owner", "alice", "principal", "alice", "roles", List.of())));
    assertFalse(
        policy.execute(
            Boolean.class,
            Map.of("owner", "alice", "principal", "bob", "roles", List.of("reader"))));
  }

  @Test
  void rejectsAnUndeclaredVariable() {
    assertThrows(ScriptCreateException.class, () -> compiler.compile("missing == 'value'"));
  }
}
```

Keep test inputs stable during each evaluation, just as production callers must.

## Contract-test matrix

Cover the dimensions the application actually supports:

| Area | Useful cases |
| --- | --- |
| compilation | valid source, parse error, check error, undeclared identifier, wrong overload |
| result handling | expected Java type, CEL error, unknown, absence, CEL null, conversion failure |
| control flow | short-circuiting, comprehensions, empty and non-empty aggregates |
| host values | boundary numbers, Unicode strings, bytes, timestamps, durations, null policy |
| reuse | repeated evaluations with different activations, concurrent evaluation with stable inputs |
| extensions | enabled/disabled language surface, invalid inputs, every custom overload |
| resource policy | maximum accepted source/input sizes, regex dialect, timeout/isolation behavior |

Parse/check failures, CEL errors, unknowns, absence, Java null, and unexpected Java exceptions are
separate outcomes. Assert the intended one rather than catching a broad exception everywhere. See
[errors, unknowns, and partial state](../concepts/errors-unknowns-and-partial-state.md).

## Protobuf

Test both generated and dynamic messages if the application accepts both. Include:

- descriptor registration and an intentionally missing registration;
- scalar, repeated, map, enum, presence, wrapper, timestamp, duration, and `Any` fields in use;
- construction or conversion errors used by the application;
- the chosen generated-code artifact and Protobuf runtime in an isolated classpath.

`cel-generated-pb` and `cel-generated-pb3` must not be tested together on one classpath. Run the
same behavioral contract separately for each supported deployment combination. See
[using Protobuf values](protobuf.md).

## Jackson

Test the application's actual mapper configuration, including naming strategies, mix-ins,
visibility, and property-modifying modules. Exercise:

- object and enum registration before checking;
- direct and mutually recursive schemas when used;
- collections, maps, arrays, optionals, and null/presence behavior;
- unsupported array or map-key shapes and incomplete generic container information;
- registry-copy isolation and later caller mapper changes;
- Jackson 2 and Jackson 3 in separate dependency configurations if both are supported.

See [using Jackson-described Java objects](jackson.md).

## General and exact registries

If the application opts into an exact aggregate registry, run the same semantic suite against both
general and exact modes. Add exact-contract rejection tests for incompatible representations,
null/key rules, empty aggregate optionals, duplicate CEL-equivalent map keys, and detectable cycles
as applicable. Concurrent mutation is an unsupported caller contract breach, not a condition that
CEL-Java promises to detect; test that the application prevents mutation while evaluation runs.

Do not make an internal evaluation-plan shape the sole correctness assertion. Planner choices can
change while CEL behavior remains correct. Behavioral parity is the primary contract; targeted
planner tests belong alongside planner implementation when needed.

## Custom libraries

Test the declaration and implementation together through checked source. Cover every overload,
invalid values, CEL error returns, unknown propagation, thread safety, and stated side-effect or
resource behavior. A test that calls only the Java operation misses checker/dispatcher mismatches.

## Native-image and packaged applications

If the application ships a native image, executable JAR, shaded JAR, or framework package, run the
same representative conditions in that artifact. A JVM unit test does not verify reflection
metadata, resource inclusion, dependency relocation, or framework build-time analysis. See
[native-image integration](native-image.md).

## Keep performance tests separate

Use behavior tests for correctness and JMH or application-level load tests for performance.
Microbenchmarks require controlled forks, warmup, measurement, and environment interpretation; they
should not turn the ordinary correctness suite into a long or noisy timing gate.
