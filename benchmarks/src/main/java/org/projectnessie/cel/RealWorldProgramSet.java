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

import static org.projectnessie.cel.Env.newCustomEnv;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.customTypeProvider;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.EvalOption.OptOptimize;
import static org.projectnessie.cel.ProgramOption.evalOptions;

import com.google.protobuf.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.projectnessie.cel.benchmark.PreparedFixture;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.Booking;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.CloudEvent;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.Device;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.GatewayAddress;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.KubernetesResource;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.Notification;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.OrganizationResource;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.PolicyResource;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures.TraceInterval;
import org.projectnessie.cel.benchmark.RealWorldWorkloads.Family;
import org.projectnessie.cel.benchmark.RealWorldWorkloads.Representation;
import org.projectnessie.cel.benchmark.RealWorldWorkloads.Scenario;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.types.jackson3.Jackson3Registry;

/** The three reusable CEL program modes and direct control for one Boolean scenario. */
public final class RealWorldProgramSet {
  public record ProgramModes(Program exactNative, Program exactDisabled, Program general) {}

  /**
   * Reusable environment and fixture for measuring compilation, planning, and the first evaluation
   * of a newly constructed program.
   *
   * <p>The compilation methods include parsing and checking. Compilation and planning retain an
   * environment because applications normally compile multiple expressions against one configured
   * environment. The {@code cold*()} methods create a fresh environment, checked AST, and program
   * before evaluating it once; they do not represent whole-JVM startup.
   */
  public static final class Lifecycle {
    private final Supplier<Env> exactEnvFactory;
    private final Supplier<Env> generalEnvFactory;
    private final Env exactEnv;
    private final Env generalEnv;
    private final List<String> expressions;
    private final List<Ast> exactAsts;
    private final List<Ast> generalAsts;
    private final Map<String, Object> activation;
    private final Class<?> resultClass;
    private final boolean expected;
    private final String scenarioId;

    private Lifecycle(
        Supplier<Env> exactEnvFactory,
        Supplier<Env> generalEnvFactory,
        List<String> expressions,
        PreparedFixture fixture,
        boolean expected,
        String scenarioId) {
      this.exactEnvFactory = exactEnvFactory;
      this.generalEnvFactory = generalEnvFactory;
      this.exactEnv = exactEnvFactory.get();
      this.generalEnv = generalEnvFactory.get();
      this.expressions = expressions;
      this.exactAsts = compile(exactEnv, expressions);
      this.generalAsts = compile(generalEnv, expressions);
      this.activation = fixture.activation();
      this.resultClass = fixture.resultClass();
      this.expected = expected;
      this.scenarioId = scenarioId;
      verify();
    }

    public List<Ast> compileExact() {
      return compile(exactEnv, expressions);
    }

    public List<Ast> compileGeneral() {
      return compile(generalEnv, expressions);
    }

    public List<Program> planExactNative() {
      return plan(exactEnv, exactAsts, OptOptimize);
    }

    public List<Program> planExactDisabled() {
      return plan(exactEnv, exactAsts, OptOptimize, OptDisableNativeEval);
    }

    public List<Program> planGeneral() {
      return plan(generalEnv, generalAsts, OptOptimize);
    }

    public Boolean coldExactNative() {
      return compilePlanEvaluate(
          exactEnvFactory.get(), expressions, activation, resultClass, OptOptimize);
    }

    public Boolean coldExactDisabled() {
      return compilePlanEvaluate(
          exactEnvFactory.get(),
          expressions,
          activation,
          resultClass,
          OptOptimize,
          OptDisableNativeEval);
    }

    public Boolean coldGeneral() {
      return compilePlanEvaluate(
          generalEnvFactory.get(), expressions, activation, resultClass, OptOptimize);
    }

    private void verify() {
      Boolean nativeResult = coldExactNative();
      Boolean disabledResult = coldExactDisabled();
      Boolean generalResult = coldGeneral();
      if (nativeResult != expected || disabledResult != expected || generalResult != expected) {
        throw new IllegalStateException(
            "Lifecycle scenario "
                + scenarioId
                + " mismatch: expected="
                + expected
                + ", exactNative="
                + nativeResult
                + ", exactDisabled="
                + disabledResult
                + ", general="
                + generalResult);
      }
    }
  }

