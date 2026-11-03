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
 * Runtime implementations of CEL function overloads.
 *
 * <p>Extension authors pair checked function declarations with {@link
 * org.projectnessie.cel.interpreter.functions.Overload} implementations and register those
 * implementations through {@link org.projectnessie.cel.ProgramOption#functions(Overload...)}. The
 * overload identifier used at checking time must match the runtime identifier. Fixed-arity
 * interfaces avoid argument-array handling; {@link
 * org.projectnessie.cel.interpreter.functions.FunctionOp} supports other arities.
 *
 * <p>Function implementations may be called concurrently when a program is shared. They must
 * therefore be thread-safe and must not rely on mutable per-evaluation state stored in the overload
 * object. Implementations return CEL values, including CEL error or unknown values where
 * applicable, and must not return Java {@code null}.
 */
package org.projectnessie.cel.interpreter.functions;
