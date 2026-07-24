/*
 * Copyright (C) 2026 The Authors of CEL-Java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projectnessie.cel.quarkus.smoke.corepb3jackson3;

import static java.util.Arrays.asList;
import static org.projectnessie.cel.Env.newCustomEnv;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.Library.StdLib;
import static org.projectnessie.cel.ProgramOption.evalOptions;

import dev.cel.expr.conformance.proto3.NestedTestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.projectnessie.cel.Ast;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.ActivationFunction;
import org.projectnessie.cel.types.jackson3.Jackson3Registry;

@Path("/cel/native-smoke")
public class SmokeResource {
  private static final String SCALAR_EXPRESSION = "x + 1";
  private static final String JACKSON_SCALAR_EXPRESSION =
      "input.name == \"reports\" && request.time < timestamp(\"2026-08-01T00:00:00Z\")";
  private static final String JACKSON_LIST_EXPRESSION = "size(input.numbers)";
  private static final String PROTO_EXPRESSION =
      "proto.repeated_int64[index] == 2 && proto.map_string_int64[map_key] == 42";

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public SmokeResponse smoke() {
    return new SmokeResponse(
        "cel-core+cel-generated-pb3+cel-jackson3",
        evaluateScalar(),
        evaluateJacksonScalar(),
        evaluateJacksonList(),
        evaluateProto());
  }

  private Comparison evaluateScalar() {
    Env env = newEnv(declarations(Decls.newVar("x", Decls.Int)));
    Programs programs = compile(env, SCALAR_EXPRESSION);
    CountingActivation enabledInput = new CountingActivation("x", 41L);
    CountingActivation establishedInput = new CountingActivation("x", 41L);

    Val enabled = evaluate(programs.enabled(), enabledInput);
    Val established = evaluate(programs.established(), establishedInput);
    return comparison(enabled, established, enabledInput, establishedInput, 0, 0);
  }

  private Comparison evaluateJacksonScalar() {
    Env env =
        newCustomEnv(
            Jackson3Registry.newRegistry(),
            asList(
                StdLib(),
                types(Input.class),
                declarations(
                    Decls.newVar("input", Decls.newObjectType(Input.class.getName())),
                    Decls.newVar("request.time", Decls.Timestamp))));
    Programs programs = compile(env, JACKSON_SCALAR_EXPRESSION);
    Input enabledValue = Input.named("reports");
    Input establishedValue = Input.named("reports");
    CountingActivation enabledInput =
        new CountingActivation(
            Map.of("input", enabledValue, "request.time", Instant.parse("2026-07-31T23:59:59Z")));
    CountingActivation establishedInput =
        new CountingActivation(
            Map.of(
                "input", establishedValue, "request.time", Instant.parse("2026-07-31T23:59:59Z")));

    Val enabled = evaluate(programs.enabled(), enabledInput);
    Val established = evaluate(programs.established(), establishedInput);
    return comparison(
        enabled,
        established,
        enabledInput,
        establishedInput,
        enabledValue.fieldReadCount(),
        establishedValue.fieldReadCount());
  }

  private Comparison evaluateJacksonList() {
    Env env =
        newCustomEnv(
            Jackson3Registry.newExactAggregateRegistry(),
            asList(
                StdLib(),
                types(Input.class),
                declarations(Decls.newVar("input", Decls.newObjectType(Input.class.getName())))));
    Programs programs = compile(env, JACKSON_LIST_EXPRESSION);
    Input enabledValue = Input.numbered(List.of(1L, 2L, 3L));
    Input establishedValue = Input.numbered(List.of(1L, 2L, 3L));
    CountingActivation enabledInput = new CountingActivation("input", enabledValue);
    CountingActivation establishedInput = new CountingActivation("input", establishedValue);

    Val enabled = evaluate(programs.enabled(), enabledInput);
    Val established = evaluate(programs.established(), establishedInput);
    return comparison(
        enabled,
        established,
        enabledInput,
        establishedInput,
        enabledValue.fieldReadCount(),
        establishedValue.fieldReadCount());
  }

  private Comparison evaluateProto() {
    Env env =
        newCustomEnv(
            ProtoTypeRegistry.newExactAggregateRegistry(TestAllTypes.getDefaultInstance()),
            asList(
                StdLib(),
                types(TestAllTypes.getDefaultInstance()),
                declarations(
                    Decls.newVar(
                        "proto", Decls.newObjectType(TestAllTypes.getDescriptor().getFullName())),
                    Decls.newVar("index", Decls.Int),
                    Decls.newVar("map_key", Decls.String))));
    Programs programs = compile(env, PROTO_EXPRESSION);
    TestAllTypes enabledValue =
        TestAllTypes.newBuilder()
            .addAllRepeatedInt64(List.of(1L, 2L, 3L))
            .putMapStringInt64("answer", 42L)
            .build();
    TestAllTypes establishedValue =
        TestAllTypes.newBuilder()
            .addAllRepeatedInt64(List.of(1L, 2L, 3L))
            .putMapStringInt64("answer", 42L)
            .build();
    CountingActivation enabledInput =
        new CountingActivation(Map.of("proto", enabledValue, "index", 1L, "map_key", "answer"));
    CountingActivation establishedInput =
        new CountingActivation(Map.of("proto", establishedValue, "index", 1L, "map_key", "answer"));

    Val enabled = evaluate(programs.enabled(), enabledInput);
    Val established = evaluate(programs.established(), establishedInput);
    return comparison(enabled, established, enabledInput, establishedInput, 0, 0);
  }

  private static Programs compile(Env env, String expression) {
    AstIssuesTuple parsed = env.parse(expression);
    if (parsed.hasIssues()) {
      throw new IllegalStateException("parse failed: " + parsed.getIssues());
    }

    Ast ast = parsed.getAst();
    AstIssuesTuple checked = env.check(ast);
    if (checked.hasIssues()) {
      throw new IllegalStateException("check failed: " + checked.getIssues());
    }

    return new Programs(
        env.program(checked.getAst()),
        env.program(checked.getAst(), evalOptions(OptDisableNativeEval)));
  }

  private static Val evaluate(Program program, CountingActivation input) {
    Val value = program.eval(input).getVal();
    if (Err.isError(value)) {
      throw new IllegalStateException("evaluation failed: " + value);
    }
    return value;
  }

  private static Comparison comparison(
      Val enabled,
      Val established,
      CountingActivation enabledInput,
      CountingActivation establishedInput,
      int enabledFieldReads,
      int establishedFieldReads) {
    if (!enabled.type().equals(established.type())
        || !Objects.equals(enabled.value(), established.value())) {
      throw new IllegalStateException(
          "enabled and established results differ: " + enabled + " != " + established);
    }
    return new Comparison(
        new Evaluation(
            enabled.value(),
            enabled.type().typeName(),
            enabledInput.lookupCount(),
            enabledFieldReads),
        new Evaluation(
            established.value(),
            established.type().typeName(),
            establishedInput.lookupCount(),
            establishedFieldReads));
  }

  public record SmokeResponse(
      String engine,
      Comparison scalar,
      Comparison jacksonScalar,
      Comparison jacksonList,
      Comparison protobuf) {}

  public record Comparison(Evaluation enabled, Evaluation established) {}

  public record Evaluation(Object value, String type, int activationLookups, int fieldReads) {}

  private record Programs(Program enabled, Program established) {}

  private static final class CountingActivation implements ActivationFunction {
    private final Map<String, Object> values;
    private int lookupCount;

    private CountingActivation(String name, Object value) {
      this(Map.of(name, value));
    }

    private CountingActivation(Map<String, Object> values) {
      this.values = values;
    }

    @Override
    public Object resolve(String name) {
      lookupCount++;
      return values.getOrDefault(name, ABSENT);
    }

    int lookupCount() {
      return lookupCount;
    }
  }

  @SuppressWarnings("unused")
  @RegisterForReflection(targets = {TestAllTypes.class, NestedTestAllTypes.class})
  static final class ProtobufReflection {}

  @RegisterForReflection
  public static final class Input {
    private final String name;
    private final List<Long> numbers;
    private int fieldReadCount;

    private Input(String name, List<Long> numbers) {
      this.name = name;
      this.numbers = numbers;
    }

    static Input named(String name) {
      return new Input(name, null);
    }

    static Input numbered(List<Long> numbers) {
      return new Input(null, numbers);
    }

    public String getName() {
      fieldReadCount++;
      return name;
    }

    public List<Long> getNumbers() {
      fieldReadCount++;
      return numbers;
    }

    int fieldReadCount() {
      return fieldReadCount;
    }
  }
}
