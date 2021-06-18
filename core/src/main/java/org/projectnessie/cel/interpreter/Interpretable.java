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
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Err.valOrErr;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Coster.Cost.OneOne;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.Coster.costOf;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.IterableT;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Container;
import org.projectnessie.cel.common.types.traits.FieldTester;
import org.projectnessie.cel.common.types.traits.Negater;
import org.projectnessie.cel.common.types.traits.Receiver;
import org.projectnessie.cel.common.types.traits.Trait;
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
    Attribute addQualifier(Qualifier qualifier);

    /**
     * Qualify replicates the Attribute.Qualify method to permit extension and interception of
     * object qualification.
     */
    Object qualify(Activation vars, Object obj);

    /** Resolve returns the value of the Attribute given the current Activation. */
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

  class EvalTestOnly implements Interpretable, Coster {
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
        if (op instanceof InterpretableAttribute) {
          InterpretableAttribute opAttr = (InterpretableAttribute) op;
          Object opVal = opAttr.resolve(ctx);
          if (opVal instanceof Val) {
            Val refVal = (Val) opVal;
            opVal = refVal.value();
          }
          if (fieldType.isSet.isSet(opVal)) {
            return True;
          }
          return False;
        }
      }

      Val obj = op.eval(ctx);
      if (obj instanceof FieldTester) {
        return ((FieldTester) obj).isSet(field);
      }
      if (obj instanceof Container) {
        return ((Container) obj).contains(field);
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

  class EvalConst extends AbstractEval implements InterpretableConst, Coster {
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

  class EvalOr extends AbstractEvalLhsRhs {
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

  class EvalAnd extends AbstractEvalLhsRhs {
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

  class EvalEq extends AbstractEvalLhsRhs implements InterpretableCall {
    EvalEq(long id, Interpretable lhs, Interpretable rhs) {
      super(id, lhs, rhs);
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val lVal = lhs.eval(ctx);
      Val rVal = rhs.eval(ctx);
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

  class EvalNe extends AbstractEvalLhsRhs implements InterpretableCall {
    EvalNe(long id, Interpretable lhs, Interpretable rhs) {
      super(id, lhs, rhs);
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val lVal = lhs.eval(ctx);
      Val rVal = rhs.eval(ctx);
      Val eqVal = lVal.equal(rVal);
      switch (eqVal.type().typeEnum()) {
        case Err:
          return eqVal;
        case Bool:
          return ((Negater) eqVal).negate();
      }
      return noSuchOverload(lVal, Operator.NotEquals.id, rVal);
    }

    /** Cost implements the Coster interface method. */
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

  class EvalZeroArity extends AbstractEval implements InterpretableCall, Coster {
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

  class EvalUnary extends AbstractEval implements InterpretableCall, Coster {
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

  class EvalBinary extends AbstractEvalLhsRhs implements InterpretableCall {
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

  class EvalVarArgs extends AbstractEval implements Coster, InterpretableCall {
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
        return ((Receiver) arg0)
            .receive(function, overload, Arrays.copyOfRange(argVals, 1, argVals.length - 1));
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

  class EvalList extends AbstractEval implements Coster {
    final Interpretable[] elems;
    private final TypeAdapter adapter;

    EvalList(long id, Interpretable[] elems, TypeAdapter adapter) {
      super(id);
      this.elems = elems;
      this.adapter = adapter;
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Val[] elemVals = new Val[elems.length];
      // If any argument is unknown or error early terminate.
      for (int i = 0; i < elems.length; i++) {
        Interpretable elem = elems[i];
        Val elemVal = elem.eval(ctx);
        if (isUnknownOrError(elemVal)) {
          return elemVal;
        }
        elemVals[i] = elemVal;
      }
      return adapter.nativeToValue(elemVals);
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

  class EvalMap extends AbstractEval implements Coster {
    final Interpretable[] keys;
    final Interpretable[] vals;
    private final TypeAdapter adapter;

    EvalMap(long id, Interpretable[] keys, Interpretable[] vals, TypeAdapter adapter) {
      super(id);
      this.keys = keys;
      this.vals = vals;
      this.adapter = adapter;
    }

    /** Eval implements the Interpretable interface method. */
    @Override
    public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
      Map<Val, Val> entries = new HashMap<>();
      // If any argument is unknown or error early terminate.
      for (int i = 0; i < keys.length; i++) {
        Interpretable key = keys[i];
        Val keyVal = key.eval(ctx);
        if (isUnknownOrError(keyVal)) {
          return keyVal;
        }
        Val valVal = vals[i].eval(ctx);
        if (isUnknownOrError(valVal)) {
          return valVal;
        }
        entries.put(keyVal, valVal);
      }
      return adapter.nativeToValue(entries);
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

  class EvalObj extends AbstractEval implements Coster {
    private final String typeName;
    private final String[] fields;
    private final Interpretable[] vals;
    private final TypeProvider provider;

    EvalObj(
        long id, String typeName, String[] fields, Interpretable[] vals, TypeProvider provider) {
      super(id);
      this.typeName = Objects.requireNonNull(typeName);
      this.fields = Objects.requireNonNull(fields);
      this.vals = Objects.requireNonNull(vals);
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

  class EvalFold extends AbstractEval implements Coster {
    // TODO combine with EvalExhaustiveFold
    final String accuVar;
    final String iterVar;
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
        Interpretable iterRange,
        Interpretable cond,
        Interpretable step,
        Interpretable result) {
      super(id);
      this.accuVar = accuVar;
      this.iterVar = iterVar;
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
      IteratorT it = ((IterableT) foldRange).iterator();
      while (it.hasNext() == True) {
        // Modify the iter var in the fold activation.
        iterCtx.val = it.next();

        // Evaluate the condition, terminate the loop if false.
        Val c = cond.eval(iterCtx);
        if (c == False) {
          break;
        }

        // Evalute the evaluation step into accu var.
        accuCtx.val = step.eval(iterCtx);
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
      long rangeCnt = 0L;
      IteratorT it = ((IterableT) foldRange).iterator();
      while (it.hasNext() == True) {
        it.next();
        rangeCnt++;
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

  // Optional Intepretable implementations that specialize, subsume, or extend the core evaluation
  // plan via decorators.

  /**
   * evalSetMembership is an Interpretable implementation which tests whether an input value exists
   * within the set of map keys used to model a set.
   */
  class EvalSetMembership extends AbstractEval implements Coster {
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

  /**
   * evalWatch is an Interpretable implementation that wraps the execution of a given expression so
   * that it may observe the computed value and send it to an observer.
   */
  class EvalWatch implements Interpretable, Coster {
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
  class EvalWatchAttr implements Coster, InterpretableAttribute, Attribute {
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
      if (q instanceof ConstantQualifierEquator) {
        ConstantQualifierEquator cq = (ConstantQualifierEquator) q;
        q = new EvalWatchConstQualEquat(cq, observer, attr.adapter());
      } else if (q instanceof ConstantQualifier) {
        ConstantQualifier cq = (ConstantQualifier) q;
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

  class EvalWatchConstQualEquat extends AbstractEvalWatch<ConstantQualifierEquator>
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
  class EvalWatchConstQual extends AbstractEvalWatch<ConstantQualifier>
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
  class EvalWatchQual extends AbstractEvalWatch<Qualifier> {
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
  class EvalWatchConst implements InterpretableConst, Coster {
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
  class EvalExhaustiveOr extends AbstractEvalLhsRhs {
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
  class EvalExhaustiveAnd extends AbstractEvalLhsRhs {
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
  class EvalExhaustiveConditional extends AbstractEval implements Coster {
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
  class EvalExhaustiveFold extends AbstractEval implements Coster {
    // TODO combine with EvalFold
    private final String accuVar;
    private final String iterVar;
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
        Interpretable cond,
        Interpretable step,
        Interpretable result) {
      super(id);
      this.accuVar = accuVar;
      this.iterVar = iterVar;
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
      IteratorT it = ((IterableT) foldRange).iterator();
      while (it.hasNext() == True) {
        // Modify the iter var in the fold activation.
        iterCtx.val = it.next();

        // Evaluate the condition, but don't terminate the loop as this is exhaustive eval!
        cond.eval(iterCtx);

        // Evalute the evaluation step into accu var.
        accuCtx.val = step.eval(iterCtx);
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
      long rangeCnt = 0L;
      IteratorT it = ((IterableT) foldRange).iterator();
      while (it.hasNext() == True) {
        it.next();
        rangeCnt++;
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

  /** evalAttr evaluates an Attribute value. */
  class EvalAttr extends AbstractEval
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
        if (v != null) {
          return adapter.nativeToValue(v);
        }
        return newErr(String.format("eval failed, ctx: %s", ctx));
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
