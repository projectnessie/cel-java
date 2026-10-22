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
package org.projectnessie.cel.benchmark;

import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.benchmark.RealWorldWorkloads.Family;
import org.projectnessie.cel.benchmark.RealWorldWorkloads.Scenario;
import org.projectnessie.cel.benchmark.proto.Binding;
import org.projectnessie.cel.benchmark.proto.Booking;
import org.projectnessie.cel.benchmark.proto.CloudEvent;
import org.projectnessie.cel.benchmark.proto.Condition;
import org.projectnessie.cel.benchmark.proto.Device;
import org.projectnessie.cel.benchmark.proto.DeviceAttributes;
import org.projectnessie.cel.benchmark.proto.EventData;
import org.projectnessie.cel.benchmark.proto.GatewayAddress;
import org.projectnessie.cel.benchmark.proto.KubernetesResource;
import org.projectnessie.cel.benchmark.proto.Member;
import org.projectnessie.cel.benchmark.proto.Notification;
import org.projectnessie.cel.benchmark.proto.OrganizationResource;
import org.projectnessie.cel.benchmark.proto.PolicyResource;
import org.projectnessie.cel.benchmark.proto.ResourceStatus;
import org.projectnessie.cel.benchmark.proto.TraceInterval;

/** Builds generated-pb3 fixtures and direct controls for paired workloads. */
public final class RealWorldProtoFixtures {
  private static final String OBJECT_TYPE = "storage.googleapis.com/Object";
  private static final String NAME_PREFIX = "projects/_/buckets/exampleco-site-assets/";
  private static final String DRIVER = "resource-driver.example.com";
  private static final String SERVICE_ACCOUNT = "iam.googleapis.com/ServiceAccount";
  private static final String USER = "iam.googleapis.com/User";
  private static final Instant INTERVAL_INSTANT = Instant.parse("2026-01-01T00:00:00.123456789Z");

  private RealWorldProtoFixtures() {}

  public static PreparedFixture prepare(Scenario scenario) {
    return switch (scenario.family()) {
      case IAM -> iam(scenario);
      case ORGANIZATION -> organization(scenario);
      case GATEWAY -> gateway(scenario);
      case DRA -> dra(scenario);
      case FLUX -> flux(scenario);
      case DAPR_TYPED -> dapr(scenario);
      case PROTOVALIDATE_BOOKING -> booking(scenario);
      case PROTOVALIDATE_INTERVAL -> interval(scenario);
      case PROTOVALIDATE_NOTIFICATION -> notification(scenario);
      default ->
          throw new IllegalArgumentException("Unsupported protobuf scenario " + scenario.id());
    };
  }

  public static Message[] registeredTypes(Family family) {
    return switch (family) {
      case IAM -> new Message[] {PolicyResource.getDefaultInstance()};
      case ORGANIZATION ->
          new Message[] {
            OrganizationResource.getDefaultInstance(),
            Binding.getDefaultInstance(),
            Member.getDefaultInstance()
          };
      case GATEWAY -> new Message[] {GatewayAddress.getDefaultInstance()};
      case DRA ->
          new Message[] {Device.getDefaultInstance(), DeviceAttributes.getDefaultInstance()};
      case FLUX ->
          new Message[] {
            KubernetesResource.getDefaultInstance(),
            ResourceStatus.getDefaultInstance(),
            Condition.getDefaultInstance()
          };
      case DAPR_TYPED ->
          new Message[] {CloudEvent.getDefaultInstance(), EventData.getDefaultInstance()};
      case PROTOVALIDATE_BOOKING -> new Message[] {Booking.getDefaultInstance()};
      case PROTOVALIDATE_INTERVAL -> new Message[] {TraceInterval.getDefaultInstance()};
      case PROTOVALIDATE_NOTIFICATION -> new Message[] {Notification.getDefaultInstance()};
      default -> throw new IllegalArgumentException("Unsupported protobuf family " + family);
    };
  }

  private static PreparedFixture iam(Scenario scenario) {
    PolicyResource resource =
        switch (scenario.id()) {
          case "iam.prefix.hit" ->
              PolicyResource.newBuilder()
                  .setType(OBJECT_TYPE)
                  .setName(NAME_PREFIX + "asset.js")
                  .build();
          case "iam.prefix.typeMiss" ->
              PolicyResource.newBuilder()
                  .setType("storage.googleapis.com/Bucket")
                  .setName(NAME_PREFIX + "asset.js")
                  .build();
          case "iam.prefix.nameMiss" ->
              PolicyResource.newBuilder()
                  .setType(OBJECT_TYPE)
                  .setName("projects/_/buckets/unrelated/asset.js")
                  .build();
          default -> throw new IllegalArgumentException(scenario.id());
        };
    return new PreparedFixture(
        Map.of("resource", resource),
        () -> resource.getType().equals(OBJECT_TYPE) && resource.getName().startsWith(NAME_PREFIX));
  }

