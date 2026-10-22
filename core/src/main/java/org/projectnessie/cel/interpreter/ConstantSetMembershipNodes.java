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

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;

import java.util.Set;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.ref.Val;

/** Shared post-evaluation semantics for optimized constant-set membership. */
final class ConstantSetMembershipSupport {
  private ConstantSetMembershipSupport() {}

  static Val evaluate(Val needle, String needleTypeName, Set<Val> values) {
    if (isUnknownOrError(needle)) {
      return needle;
    }
    if (values.isEmpty()) {
      return False;
    }
    if (!needle.type().typeName().equals(needleTypeName)) {
      return noSuchOverload(null, Operator.In.id, needle);
    }
    return values.contains(needle) ? True : False;
  }
}
