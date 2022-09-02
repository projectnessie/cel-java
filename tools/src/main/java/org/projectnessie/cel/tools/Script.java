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
package org.projectnessie.cel.tools;

import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.Program.EvalResult;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.ref.Val;

public final class Script {
  private final Env env;
  private final Program prg;

  Script(Env env, Program prg) {
    this.env = env;
    this.prg = prg;
  }

  public <T> T execute(Class<T> resultType, Function<String, Object> arguments)
      throws ScriptException {
    return evaluate(resultType, arguments);
  }

  public <T> T execute(Class<T> resultType, Map<String, Object> arguments) throws ScriptException {
    return evaluate(resultType, arguments);
  }

  @SuppressWarnings("unchecked")
  private <T> T evaluate(Class<T> resultType, Object arguments) throws ScriptExecutionException {
    Objects.requireNonNull(resultType, "resultType missing");
    Objects.requireNonNull(arguments, "arguments missing");

    EvalResult evalResult = prg.eval(arguments);

    Val result = evalResult.getVal();

    if (isError(result)) {
      Err err = (Err) result;
      throw new ScriptExecutionException(err.toString(), err.getCause());
    }
    if (isUnknown(result)) {
      if (resultType == Val.class || resultType == Object.class) {
        return (T) result;
      }
      throw new ScriptExecutionException(
          String.format(
              "script returned unknown %s, but expected result type is %s",
              result, resultType.getName()));
    }

    return result.convertToNative(resultType);
  }
}
