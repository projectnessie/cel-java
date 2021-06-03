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
import static org.projectnessie.cel.common.types.BoolT.isBool;
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
import static org.projectnessie.cel.common.types.TypeValue.TypeType;
import static org.projectnessie.cel.common.types.UintT.UintType;

import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.BoolT;
import org.projectnessie.cel.common.types.IterableT;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.Overloads;
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
 * Overload defines a named overload of a function, indicating an operand trait which must be
 * present on the first argument to the overload as well as one of either a unary, binary, or
 * function implementation.
 *
 * <p>The majority of operators within the expression language are unary or binary and the
 * specializations simplify the call contract for implementers of types with operator overloads. Any
 * added complexity is assumed to be handled by the generic FunctionOp.
 */
public class Overload {
  /** Operator name as written in an expression or defined within operators.go. */
  public final String operator;

  /**
   * Operand trait used to dispatch the call. The zero-value indicates a global function overload or
   * that one of the Unary / Binary / Function definitions should be used to execute the call.
   */
  public final Trait operandTrait;

  /** Unary defines the overload with a UnaryOp implementation. May be nil. */
  public final UnaryOp unary;

  /** Binary defines the overload with a BinaryOp implementation. May be nil. */
  public final BinaryOp binary;

  /** Function defines the overload with a FunctionOp implementation. May be nil. */
  public final FunctionOp function;

  public static Overload unary(Operator operator, UnaryOp op) {
    return unary(operator.id, op);
  }

  public static Overload unary(String operator, UnaryOp op) {
    return unary(operator, null, op);
  }

  public static Overload unary(Operator operator, Trait trait, UnaryOp op) {
    return unary(operator.id, trait, op);
  }

  public static Overload unary(String operator, Trait trait, UnaryOp op) {
    return new Overload(operator, trait, op, null, null);
  }

  public static Overload binary(Operator operator, BinaryOp op) {
    return binary(operator.id, op);
  }

  public static Overload binary(String operator, BinaryOp op) {
    return binary(operator, null, op);
  }

  public static Overload binary(Operator operator, Trait trait, BinaryOp op) {
    return binary(operator.id, trait, op);
  }

  public static Overload binary(String operator, Trait trait, BinaryOp op) {
    return new Overload(operator, trait, null, op, null);
  }

  public static Overload function(String operator, FunctionOp op) {
    return function(operator, null, op);
  }

  public static Overload function(String operator, Trait trait, FunctionOp op) {
    return new Overload(operator, trait, null, null, op);
  }

  private Overload(
      String operator, Trait operandTrait, UnaryOp unary, BinaryOp binary, FunctionOp function) {
    this.operator = operator;
    this.operandTrait = operandTrait;
    this.unary = unary;
    this.binary = binary;
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
      sb.append(", unary");
    }
    if (binary != null) {
      sb.append(", function");
    }
    sb.append('}');
    return sb.toString();
  }

  /** StandardOverloads returns the definitions of the built-in overloads. */
  public static Overload[] standardOverloads() {
    return new Overload[] {
      // Logical not (!a)
      unary(
          Operator.LogicalNot,
          Trait.NegatorType,
          v -> {
            if (!isBool(v)) {
              return noSuchOverload(null, Operator.LogicalNot.id, v);
            }
            return ((Negater) v).negate();
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
            if (BoolT.isBool(v)) {
              return noSuchOverload(null, Operator.Negate.id, v);
            }
            return ((Negater) v).negate();
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
      unary(Overloads.Iterator, Trait.IterableType, v -> ((IterableT) v).iterator()),
      unary(Overloads.HasNext, Trait.IteratorType, v -> ((IteratorT) v).hasNext()),
      unary(Overloads.Next, Trait.IteratorType, v -> ((IteratorT) v).next())
    };
  }

  static Val notStrictlyFalse(Val value) {
    if (BoolT.isBool(value)) {
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
