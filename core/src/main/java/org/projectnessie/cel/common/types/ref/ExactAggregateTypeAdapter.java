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
package org.projectnessie.cel.common.types.ref;

import com.google.api.expr.v1alpha1.Type;
import java.util.Objects;

/**
 * Type adapter that can materialize certified Java aggregate representations using their checked
 * CEL type.
 *
 * <p>This is an explicit, stricter opt-in contract applying to every instance of an implementing
 * class, independently of mutable evaluation context. It defines checked materialization in both
 * native-enabled and native-disabled planning; implementing it does not itself permit a native
 * island.
 *
 * <p>The canonical aggregate representations are {@code int[]}, {@code long[]}, {@code double[]},
 * {@code String[]}, {@code Object[]}, {@link java.util.List}, non-list {@link java.util.Collection}
 * including {@link java.util.Set}, and {@link java.util.Map}, recursively. Java {@code byte[]}
 * remains CEL bytes rather than a list. Embedded {@link Val} values and {@code Val[]} are not
 * conforming exact aggregate elements. The {@code checkedType} is authoritative for element, key,
 * and value kinds, including whether Java {@code long} bits represent CEL {@code int} or {@code
 * uint}; implementations must not infer signedness from the Java class or sign bit, and do not
 * infer implicit signed/unsigned mixing.
 *
 * <p>Inputs contain homogeneous values compatible with the checked type. Null list elements and map
 * values are permitted only when the nested checked type permits CEL null, dynamic, or a nullable
 * wrapper value. Present-null map values remain distinct from absent keys. Map keys must not be
 * null, and distinct Java keys must not be equal under CEL key equality. Exact lookup is permitted
 * only for a planner-certified domain whose Java equality and hashing agree with CEL. Cross-numeric
 * lookup is not inferred; double lookup must preserve CEL NaN inequality and signed-zero equality.
 * CEL int map keys may use any exactly representable {@link Byte}, {@link Short}, {@link Integer},
 * or {@link Long}; exact lookup uses a bounded probe of those four representations rather than
 * requiring one canonical wrapper. A {@link ClassCastException} from a speculative signed-wrapper
 * probe is treated as a non-match, including when every representation is rejected. Equality-keyed
 * maps must not contain multiple wrapper representations of the same CEL int; a cheaply detected
 * duplicate is a contract error. {@link java.util.SortedMap} comparators can make multiple probes
 * aliases for one entry, so exact lookup does not infer duplicates from those probes and the
 * provider remains responsible for the distinct-key requirement. Sets advertised through this
 * contract use the canonical homogeneous boxed representation needed by direct CEL membership:
 * {@link Boolean}, {@link String}, {@link Long} for CEL int, either all {@link Long} or all {@link
 * org.projectnessie.cel.common.ULong} for CEL uint, and {@link Double} for CEL double. Other
 * numeric wrappers and mixed boxed representations must be exposed through a general adapter
 * instead. This restriction lets a direct hash miss remain definitive without an
 * element-proportional validation scan.
 *
 * <p>List materialization preserves the established encounter-order and snapshot behavior of each
 * representation. Arrays use index order and lists use list order. Non-list collections are
 * snapshotted with {@code toArray()}, and exact inputs require that order to agree with iterator
 * encounter order. Sets are encounter-ordered CEL lists for indexing, traversal, equality, and
 * terminal conversion; unordered Java set equality is not CEL list equality.
 *
 * <p>The caller must not mutate an advertised aggregate during one CEL evaluation and must not
 * supply cycles traversed recursively by materialization. Implementations need not perform an
 * element-proportional validation pass before a constant-time operation. Cheaply detected contract
 * violations return a deterministic CEL error value, never Java {@code null}, a general-path
 * fallback, or a request to evaluate again. O(1) operations need not pre-scan unvisited elements;
 * undetected violations are caller contract breaches and must never cause expression replay or
 * corrupt reusable plan state.
 *
 * <p>Exact aggregate field materialization initially requires the same configured object to
 * implement both this interface and {@link ExactAggregateFieldProvider} and to be supplied as both
 * the CEL type adapter and provider. Activation values require only this adapter contract.
 *
 * <p>This contract exposes no evaluator, planner, node, capability, or internal result type.
 * Implementations may provide checked materialization independently of scalar native
 * specialization.
 */
public interface ExactAggregateTypeAdapter extends TypeAdapter {
  /**
   * Converts a certified Java aggregate to a CEL value using the authoritative checked CEL type.
   *
   * @param value certified Java aggregate representation
   * @param checkedType non-null checked CEL list or map type
   * @return the materialized CEL aggregate or a deterministic CEL error for a cheaply detected
   *     contract violation
   * @throws NullPointerException if {@code checkedType} is null
   * @throws IllegalArgumentException if {@code checkedType} is not a CEL list or map type
   */
  default Val nativeAggregateToValue(Object value, Type checkedType) {
    Objects.requireNonNull(checkedType, "checkedType");
    if (checkedType.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE
        && checkedType.getTypeKindCase() != Type.TypeKindCase.MAP_TYPE) {
      throw new IllegalArgumentException("checkedType must be a CEL list or map type");
    }
    return TypeAdapterSupport.nativeAggregateToValue(this, value, checkedType);
  }
}