  private final List<Program> exactNative;
  private final List<Program> exactDisabled;
  private final List<Program> general;
  private final Map<String, Object> activation;
  private final BooleanSupplier direct;
  private final boolean expected;
  private final Class<?> resultClass;
  private final String scenarioId;

  private RealWorldProgramSet(
      List<Program> exactNative,
      List<Program> exactDisabled,
      List<Program> general,
      PreparedFixture fixture,
      boolean expected,
      String scenarioId) {
    this.exactNative = List.copyOf(exactNative);
    this.exactDisabled = List.copyOf(exactDisabled);
    this.general = List.copyOf(general);
    this.activation = fixture.activation();
    this.direct = fixture.direct();
    this.expected = expected;
    this.resultClass = fixture.resultClass();
    this.scenarioId = scenarioId;
    verify();
  }

  public static RealWorldProgramSet host(Scenario scenario, PreparedFixture fixture) {
    if (!scenario.representations().contains(Representation.HOST)) {
      throw new IllegalArgumentException("Not a host scenario " + scenario.id());
    }
    requireResultClass(scenario, fixture);
    Env exactEnv = hostExactEnv(scenario.family());
    Env generalEnv = hostGeneralEnv(scenario.family());
    List<Program> exactNative = new ArrayList<>();
    List<Program> exactDisabled = new ArrayList<>();
    List<Program> general = new ArrayList<>();
    for (String expression : scenario.expressions(Representation.HOST)) {
      Ast exactAst = compile(exactEnv, expression);
      exactNative.add(exactEnv.program(exactAst, evalOptions(OptOptimize)));
      exactDisabled.add(exactEnv.program(exactAst, evalOptions(OptOptimize, OptDisableNativeEval)));
      Ast generalAst = compile(generalEnv, expression);
      general.add(generalEnv.program(generalAst, evalOptions(OptOptimize)));
    }
    return new RealWorldProgramSet(
        exactNative, exactDisabled, general, fixture, scenario.expected(), scenario.id());
  }

  public static ProgramModes hostPrograms(Family family, String expression) {
    Env exactEnv = hostExactEnv(family);
    Ast exactAst = compile(exactEnv, expression);
    Program exactNative = exactEnv.program(exactAst, evalOptions(OptOptimize));
    Program exactDisabled =
        exactEnv.program(exactAst, evalOptions(OptOptimize, OptDisableNativeEval));
    Env generalEnv = hostGeneralEnv(family);
    Program general = generalEnv.program(compile(generalEnv, expression), evalOptions(OptOptimize));
    return new ProgramModes(exactNative, exactDisabled, general);
  }

  public static Lifecycle hostLifecycle(Scenario scenario, PreparedFixture fixture) {
    if (!scenario.representations().contains(Representation.HOST)) {
      throw new IllegalArgumentException("Not a host scenario " + scenario.id());
    }
    requireResultClass(scenario, fixture);
    Supplier<Env> exactEnvFactory = () -> hostExactEnv(scenario.family());
    Supplier<Env> generalEnvFactory = () -> hostGeneralEnv(scenario.family());
    return new Lifecycle(
        exactEnvFactory,
        generalEnvFactory,
        scenario.expressions(Representation.HOST),
        fixture,
        scenario.expected(),
        scenario.id());
  }

  public static RealWorldProgramSet protobuf(
      Scenario scenario, PreparedFixture fixture, Message... registeredTypes) {
    Env exactEnv = protobufEnv(scenario, true, registeredTypes);
    Env generalEnv = protobufEnv(scenario, false, registeredTypes);
    return structured(scenario, fixture, Representation.PROTOBUF, exactEnv, generalEnv);
  }

  public static ProgramModes protobufPrograms(Scenario scenario, Message... registeredTypes) {
    Env exactEnv = protobufEnv(scenario, true, registeredTypes);
    Env generalEnv = protobufEnv(scenario, false, registeredTypes);
    return programModes(scenario.expressions(Representation.PROTOBUF).get(0), exactEnv, generalEnv);
  }

