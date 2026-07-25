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

import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.customTypeProvider;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.types.jackson.JacksonRegistry;

/** End-to-end Jackson 2 exact aggregate-field consumer benchmarks. */
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class JacksonExactAggregateFieldBench {
  @State(Scope.Benchmark)
  public static class FieldState {
    @Param({
      "size",
      "index",
      "exists",
      "setMembership",
      "mapLookup",
      "mapLookupDynamic",
      "mapLookupComputed",
      "signedIntMapLookup",
      "signedIntMapMembership",
      "signedIntMapLookupDynamic"
    })
    public String operation;

    @Param({"0", "1", "16", "1024"})
    public int size;

    Program exactNative;
    Program exactDisabled;
    Program defaultEstablished;
    Map<String, Object> variables;
    AggregateInput input;

    @Setup
    public void setup() {
      String expression =
          switch (operation) {
            case "size" -> "size(input.values)";
            case "index" -> "cardinality == 0 ? -1 : input.values[0]";
            case "exists" -> "input.values.exists(value, value == needle)";
            case "setMembership" -> "needle in input.members";
            case "mapLookup" -> "cardinality == 0 ? -1 : input.lookup['last']";
            case "mapLookupDynamic" -> "cardinality == 0 ? -1 : input.lookup[key]";
            case "mapLookupComputed" -> "cardinality == 0 ? -1 : input.lookup['key-' + suffix]";
            case "signedIntMapLookup" -> "cardinality == 0 ? -1 : input.signedLookup[0]";
            case "signedIntMapMembership" -> "cardinality != 0 && 0 in input.signedLookup";
            case "signedIntMapLookupDynamic" ->
                "cardinality == 0 ? -1 : input.signedLookup[intKey]";
            default -> throw new IllegalArgumentException(operation);
          };
      input = AggregateInput.create(size);
      long needle = size == 0 ? -1L : size - 1L;
      variables =
          Map.of(
              "input",
              input,
              "cardinality",
              (long) size,
              "needle",
              needle,
              "intKey",
              needle,
              "key",
              "key-" + (size - 1),
              "suffix",
              Integer.toString(size - 1));

      TypeRegistry exact = JacksonRegistry.newExactAggregateRegistry();
      Env exactEnv = environment(exact);
      Ast exactAst = compile(exactEnv, expression);
      exactNative = exactEnv.program(exactAst);
      exactDisabled = exactEnv.program(exactAst, evalOptions(OptDisableNativeEval));

      TypeRegistry general = JacksonRegistry.newRegistry();
      Env generalEnv = environment(general);
      defaultEstablished = generalEnv.program(compile(generalEnv, expression));
    }
  }

  @Benchmark
  public Object exactNative(FieldState state) {
    return state.exactNative.eval(state.variables);
  }

  @Benchmark
  public Object exactDisabled(FieldState state) {
    return state.exactDisabled.eval(state.variables);
  }

  @Benchmark
  public Object defaultEstablished(FieldState state) {
    return state.defaultEstablished.eval(state.variables);
  }

  @Benchmark
  public Object javaCeiling(FieldState state) {
    return switch (state.operation) {
      case "size" -> state.input.values.size();
      case "index" -> state.size == 0 ? -1L : state.input.values.get(0);
      case "exists", "setMembership" -> state.input.members.contains((long) state.size - 1L);
      case "mapLookup" -> state.size == 0 ? -1L : state.input.lookup.get("last");
      case "mapLookupDynamic", "mapLookupComputed" ->
          state.size == 0 ? -1L : state.input.lookup.get("key-" + (state.size - 1));
      case "signedIntMapLookup" -> state.size == 0 ? -1L : state.input.signedLookup.get(0);
      case "signedIntMapMembership" -> state.size != 0 && state.input.signedLookup.containsKey(0);
      case "signedIntMapLookupDynamic" ->
          state.size == 0 ? -1L : state.input.signedLookup.get(state.size - 1);
      default -> throw new IllegalArgumentException(state.operation);
    };
  }

  private static Env environment(TypeRegistry registry) {
    return newEnv(
        customTypeAdapter(registry),
        customTypeProvider(registry),
        types(AggregateInput.class),
        declarations(
            Decls.newVar("input", Decls.newObjectType(AggregateInput.class.getName())),
            Decls.newVar("cardinality", Decls.Int),
            Decls.newVar("needle", Decls.Int),
            Decls.newVar("intKey", Decls.Int),
            Decls.newVar("key", Decls.String),
            Decls.newVar("suffix", Decls.String)));
  }

  private static Ast compile(Env env, String expression) {
    var result = env.compile(expression);
    if (result.hasIssues()) {
      throw new IllegalStateException(result.getIssues().toString());
    }
    return result.getAst();
  }

  @SuppressWarnings("unused")
  public static final class AggregateInput {
    private final List<Long> values;
    private final Set<Long> members;
    private final Map<String, Long> lookup;
    private final Map<Integer, Long> signedLookup;

    private AggregateInput(
        List<Long> values,
        Set<Long> members,
        Map<String, Long> lookup,
        Map<Integer, Long> signedLookup) {
      this.values = values;
      this.members = members;
      this.lookup = lookup;
      this.signedLookup = signedLookup;
    }

    static AggregateInput create(int size) {
      List<Long> values = new ArrayList<>(size);
      Set<Long> members = new LinkedHashSet<>();
      Map<String, Long> lookup = new LinkedHashMap<>();
      Map<Integer, Long> signedLookup = new LinkedHashMap<>();
      for (int key = 0; key < size; key++) {
        long value = key;
        values.add(value);
        members.add(value);
        lookup.put("key-" + value, value);
        signedLookup.put(key, value);
      }
      if (size > 0) {
        lookup.put("last", (long) size - 1L);
      }
      return new AggregateInput(values, members, lookup, signedLookup);
    }

    public List<Long> getValues() {
      return values;
    }

    public Set<Long> getMembers() {
      return members;
    }

    public Map<String, Long> getLookup() {
      return lookup;
    }

    public Map<Integer, Long> getSignedLookup() {
      return signedLookup;
    }
  }
}
