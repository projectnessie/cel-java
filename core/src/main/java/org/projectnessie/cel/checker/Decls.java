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

import static java.lang.String.format;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Decl;
import com.google.api.expr.v1alpha1.Decl.FunctionDecl;
import com.google.api.expr.v1alpha1.Decl.FunctionDecl.Overload;
import com.google.api.expr.v1alpha1.Decl.IdentDecl;
import com.google.api.expr.v1alpha1.Decl.IdentDecl.Builder;
import com.google.api.expr.v1alpha1.Type;
import com.google.api.expr.v1alpha1.Type.AbstractType;
import com.google.api.expr.v1alpha1.Type.FunctionType;
import com.google.api.expr.v1alpha1.Type.ListType;
import com.google.api.expr.v1alpha1.Type.MapType;
import com.google.api.expr.v1alpha1.Type.PrimitiveType;
import com.google.api.expr.v1alpha1.Type.WellKnownType;
import com.google.protobuf.Empty;
import com.google.protobuf.NullValue;
import java.util.Arrays;
import java.util.List;

public class Decls {

  /** Error type used to communicate issues during type-checking. */
  public static final Type Error = Type.newBuilder().setError(Empty.getDefaultInstance()).build();

  /** Dyn is a top-type used to represent any value. */
  public static final Type Dyn = Type.newBuilder().setDyn(Empty.getDefaultInstance()).build();

  // Commonly used types.
  public static final Type Bool = newPrimitiveType(PrimitiveType.BOOL);
  public static final Type Bytes = newPrimitiveType(PrimitiveType.BYTES);
  public static final Type Double = newPrimitiveType(PrimitiveType.DOUBLE);
  public static final Type Int = newPrimitiveType(PrimitiveType.INT64);
  public static final Type Null = Type.newBuilder().setNull(NullValue.NULL_VALUE).build();
  public static final Type String = newPrimitiveType(PrimitiveType.STRING);
  public static final Type Uint = newPrimitiveType(PrimitiveType.UINT64);

  // Well-known types.
  // TODO: Replace with an abstract type registry.
  public static final Type Any = newWellKnownType(WellKnownType.ANY);
  public static final Type Duration = newWellKnownType(WellKnownType.DURATION);
  public static final Type Timestamp = newWellKnownType(WellKnownType.TIMESTAMP);

  /**
   * NewAbstractType creates an abstract type declaration which references a proto message name and
   * may also include type parameters.
   */
  public static Type newAbstractType(String name, List<Type> paramTypes) {
    return Type.newBuilder()
        .setAbstractType(AbstractType.newBuilder().setName(name).addAllParameterTypes(paramTypes))
        .build();
  }

  /**
   * NewFunctionType creates a function invocation contract, typically only used by type-checking
   * steps after overload resolution.
   */
  public static Type newFunctionType(Type resultType, List<Type> argTypes) {
    return Type.newBuilder()
        .setFunction(FunctionType.newBuilder().setResultType(resultType).addAllArgTypes(argTypes))
        .build();
  }

  /** NewFunction creates a named function declaration with one or more overloads. */
  public static Decl newFunction(String name, Overload... overloads) {
    return newFunction(name, Arrays.asList(overloads));
  }

  /** NewFunction creates a named function declaration with one or more overloads. */
  public static Decl newFunction(String name, List<Overload> overloads) {
    return Decl.newBuilder()
        .setName(name)
        .setFunction(FunctionDecl.newBuilder().addAllOverloads(overloads).build())
        .build();
  }

  /**
   * NewIdent creates a named identifier declaration with an optional literal value.
   *
   * <p>Literal values are typically only associated with enum identifiers.
   *
   * <p>Deprecated: Use NewVar or NewConst instead.
   */
  public static Decl newIdent(String name, Type t, Constant v) {
    Builder ident = IdentDecl.newBuilder().setType(t);
    if (v != null) {
      ident = ident.setValue(v);
    }
    return Decl.newBuilder().setName(name).setIdent(ident).build();
  }

