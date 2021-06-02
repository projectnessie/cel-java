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

import static org.projectnessie.cel.common.types.BoolT.boolOf;

import java.util.Iterator;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

/** Iterator permits safe traversal over the contents of an aggregate type. */
public interface IteratorT {

  static IteratorT javaIterator(TypeAdapter adapter, Iterator<?> iterator) {
    return new JavaIteratorT(adapter, iterator);
  }

  /** HasNext returns true if there are unvisited elements in the Iterator. */
  Val hasNext();
  /** Next returns the next element. */
  Val next();

  class JavaIteratorT implements IteratorT {
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
      return adapter.nativeToValue(iterator.next());
    }
  }
}
