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
package org.projectnessie.cel.common.types.ref;

/**
 * Type adapter whose Java scalar conversions have the standard CEL semantics.
 *
 * <p>Implementations preserve existing {@link Val} values and convert Java {@code null}, boolean,
 * signed integral, floating-point, and string values exactly as {@link TypeAdapterSupport}. The
 * primitive overloads must agree with {@link #nativeToValue(Object)}, and these conversions must
 * not depend on mutable evaluation context.
 *
 * <p>This semantic marker permits evaluators to carry the corresponding Java scalars internally
 * before adapting the final result. Implementations should only declare it when those guarantees
 * apply to every instance.
 */
public interface StandardScalarTypeAdapter extends TypeAdapter {}
