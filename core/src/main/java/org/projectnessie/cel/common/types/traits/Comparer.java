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

import org.projectnessie.cel.common.types.ref.Val;

/**
 * Comparer interface for ordering comparisons between values in order to support '&lt;', '&lt;=',
 * '&gt;=', '&gt;' overloads.
 */
public interface Comparer {
  /**
   * Compare this value to the input other value, returning an Int:
   *
   * <p>{@code this &lt; other -&gt; Int(-1)<br> this == other -&gt; Int(0)<br> this &gt; other
   * -&gt; Int(1) }
   *
   * <p>If the comparison cannot be made or is not supported, an error should be returned.
   */
  Val compare(Val other);
}
