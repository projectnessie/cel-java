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
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

/**
 * End-to-end benchmark for exact map-source consumers.
 *
 * <p>Traversal fixtures use insertion-ordered maps only to place early and late benchmark matches
 * deterministically. CEL map evaluation remains order-agnostic.
 */
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class NativeExactMapSourceBench {
  @State(Scope.Benchmark)
  public static class MapState {
    @Param({"size", "hit", "membershipMiss", "presentNull"})
    public String operation;

    @Param({"0", "1", "16", "1024"})
    public int size;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Map<String, Object> variables;
    Map<String, Long> values;
    Map<String, Object> nullable;

    @Setup
    public void setup() {
      String expression =
          switch (operation) {
            case "size" -> "size(values)";
            case "hit" -> "cardinality == 0 ? -1 : values['last']";
            case "membershipMiss" -> "'missing' in values";
            case "presentNull" -> "nullable['present']";
            default -> throw new IllegalArgumentException(operation);
          };
      values = new LinkedHashMap<>();
      for (long value = 0; value < size; value++) {
        values.put(value == size - 1L ? "last" : value + "-key", value);
      }
      nullable = new LinkedHashMap<>();
      nullable.put("present", null);
      variables =
          Map.of(
              "values", values,
              "nullable", nullable,
              "cardinality", (long) size);

      Env exactEnv = environment(new ExactAdapter());
      Env generalEnv = environment(DefaultTypeAdapter.Instance);
      Ast exactAst = compile(exactEnv, expression);
      exactNative = exactEnv.program(exactAst);
      exactDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));
      general = generalEnv.program(compile(generalEnv, expression));
    }
  }

  @Benchmark
  public Object exactNative(MapState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object exactDisabled(MapState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object generalAdapter(MapState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public Object javaCeiling(MapState state) {
    return switch (state.operation) {
      case "size" -> state.values.size();
      case "hit" -> state.size == 0 ? -1L : state.values.get("last");
      case "membershipMiss" -> state.values.containsKey("missing");
      case "presentNull" -> state.nullable.get("present");
      default -> throw new IllegalArgumentException(state.operation);
    };
  }

  @State(Scope.Benchmark)
  public static class ScalarKeyState {
    @Param({"constantIntHit", "constantIntMembership", "dynamicIntHit", "dynamicBoolHit"})
    public String operation;

    @Param({"1", "16", "1024"})
    public int size;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Map<String, Object> variables;
    Map<Integer, Long> integerValues;
    Map<Boolean, Long> booleanValues;
    int integerKey;
    boolean booleanKey;

    @Setup
    public void setup() {
      integerValues = new LinkedHashMap<>();
      for (int value = 0; value < size; value++) {
        integerValues.put(value, (long) value);
      }
      integerKey = size - 1;
      booleanKey = true;
      booleanValues = Map.of(false, 0L, true, 1L);
      variables =
          Map.of(
              "integerValues", integerValues,
              "booleanValues", booleanValues,
              "integerKey", (long) integerKey,
              "booleanKey", booleanKey);
      String expression =
          switch (operation) {
            case "constantIntHit" -> "integerValues[" + integerKey + "]";
            case "constantIntMembership" -> integerKey + " in integerValues";
            case "dynamicIntHit" -> "integerValues[integerKey]";
            case "dynamicBoolHit" -> "booleanValues[booleanKey]";
            default -> throw new IllegalArgumentException(operation);
          };
      Env exactEnv = scalarKeyEnvironment(new ExactAdapter());
      Env generalEnv = scalarKeyEnvironment(DefaultTypeAdapter.Instance);
      Ast exactAst = compile(exactEnv, expression);
      exactNative = exactEnv.program(exactAst);
      exactDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));
      general = generalEnv.program(compile(generalEnv, expression));
    }
  }

  @Benchmark
  public Object scalarKeyExactNative(ScalarKeyState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object scalarKeyExactDisabled(ScalarKeyState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object scalarKeyGeneralAdapter(ScalarKeyState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public Object scalarKeyJavaCeiling(ScalarKeyState state) {
    return switch (state.operation) {
      case "constantIntHit", "dynamicIntHit" -> state.integerValues.get(state.integerKey);
      case "constantIntMembership" -> state.integerValues.containsKey(state.integerKey);
      case "dynamicBoolHit" -> state.booleanValues.get(state.booleanKey);
      default -> throw new IllegalArgumentException(state.operation);
    };
  }

  @State(Scope.Benchmark)
  public static class KeyTraversalState {
    @Param({
      "existsFirst",
      "existsLast",
      "existsMiss",
      "allFirstFalse",
      "allLastFalse",
      "allTrue",
      "existsOneOne",
      "existsOneMiss"
    })
    public String scenario;

    @Param({"0", "1", "16", "1024"})
    public int size;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Map<String, Object> variables;
    Map<String, String> values;
    String targetKey;

    @Setup
    public void setup() {
      values = stringValues(size);
      targetKey =
          switch (scenario) {
            case "existsFirst", "allFirstFalse" -> keyAtOrMissing(size, 0);
            case "existsLast", "allLastFalse", "existsOneOne" -> keyAtOrMissing(size, size - 1);
            case "existsMiss", "allTrue", "existsOneMiss" -> "missing-key";
            default -> throw new IllegalArgumentException(scenario);
          };
      String expression =
          switch (scenario) {
            case "existsFirst", "existsLast", "existsMiss" ->
                "values.exists(key, key == targetKey)";
            case "allFirstFalse", "allLastFalse", "allTrue" -> "values.all(key, key != targetKey)";
            case "existsOneOne", "existsOneMiss" -> "values.exists_one(key, key == targetKey)";
            default -> throw new IllegalArgumentException(scenario);
          };
      variables = Map.of("values", values, "targetKey", targetKey);

      Env exactEnv = stringMapEnvironment(new ExactAdapter());
      Env generalEnv = stringMapEnvironment(DefaultTypeAdapter.Instance);
      Ast exactAst = compile(exactEnv, expression);
      exactNative = exactEnv.program(exactAst);
      exactDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));
      general = generalEnv.program(compile(generalEnv, expression));
    }
  }

  @Benchmark
  public Object keyTraversalExactNative(KeyTraversalState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object keyTraversalExactDisabled(KeyTraversalState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object keyTraversalGeneralAdapter(KeyTraversalState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public boolean keyTraversalJavaCeiling(KeyTraversalState state) {
    return switch (state.scenario) {
      case "existsFirst", "existsLast", "existsMiss" ->
          anyKeyMatches(state.values, state.targetKey);
      case "allFirstFalse", "allLastFalse", "allTrue" ->
          allKeysMatch(state.values, state.targetKey);
      case "existsOneOne", "existsOneMiss" -> oneKeyMatches(state.values, state.targetKey);
      default -> throw new IllegalArgumentException(state.scenario);
    };
  }

  @State(Scope.Benchmark)
  public static class EntryTraversalState {
    @Param({
      "existsFirst",
      "existsLast",
      "existsMiss",
      "allFirstFalse",
      "allLastFalse",
      "allTrue",
      "existsOneOne",
      "existsOneMiss"
    })
    public String scenario;

    @Param({"0", "1", "16", "1024"})
    public int size;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Map<String, Object> variables;
    Map<String, String> values;
    String targetKey;
    String targetValue;

    @Setup
    public void setup() {
      values = stringValues(size);
      switch (scenario) {
        case "existsFirst" -> {
          targetKey = keyAtOrMissing(size, 0);
          targetValue = valueAtOrMissing(values, targetKey);
        }
        case "existsLast", "existsOneOne" -> {
          targetKey = keyAtOrMissing(size, size - 1);
          targetValue = valueAtOrMissing(values, targetKey);
        }
        case "existsMiss", "existsOneMiss", "allTrue" -> {
          targetKey = "missing-key";
          targetValue = "missing-value";
        }
        case "allFirstFalse" -> {
          targetKey = keyAtOrMissing(size, 0);
          targetValue = "mismatched-value";
        }
        case "allLastFalse" -> {
          targetKey = keyAtOrMissing(size, size - 1);
          targetValue = "mismatched-value";
        }
        default -> throw new IllegalArgumentException(scenario);
      }
      String expression =
          switch (scenario) {
            case "existsFirst", "existsLast", "existsMiss" ->
                "values.exists(key, value, key == targetKey && value == targetValue)";
            case "allFirstFalse", "allLastFalse", "allTrue" ->
                "values.all(key, value, key != targetKey || value == targetValue)";
            case "existsOneOne", "existsOneMiss" ->
                "values.exists_one(key, value, key == targetKey && value == targetValue)";
            default -> throw new IllegalArgumentException(scenario);
          };
      variables =
          Map.of(
              "values", values,
              "targetKey", targetKey,
              "targetValue", targetValue);

      Env exactEnv = stringMapEnvironment(new ExactAdapter());
      Env generalEnv = stringMapEnvironment(DefaultTypeAdapter.Instance);
      Ast exactAst = compile(exactEnv, expression);
      exactNative = exactEnv.program(exactAst);
      exactDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));
      general = generalEnv.program(compile(generalEnv, expression));
    }
  }

  @Benchmark
  public Object entryTraversalExactNative(EntryTraversalState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object entryTraversalExactDisabled(EntryTraversalState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object entryTraversalGeneralAdapter(EntryTraversalState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public boolean entryTraversalJavaCeiling(EntryTraversalState state) {
    return switch (state.scenario) {
      case "existsFirst", "existsLast", "existsMiss" ->
          anyEntryMatches(state.values, state.targetKey, state.targetValue);
      case "allFirstFalse", "allLastFalse", "allTrue" ->
          allEntriesMatch(state.values, state.targetKey, state.targetValue);
      case "existsOneOne", "existsOneMiss" ->
          oneEntryMatches(state.values, state.targetKey, state.targetValue);
      default -> throw new IllegalArgumentException(state.scenario);
    };
  }

  @State(Scope.Benchmark)
  public static class EmptyEqualityState {
    Program exactNative;
    Program exactDisabled;
    Program general;
    Map<String, Object> variables;
    Map<String, String> left;
    Map<String, String> right;

    @Setup
    public void setup() {
      left = new LinkedHashMap<>();
      right = new LinkedHashMap<>();
      variables = Map.of("left", left, "right", right);

      Env exactEnv = equalityEnvironment(new ExactAdapter());
      Env generalEnv = equalityEnvironment(DefaultTypeAdapter.Instance);
      Ast exactAst = compile(exactEnv, "left == right");
      exactNative = exactEnv.program(exactAst);
      exactDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));
      general = generalEnv.program(compile(generalEnv, "left == right"));
    }
  }

  @Benchmark
  public Object emptyEqualityExactNative(EmptyEqualityState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object emptyEqualityExactDisabled(EmptyEqualityState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object emptyEqualityGeneralAdapter(EmptyEqualityState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public boolean emptyEqualityJavaCeiling(EmptyEqualityState state) {
    return mapsEqual(state.left, state.right);
  }

  @State(Scope.Benchmark)
  public static class ScalarEqualityState {
    @Param({
      "equal",
      "sizeMismatch",
      "missingKey",
      "firstMismatch",
      "middleMismatch",
      "lastMismatch"
    })
    public String scenario;

    @Param({"1", "16", "1024"})
    public int size;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Map<String, Object> variables;
    Map<String, String> left;
    Map<String, String> right;

    @Setup
    public void setup() {
      left = stringValues(size);
      right = new LinkedHashMap<>(left);
      switch (scenario) {
        case "equal" -> {}
        case "sizeMismatch" -> right.remove(key(size - 1));
        case "missingKey" -> {
          right.remove(key(size - 1));
          right.put("replacement-key", payload(-1));
        }
        case "firstMismatch" -> right.put(key(0), "mismatched-value");
        case "middleMismatch" -> right.put(key(size / 2), "mismatched-value");
        case "lastMismatch" -> right.put(key(size - 1), "mismatched-value");
        default -> throw new IllegalArgumentException(scenario);
      }
      variables = Map.of("left", left, "right", right);

      Env exactEnv = equalityEnvironment(new ExactAdapter());
      Env generalEnv = equalityEnvironment(DefaultTypeAdapter.Instance);
      Ast exactAst = compile(exactEnv, "left == right");
      exactNative = exactEnv.program(exactAst);
      exactDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));
      general = generalEnv.program(compile(generalEnv, "left == right"));
    }
  }

  @Benchmark
  public Object scalarEqualityExactNative(ScalarEqualityState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object scalarEqualityExactDisabled(ScalarEqualityState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object scalarEqualityGeneralAdapter(ScalarEqualityState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public boolean scalarEqualityJavaCeiling(ScalarEqualityState state) {
    return mapsEqual(state.left, state.right);
  }

  @State(Scope.Benchmark)
  public static class NestedEqualityState {
    @Param({"equal", "firstMismatch", "lastMismatch"})
    public String scenario;

    @Param({"1", "16", "1024"})
    public int size;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Map<String, Object> variables;
    Map<String, List<Long>> left;
    Map<String, List<Long>> right;

    @Setup
    public void setup() {
      left = nestedValues(size);
      right = new LinkedHashMap<>(left);
      switch (scenario) {
        case "equal" -> {}
        case "firstMismatch" -> right.put(key(0), mismatchedNestedValue(left.get(key(0))));
        case "lastMismatch" ->
            right.put(key(size - 1), mismatchedNestedValue(left.get(key(size - 1))));
        default -> throw new IllegalArgumentException(scenario);
      }
      variables = Map.of("left", left, "right", right);

      Env exactEnv = nestedEqualityEnvironment(new ExactAdapter());
      Env generalEnv = nestedEqualityEnvironment(DefaultTypeAdapter.Instance);
      Ast exactAst = compile(exactEnv, "left == right");
      exactNative = exactEnv.program(exactAst);
      exactDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));
      general = generalEnv.program(compile(generalEnv, "left == right"));
    }
  }

  @Benchmark
  public Object nestedEqualityExactNative(NestedEqualityState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object nestedEqualityExactDisabled(NestedEqualityState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object nestedEqualityGeneralAdapter(NestedEqualityState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public boolean nestedEqualityJavaCeiling(NestedEqualityState state) {
    return mapsEqual(state.left, state.right);
  }

  private static Env environment(org.projectnessie.cel.common.types.ref.TypeAdapter adapter) {
    return newEnv(
        customTypeAdapter(adapter),
        declarations(
            Decls.newVar("values", Decls.newMapType(Decls.String, Decls.Int)),
            Decls.newVar("nullable", Decls.newMapType(Decls.String, Decls.Null)),
            Decls.newVar("cardinality", Decls.Int)));
  }

  private static Env scalarKeyEnvironment(
      org.projectnessie.cel.common.types.ref.TypeAdapter adapter) {
    return newEnv(
        customTypeAdapter(adapter),
        declarations(
            Decls.newVar("integerValues", Decls.newMapType(Decls.Int, Decls.Int)),
            Decls.newVar("booleanValues", Decls.newMapType(Decls.Bool, Decls.Int)),
            Decls.newVar("integerKey", Decls.Int),
            Decls.newVar("booleanKey", Decls.Bool)));
  }

  private static Env stringMapEnvironment(
      org.projectnessie.cel.common.types.ref.TypeAdapter adapter) {
    return newEnv(
        customTypeAdapter(adapter),
        declarations(
            Decls.newVar("values", Decls.newMapType(Decls.String, Decls.String)),
            Decls.newVar("targetKey", Decls.String),
            Decls.newVar("targetValue", Decls.String)));
  }

  private static Env equalityEnvironment(
      org.projectnessie.cel.common.types.ref.TypeAdapter adapter) {
    return newEnv(
        customTypeAdapter(adapter),
        declarations(
            Decls.newVar("left", Decls.newMapType(Decls.String, Decls.String)),
            Decls.newVar("right", Decls.newMapType(Decls.String, Decls.String))));
  }

  private static Env nestedEqualityEnvironment(
      org.projectnessie.cel.common.types.ref.TypeAdapter adapter) {
    var nestedType = Decls.newMapType(Decls.String, Decls.newListType(Decls.Int));
    return newEnv(
        customTypeAdapter(adapter),
        declarations(Decls.newVar("left", nestedType), Decls.newVar("right", nestedType)));
  }

  private static Map<String, String> stringValues(int size) {
    Map<String, String> values = new LinkedHashMap<>();
    for (int i = 0; i < size; i++) {
      values.put(key(i), payload(i));
    }
    return values;
  }

  private static Map<String, List<Long>> nestedValues(int size) {
    Map<String, List<Long>> values = new LinkedHashMap<>();
    for (long i = 0; i < size; i++) {
      values.put(key((int) i), List.of(i, i + 1L, i + 2L, i + 3L));
    }
    return values;
  }

  private static List<Long> mismatchedNestedValue(List<Long> value) {
    List<Long> mismatch = new ArrayList<>(value);
    mismatch.set(mismatch.size() - 1, -1L);
    return mismatch;
  }

  private static String key(int index) {
    return "key-" + index;
  }

  private static String keyAtOrMissing(int size, int index) {
    return index >= 0 && index < size ? key(index) : "missing-key";
  }

  private static String payload(int index) {
    return "payload-" + index + '-' + "x".repeat(128);
  }

  private static String valueAtOrMissing(Map<String, String> values, String key) {
    String value = values.get(key);
    return value != null ? value : "missing-value";
  }

  private static boolean anyKeyMatches(Map<String, String> values, String targetKey) {
    for (String key : values.keySet()) {
      if (targetKey.equals(key)) {
        return true;
      }
    }
    return false;
  }

  private static boolean allKeysMatch(Map<String, String> values, String targetKey) {
    for (String key : values.keySet()) {
      if (targetKey.equals(key)) {
        return false;
      }
    }
    return true;
  }

  private static boolean oneKeyMatches(Map<String, String> values, String targetKey) {
    int matches = 0;
    for (String key : values.keySet()) {
      if (targetKey.equals(key)) {
        matches++;
      }
    }
    return matches == 1;
  }

  private static boolean anyEntryMatches(
      Map<String, String> values, String targetKey, String targetValue) {
    for (Map.Entry<String, String> entry : values.entrySet()) {
      if (targetKey.equals(entry.getKey()) && targetValue.equals(entry.getValue())) {
        return true;
      }
    }
    return false;
  }

  private static boolean allEntriesMatch(
      Map<String, String> values, String targetKey, String targetValue) {
    for (Map.Entry<String, String> entry : values.entrySet()) {
      if (targetKey.equals(entry.getKey()) && !targetValue.equals(entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  private static boolean oneEntryMatches(
      Map<String, String> values, String targetKey, String targetValue) {
    int matches = 0;
    for (Map.Entry<String, String> entry : values.entrySet()) {
      if (targetKey.equals(entry.getKey()) && targetValue.equals(entry.getValue())) {
        matches++;
      }
    }
    return matches == 1;
  }

  private static boolean mapsEqual(Map<?, ?> left, Map<?, ?> right) {
    if (left.size() != right.size()) {
      return false;
    }
    for (Map.Entry<?, ?> entry : left.entrySet()) {
      Object key = entry.getKey();
      if (!right.containsKey(key) || !entry.getValue().equals(right.get(key))) {
        return false;
      }
    }
    return true;
  }

  private static Ast compile(Env env, String expression) {
    var result = env.compile(expression);
    if (result.hasIssues()) {
      throw new IllegalStateException(result.getIssues().toString());
    }
    return result.getAst();
  }

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }
}
