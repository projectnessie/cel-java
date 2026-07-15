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
package org.projectnessie.cel.quarkus.smoke.standalone;

import static java.util.Map.of;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptHost;

@Path("/cel/native-smoke")
public class SmokeResource {
  private static final String EXPRESSION =
      "resource.name.startsWith(\"projects/_/buckets/example/objects/reports/\")"
          + " && request.time < timestamp(\"2026-08-01T00:00:00Z\")";

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public SmokeResponse smoke() throws Exception {
    Script script =
        ScriptHost.newBuilder()
            .build()
            .buildScript(EXPRESSION)
            .withDeclarations(
                Decls.newVar("resource.name", Decls.String),
                Decls.newVar("request.time", Decls.Timestamp))
            .build();

    Boolean allowed =
        script.execute(
            Boolean.class,
            of(
                "resource.name",
                "projects/_/buckets/example/objects/reports/q1.csv",
                "request.time",
                Instant.parse("2026-07-31T23:59:59Z")));

    return new SmokeResponse("cel-standalone", allowed);
  }

  public record SmokeResponse(String engine, boolean allowed) {}
}
