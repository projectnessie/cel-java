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

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.noSuchAttributeException;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Err.valOrErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Coster.Cost.OneOne;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.Coster.costOf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.IterableT;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.ListT;
import org.projectnessie.cel.common.types.MapT;
import org.projectnessie.cel.common.types.OptionalT;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Container;
import org.projectnessie.cel.common.types.traits.FieldTester;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Mapper;
import org.projectnessie.cel.common.types.traits.Negater;
import org.projectnessie.cel.common.types.traits.Receiver;
import org.projectnessie.cel.common.types.traits.Sizer;
import org.projectnessie.cel.common.types.traits.Trait;
import org.projectnessie.cel.interpreter.Activation.PartialActivation;
import org.projectnessie.cel.interpreter.Activation.VarActivation;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.AttributeFactory.ConditionalAttribute;
import org.projectnessie.cel.interpreter.AttributeFactory.ConstantQualifier;
import org.projectnessie.cel.interpreter.AttributeFactory.ConstantQualifierEquator;
import org.projectnessie.cel.interpreter.AttributeFactory.Qualifier;
import org.projectnessie.cel.interpreter.Coster.Cost;
import org.projectnessie.cel.interpreter.InterpretableDecorator.EvalObserver;
import org.projectnessie.cel.interpreter.functions.BinaryOp;
import org.projectnessie.cel.interpreter.functions.FunctionOp;
import org.projectnessie.cel.interpreter.functions.QuaternaryOp;
import org.projectnessie.cel.interpreter.functions.QuinaryOp;
import org.projectnessie.cel.interpreter.functions.TernaryOp;
import org.projectnessie.cel.interpreter.functions.UnaryOp;

/**
 * Interpretable can accept a given Activation and produce a value along with an accompanying
 * EvalState which can be used to inspect whether additional data might be necessary to complete the
 * evaluation.
 */
public interface Interpretable {
  /** ID value corresponding to the expression node. */
  long id();

  /** Eval an Activation to produce an output. */
  Val eval(Activation activation);

  /** InterpretableConst interface for tracking whether the Interpretable is a constant value. */
  interface InterpretableConst extends Interpretable {
    /** Value returns the constant value of the instruction. */
    Val value();
  }

  /** InterpretableAttribute interface for tracking whether the Interpretable is an attribute. */
  interface InterpretableAttribute extends Interpretable, Qualifier, Attribute {
    /** Attr returns the Attribute value. */
    Attribute attr();

    /** Adapter returns the type adapter to be used for adapting resolved Attribute values. */
    TypeAdapter adapter();

    /**
     * AddQualifier proxies the Attribute.AddQualifier method.
     *
     * <p>Note, this method may mutate the current attribute state. If the desire is to clone the
     * Attribute, the Attribute should first be copied before adding the qualifier. Attributes are
     * not copyable by default, so this is a capable that would need to be added to the
     * AttributeFactory or specifically to the underlying Attribute implementation.
     */
    @Override
    Attribute addQualifier(Qualifier qualifier);

    /**
     * Qualify replicates the Attribute.Qualify method to permit extension and interception of
     * object qualification.
     */
    @Override
    Object qualify(Activation vars, Object obj);

    /** Resolve returns the value of the Attribute given the current Activation. */
    @Override
    Object resolve(Activation act);
  }

  /**
   * InterpretableCall interface for inspecting Interpretable instructions related to function
   * calls.
   */
  interface InterpretableCall extends Interpretable {

    /**
     * Function returns the function name as it appears in text or mangled operator name as it
     * appears in the operators.go file.
     */
    String function();

    /**
     * OverloadID returns the overload id associated with the function specialization. Overload ids
     * are stable across language boundaries and can be treated as synonymous with a unique function
     * signature.
     */
    String overloadID();

    /**
     * Args returns the normalized arguments to the function overload. For receiver-style functions,
     * the receiver target is arg 0.
     */
    Interpretable[] args();
  }

  // Core Interpretable implementations used during the program planning phase.

  /** evalIdent evaluates a checked top-level variable directly from the activation. */
  final class EvalIdent extends AbstractEval implements Coster {
    private final String name;
    private final TypeAdapter adapter;

    EvalIdent(long id, String name, TypeAdapter adapter) {
      super(id);
      this.name = Objects.requireNonNull(name);
      this.adapter = Objects.requireNonNull(adapter);
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(Activation ctx) {
      if (ctx instanceof PartialActivation partialActivation) {
        for (AttributePattern pattern : partialActivation.unknownAttributePatterns()) {
          if (pattern.variableMatches(name)) {
            return unknownOf(id);
          }
        }
      }

      Object value = ctx.resolve(name);
      if (Activation.ABSENT == value) {
        RuntimeException err = noSuchAttributeException("id: " + id + ", names: [" + name + "]");
        return newErr(err, err.toString());
      }
      return adapter.nativeToValue(value);
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return OneOne;
    }

    @Override
    public String toString() {
      return "EvalIdent{" + "id=" + id + ", name='" + name + '\'' + '}';
    }
  }

  final class EvalTestOnly implements Interpretable, Coster {
    private final long id;
    private final Interpretable op;
    private final StringT field;
    private final FieldType fieldType;

    EvalTestOnly(long id, Interpretable op, StringT field, FieldType fieldType) {
      this.id = id;
      this.op = Objects.requireNonNull(op);
      this.field = Objects.requireNonNull(field);
      this.fieldType = fieldType;
    }

    /** ID implements the Interpretable interface method. */
    @Override
    public long id() {
      return id;
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      // Handle field selection on a proto in the most efficient way possible.
      if (fieldType != null) {
        if (op instanceof InterpretableAttribute opAttr) {
          Object opVal = opAttr.resolve(ctx);
          if (opVal instanceof Val refVal) {
            opVal = refVal.value();
          }
          if (fieldType.isSet.isSet(opVal)) {
            return True;
          }
          return False;
        }
      }

      Val obj = op.eval(ctx);
      if (obj instanceof FieldTester tester) {
        return tester.isSet(field);
      }
      if (obj instanceof Container container) {
        return container.contains(field);
      }
      return valOrErr(obj, "invalid type for field selection.");
    }

    /**
     * Cost provides the heuristic cost of a `has(field)` macro. The cost has at least 1 for
     * determining if the field exists, apart from the cost of accessing the field.
     */
    @Override
    public Cost cost() {
      Cost c = estimateCost(op);
      return c.add(OneOne);
    }

    @Override
    public String toString() {
      return "EvalTestOnly{" + "id=" + id + ", field=" + field + '}';
    }
  }

  /** NewConstValue creates a new constant valued Interpretable. */
  static InterpretableConst newConstValue(long id, Val val) {
    return new EvalConst(id, val);
  }

  abstract class AbstractEval implements Interpretable {
    protected final long id;

    AbstractEval(long id) {
      this.id = id;
    }

    /** ID implements the Interpretable interface method. */
    @Override
    public long id() {
      return id;
    }

    @Override
    public String toString() {
      return "id=" + id;
    }
  }

  abstract class AbstractEvalLhsRhs extends AbstractEval implements Coster {
    protected final Interpretable lhs;
    protected final Interpretable rhs;

    AbstractEvalLhsRhs(long id, Interpretable lhs, Interpretable rhs) {
      super(id);
      this.lhs = Objects.requireNonNull(lhs);
      this.rhs = Objects.requireNonNull(rhs);
    }

    @Override
    public String toString() {
      return "AbstractEvalLhsRhs{" + "id=" + id + ", lhs=" + lhs + ", rhs=" + rhs + '}';
    }
  }

  final class EvalConst extends AbstractEval implements InterpretableConst, Coster {
    private final Val val;

    EvalConst(long id, Val val) {
      super(id);
      this.val = val;
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation activation) {
      return val;
    }

    /** Cost returns zero for a constant valued Interpretable. */
    @Override
    public Cost cost() {
      return Cost.None;
    }

    /** Value implements the InterpretableConst interface method. */
    @Override
    public Val value() {
      return val;
    }

    @Override
    public String toString() {
      return "EvalConst{" + "id=" + id + ", val=" + val + '}';
    }
  }

  final class EvalOr extends AbstractEvalLhsRhs {
    // TODO combine with EvalExhaustiveOr
    EvalOr(long id, Interpretable lhs, Interpretable rhs) {
      super(id, lhs, rhs);
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      // short-circuit lhs.
      Val lVal = lhs.eval(ctx);
      if (lVal == True) {
        return True;
      }
      // short-circuit on rhs.
      Val rVal = rhs.eval(ctx);
      if (rVal == True) {
        return True;
      }
      // return if both sides are bool false.
      if (lVal == False && rVal == False) {
        return False;
      }
      // TODO: return both values as a set if both are unknown or error.
      // prefer left unknown to right unknown.
      if (isUnknown(lVal)) {
        return lVal;
      }
      if (isUnknown(rVal)) {
        return rVal;
      }
      // If the left-hand side is non-boolean return it as the error.
      if (isError(lVal)) {
        return lVal;
      }
      return noSuchOverload(lVal, Operator.LogicalOr.id, rVal);
    }

