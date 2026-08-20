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

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable manifest for the retained real-world workload scenarios. */
public final class RealWorldWorkloads {
  public enum Representation {
    HOST,
    PROTOBUF,
    JACKSON3
  }

  public enum Family {
    POLARIS,
    NESSIE,
    OPENFGA,
    DAPR_HOST,
    IAM,
    ORGANIZATION,
    GATEWAY,
    DRA,
    FLUX,
    DAPR_TYPED,
    PROTOVALIDATE_BOOKING,
    PROTOVALIDATE_INTERVAL,
    PROTOVALIDATE_NOTIFICATION
  }

  public record Scenario(
      String id,
      Family family,
      List<String> sourceExpressions,
      Map<Representation, List<String>> benchmarkExpressions,
      boolean expected,
      Class<?> resultClass,
      Set<Representation> representations,
      String adaptationNote,
      String provenanceUrl) {
    public Scenario {
      requireNonNull(id, "id");
      requireNonNull(family, "family");
      sourceExpressions = List.copyOf(sourceExpressions);
      EnumMap<Representation, List<String>> expressions = new EnumMap<>(Representation.class);
      benchmarkExpressions.forEach(
          (representation, sources) -> expressions.put(representation, List.copyOf(sources)));
      benchmarkExpressions = Map.copyOf(expressions);
      requireNonNull(resultClass, "resultClass");
      representations = Set.copyOf(representations);
      requireNonNull(adaptationNote, "adaptationNote");
      requireNonNull(provenanceUrl, "provenanceUrl");
      if (!benchmarkExpressions.keySet().containsAll(representations)) {
        throw new IllegalArgumentException("Missing benchmark expression for " + id);
      }
    }

    public List<String> expressions(Representation representation) {
      List<String> expressions = benchmarkExpressions.get(representation);
      if (expressions == null) {
        throw new IllegalArgumentException(id + " does not support " + representation);
      }
      return expressions;
    }
  }

  public static final String POLARIS_URL =
      "https://github.com/apache/polaris/blob/"
          + "8989f1f1a446f6e3d5da57a25892f5a89d040cd2/"
          + "persistence/nosql/persistence/maintenance/retain-cel/src/main/java/"
          + "org/apache/polaris/maintenance/cel/CelReferenceContinuePredicate.java";
  public static final String NESSIE_URL =
      "https://github.com/projectnessie/nessie/blob/"
          + "a8ab90154cf3ecc3da156e45c3a52014cfc1d148/"
          + "servers/quarkus-server/src/testFixtures/java/org/projectnessie/server/authz/"
          + "NessieAuthorizationTestProfile.java";
  public static final String OPENFGA_URL = "https://openfga.dev/docs/modeling/conditions";
  public static final String DAPR_URL =
      "https://docs.dapr.io/developing-applications/building-blocks/pubsub/"
          + "howto-route-messages/";
  public static final String IAM_URL = "https://docs.cloud.google.com/iam/docs/conditions-overview";
  public static final String ORGANIZATION_URL =
      "https://docs.cloud.google.com/iam/docs/org-policy-custom-constraints";
  public static final String GATEWAY_URL =
      "https://github.com/kubernetes-sigs/gateway-api/blob/"
          + "5e0983451ea056a1ecb2fb49910d4ccdf44ba141/config/crd/standard/"
          + "gateway.networking.k8s.io_gateways.yaml";
  public static final String DRA_URL =
      "https://kubernetes.io/docs/concepts/scheduling-eviction/" + "dynamic-resource-allocation/";
  public static final String FLUX_URL = "https://fluxcd.io/flux/cheatsheets/cel-healthchecks/";
  public static final String QDRANT_BOOKING_URL =
      "https://github.com/qdrant/qdrant-cloud-public-api/blob/"
          + "8645e6737ec43bdd32b49131b8579ad02821b36b/"
          + "proto/qdrant/cloud/booking/v1/booking.proto";
  public static final String REDPANDA_TRACING_URL =
      "https://github.com/redpanda-data/console/blob/"
          + "1018f5f71eb949cf8566734d32167cb2ea47a61f/"
          + "proto/redpanda/api/dataplane/v1alpha3/tracing.proto";
  public static final String FLYTE_NOTIFICATION_URL =
      "https://github.com/flyteorg/flyte/blob/"
          + "081e22c668f72d07cdc601c15170f3200c4105f9/"
          + "flyteidl2/notification/definition.proto";

