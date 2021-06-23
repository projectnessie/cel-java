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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.Util.deepEquals;
import static org.projectnessie.cel.Util.mapOf;
import static org.projectnessie.cel.checker.CheckerEnv.newStandardCheckerEnv;
import static org.projectnessie.cel.common.containers.Container.newContainer;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newRegistry;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.Activation.newPartialActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.AttributePattern.newAttributePattern;
import static org.projectnessie.cel.interpreter.AttributePattern.newPartialAttributeFactory;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.EvalState.newEvalState;
import static org.projectnessie.cel.interpreter.Interpretable.newConstValue;
import static org.projectnessie.cel.interpreter.Interpreter.newStandardInterpreter;
import static org.projectnessie.cel.interpreter.Interpreter.optimize;
import static org.projectnessie.cel.interpreter.Interpreter.trackState;

import com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes;
import com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.NestedMessage;
import com.google.api.expr.v1alpha1.Decl;
import com.google.api.expr.v1alpha1.Type;
import com.google.protobuf.Any;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.cel.checker.Checker;
import org.projectnessie.cel.checker.Checker.CheckResult;
import org.projectnessie.cel.checker.CheckerEnv;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.Source;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.UnknownT;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.AttributeFactory.NamespacedAttribute;
import org.projectnessie.cel.interpreter.AttributeFactory.Qualifier;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableConst;
import org.projectnessie.cel.parser.Parser;
import org.projectnessie.cel.parser.Parser.ParseResult;

class AttributesTest {

  @Test
  void attributesAbsoluteAttr() {
    TypeRegistry reg = newRegistry();
    Container cont = newContainer(Container.name("acme.ns"));
    AttributeFactory attrs = newAttributeFactory(cont, reg, reg);
    Activation vars =
        newActivation(mapOf("acme.a", mapOf("b", mapOf(4L, mapOf(false, "success")))));

    // acme.a.b[4][false]
    NamespacedAttribute attr = attrs.absoluteAttribute(1, "acme.a");
    Qualifier qualB = attrs.newQualifier(null, 2, "b");
    Qualifier qual4 = attrs.newQualifier(null, 3, 4L);
    Qualifier qualFalse = attrs.newQualifier(null, 4, false);
    attr.addQualifier(qualB);
    attr.addQualifier(qual4);
    attr.addQualifier(qualFalse);
    Object out = attr.resolve(vars);
    assertThat(out).isEqualTo("success");
    assertThat(estimateCost(attr)).extracting("min", "max").containsExactly(1L, 1L);
  }

  @Test
  void attributesAbsoluteAttr_Type() {
    TypeRegistry reg = newRegistry();
    AttributeFactory attrs = newAttributeFactory(Container.defaultContainer, reg, reg);

    // int
    NamespacedAttribute attr = attrs.absoluteAttribute(1, "int");
    Object out = attr.resolve(emptyActivation());
    assertThat(out).isSameAs(IntType);
    assertThat(estimateCost(attr)).extracting("min", "max").containsExactly(1L, 1L);
  }

  @Test
  void attributesRelativeAttr() {
    TypeRegistry reg = newRegistry();
    AttributeFactory attrs = newAttributeFactory(Container.defaultContainer, reg, reg);
    Map<Object, Object> data = mapOf("a", mapOf(-1, new int[] {2, 42}), "b", 1);
    Activation vars = newActivation(data);

    // The relative attribute under test is applied to a map literal:
    // {
    //   a: {-1: [2, 42], b: 1}
    //   b: 1
    // }
    //
    // The expression being evaluated is: <map-literal>.a[-1][b] -> 42
    InterpretableConst op = newConstValue(1, reg.nativeToValue(data));
    Attribute attr = attrs.relativeAttribute(1, op);
    Qualifier qualA = attrs.newQualifier(null, 2, "a");
    Qualifier qualNeg1 = attrs.newQualifier(null, 3, intOf(-1));
    attr.addQualifier(qualA);
    attr.addQualifier(qualNeg1);
    attr.addQualifier(attrs.absoluteAttribute(4, "b"));
    Object out = attr.resolve(vars);
    assertThat(out).isEqualTo(intOf(42));
    assertThat(estimateCost(attr)).extracting("min", "max").containsExactly(1L, 1L);
  }

