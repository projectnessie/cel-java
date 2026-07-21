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
package org.projectnessie.cel.common.types;

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.TypeT.TypeType;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;

import com.google.protobuf.Any;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.util.HashMap;
import java.util.Map;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Container;
import org.projectnessie.cel.common.types.traits.Indexer;
import org.projectnessie.cel.common.types.traits.Mapper;
import org.projectnessie.cel.common.types.traits.Sizer;
import org.projectnessie.cel.common.types.traits.Trait;

public abstract class MapT extends BaseVal implements Mapper, Container, Indexer, IterableT, Sizer {
  /** MapType singleton. */
  public static final Type MapType =
      TypeT.newTypeValue(
          TypeEnum.Map,
          Trait.ContainerType,
          Trait.IndexerType,
          Trait.IterableType,
          Trait.SizerType);

  public static Val newWrappedMap(TypeAdapter adapter, Map<Val, Val> value) {
    return new ValMapT(adapter, value);
  }

  @SuppressWarnings("unchecked")
  public static Val newMaybeWrappedMap(TypeAdapter adapter, Map<?, ?> value) {
    boolean alreadyWrapped = true;
    for (Map.Entry<?, ?> entry : value.entrySet()) {
      if (!(entry.getKey() instanceof Val key) || !(entry.getValue() instanceof Val)) {
        alreadyWrapped = false;
        break;
      }
      if (key.type().typeEnum() == TypeEnum.Null) {
        return newErr("unsupported key type");
      }
    }
    if (alreadyWrapped) {
      return newWrappedMap(adapter, (Map<Val, Val>) value);
    }

    Map<Val, Object> newMap = new HashMap<>(value.size() * 4 / 3 + 1);
    for (Map.Entry<?, ?> entry : value.entrySet()) {
      Val k = adapter.nativeToValue(entry.getKey());
      if (k.type().typeEnum() == TypeEnum.Null) {
        return newErr("unsupported key type");
      }
      int previousSize = newMap.size();
      newMap.put(k, entry.getValue());
      if (newMap.size() == previousSize) {
        // Prevent duplicate keys, error out.
        return newErr("Failed with repeated key");
      }
    }
    return new NativeMapT(adapter, newMap);
  }

  public static boolean isSupportedLiteralKeyType(Val key) {
    return switch (key.type().typeEnum()) {
      case Bool, Int, String, Uint -> true;
      default -> false;
    };
  }

  @Override
  public Type type() {
    return MapType;
  }

  protected final Val mapEqual(Val other) {
    if (!(other instanceof MapT o)) {
      return False;
    }
    if (nativeSize() != o.nativeSize()) {
      return False;
    }

    Iterable<?> entries = mapEntries();
    if (entries != null) {
      for (Object entry : entries) {
        Val key = mapEntryKey(entry);
        Val value = mapEntryValue(entry);
        Val oVal = o.find(key);
        if (oVal == null) {
          return False;
        }
        Val equal = mapValueEqual(value, oVal);
        if (equal != True) {
          return equal;
        }
      }
      return True;
    }

    IteratorT myIter = iterator();
    while (myIter.hasNext() == True) {
      Val key = myIter.next();

      Val val = get(key);
      Val oVal = o.find(key);
      if (oVal == null) {
        return False;
      }
      Val eq = mapValueEqual(val, oVal);
      if (eq == True) {
        continue;
      }
      return eq;
    }
    return True;
  }

  /** Returns backing entries when this implementation can traverse them directly. */
  protected Iterable<?> mapEntries() {
    return null;
  }

  /** Adapts the key of an entry returned by {@link #mapEntries()}. */
  protected Val mapEntryKey(Object entry) {
    throw new UnsupportedOperationException();
  }

  /** Adapts the value of an entry returned by {@link #mapEntries()}. */
  protected Val mapEntryValue(Object entry) {
    throw new UnsupportedOperationException();
  }

