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

import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Trait;

/**
 * baseIterator is the basis for list, map, and object iterators.
 *
 * <p>An iterator in and of itself should not be a valid value for comparison, but must implement
 * the `ref.Val` methods in order to be well-supported within instruction arguments processed by the
 * interpreter.
 */
public abstract class BaseIterator extends BaseVal implements IteratorT {
  public static final TypeValue IteratorType =
      TypeValue.newTypeValue("iterator", Trait.IteratorType);

  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    throw new UnsupportedOperationException("type conversion on iterators not supported");
  }

  @Override
  public Val convertToType(Type typeValue) {
    return Err.newErr("no such overload");
  }

  @Override
  public Val equal(Val other) {
    return Err.newErr("no such overload");
  }

  @Override
  public Type type() {
    return IteratorType;
  }

  @Override
  public Object value() {
    return null;
  }
}
