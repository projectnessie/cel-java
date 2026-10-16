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
package org.projectnessie.cel;

import com.google.api.expr.v1alpha1.Expr;
import java.util.ArrayDeque;
import org.projectnessie.cel.OperationAbortedException.Phase;
import org.projectnessie.cel.OperationAbortedException.Reason;
import org.projectnessie.cel.OperationAbortedException.Resource;
import org.projectnessie.cel.internal.OperationController;

/** Iterative structural admission for controlled AST operations. */
final class AstAdmission {
  private AstAdmission() {}

  static void check(Ast ast, OperationController controller, Phase phase) {
    var limits = controller.limits();
    if (limits.astNodes() >= 0 || limits.astDepth() >= 0) {
      checkTree(ast.getExpr(), controller, phase, limits.astNodes(), limits.astDepth());
    }
    if (limits.astMetadataEntries() >= 0) {
      long entries = 0;
      var info = ast.getSourceInfo();
      if (info != null) {
        entries = add(entries, info.getPositionsCount(), limits.astMetadataEntries(), phase);
        entries = add(entries, info.getLineOffsetsCount(), limits.astMetadataEntries(), phase);
        entries = add(entries, info.getMacroCallsCount(), limits.astMetadataEntries(), phase);
        for (Expr macro : info.getMacroCallsMap().values()) {
          entries =
              checkMetadataTree(macro, controller, phase, entries, limits.astMetadataEntries());
        }
      }
      entries = add(entries, ast.refMap.size(), limits.astMetadataEntries(), phase);
      add(entries, ast.typeMap.size(), limits.astMetadataEntries(), phase);
    }
  }

  private static long checkTree(
      Expr root, OperationController controller, Phase phase, long nodeLimit, int depthLimit) {
    var pending = new ArrayDeque<NodeDepth>();
    pending.push(new NodeDepth(root, 1));
    long count = 0;
    while (!pending.isEmpty()) {
      controller.checkpoint(phase);
      var current = pending.pop();
      count++;
      if (nodeLimit >= 0 && count > nodeLimit) {
        throw limit(Reason.AST_NODE_LIMIT, phase, Resource.AST_NODES, nodeLimit, count);
      }
      if (depthLimit >= 0 && current.depth > depthLimit) {
        throw limit(Reason.AST_DEPTH_LIMIT, phase, Resource.AST_DEPTH, depthLimit, current.depth);
      }
      pushChildren(current.expr, current.depth + 1, pending);
    }
    return count;
  }

  private static long checkMetadataTree(
      Expr root,
      OperationController controller,
      Phase phase,
      long initialCount,
      long metadataLimit) {
    var pending = new ArrayDeque<Expr>();
    pending.push(root);
    long count = initialCount;
    while (!pending.isEmpty()) {
      controller.checkpoint(phase);
      var current = pending.pop();
      count = add(count, 1, metadataLimit, phase);
      pushMetadataChildren(current, pending);
    }
    return count;
  }

  private static void pushChildren(Expr expr, int depth, ArrayDeque<NodeDepth> pending) {
    switch (expr.getExprKindCase()) {
      case SELECT_EXPR -> pending.push(new NodeDepth(expr.getSelectExpr().getOperand(), depth));
      case CALL_EXPR -> {
        var call = expr.getCallExpr();
        for (Expr arg : call.getArgsList()) {
          pending.push(new NodeDepth(arg, depth));
        }
        if (call.hasTarget()) {
          pending.push(new NodeDepth(call.getTarget(), depth));
        }
      }
      case LIST_EXPR -> {
        for (Expr element : expr.getListExpr().getElementsList()) {
          pending.push(new NodeDepth(element, depth));
        }
      }
      case STRUCT_EXPR -> {
        for (var entry : expr.getStructExpr().getEntriesList()) {
          pending.push(new NodeDepth(entry.getValue(), depth));
          if (entry.hasMapKey()) {
            pending.push(new NodeDepth(entry.getMapKey(), depth));
          }
        }
      }
      case COMPREHENSION_EXPR -> {
        var comprehension = expr.getComprehensionExpr();
        pending.push(new NodeDepth(comprehension.getIterRange(), depth));
        pending.push(new NodeDepth(comprehension.getAccuInit(), depth));
        pending.push(new NodeDepth(comprehension.getLoopCondition(), depth));
        pending.push(new NodeDepth(comprehension.getLoopStep(), depth));
        pending.push(new NodeDepth(comprehension.getResult(), depth));
      }
      default -> {
        // Scalar expression.
      }
    }
  }

  private static void pushMetadataChildren(Expr expr, ArrayDeque<Expr> pending) {
    switch (expr.getExprKindCase()) {
      case SELECT_EXPR -> pending.push(expr.getSelectExpr().getOperand());
      case CALL_EXPR -> {
        var call = expr.getCallExpr();
        pending.addAll(call.getArgsList());
        if (call.hasTarget()) {
          pending.push(call.getTarget());
        }
      }
      case LIST_EXPR -> pending.addAll(expr.getListExpr().getElementsList());
      case STRUCT_EXPR -> {
        for (var entry : expr.getStructExpr().getEntriesList()) {
          pending.push(entry.getValue());
          if (entry.hasMapKey()) {
            pending.push(entry.getMapKey());
          }
        }
      }
      case COMPREHENSION_EXPR -> {
        var comprehension = expr.getComprehensionExpr();
        pending.push(comprehension.getIterRange());
        pending.push(comprehension.getAccuInit());
        pending.push(comprehension.getLoopCondition());
        pending.push(comprehension.getLoopStep());
        pending.push(comprehension.getResult());
      }
      default -> {
        // Scalar expression.
      }
    }
  }

  private static long add(long value, long increment, long limit, Phase phase) {
    var observed = value + increment;
    if (observed < value) {
      observed = Long.MAX_VALUE;
    }
    if (observed > limit) {
      throw limit(Reason.AST_METADATA_LIMIT, phase, Resource.AST_METADATA_ENTRIES, limit, observed);
    }
    return observed;
  }

  private static OperationAbortedException limit(
      Reason reason, Phase phase, Resource resource, long limit, long observed) {
    return new OperationAbortedException(reason, phase, resource, limit, observed);
  }

  private record NodeDepth(Expr expr, int depth) {}
}