  private static PreparedFixture dra(Scenario scenario) {
    Device.Builder builder = Device.newBuilder();
    if (scenario.id().equals("dra.unrelated32")) {
      for (int i = 0; i < 32; i++) {
        builder.putAttributes(
            "unrelated.example.com/" + i,
            DeviceAttributes.newBuilder().setColor("blue").setSize("small").build());
      }
    }
    DeviceAttributes target =
        switch (scenario.id()) {
          case "dra.match", "dra.unrelated32" ->
              DeviceAttributes.newBuilder().setColor("black").setSize("large").build();
          case "dra.colorMiss" ->
              DeviceAttributes.newBuilder().setColor("white").setSize("large").build();
          case "dra.sizeMiss" ->
              DeviceAttributes.newBuilder().setColor("black").setSize("small").build();
          default -> throw new IllegalArgumentException(scenario.id());
        };
    Device device = builder.putAttributes(DRIVER, target).build();
    return new PreparedFixture(
        Map.of("device", device),
        () ->
            device.getAttributesMap().get(DRIVER).getColor().equals("black")
                && device.getAttributesMap().get(DRIVER).getSize().equals("large"));
  }

  private static PreparedFixture organization(Scenario scenario) {
    int bindings;
    int members;
    int failingBinding = -1;
    int failingMember = -1;
    switch (scenario.id()) {
      case "org.members.empty" -> {
        bindings = 1;
        members = 0;
      }
      case "org.members.one" -> {
        bindings = 1;
        members = 1;
      }
      case "org.members.failFirst8" -> {
        bindings = 1;
        members = 8;
        failingBinding = 0;
        failingMember = 0;
      }
      case "org.members.failLast8" -> {
        bindings = 1;
        members = 8;
        failingBinding = 0;
        failingMember = 7;
      }
      case "org.members.all32" -> {
        bindings = 1;
        members = 32;
      }
      case "org.bindings.all8x8" -> {
        bindings = 8;
        members = 8;
      }
      case "org.bindings.failLast8x8" -> {
        bindings = 8;
        members = 8;
        failingBinding = 7;
        failingMember = 7;
      }
      default -> throw new IllegalArgumentException(scenario.id());
    }
    OrganizationResource.Builder resource = OrganizationResource.newBuilder();
    for (int bindingIndex = 0; bindingIndex < bindings; bindingIndex++) {
      Binding.Builder binding = Binding.newBuilder().setRole("roles/viewer");
      for (int memberIndex = 0; memberIndex < members; memberIndex++) {
        String type =
            bindingIndex == failingBinding && memberIndex == failingMember ? USER : SERVICE_ACCOUNT;
        binding.addMembers(
            Member.newBuilder().setType(type).setValue("principal-" + memberIndex).build());
      }
      resource.addBindings(binding.build());
    }
    OrganizationResource value = resource.build();
    return new PreparedFixture(Map.of("resource", value), () -> organizationDirect(value));
  }

  private static boolean organizationDirect(OrganizationResource resource) {
    for (Binding binding : resource.getBindingsList()) {
      for (Member member : binding.getMembersList()) {
        if (!member.getType().equals(SERVICE_ACCOUNT)) {
          return false;
        }
      }
    }
    return true;
  }

