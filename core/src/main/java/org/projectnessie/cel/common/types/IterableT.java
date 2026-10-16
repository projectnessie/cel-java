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

/** Aggregate value capable of creating one-shot CEL iterators. */
public interface IterableT {

  /**
   * Returns a new one-shot cursor over this aggregate.
   *
   * <p>Traversal order, source-mutation behavior, and traversal failures are defined by the
   * implementing aggregate. The returned cursor is not thread-safe; callers must serialize access.
   */
  IteratorT iterator();
}
