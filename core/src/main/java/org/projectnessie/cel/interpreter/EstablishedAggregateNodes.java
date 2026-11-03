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

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Err.valOrErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.Coster.costOf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.projectnessie.cel.OperationAbortedException.Phase;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.IterableT;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.ListT;
import org.projectnessie.cel.common.types.OptionalT;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Mapper;
import org.projectnessie.cel.common.types.traits.Sizer;
import org.projectnessie.cel.common.types.traits.Trait;

class EvalList extends AbstractEval implements Coster {
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
  public Val eval(Activation ctx) {
    var controller = ActivationControls.controller(ctx);
    List<Val> elemVals = new ArrayList<>(elems.length);
    // If any argument is unknown or error early terminate.
    for (int i = 0; i < elems.length; i++) {
      controller.checkpoint(Phase.EVALUATE);
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
    return Interpretable.sumOfCost(elems);
  }

  @Override
  public String toString() {
    return "EvalList{" + "id=" + id + ", elems=" + Arrays.toString(elems) + '}';
  }
}

class EvalListFold extends AbstractEval implements Coster {
  final String iterVar;
  final String iterVar2;
  final Interpretable iterRange;
  final Interpretable filter;
  final Interpretable transform;
  final TypeAdapter adapter;

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

  @SuppressWarnings("DuplicatedCode")
  @Override
  public Val eval(Activation ctx) {
    var controller = ActivationControls.controller(ctx);
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
      controller.checkpoint(Phase.EVALUATE);
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

  int listCapacity(Val foldRange) {
    if (foldRange.type().hasTrait(Trait.SizerType)) {
      int size = ((Sizer) foldRange).nativeSize();
      if (size > 0) {
        return size;
      }
    }
    return 0;
  }

  @SuppressWarnings("DuplicatedCode")
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

class EvalFold extends AbstractEval implements Coster {
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
  @SuppressWarnings("DuplicatedCode")
  @Override
  public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
    var controller = ActivationControls.controller(ctx);
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
      controller.checkpoint(Phase.EVALUATE);
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
  @SuppressWarnings("DuplicatedCode")
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
