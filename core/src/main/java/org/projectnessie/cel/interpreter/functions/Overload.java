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
package org.projectnessie.cel.interpreter.functions;

import static org.projectnessie.cel.common.types.BoolT.BoolType;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.BytesT.BytesType;
import static org.projectnessie.cel.common.types.DoubleT.DoubleType;
import static org.projectnessie.cel.common.types.DurationT.DurationType;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.IntT.IntNegOne;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.TimestampT.TimestampType;
import static org.projectnessie.cel.common.types.TypeT.TypeType;
import static org.projectnessie.cel.common.types.UintT.UintType;

import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.IterableT;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Adder;
import org.projectnessie.cel.common.types.traits.Comparer;
import org.projectnessie.cel.common.types.traits.Container;
import org.projectnessie.cel.common.types.traits.Divider;
import org.projectnessie.cel.common.types.traits.Indexer;
import org.projectnessie.cel.common.types.traits.Matcher;
import org.projectnessie.cel.common.types.traits.Modder;
import org.projectnessie.cel.common.types.traits.Multiplier;
import org.projectnessie.cel.common.types.traits.Negater;
import org.projectnessie.cel.common.types.traits.Sizer;
import org.projectnessie.cel.common.types.traits.Subtractor;
import org.projectnessie.cel.common.types.traits.Trait;

/**
 * Runtime implementation associated with a CEL function or overload identifier.
 *
 * <p>Extension authors normally create an instance with a fixed-arity factory and register it
 * through {@link org.projectnessie.cel.ProgramOption#functions(Overload...)}. A checked call first
 * resolves its declared overload identifier and falls back to its function name; an unchecked call
 * resolves by function name. Consequently, the registered {@link #operator} must match the
 * identifier used by the corresponding checker declaration.
 *
 * <p>If {@link #operandTrait} is non-null, the first argument must advertise that trait before the
 * configured operation is invoked. Otherwise receiver dispatch is attempted when the first argument
 * implements {@link org.projectnessie.cel.common.types.traits.Receiver}; if neither path applies,
 * evaluation returns a CEL no-such-overload error. A null trait makes the configured operation
 * directly applicable after normal argument error/unknown propagation.
 *
 * <p>An overload is immutable, but its operation object may be invoked concurrently by reusable
 * programs and must be thread-safe. Operations return non-null CEL values; evaluation failures
 * should be CEL error values rather than Java {@code null}. Registering the same identifier twice
 * in one program configuration fails with {@link IllegalArgumentException}.
 */
public final class Overload {
  /** Function name or checked overload identifier used for dispatcher lookup. */
  public final String operator;

  /** Optional trait required on the first argument before invoking the configured operation. */
  public final Trait operandTrait;

  /** One-argument implementation, or {@code null} when not configured. */
  public final UnaryOp unary;

  /** Two-argument implementation, or {@code null} when not configured. */
  public final BinaryOp binary;

  /** Three-argument implementation, or {@code null} when not configured. */
  public final TernaryOp ternary;

  /** Four-argument implementation, or {@code null} when not configured. */
  public final QuaternaryOp quaternary;

  /** Five-argument implementation, or {@code null} when not configured. */
  public final QuinaryOp quinary;

  /** Variable-arity implementation, or {@code null} when not configured. */
  public final FunctionOp function;

