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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

class NativeListConcatTest {
  private final Env env =
      newEnv(
          customTypeAdapter(new ExactAdapter()),
          declarations(
              Decls.newVar("leftInts", Decls.newListType(Decls.Int)),
              Decls.newVar("rightInts", Decls.newListType(Decls.Int)),
              Decls.newVar("leftUints", Decls.newListType(Decls.Uint)),
              Decls.newVar("rightUints", Decls.newListType(Decls.Uint)),
              Decls.newVar("leftStrings", Decls.newListType(Decls.String)),
              Decls.newVar("rightStrings", Decls.newListType(Decls.String))));

  @Test
  void exactConcatenationSupportsImmediateSizeAndBoundaryIndexes() {
    Map<String, Object> input =
        Map.of(
            "leftInts",
            new long[] {11L, 22L},
            "rightInts",
            List.of(33L, 44L),
            "leftUints",
            new long[] {-1L},
            "rightUints",
            List.of(ULong.valueOf(1L)),
            "leftStrings",
            List.of("left"),
            "rightStrings",
            new String[] {"right"});

    assertEquivalent("size(leftInts + rightInts)", input);
    assertEquivalent("(leftInts + rightInts)[0]", input);
    assertEquivalent("(leftInts + rightInts)[1]", input);
    assertEquivalent("(leftInts + rightInts)[2]", input);
    assertEquivalent("(leftInts + rightInts)[3]", input);
    assertEquivalent("(leftUints + rightUints)[0]", input);
    assertEquivalent("(leftStrings + rightStrings)[1]", input);
  }

  @Test
  void structuralSizeDoesNotInspectElementsAndSelectedViolationsRemainErrors() {
    List<Object> invalid = Arrays.asList(null, "not-an-int");
    Map<String, Object> input =
        Map.of(
            "leftInts", invalid,
            "rightInts", List.of(1L),
            "leftUints", List.of(ULong.valueOf(1L)),
            "rightUints", List.of(ULong.valueOf(2L)),
            "leftStrings", List.of("left"),
            "rightStrings", List.of("right"));

    assertThat(assertEquivalent("size(leftInts + rightInts)", input).intValue()).isEqualTo(3L);
    assertThat(assertEquivalent("(leftInts + rightInts)[0]", input)).isInstanceOf(Err.class);
    assertThat(assertEquivalent("(leftInts + rightInts)[1]", input)).isInstanceOf(Err.class);
    assertThat(program("(leftInts + rightInts)[2]", false).eval(input).getVal().intValue())
        .isEqualTo(1L);
  }

  @Test
  void outOfRangeIndexesPreserveEstablishedErrors() {
    Map<String, Object> input =
        Map.of(
            "leftInts", List.of(1L),
            "rightInts", List.of(2L),
            "leftUints", List.of(ULong.valueOf(1L)),
            "rightUints", List.of(ULong.valueOf(2L)),
            "leftStrings", List.of("left"),
            "rightStrings", List.of("right"));

    assertThat(assertEquivalent("(leftInts + rightInts)[-1]", input)).isInstanceOf(Err.class);
    assertThat(assertEquivalent("(leftInts + rightInts)[2]", input)).isInstanceOf(Err.class);
  }

  @Test
  void resolvesBothOperandsOnceInOrderAndKeepsLeftFailurePrecedence() {
    Program program = program("size(leftInts + rightInts)", false);
    List<String> resolutions = new ArrayList<>();
    AtomicInteger leftResolutions = new AtomicInteger();
    AtomicInteger rightResolutions = new AtomicInteger();
    ActivationFunction activation =
        name -> {
          resolutions.add(name);
          if (name.equals("leftInts")) {
            leftResolutions.incrementAndGet();
            return newErr("left failed");
          }
          if (name.equals("rightInts")) {
            rightResolutions.incrementAndGet();
            return newErr("right failed");
          }
          return ActivationFunction.ABSENT;
        };

    Val result = program.eval(activation).getVal();

    assertThat(result).isInstanceOf(Err.class);
    assertThat(result.toString()).contains("left failed");
    assertThat(resolutions).containsExactly("leftInts", "rightInts");
    assertThat(leftResolutions).hasValue(1);
    assertThat(rightResolutions).hasValue(1);
  }

  @Test
  void terminalAndRepeatedConcatenationRemainEstablishedCompatible() {
    Map<String, Object> input =
        Map.of(
            "leftInts", List.of(1L),
            "rightInts", List.of(2L),
            "leftUints", List.of(ULong.valueOf(1L)),
            "rightUints", List.of(ULong.valueOf(2L)),
            "leftStrings", List.of("left"),
            "rightStrings", List.of("right"));

    assertEquivalent("leftInts + rightInts", input);
    assertEquivalent("(leftInts + rightInts) + leftInts", input);
    assertEquivalent("(leftInts + rightInts)[0 + 1]", input);
  }

  private Val assertEquivalent(String expression, Object input) {
    Val nativeValue = program(expression, false).eval(input).getVal();
    Val establishedValue = program(expression, true).eval(input).getVal();

    assertThat(nativeValue.getClass()).isEqualTo(establishedValue.getClass());
    assertThat(nativeValue.type()).isEqualTo(establishedValue.type());
    if (nativeValue.type().typeEnum() == org.projectnessie.cel.common.types.ref.TypeEnum.List) {
      assertThat(nativeValue.equal(establishedValue).booleanValue()).isTrue();
    } else {
      assertThat(nativeValue.toString()).isEqualTo(establishedValue.toString());
    }
    if (!(nativeValue instanceof Err)
        && nativeValue.type().typeEnum() != org.projectnessie.cel.common.types.ref.TypeEnum.List) {
      assertThat(nativeValue.value()).isEqualTo(establishedValue.value());
    }
    return nativeValue;
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
