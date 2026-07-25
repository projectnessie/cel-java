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

import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_DEPOSIT_BOUNDARY;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_DEPOSIT_HIT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_IMPORTANT_ABSENT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_IMPORTANT_HIT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_TYPE_HIT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_TYPE_MISS;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.NESSIE_DEFAULT_ALLOW;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.NESSIE_DEFAULT_DENY;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.NESSIE_ROLES_HIT_LATE;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.NESSIE_ROLES_MISS;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.NESSIE_RULES_DENY;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.NESSIE_RULES_FIRST;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.NESSIE_RULES_LATE;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.NESSIE_RULES_MIDDLE;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.OPENFGA_AFTER;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.OPENFGA_BEFORE;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.OPENFGA_EQUAL;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.POLARIS_CONSTANT_FALSE;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.POLARIS_CUTOFF_LEFT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.POLARIS_CUTOFF_RIGHT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.POLARIS_CUTOFF_STOP;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.projectnessie.cel.benchmark.RealWorldHostFixtures;
import org.projectnessie.cel.benchmark.RealWorldWorkloads;

/** Complete-program source-host workloads derived from Polaris, Nessie, OpenFGA, and Dapr. */
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RealWorldHostWorkloadBench {
  @State(Scope.Benchmark)
  public static class WorkloadState {
    @Param({
      POLARIS_CONSTANT_FALSE,
      POLARIS_CUTOFF_LEFT,
      POLARIS_CUTOFF_RIGHT,
      POLARIS_CUTOFF_STOP,
      NESSIE_DEFAULT_ALLOW,
      NESSIE_DEFAULT_DENY,
      NESSIE_ROLES_HIT_LATE,
      NESSIE_ROLES_MISS,
      NESSIE_RULES_FIRST,
      NESSIE_RULES_MIDDLE,
      NESSIE_RULES_LATE,
      NESSIE_RULES_DENY,
      OPENFGA_BEFORE,
      OPENFGA_EQUAL,
      OPENFGA_AFTER,
      DAPR_TYPE_HIT,
      DAPR_TYPE_MISS,
      DAPR_IMPORTANT_HIT,
      DAPR_IMPORTANT_ABSENT,
      DAPR_DEPOSIT_HIT,
      DAPR_DEPOSIT_BOUNDARY
    })
    public String scenarioId;

    RealWorldProgramSet programs;

    @Setup
    public void setup() {
      var scenario = RealWorldWorkloads.scenario(scenarioId);
      programs = RealWorldProgramSet.host(scenario, RealWorldHostFixtures.prepare(scenario));
    }
  }

  @Benchmark
  public Boolean exactNative(WorkloadState state) {
    return state.programs.exactNative();
  }

  @Benchmark
  public Boolean exactDisabled(WorkloadState state) {
    return state.programs.exactDisabled();
  }

  @Benchmark
  public Boolean general(WorkloadState state) {
    return state.programs.general();
  }

  @Benchmark
  public Boolean direct(WorkloadState state) {
    return state.programs.direct();
  }
}
