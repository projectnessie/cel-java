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

import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import org.projectnessie.cel.benchmark.RealWorldWorkloads.Scenario;

/** Builds source-host activation fixtures and their direct-Java semantic controls. */
public final class RealWorldHostFixtures {
  private static final Pattern MATCH_ALL = Pattern.compile(".*");
  private static final Set<String> NESSIE_ADMIN_OPERATIONS =
      Set.of(
          "VIEW_REFERENCE",
          "CREATE_REFERENCE",
          "DELETE_REFERENCE",
          "VIEW_REFLOG",
          "LIST_COMMITLOG",
          "READ_ENTRIES",
          "READ_CONTENT_KEY",
          "LIST_COMMIT_LOG",
          "COMMIT_CHANGE_AGAINST_REFERENCE",
          "ASSIGN_REFERENCE_TO_HASH",
          "CREATE_ENTITY",
          "UPDATE_ENTITY",
          "READ_ENTITY_VALUE",
          "DELETE_ENTITY");
  private static final Set<String> NESSIE_BRANCH_OPERATIONS =
      Set.of(
          "CREATE_REFERENCE",
          "DELETE_REFERENCE",
          "LIST_COMMIT_LOG",
          "READ_ENTRIES",
          "READ_CONTENT_KEY",
          "ASSIGN_REFERENCE_TO_HASH");
  private static final Set<String> NESSIE_USER1_OPERATIONS =
      Set.of(
          "VIEW_REFERENCE",
          "ASSIGN_REFERENCE_TO_HASH",
          "COMMIT_CHANGE_AGAINST_REFERENCE",
          "DELETE_REFERENCE");
  private static final Set<String> NESSIE_USER2_OPERATIONS =
      Set.of("VIEW_REFERENCE", "CREATE_ENTITY", "UPDATE_ENTITY");
  private static final Set<String> NESSIE_USER3_OPERATIONS =
      Set.of("VIEW_REFERENCE", "DELETE_ENTITY_VALUE", "CREATE_ENTITY", "UPDATE_ENTITY");
  private static final Set<String> NESSIE_USER4_OPERATIONS =
      Set.of("VIEW_REFERENCE", "READ_ENTITY_VALUE", "DELETE_ENTITY");
  private static final Set<String> NESSIE_DELETE_BRANCH_OPERATIONS =
      Set.of("VIEW_REFERENCE", "CREATE_REFERENCE");
  private static final Set<String> NESSIE_DELETE_BRANCH_REFS =
      Set.of("testDeleteBranchDisallowed", "main");

  private RealWorldHostFixtures() {}

  public static PreparedFixture prepare(Scenario scenario) {
    return switch (scenario.family()) {
      case POLARIS -> polaris(scenario);
      case NESSIE -> nessie(scenario);
      case OPENFGA -> openFga(scenario);
      case DAPR_HOST -> dapr(scenario);
      default -> throw new IllegalArgumentException("Not a host scenario " + scenario.id());
    };
  }

  private static PreparedFixture polaris(Scenario scenario) {
    long commits;
    long ageDays;
    switch (scenario.id()) {
      case "polaris.constant.false" -> {
        commits = 1L;
        ageDays = 0L;
      }
      case "polaris.cutoff.left" -> {
        commits = 12L;
        ageDays = 29L;
      }
      case "polaris.cutoff.right" -> {
        commits = 10L;
        ageDays = 30L;
      }
      case "polaris.cutoff.stop" -> {
        commits = 11L;
        ageDays = 30L;
      }
      default -> throw new IllegalArgumentException(scenario.id());
    }
    Map<String, Object> activation =
        Map.of(
            "ref",
            "principals",
            "commits",
            commits,
            "ageMinutes",
            ageDays * 24L * 60L,
            "ageHours",
            ageDays * 24L,
            "ageDays",
            ageDays);
    BooleanSupplier direct =
        scenario.id().equals("polaris.constant.false")
            ? () -> false
            : () -> ageDays < 30L || commits <= 10L;
    return new PreparedFixture(activation, direct);
  }

  private static PreparedFixture nessie(Scenario scenario) {
    String op = "";
    String role = "";
    List<String> roles = List.of();
    String ref = "";
    String path = "";
    switch (scenario.id()) {
      case "nessie.default.allow" -> {
        op = "VIEW_REFERENCE";
        ref = "main";
      }
      case "nessie.default.deny" -> {
        op = "CREATE_REFERENCE";
        ref = "main";
      }
      case "nessie.roles.hitLate" -> {
        List<String> values = new ArrayList<>(32);
        for (int i = 0; i < 31; i++) {
          values.add("role-" + i);
        }
        values.add("bar");
        roles = List.copyOf(values);
      }
      case "nessie.roles.miss" -> {
        List<String> values = new ArrayList<>(32);
        for (int i = 0; i < 32; i++) {
          values.add("role-" + i);
        }
        roles = List.copyOf(values);
      }
      case "nessie.rules.first" -> {
        op = "VIEW_REFERENCE";
        role = "admin_user";
        ref = "main";
      }
      case "nessie.rules.middle" -> {
        op = "CREATE_ENTITY";
        role = "test_user3";
        ref = "allowedBranch-middle";
        path = "allowed-content";
      }
      case "nessie.rules.late" -> {
        op = "VIEW_REFERENCE";
        role = "user1";
        ref = "main";
      }
      case "nessie.rules.deny" -> {
        op = "DELETE_REFERENCE";
        role = "unmatched";
        ref = "deniedBranch";
      }
      default -> throw new IllegalArgumentException(scenario.id());
    }

    Map<String, Object> activation =
        Map.of(
            "op", op,
            "role", role,
            "roles", roles,
            "ref", ref,
            "path", path,
            "contentType", "");
    BooleanSupplier direct =
        switch (scenario.id()) {
          case "nessie.default.allow", "nessie.default.deny" ->
              () ->
                  ((String) activation.get("op")).equals("VIEW_REFERENCE")
                      && MATCH_ALL.matcher((String) activation.get("ref")).find();
          case "nessie.roles.hitLate", "nessie.roles.miss" ->
              () -> ((List<?>) activation.get("roles")).contains("bar");
          default -> () -> nessieRules(activation);
        };
    return new PreparedFixture(activation, direct);
  }

