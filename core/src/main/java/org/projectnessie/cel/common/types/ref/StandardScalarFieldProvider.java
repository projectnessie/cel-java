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
 * Type provider whose scalar field accessors return canonical Java scalar representations.
 *
 * <p>For fields described as CEL boolean, signed integer, double, string, or null, the associated
 * {@link FieldType#getFrom} accessor returns the corresponding Java scalar, Java {@code null}, or
 * an existing terminal {@link Val}. Access must not depend on mutable evaluation context.
 *
 * <p>This semantic marker permits evaluators to consume supported scalar fields before adapting a
 * final result. Implementations should only declare it when those guarantees apply to every
 * instance.
 */
public interface StandardScalarFieldProvider extends TypeProvider {}
