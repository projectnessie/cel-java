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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SmokeResourceTest {
  @Test
  void evaluatesCelExpressions() {
    given()
        .when()
        .get("/cel/native-smoke")
        .then()
        .statusCode(200)
        .body("engine", equalTo("cel-core+cel-generated-pb3+cel-jackson3"))
        .body("jackson", equalTo(true))
        .body("protobuf", equalTo(true));
  }
}
