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

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures;
import org.projectnessie.cel.benchmark.RealWorldWorkloads;

/** Complete-program Jackson 3 POJO representation workloads. */
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RealWorldJackson3WorkloadBench {
  @State(Scope.Benchmark)
  public static class WorkloadState extends RealWorldPairedWorkloadState {
    @Setup
    public void setup() {
      var scenario = RealWorldWorkloads.scenario(scenarioId);
      programs =
          RealWorldProgramSet.jackson3(
              scenario,
              RealWorldPojoFixtures.prepare(scenario),
              RealWorldPojoFixtures.registeredTypes(scenario.family()));
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
