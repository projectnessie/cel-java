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
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Interpretable.newConstValue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Mapper;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableConst;

class BuiltInOptimizerTest {

  @Test
  void publicFactoryReturnsThePackagePrivateSingleton() {
    assertThat(Interpreter.optimize()).isSameAs(BuiltInOptimizer.INSTANCE);
    assertThat(InterpretableDecorator.decOptimize()).isSameAs(BuiltInOptimizer.INSTANCE);
  }

  @Test
  void dispatchFoldsListsMapsAndConversions() {
    Interpretable one = newConstValue(1, intOf(1));
    Interpretable two = newConstValue(2, intOf(2));

    Interpretable list =
        BuiltInOptimizer.INSTANCE.decorate(
            new EvalList(
                3, new Interpretable[] {one, two}, new boolean[2], DefaultTypeAdapter.Instance));
    assertThat(list).isInstanceOf(InterpretableConst.class);
    Lister listValue = (Lister) list.eval(emptyActivation());
    assertThat(listValue.nativeSize()).isEqualTo(2);
    assertThat(listValue.nativeGetAt(0)).isEqualTo(intOf(1));
    assertThat(listValue.nativeGetAt(1)).isEqualTo(intOf(2));

    Interpretable map =
        BuiltInOptimizer.INSTANCE.decorate(
            new EvalMap(
                4,
                new Interpretable[] {newConstValue(5, stringOf("key"))},
                new Interpretable[] {one},
                new boolean[1],
                DefaultTypeAdapter.Instance));
    assertThat(map).isInstanceOf(InterpretableConst.class);
    assertThat(((Mapper) map.eval(emptyActivation())).get(stringOf("key"))).isEqualTo(intOf(1));

    AtomicInteger evaluations = new AtomicInteger();
    Interpretable conversion =
        BuiltInOptimizer.INSTANCE.decorate(
            new EvalUnary(
                6,
                "int",
                Overloads.StringToInt,
                newConstValue(7, stringOf("ignored")),
                null,
                ignored -> {
                  evaluations.incrementAndGet();
                  return intOf(42);
                }));
    assertThat(conversion).isInstanceOf(InterpretableConst.class);
    assertThat(conversion.eval(emptyActivation())).isEqualTo(intOf(42));
    assertThat(conversion.eval(emptyActivation())).isEqualTo(intOf(42));
    assertThat(evaluations).hasValue(1);
  }

  @Test
  void nonConstantInputsKeepTheirOriginalNodes() {
    Interpretable dynamic =
        new AbstractEval(1) {
          @Override
          public Val eval(Activation activation) {
            return intOf(1);
          }
        };
    EvalList list =
        new EvalList(2, new Interpretable[] {dynamic}, new boolean[1], DefaultTypeAdapter.Instance);
    EvalMap map =
        new EvalMap(
            3,
            new Interpretable[] {newConstValue(4, stringOf("key"))},
            new Interpretable[] {dynamic},
            new boolean[1],
            DefaultTypeAdapter.Instance);

    assertThat(BuiltInOptimizer.INSTANCE.decorate(list)).isSameAs(list);
    assertThat(BuiltInOptimizer.INSTANCE.decorate(map)).isSameAs(map);
  }
}
