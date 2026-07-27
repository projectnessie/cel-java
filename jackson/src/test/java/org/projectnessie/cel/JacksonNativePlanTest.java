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
import static org.projectnessie.cel.common.types.StringT.stringOf;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
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
import org.projectnessie.cel.types.jackson.types.AnEnum;
import org.projectnessie.cel.types.jackson.types.ArrayObject;
import org.projectnessie.cel.types.jackson.types.InnerType;

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
  void exactObjectMapIndexConvertsOnlyTheSelectedMessage() {
    TypeRegistry registry = JacksonRegistry.newExactAggregateRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(ObjectMapInput.class, NestedObject.class),
            declarations(
                Decls.newVar("input", Decls.newObjectType(ObjectMapInput.class.getName()))));
    Ast selection = compile(env, "input.objects['target'].value");
    Ast presence = compile(env, "has(input.objects['target'].value)");

    Val value =
        assertEnabledDisabledEquivalent(
            env,
            selection,
            Map.of("input", new ObjectMapInput(Map.of("target", new NestedObject("selected")))));
    assertThat(value.value()).isEqualTo("selected");
    Val present =
        assertEnabledDisabledEquivalent(
            env,
            presence,
            Map.of("input", new ObjectMapInput(Map.of("target", new NestedObject("selected")))));
    assertThat(present.booleanValue()).isTrue();

    Val missing =
        assertEnabledDisabledEquivalent(
            env, selection, Map.of("input", new ObjectMapInput(Map.of())));
    assertThat(missing).matches(Err::isError);
    Val missingPresence =
        assertEnabledDisabledEquivalent(
            env, presence, Map.of("input", new ObjectMapInput(Map.of())));
    assertThat(missingPresence).matches(Err::isError);

    Map<String, NestedObject> nullable = new HashMap<>();
    nullable.put("target", null);
    Val nullValue =
        assertEnabledDisabledEquivalent(
            env, selection, Map.of("input", new ObjectMapInput(nullable)));
    assertThat(nullValue).matches(Err::isError);

    Val incompatible =
        assertEnabledDisabledEquivalent(
            env, selection, Map.of("input", new ObjectMapInput(incompatibleObjectMap())));
    assertThat(incompatible).matches(Err::isError);

    Program enabled = env.program(selection);
    NestedObject selectedObject = new NestedObject("selected");
    ObjectMapInput lookupOnlyInput =
        new ObjectMapInput(new LookupOnlyMap<>("target", selectedObject));
    Val selected = enabled.eval(Map.of("input", lookupOnlyInput)).getVal();
    assertThat(selected.value()).isEqualTo("selected");
    assertThat(lookupOnlyInput.objectsReadCount()).isEqualTo(1);
    assertThat(selectedObject.valueReadCount()).isEqualTo(1);
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
  void exactRegistryMaterializesCanonicalArrayFieldsInBothModes() {
    TypeRegistry registry = JacksonRegistry.newExactAggregateRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(ArrayObject.class),
            declarations(Decls.newVar("input", Decls.newObjectType(ArrayObject.class.getName()))));
    Ast ast =
        compile(
            env,
            "input.ints == [1, 2]"
                + " && input.longs[0] == 3"
                + " && input.doubles[0] == 4.5"
                + " && input.strings[0] == 'string'"
                + " && input.boxedInts[0] == 5"
                + " && input.uints[0] == 6u"
                + " && input.objects[0].intProp == 7"
                + " && input.dynamic[0] == 'dynamic'"
                + " && input.nestedInts[0][1] == 9"
                + " && input.nestedBytes[0] == b'bytes'");

    Val result = assertEnabledDisabledEquivalent(env, ast, Map.of("input", arrayObject()));

    assertThat(result.booleanValue()).isTrue();
  }

  @Test
  void exactRegistryRejectsNoncanonicalArrayFieldsInBothModes() {
    TypeRegistry registry = JacksonRegistry.newExactAggregateRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(ArrayObject.class),
            declarations(Decls.newVar("input", Decls.newObjectType(ArrayObject.class.getName()))));
    ArrayObject input = arrayObject();
    input.values = new Val[] {stringOf("embedded")};
    input.enums = new AnEnum[] {AnEnum.ENUM_VALUE_2};

    Val embeddedValue =
        assertEnabledDisabledEquivalent(
            env, compile(env, "input.values[0]"), Map.of("input", input));
    Val enumValue =
        assertEnabledDisabledEquivalent(
            env, compile(env, "input.enums[0]"), Map.of("input", input));

    assertThat(embeddedValue).matches(Err::isError);
    assertThat(enumValue).matches(Err::isError);
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
  void exactMapFieldsSupportConstantAndCheckedDynamicBooleanAndSignedIntegerKeys() {
    TypeRegistry registry = JacksonRegistry.newExactAggregateRegistry();
    Env env =
        newEnv(
            customTypeAdapter(registry),
            customTypeProvider(registry),
            types(ExactKeyMapInput.class),
            declarations(
                Decls.newVar("input", Decls.newObjectType(ExactKeyMapInput.class.getName())),
                Decls.newVar("boolKey", Decls.Bool),
                Decls.newVar("intKey", Decls.Int)));
    ExactKeyMapInput input =
        new ExactKeyMapInput(
            Map.of((byte) 1, 11L),
            Map.of((short) 1, 12L),
            Map.of(1, 13L),
            Map.of(1L, 14L),
            Map.of(true, 15L));
    Map<String, Object> activation = Map.of("input", input, "boolKey", true, "intKey", 1L);

    for (String expression :
        List.of(
            "input.byteKeys[1]",
            "1 in input.byteKeys",
            "input.byteKeys[intKey]",
            "input.shortKeys[1]",
            "1 in input.shortKeys",
            "input.shortKeys[intKey]",
            "input.integerKeys[1]",
            "1 in input.integerKeys",
            "input.integerKeys[intKey]",
            "input.integerKeys[input.lookupInteger]",
            "input.integerKeys[intKey + 0]",
            "input.longKeys[1]",
            "1 in input.longKeys",
            "input.longKeys[intKey]",
            "input.booleanKeys[true]",
            "true in input.booleanKeys",
            "input.booleanKeys[boolKey]",
            "input.booleanKeys[input.lookupBoolean]",
            "input.booleanKeys[!false]")) {
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

    for (String expression : List.of("input.integerKeys[2]", "input.integerKeys[intKey]")) {
      Ast ast = compile(env, expression);
      Val result =
          assertEnabledDisabledEquivalent(
              env, ast, Map.of("input", input, "boolKey", true, "intKey", 2L));
      assertThat(result).as(expression).matches(Err::isError);
    }
    Ast missingBoolean = compile(env, "input.booleanKeys[boolKey]");
    Val missingBooleanResult =
        assertEnabledDisabledEquivalent(
            env, missingBoolean, Map.of("input", input, "boolKey", false, "intKey", 1L));
    assertThat(missingBooleanResult).matches(Err::isError);

    Val missingMembership =
        assertEnabledDisabledEquivalent(env, compile(env, "2 in input.integerKeys"), activation);
    assertThat(missingMembership.booleanValue()).isFalse();
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
    assertThat(result.hasIssues())
        .as(expression)
        .withFailMessage(result.getIssues()::toString)
        .isFalse();
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

  private static ArrayObject arrayObject() {
    ArrayObject value = new ArrayObject();
    value.bytes = "root".getBytes(StandardCharsets.UTF_8);
    value.ints = new int[] {1, 2};
    value.longs = new long[] {3};
    value.doubles = new double[] {4.5d};
    value.strings = new String[] {"string"};
    value.boxedInts = new Integer[] {5};
    value.uints = new ULong[] {ULong.valueOf(6)};
    InnerType object = new InnerType();
    object.intProp = 7;
    value.objects = new InnerType[] {object};
    value.dynamic = new Object[] {"dynamic"};
    value.nestedInts = new int[][] {{8, 9}};
    value.nestedBytes = new byte[][] {"bytes".getBytes(StandardCharsets.UTF_8)};
    return value;
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

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Map<String, NestedObject> incompatibleObjectMap() {
    return (Map) Map.of("target", "not a nested object");
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
  public static final class ObjectMapInput {
    private final Map<String, NestedObject> objects;
    private int objectsReadCount;

    public ObjectMapInput(Map<String, NestedObject> objects) {
      this.objects = objects;
    }

    public Map<String, NestedObject> getObjects() {
      objectsReadCount++;
      return objects;
    }

    int objectsReadCount() {
      return objectsReadCount;
    }
  }

  @SuppressWarnings({"unused", "ClassCanBeRecord"})
  public static final class NestedObject {
    private final String value;
    private int valueReadCount;

    public NestedObject(String value) {
      this.value = value;
    }

    public String getValue() {
      valueReadCount++;
      return value;
    }

    int valueReadCount() {
      return valueReadCount;
    }
  }

  private static final class LookupOnlyMap<K, V> extends AbstractMap<K, V> {
    private final K key;
    private final V value;

    private LookupOnlyMap(K key, V value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public V get(Object requestedKey) {
      return key.equals(requestedKey) ? value : null;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
      throw new AssertionError("constant exact lookup must not traverse the source map");
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

  @SuppressWarnings({"unused", "ClassCanBeRecord"})
  public static final class ExactKeyMapInput {
    private final Map<Byte, Long> byteKeys;
    private final Map<Short, Long> shortKeys;
    private final Map<Integer, Long> integerKeys;
    private final Map<Long, Long> longKeys;
    private final Map<Boolean, Long> booleanKeys;
    private final int lookupInteger = 1;
    private final boolean lookupBoolean = true;

    public ExactKeyMapInput(
        Map<Byte, Long> byteKeys,
        Map<Short, Long> shortKeys,
        Map<Integer, Long> integerKeys,
        Map<Long, Long> longKeys,
        Map<Boolean, Long> booleanKeys) {
      this.byteKeys = byteKeys;
      this.shortKeys = shortKeys;
      this.integerKeys = integerKeys;
      this.longKeys = longKeys;
      this.booleanKeys = booleanKeys;
    }

    public Map<Byte, Long> getByteKeys() {
      return byteKeys;
    }

    public Map<Short, Long> getShortKeys() {
      return shortKeys;
    }

    public Map<Integer, Long> getIntegerKeys() {
      return integerKeys;
    }

    public Map<Long, Long> getLongKeys() {
      return longKeys;
    }

    public Map<Boolean, Long> getBooleanKeys() {
      return booleanKeys;
    }

    public int getLookupInteger() {
      return lookupInteger;
    }

    public boolean isLookupBoolean() {
      return lookupBoolean;
    }
  }
}