  public static final String POLARIS_CUTOFF = "ageDays < 30 || commits <= 10";
  public static final String NESSIE_DEFAULT = "op == 'VIEW_REFERENCE' && ref.matches('.*')";
  public static final String NESSIE_ROLES = "'bar' in roles";
  public static final List<String> NESSIE_RULES =
      List.of(
          "op in ['VIEW_REFERENCE','CREATE_REFERENCE','DELETE_REFERENCE','VIEW_REFLOG',"
              + "'LIST_COMMITLOG','READ_ENTRIES','READ_CONTENT_KEY','LIST_COMMIT_LOG',"
              + "'COMMIT_CHANGE_AGAINST_REFERENCE','ASSIGN_REFERENCE_TO_HASH',"
              + "'CREATE_ENTITY','UPDATE_ENTITY','READ_ENTITY_VALUE','DELETE_ENTITY']"
              + " && role == 'admin_user'",
          "op == 'VIEW_REFERENCE' && role.startsWith('test_user') && ref.matches('.*')",
          "op in ['CREATE_REFERENCE','DELETE_REFERENCE','LIST_COMMIT_LOG','READ_ENTRIES',"
              + "'READ_CONTENT_KEY','ASSIGN_REFERENCE_TO_HASH']"
              + " && role.startsWith('test_user') && ref.startsWith('allowedBranch')",
          "op == 'COMMIT_CHANGE_AGAINST_REFERENCE'"
              + " && role.startsWith('test_user') && ref.startsWith('allowedBranch')",
          "op in ['VIEW_REFERENCE','CREATE_ENTITY','UPDATE_ENTITY']"
              + " && role == 'test_user2' && path.startsWith('allowed-')"
              + " && ref.startsWith('allowedBranch')",
          "op in ['VIEW_REFERENCE','DELETE_ENTITY_VALUE','CREATE_ENTITY','UPDATE_ENTITY']"
              + " && role == 'test_user3' && path.startsWith('allowed-')"
              + " && ref.startsWith('allowedBranch')",
          "op in ['VIEW_REFERENCE','READ_ENTITY_VALUE','DELETE_ENTITY']"
              + " && role == 'test_user4' && path.startsWith('allowed-')"
              + " && ref.startsWith('allowedBranch')",
          "op == 'COMMIT_CHANGE_AGAINST_REFERENCE'"
              + " && role == 'test_user2' && ref.startsWith('allowedBranch')",
          "op == 'CREATE_REFERENCE' && role == 'user1' && ref.matches('.*')",
          "op in ['VIEW_REFERENCE','ASSIGN_REFERENCE_TO_HASH',"
              + "'COMMIT_CHANGE_AGAINST_REFERENCE','DELETE_REFERENCE']"
              + " && role == 'user1'"
              + " && (ref.startsWith('allowedBranch') || ref == 'main')",
          "op in ['VIEW_REFERENCE','CREATE_REFERENCE']"
              + " && role == 'delete_branch_disallowed_user'"
              + " && ref in ['testDeleteBranchDisallowed','main']");
  public static final String OPENFGA_TIME = "current_time < grant_time + grant_duration";
  public static final String DAPR_TYPE = "event.type == 'widget'";
  public static final String DAPR_IMPORTANT =
      "has(event.data.important) && event.data.important == true";
  public static final String DAPR_DEPOSIT =
      "event.type == 'deposit' && int(event.data.amount) > 10000";
  public static final String IAM_PREFIX =
      "resource.type == 'storage.googleapis.com/Object'"
          + " && resource.name.startsWith("
          + "'projects/_/buckets/exampleco-site-assets/')";
  public static final String ORGANIZATION_SOURCE =
      "resource.bindings.all(binding, binding.members.all(member,"
          + " MemberTypeMatches(member, ['iam.googleapis.com/ServiceAccount'])))";
  public static final String ORGANIZATION_BENCHMARK =
      "resource.bindings.all(binding, binding.members.all(member,"
          + " member.type == 'iam.googleapis.com/ServiceAccount'))";
  public static final String GATEWAY_UNIQUENESS =
      "self.all(a1, a1.type == 'IPAddress' && has(a1.value)"
          + " ? self.exists_one(a2, a2.type == a1.type"
          + " && has(a2.value) && a2.value == a1.value) : true)";
  public static final String DRA_ATTRIBUTES =
      "device.attributes['resource-driver.example.com'].color == 'black'"
          + " && device.attributes['resource-driver.example.com'].size == 'large'";
  public static final String FLUX_SOURCE =
      "status.conditions.filter(e, e.type == 'Ready').all(e, e.status == 'True')";
  public static final String FLUX_BENCHMARK =
      "resource.status.conditions" + ".filter(e, e.type == 'Ready').all(e, e.status == 'True')";
  public static final String BOOKING_PROTO =
      "this.cloud_provider_id == 'hybrid' || has(this.cloud_provider_region_id)";
  public static final String BOOKING_JACKSON =
      "this.cloudProviderId == 'hybrid' || has(this.cloudProviderRegionId)";
  public static final String INTERVAL_PROTO =
      "!has(this.start_time) || !has(this.end_time) || this.end_time >= this.start_time";
  public static final String INTERVAL_JACKSON =
      "!has(this.startTime) || !has(this.endTime) || this.endTime >= this.startTime";
  public static final String NOTIFICATION = "has(this.webhook) || has(this.email)";

