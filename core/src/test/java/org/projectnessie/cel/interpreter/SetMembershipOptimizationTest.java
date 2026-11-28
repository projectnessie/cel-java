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
import static org.projectnessie.cel.common.types.IteratorT.javaIterator;
import static org.projectnessie.cel.common.types.ListT.newValArrayList;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Interpretable.newConstValue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.projectnessie.cel.Ast;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.EvalOption;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.DoubleT;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.UintT;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
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

  @Test
  void inconsistentListSizeAndIteratorPreserveOriginalEvaluation() {
    Lister inconsistentList =
        (Lister)
            Proxy.newProxyInstance(
                Lister.class.getClassLoader(),
                new Class<?>[] {Lister.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("size")) {
                    return intOf(1);
                  }
                  if (method.getName().equals("iterator")) {
                    return javaIterator(DefaultTypeAdapter.Instance, List.of().iterator());
                  }
                  throw new UnsupportedOperationException(method.getName());
                });
    Interpretable lhs = newConstValue(1, intOf(1));
    Interpretable rhs = newConstValue(2, inconsistentList);
    MembershipCall original = new MembershipCall(lhs, rhs);

    assertThat(BuiltInOptimizer.maybeOptimizeSetMembership(original, original)).isSameAs(original);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "x in [1]",
        "x in [1u]",
        "x in [1.0]",
        "x in ['1']",
        "x in [double('NaN')]",
        "x in [0.0]",
        "x in []"
      })
  void optimizedMembershipMatchesListEquality(String expression) {
    Env env = Env.newEnv(EnvOption.declarations(Decls.newVar("x", Decls.Dyn)));
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).isFalse();
    Ast ast = compiled.getAst();
    var plain = env.program(ast, ProgramOption.evalOptions(EvalOption.OptDisableNativeEval));
    var optimized = env.program(ast, ProgramOption.evalOptions(EvalOption.OptOptimize));
    var establishedOptimized =
        env.program(
            ast,
            ProgramOption.evalOptions(EvalOption.OptOptimize, EvalOption.OptDisableNativeEval));
    for (Object value :
        List.of(1L, 1.0, UintT.uintOf(1), "1", -1L, 1.5, -0.0, 0.0, DoubleT.doubleOf(Double.NaN))) {
      Object input = Map.of("x", value);
      Val expected = plain.eval(input).getVal();
      assertThat(optimized.eval(input).getVal())
          .as(expression + " with " + value)
          .isEqualTo(expected);
      assertThat(establishedOptimized.eval(input).getVal()).isEqualTo(expected);
    }
  }

  @Test
  void crossTypeMembershipEvaluatesLeftOperandOnce() {
    AtomicInteger evaluations = new AtomicInteger();
    Interpretable lhs = countingValue(DoubleT.doubleOf(1), evaluations);
    assertThat(optimize(lhs, new Val[] {intOf(1)}).eval(emptyActivation()).booleanValue()).isTrue();
    assertThat(evaluations).hasValue(1);
  }

  private static Interpretable optimize(Interpretable lhs, Val[] values) {
    Interpretable rhs = newConstValue(2, newValArrayList(DefaultTypeAdapter.Instance, values));
    return BuiltInOptimizer.maybeOptimizeSetMembership(
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
