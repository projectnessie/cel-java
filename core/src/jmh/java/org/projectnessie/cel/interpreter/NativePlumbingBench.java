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
package org.projectnessie.cel.interpreter;

import static org.projectnessie.cel.CEL.astToCheckedExpr;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.checker.Decls.Int;
import static org.projectnessie.cel.checker.Decls.String;
import static org.projectnessie.cel.checker.Decls.newFunction;
import static org.projectnessie.cel.checker.Decls.newListType;
import static org.projectnessie.cel.checker.Decls.newMapType;
import static org.projectnessie.cel.checker.Decls.newOverload;
import static org.projectnessie.cel.checker.Decls.newVar;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
import org.projectnessie.cel.Ast;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.functions.Overload;

/** Measures the integrated typed-capability plumbing independently of production selection. */
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class NativePlumbingBench {
  @State(Scope.Benchmark)
  public static class ScalarState {
    @Param({"1", "4", "16"})
    int depth;

    Interpretable established;
    NativeIsland island;
    NativeIntCapability semantic;
    Program integrated;
    Program establishedProgram;
    Activation activation;
    Map<String, Object> variables;

    @SuppressWarnings("StringConcatenationInLoop")
    @Setup
    public void setup() {
      Env env = newEnv(declarations(newVar("x", Int)));
      String expression = "x";
      for (int i = 0; i < depth; i++) {
        expression = "(" + expression + " + " + (i + 1) + ")";
      }
      Ast ast = compile(env, expression);
      established = plan(env, ast, false);
      island = (NativeIsland) plan(env, ast, true);
      semantic = (NativeIntCapability) island.root();
      integrated = env.program(ast);
      establishedProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      variables = Map.of("x", 50_021L);
      activation = newActivation(variables);
    }
  }

  @State(Scope.Benchmark)
  public static class MixedState {
    Interpretable established;
    NativeIsland island;
    NativeBooleanCapability semantic;
    Activation activation;

    @Setup
    public void setup() {
      Env env = newEnv(declarations(newVar("x", Int), newVar("y", Int)));
      Ast ast = compile(env, "(x + 1) == y");
      established = plan(env, ast, false);
      island = (NativeIsland) plan(env, ast, true);
      semantic = (NativeBooleanCapability) island.root();
      activation = newActivation(Map.of("x", 50_021L, "y", 50_022L));
    }
  }

  @State(Scope.Benchmark)
  public static class EstablishedOuterState {
    @Param({"1", "2"})
    int islands;

    Interpretable established;
    Interpretable integrated;
    Activation activation;

    @Setup
    public void setup() {
      Overload implementation;
      Env env;
      Ast ast;
      if (islands == 1) {
        env =
            newEnv(
                declarations(
                    newVar("x", Int),
                    newFunction("opaque", newOverload("opaque_int", java.util.List.of(Int), Int))));
        ast = compile(env, "opaque(x + 1)");
        implementation = Overload.unary("opaque_int", value -> value);
      } else {
        env =
            newEnv(
                declarations(
                    newVar("x", Int),
                    newVar("y", Int),
                    newFunction(
                        "opaque2", newOverload("opaque2_int", java.util.List.of(Int, Int), Int))));
        ast = compile(env, "opaque2(x + 1, y + 1)");
        implementation = Overload.binary("opaque2_int", (left, right) -> left);
      }
      established = plan(env, ast, false, implementation);
      integrated = plan(env, ast, true, implementation);
      activation = newActivation(Map.of("x", 50_021L, "y", 50_022L));
    }
  }

  @State(Scope.Benchmark)
  public static class SelectorState {
    Interpretable established;
    NativeIsland island;
    NativeIntCapability semantic;
    Activation activation;

    @Setup
    public void setup() {
      Env env = newEnv(declarations(newVar("attrs", newMapType(String, Int))));
      Ast ast = compile(env, "attrs.answer + 1");
      established = plan(env, ast, false);
      island = (NativeIsland) plan(env, ast, true);
      semantic = (NativeIntCapability) island.root();
      activation = newActivation(Map.of("attrs", Map.of("answer", 50_021L)));
    }
  }

  @State(Scope.Benchmark)
  public static class ListIndexState {
    Interpretable established;
    NativeIsland island;
    NativeIntCapability semantic;
    Activation activation;

    @Setup
    public void setup() {
      Env env = newEnv(declarations(newVar("numbers", newListType(Int))));
      Ast ast = compile(env, "numbers[1] + 1");
      established = plan(env, ast, false);
      island = (NativeIsland) plan(env, ast, true);
      semantic = (NativeIntCapability) island.root();
      activation = newActivation(Map.of("numbers", new long[] {50_020L, 50_021L}));
    }
  }

  @State(Scope.Benchmark)
  public static class AggregateScalarRootState {
    @Param({"mapSelectorInt", "mapSelectorString", "mapIndexInt", "mapIndexString"})
    String shape;

    Interpretable established;
    Interpretable integrated;
    Program establishedProgram;
    Program integratedProgram;
    Activation activation;
    Map<String, Object> variables;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() {
      Env env;
      String expression;
      Object input;
      switch (shape) {
        case "mapSelectorInt":
          env = newEnv(declarations(newVar("attrs", newMapType(String, Int))));
          expression = "attrs.answer";
          input = Map.of("answer", 50_021L);
          variables = Map.of("attrs", input);
          break;
        case "mapSelectorString":
          env = newEnv(declarations(newVar("attrs", newMapType(String, String))));
          expression = "attrs.answer";
          input = Map.of("answer", "native-plumbing");
          variables = Map.of("attrs", input);
          break;
        case "mapIndexInt":
          env = newEnv(declarations(newVar("attrs", newMapType(String, Int))));
          expression = "attrs['answer']";
          input = Map.of("answer", 50_021L);
          variables = Map.of("attrs", input);
          break;
        case "mapIndexString":
          env = newEnv(declarations(newVar("attrs", newMapType(String, String))));
          expression = "attrs['answer']";
          input = Map.of("answer", "native-plumbing");
          variables = Map.of("attrs", input);
          break;
        default:
          throw new IllegalArgumentException("unknown aggregate scalar root shape " + shape);
      }
      Ast ast = compile(env, expression);
      established = plan(env, ast, false);
      integrated = plan(env, ast, true);
      establishedProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      integratedProgram = env.program(ast);
      activation = newActivation(variables);
    }
  }

  @State(Scope.Benchmark)
  public static class ConstructionState {
    @Param({"1", "4", "16"})
    int depth;

    Env env;
    Ast ast;

    @SuppressWarnings("StringConcatenationInLoop")
    @Setup
    public void setup() {
      env = newEnv(declarations(newVar("x", Int)));
      String expression = "x";
      for (int i = 0; i < depth; i++) {
        expression = "(" + expression + " + " + (i + 1) + ")";
      }
      ast = compile(env, expression);
    }
  }

  @State(Scope.Benchmark)
  public static class ScalarRootState {
    @Param({"int", "double", "string", "null"})
    String kind;

    Interpretable established;
    Interpretable integrated;
    Program establishedProgram;
    Program integratedProgram;
    Activation activation;
    Map<String, Object> variables;
    Env env;
    Ast ast;

    @Setup
    public void setup() {
      var declaration =
          switch (kind) {
            case "int" -> newVar("value", Int);
            case "double" -> newVar("value", org.projectnessie.cel.checker.Decls.Double);
            case "string" -> newVar("value", String);
            case "null" -> newVar("value", org.projectnessie.cel.checker.Decls.Null);
            default -> throw new IllegalArgumentException("unknown scalar root kind " + kind);
          };
      Object value =
          switch (kind) {
            case "int" -> 50_021L;
            case "double" -> 50_021.5d;
            case "string" -> "value";
            case "null" -> null;
            default -> throw new IllegalArgumentException("unknown scalar root kind " + kind);
          };
      variables = new HashMap<>();
      variables.put("value", value);
      env = newEnv(declarations(declaration));
      ast = compile(env, "value");
      established = plan(env, ast, false);
      integrated = plan(env, ast, true);
      establishedProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      integratedProgram = env.program(ast);
      activation = newActivation(variables);
    }
  }

  @State(Scope.Benchmark)
  public static class StrictScalarState {
    @Param({"intArithmetic", "doubleArithmetic", "stringConcat", "scalarComparison"})
    String shape;

    Interpretable established;
    Interpretable integrated;
    Program establishedProgram;
    Program integratedProgram;
    Activation activation;
    Map<String, Object> variables;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() {
      Env env;
      String expression;
      switch (shape) {
        case "intArithmetic":
          env = newEnv(declarations(newVar("left", Int), newVar("right", Int)));
          expression = "(left + 1) * (right - 2)";
          variables = Map.of("left", 50_021L, "right", 42L);
          break;
        case "doubleArithmetic":
          env =
              newEnv(
                  declarations(
                      newVar("left", org.projectnessie.cel.checker.Decls.Double),
                      newVar("right", org.projectnessie.cel.checker.Decls.Double)));
          expression = "(left + 1.5) / (right - 2.0)";
          variables = Map.of("left", 50_021.5d, "right", 42.0d);
          break;
        case "stringConcat":
          env = newEnv(declarations(newVar("left", String), newVar("right", String)));
          expression = "left + right";
          variables = Map.of("left", "native", "right", "-plumbing");
          break;
        case "scalarComparison":
          env = newEnv(declarations(newVar("left", String), newVar("right", String)));
          expression = "left < right";
          variables = Map.of("left", "native", "right", "wrapped");
          break;
        default:
          throw new IllegalArgumentException("unknown strict scalar shape " + shape);
      }
      Ast ast = compile(env, expression);
      established = plan(env, ast, false);
      integrated = plan(env, ast, true);
      establishedProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      integratedProgram = env.program(ast);
      activation = newActivation(variables);
    }
  }

  @State(Scope.Benchmark)
  public static class ScalarControlState {
    @Param({"equality", "logical", "conditional"})
    String shape;

    Interpretable established;
    Interpretable integrated;
    Program establishedProgram;
    Program integratedProgram;
    Activation activation;
    Map<String, Object> variables;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() {
      Env env;
      String expression;
      switch (shape) {
        case "equality":
          env = newEnv(declarations(newVar("x", Int), newVar("y", Int)));
          expression = "(x + 1) == y";
          variables = Map.of("x", 50_021L, "y", 50_022L);
          break;
        case "logical":
          env = newEnv(declarations(newVar("x", Int), newVar("y", Int), newVar("z", Int)));
          expression = "(x < y) && (y < z)";
          variables = Map.of("x", 50_021L, "y", 50_022L, "z", 50_023L);
          break;
        case "conditional":
          env =
              newEnv(
                  declarations(
                      newVar("condition", org.projectnessie.cel.checker.Decls.Bool),
                      newVar("x", Int),
                      newVar("y", Int)));
          expression = "condition ? x + 1 : y - 1";
          variables = Map.of("condition", true, "x", 50_021L, "y", 50_023L);
          break;
        default:
          throw new IllegalArgumentException("unknown scalar control shape " + shape);
      }
      Ast ast = compile(env, expression);
      established = plan(env, ast, false);
      integrated = plan(env, ast, true);
      establishedProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      integratedProgram = env.program(ast);
      activation = newActivation(variables);
    }
  }

  @Benchmark
  public Val establishedEval(ScalarState state) {
    return state.established.eval(state.activation);
  }

  @Benchmark
  public Val dualNodeEstablishedEval(ScalarState state) {
    return state.island.root().eval(state.activation);
  }

  @Benchmark
  public long typedInt(ScalarState state) {
    return state.semantic.evalInt(state.activation);
  }

  @Benchmark
  public Val islandEval(ScalarState state) {
    return state.island.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult integratedProgramBoundary(ScalarState state) {
    return state.integrated.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult establishedProgramBoundary(ScalarState state) {
    return state.establishedProgram.eval(state.variables);
  }

  @Benchmark
  public Val selectorEstablished(SelectorState state) {
    return state.established.eval(state.activation);
  }

  @Benchmark
  public long selectorTyped(SelectorState state) {
    return state.semantic.evalInt(state.activation);
  }

  @Benchmark
  public Val selectorDualNodeEstablished(SelectorState state) {
    return state.island.root().eval(state.activation);
  }

  @Benchmark
  public Val selectorIsland(SelectorState state) {
    return state.island.eval(state.activation);
  }

  @Benchmark
  public Val listIndexEstablished(ListIndexState state) {
    return state.established.eval(state.activation);
  }

  @Benchmark
  public long listIndexTyped(ListIndexState state) {
    return state.semantic.evalInt(state.activation);
  }

  @Benchmark
  public Val listIndexDualNodeEstablished(ListIndexState state) {
    return state.island.root().eval(state.activation);
  }

  @Benchmark
  public Val listIndexIsland(ListIndexState state) {
    return state.island.eval(state.activation);
  }

  @Benchmark
  public Val aggregateScalarRootEstablished(AggregateScalarRootState state) {
    return state.established.eval(state.activation);
  }

  @Benchmark
  public Val aggregateScalarRootIntegrated(AggregateScalarRootState state) {
    return state.integrated.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult aggregateScalarRootEstablishedProgram(AggregateScalarRootState state) {
    return state.establishedProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult aggregateScalarRootIntegratedProgram(AggregateScalarRootState state) {
    return state.integratedProgram.eval(state.variables);
  }

  @Benchmark
  public Val mixedEstablished(MixedState state) {
    return state.established.eval(state.activation);
  }

  @Benchmark
  public boolean mixedTyped(MixedState state) {
    return state.semantic.evalBoolean(state.activation);
  }

  @Benchmark
  public Val mixedIsland(MixedState state) {
    return state.island.eval(state.activation);
  }

  @Benchmark
  public Val establishedOuter(EstablishedOuterState state) {
    return state.established.eval(state.activation);
  }

  @Benchmark
  public Val establishedOuterWithIslands(EstablishedOuterState state) {
    return state.integrated.eval(state.activation);
  }

  @Benchmark
  public Interpretable constructEstablished(ConstructionState state) {
    return plan(state.env, state.ast, false);
  }

  @Benchmark
  public Interpretable constructIntegrated(ConstructionState state) {
    return plan(state.env, state.ast, true);
  }

  @Benchmark
  public Val scalarRootEstablished(ScalarRootState state) {
    return state.established.eval(state.activation);
  }

  @Benchmark
  public Val scalarRootIntegrated(ScalarRootState state) {
    return state.integrated.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult scalarRootEstablishedProgram(ScalarRootState state) {
    return state.establishedProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult scalarRootIntegratedProgram(ScalarRootState state) {
    return state.integratedProgram.eval(state.variables);
  }

  @Benchmark
  public Program constructScalarRootEstablishedProgram(ScalarRootState state) {
    return state.env.program(state.ast, evalOptions(OptDisableNativeEval));
  }

  @Benchmark
  public Program constructScalarRootIntegratedProgram(ScalarRootState state) {
    return state.env.program(state.ast);
  }

  @Benchmark
  public Val strictScalarEstablished(StrictScalarState state) {
    return state.established.eval(state.activation);
  }

  @Benchmark
  public Val strictScalarIntegrated(StrictScalarState state) {
    return state.integrated.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult strictScalarEstablishedProgram(StrictScalarState state) {
    return state.establishedProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult strictScalarIntegratedProgram(StrictScalarState state) {
    return state.integratedProgram.eval(state.variables);
  }

  @Benchmark
  public Val scalarControlEstablished(ScalarControlState state) {
    return state.established.eval(state.activation);
  }

  @Benchmark
  public Val scalarControlIntegrated(ScalarControlState state) {
    return state.integrated.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult scalarControlEstablishedProgram(ScalarControlState state) {
    return state.establishedProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult scalarControlIntegratedProgram(ScalarControlState state) {
    return state.integratedProgram.eval(state.variables);
  }

  private static Interpretable plan(Env env, Ast ast, boolean nativePlanning) {
    return plan(env, ast, nativePlanning, new Overload[0]);
  }

  private static Interpretable plan(
      Env env, Ast ast, boolean nativePlanning, Overload... customOverloads) {
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    dispatcher.add(customOverloads);
    AttributeFactory attributes =
        newAttributeFactory(defaultContainer, env.getTypeAdapter(), env.getTypeProvider());
    Interpreter interpreter =
        newInterpreter(
            dispatcher,
            defaultContainer,
            env.getTypeProvider(),
            env.getTypeAdapter(),
            attributes,
            nativePlanning);
    var checked = astToCheckedExpr(ast);
    return nativePlanning
        ? ((ExprInterpreter) interpreter).checkedPlanner(checked).plan(checked.getExpr())
        : interpreter.newInterpretable(checked);
  }

  private static Ast compile(Env env, String expression) {
    AstIssuesTuple result = env.compile(expression);
    if (result.hasIssues()) {
      throw Objects.requireNonNull(result.getIssues().err());
    }
    return result.getAst();
  }
}
