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
package org.projectnessie.cel.interpreter;

import java.util.Objects;

/**
 * Optional low-level contract for estimating an interpretable node's evaluation cost.
 *
 * <p>Costs are inclusive heuristic ranges, not execution budgets or measured time. They may be used
 * to compare plans, but do not make evaluation resource-bounded.
 */
public interface Coster {
  /** Returns this node's heuristic inclusive cost range. */
  Cost cost();

  /** Creates an inclusive heuristic cost range. */
  static Cost costOf(long min, long max) {
    return new Cost(min, max);
  }

  /** Immutable inclusive range of heuristic evaluation costs. */
  final class Cost {
    /** Unknown upper-bound cost. */
    public static final Cost Unknown = costOf(0, Long.MAX_VALUE);

    /** Zero evaluation cost. */
    public static final Cost None = costOf(0, 0);

    /** Exactly one cost unit. */
    public static final Cost OneOne = costOf(1, 1);

    /** Inclusive minimum cost. */
    public final long min;

    /** Inclusive maximum cost. */
    public final long max;

    private Cost(long min, long max) {
      this.min = min;
      this.max = max;
    }

    /**
     * Returns an object's declared cost, or {@link #Unknown} when it does not implement {@link
     * Coster}.
     */
    public static Cost estimateCost(Object i) {
      if (i instanceof Coster) {
        return ((Coster) i).cost();
      }
      return Unknown;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Cost cost = (Cost) o;
      return min == cost.min && max == cost.max;
    }

    @Override
    public int hashCode() {
      return Objects.hash(min, max);
    }

    @Override
    public String toString() {
      return "Cost{" + "min=" + min + ", max=" + max + '}';
    }

    /** Returns the component-wise sum of this range and {@code c}. */
    public Cost add(Cost c) {
      return new Cost(min + c.min, max + c.max);
    }

    /** Returns this range with both bounds multiplied by {@code multiplier}. */
    public Cost multiply(long multiplier) {
      return new Cost(min * multiplier, max * multiplier);
    }
  }
}
