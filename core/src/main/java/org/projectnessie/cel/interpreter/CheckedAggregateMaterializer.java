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
package org.projectnessie.cel.interpreter;

import static org.projectnessie.cel.common.types.Err.newErr;

import com.google.api.expr.v1alpha1.Type;
import java.util.Objects;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;

/** Evaluation-independent checked aggregate materialization selected by the planner. */
final class CheckedAggregateMaterializer {
  private final ExactAggregateTypeAdapter adapter;
  private final Type checkedType;

  CheckedAggregateMaterializer(ExactAggregateTypeAdapter adapter, Type checkedType) {
    this.adapter = Objects.requireNonNull(adapter, "adapter");
    this.checkedType = Objects.requireNonNull(checkedType, "checkedType");
  }

  Val materialize(Object value) {
    try {
      return adapter.nativeAggregateToValue(value, checkedType);
    } catch (Exception failure) {
      return newErr(failure, failure.toString());
    }
  }

  Val materializeListElement(Object value) {
    Val list = materialize(new Object[] {value});
    return list instanceof Lister lister ? lister.nativeGetAt(0) : list;
  }
}
