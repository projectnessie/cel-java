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

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.macros;
import static org.projectnessie.cel.ProgramOption.functions;
import static org.projectnessie.cel.checker.Checker.StandardDeclarations;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;
import static org.projectnessie.cel.parser.Macro.AllMacros;

import java.util.List;

/**
 * Library provides a collection of EnvOption and ProgramOption values used to confiugre a CEL
 * environment for a particular use case or with a related set of functionality.
 *
 * <p>Note, the ProgramOption values provided by a library are expected to be static and not vary
 * between calls to Env.Program(). If there is a need for such dynamic configuration, prefer to
 * configure these options outside the Library and within the Env.Program() call directly.
 */
public interface Library {
  /**
   * CompileOptions returns a collection of funcitional options for configuring the Parse / Check
   * environment.
   */
  List<EnvOption> getCompileOptions();

  /**
   * ProgramOptions returns a collection of functional options which should be included in every
   * Program generated from the Env.Program() call.
   */
  List<ProgramOption> getProgramOptions();

  /**
   * Lib creates an EnvOption out of a Library, allowing libraries to be provided as functional
   * args, and to be linked to each other.
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

  /** StdLib returns an EnvOption for the standard library of CEL functions and macros. */
  static EnvOption StdLib() {
    return Lib(new StdLibrary());
  }

  /**
   * stdLibrary implements the Library interface and provides functional options for the core CEL
   * features documented in the specification.
   */
  final class StdLibrary implements Library {

    /** EnvOptions returns options for the standard CEL function declarations and macros. */
    @Override
    public List<EnvOption> getCompileOptions() {
      return asList(declarations(StandardDeclarations), macros(AllMacros));
    }

    /** ProgramOptions returns function implementations for the standard CEL functions. */
    @Override
    public List<ProgramOption> getProgramOptions() {
      return singletonList(functions(standardOverloads()));
    }
  }
}
