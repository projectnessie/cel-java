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

/** Immutable planning-time permission for native specialization. */
enum PlanningPolicy {
  ESTABLISHED_ONLY(false),
  NATIVE_SPECIALIZATION_PERMITTED(true);

  private final boolean nativeSpecializationPermitted;

  PlanningPolicy(boolean nativeSpecializationPermitted) {
    this.nativeSpecializationPermitted = nativeSpecializationPermitted;
  }

  static PlanningPolicy nativeSpecialization(boolean permitted) {
    return permitted ? NATIVE_SPECIALIZATION_PERMITTED : ESTABLISHED_ONLY;
  }

  boolean nativeSpecializationPermitted() {
    return nativeSpecializationPermitted;
  }
}