  /** NewConst creates a constant identifier with a CEL constant literal value. */
  public static Decl newConst(String name, Type t, Constant v) {
    return newIdent(name, t, v);
  }

  /** NewVar creates a variable identifier. */
  public static Decl newVar(String name, Type t) {
    return newIdent(name, t, null);
  }

  /**
   * NewInstanceOverload creates a instance function overload contract. First element of argTypes is
   * instance.
   */
  public static Overload newInstanceOverload(String id, List<Type> argTypes, Type resultType) {
    return Overload.newBuilder()
        .setOverloadId(id)
        .setResultType(resultType)
        .addAllParams(argTypes)
        .setIsInstanceFunction(true)
        .build();
  }

  /** NewListType generates a new list with elements of a certain type. */
  public static Type newListType(Type elem) {
    return Type.newBuilder().setListType(ListType.newBuilder().setElemType(elem)).build();
  }

  /** NewMapType generates a new map with typed keys and values. */
  public static Type newMapType(Type key, Type value) {
    return Type.newBuilder()
        .setMapType(MapType.newBuilder().setKeyType(key).setValueType(value))
        .build();
  }

  /** NewObjectType creates an object type for a qualified type name. */
  public static Type newObjectType(String typeName) {
    return Type.newBuilder().setMessageType(typeName).build();
  }

  /**
   * NewOverload creates a function overload declaration which contains a unique overload id as well
   * as the expected argument and result types. Overloads must be aggregated within a Function
   * declaration.
   */
  public static Overload newOverload(String id, List<Type> argTypes, Type resultType) {
    return Overload.newBuilder()
        .setOverloadId(id)
        .setResultType(resultType)
        .addAllParams(argTypes)
        .setIsInstanceFunction(false)
        .build();
  }

  /** NewParameterizedInstanceOverload creates a parametric function instance overload type. */
  public static Overload newParameterizedInstanceOverload(
      String id, List<Type> argTypes, Type resultType, List<String> typeParams) {
    return Overload.newBuilder()
        .setOverloadId(id)
        .setResultType(resultType)
        .addAllParams(argTypes)
        .addAllTypeParams(typeParams)
        .setIsInstanceFunction(true)
        .build();
  }

  /** NewParameterizedOverload creates a parametric function overload type. */
  public static Overload newParameterizedOverload(
      String id, List<Type> argTypes, Type resultType, List<String> typeParams) {
    return Overload.newBuilder()
        .setOverloadId(id)
        .setResultType(resultType)
        .addAllParams(argTypes)
        .addAllTypeParams(typeParams)
        .setIsInstanceFunction(false)
        .build();
  }

  /**
   * NewPrimitiveType creates a type for a primitive value. See the var declarations for Int, Uint,
   * etc.
   */
  public static Type newPrimitiveType(PrimitiveType primitive) {
    return Type.newBuilder().setPrimitive(primitive).build();
  }

  /** NewTypeType creates a new type designating a type. */
  public static Type newTypeType(Type nested) {
    if (nested == null) {
      // must set the nested field for a valid oneof option
      nested = Type.newBuilder().build();
    }
    return Type.newBuilder().setType(nested).build();
  }

  /** NewTypeParamType creates a type corresponding to a named, contextual parameter. */
  public static Type newTypeParamType(String name) {
    return Type.newBuilder().setTypeParam(name).build();
  }

  /** NewWellKnownType creates a type corresponding to a protobuf well-known type value. */
  public static Type newWellKnownType(WellKnownType wellKnown) {
    return Type.newBuilder().setWellKnown(wellKnown).build();
  }

  /**
   * NewWrapperType creates a wrapped primitive type instance. Wrapped types are roughly equivalent
   * to a nullable, or optionally valued type.
   */
  public static Type newWrapperType(Type wrapped) {
    PrimitiveType primitive = wrapped.getPrimitive();
    if (primitive == PrimitiveType.PRIMITIVE_TYPE_UNSPECIFIED) {
      // TODO: return an error
      throw new IllegalArgumentException(
          format("Wrapped type must be a primitive, but is '%s'", wrapped));
    }
    return Type.newBuilder().setWrapper(primitive).build();
  }
}
