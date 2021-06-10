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
package org.projectnessie.cel.checker;

import static java.util.Collections.singletonList;
import static org.projectnessie.cel.checker.CheckerEnv.newStandardCheckerEnv;
import static org.projectnessie.cel.common.types.ProtoTypeRegistry.newRegistry;

import com.google.api.expr.v1alpha1.Type;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.Overloads;

public class CheckerEnvTest {

  @Test
  void overlappingIdentifier() {
    CheckerEnv env = newStandardCheckerEnv(Container.defaultContainer, newRegistry());
    Assertions.assertThatThrownBy(() -> env.add(Decls.newVar("int", Decls.newTypeType(null))))
        .hasMessage("overlapping identifier for name 'int'");
  }

  @Test
  void overlappingMacro() {
    CheckerEnv env = newStandardCheckerEnv(Container.defaultContainer, newRegistry());
    Assertions.assertThatThrownBy(
            () ->
                env.add(
                    Decls.newFunction(
                        "has", Decls.newOverload("has", singletonList(Decls.String), Decls.Bool))))
        .hasMessage("overlapping macro for name 'has' with 1 args");
  }

  @Test
  void overlappingOverload() {
    CheckerEnv env = newStandardCheckerEnv(Container.defaultContainer, newRegistry());
    Type paramA = Decls.newTypeParamType("A");
    List<String> typeParamAList = singletonList("A");
    Assertions.assertThatThrownBy(
            () ->
                env.add(
                    Decls.newFunction(
                        Overloads.TypeConvertDyn,
                        Decls.newParameterizedOverload(
                            Overloads.ToDyn, singletonList(paramA), Decls.Dyn, typeParamAList))))
        .hasMessage(
            "overlapping overload for name 'dyn' (type '(type_param: \"A\") -> dyn' with overloadId: 'to_dyn' cannot be distinguished from '(type_param: \"A\") -> dyn' with overloadId: 'to_dyn')");
  }
}
