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
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.divideByZero;
import static org.projectnessie.cel.common.types.Err.errIntOverflow;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.maybeNoSuchOverloadErr;
import static org.projectnessie.cel.common.types.Err.modulusByZero;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.Overflow.addInt64Checked;
import static org.projectnessie.cel.common.types.Overflow.divideInt64Checked;
import static org.projectnessie.cel.common.types.Overflow.moduloInt64Checked;
import static org.projectnessie.cel.common.types.Overflow.multiplyInt64Checked;
import static org.projectnessie.cel.common.types.Overflow.negateInt64Checked;
import static org.projectnessie.cel.common.types.Overflow.subtractInt64Checked;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.BoolT;
import org.projectnessie.cel.common.types.DoubleT;
import org.projectnessie.cel.common.types.IntT;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.Overflow.OverflowException;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.UintT;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.functions.Overload;

final class NativeIntConst extends EvalConst implements NativeIntCapability {
  private final long value;

  NativeIntConst(long id, long value) {
    super(id, intOf(value));
    this.value = value;
  }

  NativeIntConst(long id, IntT value) {
    super(id, value);
    this.value = value.intValue();
  }

  @Override
  public long evalInt(Activation activation) {
    return value;
  }
}

final class NativeUintConst extends EvalConst implements NativeUintCapability {
  private final long value;

  NativeUintConst(long id, long value) {
    super(id, uintOf(value));
    this.value = value;
  }

  NativeUintConst(long id, UintT value) {
    super(id, value);
    this.value = value.intValue();
  }

  @Override
  public long evalUint(Activation activation) {
    return value;
  }
}

final class NativeDoubleConst extends EvalConst implements NativeDoubleCapability {
  private final double value;

  NativeDoubleConst(long id, double value) {
    super(id, doubleOf(value));
    this.value = value;
  }

  NativeDoubleConst(long id, DoubleT value) {
    super(id, value);
    this.value = value.doubleValue();
  }

  @Override
  public double evalDouble(Activation activation) {
    return value;
  }
}

final class NativeBooleanConst extends EvalConst implements NativeBooleanCapability {
  private final boolean value;

  NativeBooleanConst(long id, boolean value) {
    super(id, boolOf(value));
    this.value = value;
  }

  NativeBooleanConst(long id, BoolT value) {
    super(id, value);
    this.value = value.booleanValue();
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return value;
  }
}

final class NativeStringConst extends EvalConst implements NativeStringCapability {
  private final String value;

  NativeStringConst(long id, String value) {
    super(id, stringOf(value));
    this.value = value;
  }

  NativeStringConst(long id, StringT value) {
    super(id, value);
    this.value = requireNonNull((String) value.value());
  }

  @Override
  public String evalString(Activation activation) {
    return value;
  }
}

final class NativeNullConst extends EvalConst implements NativeNullCapability {
  NativeNullConst(long id) {
    super(id, NullT.NullValue);
  }

  @Override
  public void evalNull(Activation activation) {}
}

final class NativeIntIdent extends EvalIdent implements NativeIntCapability {
  NativeIntIdent(long id, String name, TypeAdapter adapter) {
    super(id, name, adapter);
  }

  @Override
  public long evalInt(Activation activation) {
    return NativeSupport.intValue(adapter, resolveRaw(activation));
  }
}

final class NativeUintIdent extends EvalIdent implements NativeUintCapability {
  NativeUintIdent(long id, String name, TypeAdapter adapter) {
    super(id, name, adapter);
  }

  @Override
  public long evalUint(Activation activation) {
    return NativeSupport.uintValue(adapter, resolveRaw(activation));
  }
}

final class NativeBooleanIdent extends EvalIdent implements NativeBooleanCapability {
  NativeBooleanIdent(long id, String name, TypeAdapter adapter) {
    super(id, name, adapter);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return NativeSupport.booleanValue(adapter, resolveRaw(activation));
  }
}

final class NativeDoubleIdent extends EvalIdent implements NativeDoubleCapability {
  NativeDoubleIdent(long id, String name, TypeAdapter adapter) {
    super(id, name, adapter);
  }

  @Override
  public double evalDouble(Activation activation) {
    return NativeSupport.doubleValue(adapter, resolveRaw(activation));
  }
}

final class NativeStringIdent extends EvalIdent implements NativeStringCapability {
  NativeStringIdent(long id, String name, TypeAdapter adapter) {
    super(id, name, adapter);
  }

