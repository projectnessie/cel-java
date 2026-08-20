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

import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_DEPOSIT_HIT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_IMPORTANT_HIT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_TYPED_DEPOSIT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_TYPED_IMPORTANT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_TYPE_HIT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DRA_MATCH;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.FLUX_READY_ALL_32;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.GATEWAY_UNIQUE_16;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.IAM_PREFIX_HIT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.NESSIE_DEFAULT_ALLOW;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.NESSIE_ROLES_HIT_LATE;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.NESSIE_RULES_DENY;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.OPENFGA_BEFORE;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_BINDINGS_ALL_8_X_8;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.POLARIS_CUTOFF_LEFT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_BOOKING_REGION;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_INTERVAL_ORDERED;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_NOTIFICATION_WEBHOOK;

import java.util.List;
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
import org.projectnessie.cel.RealWorldProgramSet.Lifecycle;
import org.projectnessie.cel.benchmark.RealWorldHostFixtures;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures;
import org.projectnessie.cel.benchmark.RealWorldProtoFixtures;
import org.projectnessie.cel.benchmark.RealWorldWorkloads;

/**
 * Measures compilation, planning, and the first evaluation of a fresh program for representative
 * real-world expression shapes.
 *
 * <p>The compilation methods include parsing and checking. Compilation and planning retain a
 * configured environment. The {@code cold*} methods include environment and registry construction,
 * parsing, checking, program construction, and one complete evaluation, but not whole-JVM startup.
 */
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RealWorldLifecycleBench {
  @State(Scope.Benchmark)
  public static class HostState {
    @Param({
      POLARIS_CUTOFF_LEFT,
      NESSIE_DEFAULT_ALLOW,
      NESSIE_ROLES_HIT_LATE,
      NESSIE_RULES_DENY,
      OPENFGA_BEFORE,
      DAPR_TYPE_HIT,
      DAPR_IMPORTANT_HIT,
      DAPR_DEPOSIT_HIT
    })
    public String scenarioId;

    Lifecycle lifecycle;

    @Setup
    public void setup() {
      var scenario = RealWorldWorkloads.scenario(scenarioId);
      lifecycle =
          RealWorldProgramSet.hostLifecycle(scenario, RealWorldHostFixtures.prepare(scenario));
    }
  }

  @State(Scope.Benchmark)
  public abstract static class PairedState {
    @Param({
      IAM_PREFIX_HIT,
      ORG_BINDINGS_ALL_8_X_8,
      GATEWAY_UNIQUE_16,
      DRA_MATCH,
      FLUX_READY_ALL_32,
      DAPR_TYPED_IMPORTANT,
      DAPR_TYPED_DEPOSIT,
      PROTOVALIDATE_BOOKING_REGION,
      PROTOVALIDATE_INTERVAL_ORDERED,
      PROTOVALIDATE_NOTIFICATION_WEBHOOK
    })
    public String scenarioId;

    Lifecycle lifecycle;
  }

  @State(Scope.Benchmark)
  public static class ProtoState extends PairedState {
    @Setup
    public void setup() {
      var scenario = RealWorldWorkloads.scenario(scenarioId);
      lifecycle =
          RealWorldProgramSet.protobufLifecycle(
              scenario,
              RealWorldProtoFixtures.prepare(scenario),
              RealWorldProtoFixtures.registeredTypes(scenario.family()));
    }
  }

  @State(Scope.Benchmark)
  public static class Jackson3State extends PairedState {
    @Setup
    public void setup() {
      var scenario = RealWorldWorkloads.scenario(scenarioId);
      lifecycle =
          RealWorldProgramSet.jackson3Lifecycle(
              scenario,
              RealWorldPojoFixtures.prepare(scenario),
              RealWorldPojoFixtures.registeredTypes(scenario.family()));
    }
  }

  @Benchmark
  public List<Ast> hostCompileExact(HostState state) {
    return state.lifecycle.compileExact();
  }

  @Benchmark
  public List<Ast> hostCompileGeneral(HostState state) {
    return state.lifecycle.compileGeneral();
  }

  @Benchmark
  public List<Program> hostPlanExactNative(HostState state) {
    return state.lifecycle.planExactNative();
  }

  @Benchmark
  public List<Program> hostPlanExactDisabled(HostState state) {
    return state.lifecycle.planExactDisabled();
  }

  @Benchmark
  public List<Program> hostPlanGeneral(HostState state) {
    return state.lifecycle.planGeneral();
  }

  @Benchmark
  public Boolean hostColdExactNative(HostState state) {
    return state.lifecycle.coldExactNative();
  }

  @Benchmark
  public Boolean hostColdExactDisabled(HostState state) {
    return state.lifecycle.coldExactDisabled();
  }

  @Benchmark
  public Boolean hostColdGeneral(HostState state) {
    return state.lifecycle.coldGeneral();
  }

  @Benchmark
  public List<Ast> protoCompileExact(ProtoState state) {
    return state.lifecycle.compileExact();
  }

  @Benchmark
  public List<Ast> protoCompileGeneral(ProtoState state) {
    return state.lifecycle.compileGeneral();
  }

  @Benchmark
  public List<Program> protoPlanExactNative(ProtoState state) {
    return state.lifecycle.planExactNative();
  }

  @Benchmark
  public List<Program> protoPlanExactDisabled(ProtoState state) {
    return state.lifecycle.planExactDisabled();
  }

  @Benchmark
  public List<Program> protoPlanGeneral(ProtoState state) {
    return state.lifecycle.planGeneral();
  }

  @Benchmark
  public Boolean protoColdExactNative(ProtoState state) {
    return state.lifecycle.coldExactNative();
  }

  @Benchmark
  public Boolean protoColdExactDisabled(ProtoState state) {
    return state.lifecycle.coldExactDisabled();
  }

  @Benchmark
  public Boolean protoColdGeneral(ProtoState state) {
    return state.lifecycle.coldGeneral();
  }

  @Benchmark
  public List<Ast> jackson3CompileExact(Jackson3State state) {
    return state.lifecycle.compileExact();
  }

  @Benchmark
  public List<Ast> jackson3CompileGeneral(Jackson3State state) {
    return state.lifecycle.compileGeneral();
  }

  @Benchmark
  public List<Program> jackson3PlanExactNative(Jackson3State state) {
    return state.lifecycle.planExactNative();
  }

  @Benchmark
  public List<Program> jackson3PlanExactDisabled(Jackson3State state) {
    return state.lifecycle.planExactDisabled();
  }

  @Benchmark
  public List<Program> jackson3PlanGeneral(Jackson3State state) {
    return state.lifecycle.planGeneral();
  }

  @Benchmark
  public Boolean jackson3ColdExactNative(Jackson3State state) {
    return state.lifecycle.coldExactNative();
  }

  @Benchmark
  public Boolean jackson3ColdExactDisabled(Jackson3State state) {
    return state.lifecycle.coldExactDisabled();
  }

  @Benchmark
  public Boolean jackson3ColdGeneral(Jackson3State state) {
    return state.lifecycle.coldGeneral();
  }
}
