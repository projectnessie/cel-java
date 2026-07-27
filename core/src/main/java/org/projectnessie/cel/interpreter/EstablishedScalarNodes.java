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

import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.noSuchAttributeException;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;
import static org.projectnessie.cel.interpreter.Coster.Cost.OneOne;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;

import java.util.Objects;
import org.projectnessie.cel.RegexEngine;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Negater;
import org.projectnessie.cel.common.types.traits.Receiver;
import org.projectnessie.cel.common.types.traits.Trait;
import org.projectnessie.cel.interpreter.Activation.PartialActivation;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableAttribute;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableCall;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableConst;
import org.projectnessie.cel.interpreter.functions.BinaryOp;
import org.projectnessie.cel.interpreter.functions.UnaryOp;

abstract class AbstractEval implements Interpretable {
  protected final long id;

  AbstractEval(long id) {
    this.id = id;
  }

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

/** Evaluates a checked top-level variable directly from the activation. */
class EvalIdent extends AbstractEval implements Coster {
  protected final String name;
  protected final TypeAdapter adapter;

  EvalIdent(long id, String name, TypeAdapter adapter) {
    super(id);
    this.name = Objects.requireNonNull(name);
    this.adapter = Objects.requireNonNull(adapter);
  }

  @SuppressWarnings("DuplicatedCode")
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

  @SuppressWarnings("DuplicatedCode")
  protected final Object resolveRaw(Activation ctx) {
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
    return value;
  }

  @Override
  public Cost cost() {
    return OneOne;
  }

  @Override
  public String toString() {
    return "EvalIdent{" + "id=" + id + ", name='" + name + '\'' + '}';
  }
}

/** Checked aggregate identifier whose ordinary evaluation uses exact checked materialization. */
class EvalExactAggregateIdent extends EvalIdent {
  final CheckedAggregateMaterializer materializer;

  EvalExactAggregateIdent(
      long id, String name, TypeAdapter adapter, CheckedAggregateMaterializer materializer) {
    super(id, name, adapter);
    this.materializer = materializer;
  }

  @Override
  public Val eval(Activation ctx) {
    Object value = resolveRaw(ctx);
    return value instanceof Val val && isUnknownOrError(val)
        ? val
        : materializer.materialize(value);
  }
}

class EvalConst extends AbstractEval implements InterpretableConst, Coster {
  private final Val val;

  EvalConst(long id, Val val) {
    super(id);
    this.val = val;
  }

  @Override
  public Val eval(Activation activation) {
    return val;
  }

  @Override
  public Cost cost() {
    return Cost.None;
  }

  @Override
  public Val value() {
    return val;
  }

  @Override
  public String toString() {
    return "EvalConst{" + "id=" + id + ", val=" + val + '}';
  }
}

class EvalUnary extends AbstractEval implements InterpretableCall, Coster {
  private final String function;
  private final String overload;
  protected final Interpretable arg;
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

  @Override
  public Val eval(Activation ctx) {
    return evalPrepared(arg.eval(ctx));
  }

  protected final Val evalPrepared(Val argValue) {
    if (isUnknownOrError(argValue)) {
      return argValue;
    }
    if (impl != null && (trait == null || argValue.type().hasTrait(trait))) {
      return impl.invoke(argValue);
    }
    if (argValue.type().hasTrait(Trait.ReceiverType)) {
      return ((Receiver) argValue).receive(function, overload);
    }
    return noSuchOverload(argValue, function, overload, new Val[0]);
  }