  @Override
  public String evalString(Activation activation) {
    return NativeSupport.stringValue(adapter, resolveRaw(activation));
  }
}

final class NativeNullIdent extends EvalIdent implements NativeNullCapability {
  NativeNullIdent(long id, String name, TypeAdapter adapter) {
    super(id, name, adapter);
  }

  @Override
  public void evalNull(Activation activation) {
    NativeSupport.nullValue(adapter, resolveRaw(activation));
  }
}

final class NativeRawIdent extends EvalIdent implements NativeListSourceCapability {
  NativeRawIdent(long id, String name, TypeAdapter adapter) {
    super(id, name, adapter);
  }

  @Override
  public Object evalRaw(Activation activation) {
    return resolveRaw(activation);
  }

  @Override
  public Val materializeResolvedList(Object value) {
    return adapter.nativeToValue(value);
  }

  @Override
  public Val materializeResolvedElement(Object value) {
    return adapter.nativeToValue(value);
  }

  @Override
  public boolean exactListSource() {
    return false;
  }
}

final class NativeExactAggregateIdent extends EvalExactAggregateIdent
    implements NativeListSourceCapability {
  NativeExactAggregateIdent(
      long id, String name, TypeAdapter adapter, CheckedAggregateMaterializer materializer) {
    super(id, name, adapter, materializer);
  }

  @Override
  public Object evalRaw(Activation activation) {
    return resolveRaw(activation);
  }

  @Override
  public Val materializeResolvedList(Object value) {
    return value instanceof Val val && (isError(val) || isUnknown(val))
        ? val
        : materializer.materialize(value);
  }

  @Override
  public Val materializeResolvedElement(Object value) {
    return materializer.materializeListElement(value);
  }

  @Override
  public boolean exactListSource() {
    return true;
  }
}

final class NativeExactMapIdent extends EvalExactAggregateIdent
    implements NativeMapSourceCapability {
  NativeExactMapIdent(
      long id, String name, TypeAdapter adapter, CheckedAggregateMaterializer materializer) {
    super(id, name, adapter, materializer);
  }

  @Override
  public Object evalRaw(Activation activation) {
    return resolveRaw(activation);
  }

  @Override
  public Val materializeResolvedMap(Object value) {
    return value instanceof Val val && (isError(val) || isUnknown(val))
        ? val
        : materializer.materialize(value);
  }

  @Override
  public boolean exactMapSource() {
    return true;
  }
}

class NativeIntBinary extends EvalBinary implements NativeIntCapability {
  private final NativeArithmetic operation;

  NativeIntBinary(
      long id,
      String function,
      String overload,
      Interpretable left,
      Interpretable right,
      Overload implementation,
      NativeArithmetic operation) {
    super(id, function, overload, left, right, implementation.operandTrait, implementation.binary);
    this.operation = operation;
  }

  @SuppressWarnings({"DuplicatedCode", "ReassignedVariable", "SuspiciousNameCombination"})
  @Override
  public final long evalInt(Activation activation) {
    long leftValue = 0L;
    long rightValue = 0L;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftValue = ((NativeIntCapability) lhs).evalInt(activation);
    } catch (ValueSignal signal) {
      leftSlow = signal.value;
    }
    try {
      rightValue = ((NativeIntCapability) rhs).evalInt(activation);
    } catch (ValueSignal signal) {
      rightSlow = signal.value;
    }
    if (leftSlow != null || rightSlow != null) {
      return NativeScalarContinuations.intResult(
          evalPrepared(
              leftSlow != null ? leftSlow : intOf(leftValue),
              rightSlow != null ? rightSlow : intOf(rightValue)));
    }
    try {
      return switch (operation) {
        case ADD -> addInt64Checked(leftValue, rightValue);
        case SUBTRACT -> subtractInt64Checked(leftValue, rightValue);
        case MULTIPLY -> multiplyInt64Checked(leftValue, rightValue);
        case DIVIDE -> {
          if (rightValue == 0L) {
            throw signal(divideByZero());
          }
          yield divideInt64Checked(leftValue, rightValue);
        }
        case MODULO -> {
          if (rightValue == 0L) {
            throw signal(modulusByZero());
          }
          yield moduloInt64Checked(leftValue, rightValue);
        }
      };
    } catch (OverflowException e) {
      throw signal(errIntOverflow);
    }
  }
}

