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

/**
 * CEL values and the extension contracts for adapting and describing host-language types.
 *
 * <p>{@link org.projectnessie.cel.common.types.ref.Val} is the common result representation used by
 * the evaluator. A {@link org.projectnessie.cel.common.types.ref.TypeAdapter} converts Java input
 * values to that representation, while a {@link
 * org.projectnessie.cel.common.types.ref.TypeProvider} resolves named types, fields, enum values,
 * and object construction. A {@link org.projectnessie.cel.common.types.ref.TypeRegistry} combines
 * those roles with mutable type registration.
 *
 * <p>Configure adapters, providers, and registries before they are shared by environments or
 * programs. Unless a concrete implementation documents stronger guarantees, callers must not mutate
 * registration state or retained input objects during concurrent checking or evaluation.
 * Implementations should represent CEL evaluation failures as non-null CEL error values; Java
 * exceptions are reserved for invalid host API use or conversion to an incompatible Java result.
 */
package org.projectnessie.cel.common.types.ref;
