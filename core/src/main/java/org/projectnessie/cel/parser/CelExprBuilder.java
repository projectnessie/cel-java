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
import org.projectnessie.cel.common.operators.Operator;

/**
 * Converts nodes produced by the generated CEL grammar parser into protobuf {@link Expr} instances.
 *
 * <p>This is an internal generated-parser bridge that remains public for compatibility, not an
 * externally implementable or general-purpose expression builder. Its node parameter types are
 * package-private. Custom macros should use {@link ExprHelper} instead.
 */
public interface CelExprBuilder {
  /** Converts an expression grammar node. */
  Expr visitExpr(Node node);

  /** Converts a balanced binary expression using the supplied operator. */
  Expr visitBalanced(Node node, Operator operator);

  /** Converts a binary-expression grammar node. */
  Expr visitBinary(Node node);

  /** Converts a unary-expression grammar node. */
  Expr visitUnary(Node node);

  /** Converts a member-access or receiver-call grammar node. */
  Expr visitMember(Node node);

  /** Converts a primary-expression grammar node. */
  Expr visitPrimary(Node node);

  /** Converts a literal grammar node. */
  Expr visitLiteral(Node node);

  /** Converts an identifier token. */
  Expr visitIdentifier(Token token);
}