  public static Lifecycle protobufLifecycle(
      Scenario scenario, PreparedFixture fixture, Message... registeredTypes) {
    requireResultClass(scenario, fixture);
    Supplier<Env> exactEnvFactory = () -> protobufEnv(scenario, true, registeredTypes);
    Supplier<Env> generalEnvFactory = () -> protobufEnv(scenario, false, registeredTypes);
    return new Lifecycle(
        exactEnvFactory,
        generalEnvFactory,
        scenario.expressions(Representation.PROTOBUF),
        fixture,
        scenario.expected(),
        scenario.id());
  }

  public static RealWorldProgramSet jackson3(
      Scenario scenario, PreparedFixture fixture, Class<?>... registeredTypes) {
    Env exactEnv = jackson3Env(scenario, true, registeredTypes);
    Env generalEnv = jackson3Env(scenario, false, registeredTypes);
    return structured(scenario, fixture, Representation.JACKSON3, exactEnv, generalEnv);
  }

  public static ProgramModes jackson3Programs(Scenario scenario, Class<?>... registeredTypes) {
    Env exactEnv = jackson3Env(scenario, true, registeredTypes);
    Env generalEnv = jackson3Env(scenario, false, registeredTypes);
    return programModes(scenario.expressions(Representation.JACKSON3).get(0), exactEnv, generalEnv);
  }

  public static Lifecycle jackson3Lifecycle(
      Scenario scenario, PreparedFixture fixture, Class<?>... registeredTypes) {
    requireResultClass(scenario, fixture);
    Supplier<Env> exactEnvFactory = () -> jackson3Env(scenario, true, registeredTypes);
    Supplier<Env> generalEnvFactory = () -> jackson3Env(scenario, false, registeredTypes);
    return new Lifecycle(
        exactEnvFactory,
        generalEnvFactory,
        scenario.expressions(Representation.JACKSON3),
        fixture,
        scenario.expected(),
        scenario.id());
  }

  public Boolean exactNative() {
    return evaluate(exactNative, activation, resultClass);
  }

  public Boolean exactDisabled() {
    return evaluate(exactDisabled, activation, resultClass);
  }

  public Boolean general() {
    return evaluate(general, activation, resultClass);
  }

  public Boolean direct() {
    return direct.getAsBoolean();
  }

  public boolean expected() {
    return expected;
  }

  public static Boolean evaluate(Program program, Map<String, Object> activation) {
    return evaluate(program, activation, Boolean.class);
  }

  private static Boolean evaluate(
      Program program, Map<String, Object> activation, Class<?> resultClass) {
    Program.EvalResult result = program.eval(activation);
    return (Boolean) ((Prog) program).e.adapter.valueToNative(result.getVal(), resultClass);
  }

  private static Boolean evaluate(
      List<Program> programs, Map<String, Object> activation, Class<?> resultClass) {
    if (programs.size() == 1) {
      return evaluate(programs.get(0), activation, resultClass);
    }
    for (Program program : programs) {
      if (evaluate(program, activation, resultClass)) {
        return true;
      }
    }
    return false;
  }

  private void verify() {
    Boolean nativeResult = exactNative();
    Boolean disabledResult = exactDisabled();
    Boolean generalResult = general();
    Boolean directResult = direct();
    if (nativeResult != expected
        || disabledResult != expected
        || generalResult != expected
        || directResult != expected) {
      throw new IllegalStateException(
          "Scenario "
              + scenarioId
              + " mismatch: expected="
              + expected
              + ", exactNative="
              + nativeResult
              + ", exactDisabled="
              + disabledResult
              + ", general="
              + generalResult
              + ", direct="
              + directResult);
    }
  }

  private static RealWorldProgramSet structured(
      Scenario scenario,
      PreparedFixture fixture,
      Representation representation,
      Env exactEnv,
      Env generalEnv) {
    requireResultClass(scenario, fixture);
    List<Program> exactNative = new ArrayList<>();
    List<Program> exactDisabled = new ArrayList<>();
    List<Program> general = new ArrayList<>();
    for (String expression : scenario.expressions(representation)) {
      Ast exactAst = compile(exactEnv, expression);
      exactNative.add(exactEnv.program(exactAst, evalOptions(OptOptimize)));
      exactDisabled.add(exactEnv.program(exactAst, evalOptions(OptOptimize, OptDisableNativeEval)));
      general.add(generalEnv.program(compile(generalEnv, expression), evalOptions(OptOptimize)));
    }
    return new RealWorldProgramSet(
        exactNative, exactDisabled, general, fixture, scenario.expected(), scenario.id());
  }