final class NativeIntAdd extends NativeIntBinary {
  NativeIntAdd(
      long id,
      String function,
      String overload,
      Interpretable left,
      Interpretable right,
      Overload implementation) {
    super(id, function, overload, left, right, implementation, NativeArithmetic.ADD);
  }
}

final class NativeBooleanNot extends EvalUnary implements NativeBooleanCapability {
  NativeBooleanNot(
      long id, String function, String overload, Interpretable argument, Overload implementation) {
    super(id, function, overload, argument, implementation.operandTrait, implementation.unary);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    try {
      return !((NativeBooleanCapability) arg).evalBoolean(activation);
    } catch (ValueSignal signal) {
      return NativeScalarContinuations.booleanResult(evalPrepared(signal.value));
    }
  }
}

final class NativeIntNegate extends EvalUnary implements NativeIntCapability {
  NativeIntNegate(
      long id, String function, String overload, Interpretable argument, Overload implementation) {
    super(id, function, overload, argument, implementation.operandTrait, implementation.unary);
  }

  @Override
  public long evalInt(Activation activation) {
    try {
      return negateInt64Checked(((NativeIntCapability) arg).evalInt(activation));
    } catch (ValueSignal signal) {
      return NativeScalarContinuations.intResult(evalPrepared(signal.value));
    } catch (OverflowException e) {
      throw signal(errIntOverflow);
    }
  }
}

final class NativeDoubleNegate extends EvalUnary implements NativeDoubleCapability {
  NativeDoubleNegate(
      long id, String function, String overload, Interpretable argument, Overload implementation) {
    super(id, function, overload, argument, implementation.operandTrait, implementation.unary);
  }

  @Override
  public double evalDouble(Activation activation) {
    try {
      return -((NativeDoubleCapability) arg).evalDouble(activation);
    } catch (ValueSignal signal) {
      return NativeScalarContinuations.doubleResult(evalPrepared(signal.value));
    }
  }
}

final class NativeDoubleBinary extends EvalBinary implements NativeDoubleCapability {
  private final NativeArithmetic operation;

  NativeDoubleBinary(
      long id,
      String function,
      String overload,
      Interpretable left,
      Interpretable right,
      Overload implementation,
      NativeArithmetic operation) {
    super(id, function, overload, left, right, implementation.operandTrait, implementation.binary);
    this.operation = operation;
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public double evalDouble(Activation activation) {
    double leftValue = 0.0d;
    double rightValue = 0.0d;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftValue = ((NativeDoubleCapability) lhs).evalDouble(activation);
    } catch (ValueSignal signal) {
      leftSlow = signal.value;
    }
    try {
      rightValue = ((NativeDoubleCapability) rhs).evalDouble(activation);
    } catch (ValueSignal signal) {
      rightSlow = signal.value;
    }
    if (leftSlow != null || rightSlow != null) {
      return NativeScalarContinuations.doubleResult(
          evalPrepared(
              leftSlow != null ? leftSlow : doubleOf(leftValue),
              rightSlow != null ? rightSlow : doubleOf(rightValue)));
    }
    return switch (operation) {
      case ADD -> leftValue + rightValue;
      case SUBTRACT -> leftValue - rightValue;
      case MULTIPLY -> leftValue * rightValue;
      case DIVIDE -> leftValue / rightValue;
      case MODULO -> throw new IllegalStateException("double modulo is not supported");
    };
  }
}

final class NativeStringConcat extends EvalBinary implements NativeStringCapability {
  NativeStringConcat(
      long id,
      String function,
      String overload,
      Interpretable left,
      Interpretable right,
      Overload implementation) {
    super(id, function, overload, left, right, implementation.operandTrait, implementation.binary);
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public String evalString(Activation activation) {
    String leftValue = null;
    String rightValue = null;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftValue = ((NativeStringCapability) lhs).evalString(activation);
    } catch (ValueSignal signal) {
      leftSlow = signal.value;
    }
    try {
      rightValue = ((NativeStringCapability) rhs).evalString(activation);
    } catch (ValueSignal signal) {
      rightSlow = signal.value;
    }
    if (leftSlow != null || rightSlow != null) {
      return NativeScalarContinuations.stringResult(
          evalPrepared(
              leftSlow != null ? leftSlow : stringOf(leftValue),
              rightSlow != null ? rightSlow : stringOf(rightValue)));
    }
    return leftValue + rightValue;
  }
}