  /**
   * Creates an unconditional one-argument implementation.
   *
   * @param operator CEL operator
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload unary(Operator operator, UnaryOp op) {
    return unary(operator.id, op);
  }

  /**
   * Creates an unconditional one-argument implementation.
   *
   * @param operator dispatcher identifier
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload unary(String operator, UnaryOp op) {
    return unary(operator, null, op);
  }

  /**
   * Creates a trait-guarded one-argument implementation.
   *
   * @param operator CEL operator
   * @param trait required trait on the argument, or {@code null} for unconditional dispatch
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload unary(Operator operator, Trait trait, UnaryOp op) {
    return unary(operator.id, trait, op);
  }

  /**
   * Creates a trait-guarded one-argument implementation.
   *
   * @param operator dispatcher identifier
   * @param trait required trait on the argument, or {@code null} for unconditional dispatch
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload unary(String operator, Trait trait, UnaryOp op) {
    return new Overload(operator, trait, op, null, null, null, null, null);
  }

  /**
   * Creates an unconditional two-argument implementation.
   *
   * @param operator CEL operator
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload binary(Operator operator, BinaryOp op) {
    return binary(operator.id, op);
  }

  /**
   * Creates an unconditional two-argument implementation.
   *
   * @param operator dispatcher identifier
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload binary(String operator, BinaryOp op) {
    return binary(operator, null, op);
  }

  /**
   * Creates a trait-guarded two-argument implementation.
   *
   * @param operator CEL operator
   * @param trait required trait on the first argument, or {@code null} for unconditional dispatch
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload binary(Operator operator, Trait trait, BinaryOp op) {
    return binary(operator.id, trait, op);
  }

  /**
   * Creates a trait-guarded two-argument implementation.
   *
   * @param operator dispatcher identifier
   * @param trait required trait on the first argument, or {@code null} for unconditional dispatch
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload binary(String operator, Trait trait, BinaryOp op) {
    return new Overload(operator, trait, null, op, null, null, null, null);
  }

  /**
   * Creates an unconditional three-argument implementation.
   *
   * @param operator dispatcher identifier
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload ternary(String operator, TernaryOp op) {
    return ternary(operator, null, op);
  }

  /**
   * Creates a trait-guarded three-argument implementation.
   *
   * @param operator dispatcher identifier
   * @param trait required trait on the first argument, or {@code null} for unconditional dispatch
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload ternary(String operator, Trait trait, TernaryOp op) {
    return new Overload(operator, trait, null, null, op, null, null, null);
  }

  /**
   * Creates an unconditional four-argument implementation.
   *
   * @param operator dispatcher identifier
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload quaternary(String operator, QuaternaryOp op) {
    return quaternary(operator, null, op);
  }

  /**
   * Creates a trait-guarded four-argument implementation.
   *
   * @param operator dispatcher identifier
   * @param trait required trait on the first argument, or {@code null} for unconditional dispatch
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload quaternary(String operator, Trait trait, QuaternaryOp op) {
    return new Overload(operator, trait, null, null, null, op, null, null);
  }

  /**
   * Creates an unconditional five-argument implementation.
   *
   * @param operator dispatcher identifier
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload quinary(String operator, QuinaryOp op) {
    return quinary(operator, null, op);
  }

  /**
   * Creates a trait-guarded five-argument implementation.
   *
   * @param operator dispatcher identifier
   * @param trait required trait on the first argument, or {@code null} for unconditional dispatch
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload quinary(String operator, Trait trait, QuinaryOp op) {
    return new Overload(operator, trait, null, null, null, null, op, null);
  }

  /**
   * Creates an unconditional variable-arity implementation.
   *
   * @param operator dispatcher identifier
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload function(String operator, FunctionOp op) {
    return function(operator, null, op);
  }

  /**
   * Creates a trait-guarded variable-arity implementation.
   *
   * @param operator dispatcher identifier
   * @param trait required trait on the first argument, or {@code null} for unconditional dispatch
   * @param op runtime implementation
   * @return the immutable overload definition
   */
  public static Overload function(String operator, Trait trait, FunctionOp op) {
    return new Overload(operator, trait, null, null, null, null, null, op);
  }

  /**
   * Creates a definition containing one-, two-, and variable-arity implementations.
   *
   * <p>Planning selects the implementation matching the call arity.
   *
   * @param operator dispatcher identifier
   * @param trait required trait on the first argument, or {@code null} for unconditional dispatch
   * @param unary one-argument implementation, or {@code null}
   * @param binary two-argument implementation, or {@code null}
   * @param function generic implementation, or {@code null}
   * @return the immutable overload definition
   */
  public static Overload overload(
      String operator, Trait trait, UnaryOp unary, BinaryOp binary, FunctionOp function) {
    return new Overload(operator, trait, unary, binary, null, null, null, function);
  }

  /**
   * Creates a definition containing implementations through arity three plus a generic fallback.
   *
   * @param operator dispatcher identifier
   * @param trait required trait on the first argument, or {@code null} for unconditional dispatch
   * @param unary one-argument implementation, or {@code null}
   * @param binary two-argument implementation, or {@code null}
   * @param ternary three-argument implementation, or {@code null}
   * @param function generic implementation, or {@code null}
   * @return the immutable overload definition
   */
  public static Overload overload(
      String operator,
      Trait trait,
      UnaryOp unary,
      BinaryOp binary,
      TernaryOp ternary,
      FunctionOp function) {
    return new Overload(operator, trait, unary, binary, ternary, null, null, function);
  }