  @Test
  void attributesRelativeAttr_OneOf() {
    TypeRegistry reg = newRegistry();
    Container cont = newContainer(Container.name("acme.ns"));
    AttributeFactory attrs = newAttributeFactory(cont, reg, reg);
    Map<Object, Object> data = mapOf("a", mapOf(-1, new int[] {2, 42}), "acme.b", 1);
    Activation vars = newActivation(data);

    // The relative attribute under test is applied to a map literal:
    // {
    //   a: {-1: [2, 42], b: 1}
    //   b: 1
    // }
    //
    // The expression being evaluated is: <map-literal>.a[-1][b] -> 42
    //
    // However, since the test is validating what happens with maybe attributes
    // the attribute resolution must also consider the following variations:
    // - <map-literal>.a[-1][acme.ns.b]
    // - <map-literal>.a[-1][acme.b]
    //
    // The correct behavior should yield the value of the last alternative.
    InterpretableConst op = newConstValue(1, reg.nativeToValue(data));
    Attribute attr = attrs.relativeAttribute(1, op);
    Qualifier qualA = attrs.newQualifier(null, 2, "a");
    Qualifier qualNeg1 = attrs.newQualifier(null, 3, intOf(-1));
    attr.addQualifier(qualA);
    attr.addQualifier(qualNeg1);
    attr.addQualifier(attrs.maybeAttribute(4, "b"));
    Object out = attr.resolve(vars);
    assertThat(out).isEqualTo(intOf(42));
    assertThat(estimateCost(attr)).extracting("min", "max").containsExactly(1L, 1L);
  }

  @Test
  void attributesRelativeAttr_Conditional() {
    TypeRegistry reg = newRegistry();
    AttributeFactory attrs = newAttributeFactory(Container.defaultContainer, reg, reg);
    Map<Object, Object> data =
        mapOf(
            "a", mapOf(-1, new int[] {2, 42}),
            "b", new int[] {0, 1},
            "c", new Object[] {1, 0});
    Activation vars = newActivation(data);

    // The relative attribute under test is applied to a map literal:
    // {
    //   a: {-1: [2, 42], b: 1}
    //   b: [0, 1],
    //   c: {1, 0},
    // }
    //
    // The expression being evaluated is:
    // <map-literal>.a[-1][(false ? b : c)[0]] -> 42
    //
    // Effectively the same as saying <map-literal>.a[-1][c[0]]
    InterpretableConst cond = newConstValue(2, False);
    Attribute condAttr =
        attrs.conditionalAttribute(
            4, cond, attrs.absoluteAttribute(5, "b"), attrs.absoluteAttribute(6, "c"));
    Qualifier qual0 = attrs.newQualifier(null, 7, 0);
    condAttr.addQualifier(qual0);

    InterpretableConst obj = newConstValue(1, reg.nativeToValue(data));
    Attribute attr = attrs.relativeAttribute(1, obj);
    Qualifier qualA = attrs.newQualifier(null, 2, "a");
    Qualifier qualNeg1 = attrs.newQualifier(null, 3, intOf(-1));
    attr.addQualifier(qualA);
    attr.addQualifier(qualNeg1);
    attr.addQualifier(condAttr);
    Object out = attr.resolve(vars);
    assertThat(out).isEqualTo(intOf(42));
    assertThat(estimateCost(attr)).extracting("min", "max").containsExactly(1L, 1L);
  }

