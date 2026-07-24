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

/**
 * Type provider whose aggregate field accessors return certified exact aggregate representations.
 *
 * <p>For fields described as checked CEL list or map types, the associated {@link
 * FieldType#getFrom} accessor returns a Java representation satisfying {@link
 * ExactAggregateTypeAdapter}'s homogeneous representation, checked-kind provenance, null,
 * encounter-order, equality/hashing, immutability-during-evaluation, cycle, and contract-violation
 * requirements. This marker does not change scalar field semantics and exposes no evaluator or
 * planner implementation type.
 *
 * <p>Implementations should declare this contract only on visibly distinct opt-in provider
 * instances. Existing default providers do not acquire the stricter input contract implicitly. In
 * the initial contract, field specialization recognizes this marker only when the same configured
 * object is also the {@link ExactAggregateTypeAdapter}; independently configured provider and
 * adapter instances remain on the general field path. Field presence behavior and accessor
 * invocation order are unchanged.
 */
public interface ExactAggregateFieldProvider extends TypeProvider {
  /**
   * Returns whether one checked aggregate field has the provider's certified exact representation.
   *
   * <p>The default preserves the provider-wide contract of implementations compiled before this
   * method existed. Selective providers override it and must return {@code false} for scalar and
   * unsupported aggregate fields.
   *
   * @param messageType fully qualified checked CEL message type
   * @param fieldName protobuf/host field name used by the checked select
   * @param checkedType checked CEL result type of the field
   */
  default boolean isExactAggregateField(String messageType, String fieldName, Type checkedType) {
    return true;
  }
}