  public static final String POLARIS_CONSTANT_FALSE = "polaris.constant.false";
  public static final String POLARIS_CUTOFF_LEFT = "polaris.cutoff.left";
  public static final String POLARIS_CUTOFF_RIGHT = "polaris.cutoff.right";
  public static final String POLARIS_CUTOFF_STOP = "polaris.cutoff.stop";
  public static final String NESSIE_DEFAULT_ALLOW = "nessie.default.allow";
  public static final String NESSIE_DEFAULT_DENY = "nessie.default.deny";
  public static final String NESSIE_ROLES_HIT_LATE = "nessie.roles.hitLate";
  public static final String NESSIE_ROLES_MISS = "nessie.roles.miss";
  public static final String NESSIE_RULES_FIRST = "nessie.rules.first";
  public static final String NESSIE_RULES_MIDDLE = "nessie.rules.middle";
  public static final String NESSIE_RULES_LATE = "nessie.rules.late";
  public static final String NESSIE_RULES_DENY = "nessie.rules.deny";
  public static final String OPENFGA_BEFORE = "openfga.before";
  public static final String OPENFGA_EQUAL = "openfga.equal";
  public static final String OPENFGA_AFTER = "openfga.after";
  public static final String DAPR_TYPE_HIT = "dapr.type.hit";
  public static final String DAPR_TYPE_MISS = "dapr.type.miss";
  public static final String DAPR_IMPORTANT_HIT = "dapr.important.hit";
  public static final String DAPR_IMPORTANT_ABSENT = "dapr.important.absent";
  public static final String DAPR_DEPOSIT_HIT = "dapr.deposit.hit";
  public static final String DAPR_DEPOSIT_BOUNDARY = "dapr.deposit.boundary";
  public static final String IAM_PREFIX_HIT = "iam.prefix.hit";
  public static final String IAM_PREFIX_TYPE_MISS = "iam.prefix.typeMiss";
  public static final String IAM_PREFIX_NAME_MISS = "iam.prefix.nameMiss";
  public static final String DRA_MATCH = "dra.match";
  public static final String DRA_COLOR_MISS = "dra.colorMiss";
  public static final String DRA_SIZE_MISS = "dra.sizeMiss";
  public static final String DRA_UNRELATED_32 = "dra.unrelated32";
  public static final String ORG_MEMBERS_EMPTY = "org.members.empty";
  public static final String ORG_MEMBERS_ONE = "org.members.one";
  public static final String ORG_MEMBERS_FAIL_FIRST_8 = "org.members.failFirst8";
  public static final String ORG_MEMBERS_FAIL_LAST_8 = "org.members.failLast8";
  public static final String ORG_MEMBERS_ALL_32 = "org.members.all32";
  public static final String ORG_BINDINGS_ALL_8_X_8 = "org.bindings.all8x8";
  public static final String ORG_BINDINGS_FAIL_LAST_8_X_8 = "org.bindings.failLast8x8";
  public static final String GATEWAY_EMPTY = "gateway.empty";
  public static final String GATEWAY_ONE = "gateway.one";
  public static final String GATEWAY_UNIQUE_8 = "gateway.unique8";
  public static final String GATEWAY_UNIQUE_16 = "gateway.unique16";
  public static final String GATEWAY_DUPLICATE_FIRST_16 = "gateway.duplicateFirst16";
  public static final String GATEWAY_DUPLICATE_LAST_16 = "gateway.duplicateLast16";
  public static final String GATEWAY_HOSTNAME_16 = "gateway.hostname16";
  public static final String FLUX_NO_CONDITIONS = "flux.noConditions";
  public static final String FLUX_NO_READY_8 = "flux.noReady8";
  public static final String FLUX_READY_ONE = "flux.readyOne";
  public static final String FLUX_READY_ALL_32 = "flux.readyAll32";
  public static final String FLUX_FAIL_FIRST_32 = "flux.failFirst32";
  public static final String FLUX_FAIL_LAST_32 = "flux.failLast32";
  public static final String DAPR_TYPED_IMPORTANT = "dapr.typed.important";
  public static final String DAPR_TYPED_ABSENT = "dapr.typed.absent";
  public static final String DAPR_TYPED_DEPOSIT = "dapr.typed.deposit";
  public static final String PROTOVALIDATE_BOOKING_HYBRID = "protovalidate.booking.hybrid";
  public static final String PROTOVALIDATE_BOOKING_REGION = "protovalidate.booking.region";
  public static final String PROTOVALIDATE_BOOKING_MISSING = "protovalidate.booking.missing";
  public static final String PROTOVALIDATE_INTERVAL_ABSENT = "protovalidate.interval.absent";
  public static final String PROTOVALIDATE_INTERVAL_ORDERED = "protovalidate.interval.ordered";
  public static final String PROTOVALIDATE_INTERVAL_REVERSED = "protovalidate.interval.reversed";
  public static final String PROTOVALIDATE_NOTIFICATION_WEBHOOK =
      "protovalidate.notification.webhook";
  public static final String PROTOVALIDATE_NOTIFICATION_EMAIL = "protovalidate.notification.email";
  public static final String PROTOVALIDATE_NOTIFICATION_NONE = "protovalidate.notification.none";

