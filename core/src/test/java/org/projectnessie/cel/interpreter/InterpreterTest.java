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
package org.projectnessie.cel.interpreter;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.projectnessie.cel.Util.mapOf;
import static org.projectnessie.cel.checker.CheckerEnv.newStandardCheckerEnv;
import static org.projectnessie.cel.common.Source.newTextSource;
import static org.projectnessie.cel.common.containers.Container.newContainer;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.IntT.IntNegOne;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.NullT.NullType;
import static org.projectnessie.cel.common.types.NullT.NullValue;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newEmptyRegistry;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newRegistry;
import static org.projectnessie.cel.common.types.traits.Trait.AdderType;
import static org.projectnessie.cel.common.types.traits.Trait.NegatorType;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.Activation.newPartialActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.AttributePattern.newAttributePattern;
import static org.projectnessie.cel.interpreter.AttributePattern.newPartialAttributeFactory;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.Coster.costOf;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.EvalState.newEvalState;
import static org.projectnessie.cel.interpreter.Interpreter.exhaustiveEval;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.Interpreter.newStandardInterpreter;
import static org.projectnessie.cel.interpreter.Interpreter.optimize;
import static org.projectnessie.cel.interpreter.Interpreter.trackState;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes;
import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Decl;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.cel.checker.Checker;
import org.projectnessie.cel.checker.Checker.CheckResult;
import org.projectnessie.cel.checker.CheckerEnv;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.Source;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.DurationT;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.ListT;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.TimestampT;
import org.projectnessie.cel.common.types.UnknownT;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Adder;
import org.projectnessie.cel.common.types.traits.Negater;
import org.projectnessie.cel.interpreter.AttributeFactory.ConstantQualifier;
import org.projectnessie.cel.interpreter.AttributeFactory.FieldQualifier;
import org.projectnessie.cel.interpreter.AttributeFactory.NamespacedAttribute;
import org.projectnessie.cel.interpreter.AttributeFactory.Qualifier;
import org.projectnessie.cel.interpreter.AttributesTest.CustAttrFactory;
import org.projectnessie.cel.interpreter.Coster.Cost;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableAttribute;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableConst;
import org.projectnessie.cel.interpreter.functions.Overload;
import org.projectnessie.cel.parser.Parser;
import org.projectnessie.cel.parser.Parser.ParseResult;

class InterpreterTest {

  private static Val base64Encode(Val val) {
    if (!(val instanceof StringT)) {
      return noSuchOverload(val, "base64Encode", "", new Val[] {});
    }
    String b64 =
        Base64.getEncoder().encodeToString(val.value().toString().getBytes(StandardCharsets.UTF_8));
    return stringOf(b64);
  }

  static class TestCase {
    final InterpreterTestCase name;
    private String expr;
    private String container;
    private Cost cost;
    private Cost exhaustiveCost;
    private Cost optimizedCost;
    private String[] abbrevs;
    private Decl[] env;
    private Message[] types;
    private Overload[] funcs;
    private AttributeFactory attrs;
    private boolean unchecked;
    private String disabled;

    private Map<Object, Object> in;
    private Object out;
    private String err;

    TestCase(InterpreterTestCase name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name.name();
    }

    TestCase disabled(String reason) {
      this.disabled = reason;
      return this;
    }

    TestCase expr(String expr) {
      this.expr = expr;
      return this;
    }

    TestCase container(String container) {
      this.container = container;
      return this;
    }

    TestCase cost(Cost cost) {
      this.cost = cost;
      return this;
    }

    TestCase exhaustiveCost(Cost exhaustiveCost) {
      this.exhaustiveCost = exhaustiveCost;
      return this;
    }

    TestCase optimizedCost(Cost optimizedCost) {
      this.optimizedCost = optimizedCost;
      return this;
    }

    TestCase abbrevs(String... abbrevs) {
      this.abbrevs = abbrevs;
      return this;
    }

    TestCase env(Decl... env) {
      this.env = env;
      return this;
    }

    TestCase types(Message... types) {
      this.types = types;
      return this;
    }

    TestCase funcs(Overload... funcs) {
      this.funcs = funcs;
      return this;
    }

    TestCase attrs(AttributeFactory attrs) {
      this.attrs = attrs;
      return this;
    }

    TestCase unchecked() {
      this.unchecked = true;
      return this;
    }

    TestCase in(Object... kvPairs) {
      if (kvPairs.length == 0) {
        this.in = mapOf();
      } else {
        this.in = mapOf(kvPairs[0], kvPairs[1], Arrays.copyOfRange(kvPairs, 2, kvPairs.length));
      }
      return this;
    }

    TestCase out(Object out) {
      this.out = out;
      return this;
    }

    TestCase err(String err) {
      this.err = err;
      return this;
    }
  }