  protected static Val mapValueEqual(Val value, Val otherValue) {
    if (isError(value)) {
      return value;
    }
    if (isError(otherValue)) {
      return otherValue;
    }

    Val equal = value.equal(otherValue);
    if (equal == True || equal == False) {
      return equal;
    }
    if (!value.type().equals(otherValue.type())) {
      return False;
    }
    equal = value.equal(otherValue);
    return equal instanceof Err ? equal : boolOf(equal == True);
  }

  @Override
  public final boolean equals(Object other) {
    return other instanceof MapT o && mapEqual(o) == True;
  }

  @Override
  public final int hashCode() {
    int hash = 0;
    Iterable<?> entries = mapEntries();
    if (entries != null) {
      for (Object entry : entries) {
        Val key = mapEntryKey(entry);
        Val value = mapEntryValue(entry);
        hash += key.hashCode() ^ (value != null ? value.hashCode() : 0);
      }
      return hash;
    }

    IteratorT keys = iterator();
    while (keys.hasNext() == True) {
      Val key = keys.next();
      Val value = find(key);
      hash += key.hashCode() ^ (value != null ? value.hashCode() : 0);
    }
    return hash;
  }

  static final class ValMapT extends MapT {

    private final TypeAdapter adapter;
    private final Map<Val, Val> map;

    ValMapT(TypeAdapter adapter, Map<Val, Val> map) {
      this.adapter = adapter;
      this.map = map;
    }

    @SuppressWarnings({"removal", "unchecked"})
    @Override
    public <T> T convertToNative(Class<T> typeDesc) {
      if (Map.class.isAssignableFrom(typeDesc) || typeDesc == Object.class) {
        return (T) toJavaMap();
      }
      if (typeDesc == Struct.class) {
        return (T) toPbStruct();
      }
      if (typeDesc == Value.class) {
        return (T) toPbValue();
      }
      if (typeDesc == Any.class) {
        Struct v = toPbStruct();
        //        DynamicMessage dyn = DynamicMessage.newBuilder(v).build();
        //        return (T) Any.newBuilder().mergeFrom(dyn).build();
        return (T)
            Any.newBuilder()
                .setTypeUrl("type.googleapis.com/google.protobuf.Struct")
                .setValue(v.toByteString())
                .build();
      }
      throw new RuntimeException(
          String.format(
              "native type conversion error from '%s' to '%s'", MapType, typeDesc.getName()));
    }

    private Value toPbValue() {
      return Value.newBuilder().setStructValue(toPbStruct()).build();
    }

    private Struct toPbStruct() {
      Struct.Builder struct = Struct.newBuilder();
      map.forEach(
          (k, v) -> {
            if (k.type().typeEnum() != TypeEnum.String) {
              throw new IllegalArgumentException("bad key type");
            }
            struct.putFields(k.value().toString(), adapter.valueToNative(v, Value.class));
          });
      return struct.build();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map toJavaMap() {
      Map r = new HashMap();
      map.forEach((k, v) -> r.put(k.value(), v.value()));
      return r;
    }

    @Override
    public Val convertToType(Type typeValue) {
      if (typeValue == MapType) {
        return this;
      }
      if (typeValue == TypeType) {
        return MapType;
      }
      return newTypeConversionError(MapType, typeValue);
    }

    @Override
    public IteratorT iterator() {
      return IteratorT.javaIterator(adapter, map.keySet().iterator());
    }

    @Override
    public Val equal(Val other) {
      return mapEqual(other);
    }

    @Override
    public Object value() {
      // TODO this is expensive :(
      Map<Object, Object> nativeMap = toJavaMap();
      return nativeMap;
    }

    @Override
    public Val contains(Val value) {
      if (isUnknownOrError(value)) {
        return value;
      }
      return boolOf(find(value) != null);
    }

    @Override
    public Val get(Val index) {
      return find(index);
    }

    @Override
    public Val size() {
      return IntT.intOf(map.size());
    }

    @Override
    public int nativeSize() {
      return map.size();
    }

    @Override
    protected Iterable<?> mapEntries() {
      return map.entrySet();
    }

    @Override
    protected Val mapEntryKey(Object entry) {
      return (Val) ((Map.Entry<?, ?>) entry).getKey();
    }

    @Override
    protected Val mapEntryValue(Object entry) {
      return (Val) ((Map.Entry<?, ?>) entry).getValue();
    }

    @Override
    public Val find(Val key) {
      // Note: no special handling for heterogenous numeric map keys needed, the Val type
      // implementations implement .hashCode() and .equals() do deal with heterogenous numeric keys.
      return map.get(key);
    }

    @Override
    public String toString() {
      return "JavaMapT{" + "adapter=" + adapter + ", map=" + map + '}';
    }
  }