  @Test
  void attributesRelativeAttr_Relative() {
    Container cont = newContainer(Container.name("acme.ns"));
    TypeRegistry reg = newRegistry();
    AttributeFactory attrs = newAttributeFactory(cont, reg, reg);
    Map<Object, Object> data =
        mapOf(
            "a",
            mapOf(
                -1,
                mapOf(
                    "first", 1,
                    "second", 2,
                    "third", 3)),
            "b",
            2L);
    Activation vars = newActivation(data);

    // The environment declares the following variables:
    // {
    //   a: {
    //     -1: {
    //       "first": 1u,
    //       "second": 2u,
    //       "third": 3u,
    //     }
    //   },
    //   b: 2u,
    // }
    //
    // The map of input variables is also re-used as a map-literal <obj> in the expression.
    //
    // The relative object under test is the following map literal.
    // <mp> {
    //   1u: "first",
    //   2u: "second",
    //   3u: "third",
    // }
    //
    // The expression under test is:
    //   <obj>.a[-1][<mp>[b]]
    //
    // This is equivalent to:
    //   <obj>.a[-1]["second"] -> 2u
    InterpretableConst obj = newConstValue(1, reg.nativeToValue(data));
    InterpretableConst mp =
        newConstValue(
            1,
            reg.nativeToValue(
                mapOf(
                    1, "first",
                    2, "second",
                    3, "third")));
    Attribute relAttr = attrs.relativeAttribute(4, mp);
    Qualifier qualB = attrs.newQualifier(null, 5, attrs.absoluteAttribute(5, "b"));
    relAttr.addQualifier(qualB);
    Attribute attr = attrs.relativeAttribute(1, obj);
    Qualifier qualA = attrs.newQualifier(null, 2, "a");
    Qualifier qualNeg1 = attrs.newQualifier(null, 3, intOf(-1));
    attr.addQualifier(qualA);
    attr.addQualifier(qualNeg1);
    attr.addQualifier(relAttr);

    Object out = attr.resolve(vars);
    assertThat(out).isEqualTo(intOf(2));
    assertThat(estimateCost(attr)).extracting("min", "max").containsExactly(1L, 1L);
  }

  @Test
  void attributesOneofAttr() {
    TypeRegistry reg = newRegistry();
    Container cont = newContainer(Container.name("acme.ns"));
    AttributeFactory attrs = newAttributeFactory(cont, reg, reg);
    Map<Object, Object> data =
        mapOf("a", mapOf("b", new int[] {2, 42}), "acme.a.b", 1, "acme.ns.a.b", "found");
    Activation vars = newActivation(data);

    // a.b -> should resolve to acme.ns.a.b per namespace resolution rules.
    Attribute attr = attrs.maybeAttribute(1, "a");
    Qualifier qualB = attrs.newQualifier(null, 2, "b");
    attr.addQualifier(qualB);
    Object out = attr.resolve(vars);
    assertThat(out).isEqualTo("found");
    assertThat(estimateCost(attr)).extracting("min", "max").containsExactly(1L, 1L);
  }

  @Test
  void attributesConditionalAttr_TrueBranch() {
    TypeRegistry reg = newRegistry();
    AttributeFactory attrs = newAttributeFactory(Container.defaultContainer, reg, reg);
    Map<Object, Object> data =
        mapOf(
            "a", mapOf(-1, new int[] {2, 42}),
            "b", mapOf("c", mapOf(-1, new int[] {2, 42})));
    Activation vars = newActivation(data);

    // (true ? a : b.c)[-1][1]
    NamespacedAttribute tv = attrs.absoluteAttribute(2, "a");
    Attribute fv = attrs.maybeAttribute(3, "b");
    Qualifier qualC = attrs.newQualifier(null, 4, "c");
    fv.addQualifier(qualC);
    Attribute cond = attrs.conditionalAttribute(1, newConstValue(0, True), tv, fv);
    Qualifier qualNeg1 = attrs.newQualifier(null, 5, intOf(-1));
    Qualifier qual1 = attrs.newQualifier(null, 6, intOf(1));
    cond.addQualifier(qualNeg1);
    cond.addQualifier(qual1);
    Object out = cond.resolve(vars);
    assertThat(out).isEqualTo(42);
    assertThat(estimateCost(fv)).extracting("min", "max").containsExactly(1L, 1L);
    // Note: migrated to JMH
  }

  @Test
  void attributesConditionalAttr_FalseBranch() {
    TypeRegistry reg = newRegistry();
    AttributeFactory attrs = newAttributeFactory(Container.defaultContainer, reg, reg);
    Map<Object, Object> data =
        mapOf(
            "a", mapOf(-1, new int[] {2, 42}),
            "b", mapOf("c", mapOf(-1, new int[] {2, 42})));
    Activation vars = newActivation(data);

    // (false ? a : b.c)[-1][1]
    NamespacedAttribute tv = attrs.absoluteAttribute(2, "a");
    Attribute fv = attrs.maybeAttribute(3, "b");
    Qualifier qualC = attrs.newQualifier(null, 4, "c");
    fv.addQualifier(qualC);
    Attribute cond = attrs.conditionalAttribute(1, newConstValue(0, False), tv, fv);
    Qualifier qualNeg1 = attrs.newQualifier(null, 5, intOf(-1));
    Qualifier qual1 = attrs.newQualifier(null, 6, intOf(1));
    cond.addQualifier(qualNeg1);
    cond.addQualifier(qual1);
    Object out = cond.resolve(vars);
    assertThat(out).isEqualTo(42);
    assertThat(estimateCost(fv)).extracting("min", "max").containsExactly(1L, 1L);
    // Note: migrated to JMH
  }

