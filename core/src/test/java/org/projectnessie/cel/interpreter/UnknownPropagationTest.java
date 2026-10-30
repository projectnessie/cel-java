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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Interpretable.newConstValue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.UnknownT;
import org.projectnessie.cel.common.types.ref.Val;

class UnknownPropagationTest {

  @Test
  void sharedLogicalCombinationPreservesPrecedenceAndMergesUnknowns() {
    UnknownT first = unknownOf(1L);
    UnknownT second = unknownOf(2L);
    Val error = newErr("failure");

    for (boolean and : List.of(false, true)) {
      Val absorbing = and ? False : True;
      Val identity = and ? True : False;

      assertThat(LogicalValueSupport.combine(first, second, and)).isEqualTo(unknownOf(1L, 2L));
      assertThat(LogicalValueSupport.combine(second, first, and)).isEqualTo(unknownOf(1L, 2L));
      assertThat(LogicalValueSupport.combine(first, error, and)).isSameAs(first);
      assertThat(LogicalValueSupport.combine(error, first, and)).isSameAs(first);
      assertThat(LogicalValueSupport.combine(first, absorbing, and)).isSameAs(absorbing);
      assertThat(LogicalValueSupport.combine(absorbing, first, and)).isSameAs(absorbing);
      assertThat(LogicalValueSupport.combine(identity, identity, and)).isSameAs(identity);
      assertThat(LogicalValueSupport.combine(identity, first, and)).isSameAs(first);
      assertThat(LogicalValueSupport.combine(first, identity, and)).isSameAs(first);
    }
    assertThat(LogicalValueSupport.combine(unknownOf(1L, 2L), unknownOf(2L, 3L), true))
        .isEqualTo(unknownOf(1L, 2L, 3L));
    assertThat(LogicalValueSupport.combine(unknownOf(1L, 2L), unknownOf(1L, 2L), false))
        .isEqualTo(unknownOf(1L, 2L));
  }

  @Test
  void sharedLogicalCombinationRetainsErrorAndInvalidOperandControls() {
    Val firstError = newErr("first");
    Val secondError = newErr("second");
    Val invalid = stringOf("invalid");

    for (boolean and : List.of(false, true)) {
      Val absorbing = and ? False : True;
      Val identity = and ? True : False;

      assertThat(LogicalValueSupport.combine(firstError, secondError, and)).isSameAs(firstError);
      assertThat(LogicalValueSupport.combine(firstError, identity, and)).isSameAs(firstError);
      assertThat(LogicalValueSupport.combine(firstError, absorbing, and)).isSameAs(absorbing);
      assertThat(LogicalValueSupport.combine(invalid, absorbing, and)).isSameAs(absorbing);
      assertThat(LogicalValueSupport.combine(absorbing, invalid, and)).isSameAs(absorbing);
      assertThat(LogicalValueSupport.combine(invalid, identity, and)).matches(val -> isError(val));
      assertThat(LogicalValueSupport.combine(invalid, invalid, and)).matches(val -> isError(val));
    }
  }

  @Test
  void establishedAndExhaustiveNodesMergeBothUnknownOperands() {
    Interpretable first = newConstValue(1L, unknownOf(1L));
    Interpretable second = newConstValue(2L, unknownOf(2L));

    assertThat(new EvalAnd(3L, first, second).eval(emptyActivation())).isEqualTo(unknownOf(1L, 2L));
    assertThat(new EvalOr(3L, first, second).eval(emptyActivation())).isEqualTo(unknownOf(1L, 2L));
    assertThat(new EvalExhaustiveAnd(3L, first, second).eval(emptyActivation()))
        .isEqualTo(unknownOf(1L, 2L));
    assertThat(new EvalExhaustiveOr(3L, first, second).eval(emptyActivation()))
        .isEqualTo(unknownOf(1L, 2L));
  }

  @Test
  void nativeLogicalContinuationMergesBothUnknownOperands() {
    NativeBooleanCapability first = signalingBoolean(1L, unknownOf(1L));
    NativeBooleanCapability second = signalingBoolean(2L, unknownOf(2L));

    assertThatThrownBy(() -> NativeLogical.evaluate(first, second, emptyActivation(), true))
        .isInstanceOfSatisfying(
            ValueSignal.class, signal -> assertThat(signal.value).isEqualTo(unknownOf(1L, 2L)));
    assertThatThrownBy(() -> NativeLogical.evaluate(first, second, emptyActivation(), false))
        .isInstanceOfSatisfying(
            ValueSignal.class, signal -> assertThat(signal.value).isEqualTo(unknownOf(1L, 2L)));
  }

  @Test
  void nativeLoopPendingResultMergesUnknownPredicateResults() {
    for (NativeQuantifier quantifier : NativeQuantifier.values()) {
      NativeLoopBinding binding = new NativeLoopBinding(emptyActivation(), "item");
      binding.record(unknownOf(1L), quantifier);
      binding.record(unknownOf(2L), quantifier);

      assertThatThrownBy(() -> binding.finish(quantifier))
          .isInstanceOfSatisfying(
              ValueSignal.class, signal -> assertThat(signal.value).isEqualTo(unknownOf(1L, 2L)));
    }
  }

  @Test
  void establishedShortCircuitAndExhaustiveEvaluationCountsRemainDistinct() {
    AtomicInteger standardRightCalls = new AtomicInteger();
    Interpretable standardRight = countingValue(2L, unknownOf(2L), standardRightCalls);
    assertThat(new EvalAnd(3L, newConstValue(1L, False), standardRight).eval(emptyActivation()))
        .isSameAs(False);
    assertThat(new EvalOr(3L, newConstValue(1L, True), standardRight).eval(emptyActivation()))
        .isSameAs(True);
    assertThat(standardRightCalls).hasValue(0);

    AtomicInteger exhaustiveRightCalls = new AtomicInteger();
    Interpretable exhaustiveRight = countingValue(2L, unknownOf(2L), exhaustiveRightCalls);
    assertThat(
            new EvalExhaustiveAnd(3L, newConstValue(1L, False), exhaustiveRight)
                .eval(emptyActivation()))
        .isSameAs(False);
    assertThat(
            new EvalExhaustiveOr(3L, newConstValue(1L, True), exhaustiveRight)
                .eval(emptyActivation()))
        .isSameAs(True);
    assertThat(exhaustiveRightCalls).hasValue(2);
  }

  private static NativeBooleanCapability signalingBoolean(long id, Val value) {
    return new NativeBooleanCapability() {
      @Override
      public boolean evalBoolean(Activation activation) {
        throw ValueSignal.signal(value);
      }

      @Override
      public Val eval(Activation activation) {
        return value;
      }

      @Override
      public long id() {
        return id;
      }
    };
  }

  private static Interpretable countingValue(long id, Val value, AtomicInteger calls) {
    return new Interpretable() {
      @Override
      public Val eval(Activation activation) {
        calls.incrementAndGet();
        return value;
      }

      @Override
      public long id() {
        return id;
      }
    };
  }
}
