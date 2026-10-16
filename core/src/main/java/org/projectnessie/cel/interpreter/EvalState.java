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

import org.agrona.collections.Long2ObjectHashMap;
import org.projectnessie.cel.common.types.ref.Val;

/**
 * Mutable state containing values observed for expression IDs during one evaluation.
 *
 * <p>A state returned by {@link org.projectnessie.cel.Program#eval(Object)} belongs to that result
 * and is not reused by another evaluation. Callers may inspect, augment, or reset it without
 * affecting the program or another result. Instances are not thread-safe; externally synchronize
 * access if a state is shared across threads.
 */
public interface EvalState {
  /**
   * Returns a snapshot of the expression IDs with recorded values.
   *
   * <p>The order is unspecified. Mutating the returned array does not affect this state.
   *
   * @return recorded expression IDs
   */
  long[] ids();

  /**
   * Returns the observed value for the given expression ID.
   *
   * @param id expression ID
   * @return the recorded value, or {@code null} if no value is recorded
   */
  Val value(long id);

  /**
   * Records or replaces the observed value for an expression ID.
   *
   * @param id expression ID
   * @param v value to record
   */
  void setValue(long id, Val v);

  /** Clears all recorded expression values. */
  void reset();

  /**
   * Creates an empty evaluation state.
   *
   * <p>The backing storage is allocated lazily when the first value is recorded.
   *
   * @return a new mutable evaluation state
   */
  static EvalState newEvalState() {
    return new EvalStateImpl();
  }

  /** Default mutable {@link EvalState} implementation. */
  final class EvalStateImpl implements EvalState {
    private Long2ObjectHashMap<Val> values;

    @Override
    public long[] ids() {
      if (values == null) {
        return new long[0];
      }
      return values.keySet().stream().mapToLong(l -> l).toArray();
    }

    @Override
    public Val value(long id) {
      return values != null ? values.get(id) : null;
    }

    @Override
    public void setValue(long id, Val v) {
      if (values == null) {
        values = new Long2ObjectHashMap<>();
      }
      values.put(id, v);
    }

    @Override
    public void reset() {
      if (values != null) {
        values.clear();
      }
    }
  }
}
