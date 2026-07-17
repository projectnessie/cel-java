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
package org.projectnessie.cel.extension;

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.errIntOverflow;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import com.google.api.expr.v1alpha1.Decl;
import com.google.api.expr.v1alpha1.Type;
import java.util.ArrayList;
import java.util.List;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.DoubleT;
import org.projectnessie.cel.common.types.IntT;
import org.projectnessie.cel.common.types.UintT;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.interpreter.functions.Overload;

/** MathLib provides CEL helper functions from the standard math extension library. */
public final class MathLib implements Library {
  private static final String GREATEST = "math.greatest";
  private static final String LEAST = "math.least";
  private static final String CEIL = "math.ceil";
  private static final String FLOOR = "math.floor";
  private static final String ROUND = "math.round";
  private static final String TRUNC = "math.trunc";
  private static final String ABS = "math.abs";
  private static final String SIGN = "math.sign";
  private static final String IS_NAN = "math.isNaN";
  private static final String IS_INF = "math.isInf";
  private static final String IS_FINITE = "math.isFinite";
  private static final String BIT_AND = "math.bitAnd";
  private static final String BIT_OR = "math.bitOr";
  private static final String BIT_XOR = "math.bitXor";
  private static final String BIT_NOT = "math.bitNot";
  private static final String BIT_SHIFT_LEFT = "math.bitShiftLeft";
  private static final String BIT_SHIFT_RIGHT = "math.bitShiftRight";

  private MathLib() {}

  public static EnvOption math() {
    return Library.Lib(new MathLib());
  }

  @Override
  public List<EnvOption> getCompileOptions() {
    List<Decl> declarations = new ArrayList<>();
    declarations.add(minMaxDeclaration(GREATEST));
    declarations.add(minMaxDeclaration(LEAST));
    declarations.add(unaryDeclaration(CEIL, Decls.Double));
    declarations.add(unaryDeclaration(FLOOR, Decls.Double));
    declarations.add(unaryDeclaration(ROUND, Decls.Double));
    declarations.add(unaryDeclaration(TRUNC, Decls.Double));
    declarations.add(unaryDeclaration(ABS, Decls.Dyn));
    declarations.add(unaryDeclaration(SIGN, Decls.Dyn));
    declarations.add(unaryDeclaration(IS_NAN, Decls.Bool));
    declarations.add(unaryDeclaration(IS_INF, Decls.Bool));
    declarations.add(unaryDeclaration(IS_FINITE, Decls.Bool));
    declarations.add(binaryDeclaration(BIT_AND, Decls.Dyn));
    declarations.add(binaryDeclaration(BIT_OR, Decls.Dyn));
    declarations.add(binaryDeclaration(BIT_XOR, Decls.Dyn));
    declarations.add(unaryDeclaration(BIT_NOT, Decls.Dyn));
    declarations.add(binaryDeclaration(BIT_SHIFT_LEFT, Decls.Dyn));
    declarations.add(binaryDeclaration(BIT_SHIFT_RIGHT, Decls.Dyn));
    return List.of(EnvOption.declarations(declarations));
  }