  /**
   * Creates a definition containing implementations through arity four plus a generic fallback.
   *
   * @param operator dispatcher identifier
   * @param trait required trait on the first argument, or {@code null} for unconditional dispatch
   * @param unary one-argument implementation, or {@code null}
   * @param binary two-argument implementation, or {@code null}
   * @param ternary three-argument implementation, or {@code null}
   * @param quaternary four-argument implementation, or {@code null}
   * @param function generic implementation, or {@code null}
   * @return the immutable overload definition
   */
  public static Overload overload(
      String operator,
      Trait trait,
      UnaryOp unary,
      BinaryOp binary,
      TernaryOp ternary,
      QuaternaryOp quaternary,
      FunctionOp function) {
    return new Overload(operator, trait, unary, binary, ternary, quaternary, null, function);
  }

  /**
   * Creates a definition containing implementations through arity five plus a generic fallback.
   *
   * @param operator dispatcher identifier
   * @param trait required trait on the first argument, or {@code null} for unconditional dispatch
   * @param unary one-argument implementation, or {@code null}
   * @param binary two-argument implementation, or {@code null}
   * @param ternary three-argument implementation, or {@code null}
   * @param quaternary four-argument implementation, or {@code null}
   * @param quinary five-argument implementation, or {@code null}
   * @param function generic implementation, or {@code null}
   * @return the immutable overload definition
   */
  public static Overload overload(
      String operator,
      Trait trait,
      UnaryOp unary,
      BinaryOp binary,
      TernaryOp ternary,
      QuaternaryOp quaternary,
      QuinaryOp quinary,
      FunctionOp function) {
    return new Overload(operator, trait, unary, binary, ternary, quaternary, quinary, function);
  }

  private Overload(
      String operator,
      Trait operandTrait,
      UnaryOp unary,
      BinaryOp binary,
      TernaryOp ternary,
      QuaternaryOp quaternary,
      QuinaryOp quinary,
      FunctionOp function) {
    this.operator = operator;
    this.operandTrait = operandTrait;
    this.unary = unary;
    this.binary = binary;
    this.ternary = ternary;
    this.quaternary = quaternary;
    this.quinary = quinary;
    this.function = function;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("Overload{");
    sb.append(operator).append('\'');
    sb.append(", trait=").append(operandTrait);
    if (unary != null) {
      sb.append(", unary");
    }
    if (binary != null) {
      sb.append(", binary");
    }
    if (ternary != null) {
      sb.append(", ternary");
    }
    if (quaternary != null) {
      sb.append(", quaternary");
    }
    if (quinary != null) {
      sb.append(", quinary");
    }
    if (function != null) {
      sb.append(", function");
    }
    sb.append('}');
    return sb.toString();
  }

  /**
   * Returns the built-in runtime overload definitions.
   *
   * <p>The returned array is a new shallow copy. The overload definitions and their operation
   * objects are shared and immutable.
   *
   * @return a copy of the standard overload array
   */
  public static Overload[] standardOverloads() {
    return StandardOverloadsHolder.OVERLOADS.clone();
  }

  private static final class StandardOverloadsHolder {
    private static final Overload[] OVERLOADS = createStandardOverloads();
  }