class NativeScalarEq extends EvalEq implements NativeBooleanCapability {
  private final NativeScalarKind kind;

  NativeScalarEq(long id, Interpretable left, Interpretable right, NativeScalarKind kind) {
    super(id, left, right);
    this.kind = kind;
  }

  @Override
  public final boolean evalBoolean(Activation activation) {
    return NativeScalarEquality.evaluate(kind, lhs, rhs, activation, this, null);
  }
}

final class NativeScalarNe extends EvalNe implements NativeBooleanCapability {
  private final NativeScalarKind kind;

  NativeScalarNe(long id, Interpretable left, Interpretable right, NativeScalarKind kind) {
    super(id, left, right);
    this.kind = kind;
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return NativeScalarEquality.evaluate(kind, lhs, rhs, activation, null, this);
  }
}

final class NativeBooleanEq extends NativeScalarEq {
  NativeBooleanEq(long id, Interpretable left, Interpretable right) {
    super(id, left, right, NativeScalarKind.BOOLEAN);
  }
}

final class NativeIntEq extends NativeScalarEq {
  NativeIntEq(long id, Interpretable left, Interpretable right) {
    super(id, left, right, NativeScalarKind.INT);
  }
}

final class NativeScalarEquality {
  private NativeScalarEquality() {}

  static boolean evaluate(
      NativeScalarKind kind,
      Interpretable left,
      Interpretable right,
      Activation activation,
      EvalEq equality,
      EvalNe inequality) {
    return switch (kind) {
      case BOOLEAN -> equalBoolean(left, right, activation, equality, inequality);
      case INT -> equalInt(left, right, activation, equality, inequality);
      case UINT -> equalUint(left, right, activation, equality, inequality);
      case DOUBLE -> equalDouble(left, right, activation, equality, inequality);
      case STRING -> equalString(left, right, activation, equality, inequality);
      case NULL -> equalNull(left, right, activation, equality, inequality);
    };
  }