  @SuppressWarnings("unused")
  static TestCase[] testCases() {
    return new TestCase[] {
      new TestCase(InterpreterTestCase.map_key_null)
          .expr("{null:false}[null]")
          .err("message: unsupported key type"),
      new TestCase(InterpreterTestCase.map_value_repeat_key_heterogeneous)
          .expr("{0: 1, 0u: 2}[0.0]")
          .err("message: Failed with repeated key"),
      new TestCase(InterpreterTestCase.map_key_mixed_numbers_lossy_double_key)
          .expr("{1u: 1.0, 2: 2.0, 3u: 3.0}[3.1]")
          .err("no such key: double{3.1}"),
      new TestCase(InterpreterTestCase.zero_based_double_error)
          .expr("[7, 8, 9][dyn(0.1)]")
          .err("invalid_argument"),
      new TestCase(InterpreterTestCase.zero_based_double).expr("[7, 8, 9][dyn(0.0)]").out(intOf(7)),
      new TestCase(InterpreterTestCase.not_int32_eq_uint)
          .expr("Int32Value{value: 34} == dyn(UInt64Value{value: 18446744073709551615u})")
          .container("google.protobuf")
          .out(False),
      new TestCase(InterpreterTestCase.not_uint32_eq_double)
          .expr("UInt32Value{value: 34u} == dyn(DoubleValue{value: 18446744073709551616.0})")
          .container("google.protobuf")
          .out(False),
      new TestCase(InterpreterTestCase.eq_proto_different_types)
          .expr("dyn(TestAllTypes{}) == dyn(NestedTestAllTypes{})")
          .container("google.api.expr.test.v1.proto2")
          .types(
              com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .out(False),
      new TestCase(InterpreterTestCase.not_lt_dyn_big_uint_int)
          .expr("dyn(9223372036854775808u) < 1")
          .out(False),
      new TestCase(InterpreterTestCase.lt_dyn_int_big_uint)
          .expr("dyn(1) < 9223372036854775808u")
          .out(True),
      new TestCase(InterpreterTestCase.lt_dyn_uint_big_double)
          .expr("dyn(18446744073709551615u) < 18446744073709590000.0")
          .out(True),
      new TestCase(InterpreterTestCase.not_lt_dyn_uint_small_int)
          .expr("dyn(1u) < -9223372036854775808")
          .out(False),
      new TestCase(InterpreterTestCase.lt_ne_dyn_int_double).expr("dyn(24) != 24.1").out(True),
      new TestCase(InterpreterTestCase.eq_proto_nan_equal)
          .expr(
              "TestAllTypes{single_double: double('NaN')} == TestAllTypes{single_double: double('NaN')}")
          .container("google.api.expr.test.v1.proto3")
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          // The outcome in the generated Java proto code is different than in the conformance-test,
          // it is NOT: "For proto equality, fields with NaN value are treated as not equal."
          .out(True),
      new TestCase(InterpreterTestCase.eq_bool_not_null)
          .expr("google.protobuf.BoolValue{} != null")
          .out(True),
      new TestCase(InterpreterTestCase.literal_any)
          .expr(
              "google.protobuf.Any{type_url: 'type.googleapis.com/google.api.expr.test.v1.proto2.TestAllTypes', value: b'\\x08\\x96\\x01'}")
          .types(
              com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance(),
              Any.getDefaultInstance())
          .out(TestAllTypes.newBuilder().setSingleInt32(150).build()),
      new TestCase(InterpreterTestCase.literal_var)
          .expr("x")
          .env(Decls.newVar("x", Decls.newObjectType("google.protobuf.Any")))
          .types(
              Any.getDefaultInstance(),
              com.google.api.expr.v1alpha1.Value.getDefaultInstance(),
              com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .in(
              "x",
              com.google.api.expr.v1alpha1.Value.newBuilder()
                  .setObjectValue(
                      Any.newBuilder()
                          .setTypeUrl(
                              "type.googleapis.com/google.api.expr.test.v1.proto2.TestAllTypes")
                          .setValue(ByteString.copyFrom(new byte[] {8, (byte) 150, 1})))
                  .build())
          .out(TestAllTypes.newBuilder().setSingleInt32(150).build()),
      new TestCase(InterpreterTestCase.select_pb3_unset)
          .expr("TestAllTypes{}.single_struct")
          .container("google.api.expr.test.v1.proto3")
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .out(Struct.getDefaultInstance()),
      new TestCase(InterpreterTestCase.elem_in_mixed_type_list2)
          .expr("'elem' in [1u, 'str', 2, b'bytes']")
          .out(False),
      new TestCase(InterpreterTestCase.elem_in_mixed_type_list)
          .expr("'elem' in [1, 'elem', 2]")
          .out(boolOf(true)),
      new TestCase(InterpreterTestCase.select_literal_uint)
          .expr("google.protobuf.UInt32Value{value: 123u}")
          .out(ULong.valueOf(123)),
      new TestCase(InterpreterTestCase.select_pb3_unset)
          .expr("TestAllTypes{}.single_struct")
          .container("google.api.expr.test.v1.proto3")
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .out(Struct.getDefaultInstance()),
      new TestCase(InterpreterTestCase.select_on_int64)
          .expr("a.pancakes")
          .types(Decls.newVar("a", Decls.Int))
          .in("a", intOf(15))
          .err("no such overload: int.ref-resolve(*)")
          .unchecked(),
      new TestCase(InterpreterTestCase.select_pb3_empty_list)
          .container("google.api.expr.test.v1.proto3")
          .expr("TestAllTypes{list_value: []}.list_value")
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .out(ListValue.getDefaultInstance()),
      new TestCase(InterpreterTestCase.select_pb3_enum_big)
          .container("google.api.expr.test.v1.proto3")
          .expr("x.standalone_enum")
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .env(
              Decls.newVar("x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))
          .in(
              "x",
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.newBuilder()
                  .setStandaloneEnumValue(108)
                  .build())
          .out(intOf(108)),
      new TestCase(InterpreterTestCase.select_pb3_enum_neg)
          .container("google.api.expr.test.v1.proto3")
          .expr("x.standalone_enum")
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .env(
              Decls.newVar("x", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))
          .in(
              "x",
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.newBuilder()
                  .setStandaloneEnumValue(-3)
                  .build())
          .out(intOf(-3)),
      new TestCase(InterpreterTestCase.eq_list_mixed_type_numbers)
          .expr("[1.0, 2.0, 3] == [1u, 2, 3u]")
          .out(True),
      new TestCase(InterpreterTestCase.not_eq_list_mixed_type_numbers)
          .expr("[1.0, 2.1] == [1u, 2]")
          .out(False),
      new TestCase(InterpreterTestCase.eq_list_elem_mixed_types_one_element)
          .expr("[1] == [1.0]")
          .out(True),
      new TestCase(InterpreterTestCase.eq_list_elem_one_element)
          .expr("['str'] == ['str']")
          .out(True),
      new TestCase(InterpreterTestCase.not_eq_list_one_element)
          .expr("['str'] == ['blah']")
          .out(False),
      new TestCase(InterpreterTestCase.not_eq_list_one_element2).expr("[1] == [2]").out(False),
      new TestCase(InterpreterTestCase.parse_nest_message_literal)
          .container("google.api.expr.test.v1.proto3")
          .expr(
              "NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: "
                  + "NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: "
                  + "NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: "
                  + "NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: "
                  + "NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: NestedTestAllTypes{child: "
                  + "NestedTestAllTypes{payload: TestAllTypes{single_int64: 137}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}.payload.single_int64")
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.NestedTestAllTypes
                  .getDefaultInstance())
          .out(intOf(0)),
      new TestCase(InterpreterTestCase.parse_repeat_index)
          .expr(
              "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[['foo']]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0]")
          .out(stringOf("foo")),
      new TestCase(InterpreterTestCase.cond_bad_type)
          .expr("'cows' ? false : 17")
          .err("no such overload")
          .unchecked(),
      new TestCase(InterpreterTestCase.and_false_1st)
          .expr("false && true")
          .cost(costOf(0, 1))
          .exhaustiveCost(costOf(1, 1))
          .out(False),
      new TestCase(InterpreterTestCase.and_false_2nd)
          .expr("true && false")
          .cost(costOf(0, 1))
          .exhaustiveCost(costOf(1, 1))
          .out(False),
      new TestCase(InterpreterTestCase.and_error_1st_false)
          .expr("1/0 != 0 && false")
          .cost(costOf(2, 3))
          .exhaustiveCost(costOf(3, 3))
          .out(False),
      new TestCase(InterpreterTestCase.and_error_2nd_false)
          .expr("false && 1/0 != 0")
          .cost(costOf(0, 3))
          .exhaustiveCost(costOf(3, 3))
          .out(False),
      new TestCase(InterpreterTestCase.and_error_1st_error)
          .expr("1/0 != 0 && true")
          .cost(costOf(2, 3))
          .exhaustiveCost(costOf(3, 3))
          .err("divide by zero"),
      new TestCase(InterpreterTestCase.and_error_2nd_error)
          .expr("true && 1/0 != 0")
          .cost(costOf(0, 3))
          .exhaustiveCost(costOf(3, 3))
          .err("divide by zero"),
      new TestCase(InterpreterTestCase.call_no_args)
          .expr("zero()")
          .cost(costOf(1, 1))
          .unchecked()
          .funcs(Overload.function("zero", (args) -> IntZero))
          .out(IntZero),
      new TestCase(InterpreterTestCase.call_one_arg)
          .expr("neg(1)")
          .cost(costOf(1, 1))
          .unchecked()
          .funcs(Overload.unary("neg", NegatorType, arg -> ((Negater) arg).negate()))
          .out(IntNegOne),
      new TestCase(InterpreterTestCase.call_two_arg)
          .expr("b'abc'.concat(b'def')")
          .cost(costOf(1, 1))
          .unchecked()
          .funcs(Overload.binary("concat", AdderType, (lhs, rhs) -> ((Adder) lhs).add(rhs)))
          .out(new byte[] {'a', 'b', 'c', 'd', 'e', 'f'}),
      new TestCase(InterpreterTestCase.call_varargs)
          .expr("addall(a, b, c, d) == 10")
          .cost(costOf(6, 6))
          .unchecked()
          .funcs(
              Overload.function(
                  "addall",
                  AdderType,
                  args -> {
                    int val = 0;
                    for (Val arg : args) {
                      val += arg.intValue();
                    }
                    return intOf(val);
                  }))
          .in("a", 1, "b", 2, "c", 3, "d", 4),
      new TestCase(InterpreterTestCase.call_ns_func)
          .expr("base64.encode('hello')")
          .cost(costOf(1, 1))
          .env(
              Decls.newFunction(
                  "base64.encode",
                  singletonList(
                      Decls.newOverload(
                          "base64_encode_string", singletonList(Decls.String), Decls.String))))
          .funcs(
              Overload.unary("base64.encode", InterpreterTest::base64Encode),
              Overload.unary("base64_encode_string", InterpreterTest::base64Encode))
          .out("aGVsbG8="),
      new TestCase(InterpreterTestCase.call_ns_func_unchecked)
          .expr("base64.encode('hello')")
          .cost(costOf(1, 1))
          .unchecked()
          .funcs(Overload.unary("base64.encode", InterpreterTest::base64Encode))
          .out("aGVsbG8="),
      new TestCase(InterpreterTestCase.call_ns_func_in_pkg)
          .container("base64")
          .expr("encode('hello')")
          .cost(costOf(1, 1))
          .env(
              Decls.newFunction(
                  "base64.encode",
                  singletonList(
                      Decls.newOverload(
                          "base64_encode_string", singletonList(Decls.String), Decls.String))))
          .funcs(
              Overload.unary("base64.encode", InterpreterTest::base64Encode),
              Overload.unary("base64_encode_string", InterpreterTest::base64Encode))
          .out("aGVsbG8="),
      new TestCase(InterpreterTestCase.call_ns_func_unchecked_in_pkg)
          .expr("encode('hello')")
          .cost(costOf(1, 1))
          .container("base64")
          .unchecked()
          .funcs(Overload.unary("base64.encode", InterpreterTest::base64Encode))
          .out("aGVsbG8="),
      new TestCase(InterpreterTestCase.complex)
          .expr(
              "!(headers.ip in [\"10.0.1.4\", \"10.0.1.5\"]) && \n"
                  + "((headers.path.startsWith(\"v1\") && headers.token in [\"v1\", \"v2\", \"admin\"]) || \n"
                  + "(headers.path.startsWith(\"v2\") && headers.token in [\"v2\", \"admin\"]) || \n"
                  + "(headers.path.startsWith(\"/admin\") && headers.token == \"admin\" && headers.ip in [\"10.0.1.2\", \"10.0.1.2\", \"10.0.1.2\"]))")
          .cost(costOf(3, 24))
          .exhaustiveCost(costOf(24, 24))
          .optimizedCost(costOf(2, 20))
          .env(Decls.newVar("headers", Decls.newMapType(Decls.String, Decls.String)))
          .in(
              "headers",
              mapOf(
                  "ip", "10.0.1.2",
                  "path", "/admin/edit",
                  "token", "admin")),
      new TestCase(InterpreterTestCase.complex_qual_vars)
          .expr(
              "!(headers.ip in [\"10.0.1.4\", \"10.0.1.5\"]) && \n"
                  + "((headers.path.startsWith(\"v1\") && headers.token in [\"v1\", \"v2\", \"admin\"]) || \n"
                  + "(headers.path.startsWith(\"v2\") && headers.token in [\"v2\", \"admin\"]) || \n"
                  + "(headers.path.startsWith(\"/admin\") && headers.token == \"admin\" && headers.ip in [\"10.0.1.2\", \"10.0.1.2\", \"10.0.1.2\"]))")
          .cost(costOf(3, 24))
          .exhaustiveCost(costOf(24, 24))
          .optimizedCost(costOf(2, 20))
          .env(
              Decls.newVar("headers.ip", Decls.String),
              Decls.newVar("headers.path", Decls.String),
              Decls.newVar("headers.token", Decls.String))
          .in(
              "headers.ip", "10.0.1.2",
              "headers.path", "/admin/edit",
              "headers.token", "admin"),
      new TestCase(InterpreterTestCase.cond)
          .expr("a ? b < 1.2 : c == ['hello']")
          .cost(costOf(3, 3))
          .env(
              Decls.newVar("a", Decls.Bool),
              Decls.newVar("b", Decls.Double),
              Decls.newVar("c", Decls.newListType(Decls.String)))
          .in("a", true, "b", 2.0, "c", new String[] {"hello"})
          .out(False),
      new TestCase(InterpreterTestCase.in_list)
          .expr("6 in [2, 12, 6]")
          .cost(costOf(1, 1))
          .optimizedCost(costOf(0, 0)),
      new TestCase(InterpreterTestCase.in_map)
          .expr("'other-key' in {'key': null, 'other-key': 42}")
          .cost(costOf(1, 1)),
      new TestCase(InterpreterTestCase.index)
          .expr("m['key'][1] == 42u && m['null'] == null && m[string(0)] == 10")
          .cost(costOf(2, 9))
          .exhaustiveCost(costOf(9, 9))
          .optimizedCost(costOf(2, 8))
          .env(Decls.newVar("m", Decls.newMapType(Decls.String, Decls.Dyn)))
          .in(
              "m",
              mapOf(
                  "key",
                  new Object[] {ULong.valueOf(21), ULong.valueOf(42)},
                  "null",
                  null,
                  "0",
                  10)),
      new TestCase(InterpreterTestCase.index_relative)
          .expr(
              "([[[1]], [[2]], [[3]]][0][0] + [2, 3, {'four': {'five': 'six'}}])[3].four.five == 'six'")
          .cost(costOf(2, 2)),
      new TestCase(InterpreterTestCase.literal_bool_false)
          .expr("false")
          .cost(costOf(0, 0))
          .out(False),
      new TestCase(InterpreterTestCase.literal_bool_true).expr("true").cost(costOf(0, 0)),
      new TestCase(InterpreterTestCase.literal_empty)
          .expr("google.protobuf.Any{}")
          .err("conversion error: got Any with empty type-url"),
      new TestCase(InterpreterTestCase.literal_null).expr("null").cost(costOf(0, 0)).out(NullValue),
      new TestCase(InterpreterTestCase.literal_list)
          .expr("[1, 2, 3]")
          .cost(costOf(0, 0))
          .out(new long[] {1, 2, 3}),
      new TestCase(InterpreterTestCase.literal_map)
          .expr("{'hi': 21, 'world': 42u}")
          .cost(costOf(0, 0))
          .out(mapOf("hi", 21, "world", ULong.valueOf(42))),
      new TestCase(InterpreterTestCase.literal_equiv_string_bytes)
          .expr("string(bytes(\"\\303\\277\")) == '''\\303\\277'''")
          .cost(costOf(3, 3))
          .optimizedCost(costOf(1, 1)),
      new TestCase(InterpreterTestCase.literal_not_equiv_string_bytes)
          .expr("string(b\"\\303\\277\") != '''\\303\\277'''")
          .cost(costOf(2, 2))
          .optimizedCost(costOf(1, 1)),
      new TestCase(InterpreterTestCase.literal_equiv_bytes_string)
          .expr("string(b\"\\303\\277\") == '\u00FF'")
          .cost(costOf(2, 2))
          .optimizedCost(costOf(1, 1)),
      new TestCase(InterpreterTestCase.literal_bytes_string)
          .expr("string(b'aaa\"bbb')")
          .cost(costOf(1, 1))
          .optimizedCost(costOf(0, 0))
          .out("aaa\"bbb"),
      new TestCase(InterpreterTestCase.literal_bytes_string2)
          .expr("string(b\"\"\"Kim\\t\"\"\")")
          .cost(costOf(1, 1))
          .optimizedCost(costOf(0, 0))
          .out("Kim\t"),
      new TestCase(InterpreterTestCase.literal_pb_struct)
          .expr("google.protobuf.Struct{fields: {'uno': 1.0, 'dos': 2.0}}")
          .out(mapOf("uno", 1.0d, "dos", 2.0d)),
      new TestCase(InterpreterTestCase.literal_pb3_msg)
          .container("google.api.expr")
          .types(Expr.getDefaultInstance())
          .expr(
              "v1alpha1.Expr{ \n"
                  + "	id: 1, \n"
                  + "	const_expr: v1alpha1.Constant{ \n"
                  + "		string_value: \"oneof_test\" \n"
                  + "	}\n"
                  + "}")
          .cost(costOf(0, 0))
          .out(
              Expr.newBuilder()
                  .setId(1)
                  .setConstExpr(Constant.newBuilder().setStringValue("oneof_test"))
                  .build()),
      new TestCase(InterpreterTestCase.literal_pb_enum)
          .container("google.api.expr.test.v1.proto3")
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .expr(
              "TestAllTypes{\n"
                  + "repeated_nested_enum: [\n"
                  + "	0,\n"
                  + "	TestAllTypes.NestedEnum.BAZ,\n"
                  + "	TestAllTypes.NestedEnum.BAR],\n"
                  + "repeated_int32: [\n"
                  + "	TestAllTypes.NestedEnum.FOO,\n"
                  + "	TestAllTypes.NestedEnum.BAZ]}")
          .cost(costOf(0, 0))
          .out(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.newBuilder()
                  .addRepeatedNestedEnum(
                      com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.NestedEnum
                          .FOO)
                  .addRepeatedNestedEnum(
                      com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.NestedEnum
                          .BAZ)
                  .addRepeatedNestedEnum(
                      com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.NestedEnum
                          .BAR)
                  .addRepeatedInt32(0)
                  .addRepeatedInt32(2)
                  .build()),
      new TestCase(InterpreterTestCase.timestamp_eq_timestamp)
          .expr("timestamp(0) == timestamp(0)")
          .cost(costOf(3, 3))
          .optimizedCost(costOf(1, 1)),
      new TestCase(InterpreterTestCase.timestamp_ne_timestamp)
          .expr("timestamp(1) != timestamp(2)")
          .cost(costOf(3, 3))
          .optimizedCost(costOf(1, 1)),
      new TestCase(InterpreterTestCase.timestamp_lt_timestamp)
          .expr("timestamp(0) < timestamp(1)")
          .cost(costOf(3, 3))
          .optimizedCost(costOf(1, 1)),
      new TestCase(InterpreterTestCase.timestamp_le_timestamp)
          .expr("timestamp(2) <= timestamp(2)")
          .cost(costOf(3, 3))
          .optimizedCost(costOf(1, 1)),
      new TestCase(InterpreterTestCase.timestamp_gt_timestamp)
          .expr("timestamp(1) > timestamp(0)")
          .cost(costOf(3, 3))
          .optimizedCost(costOf(1, 1)),
      new TestCase(InterpreterTestCase.timestamp_ge_timestamp)
          .expr("timestamp(2) >= timestamp(2)")
          .cost(costOf(3, 3))
          .optimizedCost(costOf(1, 1)),
      new TestCase(InterpreterTestCase.string_to_timestamp)
          .expr("timestamp('1986-04-26T01:23:40Z')")
          .cost(costOf(1, 1))
          .optimizedCost(costOf(0, 0))
          .out(Timestamp.newBuilder().setSeconds(514862620).build()),
      new TestCase(InterpreterTestCase.macro_all_non_strict)
          .expr("![0, 2, 4].all(x, 4/x != 2 && 4/(4-x) != 2)")
          .cost(costOf(5, 38))
          .exhaustiveCost(costOf(38, 38)),
      new TestCase(InterpreterTestCase.macro_all_non_strict_var)
          .expr(
              "code == \"111\" && [\"a\", \"b\"].all(x, x in tags) \n"
                  + "|| code == \"222\" && [\"a\", \"b\"].all(x, x in tags)")
          .env(
              Decls.newVar("code", Decls.String),
              Decls.newVar("tags", Decls.newListType(Decls.String)))
          .in("code", "222", "tags", new String[] {"a", "b"}),
      new TestCase(InterpreterTestCase.macro_exists_lit)
          .expr("[1, 2, 3, 4, 5u, 1.0].exists(e, type(e) == uint)"),
      new TestCase(InterpreterTestCase.macro_exists_nonstrict)
          .expr("[0, 2, 4].exists(x, 4/x == 2 && 4/(4-x) == 2)"),
      new TestCase(InterpreterTestCase.macro_exists_var)
          .expr("elems.exists(e, type(e) == uint)")
          .cost(costOf(0, 9223372036854775807L))
          .exhaustiveCost(costOf(0, 9223372036854775807L))
          .env(Decls.newVar("elems", Decls.newListType(Decls.Dyn)))
          .in("elems", new Object[] {0, 1, 2, 3, 4, ULong.valueOf(5), 6}),
      new TestCase(InterpreterTestCase.macro_exists_one)
          .expr("[1, 2, 3].exists_one(x, (x % 2) == 0)"),
      new TestCase(InterpreterTestCase.macro_filter).expr("[1, 2, 3].filter(x, x > 2) == [3]"),
      new TestCase(InterpreterTestCase.macro_has_map_key)
          .expr("has({'a':1}.a) && !has({}.a)")
          .cost(costOf(1, 4))
          .exhaustiveCost(costOf(4, 4)),
      new TestCase(InterpreterTestCase.macro_has_pb2_field)
          .container("google.api.expr.test.v1.proto2")
          .types(
              com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .env(
              Decls.newVar(
                  "pb2", Decls.newObjectType("google.api.expr.test.v1.proto2.TestAllTypes")))
          .in(
              "pb2",
              com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes.newBuilder()
                  .addRepeatedBool(false)
                  .putMapInt64NestedType(
                      1,
                      com.google.api.expr.test.v1.proto2.TestAllTypesProto.NestedTestAllTypes
                          .getDefaultInstance())
                  .build())
          .expr(
              "has(TestAllTypes{standalone_enum: TestAllTypes.NestedEnum.BAR}.standalone_enum) \n"
                  + "&& has(TestAllTypes{standalone_enum: TestAllTypes.NestedEnum.FOO}.standalone_enum) \n"
                  + "&& !has(TestAllTypes{single_nested_enum: TestAllTypes.NestedEnum.FOO}.single_nested_message) \n"
                  + "&& !has(TestAllTypes{}.standalone_enum) \n"
                  + "&& has(TestAllTypes{single_nested_enum: TestAllTypes.NestedEnum.FOO}.single_nested_enum) \n"
                  + "&& !has(pb2.single_int64) \n"
                  + "&& has(pb2.repeated_bool) \n"
                  + "&& !has(pb2.repeated_int32) \n"
                  + "&& has(pb2.map_int64_nested_type) \n"
                  + "&& !has(pb2.map_string_string)")
          .cost(costOf(1, 29))
          .exhaustiveCost(costOf(29, 29)),
      new TestCase(InterpreterTestCase.macro_has_pb3_field)
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .env(
              Decls.newVar(
                  "pb3", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))
          .container("google.api.expr.test.v1.proto3")
          .in(
              "pb3",
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.newBuilder()
                  .addRepeatedBool(false)
                  .putMapInt64NestedType(
                      1,
                      com.google.api.expr.test.v1.proto3.TestAllTypesProto.NestedTestAllTypes
                          .getDefaultInstance())
                  .build())
          .expr(
              "has(TestAllTypes{standalone_enum: TestAllTypes.NestedEnum.BAR}.standalone_enum) \n"
                  + "&& !has(TestAllTypes{standalone_enum: TestAllTypes.NestedEnum.FOO}.standalone_enum) \n"
                  + "&& !has(TestAllTypes{single_nested_enum: TestAllTypes.NestedEnum.FOO}.single_nested_message) \n"
                  + "&& has(TestAllTypes{single_nested_enum: TestAllTypes.NestedEnum.FOO}.single_nested_enum) \n"
                  + "&& !has(TestAllTypes{}.single_nested_message) \n"
                  + "&& has(TestAllTypes{single_nested_message: TestAllTypes.NestedMessage{}}.single_nested_message) \n"
                  + "&& !has(TestAllTypes{}.standalone_enum) \n"
                  + "&& !has(pb3.single_int64) \n"
                  + "&& has(pb3.repeated_bool) \n"
                  + "&& !has(pb3.repeated_int32) \n"
                  + "&& has(pb3.map_int64_nested_type) \n"
                  + "&& !has(pb3.map_string_string)")
          .cost(costOf(1, 35))
          .exhaustiveCost(costOf(35, 35)),
      new TestCase(InterpreterTestCase.macro_map)
          .expr("[1, 2, 3].map(x, x * 2) == [2, 4, 6]")
          .cost(costOf(6, 14))
          .exhaustiveCost(costOf(14, 14)),
      new TestCase(InterpreterTestCase.matches)
          .expr(
              "input.matches('k.*') \n"
                  + "&& !'foo'.matches('k.*') \n"
                  + "&& !'bar'.matches('k.*') \n"
                  + "&& 'kilimanjaro'.matches('.*ro')")
          .cost(costOf(2, 10))
          .exhaustiveCost(costOf(10, 10))
          .env(Decls.newVar("input", Decls.String))
          .in("input", "kathmandu"),
      new TestCase(InterpreterTestCase.nested_proto_field)
          .expr("pb3.single_nested_message.bb")
          .cost(costOf(1, 1))
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .env(
              Decls.newVar(
                  "pb3", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))
          .in(
              "pb3",
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.newBuilder()
                  .setSingleNestedMessage(
                      com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                          .NestedMessage.newBuilder()
                          .setBb(1234)
                          .build())
                  .build())
          .out(intOf(1234)),
      new TestCase(InterpreterTestCase.nested_proto_field_with_index)
          .expr("pb3.map_int64_nested_type[0].child.payload.single_int32 == 1")
          .cost(costOf(2, 2))
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .env(
              Decls.newVar(
                  "pb3", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))
          .in(
              "pb3",
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.newBuilder()
                  .putMapInt64NestedType(
                      0,
                      com.google.api.expr.test.v1.proto3.TestAllTypesProto.NestedTestAllTypes
                          .newBuilder()
                          .setChild(
                              com.google.api.expr.test.v1.proto3.TestAllTypesProto
                                  .NestedTestAllTypes.newBuilder()
                                  .setPayload(
                                      com.google.api.expr.test.v1.proto3.TestAllTypesProto
                                          .TestAllTypes.newBuilder()
                                          .setSingleInt32(1)))
                          .build())
                  .build()),
      new TestCase(InterpreterTestCase.or_true_1st)
          .expr("ai == 20 || ar[\"foo\"] == \"bar\"")
          .cost(costOf(2, 5))
          .exhaustiveCost(costOf(5, 5))
          .env(
              Decls.newVar("ai", Decls.Int),
              Decls.newVar("ar", Decls.newMapType(Decls.String, Decls.String)))
          .in("ai", 20, "ar", mapOf("foo", "bar")),
      new TestCase(InterpreterTestCase.or_true_2nd)
          .expr("ai == 20 || ar[\"foo\"] == \"bar\"")
          .cost(costOf(2, 5))
          .exhaustiveCost(costOf(5, 5))
          .env(
              Decls.newVar("ai", Decls.Int),
              Decls.newVar("ar", Decls.newMapType(Decls.String, Decls.String)))
          .in("ai", 2, "ar", mapOf("foo", "bar")),
      new TestCase(InterpreterTestCase.or_false)
          .expr("ai == 20 || ar[\"foo\"] == \"bar\"")
          .cost(costOf(2, 5))
          .exhaustiveCost(costOf(5, 5))
          .env(
              Decls.newVar("ai", Decls.Int),
              Decls.newVar("ar", Decls.newMapType(Decls.String, Decls.String)))
          .in("ai", 2, "ar", mapOf("foo", "baz"))
          .out(False),
      new TestCase(InterpreterTestCase.or_error_1st_error)
          .expr("1/0 != 0 || false")
          .cost(costOf(2, 3))
          .exhaustiveCost(costOf(3, 3))
          .err("divide by zero"),
      new TestCase(InterpreterTestCase.or_error_2nd_error)
          .expr("false || 1/0 != 0")
          .cost(costOf(0, 3))
          .exhaustiveCost(costOf(3, 3))
          .err("divide by zero"),
      new TestCase(InterpreterTestCase.or_error_1st_true)
          .expr("1/0 != 0 || true")
          .cost(costOf(2, 3))
          .exhaustiveCost(costOf(3, 3))
          .out(True),
      new TestCase(InterpreterTestCase.or_error_2nd_true)
          .expr("true || 1/0 != 0")
          .cost(costOf(0, 3))
          .exhaustiveCost(costOf(3, 3))
          .out(True),
      new TestCase(InterpreterTestCase.pkg_qualified_id)
          .expr("b.c.d != 10")
          .cost(costOf(2, 2))
          .container("a.b")
          .env(Decls.newVar("a.b.c.d", Decls.Int))
          .in("a.b.c.d", 9),
      new TestCase(InterpreterTestCase.pkg_qualified_id_unchecked)
          .expr("c.d != 10")
          .cost(costOf(2, 2))
          .unchecked()
          .container("a.b")
          .in("a.c.d", 9),
      new TestCase(InterpreterTestCase.pkg_qualified_index_unchecked)
          .expr("b.c['d'] == 10")
          .cost(costOf(2, 2))
          .unchecked()
          .container("a.b")
          .in("a.b.c", mapOf("d", 10)),
      new TestCase(InterpreterTestCase.select_key)
          .expr(
              "m.strMap['val'] == 'string'\n"
                  + "&& m.floatMap['val'] == 1.5\n"
                  + "&& m.doubleMap['val'] == -2.0\n"
                  + "&& m.intMap['val'] == -3\n"
                  + "&& m.int32Map['val'] == 4\n"
                  + "&& m.int64Map['val'] == -5\n"
                  + "&& m.uintMap['val'] == 6u\n"
                  + "&& m.uint32Map['val'] == 7u\n"
                  + "&& m.uint64Map['val'] == 8u\n"
                  + "&& m.boolMap['val'] == true\n"
                  + "&& m.boolMap['val'] != false")
          .cost(costOf(2, 32))
          .exhaustiveCost(costOf(32, 32))
          .env(Decls.newVar("m", Decls.newMapType(Decls.String, Decls.Dyn)))
          .in(
              "m",
              mapOf(
                  "strMap", mapOf("val", "string"),
                  "floatMap", mapOf("val", 1.5f),
                  "doubleMap", mapOf("val", -2.0d),
                  "intMap", mapOf("val", -3),
                  "int32Map", mapOf("val", 4),
                  "int64Map", mapOf("val", -5L),
                  "uintMap", mapOf("val", ULong.valueOf(6)),
                  "uint32Map", mapOf("val", ULong.valueOf(7)),
                  "uint64Map", mapOf("val", ULong.valueOf(8L)),
                  "boolMap", mapOf("val", true))),
      new TestCase(InterpreterTestCase.select_bool_key)
          .expr(
              "m.boolStr[true] == 'string'\n"
                  + "&& m.boolFloat32[true] == 1.5\n"
                  + "&& m.boolFloat64[false] == -2.1\n"
                  + "&& m.boolInt[false] == -3\n"
                  + "&& m.boolInt32[false] == 0\n"
                  + "&& m.boolInt64[true] == 4\n"
                  + "&& m.boolUint[true] == 5u\n"
                  + "&& m.boolUint32[true] == 6u\n"
                  + "&& m.boolUint64[false] == 7u\n"
                  + "&& m.boolBool[true]\n"
                  + "&& m.boolIface[false] == true")
          .cost(costOf(2, 31))
          .exhaustiveCost(costOf(31, 31))
          .env(Decls.newVar("m", Decls.newMapType(Decls.String, Decls.Dyn)))
          .in(
              "m",
              mapOf(
                  "boolStr", mapOf(true, "string"),
                  "boolFloat32", mapOf(true, 1.5f),
                  "boolFloat64", mapOf(false, -2.1d),
                  "boolInt", mapOf(false, -3),
                  "boolInt32", mapOf(false, 0),
                  "boolInt64", mapOf(true, 4L),
                  "boolUint", mapOf(true, ULong.valueOf(5)),
                  "boolUint32", mapOf(true, ULong.valueOf(6)),
                  "boolUint64", mapOf(false, ULong.valueOf(7L)),
                  "boolBool", mapOf(true, true),
                  "boolIface", mapOf(false, true))),
      new TestCase(InterpreterTestCase.select_uint_key)
          .expr(
              "m.uintIface[1u] == 'string'\n"
                  + "&& m.uint32Iface[2u] == 1.5\n"
                  + "&& m.uint64Iface[3u] == -2.1\n"
                  + "&& m.uint64String[4u] == 'three'")
          .cost(costOf(2, 11))
          .exhaustiveCost(costOf(11, 11))
          .env(Decls.newVar("m", Decls.newMapType(Decls.String, Decls.Dyn)))
          .in(
              "m",
              mapOf(
                  "uintIface", mapOf(ULong.valueOf(1), "string"),
                  "uint32Iface", mapOf(ULong.valueOf(2), 1.5),
                  "uint64Iface", mapOf(ULong.valueOf(3), -2.1),
                  "uint64String", mapOf(ULong.valueOf(4), "three"))),
      new TestCase(InterpreterTestCase.select_index)
          .expr(
              "m.strList[0] == 'string'\n"
                  + "&& m.floatList[0] == 1.5\n"
                  + "&& m.doubleList[0] == -2.0\n"
                  + "&& m.intList[0] == -3\n"
                  + "&& m.int32List[0] == 4\n"
                  + "&& m.int64List[0] == -5\n"
                  + "&& m.uintList[0] == 6u\n"
                  + "&& m.uint32List[0] == 7u\n"
                  + "&& m.uint64List[0] == 8u\n"
                  + "&& m.boolList[0] == true\n"
                  + "&& m.boolList[1] != true\n"
                  + "&& m.ifaceList[0] == {}")
          .cost(costOf(2, 35))
          .exhaustiveCost(costOf(35, 35))
          .env(Decls.newVar("m", Decls.newMapType(Decls.String, Decls.Dyn)))
          .in(
              "m",
              mapOf(
                  "strList", new String[] {"string"},
                  "floatList", new Float[] {1.5f},
                  "doubleList", new Double[] {-2.0d},
                  "intList", new int[] {-3},
                  "int32List", new int[] {4},
                  "int64List", new long[] {-5L},
                  "uintList", new Object[] {ULong.valueOf(6)},
                  "uint32List", new Object[] {ULong.valueOf(7)},
                  "uint64List", new Object[] {ULong.valueOf(8L)},
                  "boolList", new boolean[] {true, false},
                  "ifaceList", new Object[] {new HashMap<>()})),
      new TestCase(InterpreterTestCase.select_field)
          .expr(
              "a.b.c\n"
                  + "&& pb3.repeated_nested_enum[0] == TestAllTypes.NestedEnum.BAR\n"
                  + "&& json.list[0] == 'world'")
          .cost(costOf(1, 7))
          .exhaustiveCost(costOf(7, 7))
          .container("google.api.expr.test.v1.proto3")
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .env(
              Decls.newVar("a.b", Decls.newMapType(Decls.String, Decls.Bool)),
              Decls.newVar(
                  "pb3", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")),
              Decls.newVar("json", Decls.newMapType(Decls.String, Decls.Dyn)))
          .in(
              "a.b",
              mapOf("c", true),
              "pb3",
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.newBuilder()
                  .addRepeatedNestedEnum(
                      com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.NestedEnum
                          .BAR)
                  .build(),
              "json",
              Value.newBuilder()
                  .setStructValue(
                      Struct.newBuilder()
                          .putFields(
                              "list",
                              Value.newBuilder()
                                  .setListValue(
                                      ListValue.newBuilder()
                                          .addValues(Value.newBuilder().setStringValue("world")))
                                  .build()))
                  .build()),
      // pb2 primitive fields may have default values set.
      new TestCase(InterpreterTestCase.select_pb2_primitive_fields)
          .expr(
              "!has(a.single_int32)\n"
                  + "&& a.single_int32 == -32\n"
                  + "&& a.single_int64 == -64\n"
                  + "&& a.single_uint32 == 32u\n"
                  + "&& a.single_uint64 == 64u\n"
                  + "&& a.single_float == 3.0\n"
                  + "&& a.single_double == 6.4\n"
                  + "&& a.single_bool\n"
                  + "&& \"empty\" == a.single_string")
          .cost(costOf(3, 26))
          .exhaustiveCost(costOf(26, 26))
          .types(TestAllTypes.getDefaultInstance())
          .in("a", TestAllTypes.newBuilder().build())
          .env(
              Decls.newVar(
                  "a", Decls.newObjectType("google.api.expr.test.v1.proto2.TestAllTypes"))),
      // Wrapper type nil or value test.
      new TestCase(InterpreterTestCase.select_pb3_wrapper_fields)
          .expr(
              "!has(a.single_int32_wrapper) && a.single_int32_wrapper == null\n"
                  + "&& has(a.single_int64_wrapper) && a.single_int64_wrapper == 0\n"
                  + "&& has(a.single_string_wrapper) && a.single_string_wrapper == \"hello\"\n"
                  + "&& a.single_int64_wrapper == Int32Value{value: 0}")
          .cost(costOf(3, 21))
          .exhaustiveCost(costOf(21, 21))
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .abbrevs("google.protobuf.Int32Value")
          .env(
              Decls.newVar("a", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))
          .in(
              "a",
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.newBuilder()
                  .setSingleInt64Wrapper(Int64Value.newBuilder().build())
                  .setSingleStringWrapper(StringValue.of("hello"))
                  .build()),
      new TestCase(InterpreterTestCase.select_pb3_compare)
          .expr("a.single_uint64 > 3u")
          .cost(costOf(2, 2))
          .container("google.api.expr.test.v1.proto3")
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .env(
              Decls.newVar("a", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))
          .in(
              "a",
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.newBuilder()
                  .setSingleUint64(10)
                  .build())
          .out(True),
      new TestCase(InterpreterTestCase.select_pb3_compare_signed)
          .expr("a.single_int64 > 3")
          .cost(costOf(2, 2))
          .container("google.api.expr.test.v1.proto3")
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .env(
              Decls.newVar("a", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))
          .in(
              "a",
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.newBuilder()
                  .setSingleInt64(10)
                  .build())
          .out(True),
      new TestCase(InterpreterTestCase.select_custom_pb3_compare)
          .expr("a.bb > 100")
          .cost(costOf(2, 2))
          .container("google.api.expr.test.v1.proto3")
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.NestedMessage
                  .getDefaultInstance())
          .env(
              Decls.newVar(
                  "a",
                  Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes.NestedMessage")))
          .attrs(
              new CustAttrFactory(
                  newAttributeFactory(
                      testContainer("google.api.expr.test.v1.proto3"),
                      DefaultTypeAdapter.Instance,
                      newEmptyRegistry())))
          .in(
              "a",
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.NestedMessage
                  .newBuilder()
                  .setBb(101)
                  .build())
          .out(True),
      new TestCase(InterpreterTestCase.select_relative)
          .expr("json('{\"hi\":\"world\"}').hi == 'world'")
          .cost(costOf(2, 2))
          .env(
              Decls.newFunction(
                  "json",
                  singletonList(
                      Decls.newOverload("string_to_json", singletonList(Decls.String), Decls.Dyn))))
          .funcs(
              Overload.unary(
                  "json",
                  val -> {
                    if (val.type() != StringT.StringType) {
                      return noSuchOverload(StringType, "json", val);
                    }
                    StringT str = (StringT) val;
                    Map<String, Object> m = new HashMap<>();
                    // TODO need some toJson here
                    throw new UnsupportedOperationException("IMPLEMENT ME");
                    // json.Unmarshal([]byte(str), &m)
                    // return DefaultTypeAdapter.Instance.nativeToValue(m);
                  }))
          .disabled("would need some JSON library to implement this test..."),
      new TestCase(InterpreterTestCase.select_subsumed_field)
          .expr("a.b.c")
          .cost(costOf(1, 1))
          .env(
              Decls.newVar("a.b.c", Decls.Int),
              Decls.newVar("a.b", Decls.newMapType(Decls.String, Decls.String)))
          .in("a.b.c", 10, "a.b", mapOf("c", "ten"))
          .out(intOf(10)),
      new TestCase(InterpreterTestCase.select_empty_repeated_nested)
          .expr("TestAllTypes{}.repeated_nested_message.size() == 0")
          .cost(costOf(2, 2))
          .types(
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance())
          .container("google.api.expr.test.v1.proto3")
          .out(True),
      new TestCase(InterpreterTestCase.duration_get_milliseconds)
          .expr("x.getMilliseconds()")
          .env(Decls.newVar("x", Decls.Duration))
          .in(
              "x",
              com.google.protobuf.Duration.newBuilder().setSeconds(123).setNanos(321456789).build())
          .cost(costOf(2, 2))
          .exhaustiveCost(costOf(2, 2))
          .out(321),
      new TestCase(InterpreterTestCase.timestamp_get_hours_tz)
          .expr("timestamp('2009-02-13T23:31:30Z').getHours('2:00')")
          .out(intOf(1))
          .cost(costOf(2, 2))
          .optimizedCost(costOf(1, 1)),
      new TestCase(InterpreterTestCase.index_out_of_range)
          .expr("[1, 2, 3][3]")
          .err("invalid_argument: index '3' out of range in list of size '3'"),
      new TestCase(InterpreterTestCase.parse_nest_list_index)
          .expr(
              "a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[a[0]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]")
          .env(Decls.newVar("a", Decls.newListType(Decls.Int)))
          .in("a", new long[] {0})
          .out(intOf(0)),
      new TestCase(InterpreterTestCase.root_null_handling)
          .expr("a == null")
          .env(Decls.newVar("a", Decls.Any))
          .in("a", null)
          .out(true),
      new TestCase(InterpreterTestCase.root_no_such_attribute)
          .expr("a == null")
          .env(Decls.newVar("a", Decls.Any))
          .in("b", null)
          .err("undeclared reference to 'id: 1, names: [a]' (in container '')"),
    };
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void interpreter(TestCase tc) {
    Assumptions.assumeTrue(tc.disabled == null, tc.disabled);

    Program prg = program(tc);
    Val want = True;
    if (tc.out != null) {
      want = (Val) tc.out;
    }
    Val got = prg.interpretable.eval(prg.activation);
    if (UnknownT.isUnknown(want)) {
      assertThat(got).isEqualTo(want);
    } else if (tc.err != null) {
      assertThat(got).isInstanceOf(Err.class).extracting(Object::toString).isEqualTo(tc.err);
    } else if (isError(want)) {
      assertThat(got).isEqualTo(want);
      assertThat(got.equal(want)).isSameAs(True);
    } else {
      if (isError(got) && ((Err) got).hasCause()) {
        throw ((Err) got).toRuntimeException();
      }
      assertThat(got).isEqualTo(want);
      assertThat(got.equal(want)).isSameAs(True);
    }

    if (tc.cost != null) {
      Cost cost = estimateCost(prg.interpretable);
      assertThat(cost).isEqualTo(tc.cost);
    }
    EvalState state = newEvalState();
    Map<String, InterpretableDecorator> opts = new HashMap<>();
    opts.put("optimize", optimize());
    opts.put("exhaustive", exhaustiveEval(state));
    opts.put("track", trackState(state));
    for (Entry<String, InterpretableDecorator> en : opts.entrySet()) {
      String mode = en.getKey();
      InterpretableDecorator opt = en.getValue();

      prg = program(tc, opt);
      got = prg.interpretable.eval(prg.activation);
      if (UnknownT.isUnknown(want)) {
        assertThat(got).isEqualTo(want);
      } else if (tc.err != null) {
        assertThat(got)
            .isInstanceOf(Err.class)
            .extracting(Object::toString)
            .asString()
            .startsWith(tc.err);
      } else {
        assertThat(got).isEqualTo(want);
        assertThat(got.equal(want)).isSameAs(True);
      }

      if ("exhaustive".equals(mode) && tc.cost != null) {
        Cost wantedCost = tc.cost;
        if (tc.exhaustiveCost != null) {
          wantedCost = tc.exhaustiveCost;
        }
        Cost cost = estimateCost(prg.interpretable);
        assertThat(cost).isEqualTo(wantedCost);
      }
      if ("optimize".equals(mode) && tc.cost != null) {
        Cost wantedCost = tc.cost;
        if (tc.optimizedCost != null) {
          wantedCost = tc.optimizedCost;
        }
        Cost cost = estimateCost(prg.interpretable);
        assertThat(cost).isEqualTo(wantedCost);
      }
      state.reset();
    }
  }

  @Test
  void protoAttributeOpt() {
    Program inst =
        program(
            new TestCase(InterpreterTestCase.nested_proto_field_with_index)
                .expr("pb3.map_int64_nested_type[0].child.payload.single_int32")
                .types(
                    com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                        .getDefaultInstance())
                .env(
                    Decls.newVar(
                        "pb3", Decls.newObjectType("google.api.expr.test.v1.proto3.TestAllTypes")))
                .in(
                    "pb3",
                    com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.newBuilder()
                        .putMapInt64NestedType(
                            0,
                            com.google.api.expr.test.v1.proto3.TestAllTypesProto.NestedTestAllTypes
                                .newBuilder()
                                .setChild(
                                    com.google.api.expr.test.v1.proto3.TestAllTypesProto
                                        .NestedTestAllTypes.newBuilder()
                                        .setPayload(
                                            com.google.api.expr.test.v1.proto3.TestAllTypesProto
                                                .TestAllTypes.newBuilder()
                                                .setSingleInt32(1)))
                                .build())
                        .build()),
            optimize());
    assertThat(inst.interpretable).isInstanceOf(InterpretableAttribute.class);
    InterpretableAttribute attr = (InterpretableAttribute) inst.interpretable;
    assertThat(attr.attr()).isInstanceOf(NamespacedAttribute.class);
    NamespacedAttribute absAttr = (NamespacedAttribute) attr.attr();
    List<Qualifier> quals = absAttr.qualifiers();
    assertThat(quals).hasSize(5);
    assertThat(isFieldQual(quals.get(0), "map_int64_nested_type")).isTrue();
    assertThat(isConstQual(quals.get(1), IntZero)).isTrue();
    assertThat(isFieldQual(quals.get(2), "child")).isTrue();
    assertThat(isFieldQual(quals.get(3), "payload")).isTrue();
    assertThat(isFieldQual(quals.get(4), "single_int32")).isTrue();
  }

  @Test
  void logicalAndMissingType() {
    Source src = newTextSource("a && TestProto{c: true}.c");

    ParseResult parsed = Parser.parseAllMacros(src);
    assertThat(parsed.hasErrors()).withFailMessage(parsed.getErrors()::toDisplayString).isFalse();

    TypeRegistry reg = newRegistry();
    Container cont = Container.defaultContainer;
    AttributeFactory attrs = newAttributeFactory(cont, reg, reg);
    Interpreter intr = newStandardInterpreter(cont, reg, reg, attrs);
    assertThatThrownBy(() -> intr.newUncheckedInterpretable(parsed.getExpr()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("unknown type: TestProto");
  }

  @Test
  void exhaustiveConditionalExpr() {
    Source src = newTextSource("a ? b < 1.0 : c == ['hello']");
    ParseResult parsed = Parser.parseAllMacros(src);
    assertThat(parsed.hasErrors()).withFailMessage(parsed.getErrors()::toDisplayString).isFalse();

    EvalState state = newEvalState();
    Container cont = Container.defaultContainer;
    TypeRegistry reg = newRegistry(ParsedExpr.getDefaultInstance());
    AttributeFactory attrs = newAttributeFactory(cont, reg, reg);
    Interpreter intr = newStandardInterpreter(cont, reg, reg, attrs);
    Interpretable interpretable =
        intr.newUncheckedInterpretable(parsed.getExpr(), exhaustiveEval(state));
    Activation vars =
        newActivation(
            mapOf(
                "a", True,
                "b", doubleOf(0.999),
                "c", ListT.newStringArrayList(new String[] {"hello"})));
    Val result = interpretable.eval(vars);
    // Operator "_==_" is at Expr 7, should be evaluated in exhaustive mode
    // even though "a" is true
    Val ev = state.value(7);
    // "==" should be evaluated in exhaustive mode though unnecessary
    assertThat(ev).withFailMessage("Else expression expected to be true").isSameAs(True);
    assertThat(result).isSameAs(True);
  }

  @Test
  void exhaustiveLogicalOrEquals() {
    // a || b == "b"
    // Operator "==" is at Expr 4, should be evaluated though "a" is true
    Source src = newTextSource("a || b == \"b\"");
    ParseResult parsed = Parser.parseAllMacros(src);
    assertThat(parsed.hasErrors()).withFailMessage(parsed.getErrors()::toDisplayString).isFalse();

    EvalState state = newEvalState();
    TypeRegistry reg = newRegistry(Expr.getDefaultInstance());
    Container cont = testContainer("test");
    AttributeFactory attrs = newAttributeFactory(cont, reg, reg);
    Interpreter interp = newStandardInterpreter(cont, reg, reg, attrs);
    Interpretable i = interp.newUncheckedInterpretable(parsed.getExpr(), exhaustiveEval(state));
    Activation vars = newActivation(mapOf("a", true, "b", "b"));
    Val result = i.eval(vars);
    Val rhv = state.value(3);
    // "==" should be evaluated in exhaustive mode though unnecessary
    assertThat(rhv)
        .withFailMessage("Right hand side expression expected to be true")
        .isSameAs(True);
    assertThat(result).isSameAs(True);
  }

  @Test
  void setProto2PrimitiveFields() {
    // Test the use of proto2 primitives within object construction.
    Source src =
        newTextSource(
            "input == TestAllTypes{\n"
                + "  single_int32: 1,\n"
                + "  single_int64: 2,\n"
                + "  single_uint32: 3u,\n"
                + "  single_uint64: 4u,\n"
                + "  single_float: -3.3,\n"
                + "  single_double: -2.2,\n"
                + "  single_string: \"hello world\",\n"
                + "  single_bool: true\n"
                + "}");
    ParseResult parsed = Parser.parseAllMacros(src);
    assertThat(parsed.hasErrors()).withFailMessage(parsed.getErrors()::toDisplayString).isFalse();

    Container cont = testContainer("google.api.expr.test.v1.proto2");
    TypeRegistry reg =
        newRegistry(
            com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes.getDefaultInstance());
    CheckerEnv env = newStandardCheckerEnv(cont, reg);
    env.add(
        singletonList(
            Decls.newVar(
                "input", Decls.newObjectType("google.api.expr.test.v1.proto2.TestAllTypes"))));
    CheckResult checkResult = Checker.Check(parsed, src, env);
    if (parsed.hasErrors()) {
      throw new IllegalArgumentException(parsed.getErrors().toDisplayString());
    }

    AttributeFactory attrs = newAttributeFactory(cont, reg, reg);
    Interpreter i = newStandardInterpreter(cont, reg, reg, attrs);
    Interpretable eval = i.newInterpretable(checkResult.getCheckedExpr());
    int one = 1;
    long two = 2L;
    int three = 3;
    long four = 4L;
    float five = -3.3f;
    double six = -2.2d;
    String str = "hello world";
    boolean truth = true;
    com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes input =
        com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes.newBuilder()
            .setSingleInt32(one)
            .setSingleInt64(two)
            .setSingleUint32(three)
            .setSingleUint64(four)
            .setSingleFloat(five)
            .setSingleDouble(six)
            .setSingleString(str)
            .setSingleBool(truth)
            .build();
    Activation vars = newActivation(mapOf("input", reg.nativeToValue(input)));
    Val result = eval.eval(vars);
    assertThat(result.value()).isInstanceOf(Boolean.class);
    boolean got = (Boolean) result.value();
    assertThat(got).isTrue();
  }

  @Test
  void missingIdentInSelect() {
    Source src = newTextSource("a.b.c");
    ParseResult parsed = Parser.parseAllMacros(src);
    assertThat(parsed.hasErrors()).withFailMessage(parsed.getErrors()::toDisplayString).isFalse();

    Container cont = testContainer("test");
    TypeRegistry reg = newRegistry();
    CheckerEnv env = newStandardCheckerEnv(cont, reg);
    env.add(Decls.newVar("a.b", Decls.Dyn));
    CheckResult checkResult = Checker.Check(parsed, src, env);
    if (parsed.hasErrors()) {
      throw new IllegalArgumentException(parsed.getErrors().toDisplayString());
    }

    AttributeFactory attrs = newPartialAttributeFactory(cont, reg, reg);
    Interpreter interp = newStandardInterpreter(cont, reg, reg, attrs);
    Interpretable i = interp.newInterpretable(checkResult.getCheckedExpr());
    Activation vars =
        newPartialActivation(
            mapOf("a.b", mapOf("d", "hello")), newAttributePattern("a.b").qualString("c"));
    Val result = i.eval(vars);
    assertThat(result).isInstanceOf(UnknownT.class);

    result = i.eval(emptyActivation());
    assertThat(result).isInstanceOf(Err.class);
  }

  static class ConvTestCase {
    final String in;
    Val out;
    boolean fail;
    String err;

    ConvTestCase(String in) {
      this.in = in;
    }

    ConvTestCase out(Val out) {
      this.out = out;
      return this;
    }

    ConvTestCase err(String err) {
      this.fail = true;
      this.err = err;
      return this;
    }

    ConvTestCase fail() {
      this.fail = true;
      return this;
    }

    @Override
    public String toString() {
      return "ConvTestCase{" + "in='" + in + '\'' + '}';
    }
  }

  @SuppressWarnings("unused")
  static ConvTestCase[] typeConversionOptTests() {
    long uint64_10000000000000000000 = -8446744073709551616L;

    return new ConvTestCase[] {
      new ConvTestCase("string(b'\\000\\xff')").err("invalid UTF-8"),
      new ConvTestCase("b'\\000\\xff'").out(bytesOf(new byte[] {0, (byte) 0xff})),
      new ConvTestCase("double(18446744073709551615u)").out(doubleOf(1.8446744073709551615e19)),
      new ConvTestCase("uint(1e19)").out(uintOf(uint64_10000000000000000000)),
      new ConvTestCase("int(-123.456)").out(intOf(-123)),
      new ConvTestCase("int(1.9)").out(intOf(1)),
      new ConvTestCase("int(-7.9)").out(intOf(-7)),
      new ConvTestCase("int(11.5)").out(intOf(11)),
      new ConvTestCase("int(-3.5)").out(intOf(-3)),
      new ConvTestCase("string(timestamp('2009-02-13T23:31:30Z'))")
          .out(stringOf("2009-02-13T23:31:30Z")),
      new ConvTestCase("string(timestamp('2009-02-13T23:31:30.999999999Z'))")
          .out(stringOf("2009-02-13T23:31:30.999999999Z")),
      new ConvTestCase("string(duration('1000000s'))").out(stringOf("1000000s")),
      new ConvTestCase("timestamp('0000-01-01T00:00:00Z')").err("range"),
      new ConvTestCase("timestamp('9999-12-31T23:59:59Z')")
          .out(
              TimestampT.timestampOf(
                  Timestamp.newBuilder().setSeconds(TimestampT.maxUnixTime).build())),
      new ConvTestCase("timestamp('10000-01-01T00:00:00Z')").err("range"),
      new ConvTestCase("bool('tru')").fail(),
      new ConvTestCase("bool(\"true\")").out(True),
      new ConvTestCase("bytes(\"hello\")").out(bytesOf("hello".getBytes(StandardCharsets.UTF_8))),
      new ConvTestCase("double(\"_123\")").fail(),
      new ConvTestCase("double(\"123.0\")").out(doubleOf(123.0)),
      new ConvTestCase("duration('12hh3')").fail(),
      new ConvTestCase("duration('12s')").out(DurationT.durationOf(Duration.ofSeconds(12))),
      new ConvTestCase("duration('-320000000000s')").err("range"),
      new ConvTestCase("duration('320000000000s')").err("range"),
      new ConvTestCase("dyn(1u)").out(uintOf(1)),
      new ConvTestCase("int('11l')").fail(),
      new ConvTestCase("int('11')").out(intOf(11)),
      new ConvTestCase("string('11')").out(stringOf("11")),
      new ConvTestCase("timestamp('123')").fail(),
      new ConvTestCase("timestamp(123)")
          .out(TimestampT.timestampOf(Timestamp.newBuilder().setSeconds(123).build())),
      new ConvTestCase("type(null)").out(NullType),
      new ConvTestCase("type(timestamp(int('123')))").out(TimestampT.TimestampType),
      new ConvTestCase("uint(-1)").fail(),
      new ConvTestCase("uint(1)").out(uintOf(1))
    };
  }

  @ParameterizedTest
  @MethodSource("typeConversionOptTests")
  void typeConversionOpt(ConvTestCase tc) {
    Source src = newTextSource(tc.in);
    ParseResult parsed = Parser.parseAllMacros(src);
    assertThat(parsed.hasErrors()).withFailMessage(parsed.getErrors()::toDisplayString).isFalse();
    Container cont = Container.defaultContainer;
    TypeRegistry reg = newRegistry();
    CheckerEnv env = newStandardCheckerEnv(cont, reg);
    CheckResult checkResult = Checker.Check(parsed, src, env);
    if (parsed.hasErrors()) {
      throw new IllegalArgumentException(parsed.getErrors().toDisplayString());
    }
    AttributeFactory attrs = newAttributeFactory(cont, reg, reg);
    Interpreter interp = newStandardInterpreter(cont, reg, reg, attrs);
    // Show that program planning will now produce an error.

    if (!tc.fail) {
      typeConversionOptCheck(tc, checkResult, interp);
    } else {
      Throwable err = catchThrowable(() -> typeConversionOptCheck(tc, checkResult, interp));
      assertThat(err)
          .withFailMessage(() -> format("Expected '%s' to fail with '%s'", tc.in, tc.err))
          .isNotNull();
      // TODO 'err' below comes from "try-catch" of the preceding 'newInterpretable'
      //  Show how the error returned during program planning is the same as the runtime
      //  error which would be produced normally.
      Interpretable i2 = interp.newInterpretable(checkResult.getCheckedExpr());
      Val errVal = i2.eval(emptyActivation());
      String errValStr = errVal.toString();
      assertThat(errValStr).isEqualTo(err.getMessage());

      if (tc.err != null) {
        assertThat(errValStr).contains(tc.err);
      }
    }
  }

  private void typeConversionOptCheck(
      ConvTestCase tc, CheckResult checkResult, Interpreter interp) {
    Interpretable i = interp.newInterpretable(checkResult.getCheckedExpr(), optimize());
    if (tc.out != null) {
      assertThat(i).isInstanceOf(InterpretableConst.class);
      InterpretableConst ic = (InterpretableConst) i;
      ic.value().equal(tc.out);
      assertThat(ic.value()).extracting(o -> o.equal(tc.out)).isSameAs(True);
    }
  }

  static Container testContainer(String name) {
    return newContainer(Container.name(name));
  }

  static class Program {
    final Interpretable interpretable;
    final Activation activation;

    Program(Interpretable interpretable, Activation activation) {
      this.interpretable = Objects.requireNonNull(interpretable);
      this.activation = Objects.requireNonNull(activation);
    }
  }

  static Program program(TestCase tst, InterpretableDecorator... opts) {
    // Configure the package.
    Container cont = Container.defaultContainer;
    if (tst.container != null) {
      cont = testContainer(tst.container);
    }
    if (tst.abbrevs != null) {
      cont = Container.newContainer(Container.name(cont.name()), Container.abbrevs(tst.abbrevs));
    }
    TypeRegistry reg;
    reg = newRegistry();
    if (tst.types != null) {
      reg = newRegistry(tst.types);
    }
    AttributeFactory attrs = newAttributeFactory(cont, reg, reg);
    if (tst.attrs != null) {
      attrs = tst.attrs;
    }

    // Configure the environment.
    CheckerEnv env = newStandardCheckerEnv(cont, reg);
    if (tst.env != null) {
      env.add(tst.env);
    }
    // Configure the program input.
    Activation vars = emptyActivation();
    if (tst.in != null) {
      vars = newActivation(tst.in);
    }
    // Adapt the test output, if needed.
    if (tst.out != null) {
      tst.out = reg.nativeToValue(tst.out);
    }

    Dispatcher disp = newDispatcher();
    disp.add(standardOverloads());
    if (tst.funcs != null) {
      disp.add(tst.funcs);
    }
    Interpreter interp = newInterpreter(disp, cont, reg, reg, attrs);

    // Parse the expression.
    Source s = newTextSource(tst.expr);
    ParseResult parsed = Parser.parseAllMacros(s);
    assertThat(parsed.hasErrors()).withFailMessage(parsed.getErrors()::toDisplayString).isFalse();
    Interpretable prg;
    if (tst.unchecked) {
      // Build the program plan.
      prg = interp.newUncheckedInterpretable(parsed.getExpr(), opts);
      return new Program(prg, vars);
    }
    // Check the expression.
    CheckResult checkResult = Checker.Check(parsed, s, env);

    assertThat(checkResult.hasErrors())
        .withFailMessage(() -> checkResult.getErrors().toDisplayString())
        .isFalse();

    // Build the program plan.
    prg = interp.newInterpretable(checkResult.getCheckedExpr(), opts);
    return new Program(prg, vars);
  }

  static boolean isConstQual(Qualifier q, Val val) {
    if (!(q instanceof ConstantQualifier)) {
      return false;
    }
    return ((ConstantQualifier) q).value().equal(val) == True;
  }

  static boolean isFieldQual(Qualifier q, String fieldName) {
    if (!(q instanceof FieldQualifier)) {
      return false;
    }
    return ((FieldQualifier) q).name.equals(fieldName);
  }
}
