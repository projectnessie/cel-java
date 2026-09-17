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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.CEL.attributePattern;
import static org.projectnessie.cel.CEL.partialVars;
import static org.projectnessie.cel.Env.newCustomEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.EvalOption.OptOptimize;
import static org.projectnessie.cel.EvalOption.OptPartialEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;

import com.google.protobuf.DynamicMessage;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.BoolT;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.UnknownT;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;

class ExactProtoAggregatePlanTest {
  private static final String TYPE = TestAllTypes.getDescriptor().getFullName();

  @Test
  void exactMessageListsUseNestedObjectQuantifiersWithoutWrapperConversion() {
    TypeRegistry exactRegistry =
        ProtoTypeRegistry.newExactAggregateRegistry(TestAllTypes.getDefaultInstance());
    Env exact =
        newCustomEnv(
            exactRegistry,
            List.of(
                Library.StdLib(),
                declarations(
                    Decls.newVar("messages", Decls.newListType(Decls.newObjectType(TYPE))))));
    String expression =
        "messages.all(a1, "
            + "messages.exists_one(a2, "
            + "a2.single_string == a1.single_string && has(a2.single_string)))";
    Ast ast = compile(exact, expression);
    Prog enabled = (Prog) exact.program(ast, evalOptions(OptOptimize));
    Prog disabled = (Prog) exact.program(ast, evalOptions(OptOptimize, OptDisableNativeEval));
    List<TestAllTypes> messages =
        List.of(
            TestAllTypes.newBuilder().setSingleString("one").build(),
            TestAllTypes.newBuilder().setSingleString("two").build(),
            TestAllTypes.newBuilder().setSingleString("three").build());

    assertThat(enabled.interpretable.getClass().getSimpleName()).isEqualTo("NativeIsland");
    assertThat(disabled.interpretable.getClass().getSimpleName()).isNotEqualTo("NativeIsland");
    assertEquivalent(
        enabled.eval(Map.of("messages", messages)).getVal(),
        disabled.eval(Map.of("messages", messages)).getVal());
    assertThat(enabled.eval(Map.of("messages", messages)).getVal().booleanValue()).isTrue();

    for (Object invalidElement : List.of(NullSentinel.INSTANCE, BoolT.True, "not a message")) {
      List<Object> invalid = new ArrayList<>(messages);
      invalid.set(1, invalidElement == NullSentinel.INSTANCE ? null : invalidElement);
      Val enabledInvalid = enabled.eval(Map.of("messages", invalid)).getVal();
      Val disabledInvalid = disabled.eval(Map.of("messages", invalid)).getVal();
      assertThat(enabledInvalid).isInstanceOf(Err.class);
      assertThat(disabledInvalid).isInstanceOf(Err.class);
    }

    Ast allSuppression = compile(exact, "messages.all(a, a.single_string == 'keep')");
    Program enabledAll = exact.program(allSuppression);
    Program disabledAll = exact.program(allSuppression, evalOptions(OptDisableNativeEval));
    List<Object> invalidBeforeFalse =
        new ArrayList<>(
            java.util.Arrays.asList(
                null, TestAllTypes.newBuilder().setSingleString("false").build()));
    assertEquivalent(
        enabledAll.eval(Map.of("messages", invalidBeforeFalse)).getVal(),
        disabledAll.eval(Map.of("messages", invalidBeforeFalse)).getVal());
    assertThat(enabledAll.eval(Map.of("messages", invalidBeforeFalse)).getVal().booleanValue())
        .isFalse();

    Ast existsOne = compile(exact, "messages.exists_one(a, a.single_string == 'match')");
    Program enabledExistsOne = exact.program(existsOne);
    Program disabledExistsOne = exact.program(existsOne, evalOptions(OptDisableNativeEval));
    TestAllTypes match = TestAllTypes.newBuilder().setSingleString("match").build();
    TestAllTypes other = TestAllTypes.newBuilder().setSingleString("other").build();
    for (List<Object> invalidAroundMatch :
        List.of(
            new ArrayList<Object>(java.util.Arrays.asList(null, match, other)),
            new ArrayList<Object>(java.util.Arrays.asList(match, null, other)))) {
      assertThat(enabledExistsOne.eval(Map.of("messages", invalidAroundMatch)).getVal())
          .isInstanceOf(Err.class);
      assertThat(disabledExistsOne.eval(Map.of("messages", invalidAroundMatch)).getVal())
          .isInstanceOf(Err.class);
    }

    Env general =
        newCustomEnv(
            ProtoTypeRegistry.newRegistry(TestAllTypes.getDefaultInstance()),
            List.of(
                Library.StdLib(),
                declarations(
                    Decls.newVar("messages", Decls.newListType(Decls.newObjectType(TYPE))))));
    Prog generalProgram =
        (Prog) general.program(compile(general, expression), evalOptions(OptOptimize));
    assertThat(generalProgram.interpretable.getClass().getSimpleName())
        .isNotEqualTo("NativeIsland");
  }

