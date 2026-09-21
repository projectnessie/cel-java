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
package org.projectnessie.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_TYPED_DEPOSIT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DRA_COLOR_MISS;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DRA_MATCH;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DRA_SIZE_MISS;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DRA_UNRELATED_32;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.FLUX_FAIL_FIRST_32;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.FLUX_FAIL_LAST_32;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.FLUX_NO_CONDITIONS;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.FLUX_NO_READY_8;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.FLUX_READY_ALL_32;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.FLUX_READY_ONE;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.GATEWAY_DUPLICATE_FIRST_16;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.GATEWAY_DUPLICATE_LAST_16;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.GATEWAY_EMPTY;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.GATEWAY_HOSTNAME_16;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.GATEWAY_ONE;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.GATEWAY_UNIQUE_16;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.GATEWAY_UNIQUE_8;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.IAM_PREFIX_NAME_MISS;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.IAM_PREFIX_TYPE_MISS;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_BINDINGS_ALL_8_X_8;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_BINDINGS_FAIL_LAST_8_X_8;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_MEMBERS_ALL_32;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_MEMBERS_EMPTY;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_MEMBERS_FAIL_FIRST_8;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_MEMBERS_FAIL_LAST_8;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_MEMBERS_ONE;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_BOOKING_MISSING;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_INTERVAL_REVERSED;
import static org.projectnessie.cel.common.types.Err.isError;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.projectnessie.cel.RealWorldProgramSet.ProgramModes;
import org.projectnessie.cel.benchmark.PreparedFixture;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.CloudEvent;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.Device;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.EventData;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.GatewayAddress;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.KubernetesResource;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.OrganizationResource;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.PolicyResource;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.TraceInterval;
import org.projectnessie.cel.benchmark.RealWorldProtoFixtures;
import org.projectnessie.cel.benchmark.RealWorldWorkloads;
import org.projectnessie.cel.benchmark.RealWorldWorkloads.Family;
import org.projectnessie.cel.benchmark.RealWorldWorkloads.Scenario;

class RealWorldJackson3WorkloadTest {
  @TestFactory
  Stream<DynamicTest> retainedJacksonScenarios() {
    return RealWorldProtoWorkloadTest.implementedScenarios()
        .map(
            scenario ->
                DynamicTest.dynamicTest(
                    scenario.id(),
                    () -> {
                      PreparedFixture fixture = RealWorldPojoFixtures.prepare(scenario);
                      RealWorldProgramSet programs =
                          RealWorldProgramSet.jackson3(
                              scenario,
                              fixture,
                              RealWorldPojoFixtures.registeredTypes(scenario.family()));
                      assertThat(programs.exactNative()).isEqualTo(scenario.expected());
                      assertThat(programs.exactDisabled()).isEqualTo(scenario.expected());
                      assertThat(programs.general()).isEqualTo(scenario.expected());
                      assertThat(programs.direct()).isEqualTo(scenario.expected());
                    }));
  }

  @TestFactory
  Stream<DynamicTest> protobufAndJacksonAgree() {
    return RealWorldProtoWorkloadTest.implementedScenarios()
        .map(
            scenario ->
                DynamicTest.dynamicTest(
                    scenario.id(),
                    () -> {
                      RealWorldProgramSet protobuf =
                          RealWorldProgramSet.protobuf(
                              scenario,
                              RealWorldProtoFixtures.prepare(scenario),
                              RealWorldProtoFixtures.registeredTypes(scenario.family()));
                      RealWorldProgramSet jackson =
                          RealWorldProgramSet.jackson3(
                              scenario,
                              RealWorldPojoFixtures.prepare(scenario),
                              RealWorldPojoFixtures.registeredTypes(scenario.family()));
                      assertThat(jackson.exactNative()).isEqualTo(protobuf.exactNative());
                      assertThat(jackson.exactDisabled()).isEqualTo(protobuf.exactDisabled());
                      assertThat(jackson.general()).isEqualTo(protobuf.general());
                    }));
  }

  @Test
  void missingDraDriverIsCelErrorInEveryMode() {
    Scenario scenario = RealWorldWorkloads.scenario(DRA_MATCH);
    ProgramModes modes =
        RealWorldProgramSet.jackson3Programs(
            scenario, RealWorldPojoFixtures.registeredTypes(Family.DRA));
    Device device = new Device(new LinkedHashMap<>());
    Map<String, Object> activation = Map.of("device", device);
    for (Program program : List.of(modes.exactNative(), modes.exactDisabled(), modes.general())) {
      assertThat(isError(program.eval(activation).getVal())).isTrue();
    }
  }