  @Override
  public List<ProgramOption> getProgramOptions() {
    List<Overload> overloads = new ArrayList<>();
    overloads.add(
        Overload.overload(GREATEST, null, MathLib::greatest, MathLib::greatest, MathLib::greatest));
    overloads.add(Overload.overload(LEAST, null, MathLib::least, MathLib::least, MathLib::least));
    addArityOverloads(overloads, GREATEST, MathLib::greatest);
    addArityOverloads(overloads, LEAST, MathLib::least);
    overloads.add(Overload.unary(CEIL, MathLib::ceil));
    overloads.add(Overload.unary(overloadId(CEIL, 1), MathLib::ceil));
    overloads.add(Overload.unary(FLOOR, MathLib::floor));
    overloads.add(Overload.unary(overloadId(FLOOR, 1), MathLib::floor));
    overloads.add(Overload.unary(ROUND, MathLib::round));
    overloads.add(Overload.unary(overloadId(ROUND, 1), MathLib::round));
    overloads.add(Overload.unary(TRUNC, MathLib::trunc));
    overloads.add(Overload.unary(overloadId(TRUNC, 1), MathLib::trunc));
    overloads.add(Overload.unary(ABS, MathLib::abs));
    overloads.add(Overload.unary(overloadId(ABS, 1), MathLib::abs));
    overloads.add(Overload.unary(SIGN, MathLib::sign));
    overloads.add(Overload.unary(overloadId(SIGN, 1), MathLib::sign));
    overloads.add(Overload.unary(IS_NAN, MathLib::isNaN));
    overloads.add(Overload.unary(overloadId(IS_NAN, 1), MathLib::isNaN));
    overloads.add(Overload.unary(IS_INF, MathLib::isInf));
    overloads.add(Overload.unary(overloadId(IS_INF, 1), MathLib::isInf));
    overloads.add(Overload.unary(IS_FINITE, MathLib::isFinite));
    overloads.add(Overload.unary(overloadId(IS_FINITE, 1), MathLib::isFinite));
    overloads.add(Overload.binary(BIT_AND, MathLib::bitAnd));
    overloads.add(Overload.binary(overloadId(BIT_AND, 2), MathLib::bitAnd));
    overloads.add(Overload.binary(BIT_OR, MathLib::bitOr));
    overloads.add(Overload.binary(overloadId(BIT_OR, 2), MathLib::bitOr));
    overloads.add(Overload.binary(BIT_XOR, MathLib::bitXor));
    overloads.add(Overload.binary(overloadId(BIT_XOR, 2), MathLib::bitXor));
    overloads.add(Overload.unary(BIT_NOT, MathLib::bitNot));
    overloads.add(Overload.unary(overloadId(BIT_NOT, 1), MathLib::bitNot));
    overloads.add(Overload.binary(BIT_SHIFT_LEFT, MathLib::bitShiftLeft));
    overloads.add(Overload.binary(overloadId(BIT_SHIFT_LEFT, 2), MathLib::bitShiftLeft));
    overloads.add(Overload.binary(BIT_SHIFT_RIGHT, MathLib::bitShiftRight));
    overloads.add(Overload.binary(overloadId(BIT_SHIFT_RIGHT, 2), MathLib::bitShiftRight));
    return List.of(ProgramOption.functions(overloads.toArray(Overload[]::new)));
  }

  private static Decl minMaxDeclaration(String function) {
    List<com.google.api.expr.v1alpha1.Decl.FunctionDecl.Overload> overloads = new ArrayList<>();
    overloads.add(Decls.newOverload(overloadId(function, "int"), List.of(Decls.Int), Decls.Int));
    overloads.add(Decls.newOverload(overloadId(function, "uint"), List.of(Decls.Uint), Decls.Uint));
    overloads.add(
        Decls.newOverload(overloadId(function, "double"), List.of(Decls.Double), Decls.Double));
    for (int arity = 2; arity <= 5; arity++) {
      overloads.add(Decls.newOverload(overloadId(function, arity), dynArgs(arity), Decls.Dyn));
    }
    overloads.add(
        Decls.newOverload(
            overloadId(function, "list"), List.of(Decls.newListType(Decls.Dyn)), Decls.Dyn));
    return Decls.newFunction(function, overloads);
  }

  private static Decl unaryDeclaration(String function, Type result) {
    return Decls.newFunction(
        function, Decls.newOverload(overloadId(function, 1), List.of(Decls.Dyn), result));
  }

  private static Decl binaryDeclaration(String function, Type result) {
    return Decls.newFunction(
        function, Decls.newOverload(overloadId(function, 2), dynArgs(2), result));
  }

