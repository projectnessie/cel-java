/*
 * Copyright (C) 2023 The Authors of CEL-Java
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

import java.util.Objects;

/**
 * Legacy activation-resolution result distinguishing absent, present-null, and present values.
 *
 * @deprecated migrate callbacks to {@link ActivationFunction}, using {@link
 *     ActivationFunction#ABSENT} for absence and ordinary {@code null} for present null
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated(forRemoval = true)
public final class ResolvedValue {
  /** Legacy singleton representing a present {@code null} value. */
  public static final ResolvedValue NULL_VALUE = new ResolvedValue(null, true);

  /** Legacy singleton representing an absent binding. */
  public static final ResolvedValue ABSENT = new ResolvedValue(null, false);

  /** Returns a legacy result containing a non-null value. */
  public static ResolvedValue resolvedValue(Object value) {
    return new ResolvedValue(Objects.requireNonNull(value), true);
  }

  private final Object value;
  private final boolean present;

  private ResolvedValue(Object value, boolean present) {
    this.value = value;
    this.present = present;
  }

  /** Returns the contained value, including {@code null} for {@link #NULL_VALUE}. */
  public Object value() {
    return value;
  }

  /** Returns whether a binding is present. */
  public boolean present() {
    return present;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ResolvedValue that = (ResolvedValue) o;

    if (present != that.present) return false;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    int result = value != null ? value.hashCode() : 0;
    result = 31 * result + (present ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ResolvedValue{present=" + present + ", value=" + value + '}';
  }

  static Object mapLegacy(Object o) {
    if (o instanceof ResolvedValue resolvedValue) {
      if (resolvedValue.present()) {
        return resolvedValue.value();
      }
      return ActivationFunction.ABSENT;
    }
    return Objects.requireNonNullElse(o, ActivationFunction.ABSENT);
  }

  static ResolvedValue mapTo(Object result) {
    if (result instanceof ResolvedValue) {
      return (ResolvedValue) result;
    } else if (result == null) {
      return ResolvedValue.NULL_VALUE;
    } else if (result == ActivationFunction.ABSENT) {
      return ResolvedValue.ABSENT;
    }
    return ResolvedValue.resolvedValue(result);
  }
}
