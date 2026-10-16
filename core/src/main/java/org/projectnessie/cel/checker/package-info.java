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
 * Advanced CEL type-checking APIs.
 *
 * <p>Most applications should configure and compile expressions through {@link
 * org.projectnessie.cel.Env}. The types in this package expose the lower-level declaration, scope,
 * substitution, and checking machinery used by an environment. Callers using these APIs directly
 * are responsible for supplying a coherent container, type provider, declarations, and overload
 * set.
 *
 * <p>Checker configuration is intended to be completed before it is shared. Individual API
 * contracts describe any stronger concurrency or ownership guarantees.
 */
package org.projectnessie.cel.checker;
