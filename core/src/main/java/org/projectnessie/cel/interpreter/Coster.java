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

/** Coster calculates the heuristic cost incurred during evaluation. */
public interface Coster {
  Cost cost();

  static Cost costOf(long min, long max) {
    return new Cost(min, max);
  }

  final class Cost {
    public static final Cost Unknown = costOf(0, Long.MAX_VALUE);
    public static final Cost None = costOf(0, 0);
    public static final Cost OneOne = costOf(1, 1);
    public final long min;
    public final long max;

    private Cost(long min, long max) {
      this.min = min;
      this.max = max;
    }

    /** estimateCost returns the heuristic cost interval for the program. */
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
  }
}
