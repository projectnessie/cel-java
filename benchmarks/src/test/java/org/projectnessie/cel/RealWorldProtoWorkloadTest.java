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

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.projectnessie.cel.RealWorldProgramSet.ProgramModes;
import org.projectnessie.cel.benchmark.PreparedFixture;
import org.projectnessie.cel.benchmark.RealWorldProtoFixtures;
import org.projectnessie.cel.benchmark.RealWorldWorkloads;
import org.projectnessie.cel.benchmark.RealWorldWorkloads.Family;
import org.projectnessie.cel.benchmark.RealWorldWorkloads.Scenario;
import org.projectnessie.cel.benchmark.proto.Booking;
import org.projectnessie.cel.benchmark.proto.CloudEvent;
import org.projectnessie.cel.benchmark.proto.Device;
import org.projectnessie.cel.benchmark.proto.EventData;
import org.projectnessie.cel.benchmark.proto.GatewayAddress;
import org.projectnessie.cel.benchmark.proto.KubernetesResource;
import org.projectnessie.cel.benchmark.proto.OrganizationResource;
import org.projectnessie.cel.benchmark.proto.PolicyResource;
import org.projectnessie.cel.benchmark.proto.TraceInterval;

class RealWorldProtoWorkloadTest {
  @TestFactory
  Stream<DynamicTest> retainedProtobufScenarios() {
    return implementedScenarios()
        .map(
            scenario ->
                DynamicTest.dynamicTest(
                    scenario.id(),
                    () -> {
                      PreparedFixture fixture = RealWorldProtoFixtures.prepare(scenario);
                      RealWorldProgramSet programs =
                          RealWorldProgramSet.protobuf(
                              scenario,
                              fixture,
                              RealWorldProtoFixtures.registeredTypes(scenario.family()));
                      assertThat(programs.exactNative()).isEqualTo(scenario.expected());
                      assertThat(programs.exactDisabled()).isEqualTo(scenario.expected());
                      assertThat(programs.general()).isEqualTo(scenario.expected());
                      assertThat(programs.direct()).isEqualTo(scenario.expected());
                    }));
  }

  @Test
  void missingDraDriverIsCelErrorInEveryMode() {
    Scenario scenario = RealWorldWorkloads.scenario(DRA_MATCH);
    ProgramModes modes =
        RealWorldProgramSet.protobufPrograms(
            scenario, RealWorldProtoFixtures.registeredTypes(Family.DRA));
    Map<String, Object> activation = Map.of("device", Device.getDefaultInstance());
    for (Program program : List.of(modes.exactNative(), modes.exactDisabled(), modes.general())) {
      assertThat(isError(program.eval(activation).getVal())).isTrue();
    }
  }

  @Test
  void fixtureDimensionsRemainDistinct() {
    PolicyResource typeMiss =
        (PolicyResource)
            RealWorldProtoFixtures.prepare(RealWorldWorkloads.scenario(IAM_PREFIX_TYPE_MISS))
                .activation()
                .get("resource");
    assertThat(typeMiss.getName()).startsWith("projects/_/buckets/exampleco-site-assets/");

    PolicyResource nameMiss =
        (PolicyResource)
            RealWorldProtoFixtures.prepare(RealWorldWorkloads.scenario(IAM_PREFIX_NAME_MISS))
                .activation()
                .get("resource");
    assertThat(nameMiss.getType()).isEqualTo("storage.googleapis.com/Object");

    Device colorMiss = protoDevice(DRA_COLOR_MISS);
    assertThat(colorMiss.getAttributesOrThrow("resource-driver.example.com").getSize())
        .isEqualTo("large");
    Device sizeMiss = protoDevice(DRA_SIZE_MISS);
    assertThat(sizeMiss.getAttributesOrThrow("resource-driver.example.com").getColor())
        .isEqualTo("black");
    assertThat(protoDevice(DRA_UNRELATED_32).getAttributesCount()).isEqualTo(33);

    assertOrganizationShapes();
    assertGatewayShapes();
    assertFluxShapes();

    Booking booking = (Booking) protoRoot(PROTOVALIDATE_BOOKING_MISSING, "this");
    assertThat(booking.hasCloudProviderRegionId()).isFalse();
    TraceInterval interval = (TraceInterval) protoRoot(PROTOVALIDATE_INTERVAL_REVERSED, "this");
    assertThat(interval.getStartTime().getNanos() - interval.getEndTime().getNanos()).isEqualTo(1);
  }

