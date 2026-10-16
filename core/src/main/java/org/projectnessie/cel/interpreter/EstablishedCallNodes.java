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
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Err.valOrErr;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;
import static org.projectnessie.cel.interpreter.Coster.Cost.OneOne;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.Interpretable.receiveVarArgs;
import static org.projectnessie.cel.interpreter.Interpretable.sumOfCost;

import java.util.Arrays;
import java.util.Objects;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Container;
import org.projectnessie.cel.common.types.traits.FieldTester;
import org.projectnessie.cel.common.types.traits.Receiver;
import org.projectnessie.cel.common.types.traits.Trait;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableCall;
import org.projectnessie.cel.interpreter.functions.FunctionOp;
import org.projectnessie.cel.interpreter.functions.QuaternaryOp;
import org.projectnessie.cel.interpreter.functions.QuinaryOp;
import org.projectnessie.cel.interpreter.functions.TernaryOp;

/** Package-private established call nodes. */
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

  @SuppressWarnings("DuplicatedCode")
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
    return noSuchOverload(firstVal, function, overload, new Val[] {firstVal, secondVal, thirdVal});
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

  @SuppressWarnings("DuplicatedCode")
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

  @SuppressWarnings("DuplicatedCode")
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
