# Using CEL for authorization decisions

CEL can express authorization policy, but CEL-Java is an expression engine rather than an
authorization sandbox. The application owns the policy source, declarations, input model,
available functions, resource controls, and final allow-or-deny decision.

CEL-Java does not impose an overall CPU, memory, allocation, result-size, or latency budget.
Expression cost estimates are descriptive; they are not an execution limit. Parser source and
recursion limits do not bound evaluation over large or deeply nested runtime inputs.

## A fail-closed shape

Compile trusted policy during configuration and retain the reusable script. Treat compilation
failure as a configuration or startup failure rather than silently installing an allow policy:

```java
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.RegexEngine;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptCompiler;
import org.projectnessie.cel.tools.ScriptException;

public final class AuthorizationPolicy {
  private final Script decision;

  public AuthorizationPolicy(String trustedPolicySource) throws ScriptException {
    ScriptCompiler compiler =
        ScriptCompiler.newBuilder()
            .regexEngine(RegexEngine.RE2)
            .withDeclarations(
                Decls.newVar("principal", Decls.String),
                Decls.newVar("resourceOwner", Decls.String),
                Decls.newVar("roles", Decls.newListType(Decls.String)))
            .build();
    this.decision = compiler.compile(trustedPolicySource);
  }

  public boolean grants(String principal, String resourceOwner, java.util.List<String> roles) {
    try {
      Boolean result =
          decision.execute(
              Boolean.class,
              Map.of(
                  "principal", principal,
                  "resourceOwner", resourceOwner,
                  "roles", List.copyOf(roles)));
      return Boolean.TRUE.equals(result);
    } catch (ScriptException | RuntimeException failure) {
      // Record the failure without logging sensitive input values.
      return false;
    }
  }
}
```

The surrounding application must ensure that `trustedPolicySource` really comes from the intended
policy authority. If policy authors are less trusted than the host, constrain which declarations,
macros, extension libraries, and custom functions they may use.

The example selects RE2/J for the standard `matches` function. RE2 avoids the catastrophic
backtracking possible in some Java regular expressions, but changes the accepted regex dialect and
does not limit the rest of evaluation. Java regular expressions remain CEL-Java's compatibility
default.

## Define the resource boundary outside CEL

Before evaluating untrusted expressions or untrusted data, establish host controls appropriate to
the consequence of the decision:

- Control or allowlist policy source and cap source length before compilation.
- Restrict declarations, macros, bundled extensions, and custom functions to the policy model.
- Bound input graph depth and the sizes of collections, maps, strings, byte sequences, and messages.
- Prefer RE2 when the policy's regex dialect permits it; otherwise validate or constrain patterns.
- Keep custom functions bounded and side-effect free. A function that blocks, performs I/O, or
  allocates without limits expands the authorization trust boundary.
- Use CEL-Java controlled operations for cooperative elapsed/CPU/allocation and AST limits. Combine
  them with process, request, or workload isolation appropriate to the consequence of the
  decision. Neither a cooperative limit nor a Java executor timeout can preempt arbitrary host
  callback code.
- Require a Boolean result and deny on compilation failure, CEL error, incompatible unknown result,
  conversion failure, timeout, or unexpected Java exception.
- Keep activation maps and every reachable value stable for the complete evaluation.
- Monitor failures and latency without exposing policy inputs or security-sensitive values.

The appropriate isolation mechanism depends on the application. High-consequence multi-tenant
evaluation may require a stronger boundary than an in-process deadline.

## Failure modes are not interchangeable

- Parse and type-check diagnostics become `ScriptCreateException`.
- A CEL error result becomes `ScriptExecutionException` at the `Script` API.
- An unknown can be returned as a CEL `Val`, but requesting `Boolean` reports an incompatible
  unknown as `ScriptExecutionException`.
- Cancellation, interruption, and resource-limit exhaustion are unchecked
  `OperationAbortedException` control outcomes, not CEL errors.
- An absent binding is not Java null. Use an activation API that preserves that distinction when it
  matters.
- Java exceptions can come from adapters, conversions, custom functions, or host code.

A fail-closed decision path handles all of these deliberately. For partial-state policy use cases,
read [errors, unknowns, and partial state](../concepts/errors-unknowns-and-partial-state.md) rather
than treating unknown as false by accident.

## Reuse and data ownership

Compile a policy once and evaluate the resulting `Script` repeatedly. A script may be called
concurrently when its configured registries, adapters, libraries, and functions are safe for the
concurrent access they receive. Configuration is completed before the compiler or script is shared.

Copy or freeze mutable authorization inputs at the application boundary. CEL-Java does not mutate a
map passed to `Script.execute`, but external mutation of that map or of reachable objects during
evaluation is outside the contract and can yield inconsistent decisions.

## Authorization review checklist

- Who can create or update the policy source?
- Which variables, types, macros, extensions, and functions are visible?
- What bounds apply to source and runtime inputs?
- What prevents pathological regex behavior?
- What deadline and isolation boundary covers evaluation and custom functions?
- Are all non-Boolean and failure outcomes denied?
- Are policies compiled before serving traffic and scripts reused safely?
- Do tests cover allow, deny, absence, null, errors, unknowns, and hostile-size inputs?

See [testing CEL integrations](testing-cel-integrations.md) for integration-test guidance.
