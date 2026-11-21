/*
 * Copyright (C) 2021 The Authors of CEL-Java
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

import static java.util.Objects.requireNonNull;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.interpreter.Activation.newActivation;

import com.google.protobuf.DynamicMessage;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.ArrayList;
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
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.interpreter.Activation;
import org.projectnessie.cel.interpreter.Interpretable;
import org.projectnessie.cel.interpreter.NativeCapabilityBenchmark;

/** Measures production native scalar plans against the existing evaluator and program boundary. */
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class NativePlanBench {
  @State(Scope.Benchmark)
  public abstract static class ScalarState {
    @Param({"1", "4", "16"})
    int depth;

    Env env;
    Ast ast;
    Prog nativeProgram;
    NativeCapabilityBenchmark nativePlan;
    Program currentProgram;
    Interpretable currentPlan;
    Activation activation;
    Map<String, Object> variables;

    final void setup(TypeFixture fixture) {
      env = newEnv(declarations(Decls.newVar("x", fixture.type)));
      ast = compile(env, expression(fixture));
      nativeProgram = (Prog) env.program(ast);
      nativePlan = requireNative(nativeProgram);
      currentProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      currentPlan = ((Prog) currentProgram).interpretable;
      variables = Map.of("x", fixture.value);
      activation = newActivation(variables);
      assertEquivalent(nativePlan.eval(activation), currentPlan.eval(activation));
    }

    private String expression(TypeFixture fixture) {
      String source = "x";
      for (int i = 0; i < depth; i++) {
        source = fixture.next(source, i);
      }
      return source;
    }
  }

  @State(Scope.Benchmark)
  public static class IntState extends ScalarState {
    @Setup
    public void setup() {
      setup(
          new TypeFixture(Decls.Int, 50_021L) {
            @Override
            String next(String source, int level) {
              return switch (level & 3) {
                case 0 -> "(" + source + " + 17)";
                case 1 -> "(" + source + " * 3)";
                case 2 -> "(" + source + " - 11)";
                default -> "(" + source + " / 2)";
              };
            }
          });
    }
  }

  @State(Scope.Benchmark)
  public static class DoubleState extends ScalarState {
    @Setup
    public void setup() {
      setup(
          new TypeFixture(Decls.Double, 50_021.25d) {
            @Override
            String next(String source, int level) {
              return switch (level & 3) {
                case 0 -> "(" + source + " + 17.25)";
                case 1 -> "(" + source + " * 1.5)";
                case 2 -> "(" + source + " - 11.5)";
                default -> "(" + source + " / 2.0)";
              };
            }
          });
    }
  }

  @State(Scope.Benchmark)
  public static class ConstructionState {
    @Param({
      "smallNative",
      "deepNative",
      "unsupportedRoot",
      "unsupportedLate",
      "literal16",
      "literal1024"
    })
    String shape;

    Env env;
    Ast ast;
    Map<String, Object> variables;

    @Setup
    public void setup() {
      env = newEnv(declarations(Decls.newVar("x", Decls.Int)));
      String source =
          switch (shape) {
            case "smallNative" -> "x + 17";
            case "deepNative" -> deepExpression();
            case "unsupportedRoot" -> "[x][0]";
            case "unsupportedLate" -> deepExpression() + " + [x][0]";
            case "literal16" -> listLiteralIndexExpression(16);
            case "literal1024" -> listLiteralIndexExpression(1024);
            default -> throw new IllegalArgumentException("unknown construction shape " + shape);
          };
      ast = compile(env, source);
      variables = Map.of("x", 50_021L);
    }
  }

  @State(Scope.Benchmark)
  public static class MapSelectorState {
    @Param({"dot", "constantIndex"})
    String shape;

    NativeCapabilityBenchmark comparisonPlan;
    NativeCapabilityBenchmark terminalPlan;
    Interpretable currentComparison;
    Interpretable currentTerminal;
    Program nativeProgram;
    Program currentProgram;
    Activation activation;
    Map<String, Object> variables;
    Map<String, Long> values;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() {
      Env env =
          newEnv(
              declarations(
                  Decls.newVar("attrs", Decls.newMapType(Decls.String, Decls.Int)),
                  Decls.newVar("key", Decls.String),
                  Decls.newVar("target", Decls.Int)));
      String selection =
          switch (shape) {
            case "dot" -> "attrs.answer";
            case "constantIndex" -> "attrs['answer']";
            default -> throw new IllegalArgumentException("unknown map selector shape " + shape);
          };
      Ast comparisonAst = compile(env, selection + " == target");
      Ast terminalAst = compile(env, selection);
      Prog comparisonProgram = (Prog) env.program(comparisonAst);
      Prog terminalProgram = (Prog) env.program(terminalAst);
      comparisonPlan = requireNative(comparisonProgram);
      terminalPlan = requireNative(terminalProgram);
      currentComparison = currentPlan(comparisonProgram, comparisonAst);
      currentTerminal = currentPlan(terminalProgram, terminalAst);
      nativeProgram = comparisonProgram;
      currentProgram = env.program(comparisonAst, evalOptions(OptDisableNativeEval));
      values = Map.of("answer", 50_021L);
      variables = Map.of("attrs", values, "key", "answer", "target", 50_021L);
      activation = newActivation(variables);
      assertEquivalent(comparisonPlan.eval(activation), currentComparison.eval(activation));
      assertEquivalent(terminalPlan.eval(activation), currentTerminal.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class ProtoSelectorState {
    @Param({"generated", "dynamic"})
    String representation;

    NativeCapabilityBenchmark comparisonPlan;
    NativeCapabilityBenchmark terminalPlan;
    Interpretable currentComparison;
    Interpretable currentTerminal;
    Program nativeProgram;
    Program currentProgram;
    Activation activation;
    Map<String, Object> variables;
    TestAllTypes generated;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() throws Exception {
      generated = TestAllTypes.newBuilder().setSingleInt64(50_021L).build();
      Object message =
          representation.equals("generated")
              ? generated
              : DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), generated.toByteString());
      Env env =
          newEnv(
              types(TestAllTypes.getDefaultInstance()),
              declarations(
                  Decls.newVar(
                      "msg", Decls.newObjectType(TestAllTypes.getDescriptor().getFullName())),
                  Decls.newVar("target", Decls.Int)));
      Ast comparisonAst = compile(env, "msg.single_int64 == target");
      Ast terminalAst = compile(env, "msg.single_int64");
      Prog comparisonProgram = (Prog) env.program(comparisonAst);
      Prog terminalProgram = (Prog) env.program(terminalAst);
      comparisonPlan = requireNative(comparisonProgram);
      terminalPlan = requireNative(terminalProgram);
      currentComparison = currentPlan(comparisonProgram, comparisonAst);
      currentTerminal = currentPlan(terminalProgram, terminalAst);
      nativeProgram = comparisonProgram;
      currentProgram = env.program(comparisonAst, evalOptions(OptDisableNativeEval));
      variables = Map.of("msg", message, "target", 50_021L);
      activation = newActivation(variables);
      assertEquivalent(comparisonPlan.eval(activation), currentComparison.eval(activation));
      assertEquivalent(terminalPlan.eval(activation), currentTerminal.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class ProtoStringSelectorState {
    @Param({"generated", "dynamic"})
    String representation;

    NativeCapabilityBenchmark comparisonPlan;
    Interpretable currentComparison;
    Program nativeProgram;
    Program currentProgram;
    Activation activation;
    Map<String, Object> variables;

    @Setup
    public void setup() throws Exception {
      TestAllTypes generated =
          TestAllTypes.newBuilder().setSingleString("native-selector-value").build();
      Object message =
          representation.equals("generated")
              ? generated
              : DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), generated.toByteString());
      Env env =
          newEnv(
              types(TestAllTypes.getDefaultInstance()),
              declarations(
                  Decls.newVar(
                      "msg", Decls.newObjectType(TestAllTypes.getDescriptor().getFullName())),
                  Decls.newVar("target", Decls.String)));
      Ast ast = compile(env, "msg.single_string == target");
      Prog program = (Prog) env.program(ast);
      comparisonPlan = requireNative(program);
      currentComparison = currentPlan(program, ast);
      nativeProgram = program;
      currentProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      variables = Map.of("msg", message, "target", "native-selector-value");
      activation = newActivation(variables);
      assertEquivalent(comparisonPlan.eval(activation), currentComparison.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class ListIndexState {
    @Param({"longArray", "javaList", "adaptedList"})
    String representation;

    @Param({"16", "1024"})
    int size;

    int index;
    long target;
    Object values;
    NativeCapabilityBenchmark comparisonPlan;
    NativeCapabilityBenchmark terminalPlan;
    Interpretable currentComparison;
    Interpretable currentTerminal;
    Program nativeProgram;
    Program currentProgram;
    Activation activation;
    Map<String, Object> variables;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() {
      index = size / 2;
      target = 50_021L;
      long[] longValues = new long[size];
      List<Long> javaValues = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        long value = i == index ? target : i;
        longValues[i] = value;
        javaValues.add(value);
      }
      values =
          switch (representation) {
            case "longArray" -> longValues;
            case "javaList" -> List.copyOf(javaValues);
            case "adaptedList" -> DefaultTypeAdapter.Instance.nativeToValue(javaValues);
            default ->
                throw new IllegalArgumentException("unknown list representation " + representation);
          };

      Env env =
          newEnv(
              declarations(
                  Decls.newVar("numbers", Decls.newListType(Decls.Int)),
                  Decls.newVar("target", Decls.Int)));
      String selection = "numbers[" + index + "]";
      Ast comparisonAst = compile(env, selection + " == target");
      Ast terminalAst = compile(env, selection);
      Prog comparisonProgram = (Prog) env.program(comparisonAst);
      Prog terminalProgram = (Prog) env.program(terminalAst);
      comparisonPlan = requireNative(comparisonProgram);
      terminalPlan = requireNative(terminalProgram);
      currentComparison = currentPlan(comparisonProgram, comparisonAst);
      currentTerminal = currentPlan(terminalProgram, terminalAst);
      nativeProgram = comparisonProgram;
      currentProgram = env.program(comparisonAst, evalOptions(OptDisableNativeEval));
      variables = Map.of("numbers", values, "target", target);
      activation = newActivation(variables);
      assertEquivalent(comparisonPlan.eval(activation), currentComparison.eval(activation));
      assertEquivalent(terminalPlan.eval(activation), currentTerminal.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class StringListIndexState {
    @Param({"stringArray", "javaList", "adaptedList"})
    String representation;

    @Param({"16", "1024"})
    int size;

    int index;
    String target;
    Object values;
    NativeCapabilityBenchmark comparisonPlan;
    NativeCapabilityBenchmark terminalPlan;
    Interpretable currentComparison;
    Interpretable currentTerminal;
    Program nativeProgram;
    Program currentProgram;
    Activation activation;
    Map<String, Object> variables;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() {
      index = size / 2;
      String[] stringValues = new String[size];
      List<String> javaValues = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        String value = "value-" + i;
        stringValues[i] = value;
        javaValues.add(value);
      }
      target = stringValues[index];
      values =
          switch (representation) {
            case "stringArray" -> stringValues;
            case "javaList" -> List.copyOf(javaValues);
            case "adaptedList" -> DefaultTypeAdapter.Instance.nativeToValue(javaValues);
            default ->
                throw new IllegalArgumentException("unknown list representation " + representation);
          };

      Env env =
          newEnv(
              declarations(
                  Decls.newVar("words", Decls.newListType(Decls.String)),
                  Decls.newVar("target", Decls.String)));
      String selection = "words[" + index + "]";
      Ast comparisonAst = compile(env, selection + " == target");
      Ast terminalAst = compile(env, selection);
      Prog comparisonProgram = (Prog) env.program(comparisonAst);
      Prog terminalProgram = (Prog) env.program(terminalAst);
      comparisonPlan = requireNative(comparisonProgram);
      terminalPlan = requireNative(terminalProgram);
      currentComparison = currentPlan(comparisonProgram, comparisonAst);
      currentTerminal = currentPlan(terminalProgram, terminalAst);
      nativeProgram = comparisonProgram;
      currentProgram = env.program(comparisonAst, evalOptions(OptDisableNativeEval));
      variables = Map.of("words", values, "target", target);
      activation = newActivation(variables);
      assertEquivalent(comparisonPlan.eval(activation), currentComparison.eval(activation));
      assertEquivalent(terminalPlan.eval(activation), currentTerminal.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class StringListMembershipState {
    @Param({"stringArray", "javaList", "adaptedList"})
    String representation;

    @Param({"16", "1024"})
    int size;

    @Param({"first", "middle", "absent"})
    String position;

    String needle;
    Object values;
    NativeCapabilityBenchmark nativePlan;
    Interpretable currentPlan;
    Program nativeProgram;
    Program currentProgram;
    Activation activation;
    Map<String, Object> variables;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() {
      String[] stringValues = new String[size];
      List<String> javaValues = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        String value = "value-" + i;
        stringValues[i] = value;
        javaValues.add(value);
      }
      needle =
          switch (position) {
            case "first" -> stringValues[0];
            case "middle" -> stringValues[size / 2];
            case "absent" -> "absent";
            default -> throw new IllegalArgumentException("unknown match position " + position);
          };
      values =
          switch (representation) {
            case "stringArray" -> stringValues;
            case "javaList" -> List.copyOf(javaValues);
            case "adaptedList" -> DefaultTypeAdapter.Instance.nativeToValue(javaValues);
            default ->
                throw new IllegalArgumentException("unknown list representation " + representation);
          };

      Env env =
          newEnv(
              declarations(
                  Decls.newVar("needle", Decls.String),
                  Decls.newVar("values", Decls.newListType(Decls.String))));
      Ast ast = compile(env, "needle in values");
      Prog program = (Prog) env.program(ast);
      nativePlan = requireNative(program);
      currentPlan = currentPlan(program, ast);
      nativeProgram = program;
      currentProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      variables = Map.of("needle", needle, "values", values);
      activation = newActivation(variables);
      assertEquivalent(nativePlan.eval(activation), currentPlan.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class MappedStringMembershipState {
    @Param({"unfilteredIdentity", "unfilteredComputed", "filteredComputed", "filter"})
    String shape;

    @Param({"16", "1024"})
    int size;

    @Param({"first", "middle", "absent"})
    String position;

    String needle;
    String[] values;
    String suffix;
    String excluded;
    NativeCapabilityBenchmark nativePlan;
    Interpretable currentPlan;
    Program nativeProgram;
    Program currentProgram;
    Activation activation;
    Map<String, Object> variables;

    @Setup
    public void setup() {
      values = new String[size];
      for (int i = 0; i < size; i++) {
        values[i] = "value-" + i;
      }
      suffix = "-mapped";
      excluded = values[size - 1];
      boolean filtered = shape.equals("filteredComputed") || shape.equals("filter");
      int acceptedSize = filtered ? size - 1 : size;
      int matchIndex =
          switch (position) {
            case "first" -> 0;
            case "middle" -> acceptedSize / 2;
            case "absent" -> -1;
            default -> throw new IllegalArgumentException("unknown match position " + position);
          };
      boolean computed = shape.equals("unfilteredComputed") || shape.equals("filteredComputed");
      needle = matchIndex == -1 ? "absent" : values[matchIndex] + (computed ? suffix : "");

      Env env =
          newEnv(
              declarations(
                  Decls.newVar("needle", Decls.String),
                  Decls.newVar("values", Decls.newListType(Decls.String)),
                  Decls.newVar("suffix", Decls.String),
                  Decls.newVar("excluded", Decls.String)));
      String expression =
          switch (shape) {
            case "unfilteredIdentity" -> "needle in values.map(value, value)";
            case "unfilteredComputed" -> "needle in values.map(value, value + suffix)";
            case "filteredComputed" ->
                "needle in values.map(value, value != excluded, value + suffix)";
            case "filter" -> "needle in values.filter(value, value != excluded)";
            default ->
                throw new IllegalArgumentException("unknown mapped membership shape " + shape);
          };
      Ast ast = compile(env, expression);
      Prog program = (Prog) env.program(ast);
      nativePlan = requireNative(program);
      currentPlan = currentPlan(program, ast);
      nativeProgram = program;
      currentProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      variables =
          Map.of("needle", needle, "values", values, "suffix", suffix, "excluded", excluded);
      activation = newActivation(variables);
      assertEquivalent(nativePlan.eval(activation), currentPlan.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class QuantifierState {
    @Param({"exists", "all", "existsOne"})
    String quantifier;

    @Param({"longArray", "stringArray"})
    String representation;

    @Param({"16", "1024"})
    int size;

    @Param({"first", "last", "absent", "twice"})
    String position;

    Object values;
    Object target;
    NativeCapabilityBenchmark nativePlan;
    Interpretable currentPlan;
    Program nativeProgram;
    Program currentProgram;
    Activation activation;
    Map<String, Object> variables;

    @Setup
    public void setup() {
      int targetIndex =
          switch (position) {
            case "first", "twice" -> 0;
            case "last" -> size - 1;
            case "absent" -> -1;
            default -> throw new IllegalArgumentException("unknown match position " + position);
          };
      Env env;
      String expression;
      if (representation.equals("longArray")) {
        long[] longValues = new long[size];
        for (int i = 0; i < size; i++) {
          longValues[i] = i;
        }
        if (position.equals("twice")) {
          longValues[size - 1] = 0L;
        }
        values = longValues;
        target = targetIndex >= 0 ? (long) targetIndex : -1L;
        env =
            newEnv(
                declarations(
                    Decls.newVar("values", Decls.newListType(Decls.Int)),
                    Decls.newVar("target", Decls.Int)));
        expression = "values";
      } else if (representation.equals("stringArray")) {
        String[] stringValues = new String[size];
        for (int i = 0; i < size; i++) {
          stringValues[i] = "value-" + i;
        }
        if (position.equals("twice")) {
          stringValues[size - 1] = stringValues[0];
        }
        values = stringValues;
        target = targetIndex >= 0 ? stringValues[targetIndex] : "absent";
        env =
            newEnv(
                declarations(
                    Decls.newVar("values", Decls.newListType(Decls.String)),
                    Decls.newVar("target", Decls.String)));
        expression = "values";
      } else {
        throw new IllegalArgumentException("unknown list representation " + representation);
      }
      expression +=
          switch (quantifier) {
            case "exists" -> ".exists(value, value == target)";
            case "all" -> ".all(value, value != target)";
            case "existsOne" -> ".exists_one(value, value == target)";
            default -> throw new IllegalArgumentException("unknown quantifier " + quantifier);
          };

      Ast ast = compile(env, expression);
      Prog program = (Prog) env.program(ast);
      nativePlan = requireNative(program);
      currentPlan = currentPlan(program, ast);
      nativeProgram = program;
      currentProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      variables = Map.of("values", values, "target", target);
      activation = newActivation(variables);
      assertEquivalent(nativePlan.eval(activation), currentPlan.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class MapAggregateState {
    @Param({"exists", "all", "existsOne"})
    String quantifier;

    @Param({"unfiltered", "filteredNone", "filteredHalf", "filteredAlmostAll"})
    String shape;

    @Param({"identity", "computed"})
    String transformation;

    @Param({"16", "1024"})
    int size;

    @Param({"first", "last", "absent", "twice"})
    String position;

    long[] values;
    long argument;
    long excluded;
    long target;
    NativeCapabilityBenchmark nativePlan;
    Interpretable currentPlan;
    Program nativeProgram;
    Program currentProgram;
    Activation activation;
    Map<String, Object> variables;

    @Setup
    public void setup() {
      values = new long[size];
      for (int i = 0; i < size; i++) {
        values[i] = i;
      }
      argument = 17L;
      excluded = values[size / 2];
      int targetIndex =
          switch (position) {
            case "first" -> 0;
            case "last" -> shape.equals("filteredHalf") ? size - 2 : size - 1;
            case "absent" -> -1;
            case "twice" -> {
              values[size - 1] = values[0];
              yield 0;
            }
            default -> throw new IllegalArgumentException("unknown match position " + position);
          };
      target =
          targetIndex >= 0
              ? values[targetIndex] + (transformation.equals("computed") ? argument : 0L)
              : -1L;

      Env env =
          newEnv(
              declarations(
                  Decls.newVar("values", Decls.newListType(Decls.Int)),
                  Decls.newVar("argument", Decls.Int),
                  Decls.newVar("excluded", Decls.Int),
                  Decls.newVar("target", Decls.Int)));
      String transform = transformation.equals("identity") ? "value" : "value + argument";
      String aggregate =
          switch (quantifier) {
            case "exists" -> ".exists(mapped, mapped == target)";
            case "all" -> ".all(mapped, mapped != target)";
            case "existsOne" -> ".exists_one(mapped, mapped == target)";
            default -> throw new IllegalArgumentException("unknown quantifier " + quantifier);
          };
      String mapping =
          switch (shape) {
            case "unfiltered" -> "values.map(value, " + transform + ")";
            case "filteredNone" ->
                transformation.equals("identity")
                    ? "values.filter(value, false)"
                    : "values.map(value, false, " + transform + ")";
            case "filteredHalf" ->
                transformation.equals("identity")
                    ? "values.filter(value, value % 2 == 0)"
                    : "values.map(value, value % 2 == 0, " + transform + ")";
            case "filteredAlmostAll" ->
                transformation.equals("identity")
                    ? "values.filter(value, value != excluded)"
                    : "values.map(value, value != excluded, " + transform + ")";
            default ->
                throw new IllegalArgumentException("unknown mapped aggregate shape " + shape);
          };
      Ast ast = compile(env, mapping + aggregate);
      Prog program = (Prog) env.program(ast);
      nativePlan = requireNative(program);
      currentPlan = currentPlan(program, ast);
      nativeProgram = program;
      currentProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      variables =
          Map.of("values", values, "argument", argument, "excluded", excluded, "target", target);
      activation = newActivation(variables);
      assertEquivalent(nativePlan.eval(activation), currentPlan.eval(activation));
    }

    boolean accepts(long value) {
      return switch (shape) {
        case "unfiltered" -> true;
        case "filteredNone" -> false;
        case "filteredHalf" -> value % 2 == 0;
        case "filteredAlmostAll" -> value != excluded;
        default -> throw new IllegalArgumentException("unknown mapped aggregate shape " + shape);
      };
    }
  }

  @State(Scope.Benchmark)
  public static class FilterSizeState {
    @Param({"longArray", "stringArray"})
    String representation;

    @Param({"16", "1024"})
    int size;

    @Param({"first", "last", "absent"})
    String position;

    Object values;
    Object target;
    NativeCapabilityBenchmark nativePlan;
    Interpretable currentPlan;
    Program nativeProgram;
    Program currentProgram;
    Activation activation;
    Map<String, Object> variables;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() {
      int targetIndex =
          switch (position) {
            case "first" -> 0;
            case "last" -> size - 1;
            case "absent" -> -1;
            default -> throw new IllegalArgumentException("unknown match position " + position);
          };
      Env env;
      if (representation.equals("longArray")) {
        long[] longValues = new long[size];
        for (int i = 0; i < size; i++) {
          longValues[i] = i;
        }
        values = longValues;
        target = targetIndex >= 0 ? (long) targetIndex : -1L;
        env =
            newEnv(
                declarations(
                    Decls.newVar("values", Decls.newListType(Decls.Int)),
                    Decls.newVar("target", Decls.Int)));
      } else if (representation.equals("stringArray")) {
        String[] stringValues = new String[size];
        for (int i = 0; i < size; i++) {
          stringValues[i] = "value-" + i;
        }
        values = stringValues;
        target = targetIndex >= 0 ? stringValues[targetIndex] : "absent";
        env =
            newEnv(
                declarations(
                    Decls.newVar("values", Decls.newListType(Decls.String)),
                    Decls.newVar("target", Decls.String)));
      } else {
        throw new IllegalArgumentException("unknown list representation " + representation);
      }

      Ast ast = compile(env, "size(values.filter(value, value == target))");
      Prog program = (Prog) env.program(ast);
      nativePlan = requireNative(program);
      currentPlan = currentPlan(program, ast);
      nativeProgram = program;
      currentProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      variables = Map.of("values", values, "target", target);
      activation = newActivation(variables);
      assertEquivalent(nativePlan.eval(activation), currentPlan.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class MapSizeState {
    @Param({"longArray", "stringArray"})
    String representation;

    @Param({"16", "1024"})
    int size;

    @Param({"identity", "computed"})
    String transformation;

    @Param({"unfiltered", "filtered"})
    String mapping;

    Object values;
    Object argument;
    Object excluded;
    NativeCapabilityBenchmark nativePlan;
    Interpretable currentPlan;
    Program nativeProgram;
    Program currentProgram;
    Activation activation;
    Map<String, Object> variables;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() {
      Env env;
      if (representation.equals("longArray")) {
        long[] longValues = new long[size];
        for (int i = 0; i < size; i++) {
          longValues[i] = i;
        }
        values = longValues;
        argument = 17L;
        excluded = (long) size - 1L;
        env =
            newEnv(
                declarations(
                    Decls.newVar("values", Decls.newListType(Decls.Int)),
                    Decls.newVar("argument", Decls.Int),
                    Decls.newVar("excluded", Decls.Int)));
      } else if (representation.equals("stringArray")) {
        String[] stringValues = new String[size];
        for (int i = 0; i < size; i++) {
          stringValues[i] = "value-" + i;
        }
        values = stringValues;
        argument = "-mapped";
        excluded = stringValues[size - 1];
        env =
            newEnv(
                declarations(
                    Decls.newVar("values", Decls.newListType(Decls.String)),
                    Decls.newVar("argument", Decls.String),
                    Decls.newVar("excluded", Decls.String)));
      } else {
        throw new IllegalArgumentException("unknown list representation " + representation);
      }

      String transform = transformation.equals("identity") ? "value" : "value + argument";
      String expression =
          switch (mapping) {
            case "unfiltered" -> "size(values.map(value, " + transform + "))";
            case "filtered" -> "size(values.map(value, value != excluded, " + transform + "))";
            default -> throw new IllegalArgumentException("unknown mapping " + mapping);
          };
      Ast ast = compile(env, expression);
      Prog program = (Prog) env.program(ast);
      nativePlan = requireNative(program);
      currentPlan = currentPlan(program, ast);
      nativeProgram = program;
      currentProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      variables =
          mapping.equals("filtered")
              ? Map.of("values", values, "argument", argument, "excluded", excluded)
              : Map.of("values", values, "argument", argument);
      activation = newActivation(variables);
      assertEquivalent(nativePlan.eval(activation), currentPlan.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class MapIndexState {
    @Param({"longArray", "stringArray"})
    String representation;

    @Param({"16", "1024"})
    int size;

    @Param({"identity", "computed"})
    String transformation;

    @Param({"unfiltered", "filtered"})
    String mapping;

    @Param({"first", "middle", "last"})
    String position;

    int index;
    Object values;
    Object argument;
    Object excluded;
    Object target;
    NativeCapabilityBenchmark nativePlan;
    Interpretable currentPlan;
    Program nativeProgram;
    Program currentProgram;
    Activation activation;
    Map<String, Object> variables;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() {
      int acceptedSize =
          switch (mapping) {
            case "unfiltered" -> size;
            case "filtered" -> size - 1;
            default -> throw new IllegalArgumentException("unknown mapping " + mapping);
          };
      index =
          switch (position) {
            case "first" -> 0;
            case "middle" -> acceptedSize / 2;
            case "last" -> acceptedSize - 1;
            default -> throw new IllegalArgumentException("unknown index position " + position);
          };

      Env env;
      if (representation.equals("longArray")) {
        long[] longValues = new long[size];
        for (int i = 0; i < size; i++) {
          longValues[i] = i;
        }
        values = longValues;
        argument = 17L;
        excluded = (long) size - 1L;
        target =
            transformation.equals("identity")
                ? longValues[index]
                : Math.addExact(longValues[index], (Long) argument);
        env =
            newEnv(
                declarations(
                    Decls.newVar("values", Decls.newListType(Decls.Int)),
                    Decls.newVar("argument", Decls.Int),
                    Decls.newVar("excluded", Decls.Int),
                    Decls.newVar("target", Decls.Int)));
      } else if (representation.equals("stringArray")) {
        String[] stringValues = new String[size];
        for (int i = 0; i < size; i++) {
          stringValues[i] = "value-" + i;
        }
        values = stringValues;
        argument = "-mapped";
        excluded = stringValues[size - 1];
        target =
            transformation.equals("identity")
                ? stringValues[index]
                : stringValues[index] + argument;
        env =
            newEnv(
                declarations(
                    Decls.newVar("values", Decls.newListType(Decls.String)),
                    Decls.newVar("argument", Decls.String),
                    Decls.newVar("excluded", Decls.String),
                    Decls.newVar("target", Decls.String)));
      } else {
        throw new IllegalArgumentException("unknown list representation " + representation);
      }

      String transform = transformation.equals("identity") ? "value" : "value + argument";
      String expression =
          switch (mapping) {
            case "unfiltered" -> "values.map(value, " + transform + ")[" + index + "] == target";
            case "filtered" ->
                "values.map(value, value != excluded, " + transform + ")[" + index + "] == target";
            default -> throw new IllegalArgumentException("unknown mapping " + mapping);
          };
      Ast ast = compile(env, expression);
      Prog program = (Prog) env.program(ast);
      nativePlan = requireNative(program);
      currentPlan = currentPlan(program, ast);
      nativeProgram = program;
      currentProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      variables =
          mapping.equals("filtered")
              ? Map.of(
                  "values", values, "argument", argument, "excluded", excluded, "target", target)
              : Map.of("values", values, "argument", argument, "target", target);
      activation = newActivation(variables);
      assertEquivalent(nativePlan.eval(activation), currentPlan.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class StringListLiteralMembershipState {
    @Param({"constant", "computed"})
    String shape;

    @Param({"16", "1024"})
    int size;

    @Param({"first", "middle", "absent"})
    String position;

    String needle;
    String fallback;
    String[] values;
    boolean condition;
    NativeCapabilityBenchmark nativePlan;
    Interpretable currentPlan;
    Program nativeProgram;
    Program currentProgram;
    Activation activation;
    Map<String, Object> variables;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() {
      values = new String[size];
      fallback = "fallback";
      condition = true;
      StringBuilder literal = new StringBuilder("[");
      for (int i = 0; i < size; i++) {
        if (i > 0) {
          literal.append(',');
        }
        String value = "value-" + i;
        values[i] = value;
        if (shape.equals("constant")) {
          literal.append('\'').append(value).append('\'');
        } else if (shape.equals("computed")) {
          literal.append("(condition?'").append(value).append("':fallback)");
        } else {
          throw new IllegalArgumentException("unknown list literal shape " + shape);
        }
      }
      literal.append(']');
      needle =
          switch (position) {
            case "first" -> values[0];
            case "middle" -> values[size / 2];
            case "absent" -> "absent";
            default -> throw new IllegalArgumentException("unknown match position " + position);
          };

      Env env =
          newEnv(
              declarations(
                  Decls.newVar("needle", Decls.String),
                  Decls.newVar("condition", Decls.Bool),
                  Decls.newVar("fallback", Decls.String)));
      Ast ast = compile(env, "needle in " + literal);
      Prog program = (Prog) env.program(ast);
      nativePlan = requireNative(program);
      currentPlan = currentPlan(program, ast);
      nativeProgram = program;
      currentProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      variables = Map.of("needle", needle, "condition", condition, "fallback", fallback);
      activation = newActivation(variables);
      assertEquivalent(nativePlan.eval(activation), currentPlan.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class BoolListLiteralState {
    @Param({"constant", "computed"})
    String shape;

    @Param({"16", "1024"})
    int size;

    int index;
    boolean fallback;
    boolean[] values;
    boolean condition;
    NativeCapabilityBenchmark nativeIndexPlan;
    NativeCapabilityBenchmark nativeSizePlan;
    Interpretable currentIndexPlan;
    Interpretable currentSizePlan;
    Program nativeIndexProgram;
    Program nativeSizeProgram;
    Program currentIndexProgram;
    Program currentSizeProgram;
    Activation activation;
    Map<String, Object> variables;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() {
      index = size / 2;
      values = new boolean[size];
      fallback = false;
      condition = true;
      StringBuilder literal = new StringBuilder("[");
      for (int i = 0; i < size; i++) {
        if (i > 0) {
          literal.append(',');
        }
        boolean value = (i & 1) == 0;
        values[i] = value;
        if (shape.equals("constant")) {
          literal.append(value);
        } else if (shape.equals("computed")) {
          literal.append("(condition?").append(value).append(":fallback)");
        } else {
          throw new IllegalArgumentException("unknown list literal shape " + shape);
        }
      }
      literal.append(']');

      Env env =
          newEnv(
              declarations(
                  Decls.newVar("condition", Decls.Bool), Decls.newVar("fallback", Decls.Bool)));
      Ast indexAst = compile(env, literal + "[" + index + "]");
      Ast sizeAst = compile(env, "size(" + literal + ")");
      Prog indexProgram = (Prog) env.program(indexAst);
      Prog sizeProgram = (Prog) env.program(sizeAst);
      nativeIndexPlan = requireNative(indexProgram);
      nativeSizePlan = requireNative(sizeProgram);
      currentIndexPlan = currentPlan(indexProgram, indexAst);
      currentSizePlan = currentPlan(sizeProgram, sizeAst);
      nativeIndexProgram = indexProgram;
      nativeSizeProgram = sizeProgram;
      currentIndexProgram = env.program(indexAst, evalOptions(OptDisableNativeEval));
      currentSizeProgram = env.program(sizeAst, evalOptions(OptDisableNativeEval));
      variables = Map.of("condition", condition, "fallback", fallback);
      activation = newActivation(variables);
      assertEquivalent(nativeIndexPlan.eval(activation), currentIndexPlan.eval(activation));
      assertEquivalent(nativeSizePlan.eval(activation), currentSizePlan.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class StringListLiteralState {
    @Param({"constant", "computed"})
    String shape;

    @Param({"16", "1024"})
    int size;

    int index;
    String fallback;
    String[] values;
    boolean condition;
    NativeCapabilityBenchmark nativeIndexPlan;
    NativeCapabilityBenchmark nativeSizePlan;
    Interpretable currentIndexPlan;
    Interpretable currentSizePlan;
    Program nativeIndexProgram;
    Program nativeSizeProgram;
    Program currentIndexProgram;
    Program currentSizeProgram;
    Activation activation;
    Map<String, Object> variables;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() {
      index = size / 2;
      values = new String[size];
      fallback = "fallback";
      condition = true;
      StringBuilder literal = new StringBuilder("[");
      for (int i = 0; i < size; i++) {
        if (i > 0) {
          literal.append(',');
        }
        String value = "value-" + i;
        values[i] = value;
        if (shape.equals("constant")) {
          literal.append('\'').append(value).append('\'');
        } else if (shape.equals("computed")) {
          literal.append("(condition?'").append(value).append("':fallback)");
        } else {
          throw new IllegalArgumentException("unknown list literal shape " + shape);
        }
      }
      literal.append(']');

      Env env =
          newEnv(
              declarations(
                  Decls.newVar("condition", Decls.Bool), Decls.newVar("fallback", Decls.String)));
      Ast indexAst = compile(env, literal + "[" + index + "]");
      Ast sizeAst = compile(env, "size(" + literal + ")");
      Prog indexProgram = (Prog) env.program(indexAst);
      Prog sizeProgram = (Prog) env.program(sizeAst);
      nativeIndexPlan = requireNative(indexProgram);
      nativeSizePlan = requireNative(sizeProgram);
      currentIndexPlan = currentPlan(indexProgram, indexAst);
      currentSizePlan = currentPlan(sizeProgram, sizeAst);
      nativeIndexProgram = indexProgram;
      nativeSizeProgram = sizeProgram;
      currentIndexProgram = env.program(indexAst, evalOptions(OptDisableNativeEval));
      currentSizeProgram = env.program(sizeAst, evalOptions(OptDisableNativeEval));
      variables = Map.of("condition", condition, "fallback", fallback);
      activation = newActivation(variables);
      assertEquivalent(nativeIndexPlan.eval(activation), currentIndexPlan.eval(activation));
      assertEquivalent(nativeSizePlan.eval(activation), currentSizePlan.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class IntListLiteralState {
    @Param({"constant", "computed"})
    String shape;

    @Param({"16", "1024"})
    int size;

    int index;
    long x;
    long target;
    NativeCapabilityBenchmark nativePlan;
    NativeCapabilityBenchmark nativeSizePlan;
    Interpretable currentPlan;
    Interpretable currentSizePlan;
    Interpretable currentTerminal;
    Program nativeProgram;
    Program nativeSizeProgram;
    Program currentProgram;
    Program currentSizeProgram;
    Program currentTerminalProgram;
    Activation activation;
    Map<String, Object> variables;

    @Setup
    public void setup() {
      index = size / 2;
      x = 50_021L;
      StringBuilder literal = new StringBuilder("[");
      for (int i = 0; i < size; i++) {
        if (i > 0) {
          literal.append(',');
        }
        if (shape.equals("constant")) {
          literal.append(50_021L + i);
        } else if (shape.equals("computed")) {
          literal.append("x+").append(i);
        } else {
          throw new IllegalArgumentException("unknown list literal shape " + shape);
        }
      }
      literal.append(']');
      target = 50_021L + index;

      Env env =
          newEnv(declarations(Decls.newVar("x", Decls.Int), Decls.newVar("target", Decls.Int)));
      Ast ast = compile(env, literal + "[" + index + "] == target");
      Ast sizeAst = compile(env, "size(" + literal + ")");
      Ast terminalAst = compile(env, literal.toString());
      Prog program = (Prog) env.program(ast);
      Prog sizeProgram = (Prog) env.program(sizeAst);
      nativePlan = requireNative(program);
      nativeSizePlan = requireNative(sizeProgram);
      currentPlan = currentPlan(program, ast);
      currentSizePlan = currentPlan(sizeProgram, sizeAst);
      currentTerminal = currentPlan(program, terminalAst);
      nativeProgram = program;
      nativeSizeProgram = sizeProgram;
      currentProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      currentSizeProgram = env.program(sizeAst, evalOptions(OptDisableNativeEval));
      currentTerminalProgram = env.program(terminalAst, evalOptions(OptDisableNativeEval));
      variables = Map.of("x", x, "target", target);
      activation = newActivation(variables);
      assertEquivalent(nativePlan.eval(activation), currentPlan.eval(activation));
      assertEquivalent(nativeSizePlan.eval(activation), currentSizePlan.eval(activation));
    }
  }

  @State(Scope.Benchmark)
  public static class DoubleListLiteralState {
    @Param({"constant", "computed"})
    String shape;

    @Param({"16", "1024"})
    int size;

    int index;
    double d;
    NativeCapabilityBenchmark nativeIndexPlan;
    NativeCapabilityBenchmark nativeSizePlan;
    Interpretable currentIndexPlan;
    Interpretable currentSizePlan;
    Program nativeIndexProgram;
    Program nativeSizeProgram;
    Program currentIndexProgram;
    Program currentSizeProgram;
    Activation activation;
    Map<String, Object> variables;

    @SuppressWarnings("DuplicatedCode")
    @Setup
    public void setup() {
      index = size / 2;
      d = 50_021.25d;
      StringBuilder literal = new StringBuilder("[");
      for (int i = 0; i < size; i++) {
        if (i > 0) {
          literal.append(',');
        }
        if (shape.equals("constant")) {
          literal.append(d + i);
        } else if (shape.equals("computed")) {
          literal.append("d+").append(i).append(".0");
        } else {
          throw new IllegalArgumentException("unknown list literal shape " + shape);
        }
      }
      literal.append(']');

      Env env = newEnv(declarations(Decls.newVar("d", Decls.Double)));
      Ast indexAst = compile(env, literal + "[" + index + "]");
      Ast sizeAst = compile(env, "size(" + literal + ")");
      Prog indexProgram = (Prog) env.program(indexAst);
      Prog sizeProgram = (Prog) env.program(sizeAst);
      nativeIndexPlan = requireNative(indexProgram);
      nativeSizePlan = requireNative(sizeProgram);
      currentIndexPlan = currentPlan(indexProgram, indexAst);
      currentSizePlan = currentPlan(sizeProgram, sizeAst);
      nativeIndexProgram = indexProgram;
      nativeSizeProgram = sizeProgram;
      currentIndexProgram = env.program(indexAst, evalOptions(OptDisableNativeEval));
      currentSizeProgram = env.program(sizeAst, evalOptions(OptDisableNativeEval));
      variables = Map.of("d", d);
      activation = newActivation(variables);
      assertEquivalent(nativeIndexPlan.eval(activation), currentIndexPlan.eval(activation));
      assertEquivalent(nativeSizePlan.eval(activation), currentSizePlan.eval(activation));
    }
  }

  @Benchmark
  public long intNativePrimitive(IntState state) {
    return state.nativePlan.evalInt(state.activation);
  }

  @Benchmark
  public Val intNativeFinalVal(IntState state) {
    return state.nativePlan.eval(state.activation);
  }

  @Benchmark
  public Val intCurrentRaw(IntState state) {
    return state.currentPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult intNativeProgram(IntState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult intCurrentProgram(IntState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public double doubleNativePrimitive(DoubleState state) {
    return state.nativePlan.evalDouble(state.activation);
  }

  @Benchmark
  public Val doubleNativeFinalVal(DoubleState state) {
    return state.nativePlan.eval(state.activation);
  }

  @Benchmark
  public Val doubleCurrentRaw(DoubleState state) {
    return state.currentPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult doubleNativeProgram(DoubleState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult doubleCurrentProgram(DoubleState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public boolean mapSelectorNativePrimitive(MapSelectorState state) {
    return state.comparisonPlan.evalBoolean(state.activation);
  }

  @Benchmark
  public long mapSelectorTerminalPrimitive(MapSelectorState state) {
    return state.terminalPlan.evalInt(state.activation);
  }

  @Benchmark
  public Val mapSelectorNativeFinalVal(MapSelectorState state) {
    return state.comparisonPlan.eval(state.activation);
  }

  @Benchmark
  public Val mapSelectorCurrentRaw(MapSelectorState state) {
    return state.currentComparison.eval(state.activation);
  }

  @Benchmark
  public Val mapSelectorTerminalCurrentRaw(MapSelectorState state) {
    return state.currentTerminal.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult mapSelectorNativeProgram(MapSelectorState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult mapSelectorCurrentProgram(MapSelectorState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public boolean mapSelectorJava(MapSelectorState state) {
    return state.values.get("answer") == 50_021L;
  }

  @Benchmark
  public boolean protoSelectorNativePrimitive(ProtoSelectorState state) {
    return state.comparisonPlan.evalBoolean(state.activation);
  }

  @Benchmark
  public long protoSelectorTerminalPrimitive(ProtoSelectorState state) {
    return state.terminalPlan.evalInt(state.activation);
  }

  @Benchmark
  public Val protoSelectorNativeFinalVal(ProtoSelectorState state) {
    return state.comparisonPlan.eval(state.activation);
  }

  @Benchmark
  public Val protoSelectorCurrentRaw(ProtoSelectorState state) {
    return state.currentComparison.eval(state.activation);
  }

  @Benchmark
  public Val protoSelectorTerminalCurrentRaw(ProtoSelectorState state) {
    return state.currentTerminal.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult protoSelectorNativeProgram(ProtoSelectorState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult protoSelectorCurrentProgram(ProtoSelectorState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public boolean protoSelectorJava(ProtoSelectorState state) {
    return state.generated.getSingleInt64() == 50_021L;
  }

  @Benchmark
  public boolean protoStringSelectorNativePrimitive(ProtoStringSelectorState state) {
    return state.comparisonPlan.evalBoolean(state.activation);
  }

  @Benchmark
  public Val protoStringSelectorCurrentRaw(ProtoStringSelectorState state) {
    return state.currentComparison.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult protoStringSelectorNativeProgram(ProtoStringSelectorState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult protoStringSelectorCurrentProgram(ProtoStringSelectorState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public boolean listIndexNativePrimitive(ListIndexState state) {
    return state.comparisonPlan.evalBoolean(state.activation);
  }

  @Benchmark
  public long listIndexTerminalPrimitive(ListIndexState state) {
    return state.terminalPlan.evalInt(state.activation);
  }

  @Benchmark
  public Val listIndexNativeFinalVal(ListIndexState state) {
    return state.comparisonPlan.eval(state.activation);
  }

  @Benchmark
  public Val listIndexCurrentRaw(ListIndexState state) {
    return state.currentComparison.eval(state.activation);
  }

  @Benchmark
  public Val listIndexTerminalCurrentRaw(ListIndexState state) {
    return state.currentTerminal.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult listIndexNativeProgram(ListIndexState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult listIndexCurrentProgram(ListIndexState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public boolean listIndexJava(ListIndexState state) {
    long value =
        switch (state.representation) {
          case "longArray" -> ((long[]) state.values)[state.index];
          case "javaList" ->
              ((List<?>) state.values).get(state.index) instanceof Long element
                  ? element
                  : Long.MIN_VALUE;
          case "adaptedList" -> ((Lister) state.values).nativeGetAt(state.index).intValue();
          default -> throw new IllegalArgumentException(state.representation);
        };
    return value == state.target;
  }

  @Benchmark
  public boolean stringListIndexNativePrimitive(StringListIndexState state) {
    return state.comparisonPlan.evalBoolean(state.activation);
  }

  @Benchmark
  public String stringListIndexTerminalPrimitive(StringListIndexState state) {
    return state.terminalPlan.evalString(state.activation);
  }

  @Benchmark
  public Val stringListIndexNativeFinalVal(StringListIndexState state) {
    return state.comparisonPlan.eval(state.activation);
  }

  @Benchmark
  public Val stringListIndexCurrentRaw(StringListIndexState state) {
    return state.currentComparison.eval(state.activation);
  }

  @Benchmark
  public Val stringListIndexTerminalCurrentRaw(StringListIndexState state) {
    return state.currentTerminal.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult stringListIndexNativeProgram(StringListIndexState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult stringListIndexCurrentProgram(StringListIndexState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public boolean stringListIndexJava(StringListIndexState state) {
    String value =
        switch (state.representation) {
          case "stringArray" -> ((String[]) state.values)[state.index];
          case "javaList" -> (String) ((List<?>) state.values).get(state.index);
          case "adaptedList" -> (String) ((Lister) state.values).nativeGetAt(state.index).value();
          default -> throw new IllegalArgumentException(state.representation);
        };
    return value.equals(state.target);
  }

  @Benchmark
  public boolean stringListMembershipNativePrimitive(StringListMembershipState state) {
    return state.nativePlan.evalBoolean(state.activation);
  }

  @Benchmark
  public Val stringListMembershipNativeFinalVal(StringListMembershipState state) {
    return state.nativePlan.eval(state.activation);
  }

  @Benchmark
  public Val stringListMembershipCurrentRaw(StringListMembershipState state) {
    return state.currentPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult stringListMembershipNativeProgram(StringListMembershipState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult stringListMembershipCurrentProgram(StringListMembershipState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public boolean stringListMembershipJava(StringListMembershipState state) {
    for (int i = 0; i < state.size; i++) {
      String value =
          switch (state.representation) {
            case "stringArray" -> ((String[]) state.values)[i];
            case "javaList" -> (String) ((List<?>) state.values).get(i);
            case "adaptedList" -> (String) ((Lister) state.values).nativeGetAt(i).value();
            default -> throw new IllegalArgumentException(state.representation);
          };
      if (state.needle.equals(value)) {
        return true;
      }
    }
    return false;
  }

  @Benchmark
  public boolean mappedStringMembershipNativePrimitive(MappedStringMembershipState state) {
    return state.nativePlan.evalBoolean(state.activation);
  }

  @Benchmark
  public Val mappedStringMembershipNativeFinalVal(MappedStringMembershipState state) {
    return state.nativePlan.eval(state.activation);
  }

  @Benchmark
  public Val mappedStringMembershipCurrentRaw(MappedStringMembershipState state) {
    return state.currentPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult mappedStringMembershipNativeProgram(MappedStringMembershipState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult mappedStringMembershipCurrentProgram(
      MappedStringMembershipState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public boolean mappedStringMembershipJava(MappedStringMembershipState state) {
    boolean filtered = state.shape.equals("filteredComputed") || state.shape.equals("filter");
    boolean computed =
        state.shape.equals("unfilteredComputed") || state.shape.equals("filteredComputed");
    boolean match = false;
    long checksum = 0L;
    for (String source : state.values) {
      if (filtered && source.equals(state.excluded)) {
        continue;
      }
      String value = computed ? source + state.suffix : source;
      checksum ^= value.hashCode();
      if (state.needle.equals(value)) {
        match = true;
      }
    }
    return match != (checksum == Long.MIN_VALUE);
  }

  @Benchmark
  public boolean quantifierNativePrimitive(QuantifierState state) {
    return state.nativePlan.evalBoolean(state.activation);
  }

  @Benchmark
  public Val quantifierNativeFinalVal(QuantifierState state) {
    return state.nativePlan.eval(state.activation);
  }

  @Benchmark
  public Val quantifierCurrentRaw(QuantifierState state) {
    return state.currentPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult quantifierNativeProgram(QuantifierState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult quantifierCurrentProgram(QuantifierState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public boolean quantifierJava(QuantifierState state) {
    int matches = 0;
    if (state.values instanceof long[] longValues) {
      long target = (Long) state.target;
      for (long value : longValues) {
        if (value == target) {
          if (state.quantifier.equals("exists")) {
            return true;
          }
          if (state.quantifier.equals("all")) {
            return false;
          }
          matches++;
        }
      }
    } else {
      String target = (String) state.target;
      for (String value : (String[]) state.values) {
        if (value.equals(target)) {
          if (state.quantifier.equals("exists")) {
            return true;
          }
          if (state.quantifier.equals("all")) {
            return false;
          }
          matches++;
        }
      }
    }
    return switch (state.quantifier) {
      case "exists" -> false;
      case "all" -> true;
      case "existsOne" -> matches == 1;
      default -> throw new IllegalArgumentException("unknown quantifier " + state.quantifier);
    };
  }

  @Benchmark
  public boolean mapAggregateNativePrimitive(MapAggregateState state) {
    return state.nativePlan.evalBoolean(state.activation);
  }

  @Benchmark
  public Val mapAggregateNativeFinalVal(MapAggregateState state) {
    return state.nativePlan.eval(state.activation);
  }

  @Benchmark
  public Val mapAggregateCurrentRaw(MapAggregateState state) {
    return state.currentPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult mapAggregateNativeProgram(MapAggregateState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult mapAggregateCurrentProgram(MapAggregateState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public boolean mapAggregateJava(MapAggregateState state) {
    int accepted = 0;
    for (long value : state.values) {
      if (state.accepts(value)) {
        accepted++;
      }
    }
    long[] mapped = new long[accepted];
    int mappedSize = 0;
    for (long value : state.values) {
      if (!state.accepts(value)) {
        continue;
      }
      mapped[mappedSize++] =
          state.transformation.equals("identity") ? value : Math.addExact(value, state.argument);
    }
    int matches = 0;
    for (long value : mapped) {
      if (value == state.target) {
        if (state.quantifier.equals("exists")) {
          return true;
        }
        if (state.quantifier.equals("all")) {
          return false;
        }
        matches++;
      }
    }
    return switch (state.quantifier) {
      case "exists" -> false;
      case "all" -> true;
      case "existsOne" -> matches == 1;
      default -> throw new IllegalArgumentException("unknown quantifier " + state.quantifier);
    };
  }

  @Benchmark
  public long filterSizeNativePrimitive(FilterSizeState state) {
    return state.nativePlan.evalInt(state.activation);
  }

  @Benchmark
  public Val filterSizeNativeFinalVal(FilterSizeState state) {
    return state.nativePlan.eval(state.activation);
  }

  @Benchmark
  public Val filterSizeCurrentRaw(FilterSizeState state) {
    return state.currentPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult filterSizeNativeProgram(FilterSizeState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult filterSizeCurrentProgram(FilterSizeState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public long filterSizeJava(FilterSizeState state) {
    long matches = 0L;
    if (state.values instanceof long[] longValues) {
      long target = (Long) state.target;
      for (long value : longValues) {
        if (value == target) {
          matches++;
        }
      }
    } else {
      String target = (String) state.target;
      for (String value : (String[]) state.values) {
        if (value.equals(target)) {
          matches++;
        }
      }
    }
    return matches;
  }

  @Benchmark
  public long mapSizeNativePrimitive(MapSizeState state) {
    return state.nativePlan.evalInt(state.activation);
  }

  @Benchmark
  public Val mapSizeNativeFinalVal(MapSizeState state) {
    return state.nativePlan.eval(state.activation);
  }

  @Benchmark
  public Val mapSizeCurrentRaw(MapSizeState state) {
    return state.currentPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult mapSizeNativeProgram(MapSizeState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult mapSizeCurrentProgram(MapSizeState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public long mapSizeJava(MapSizeState state) {
    long transformed = 0L;
    long accepted = 0L;
    if (state.values instanceof long[] longValues) {
      long excluded = (Long) state.excluded;
      if (state.transformation.equals("identity")) {
        for (long value : longValues) {
          if (state.mapping.equals("unfiltered") || value != excluded) {
            transformed ^= value;
            accepted++;
          }
        }
      } else {
        long argument = (Long) state.argument;
        for (long value : longValues) {
          if (state.mapping.equals("unfiltered") || value != excluded) {
            transformed ^= Math.addExact(value, argument);
            accepted++;
          }
        }
      }
    } else {
      String excluded = (String) state.excluded;
      if (state.transformation.equals("identity")) {
        for (String value : (String[]) state.values) {
          if (state.mapping.equals("unfiltered") || !value.equals(excluded)) {
            transformed ^= value.hashCode();
            accepted++;
          }
        }
      } else {
        String argument = (String) state.argument;
        for (String value : (String[]) state.values) {
          if (state.mapping.equals("unfiltered") || !value.equals(excluded)) {
            transformed ^= (value + argument).hashCode();
            accepted++;
          }
        }
      }
    }
    return accepted + (transformed == Long.MIN_VALUE ? 1 : 0);
  }

  @Benchmark
  public boolean mapIndexNativePrimitive(MapIndexState state) {
    return state.nativePlan.evalBoolean(state.activation);
  }

  @Benchmark
  public Val mapIndexNativeFinalVal(MapIndexState state) {
    return state.nativePlan.eval(state.activation);
  }

  @Benchmark
  public Val mapIndexCurrentRaw(MapIndexState state) {
    return state.currentPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult mapIndexNativeProgram(MapIndexState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult mapIndexCurrentProgram(MapIndexState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public boolean mapIndexJava(MapIndexState state) {
    long checksum = 0L;
    boolean equal;
    if (state.values instanceof long[] longValues) {
      long selected = 0L;
      long argument = (Long) state.argument;
      long excluded = (Long) state.excluded;
      int accepted = 0;
      for (long longValue : longValues) {
        if (state.mapping.equals("filtered") && longValue == excluded) {
          continue;
        }
        long value =
            state.transformation.equals("identity")
                ? longValue
                : Math.addExact(longValue, argument);
        checksum ^= value;
        if (accepted == state.index) {
          selected = value;
        }
        accepted++;
      }
      equal = selected == (Long) state.target;
    } else {
      String selected = null;
      String argument = (String) state.argument;
      String excluded = (String) state.excluded;
      String[] stringValues = (String[]) state.values;
      int accepted = 0;
      for (String stringValue : stringValues) {
        if (state.mapping.equals("filtered") && stringValue.equals(excluded)) {
          continue;
        }
        String value =
            state.transformation.equals("identity") ? stringValue : stringValue + argument;
        checksum ^= value.hashCode();
        if (accepted == state.index) {
          selected = value;
        }
        accepted++;
      }
      equal = requireNonNull(selected).equals(state.target);
    }
    return equal != (checksum == Long.MIN_VALUE);
  }

  @Benchmark
  public boolean stringListLiteralMembershipNativePrimitive(
      StringListLiteralMembershipState state) {
    return state.nativePlan.evalBoolean(state.activation);
  }

  @Benchmark
  public Val stringListLiteralMembershipNativeFinalVal(StringListLiteralMembershipState state) {
    return state.nativePlan.eval(state.activation);
  }

  @Benchmark
  public Val stringListLiteralMembershipCurrentRaw(StringListLiteralMembershipState state) {
    return state.currentPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult stringListLiteralMembershipNativeProgram(
      StringListLiteralMembershipState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult stringListLiteralMembershipCurrentProgram(
      StringListLiteralMembershipState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public boolean stringListLiteralMembershipJava(StringListLiteralMembershipState state) {
    for (String value : state.values) {
      String evaluated = state.shape.equals("constant") || state.condition ? value : state.fallback;
      if (state.needle.equals(evaluated)) {
        return true;
      }
    }
    return false;
  }

  @Benchmark
  public boolean boolListLiteralIndexNativePrimitive(BoolListLiteralState state) {
    return state.nativeIndexPlan.evalBoolean(state.activation);
  }

  @Benchmark
  public Val boolListLiteralIndexNativeFinalVal(BoolListLiteralState state) {
    return state.nativeIndexPlan.eval(state.activation);
  }

  @Benchmark
  public Val boolListLiteralIndexCurrentRaw(BoolListLiteralState state) {
    return state.currentIndexPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult boolListLiteralIndexNativeProgram(BoolListLiteralState state) {
    return state.nativeIndexProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult boolListLiteralIndexCurrentProgram(BoolListLiteralState state) {
    return state.currentIndexProgram.eval(state.variables);
  }

  @Benchmark
  public boolean boolListLiteralIndexJava(BoolListLiteralState state) {
    boolean selected = false;
    for (int i = 0; i < state.size; i++) {
      boolean value =
          state.shape.equals("constant") || state.condition ? state.values[i] : state.fallback;
      if (i == state.index) {
        selected = value;
      }
    }
    return selected;
  }

  @Benchmark
  public long boolListLiteralSizeNativePrimitive(BoolListLiteralState state) {
    return state.nativeSizePlan.evalInt(state.activation);
  }

  @Benchmark
  public Val boolListLiteralSizeNativeFinalVal(BoolListLiteralState state) {
    return state.nativeSizePlan.eval(state.activation);
  }

  @Benchmark
  public Val boolListLiteralSizeCurrentRaw(BoolListLiteralState state) {
    return state.currentSizePlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult boolListLiteralSizeNativeProgram(BoolListLiteralState state) {
    return state.nativeSizeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult boolListLiteralSizeCurrentProgram(BoolListLiteralState state) {
    return state.currentSizeProgram.eval(state.variables);
  }

  @Benchmark
  public int boolListLiteralSizeJava(BoolListLiteralState state) {
    boolean evaluated = false;
    if (state.shape.equals("computed")) {
      for (boolean value : state.values) {
        evaluated ^= state.condition ? value : state.fallback;
      }
    }
    return state.size;
  }

  @Benchmark
  public String stringListLiteralIndexNativePrimitive(StringListLiteralState state) {
    return state.nativeIndexPlan.evalString(state.activation);
  }

  @Benchmark
  public Val stringListLiteralIndexNativeFinalVal(StringListLiteralState state) {
    return state.nativeIndexPlan.eval(state.activation);
  }

  @Benchmark
  public Val stringListLiteralIndexCurrentRaw(StringListLiteralState state) {
    return state.currentIndexPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult stringListLiteralIndexNativeProgram(StringListLiteralState state) {
    return state.nativeIndexProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult stringListLiteralIndexCurrentProgram(StringListLiteralState state) {
    return state.currentIndexProgram.eval(state.variables);
  }

  @Benchmark
  public String stringListLiteralIndexJava(StringListLiteralState state) {
    String selected = null;
    for (int i = 0; i < state.size; i++) {
      String value =
          state.shape.equals("constant") || state.condition ? state.values[i] : state.fallback;
      if (i == state.index) {
        selected = value;
      }
    }
    return selected;
  }

  @Benchmark
  public long stringListLiteralSizeNativePrimitive(StringListLiteralState state) {
    return state.nativeSizePlan.evalInt(state.activation);
  }

  @Benchmark
  public Val stringListLiteralSizeNativeFinalVal(StringListLiteralState state) {
    return state.nativeSizePlan.eval(state.activation);
  }

  @Benchmark
  public Val stringListLiteralSizeCurrentRaw(StringListLiteralState state) {
    return state.currentSizePlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult stringListLiteralSizeNativeProgram(StringListLiteralState state) {
    return state.nativeSizeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult stringListLiteralSizeCurrentProgram(StringListLiteralState state) {
    return state.currentSizeProgram.eval(state.variables);
  }

  @Benchmark
  public int stringListLiteralSizeJava(StringListLiteralState state) {
    String evaluated = null;
    if (state.shape.equals("computed")) {
      for (String value : state.values) {
        evaluated = state.condition ? value : state.fallback;
      }
    }
    return evaluated == null && state.shape.equals("computed") ? -1 : state.size;
  }

  @Benchmark
  public boolean intListLiteralIndexNativePrimitive(IntListLiteralState state) {
    return state.nativePlan.evalBoolean(state.activation);
  }

  @Benchmark
  public Val intListLiteralIndexNativeFinalVal(IntListLiteralState state) {
    return state.nativePlan.eval(state.activation);
  }

  @Benchmark
  public Val intListLiteralIndexCurrentRaw(IntListLiteralState state) {
    return state.currentPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult intListLiteralIndexNativeProgram(IntListLiteralState state) {
    return state.nativeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult intListLiteralIndexCurrentProgram(IntListLiteralState state) {
    return state.currentProgram.eval(state.variables);
  }

  @Benchmark
  public boolean intListLiteralIndexJava(IntListLiteralState state) {
    long selected = 0L;
    for (int i = 0; i < state.size; i++) {
      long value = state.shape.equals("constant") ? 50_021L + i : state.x + i;
      if (i == state.index) {
        selected = value;
      }
    }
    return selected == state.target;
  }

  @Benchmark
  public long intListLiteralSizeNativePrimitive(IntListLiteralState state) {
    return state.nativeSizePlan.evalInt(state.activation);
  }

  @Benchmark
  public Val intListLiteralSizeNativeFinalVal(IntListLiteralState state) {
    return state.nativeSizePlan.eval(state.activation);
  }

  @Benchmark
  public Val intListLiteralSizeCurrentRaw(IntListLiteralState state) {
    return state.currentSizePlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult intListLiteralSizeNativeProgram(IntListLiteralState state) {
    return state.nativeSizeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult intListLiteralSizeCurrentProgram(IntListLiteralState state) {
    return state.currentSizeProgram.eval(state.variables);
  }

  @Benchmark
  public int intListLiteralSizeJava(IntListLiteralState state) {
    long evaluated = 0L;
    if (state.shape.equals("computed")) {
      for (int i = 0; i < state.size; i++) {
        evaluated ^= state.x + i;
      }
    }
    return evaluated == Long.MIN_VALUE ? -1 : state.size;
  }

  @Benchmark
  public double doubleListLiteralIndexNativePrimitive(DoubleListLiteralState state) {
    return state.nativeIndexPlan.evalDouble(state.activation);
  }

  @Benchmark
  public Val doubleListLiteralIndexNativeFinalVal(DoubleListLiteralState state) {
    return state.nativeIndexPlan.eval(state.activation);
  }

  @Benchmark
  public Val doubleListLiteralIndexCurrentRaw(DoubleListLiteralState state) {
    return state.currentIndexPlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult doubleListLiteralIndexNativeProgram(DoubleListLiteralState state) {
    return state.nativeIndexProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult doubleListLiteralIndexCurrentProgram(DoubleListLiteralState state) {
    return state.currentIndexProgram.eval(state.variables);
  }

  @Benchmark
  public double doubleListLiteralIndexJava(DoubleListLiteralState state) {
    double selected = 0.0d;
    for (int i = 0; i < state.size; i++) {
      double value = state.d + i;
      if (i == state.index) {
        selected = value;
      }
    }
    return selected;
  }

  @Benchmark
  public long doubleListLiteralSizeNativePrimitive(DoubleListLiteralState state) {
    return state.nativeSizePlan.evalInt(state.activation);
  }

  @Benchmark
  public Val doubleListLiteralSizeNativeFinalVal(DoubleListLiteralState state) {
    return state.nativeSizePlan.eval(state.activation);
  }

  @Benchmark
  public Val doubleListLiteralSizeCurrentRaw(DoubleListLiteralState state) {
    return state.currentSizePlan.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult doubleListLiteralSizeNativeProgram(DoubleListLiteralState state) {
    return state.nativeSizeProgram.eval(state.variables);
  }

  @Benchmark
  public Program.EvalResult doubleListLiteralSizeCurrentProgram(DoubleListLiteralState state) {
    return state.currentSizeProgram.eval(state.variables);
  }

  @Benchmark
  public int doubleListLiteralSizeJava(DoubleListLiteralState state) {
    double evaluated = 0.0d;
    if (state.shape.equals("computed")) {
      for (int i = 0; i < state.size; i++) {
        evaluated += state.d + i;
      }
    }
    return evaluated == Double.NEGATIVE_INFINITY ? -1 : state.size;
  }

  @Benchmark
  public Val intListLiteralTerminalCurrentRaw(IntListLiteralState state) {
    return state.currentTerminal.eval(state.activation);
  }

  @Benchmark
  public Program.EvalResult intListLiteralTerminalCurrentProgram(IntListLiteralState state) {
    return state.currentTerminalProgram.eval(state.variables);
  }

  @Benchmark
  public long[] intListLiteralTerminalJava(IntListLiteralState state) {
    long[] values = new long[state.size];
    for (int i = 0; i < values.length; i++) {
      values[i] = state.shape.equals("constant") ? 50_021L + i : state.x + i;
    }
    return values;
  }

  @Benchmark
  public Program constructNativeProgram(ConstructionState state) {
    return state.env.program(state.ast);
  }

  @Benchmark
  public Program constructCurrentProgram(ConstructionState state) {
    return state.env.program(state.ast, evalOptions(OptDisableNativeEval));
  }

  @Benchmark
  public Program.EvalResult constructAndEvaluate(ConstructionState state) {
    return state.env.program(state.ast).eval(state.variables);
  }

  private static Ast compile(Env env, String source) {
    AstIssuesTuple result = env.compile(source);
    if (result.hasIssues()) {
      throw requireNonNull(result.getIssues().err());
    }
    return result.getAst();
  }

  private static NativeCapabilityBenchmark requireNative(Prog program) {
    return NativeCapabilityBenchmark.require(program.interpretable);
  }

  private static Interpretable currentPlan(Prog program, Ast ast) {
    return ((Prog) program.e.program(ast, evalOptions(OptDisableNativeEval))).interpretable;
  }

  private static String deepExpression() {
    String source = "x";
    for (int level = 0; level < 16; level++) {
      source =
          switch (level & 3) {
            case 0 -> "(" + source + " + 17)";
            case 1 -> "(" + source + " * 3)";
            case 2 -> "(" + source + " - 11)";
            default -> "(" + source + " / 2)";
          };
    }
    return source;
  }

  private static String listLiteralIndexExpression(int size) {
    StringBuilder source = new StringBuilder("[");
    for (int i = 0; i < size; i++) {
      if (i > 0) {
        source.append(',');
      }
      source.append("x+").append(i);
    }
    return source.append("][").append(size / 2).append(']').toString();
  }

  private static void assertEquivalent(Val actual, Val expected) {
    if (actual.getClass() != expected.getClass() || !actual.value().equals(expected.value())) {
      throw new IllegalStateException("native and current results differ");
    }
  }

  private abstract static class TypeFixture {
    final com.google.api.expr.v1alpha1.Type type;
    final Object value;

    TypeFixture(com.google.api.expr.v1alpha1.Type type, Object value) {
      this.type = type;
      this.value = value;
    }

    abstract String next(String source, int level);
  }
}
