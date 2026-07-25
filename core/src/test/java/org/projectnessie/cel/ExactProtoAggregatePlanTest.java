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

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.Env.newCustomEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;

import com.google.protobuf.DynamicMessage;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;

class ExactProtoAggregatePlanTest {
  private static final String TYPE = TestAllTypes.getDescriptor().getFullName();

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
                Decls.newVar("key", Decls.String),
                Decls.newVar("suffix", Decls.String))));
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
    if (!(nativeValue instanceof Err)) {
      assertThat(nativeValue.value()).isEqualTo(disabledValue.value());
    }
  }
}
