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
package org.projectnessie.cel;

import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Reference;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.api.expr.v1alpha1.Type;
import java.util.HashMap;
import java.util.Map;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.Source;

/**
 * A parsed or type-checked CEL abstract syntax tree and its source metadata.
 *
 * <p>An AST returned by {@link Env#parse(String)} is unchecked. Passing it to {@link
 * Env#check(Ast)} produces a checked AST with reference and type metadata. Prefer a checked AST
 * when creating a {@link Program}; unchecked ASTs are supported but provide less planning
 * information.
 *
 * <p>The expression and source-info values are immutable Protobuf messages. The constructors retain
 * the supplied {@link Source} and, for the full constructor, the supplied reference and type maps.
 * Callers that construct an AST directly must keep retained mutable state stable while the AST is
 * in use.
 *
 * <p>{@link CEL#astToParsedExpr(Ast)} and {@link CEL#astToCheckedExpr(Ast)} expose the
 * corresponding Protobuf representations. Exporting those messages does not by itself guarantee
 * that another CEL runtime recognizes implementation-specific macro expansions.
 */
public final class Ast {
  private final Expr expr;
  private final SourceInfo info;
  private final Source source;
  final Map<Long, Reference> refMap;
  final Map<Long, Type> typeMap;

  /**
   * Creates an unchecked AST.
   *
   * @param expr parsed CEL expression
   * @param info source and position metadata
   * @param source source used to parse the expression
   */
  public Ast(Expr expr, SourceInfo info, Source source) {
    this(expr, info, source, new HashMap<>(), new HashMap<>());
  }

  /**
   * Creates an AST with explicit reference and type metadata.
   *
   * <p>The maps are retained rather than copied. A non-empty type map marks the AST as checked.
   *
   * @param expr parsed or checked CEL expression
   * @param info source and position metadata
   * @param source source used to parse or check the expression
   * @param refMap checked reference metadata keyed by expression ID
   * @param typeMap checked type metadata keyed by expression ID
   */
  public Ast(
      Expr expr,
      SourceInfo info,
      Source source,
      Map<Long, Reference> refMap,
      Map<Long, Type> typeMap) {
    this.expr = expr;
    this.info = info;
    this.source = source;
    this.refMap = refMap;
    this.typeMap = typeMap;
  }

  /**
   * Returns the parsed or checked expression Protobuf message.
   *
   * @return expression tree
   */
  public Expr getExpr() {
    return expr;
  }

  /**
   * Reports whether this AST contains checked type metadata.
   *
   * @return {@code true} when the type map is non-null and non-empty
   */
  public boolean isChecked() {
    return typeMap != null && !typeMap.isEmpty();
  }

  /**
   * Returns the source associated with this AST.
   *
   * @return retained source
   */
  public Source getSource() {
    return source;
  }

  /**
   * Returns source-location and macro-expansion metadata for expression elements.
   *
   * @return source information
   */
  public SourceInfo getSourceInfo() {
    return info;
  }

  /**
   * Returns the checked result type of this expression.
   *
   * @return the root expression's checked type, or {@link Decls#Dyn} for an unchecked AST
   */
  public Type getResultType() {
    if (!isChecked()) {
      return Decls.Dyn;
    }
    return typeMap.get(expr.getId());
  }

  /**
   * Returns the source text associated with this AST.
   *
   * <p>This is the retained source's content, not a newly unparsed representation of the
   * expression. Use {@link CEL#astToString(Ast)} to obtain a stable unparsed expression.
   *
   * @return source content
   */
  @Override
  public String toString() {
    return source.content();
  }
}