  @Test
  void attributesConditionalAttr_ErrorUnknown() {
    TypeRegistry reg = newRegistry();
    AttributeFactory attrs = newAttributeFactory(Container.defaultContainer, reg, reg);

    // err ? a : b
    NamespacedAttribute tv = attrs.absoluteAttribute(2, "a");
    Attribute fv = attrs.maybeAttribute(3, "b");
    Attribute cond = attrs.conditionalAttribute(1, newConstValue(0, newErr("test error")), tv, fv);
    Object out = cond.resolve(emptyActivation());
    assertThat(estimateCost(fv)).extracting("min", "max").containsExactly(1L, 1L);

    // unk ? a : b
    Attribute condUnk = attrs.conditionalAttribute(1, newConstValue(0, unknownOf(1)), tv, fv);
    out = condUnk.resolve(emptyActivation());
    assertThat(out).isInstanceOf(UnknownT.class);
    assertThat(estimateCost(fv)).extracting("min", "max").containsExactly(1L, 1L);
  }

  @Test
  void benchmarkResolverFieldQualifier() {
    TestAllTypes msg =
        TestAllTypes.newBuilder()
            .setSingleNestedMessage(NestedMessage.newBuilder().setBb(123))
            .build();
    TypeRegistry reg = newRegistry(msg);
    AttributeFactory attrs = newAttributeFactory(Container.defaultContainer, reg, reg);
    Activation vars = newActivation(mapOf("msg", msg));
    NamespacedAttribute attr = attrs.absoluteAttribute(1, "msg");
    Type opType = reg.findType("google.api.expr.test.v1.proto3.TestAllTypes");
    assertThat(opType).isNotNull();
    Type fieldType = reg.findType("google.api.expr.test.v1.proto3.TestAllTypes.NestedMessage");
    assertThat(fieldType).isNotNull();
    attr.addQualifier(makeQualifier(attrs, opType.getType(), 2, "single_nested_message"));
    attr.addQualifier(makeQualifier(attrs, fieldType.getType(), 3, "bb"));
    // Note: migrated to JMH
  }

  @Test
  void resolverCustomQualifier() {
    TypeRegistry reg = newRegistry();
    AttributeFactory attrs =
        new CustAttrFactory(newAttributeFactory(Container.defaultContainer, reg, reg));
    NestedMessage msg = NestedMessage.newBuilder().setBb(123).build();
    Activation vars = newActivation(mapOf("msg", msg));
    NamespacedAttribute attr = attrs.absoluteAttribute(1, "msg");
    Qualifier qualBB =
        attrs.newQualifier(
            Type.newBuilder()
                .setMessageType("google.api.expr.test.v1.proto3.TestAllTypes.NestedMessage")
                .build(),
            2,
            "bb");
    attr.addQualifier(qualBB);
    Object out = attr.resolve(vars);
    assertThat(out).isEqualTo(123);
    assertThat(estimateCost(attr)).extracting("min", "max").containsExactly(1L, 1L);
  }

  @Test
  void attributesMissingMsg() {
    TypeRegistry reg = newRegistry();
    AttributeFactory attrs = newAttributeFactory(Container.defaultContainer, reg, reg);
    Any any = Any.pack(TestAllTypes.getDefaultInstance());
    Activation vars = newActivation(mapOf("missing_msg", any));

    // missing_msg.field
    NamespacedAttribute attr = attrs.absoluteAttribute(1, "missing_msg");
    Qualifier field = attrs.newQualifier(null, 2, "field");
    attr.addQualifier(field);
    assertThatThrownBy(() -> attr.resolve(vars))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("unknown type 'google.api.expr.test.v1.proto3.TestAllTypes'");
  }

