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
package org.projectnessie.cel.common.types;

import java.math.BigInteger;

/** Pure comparison and equality semantics shared by CEL's built-in numeric values. */
final class NumericComparison {
  private NumericComparison() {}

  static int compareInt(long left, long right) {
    return Long.compare(left, right);
  }

  static int compareIntUint(long left, long right) {
    if (left < 0L || right < 0L) {
      return -1;
    }
    return Long.compare(left, right);
  }

  static int compareUintInt(long left, long right) {
    return -compareIntUint(right, left);
  }

  static int compareUint(long left, long right) {
    return Long.compareUnsigned(left, right);
  }

  static int compareIntDouble(long left, double right) {
    return compareDouble((double) left, right);
  }

  static int compareDoubleInt(double left, long right) {
    return compareDouble(left, (double) right);
  }

  static int compareUintDouble(long left, double right) {
    if (right < 0.0d) {
      return 1;
    }
    return compareDouble(unsignedLongToDouble(left), right);
  }

  static int compareDoubleUint(double left, long right) {
    return compareDouble(left, unsignedLongToDouble(right));
  }

  static int compareDouble(double left, double right) {
    if (left == right) {
      // CEL treats positive and negative zero as equal.
      return 0;
    }
    return Double.compare(left, right);
  }

  static boolean equalInt(long left, long right) {
    return left == right;
  }

  static boolean equalIntUint(long left, long right) {
    return left >= 0L && right >= 0L && left == right;
  }

  static boolean equalUintInt(long left, long right) {
    return equalIntUint(right, left);
  }

  static boolean equalUint(long left, long right) {
    return left == right;
  }

  static boolean equalIntDouble(long left, double right) {
    return (double) left == right;
  }

  static boolean equalDoubleInt(double left, long right) {
    return left == (double) right;
  }

  static boolean equalUintDouble(long left, double right) {
    return unsignedLongToDouble(left) == right;
  }

  static boolean equalDoubleUint(double left, long right) {
    return left == unsignedLongToDouble(right);
  }

  static boolean equalDouble(double left, double right) {
    return left == right;
  }

  private static double unsignedLongToDouble(long value) {
    return value >= 0L
        ? (double) value
        : new BigInteger(Long.toUnsignedString(value)).doubleValue();
  }
}
