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
package org.projectnessie.cel.types.jackson3.types;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.immutables.value.Value;
import org.immutables.value.Value.Derived;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

@Value.Immutable(prehash = true)
@JsonSerialize(as = ImmutableRefVariantC.class)
@JsonDeserialize(as = ImmutableRefVariantC.class)
@JsonTypeName("C")
public abstract class RefVariantC implements RefBase {

  @Override
  @Derived
  public String getHash() {
    return getName();
  }

  public static RefVariantC of(String hash) {
    return ImmutableRefVariantC.builder().name(hash).build();
  }
}
