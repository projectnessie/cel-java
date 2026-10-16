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
 * Behavioral capabilities implemented by CEL values.
 *
 * <p>A value advertises supported capabilities through {@link
 * org.projectnessie.cel.common.types.ref.Type#hasTrait(Trait)} and implements the corresponding
 * interface in this package. The interpreter checks the advertised trait before dispatching an
 * operator. Implementations must therefore keep the advertised {@link Trait}, implemented Java
 * interface, and operation semantics consistent.
 *
 * <p>Trait methods return CEL {@link org.projectnessie.cel.common.types.ref.Val} instances.
 * Unsupported operands and evaluation failures should be returned as CEL error values, and unknown
 * inputs should be preserved according to CEL semantics; implementations must not return Java
 * {@code null} except where a method such as {@link
 * org.projectnessie.cel.common.types.traits.Mapper#find(org.projectnessie.cel.common.types.ref.Val)}
 * explicitly reserves it for absence. Values used concurrently by reusable programs must either be
 * immutable or provide their own thread safety.
 */
package org.projectnessie.cel.common.types.traits;
