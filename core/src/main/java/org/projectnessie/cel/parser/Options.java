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
package org.projectnessie.cel.parser;

import static java.util.Arrays.asList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable parser limits and macro configuration.
 *
 * <p>Defaults limit recursion to 250 levels and source size to 100,000 Unicode code points. These
 * are parser resource limits; they are not runtime evaluation budgets.
 */
public final class Options {
  private final int maxRecursionDepth;
  private final int errorRecoveryLimit;
  private final int expressionSizeCodePointLimit;
  private final Map<String, Macro> macros;

  private Options(
      int maxRecursionDepth,
      int errorRecoveryLimit,
      int expressionSizeCodePointLimit,
      Map<String, Macro> macros) {
    this.maxRecursionDepth = maxRecursionDepth;
    this.errorRecoveryLimit = errorRecoveryLimit;
    this.expressionSizeCodePointLimit = expressionSizeCodePointLimit;
    this.macros = macros;
  }

  /** Returns the maximum parser recursion depth. */
  public int getMaxRecursionDepth() {
    return maxRecursionDepth;
  }

  /**
   * Returns the maximum number of parser error-recovery attempts.
   *
   * <p>This setting is currently not respected because CongoCC stops parsing after the first syntax
   * error and does not provide error recovery.
   */
  public int getErrorRecoveryLimit() {
    return errorRecoveryLimit;
  }

  /** Returns the maximum source size measured in Unicode code points. */
  public int getExpressionSizeCodePointLimit() {
    return expressionSizeCodePointLimit;
  }

  /** Returns the macro registered for a parser lookup key, or {@code null} when absent. */
  public Macro getMacro(String name) {
    return macros.get(name);
  }

  /** Returns a builder initialized with parser limits and no macros. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for immutable parser options. Builder instances are not thread-safe. */
  public static final class Builder {
    private final Map<String, Macro> macros = new HashMap<>();
    private int maxRecursionDepth = 250;
    private int errorRecoveryLimit = 30;
    private int expressionSizeCodePointLimit = 100_000;

    private Builder() {}

    /**
     * Sets the parser recursion limit.
     *
     * @param maxRecursionDepth non-negative limit, or {@code -1} for no configured limit
     * @throws IllegalArgumentException if the value is less than {@code -1}
     */
    public Builder maxRecursionDepth(int maxRecursionDepth) {
      if (maxRecursionDepth < -1) {
        throw new IllegalArgumentException(
            String.format(
                "max recursion depth must be greater than or equal to -1: %d", maxRecursionDepth));
      } else if (maxRecursionDepth == -1) {
        maxRecursionDepth = Integer.MAX_VALUE;
      }
      this.maxRecursionDepth = maxRecursionDepth;
      return this;
    }

    /**
     * Sets the maximum number of parser error-recovery attempts.
     *
     * <p>This setting is currently not respected because CongoCC stops parsing after the first
     * syntax error and does not provide error recovery.
     */
    public Builder errorRecoveryLimit(int errorRecoveryLimit) {
      if (errorRecoveryLimit < -1) {
        throw new IllegalArgumentException(
            String.format(
                "error recovery limit must be greater than or equal to -1: %d",
                errorRecoveryLimit));
      } else if (errorRecoveryLimit == -1) {
        errorRecoveryLimit = Integer.MAX_VALUE;
      }
      this.errorRecoveryLimit = errorRecoveryLimit;
      return this;
    }

    /**
     * Sets the source-size limit measured in Unicode code points.
     *
     * @param expressionSizeCodePointLimit non-negative limit, or {@code -1} for no configured limit
     * @throws IllegalArgumentException if the value is less than {@code -1}
     */
    public Builder expressionSizeCodePointLimit(int expressionSizeCodePointLimit) {
      if (expressionSizeCodePointLimit < -1) {
        throw new IllegalArgumentException(
            String.format(
                "expression size code point limit must be greater than or equal to -1: %d",
                expressionSizeCodePointLimit));
      } else if (expressionSizeCodePointLimit == -1) {
        expressionSizeCodePointLimit = Integer.MAX_VALUE;
      }
      this.expressionSizeCodePointLimit = expressionSizeCodePointLimit;
      return this;
    }

    /** Adds or replaces macros by their name, arity, and call-style lookup key. */
    public Builder macros(Macro... macros) {
      return macros(asList(macros));
    }

    /** Adds or replaces macros by their name, arity, and call-style lookup key. */
    public Builder macros(List<Macro> macros) {
      for (Macro macro : macros) {
        this.macros.put(macro.macroKey(), macro);
      }
      return this;
    }

    /** Builds an immutable snapshot of the configured parser options. */
    public Options build() {
      return new Options(
          maxRecursionDepth, errorRecoveryLimit, expressionSizeCodePointLimit, Map.copyOf(macros));
    }
  }
}
