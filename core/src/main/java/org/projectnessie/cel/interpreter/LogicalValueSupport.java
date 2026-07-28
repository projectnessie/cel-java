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

import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Types.boolOf;

import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.UnknownT;
import org.projectnessie.cel.common.types.ref.Val;

/** Shared CEL logical-result combination for established and native evaluator paths. */
final class LogicalValueSupport {
  private LogicalValueSupport() {}

  static Val combine(Val left, Val right, boolean and) {
    Val shortCircuit = boolOf(!and);
    Val identity = boolOf(and);
    if (left == shortCircuit || right == shortCircuit) {
      return shortCircuit;
    }
    if (left == identity && right == identity) {
      return identity;
    }
    if (left instanceof UnknownT leftUnknown && right instanceof UnknownT rightUnknown) {
      return leftUnknown.merge(rightUnknown);
    }
    if (left instanceof UnknownT) {
      return left;
    }
    if (right instanceof UnknownT) {
      return right;
    }
    if (isError(left)) {
      return left;
    }
    return noSuchOverload(left, and ? Operator.LogicalAnd.id : Operator.LogicalOr.id, right);
  }
}
