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

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.UnknownT;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

class NativeListSourceTest {
  private final ExactAdapter adapter = new ExactAdapter();
  private final Env env =
      newEnv(
          customTypeAdapter(adapter),
          declarations(
              Decls.newVar("values", Decls.newListType(Decls.Int)),
              Decls.newVar("uints", Decls.newListType(Decls.Uint)),
              Decls.newVar("strings", Decls.newListType(Decls.String)),
              Decls.newVar("index", Decls.Int)));

  @Test
  void exactSourcesSupportStructuralAndIndexedConsumersWithoutChangingResults() {
    Map<String, Object> input =
        Map.of(
            "values",
            new LinkedHashSet<>(List.of(11L, 22L, 33L)),
            "uints",
            new long[] {-1L, 1L},
            "index",
            1L);

    assertEquivalent("size(values)", input);
    assertEquivalent("values[0]", input);
    assertEquivalent("values[2]", input);
    assertEquivalent("values[index]", input);
    assertEquivalent("uints[0]", input);
  }

  @Test
  void structuralMutationsCompletedBetweenEvaluationsAreVisible() {
    List<Long> values = new ArrayList<>(List.of(1L, 2L));
    Map<String, Object> input = Map.of("values", values);
    String expression = "size(values) * 100 + values[size(values) - 1]";
    Program nativeProgram = program(expression, false);
    Program establishedProgram = program(expression, true);

    assertThat(nativeProgram.eval(input).getVal()).isEqualTo(intOf(202));
    assertThat(establishedProgram.eval(input).getVal()).isEqualTo(intOf(202));

    values.add(3L);
    assertThat(nativeProgram.eval(input).getVal()).isEqualTo(intOf(303));
    assertThat(establishedProgram.eval(input).getVal()).isEqualTo(intOf(303));

    values.subList(0, 2).clear();
    values.set(0, 9L);
    assertThat(nativeProgram.eval(input).getVal()).isEqualTo(intOf(109));
    assertThat(establishedProgram.eval(input).getVal()).isEqualTo(intOf(109));
  }

  @Test
  void exactSourcesSupportQuantifiersAndMappedFilteredConsumers() {
    Map<String, Object> input =
        Map.of(
            "values",
            new LinkedHashSet<>(List.of(1L, 2L, 3L)),
            "uints",
            List.of(ULong.valueOf(-1L), ULong.valueOf(1L)),
            "index",
            0L);

    assertEquivalent("values.exists(v, v == 2)", input);
    assertEquivalent("values.all(v, v > 0)", input);
    assertEquivalent("uints.exists(v, v == 18446744073709551615u)", input);
    assertEquivalent("values.filter(v, v > 1).map(v, v)[0]", input);
  }

  @Test
  void dynamicIndexResolvesEachOperandOnce() {
    Program nativeProgram = program("values[index]", false);
    AtomicInteger valuesResolutions = new AtomicInteger();
    AtomicInteger indexResolutions = new AtomicInteger();
    ActivationFunction activation =
        name -> {
          if (name.equals("values")) {
            valuesResolutions.incrementAndGet();
            return new long[] {10L, 20L, 30L};
          }
          if (name.equals("index")) {
            indexResolutions.incrementAndGet();
            return 1L;
          }
          return ActivationFunction.ABSENT;
        };

    assertThat(nativeProgram.eval(activation).getVal().intValue()).isEqualTo(20L);
    assertThat(valuesResolutions).hasValue(1);
    assertThat(indexResolutions).hasValue(1);
  }

  @Test
  void visitedExactSourceViolationsRemainCheckedErrors() {
    Map<String, Object> input =
        Map.of(
            "values",
            List.of("not-an-int"),
            "uints",
            List.of(ULong.valueOf(1L)),
            "strings",
            new String[] {null},
            "index",
            0L);

    assertThat(assertEquivalent("values[0] == 1", input)).isInstanceOf(Err.class);
    assertThat(assertEquivalent("values.exists(v, v == 1)", input)).isInstanceOf(Err.class);
    assertThat(assertEquivalent("strings[0] == 'x'", input)).isInstanceOf(Err.class);
    assertThat(assertEquivalent("strings.exists(s, s == 'x')", input)).isInstanceOf(Err.class);

    Map<String, Object> embedded =
        Map.of(
            "values", List.of(intOf(1)),
            "uints", List.of(ULong.valueOf(1L)),
            "strings", List.of("value"),
            "index", 0L);
    assertThat(assertEquivalent("values[0] == 1", embedded)).isInstanceOf(Err.class);
    assertThat(assertEquivalent("values.exists(v, v == 1)", embedded)).isInstanceOf(Err.class);
  }

