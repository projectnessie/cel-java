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

import com.google.api.expr.v1alpha1.Type;
import java.util.List;
import org.projectnessie.cel.common.Errors;
import org.projectnessie.cel.common.Location;
import org.projectnessie.cel.common.Source;

// typeErrors is a specialization of Errors.
public class TypeErrors extends Errors {

  public TypeErrors(Source source) {
    super(source);
  }

  void undeclaredReference(Location l, String container, String name) {
    reportError(l, "undeclared reference to '%s' (in container '%s')", name, container);
  }

  void expressionDoesNotSelectField(Location l) {
    reportError(l, "expression does not select a field");
  }

  void typeDoesNotSupportFieldSelection(Location l, Type t) {
    reportError(l, "type '%s' does not support field selection", formatCheckedType(t));
  }

  void undefinedField(Location l, String field) {
    reportError(l, "undefined field '%s'", field);
  }

  void fieldDoesNotSupportPresenceCheck(Location l, String field) {
    reportError(l, "field '%s' does not support presence check", field);
  }

  void overlappingOverload(
      Location l, String name, String overloadID1, Type f1, String overloadID2, Type f2) {
    reportError(
        l,
        "overlapping overload for name '%s' (type '%s' with overloadId: '%s' cannot be distinguished from '%s' with "
            + "overloadId: '%s')",
        name,
        formatCheckedType(f1),
        overloadID1,
        formatCheckedType(f2),
        overloadID2);
  }

  void overlappingMacro(Location l, String name, int args) {
    reportError(
        l, "overload for name '%s' with %d argument(s) overlaps with predefined macro", name, args);
  }

  void noMatchingOverload(Location l, String name, List<Type> args, boolean isInstance) {
    String signature = formatFunction(null, args, isInstance);
    reportError(l, "found no matching overload for '%s' applied to '%s'", name, signature);
  }

  void aggregateTypeMismatch(Location l, Type aggregate, Type member) {
    reportError(
        l,
        "type '%s' does not match previous type '%s' in aggregate. Use 'dyn(x)' to make the aggregate dynamic.",
        formatCheckedType(member),
        formatCheckedType(aggregate));
  }

  void notAType(Location l, Type t) {
    reportError(l, "'%s(%v)' is not a type", formatCheckedType(t), t);
  }

  void notAMessageType(Location l, Type t) {
    reportError(l, "'%s' is not a message type", formatCheckedType(t));
  }

  void fieldTypeMismatch(Location l, String name, Type field, Type value) {
    reportError(
        l,
        "expected type of field '%s' is '%s' but provided type is '%s'",
        name,
        formatCheckedType(field),
        formatCheckedType(value));
  }

  void unexpectedFailedResolution(Location l, String typeName) {
    reportError(l, "[internal] unexpected failed resolution of '%s'", typeName);
  }

  void notAComprehensionRange(Location l, Type t) {
    reportError(
        l,
        "expression of type '%s' cannot be range of a comprehension (must be list, map, or dynamic)",
        formatCheckedType(t));
  }

  void typeMismatch(Location l, Type expected, Type actual) {
    reportError(
        l,
        "expected type '%s' but found '%s'",
        formatCheckedType(expected),
        formatCheckedType(actual));
  }

  public void unknownType(Location l, String info) {
    //    reportError(l, "unknown type%s", info != null ? " for: " + info : "");
  }

  static String formatFunction(Type resultType, List<Type> argTypes, boolean isInstance) {
    StringBuilder result = new StringBuilder();
    if (isInstance) {
      Type target = argTypes.get(0);
      argTypes = argTypes.subList(1, argTypes.size());

      result.append(formatCheckedType(target));
      result.append(".");
    }

    result.append("(");
    for (int i = 0; i < argTypes.size(); i++) {
      Type arg = argTypes.get(i);
      if (i > 0) {
        result.append(", ");
      }
      result.append(formatCheckedType(arg));
    }
    result.append(")");
    if (resultType != null) {
      result.append(" -> ");
      result.append(formatCheckedType(resultType));
    }

    return result.toString();
  }
}
