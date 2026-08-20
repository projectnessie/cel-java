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

/** Immutable planning-time permissions for native specialization and built-in optimization. */
enum PlanningPolicy {
  ESTABLISHED_ONLY(false, false),
  NATIVE_SPECIALIZATION_PERMITTED(true, false),
  NATIVE_OPTIMIZED(true, true);

  private final boolean nativeSpecializationPermitted;
  private final boolean builtInOptimizationEnabled;

  PlanningPolicy(boolean nativeSpecializationPermitted, boolean builtInOptimizationEnabled) {
    this.nativeSpecializationPermitted = nativeSpecializationPermitted;
    this.builtInOptimizationEnabled = builtInOptimizationEnabled;
  }

  static PlanningPolicy nativeSpecialization(boolean permitted) {
    return permitted ? NATIVE_SPECIALIZATION_PERMITTED : ESTABLISHED_ONLY;
  }

  boolean nativeSpecializationPermitted() {
    return nativeSpecializationPermitted;
  }

  boolean builtInOptimizationEnabled() {
    return builtInOptimizationEnabled;
  }
}
