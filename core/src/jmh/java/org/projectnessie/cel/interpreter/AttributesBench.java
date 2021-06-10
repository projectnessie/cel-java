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
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.ProtoTypeRegistry.newRegistry;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.Interpretable.newConstValue;

import com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes;
import com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes.NestedMessage;
import com.google.api.expr.v1alpha1.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.AttributeFactory.NamespacedAttribute;
import org.projectnessie.cel.interpreter.AttributeFactory.Qualifier;
import org.projectnessie.cel.interpreter.AttributesTest.CustAttrFactory;

@Warmup(iterations = 1, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class AttributesBench {

  @Benchmark
  public void attributesConditionalAttr_TrueBranch() {
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
  }

  @Benchmark
  public void attributesConditionalAttr_FalseBranch() {
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
  }

  @State(Scope.Benchmark)
  public static class BenchmarkResolverFieldQualifierState {

    final NamespacedAttribute attr;
    final Activation vars;

    public BenchmarkResolverFieldQualifierState() {
      TestAllTypes msg =
          TestAllTypes.newBuilder()
              .setSingleNestedMessage(NestedMessage.newBuilder().setBb(123))
              .build();
      TypeRegistry reg = newRegistry(msg);
      Activation vars = newActivation(mapOf("msg", msg));

      Type opType = reg.findType("google.api.expr.test.v1.proto3.TestAllTypes");
      assertThat(opType).isNotNull();
      Type fieldType = reg.findType("google.api.expr.test.v1.proto3.TestAllTypes.NestedMessage");
      assertThat(fieldType).isNotNull();

      AttributeFactory attrs = newAttributeFactory(Container.defaultContainer, reg, reg);
      NamespacedAttribute attr = attrs.absoluteAttribute(1, "msg");
      attr.addQualifier(makeQualifier(attrs, opType.getType(), 2, "single_nested_message"));
      attr.addQualifier(makeQualifier(attrs, fieldType.getType(), 3, "bb"));

      this.attr = attr;
      this.vars = vars;
    }
  }

  @Benchmark
  public void benchmarkResolverFieldQualifier(BenchmarkResolverFieldQualifierState state) {
    state.attr.resolve(state.vars);
  }

  @State(Scope.Benchmark)
  public static class BenchmarkResolverCustomQualifierState {

    final NamespacedAttribute attr;
    final Activation vars;

    public BenchmarkResolverCustomQualifierState() {
      TypeRegistry reg = newRegistry();
      NestedMessage msg = NestedMessage.newBuilder().setBb(123).build();
      Activation vars = newActivation(mapOf("msg", msg));

      AttributeFactory attrs =
          new CustAttrFactory(newAttributeFactory(Container.defaultContainer, reg, reg));
      NamespacedAttribute attr = attrs.absoluteAttribute(1, "msg");
      Qualifier qualBB =
          attrs.newQualifier(
              Type.newBuilder()
                  .setMessageType("google.api.expr.test.v1.proto3.TestAllTypes.NestedMessage")
                  .build(),
              2,
              "bb");

      attr.addQualifier(qualBB);

      this.attr = attr;
      this.vars = vars;
    }
  }

  @Benchmark
  public void benchmarkResolverCustomQualifier(BenchmarkResolverCustomQualifierState state) {
    state.attr.resolve(state.vars);
  }

  static Qualifier makeQualifier(AttributeFactory attrs, Type typ, long qualID, Object val) {
    return attrs.newQualifier(typ, qualID, val);
  }

  private static Map<Object, Object> mapOf(Object... kvPairs) {
    Map<Object, Object> map = new HashMap<>();
    assertThat(kvPairs.length % 2).isEqualTo(0);
    for (int i = 0; i < kvPairs.length; i += 2) {
      map.put(kvPairs[i], kvPairs[i + 1]);
    }
    return map;
  }
}