  private static final List<Scenario> HOST_SCENARIOS = createHostScenarios();
  private static final List<Scenario> PAIRED_SCENARIOS = createPairedScenarios();
  private static final Map<String, Scenario> BY_ID = index();

  private RealWorldWorkloads() {}

  public static List<Scenario> hostScenarios() {
    return HOST_SCENARIOS;
  }

  public static List<Scenario> pairedScenarios() {
    return PAIRED_SCENARIOS;
  }

  public static Scenario scenario(String id) {
    Scenario scenario = BY_ID.get(id);
    if (scenario == null) {
      throw new IllegalArgumentException("Unknown scenario " + id);
    }
    return scenario;
  }

  public static String[] hostScenarioIds() {
    return HOST_SCENARIOS.stream().map(Scenario::id).toArray(String[]::new);
  }

  public static String[] pairedScenarioIds() {
    return PAIRED_SCENARIOS.stream().map(Scenario::id).toArray(String[]::new);
  }

  private static List<Scenario> createHostScenarios() {
    List<Scenario> scenarios = new ArrayList<>();
    scenarios.add(host(POLARIS_CONSTANT_FALSE, Family.POLARIS, "false", false, POLARIS_URL));
    scenarios.add(host(POLARIS_CUTOFF_LEFT, Family.POLARIS, POLARIS_CUTOFF, true, POLARIS_URL));
    scenarios.add(host(POLARIS_CUTOFF_RIGHT, Family.POLARIS, POLARIS_CUTOFF, true, POLARIS_URL));
    scenarios.add(host(POLARIS_CUTOFF_STOP, Family.POLARIS, POLARIS_CUTOFF, false, POLARIS_URL));
    scenarios.add(host(NESSIE_DEFAULT_ALLOW, Family.NESSIE, NESSIE_DEFAULT, true, NESSIE_URL));
    scenarios.add(host(NESSIE_DEFAULT_DENY, Family.NESSIE, NESSIE_DEFAULT, false, NESSIE_URL));
    scenarios.add(host(NESSIE_ROLES_HIT_LATE, Family.NESSIE, NESSIE_ROLES, true, NESSIE_URL));
    scenarios.add(host(NESSIE_ROLES_MISS, Family.NESSIE, NESSIE_ROLES, false, NESSIE_URL));
    scenarios.add(hostRules(NESSIE_RULES_FIRST, true));
    scenarios.add(hostRules(NESSIE_RULES_MIDDLE, true));
    scenarios.add(hostRules(NESSIE_RULES_LATE, true));
    scenarios.add(hostRules(NESSIE_RULES_DENY, false));
    scenarios.add(host(OPENFGA_BEFORE, Family.OPENFGA, OPENFGA_TIME, true, OPENFGA_URL));
    scenarios.add(host(OPENFGA_EQUAL, Family.OPENFGA, OPENFGA_TIME, false, OPENFGA_URL));
    scenarios.add(host(OPENFGA_AFTER, Family.OPENFGA, OPENFGA_TIME, false, OPENFGA_URL));
    scenarios.add(host(DAPR_TYPE_HIT, Family.DAPR_HOST, DAPR_TYPE, true, DAPR_URL));
    scenarios.add(host(DAPR_TYPE_MISS, Family.DAPR_HOST, DAPR_TYPE, false, DAPR_URL));
    scenarios.add(host(DAPR_IMPORTANT_HIT, Family.DAPR_HOST, DAPR_IMPORTANT, true, DAPR_URL));
    scenarios.add(host(DAPR_IMPORTANT_ABSENT, Family.DAPR_HOST, DAPR_IMPORTANT, false, DAPR_URL));
    scenarios.add(host(DAPR_DEPOSIT_HIT, Family.DAPR_HOST, DAPR_DEPOSIT, true, DAPR_URL));
    scenarios.add(host(DAPR_DEPOSIT_BOUNDARY, Family.DAPR_HOST, DAPR_DEPOSIT, false, DAPR_URL));
    return List.copyOf(scenarios);
  }