  @SuppressWarnings("DuplicatedCode")
  private static boolean equalBoolean(
      Interpretable left,
      Interpretable right,
      Activation activation,
      EvalEq equality,
      EvalNe inequality) {
    boolean leftValue = false;
    boolean rightValue = false;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftValue = ((NativeBooleanCapability) left).evalBoolean(activation);
    } catch (ValueSignal valueSignal) {
      leftSlow = valueSignal.value;
    }
    try {
      rightValue = ((NativeBooleanCapability) right).evalBoolean(activation);
    } catch (ValueSignal valueSignal) {
      rightSlow = valueSignal.value;
    }
    if (leftSlow != null || rightSlow != null) {
      return slow(
          leftSlow != null ? leftSlow : boolOf(leftValue),
          rightSlow != null ? rightSlow : boolOf(rightValue),
          equality,
          inequality);
    }
    return result(leftValue == rightValue, inequality);
  }

  @SuppressWarnings("DuplicatedCode")
  private static boolean equalInt(
      Interpretable left,
      Interpretable right,
      Activation activation,
      EvalEq equality,
      EvalNe inequality) {
    long leftValue = 0L;
    long rightValue = 0L;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftValue = ((NativeIntCapability) left).evalInt(activation);
    } catch (ValueSignal valueSignal) {
      leftSlow = valueSignal.value;
    }
    try {
      rightValue = ((NativeIntCapability) right).evalInt(activation);
    } catch (ValueSignal valueSignal) {
      rightSlow = valueSignal.value;
    }
    if (leftSlow != null || rightSlow != null) {
      return slow(
          leftSlow != null ? leftSlow : intOf(leftValue),
          rightSlow != null ? rightSlow : intOf(rightValue),
          equality,
          inequality);
    }
    return result(leftValue == rightValue, inequality);
  }

  @SuppressWarnings("DuplicatedCode")
  private static boolean equalUint(
      Interpretable left,
      Interpretable right,
      Activation activation,
      EvalEq equality,
      EvalNe inequality) {
    long leftValue = 0L;
    long rightValue = 0L;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftValue = ((NativeUintCapability) left).evalUint(activation);
    } catch (ValueSignal valueSignal) {
      leftSlow = valueSignal.value;
    }
    try {
      rightValue = ((NativeUintCapability) right).evalUint(activation);
    } catch (ValueSignal valueSignal) {
      rightSlow = valueSignal.value;
    }
    if (leftSlow != null || rightSlow != null) {
      return slow(
          leftSlow != null ? leftSlow : uintOf(leftValue),
          rightSlow != null ? rightSlow : uintOf(rightValue),
          equality,
          inequality);
    }
    return result(leftValue == rightValue, inequality);
  }

  @SuppressWarnings("DuplicatedCode")
  private static boolean equalDouble(
      Interpretable left,
      Interpretable right,
      Activation activation,
      EvalEq equality,
      EvalNe inequality) {
    double leftValue = 0.0d;
    double rightValue = 0.0d;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftValue = ((NativeDoubleCapability) left).evalDouble(activation);
    } catch (ValueSignal valueSignal) {
      leftSlow = valueSignal.value;
    }
    try {
      rightValue = ((NativeDoubleCapability) right).evalDouble(activation);
    } catch (ValueSignal valueSignal) {
      rightSlow = valueSignal.value;
    }
    if (leftSlow != null || rightSlow != null) {
      return slow(
          leftSlow != null ? leftSlow : doubleOf(leftValue),
          rightSlow != null ? rightSlow : doubleOf(rightValue),
          equality,
          inequality);
    }
    return result(leftValue == rightValue, inequality);
  }

  @SuppressWarnings("DuplicatedCode")
  private static boolean equalString(
      Interpretable left,
      Interpretable right,
      Activation activation,
      EvalEq equality,
      EvalNe inequality) {
    String leftValue = null;
    String rightValue = null;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftValue = ((NativeStringCapability) left).evalString(activation);
    } catch (ValueSignal valueSignal) {
      leftSlow = valueSignal.value;
    }
    try {
      rightValue = ((NativeStringCapability) right).evalString(activation);
    } catch (ValueSignal valueSignal) {
      rightSlow = valueSignal.value;
    }
    if (leftSlow != null || rightSlow != null) {
      return slow(
          leftSlow != null ? leftSlow : stringOf(leftValue),
          rightSlow != null ? rightSlow : stringOf(rightValue),
          equality,
          inequality);
    }
    String fastLeft = requireNonNull(leftValue, "native string capability returned null");
    String fastRight = requireNonNull(rightValue, "native string capability returned null");
    return result(fastLeft.equals(fastRight), inequality);
  }

  private static boolean equalNull(
      Interpretable left,
      Interpretable right,
      Activation activation,
      EvalEq equality,
      EvalNe inequality) {
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      ((NativeNullCapability) left).evalNull(activation);
    } catch (ValueSignal valueSignal) {
      leftSlow = valueSignal.value;
    }
    try {
      ((NativeNullCapability) right).evalNull(activation);
    } catch (ValueSignal valueSignal) {
      rightSlow = valueSignal.value;
    }
    if (leftSlow == null && rightSlow == null) {
      return result(true, inequality);
    }
    return slow(
        leftSlow != null ? leftSlow : NullT.NullValue,
        rightSlow != null ? rightSlow : NullT.NullValue,
        equality,
        inequality);
  }

  private static boolean slow(Val left, Val right, EvalEq equality, EvalNe inequality) {
    return NativeScalarContinuations.booleanResult(
        equality != null
            ? equality.evalPrepared(left, right)
            : inequality.evalPrepared(left, right));
  }

  private static boolean result(boolean equal, EvalNe inequality) {
    return (inequality == null) == equal;
  }
}

final class NativeLogicalAnd extends EvalAnd implements NativeBooleanCapability {
  NativeLogicalAnd(long id, Interpretable left, Interpretable right) {
    super(id, left, right);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return NativeLogical.evaluate(lhs, rhs, activation, true);
  }
}

final class NativeLogicalOr extends EvalOr implements NativeBooleanCapability {
  NativeLogicalOr(long id, Interpretable left, Interpretable right) {
    super(id, left, right);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return NativeLogical.evaluate(lhs, rhs, activation, false);
  }
}

final class NativeLogical {
  private NativeLogical() {}

