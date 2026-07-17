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

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.Types.boolOf;

import java.util.Collections;
import java.util.EnumSet;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Trait;

/** TypeValue is an instance of a Value that describes a value's type. */
public class TypeT implements Type, Val {
  /** TypeType is the type of a TypeValue. */
  public static final Type TypeType = newTypeValue(TypeEnum.Type);

  private final TypeEnum typeEnum;
  private final EnumSet<Trait> traitMask;

  TypeT(TypeEnum typeEnum, Trait... traits) {
    EnumSet<Trait> traitEnumSet = EnumSet.noneOf(Trait.class);
    Collections.addAll(traitEnumSet, traits);
    this.typeEnum = typeEnum;
    this.traitMask = traitEnumSet;
  }

  /** NewTypeValue returns *TypeValue which is both a ref.Type and ref.Val. */
  static Type newTypeValue(TypeEnum typeEnum, Trait... traits) {
    return new TypeT(typeEnum, traits);
  }

  /**
   * NewObjectTypeValue returns a *TypeValue based on the input name, which is annotated with the
   * traits relevant to all objects.
   */
  public static Type newObjectTypeValue(String name) {
    return new ObjectTypeT(name);
  }

  /** NewObjectTypeValue returns a *TypeValue with the supplied traits for a qualified type name. */
  public static Type newObjectTypeValue(String name, Trait... traits) {
    return new ObjectTypeT(name, traits);
  }

  static final class ObjectTypeT extends TypeT {
    private final String typeName;

    ObjectTypeT(String typeName) {
      this(typeName, Trait.FieldTesterType, Trait.IndexerType);
    }

    ObjectTypeT(String typeName, Trait... traits) {
      super(TypeEnum.Object, traits);
      this.typeName = typeName;
    }

    @Override
    public String typeName() {
      return typeName;
    }
  }

  @Override
  public boolean booleanValue() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long intValue() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double doubleValue() {
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
    return switch (typeVal.typeEnum()) {
      case Type -> TypeType;
      case String -> stringOf(typeName());
      default -> newTypeConversionError(TypeType, typeVal);
    };
  }

  /** Equal implements ref.Val.Equal. */
  @Override
  public Val equal(Val other) {
    if (TypeType != other.type()) {
      return False;
    }
    return boolOf(this.equals(other));
  }

  /**
   * HasTrait indicates whether the type supports the given trait. Trait codes are defined in the
   * traits package, e.g. see traits.AdderType.
   */
  @Override
  public boolean hasTrait(Trait trait) {
    return traitMask.contains(trait);
  }

  @Override
  public TypeEnum typeEnum() {
    return typeEnum;
  }

  /** String implements fmt.Stringer. */
  @Override
  public String toString() {
    return typeName();
  }

  /** Type implements ref.Val.Type. */
  @Override
  public Type type() {
    return TypeType;
  }

  /** TypeName gives the type's name as a string. */
  @Override
  public String typeName() {
    return typeEnum.getName();
  }

  /** Value implements ref.Val.Value. */
  @Override
  public Object value() {
    return typeName();
  }

  /**
   * Type-value equality follows CEL runtime semantics and compares qualified runtime type names.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Type typeValue)) {
      return false;
    }
    return typeName().equals(typeValue.typeName());
  }

  @Override
  public int hashCode() {
    return typeName().hashCode();
  }
}
