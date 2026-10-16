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

import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_TYPED_ABSENT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_TYPED_DEPOSIT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_TYPED_IMPORTANT;
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
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.IAM_PREFIX_HIT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.IAM_PREFIX_NAME_MISS;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.IAM_PREFIX_TYPE_MISS;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_BINDINGS_ALL_8_X_8;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_BINDINGS_FAIL_LAST_8_X_8;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_MEMBERS_ALL_32;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_MEMBERS_EMPTY;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_MEMBERS_FAIL_FIRST_8;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_MEMBERS_FAIL_LAST_8;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_MEMBERS_ONE;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_BOOKING_HYBRID;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_BOOKING_MISSING;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_BOOKING_REGION;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_INTERVAL_ABSENT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_INTERVAL_ORDERED;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_INTERVAL_REVERSED;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_NOTIFICATION_EMAIL;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_NOTIFICATION_NONE;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_NOTIFICATION_WEBHOOK;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/** Shared complete scenario parameter for the two paired representations. */
@State(Scope.Benchmark)
public abstract class RealWorldPairedWorkloadState {
  @Param({
    IAM_PREFIX_HIT,
    IAM_PREFIX_TYPE_MISS,
    IAM_PREFIX_NAME_MISS,
    ORG_MEMBERS_EMPTY,
    ORG_MEMBERS_ONE,
    ORG_MEMBERS_FAIL_FIRST_8,
    ORG_MEMBERS_FAIL_LAST_8,
    ORG_MEMBERS_ALL_32,
    ORG_BINDINGS_ALL_8_X_8,
    ORG_BINDINGS_FAIL_LAST_8_X_8,
    GATEWAY_EMPTY,
    GATEWAY_ONE,
    GATEWAY_UNIQUE_8,
    GATEWAY_UNIQUE_16,
    GATEWAY_DUPLICATE_FIRST_16,
    GATEWAY_DUPLICATE_LAST_16,
    GATEWAY_HOSTNAME_16,
    DRA_MATCH,
    DRA_COLOR_MISS,
    DRA_SIZE_MISS,
    DRA_UNRELATED_32,
    FLUX_NO_CONDITIONS,
    FLUX_NO_READY_8,
    FLUX_READY_ONE,
    FLUX_READY_ALL_32,
    FLUX_FAIL_FIRST_32,
    FLUX_FAIL_LAST_32,
    DAPR_TYPED_IMPORTANT,
    DAPR_TYPED_ABSENT,
    DAPR_TYPED_DEPOSIT,
    PROTOVALIDATE_BOOKING_HYBRID,
    PROTOVALIDATE_BOOKING_REGION,
    PROTOVALIDATE_BOOKING_MISSING,
    PROTOVALIDATE_INTERVAL_ABSENT,
    PROTOVALIDATE_INTERVAL_ORDERED,
    PROTOVALIDATE_INTERVAL_REVERSED,
    PROTOVALIDATE_NOTIFICATION_WEBHOOK,
    PROTOVALIDATE_NOTIFICATION_EMAIL,
    PROTOVALIDATE_NOTIFICATION_NONE
  })
  public String scenarioId;

  RealWorldProgramSet programs;
}
