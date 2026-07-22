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
import static org.projectnessie.cel.common.types.Err.noSuchKey;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.interpreter.ValueSignal.signal;

import java.util.Map;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Mapper;
import org.projectnessie.cel.common.types.traits.Sizer;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.functions.Overload;

/** Shared exact-map operations over an already-resolved host value. */
final class NativeMapSources {
  static final Object ABSENT = new Object();

  private NativeMapSources() {}

  static int size(NativeMapSourceCapability source, Object raw) {
    try {
      if (source.exactMapSource() && raw instanceof Map<?, ?> map) {
        return map.size();
      }
      Val materialized = materializedMap(source, raw);
      if (materialized instanceof Sizer sizer) {
        return sizer.nativeSize();
      }
      throw signal(newErr("got '%s', expected map type", materialized.getClass().getName()));
    } catch (ValueSignal failure) {
      throw failure;
    } catch (Exception failure) {
      throw signal(newErr(failure, failure.toString()));
    }
  }

  /**
   * Returns the raw value, including Java null for present null, or {@link #ABSENT} for a missing
   * key.
   */
  static Object lookup(NativeMapSourceCapability source, Object raw, Object hostKey, Val celKey) {
    try {
      if (source.exactMapSource() && raw instanceof Map<?, ?> map) {
        Object value = map.get(hostKey);
        return value != null || map.containsKey(hostKey) ? value : ABSENT;
      }
      Mapper materialized = materializedMapper(source, raw);
      Val value = materialized.find(celKey);
      return value != null ? value : ABSENT;
    } catch (ValueSignal failure) {
      throw failure;
    } catch (Exception failure) {
      throw signal(newErr(failure, failure.toString()));
    }
  }

  static Val checkedValue(NativeMapSourceCapability source, Object raw, Val celKey) {
    Mapper materialized = materializedMapper(source, raw);
    Val value = materialized.find(celKey);
    if (value == null) {
      throw signal(noSuchKey(celKey));
    }
    return value;
  }

  private static Mapper materializedMapper(NativeMapSourceCapability source, Object raw) {
    Val materialized = materializedMap(source, raw);
    if (materialized instanceof Mapper mapper) {
      return mapper;
    }
    throw signal(newErr("got '%s', expected map type", materialized.getClass().getName()));
  }

  private static Val materializedMap(NativeMapSourceCapability source, Object raw) {
    Val materialized = source.materializeResolvedMap(raw);
    if (isError(materialized) || isUnknown(materialized)) {
      throw signal(materialized);
    }
    return materialized;
  }
}

final class NativeMapSize extends EvalUnary implements NativeIntCapability {
  private final NativeMapSourceCapability source;

  NativeMapSize(
      long id, String function, String overload, Interpretable operand, Overload implementation) {
    super(id, function, overload, operand, implementation.operandTrait, implementation.unary);
    this.source = (NativeMapSourceCapability) operand;
  }

  @Override
  public long evalInt(Activation activation) {
    Object raw;
    try {
      raw = source.evalRaw(activation);
    } catch (ValueSignal failure) {
      raw = failure.value;
    }
    return NativeMapSources.size(source, raw);
  }
}

abstract class NativeMapIndex extends NativeScalarAttr {
  final NativeMapSourceCapability source;
  final Object hostKey;
  final Val celKey;

  NativeMapIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeMapSourceCapability source,
      Object hostKey,
      Val celKey) {
    super(id, adapter, establishedAttribute, null);
    this.source = source;
    this.hostKey = hostKey;
    this.celKey = celKey;
  }

  final Object resolveMap(Activation activation) {
    Object raw;
    try {
      raw = source.evalRaw(activation);
    } catch (ValueSignal failure) {
      raw = failure.value;
    }
    return raw;
  }

  final Object selectValue(Object raw) {
    Object value = NativeMapSources.lookup(source, raw, hostKey, celKey);
    if (value == NativeMapSources.ABSENT) {
      throw signal(noSuchKey(celKey));
    }
    return value;
  }

  final Val checkedValue(Object raw) {
    return NativeMapSources.checkedValue(source, raw, celKey);
  }
}

final class NativeBooleanMapIndex extends NativeMapIndex implements NativeBooleanCapability {
  NativeBooleanMapIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeMapSourceCapability source,
      Object hostKey,
      Val celKey) {
    super(id, adapter, establishedAttribute, source, hostKey, celKey);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    Object raw = resolveMap(activation);
    Object value = selectValue(raw);
    return value instanceof Boolean bool
        ? bool
        : NativeSupport.booleanValue(adapter, checkedValue(raw));
  }
}

final class NativeIntMapIndex extends NativeMapIndex implements NativeIntCapability {
  NativeIntMapIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeMapSourceCapability source,
      Object hostKey,
      Val celKey) {
    super(id, adapter, establishedAttribute, source, hostKey, celKey);
  }

  @Override
  public long evalInt(Activation activation) {
    Object raw = resolveMap(activation);
    Object value = selectValue(raw);
    if (value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long) {
      return ((Number) value).longValue();
    }
    return NativeSupport.intValue(adapter, checkedValue(raw));
  }
}

