# Resource controls and cancellation

CEL-Java can apply cooperative controls to one parse, check, program-construction, or evaluation
operation. Existing `compile(...)`, `program(...)`, `eval(...)`, and `execute(...)` methods remain
unrestricted fast paths. Choose a `...Cancelable(...)` factory when an operation needs cancellation
or finite limits.

These controls reduce the risk from expensive expressions and large inputs. They are not process
isolation, a Java security boundary, or a guarantee that arbitrary host code stops immediately.

## Available controls

Build an immutable `ResourceLimits` value with any combination of:

| Limit | Measurement |
| --- | --- |
| elapsed time | monotonic time from `System.nanoTime()` |
| CPU time | CPU consumed by the operation's executing thread |
| allocated bytes | cumulative bytes allocated by the executing thread, not live or retained heap |
| AST nodes | expression nodes after macro expansion |
| AST depth | expression-tree depth after macro expansion; the root has depth one |
| AST metadata entries | source, macro, checked-reference, and checked-type metadata |

An omitted limit is unlimited. A zero limit is valid. The elapsed-time clock and thread counters
start when the handle's execution method starts; creating a handle and waiting in an executor queue
do not consume its budget.

Elapsed time includes time for which the executing thread is descheduled or blocked in a
synchronous custom function, resolver, adapter, provider, or regex operation. CPU and allocation
limits account only for the executing thread. Work delegated by a custom function to another thread
is outside those two counters, though its synchronous wait still counts toward elapsed time.

CPU and allocation limits use JVM management counters. If a requested counter is unsupported,
cannot be enabled, is denied, or returns an invalid reading, the operation fails closed with
`MEASUREMENT_UNAVAILABLE`. CEL-Java may enable a supported JVM counter and does not disable it
afterward.

## Controlled script compilation and execution

The tools API preserves its checked exceptions while exposing specialized one-shot handles:

```java
import java.time.Duration;
import java.util.Map;
import org.projectnessie.cel.OperationAbortedException;
import org.projectnessie.cel.ResourceLimits;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptCompiler;

var limits =
    ResourceLimits.newBuilder()
        .elapsedTimeLimit(Duration.ofMillis(50))
        .cpuTimeLimit(Duration.ofMillis(20))
        .allocatedBytesLimit(4 * 1024 * 1024)
        .astNodeLimit(2_000)
        .astDepthLimit(100)
        .astMetadataEntryLimit(10_000)
        .build();

ScriptCompiler compiler = ScriptCompiler.newBuilder().build();
Script script = compiler.compileCancelable("[1, 2, 3].exists(x, x == 2)", limits).compile();

try {
  boolean result = script.executeCancelable(Boolean.class, Map.of(), limits).execute();
} catch (OperationAbortedException aborted) {
  // Apply the host's fail-closed policy using reason, phase, and resource metadata.
}
```

`ScriptCompiler.compileCancelable(...)` uses one elapsed/CPU/allocation baseline across parsing,
checking, optimization, and planning. `Script.executeCancelable(...)` keeps one baseline across
evaluation and native result conversion. Parse/check failures remain `ScriptCreateException`; CEL
errors and unsupported result conversions remain `ScriptExecutionException`.
`OperationAbortedException` is deliberately neither of those: it describes a host-control outcome,
not a CEL value.

## Low-level controlled operations

The low-level API provides:

- `Env.parseCancelable(...)`;
- `Env.checkCancelable(...)`;
- `Env.compileCancelable(...)`;
- `Env.programCancelable(...)`; and
- `Program.evalCancelable(...)`.

`Env` factories return `CancelableOperation<T>`. `Program` returns `CancelableEval`, whose
`eval()` method is also available as `execute()`. Factories without a `ResourceLimits` argument
create cancellation-only handles.

Each handle is one-shot. Its execution method runs synchronously on the calling thread, and a
second or concurrent execution attempt throws `IllegalStateException`. Reusable objects remain the
`Env`, `Program`, `ScriptCompiler`, and `Script`, not their per-operation handles.

## Cancellation and interruption

Another thread may call a handle's `cancel()` method. It is thread-safe and idempotent, sets a
cooperative cancellation flag, and does not call `Thread.interrupt()`. Cancellation is reported as
an unchecked `OperationAbortedException` at the next checkpoint.

CEL-Java also treats an already-interrupted or subsequently interrupted executing thread as a
controlled abort. It observes but does not clear the interrupt flag. Applications using an executor
may combine `handle.cancel()` with `Future.cancel(true)` only when their custom callbacks and
executor lifecycle are designed for interruption.

Calling `cancel()` cannot preempt arbitrary Java code. For example, if a custom function is blocked
inside an uninterruptible operation, CEL-Java regains control only when that function returns or
throws.

### Executor cancellation

The handle is separate from the executor task so another thread can request cooperative
cancellation. The elapsed-time budget starts inside `eval()`, not while the task waits in the
queue:

```java
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.OperationAbortedException;
import org.projectnessie.cel.ResourceLimits;

var env = Env.newEnv();
var checked = env.compile("[1, 2, 3].exists(x, x == 2)");
if (checked.hasIssues()) {
  throw new IllegalArgumentException(checked.getIssues().toString());
}
var program = env.program(checked.getAst());
var limits =
    ResourceLimits.newBuilder().elapsedTimeLimit(Duration.ofMillis(100)).build();
var evaluation = program.evalCancelable(Map.of(), limits);
var executor = Executors.newSingleThreadExecutor();
var canceller = Executors.newSingleThreadScheduledExecutor();

var future = executor.submit(evaluation::eval);
var cancellation = canceller.schedule(evaluation::cancel, 75, TimeUnit.MILLISECONDS);
try {
  var value = future.get();
  // Inspect value.getVal(): an ordinary CEL error remains a Val result.
} catch (ExecutionException failure) {
  if (failure.getCause() instanceof OperationAbortedException aborted) {
    // Distinguish cancellation or a resource limit using reason and phase.
  } else {
    throw failure;
  }
} finally {
  cancellation.cancel(false);
  executor.shutdownNow();
  canceller.shutdownNow();
}
```

`Future.cancel(true)` additionally interrupts the executor thread. It can reduce latency when every
host callback on that path has a compatible interruption contract, but the CEL handle’s `cancel()`
does not interrupt the thread itself.

## Cooperative precision

Cheap checkpoints run at parser and AST-construction boundaries, checker and planner nodes,
aggregate construction, comprehensions, native and established collection traversal, equality and
membership loops, activation resolution, regex operations, optimization, and result conversion.
Expensive management counters and the monotonic clock are sampled periodically in hot loops.

Consequently:

- a budget can overshoot between samples;
- cancellation latency depends on reaching the next checkpoint;
- synchronous host work is charged but not forcibly preempted;
- thread CPU and allocation do not include work moved to other threads; and
- there is no result-size, general heap-retention, per-comprehension-iteration, or deterministic
  abstract “step” limit.

Use structural and measured controls together with source admission, bounded inputs, a restricted
function set, and deployment isolation appropriate to the trust boundary. See
[Compatibility and limitations](../reference/compatibility-and-limitations.md) and
[Authorization expressions](../guides/authorization.md).