  @Test
  void attributeMissingMsg_UnknownField() {
    TypeRegistry reg = newRegistry();
    AttributeFactory attrs = newPartialAttributeFactory(Container.defaultContainer, reg, reg);
    Any any = Any.pack(TestAllTypes.getDefaultInstance());
    Activation vars =
        newPartialActivation(
            mapOf("missing_msg", any), newAttributePattern("missing_msg").qualString("field"));

    // missing_msg.field
    NamespacedAttribute attr = attrs.absoluteAttribute(1, "missing_msg");
    Qualifier field = attrs.newQualifier(null, 2, "field");
    attr.addQualifier(field);
    Object out = attr.resolve(vars);
    assertThat(out).isInstanceOf(UnknownT.class);
  }

  static class TestDef {
    final String expr;
    List<Decl> env;
    Map<Object, Object> in;
    Val out;
    Map<Object, Object> state;

    TestDef(String expr) {
      this.expr = expr;
    }

    TestDef env(Decl... env) {
      this.env = Arrays.asList(env);
      return this;
    }

    TestDef in(Map<Object, Object> in) {
      this.in = in;
      return this;
    }

    TestDef out(Val out) {
      this.out = out;
      return this;
    }

    TestDef state(Map<Object, Object> state) {
      this.state = state;
      return this;
    }

    @Override
    public String toString() {
      return expr;
    }
  }

  @SuppressWarnings("unused")
  static TestDef[] attributeStateTrackingTests() {
    return new TestDef[] {
      new TestDef("[{\"field\": true}][0].field")
          .env()
          .in(mapOf())
          .out(True)
          .state(
              mapOf(
                  // overall expression
                  1L, true,
                  // [{"field": true}][0]
                  6L, mapOf(stringOf("field"), True),
                  // [{"field": true}][0].field
                  8L, true)),
      new TestDef("a[1]['two']")
          .env(
              Decls.newVar(
                  "a", Decls.newMapType(Decls.Int, Decls.newMapType(Decls.String, Decls.Bool))))
          .in(mapOf("a", mapOf(1L, mapOf("two", true))))
          .out(True)
          .state(
              mapOf(
                  // overall expression
                  1L, true,
                  // a[1]
                  2L, mapOf("two", true),
                  // a[1]["two"]
                  4L, true)),
      new TestDef("a[1][2][3]")
          .env(
              Decls.newVar(
                  "a", Decls.newMapType(Decls.Int, Decls.newMapType(Decls.Dyn, Decls.Dyn))))
          .in(
              mapOf(
                  "a",
                  mapOf(1, mapOf(1L, 0L, 2L, new String[] {"index", "middex", "outdex", "dex"}))))
          .out(stringOf("dex"))
          .state(
              mapOf(
                  // overall expression
                  1L,
                  "dex",
                  // a[1]
                  2L,
                  mapOf(1L, 0L, 2L, new String[] {"index", "middex", "outdex", "dex"}),
                  // a[1][2]
                  4L,
                  new String[] {"index", "middex", "outdex", "dex"},
                  // a[1][2][3]
                  6L,
                  "dex")),
      new TestDef("a[1][2][a[1][1]]")
          .env(
              Decls.newVar(
                  "a", Decls.newMapType(Decls.Int, Decls.newMapType(Decls.Dyn, Decls.Dyn))))
          .in(
              mapOf(
                  "a",
                  mapOf(1L, mapOf(1L, 0L, 2L, new String[] {"index", "middex", "outdex", "dex"}))))
          .out(stringOf("index"))
          .state(
              mapOf(
                  // overall expression
                  1L,
                  "index",
                  // a[1]
                  2L,
                  mapOf(1L, 0L, 2L, new String[] {"index", "middex", "outdex", "dex"}),
                  // a[1][2]
                  4L,
                  new String[] {"index", "middex", "outdex", "dex"},
                  // a[1][2][a[1][1]]
                  6L,
                  "index",
                  // dynamic index into a[1][2]
                  // a[1]
                  8L,
                  mapOf(1L, 0L, 2L, new String[] {"index", "middex", "outdex", "dex"}),
                  // a[1][1]
                  10L,
                  intOf(0)))
    };
  }