  private static List<Scenario> createPairedScenarios() {
    List<Scenario> scenarios = new ArrayList<>();
    scenarios.add(pair(IAM_PREFIX_HIT, Family.IAM, IAM_PREFIX, true, IAM_URL));
    scenarios.add(pair(IAM_PREFIX_TYPE_MISS, Family.IAM, IAM_PREFIX, false, IAM_URL));
    scenarios.add(pair(IAM_PREFIX_NAME_MISS, Family.IAM, IAM_PREFIX, false, IAM_URL));

    scenarios.add(
        adaptedPair(
            ORG_MEMBERS_EMPTY,
            Family.ORGANIZATION,
            ORGANIZATION_SOURCE,
            ORGANIZATION_BENCHMARK,
            true,
            "MemberTypeMatches is mirrored by a preclassified member.type field.",
            ORGANIZATION_URL));
    scenarios.add(pairFromPrevious(scenarios, ORG_MEMBERS_ONE, true));
    scenarios.add(pairFromPrevious(scenarios, ORG_MEMBERS_FAIL_FIRST_8, false));
    scenarios.add(pairFromPrevious(scenarios, ORG_MEMBERS_FAIL_LAST_8, false));
    scenarios.add(pairFromPrevious(scenarios, ORG_MEMBERS_ALL_32, true));
    scenarios.add(pairFromPrevious(scenarios, ORG_BINDINGS_ALL_8_X_8, true));
    scenarios.add(pairFromPrevious(scenarios, ORG_BINDINGS_FAIL_LAST_8_X_8, false));

    scenarios.add(pair(GATEWAY_EMPTY, Family.GATEWAY, GATEWAY_UNIQUENESS, true, GATEWAY_URL));
    scenarios.add(pairFromPrevious(scenarios, GATEWAY_ONE, true));
    scenarios.add(pairFromPrevious(scenarios, GATEWAY_UNIQUE_8, true));
    scenarios.add(pairFromPrevious(scenarios, GATEWAY_UNIQUE_16, true));
    scenarios.add(pairFromPrevious(scenarios, GATEWAY_DUPLICATE_FIRST_16, false));
    scenarios.add(pairFromPrevious(scenarios, GATEWAY_DUPLICATE_LAST_16, false));
    scenarios.add(pairFromPrevious(scenarios, GATEWAY_HOSTNAME_16, true));

    scenarios.add(pair(DRA_MATCH, Family.DRA, DRA_ATTRIBUTES, true, DRA_URL));
    scenarios.add(pairFromPrevious(scenarios, DRA_COLOR_MISS, false));
    scenarios.add(pairFromPrevious(scenarios, DRA_SIZE_MISS, false));
    scenarios.add(pairFromPrevious(scenarios, DRA_UNRELATED_32, true));

    scenarios.add(
        adaptedPair(
            FLUX_NO_CONDITIONS,
            Family.FLUX,
            FLUX_SOURCE,
            FLUX_BENCHMARK,
            true,
            "The source host exposes resource fields implicitly; the benchmark uses resource.",
            FLUX_URL));
    scenarios.add(pairFromPrevious(scenarios, FLUX_NO_READY_8, true));
    scenarios.add(pairFromPrevious(scenarios, FLUX_READY_ONE, true));
    scenarios.add(pairFromPrevious(scenarios, FLUX_READY_ALL_32, true));
    scenarios.add(pairFromPrevious(scenarios, FLUX_FAIL_FIRST_32, false));
    scenarios.add(pairFromPrevious(scenarios, FLUX_FAIL_LAST_32, false));

    scenarios.add(pair(DAPR_TYPED_IMPORTANT, Family.DAPR_TYPED, DAPR_IMPORTANT, true, DAPR_URL));
    scenarios.add(pairFromPrevious(scenarios, DAPR_TYPED_ABSENT, false));
    scenarios.add(pair(DAPR_TYPED_DEPOSIT, Family.DAPR_TYPED, DAPR_DEPOSIT, true, DAPR_URL));

    scenarios.add(
        representationPair(
            PROTOVALIDATE_BOOKING_HYBRID,
            Family.PROTOVALIDATE_BOOKING,
            BOOKING_PROTO,
            BOOKING_PROTO,
            BOOKING_JACKSON,
            true,
            "Jackson uses Java property names; protobuf retains schema field names.",
            QDRANT_BOOKING_URL));
    scenarios.add(pairFromPrevious(scenarios, PROTOVALIDATE_BOOKING_REGION, true));
    scenarios.add(pairFromPrevious(scenarios, PROTOVALIDATE_BOOKING_MISSING, false));

    scenarios.add(
        representationPair(
            PROTOVALIDATE_INTERVAL_ABSENT,
            Family.PROTOVALIDATE_INTERVAL,
            INTERVAL_PROTO,
            INTERVAL_PROTO,
            INTERVAL_JACKSON,
            true,
            "Jackson uses Java property names and nullable Instant fields.",
            REDPANDA_TRACING_URL));
    scenarios.add(pairFromPrevious(scenarios, PROTOVALIDATE_INTERVAL_ORDERED, true));
    scenarios.add(pairFromPrevious(scenarios, PROTOVALIDATE_INTERVAL_REVERSED, false));

    scenarios.add(
        pair(
            PROTOVALIDATE_NOTIFICATION_WEBHOOK,
            Family.PROTOVALIDATE_NOTIFICATION,
            NOTIFICATION,
            true,
            FLYTE_NOTIFICATION_URL));
    scenarios.add(pairFromPrevious(scenarios, PROTOVALIDATE_NOTIFICATION_EMAIL, true));
    scenarios.add(pairFromPrevious(scenarios, PROTOVALIDATE_NOTIFICATION_NONE, false));
    return List.copyOf(scenarios);
  }