  private static Overload[] createStandardOverloads() {
    return new Overload[] {
      // Logical not (!a)
      unary(
          Operator.LogicalNot,
          Trait.NegatorType,
          v -> {
            if (v.type().typeEnum() == TypeEnum.Bool) {
              return ((Negater) v).negate();
            }
            return noSuchOverload(null, Operator.LogicalNot.id, v);
          }),

      // Not strictly false: IsBool(a) ? a : true
      unary(Operator.NotStrictlyFalse, Overload::notStrictlyFalse),
      // Deprecated: not strictly false, may be overridden in the environment.
      unary(Operator.OldNotStrictlyFalse, Overload::notStrictlyFalse),

      // Less than operator
      binary(
          Operator.Less,
          Trait.ComparerType,
          (lhs, rhs) -> {
            Val cmp = ((Comparer) lhs).compare(rhs);
            if (cmp == IntNegOne) {
              return True;
            }
            if (cmp == IntOne || cmp == IntZero) {
              return False;
            }
            return cmp;
          }),

      // Less than or equal operator
      binary(
          Operator.LessEquals,
          Trait.ComparerType,
          (lhs, rhs) -> {
            Val cmp = ((Comparer) lhs).compare(rhs);
            if (cmp == IntNegOne || cmp == IntZero) {
              return True;
            }
            if (cmp == IntOne) {
              return False;
            }
            return cmp;
          }),

      // Greater than operator
      binary(
          Operator.Greater,
          Trait.ComparerType,
          (lhs, rhs) -> {
            Val cmp = ((Comparer) lhs).compare(rhs);
            if (cmp == IntOne) {
              return True;
            }
            if (cmp == IntNegOne || cmp == IntZero) {
              return False;
            }
            return cmp;
          }),

      // Greater than equal operators
      binary(
          Operator.GreaterEquals,
          Trait.ComparerType,
          (lhs, rhs) -> {
            Val cmp = ((Comparer) lhs).compare(rhs);
            if (cmp == IntOne || cmp == IntZero) {
              return True;
            }
            if (cmp == IntNegOne) {
              return False;
            }
            return cmp;
          }),

      // TODO: Verify overflow, NaN, underflow cases for numeric values.

      // Add operator
      binary(Operator.Add, Trait.AdderType, (lhs, rhs) -> ((Adder) lhs).add(rhs)),

      // Subtract operators
      binary(
          Operator.Subtract, Trait.SubtractorType, (lhs, rhs) -> ((Subtractor) lhs).subtract(rhs)),

      // Multiply operator
      binary(
          Operator.Multiply, Trait.MultiplierType, (lhs, rhs) -> ((Multiplier) lhs).multiply(rhs)),

      // Divide operator
      binary(Operator.Divide, Trait.DividerType, (lhs, rhs) -> ((Divider) lhs).divide(rhs)),

      // Modulo operator
      binary(Operator.Modulo, Trait.ModderType, (lhs, rhs) -> ((Modder) lhs).modulo(rhs)),

      // Negate operator
      unary(
          Operator.Negate,
          Trait.NegatorType,
          v -> {
            if (v.type().typeEnum() != TypeEnum.Bool) {
              return ((Negater) v).negate();
            }
            return noSuchOverload(null, Operator.Negate.id, v);
          }),

      // Index operator
      binary(Operator.Index, Trait.IndexerType, (lhs, rhs) -> ((Indexer) lhs).get(rhs)),

      // Size function
      unary(Overloads.Size, Trait.SizerType, (v) -> ((Sizer) v).size()),

      // In operator
      binary(Operator.In, Overload::inAggregate),
      // Deprecated: in operator, may be overridden in the environment.
      binary(Operator.OldIn, Overload::inAggregate),

      // Matches function
      binary(Overloads.Matches, Trait.MatcherType, (lhs, rhs) -> ((Matcher) lhs).match(rhs)),

      // Type conversion functions
      // TODO: verify type conversion safety of numeric values.

      // Int conversions.
      unary(Overloads.TypeConvertInt, v -> v.convertToType(IntType)),

      // Uint conversions.
      unary(Overloads.TypeConvertUint, v -> v.convertToType(UintType)),

      // Double conversions.
      unary(Overloads.TypeConvertDouble, v -> v.convertToType(DoubleType)),

      // Bool conversions.
      unary(Overloads.TypeConvertBool, v -> v.convertToType(BoolType)),

      // Bytes conversions.
      unary(Overloads.TypeConvertBytes, v -> v.convertToType(BytesType)),

      // String conversions.
      unary(Overloads.TypeConvertString, v -> v.convertToType(StringType)),

      // Timestamp conversions.
      unary(Overloads.TypeConvertTimestamp, v -> v.convertToType(TimestampType)),

      // Duration conversions.
      unary(Overloads.TypeConvertDuration, v -> v.convertToType(DurationType)),

      // Type operations.
      unary(Overloads.TypeConvertType, v -> v.convertToType(TypeType)),

      // Dyn conversion (identity function).
      unary(Overloads.TypeConvertDyn, v -> v),
      // The interpreter cursor crosses these low-level unary boundaries as a Val.
      unary(Overloads.Iterator, Trait.IterableType, v -> ((IterableT) v).iterator()),
      unary(Overloads.HasNext, Trait.IteratorType, v -> ((IteratorT) v).hasNext()),
      unary(Overloads.Next, Trait.IteratorType, v -> ((IteratorT) v).next())
    };
  }

  static Val notStrictlyFalse(Val value) {
    if (value.type().typeEnum() == TypeEnum.Bool) {
      return value;
    }
    return True;
  }

  static Val inAggregate(Val lhs, Val rhs) {
    if (rhs.type().hasTrait(Trait.ContainerType)) {
      return ((Container) rhs).contains(lhs);
    }
    return noSuchOverload(lhs, Operator.In.id, rhs);
  }
}
