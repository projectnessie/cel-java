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
package org.projectnessie.cel.parser;

import com.google.api.expr.v1alpha1.Expr;

/**
 * Grammar node capable of converting itself through a {@link CelExprBuilder}.
 *
 * <p>This internal bridge remains public for compatibility but is not an external parser extension
 * point because the corresponding builder uses package-private node types.
 */
public interface CelExprNode {
  /** Converts this grammar node into a protobuf CEL expression. */
  Expr toCelExpr(CelExprBuilder builder);
}