  private static PreparedFixture gateway(Scenario scenario) {
    int size =
        switch (scenario.id()) {
          case "gateway.empty" -> 0;
          case "gateway.one" -> 1;
          case "gateway.unique8" -> 8;
          default -> 16;
        };
    List<GatewayAddress> addresses = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      String type = scenario.id().equals("gateway.hostname16") ? "Hostname" : "IPAddress";
      String value = type.equals("Hostname") ? "host-" + i + ".example.com" : "192.0.2." + i;
      if (scenario.id().equals("gateway.duplicateFirst16") && i == 1) {
        value = "192.0.2.0";
      } else if (scenario.id().equals("gateway.duplicateLast16") && i == 15) {
        value = "192.0.2.14";
      }
      addresses.add(GatewayAddress.newBuilder().setType(type).setValue(value).build());
    }
    List<GatewayAddress> value = List.copyOf(addresses);
    return new PreparedFixture(Map.of("self", value), () -> gatewayDirect(value));
  }

  private static boolean gatewayDirect(List<GatewayAddress> addresses) {
    for (GatewayAddress first : addresses) {
      if (first.getType().equals("IPAddress") && first.hasValue()) {
        int matches = 0;
        for (GatewayAddress second : addresses) {
          if (second.getType().equals(first.getType())
              && second.hasValue()
              && second.getValue().equals(first.getValue())) {
            matches++;
          }
        }
        if (matches != 1) {
          return false;
        }
      }
    }
    return true;
  }

  private static PreparedFixture flux(Scenario scenario) {
    int size =
        switch (scenario.id()) {
          case "flux.noConditions" -> 0;
          case "flux.readyOne" -> 1;
          case "flux.noReady8" -> 8;
          default -> 32;
        };
    ResourceStatus.Builder status = ResourceStatus.newBuilder();
    for (int i = 0; i < size; i++) {
      String type = scenario.id().equals("flux.noReady8") ? "Healthy" : "Ready";
      String conditionStatus = "True";
      if ((scenario.id().equals("flux.failFirst32") && i == 0)
          || (scenario.id().equals("flux.failLast32") && i == 31)) {
        conditionStatus = "False";
      }
      status.addConditions(Condition.newBuilder().setType(type).setStatus(conditionStatus).build());
    }
    KubernetesResource resource = KubernetesResource.newBuilder().setStatus(status.build()).build();
    return new PreparedFixture(Map.of("resource", resource), () -> fluxDirect(resource));
  }

  private static boolean fluxDirect(KubernetesResource resource) {
    List<Condition> ready = new ArrayList<>(resource.getStatus().getConditionsCount());
    for (Condition condition : resource.getStatus().getConditionsList()) {
      if (condition.getType().equals("Ready")) {
        ready.add(condition);
      }
    }
    for (Condition condition : ready) {
      if (!condition.getStatus().equals("True")) {
        return false;
      }
    }
    return true;
  }

  private static PreparedFixture dapr(Scenario scenario) {
    CloudEvent event =
        switch (scenario.id()) {
          case "dapr.typed.important" ->
              CloudEvent.newBuilder()
                  .setType("other")
                  .setData(EventData.newBuilder().setImportant(true).build())
                  .build();
          case "dapr.typed.absent" ->
              CloudEvent.newBuilder()
                  .setType("other")
                  .setData(EventData.getDefaultInstance())
                  .build();
          case "dapr.typed.deposit" ->
              CloudEvent.newBuilder()
                  .setType("deposit")
                  .setData(EventData.newBuilder().setAmount("10001").build())
                  .build();
          default -> throw new IllegalArgumentException(scenario.id());
        };
    return new PreparedFixture(
        Map.of("event", event),
        scenario.id().equals("dapr.typed.deposit")
            ? () ->
                event.getType().equals("deposit")
                    && Long.parseLong(event.getData().getAmount()) > 10_000L
            : () -> event.getData().hasImportant() && event.getData().getImportant());
  }

  private static PreparedFixture booking(Scenario scenario) {
    Booking booking =
        switch (scenario.id()) {
          case "protovalidate.booking.hybrid" ->
              Booking.newBuilder().setCloudProviderId("hybrid").build();
          case "protovalidate.booking.region" ->
              Booking.newBuilder()
                  .setCloudProviderId("aws")
                  .setCloudProviderRegionId("us-east-1")
                  .build();
          case "protovalidate.booking.missing" ->
              Booking.newBuilder().setCloudProviderId("aws").build();
          default -> throw new IllegalArgumentException(scenario.id());
        };
    return new PreparedFixture(
        Map.of("this", booking),
        () -> booking.getCloudProviderId().equals("hybrid") || booking.hasCloudProviderRegionId());
  }

  private static PreparedFixture interval(Scenario scenario) {
    TraceInterval interval =
        switch (scenario.id()) {
          case "protovalidate.interval.absent" -> TraceInterval.getDefaultInstance();
          case "protovalidate.interval.ordered" ->
              TraceInterval.newBuilder()
                  .setStartTime(timestamp(INTERVAL_INSTANT))
                  .setEndTime(timestamp(INTERVAL_INSTANT))
                  .build();
          case "protovalidate.interval.reversed" ->
              TraceInterval.newBuilder()
                  .setStartTime(timestamp(INTERVAL_INSTANT))
                  .setEndTime(timestamp(INTERVAL_INSTANT.minusNanos(1L)))
                  .build();
          default -> throw new IllegalArgumentException(scenario.id());
        };
    return new PreparedFixture(Map.of("this", interval), () -> intervalDirect(interval));
  }

  private static boolean intervalDirect(TraceInterval interval) {
    if (!interval.hasStartTime()) {
      return true;
    }
    if (!interval.hasEndTime()) {
      return true;
    }
    return compare(interval.getEndTime(), interval.getStartTime()) >= 0;
  }

  private static PreparedFixture notification(Scenario scenario) {
    Notification notification =
        switch (scenario.id()) {
          case "protovalidate.notification.webhook" ->
              Notification.newBuilder().setWebhook("https://example.com/hook").build();
          case "protovalidate.notification.email" ->
              Notification.newBuilder().setEmail("alerts@example.com").build();
          case "protovalidate.notification.none" -> Notification.getDefaultInstance();
          default -> throw new IllegalArgumentException(scenario.id());
        };
    return new PreparedFixture(
        Map.of("this", notification), () -> notification.hasWebhook() || notification.hasEmail());
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }

  private static int compare(Timestamp left, Timestamp right) {
    int seconds = Long.compare(left.getSeconds(), right.getSeconds());
    return seconds != 0 ? seconds : Integer.compare(left.getNanos(), right.getNanos());
  }
}