    /**
     * Cost implements the Coster interface method. The minimum possible cost incurs when the
     * left-hand side expr is sufficient in determining the evaluation result.
     */
    @Override
    public Cost cost() {
      return calShortCircuitBinaryOpsCost(lhs, rhs);
    }

    @Override
    public String toString() {
      return "EvalOr{" + "id=" + id + ", lhs=" + lhs + ", rhs=" + rhs + '}';
    }
  }

  final class EvalAnd extends AbstractEvalLhsRhs {
    // TODO combine with EvalExhaustiveAnd
    EvalAnd(long id, Interpretable lhs, Interpretable rhs) {
      super(id, lhs, rhs);
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      // short-circuit lhs.
      Val lVal = lhs.eval(ctx);
      if (lVal == False) {
        return False;
      }
      // short-circuit on rhs.
      Val rVal = rhs.eval(ctx);
      if (rVal == False) {
        return False;
      }
      // return if both sides are bool true.
      if (lVal == True && rVal == True) {
        return True;
      }
      // TODO: return both values as a set if both are unknown or error.
      // prefer left unknown to right unknown.
      if (isUnknown(lVal)) {
        return lVal;
      }
      if (isUnknown(rVal)) {
        return rVal;
      }
      // If the left-hand side is non-boolean return it as the error.
      if (isError(lVal)) {
        return lVal;
      }
      return noSuchOverload(lVal, Operator.LogicalAnd.id, rVal);
    }

    /**
     * Cost implements the Coster interface method. The minimum possible cost incurs when the
     * left-hand side expr is sufficient in determining the evaluation result.
     */
    @Override
    public Cost cost() {
      return calShortCircuitBinaryOpsCost(lhs, rhs);
    }

    @Override
    public String toString() {
      return "EvalAnd{" + "id=" + id + ", lhs=" + lhs + ", rhs=" + rhs + '}';
    }
  }

  static Cost calShortCircuitBinaryOpsCost(Interpretable lhs, Interpretable rhs) {
    Cost l = estimateCost(lhs);
    Cost r = estimateCost(rhs);
    return costOf(l.min, l.max + r.max + 1);
  }

  final class EvalEq extends AbstractEvalLhsRhs implements InterpretableCall {
    EvalEq(long id, Interpretable lhs, Interpretable rhs) {
      super(id, lhs, rhs);
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val lVal = lhs.eval(ctx);
      Val rVal = rhs.eval(ctx);
      // Early return if any argument to the function is unknown or error.
      if (isUnknownOrError(lVal)) {
        return lVal;
      }
      if (isUnknownOrError(rVal)) {
        return rVal;
      }
      return lVal.equal(rVal);
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return calExhaustiveBinaryOpsCost(lhs, rhs);
    }

    /** Function implements the InterpretableCall interface method. */
    @Override
    public String function() {
      return Operator.Equals.id;
    }

    /** OverloadID implements the InterpretableCall interface method. */
    @Override
    public String overloadID() {
      return Overloads.Equals;
    }

    /** Args implements the InterpretableCall interface method. */
    @Override
    public Interpretable[] args() {
      return new Interpretable[] {lhs, rhs};
    }

    @Override
    public String toString() {
      return "EvalEq{" + "id=" + id + ", lhs=" + lhs + ", rhs=" + rhs + '}';
    }
  }

  final class EvalNe extends AbstractEvalLhsRhs implements InterpretableCall {
    EvalNe(long id, Interpretable lhs, Interpretable rhs) {
      super(id, lhs, rhs);
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val lVal = lhs.eval(ctx);
      Val rVal = rhs.eval(ctx);
      // Early return if any argument to the function is unknown or error.
      if (isUnknownOrError(lVal)) {
        return lVal;
      }
      if (isUnknownOrError(rVal)) {
        return rVal;
      }
      Val eqVal = lVal.equal(rVal);
      return switch (eqVal.type().typeEnum()) {
        case Err -> eqVal;
        case Bool -> ((Negater) eqVal).negate();
        default -> noSuchOverload(lVal, Operator.NotEquals.id, rVal);
      };
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return calExhaustiveBinaryOpsCost(lhs, rhs);
    }

    /** Function implements the InterpretableCall interface method. */
    @Override
    public String function() {
      return Operator.NotEquals.id;
    }

    /** OverloadID implements the InterpretableCall interface method. */
    @Override
    public String overloadID() {
      return Overloads.NotEquals;
    }

    /** Args implements the InterpretableCall interface method. */
    @Override
    public Interpretable[] args() {
      return new Interpretable[] {lhs, rhs};
    }

    @Override
    public String toString() {
      return "EvalNe{" + "id=" + id + ", lhs=" + lhs + ", rhs=" + rhs + '}';
    }
  }

  final class EvalZeroArity extends AbstractEval implements InterpretableCall, Coster {
    private final String function;
    private final String overload;
    private final FunctionOp impl;

    EvalZeroArity(long id, String function, String overload, FunctionOp impl) {
      super(id);
      this.function = Objects.requireNonNull(function);
      this.overload = Objects.requireNonNull(overload);
      this.impl = impl;
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation activation) {
      return impl.invoke();
    }

    /** Cost returns 1 representing the heuristic cost of the function. */
    @Override
    public Cost cost() {
      return Cost.OneOne;
    }

    /** Function implements the InterpretableCall interface method. */
    @Override
    public String function() {
      return function;
    }

    /** OverloadID implements the InterpretableCall interface method. */
    @Override
    public String overloadID() {
      return overload;
    }

    /** Args returns the argument to the unary function. */
    @Override
    public Interpretable[] args() {
      return new Interpretable[0];
    }

    @Override
    public String toString() {
      return "EvalZeroArity{"
          + "id="
          + id
          + ", function='"
          + function
          + '\''
          + ", overload='"
          + overload
          + '\''
          + ", impl="
          + impl
          + '}';
    }
  }

  final class EvalUnary extends AbstractEval implements InterpretableCall, Coster {
    private final String function;
    private final String overload;
    private final Interpretable arg;
    private final Trait trait;
    private final UnaryOp impl;

    EvalUnary(
        long id, String function, String overload, Interpretable arg, Trait trait, UnaryOp impl) {
      super(id);
      this.function = Objects.requireNonNull(function);
      this.overload = Objects.requireNonNull(overload);
      this.arg = Objects.requireNonNull(arg);
      this.trait = trait;
      this.impl = impl;
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val argVal = arg.eval(ctx);
      // Early return if the argument to the function is unknown or error.
      if (isUnknownOrError(argVal)) {
        return argVal;
      }
      // If the implementation is bound and the argument value has the right traits required to
      // invoke it, then call the implementation.
      if (impl != null && (trait == null || argVal.type().hasTrait(trait))) {
        return impl.invoke(argVal);
      }
      // Otherwise, if the argument is a ReceiverType attempt to invoke the receiver method on the
      // operand (arg0).
      if (argVal.type().hasTrait(Trait.ReceiverType)) {
        return ((Receiver) argVal).receive(function, overload);
      }
      return noSuchOverload(argVal, function, overload, new Val[] {});
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      Cost c = estimateCost(arg);
      return Cost.OneOne.add(c); // add cost for function
    }

    /** Function implements the InterpretableCall interface method. */
    @Override
    public String function() {
      return function;
    }

    /** OverloadID implements the InterpretableCall interface method. */
    @Override
    public String overloadID() {
      return overload;
    }

    /** Args returns the argument to the unary function. */
    @Override
    public Interpretable[] args() {
      return new Interpretable[] {arg};
    }

    @Override
    public String toString() {
      return "EvalUnary{"
          + "id="
          + id
          + ", function='"
          + function
          + '\''
          + ", overload='"
          + overload
          + '\''
          + ", arg="
          + arg
          + ", trait="
          + trait
          + ", impl="
          + impl
          + '}';
    }
  }

  final class EvalBinary extends AbstractEvalLhsRhs implements InterpretableCall {
    private final String function;
    private final String overload;
    private final Trait trait;
    private final BinaryOp impl;

    EvalBinary(
        long id,
        String function,
        String overload,
        Interpretable lhs,
        Interpretable rhs,
        Trait trait,
        BinaryOp impl) {
      super(id, lhs, rhs);
      this.function = Objects.requireNonNull(function);
      this.overload = Objects.requireNonNull(overload);
      this.trait = trait;
      this.impl = impl;
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val lVal = lhs.eval(ctx);
      Val rVal = rhs.eval(ctx);
      // Early return if any argument to the function is unknown or error.
      if (isUnknownOrError(lVal)) {
        return lVal;
      }
      if (isUnknownOrError(rVal)) {
        return rVal;
      }
      // If the implementation is bound and the argument value has the right traits required to
      // invoke it, then call the implementation.
      if (impl != null && (trait == null || lVal.type().hasTrait(trait))) {
        return impl.invoke(lVal, rVal);
      }
      // Otherwise, if the argument is a ReceiverType attempt to invoke the receiver method on the
      // operand (arg0).
      if (lVal.type().hasTrait(Trait.ReceiverType)) {
        return ((Receiver) lVal).receive(function, overload, rVal);
      }
      return noSuchOverload(lVal, function, overload, new Val[] {rVal});
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return calExhaustiveBinaryOpsCost(lhs, rhs);
    }

