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

import static org.projectnessie.cel.common.types.Err.noMoreElements;
import static org.projectnessie.cel.common.types.Types.boolOf;

import java.util.Iterator;
import java.util.NoSuchElementException;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

/** Iterator permits safe traversal over the contents of an aggregate type. */
public interface IteratorT extends Val {

  static IteratorT javaIterator(TypeAdapter adapter, Iterator<?> iterator) {
    return new JavaIteratorT(adapter, iterator);
  }

  /** HasNext returns true if there are unvisited elements in the Iterator. */
  Val hasNext();
  /** Next returns the next element. */
  Val next();

  final class JavaIteratorT extends BaseVal implements IteratorT {
    private final TypeAdapter adapter;
    private final Iterator<?> iterator;

    JavaIteratorT(TypeAdapter adapter, Iterator<?> iterator) {
      this.adapter = adapter;
      this.iterator = iterator;
    }

    @Override
    public Val hasNext() {
      return boolOf(iterator.hasNext());
    }

    @Override
    public Val next() {
      Object n;
      try {
        n = iterator.next();
      } catch (NoSuchElementException e) {
        return noMoreElements();
      }
      if (n instanceof Val) {
        return (Val) n;
      }
      return adapter.nativeToValue(n);
    }

    @Override
    public <T> T convertToNative(Class<T> typeDesc) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Val convertToType(Type typeValue) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Val equal(Val other) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Type type() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object value() {
      throw new UnsupportedOperationException();
    }
  }
}