  @Override
  public Cost cost() {
    return Cost.OneOne.add(estimateCost(arg));
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

class EvalEq extends AbstractEvalLhsRhs implements InterpretableCall {
  EvalEq(long id, Interpretable lhs, Interpretable rhs) {
    super(id, lhs, rhs);
  }

  @Override
  public Val eval(Activation ctx) {
    return evalPrepared(lhs.eval(ctx), rhs.eval(ctx));
  }

  protected final Val evalPrepared(Val left, Val right) {
    if (isUnknownOrError(left)) {
      return left;
    }
    if (isUnknownOrError(right)) {
      return right;
    }
    return left.equal(right);
  }

  @Override
  public Cost cost() {
    return Interpretable.calExhaustiveBinaryOpsCost(lhs, rhs);
  }

  @Override
  public String function() {
    return Operator.Equals.id;
  }

  @Override
  public String overloadID() {
    return Overloads.Equals;
  }

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

  @Override
  public Val eval(Activation ctx) {
    return evalPrepared(lhs.eval(ctx), rhs.eval(ctx));
  }

  protected final Val evalPrepared(Val left, Val right) {
    if (isUnknownOrError(left)) {
      return left;
    }
    if (isUnknownOrError(right)) {
      return right;
    }
    Val equal = left.equal(right);
    return switch (equal.type().typeEnum()) {
      case Err -> equal;
      case Bool -> ((Negater) equal).negate();
      default -> noSuchOverload(left, Operator.NotEquals.id, right);
    };
  }

  @Override
  public Cost cost() {
    return Interpretable.calExhaustiveBinaryOpsCost(lhs, rhs);
  }

  @Override
  public String function() {
    return Operator.NotEquals.id;
  }

  @Override
  public String overloadID() {
    return Overloads.NotEquals;
  }

  @Override
  public Interpretable[] args() {
    return new Interpretable[] {lhs, rhs};
  }

  @Override
  public String toString() {
    return "EvalNe{" + "id=" + id + ", lhs=" + lhs + ", rhs=" + rhs + '}';
  }
}

class EvalBinary extends AbstractEvalLhsRhs implements InterpretableCall {
  protected final String function;
  protected final String overload;
  protected final Trait trait;
  protected final BinaryOp impl;

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

  @Override
  public Val eval(Activation ctx) {
    return evalPrepared(lhs.eval(ctx), rhs.eval(ctx));
  }

  protected final Val evalPrepared(Val left, Val right) {
    if (isUnknownOrError(left)) {
      return left;
    }
    if (isUnknownOrError(right)) {
      return right;
    }
    if (impl != null && (trait == null || left.type().hasTrait(trait))) {
      return impl.invoke(left, right);
    }
    if (left.type().hasTrait(Trait.ReceiverType)) {
      return ((Receiver) left).receive(function, overload, right);
    }
    return noSuchOverload(left, function, overload, new Val[] {right});
  }

  @Override
  public Cost cost() {
    return Interpretable.calExhaustiveBinaryOpsCost(lhs, rhs);
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

/** Exact built-in string {@code matches} call using the program's selected regex engine. */
final class EvalRegex extends EvalBinary {
  private final RegexEngine regexEngine;

  EvalRegex(
      long id,
      String function,
      String overload,
      Interpretable lhs,
      Interpretable rhs,
      RegexEngine regexEngine,
      BinaryOp implementation,
      Trait trait) {
    super(id, function, overload, lhs, rhs, trait, implementation);
    this.regexEngine = Objects.requireNonNull(regexEngine);
  }

  @Override
  public Val eval(Activation ctx) {
    Val left = lhs.eval(ctx);
    Val right = rhs.eval(ctx);
    if (left instanceof StringT input && right instanceof StringT expression) {
      try {
        return boolOf(
            RegexSupport.find(regexEngine, (String) expression.value(), (String) input.value()));
      } catch (Exception failure) {
        return newErr(failure, "%s", failure.getMessage());
      }
    }
    return evalPrepared(left, right);
  }
}

/** Evaluates an attribute value. */
class EvalAttr extends AbstractEval
    implements InterpretableAttribute, Coster, AttributeFactory.Qualifier, Attribute {
  protected final TypeAdapter adapter;
  protected Attribute attr;

  EvalAttr(TypeAdapter adapter, Attribute attr) {
    super(attr.id());
    this.adapter = Objects.requireNonNull(adapter);
    this.attr = Objects.requireNonNull(attr);
  }

  @Override
  public Attribute addQualifier(AttributeFactory.Qualifier qualifier) {
    attr = attr.addQualifier(qualifier);
    return attr;
  }

  @Override
  public Attribute attr() {
    return attr;
  }

  @Override
  public TypeAdapter adapter() {
    return adapter;
  }

  @Override
  public Cost cost() {
    return estimateCost(attr);
  }

  @Override
  public Val eval(Activation ctx) {
    try {
      return adapter.nativeToValue(attr.resolve(ctx));
    } catch (Exception e) {
      return newErr(e, e.toString());
    }
  }

  @Override
  public Object qualify(Activation ctx, Object obj) {
    return attr.qualify(ctx, obj);
  }

  @Override
  public Object resolve(Activation ctx) {
    return attr.resolve(ctx);
  }

  @Override
  public String toString() {
    return "EvalAttr{" + "id=" + id + ", attr=" + attr + '}';
  }
}