    /** Function implements the InterpretableCall interface method. */
    @Override
    public String function() {
      return function;
    }

    /** OverloadID implements the InterpretableCall interface method. */
    @Override
    public String overloadID() {
      return overload;
    }

    /** Args returns the argument to the unary function. */
    @Override
    public Interpretable[] args() {
      return new Interpretable[] {lhs, rhs};
    }

    @Override
    public String toString() {
      return "EvalBinary{"
          + "id="
          + id
          + ", lhs="
          + lhs
          + ", rhs="
          + rhs
          + ", function='"
          + function
          + '\''
          + ", overload='"
          + overload
          + '\''
          + ", trait="
          + trait
          + ", impl="
          + impl
          + '}';
    }
  }

  final class EvalTernary extends AbstractEval implements Coster, InterpretableCall {
    private final String function;
    private final String overload;
    private final Interpretable first;
    private final Interpretable second;
    private final Interpretable third;
    private final Trait trait;
    private final TernaryOp impl;

    EvalTernary(
        long id,
        String function,
        String overload,
        Interpretable first,
        Interpretable second,
        Interpretable third,
        Trait trait,
        TernaryOp impl) {
      super(id);
      this.function = Objects.requireNonNull(function);
      this.overload = Objects.requireNonNull(overload);
      this.first = Objects.requireNonNull(first);
      this.second = Objects.requireNonNull(second);
      this.third = Objects.requireNonNull(third);
      this.trait = trait;
      this.impl = Objects.requireNonNull(impl);
    }

    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val firstVal = first.eval(ctx);
      if (isUnknownOrError(firstVal)) {
        return firstVal;
      }
      Val secondVal = second.eval(ctx);
      if (isUnknownOrError(secondVal)) {
        return secondVal;
      }
      Val thirdVal = third.eval(ctx);
      if (isUnknownOrError(thirdVal)) {
        return thirdVal;
      }
      if (trait == null || firstVal.type().hasTrait(trait)) {
        return impl.invoke(firstVal, secondVal, thirdVal);
      }
      if (firstVal.type().hasTrait(Trait.ReceiverType)) {
        return ((Receiver) firstVal).receive(function, overload, secondVal, thirdVal);
      }
      return noSuchOverload(
          firstVal, function, overload, new Val[] {firstVal, secondVal, thirdVal});
    }

    @Override
    public Cost cost() {
      return estimateCost(first).add(estimateCost(second)).add(estimateCost(third)).add(OneOne);
    }

    @Override
    public String function() {
      return function;
    }

    @Override
    public String overloadID() {
      return overload;
    }

    @Override
    public Interpretable[] args() {
      return new Interpretable[] {first, second, third};
    }

    @Override
    public String toString() {
      return "EvalTernary{"
          + "id="
          + id
          + ", first="
          + first
          + ", second="
          + second
          + ", third="
          + third
          + ", function='"
          + function
          + '\''
          + ", overload='"
          + overload
          + '\''
          + ", trait="
          + trait
          + ", impl="
          + impl
          + '}';
    }
  }

  final class EvalQuaternary extends AbstractEval implements Coster, InterpretableCall {
    private final String function;
    private final String overload;
    private final Interpretable first;
    private final Interpretable second;
    private final Interpretable third;
    private final Interpretable fourth;
    private final Trait trait;
    private final QuaternaryOp impl;

    EvalQuaternary(
        long id,
        String function,
        String overload,
        Interpretable first,
        Interpretable second,
        Interpretable third,
        Interpretable fourth,
        Trait trait,
        QuaternaryOp impl) {
      super(id);
      this.function = Objects.requireNonNull(function);
      this.overload = Objects.requireNonNull(overload);
      this.first = Objects.requireNonNull(first);
      this.second = Objects.requireNonNull(second);
      this.third = Objects.requireNonNull(third);
      this.fourth = Objects.requireNonNull(fourth);
      this.trait = trait;
      this.impl = Objects.requireNonNull(impl);
    }

    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val firstVal = first.eval(ctx);
      if (isUnknownOrError(firstVal)) {
        return firstVal;
      }
      Val secondVal = second.eval(ctx);
      if (isUnknownOrError(secondVal)) {
        return secondVal;
      }
      Val thirdVal = third.eval(ctx);
      if (isUnknownOrError(thirdVal)) {
        return thirdVal;
      }
      Val fourthVal = fourth.eval(ctx);
      if (isUnknownOrError(fourthVal)) {
        return fourthVal;
      }
      if (trait == null || firstVal.type().hasTrait(trait)) {
        return impl.invoke(firstVal, secondVal, thirdVal, fourthVal);
      }
      if (firstVal.type().hasTrait(Trait.ReceiverType)) {
        return ((Receiver) firstVal).receive(function, overload, secondVal, thirdVal, fourthVal);
      }
      return noSuchOverload(
          firstVal, function, overload, new Val[] {firstVal, secondVal, thirdVal, fourthVal});
    }

    @Override
    public Cost cost() {
      return estimateCost(first)
          .add(estimateCost(second))
          .add(estimateCost(third))
          .add(estimateCost(fourth))
          .add(OneOne);
    }

    @Override
    public String function() {
      return function;
    }

    @Override
    public String overloadID() {
      return overload;
    }

    @Override
    public Interpretable[] args() {
      return new Interpretable[] {first, second, third, fourth};
    }

