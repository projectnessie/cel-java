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
package org.projectnessie.cel.interpreter;

import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Reference;
import java.util.Map;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.interpreter.functions.Overload;

/** Exact identity checks for calls resolved to the built-in overload objects. */
final class StandardOverloadProvenance {
  private StandardOverloadProvenance() {}

  static boolean isExactStandard(
      Dispatcher dispatcher, Map<Long, Reference> references, Expr expression) {
    if (expression.getExprKindCase() != Expr.ExprKindCase.CALL_EXPR) {
      return false;
    }
    Reference reference = references.get(expression.getId());
    if (reference == null || reference.getOverloadIdCount() != 1) {
      return false;
    }
    String function = expression.getCallExpr().getFunction();
    Overload implementation = dispatcher.findOverload(reference.getOverloadId(0));
    if (implementation == null) {
      implementation = dispatcher.findOverload(function);
    }
    if (implementation == null) {
      return isIntrinsicStandard(function, reference.getOverloadId(0));
    }
    return isExactStandardImplementation(implementation, function);
  }

  static boolean isExactStandardImplementation(Overload implementation, String function) {
    if (implementation == null) {
      return false;
    }
    for (Overload standard : Overload.standardOverloads()) {
      if (standard == implementation && standard.operator.equals(function)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isIntrinsicStandard(String function, String overload) {
    return (function.equals(Operator.LogicalAnd.id) && overload.equals(Overloads.LogicalAnd))
        || (function.equals(Operator.LogicalOr.id) && overload.equals(Overloads.LogicalOr))
        || (function.equals(Operator.Conditional.id) && overload.equals(Overloads.Conditional))
        || (function.equals(Operator.Equals.id) && overload.equals(Overloads.Equals))
        || (function.equals(Operator.NotEquals.id) && overload.equals(Overloads.NotEquals));
  }
}
