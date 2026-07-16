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
import static java.util.Map.of;
import static org.projectnessie.cel.Env.newCustomEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.Library.StdLib;

import com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes;
import com.google.protobuf.Int32Value;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import org.projectnessie.cel.Ast;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.Program.EvalResult;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.types.jackson3.Jackson3Registry;

@Path("/cel/native-smoke")
public class SmokeResource {
  private static final String JACKSON_EXPRESSION =
      "input.name == \"reports\" && request.time < timestamp(\"2026-08-01T00:00:00Z\")";

  private static final String PROTO_EXPRESSION = "proto.single_int32_wrapper == 123";

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public SmokeResponse smoke() {
    return new SmokeResponse(
        "cel-core+cel-generated-pb3+cel-jackson3", evaluateJackson(), evaluateProto());
  }

  private boolean evaluateJackson() {
    Env env =
        newCustomEnv(
            Jackson3Registry.newRegistry(),
            asList(
                StdLib(),
                types(Input.class),
                declarations(
                    Decls.newVar("input", Decls.newObjectType(Input.class.getName())),
                    Decls.newVar("request.time", Decls.Timestamp))));

    Program program = compile(env, JACKSON_EXPRESSION);
    EvalResult result =
        program.eval(
            of(
                "input",
                new Input("reports"),
                "request.time",
                Instant.parse("2026-07-31T23:59:59Z")));
    return booleanResult(result);
  }

  private boolean evaluateProto() {
    Env env =
        newCustomEnv(
            ProtoTypeRegistry.newRegistry(),
            asList(
                StdLib(),
                types(TestAllTypes.getDefaultInstance()),
                declarations(
                    Decls.newVar(
                        "proto",
                        Decls.newObjectType(TestAllTypes.getDescriptor().getFullName())))));

    Program program = compile(env, PROTO_EXPRESSION);
    EvalResult result =
        program.eval(
            of(
                "proto",
                TestAllTypes.newBuilder().setSingleInt32Wrapper(Int32Value.of(123)).build()));
    return booleanResult(result);
  }

  private static Program compile(Env env, String expression) {
    AstIssuesTuple parsed = env.parse(expression);
    if (parsed.hasIssues()) {
      throw new IllegalStateException("parse failed: " + parsed.getIssues());
    }

    Ast ast = parsed.getAst();
    AstIssuesTuple checked = env.check(ast);
    if (checked.hasIssues()) {
      throw new IllegalStateException("check failed: " + checked.getIssues());
    }

    return env.program(checked.getAst());
  }

  private static boolean booleanResult(EvalResult result) {
    Val value = result.getVal();
    if (Err.isError(value)) {
      throw new IllegalStateException("evaluation failed: " + value);
    }
    return value.booleanValue();
  }

  public record SmokeResponse(String engine, boolean jackson, boolean protobuf) {}

  @RegisterForReflection(targets = TestAllTypes.class)
  static final class ProtobufReflection {}

  @RegisterForReflection
  public static final class Input {
    private final String name;

    public Input(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }
}
