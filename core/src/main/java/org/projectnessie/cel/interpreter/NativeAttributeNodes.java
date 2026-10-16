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

import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;

abstract class NativeScalarAttr extends EvalAttr {
  private final long semanticId;
  private final Attribute partialAttribute;

  NativeScalarAttr(long id, TypeAdapter adapter, Attribute attribute, Attribute partialAttribute) {
    super(adapter, attribute);
    this.semanticId = id;
    this.partialAttribute = partialAttribute;
  }

  @Override
  public final long id() {
    return semanticId;
  }

  final Object resolveNative(Activation activation) {
    try {
      return activation instanceof Activation.PartialActivation && partialAttribute != null
          ? partialAttribute.resolve(activation)
          : attr.resolve(activation);
    } catch (ValueSignal valueSignal) {
      throw valueSignal;
    } catch (Exception e) {
      if (e instanceof org.projectnessie.cel.OperationAbortedException aborted) {
        throw aborted;
      }
      throw signal(newErr(e, e.toString()));
    }
  }

  final boolean usesPartialAttribute(Activation activation) {
    return activation instanceof Activation.PartialActivation && partialAttribute != null;
  }
}

class NativeIntAttr extends NativeScalarAttr implements NativeIntCapability {
  NativeIntAttr(long id, TypeAdapter adapter, Attribute attribute, Attribute partialAttribute) {
    super(id, adapter, attribute, partialAttribute);
  }

  @Override
  public long evalInt(Activation activation) {
    return NativeSupport.intValue(adapter, resolveNative(activation));
  }
}

final class NativeUintAttr extends NativeScalarAttr implements NativeUintCapability {
  NativeUintAttr(long id, TypeAdapter adapter, Attribute attribute, Attribute partialAttribute) {
    super(id, adapter, attribute, partialAttribute);
  }

  @Override
  public long evalUint(Activation activation) {
    return NativeSupport.uintValue(adapter, resolveNative(activation));
  }
}

final class NativeBooleanAttr extends NativeScalarAttr implements NativeBooleanCapability {
  NativeBooleanAttr(long id, TypeAdapter adapter, Attribute attribute, Attribute partialAttribute) {
    super(id, adapter, attribute, partialAttribute);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    return NativeSupport.booleanValue(adapter, resolveNative(activation));
  }
}

final class NativeDoubleAttr extends NativeScalarAttr implements NativeDoubleCapability {
  NativeDoubleAttr(long id, TypeAdapter adapter, Attribute attribute, Attribute partialAttribute) {
    super(id, adapter, attribute, partialAttribute);
  }

  @Override
  public double evalDouble(Activation activation) {
    return NativeSupport.doubleValue(adapter, resolveNative(activation));
  }
}

final class NativeStringAttr extends NativeScalarAttr implements NativeStringCapability {
  NativeStringAttr(long id, TypeAdapter adapter, Attribute attribute, Attribute partialAttribute) {
    super(id, adapter, attribute, partialAttribute);
  }

  @Override
  public String evalString(Activation activation) {
    return NativeSupport.stringValue(adapter, resolveNative(activation));
  }
}

final class NativeStringMapObjectField extends NativeScalarAttr implements NativeStringCapability {
  private final NativeMapObjectIndex source;
  private final FieldType fieldType;

  NativeStringMapObjectField(
      long id,
      TypeAdapter adapter,
      Attribute attribute,
      Attribute partialAttribute,
      NativeMapObjectIndex source,
      FieldType fieldType) {
    super(id, adapter, attribute, partialAttribute);
    this.source = source;
    this.fieldType = fieldType;
  }

  @Override
  public String evalString(Activation activation) {
    if (usesPartialAttribute(activation)) {
      return NativeSupport.stringValue(adapter, resolveNative(activation));
    }
    Object target = source.selectRaw(activation);
    if (target == null || target instanceof Val) {
      Val value = source.materializeSelected(target);
      if (isError(value) || isUnknown(value)) {
        throw signal(value);
      }
      target = value.value();
    }
    try {
      return NativeSupport.stringValue(adapter, fieldType.getFrom.getFrom(target));
    } catch (ValueSignal failure) {
      throw failure;
    } catch (Exception failure) {
      if (failure instanceof org.projectnessie.cel.OperationAbortedException aborted) {
        throw aborted;
      }
      throw signal(newErr(failure, failure.toString()));
    }
  }
}

final class NativeNullAttr extends NativeScalarAttr implements NativeNullCapability {
  NativeNullAttr(long id, TypeAdapter adapter, Attribute attribute, Attribute partialAttribute) {
    super(id, adapter, attribute, partialAttribute);
  }

  @Override
  public void evalNull(Activation activation) {
    NativeSupport.nullValue(adapter, resolveNative(activation));
  }
}

/** Checked exact aggregate field whose getter result remains raw until its consumer chooses. */
class EvalExactAggregateFieldAttr extends AbstractEval implements Coster {
  private final Attribute attribute;
  private final Attribute partialAttribute;
  final CheckedAggregateMaterializer materializer;

  EvalExactAggregateFieldAttr(
      long id,
      Attribute attribute,
      Attribute partialAttribute,
      CheckedAggregateMaterializer materializer) {
    super(id);
    this.attribute = attribute;
    this.partialAttribute = partialAttribute;
    this.materializer = materializer;
  }

  final Object resolveRaw(Activation activation) {
    try {
      return activation instanceof Activation.PartialActivation && partialAttribute != null
          ? partialAttribute.resolve(activation)
          : attribute.resolve(activation);
    } catch (ValueSignal failure) {
      throw failure;
    } catch (Exception failure) {
      if (failure instanceof org.projectnessie.cel.OperationAbortedException aborted) {
        throw aborted;
      }
      throw signal(newErr(failure, failure.toString()));
    }
  }

  @Override
  public Val eval(Activation activation) {
    try {
      Object value = resolveRaw(activation);
      return value instanceof Val val && (isError(val) || isUnknown(val))
          ? val
          : materializer.materialize(value);
    } catch (ValueSignal failure) {
      return failure.value;
    }
  }

  @Override
  public Cost cost() {
    return estimateCost(attribute);
  }
}

final class NativeExactListFieldAttr extends EvalExactAggregateFieldAttr
    implements NativeListSourceCapability {
  NativeExactListFieldAttr(
      long id,
      Attribute attribute,
      Attribute partialAttribute,
      CheckedAggregateMaterializer materializer) {
    super(id, attribute, partialAttribute, materializer);
  }

  @Override
  public Object evalRaw(Activation activation) {
    return resolveRaw(activation);
  }

  @Override
  public Val materializeResolvedList(Object value) {
    return value instanceof Val val && (isError(val) || isUnknown(val))
        ? val
        : materializer.materialize(value);
  }

  @Override
  public Val materializeResolvedElement(Object value) {
    return materializer.materializeListElement(value);
  }

  @Override
  public boolean exactListSource() {
    return true;
  }
}

final class NativeExactMapFieldAttr extends EvalExactAggregateFieldAttr
    implements NativeMapSourceCapability {
  NativeExactMapFieldAttr(
      long id,
      Attribute attribute,
      Attribute partialAttribute,
      CheckedAggregateMaterializer materializer) {
    super(id, attribute, partialAttribute, materializer);
  }

  @Override
  public Object evalRaw(Activation activation) {
    return resolveRaw(activation);
  }

  @Override
  public Val materializeResolvedMap(Object value) {
    return value instanceof Val val && (isError(val) || isUnknown(val))
        ? val
        : materializer.materialize(value);
  }

  @Override
  public boolean exactMapSource() {
    return true;
  }
}
