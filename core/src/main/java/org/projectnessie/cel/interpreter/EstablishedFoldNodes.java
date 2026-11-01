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
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.IterableT;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.ListT;
import org.projectnessie.cel.common.types.MapT;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Mapper;
import org.projectnessie.cel.common.types.traits.Sizer;
import org.projectnessie.cel.common.types.traits.Trait;

/** Package-private established fold nodes. */
final class EvalMapFold extends AbstractEval implements Coster {
  final String iterVar;
  final String iterVar2;
  final Interpretable iterRange;
  final Interpretable filter;
  final Interpretable transform;
  final TypeAdapter adapter;

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

  @SuppressWarnings("DuplicatedCode")
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

  int mapCapacity(Val foldRange) {
    if (foldRange.type().hasTrait(Trait.SizerType)) {
      int size = ((Sizer) foldRange).nativeSize();
      if (size > 0) {
        long capacity = size * 4L / 3 + 1;
        return capacity <= Integer.MAX_VALUE ? (int) capacity : Integer.MAX_VALUE;
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
  @SuppressWarnings("DuplicatedCode")
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

final class EvalExhaustiveListFold extends AbstractEval implements Coster {
  private final EvalListFold fold;

  EvalExhaustiveListFold(EvalListFold fold) {
    super(fold.id);
    this.fold = fold;
  }

  @SuppressWarnings("DuplicatedCode")
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
        if (result == null) {
          result = noSuchOverload(null, Operator.Conditional.id, include);
        }
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

final class EvalExhaustiveMapFold extends AbstractEval implements Coster {
  private final EvalMapFold fold;

  EvalExhaustiveMapFold(EvalMapFold fold) {
    super(fold.id);
    this.fold = fold;
  }

  @SuppressWarnings("DuplicatedCode")
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
        if (result == null) {
          result = noSuchOverload(null, Operator.Conditional.id, include);
        }
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
