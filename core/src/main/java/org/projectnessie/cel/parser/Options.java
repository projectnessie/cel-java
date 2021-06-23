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

  public int getMaxRecursionDepth() {
    return maxRecursionDepth;
  }

  public int getErrorRecoveryLimit() {
    return errorRecoveryLimit;
  }

  public int getExpressionSizeCodePointLimit() {
    return expressionSizeCodePointLimit;
  }

  public Macro getMacro(String name) {
    return macros.get(name);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private final Map<String, Macro> macros = new HashMap<>();
    private int maxRecursionDepth = 250;
    private int errorRecoveryLimit = 30;
    private int expressionSizeCodePointLimit = 100_000;

    private Builder() {}

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

    public Builder macros(Macro... macros) {
      return macros(asList(macros));
    }

    public Builder macros(List<Macro> macros) {
      for (Macro macro : macros) {
        this.macros.put(macro.macroKey(), macro);
      }
      return this;
    }

    public Options build() {
      return new Options(
          maxRecursionDepth,
          errorRecoveryLimit,
          expressionSizeCodePointLimit,
          new HashMap<>(macros));
    }
  }
}
