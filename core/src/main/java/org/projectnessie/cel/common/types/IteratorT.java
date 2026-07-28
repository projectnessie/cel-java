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
import java.util.Objects;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Trait;

/**
 * Stateful, one-shot cursor used by the low-level interpreter to traverse an aggregate value.
 *
 * <p>This interface extends {@link Val} so cursors can pass through interpreter operations. An
 * iterator is not a first-class CEL value: CEL expressions cannot name or construct one, and only
 * traversal, runtime type inspection, and the diagnostic behavior defined here are supported.
 *
 * <p>Iterators are not thread-safe or reusable. Callers must serialize access and must not mutate
 * the source aggregate while traversing it. Traversal order, source-mutation behavior, and
 * non-exhaustion failures are defined by the source aggregate or wrapped Java iterator.
 *
 * <p>Direct implementations need only implement {@link #hasNext()} and {@link #next()}. A class
 * that also extends a superclass with concrete {@link Val} methods must account for Java's
 * superclass-before-interface default-method precedence. In particular, an implementation extending
 * {@link org.projectnessie.cel.common.types.ref.BaseVal} must override its primitive accessors and
 * its payload-based {@link Object#equals(Object)}, {@link Object#hashCode()}, and {@link
 * Object#toString()} methods.
 */
public interface IteratorT extends Val {
  /**
   * Runtime type used for low-level iterator trait dispatch.
   *
   * <p>This object-kind type is not a CEL language type and is not supported as an expression-level
   * conversion target.
   */
  Type IteratorType = TypeT.newObjectTypeValue("iterator", Trait.IteratorType);

  /**
   * Wraps a Java iterator as a CEL interpreter cursor.
   *
   * <p>Elements already implementing {@link Val} are returned unchanged; other elements, including
   * {@code null}, are converted through {@code adapter}. A Java {@link NoSuchElementException} is
   * represented by the CEL no-more-elements error. Other delegate and adapter failures propagate
   * unchanged.
   *
   * @param adapter adapter used for native elements
   * @param iterator Java iterator to wrap
   * @return a new one-shot cursor
   * @throws NullPointerException if either argument is {@code null}
   */
  static IteratorT javaIterator(TypeAdapter adapter, Iterator<?> iterator) {
    return new JavaIteratorT(
        Objects.requireNonNull(adapter, "adapter"), Objects.requireNonNull(iterator, "iterator"));
  }

  /**
   * Reports whether an unvisited element remains without advancing the cursor.
   *
   * @return a CEL boolean value
   */
  Val hasNext();

  /**
   * Advances the cursor once and returns the next element.
   *
   * @return the next value, or a CEL no-more-elements error after exhaustion
   */
  Val next();

  /**
   * Returns the stable low-level iterator runtime type without advancing the cursor.
   *
   * @return {@link #IteratorType}
   */
  @Override
  default Type type() {
    return IteratorType;
  }

  /**
   * Returns no data payload because an iterator is interpreter control state.
   *
   * @return always {@code null}
   */
  @Override
  default Object value() {
    return null;
  }

  /**
   * Returns a CEL no-such-overload error without observing or advancing either cursor.
   *
   * @param other value presented for comparison
   * @return a CEL error
   */
  @Override
  default Val equal(Val other) {
    return Err.noSuchOverload(this, "equal", other);
  }

  /**
   * Returns a CEL conversion error because iterators are not first-class CEL values.
   *
   * @param typeValue requested CEL type
   * @return a CEL error
   */
  @Override
  default Val convertToType(Type typeValue) {
    return Err.newTypeConversionError(IteratorType, typeValue);
  }

  /**
   * Native conversion is unsupported because an iterator has no CEL data payload.
   *
   * @param typeDesc requested native type
   * @param <T> requested native type
   * @return never returns normally
   * @throws UnsupportedOperationException always
   */
  @Override
  @SuppressWarnings("removal")
  default <T> T convertToNative(Class<T> typeDesc) {
    throw new UnsupportedOperationException("type conversion not supported for 'iterator'");
  }

  /**
   * Returns no primitive boolean because an iterator is interpreter control state.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  default boolean booleanValue() {
    throw new UnsupportedOperationException("iterator cannot be used as boolean");
  }

  /**
   * Returns no primitive integer because an iterator is interpreter control state.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  default long intValue() {
    throw new UnsupportedOperationException("iterator cannot be used as integer");
  }

  /**
   * Returns no primitive double because an iterator is interpreter control state.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  default double doubleValue() {
    throw new UnsupportedOperationException("iterator cannot be used as double");
  }

  /** Built-in cursor returned by {@link #javaIterator(TypeAdapter, Iterator)}. */
  final class JavaIteratorT extends BaseIteratorT {
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
  }
}
