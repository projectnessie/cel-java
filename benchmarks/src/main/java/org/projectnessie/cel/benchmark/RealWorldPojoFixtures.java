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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable JavaBean-style types used by the Jackson 3 workload representation. */
public final class RealWorldPojoFixtures {
  private static final String OBJECT_TYPE = "storage.googleapis.com/Object";
  private static final String NAME_PREFIX = "projects/_/buckets/exampleco-site-assets/";
  private static final String DRIVER = "resource-driver.example.com";
  private static final String SERVICE_ACCOUNT = "iam.googleapis.com/ServiceAccount";
  private static final String USER = "iam.googleapis.com/User";
  private static final Instant INTERVAL_INSTANT = Instant.parse("2026-01-01T00:00:00.123456789Z");

  private RealWorldPojoFixtures() {}

  public static PreparedFixture prepare(RealWorldWorkloads.Scenario scenario) {
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
          throw new IllegalArgumentException("Unsupported Jackson scenario " + scenario.id());
    };
  }

  public static Class<?>[] registeredTypes(RealWorldWorkloads.Family family) {
    return switch (family) {
      case IAM -> new Class<?>[] {PolicyResource.class};
      case ORGANIZATION -> new Class<?>[] {OrganizationResource.class, Binding.class, Member.class};
      case GATEWAY -> new Class<?>[] {GatewayAddress.class};
      case DRA -> new Class<?>[] {Device.class, DeviceAttributes.class};
      case FLUX -> new Class<?>[] {KubernetesResource.class, ResourceStatus.class, Condition.class};
      case DAPR_TYPED -> new Class<?>[] {CloudEvent.class, EventData.class};
      case PROTOVALIDATE_BOOKING -> new Class<?>[] {Booking.class};
      case PROTOVALIDATE_INTERVAL -> new Class<?>[] {TraceInterval.class};
      case PROTOVALIDATE_NOTIFICATION -> new Class<?>[] {Notification.class};
      default -> throw new IllegalArgumentException("Unsupported Jackson family " + family);
    };
  }

  private static PreparedFixture iam(RealWorldWorkloads.Scenario scenario) {
    PolicyResource resource =
        switch (scenario.id()) {
          case "iam.prefix.hit" -> new PolicyResource(OBJECT_TYPE, NAME_PREFIX + "asset.js");
          case "iam.prefix.typeMiss" ->
              new PolicyResource("storage.googleapis.com/Bucket", NAME_PREFIX + "asset.js");
          case "iam.prefix.nameMiss" ->
              new PolicyResource(OBJECT_TYPE, "projects/_/buckets/unrelated/asset.js");
          default -> throw new IllegalArgumentException(scenario.id());
        };
    return new PreparedFixture(
        Map.of("resource", resource),
        () -> resource.getType().equals(OBJECT_TYPE) && resource.getName().startsWith(NAME_PREFIX));
  }

  private static PreparedFixture dra(RealWorldWorkloads.Scenario scenario) {
    Map<String, DeviceAttributes> attributes = new LinkedHashMap<>();
    if (scenario.id().equals("dra.unrelated32")) {
      for (int i = 0; i < 32; i++) {
        attributes.put("unrelated.example.com/" + i, new DeviceAttributes("blue", "small"));
      }
    }
    DeviceAttributes target =
        switch (scenario.id()) {
          case "dra.match", "dra.unrelated32" -> new DeviceAttributes("black", "large");
          case "dra.colorMiss" -> new DeviceAttributes("white", "large");
          case "dra.sizeMiss" -> new DeviceAttributes("black", "small");
          default -> throw new IllegalArgumentException(scenario.id());
        };
    attributes.put(DRIVER, target);
    Device device = new Device(attributes);
    return new PreparedFixture(
        Map.of("device", device),
        () ->
            device.getAttributes().get(DRIVER).getColor().equals("black")
                && device.getAttributes().get(DRIVER).getSize().equals("large"));
  }

  private static PreparedFixture organization(RealWorldWorkloads.Scenario scenario) {
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
    List<Binding> bindingValues = new ArrayList<>(bindings);
    for (int bindingIndex = 0; bindingIndex < bindings; bindingIndex++) {
      List<Member> memberValues = new ArrayList<>(members);
      for (int memberIndex = 0; memberIndex < members; memberIndex++) {
        String type =
            bindingIndex == failingBinding && memberIndex == failingMember ? USER : SERVICE_ACCOUNT;
        memberValues.add(new Member(type, "principal-" + memberIndex));
      }
      bindingValues.add(new Binding("roles/viewer", memberValues));
    }
    OrganizationResource resource = new OrganizationResource(bindingValues);
    return new PreparedFixture(Map.of("resource", resource), () -> organizationDirect(resource));
  }

  private static boolean organizationDirect(OrganizationResource resource) {
    for (Binding binding : resource.getBindings()) {
      for (Member member : binding.getMembers()) {
        if (!member.getType().equals(SERVICE_ACCOUNT)) {
          return false;
        }
      }
    }
    return true;
  }

  private static PreparedFixture gateway(RealWorldWorkloads.Scenario scenario) {
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
      addresses.add(new GatewayAddress(type, value));
    }
    List<GatewayAddress> value = List.copyOf(addresses);
    return new PreparedFixture(Map.of("self", value), () -> gatewayDirect(value));
  }

  private static boolean gatewayDirect(List<GatewayAddress> addresses) {
    for (GatewayAddress first : addresses) {
      if (first.getType().equals("IPAddress") && first.getValue() != null) {
        int matches = 0;
        for (GatewayAddress second : addresses) {
          if (second.getType().equals(first.getType())
              && second.getValue() != null
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

  private static PreparedFixture flux(RealWorldWorkloads.Scenario scenario) {
    int size =
        switch (scenario.id()) {
          case "flux.noConditions" -> 0;
          case "flux.readyOne" -> 1;
          case "flux.noReady8" -> 8;
          default -> 32;
        };
    List<Condition> conditions = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      String type = scenario.id().equals("flux.noReady8") ? "Healthy" : "Ready";
      String status = "True";
      if ((scenario.id().equals("flux.failFirst32") && i == 0)
          || (scenario.id().equals("flux.failLast32") && i == 31)) {
        status = "False";
      }
      conditions.add(new Condition(type, status));
    }
    KubernetesResource resource = new KubernetesResource(new ResourceStatus(conditions));
    return new PreparedFixture(Map.of("resource", resource), () -> fluxDirect(resource));
  }

  private static boolean fluxDirect(KubernetesResource resource) {
    List<Condition> ready = new ArrayList<>(resource.getStatus().getConditions().size());
    for (Condition condition : resource.getStatus().getConditions()) {
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

  private static PreparedFixture dapr(RealWorldWorkloads.Scenario scenario) {
    CloudEvent event =
        switch (scenario.id()) {
          case "dapr.typed.important" -> new CloudEvent("other", new EventData(true, ""));
          case "dapr.typed.absent" -> new CloudEvent("other", new EventData(null, ""));
          case "dapr.typed.deposit" -> new CloudEvent("deposit", new EventData(null, "10001"));
          default -> throw new IllegalArgumentException(scenario.id());
        };
    return new PreparedFixture(
        Map.of("event", event),
        scenario.id().equals("dapr.typed.deposit")
            ? () ->
                event.getType().equals("deposit")
                    && Long.parseLong(event.getData().getAmount()) > 10_000L
            : () -> event.getData().getImportant() != null && event.getData().getImportant());
  }

  private static PreparedFixture booking(RealWorldWorkloads.Scenario scenario) {
    Booking booking =
        switch (scenario.id()) {
          case "protovalidate.booking.hybrid" -> new Booking("hybrid", null);
          case "protovalidate.booking.region" -> new Booking("aws", "us-east-1");
          case "protovalidate.booking.missing" -> new Booking("aws", null);
          default -> throw new IllegalArgumentException(scenario.id());
        };
    return new PreparedFixture(
        Map.of("this", booking),
        () ->
            booking.getCloudProviderId().equals("hybrid")
                || booking.getCloudProviderRegionId() != null);
  }

  private static PreparedFixture interval(RealWorldWorkloads.Scenario scenario) {
    TraceInterval interval =
        switch (scenario.id()) {
          case "protovalidate.interval.absent" -> new TraceInterval(null, null);
          case "protovalidate.interval.ordered" ->
              new TraceInterval(INTERVAL_INSTANT, INTERVAL_INSTANT);
          case "protovalidate.interval.reversed" ->
              new TraceInterval(INTERVAL_INSTANT, INTERVAL_INSTANT.minusNanos(1L));
          default -> throw new IllegalArgumentException(scenario.id());
        };
    return new PreparedFixture(Map.of("this", interval), () -> intervalDirect(interval));
  }

  private static boolean intervalDirect(TraceInterval interval) {
    if (interval.getStartTime() == null) {
      return true;
    }
    if (interval.getEndTime() == null) {
      return true;
    }
    return interval.getEndTime().compareTo(interval.getStartTime()) >= 0;
  }

  private static PreparedFixture notification(RealWorldWorkloads.Scenario scenario) {
    Notification notification =
        switch (scenario.id()) {
          case "protovalidate.notification.webhook" ->
              new Notification("https://example.com/hook", null);
          case "protovalidate.notification.email" -> new Notification(null, "alerts@example.com");
          case "protovalidate.notification.none" -> new Notification(null, null);
          default -> throw new IllegalArgumentException(scenario.id());
        };
    return new PreparedFixture(
        Map.of("this", notification),
        () -> notification.getWebhook() != null || notification.getEmail() != null);
  }

  public static final class PolicyResource {
    private final String type;
    private final String name;

    public PolicyResource(String type, String name) {
      this.type = type;
      this.name = name;
    }

    public String getType() {
      return type;
    }

    public String getName() {
      return name;
    }
  }

  public static final class Member {
    private final String type;
    private final String value;

    public Member(String type, String value) {
      this.type = type;
      this.value = value;
    }

    public String getType() {
      return type;
    }

    public String getValue() {
      return value;
    }
  }

  public static final class Binding {
    private final String role;
    private final List<Member> members;

    public Binding(String role, List<Member> members) {
      this.role = role;
      this.members = List.copyOf(members);
    }

    public String getRole() {
      return role;
    }

    public List<Member> getMembers() {
      return members;
    }
  }

  public static final class OrganizationResource {
    private final List<Binding> bindings;

    public OrganizationResource(List<Binding> bindings) {
      this.bindings = List.copyOf(bindings);
    }

    public List<Binding> getBindings() {
      return bindings;
    }
  }

  public static final class GatewayAddress {
    private final String type;
    private final String value;

    public GatewayAddress(String type, String value) {
      this.type = type;
      this.value = value;
    }

    public String getType() {
      return type;
    }

    public String getValue() {
      return value;
    }
  }

  public static final class DeviceAttributes {
    private final String color;
    private final String size;

    public DeviceAttributes(String color, String size) {
      this.color = color;
      this.size = size;
    }

    public String getColor() {
      return color;
    }

    public String getSize() {
      return size;
    }
  }

  public static final class Device {
    private final Map<String, DeviceAttributes> attributes;

    public Device(Map<String, DeviceAttributes> attributes) {
      this.attributes = immutableLinkedMap(attributes);
    }

    public Map<String, DeviceAttributes> getAttributes() {
      return attributes;
    }
  }

  public static final class Condition {
    private final String type;
    private final String status;

    public Condition(String type, String status) {
      this.type = type;
      this.status = status;
    }

    public String getType() {
      return type;
    }

    public String getStatus() {
      return status;
    }
  }

  public static final class ResourceStatus {
    private final List<Condition> conditions;

    public ResourceStatus(List<Condition> conditions) {
      this.conditions = List.copyOf(conditions);
    }

    public List<Condition> getConditions() {
      return conditions;
    }
  }

  public static final class KubernetesResource {
    private final ResourceStatus status;

    public KubernetesResource(ResourceStatus status) {
      this.status = status;
    }

    public ResourceStatus getStatus() {
      return status;
    }
  }

  public static final class EventData {
    private final Boolean important;
    private final String amount;

    public EventData(Boolean important, String amount) {
      this.important = important;
      this.amount = amount;
    }

    public Boolean getImportant() {
      return important;
    }

    public String getAmount() {
      return amount;
    }
  }

  public static final class CloudEvent {
    private final String type;
    private final EventData data;

    public CloudEvent(String type, EventData data) {
      this.type = type;
      this.data = data;
    }

    public String getType() {
      return type;
    }

    public EventData getData() {
      return data;
    }
  }

  public static final class Booking {
    private final String cloudProviderId;
    private final String cloudProviderRegionId;

    public Booking(String cloudProviderId, String cloudProviderRegionId) {
      this.cloudProviderId = cloudProviderId;
      this.cloudProviderRegionId = cloudProviderRegionId;
    }

    public String getCloudProviderId() {
      return cloudProviderId;
    }

    public String getCloudProviderRegionId() {
      return cloudProviderRegionId;
    }
  }

  public static final class TraceInterval {
    private final Instant startTime;
    private final Instant endTime;

    public TraceInterval(Instant startTime, Instant endTime) {
      this.startTime = startTime;
      this.endTime = endTime;
    }

    public Instant getStartTime() {
      return startTime;
    }

    public Instant getEndTime() {
      return endTime;
    }
  }

  public static final class Notification {
    private final String webhook;
    private final String email;

    public Notification(String webhook, String email) {
      this.webhook = webhook;
      this.email = email;
    }

    public String getWebhook() {
      return webhook;
    }

    public String getEmail() {
      return email;
    }
  }

  private static <K, V> Map<K, V> immutableLinkedMap(Map<K, V> source) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }
}