  private static List<Type> dynArgs(int count) {
    List<Type> args = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      args.add(Decls.Dyn);
    }
    return args;
  }

  private static void addArityOverloads(
      List<Overload> overloads,
      String function,
      org.projectnessie.cel.interpreter.functions.FunctionOp op) {
    overloads.add(Overload.unary(overloadId(function, "int"), op::invoke));
    overloads.add(Overload.unary(overloadId(function, "uint"), op::invoke));
    overloads.add(Overload.unary(overloadId(function, "double"), op::invoke));
    overloads.add(
        Overload.binary(overloadId(function, 2), (left, right) -> op.invoke(left, right)));
    for (int arity = 3; arity <= 5; arity++) {
      overloads.add(Overload.function(overloadId(function, arity), op));
    }
    overloads.add(Overload.unary(overloadId(function, "list"), op::invoke));
  }

  private static String overloadId(String function, int arity) {
    return function.replace('.', '_') + "_" + arity;
  }

  private static String overloadId(String function, String suffix) {
    return function.replace('.', '_') + "_" + suffix;
  }

  private static Val greatest(Val... values) {
    return minMax(values, true);
  }

  private static Val least(Val... values) {
    return minMax(values, false);
  }

  private static Val minMax(Val[] values, boolean greatest) {
    List<Val> candidates = candidates(values);
    if (candidates.isEmpty()) {
      return newErr("empty argument list");
    }

    Val result = candidates.get(0);
    if (!isNumber(result)) {
      return noSuchOverload();
    }
    for (int i = 1; i < candidates.size(); i++) {
      Val candidate = candidates.get(i);
      if (!isNumber(candidate)) {
        return noSuchOverload();
      }
      int cmp = compareNumbers(candidate, result);
      if ((greatest && cmp > 0) || (!greatest && cmp < 0)) {
        result = candidate;
      }
    }
    return result;
  }

  private static List<Val> candidates(Val[] values) {
    if (values.length == 1 && values[0] instanceof Lister list) {
      int size = (int) list.size().intValue();
      List<Val> elements = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        elements.add(list.get(intOf(i)));
      }
      return elements;
    }
    return List.of(values);
  }

  private static int compareNumbers(Val left, Val right) {
    if (left instanceof DoubleT || right instanceof DoubleT) {
      return Double.compare(asDouble(left), asDouble(right));
    }
    if (left instanceof UintT && right instanceof UintT) {
      return Long.compareUnsigned(left.intValue(), right.intValue());
    }
    if (left instanceof UintT && right instanceof IntT) {
      long rightValue = right.intValue();
      return rightValue < 0 ? 1 : Long.compareUnsigned(left.intValue(), rightValue);
    }
    if (left instanceof IntT && right instanceof UintT) {
      long leftValue = left.intValue();
      return leftValue < 0 ? -1 : Long.compareUnsigned(leftValue, right.intValue());
    }
    return Long.compare(left.intValue(), right.intValue());
  }

  private static double asDouble(Val value) {
    if (value instanceof DoubleT) {
      return value.doubleValue();
    }
    if (value instanceof UintT) {
      return Long.toUnsignedString(value.intValue()).equals("18446744073709551615")
          ? 18446744073709551615.0
          : Double.parseDouble(Long.toUnsignedString(value.intValue()));
    }
    return value.intValue();
  }

  private static Val ceil(Val value) {
    if (!(value instanceof DoubleT)) {
      return noSuchOverload();
    }
    return doubleOf(Math.ceil(value.doubleValue()));
  }

  private static Val floor(Val value) {
    if (!(value instanceof DoubleT)) {
      return noSuchOverload();
    }
    return doubleOf(Math.floor(value.doubleValue()));
  }

  private static Val round(Val value) {
    if (!(value instanceof DoubleT)) {
      return noSuchOverload();
    }
    double d = value.doubleValue();
    if (Double.isNaN(d) || Double.isInfinite(d)) {
      return doubleOf(d);
    }
    return doubleOf(d < 0 ? Math.ceil(d - 0.5d) : Math.floor(d + 0.5d));
  }

  private static Val trunc(Val value) {
    if (!(value instanceof DoubleT)) {
      return noSuchOverload();
    }
    double d = value.doubleValue();
    if (Double.isNaN(d) || Double.isInfinite(d)) {
      return doubleOf(d);
    }
    return doubleOf(d < 0 ? Math.ceil(d) : Math.floor(d));
  }

  private static Val abs(Val value) {
    if (value instanceof UintT) {
      return value;
    }
    if (value instanceof IntT) {
      long v = value.intValue();
      if (v == Long.MIN_VALUE) {
        return errIntOverflow;
      }
      return intOf(Math.abs(v));
    }
    if (value instanceof DoubleT) {
      return doubleOf(Math.abs(value.doubleValue()));
    }
    return noSuchOverload();
  }

  private static Val sign(Val value) {
    if (value instanceof UintT) {
      return uintOf(value.intValue() == 0 ? 0 : 1);
    }
    if (value instanceof IntT) {
      return intOf(Long.compare(value.intValue(), 0));
    }
    if (value instanceof DoubleT) {
      return doubleOf(Double.compare(value.doubleValue(), 0.0d));
    }
    return noSuchOverload();
  }

  private static Val isNaN(Val value) {
    if (!(value instanceof DoubleT)) {
      return noSuchOverload();
    }
    return Double.isNaN(value.doubleValue()) ? True : False;
  }

  private static Val isInf(Val value) {
    if (!(value instanceof DoubleT)) {
      return noSuchOverload();
    }
    return Double.isInfinite(value.doubleValue()) ? True : False;
  }

  private static Val isFinite(Val value) {
    if (!(value instanceof DoubleT)) {
      return noSuchOverload();
    }
    double d = value.doubleValue();
    return !Double.isNaN(d) && !Double.isInfinite(d) ? True : False;
  }

  private static Val bitAnd(Val left, Val right) {
    return bitwise(left, right, (a, b) -> a & b);
  }

  private static Val bitOr(Val left, Val right) {
    return bitwise(left, right, (a, b) -> a | b);
  }

  private static Val bitXor(Val left, Val right) {
    return bitwise(left, right, (a, b) -> a ^ b);
  }

  private static Val bitwise(Val left, Val right, LongOperator op) {
    if (left instanceof IntT && right instanceof IntT) {
      return intOf(op.apply(left.intValue(), right.intValue()));
    }
    if (left instanceof UintT && right instanceof UintT) {
      return uintOf(op.apply(left.intValue(), right.intValue()));
    }
    return noSuchOverload();
  }

  private static Val bitNot(Val value) {
    if (value instanceof IntT) {
      return intOf(~value.intValue());
    }
    if (value instanceof UintT) {
      return uintOf(~value.intValue());
    }
    return noSuchOverload();
  }

  private static Val bitShiftLeft(Val left, Val right) {
    if (!(right instanceof IntT)) {
      return noSuchOverload();
    }
    long shift = right.intValue();
    if (shift < 0) {
      return newErr("negative offset");
    }
    if (shift >= Long.SIZE) {
      return left instanceof UintT ? uintOf(0) : left instanceof IntT ? intOf(0) : noSuchOverload();
    }
    if (left instanceof IntT) {
      return intOf(left.intValue() << shift);
    }
    if (left instanceof UintT) {
      return uintOf(left.intValue() << shift);
    }
    return noSuchOverload();
  }

  private static Val bitShiftRight(Val left, Val right) {
    if (!(right instanceof IntT)) {
      return noSuchOverload();
    }
    long shift = right.intValue();
    if (shift < 0) {
      return newErr("negative offset");
    }
    if (shift >= Long.SIZE) {
      return left instanceof UintT ? uintOf(0) : left instanceof IntT ? intOf(0) : noSuchOverload();
    }
    if (left instanceof IntT) {
      return intOf(left.intValue() >>> shift);
    }
    if (left instanceof UintT) {
      return uintOf(left.intValue() >>> shift);
    }
    return noSuchOverload();
  }

  private static boolean isNumber(Val value) {
    return value instanceof IntT || value instanceof UintT || value instanceof DoubleT;
  }

  private static Val noSuchOverload() {
    return newErr("no such overload");
  }

  @FunctionalInterface
  private interface LongOperator {
    long apply(long left, long right);
  }
}
