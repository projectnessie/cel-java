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

import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.macros;
import static org.projectnessie.cel.checker.Checker.StandardDeclarations;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;
import static org.projectnessie.cel.parser.Macro.AllMacros;

import java.util.List;
import org.projectnessie.cel.interpreter.functions.Overload;

/**
 * Contributes related compile-time and runtime functionality to a CEL environment.
 *
 * <p>Compile options typically add declarations and macros. Program options provide the matching
 * function overload implementations or evaluation configuration. Install a library with {@link
 * #Lib(Library)}; high-level compiler builders also accept libraries directly.
 *
 * <p>A library's program options are captured when the library is applied to an environment and
 * reused for every program created from that environment. They should therefore describe stable
 * configuration rather than vary between calls. Mutable library state and supplied functions must
 * be safe for the concurrent compilation and evaluation they receive.
 */
public interface Library {
  /**
   * Returns options that configure parsing and type checking.
   *
   * @return non-null compile options containing no null elements
   */
  List<EnvOption> getCompileOptions();

  /**
   * Returns options included in every program created from the configured environment.
   *
   * @return non-null program options containing no null elements
   */
  List<ProgramOption> getProgramOptions();

  /**
   * Wraps a library as an environment option.
   *
   * <p>Compile options are applied in returned-list order, followed by capture of the program
   * options. The library and its returned values are dereferenced when the returned option is
   * applied, not when this factory is called.
   *
   * @param l library to install
   * @return option that installs {@code l}
   * @throws NullPointerException when the returned option is applied if {@code l}, a returned
   *     option list, an option, or an applied compile-option result is {@code null}
   */
  static EnvOption Lib(Library l) {
    return e -> {
      for (EnvOption opt : l.getCompileOptions()) {
        e = opt.apply(e);
        if (e == null) {
          throw new NullPointerException(
              String.format("env option of type '%s' returned null", opt.getClass().getName()));
        }
      }
      e.addProgOpts(l.getProgramOptions());
      return e;
    };
  }

  /**
   * Returns an option that installs the standard CEL library.
   *
   * @return standard-library environment option
   */
  static EnvOption StdLib() {
    return Lib(new StdLibrary());
  }

  /** Standard declarations, macros, and function implementations from the CEL specification. */
  final class StdLibrary implements Library {

    /**
     * Returns standard CEL declarations and macros.
     *
     * @return compile options
     */
    @Override
    public List<EnvOption> getCompileOptions() {
      return List.of(declarations(StandardDeclarations), macros(AllMacros));
    }

    /**
     * Returns standard CEL function implementations.
     *
     * @return program options
     */
    @Override
    public List<ProgramOption> getProgramOptions() {
      Overload[] overloads = standardOverloads();
      return List.of(
          p -> {
            p.dispatcher.add(overloads);
            return p;
          });
    }
  }
}
