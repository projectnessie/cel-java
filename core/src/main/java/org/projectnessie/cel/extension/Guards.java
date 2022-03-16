/*
 * Copyright (C) 2022 The Authors of CEL-Java
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
package org.projectnessie.cel.extension;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.IntT;
import org.projectnessie.cel.common.types.ListT;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.interpreter.functions.BinaryOp;
import org.projectnessie.cel.interpreter.functions.FunctionOp;
import org.projectnessie.cel.interpreter.functions.UnaryOp;

/** function invocation guards for common call signatures within extension functions. */
public final class Guards {

  private Guards() {}

  public static BinaryOp callInStrIntOutStr(BiFunction<String, Integer, String> func) {
    return (lhs, rhs) -> {
      if (lhs.type() != StringT.StringType) {
        return Err.maybeNoSuchOverloadErr(lhs);
      }
      if (rhs.type() != IntT.IntType) {
        return Err.maybeNoSuchOverloadErr(rhs);
      }
      try {
        return StringT.stringOf(func.apply(((String) lhs.value()), getIntValue((IntT) rhs)));
      } catch (RuntimeException e) {
        return Err.newErr(e, "%s", e.getMessage());
      }
    };
  }

  public static BinaryOp callInStrStrOutInt(BiFunction<String, String, Integer> func) {
    return (lhs, rhs) -> {
      if (lhs.type() != StringT.StringType) {
        return Err.maybeNoSuchOverloadErr(lhs);
      }
      if (rhs.type() != StringT.StringType) {
        return Err.maybeNoSuchOverloadErr(rhs);
      }
      try {
        return IntT.intOf(func.apply(((String) lhs.value()), ((String) rhs.value())));
      } catch (RuntimeException e) {
        return Err.newErr(e, "%s", e.getMessage());
      }
    };
  }

  public static BinaryOp callInStrStrOutStrArr(BiFunction<String, String, String[]> func) {
    return (lhs, rhs) -> {
      if (lhs.type() != StringT.StringType) {
        return Err.maybeNoSuchOverloadErr(lhs);
      }
      if (rhs.type() != StringT.StringType) {
        return Err.maybeNoSuchOverloadErr(rhs);
      }
      try {
        return ListT.newStringArrayList(func.apply(((String) lhs.value()), ((String) rhs.value())));
      } catch (RuntimeException e) {
        return Err.newErr(e, "%s", e.getMessage());
      }
    };
  }

  public static FunctionOp callInStrStrStrOutStr(TriFunction<String, String, String, String> func) {
    return values -> {
      if (values.length != 3) {
        return Err.maybeNoSuchOverloadErr(null);
      }
      if (values[0].type() != StringT.StringType) {
        return Err.maybeNoSuchOverloadErr(values[0]);
      }
      if (values[1].type() != StringT.StringType) {
        return Err.maybeNoSuchOverloadErr(values[1]);
      }
      if (values[2].type() != StringT.StringType) {
        return Err.maybeNoSuchOverloadErr(values[2]);
      }
      try {
        return StringT.stringOf(
            func.apply(
                ((String) values[0].value()),
                ((String) values[1].value()),
                ((String) values[2].value())));
      } catch (RuntimeException e) {
        return Err.newErr(e, "%s", e.getMessage());
      }
    };
  }

  public static UnaryOp callInStrOutStr(UnaryOperator<String> func) {
    return val -> {
      if (val.type() != StringT.StringType) {
        return Err.maybeNoSuchOverloadErr(val);
      }
      try {
        return StringT.stringOf(func.apply(((String) val.value())));
      } catch (RuntimeException e) {
        return Err.newErr(e, "%s", e.getMessage());
      }
    };
  }

  private static int getIntValue(IntT value) {
    Long longValue = (Long) value.value();
    if (longValue > Integer.MAX_VALUE || (longValue < Integer.MIN_VALUE)) {
      throw new RuntimeException(String.format("Integer %d value overflow", longValue));
    }
    return longValue.intValue();
  }
}
