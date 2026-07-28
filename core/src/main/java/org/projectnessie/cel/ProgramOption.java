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
package org.projectnessie.cel;

import static org.projectnessie.cel.interpreter.Activation.newActivation;

import java.util.Collections;
import java.util.Objects;
import org.projectnessie.cel.interpreter.InterpretableDecorator;
import org.projectnessie.cel.interpreter.functions.Overload;

/**
 * Opaque configuration token used while creating a {@link Program}.
 *
 * <p>Obtain options from the static factories on this interface. Although this is declared as a
 * functional interface, its abstract method uses a package-private implementation type; external
 * implementation is not a supported extension mechanism.
 *
 * <p>Options are applied in order. Environment-level options contributed by a {@link Library} are
 * applied before options passed directly to {@link Env#program(Ast, ProgramOption...)}.
 */
@FunctionalInterface
public interface ProgramOption {
  /**
   * Applies this token to internal program construction.
   *
   * <p>This method is for CEL-Java's factory-produced options and is not an external SPI.
   *
   * @param prog internal program construction state
   * @return construction state for the next option
   */
  Prog apply(Prog prog);

  /**
   * Appends a low-level interpreter-plan decorator.
   *
   * <p>Decorators can inspect, alter, or replace the established interpreter plan. Custom
   * decorators are applied in insertion order; built-in optimization and state decorators may have
   * fixed later placement. Supplying a custom decorator disables native planning for the program.
   *
   * @param dec decorator to append
   * @return program configuration token
   */
  static ProgramOption customDecorator(InterpretableDecorator dec) {
    return p -> {
      p.decorators.add(dec);
      return p;
    };
  }

  /**
   * Adds runtime function overload implementations.
   *
   * <p>Each overload ID must correspond to a declaration available while checking the expression.
   * Duplicate overload IDs are rejected rather than silently replaced.
   *
   * @param funcs overload implementations to add
   * @return program configuration token
   */
  static ProgramOption functions(Overload... funcs) {
    return p -> {
      p.dispatcher.add(funcs);
      return p;
    };
  }

  /**
   * Sets default variable bindings for a program.
   *
   * <p>Bindings supplied to {@link Program#eval(Object)} shadow defaults with the same name. {@code
   * vars} accepts the same activation inputs as {@link
   * org.projectnessie.cel.interpreter.Activation#newActivation(Object)}. Retained maps and values
   * must remain stable while the program may evaluate them.
   *
   * @param vars default activation, map, or resolver
   * @return program configuration token
   */
  static ProgramOption globals(Object vars) {
    return p -> {
      p.defaultVars = newActivation(vars);
      return p;
    };
  }

  /**
   * Enables evaluation modes for a program.
   *
   * @param opts evaluation options
   * @return program configuration token
   */
  static ProgramOption evalOptions(EvalOption... opts) {
    return p -> {
      Collections.addAll(p.evalOpts, opts);
      return p;
    };
  }

  /**
   * Selects the regular-expression engine used by the standard CEL {@code matches} function.
   *
   * <p>The default is {@link RegexEngine#JAVA} for compatibility with earlier CEL-Java releases.
   * Use {@link RegexEngine#RE2} for the RE2 dialect and non-backtracking execution.
   *
   * @param engine the engine to use for this program
   * @return a program option that selects {@code engine}
   * @throws NullPointerException if {@code engine} is {@code null}
   */
  static ProgramOption regexEngine(RegexEngine engine) {
    Objects.requireNonNull(engine, "engine");
    return p -> {
      p.regexEngine = engine;
      return p;
    };
  }
}
