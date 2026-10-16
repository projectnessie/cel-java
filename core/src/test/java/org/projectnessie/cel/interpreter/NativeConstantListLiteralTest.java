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

import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.ListT.newValArrayList;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.interpreter.Coster.Cost;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableConst;

class NativeConstantListLiteralTest {

  @Test
  void leavesExposeOnlyTheirTruthfulCapabilityAndRetainExactList() {
    Lister booleans = list(True);
    Lister ints = list(intOf(42));
    Lister uints = list(uintOf(-1L));
    Lister doubles = list(doubleOf(42.5));
    Lister strings = list(stringOf("value"));

    NativeConstantBooleanListLiteral booleanNode =
        new NativeConstantBooleanListLiteral(1, booleans);
    NativeConstantIntListLiteral intNode = new NativeConstantIntListLiteral(2, ints);
    NativeConstantUintListLiteral uintNode = new NativeConstantUintListLiteral(3, uints);
    NativeConstantDoubleListLiteral doubleNode = new NativeConstantDoubleListLiteral(4, doubles);
    NativeConstantStringListLiteral stringNode = new NativeConstantStringListLiteral(5, strings);

    assertConstantNode(booleanNode, booleans, NativeBooleanListLiteralCapability.class);
    assertConstantNode(intNode, ints, NativeIntListLiteralCapability.class);
    assertConstantNode(uintNode, uints, NativeUintListLiteralCapability.class);
    assertConstantNode(doubleNode, doubles, NativeDoubleListLiteralCapability.class);
    assertConstantNode(stringNode, strings, NativeStringListLiteralCapability.class);

    assertThat(booleanNode.evalBooleanAt(emptyActivation(), 0)).isTrue();
    assertThat(intNode.evalIntAt(emptyActivation(), 0)).isEqualTo(42L);
    assertThat(uintNode.evalUintAt(emptyActivation(), 0)).isEqualTo(-1L);
    assertThat(doubleNode.evalDoubleAt(emptyActivation(), 0)).isEqualTo(42.5d);
    assertThat(stringNode.evalStringAt(emptyActivation(), 0)).isEqualTo("value");

    assertThat(stream(NativeConstantScalarListLiteral.class.getDeclaredFields()))
        .noneMatch(field -> field.getType().isArray());
  }

  @Test
  void typedIndexPropagatesTheExactInvalidIndexValue() {
    Lister values = list(intOf(1));
    NativeConstantIntListLiteral node = new NativeConstantIntListLiteral(1, values);
    Val expected = values.nativeGetAt(-1);

    try {
      node.evalIntAt(emptyActivation(), -1);
      fail("expected invalid index signal");
    } catch (ValueSignal valueSignal) {
      assertThat(valueSignal.value.toString()).isEqualTo(expected.toString());
    }
  }

  @Test
  void nullBackedStringIndexCarriesTheExactCompatibilityValue() {
    Val nullBacked = stringOf(null);
    NativeConstantStringListLiteral node = new NativeConstantStringListLiteral(1, list(nullBacked));

    try {
      node.evalStringAt(emptyActivation(), 0);
      fail("expected null-backed string signal");
    } catch (ValueSignal valueSignal) {
      assertThat(valueSignal.value).isSameAs(nullBacked);
    }
  }

  @Test
  void stringMembershipEvaluatesNeedleOnce() {
    NativeConstantStringListLiteral node =
        new NativeConstantStringListLiteral(1, list(stringOf("needle")));
    AtomicInteger evaluations = new AtomicInteger();
    NativeStringCapability needle =
        new NativeStringCapability() {
          @Override
          public String evalString(Activation activation) {
            evaluations.incrementAndGet();
            return "needle";
          }

          @Override
          public long id() {
            return 2;
          }

          @Override
          public Val eval(Activation activation) {
            return stringOf("needle");
          }
        };

    assertThat(node.evalContains(emptyActivation(), needle)).isTrue();
    assertThat(evaluations).hasValue(1);
  }

  private static void assertConstantNode(
      NativeConstantScalarListLiteral node,
      Lister expected,
      Class<? extends NativeScalarListLiteralCapability> expectedCapability) {
    assertThat(node).isInstanceOf(InterpretableConst.class);
    assertThat(node).isInstanceOf(NativeScalarListLiteralCapability.class);
    assertThat(node.eval(emptyActivation())).isSameAs(expected);
    assertThat(node.eval(emptyActivation())).isSameAs(node.value());
    assertThat(node.evalSize(emptyActivation())).isEqualTo(1);
    assertThat(estimateCost(node)).isEqualTo(Cost.None);
    assertThat(
            Stream.of(
                    NativeBooleanListLiteralCapability.class,
                    NativeIntListLiteralCapability.class,
                    NativeUintListLiteralCapability.class,
                    NativeDoubleListLiteralCapability.class,
                    NativeStringListLiteralCapability.class)
                .filter(capability -> capability.isInstance(node)))
        .containsExactly(expectedCapability);
  }

  private static Lister list(Val... values) {
    return (Lister) newValArrayList(DefaultTypeAdapter.Instance, values);
  }
}