  @ParameterizedTest
  @MethodSource("attributeStateTrackingTests")
  void attributeStateTracking(TestDef tc) {
    Source src = Source.newTextSource(tc.expr);
    ParseResult parsed = Parser.parseAllMacros(src);
    assertThat(parsed.hasErrors()).isFalse();
    Container cont = Container.defaultContainer;
    TypeRegistry reg = newRegistry();
    CheckerEnv env = newStandardCheckerEnv(cont, reg);
    if (tc.env != null) {
      env.add(tc.env);
    }
    CheckResult checkResult = Checker.Check(parsed, src, env);
    if (parsed.hasErrors()) {
      throw new IllegalArgumentException(parsed.getErrors().toDisplayString());
    }
    AttributeFactory attrs = newAttributeFactory(cont, reg, reg);
    Interpreter interp = newStandardInterpreter(cont, reg, reg, attrs);
    // Show that program planning will now produce an error.
    EvalState st = newEvalState();
    Interpretable i =
        interp.newInterpretable(checkResult.getCheckedExpr(), optimize(), trackState(st));
    Activation in = newActivation(tc.in);
    Val out = i.eval(in);
    assertThat(out).extracting(o -> o.equal(tc.out)).isSameAs(True);
    for (Entry<Object, Object> iv : tc.state.entrySet()) {
      long id = ((Number) iv.getKey()).longValue();
      Object val = iv.getValue();
      Val stVal = st.value(id);
      assertThat(stVal)
          .withFailMessage(() -> String.format("id(%d), val=%s, stVal=%s", id, val, stVal))
          .isNotNull();
      assertThat(stVal)
          .withFailMessage(() -> String.format("id(%d), val=%s, stVal=%s", id, val, stVal))
          .isEqualTo(DefaultTypeAdapter.Instance.nativeToValue(val));
      deepEquals(String.format("id(%d)", id), stVal.value(), val);
    }
  }

  @Test
  void benchmarkResolverCustomQualifier() {
    TypeRegistry reg = newRegistry();
    AttributeFactory attrs =
        new CustAttrFactory(newAttributeFactory(Container.defaultContainer, reg, reg));
    NestedMessage msg = NestedMessage.newBuilder().setBb(123).build();
    Activation vars = newActivation(mapOf("msg", msg));
    NamespacedAttribute attr = attrs.absoluteAttribute(1, "msg");
    Qualifier qualBB =
        attrs.newQualifier(
            Type.newBuilder()
                .setMessageType("google.api.expr.test.v1.proto3.TestAllTypes.NestedMessage")
                .build(),
            2,
            "bb");
    attr.addQualifier(qualBB);
    // Note: Migrated to JMH
  }

  static class CustAttrFactory implements AttributeFactory {

    private final AttributeFactory af;

    public CustAttrFactory(AttributeFactory af) {
      this.af = af;
    }

    @Override
    public Qualifier newQualifier(Type objType, long qualID, Object val) {
      if (objType
          .getMessageType()
          .equals("google.api.expr.test.v1.proto3.TestAllTypes.NestedMessage")) {
        return new NestedMsgQualifier(qualID, (String) val);
      }
      return af.newQualifier(objType, qualID, val);
    }

    @Override
    public NamespacedAttribute absoluteAttribute(long id, String... names) {
      return af.absoluteAttribute(id, names);
    }

    @Override
    public Attribute conditionalAttribute(long id, Interpretable expr, Attribute t, Attribute f) {
      return af.conditionalAttribute(id, expr, t, f);
    }

    @Override
    public Attribute maybeAttribute(long id, String name) {
      return af.maybeAttribute(id, name);
    }

    @Override
    public Attribute relativeAttribute(long id, Interpretable operand) {
      return af.relativeAttribute(id, operand);
    }
  }

  static class NestedMsgQualifier implements Coster, Qualifier {
    private final long id;
    private final String field;

    NestedMsgQualifier(long id, String field) {
      this.id = id;
      this.field = field;
    }

    @Override
    public long id() {
      return id;
    }

    @Override
    public Object qualify(Activation vars, Object obj) {
      return ((NestedMessage) obj).getBb();
    }

    /** Cost implements the Coster interface method. It returns zero for testing purposes. */
    @Override
    public Cost cost() {
      return Cost.None;
    }
  }

  static Qualifier makeQualifier(AttributeFactory attrs, Type typ, long qualID, Object val) {
    Qualifier qual = attrs.newQualifier(typ, qualID, val);
    return qual;
  }
}
