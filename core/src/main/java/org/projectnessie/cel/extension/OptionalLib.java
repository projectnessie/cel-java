/*
 * Copyright (C) 2026 The Authors of CEL-Java
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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import java.util.List;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.checker.Decls;

/**
 * OptionalLib provides compile-time declarations for CEL optional helper functions.
 *
 * <p>The current implementation intentionally exposes type-checking support only. It is sufficient
 * for check-only conformance cases that exercise optional type deduction, but it does not provide
 * runtime optional values or optional-selection semantics.
 */
public final class OptionalLib implements Library {
  private static final String OPTIONAL_TYPE = "optional_type";
  private static final String OPTIONAL_NONE = "optional.none";
  private static final String OPTIONAL_OF = "optional.of";
  private static final String OPTIONAL_OF_NON_ZERO_VALUE = "optional.ofNonZeroValue";
  private static final String TYPE_PARAM_A = "A";

  private OptionalLib() {}

  public static EnvOption optionals() {
    return Library.Lib(new OptionalLib());
  }

  @Override
  public List<EnvOption> getCompileOptions() {
    var typeParamA = Decls.newTypeParamType(TYPE_PARAM_A);
    var optionalA = Decls.newAbstractType(OPTIONAL_TYPE, singletonList(typeParamA));
    var typeParams = singletonList(TYPE_PARAM_A);

    return List.of(
        EnvOption.declarations(
            Decls.newFunction(
                OPTIONAL_NONE,
                Decls.newParameterizedOverload(
                    "optional_none", emptyList(), optionalA, typeParams)),
            Decls.newFunction(
                OPTIONAL_OF,
                Decls.newParameterizedOverload(
                    "optional_of", singletonList(typeParamA), optionalA, typeParams)),
            Decls.newFunction(
                OPTIONAL_OF_NON_ZERO_VALUE,
                Decls.newParameterizedOverload(
                    "optional_of_non_zero_value",
                    singletonList(typeParamA),
                    optionalA,
                    typeParams))));
  }

  @Override
  public List<ProgramOption> getProgramOptions() {
    return emptyList();
  }
}
