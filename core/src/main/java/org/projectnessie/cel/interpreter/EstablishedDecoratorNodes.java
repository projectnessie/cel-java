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
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.Interpretable.calExhaustiveBinaryOpsCost;

import java.util.Objects;
import java.util.Set;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.AttributeFactory.ConstantQualifier;
import org.projectnessie.cel.interpreter.AttributeFactory.ConstantQualifierEquator;
import org.projectnessie.cel.interpreter.AttributeFactory.Qualifier;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableAttribute;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableConst;
import org.projectnessie.cel.interpreter.InterpretableDecorator.EvalObserver;

/** Package-private established decorator nodes. */
final class EvalSetMembership extends AbstractEval implements Coster {
  private final Interpretable inst;
  private final Interpretable arg;
  private final String argTypeName;
  private final Set<Val> valueSet;

  EvalSetMembership(Interpretable inst, Interpretable arg, String argTypeName, Set<Val> valueSet) {
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

final class EvalExhaustiveOr extends AbstractEvalLhsRhs {
  // TODO combine with EvalOr
  EvalExhaustiveOr(long id, Interpretable lhs, Interpretable rhs) {
    super(id, lhs, rhs);
  }

  /** Eval implements the Interpretable interface method. */
  @SuppressWarnings("DuplicatedCode")
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

final class EvalExhaustiveAnd extends AbstractEvalLhsRhs {
  // TODO combine with EvalAnd
  EvalExhaustiveAnd(long id, Interpretable lhs, Interpretable rhs) {
    super(id, lhs, rhs);
  }

  /** Eval implements the Interpretable interface method. */
  @SuppressWarnings("DuplicatedCode")
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
    Val cVal = attr.expr().eval(ctx);
    Object tVal = attr.truthy().resolve(ctx);
    Object fVal = attr.falsy().resolve(ctx);
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