    @Override
    public String toString() {
      return "EvalQuaternary{"
          + "id="
          + id
          + ", first="
          + first
          + ", second="
          + second
          + ", third="
          + third
          + ", fourth="
          + fourth
          + ", function='"
          + function
          + '\''
          + ", overload='"
          + overload
          + '\''
          + ", trait="
          + trait
          + ", impl="
          + impl
          + '}';
    }
  }

  final class EvalQuinary extends AbstractEval implements Coster, InterpretableCall {
    private final String function;
    private final String overload;
    private final Interpretable first;
    private final Interpretable second;
    private final Interpretable third;
    private final Interpretable fourth;
    private final Interpretable fifth;
    private final Trait trait;
    private final QuinaryOp impl;

    EvalQuinary(
        long id,
        String function,
        String overload,
        Interpretable first,
        Interpretable second,
        Interpretable third,
        Interpretable fourth,
        Interpretable fifth,
        Trait trait,
        QuinaryOp impl) {
      super(id);
      this.function = Objects.requireNonNull(function);
      this.overload = Objects.requireNonNull(overload);
      this.first = Objects.requireNonNull(first);
      this.second = Objects.requireNonNull(second);
      this.third = Objects.requireNonNull(third);
      this.fourth = Objects.requireNonNull(fourth);
      this.fifth = Objects.requireNonNull(fifth);
      this.trait = trait;
      this.impl = Objects.requireNonNull(impl);
    }

    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val firstVal = first.eval(ctx);
      if (isUnknownOrError(firstVal)) {
        return firstVal;
      }
      Val secondVal = second.eval(ctx);
      if (isUnknownOrError(secondVal)) {
        return secondVal;
      }
      Val thirdVal = third.eval(ctx);
      if (isUnknownOrError(thirdVal)) {
        return thirdVal;
      }
      Val fourthVal = fourth.eval(ctx);
      if (isUnknownOrError(fourthVal)) {
        return fourthVal;
      }
      Val fifthVal = fifth.eval(ctx);
      if (isUnknownOrError(fifthVal)) {
        return fifthVal;
      }
      if (trait == null || firstVal.type().hasTrait(trait)) {
        return impl.invoke(firstVal, secondVal, thirdVal, fourthVal, fifthVal);
      }
      if (firstVal.type().hasTrait(Trait.ReceiverType)) {
        return ((Receiver) firstVal)
            .receive(function, overload, secondVal, thirdVal, fourthVal, fifthVal);
      }
      return noSuchOverload(
          firstVal,
          function,
          overload,
          new Val[] {firstVal, secondVal, thirdVal, fourthVal, fifthVal});
    }

    @Override
    public Cost cost() {
      return estimateCost(first)
          .add(estimateCost(second))
          .add(estimateCost(third))
          .add(estimateCost(fourth))
          .add(estimateCost(fifth))
          .add(OneOne);
    }

    @Override
    public String function() {
      return function;
    }

    @Override
    public String overloadID() {
      return overload;
    }

    @Override
    public Interpretable[] args() {
      return new Interpretable[] {first, second, third, fourth, fifth};
    }

    @Override
    public String toString() {
      return "EvalQuinary{"
          + "id="
          + id
          + ", first="
          + first
          + ", second="
          + second
          + ", third="
          + third
          + ", fourth="
          + fourth
          + ", fifth="
          + fifth
          + ", function='"
          + function
          + '\''
          + ", overload='"
          + overload
          + '\''
          + ", trait="
          + trait
          + ", impl="
          + impl
          + '}';
    }
  }

  final class EvalVarArgs extends AbstractEval implements Coster, InterpretableCall {
    private final String function;
    private final String overload;
    private final Interpretable[] args;
    private final Trait trait;
    private final FunctionOp impl;

    public EvalVarArgs(
        long id,
        String function,
        String overload,
        Interpretable[] args,
        Trait trait,
        FunctionOp impl) {
      super(id);
      this.function = Objects.requireNonNull(function);
      this.overload = Objects.requireNonNull(overload);
      this.args = Objects.requireNonNull(args);
      this.trait = trait;
      this.impl = impl;
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val[] argVals = new Val[args.length];
      // Early return if any argument to the function is unknown or error.
      for (int i = 0; i < args.length; i++) {
        Interpretable arg = args[i];
        argVals[i] = arg.eval(ctx);
        if (isUnknownOrError(argVals[i])) {
          return argVals[i];
        }
      }
      // If the implementation is bound and the argument value has the right traits required to
      // invoke it, then call the implementation.
      Val arg0 = argVals[0];
      if (impl != null && (trait == null || arg0.type().hasTrait(trait))) {
        return impl.invoke(argVals);
      }
      // Otherwise, if the argument is a ReceiverType attempt to invoke the receiver method on the
      // operand (arg0).
      if (arg0.type().hasTrait(Trait.ReceiverType)) {
        return receiveVarArgs((Receiver) arg0, function, overload, argVals);
      }
      return noSuchOverload(arg0, function, overload, argVals);
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      Cost c = sumOfCost(args);
      return c.add(OneOne); // add cost for function
    }

    /** Function implements the InterpretableCall interface method. */
    @Override
    public String function() {
      return function;
    }

    /** OverloadID implements the InterpretableCall interface method. */
    @Override
    public String overloadID() {
      return overload;
    }

    /** Args returns the argument to the unary function. */
    @Override
    public Interpretable[] args() {
      return args;
    }

    @Override
    public String toString() {
      return "EvalVarArgs{"
          + "id="
          + id
          + ", function='"
          + function
          + '\''
          + ", overload='"
          + overload
          + '\''
          + ", args="
          + Arrays.toString(args)
          + ", trait="
          + trait
          + ", impl="
          + impl
          + '}';
    }
  }

  final class EvalList extends AbstractEval implements Coster {
    final Interpretable[] elems;
    final boolean[] optionalIndices;
    private final TypeAdapter adapter;

    EvalList(long id, Interpretable[] elems, TypeAdapter adapter) {
      this(id, elems, new boolean[elems.length], adapter);
    }

    EvalList(long id, Interpretable[] elems, boolean[] optionalIndices, TypeAdapter adapter) {
      super(id);
      this.elems = elems;
      this.optionalIndices = optionalIndices;
      this.adapter = adapter;
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      List<Val> elemVals = new ArrayList<>(elems.length);
      // If any argument is unknown or error early terminate.
      for (int i = 0; i < elems.length; i++) {
        Interpretable elem = elems[i];
        Val elemVal = elem.eval(ctx);
        if (isUnknownOrError(elemVal)) {
          return elemVal;
        }
        if (optionalIndices[i]) {
          if (!(elemVal instanceof OptionalT optional)) {
            return newErr("optional list element is not optional");
          }
          if (!optional.hasValue()) {
            continue;
          }
          elemVal = optional.getValue();
        }
        elemVals.add(elemVal);
      }
      return adapter.nativeToValue(elemVals.toArray(Val[]::new));
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return sumOfCost(elems);
    }

    @Override
    public String toString() {
      return "EvalList{" + "id=" + id + ", elems=" + Arrays.toString(elems) + '}';
    }
  }

  final class EvalMap extends AbstractEval implements Coster {
    final Interpretable[] keys;
    final Interpretable[] vals;
    final boolean[] optionalEntries;
    private final TypeAdapter adapter;

    EvalMap(long id, Interpretable[] keys, Interpretable[] vals, TypeAdapter adapter) {
      this(id, keys, vals, new boolean[keys.length], adapter);
    }

    EvalMap(
        long id,
        Interpretable[] keys,
        Interpretable[] vals,
        boolean[] optionalEntries,
        TypeAdapter adapter) {
      super(id);
      this.keys = keys;
      this.vals = vals;
      this.optionalEntries = optionalEntries;
      this.adapter = adapter;
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Map<Val, Val> entries = new HashMap<>(keys.length * 4 / 3 + 1);
      // If any argument is unknown or error early terminate.
      for (int i = 0; i < keys.length; i++) {
        Interpretable key = keys[i];
        Val keyVal = key.eval(ctx);
        if (isUnknownOrError(keyVal)) {
          return keyVal;
        }
        if (!MapT.isSupportedLiteralKeyType(keyVal)) {
          return newErr("unsupported key type");
        }
        Val valVal = vals[i].eval(ctx);
        if (isUnknownOrError(valVal)) {
          return valVal;
        }
        if (optionalEntries[i]) {
          if (!(valVal instanceof OptionalT optional)) {
            return newErr("optional map entry is not optional");
          }
          if (!optional.hasValue()) {
            continue;
          }
          valVal = optional.getValue();
        }
        if (entries.putIfAbsent(keyVal, valVal) != null) {
          // Prevent duplicate keys, error out.
          return newErr("Failed with repeated key");
        }
      }
      return MapT.newWrappedMap(adapter, entries);
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      Cost k = sumOfCost(keys);
      Cost v = sumOfCost(vals);
      return k.add(v);
    }

    @Override
    public String toString() {
      return "EvalMap{"
          + "id="
          + id
          + ", keys="
          + Arrays.toString(keys)
          + ", vals="
          + Arrays.toString(vals)
          + '}';
    }
  }

  final class EvalObj extends AbstractEval implements Coster {
    private final String typeName;
    private final String[] fields;
    private final Interpretable[] vals;
    private final boolean[] optionalEntries;
    private final TypeProvider provider;

    EvalObj(
        long id, String typeName, String[] fields, Interpretable[] vals, TypeProvider provider) {
      this(id, typeName, fields, vals, new boolean[fields.length], provider);
    }

    EvalObj(
        long id,
        String typeName,
        String[] fields,
        Interpretable[] vals,
        boolean[] optionalEntries,
        TypeProvider provider) {
      super(id);
      this.typeName = Objects.requireNonNull(typeName);
      this.fields = Objects.requireNonNull(fields);
      this.vals = Objects.requireNonNull(vals);
      this.optionalEntries = optionalEntries;
      this.provider = Objects.requireNonNull(provider);
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Map<String, Val> fieldVals = new HashMap<>();
      // If any argument is unknown or error early terminate.
      for (int i = 0; i < fields.length; i++) {
        String field = fields[i];
        Val val = vals[i].eval(ctx);
        if (isUnknownOrError(val)) {
          return val;
        }
        if (optionalEntries[i]) {
          if (!(val instanceof OptionalT optional)) {
            return newErr("optional message field is not optional");
          }
          if (!optional.hasValue()) {
            continue;
          }
          val = optional.getValue();
        }
        fieldVals.put(field, val);
      }
      return provider.newValue(typeName, fieldVals);
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return sumOfCost(vals);
    }

    @Override
    public String toString() {
      return "EvalObj{"
          + "id="
          + id
          + ", typeName='"
          + typeName
          + '\''
          + ", fields="
          + Arrays.toString(fields)
          + ", vals="
          + Arrays.toString(vals)
          + ", provider="
          + provider
          + '}';
    }
  }

  static Cost sumOfCost(Interpretable[] interps) {
    long min = 0L;
    long max = 0L;
    for (Interpretable in : interps) {
      Cost t = estimateCost(in);
      min += t.min;
      max += t.max;
    }
    return costOf(min, max);
  }

  final class EvalFold extends AbstractEval implements Coster {
    // TODO combine with EvalExhaustiveFold
    final String accuVar;
    final String iterVar;
    final String iterVar2;
    final Interpretable iterRange;
    final Interpretable accu;
    final Interpretable cond;
    final Interpretable step;
    final Interpretable result;

    EvalFold(
        long id,
        String accuVar,
        Interpretable accu,
        String iterVar,
        String iterVar2,
        Interpretable iterRange,
        Interpretable cond,
        Interpretable step,
        Interpretable result) {
      super(id);
      this.accuVar = accuVar;
      this.iterVar = iterVar;
      this.iterVar2 = iterVar2;
      this.iterRange = iterRange;
      this.accu = accu;
      this.cond = cond;
      this.step = step;
      this.result = result;
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val foldRange = iterRange.eval(ctx);
      if (!foldRange.type().hasTrait(Trait.IterableType)) {
        return valOrErr(
            foldRange, "got '%s', expected iterable type", foldRange.getClass().getName());
      }
      // Configure the fold activation with the accumulator initial value.
      VarActivation accuCtx = new VarActivation();
      accuCtx.parent = ctx;
      accuCtx.name = accuVar;
      accuCtx.val = accu.eval(ctx);
      VarActivation iterCtx = new VarActivation();
      iterCtx.parent = accuCtx;
      iterCtx.name = iterVar;
      VarActivation iterCtx2 = null;
      if (!iterVar2.isEmpty()) {
        iterCtx2 = new VarActivation();
        iterCtx2.parent = iterCtx;
        iterCtx2.name = iterVar2;
      }
      IteratorT it = ((IterableT) foldRange).iterator();
      long index = 0L;
      var isLister = foldRange instanceof Lister;
      var mapper = (foldRange instanceof Mapper m) ? m : null;
      while (it.hasNext() == True) {
        // Modify the iter var in the fold activation.
        Val next = it.next();
        Activation loopCtx = iterCtx;
        if (iterCtx2 != null) {
          if (isLister) {
            iterCtx.val = intOf(index);
            iterCtx2.val = next;
          } else if (mapper != null) {
            iterCtx.val = next;
            iterCtx2.val = mapper.get(next);
          } else {
            return valOrErr(
                foldRange, "got '%s', expected list or map type", foldRange.getClass().getName());
          }
          loopCtx = iterCtx2;
        } else {
          iterCtx.val = next;
        }
        index++;

        // Evaluate the condition, terminate the loop if false.
        Val c = cond.eval(loopCtx);
        if (c == False) {
          break;
        }

        // Evalute the evaluation step into accu var.
        accuCtx.val = step.eval(loopCtx);
      }
      // Compute the result.
      return result.eval(accuCtx);
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      // Compute the cost for evaluating iterRange.
      Cost i = estimateCost(iterRange);

      // Compute the size of iterRange. If the size depends on the input, return the maximum
      // possible
      // cost range.
      Val foldRange = iterRange.eval(emptyActivation());
      if (!foldRange.type().hasTrait(Trait.IterableType)) {
        return Cost.Unknown;
      }
      long rangeCnt;
      if (foldRange instanceof Sizer sizer) {
        rangeCnt = sizer.nativeSize();
      } else {
        rangeCnt = 0L;
        IteratorT it = ((IterableT) foldRange).iterator();
        while (it.hasNext() == True) {
          it.next();
          rangeCnt++;
        }
      }
      Cost a = estimateCost(accu);
      Cost c = estimateCost(cond);
      Cost s = estimateCost(step);
      Cost r = estimateCost(result);

      // The cond and step costs are multiplied by size(iterRange). The minimum possible cost incurs
      // when the evaluation result can be determined by the first iteration.
      return i.add(a)
          .add(r)
          .add(costOf(c.min, c.max * rangeCnt))
          .add(costOf(s.min, s.max * rangeCnt));
    }

    @Override
    public String toString() {
      return "EvalFold{"
          + "id="
          + id
          + ", accuVar='"
          + accuVar
          + '\''
          + ", iterVar='"
          + iterVar
          + '\''
          + ", iterVar2='"
          + iterVar2
          + '\''
          + ", iterRange="
          + iterRange
          + ", accu="
          + accu
          + ", cond="
          + cond
          + ", step="
          + step
          + ", result="
          + result
          + '}';
    }
  }

  final class EvalListFold extends AbstractEval implements Coster {
    final String iterVar;
    final String iterVar2;
    final Interpretable iterRange;
    final Interpretable filter;
    final Interpretable transform;
    private final TypeAdapter adapter;

    EvalListFold(
        long id,
        String iterVar,
        String iterVar2,
        Interpretable iterRange,
        Interpretable filter,
        Interpretable transform,
        TypeAdapter adapter) {
      super(id);
      this.iterVar = iterVar;
      this.iterVar2 = iterVar2;
      this.iterRange = iterRange;
      this.filter = filter;
      this.transform = transform;
      this.adapter = adapter;
    }

    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val foldRange = iterRange.eval(ctx);
      if (!foldRange.type().hasTrait(Trait.IterableType)) {
        return valOrErr(
            foldRange, "got '%s', expected iterable type", foldRange.getClass().getName());
      }

      VarActivation iterCtx = new VarActivation();
      iterCtx.parent = ctx;
      iterCtx.name = iterVar;
      VarActivation iterCtx2 = null;
      if (!iterVar2.isEmpty()) {
        iterCtx2 = new VarActivation();
        iterCtx2.parent = iterCtx;
        iterCtx2.name = iterVar2;
      }
      List<Val> values = new ArrayList<>(listCapacity(foldRange));
      IteratorT it = ((IterableT) foldRange).iterator();
      long index = 0L;
      var isLister = foldRange instanceof Lister;
      var mapper = (foldRange instanceof Mapper m) ? m : null;
      while (it.hasNext() == True) {
        Val next = it.next();
        Activation loopCtx = iterCtx;
        if (iterCtx2 != null) {
          if (isLister) {
            iterCtx.val = intOf(index);
            iterCtx2.val = next;
          } else if (mapper != null) {
            iterCtx.val = next;
            iterCtx2.val = mapper.get(next);
          } else {
            return valOrErr(
                foldRange, "got '%s', expected list or map type", foldRange.getClass().getName());
          }
          loopCtx = iterCtx2;
        } else {
          iterCtx.val = next;
        }
        index++;

        if (filter != null) {
          Val include = filter.eval(loopCtx);
          if (include == False) {
            continue;
          }
          if (include != True) {
            return noSuchOverload(null, Operator.Conditional.id, include);
          }
        }

        Val value = transform.eval(loopCtx);
        if (isUnknownOrError(value)) {
          return value;
        }
        values.add(value);
      }
      return ListT.newValArrayList(adapter, values.toArray(new Val[0]));
    }

    private int listCapacity(Val foldRange) {
      if (foldRange.type().hasTrait(Trait.SizerType)) {
        long size = ((Sizer) foldRange).nativeSize();
        if (size > 0 && size <= Integer.MAX_VALUE) {
          return (int) size;
        }
      }
      return 0;
    }

    @Override
    public Cost cost() {
      Cost range = estimateCost(iterRange);
      Cost result = estimateCost(transform);
      if (filter != null) {
        result = result.add(estimateCost(filter));
      }
      Val foldRange = iterRange.eval(emptyActivation());
      if (!foldRange.type().hasTrait(Trait.IterableType)) {
        return Cost.Unknown;
      }
      long rangeCnt;
      if (foldRange instanceof Sizer sizer) {
        rangeCnt = sizer.nativeSize();
      } else {
        rangeCnt = 0L;
        IteratorT it = ((IterableT) foldRange).iterator();
        while (it.hasNext() == True) {
          it.next();
          rangeCnt++;
        }
      }
      return range.add(result.multiply(rangeCnt));
    }

    @Override
    public String toString() {
      return "EvalListFold{"
          + "id="
          + id
          + ", iterVar='"
          + iterVar
          + '\''
          + ", iterVar2='"
          + iterVar2
          + '\''
          + ", iterRange="
          + iterRange
          + ", filter="
          + filter
          + ", transform="
          + transform
          + '}';
    }
  }

  final class EvalMapFold extends AbstractEval implements Coster {
    final String iterVar;
    final String iterVar2;
    final Interpretable iterRange;
    final Interpretable filter;
    final Interpretable transform;
    private final TypeAdapter adapter;

    EvalMapFold(
        long id,
        String iterVar,
        String iterVar2,
        Interpretable iterRange,
        Interpretable filter,
        Interpretable transform,
        TypeAdapter adapter) {
      super(id);
      this.iterVar = iterVar;
      this.iterVar2 = iterVar2;
      this.iterRange = iterRange;
      this.filter = filter;
      this.transform = transform;
      this.adapter = adapter;
    }

    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val foldRange = iterRange.eval(ctx);
      if (!foldRange.type().hasTrait(Trait.IterableType)) {
        return valOrErr(
            foldRange, "got '%s', expected iterable type", foldRange.getClass().getName());
      }

      VarActivation iterCtx = new VarActivation();
      iterCtx.parent = ctx;
      iterCtx.name = iterVar;
      VarActivation iterCtx2 = null;
      if (!iterVar2.isEmpty()) {
        iterCtx2 = new VarActivation();
        iterCtx2.parent = iterCtx;
        iterCtx2.name = iterVar2;
      }
      Map<Val, Val> values = new HashMap<>(mapCapacity(foldRange));
      IteratorT it = ((IterableT) foldRange).iterator();
      long index = 0L;
      var isLister = foldRange instanceof Lister;
      var mapper = (foldRange instanceof Mapper m) ? m : null;
      while (it.hasNext() == True) {
        Val next = it.next();
        Val key;
        Activation loopCtx = iterCtx;
        if (iterCtx2 != null) {
          if (isLister) {
            key = intOf(index);
            iterCtx.val = key;
            iterCtx2.val = next;
          } else if (mapper != null) {
            key = next;
            iterCtx.val = key;
            iterCtx2.val = mapper.get(next);
          } else {
            return valOrErr(
                foldRange, "got '%s', expected list or map type", foldRange.getClass().getName());
          }
          loopCtx = iterCtx2;
        } else {
          key = next;
          iterCtx.val = next;
        }
        index++;

        if (filter != null) {
          Val include = filter.eval(loopCtx);
          if (include == False) {
            continue;
          }
          if (include != True) {
            return noSuchOverload(null, Operator.Conditional.id, include);
          }
        }

        Val value = transform.eval(loopCtx);
        if (isUnknownOrError(value)) {
          return value;
        }
        values.put(key, value);
      }
      return MapT.newWrappedMap(adapter, values);
    }

    private int mapCapacity(Val foldRange) {
      if (foldRange.type().hasTrait(Trait.SizerType)) {
        long size = ((Sizer) foldRange).nativeSize();
        if (size > 0 && size <= Integer.MAX_VALUE) {
          long capacity = size * 4 / 3 + 1;
          return capacity <= Integer.MAX_VALUE ? (int) capacity : Integer.MAX_VALUE;
        }
      }
      return 0;
    }

    @Override
    public Cost cost() {
      Cost range = estimateCost(iterRange);
      Cost result = estimateCost(transform);
      if (filter != null) {
        result = result.add(estimateCost(filter));
      }
      Val foldRange = iterRange.eval(emptyActivation());
      if (!foldRange.type().hasTrait(Trait.IterableType)) {
        return Cost.Unknown;
      }
      long rangeCnt;
      if (foldRange instanceof Sizer sizer) {
        rangeCnt = sizer.nativeSize();
      } else {
        rangeCnt = 0L;
        IteratorT it = ((IterableT) foldRange).iterator();
        while (it.hasNext() == True) {
          it.next();
          rangeCnt++;
        }
      }
      return range.add(result.multiply(rangeCnt));
    }

    @Override
    public String toString() {
      return "EvalMapFold{"
          + "id="
          + id
          + ", iterVar='"
          + iterVar
          + '\''
          + ", iterVar2='"
          + iterVar2
          + '\''
          + ", iterRange="
          + iterRange
          + ", filter="
          + filter
          + ", transform="
          + transform
          + '}';
    }
  }

  // Optional Intepretable implementations that specialize, subsume, or extend the core evaluation
  // plan via decorators.

  /**
   * evalSetMembership is an Interpretable implementation which tests whether an input value exists
   * within the set of map keys used to model a set.
   */
  final class EvalSetMembership extends AbstractEval implements Coster {
    private final Interpretable inst;
    private final Interpretable arg;
    private final String argTypeName;
    private final Set<Val> valueSet;

    EvalSetMembership(
        Interpretable inst, Interpretable arg, String argTypeName, Set<Val> valueSet) {
      super(inst.id());
      this.inst = inst;
      this.arg = arg;
      this.argTypeName = argTypeName;
      this.valueSet = valueSet;
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val val = arg.eval(ctx);
      if (isUnknownOrError(val)) {
        return val;
      }
      if (valueSet.isEmpty()) {
        return False;
      }
      if (!val.type().typeName().equals(argTypeName)) {
        return noSuchOverload(null, Operator.In.id, val);
      }
      return valueSet.contains(val) ? True : False;
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return estimateCost(arg);
    }

    @Override
    public String toString() {
      return "EvalSetMembership{"
          + "id="
          + id
          + ", inst="
          + inst
          + ", arg="
          + arg
          + ", argTypeName='"
          + argTypeName
          + '\''
          + ", valueSet="
          + valueSet
          + '}';
    }
  }

  final class EvalReceiverVarArgs extends AbstractEval implements Coster, InterpretableCall {
    private final String function;
    private final String overload;
    private final Interpretable[] args;

    public EvalReceiverVarArgs(long id, String function, String overload, Interpretable[] args) {
      super(id);
      this.function = Objects.requireNonNull(function);
      this.overload = Objects.requireNonNull(overload);
      this.args = Objects.requireNonNull(args);
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val arg0 = args[0].eval(ctx);
      if (isUnknownOrError(arg0)) {
        return arg0;
      }

      return switch (args.length) {
        case 3 -> evalReceiverTail2(ctx, arg0);
        case 4 -> evalReceiverTail3(ctx, arg0);
        default -> evalReceiverTail(ctx, arg0);
      };
    }

    private Val evalReceiverTail2(org.projectnessie.cel.interpreter.Activation ctx, Val arg0) {
      Val arg1 = args[1].eval(ctx);
      if (isUnknownOrError(arg1)) {
        return arg1;
      }
      Val arg2 = args[2].eval(ctx);
      if (isUnknownOrError(arg2)) {
        return arg2;
      }
      return receiveOrNoSuchOverload(arg0, arg1, arg2);
    }

    private Val evalReceiverTail3(org.projectnessie.cel.interpreter.Activation ctx, Val arg0) {
      Val arg1 = args[1].eval(ctx);
      if (isUnknownOrError(arg1)) {
        return arg1;
      }
      Val arg2 = args[2].eval(ctx);
      if (isUnknownOrError(arg2)) {
        return arg2;
      }
      Val arg3 = args[3].eval(ctx);
      if (isUnknownOrError(arg3)) {
        return arg3;
      }
      return receiveOrNoSuchOverload(arg0, arg1, arg2, arg3);
    }

    private Val evalReceiverTail(org.projectnessie.cel.interpreter.Activation ctx, Val arg0) {
      Val[] tailArgs = new Val[args.length - 1];
      for (int i = 1; i < args.length; i++) {
        Val argVal = args[i].eval(ctx);
        if (isUnknownOrError(argVal)) {
          return argVal;
        }
        tailArgs[i - 1] = argVal;
      }
      return receiveOrNoSuchOverload(arg0, tailArgs);
    }

    private Val receiveOrNoSuchOverload(Val arg0, Val... tailArgs) {
      if (arg0.type().hasTrait(Trait.ReceiverType)) {
        return ((Receiver) arg0).receive(function, overload, tailArgs);
      }
      return noSuchOverload(arg0, function, overload, tailArgs);
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      Cost c = sumOfCost(args);
      return c.add(OneOne); // add cost for function
    }

    /** Function implements the InterpretableCall interface method. */
    @Override
    public String function() {
      return function;
    }

    /** OverloadID implements the InterpretableCall interface method. */
    @Override
    public String overloadID() {
      return overload;
    }

    /** Args returns the argument to the unary function. */
    @Override
    public Interpretable[] args() {
      return args;
    }

    @Override
    public String toString() {
      return "EvalReceiverVarArgs{"
          + "id="
          + id
          + ", function='"
          + function
          + '\''
          + ", overload='"
          + overload
          + '\''
          + ", args="
          + Arrays.toString(args)
          + '}';
    }
  }

  static Val receiveVarArgs(Receiver receiver, String function, String overload, Val[] argVals) {
    return switch (argVals.length) {
      case 1 -> receiver.receive(function, overload);
      case 2 -> receiver.receive(function, overload, argVals[1]);
      case 3 -> receiver.receive(function, overload, argVals[1], argVals[2]);
      case 4 -> receiver.receive(function, overload, argVals[1], argVals[2], argVals[3]);
      default ->
          receiver.receive(function, overload, Arrays.copyOfRange(argVals, 1, argVals.length));
    };
  }

  /**
   * evalWatch is an Interpretable implementation that wraps the execution of a given expression so
   * that it may observe the computed value and send it to an observer.
   */
  final class EvalWatch implements Interpretable, Coster {
    private final Interpretable i;
    private final EvalObserver observer;

    public EvalWatch(Interpretable i, EvalObserver observer) {
      this.i = Objects.requireNonNull(i);
      this.observer = Objects.requireNonNull(observer);
    }

    @Override
    public long id() {
      return i.id();
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val val = i.eval(ctx);
      observer.observe(id(), val);
      return val;
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return estimateCost(i);
    }

    @Override
    public String toString() {
      return "EvalWatch{" + i + '}';
    }
  }

  /**
   * evalWatchAttr describes a watcher of an instAttr Interpretable.
   *
   * <p>Since the watcher may be selected against at a later stage in program planning, the watcher
   * must implement the instAttr interface by proxy.
   */
  final class EvalWatchAttr implements Coster, InterpretableAttribute, Attribute {
    private final InterpretableAttribute attr;
    private final EvalObserver observer;

    public EvalWatchAttr(InterpretableAttribute attr, EvalObserver observer) {
      this.attr = Objects.requireNonNull(attr);
      this.observer = Objects.requireNonNull(observer);
    }

    @Override
    public long id() {
      return attr.id();
    }

    /**
     * AddQualifier creates a wrapper over the incoming qualifier which observes the qualification
     * result.
     */
    @Override
    public Attribute addQualifier(AttributeFactory.Qualifier q) {
      if (q instanceof ConstantQualifierEquator cq) {
        q = new EvalWatchConstQualEquat(cq, observer, attr.adapter());
      } else if (q instanceof ConstantQualifier cq) {
        q = new EvalWatchConstQual(cq, observer, attr.adapter());
      } else {
        q = new EvalWatchQual(q, observer, attr.adapter());
      }
      attr.addQualifier(q);
      return this;
    }

    @Override
    public Attribute attr() {
      return attr.attr();
    }

    @Override
    public TypeAdapter adapter() {
      return attr.adapter();
    }

    @Override
    public Object qualify(Activation vars, Object obj) {
      return attr.qualify(vars, obj);
    }

    @Override
    public Object resolve(Activation act) {
      return attr.resolve(act);
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return estimateCost(attr);
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val val = attr.eval(ctx);
      observer.observe(id(), val);
      return val;
    }

    @Override
    public String toString() {
      return "EvalWatchAttr{" + attr + '}';
    }
  }

  abstract class AbstractEvalWatch<T extends Qualifier> extends AbstractEval
      implements Coster, Qualifier {
    protected final T delegate;
    protected final EvalObserver observer;
    protected final TypeAdapter adapter;

    AbstractEvalWatch(T delegate, EvalObserver observer, TypeAdapter adapter) {
      super(delegate.id());
      this.delegate = delegate;
      this.observer = Objects.requireNonNull(observer);
      this.adapter = Objects.requireNonNull(adapter);
    }

    /** Qualify observes the qualification of a object via a value computed at runtime. */
    @Override
    public Object qualify(org.projectnessie.cel.interpreter.Activation vars, Object obj) {
      Object out = delegate.qualify(vars, obj);
      Val val;
      if (out != null) {
        val = adapter.nativeToValue(out);
      } else {
        val = newErr(String.format("qualify failed, vars=%s, obj=%s", vars, obj));
      }
      observer.observe(id(), val);
      return out;
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return estimateCost(delegate);
    }
  }

  final class EvalWatchConstQualEquat extends AbstractEvalWatch<ConstantQualifierEquator>
      implements ConstantQualifierEquator {
    EvalWatchConstQualEquat(
        ConstantQualifierEquator delegate, EvalObserver observer, TypeAdapter adapter) {
      super(delegate, observer, adapter);
    }

    @Override
    public Val eval(Activation activation) {
      throw new UnsupportedOperationException("WTF?");
    }

    @Override
    public Val value() {
      return delegate.value();
    }

    /**
     * QualifierValueEquals tests whether the incoming value is equal to the qualificying constant.
     */
    @Override
    public boolean qualifierValueEquals(Object value) {
      return delegate.qualifierValueEquals(value);
    }

    @Override
    public String toString() {
      return "EvalWatchConstQualEquat{" + delegate + '}';
    }
  }

  /**
   * evalWatchConstQual observes the qualification of an object using a constant boolean, int,
   * string, or uint.
   */
  final class EvalWatchConstQual extends AbstractEvalWatch<ConstantQualifier>
      implements ConstantQualifier, Coster {
    EvalWatchConstQual(ConstantQualifier delegate, EvalObserver observer, TypeAdapter adapter) {
      super(delegate, observer, adapter);
    }

    @Override
    public Val eval(Activation activation) {
      throw new UnsupportedOperationException("WTF?");
    }

    @Override
    public Val value() {
      return delegate.value();
    }

    @Override
    public String toString() {
      return "EvalWatchConstQual{" + delegate + '}';
    }
  }

  /** evalWatchQual observes the qualification of an object by a value computed at runtime. */
  final class EvalWatchQual extends AbstractEvalWatch<Qualifier> {
    public EvalWatchQual(Qualifier delegate, EvalObserver observer, TypeAdapter adapter) {
      super(delegate, observer, adapter);
    }

    @Override
    public Val eval(Activation activation) {
      throw new UnsupportedOperationException("WTF?");
    }

    @Override
    public String toString() {
      return "EvalWatchQual{" + delegate + '}';
    }
  }

  /** evalWatchConst describes a watcher of an instConst Interpretable. */
  final class EvalWatchConst implements InterpretableConst, Coster {
    private final InterpretableConst c;
    private final EvalObserver observer;

    EvalWatchConst(InterpretableConst c, EvalObserver observer) {
      this.c = Objects.requireNonNull(c);
      this.observer = Objects.requireNonNull(observer);
    }

    @Override
    public long id() {
      return c.id();
    }

    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation activation) {
      Val val = value();
      observer.observe(id(), val);
      return val;
    }

    @Override
    public Val value() {
      return c.value();
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return estimateCost(c);
    }

    @Override
    public String toString() {
      return "EvalWatchConst{" + c + '}';
    }
  }

  /** evalExhaustiveOr is just like evalOr, but does not short-circuit argument evaluation. */
  final class EvalExhaustiveOr extends AbstractEvalLhsRhs {
    // TODO combine with EvalOr
    EvalExhaustiveOr(long id, Interpretable lhs, Interpretable rhs) {
      super(id, lhs, rhs);
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val lVal = lhs.eval(ctx);
      Val rVal = rhs.eval(ctx);
      if (lVal == True || rVal == True) {
        return True;
      }
      if (lVal == False && rVal == False) {
        return False;
      }
      if (isUnknown(lVal)) {
        return lVal;
      }
      if (isUnknown(rVal)) {
        return rVal;
      }
      // TODO: Combine the errors into a set in the future.
      // If the left-hand side is non-boolean return it as the error.
      if (isError(lVal)) {
        return lVal;
      }
      return noSuchOverload(lVal, Operator.LogicalOr.id, rVal);
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return calExhaustiveBinaryOpsCost(lhs, rhs);
    }

    @Override
    public String toString() {
      return "EvalExhaustiveOr{" + "id=" + id + ", lhs=" + lhs + ", rhs=" + rhs + '}';
    }
  }

  /** evalExhaustiveAnd is just like evalAnd, but does not short-circuit argument evaluation. */
  final class EvalExhaustiveAnd extends AbstractEvalLhsRhs {
    // TODO combine with EvalAnd
    EvalExhaustiveAnd(long id, Interpretable lhs, Interpretable rhs) {
      super(id, lhs, rhs);
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val lVal = lhs.eval(ctx);
      Val rVal = rhs.eval(ctx);
      if (lVal == False || rVal == False) {
        return False;
      }
      if (lVal == True && rVal == True) {
        return True;
      }
      if (isUnknown(lVal)) {
        return lVal;
      }
      if (isUnknown(rVal)) {
        return rVal;
      }
      if (isError(lVal)) {
        return lVal;
      }
      return noSuchOverload(lVal, Operator.LogicalAnd.id, rVal);
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return calExhaustiveBinaryOpsCost(lhs, rhs);
    }

    @Override
    public String toString() {
      return "EvalExhaustiveAnd{" + "id=" + id + ", lhs=" + lhs + ", rhs=" + rhs + '}';
    }
  }

  static Cost calExhaustiveBinaryOpsCost(Interpretable lhs, Interpretable rhs) {
    Cost l = estimateCost(lhs);
    Cost r = estimateCost(rhs);
    return Cost.OneOne.add(l).add(r);
  }

  /**
   * evalExhaustiveConditional is like evalConditional, but does not short-circuit argument
   * evaluation.
   */
  final class EvalExhaustiveConditional extends AbstractEval implements Coster {
    // TODO combine with EvalConditional
    private final TypeAdapter adapter;
    private final ConditionalAttribute attr;

    EvalExhaustiveConditional(long id, TypeAdapter adapter, ConditionalAttribute attr) {
      super(id);
      this.adapter = Objects.requireNonNull(adapter);
      this.attr = Objects.requireNonNull(attr);
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val cVal = attr.expr.eval(ctx);
      Object tVal = attr.truthy.resolve(ctx);
      Object fVal = attr.falsy.resolve(ctx);
      if (cVal == True) {
        return adapter.nativeToValue(tVal);
      } else if (cVal == False) {
        return adapter.nativeToValue(fVal);
      } else {
        return noSuchOverload(null, Operator.Conditional.id, cVal);
      }
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return attr.cost();
    }

    @Override
    public String toString() {
      return "EvalExhaustiveConditional{" + "id=" + id + ", attr=" + attr + '}';
    }
  }

  /** evalExhaustiveFold is like evalFold, but does not short-circuit argument evaluation. */
  final class EvalExhaustiveFold extends AbstractEval implements Coster {
    // TODO combine with EvalFold
    private final String accuVar;
    private final String iterVar;
    private final String iterVar2;
    private final Interpretable iterRange;
    private final Interpretable accu;
    private final Interpretable cond;
    private final Interpretable step;
    private final Interpretable result;

    EvalExhaustiveFold(
        long id,
        Interpretable accu,
        String accuVar,
        Interpretable iterRange,
        String iterVar,
        String iterVar2,
        Interpretable cond,
        Interpretable step,
        Interpretable result) {
      super(id);
      this.accuVar = accuVar;
      this.iterVar = iterVar;
      this.iterVar2 = iterVar2;
      this.iterRange = iterRange;
      this.accu = accu;
      this.cond = cond;
      this.step = step;
      this.result = result;
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val foldRange = iterRange.eval(ctx);
      if (!foldRange.type().hasTrait(Trait.IterableType)) {
        return valOrErr(
            foldRange, "got '%s', expected iterable type", foldRange.getClass().getName());
      }
      // Configure the fold activation with the accumulator initial value.
      VarActivation accuCtx = new VarActivation();
      accuCtx.parent = ctx;
      accuCtx.name = accuVar;
      accuCtx.val = accu.eval(ctx);
      VarActivation iterCtx = new VarActivation();
      iterCtx.parent = accuCtx;
      iterCtx.name = iterVar;
      VarActivation iterCtx2 = null;
      if (!iterVar2.isEmpty()) {
        iterCtx2 = new VarActivation();
        iterCtx2.parent = iterCtx;
        iterCtx2.name = iterVar2;
      }
      IteratorT it = ((IterableT) foldRange).iterator();
      var isLister = foldRange instanceof Lister;
      var mapper = (foldRange instanceof Mapper m) ? m : null;
      long index = 0L;
      while (it.hasNext() == True) {
        // Modify the iter var in the fold activation.
        Val next = it.next();
        Activation loopCtx = iterCtx;
        if (iterCtx2 != null) {
          if (isLister) {
            iterCtx.val = intOf(index);
            iterCtx2.val = next;
          } else if (mapper != null) {
            iterCtx.val = next;
            iterCtx2.val = mapper.get(next);
          } else {
            return valOrErr(
                foldRange, "got '%s', expected list or map type", foldRange.getClass().getName());
          }
          loopCtx = iterCtx2;
        } else {
          iterCtx.val = next;
        }
        index++;

        // Evaluate the condition, but don't terminate the loop as this is exhaustive eval!
        cond.eval(loopCtx);

        // Evalute the evaluation step into accu var.
        accuCtx.val = step.eval(loopCtx);
      }
      // Compute the result.
      return result.eval(accuCtx);
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      // Compute the cost for evaluating iterRange.
      Cost i = estimateCost(iterRange);

      // Compute the size of iterRange. If the size depends on the input, return the maximum
      // possible
      // cost range.
      Val foldRange = iterRange.eval(emptyActivation());
      if (!foldRange.type().hasTrait(Trait.IterableType)) {
        return Cost.Unknown;
      }
      long rangeCnt;
      if (foldRange instanceof Sizer sizer) {
        rangeCnt = sizer.nativeSize();
      } else {
        rangeCnt = 0L;
        IteratorT it = ((IterableT) foldRange).iterator();
        while (it.hasNext() == True) {
          it.next();
          rangeCnt++;
        }
      }

      Cost a = estimateCost(accu);
      Cost c = estimateCost(cond);
      Cost s = estimateCost(step);
      Cost r = estimateCost(result);

      // The cond and step costs are multiplied by size(iterRange).
      return i.add(a).add(c.multiply(rangeCnt)).add(s.multiply(rangeCnt)).add(r);
    }

    @Override
    public String toString() {
      return "EvalExhaustiveFold{"
          + "id="
          + id
          + ", accuVar='"
          + accuVar
          + '\''
          + ", iterVar='"
          + iterVar
          + '\''
          + ", iterVar2='"
          + iterVar2
          + '\''
          + ", iterRange="
          + iterRange
          + ", accu="
          + accu
          + ", cond="
          + cond
          + ", step="
          + step
          + ", result="
          + result
          + '}';
    }
  }

  /** EvalExhaustiveListFold evaluates every filter and transform without short-circuiting. */
  final class EvalExhaustiveListFold extends AbstractEval implements Coster {
    private final EvalListFold fold;

    EvalExhaustiveListFold(EvalListFold fold) {
      super(fold.id);
      this.fold = fold;
    }

    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val foldRange = fold.iterRange.eval(ctx);
      if (!foldRange.type().hasTrait(Trait.IterableType)) {
        return valOrErr(
            foldRange, "got '%s', expected iterable type", foldRange.getClass().getName());
      }

      VarActivation iterCtx = new VarActivation();
      iterCtx.parent = ctx;
      iterCtx.name = fold.iterVar;
      VarActivation iterCtx2 = null;
      if (!fold.iterVar2.isEmpty()) {
        iterCtx2 = new VarActivation();
        iterCtx2.parent = iterCtx;
        iterCtx2.name = fold.iterVar2;
      }
      List<Val> values = new ArrayList<>(fold.listCapacity(foldRange));
      Val result = null;
      IteratorT it = ((IterableT) foldRange).iterator();
      long index = 0L;
      while (it.hasNext() == True) {
        Val next = it.next();
        Activation loopCtx = iterCtx;
        if (iterCtx2 != null) {
          if (foldRange instanceof Lister) {
            iterCtx.val = intOf(index);
            iterCtx2.val = next;
          } else if (foldRange instanceof Mapper) {
            iterCtx.val = next;
            iterCtx2.val = ((Mapper) foldRange).get(next);
          } else {
            return valOrErr(
                foldRange, "got '%s', expected list or map type", foldRange.getClass().getName());
          }
          loopCtx = iterCtx2;
        } else {
          iterCtx.val = next;
        }
        index++;

        Val include = fold.filter != null ? fold.filter.eval(loopCtx) : True;
        Val value = fold.transform.eval(loopCtx);
        if (include == False) {
          continue;
        }
        if (include != True) {
          result = noSuchOverload(null, Operator.Conditional.id, include);
          continue;
        }
        if (result == null) {
          if (isUnknownOrError(value)) {
            result = value;
          } else {
            values.add(value);
          }
        }
      }
      return result != null
          ? result
          : ListT.newValArrayList(fold.adapter, values.toArray(new Val[0]));
    }

    @Override
    public Cost cost() {
      return fold.cost();
    }

    @Override
    public String toString() {
      return "EvalExhaustiveListFold{" + fold + '}';
    }
  }

  /** EvalExhaustiveMapFold evaluates every filter and transform without short-circuiting. */
  final class EvalExhaustiveMapFold extends AbstractEval implements Coster {
    private final EvalMapFold fold;

    EvalExhaustiveMapFold(EvalMapFold fold) {
      super(fold.id);
      this.fold = fold;
    }

    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val foldRange = fold.iterRange.eval(ctx);
      if (!foldRange.type().hasTrait(Trait.IterableType)) {
        return valOrErr(
            foldRange, "got '%s', expected iterable type", foldRange.getClass().getName());
      }

      VarActivation iterCtx = new VarActivation();
      iterCtx.parent = ctx;
      iterCtx.name = fold.iterVar;
      VarActivation iterCtx2 = null;
      if (!fold.iterVar2.isEmpty()) {
        iterCtx2 = new VarActivation();
        iterCtx2.parent = iterCtx;
        iterCtx2.name = fold.iterVar2;
      }
      Map<Val, Val> values = new HashMap<>(fold.mapCapacity(foldRange));
      Val result = null;
      IteratorT it = ((IterableT) foldRange).iterator();
      long index = 0L;
      while (it.hasNext() == True) {
        Val next = it.next();
        Val key;
        Activation loopCtx = iterCtx;
        if (iterCtx2 != null) {
          if (foldRange instanceof Lister) {
            key = intOf(index);
            iterCtx.val = key;
            iterCtx2.val = next;
          } else if (foldRange instanceof Mapper) {
            key = next;
            iterCtx.val = key;
            iterCtx2.val = ((Mapper) foldRange).get(next);
          } else {
            return valOrErr(
                foldRange, "got '%s', expected list or map type", foldRange.getClass().getName());
          }
          loopCtx = iterCtx2;
        } else {
          key = next;
          iterCtx.val = next;
        }
        index++;

        Val include = fold.filter != null ? fold.filter.eval(loopCtx) : True;
        Val value = fold.transform.eval(loopCtx);
        if (include == False) {
          continue;
        }
        if (include != True) {
          result = noSuchOverload(null, Operator.Conditional.id, include);
          continue;
        }
        if (result == null) {
          if (isUnknownOrError(value)) {
            result = value;
          } else {
            values.put(key, value);
          }
        }
      }
      return result != null ? result : MapT.newWrappedMap(fold.adapter, values);
    }

    @Override
    public Cost cost() {
      return fold.cost();
    }

    @Override
    public String toString() {
      return "EvalExhaustiveMapFold{" + fold + '}';
    }
  }

  /** evalAttr evaluates an Attribute value. */
  final class EvalAttr extends AbstractEval
      implements InterpretableAttribute, Coster, Qualifier, Attribute {
    private final TypeAdapter adapter;
    private Attribute attr;

    EvalAttr(TypeAdapter adapter, Attribute attr) {
      super(attr.id());
      this.adapter = Objects.requireNonNull(adapter);
      this.attr = Objects.requireNonNull(attr);
    }

    /** AddQualifier implements the instAttr interface method. */
    @Override
    public Attribute addQualifier(AttributeFactory.Qualifier qualifier) {
      attr = attr.addQualifier(qualifier);
      return attr;
    }

    /** Attr implements the instAttr interface method. */
    @Override
    public Attribute attr() {
      return attr;
    }

    /** Adapter implements the instAttr interface method. */
    @Override
    public TypeAdapter adapter() {
      return adapter;
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      return estimateCost(attr);
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      try {
        Object v = attr.resolve(ctx);
        return adapter.nativeToValue(v);
      } catch (Exception e) {
        return newErr(e, e.toString());
      }
    }

    /** Qualify proxies to the Attribute's Qualify method. */
    @Override
    public Object qualify(org.projectnessie.cel.interpreter.Activation ctx, Object obj) {
      return attr.qualify(ctx, obj);
    }

    /** Resolve proxies to the Attribute's Resolve method. */
    @Override
    public Object resolve(org.projectnessie.cel.interpreter.Activation ctx) {
      return attr.resolve(ctx);
    }

    @Override
    public String toString() {
      return "EvalAttr{" + "id=" + id + ", attr=" + attr + '}';
    }
  }
}
