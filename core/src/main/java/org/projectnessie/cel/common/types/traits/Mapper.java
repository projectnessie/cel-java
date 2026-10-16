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
package org.projectnessie.cel.common.types.traits;

import org.projectnessie.cel.common.types.IterableT;
import org.projectnessie.cel.common.types.ref.Val;

/**
 * Complete runtime contract for a CEL map value.
 *
 * <p>Implementations support key membership, indexing, key iteration, and sizing in addition to the
 * base {@link Val} contract. Keys yielded by iteration must be accepted by {@link #find(Val)} and
 * {@link #get(Val)}.
 */
public interface Mapper extends Val, Container, Indexer, IterableT, Sizer {

  /**
   * Finds the value associated with {@code key} without converting absence into a CEL error.
   *
   * <p>This method is the deliberate exception to the usual non-null trait return contract: Java
   * {@code null} means that the key is absent. A present CEL null value is returned as a non-null
   * CEL null {@link Val}. The evaluator propagates CEL error and unknown arguments before ordinary
   * map lookup; direct callers should likewise pass an evaluated, valid CEL key.
   *
   * @param key map key
   * @return the associated CEL value, or Java {@code null} if absent
   */
  Val find(Val key);
}