  private static ProgramModes programModes(String expression, Env exactEnv, Env generalEnv) {
    Ast exactAst = compile(exactEnv, expression);
    return new ProgramModes(
        exactEnv.program(exactAst, evalOptions(OptOptimize)),
        exactEnv.program(exactAst, evalOptions(OptOptimize, OptDisableNativeEval)),
        generalEnv.program(compile(generalEnv, expression), evalOptions(OptOptimize)));
  }

  private static List<Ast> compile(Env env, List<String> expressions) {
    List<Ast> asts = new ArrayList<>(expressions.size());
    for (String expression : expressions) {
      asts.add(compile(env, expression));
    }
    return asts;
  }

  private static List<Program> plan(Env env, List<Ast> asts, EvalOption... options) {
    List<Program> programs = new ArrayList<>(asts.size());
    for (Ast ast : asts) {
      programs.add(env.program(ast, evalOptions(options)));
    }
    return programs;
  }

  private static Boolean compilePlanEvaluate(
      Env env,
      List<String> expressions,
      Map<String, Object> activation,
      Class<?> resultClass,
      EvalOption... options) {
    return evaluate(plan(env, compile(env, expressions), options), activation, resultClass);
  }

  private static Env hostExactEnv(Family family) {
    return newEnv(
        customTypeAdapter(new ExactHostAdapter()), declarations(hostDeclarations(family)));
  }

  private static Env hostGeneralEnv(Family family) {
    return newEnv(
        customTypeAdapter(DefaultTypeAdapter.Instance), declarations(hostDeclarations(family)));
  }

  private static Env protobufEnv(Scenario scenario, boolean exact, Message... registeredTypes) {
    TypeRegistry registry =
        exact
            ? ProtoTypeRegistry.newExactAggregateRegistry(registeredTypes)
            : ProtoTypeRegistry.newRegistry(registeredTypes);
    return newCustomEnv(
        registry,
        List.of(
            Library.StdLib(),
            declarations(structuredDeclarations(scenario.family(), Representation.PROTOBUF))));
  }

  private static Env jackson3Env(Scenario scenario, boolean exact, Class<?>... registeredTypes) {
    TypeRegistry registry =
        exact ? Jackson3Registry.newExactAggregateRegistry() : Jackson3Registry.newRegistry();
    return newEnv(
        customTypeAdapter(registry),
        customTypeProvider(registry),
        types((Object[]) registeredTypes),
        declarations(structuredDeclarations(scenario.family(), Representation.JACKSON3)));
  }

  private static void requireResultClass(Scenario scenario, PreparedFixture fixture) {
    if (!scenario.resultClass().equals(fixture.resultClass())) {
      throw new IllegalArgumentException(
          "Scenario "
              + scenario.id()
              + " declares "
              + scenario.resultClass().getName()
              + " but fixture declares "
              + fixture.resultClass().getName());
    }
  }

  private static com.google.api.expr.v1alpha1.Decl[] hostDeclarations(Family family) {
    return switch (family) {
      case POLARIS ->
          new com.google.api.expr.v1alpha1.Decl[] {
            Decls.newVar("ref", Decls.String),
            Decls.newVar("commits", Decls.Int),
            Decls.newVar("ageMinutes", Decls.Int),
            Decls.newVar("ageHours", Decls.Int),
            Decls.newVar("ageDays", Decls.Int)
          };
      case NESSIE ->
          new com.google.api.expr.v1alpha1.Decl[] {
            Decls.newVar("op", Decls.String),
            Decls.newVar("role", Decls.String),
            Decls.newVar("roles", Decls.newListType(Decls.String)),
            Decls.newVar("ref", Decls.String),
            Decls.newVar("path", Decls.String),
            Decls.newVar("contentType", Decls.String)
          };
      case OPENFGA ->
          new com.google.api.expr.v1alpha1.Decl[] {
            Decls.newVar("current_time", Decls.Timestamp),
            Decls.newVar("grant_time", Decls.Timestamp),
            Decls.newVar("grant_duration", Decls.Duration)
          };
      case DAPR_HOST -> new com.google.api.expr.v1alpha1.Decl[] {Decls.newVar("event", Decls.Dyn)};
      default -> throw new IllegalArgumentException("Not a host family " + family);
    };
  }

