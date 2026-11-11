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
package org.projectnessie.cel.parser;

import com.google.api.expr.v1alpha1.Expr;
import java.util.List;
import org.projectnessie.cel.common.ErrorWithLocation;

/**
 * Rewrites the target and arguments of a function call that matches a {@link Macro}.
 *
 * <p>The target is {@code null} for a global call and non-null for a receiver-style call. Throw
 * {@link ErrorWithLocation} to report a source-aware parse diagnostic.
 */
@FunctionalInterface
public interface MacroExpander {
  /**
   * Expands one matching call.
   *
   * @param eh source-aware expression construction helper
   * @param target receiver expression, or {@code null} for a global call
   * @param args call arguments, excluding the receiver
   * @return replacement expression
   * @throws ErrorWithLocation if the matched call cannot be expanded
   */
  Expr expand(ExprHelper eh, Expr target, List<Expr> args) throws ErrorWithLocation;
}
