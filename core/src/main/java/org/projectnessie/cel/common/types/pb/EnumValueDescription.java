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
package org.projectnessie.cel.common.types.pb;

import com.google.protobuf.Descriptors.EnumValueDescriptor;
import java.util.Objects;

/**
 * Immutable association between a fully qualified protobuf enum-constant name and its numeric
 * value.
 *
 * <p>This is descriptor metadata used by {@link ProtoTypeRegistry}. CEL represents protobuf enum
 * constants as integers.
 */
public final class EnumValueDescription {

  private final String enumValueName;
  private final EnumValueDescriptor desc;

  private EnumValueDescription(String enumValueName, EnumValueDescriptor desc) {
    this.enumValueName = enumValueName;
    this.desc = desc;
  }

  /**
   * Creates an enum-value description.
   *
   * @param name fully qualified protobuf enum-constant name
   * @param desc protobuf descriptor that supplies the numeric value
   * @return an immutable description
   */
  public static EnumValueDescription newEnumValueDescription(
      String name, EnumValueDescriptor desc) {
    return new EnumValueDescription(name, desc);
  }

  /** Returns the fully qualified identifier name for the enum constant. */
  public String name() {
    return enumValueName;
  }

  /** Returns the numeric protobuf value exposed to CEL as an integer. */
  public int value() {
    return desc.getNumber();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EnumValueDescription that = (EnumValueDescription) o;
    return Objects.equals(enumValueName, that.enumValueName) && Objects.equals(desc, that.desc);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enumValueName, desc);
  }
}
