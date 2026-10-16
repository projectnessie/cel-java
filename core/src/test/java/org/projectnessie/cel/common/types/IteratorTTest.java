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
package org.projectnessie.cel.common.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.common.types.BoolT.BoolType;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.IteratorT.IteratorType;
import static org.projectnessie.cel.common.types.NullT.NullValue;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TypeT.TypeType;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Trait;

class IteratorTTest {

  @Test
  void exposesIteratorRuntimeType() {
    IteratorT iterator =
        IteratorT.javaIterator(value -> stringOf((String) value), List.of().iterator());

    assertThat(iterator).isInstanceOf(BaseVal.class);
    assertThat(iterator.type()).isSameAs(IteratorType);
    assertThat(IteratorType.typeName()).isEqualTo("iterator");
    assertThat(IteratorType.typeEnum()).isEqualTo(TypeEnum.Object);
    for (Trait trait : Trait.values()) {
      assertThat(IteratorType.hasTrait(trait))
          .as(trait.name())
          .isEqualTo(trait == Trait.IteratorType);
    }
  }

  @Test
  void traversesAndAdaptsNativeElementsOnce() {
    AtomicInteger adaptations = new AtomicInteger();
    TypeAdapter adapter =
        value -> {
          adaptations.incrementAndGet();
          return stringOf((String) value);
        };
    CountingIterator delegate = new CountingIterator(List.of("one", "two"));
    IteratorT iterator = IteratorT.javaIterator(adapter, delegate);

    assertThat(iterator.hasNext()).isSameAs(True);
    assertThat(iterator.hasNext()).isSameAs(True);
    assertThat(delegate.nextCalls).isZero();
    assertThat(adaptations).hasValue(0);

    assertThat(iterator.next()).isEqualTo(stringOf("one"));
    assertThat(delegate.nextCalls).isEqualTo(1);
    assertThat(adaptations).hasValue(1);
    assertThat(iterator.next()).isEqualTo(stringOf("two"));
    assertThat(delegate.nextCalls).isEqualTo(2);
    assertThat(adaptations).hasValue(2);
    assertThat(iterator.hasNext()).isSameAs(False);

    assertNoMoreElements(iterator.next());
    assertNoMoreElements(iterator.next());
    assertThat(delegate.nextCalls).isEqualTo(4);
    assertThat(adaptations).hasValue(2);
  }

  @Test
  void returnsValElementsWithoutAdapting() {
    AtomicInteger adaptations = new AtomicInteger();
    Val value = intOf(42);
    IteratorT iterator =
        IteratorT.javaIterator(
            ignored -> {
              adaptations.incrementAndGet();
              return stringOf("unexpected");
            },
            List.of(value).iterator());

    assertThat(iterator.next()).isSameAs(value);
    assertThat(adaptations).hasValue(0);
  }

  @Test
  void adaptsNativeNullElement() {
    AtomicInteger adaptations = new AtomicInteger();
    IteratorT iterator =
        IteratorT.javaIterator(
            value -> {
              assertThat(value).isNull();
              adaptations.incrementAndGet();
              return NullValue;
            },
            Collections.singletonList((Object) null).iterator());

    assertThat(iterator.next()).isSameAs(NullValue);
    assertThat(adaptations).hasValue(1);
  }

