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

import static org.projectnessie.cel.common.types.Types.boolOf;

import java.util.Arrays;
import java.util.Objects;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;

/**
 * An unknown CEL value together with the expression IDs that contributed to it.
 *
 * <p>Unknown values arise during partial evaluation when an input selected by an unknown attribute
 * pattern is encountered. The expression IDs form a non-empty, sorted set: duplicate IDs are
 * removed and iteration order is deterministic. Instances are immutable.
 *
 * <p>{@link #equal(Val)} retains CEL's type-level unknown behavior and therefore does not compare
 * provenance. Use Java {@link #equals(Object)} when exact provenance-set equality is required.
 */
public final class UnknownT extends BaseVal {
  /** CEL unknown type singleton. */
  public static final Type UnknownType = TypeT.newTypeValue(TypeEnum.Unknown);

  /**
   * Creates an unknown value attributed to one expression ID.
   *
   * @param expressionId expression ID contributing to the unknown value
   * @return the immutable unknown value
   */
  public static UnknownT unknownOf(long expressionId) {
    return new UnknownT(new long[] {expressionId});
  }

  /**
   * Creates an unknown value attributed to one or more expression IDs.
   *
   * <p>The supplied IDs are sorted and deduplicated. The varargs array is not retained.
   *
   * @param firstExpressionId first expression ID, which makes the set non-empty
   * @param additionalExpressionIds any additional contributing expression IDs
   * @return the immutable unknown value
   * @throws NullPointerException if {@code additionalExpressionIds} is {@code null}
   */
  public static UnknownT unknownOf(long firstExpressionId, long... additionalExpressionIds) {
    Objects.requireNonNull(additionalExpressionIds, "additionalExpressionIds");
    if (additionalExpressionIds.length == 0) {
      return unknownOf(firstExpressionId);
    }

    long[] expressionIds = new long[additionalExpressionIds.length + 1];
    expressionIds[0] = firstExpressionId;
    System.arraycopy(additionalExpressionIds, 0, expressionIds, 1, additionalExpressionIds.length);
    Arrays.sort(expressionIds);

    int unique = 1;
    for (int i = 1; i < expressionIds.length; i++) {
      if (expressionIds[i] != expressionIds[unique - 1]) {
        expressionIds[unique++] = expressionIds[i];
      }
    }
    return new UnknownT(
        unique == expressionIds.length ? expressionIds : Arrays.copyOf(expressionIds, unique));
  }

  private final long[] expressionIds;

  private UnknownT(long[] expressionIds) {
    this.expressionIds = expressionIds;
  }

  /**
   * Returns the sorted expression IDs that contributed to this unknown value.
   *
   * @return a defensive copy of the non-empty provenance set
   */
  public long[] expressionIds() {
    return expressionIds.clone();
  }

  /**
   * Returns the union of this value's and {@code other}'s expression IDs.
   *
   * <p>If either operand already represents the complete union, that operand is returned directly.
   *
   * @param other another unknown value
   * @return an immutable unknown containing both provenance sets
   * @throws NullPointerException if {@code other} is {@code null}
   */
  public UnknownT merge(UnknownT other) {
    Objects.requireNonNull(other, "other");
    if (this == other) {
      return this;
    }

    int left = 0;
    int right = 0;
    boolean leftContributed = false;
    boolean rightContributed = false;
    while (left < expressionIds.length || right < other.expressionIds.length) {
      if (right == other.expressionIds.length
          || (left < expressionIds.length && expressionIds[left] < other.expressionIds[right])) {
        left++;
        leftContributed = true;
      } else if (left == expressionIds.length || other.expressionIds[right] < expressionIds[left]) {
        right++;
        rightContributed = true;
      } else {
        left++;
        right++;
      }
    }

    if (!rightContributed) {
      return this;
    }
    if (!leftContributed) {
      return other;
    }

    long[] merged = new long[expressionIds.length + other.expressionIds.length];
    left = 0;
    right = 0;
    int result = 0;
    while (left < expressionIds.length || right < other.expressionIds.length) {
      if (right == other.expressionIds.length
          || (left < expressionIds.length && expressionIds[left] < other.expressionIds[right])) {
        merged[result++] = expressionIds[left++];
      } else if (left == expressionIds.length || other.expressionIds[right] < expressionIds[left]) {
        merged[result++] = other.expressionIds[right++];
      } else {
        merged[result++] = expressionIds[left++];
        right++;
      }
    }
    return new UnknownT(result == merged.length ? merged : Arrays.copyOf(merged, result));
  }

  /**
   * Converts this value to its native representation.
   *
   * <p>{@code long[]} and {@code Object} receive a defensive array. Converting to a scalar {@code
   * long} remains supported for singleton unknowns for compatibility, but is rejected when
   * provenance contains multiple IDs.
   *
   * @throws RuntimeException if the requested representation is unsupported, including a scalar
   *     representation for a multi-ID value
   */
  @SuppressWarnings({"removal", "unchecked"})
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc == Val.class || typeDesc == UnknownT.class) {
      return (T) this;
    }
    if (typeDesc == long[].class || typeDesc == Object.class) {
      return (T) expressionIds();
    }
    if ((typeDesc == Long.class || typeDesc == long.class) && expressionIds.length == 1) {
      return (T) Long.valueOf(expressionIds[0]);
    }
    throw new RuntimeException(
        String.format(
            "native type conversion error from '%s' to '%s'", UnknownType, typeDesc.getName()));
  }

  /**
   * Returns the sole expression ID for scalar compatibility.
   *
   * @throws IllegalStateException if this value contains multiple expression IDs
   */
  @Override
  public long intValue() {
    if (expressionIds.length != 1) {
      throw new IllegalStateException("unknown contains multiple expression ids");
    }
    return expressionIds[0];
  }

  /** Returns this unknown unchanged for CEL type conversions. */
  @Override
  public Val convertToType(Type typeVal) {
    return this;
  }

  /**
   * Returns whether {@code other} is an unknown CEL value.
   *
   * <p>CEL equality does not expose or compare unknown provenance.
   */
  @Override
  public Val equal(Val other) {
    return boolOf(other.type().typeEnum() == TypeEnum.Unknown);
  }

  /** Returns the CEL unknown type. */
  @Override
  public Type type() {
    return UnknownType;
  }

  /** Returns a defensive array containing the sorted expression IDs. */
  @Override
  public Object value() {
    return expressionIds();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UnknownT unknownT = (UnknownT) o;
    return Arrays.equals(expressionIds, unknownT.expressionIds);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(expressionIds);
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder("unknown{");
    for (int i = 0; i < expressionIds.length; i++) {
      if (i > 0) {
        result.append(", ");
      }
      result.append(expressionIds[i]);
    }
    return result.append('}').toString();
  }

  /** Returns whether {@code val} is an unknown CEL value. */
  public static boolean isUnknown(Object val) {
    return val != null && val.getClass() == UnknownT.class;
  }
}
