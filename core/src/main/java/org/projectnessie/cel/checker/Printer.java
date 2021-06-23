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
package org.projectnessie.cel.checker;

import static org.projectnessie.cel.checker.Types.formatCheckedType;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Reference;
import com.google.api.expr.v1alpha1.Type;
import org.projectnessie.cel.common.debug.Debug;
import org.projectnessie.cel.common.debug.Debug.Adorner;

public final class Printer {

  static final class SemanticAdorner implements Adorner {
    private final CheckedExpr checks;

    SemanticAdorner(CheckedExpr checks) {
      this.checks = checks;
    }

    @Override
    public String getMetadata(Object elem) {
      if (!(elem instanceof Expr)) {
        return "";
      }
      StringBuilder result = new StringBuilder();
      Expr e = (Expr) elem;
      Type t = checks.getTypeMapMap().get(e.getId());
      if (t != null) {
        result.append("~");
        result.append(formatCheckedType(t));
      }

      switch (e.getExprKindCase()) {
        case IDENT_EXPR:
        case CALL_EXPR:
        case STRUCT_EXPR:
        case SELECT_EXPR:
          Reference ref = checks.getReferenceMapMap().get(e.getId());
          if (ref != null) {
            if (ref.getOverloadIdCount() == 0) {
              result.append("^").append(ref.getName());
            } else {
              for (int i = 0; i < ref.getOverloadIdCount(); i++) {
                if (i == 0) {
                  result.append("^");
                } else {
                  result.append("|");
                }
                result.append(ref.getOverloadId(i));
              }
            }
          }
      }

      return result.toString();
    }
  }

  /**
   * Print returns a string representation of the Expr message, annotated with types from the
   * CheckedExpr. The Expr must be a sub-expression embedded in the CheckedExpr.
   */
  public static String print(Expr e, CheckedExpr checks) {
    SemanticAdorner a = new SemanticAdorner(checks);
    return Debug.toAdornedDebugString(e, a);
  }
}
