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
package org.projectnessie.cel.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.Err.noMoreElements;

import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Val;

/** Compile-time fixture for direct external implementations of the public iterator protocol. */
class IteratorPublicApiCompileTest {

  @Test
  void directImplementationNeedsOnlyTraversalMethods() {
    IteratorT iterator = new ExternalIterator();
    IteratorT other = new ExternalIterator();
    int identityHash = iterator.hashCode();

    assertThat(iterator.type()).isSameAs(IteratorT.IteratorType);
    assertThat(iterator.value()).isNull();
    assertThat(iterator.hasNext()).isSameAs(False);
    assertThat(iterator.next()).matches(org.projectnessie.cel.common.types.Err::isError);
    assertThat(iterator.equals(iterator)).isTrue();
    assertThat(iterator.equals(other)).isFalse();
    assertThat(iterator.hashCode()).isEqualTo(identityHash);
  }

  @Test
  void externalOverrideStillWins() {
    IteratorT iterator = new OverridingIterator();

    assertThat(iterator.value()).isEqualTo("external");
  }

  @Test
  void baseValImplementationCanSelectIteratorPrimitiveDiagnostics() {
    IteratorT iterator = new ExternalBaseValIterator();
    IteratorT other = new ExternalBaseValIterator();
    int identityHash = iterator.hashCode();

    assertThat(iterator.type()).isSameAs(IteratorT.IteratorType);
    assertThatThrownBy(iterator::booleanValue)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("iterator cannot be used as boolean");
    assertThatThrownBy(iterator::intValue)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("iterator cannot be used as integer");
    assertThatThrownBy(iterator::doubleValue)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("iterator cannot be used as double");
    assertThat(iterator.equals(iterator)).isTrue();
    assertThat(iterator.equals(other)).isFalse();
    assertThat(iterator.hashCode()).isEqualTo(identityHash);
    assertThat(iterator).hasToString("iterator");
  }

  private static class ExternalIterator implements IteratorT {
    @Override
    public Val hasNext() {
      return False;
    }

    @Override
    public Val next() {
      return noMoreElements();
    }
  }

  private static final class OverridingIterator extends ExternalIterator {
    @Override
    public Object value() {
      return "external";
    }
  }

  private static final class ExternalBaseValIterator extends BaseVal implements IteratorT {
    @Override
    public Val hasNext() {
      return False;
    }

    @Override
    public Val next() {
      return noMoreElements();
    }

    @Override
    public boolean booleanValue() {
      return IteratorT.super.booleanValue();
    }

    @Override
    public long intValue() {
      return IteratorT.super.intValue();
    }

    @Override
    public double doubleValue() {
      return IteratorT.super.doubleValue();
    }

    @Override
    public boolean equals(Object other) {
      return this == other;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String toString() {
      return "iterator";
    }
  }
}
