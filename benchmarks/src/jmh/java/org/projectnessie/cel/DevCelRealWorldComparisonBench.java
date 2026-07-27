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

import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_IMPORTANT_HIT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DRA_UNRELATED_32;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.FLUX_READY_ALL_32;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.GATEWAY_HOSTNAME_16;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.GATEWAY_UNIQUE_16;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.IAM_PREFIX_HIT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.NESSIE_DEFAULT_ALLOW;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.NESSIE_ROLES_MISS;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_BINDINGS_ALL_8_X_8;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_MEMBERS_ALL_32;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.POLARIS_CUTOFF_STOP;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.PROTOVALIDATE_NOTIFICATION_NONE;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.CelType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerBuilder;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import org.projectnessie.cel.benchmark.PreparedFixture;
import org.projectnessie.cel.benchmark.RealWorldHostFixtures;
import org.projectnessie.cel.benchmark.RealWorldProtoFixtures;
import org.projectnessie.cel.benchmark.RealWorldWorkloads;
import org.projectnessie.cel.benchmark.RealWorldWorkloads.Family;
import org.projectnessie.cel.benchmark.RealWorldWorkloads.Representation;
import org.projectnessie.cel.benchmark.RealWorldWorkloads.Scenario;

/**
 * Compares Projectnessie CEL-Java with the independent {@code dev.cel} Java implementation.
 *
 * <p>The {@code dev.cel} implementation is maintained in the <a
 * href="https://github.com/cel-expr/cel-java">{@code cel-expr/cel-java}</a> repository, formerly
 * {@code google/cel-java}. It is not an upstream project from which Projectnessie CEL-Java is
 * derived. Its exact release is pinned by the {@code celExprJava} entry in the Gradle version
 * catalog.
 *
 * <h2>Measured boundary</h2>
 *
 * <p>This benchmark measures warm, single-threaded, complete-program evaluation. Each state
 * constructs, checks, and plans its programs once during trial setup. Each benchmark invocation
 * then:
 *
 * <ul>
 *   <li>evaluates the same CEL source expression or ordered expression sequence,
 *   <li>uses the same activation and, for Protobuf scenarios, the same generated message instance,
 *   <li>short-circuits an expression sequence after the first {@code true} result, and
 *   <li>materializes the result as a Java {@link Boolean}.
 * </ul>
 *
 * <p>Checking, planning, environment construction, JVM startup, concurrency scaling, and
 * application tail latency are outside the measured boundary.
 *
 * <h2>Compared configurations</h2>
 *
 * <p>Projectnessie is represented by two configurations. Both use {@link EvalOption#OptOptimize}.
 * The {@code projectnessieExactNative*} methods additionally use exact input adapters and native
 * planning. This is an opt-in fast path whose availability depends on the registered input types
 * and expression shape. The {@code projectnessieGeneral*} methods use the general value-adaptation
 * path and are included to make the contribution and scope of exact/native evaluation visible.
 *
 * <p>The {@code devCel*} methods use {@link CelCompilerFactory#standardCelCompilerBuilder()} with
 * the standard macros and {@link CelRuntimeFactory#plannerRuntimeBuilder()}. No separate {@code
 * dev.cel} AST optimization pass is applied. This represents the documented compiler and planner
 * runtime path, not every configuration or feature offered by that implementation.
 *
 * <h2>Workload and dependency limitations</h2>
 *
 * <p>The scenarios are a deliberately bounded common subset of {@link RealWorldWorkloads}. They
 * cover scalar and list host values, a dynamic map, constant regular expressions, and generated
 * Protobuf fields, maps, repeated messages, presence, and nested comprehensions. Jackson scenarios
 * are excluded because there is no equivalent {@code dev.cel} Jackson integration in this harness.
 * The subset must not be treated as a general ranking across all CEL features, input systems,
 * diagnostics, extensions, or application workloads.
 *
 * <p>Both implementations run in one JMH classpath and therefore use the Protobuf runtime version
 * pinned by this project. This keeps the generated messages and runtime representation identical,
 * but it does not reproduce the exact transitive dependency graph declared by the selected {@code
 * dev.cel} release.
 *
 * <h2>Correctness and interpretation</h2>
 *
 * <p>Trial setup evaluates every compared configuration and rejects a scenario unless all results
 * match its expected value. This is a semantic guard for the selected inputs, not a conformance
 * test. Benchmark results should always identify the two library revisions, JDK, hardware, JMH
 * configuration, selected scenarios, and compared modes. Results from this class establish only the
 * relative cost of the documented warm-evaluation boundary.
 */
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class DevCelRealWorldComparisonBench {
  @State(Scope.Benchmark)
  public static class HostState {
    @Param({POLARIS_CUTOFF_STOP, NESSIE_DEFAULT_ALLOW, NESSIE_ROLES_MISS, DAPR_IMPORTANT_HIT})
    public String scenarioId;

    RealWorldProgramSet projectnessie;
    DevCelPrograms devCel;

    @Setup
    public void setup() throws CelValidationException, CelEvaluationException {
      Scenario scenario = RealWorldWorkloads.scenario(scenarioId);
      PreparedFixture fixture = RealWorldHostFixtures.prepare(scenario);
      projectnessie = RealWorldProgramSet.host(scenario, fixture);
      devCel = DevCelPrograms.host(scenario, fixture);
      verify(scenario, projectnessie.exactNative(), projectnessie.general(), devCel.evaluate());
    }
  }

  @State(Scope.Benchmark)
  public static class ProtoState {
    @Param({
      IAM_PREFIX_HIT,
      DRA_UNRELATED_32,
      ORG_MEMBERS_ALL_32,
      ORG_BINDINGS_ALL_8_X_8,
      GATEWAY_HOSTNAME_16,
      GATEWAY_UNIQUE_16,
      FLUX_READY_ALL_32,
      PROTOVALIDATE_NOTIFICATION_NONE
    })
    public String scenarioId;

    RealWorldProgramSet projectnessie;
    DevCelPrograms devCel;

    @Setup
    public void setup() throws CelValidationException, CelEvaluationException {
      Scenario scenario = RealWorldWorkloads.scenario(scenarioId);
      PreparedFixture fixture = RealWorldProtoFixtures.prepare(scenario);
      Message[] messageTypes = RealWorldProtoFixtures.registeredTypes(scenario.family());
      projectnessie = RealWorldProgramSet.protobuf(scenario, fixture, messageTypes);
      devCel = DevCelPrograms.protobuf(scenario, fixture, messageTypes);
      verify(scenario, projectnessie.exactNative(), projectnessie.general(), devCel.evaluate());
    }
  }

  @Benchmark
  public Boolean projectnessieExactNativeHost(HostState state) {
    return state.projectnessie.exactNative();
  }

  @Benchmark
  public Boolean projectnessieGeneralHost(HostState state) {
    return state.projectnessie.general();
  }

  @Benchmark
  public Boolean devCelHost(HostState state) throws CelEvaluationException {
    return state.devCel.evaluate();
  }

  @Benchmark
  public Boolean projectnessieExactNativeProto(ProtoState state) {
    return state.projectnessie.exactNative();
  }

  @Benchmark
  public Boolean projectnessieGeneralProto(ProtoState state) {
    return state.projectnessie.general();
  }

  @Benchmark
  public Boolean devCelProto(ProtoState state) throws CelEvaluationException {
    return state.devCel.evaluate();
  }

  private static void verify(
      Scenario scenario,
      Boolean projectnessieExactNative,
      Boolean projectnessieGeneral,
      Boolean devCel) {
    if (projectnessieExactNative != scenario.expected()
        || projectnessieGeneral != scenario.expected()
        || devCel != scenario.expected()) {
      throw new IllegalStateException(
          scenario.id()
              + ": expected="
              + scenario.expected()
              + ", projectnessieExactNative="
              + projectnessieExactNative
              + ", projectnessieGeneral="
              + projectnessieGeneral
              + ", devCel="
              + devCel);
    }
  }

  private record DevCelPrograms(List<CelRuntime.Program> programs, Map<String, Object> activation) {
    private DevCelPrograms(List<CelRuntime.Program> programs, Map<String, Object> activation) {
      this.programs = List.copyOf(programs);
      this.activation = activation;
    }

    static DevCelPrograms host(Scenario scenario, PreparedFixture fixture)
        throws CelValidationException, CelEvaluationException {
      CelCompilerBuilder compiler = standardCompiler();
      addHostVariables(compiler, scenario.family());
      return create(
          compiler.build(),
          CelRuntimeFactory.plannerRuntimeBuilder().build(),
          scenario.expressions(Representation.HOST),
          fixture.activation());
    }

    static DevCelPrograms protobuf(
        Scenario scenario, PreparedFixture fixture, Message[] messageTypes)
        throws CelValidationException, CelEvaluationException {
      List<Descriptor> descriptors =
          Arrays.stream(messageTypes).map(Message::getDescriptorForType).toList();
      Descriptor rootDescriptor = messageTypes[0].getDescriptorForType();
      CelType rootType = StructTypeReference.create(rootDescriptor.getFullName());
      CelCompilerBuilder compiler =
          standardCompiler()
              .addMessageTypes(descriptors)
              .addVar(variableName(scenario.family()), variableType(scenario.family(), rootType));
      CelRuntime runtime =
          CelRuntimeFactory.plannerRuntimeBuilder().addMessageTypes(descriptors).build();
      return create(
          compiler.build(),
          runtime,
          scenario.expressions(Representation.PROTOBUF),
          fixture.activation());
    }

    private static CelCompilerBuilder standardCompiler() {
      return CelCompilerFactory.standardCelCompilerBuilder()
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS);
    }

    private static DevCelPrograms create(
        CelCompiler compiler,
        CelRuntime runtime,
        List<String> expressions,
        Map<String, Object> activation)
        throws CelValidationException, CelEvaluationException {
      List<CelRuntime.Program> programs = new ArrayList<>(expressions.size());
      for (String expression : expressions) {
        programs.add(runtime.createProgram(compiler.compile(expression).getAst()));
      }
      return new DevCelPrograms(programs, activation);
    }

    Boolean evaluate() throws CelEvaluationException {
      for (CelRuntime.Program program : programs) {
        if ((Boolean) program.eval(activation)) {
          return true;
        }
      }
      return false;
    }

    private static void addHostVariables(CelCompilerBuilder compiler, Family family) {
      switch (family) {
        case POLARIS ->
            compiler
                .addVar("ref", SimpleType.STRING)
                .addVar("commits", SimpleType.INT)
                .addVar("ageMinutes", SimpleType.INT)
                .addVar("ageHours", SimpleType.INT)
                .addVar("ageDays", SimpleType.INT);
        case NESSIE ->
            compiler
                .addVar("op", SimpleType.STRING)
                .addVar("role", SimpleType.STRING)
                .addVar("roles", ListType.create(SimpleType.STRING))
                .addVar("ref", SimpleType.STRING)
                .addVar("path", SimpleType.STRING)
                .addVar("contentType", SimpleType.STRING);
        case DAPR_HOST -> compiler.addVar("event", SimpleType.DYN);
        default -> throw new IllegalArgumentException("Unsupported host family " + family);
      }
    }

    private static String variableName(Family family) {
      return switch (family) {
        case IAM, ORGANIZATION, FLUX -> "resource";
        case GATEWAY -> "self";
        case DRA -> "device";
        case DAPR_TYPED -> "event";
        case PROTOVALIDATE_BOOKING, PROTOVALIDATE_INTERVAL, PROTOVALIDATE_NOTIFICATION -> "this";
        default -> throw new IllegalArgumentException("Unsupported protobuf family " + family);
      };
    }

    private static CelType variableType(Family family, CelType rootType) {
      return family == Family.GATEWAY ? ListType.create(rootType) : rootType;
    }
  }
}
