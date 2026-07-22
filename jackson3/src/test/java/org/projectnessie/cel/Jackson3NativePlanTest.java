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

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.customTypeProvider;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.ref.ExactAggregateFieldProvider;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarFieldProvider;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.types.jackson3.Jackson3Registry;

class Jackson3NativePlanTest {
  @Test
  void registryDeclaresStandardScalarSemantics() {
    assertThat(Jackson3Registry.newRegistry()).isInstanceOf(StandardScalarTypeAdapter.class);
    assertThat(Jackson3Registry.newRegistry()).isInstanceOf(StandardScalarFieldProvider.class);
    assertThat(Jackson3Registry.newRegistry()).isNotInstanceOf(ExactAggregateTypeAdapter.class);
    assertThat(Jackson3Registry.newRegistry()).isNotInstanceOf(ExactAggregateFieldProvider.class);
  }

  @Test
  void exactRegistryAndCopiesPreserveExplicitAggregateContract() {
    TypeRegistry exact = Jackson3Registry.newExactAggregateRegistry();
    exact.register(AggregateInput.class);
    TypeRegistry exactCopy = exact.copy();
    TypeRegistry defaultCopy = Jackson3Registry.newRegistry().copy();

    assertThat(exact).isInstanceOf(ExactAggregateTypeAdapter.class);
    assertThat(exact).isInstanceOf(ExactAggregateFieldProvider.class);
    assertThat(exact).isInstanceOf(StandardScalarTypeAdapter.class);
    assertThat(exact).isInstanceOf(StandardScalarFieldProvider.class);
    assertThat(exactCopy).isInstanceOf(ExactAggregateTypeAdapter.class);
    assertThat(exactCopy).isInstanceOf(ExactAggregateFieldProvider.class);
    assertThat(exactCopy.findType(AggregateInput.class.getName())).isNotNull();
    assertThat(defaultCopy).isNotInstanceOf(ExactAggregateTypeAdapter.class);
    assertThat(defaultCopy).isNotInstanceOf(ExactAggregateFieldProvider.class);
  }

  @Test
  void exactRegistryMaterializesCheckedSignedUnsignedAndNestedAggregates() {
    TypeRegistry registry = Jackson3Registry.newExactAggregateRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            declarations(
                Decls.newVar("signed", Decls.newListType(Decls.Int)),
                Decls.newVar("unsigned", Decls.newListType(Decls.Uint)),
                Decls.newVar(
                    "nested", Decls.newMapType(Decls.String, Decls.newListType(Decls.Uint)))));
    Ast ast =
        compile(
            env,
            "signed == [1, -1] && "
                + "unsigned == [1u, 18446744073709551615u] && "
                + "nested == {'bits': [18446744073709551615u]}");
    Program enabled = env.program(ast);
    Prog disabled = (Prog) env.program(ast, evalOptions(OptDisableNativeEval));
    Map<String, Object> input =
        Map.of(
            "signed", new long[] {1L, -1L},
            "unsigned", List.of(1L, -1L),
            "nested", Map.of("bits", new long[] {-1L}));