  @Test
  void fixtureDimensionsRemainDistinct() {
    PolicyResource typeMiss =
        (PolicyResource)
            RealWorldPojoFixtures.prepare(RealWorldWorkloads.scenario(IAM_PREFIX_TYPE_MISS))
                .activation()
                .get("resource");
    assertThat(typeMiss.getName()).startsWith("projects/_/buckets/exampleco-site-assets/");

    PolicyResource nameMiss =
        (PolicyResource)
            RealWorldPojoFixtures.prepare(RealWorldWorkloads.scenario(IAM_PREFIX_NAME_MISS))
                .activation()
                .get("resource");
    assertThat(nameMiss.getType()).isEqualTo("storage.googleapis.com/Object");

    Device colorMiss = pojoDevice(DRA_COLOR_MISS);
    assertThat(colorMiss.getAttributes().get("resource-driver.example.com").getSize())
        .isEqualTo("large");
    Device sizeMiss = pojoDevice(DRA_SIZE_MISS);
    assertThat(sizeMiss.getAttributes().get("resource-driver.example.com").getColor())
        .isEqualTo("black");
    assertThat(pojoDevice(DRA_UNRELATED_32).getAttributes()).hasSize(33);

    assertOrganizationShapes();
    assertGatewayShapes();
    assertFluxShapes();

    assertThat(
            ((RealWorldPojoFixtures.Booking) pojoRoot(PROTOVALIDATE_BOOKING_MISSING, "this"))
                .getCloudProviderRegionId())
        .isNull();
    TraceInterval interval = (TraceInterval) pojoRoot(PROTOVALIDATE_INTERVAL_REVERSED, "this");
    assertThat(interval.getStartTime().getNano() - interval.getEndTime().getNano()).isEqualTo(1);
  }

  private static Device pojoDevice(String scenarioId) {
    return (Device) pojoRoot(scenarioId, "device");
  }

  private static void assertOrganizationShapes() {
    Map.of(
            ORG_MEMBERS_EMPTY, 0,
            ORG_MEMBERS_ONE, 1,
            ORG_MEMBERS_FAIL_FIRST_8, 8,
            ORG_MEMBERS_FAIL_LAST_8, 8,
            ORG_MEMBERS_ALL_32, 32)
        .forEach(
            (scenarioId, members) -> {
              OrganizationResource resource =
                  (OrganizationResource) pojoRoot(scenarioId, "resource");
              assertThat(resource.getBindings()).as(scenarioId).hasSize(1);
              assertThat(resource.getBindings().get(0).getMembers())
                  .as(scenarioId)
                  .hasSize(members);
            });
    OrganizationResource failFirst =
        (OrganizationResource) pojoRoot(ORG_MEMBERS_FAIL_FIRST_8, "resource");
    assertThat(failFirst.getBindings().get(0).getMembers().get(0).getType())
        .isEqualTo("iam.googleapis.com/User");
    assertThat(failFirst.getBindings().get(0).getMembers().get(7).getType())
        .isEqualTo("iam.googleapis.com/ServiceAccount");
    OrganizationResource failLast =
        (OrganizationResource) pojoRoot(ORG_MEMBERS_FAIL_LAST_8, "resource");
    assertThat(failLast.getBindings().get(0).getMembers().get(0).getType())
        .isEqualTo("iam.googleapis.com/ServiceAccount");
    assertThat(failLast.getBindings().get(0).getMembers().get(7).getType())
        .isEqualTo("iam.googleapis.com/User");

    for (String scenarioId : List.of(ORG_BINDINGS_ALL_8_X_8, ORG_BINDINGS_FAIL_LAST_8_X_8)) {
      OrganizationResource resource = (OrganizationResource) pojoRoot(scenarioId, "resource");
      assertThat(resource.getBindings()).as(scenarioId).hasSize(8);
      assertThat(resource.getBindings())
          .as(scenarioId)
          .allSatisfy(binding -> assertThat(binding.getMembers()).hasSize(8));
    }
    OrganizationResource failLastBinding =
        (OrganizationResource) pojoRoot(ORG_BINDINGS_FAIL_LAST_8_X_8, "resource");
    assertThat(failLastBinding.getBindings().get(7).getMembers().get(7).getType())
        .isEqualTo("iam.googleapis.com/User");
  }

