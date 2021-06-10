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
 * Ast representing the checked or unchecked expression, its source, and related metadata such as
 * source position information.
 */
public class Ast {
  private final Expr expr;
  private final SourceInfo info;
  private final Source source;
  final Map<Long, Reference> refMap;
  final Map<Long, Type> typeMap;

  public Ast(Expr expr, SourceInfo info, Source source) {
    this(expr, info, source, new HashMap<>(), new HashMap<>());
  }

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

  /** Expr returns the proto serializable instance of the parsed/checked expression. */
  public Expr getExpr() {
    return expr;
  }

  /** IsChecked returns whether the Ast value has been successfully type-checked. */
  public boolean isChecked() {
    return typeMap != null && !typeMap.isEmpty();
  }

  public Source getSource() {
    return source;
  }

  /**
   * SourceInfo returns character offset and newling position information about expression elements.
   */
  public SourceInfo getSourceInfo() {
    return info;
  }

  /**
   * ResultType returns the output type of the expression if the Ast has been type-checked, else
   * returns decls.Dyn as the parse step cannot infer the type.
   */
  public Type getResultType() {
    if (!isChecked()) {
      return Decls.Dyn;
    }
    return typeMap.get(expr.getId());
  }

  /**
   * Source returns a view of the input used to create the Ast. This source may be complete or
   * constructed from the SourceInfo.
   */
  @Override
  public String toString() {
    return source.content();
  }
}