    assertThat(disabled.interpretable.getClass().getSimpleName()).isNotEqualTo("NativeIsland");
    assertEquivalent(enabled.eval(input).getVal(), disabled.eval(input).getVal());
    assertThat(enabled.eval(input).getVal().booleanValue()).isTrue();
  }

  @Test
  void exactRegistryPreservesPresentNullAndReportsTypeMismatchInBothModes() {
    TypeRegistry registry = Jackson3Registry.newExactAggregateRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            declarations(
                Decls.newVar("nullable", Decls.newMapType(Decls.String, Decls.Null)),
                Decls.newVar("numbers", Decls.newListType(Decls.Int))));
    Ast nullAst = compile(env, "nullable['present'] == null && !('absent' in nullable)");
    Ast mismatchAst = compile(env, "numbers[0] == 1");
    Map<String, Object> nullable = new HashMap<>();
    nullable.put("present", null);

    assertEnabledDisabledEquivalent(
        env, nullAst, Map.of("nullable", nullable, "numbers", List.of(1L)));
    Val mismatch =
        assertEnabledDisabledEquivalent(
            env, mismatchAst, Map.of("nullable", nullable, "numbers", List.of("wrong")));
    assertThat(mismatch).matches(Err::isError);
  }

  @Test
  void exactRegistryMaterializesCheckedAggregateFieldsInBothModes() {
    TypeRegistry registry = Jackson3Registry.newExactAggregateRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(AggregateInput.class),
            declarations(
                Decls.newVar("input", Decls.newObjectType(AggregateInput.class.getName()))));
    Ast ast =
        compile(
            env,
            "input.numbers == [1, 2] && "
                + "input.unsigned == [1u, 18446744073709551615u] && "
                + "input.nested == {'bits': [3, 4]} && "
                + "input.tags == ['first', 'second'] && "
                + "input.optionalNumbers == [5, 6]");
    AggregateInput input =
        new AggregateInput(
            List.of(1L, 2L),
            List.of(ULong.valueOf(1L), ULong.valueOf(-1L)),
            Map.of("bits", List.of(3L, 4L)),
            new LinkedHashSet<>(List.of("first", "second")),
            Optional.of(List.of(5L, 6L)));

    Val result = assertEnabledDisabledEquivalent(env, ast, Map.of("input", input));
    assertThat(result.booleanValue()).isTrue();
    assertThat(input.numbersReadCount()).isEqualTo(2);
    assertThat(input.optionalNumbersReadCount()).isEqualTo(2);
  }

  @Test
  void exactAggregateFieldsBecomeNativeSourcesWithoutGetterReplay() {
    TypeRegistry registry = Jackson3Registry.newExactAggregateRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(AggregateInput.class),
            declarations(
                Decls.newVar("input", Decls.newObjectType(AggregateInput.class.getName()))));

    for (String expression :
        List.of(
            "size(input.numbers)",
            "input.numbers[1]",
            "input.numbers.exists(number, number == 2)")) {
      Ast ast = compile(env, expression);
      Prog enabled = (Prog) env.program(ast);
      Prog disabled = (Prog) env.program(ast, evalOptions(OptDisableNativeEval));
      AggregateInput enabledInput = aggregateInput();
      AggregateInput disabledInput = aggregateInput();

      assertThat(enabled.interpretable.getClass().getSimpleName())
          .as(expression)
          .isEqualTo("NativeIsland");
      assertThat(disabled.interpretable.getClass().getSimpleName())
          .as(expression)
          .isNotEqualTo("NativeIsland");
      assertEquivalent(
          enabled.eval(Map.of("input", enabledInput)).getVal(),
          disabled.eval(Map.of("input", disabledInput)).getVal());
      assertThat(enabledInput.numbersReadCount()).as(expression).isEqualTo(1);
      assertThat(disabledInput.numbersReadCount()).as(expression).isEqualTo(1);
    }
  }

  @Test
  void exactRegistryReportsEmptyOptionalAggregateWithoutReplayingGetter() {
    TypeRegistry registry = Jackson3Registry.newExactAggregateRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(AggregateInput.class),
            declarations(
                Decls.newVar("input", Decls.newObjectType(AggregateInput.class.getName()))));
    Ast ast = compile(env, "input.optionalNumbers == [5, 6]");
    AggregateInput input =
        new AggregateInput(
            List.of(1L, 2L),
            List.of(ULong.valueOf(1L)),
            Map.of("bits", List.of(3L, 4L)),
            Set.of("first"),
            Optional.empty());

    Val result = assertEnabledDisabledEquivalent(env, ast, Map.of("input", input));

    assertThat(result).matches(Err::isError);
    assertThat(input.optionalNumbersReadCount()).isEqualTo(2);
  }

  @Test
  void evaluatesTopLevelStringSelectionLikeCurrentEvaluator() {
    Jackson3Registry registry = (Jackson3Registry) Jackson3Registry.newRegistry();
    Env env = env(registry);
    Ast ast = compile(env, "input.text == expected");

    Prog nativeProgram = (Prog) env.program(ast);
    Program currentProgram = env.program(ast, evalOptions(OptDisableNativeEval));
    assertThat(nativeProgram.interpretable.getClass().getSimpleName()).isEqualTo("NativeIsland");

    assertEquivalent(
        nativeProgram.eval(Map.of("input", new Input("cel", 42L), "expected", "cel")).getVal(),
        currentProgram.eval(Map.of("input", new Input("cel", 42L), "expected", "cel")).getVal());

    assertEquivalent(
        nativeProgram.eval(Map.of("input", new Input(null, 42L), "expected", "cel")).getVal(),
        currentProgram.eval(Map.of("input", new Input(null, 42L), "expected", "cel")).getVal());

    assertEquivalent(
        nativeProgram.eval(Map.of("input", "wrong object", "expected", "cel")).getVal(),
        currentProgram.eval(Map.of("input", "wrong object", "expected", "cel")).getVal());

    assertEquivalent(
        nativeProgram.eval(Map.of("expected", "cel")).getVal(),
        currentProgram.eval(Map.of("expected", "cel")).getVal());

    Map<String, Object> nullInput = new HashMap<>();
    nullInput.put("input", null);
    nullInput.put("expected", "cel");
    assertEquivalent(
        nativeProgram.eval(nullInput).getVal(), currentProgram.eval(nullInput).getVal());
  }

  @Test
  void copiedRegistryRemainsEligible() {
    Jackson3Registry original = (Jackson3Registry) Jackson3Registry.newRegistry();
    original.register(Input.class);
    TypeRegistry copied = original.copy();

    assertThat(copied).isInstanceOf(StandardScalarTypeAdapter.class);
    Env env = env((Jackson3Registry) copied);
    Prog program = (Prog) env.program(compile(env, "input.text == 'cel'"));

    assertThat(program.interpretable.getClass().getSimpleName()).isEqualTo("NativeIsland");
    assertThat(program.eval(Map.of("input", new Input("cel", 42L))).getVal().booleanValue())
        .isTrue();
  }

  @Test
  void numericSelectionStaysOnCurrentEvaluator() {
    Jackson3Registry registry = (Jackson3Registry) Jackson3Registry.newRegistry();
    Env env = env(registry);
    Prog program = (Prog) env.program(compile(env, "input.number == 42"));

    assertThat(program.interpretable).isNotNull();
    assertThat(program.interpretable.getClass().getSimpleName()).isNotEqualTo("NativeIsland");
    assertThat(program.eval(Map.of("input", new Input("cel", 42L))).getVal().booleanValue())
        .isTrue();
  }

  private static Env env(Jackson3Registry registry) {
    return newEnv(
        customTypeAdapter(registry),
        customTypeProvider(registry),
        types(Input.class),
        declarations(
            Decls.newVar("input", Decls.newObjectType(Input.class.getName())),
            Decls.newVar("expected", Decls.String)));
  }

  private static Ast compile(Env env, String expression) {
    AstIssuesTuple result = env.compile(expression);
    assertThat(result.hasIssues()).as(expression).isFalse();
    return result.getAst();
  }

  private static void assertEquivalent(Val actual, Val expected) {
    assertThat(actual.getClass()).isEqualTo(expected.getClass());
    assertThat(actual.type()).isSameAs(expected.type());
    assertThat(actual.toString()).isEqualTo(expected.toString());
    if (actual instanceof Err actualError && expected instanceof Err expectedError) {
      assertThat(actualError.hasCause()).isEqualTo(expectedError.hasCause());
      if (actualError.hasCause()) {
        assertThat(actualError.getCause().getClass())
            .isEqualTo(expectedError.getCause().getClass());
        assertThat(actualError.getCause().getMessage())
            .isEqualTo(expectedError.getCause().getMessage());
      }
      return;
    }
    assertThat(actual.value()).isEqualTo(expected.value());
  }

  private static Val assertEnabledDisabledEquivalent(Env env, Ast ast, Object input) {
    Program enabled = env.program(ast);
    Prog disabled = (Prog) env.program(ast, evalOptions(OptDisableNativeEval));

    assertThat(disabled.interpretable.getClass().getSimpleName()).isNotEqualTo("NativeIsland");
    Val result = enabled.eval(input).getVal();
    assertEquivalent(result, disabled.eval(input).getVal());
    return result;
  }

  private static AggregateInput aggregateInput() {
    return new AggregateInput(
        List.of(1L, 2L),
        List.of(ULong.valueOf(1L), ULong.valueOf(-1L)),
        Map.of("bits", List.of(3L, 4L)),
        new LinkedHashSet<>(List.of("first", "second")),
        Optional.of(List.of(5L, 6L)));
  }

  @SuppressWarnings({"unused", "ClassCanBeRecord"})
  public static final class Input {
    private final String text;
    private final long number;

    public Input(String text, long number) {
      this.text = text;
      this.number = number;
    }

    public String getText() {
      return text;
    }

    public long getNumber() {
      return number;
    }
  }

  @SuppressWarnings({"unused", "ClassCanBeRecord"})
  public static final class AggregateInput {
    private final List<Long> numbers;
    private final List<ULong> unsigned;
    private final Map<String, List<Long>> nested;
    private final Set<String> tags;
    private final Optional<List<Long>> optionalNumbers;
    private int numbersReadCount;
    private int optionalNumbersReadCount;

    public AggregateInput(
        List<Long> numbers,
        List<ULong> unsigned,
        Map<String, List<Long>> nested,
        Set<String> tags,
        Optional<List<Long>> optionalNumbers) {
      this.numbers = numbers;
      this.unsigned = unsigned;
      this.nested = nested;
      this.tags = tags;
      this.optionalNumbers = optionalNumbers;
    }

    public List<Long> getNumbers() {
      numbersReadCount++;
      return numbers;
    }

    public List<ULong> getUnsigned() {
      return unsigned;
    }

    public Map<String, List<Long>> getNested() {
      return nested;
    }

    public Set<String> getTags() {
      return tags;
    }

    public Optional<List<Long>> getOptionalNumbers() {
      optionalNumbersReadCount++;
      return optionalNumbers;
    }

    int numbersReadCount() {
      return numbersReadCount;
    }

    int optionalNumbersReadCount() {
      return optionalNumbersReadCount;
    }
  }
}