  private static Scenario host(
      String id, Family family, String expression, boolean expected, String provenanceUrl) {
    return scenario(
        id,
        family,
        List.of(expression),
        Map.of(Representation.HOST, List.of(expression)),
        expected,
        EnumSet.of(Representation.HOST),
        "Source expression is used without adaptation.",
        provenanceUrl);
  }

  private static Scenario hostRules(String id, boolean expected) {
    return scenario(
        id,
        Family.NESSIE,
        NESSIE_RULES,
        Map.of(Representation.HOST, NESSIE_RULES),
        expected,
        EnumSet.of(Representation.HOST),
        "The HashMap-backed host policy is made source ordered for a deterministic position case.",
        NESSIE_URL);
  }

  private static Scenario pair(
      String id, Family family, String expression, boolean expected, String provenanceUrl) {
    return scenario(
        id,
        family,
        List.of(expression),
        Map.of(
            Representation.PROTOBUF,
            List.of(expression),
            Representation.JACKSON3,
            List.of(expression)),
        expected,
        EnumSet.of(Representation.PROTOBUF, Representation.JACKSON3),
        "Source expression is used without adaptation.",
        provenanceUrl);
  }

  private static Scenario adaptedPair(
      String id,
      Family family,
      String sourceExpression,
      String benchmarkExpression,
      boolean expected,
      String note,
      String provenanceUrl) {
    return scenario(
        id,
        family,
        List.of(sourceExpression),
        Map.of(
            Representation.PROTOBUF,
            List.of(benchmarkExpression),
            Representation.JACKSON3,
            List.of(benchmarkExpression)),
        expected,
        EnumSet.of(Representation.PROTOBUF, Representation.JACKSON3),
        note,
        provenanceUrl);
  }

