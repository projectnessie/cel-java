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

import static java.util.Objects.requireNonNull;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.functions.Overload;

/**
 * Same-kind scalar membership over an exact list-concatenation traversal.
 *
 * <p>The needle is evaluated first, but an exceptional needle does not suppress strict RHS source
 * resolution and sizing. Once both operands have been evaluated, an error or unknown needle takes
 * precedence over an RHS failure; an ordinary slow-path needle does not.
 */
final class NativeScalarListConcatMembership extends EvalBinary implements NativeBooleanCapability {
  private final NativeListTraversalPlan traversal;
  private final NativeScalarKind kind;
  private final TypeAdapter adapter;

  NativeScalarListConcatMembership(
      long id,
      Interpretable needle,
      NativeListConcat concat,
      NativeListTraversalPlan traversal,
      Overload implementation,
      NativeScalarKind kind,
      TypeAdapter adapter) {
    super(
        id,
        Operator.In.id,
        Overloads.InList,
        needle,
        concat,
        implementation.operandTrait,
        implementation.binary);
    this.traversal = requireNonNull(traversal, "traversal");
    this.kind = requireNonNull(kind, "kind");
    if (kind == NativeScalarKind.NULL) {
      throw new IllegalArgumentException("null concat membership is not specialized");
    }
    this.adapter = requireNonNull(adapter, "adapter");
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    MembershipEvaluation evaluation = new MembershipEvaluation(kind, adapter);
    evaluation.captureNeedle(lhs, activation);

    NativeResolvedListTraversal resolved = null;
    ValueSignal rhsFailure = null;
    try {
      resolved = traversal.resolve(activation);
    } catch (ValueSignal failure) {
      rhsFailure = failure;
    } catch (Exception failure) {
      if (failure instanceof org.projectnessie.cel.OperationAbortedException aborted) {
        throw aborted;
      }
      rhsFailure = signal(newErr(failure, failure.toString()));
    }

    if (evaluation.exceptionalNeedle != null) {
      throw evaluation.exceptionalNeedle;
    }
    if (rhsFailure != null) {
      throw rhsFailure;
    }
    return requireNonNull(resolved, "resolved traversal")
        .traverse(kind, new NativeLoopBinding(activation, ""), evaluation);
  }

  int sourceCount() {
    return traversal.sourceCount();
  }

  /** Evaluation-local needle and comparison state; reusable plan nodes remain immutable. */
  private static final class MembershipEvaluation implements NativeScalarLoopConsumer {
    private final NativeScalarKind kind;
    private final TypeAdapter adapter;
    private boolean booleanNeedle;
    private long integerNeedle;
    private double doubleNeedle;
    private String stringNeedle;
    private Val slowNeedle;
    private ValueSignal exceptionalNeedle;

    private MembershipEvaluation(NativeScalarKind kind, TypeAdapter adapter) {
      this.kind = kind;
      this.adapter = adapter;
    }

    private void captureNeedle(Interpretable needle, Activation activation) {
      try {
        switch (kind) {
          case BOOLEAN ->
              booleanNeedle = ((NativeBooleanCapability) needle).evalBoolean(activation);
          case INT -> integerNeedle = ((NativeIntCapability) needle).evalInt(activation);
          case UINT -> integerNeedle = ((NativeUintCapability) needle).evalUint(activation);
          case DOUBLE -> doubleNeedle = ((NativeDoubleCapability) needle).evalDouble(activation);
          case STRING ->
              stringNeedle =
                  requireNonNull(
                      ((NativeStringCapability) needle).evalString(activation),
                      "native string capability returned null");
          case NULL -> throw new IllegalStateException("null concat membership is not specialized");
        }
      } catch (ValueSignal valueSignal) {
        if (isError(valueSignal.value) || isUnknown(valueSignal.value)) {
          exceptionalNeedle = valueSignal;
        } else {
          slowNeedle = valueSignal.value;
        }
      } catch (Exception exception) {
        if (exception instanceof org.projectnessie.cel.OperationAbortedException aborted) {
          throw aborted;
        }
        exceptionalNeedle = signal(newErr(exception, exception.toString()));
      }
    }

    @Override
    public boolean test(NativeLoopBinding binding) {
      if (slowNeedle != null) {
        return slowNeedle.equal(elementValue(binding)) == True;
      }
      return switch (kind) {
        case BOOLEAN -> booleanNeedle == binding.booleanValue(adapter);
        case INT -> integerNeedle == binding.intValue(adapter);
        case UINT -> integerNeedle == binding.uintValue(adapter);
        case DOUBLE -> doubleNeedle == binding.doubleValue(adapter);
        case STRING -> stringNeedle.equals(binding.stringValue(adapter));
        case NULL -> throw new IllegalStateException("null concat membership is not specialized");
      };
    }

    private Val elementValue(NativeLoopBinding binding) {
      return switch (kind) {
        case BOOLEAN -> boolOf(binding.booleanValue(adapter));
        case INT -> intOf(binding.intValue(adapter));
        case UINT -> uintOf(binding.uintValue(adapter));
        case DOUBLE -> doubleOf(binding.doubleValue(adapter));
        case STRING -> stringOf(binding.stringValue(adapter));
        case NULL -> throw new IllegalStateException("null concat membership is not specialized");
      };
    }
  }
}