  private static void assertGatewayShapes() {
    Map.of(
            GATEWAY_EMPTY, 0,
            GATEWAY_ONE, 1,
            GATEWAY_UNIQUE_8, 8,
            GATEWAY_UNIQUE_16, 16,
            GATEWAY_DUPLICATE_FIRST_16, 16,
            GATEWAY_DUPLICATE_LAST_16, 16,
            GATEWAY_HOSTNAME_16, 16)
        .forEach(
            (scenarioId, size) -> assertThat(pojoGateway(scenarioId)).as(scenarioId).hasSize(size));
    assertThat(pojoGateway(GATEWAY_DUPLICATE_FIRST_16).get(1).getValue())
        .isEqualTo(pojoGateway(GATEWAY_DUPLICATE_FIRST_16).get(0).getValue());
    assertThat(pojoGateway(GATEWAY_DUPLICATE_LAST_16).get(15).getValue())
        .isEqualTo(pojoGateway(GATEWAY_DUPLICATE_LAST_16).get(14).getValue());
    assertThat(pojoGateway(GATEWAY_HOSTNAME_16))
        .allSatisfy(address -> assertThat(address.getType()).isEqualTo("Hostname"));
  }

  private static void assertFluxShapes() {
    Map.of(
            FLUX_NO_CONDITIONS, 0,
            FLUX_NO_READY_8, 8,
            FLUX_READY_ONE, 1,
            FLUX_READY_ALL_32, 32,
            FLUX_FAIL_FIRST_32, 32,
            FLUX_FAIL_LAST_32, 32)
        .forEach(
            (scenarioId, size) ->
                assertThat(pojoFlux(scenarioId).getStatus().getConditions())
                    .as(scenarioId)
                    .hasSize(size));
    assertThat(pojoFlux(FLUX_NO_READY_8).getStatus().getConditions())
        .allSatisfy(condition -> assertThat(condition.getType()).isEqualTo("Healthy"));
    assertThat(pojoFlux(FLUX_READY_ALL_32).getStatus().getConditions())
        .allSatisfy(condition -> assertThat(condition.getStatus()).isEqualTo("True"));
    assertThat(pojoFlux(FLUX_FAIL_FIRST_32).getStatus().getConditions().get(0).getStatus())
        .isEqualTo("False");
    assertThat(pojoFlux(FLUX_FAIL_FIRST_32).getStatus().getConditions().get(31).getStatus())
        .isEqualTo("True");
    assertThat(pojoFlux(FLUX_FAIL_LAST_32).getStatus().getConditions().get(0).getStatus())
        .isEqualTo("True");
    assertThat(pojoFlux(FLUX_FAIL_LAST_32).getStatus().getConditions().get(31).getStatus())
        .isEqualTo("False");
  }

  @SuppressWarnings("unchecked")
  private static List<GatewayAddress> pojoGateway(String scenarioId) {
    return (List<GatewayAddress>) pojoRoot(scenarioId, "self");
  }

  private static KubernetesResource pojoFlux(String scenarioId) {
    return (KubernetesResource) pojoRoot(scenarioId, "resource");
  }

  private static Object pojoRoot(String scenarioId, String variable) {
    return RealWorldPojoFixtures.prepare(RealWorldWorkloads.scenario(scenarioId))
        .activation()
        .get(variable);
  }

  @Test
  void invalidTypedDaprAmountIsCelErrorInEveryMode() {
    Scenario scenario = RealWorldWorkloads.scenario(DAPR_TYPED_DEPOSIT);
    ProgramModes modes =
        RealWorldProgramSet.jackson3Programs(
            scenario, RealWorldPojoFixtures.registeredTypes(Family.DAPR_TYPED));
    CloudEvent event = new CloudEvent("deposit", new EventData(null, "not-a-number"));
    Map<String, Object> activation = Map.of("event", event);
    for (Program program : List.of(modes.exactNative(), modes.exactDisabled(), modes.general())) {
      assertThat(isError(program.eval(activation).getVal())).isTrue();
    }
  }
}
