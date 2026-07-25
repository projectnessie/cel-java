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
import java.util.SortedMap;
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
  private static final Object INCOMPATIBLE_PROBE = new Object();

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
        return exactLookup(map, hostKey, celKey);
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

  static Object exactLookup(Map<?, ?> map, Object hostKey, Val celKey) {
    return switch (celKey.type().typeEnum()) {
      case Int -> exactIntLookup(map, celKey.intValue());
      case Uint -> exactUintLookup(map, celKey.intValue());
      default -> exactSingleLookup(map, hostKey);
    };
  }

  static Object exactIntLookup(Map<?, ?> map, long key) {
    boolean inferDuplicates = !(map instanceof SortedMap<?, ?>);
    Object found = ABSENT;
    Object candidate = exactIntProbe(map, key);
    if (candidate != INCOMPATIBLE_PROBE) {
      found = candidate;
    }
    if (key >= Integer.MIN_VALUE && key <= Integer.MAX_VALUE) {
      candidate = exactIntProbe(map, (int) key);
      if (candidate != ABSENT && candidate != INCOMPATIBLE_PROBE) {
        if (found != ABSENT && inferDuplicates) {
          throw signal(newErr("Failed with repeated key"));
        }
        if (found == ABSENT) {
          found = candidate;
        }
      }
    }
    if (key >= Short.MIN_VALUE && key <= Short.MAX_VALUE) {
      candidate = exactIntProbe(map, (short) key);
      if (candidate != ABSENT && candidate != INCOMPATIBLE_PROBE) {
        if (found != ABSENT && inferDuplicates) {
          throw signal(newErr("Failed with repeated key"));
        }
        if (found == ABSENT) {
          found = candidate;
        }
      }
    }
    if (key >= Byte.MIN_VALUE && key <= Byte.MAX_VALUE) {
      candidate = exactIntProbe(map, (byte) key);
      if (candidate != ABSENT && candidate != INCOMPATIBLE_PROBE) {
        if (found != ABSENT && inferDuplicates) {
          throw signal(newErr("Failed with repeated key"));
        }
        if (found == ABSENT) {
          found = candidate;
        }
      }
    }
    // A sorted map may use a comparator that makes several wrapper probes aliases for one entry.
    // It therefore cannot support bounded duplicate inference without an O(n) entry scan.
    return found;
  }

  static Object exactSingleLookup(Map<?, ?> map, Object hostKey) {
    Object value = map.get(hostKey);
    return value != null || map.containsKey(hostKey) ? value : ABSENT;
  }

  static Object exactUintLookup(Map<?, ?> map, long key) {
    boolean inferDuplicates = !(map instanceof SortedMap<?, ?>);
    Object found = ABSENT;
    Object candidate = exactIntProbe(map, key);
    if (candidate != INCOMPATIBLE_PROBE) {
      found = candidate;
    }
    candidate = exactIntProbe(map, ULong.valueOf(key));
    if (candidate != ABSENT && candidate != INCOMPATIBLE_PROBE) {
      if (found != ABSENT && inferDuplicates) {
        throw signal(newErr("Failed with repeated key"));
      }
      if (found == ABSENT) {
        found = candidate;
      }
    }
    // A sorted map may use a comparator that makes both uint probes aliases for one entry.
    return found;
  }

  private static Object exactIntProbe(Map<?, ?> map, Object hostKey) {
    try {
      return exactSingleLookup(map, hostKey);
    } catch (ClassCastException incompatibleProbe) {
      return INCOMPATIBLE_PROBE;
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

  static Map<?, ?> strictExactMap(Object raw) {
    if (raw instanceof Val val && (isError(val) || isUnknown(val))) {
      throw signal(val);
    }
    if (raw instanceof Map<?, ?> map) {
      return map;
    }
    throw signal(
        newErr(
            "got '%s', expected certified exact map type",
            raw == null ? "null" : raw.getClass().getName()));
  }

  static ValueSignal incompatibleSelected(Object value, String expected) {
    return signal(
        newErr(
            "exact map value of Java type '%s' is incompatible with checked CEL %s",
            value == null ? "null" : value.getClass().getName(), expected));
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

final class NativeMapDynamicKey {
  enum Kind {
    STRING("string"),
    BOOLEAN("bool"),
    INT("int");

    private final String celName;

    Kind(String celName) {
      this.celName = celName;
    }
  }

  private final Kind kind;
  private final Object capability;

  private NativeMapDynamicKey(Kind kind, Object capability) {
    this.kind = kind;
    this.capability = capability;
  }

  static NativeMapDynamicKey string(NativeStringCapability capability) {
    return new NativeMapDynamicKey(Kind.STRING, capability);
  }

  static NativeMapDynamicKey bool(NativeBooleanCapability capability) {
    return new NativeMapDynamicKey(Kind.BOOLEAN, capability);
  }

  static NativeMapDynamicKey integer(NativeIntCapability capability) {
    return new NativeMapDynamicKey(Kind.INT, capability);
  }

  Object lookup(Map<?, ?> map, Activation activation) {
    return switch (kind) {
      case STRING -> {
        String key = ((NativeStringCapability) capability).evalString(activation);
        if (key == null) {
          throw signal(newErr("null is not a valid CEL string map key"));
        }
        Object value = NativeMapSources.exactSingleLookup(map, key);
        if (value == NativeMapSources.ABSENT) {
          throw signal(noSuchKey(org.projectnessie.cel.common.types.StringT.stringOf(key)));
        }
        yield value;
      }
      case BOOLEAN -> {
        boolean key = ((NativeBooleanCapability) capability).evalBoolean(activation);
        Object value = NativeMapSources.exactSingleLookup(map, key);
        if (value == NativeMapSources.ABSENT) {
          throw signal(
              noSuchKey(
                  key
                      ? org.projectnessie.cel.common.types.BoolT.True
                      : org.projectnessie.cel.common.types.BoolT.False));
        }
        yield value;
      }
      case INT -> {
        long key = ((NativeIntCapability) capability).evalInt(activation);
        Object value;
        try {
          value = NativeMapSources.exactIntLookup(map, key);
        } catch (ValueSignal failure) {
          throw NativeSupport.propagatedError(failure.value);
        }
        if (value == NativeMapSources.ABSENT) {
          throw signal(noSuchKey(org.projectnessie.cel.common.types.IntT.intOf(key)));
        }
        yield value;
      }
    };
  }

  String celName() {
    return kind.celName;
  }

  Object capability() {
    return capability;
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
  final NativeMapDynamicKey dynamicKey;

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
    this.dynamicKey = null;
  }

  NativeMapIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeMapSourceCapability source,
      NativeMapDynamicKey dynamicKey) {
    super(id, adapter, establishedAttribute, null);
    this.source = source;
    this.hostKey = null;
    this.celKey = null;
    this.dynamicKey = dynamicKey;
  }

  final Object resolveMap(Activation activation) {
    try {
      return source.evalRaw(activation);
    } catch (ValueSignal failure) {
      return failure.value;
    } catch (Exception failure) {
      throw signal(newErr(failure, failure.toString()));
    }
  }

  final Selection selectValue(Activation activation) {
    Object raw = resolveMap(activation);
    if (dynamicKey != null) {
      Map<?, ?> map = NativeMapSources.strictExactMap(raw);
      Object value;
      try {
        value = dynamicKey.lookup(map, activation);
      } catch (ValueSignal failure) {
        if (isError(failure.value) || isUnknown(failure.value)) {
          throw failure;
        }
        throw signal(
            newErr(
                "exact map key of CEL type '%s' is incompatible with checked CEL %s",
                failure.value.type().typeName(), dynamicKey.celName()));
      } catch (Exception failure) {
        throw signal(newErr(failure, failure.toString()));
      }
      return new Selection(raw, value, null);
    }
    Object value;
    try {
      value = NativeMapSources.lookup(source, raw, hostKey, celKey);
    } catch (ValueSignal failure) {
      throw NativeSupport.propagatedError(failure.value);
    }
    if (value == NativeMapSources.ABSENT) {
      throw signal(noSuchKey(celKey));
    }
    return new Selection(raw, value, celKey);
  }

  final Val checkedValue(Selection selection) {
    if (selection.celKey == null) {
      throw NativeMapSources.incompatibleSelected(selection.value, "scalar");
    }
    return NativeMapSources.checkedValue(source, selection.raw, selection.celKey);
  }

  record Selection(Object raw, Object value, Val celKey) {}
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

  NativeBooleanMapIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeMapSourceCapability source,
      NativeMapDynamicKey dynamicKey) {
    super(id, adapter, establishedAttribute, source, dynamicKey);
  }

  @Override
  public boolean evalBoolean(Activation activation) {
    Selection selection = selectValue(activation);
    return selection.value() instanceof Boolean bool
        ? bool
        : NativeSupport.booleanValue(adapter, checkedValue(selection));
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

  NativeIntMapIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeMapSourceCapability source,
      NativeMapDynamicKey dynamicKey) {
    super(id, adapter, establishedAttribute, source, dynamicKey);
  }

  @Override
  public long evalInt(Activation activation) {
    Selection selection = selectValue(activation);
    Object value = selection.value();
    if (value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long) {
      return ((Number) value).longValue();
    }
    return NativeSupport.intValue(adapter, checkedValue(selection));
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

  NativeUintMapIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeMapSourceCapability source,
      NativeMapDynamicKey dynamicKey) {
    super(id, adapter, establishedAttribute, source, dynamicKey);
  }

  @Override
  public long evalUint(Activation activation) {
    Selection selection = selectValue(activation);
    Object value = selection.value();
    if (value instanceof Long bits) {
      return bits;
    }
    if (value instanceof ULong unsigned) {
      return unsigned.longValue();
    }
    return NativeSupport.uintValue(adapter, checkedValue(selection));
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

  NativeDoubleMapIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeMapSourceCapability source,
      NativeMapDynamicKey dynamicKey) {
    super(id, adapter, establishedAttribute, source, dynamicKey);
  }

  @Override
  public double evalDouble(Activation activation) {
    Selection selection = selectValue(activation);
    Object value = selection.value();
    if (value instanceof Float || value instanceof Double) {
      return ((Number) value).doubleValue();
    }
    return NativeSupport.doubleValue(adapter, checkedValue(selection));
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

  NativeStringMapIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeMapSourceCapability source,
      NativeMapDynamicKey dynamicKey) {
    super(id, adapter, establishedAttribute, source, dynamicKey);
  }

  @Override
  public String evalString(Activation activation) {
    Selection selection = selectValue(activation);
    return selection.value() instanceof String string
        ? string
        : NativeSupport.stringValue(adapter, checkedValue(selection));
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

  NativeNullMapIndex(
      long id,
      TypeAdapter adapter,
      Attribute establishedAttribute,
      NativeMapSourceCapability source,
      NativeMapDynamicKey dynamicKey) {
    super(id, adapter, establishedAttribute, source, dynamicKey);
  }

  @Override
  public void evalNull(Activation activation) {
    Selection selection = selectValue(activation);
    if (selection.value() != null) {
      NativeSupport.nullValue(adapter, checkedValue(selection));
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
