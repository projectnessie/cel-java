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
 * Core CEL parsing, checking, planning, and evaluation APIs.
 *
 * <p>{@link org.projectnessie.cel.Env} coordinates compile-time declarations, macros, configured
 * type providers and registries, and program defaults. Parse and check source into an {@link
 * org.projectnessie.cel.Ast}, create a reusable {@link org.projectnessie.cel.Program}, and evaluate
 * it with an activation or Java map. {@link org.projectnessie.cel.EnvOption} and {@link
 * org.projectnessie.cel.ProgramOption} provide built-in configuration tokens; {@link
 * org.projectnessie.cel.Library} packages matching compile-time and runtime extensions.
 *
 * <p>The package also exposes advanced AST conversion, partial-evaluation, and state-inspection
 * APIs. Hosts remain responsible for expression policy and resource controls: creating a program
 * does not establish a CPU, memory, result-size, or latency budget.
 */
package org.projectnessie.cel;
