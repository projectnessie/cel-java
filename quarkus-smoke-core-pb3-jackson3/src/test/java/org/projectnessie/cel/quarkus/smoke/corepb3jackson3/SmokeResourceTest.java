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
        .body("scalar.enabled.value", equalTo(42))
        .body("scalar.established.value", equalTo(42))
        .body("scalar.enabled.type", equalTo("int"))
        .body("scalar.established.type", equalTo("int"))
        .body("scalar.enabled.activationLookups", equalTo(1))
        .body("scalar.established.activationLookups", equalTo(1))
        .body("scalar.enabled.fieldReads", equalTo(0))
        .body("scalar.established.fieldReads", equalTo(0))
        .body("jacksonScalar.enabled.value", equalTo(true))
        .body("jacksonScalar.established.value", equalTo(true))
        .body("jacksonScalar.enabled.type", equalTo("bool"))
        .body("jacksonScalar.established.type", equalTo("bool"))
        .body("jacksonScalar.enabled.activationLookups", equalTo(2))
        .body("jacksonScalar.established.activationLookups", equalTo(2))
        .body("jacksonScalar.enabled.fieldReads", equalTo(1))
        .body("jacksonScalar.established.fieldReads", equalTo(1))
        .body("jacksonList.enabled.value", equalTo(3))
        .body("jacksonList.established.value", equalTo(3))
        .body("jacksonList.enabled.type", equalTo("int"))
        .body("jacksonList.established.type", equalTo("int"))
        .body("jacksonList.enabled.activationLookups", equalTo(1))
        .body("jacksonList.established.activationLookups", equalTo(1))
        .body("jacksonList.enabled.fieldReads", equalTo(1))
        .body("jacksonList.established.fieldReads", equalTo(1))
        .body("protobuf.enabled.value", equalTo(true))
        .body("protobuf.established.value", equalTo(true))
        .body("protobuf.enabled.type", equalTo("bool"))
        .body("protobuf.established.type", equalTo("bool"))
        .body("protobuf.enabled.activationLookups", equalTo(10))
        .body("protobuf.established.activationLookups", equalTo(10))
        .body("protobuf.enabled.fieldReads", equalTo(0))
        .body("protobuf.established.fieldReads", equalTo(0));
  }
}
