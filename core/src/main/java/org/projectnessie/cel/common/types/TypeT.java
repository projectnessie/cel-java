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
package org.projectnessie.cel.common.types;

import static org.projectnessie.cel.common.types.BoolT.boolOf;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;

import java.util.EnumSet;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Trait;

/** TypeValue is an instance of a Value that describes a value's type. */
public class TypeT implements Type, Val {
  /** TypeType is the type of a TypeValue. */
  public static final TypeT TypeType = newTypeValue("type");

  private final String name;
  private final EnumSet<Trait> traitMask;

  private TypeT(String name, Trait... traits) {
    EnumSet<Trait> traitEnumSet = EnumSet.noneOf(Trait.class);
    for (Trait trait : traits) {
      traitEnumSet.add(trait);
    }
    this.name = name;
    this.traitMask = traitEnumSet;
  }

  /** NewTypeValue returns *TypeValue which is both a ref.Type and ref.Val. */
  public static TypeT newTypeValue(String name, Trait... traits) {
    return new TypeT(name, traits);
  }

  /**
   * NewObjectTypeValue returns a *TypeValue based on the input name, which is annotated with the
   * traits relevant to all objects.
   */
  public static TypeT newObjectTypeValue(String name) {
    return newTypeValue(name, Trait.FieldTesterType, Trait.IndexerType);
  }

  @Override
  public boolean booleanValue() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long intValue() {
    throw new UnsupportedOperationException();
  }

  /** ConvertToNative implements ref.Val.ConvertToNative. */
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    throw new UnsupportedOperationException("type conversion not supported for 'type'");
  }

  /** ConvertToType implements ref.Val.ConvertToType. */
  @Override
  public Val convertToType(Type typeVal) {
    if (typeVal.equals(TypeType)) {
      return TypeType;
    }
    if (typeVal.equals(StringType)) {
      return stringOf(typeName());
    }
    return newTypeConversionError(TypeType, typeVal);
  }

  /** Equal implements ref.Val.Equal. */
  public Val equal(Val other) {
    if (!TypeType.equals(other.type())) {
      return noSuchOverload(this, "equal", other);
    }
    return boolOf(typeName().equals(((Type) other).typeName()));
  }

  /**
   * HasTrait indicates whether the type supports the given trait. Trait codes are defined in the
   * traits package, e.g. see traits.AdderType.
   */
  @Override
  public boolean hasTrait(Trait trait) {
    return traitMask.contains(trait);
  }

  /** String implements fmt.Stringer. */
  @Override
  public String toString() {
    return name;
  }

  /** Type implements ref.Val.Type. */
  @Override
  public Type type() {
    return TypeType;
  }

  /** TypeName gives the type's name as a string. */
  @Override
  public String typeName() {
    return name;
  }

  /** Value implements ref.Val.Value. */
  @Override
  public Object value() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TypeT typeValue = (TypeT) o;
    return name.equals(typeValue.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }
}
