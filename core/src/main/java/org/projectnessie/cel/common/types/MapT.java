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
import static org.projectnessie.cel.common.types.BoolT.boolOf;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Container;
import org.projectnessie.cel.common.types.traits.Indexer;
import org.projectnessie.cel.common.types.traits.Mapper;
import org.projectnessie.cel.common.types.traits.Sizer;
import org.projectnessie.cel.common.types.traits.Trait;

public abstract class MapT extends BaseVal implements Mapper, Container, Indexer, IterableT, Sizer {
  /** MapType singleton. */
  public static final TypeValue MapType =
      TypeValue.newTypeValue(
          "map", Trait.ContainerType, Trait.IndexerType, Trait.IterableType, Trait.SizerType);

  public static Val newWrappedMap(TypeAdapter adapter, Map<Val, Val> value) {
    return new ValMapT(adapter, value);
  }

  public static Val newMaybeWrappedMap(TypeAdapter adapter, Map<?, ?> value) {
    Map<Val, Val> newMap = new HashMap<>(value.size() * 4 / 3 + 1);
    value.forEach((k, v) -> newMap.put(adapter.nativeToValue(k), adapter.nativeToValue(v)));
    return newWrappedMap(adapter, newMap);
  }

  @Override
  public Type type() {
    return MapType;
  }

  static final class ValMapT extends MapT {

    private final TypeAdapter adapter;
    private final Map<Val, Val> map;

    ValMapT(TypeAdapter adapter, Map<Val, Val> map) {
      this.adapter = adapter;
      this.map = map;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T convertToNative(Class<T> typeDesc) {
      if (Map.class.isAssignableFrom(typeDesc) || typeDesc == Object.class) {
        Map r = new HashMap();
        map.forEach((k, v) -> r.put(k.value(), v.value()));
        return (T) r;
      }
      throw new RuntimeException(
          String.format(
              "native type conversion error from '%s' to '%s'", MapType, typeDesc.getName()));
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
      // TODO this is expensive :(
      if (!(other instanceof MapT)) {
        return False;
      }
      MapT o = (MapT) other;
      if (!size().equal(o.size()).booleanValue()) {
        return False;
      }
      IteratorT myIter = iterator();
      while (myIter.hasNext() == True) {
        Val key = myIter.next();
        Val val = get(key);
        Val oVal = o.find(key);
        if (val == null || oVal == null) {
          return False;
        }
        Val eq = val.equal(oVal);
        if (eq instanceof Err) {
          return eq;
        }
        if (val.equal(oVal) != True) {
          return False;
        }
      }
      return True;
    }

    @Override
    public Object value() {
      // TODO this is expensive :(
      Map<Object, Object> nativeMap = new HashMap<>();
      map.forEach((k, v) -> nativeMap.put(k.value(), v.value()));
      return nativeMap;
    }

    @Override
    public Val contains(Val value) {
      return boolOf(map.containsKey(value));
    }

    @Override
    public Val get(Val index) {
      return map.get(index);
    }

    @Override
    public Val size() {
      return IntT.intOf(map.size());
    }

    @Override
    public Val find(Val key) {
      return map.get(key);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ValMapT valMapT = (ValMapT) o;
      return Objects.equals(map, valMapT.map);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), map);
    }

    @Override
    public String toString() {
      return "JavaMapT{" + "adapter=" + adapter + ", map=" + map + '}';
    }
  }
}
