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
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.ref.ExactAggregateFieldProvider;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarFieldProvider;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.types.jackson.JacksonRegistry;

class JacksonNativePlanTest {

  @Test
  void registryOptsIntoIntegratedScalarPlanningButNotNativeFieldAccess() {
    TypeRegistry registry = JacksonRegistry.newRegistry();

    assertThat(registry).isInstanceOf(StandardScalarTypeAdapter.class);
    assertThat(registry).isNotInstanceOf(StandardScalarFieldProvider.class);

    Env scalarEnv =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            declarations(Decls.newVar("x", Decls.Int)));
    Prog scalarProgram = (Prog) scalarEnv.program(compile(scalarEnv, "x + 1"));
    assertThat(scalarProgram.interpretable.getClass().getSimpleName()).isEqualTo("NativeIsland");
    assertThat(scalarProgram.eval(Map.of("x", 50_021L)).getVal().intValue()).isEqualTo(50_022L);
  }

  @Test
  void exactRegistryAndCopiesPreserveOnlyTheExplicitAggregateContract() {
    TypeRegistry exact = JacksonRegistry.newExactAggregateRegistry();
    exact.register(AggregateInput.class);
    TypeRegistry exactCopy = exact.copy();
    TypeRegistry defaultCopy = JacksonRegistry.newRegistry().copy();

    assertThat(exact).isInstanceOf(ExactAggregateTypeAdapter.class);
    assertThat(exact).isInstanceOf(ExactAggregateFieldProvider.class);
    assertThat(exact).isInstanceOf(StandardScalarTypeAdapter.class);
    assertThat(exact).isNotInstanceOf(StandardScalarFieldProvider.class);
    assertThat(exactCopy).isInstanceOf(ExactAggregateTypeAdapter.class);
    assertThat(exactCopy).isInstanceOf(ExactAggregateFieldProvider.class);
    assertThat(exactCopy).isNotInstanceOf(StandardScalarFieldProvider.class);
    assertThat(exactCopy.findType(AggregateInput.class.getName())).isNotNull();
    assertThat(defaultCopy).isNotInstanceOf(ExactAggregateTypeAdapter.class);
    assertThat(defaultCopy).isNotInstanceOf(ExactAggregateFieldProvider.class);
  }

  @Test
  void exactRegistryMaterializesGenericAggregateFieldsInBothModes() {
    TypeRegistry registry = JacksonRegistry.newExactAggregateRegistry();
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
    TypeRegistry registry = JacksonRegistry.newExactAggregateRegistry();
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
  void exactMapFieldsSupportCheckedDynamicStringLookupForEveryScalarKind() {
    TypeRegistry registry = JacksonRegistry.newExactAggregateRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(DynamicMapInput.class),
            declarations(
                Decls.newVar("input", Decls.newObjectType(DynamicMapInput.class.getName())),
                Decls.newVar("key", Decls.String),
                Decls.newVar("prefix", Decls.String),
                Decls.newVar("suffix", Decls.String)));
    DynamicMapInput input = dynamicMapInput();
    Map<String, Object> activation =
        Map.of("input", input, "key", "one", "prefix", "o", "suffix", "ne");

    for (String expression :
        List.of(
            "input.booleans[key]",
            "input.integers[key]",
            "input.unsigned[key]",
            "input.doubles[key]",
            "input.texts[key]",
            "input.integers[input.lookupKey]",
            "input.integers[prefix + suffix]")) {
      Ast ast = compile(env, expression);
      Prog enabled = (Prog) env.program(ast);
      Prog disabled = (Prog) env.program(ast, evalOptions(OptDisableNativeEval));

      assertThat(enabled.interpretable.getClass().getSimpleName())
          .as(expression)
          .isEqualTo("NativeIsland");
      assertThat(disabled.interpretable.getClass().getSimpleName())
          .as(expression)
          .isNotEqualTo("NativeIsland");
      Val result = enabled.eval(activation).getVal();
      assertEquivalent(result, disabled.eval(activation).getVal());
      assertThat(result).as(expression).isNotInstanceOf(Err.class);
    }
  }

  @Test
  void checkedDynamicNullMapLookupDistinguishesPresentNullFromAbsent() {
    TypeRegistry registry = JacksonRegistry.newExactAggregateRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            declarations(
                Decls.newVar("nulls", Decls.newMapType(Decls.String, Decls.Null)),
                Decls.newVar("key", Decls.String)));
    Map<String, Object> nulls = new HashMap<>();
    nulls.put("present", null);
    Ast lookup = compile(env, "nulls[key]");

    assertThat(((Prog) env.program(lookup)).interpretable.getClass().getSimpleName())
        .isEqualTo("NativeIsland");

    Val present =
        assertEnabledDisabledEquivalent(env, lookup, Map.of("nulls", nulls, "key", "present"));
    Val absent =
        assertEnabledDisabledEquivalent(env, lookup, Map.of("nulls", nulls, "key", "absent"));

    assertThat(present).isSameAs(NullT.NullValue);
    assertThat(absent).matches(Err::isError);
  }

  @Test
  void repeatedExactListFieldsSupportNativeConcatSizeAndIndex() {
    TypeRegistry registry = JacksonRegistry.newExactAggregateRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(AggregateInput.class),
            declarations(
                Decls.newVar("input", Decls.newObjectType(AggregateInput.class.getName()))));

    for (var evaluation :
        Map.of(
                "size(input.numbers + input.numbers + input.numbers)", 6L,
                "(input.numbers + input.numbers + input.numbers)[4]", 1L)
            .entrySet()) {
      String expression = evaluation.getKey();
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
      Val enabledResult = enabled.eval(Map.of("input", enabledInput)).getVal();
      Val disabledResult = disabled.eval(Map.of("input", disabledInput)).getVal();
      assertEquivalent(enabledResult, disabledResult);
      assertThat(enabledResult.intValue()).as(expression).isEqualTo(evaluation.getValue());
      assertThat(enabledInput.numbersReadCount()).as(expression).isEqualTo(3);
      assertThat(disabledInput.numbersReadCount()).as(expression).isEqualTo(3);
    }
  }

  @Test
  void repeatedExactNonScalarListFieldsSupportNativeConcatSize() {
    TypeRegistry registry = JacksonRegistry.newExactAggregateRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(NestedAggregateInput.class),
            declarations(
                Decls.newVar("input", Decls.newObjectType(NestedAggregateInput.class.getName()))));
    Ast ast = compile(env, "size(input.entries + input.entries + input.entries)");
    Prog enabled = (Prog) env.program(ast);
    Prog disabled = (Prog) env.program(ast, evalOptions(OptDisableNativeEval));
    NestedAggregateInput enabledInput = nestedAggregateInput();
    NestedAggregateInput disabledInput = nestedAggregateInput();

    assertThat(enabled.interpretable.getClass().getSimpleName()).isEqualTo("NativeIsland");
    assertThat(disabled.interpretable.getClass().getSimpleName()).isNotEqualTo("NativeIsland");
    Val enabledResult = enabled.eval(Map.of("input", enabledInput)).getVal();
    assertEquivalent(enabledResult, disabled.eval(Map.of("input", disabledInput)).getVal());
    assertThat(enabledResult.intValue()).isEqualTo(6L);
    assertThat(enabledInput.entriesReadCount()).isEqualTo(3);
    assertThat(disabledInput.entriesReadCount()).isEqualTo(3);
  }

  @Test
  void exactRegistryReportsEmptyOptionalAggregateWithoutReplayingGetter() {
    TypeRegistry registry = JacksonRegistry.newExactAggregateRegistry();
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
  void exactRegistryKeepsJacksonScalarSelectorsOnEstablishedEvaluation() {
    TypeRegistry registry = JacksonRegistry.newExactAggregateRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(Person.class),
            declarations(
                Decls.newVar("person", Decls.newObjectType(Person.class.getName())),
                Decls.newVar("expected", Decls.String)));
    Prog program = (Prog) env.program(compile(env, "person.email == expected"));

    assertThat(program.interpretable.getClass().getSimpleName()).isNotEqualTo("NativeIsland");
    assertThat(
            program
                .eval(Map.of("person", new Person("cel", 50_021L), "expected", "cel"))
                .getVal()
                .booleanValue())
        .isTrue();
  }

  @Test
  void jacksonSelectorsRemainOnTheCurrentEvaluator() {
    TypeRegistry registry = JacksonRegistry.newRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(Person.class),
            declarations(Decls.newVar("person", Decls.newObjectType(Person.class.getName()))));

    for (String expression : new String[] {"person.email", "person.priority"}) {
      Prog program = (Prog) env.program(compile(env, expression));
      assertThat(program.interpretable)
          .as(expression)
          .isNotNull()
          .extracting(value -> value.getClass().getSimpleName())
          .isNotEqualTo("NativeIsland");
    }

    Prog stringProgram = (Prog) env.program(compile(env, "person.email"));
    assertThat(stringProgram.eval(Map.of("person", new Person("cel", 50_021L))).getVal().value())
        .isEqualTo("cel");
  }

  private static Ast compile(Env env, String expression) {
    AstIssuesTuple result = env.compile(expression);
    assertThat(result.hasIssues()).as(expression).isFalse();
    return result.getAst();
  }

  private static Val assertEnabledDisabledEquivalent(Env env, Ast ast, Object input) {
    Program enabled = env.program(ast);
    Prog disabled = (Prog) env.program(ast, evalOptions(OptDisableNativeEval));

    assertThat(disabled.interpretable.getClass().getSimpleName()).isNotEqualTo("NativeIsland");
    Val result = enabled.eval(input).getVal();
    assertEquivalent(result, disabled.eval(input).getVal());
    return result;
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

  private static AggregateInput aggregateInput() {
    return new AggregateInput(
        List.of(1L, 2L),
        List.of(ULong.valueOf(1L), ULong.valueOf(-1L)),
        Map.of("bits", List.of(3L, 4L)),
        new LinkedHashSet<>(List.of("first", "second")),
        Optional.of(List.of(5L, 6L)));
  }

  private static NestedAggregateInput nestedAggregateInput() {
    return new NestedAggregateInput(List.of(Map.of("value", 1L), Map.of("value", 2L)));
  }

  private static DynamicMapInput dynamicMapInput() {
    return new DynamicMapInput(
        Map.of("one", true),
        Map.of("one", 1L),
        Map.of("one", ULong.valueOf(-1L)),
        Map.of("one", -0.0d),
        Map.of("one", "value"));
  }

  @SuppressWarnings({"unused", "ClassCanBeRecord"})
  public static final class Person {
    private final String email;
    private final long priority;

    public Person(String email, long priority) {
      this.email = email;
      this.priority = priority;
    }

    public String getEmail() {
      return email;
    }

    public long getPriority() {
      return priority;
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

  @SuppressWarnings("unused")
  public static final class NestedAggregateInput {
    private final List<Map<String, Long>> entries;
    private int entriesReadCount;

    public NestedAggregateInput(List<Map<String, Long>> entries) {
      this.entries = entries;
    }

    public List<Map<String, Long>> getEntries() {
      entriesReadCount++;
      return entries;
    }

    int entriesReadCount() {
      return entriesReadCount;
    }
  }

  @SuppressWarnings({"unused", "ClassCanBeRecord"})
  public static final class DynamicMapInput {
    private final Map<String, Boolean> booleans;
    private final Map<String, Long> integers;
    private final Map<String, ULong> unsigned;
    private final Map<String, Double> doubles;
    private final Map<String, String> texts;
    private final String lookupKey = "one";

    public DynamicMapInput(
        Map<String, Boolean> booleans,
        Map<String, Long> integers,
        Map<String, ULong> unsigned,
        Map<String, Double> doubles,
        Map<String, String> texts) {
      this.booleans = booleans;
      this.integers = integers;
      this.unsigned = unsigned;
      this.doubles = doubles;
      this.texts = texts;
    }

    public Map<String, Boolean> getBooleans() {
      return booleans;
    }

    public Map<String, Long> getIntegers() {
      return integers;
    }

    public Map<String, ULong> getUnsigned() {
      return unsigned;
    }

    public Map<String, Double> getDoubles() {
      return doubles;
    }

    public Map<String, String> getTexts() {
      return texts;
    }

    public String getLookupKey() {
      return lookupKey;
    }
  }
}
