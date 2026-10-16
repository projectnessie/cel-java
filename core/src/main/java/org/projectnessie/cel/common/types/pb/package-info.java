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
 * Protocol Buffer type registration, checked-type mapping, and runtime value adaptation.
 *
 * <p>{@link org.projectnessie.cel.common.types.pb.ProtoTypeRegistry} is the primary integration
 * entry point. It supports generated and dynamic protobuf messages, protobuf well-known types, enum
 * constants, extensions, CEL object construction, and independent registry copies. The other public
 * types in this package expose lower-level descriptor and value machinery for advanced
 * integrations.
 *
 * <p>{@code cel-core} does not choose a generated protobuf runtime transitively. Applications must
 * add exactly one of {@code cel-generated-pb} or {@code cel-generated-pb3}; both provide the same
 * generated CEL expression classes for different protobuf runtime choices and must not appear
 * together on one class path.
 */
package org.projectnessie.cel.common.types.pb;