  @SuppressWarnings("DuplicatedCode")
  static boolean evaluate(
      Interpretable left, Interpretable right, Activation activation, boolean and) {
    boolean leftValue = false;
    Val leftSlow = null;
    try {
      leftValue = ((NativeBooleanCapability) left).evalBoolean(activation);
    } catch (ValueSignal valueSignal) {
      leftSlow = valueSignal.value;
    }
    if (leftSlow == null && leftValue != and) {
      return !and;
    }

    boolean rightValue = false;
    Val rightSlow = null;
    try {
      rightValue = ((NativeBooleanCapability) right).evalBoolean(activation);
    } catch (ValueSignal valueSignal) {
      rightSlow = valueSignal.value;
    }
    if (rightSlow == null && rightValue != and) {
      return !and;
    }
    if (leftSlow == null && rightSlow == null) {
      return and;
    }

    Val leftResult = leftSlow != null ? leftSlow : boolOf(leftValue);
    Val rightResult = rightSlow != null ? rightSlow : boolOf(rightValue);
    if (isUnknown(leftResult)) {
      throw signal(leftResult);
    }
    if (isUnknown(rightResult)) {
      throw signal(rightResult);
    }
    if (isError(leftResult)) {
      throw signal(leftResult);
    }
    throw signal(
        noSuchOverload(
            leftResult, and ? Operator.LogicalAnd.id : Operator.LogicalOr.id, rightResult));
  }
}

abstract class NativeConditional extends EvalAttr {
  final NativeBooleanCapability condition;

  NativeConditional(TypeAdapter adapter, Attribute attribute, NativeBooleanCapability condition) {
    super(adapter, attribute);
    this.condition = condition;
  }

  final boolean selectTruthy(Activation activation) {
    try {
      return condition.evalBoolean(activation);
    } catch (ValueSignal valueSignal) {
      if (isUnknown(valueSignal.value)) {
        throw valueSignal;
      }
      if (isError(valueSignal.value)) {
        throw NativeSupport.propagatedError(valueSignal.value);
      }
      throw signal(maybeNoSuchOverloadErr(valueSignal.value));
    }
  }

  final ValueSignal selectedBranch(ValueSignal valueSignal) {
    if (!isError(valueSignal.value)) {
      return valueSignal;
    }
    return NativeSupport.propagatedError(valueSignal.value);
  }
}

final class NativeBooleanConditional extends NativeConditional implements NativeBooleanCapability {
  private final NativeBooleanCapability truthy;
  private final NativeBooleanCapability falsy;

  NativeBooleanConditional(
      TypeAdapter adapter,
      Attribute attribute,
      NativeBooleanCapability condition,
      NativeBooleanCapability truthy,
      NativeBooleanCapability falsy) {
    super(adapter, attribute, condition);
    this.truthy = truthy;
    this.falsy = falsy;
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    boolean selectTruthy = selectTruthy(activation);
    try {
      return selectTruthy ? truthy.evalBoolean(activation) : falsy.evalBoolean(activation);
    } catch (ValueSignal valueSignal) {
      throw selectedBranch(valueSignal);
    }
  }
}

final class NativeIntConditional extends NativeConditional implements NativeIntCapability {
  private final NativeIntCapability truthy;
  private final NativeIntCapability falsy;

  NativeIntConditional(
      TypeAdapter adapter,
      Attribute attribute,
      NativeBooleanCapability condition,
      NativeIntCapability truthy,
      NativeIntCapability falsy) {
    super(adapter, attribute, condition);
    this.truthy = truthy;
    this.falsy = falsy;
  }

  @Override
  public long evalInt(Activation activation) {
    boolean selectTruthy = selectTruthy(activation);
    try {
      return selectTruthy ? truthy.evalInt(activation) : falsy.evalInt(activation);
    } catch (ValueSignal valueSignal) {
      throw selectedBranch(valueSignal);
    }
  }
}

final class NativeUintConditional extends NativeConditional implements NativeUintCapability {
  private final NativeUintCapability truthy;
  private final NativeUintCapability falsy;

  NativeUintConditional(
      TypeAdapter adapter,
      Attribute attribute,
      NativeBooleanCapability condition,
      NativeUintCapability truthy,
      NativeUintCapability falsy) {
    super(adapter, attribute, condition);
    this.truthy = truthy;
    this.falsy = falsy;
  }

  @Override
  public long evalUint(Activation activation) {
    boolean selectTruthy = selectTruthy(activation);
    try {
      return selectTruthy ? truthy.evalUint(activation) : falsy.evalUint(activation);
    } catch (ValueSignal valueSignal) {
      throw selectedBranch(valueSignal);
    }
  }
}

final class NativeDoubleConditional extends NativeConditional implements NativeDoubleCapability {
  private final NativeDoubleCapability truthy;
  private final NativeDoubleCapability falsy;

