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
 * CEL runtime values and built-in value operations.
 *
 * <p>The common value contract is {@link org.projectnessie.cel.common.types.ref.Val}. Applications
 * usually provide ordinary Java, Protobuf, or Jackson values and let a configured type adapter
 * create CEL values. Direct use of these classes is primarily intended for custom functions, type
 * adapters, and advanced interpreter integrations.
 *
 * <p>CEL equality, conversion, errors, and unknowns are represented by CEL values and do not in
 * general have the same contract as Java {@link java.lang.Object#equals(Object)} or Java numeric
 * conversions. Aggregate adapters may retain caller-owned collections; consult the concrete type
 * before mutating or sharing a source value.
 */
package org.projectnessie.cel.common.types;