  @Test
  void invalidTypedDaprAmountIsCelErrorInEveryMode() {
    Scenario scenario = RealWorldWorkloads.scenario(DAPR_TYPED_DEPOSIT);
    ProgramModes modes =
        RealWorldProgramSet.protobufPrograms(
            scenario, RealWorldProtoFixtures.registeredTypes(Family.DAPR_TYPED));
    CloudEvent event =
        CloudEvent.newBuilder()
            .setType("deposit")
            .setData(EventData.newBuilder().setAmount("not-a-number").build())
            .build();
    Map<String, Object> activation = Map.of("event", event);
    for (Program program : List.of(modes.exactNative(), modes.exactDisabled(), modes.general())) {
      assertThat(isError(program.eval(activation).getVal())).isTrue();
    }
  }

  static Stream<Scenario> implementedScenarios() {
    return RealWorldWorkloads.pairedScenarios().stream();
  }

  private static Device protoDevice(String scenarioId) {
    return (Device) protoRoot(scenarioId, "device");
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
                  (OrganizationResource) protoRoot(scenarioId, "resource");
              assertThat(resource.getBindingsCount()).as(scenarioId).isEqualTo(1);
              assertThat(resource.getBindings(0).getMembersCount())
                  .as(scenarioId)
                  .isEqualTo(members);
            });
    OrganizationResource failFirst =
        (OrganizationResource) protoRoot(ORG_MEMBERS_FAIL_FIRST_8, "resource");
    assertThat(failFirst.getBindings(0).getMembers(0).getType())
        .isEqualTo("iam.googleapis.com/User");
    assertThat(failFirst.getBindings(0).getMembers(7).getType())
        .isEqualTo("iam.googleapis.com/ServiceAccount");
    OrganizationResource failLast =
        (OrganizationResource) protoRoot(ORG_MEMBERS_FAIL_LAST_8, "resource");
    assertThat(failLast.getBindings(0).getMembers(0).getType())
        .isEqualTo("iam.googleapis.com/ServiceAccount");
    assertThat(failLast.getBindings(0).getMembers(7).getType())
        .isEqualTo("iam.googleapis.com/User");

    for (String scenarioId : List.of(ORG_BINDINGS_ALL_8_X_8, ORG_BINDINGS_FAIL_LAST_8_X_8)) {
      OrganizationResource resource = (OrganizationResource) protoRoot(scenarioId, "resource");
      assertThat(resource.getBindingsCount()).as(scenarioId).isEqualTo(8);
      assertThat(resource.getBindingsList())
          .as(scenarioId)
          .allSatisfy(binding -> assertThat(binding.getMembersCount()).isEqualTo(8));
    }
    OrganizationResource failLastBinding =
        (OrganizationResource) protoRoot(ORG_BINDINGS_FAIL_LAST_8_X_8, "resource");
    assertThat(failLastBinding.getBindings(7).getMembers(7).getType())
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
            (scenarioId, size) ->
                assertThat(protoGateway(scenarioId)).as(scenarioId).hasSize(size));
    assertThat(protoGateway(GATEWAY_DUPLICATE_FIRST_16).get(1).getValue())
        .isEqualTo(protoGateway(GATEWAY_DUPLICATE_FIRST_16).get(0).getValue());
    assertThat(protoGateway(GATEWAY_DUPLICATE_LAST_16).get(15).getValue())
        .isEqualTo(protoGateway(GATEWAY_DUPLICATE_LAST_16).get(14).getValue());
    assertThat(protoGateway(GATEWAY_HOSTNAME_16))
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
                assertThat(protoFlux(scenarioId).getStatus().getConditionsCount())
                    .as(scenarioId)
                    .isEqualTo(size));
    assertThat(protoFlux(FLUX_NO_READY_8).getStatus().getConditionsList())
        .allSatisfy(condition -> assertThat(condition.getType()).isEqualTo("Healthy"));
    assertThat(protoFlux(FLUX_READY_ALL_32).getStatus().getConditionsList())
        .allSatisfy(condition -> assertThat(condition.getStatus()).isEqualTo("True"));
    assertThat(protoFlux(FLUX_FAIL_FIRST_32).getStatus().getConditions(0).getStatus())
        .isEqualTo("False");
    assertThat(protoFlux(FLUX_FAIL_FIRST_32).getStatus().getConditions(31).getStatus())
        .isEqualTo("True");
    assertThat(protoFlux(FLUX_FAIL_LAST_32).getStatus().getConditions(0).getStatus())
        .isEqualTo("True");
    assertThat(protoFlux(FLUX_FAIL_LAST_32).getStatus().getConditions(31).getStatus())
        .isEqualTo("False");
  }

  @SuppressWarnings("unchecked")
  private static List<GatewayAddress> protoGateway(String scenarioId) {
    return (List<GatewayAddress>) protoRoot(scenarioId, "self");
  }

  private static KubernetesResource protoFlux(String scenarioId) {
    return (KubernetesResource) protoRoot(scenarioId, "resource");
  }

  private static Object protoRoot(String scenarioId, String variable) {
    return RealWorldProtoFixtures.prepare(RealWorldWorkloads.scenario(scenarioId))
        .activation()
        .get(variable);
  }
}