  static final class NativeMapT extends MapT {
    private final TypeAdapter adapter;
    private final Map<Val, Object> map;

    NativeMapT(TypeAdapter adapter, Map<Val, Object> map) {
      this.adapter = adapter;
      this.map = map;
    }

    @SuppressWarnings("removal")
    @Override
    public <T> T convertToNative(Class<T> typeDesc) {
      return adapter.valueToNative(newWrappedMap(adapter, adaptedMap()), typeDesc);
    }

    @Override
    public Val convertToType(Type typeValue) {
      if (typeValue == MapType) {
        return this;
      }
      if (typeValue == TypeType) {
        return MapType;
      }
      return newTypeConversionError(MapType, typeValue);
    }

    @Override
    public IteratorT iterator() {
      return IteratorT.javaIterator(adapter, map.keySet().iterator());
    }

    @Override
    public Val equal(Val other) {
      if (other instanceof NativeMapT nativeOther) {
        if (map.size() != nativeOther.map.size()) {
          return False;
        }
        for (Map.Entry<Val, Object> entry : map.entrySet()) {
          Object otherRawValue = nativeOther.map.get(entry.getKey());
          if (otherRawValue == null && !nativeOther.map.containsKey(entry.getKey())) {
            return False;
          }
          Val equal =
              mapValueEqual(
                  adapter.nativeToValue(entry.getValue()),
                  nativeOther.adapter.nativeToValue(otherRawValue));
          if (equal != True) {
            return equal;
          }
        }
        return True;
      }
      return mapEqual(other);
    }

    @Override
    public Object value() {
      Map<Object, Object> nativeMap = new HashMap<>(map.size() * 4 / 3 + 1);
      map.forEach(
          (key, rawValue) -> nativeMap.put(key.value(), adapter.nativeToValue(rawValue).value()));
      return nativeMap;
    }

    @Override
    public Val contains(Val value) {
      if (isUnknownOrError(value)) {
        return value;
      }
      Object rawValue = map.get(value);
      return boolOf(rawValue != null || map.containsKey(value));
    }

    @Override
    public Val get(Val index) {
      return find(index);
    }

    @Override
    public Val size() {
      return IntT.intOf(map.size());
    }

    @Override
    public int nativeSize() {
      return map.size();
    }

    @Override
    protected Iterable<?> mapEntries() {
      return map.entrySet();
    }

    @Override
    protected Val mapEntryKey(Object entry) {
      return (Val) ((Map.Entry<?, ?>) entry).getKey();
    }

    @Override
    protected Val mapEntryValue(Object entry) {
      return adapter.nativeToValue(((Map.Entry<?, ?>) entry).getValue());
    }

    @Override
    public Val find(Val key) {
      Object rawValue = map.get(key);
      return rawValue != null || map.containsKey(key) ? adapter.nativeToValue(rawValue) : null;
    }

    private Map<Val, Val> adaptedMap() {
      Map<Val, Val> adapted = new HashMap<>(map.size() * 4 / 3 + 1);
      map.forEach((key, rawValue) -> adapted.put(key, adapter.nativeToValue(rawValue)));
      return adapted;
    }

    @Override
    public String toString() {
      return "NativeMapT{" + "adapter=" + adapter + ", map=" + map + '}';
    }
  }

  /**
   * NewJSONStruct creates a traits.Mapper implementation backed by a JSON struct that has been
   * encoded in protocol buffer form.
   *
   * <p>The `adapter` argument provides type adaptation capabilities from proto to CEL.
   */
  public static Val newJSONStruct(TypeAdapter adapter, Struct value) {
    Map<String, Value> fields = value.getFieldsMap();
    return newMaybeWrappedMap(adapter, fields);
  }
}
