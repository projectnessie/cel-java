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

import com.google.api.expr.v1alpha1.Decl;
import com.google.api.expr.v1alpha1.Type;
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
 * Complete-program decision benchmarks for consumers that may eventually traverse exact
 * concatenation sources without materializing the concatenated CEL list.
 *
 * <p>The scenario parameters deliberately encode operation and outcome together. This keeps routine
 * decision runs smaller than a Cartesian product of operation, outcome, and hit position, while
 * retaining representative early, late, and miss cases.
 */
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class NativeListConcatExtendedBench {
  @State(Scope.Benchmark)
  public static class EqualityState {
    @Param({"equal", "firstMismatch", "lastMismatch"})
    public String scenario;

    @Param({"2", "16", "64"})
    public int sourceCount;

    @Param({"16", "1024"})
    public int size;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Map<String, Object> variables;
    List<List<Long>> sources;
    List<Long> other;

    @Setup
    public void setup() {
      sources = longSources(sourceCount, size);
      other = new ArrayList<>(Math.multiplyExact(sourceCount, size));
      for (List<Long> source : sources) {
        other.addAll(source);
      }
      switch (scenario) {
        case "equal" -> {}
        case "firstMismatch" -> other.set(0, -1L);
        case "lastMismatch" -> other.set(other.size() - 1, -1L);
        default -> throw new IllegalArgumentException("unknown equality scenario " + scenario);
      }

      Map<String, Object> inputs = sourceVariables("values", sources);
      inputs.put("other", other);
      variables = Map.copyOf(inputs);
      Decl[] declarations =
          listDeclarations(
              "values",
              sourceCount,
              Decls.Int,
              Decls.newVar("other", Decls.newListType(Decls.Int)));
      Programs programs =
          programs("(" + concatExpression("values", sourceCount) + ") == other", declarations);
      exactNative = programs.exactNative;
      exactDisabled = programs.exactDisabled;
      general = programs.general;
    }
  }

  @Benchmark
  public Object equalityExactNative(EqualityState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object equalityExactDisabled(EqualityState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object equalityGeneral(EqualityState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public boolean equalityJavaCeiling(EqualityState state) {
    int position = 0;
    for (List<Long> source : state.sources) {
      for (Long value : source) {
        if (!value.equals(state.other.get(position++))) {
          return false;
        }
      }
    }
    return position == state.other.size();
  }

  @State(Scope.Benchmark)
  public static class QuantifierState {
    @Param({
      "existsFirst",
      "existsLast",
      "existsMiss",
      "allFirstFalse",
      "allTrue",
      "existsOneOne",
      "existsOneTwo"
    })
    public String scenario;

    @Param({"2", "16", "64"})
    public int sourceCount;

    @Param({"1", "16", "1024"})
    public int size;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Map<String, Object> variables;
    List<List<Long>> sources;
    long target;

    @Setup
    public void setup() {
      sources = longSources(sourceCount, size);
      long last = Math.multiplyExact((long) sourceCount, size) - 1L;
      target =
          switch (scenario) {
            case "existsFirst", "allFirstFalse", "existsOneOne", "existsOneTwo" -> 0L;
            case "existsLast" -> last;
            case "existsMiss", "allTrue" -> -1L;
            default ->
                throw new IllegalArgumentException("unknown quantifier scenario " + scenario);
          };
      if (scenario.equals("existsOneTwo")) {
        sources.get(sourceCount - 1).set(size - 1, target);
      }

      Map<String, Object> inputs = sourceVariables("values", sources);
      inputs.put("target", target);
      variables = Map.copyOf(inputs);
      Decl[] declarations =
          listDeclarations("values", sourceCount, Decls.Int, Decls.newVar("target", Decls.Int));
      String concat = "(" + concatExpression("values", sourceCount) + ")";
      String expression =
          switch (scenario) {
            case "existsFirst", "existsLast", "existsMiss" ->
                concat + ".exists(value, value == target)";
            case "allFirstFalse", "allTrue" -> concat + ".all(value, value != target)";
            case "existsOneOne", "existsOneTwo" -> concat + ".exists_one(value, value == target)";
            default ->
                throw new IllegalArgumentException("unknown quantifier scenario " + scenario);
          };
      Programs programs = programs(expression, declarations);
      exactNative = programs.exactNative;
      exactDisabled = programs.exactDisabled;
      general = programs.general;
    }
  }

  @Benchmark
  public Object quantifierExactNative(QuantifierState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object quantifierExactDisabled(QuantifierState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object quantifierGeneral(QuantifierState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public boolean quantifierJavaCeiling(QuantifierState state) {
    if (state.scenario.startsWith("existsOne")) {
      int matches = 0;
      for (List<Long> source : state.sources) {
        for (long value : source) {
          if (value == state.target) {
            matches++;
          }
        }
      }
      return matches == 1;
    }
    if (state.scenario.startsWith("all")) {
      for (List<Long> source : state.sources) {
        for (long value : source) {
          if (value == state.target) {
            return false;
          }
        }
      }
      return true;
    }
    for (List<Long> source : state.sources) {
      for (long value : source) {
        if (value == state.target) {
          return true;
        }
      }
    }
    return false;
  }

  @State(Scope.Benchmark)
  public static class MapFilterState {
    @Param({"filterSize", "mapIndex", "mappedStringMembership", "mappedExists"})
    public String scenario;

    @Param({"2", "16", "64"})
    public int sourceCount;

    @Param({"16", "1024"})
    public int size;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Map<String, Object> variables;
    List<List<Long>> intSources;
    List<List<String>> stringSources;
    long threshold;
    long target;
    String needle;

    @Setup
    public void setup() {
      intSources = longSources(sourceCount, size);
      stringSources = stringSources(sourceCount, size);
      long total = Math.multiplyExact((long) sourceCount, size);
      threshold = total / 2L;
      target = total;
      needle = stringValue(sourceCount - 1, size - 1) + "!";

      String expression;
      Decl[] declarations;
      Map<String, Object> inputs;
      switch (scenario) {
        case "filterSize" -> {
          expression =
              "size(("
                  + concatExpression("ints", sourceCount)
                  + ").filter(value, value >= threshold))";
          declarations =
              listDeclarations(
                  "ints", sourceCount, Decls.Int, Decls.newVar("threshold", Decls.Int));
          inputs = sourceVariables("ints", intSources);
          inputs.put("threshold", threshold);
        }
        case "mapIndex" -> {
          expression =
              "("
                  + concatExpression("ints", sourceCount)
                  + ").map(value, value + 1)["
                  + (total - 1L)
                  + "]";
          declarations = listDeclarations("ints", sourceCount, Decls.Int);
          inputs = sourceVariables("ints", intSources);
        }
        case "mappedStringMembership" -> {
          expression =
              "needle in ("
                  + concatExpression("strings", sourceCount)
                  + ").map(value, value + suffix)";
          declarations =
              listDeclarations(
                  "strings",
                  sourceCount,
                  Decls.String,
                  Decls.newVar("needle", Decls.String),
                  Decls.newVar("suffix", Decls.String));
          inputs = sourceVariables("strings", stringSources);
          inputs.put("needle", needle);
          inputs.put("suffix", "!");
        }
        case "mappedExists" -> {
          expression =
              "("
                  + concatExpression("ints", sourceCount)
                  + ").map(value, value + 1).exists(mapped, mapped == target)";
          declarations =
              listDeclarations("ints", sourceCount, Decls.Int, Decls.newVar("target", Decls.Int));
          inputs = sourceVariables("ints", intSources);
          inputs.put("target", target);
        }
        default -> throw new IllegalArgumentException("unknown map/filter scenario " + scenario);
      }

      variables = Map.copyOf(inputs);
      Programs programs = programs(expression, declarations);
      exactNative = programs.exactNative;
      exactDisabled = programs.exactDisabled;
      general = programs.general;
    }
  }

  @Benchmark
  public Object mapFilterExactNative(MapFilterState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object mapFilterExactDisabled(MapFilterState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object mapFilterGeneral(MapFilterState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public Object mapFilterJavaCeiling(MapFilterState state) {
    return switch (state.scenario) {
      case "filterSize" -> {
        long count = 0L;
        for (List<Long> source : state.intSources) {
          for (long value : source) {
            if (value >= state.threshold) {
              count++;
            }
          }
        }
        yield count;
      }
      case "mapIndex" -> state.intSources.get(state.sourceCount - 1).get(state.size - 1) + 1L;
      case "mappedStringMembership" -> {
        boolean found = false;
        for (List<String> source : state.stringSources) {
          for (String value : source) {
            if ((value + "!").equals(state.needle)) {
              found = true;
              break;
            }
          }
          if (found) {
            break;
          }
        }
        yield found;
      }
      case "mappedExists" -> {
        boolean found = false;
        for (List<Long> source : state.intSources) {
          for (long value : source) {
            if (value + 1L == state.target) {
              found = true;
              break;
            }
          }
          if (found) {
            break;
          }
        }
        yield found;
      }
      default ->
          throw new IllegalArgumentException("unknown map/filter scenario " + state.scenario);
    };
  }

  @State(Scope.Benchmark)
  public static class MembershipState {
    @Param({
      "stringConstantLast",
      "stringDynamicLast",
      "stringDynamicMiss",
      "intConstantLast",
      "intDynamicLast",
      "intDynamicMiss"
    })
    public String scenario;

    @Param({"2", "16", "64"})
    public int sourceCount;

    @Param({"1", "16", "1024"})
    public int size;

    Program exactNative;
    Program exactDisabled;
    Program general;
    Map<String, Object> variables;
    List<? extends List<?>> sources;
    Object needle;

    @Setup
    public void setup() {
      boolean strings = scenario.startsWith("string");
      boolean miss = scenario.endsWith("Miss");
      boolean dynamic = scenario.contains("Dynamic");
      String prefix = strings ? "strings" : "ints";
      Type elementType = strings ? Decls.String : Decls.Int;
      String expression;
      Decl[] declarations;
      Map<String, Object> inputs;

      if (strings) {
        List<List<String>> values = stringSources(sourceCount, size);
        sources = values;
        needle = miss ? "missing" : stringValue(sourceCount - 1, size - 1);
        inputs = sourceVariables(prefix, values);
        if (dynamic) {
          inputs.put("needle", needle);
          declarations =
              listDeclarations(
                  prefix, sourceCount, elementType, Decls.newVar("needle", Decls.String));
          expression = "needle in (" + concatExpression(prefix, sourceCount) + ")";
        } else {
          declarations = listDeclarations(prefix, sourceCount, elementType);
          expression = "'" + needle + "' in (" + concatExpression(prefix, sourceCount) + ")";
        }
      } else {
        List<List<Long>> values = longSources(sourceCount, size);
        sources = values;
        needle = miss ? -1L : Math.multiplyExact((long) sourceCount, size) - 1L;
        inputs = sourceVariables(prefix, values);
        if (dynamic) {
          inputs.put("needle", needle);
          declarations =
              listDeclarations(prefix, sourceCount, elementType, Decls.newVar("needle", Decls.Int));
          expression = "needle in (" + concatExpression(prefix, sourceCount) + ")";
        } else {
          declarations = listDeclarations(prefix, sourceCount, elementType);
          expression = needle + " in (" + concatExpression(prefix, sourceCount) + ")";
        }
      }

      variables = Map.copyOf(inputs);
      Programs programs = programs(expression, declarations);
      exactNative = programs.exactNative;
      exactDisabled = programs.exactDisabled;
      general = programs.general;
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
  public Object membershipGeneral(MembershipState state) {
    return state.general.eval(state.variables);
  }

  @Benchmark
  public boolean membershipJavaCeiling(MembershipState state) {
    for (List<?> source : state.sources) {
      if (source.contains(state.needle)) {
        return true;
      }
    }
    return false;
  }

  private static Programs programs(String expression, Decl[] declarations) {
    Env exactEnv = newEnv(customTypeAdapter(new ExactAdapter()), declarations(declarations));
    Ast exactAst = compile(exactEnv, expression);
    Program exactNative = exactEnv.program(exactAst);
    Program exactDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));

    Env generalEnv = newEnv(declarations(declarations));
    Program general = generalEnv.program(compile(generalEnv, expression));
    return new Programs(exactNative, exactDisabled, general);
  }

  private static Ast compile(Env env, String expression) {
    var result = env.compile(expression);
    if (result.hasIssues()) {
      throw new IllegalStateException(expression + ": " + result.getIssues());
    }
    return result.getAst();
  }

  private static Decl[] listDeclarations(
      String prefix, int sourceCount, Type elementType, Decl... additional) {
    Decl[] result = new Decl[sourceCount + additional.length];
    for (int source = 0; source < sourceCount; source++) {
      result[source] = Decls.newVar(sourceName(prefix, source), Decls.newListType(elementType));
    }
    System.arraycopy(additional, 0, result, sourceCount, additional.length);
    return result;
  }

  private static String concatExpression(String prefix, int sourceCount) {
    StringBuilder expression = new StringBuilder();
    for (int source = 0; source < sourceCount; source++) {
      if (source != 0) {
        expression.append(" + ");
      }
      expression.append(sourceName(prefix, source));
    }
    return expression.toString();
  }

  private static String sourceName(String prefix, int source) {
    return prefix + source;
  }

  private static List<List<Long>> longSources(int sourceCount, int size) {
    List<List<Long>> sources = new ArrayList<>(sourceCount);
    for (int source = 0; source < sourceCount; source++) {
      List<Long> values = new ArrayList<>(size);
      long offset = Math.multiplyExact((long) source, size);
      for (int index = 0; index < size; index++) {
        values.add(offset + index);
      }
      sources.add(values);
    }
    return sources;
  }

  private static List<List<String>> stringSources(int sourceCount, int size) {
    List<List<String>> sources = new ArrayList<>(sourceCount);
    for (int source = 0; source < sourceCount; source++) {
      List<String> values = new ArrayList<>(size);
      for (int index = 0; index < size; index++) {
        values.add(stringValue(source, index));
      }
      sources.add(values);
    }
    return sources;
  }

  private static String stringValue(int source, int index) {
    return "value-" + source + "-" + index;
  }

  private static <T> Map<String, Object> sourceVariables(String prefix, List<List<T>> sources) {
    Map<String, Object> variables = new LinkedHashMap<>();
    for (int source = 0; source < sources.size(); source++) {
      variables.put(sourceName(prefix, source), sources.get(source));
    }
    return variables;
  }

  private record Programs(Program exactNative, Program exactDisabled, Program general) {}

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }
}
