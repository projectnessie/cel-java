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

/** Type interface indicate the name of a given type. */
public interface Type {

  /**
   * HasTrait returns whether the type has a given trait associated with it.
   *
   * <p>See common/types/traits/traits.go for a list of supported traits.
   */
  boolean hasTrait(Trait trait);

  /**
   * TypeName returns the qualified type name of the type.
   *
   * <p>The type name is also used as the type's identifier name at type-check and interpretation
   * time.
   */
  String typeName();
}
