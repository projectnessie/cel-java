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
 * High-level APIs for compiling and repeatedly evaluating CEL expressions.
 *
 * <p>New integrations should configure one immutable {@link
 * org.projectnessie.cel.tools.ScriptCompiler}, compile each expression once, and reuse the
 * resulting {@link org.projectnessie.cel.tools.Script} with different inputs:
 *
 * <pre>{@code
 * ScriptCompiler compiler =
 *     ScriptCompiler.newBuilder()
 *         .withDeclarations(Decls.newVar("name", Decls.String))
 *         .build();
 * Script script = compiler.compile("name.startsWith('A')");
 * boolean matches = script.execute(Boolean.class, Map.of("name", "Ada"));
 * }</pre>
 *
 * <p>{@link org.projectnessie.cel.tools.ScriptHost} is the deprecated compatibility API. Use the
 * lower-level {@code org.projectnessie.cel} APIs when explicit parse, check, AST,
 * partial-evaluation, or planning control is required.
 */
package org.projectnessie.cel.tools;