  private enum NullSentinel {
    INSTANCE
  }

  @Test
  void repeatedFieldsFeedEveryExistingExactListConsumer() throws Exception {
    TestAllTypes generated =
        TestAllTypes.newBuilder()
            .addAllRepeatedInt64(List.of(1L, 2L, 3L))
            .addRepeatedUint32(-1)
            .addRepeatedBool(true)
            .addRepeatedDouble(1.5d)
            .addRepeatedString("value")
            .build();
    DynamicMessage dynamic =
        DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), generated.toByteString());
    Env env = exactEnv();

    for (String expression :
        List.of(
            "msg.repeated_int64 == [1, 2, 3]",
            "msg.repeated_int64 != [1, 2]",
            "2 in msg.repeated_int64",
            "size(msg.repeated_int64) == 3",
            "msg.repeated_int64[index] == 2",
            "msg.repeated_int64.all(v, v > 0)",
            "msg.repeated_int64.exists(v, v == 3)",
            "msg.repeated_int64.exists_one(v, v == 2)",
            "msg.repeated_int64.map(v, v + 1)[0] == 2",
            "msg.repeated_int64.filter(v, v > 1).size() == 2",
            "(msg.repeated_int64 + msg.repeated_int64)[4] == 2",
            "msg.repeated_uint32[0] == 4294967295u",
            "msg.repeated_bool[0]",
            "msg.repeated_double[0] == 1.5",
            "msg.repeated_string[0] == 'value'")) {
      Ast ast = compile(env, expression);
      Prog nativeProgram = (Prog) env.program(ast);
      Prog disabledProgram = (Prog) env.program(ast, evalOptions(OptDisableNativeEval));
      if (!expression.contains(" == [") && !expression.contains(" != [")) {
        assertThat(nativeProgram.interpretable.getClass().getSimpleName())
            .as(expression)
            .isEqualTo("NativeIsland");
      }
      for (Object message : List.of(generated, dynamic)) {
        Map<String, Object> input = Map.of("msg", message, "index", 1L);
        assertEquivalent(nativeProgram.eval(input).getVal(), disabledProgram.eval(input).getVal());
      }
    }
  }

  @Test
  void repeatedFieldBoundsAndEmptySizeAgreeWithEstablishedEvaluation() throws Exception {
    TestAllTypes generated =
        TestAllTypes.newBuilder().addAllRepeatedInt64(List.of(1L, 2L, 3L)).build();
    DynamicMessage dynamic =
        DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), generated.toByteString());
    Env env = exactEnv();
    Ast indexAst = compile(env, "msg.repeated_int64[index]");
    Program nativeIndex = env.program(indexAst);
    Program disabledIndex = env.program(indexAst, evalOptions(OptDisableNativeEval));

    for (Object message : List.of(generated, dynamic)) {
      for (long index : List.of(-1L, 3L)) {
        Map<String, Object> input = Map.of("msg", message, "index", index);
        Val nativeValue = nativeIndex.eval(input).getVal();
        assertEquivalent(nativeValue, disabledIndex.eval(input).getVal());
        assertThat(nativeValue).isInstanceOf(Err.class);
      }
    }

    Ast sizeAst = compile(env, "size(msg.repeated_int64)");
    Program nativeSize = env.program(sizeAst);
    Program disabledSize = env.program(sizeAst, evalOptions(OptDisableNativeEval));
    TestAllTypes empty = TestAllTypes.getDefaultInstance();
    DynamicMessage emptyDynamic = DynamicMessage.getDefaultInstance(TestAllTypes.getDescriptor());
    for (Object message : List.of(empty, emptyDynamic)) {
      Map<String, Object> input = Map.of("msg", message);
      assertEquivalent(nativeSize.eval(input).getVal(), disabledSize.eval(input).getVal());
    }
  }

  @Test
  void repeatedMessageFieldsPreserveGeneratedDynamicAndGeneralSemantics() throws Exception {
    TestAllTypes populated =
        TestAllTypes.newBuilder()
            .addRepeatedNestedMessage(TestAllTypes.NestedMessage.newBuilder().setBb(1))
            .addRepeatedNestedMessage(TestAllTypes.NestedMessage.newBuilder().setBb(0))
            .build();
    DynamicMessage dynamic =
        DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), populated.toByteString());
    TestAllTypes singleton =
        TestAllTypes.newBuilder()
            .addRepeatedNestedMessage(TestAllTypes.NestedMessage.newBuilder().setBb(1))
            .build();
    DynamicMessage dynamicSingleton =
        DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), singleton.toByteString());
    TestAllTypes empty = TestAllTypes.getDefaultInstance();
    DynamicMessage emptyDynamic = DynamicMessage.getDefaultInstance(TestAllTypes.getDescriptor());
    Env exact = exactEnv();
    Env general = generalEnv();

    for (Object message : List.of(populated, dynamic)) {
      assertThreeWayEquivalent(
          exact,
          general,
          "size(msg.repeated_nested_message) == expected",
          Map.of("msg", message, "expected", 2L));
      assertThat(
              assertThreeWayEquivalent(
                      exact,
                      general,
                      "msg.repeated_nested_message.all(item, item.bb > 0)",
                      Map.of("msg", message))
                  .booleanValue())
          .isFalse();
      assertThreeWayEquivalent(
          exact,
          general,
          "msg.repeated_nested_message[0].bb == expected",
          Map.of("msg", message, "expected", 1L));

      String outOfBoundsExpression = "msg.repeated_nested_message[2].bb";
      Map<String, Object> outOfBoundsInput = Map.of("msg", message);
      Ast outOfBoundsAst = compile(exact, outOfBoundsExpression);
      Val nativeOutOfBounds = exact.program(outOfBoundsAst).eval(outOfBoundsInput).getVal();
      Val disabledOutOfBounds =
          exact
              .program(outOfBoundsAst, evalOptions(OptDisableNativeEval))
              .eval(outOfBoundsInput)
              .getVal();
      Val generalOutOfBounds =
          general.program(compile(general, outOfBoundsExpression)).eval(outOfBoundsInput).getVal();
      assertThat(List.of(nativeOutOfBounds, disabledOutOfBounds, generalOutOfBounds))
          .allMatch(Err.class::isInstance);
      assertEquivalent(nativeOutOfBounds, disabledOutOfBounds);
      assertEquivalent(nativeOutOfBounds, generalOutOfBounds);
    }

    for (Object message : List.of(singleton, dynamicSingleton)) {
      assertThat(
              assertThreeWayEquivalent(
                      exact,
                      general,
                      "msg.repeated_nested_message.all(item, item.bb > 0)",
                      Map.of("msg", message))
                  .booleanValue())
          .isTrue();
    }

    for (Object message : List.of(empty, emptyDynamic)) {
      assertThreeWayEquivalent(
          exact,
          general,
          "size(msg.repeated_nested_message) == expected",
          Map.of("msg", message, "expected", 0L));
      assertThat(
              assertThreeWayEquivalent(
                      exact,
                      general,
                      "msg.repeated_nested_message.all(item, item.bb > 0)",
                      Map.of("msg", message))
                  .booleanValue())
          .isTrue();
    }
  }

  @Test
  void repeatedMessageFieldsPreservePartialQualifierMatching() {
    TestAllTypes populated =
        TestAllTypes.newBuilder()
            .addRepeatedNestedMessage(TestAllTypes.NestedMessage.newBuilder().setBb(1))
            .build();
    Env exact = exactEnv();

    for (Object message : List.of(populated, TestAllTypes.getDefaultInstance())) {
      Val fieldUnknown =
          assertPartialEquivalent(
              exact,
              "msg.repeated_nested_message.all(item, true)",
              partialVars(
                  Map.of("msg", message),
                  attributePattern("msg").qualString("repeated_nested_message")));
      assertThat(fieldUnknown).isInstanceOf(UnknownT.class);

      Val descendantUnknown =
          assertPartialEquivalent(
              exact,
              "msg.repeated_nested_message.all(item, item.bb > 0)",
              partialVars(
                  Map.of("msg", message),
                  attributePattern("msg")
                      .qualString("repeated_nested_message")
                      .wildcard()
                      .qualString("bb")));
      assertThat(descendantUnknown).isInstanceOf(UnknownT.class);

      Val nonmatching =
          assertPartialEquivalent(
              exact,
              "msg.repeated_nested_message.all(item, item.bb > 0)",
              partialVars(
                  Map.of("msg", message), attributePattern("msg").qualString("single_string")));
      assertThat(nonmatching.booleanValue()).isTrue();
    }
  }

  @SuppressWarnings("resource")
  @Test
  void repeatedMessageFieldPlansCanBeEvaluatedConcurrently() throws Exception {
    TestAllTypes populated =
        TestAllTypes.newBuilder()
            .addRepeatedNestedMessage(TestAllTypes.NestedMessage.newBuilder().setBb(1))
            .addRepeatedNestedMessage(TestAllTypes.NestedMessage.newBuilder().setBb(2))
            .build();
    DynamicMessage dynamic =
        DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), populated.toByteString());
    DynamicMessage emptyDynamic = DynamicMessage.getDefaultInstance(TestAllTypes.getDescriptor());
    Env exact = exactEnv();
    Program program =
        exact.program(
            compile(
                exact,
                "msg.repeated_nested_message.all(item, true)"
                    + " && size(msg.repeated_nested_message) == expected"));

    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        Object message =
            switch (i % 4) {
              case 0 -> populated;
              case 1 -> dynamic;
              case 2 -> TestAllTypes.getDefaultInstance();
              default -> emptyDynamic;
            };
        long actualSize = i % 4 < 2 ? 2L : 0L;
        long expectedSize = i % 3 == 0 ? actualSize + 1L : actualSize;
        boolean expectedResult = actualSize == expectedSize;
        results.add(
            executor.submit(
                () ->
                    program
                            .eval(Map.of("msg", message, "expected", expectedSize))
                            .getVal()
                            .booleanValue()
                        == expectedResult));
      }
      for (Future<Boolean> result : results) {
        assertThat(result.get(5, SECONDS)).isTrue();
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  @Test
  void exactProtobufMapSupportsConstantAndCheckedStringIndex() throws Exception {
    TestAllTypes generated =
        TestAllTypes.newBuilder()
            .putMapStringInt64("first", 1L)
            .putMapStringInt64("answer", 42L)
            .putMapStringBool("answer", true)
            .putMapStringBool("default", false)
            .putMapStringString("answer", "value")
            .putMapStringString("default", "")
            .putMapStringDouble("answer", -0.0d)
            .putMapStringDouble("positiveZero", 0.0d)
            .putMapStringDouble("nan", Double.NaN)
            .build();
    DynamicMessage dynamic =
        DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), generated.toByteString());
    Env env = exactEnv();

    for (String expression :
        List.of(
            "msg.map_string_int64['answer']",
            "msg.map_string_int64[key]",
            "msg.map_string_int64['ans' + suffix]",
            "size(msg.map_string_int64)",
            "'answer' in msg.map_string_int64",
            "msg.map_string_bool['answer']",
            "msg.map_string_bool['default']",
            "msg.map_string_bool[key]",
            "msg.map_string_string['answer']",
            "msg.map_string_string['default']",
            "msg.map_string_string[key]",
            "msg.map_string_double['answer']",
            "msg.map_string_double['positiveZero']",
            "msg.map_string_double['nan']",
            "msg.map_string_double[key]",
            "msg.map_string_bool['missing']")) {
      Ast ast = compile(env, expression);
      Prog nativeProgram = (Prog) env.program(ast);
      Prog disabledProgram = (Prog) env.program(ast, evalOptions(OptDisableNativeEval));
      assertThat(nativeProgram.interpretable.getClass().getSimpleName())
          .as(expression)
          .isEqualTo("NativeIsland");
      for (Object message : List.of(generated, dynamic)) {
        Map<String, Object> input = Map.of("msg", message, "key", "answer", "suffix", "wer");
        assertEquivalent(nativeProgram.eval(input).getVal(), disabledProgram.eval(input).getVal());
      }
    }
  }

  @Test
  void exactProtobufMapTerminalMaterializationAndEqualityRemainEstablishedCompatible()
      throws Exception {
    TestAllTypes generated =
        TestAllTypes.newBuilder()
            .putMapStringBool("answer", true)
            .putMapStringString("answer", "value")
            .putMapStringDouble("answer", -0.0d)
            .build();
    DynamicMessage dynamic =
        DynamicMessage.parseFrom(TestAllTypes.getDescriptor(), generated.toByteString());
    Env env = exactEnv();

    for (String expression :
        List.of(
            "msg.map_string_bool == {'answer': true}",
            "msg.map_string_string == {'answer': 'value'}",
            "msg.map_string_double == {'answer': -0.0}")) {
      Ast ast = compile(env, expression);
      Program nativeProgram = env.program(ast);
      Program disabledProgram = env.program(ast, evalOptions(OptDisableNativeEval));
      for (Object message : List.of(generated, dynamic)) {
        Map<String, Object> input = Map.of("msg", message);
        assertEquivalent(nativeProgram.eval(input).getVal(), disabledProgram.eval(input).getVal());
      }
    }
  }

  private static Env exactEnv() {
    TypeRegistry registry =
        ProtoTypeRegistry.newExactAggregateRegistry(TestAllTypes.getDefaultInstance());
    return newCustomEnv(
        registry,
        List.of(
            Library.StdLib(),
            declarations(
                Decls.newVar("msg", Decls.newObjectType(TYPE)),
                Decls.newVar("index", Decls.Int),
                Decls.newVar("expected", Decls.Int),
                Decls.newVar("key", Decls.String),
                Decls.newVar("suffix", Decls.String))));
  }

  private static Env generalEnv() {
    TypeRegistry registry = ProtoTypeRegistry.newRegistry(TestAllTypes.getDefaultInstance());
    return newCustomEnv(
        registry,
        List.of(
            Library.StdLib(),
            declarations(
                Decls.newVar("msg", Decls.newObjectType(TYPE)),
                Decls.newVar("expected", Decls.Int))));
  }

  private static Val assertThreeWayEquivalent(
      Env exact, Env general, String expression, Map<String, Object> input) {
    Ast exactAst = compile(exact, expression);
    Val nativeValue = exact.program(exactAst).eval(input).getVal();
    Val disabledValue =
        exact.program(exactAst, evalOptions(OptDisableNativeEval)).eval(input).getVal();
    Val generalValue = general.program(compile(general, expression)).eval(input).getVal();
    assertEquivalent(nativeValue, disabledValue);
    assertEquivalent(nativeValue, generalValue);
    return nativeValue;
  }

  private static Val assertPartialEquivalent(Env exact, String expression, Object input) {
    Ast ast = compile(exact, expression);
    Val nativeValue = exact.program(ast, evalOptions(OptPartialEval)).eval(input).getVal();
    Val disabledValue =
        exact.program(ast, evalOptions(OptPartialEval, OptDisableNativeEval)).eval(input).getVal();
    assertEquivalent(nativeValue, disabledValue);
    return nativeValue;
  }

  private static Ast compile(Env env, String expression) {
    Env.AstIssuesTuple compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    return compiled.getAst();
  }

  private static void assertEquivalent(Val nativeValue, Val disabledValue) {
    assertThat(nativeValue.getClass()).isEqualTo(disabledValue.getClass());
    assertThat(nativeValue.type()).isEqualTo(disabledValue.type());
    assertThat(nativeValue.toString()).isEqualTo(disabledValue.toString());
    if (nativeValue instanceof Err nativeError && disabledValue instanceof Err disabledError) {
      assertThat(nativeError.hasCause()).isEqualTo(disabledError.hasCause());
      if (nativeError.hasCause()) {
        assertThat(nativeError.getCause().getClass())
            .isEqualTo(disabledError.getCause().getClass());
        assertThat(nativeError.getCause().getMessage())
            .isEqualTo(disabledError.getCause().getMessage());
      }
      return;
    }
    if (!(nativeValue instanceof UnknownT)) {
      assertThat(nativeValue.value()).isEqualTo(disabledValue.value());
    }
  }
}