  private static com.google.api.expr.v1alpha1.Decl[] structuredDeclarations(
      Family family, Representation representation) {
    return switch (family) {
      case IAM ->
          new com.google.api.expr.v1alpha1.Decl[] {
            Decls.newVar(
                "resource",
                Decls.newObjectType(
                    representation == Representation.PROTOBUF
                        ? org.projectnessie.cel.benchmark.proto.PolicyResource.getDescriptor()
                            .getFullName()
                        : PolicyResource.class.getName()))
          };
      case DRA ->
          new com.google.api.expr.v1alpha1.Decl[] {
            Decls.newVar(
                "device",
                Decls.newObjectType(
                    representation == Representation.PROTOBUF
                        ? org.projectnessie.cel.benchmark.proto.Device.getDescriptor().getFullName()
                        : Device.class.getName()))
          };
      case ORGANIZATION ->
          new com.google.api.expr.v1alpha1.Decl[] {
            Decls.newVar(
                "resource",
                Decls.newObjectType(
                    representation == Representation.PROTOBUF
                        ? org.projectnessie.cel.benchmark.proto.OrganizationResource.getDescriptor()
                            .getFullName()
                        : OrganizationResource.class.getName()))
          };
      case GATEWAY ->
          new com.google.api.expr.v1alpha1.Decl[] {
            Decls.newVar(
                "self",
                Decls.newListType(
                    Decls.newObjectType(
                        representation == Representation.PROTOBUF
                            ? org.projectnessie.cel.benchmark.proto.GatewayAddress.getDescriptor()
                                .getFullName()
                            : GatewayAddress.class.getName())))
          };
      case FLUX ->
          new com.google.api.expr.v1alpha1.Decl[] {
            Decls.newVar(
                "resource",
                Decls.newObjectType(
                    representation == Representation.PROTOBUF
                        ? org.projectnessie.cel.benchmark.proto.KubernetesResource.getDescriptor()
                            .getFullName()
                        : KubernetesResource.class.getName()))
          };
      case DAPR_TYPED ->
          new com.google.api.expr.v1alpha1.Decl[] {
            Decls.newVar(
                "event",
                Decls.newObjectType(
                    representation == Representation.PROTOBUF
                        ? org.projectnessie.cel.benchmark.proto.CloudEvent.getDescriptor()
                            .getFullName()
                        : CloudEvent.class.getName()))
          };
      case PROTOVALIDATE_BOOKING ->
          new com.google.api.expr.v1alpha1.Decl[] {
            Decls.newVar(
                "this",
                Decls.newObjectType(
                    representation == Representation.PROTOBUF
                        ? org.projectnessie.cel.benchmark.proto.Booking.getDescriptor()
                            .getFullName()
                        : Booking.class.getName()))
          };
      case PROTOVALIDATE_INTERVAL ->
          new com.google.api.expr.v1alpha1.Decl[] {
            Decls.newVar(
                "this",
                Decls.newObjectType(
                    representation == Representation.PROTOBUF
                        ? org.projectnessie.cel.benchmark.proto.TraceInterval.getDescriptor()
                            .getFullName()
                        : TraceInterval.class.getName()))
          };
      case PROTOVALIDATE_NOTIFICATION ->
          new com.google.api.expr.v1alpha1.Decl[] {
            Decls.newVar(
                "this",
                Decls.newObjectType(
                    representation == Representation.PROTOBUF
                        ? org.projectnessie.cel.benchmark.proto.Notification.getDescriptor()
                            .getFullName()
                        : Notification.class.getName()))
          };
      default -> throw new IllegalArgumentException("Not yet a structured family " + family);
    };
  }

  private static Ast compile(Env env, String expression) {
    Env.AstIssuesTuple result = env.compile(expression);
    if (result.hasIssues()) {
      throw new IllegalStateException(expression + ": " + result.getIssues());
    }
    return result.getAst();
  }

  private static final class ExactHostAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }
}