  NativeDoubleConditional(
      TypeAdapter adapter,
      Attribute attribute,
      NativeBooleanCapability condition,
      NativeDoubleCapability truthy,
      NativeDoubleCapability falsy) {
    super(adapter, attribute, condition);
    this.truthy = truthy;
    this.falsy = falsy;
  }

  @Override
  public double evalDouble(Activation activation) {
    boolean selectTruthy = selectTruthy(activation);
    try {
      return selectTruthy ? truthy.evalDouble(activation) : falsy.evalDouble(activation);
    } catch (ValueSignal valueSignal) {
      throw selectedBranch(valueSignal);
    }
  }
}

final class NativeStringConditional extends NativeConditional implements NativeStringCapability {
  private final NativeStringCapability truthy;
  private final NativeStringCapability falsy;

  NativeStringConditional(
      TypeAdapter adapter,
      Attribute attribute,
      NativeBooleanCapability condition,
      NativeStringCapability truthy,
      NativeStringCapability falsy) {
    super(adapter, attribute, condition);
    this.truthy = truthy;
    this.falsy = falsy;
  }

  @Override
  public String evalString(Activation activation) {
    boolean selectTruthy = selectTruthy(activation);
    try {
      return selectTruthy ? truthy.evalString(activation) : falsy.evalString(activation);
    } catch (ValueSignal valueSignal) {
      throw selectedBranch(valueSignal);
    }
  }
}

final class NativeNullConditional extends NativeConditional implements NativeNullCapability {
  private final NativeNullCapability truthy;
  private final NativeNullCapability falsy;

  NativeNullConditional(
      TypeAdapter adapter,
      Attribute attribute,
      NativeBooleanCapability condition,
      NativeNullCapability truthy,
      NativeNullCapability falsy) {
    super(adapter, attribute, condition);
    this.truthy = truthy;
    this.falsy = falsy;
  }

  @Override
  public void evalNull(Activation activation) {
    boolean selectTruthy = selectTruthy(activation);
    try {
      if (selectTruthy) {
        truthy.evalNull(activation);
      } else {
        falsy.evalNull(activation);
      }
    } catch (ValueSignal valueSignal) {
      throw selectedBranch(valueSignal);
    }
  }
}

final class NativeScalarComparison extends EvalBinary implements NativeBooleanCapability {
  private final NativeScalarKind kind;
  private final NativeComparison comparison;

  NativeScalarComparison(
      long id,
      String function,
      String overload,
      Interpretable left,
      Interpretable right,
      Overload implementation,
      NativeScalarKind kind,
      NativeComparison comparison) {
    super(id, function, overload, left, right, implementation.operandTrait, implementation.binary);
    this.kind = kind;
    this.comparison = comparison;
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return switch (kind) {
      case BOOLEAN -> compareBoolean(activation);
      case INT -> compareInt(activation);
      case UINT -> compareUint(activation);
      case DOUBLE -> compareDouble(activation);
      case STRING -> compareString(activation);
      case NULL -> throw new IllegalStateException("null values are not ordered");
    };
  }