  @Test
  @SuppressWarnings("removal")
  void diagnosticsAreStableAndNonConsuming() {
    AtomicInteger adaptations = new AtomicInteger();
    CountingIterator delegate = new CountingIterator(List.of("value"));
    IteratorT iterator =
        IteratorT.javaIterator(
            value -> {
              adaptations.incrementAndGet();
              return stringOf((String) value);
            },
            delegate);
    CountingIterator otherDelegate = new CountingIterator(List.of("other"));
    IteratorT other = IteratorT.javaIterator(value -> stringOf((String) value), otherDelegate);
    int identityHash = iterator.hashCode();

    assertThat(iterator.type()).isSameAs(IteratorType);
    assertThat(iterator.value()).isNull();
    assertCelError(iterator.equal(other), "no such overload: iterator.equal(iterator)");
    assertCelError(iterator.equal(stringOf("other")), "no such overload: iterator.equal(string)");
    assertCelError(
        iterator.convertToType(IteratorType),
        "type conversion error from 'iterator' to 'iterator'");
    assertCelError(
        iterator.convertToType(TypeType), "type conversion error from 'iterator' to 'type'");
    assertCelError(
        iterator.convertToType(BoolType), "type conversion error from 'iterator' to 'bool'");
    assertThatThrownBy(() -> iterator.convertToNative(Object.class))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("type conversion not supported for 'iterator'");
    assertThatThrownBy(() -> iterator.convertToNative(Val.class))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("type conversion not supported for 'iterator'");
    assertThatThrownBy(() -> iterator.convertToNative(IteratorT.class))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("type conversion not supported for 'iterator'");
    assertThatThrownBy(iterator::booleanValue)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("iterator cannot be used as boolean");
    assertThatThrownBy(iterator::intValue)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("iterator cannot be used as integer");
    assertThatThrownBy(iterator::doubleValue)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("iterator cannot be used as double");

    assertThat(iterator).isSameAs(iterator).isNotSameAs(other);
    assertThat(iterator.equals(other)).isFalse();
    assertThat(iterator.hashCode()).isEqualTo(identityHash);
    assertThat(iterator).hasToString("iterator");
    assertThat(delegate.nextCalls).isZero();
    assertThat(otherDelegate.nextCalls).isZero();
    assertThat(adaptations).hasValue(0);

    assertThat(iterator.next()).isEqualTo(stringOf("value"));
    assertThat(iterator.hashCode()).isEqualTo(identityHash);
    assertThat(iterator).hasToString("iterator");
  }

  @Test
  void validatesFactoryArguments() {
    Iterator<Object> delegate = List.<Object>of().iterator();
    TypeAdapter adapter = value -> stringOf(String.valueOf(value));

    assertThatThrownBy(() -> IteratorT.javaIterator(null, delegate))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("adapter");
    assertThatThrownBy(() -> IteratorT.javaIterator(adapter, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("iterator");
  }

  @Test
  void mapsDelegateExhaustionAndPropagatesOtherFailures() {
    IteratorT exhausted =
        IteratorT.javaIterator(
            value -> stringOf(String.valueOf(value)),
            new Iterator<>() {
              @Override
              public boolean hasNext() {
                return true;
              }

              @Override
              public Object next() {
                throw new NoSuchElementException("delegate exhausted");
              }
            });
    assertNoMoreElements(exhausted.next());

    IllegalStateException delegateFailure = new IllegalStateException("delegate failed");
    IteratorT failingDelegate =
        IteratorT.javaIterator(
            value -> stringOf(String.valueOf(value)),
            new Iterator<>() {
              @Override
              public boolean hasNext() {
                return true;
              }

              @Override
              public Object next() {
                throw delegateFailure;
              }
            });
    assertThatThrownBy(failingDelegate::next).isSameAs(delegateFailure);
  }

  @Test
  void propagatesAdapterFailureAfterAdvancing() {
    AtomicInteger nextCalls = new AtomicInteger();
    IllegalArgumentException adapterFailure = new IllegalArgumentException("adapter failed");
    IteratorT iterator =
        IteratorT.javaIterator(
            value -> {
              throw adapterFailure;
            },
            new Iterator<>() {
              @Override
              public boolean hasNext() {
                return true;
              }

              @Override
              public Object next() {
                nextCalls.incrementAndGet();
                return "value";
              }
            });

    assertThatThrownBy(iterator::next).isSameAs(adapterFailure);
    assertThat(nextCalls).hasValue(1);
  }

  private static void assertNoMoreElements(Val value) {
    assertCelError(value, "no more elements");
  }

  private static void assertCelError(Val value, String message) {
    assertThat(value).matches(Err::isError).hasToString(message);
  }

  private static final class CountingIterator implements Iterator<Object> {
    private final List<?> values;
    private int index;
    private int nextCalls;

    private CountingIterator(List<?> values) {
      this.values = values;
    }

    @Override
    public boolean hasNext() {
      return index < values.size();
    }

    @Override
    public Object next() {
      nextCalls++;
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return values.get(index++);
    }
  }
}
