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

import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import com.google.api.expr.v1alpha1.Decl;
import com.google.api.expr.v1alpha1.Type;
import com.google.protobuf.Duration;
import com.google.protobuf.Struct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.projectnessie.cel.Ast;
import org.projectnessie.cel.CEL;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

/** End-to-end benchmark for immediate checked list-concatenation consumers. */
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class NativeListConcatBench {
  @State(Scope.Benchmark)
  public static class ConcatState {
    @Param({"size", "boundaryIndex", "variableIndex", "computedIndex"})
    public String operation;

    @Param({"1", "16", "1024"})
    public int size;

    @Param({"2", "4", "16", "64"})
    public int sourceCount;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Interpretable exactNativeRaw;
    Interpretable exactDisabledRaw;
    NativeIntCapability exactNativePrimitive;
    Activation activation;
    Map<String, Object> variables;
    List<List<Long>> sources;

    @Setup
    public void setup() {
      String expression = expression(operation, sourceCount, size);
      Decl[] declarations = listDeclarations(sourceCount);
      sources = new ArrayList<>(sourceCount);
      Map<String, Object> inputs = new LinkedHashMap<>();
      for (int source = 0; source < sourceCount; source++) {
        List<Long> values = values(size, Math.multiplyExact(source, size));
        sources.add(values);
        inputs.put(sourceName(source), values);
      }
      long boundaryIndex = Math.multiplyExact((long) size, sourceCount - 1L);
      inputs.put("index", operation.equals("computedIndex") ? boundaryIndex - 1L : boundaryIndex);
      variables = Map.copyOf(inputs);

      Env exactEnv = newEnv(customTypeAdapter(new ExactAdapter()), declarations(declarations));
      Env generalEnv = newEnv(declarations(declarations));
      Ast exactAst = compile(exactEnv, expression);
      exactNative = exactEnv.program(exactAst);
      exactDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));
      general = generalEnv.program(compile(generalEnv, expression));
      exactNativeRaw = plan(exactEnv, exactAst, true);
      exactDisabledRaw = plan(exactEnv, exactAst, false);
      exactNativePrimitive =
          (NativeIntCapability)
              (exactNativeRaw instanceof NativeIsland island ? island.root() : exactNativeRaw);
      activation = newActivation(variables);
    }
  }

  @Benchmark
  public Object exactNative(ConcatState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object exactDisabled(ConcatState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public long exactNativePrimitive(ConcatState state) {
    return state.exactNativePrimitive.evalInt(state.activation);
  }

  @Benchmark
  public Val exactNativeFinalVal(ConcatState state) {
    return state.exactNativeRaw.eval(state.activation);
  }

  @Benchmark
  public Val exactDisabledRaw(ConcatState state) {
    return state.exactDisabledRaw.eval(state.activation);
  }

  @Benchmark
  public Object generalAdapter(ConcatState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public long javaCeiling(ConcatState state) {
    return state.operation.equals("size")
        ? Math.multiplyExact((long) state.sourceCount, state.size)
        : state.sources.get(state.sourceCount - 1).get(0);
  }

  @State(Scope.Benchmark)
  public static class MembershipState {
    @Param({"first", "middle", "last", "miss"})
    public String hitPosition;

    @Param({"1", "16", "1024"})
    public int size;

    @Param({"2", "4", "16", "64"})
    public int sourceCount;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Interpretable exactNativeRaw;
    NativeBooleanCapability exactNativePrimitive;
    Activation activation;
    Map<String, Object> variables;
    List<List<String>> sources;
    String needle;

    @Setup
    public void setup() {
      int hitSource =
          switch (hitPosition) {
            case "first" -> 0;
            case "middle" -> sourceCount / 2;
            case "last" -> sourceCount - 1;
            case "miss" -> -1;
            default -> throw new IllegalArgumentException(hitPosition);
          };
      int hitIndex = hitPosition.equals("last") ? size - 1 : 0;
      needle = hitSource >= 0 ? stringValue(hitSource, hitIndex) : "missing";
      String expression = "'" + needle + "' in (" + concatExpression(sourceCount) + ")";
      Decl[] declarations = listDeclarations(sourceCount, Decls.String);
      sources = new ArrayList<>(sourceCount);
      Map<String, Object> inputs = new LinkedHashMap<>();
      for (int source = 0; source < sourceCount; source++) {
        List<String> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
          values.add(stringValue(source, index));
        }
        sources.add(values);
        inputs.put(sourceName(source), values);
      }
      variables = Map.copyOf(inputs);

      Env exactEnv = newEnv(customTypeAdapter(new ExactAdapter()), declarations(declarations));
      Env generalEnv = newEnv(declarations(declarations));
      Ast exactAst = compile(exactEnv, expression);
      exactNative = exactEnv.program(exactAst);
      exactDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));
      general = generalEnv.program(compile(generalEnv, expression));
      exactNativeRaw = plan(exactEnv, exactAst, true);
      exactNativePrimitive =
          (NativeBooleanCapability)
              (exactNativeRaw instanceof NativeIsland island ? island.root() : exactNativeRaw);
      activation = newActivation(variables);
    }
  }

  @Benchmark
  public Object membershipExactNative(MembershipState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object membershipExactDisabled(MembershipState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object membershipGeneralAdapter(MembershipState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public boolean membershipExactNativePrimitive(MembershipState state) {
    return state.exactNativePrimitive.evalBoolean(state.activation);
  }

  @Benchmark
  public Val membershipExactNativeFinalVal(MembershipState state) {
    return state.exactNativeRaw.eval(state.activation);
  }

  @Benchmark
  public boolean membershipJavaCeiling(MembershipState state) {
    for (List<String> source : state.sources) {
      if (source.contains(state.needle)) {
        return true;
      }
    }
    return false;
  }

  @State(Scope.Benchmark)
  public static class PlanState {
    @Param({"size", "boundaryIndex", "variableIndex", "computedIndex"})
    public String operation;

    @Param({"1", "16", "1024"})
    public int size;

    @Param({"2", "4", "16", "64"})
    public int sourceCount;

    Env exactEnv;
    Ast exactAst;

    @Setup
    public void setup() {
      exactEnv =
          newEnv(
              customTypeAdapter(new ExactAdapter()), declarations(listDeclarations(sourceCount)));
      exactAst = compile(exactEnv, expression(operation, sourceCount, size));
    }
  }

  @State(Scope.Benchmark)
  public static class EmptyIndexState {
    Program exactNative;
    Program exactDisabled;
    Map<String, Object> variables;

    @Setup
    public void setup() {
      Env env =
          newEnv(
              customTypeAdapter(new ExactAdapter()),
              declarations(
                  Decls.newVar("list0", Decls.newListType(Decls.Int)),
                  Decls.newVar("list1", Decls.newListType(Decls.Int))));
      Ast ast = compile(env, "(list0 + list1)[0]");
      exactNative = env.program(ast);
      exactDisabled = env.program(ast, evalOptions(OptDisableNativeEval));
      variables = Map.of("list0", List.of(), "list1", List.of());
    }
  }

  @State(Scope.Benchmark)
  public static class TypedIndexState {
    @Param({"uint", "double", "string"})
    public String elementKind;

    Program exactNative;
    Program exactDisabled;
    Map<String, Object> variables;

    @Setup
    public void setup() {
      Type elementType =
          switch (elementKind) {
            case "uint" -> Decls.Uint;
            case "double" -> Decls.Double;
            case "string" -> Decls.String;
            default -> throw new IllegalArgumentException("unknown element kind " + elementKind);
          };
      Env env =
          newEnv(
              customTypeAdapter(new ExactAdapter()),
              declarations(listDeclarations(4, elementType)));
      Ast ast = compile(env, "(list0 + list1 + list2 + list3)[48]");
      exactNative = env.program(ast);
      exactDisabled = env.program(ast, evalOptions(OptDisableNativeEval));
      Map<String, Object> inputs = new LinkedHashMap<>();
      for (int source = 0; source < 4; source++) {
        int offset = source * 16;
        List<Object> values = new ArrayList<>(16);
        for (int index = 0; index < 16; index++) {
          values.add(
              switch (elementKind) {
                case "uint" -> ULong.valueOf(offset + index);
                case "double" -> (double) (offset + index);
                case "string" -> "value-" + (offset + index);
                default ->
                    throw new IllegalArgumentException("unknown element kind " + elementKind);
              });
        }
        inputs.put(sourceName(source), values);
      }
      variables = Map.copyOf(inputs);
    }
  }

  @State(Scope.Benchmark)
  public static class NonScalarSizeState {
    Program exactNative;
    Program exactDisabled;
    Map<String, Object> variables;

    @Setup
    public void setup() {
      Env env =
          newEnv(
              customTypeAdapter(new ExactAdapter()),
              declarations(listDeclarations(4, Decls.newMapType(Decls.String, Decls.Int))));
      Ast ast = compile(env, "size(list0 + list1 + list2 + list3)");
      exactNative = env.program(ast);
      exactDisabled = env.program(ast, evalOptions(OptDisableNativeEval));
      Map<String, Object> inputs = new LinkedHashMap<>();
      for (int source = 0; source < 4; source++) {
        List<Map<String, Long>> values = new ArrayList<>(16);
        for (int index = 0; index < 16; index++) {
          values.add(Map.of("value", (long) source * 16 + index));
        }
        inputs.put(sourceName(source), values);
      }
      variables = Map.copyOf(inputs);
    }
  }

  @State(Scope.Benchmark)
  public static class NonScalarIndexState {
    @Param({"bytes", "wrapperInt", "duration", "message", "dyn", "nestedList", "nestedMap"})
    public String elementKind;

    @Param({"constant", "variable"})
    public String indexKind;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Map<String, Object> variables;
    List<List<Object>> sources;
    int selectedIndex;

    @Setup
    public void setup() {
      int sourceCount = 4;
      int sourceSize = 16;
      Type elementType =
          switch (elementKind) {
            case "bytes" -> Decls.Bytes;
            case "wrapperInt" -> Decls.newWrapperType(Decls.Int);
            case "duration" -> Decls.Duration;
            case "message" -> Decls.newObjectType("google.protobuf.Struct");
            case "dyn" -> Decls.Dyn;
            case "nestedList" -> Decls.newListType(Decls.Int);
            case "nestedMap" -> Decls.newMapType(Decls.String, Decls.Int);
            default -> throw new IllegalArgumentException("unknown element kind " + elementKind);
          };
      selectedIndex = Math.multiplyExact(sourceSize, sourceCount - 1);
      String index = indexKind.equals("constant") ? Integer.toString(selectedIndex) : "index";
      String expression = "(" + concatExpression(sourceCount) + ")[" + index + "]";
      Decl[] listDeclarations = listDeclarations(sourceCount, elementType);
      Decl[] declarations =
          indexKind.equals("constant")
              ? listDeclarations
              : java.util.Arrays.copyOf(listDeclarations, sourceCount + 1);
      if (indexKind.equals("variable")) {
        declarations[sourceCount] = Decls.newVar("index", Decls.Int);
      }

      sources = new ArrayList<>(sourceCount);
      Map<String, Object> inputs = new LinkedHashMap<>();
      for (int source = 0; source < sourceCount; source++) {
        List<Object> values = new ArrayList<>(sourceSize);
        for (int indexInSource = 0; indexInSource < sourceSize; indexInSource++) {
          values.add(nonScalarValue(elementKind, source * sourceSize + indexInSource));
        }
        sources.add(values);
        inputs.put(sourceName(source), values);
      }
      if (indexKind.equals("variable")) {
        inputs.put("index", (long) selectedIndex);
      }
      variables = Map.copyOf(inputs);

      Env exactEnv = newEnv(customTypeAdapter(new ExactAdapter()), declarations(declarations));
      Env generalEnv = newEnv(declarations(declarations));
      Ast exactAst = compile(exactEnv, expression);
      exactNative = exactEnv.program(exactAst);
      exactDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));
      general = generalEnv.program(compile(generalEnv, expression));
    }
  }

  @Benchmark
  public Program planExactNative(PlanState state) {
    return state.exactEnv.program(state.exactAst);
  }

  @Benchmark
  public Object exactNativeEmptyMiss(EmptyIndexState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object exactDisabledEmptyMiss(EmptyIndexState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object typedExactNative(TypedIndexState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object typedExactDisabled(TypedIndexState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object nonScalarSizeExactNative(NonScalarSizeState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object nonScalarSizeExactDisabled(NonScalarSizeState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object nonScalarIndexExactNative(NonScalarIndexState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object nonScalarIndexExactDisabled(NonScalarIndexState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object nonScalarIndexGeneralAdapter(NonScalarIndexState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public Object nonScalarIndexJavaCeiling(NonScalarIndexState state) {
    return state.sources.get(3).get(0);
  }

  private static String expression(String operation, int sourceCount, int size) {
    String concat = concatExpression(sourceCount);
    if (operation.equals("size")) {
      return "size(" + concat + ")";
    }
    long index = Math.multiplyExact((long) size, sourceCount - 1L);
    return switch (operation) {
      case "boundaryIndex" -> "(" + concat + ")[" + index + "]";
      case "variableIndex" -> "(" + concat + ")[index]";
      case "computedIndex" -> "(" + concat + ")[index + 1]";
      default -> throw new IllegalArgumentException("unknown operation " + operation);
    };
  }

  private static String concatExpression(int sourceCount) {
    StringBuilder concat = new StringBuilder();
    for (int source = 0; source < sourceCount; source++) {
      if (source != 0) {
        concat.append(" + ");
      }
      concat.append(sourceName(source));
    }
    return concat.toString();
  }

  private static Decl[] listDeclarations(int sourceCount) {
    Decl[] declarations =
        java.util.Arrays.copyOf(listDeclarations(sourceCount, Decls.Int), sourceCount + 1);
    declarations[sourceCount] = Decls.newVar("index", Decls.Int);
    return declarations;
  }

  private static Decl[] listDeclarations(int sourceCount, Type elementType) {
    Decl[] declarations = new Decl[sourceCount];
    for (int source = 0; source < sourceCount; source++) {
      declarations[source] = Decls.newVar(sourceName(source), Decls.newListType(elementType));
    }
    return declarations;
  }

  private static String sourceName(int source) {
    return "list" + source;
  }

  private static List<Long> values(int size, int offset) {
    List<Long> values = new ArrayList<>(size);
    for (long value = 0; value < size; value++) {
      values.add(offset + value);
    }
    return values;
  }

  private static String stringValue(int source, int index) {
    return "value-" + source + "-" + index;
  }

  private static Object nonScalarValue(String elementKind, int value) {
    return switch (elementKind) {
      case "bytes" -> new byte[] {(byte) value};
      case "wrapperInt" -> (long) value;
      case "duration" -> Duration.newBuilder().setSeconds(value).build();
      case "message" -> Struct.getDefaultInstance();
      case "dyn" -> "value-" + value;
      case "nestedList" -> List.of((long) value);
      case "nestedMap" -> Map.of("value", (long) value);
      default -> throw new IllegalArgumentException("unknown element kind " + elementKind);
    };
  }

  private static Ast compile(Env env, String expression) {
    var result = env.compile(expression);
    if (result.hasIssues()) {
      throw new IllegalStateException(result.getIssues().toString());
    }
    return result.getAst();
  }

  private static Interpretable plan(Env env, Ast ast, boolean nativeEvaluation) {
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    TypeAdapter adapter = env.getTypeAdapter();
    AttributeFactory attributes =
        newAttributeFactory(defaultContainer, adapter, env.getTypeProvider());
    return newInterpreter(
            dispatcher,
            defaultContainer,
            env.getTypeProvider(),
            adapter,
            attributes,
            nativeEvaluation)
        .newInterpretable(CEL.astToCheckedExpr(ast));
  }

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }
}
