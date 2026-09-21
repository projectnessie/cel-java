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
 * Advanced CEL parsing, macro, and unparsing APIs.
 *
 * <p>Most applications should parse and check expressions through {@link
 * org.projectnessie.cel.Env}. Direct parser users provide a {@link
 * org.projectnessie.cel.common.Source}, parser {@link org.projectnessie.cel.parser.Options}, and
 * macro set and receive an expression plus structured diagnostics.
 *
 * <p>Macros rewrite source expressions into CEL expression trees. A runtime that evaluates an
 * exported tree must provide semantics compatible with every macro expansion used to create it; no
 * cross-runtime portability promise is made for CEL-Java-specific comprehension-v2 expansion
 * shapes.
 */
package org.projectnessie.cel.parser;