  @Test
  void generalUnsignedSourcesDoNotReinterpretSignedLongArrays() {
    Env general =
        newEnv(
            declarations(
                Decls.newVar("uints", Decls.newListType(Decls.Uint)),
                Decls.newVar("index", Decls.Int)));
    Object input = Map.of("uints", new long[] {-1L}, "index", 0L);

    assertEquivalent(general, "uints[0] == 18446744073709551615u", input);
    assertEquivalent(general, "uints.exists(v, v == 18446744073709551615u)", input);
  }

  @Test
  void exceptionalSourceSuppressesDynamicIndexAndHostFailuresBecomeErrors() {
    AtomicInteger indexResolutions = new AtomicInteger();
    ActivationFunction missingSource =
        name -> {
          if (name.equals("index")) {
            indexResolutions.incrementAndGet();
            return 0L;
          }
          return ActivationFunction.ABSENT;
        };

    assertThat(program("values[index]", false).eval(missingSource).getVal())
        .isInstanceOf(Err.class);
    assertThat(indexResolutions).hasValue(0);

    ActivationFunction errorSource =
        name -> {
          if (name.equals("values")) {
            return newErr("source failed");
          }
          if (name.equals("index")) {
            indexResolutions.incrementAndGet();
            return 0L;
          }
          return ActivationFunction.ABSENT;
        };
    assertThat(program("values[index]", false).eval(errorSource).getVal()).isInstanceOf(Err.class);
    assertThat(indexResolutions).hasValue(0);

    ActivationFunction unknownSource =
        name -> {
          if (name.equals("values")) {
            return unknownOf(7L);
          }
          if (name.equals("index")) {
            indexResolutions.incrementAndGet();
            return 0L;
          }
          return ActivationFunction.ABSENT;
        };
    assertThat(program("values[index]", false).eval(unknownSource).getVal())
        .isInstanceOf(UnknownT.class);
    assertThat(indexResolutions).hasValue(0);

    ActivationFunction throwingIndex =
        name -> {
          if (name.equals("values")) {
            return new long[] {1L};
          }
          if (name.equals("index")) {
            throw new IllegalStateException("index failed");
          }
          return ActivationFunction.ABSENT;
        };
    assertThat(program("values[index]", false).eval(throwingIndex).getVal())
        .isInstanceOf(Err.class);

    List<Long> throwing =
        new AbstractList<>() {
          @Override
          public Long get(int index) {
            throw new IllegalStateException("get failed");
          }

          @Override
          public int size() {
            return 1;
          }
        };
    assertThat(
            program("values[0]", false)
                .eval(
                    Map.of(
                        "values",
                        throwing,
                        "uints",
                        List.of(ULong.valueOf(1L)),
                        "strings",
                        List.of("value"),
                        "index",
                        0L))
                .getVal())
        .isInstanceOf(Err.class);
  }

  @Test
  void indexesOutsideTheIntegerRangeRemainOutOfRange() {
    Map<String, Object> input =
        Map.of(
            "values",
            new long[] {11L},
            "uints",
            List.of(ULong.valueOf(1L)),
            "strings",
            List.of("value"),
            "index",
            4_294_967_296L);
    Val result = assertEquivalent("values[index]", input);

    assertThat(result).isInstanceOf(Err.class);
    assertThat(result.toString()).contains("4294967296");
  }

  private Val assertEquivalent(String expression, Object input) {
    Val nativeValue = program(expression, false).eval(input).getVal();
    Val establishedValue = program(expression, true).eval(input).getVal();

    assertThat(nativeValue.getClass()).isEqualTo(establishedValue.getClass());
    assertThat(nativeValue.type()).isEqualTo(establishedValue.type());
    assertThat(nativeValue.toString()).isEqualTo(establishedValue.toString());
    if (nativeValue instanceof Err) {
      return nativeValue;
    }
    assertThat(nativeValue.value()).isEqualTo(establishedValue.value());
    return nativeValue;
  }

  private static void assertEquivalent(Env env, String expression, Object input) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    Val nativeValue = env.program(compiled.getAst()).eval(input).getVal();
    Val establishedValue =
        env.program(compiled.getAst(), evalOptions(OptDisableNativeEval)).eval(input).getVal();

    assertThat(nativeValue.getClass()).isEqualTo(establishedValue.getClass());
    assertThat(nativeValue.toString()).isEqualTo(establishedValue.toString());
    assertThat(nativeValue.booleanValue()).isEqualTo(false);
  }

  private Program program(String expression, boolean disableNative) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    return disableNative
        ? env.program(compiled.getAst(), evalOptions(OptDisableNativeEval))
        : env.program(compiled.getAst());
  }

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }
}