  private static Scenario representationPair(
      String id,
      Family family,
      String sourceExpression,
      String protoExpression,
      String jacksonExpression,
      boolean expected,
      String note,
      String provenanceUrl) {
    return scenario(
        id,
        family,
        List.of(sourceExpression),
        Map.of(
            Representation.PROTOBUF,
            List.of(protoExpression),
            Representation.JACKSON3,
            List.of(jacksonExpression)),
        expected,
        EnumSet.of(Representation.PROTOBUF, Representation.JACKSON3),
        note,
        provenanceUrl);
  }

  private static Scenario pairFromPrevious(List<Scenario> scenarios, String id, boolean expected) {
    if (scenarios.isEmpty()) {
      throw new IllegalStateException("No prior paired scenario");
    }
    Scenario previous = scenarios.get(scenarios.size() - 1);
    return scenario(
        id,
        previous.family(),
        previous.sourceExpressions(),
        previous.benchmarkExpressions(),
        expected,
        previous.representations(),
        previous.adaptationNote(),
        previous.provenanceUrl());
  }

  private static Scenario scenario(
      String id,
      Family family,
      List<String> sourceExpressions,
      Map<Representation, List<String>> benchmarkExpressions,
      boolean expected,
      Set<Representation> representations,
      String adaptationNote,
      String provenanceUrl) {
    return new Scenario(
        id,
        family,
        sourceExpressions,
        benchmarkExpressions,
        expected,
        Boolean.class,
        representations,
        adaptationNote,
        provenanceUrl);
  }

  private static Map<String, Scenario> index() {
    Map<String, Scenario> index = new LinkedHashMap<>();
    for (Scenario scenario : HOST_SCENARIOS) {
      if (index.put(scenario.id(), scenario) != null) {
        throw new IllegalStateException("Duplicate scenario " + scenario.id());
      }
    }
    for (Scenario scenario : PAIRED_SCENARIOS) {
      if (index.put(scenario.id(), scenario) != null) {
        throw new IllegalStateException("Duplicate scenario " + scenario.id());
      }
    }
    return Map.copyOf(index);
  }
}
