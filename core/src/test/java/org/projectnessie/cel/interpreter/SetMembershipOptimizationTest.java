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
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.ListT.newValArrayList;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Interpretable.newConstValue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableCall;

class SetMembershipOptimizationTest {

  @Test
  void emptySetStillEvaluatesLeftOperand() {
    AtomicInteger evaluations = new AtomicInteger();
    Interpretable lhs = countingValue(intOf(42), evaluations);
    Interpretable optimized = optimize(lhs, new Val[0]);

    assertThat(optimized.eval(emptyActivation())).isSameAs(False);
    assertThat(evaluations).hasValue(1);
  }

  @Test
  void optimizedMembershipPropagatesErrorAndUnknown() {
    Val error = newErr("left failed");
    Val unknown = unknownOf(42);

    assertThat(optimize(newConstValue(1, error), new Val[] {intOf(1)}).eval(emptyActivation()))
        .isSameAs(error);
    assertThat(optimize(newConstValue(1, unknown), new Val[] {intOf(1)}).eval(emptyActivation()))
        .isSameAs(unknown);
  }

  private static Interpretable optimize(Interpretable lhs, Val[] values) {
    Interpretable rhs = newConstValue(2, newValArrayList(DefaultTypeAdapter.Instance, values));
    return InterpretableDecorator.maybeOptimizeSetMembership(
        new MembershipCall(lhs, rhs), new MembershipCall(lhs, rhs));
  }

  private static Interpretable countingValue(Val value, AtomicInteger evaluations) {
    return new Interpretable() {
      @Override
      public long id() {
        return 1;
      }

      @Override
      public Val eval(Activation activation) {
        evaluations.incrementAndGet();
        return value;
      }
    };
  }

  private static final class MembershipCall implements InterpretableCall {
    private final Interpretable[] args;

    private MembershipCall(Interpretable lhs, Interpretable rhs) {
      this.args = new Interpretable[] {lhs, rhs};
    }

    @Override
    public long id() {
      return 3;
    }

    @Override
    public Val eval(Activation activation) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String function() {
      return "@in";
    }

    @Override
    public String overloadID() {
      return Overloads.InList;
    }

    @Override
    public Interpretable[] args() {
      return args;
    }
  }
}