final class NativeUintMapIndex extends NativeMapIndex implements NativeUintCapability {
  NativeUintMapIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeMapSourceCapability source,
      Object hostKey,
      Val celKey) {
    super(id, adapter, establishedAttribute, source, hostKey, celKey);
  }

  @Override
  public long evalUint(Activation activation) {
    Object raw = resolveMap(activation);
    Object value = selectValue(raw);
    if (value instanceof Long bits) {
      return bits;
    }
    if (value instanceof ULong unsigned) {
      return unsigned.longValue();
    }
    return NativeSupport.uintValue(adapter, checkedValue(raw));
  }
}

final class NativeDoubleMapIndex extends NativeMapIndex implements NativeDoubleCapability {
  NativeDoubleMapIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeMapSourceCapability source,
      Object hostKey,
      Val celKey) {
    super(id, adapter, establishedAttribute, source, hostKey, celKey);
  }

  @Override
  public double evalDouble(Activation activation) {
    Object raw = resolveMap(activation);
    Object value = selectValue(raw);
    if (value instanceof Float || value instanceof Double) {
      return ((Number) value).doubleValue();
    }
    return NativeSupport.doubleValue(adapter, checkedValue(raw));
  }
}

final class NativeStringMapIndex extends NativeMapIndex implements NativeStringCapability {
  NativeStringMapIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeMapSourceCapability source,
      Object hostKey,
      Val celKey) {
    super(id, adapter, establishedAttribute, source, hostKey, celKey);
  }

  @Override
  public String evalString(Activation activation) {
    Object raw = resolveMap(activation);
    Object value = selectValue(raw);
    return value instanceof String string
        ? string
        : NativeSupport.stringValue(adapter, checkedValue(raw));
  }
}

final class NativeNullMapIndex extends NativeMapIndex implements NativeNullCapability {
  NativeNullMapIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeMapSourceCapability source,
      Object hostKey,
      Val celKey) {
    super(id, adapter, establishedAttribute, source, hostKey, celKey);
  }

  @Override
  public void evalNull(Activation activation) {
    Object raw = resolveMap(activation);
    if (selectValue(raw) != null) {
      NativeSupport.nullValue(adapter, checkedValue(raw));
    }
  }
}

final class NativeMapMembership extends EvalBinary implements NativeBooleanCapability {
  private final NativeMapSourceCapability source;
  private final Object hostKey;
  private final Val celKey;

  NativeMapMembership(
      long id,
      Interpretable key,
      Interpretable map,
      Object hostKey,
      Val celKey,
      Overload implementation) {
    super(
        id,
        Operator.In.id,
        Overloads.InMap,
        key,
        map,
        implementation.operandTrait,
        implementation.binary);
    this.source = (NativeMapSourceCapability) map;
    this.hostKey = hostKey;
    this.celKey = celKey;
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    Object raw;
    try {
      raw = source.evalRaw(activation);
    } catch (ValueSignal failure) {
      raw = failure.value;
    }
    return NativeMapSources.lookup(source, raw, hostKey, celKey) != NativeMapSources.ABSENT;
  }
}

abstract class NativeMapAggregateIndex extends AbstractEval {
  final NativeMapSourceCapability source;
  final Object hostKey;
  final Val celKey;
  final CheckedAggregateMaterializer materializer;

  NativeMapAggregateIndex(
      long id,
      NativeMapSourceCapability source,
      Object hostKey,
      Val celKey,
      CheckedAggregateMaterializer materializer) {
    super(id);
    this.source = source;
    this.hostKey = hostKey;
    this.celKey = celKey;
    this.materializer = materializer;
  }

  final Object selectRaw(Activation activation) {
    Object raw;
    try {
      raw = source.evalRaw(activation);
    } catch (ValueSignal failure) {
      raw = failure.value;
    }
    Object value = NativeMapSources.lookup(source, raw, hostKey, celKey);
    if (value == NativeMapSources.ABSENT) {
      throw signal(noSuchKey(celKey));
    }
    return value;
  }

  @Override
  public Val eval(Activation activation) {
    try {
      Object value = selectRaw(activation);
      return value instanceof Val val && (isError(val) || isUnknown(val))
          ? val
          : materializer.materialize(value);
    } catch (ValueSignal failure) {
      return failure.value;
    }
  }
}

final class NativeMapListIndex extends NativeMapAggregateIndex
    implements NativeListSourceCapability {
  NativeMapListIndex(
      long id,
      NativeMapSourceCapability source,
      Object hostKey,
      Val celKey,
      CheckedAggregateMaterializer materializer) {
    super(id, source, hostKey, celKey, materializer);
  }

  @Override
  public Object evalRaw(Activation activation) {
    return selectRaw(activation);
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

final class NativeMapMapIndex extends NativeMapAggregateIndex implements NativeMapSourceCapability {
  NativeMapMapIndex(
      long id,
      NativeMapSourceCapability source,
      Object hostKey,
      Val celKey,
      CheckedAggregateMaterializer materializer) {
    super(id, source, hostKey, celKey, materializer);
  }

  @Override
  public Object evalRaw(Activation activation) {
    return selectRaw(activation);
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
