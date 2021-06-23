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

/** EvalState tracks the values associated with expression ids during execution. */
public interface EvalState {
  /** IDs returns the list of ids with recorded values. */
  long[] ids();

  /**
   * Value returns the observed value of the given expression id if found, and a nil false result if
   * not.
   */
  Val value(long id);

  /** SetValue sets the observed value of the expression id. */
  void setValue(long id, Val v);

  /** Reset clears the previously recorded expression values. */
  void reset();

  /**
   * NewEvalState returns an EvalState instanced used to observe the intermediate evaluations of an
   * expression.
   */
  static EvalState newEvalState() {
    return new EvalStateImpl();
  }

  /** evalState permits the mutation of evaluation state for a given expression id. */
  final class EvalStateImpl implements EvalState {
    private final Long2ObjectHashMap<Val> values = new Long2ObjectHashMap<>();

    /** IDs implements the EvalState interface method. */
    @Override
    public long[] ids() {
      return values.keySet().stream().mapToLong(l -> l).toArray();
    }

    /** Value is an implementation of the EvalState interface method. */
    @Override
    public Val value(long id) {
      return values.get(id);
    }

    /** SetValue is an implementation of the EvalState interface method. */
    @Override
    public void setValue(long id, Val v) {
      values.put(id, v);
    }

    /** Reset implements the EvalState interface method. */
    @Override
    public void reset() {
      values.clear();
    }
  }
}