  private static boolean nessieRules(Map<String, Object> activation) {
    String op = (String) activation.get("op");
    String role = (String) activation.get("role");
    String ref = (String) activation.get("ref");
    String path = (String) activation.get("path");
    if (NESSIE_ADMIN_OPERATIONS.contains(op) && role.equals("admin_user")) {
      return true;
    }
    if (op.equals("VIEW_REFERENCE")
        && role.startsWith("test_user")
        && MATCH_ALL.matcher(ref).find()) {
      return true;
    }
    if (NESSIE_BRANCH_OPERATIONS.contains(op)
        && role.startsWith("test_user")
        && ref.startsWith("allowedBranch")) {
      return true;
    }
    if (op.equals("COMMIT_CHANGE_AGAINST_REFERENCE")
        && role.startsWith("test_user")
        && ref.startsWith("allowedBranch")) {
      return true;
    }
    if (NESSIE_USER2_OPERATIONS.contains(op)
        && role.equals("test_user2")
        && path.startsWith("allowed-")
        && ref.startsWith("allowedBranch")) {
      return true;
    }
    if (NESSIE_USER3_OPERATIONS.contains(op)
        && role.equals("test_user3")
        && path.startsWith("allowed-")
        && ref.startsWith("allowedBranch")) {
      return true;
    }
    if (NESSIE_USER4_OPERATIONS.contains(op)
        && role.equals("test_user4")
        && path.startsWith("allowed-")
        && ref.startsWith("allowedBranch")) {
      return true;
    }
    if (op.equals("COMMIT_CHANGE_AGAINST_REFERENCE")
        && role.equals("test_user2")
        && ref.startsWith("allowedBranch")) {
      return true;
    }
    if (op.equals("CREATE_REFERENCE") && role.equals("user1") && MATCH_ALL.matcher(ref).find()) {
      return true;
    }
    if (NESSIE_USER1_OPERATIONS.contains(op)
        && role.equals("user1")
        && (ref.startsWith("allowedBranch") || ref.equals("main"))) {
      return true;
    }
    return NESSIE_DELETE_BRANCH_OPERATIONS.contains(op)
        && role.equals("delete_branch_disallowed_user")
        && NESSIE_DELETE_BRANCH_REFS.contains(ref);
  }

  private static PreparedFixture openFga(Scenario scenario) {
    Instant grant = Instant.parse("2026-01-01T00:00:00Z");
    java.time.Duration duration = java.time.Duration.ofHours(1L);
    Instant cutoff = grant.plus(duration);
    Instant current =
        switch (scenario.id()) {
          case "openfga.before" -> cutoff.minusNanos(1L);
          case "openfga.equal" -> cutoff;
          case "openfga.after" -> cutoff.plusNanos(1L);
          default -> throw new IllegalArgumentException(scenario.id());
        };
    Map<String, Object> activation =
        Map.of(
            "current_time",
            timestamp(current),
            "grant_time",
            timestamp(grant),
            "grant_duration",
            duration(duration));
    return new PreparedFixture(activation, () -> current.isBefore(grant.plus(duration)));
  }

  private static PreparedFixture dapr(Scenario scenario) {
    String type;
    Map<String, Object> data;
    switch (scenario.id()) {
      case "dapr.type.hit" -> {
        type = "widget";
        data = Map.of();
      }
      case "dapr.type.miss" -> {
        type = "other";
        data = Map.of();
      }
      case "dapr.important.hit" -> {
        type = "other";
        data = Map.of("important", true);
      }
      case "dapr.important.absent" -> {
        type = "other";
        data = Map.of();
      }
      case "dapr.deposit.hit" -> {
        type = "deposit";
        data = Map.of("amount", "10001");
      }
      case "dapr.deposit.boundary" -> {
        type = "deposit";
        data = Map.of("amount", "10000");
      }
      default -> throw new IllegalArgumentException(scenario.id());
    }
    Map<String, Object> event = Map.of("type", type, "data", data);
    Map<String, Object> activation = Map.of("event", event);
    BooleanSupplier direct =
        switch (scenario.id()) {
          case "dapr.type.hit", "dapr.type.miss" ->
              () -> ((String) event.get("type")).equals("widget");
          case "dapr.important.hit", "dapr.important.absent" ->
              () -> {
                Map<?, ?> directData = (Map<?, ?>) event.get("data");
                return directData.containsKey("important")
                    && Boolean.TRUE.equals(directData.get("important"));
              };
          case "dapr.deposit.hit", "dapr.deposit.boundary" ->
              () ->
                  ((String) event.get("type")).equals("deposit")
                      && Long.parseLong((String) ((Map<?, ?>) event.get("data")).get("amount"))
                          > 10_000L;
          default -> throw new IllegalArgumentException(scenario.id());
        };
    return new PreparedFixture(activation, direct);
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }

  private static Duration duration(java.time.Duration duration) {
    return Duration.newBuilder()
        .setSeconds(duration.getSeconds())
        .setNanos(duration.getNano())
        .build();
  }
}
