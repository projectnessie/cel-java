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
package org.projectnessie.cel.common.types.ref;

import org.projectnessie.cel.common.types.traits.Trait;

/**
 * Runtime CEL type value.
 *
 * <p>A type identifies values by their CEL name and advertises the behavioral {@link Trait traits}
 * implemented by those values. Custom {@link Val} implementations must return a stable type whose
 * advertised traits agree with the Java trait interfaces implemented by the value.
 */
public interface Type extends Val {

  /**
   * Tests whether values of this type support {@code trait}.
   *
   * @param trait behavioral capability to test
   * @return whether values of this type implement the corresponding trait contract
   */
  boolean hasTrait(Trait trait);

  /**
   * Returns the stable, qualified CEL type name.
   *
   * <p>The type name is also used as the type's identifier name at type-check and interpretation
   * time.
   *
   * @return the CEL type name
   */
  String typeName();

  /**
   * Returns the built-in runtime category used for dispatch.
   *
   * @return the runtime type category
   */
  TypeEnum typeEnum();
}
