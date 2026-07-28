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
 * Jackson 2 integration for exposing Java application objects to CEL.
 *
 * <p>This package belongs to the {@code cel-jackson} artifact. Create a {@link
 * org.projectnessie.cel.types.jackson.JacksonRegistry} when inputs are modeled by Jackson 2. The
 * separate {@code cel-jackson3} artifact serves Jackson 3 applications; applications normally need
 * only one of the two integration artifacts. It is used alongside {@code cel-core} or {@code
 * cel-tools} and exactly one generated protobuf artifact.
 *
 * <p>The registry maps Jackson-visible bean properties to CEL object fields. It is an input
 * adapter, not a general Jackson serialization bridge, and it does not construct Java objects from
 * CEL object literals.
 */
package org.projectnessie.cel.types.jackson;