  @SuppressWarnings("DuplicatedCode")
  private boolean compareBoolean(Activation activation) {
    boolean leftValue = false;
    boolean rightValue = false;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftValue = ((NativeBooleanCapability) lhs).evalBoolean(activation);
    } catch (ValueSignal signal) {
      leftSlow = signal.value;
    }
    try {
      rightValue = ((NativeBooleanCapability) rhs).evalBoolean(activation);
    } catch (ValueSignal signal) {
      rightSlow = signal.value;
    }
    if (leftSlow != null || rightSlow != null) {
      return slowComparison(
          leftSlow != null ? leftSlow : boolOf(leftValue),
          rightSlow != null ? rightSlow : boolOf(rightValue));
    }
    return comparison.test(Boolean.compare(leftValue, rightValue));
  }

  @SuppressWarnings("DuplicatedCode")
  private boolean compareInt(Activation activation) {
    long leftValue = 0L;
    long rightValue = 0L;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftValue = ((NativeIntCapability) lhs).evalInt(activation);
    } catch (ValueSignal signal) {
      leftSlow = signal.value;
    }
    try {
      rightValue = ((NativeIntCapability) rhs).evalInt(activation);
    } catch (ValueSignal signal) {
      rightSlow = signal.value;
    }
    if (leftSlow != null || rightSlow != null) {
      return slowComparison(
          leftSlow != null ? leftSlow : intOf(leftValue),
          rightSlow != null ? rightSlow : intOf(rightValue));
    }
    return comparison.test(Long.compare(leftValue, rightValue));
  }

  @SuppressWarnings("DuplicatedCode")
  private boolean compareUint(Activation activation) {
    long leftValue = 0L;
    long rightValue = 0L;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftValue = ((NativeUintCapability) lhs).evalUint(activation);
    } catch (ValueSignal signal) {
      leftSlow = signal.value;
    }
    try {
      rightValue = ((NativeUintCapability) rhs).evalUint(activation);
    } catch (ValueSignal signal) {
      rightSlow = signal.value;
    }
    if (leftSlow != null || rightSlow != null) {
      return slowComparison(
          leftSlow != null ? leftSlow : uintOf(leftValue),
          rightSlow != null ? rightSlow : uintOf(rightValue));
    }
    return comparison.test(Long.compareUnsigned(leftValue, rightValue));
  }

  @SuppressWarnings("DuplicatedCode")
  private boolean compareDouble(Activation activation) {
    double leftValue = 0.0d;
    double rightValue = 0.0d;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftValue = ((NativeDoubleCapability) lhs).evalDouble(activation);
    } catch (ValueSignal signal) {
      leftSlow = signal.value;
    }
    try {
      rightValue = ((NativeDoubleCapability) rhs).evalDouble(activation);
    } catch (ValueSignal signal) {
      rightSlow = signal.value;
    }
    if (leftSlow != null || rightSlow != null) {
      return slowComparison(
          leftSlow != null ? leftSlow : doubleOf(leftValue),
          rightSlow != null ? rightSlow : doubleOf(rightValue));
    }
    int result = leftValue == rightValue ? 0 : Double.compare(leftValue, rightValue);
    return comparison.test(result);
  }

  @SuppressWarnings("DuplicatedCode")
  private boolean compareString(Activation activation) {
    String leftValue = null;
    String rightValue = null;
    Val leftSlow = null;
    Val rightSlow = null;
    try {
      leftValue = ((NativeStringCapability) lhs).evalString(activation);
    } catch (ValueSignal signal) {
      leftSlow = signal.value;
    }
    try {
      rightValue = ((NativeStringCapability) rhs).evalString(activation);
    } catch (ValueSignal signal) {
      rightSlow = signal.value;
    }
    if (leftSlow != null || rightSlow != null) {
      return slowComparison(
          leftSlow != null ? leftSlow : stringOf(leftValue),
          rightSlow != null ? rightSlow : stringOf(rightValue));
    }
    String fastLeft = requireNonNull(leftValue, "native string capability returned null");
    String fastRight = requireNonNull(rightValue, "native string capability returned null");
    return comparison.test(fastLeft.compareTo(fastRight));
  }

  private boolean slowComparison(Val leftValue, Val rightValue) {
    return NativeScalarContinuations.booleanResult(evalPrepared(leftValue, rightValue));
  }
}

enum NativeArithmetic {
  ADD,
  SUBTRACT,
  MULTIPLY,
  DIVIDE,
  MODULO
}

enum NativeComparison {
  LESS,
  LESS_EQUALS,
  GREATER,
  GREATER_EQUALS;

  boolean test(int result) {
    return switch (this) {
      case LESS -> result < 0;
      case LESS_EQUALS -> result <= 0;
      case GREATER -> result > 0;
      case GREATER_EQUALS -> result >= 0;
    };
  }
}

enum NativeScalarKind {
  BOOLEAN,
  INT,
  UINT,
  DOUBLE,
  STRING,
  NULL
}

final class NativeScalarContinuations {
  private NativeScalarContinuations() {}

  static boolean booleanResult(Val result) {
    if (result instanceof BoolT) {
      return result.booleanValue();
    }
    throw signal(result);
  }

  static long intResult(Val result) {
    if (result instanceof IntT) {
      return result.intValue();
    }
    throw signal(result);
  }

  static long uintResult(Val result) {
    if (result instanceof UintT) {
      return result.intValue();
    }
    throw signal(result);
  }

  static double doubleResult(Val result) {
    if (result instanceof DoubleT) {
      return result.doubleValue();
    }
    throw signal(result);
  }

  static String stringResult(Val result) {
    if (result instanceof StringT && result.value() != null) {
      return (String) result.value();
    }
    throw signal(result);
  }
}
