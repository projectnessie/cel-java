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
 * Advanced CEL planning and evaluation APIs.
 *
 * <p>Most applications should create reusable programs through {@link org.projectnessie.cel.Env}.
 * This package exposes activations, dispatch, interpretable nodes, planning decorators, evaluation
 * state, partial evaluation, and cost estimation for advanced integrations.
 *
 * <p>Planning policy may select specialized evaluation over supported Java-native representations.
 * Such native evaluation is an interpreter optimization with semantic guards and ordinary-evaluator
 * fallback; it is not Java-code generation, JNI, machine-code compilation, or GraalVM native-image
 * generation. Callers must not depend on a particular expression selecting a specialized path.
 */
package org.projectnessie.cel.interpreter;
