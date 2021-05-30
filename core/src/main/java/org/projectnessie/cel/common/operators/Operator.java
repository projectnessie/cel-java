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
package org.projectnessie.cel.common.operators;

import java.util.HashMap;
import java.util.Map;

public enum Operator {

  // Symbolic operators.
  Conditional("_?_:_", 8, null),
  LogicalAnd("_&&_", 6, "&&"),
  LogicalOr("_||_", 7, "||"),
  LogicalNot("!_", 2, "!"),
  Equals("_==_", 5, "=="),
  NotEquals("_!=_", 5, "!="),
  Less("_<_", 5, "<"),
  LessEquals("_<=_", 5, "<="),
  Greater("_>_", 5, ">"),
  GreaterEquals("_>=_", 5, ">="),
  Add("_+_", 4, "+"),
  Subtract("_-_", 4, "-"),
  Multiply("_*_", 3, "*"),
  Divide("_/_", 3, "/"),
  Modulo("_%_", 3, "%"),
  Negate("-_", 2, "-"),
  Index("_[_]", 1, null),
  // Macros, must have a valid identifier.
  Has("has"),
  All("all"),
  Exists("exists"),
  ExistsOne("exists_one"),
  Map("map"),
  Filter("filter"),
  // Named operators, must not have be valid identifiers.
  NotStrictlyFalse("@not_strictly_false"),
  In("@in", 5, "in"),
  // Deprecated: named operators with valid identifiers.
  OldNotStrictlyFalse("__not_strictly_false__"),
  OldIn("_in_", 5, "in");

  private static final Map<String, Operator> operators;
  private static final Map<String, Operator> operatorsById;
  // precedence of the operator, where the higher value means higher.

  public final String id;
  public final String reverse;
  public final int precedence;

  Operator(String id) {
    this(id, 0, null);
  }

  Operator(String id, int precedence, String reverse) {
    this.id = id;
    this.precedence = precedence;
    this.reverse = reverse;
  }

  static {
    {
      Map<String, Operator> m = new HashMap<>();
      m.put("+", Add);
      m.put("/", Divide);
      m.put("==", Equals);
      m.put(">", Greater);
      m.put(">=", GreaterEquals);
      m.put("in", In);
      m.put("<", Less);
      m.put("<=", LessEquals);
      m.put("%", Modulo);
      m.put("*", Multiply);
      m.put("!=", NotEquals);
      m.put("-", Subtract);
      operators = m;
    }

    {
      Map<String, Operator> m = new HashMap<>();
      for (Operator op : Operator.values()) {
        m.put(op.id, op);
      }
      operatorsById = m;
    }
  }

  public static Operator byId(String id) {
    return operatorsById.get(id);
  }

  // Find the internal function name for an operator, if the input text is one.
  public static Operator find(String text) {
    return operators.get(text);
  }

  // FindReverse returns the unmangled, text representation of the operator.
  public static String findReverse(String id) {
    Operator op = byId(id);
    return op != null ? op.reverse : null;
  }

  // FindReverseBinaryOperator returns the unmangled, text representation of a binary operator.
  public static String findReverseBinaryOperator(String id) {
    Operator op = byId(id);
    if (op == null || op == LogicalNot || op == Negate) {
      return null;
    }
    return op.reverse;
  }

  public static int precedence(String id) {
    Operator op = byId(id);
    return op != null ? op.precedence : 0;
  }
}
