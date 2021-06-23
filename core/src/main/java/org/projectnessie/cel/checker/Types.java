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

import com.google.api.expr.v1alpha1.Type;
import com.google.api.expr.v1alpha1.Type.AbstractType;
import com.google.api.expr.v1alpha1.Type.FunctionType;
import com.google.api.expr.v1alpha1.Type.MapType;
import com.google.api.expr.v1alpha1.Type.PrimitiveType;
import com.google.api.expr.v1alpha1.Type.TypeKindCase;
import com.google.api.expr.v1alpha1.Type.WellKnownType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class Types {

  enum Kind {
    kindUnknown,
    kindError,
    kindFunction,
    kindDyn,
    kindPrimitive,
    kindWellKnown,
    kindWrapper,
    kindNull,
    kindAbstract, // TODO: Update the checker protos to include abstract
    kindType,
    kindList,
    kindMap,
    kindObject,
    kindTypeParam
  }

  /** FormatCheckedType converts a type message into a string representation. */
  public static String formatCheckedType(Type t) {
    // This is a very hot method.

    if (t == null) {
      return "(type not known)";
    }

    // short-cut the "easy" types
    switch (kindOf(t)) {
      case kindDyn:
        return "dyn";
      case kindNull:
        return "null";
      case kindPrimitive:
        switch (t.getPrimitive()) {
          case UINT64:
            return "uint";
          case INT64:
            return "int";
          case BOOL:
            return "bool";
          case BYTES:
            return "bytes";
          case DOUBLE:
            return "double";
          case STRING:
            return "string";
        }
        // unrecognizes & not-specified - ignore above
        return t.getPrimitive().toString().toLowerCase(Locale.ROOT).trim();
      case kindWellKnown:
        switch (t.getWellKnown()) {
          case ANY:
            return "any";
          case DURATION:
            return "duration";
          case TIMESTAMP:
            return "timestamp";
        }
      case kindError:
        return "!error!";
    }

    // complex types, use a StringBuilder, which is more efficient
    StringBuilder sb = new StringBuilder();
    formatCheckedType(sb, t);
    return sb.toString();
  }

  static void formatCheckedType(StringBuilder sb, Type t) {
    switch (kindOf(t)) {
      case kindDyn:
        sb.append("dyn");
        return;
      case kindFunction:
        TypeErrors.formatFunction(
            sb, t.getFunction().getResultType(), t.getFunction().getArgTypesList(), false);
        return;
      case kindList:
        sb.append("list(");
        formatCheckedType(sb, t.getListType().getElemType());
        sb.append(')');
        return;
      case kindObject:
        sb.append(t.getMessageType());
        return;
      case kindMap:
        sb.append("map(");
        formatCheckedType(sb, t.getMapType().getKeyType());
        sb.append(", ");
        formatCheckedType(sb, t.getMapType().getValueType());
        sb.append(')');
        return;
      case kindNull:
        sb.append("null");
        return;
      case kindPrimitive:
        formatCheckedTypePrimitive(sb, t.getPrimitive());
        return;
      case kindType:
        if (t.getType() == Type.getDefaultInstance()) {
          sb.append("type");
          return;
        }
        sb.append("type(");
        formatCheckedType(sb, t.getType());
        sb.append(')');
        return;
      case kindWellKnown:
        switch (t.getWellKnown()) {
          case ANY:
            sb.append("any");
            return;
          case DURATION:
            sb.append("duration");
            return;
          case TIMESTAMP:
            sb.append("timestamp");
            return;
        }
      case kindWrapper:
        sb.append("wrapper(");
        formatCheckedTypePrimitive(sb, t.getWrapper());
        sb.append(')');
        return;
      case kindError:
        sb.append("!error!");
        return;
    }

    String tStr = t.toString();
    for (int i = 0; i < tStr.length(); i++) {
      char c = tStr.charAt(i);
      if (c != '\n') {
        sb.append(c);
      }
    }
  }

  private static void formatCheckedTypePrimitive(StringBuilder sb, Type.PrimitiveType t) {
    switch (t) {
      case UINT64:
        sb.append("uint");
        return;
      case INT64:
        sb.append("int");
        return;
      case BOOL:
        sb.append("bool");
        return;
      case BYTES:
        sb.append("bytes");
        return;
      case DOUBLE:
        sb.append("double");
        return;
      case STRING:
        sb.append("string");
        return;
    }
    // unrecognizes & not-specified - ignore above
    sb.append(t.toString().toLowerCase(Locale.ROOT).trim());
  }

  /** isDyn returns true if the input t is either type DYN or a well-known ANY message. */
  static boolean isDyn(Type t) {
    // Note: object type values that are well-known and map to a DYN value in practice
    // are sanitized prior to being added to the environment.
    switch (kindOf(t)) {
      case kindDyn:
        return true;
      case kindWellKnown:
        return t.getWellKnown() == WellKnownType.ANY;
      default:
        return false;
    }
  }

  /** isDynOrError returns true if the input is either an Error, DYN, or well-known ANY message. */
  static boolean isDynOrError(Type t) {
    if (kindOf(t) == Kind.kindError) {
      return true;
    }
    return isDyn(t);
  }

  /**
   * isEqualOrLessSpecific checks whether one type is equal or less specific than the other one. A
   * type is less specific if it matches the other type using the DYN type.
   */
  static boolean isEqualOrLessSpecific(Type t1, Type t2) {
    Kind kind1 = kindOf(t1);
    Kind kind2 = kindOf(t2);
    // The first type is less specific.
    if (isDyn(t1) || kind1 == Kind.kindTypeParam) {
      return true;
    }
    // The first type is not less specific.
    if (isDyn(t2) || kind2 == Kind.kindTypeParam) {
      return false;
    }
    // Types must be of the same kind to be equal.
    if (kind1 != kind2) {
      return false;
    }

    // With limited exceptions for ANY and JSON values, the types must agree and be equivalent in
    // order to return true.
    switch (kind1) {
      case kindAbstract:
        {
          AbstractType a1 = t1.getAbstractType();
          AbstractType a2 = t2.getAbstractType();
          if (!a1.getName().equals(a2.getName())
              || a1.getParameterTypesCount() != a2.getParameterTypesCount()) {
            return false;
          }
          for (int i = 0; i < a1.getParameterTypesList().size(); i++) {
            Type p1 = a1.getParameterTypes(i);
            if (!isEqualOrLessSpecific(p1, a2.getParameterTypes(i))) {
              return false;
            }
          }
          return true;
        }
      case kindFunction:
        {
          FunctionType fn1 = t1.getFunction();
          FunctionType fn2 = t2.getFunction();
          if (fn1.getArgTypesCount() != fn2.getArgTypesCount()) {
            return false;
          }
          if (!isEqualOrLessSpecific(fn1.getResultType(), fn2.getResultType())) {
            return false;
          }
          for (int i = 0; i < fn1.getArgTypesList().size(); i++) {
            Type a1 = fn1.getArgTypes(i);
            if (!isEqualOrLessSpecific(a1, fn2.getArgTypes(i))) {
              return false;
            }
          }
          return true;
        }
      case kindList:
        return isEqualOrLessSpecific(
            t1.getListType().getElemType(), t2.getListType().getElemType());
      case kindMap:
        {
          MapType m1 = t1.getMapType();
          MapType m2 = t2.getMapType();
          return isEqualOrLessSpecific(m1.getKeyType(), m2.getKeyType())
              && isEqualOrLessSpecific(m1.getValueType(), m2.getValueType());
        }
      case kindType:
        return true;
      default:
        return t1.equals(t2);
    }
  }

  /** internalIsAssignable returns true if t1 is assignable to t2. */
  static boolean internalIsAssignable(Mapping m, Type t1, Type t2) {
    // A type is always assignable to itself.
    // Early terminate the call to avoid cases of infinite recursion.
    if (t1.equals(t2)) {
      return true;
    }
    // Process type parameters.

    Kind kind1 = kindOf(t1);
    Kind kind2 = kindOf(t2);
    if (kind2 == Kind.kindTypeParam) {
      Type t2Sub = m.find(t2);
      if (t2Sub != null) {
        // If the types are compatible, pick the more general type and return true
        if (internalIsAssignable(m, t1, t2Sub)) {
          m.add(t2, mostGeneral(t1, t2Sub));
          return true;
        }
        return false;
      }
      if (notReferencedIn(m, t2, t1)) {
        m.add(t2, t1);
        return true;
      }
    }
    if (kind1 == Kind.kindTypeParam) {
      // For the lower type bound, we currently do not perform adjustment. The restricted
      // way we use type parameters in lower type bounds, it is not necessary, but may
      // become if we generalize type unification.
      Type t1Sub = m.find(t1);
      if (t1Sub != null) {
        // If the types are compatible, pick the more general type and return true
        if (internalIsAssignable(m, t1Sub, t2)) {
          m.add(t1, mostGeneral(t1Sub, t2));
          return true;
        }
        return false;
      }
      if (notReferencedIn(m, t1, t2)) {
        m.add(t1, t2);
        return true;
      }
    }

    // Next check for wildcard types.
    if (isDynOrError(t1) || isDynOrError(t2)) {
      return true;
    }

    // Test for when the types do not need to agree, but are more specific than dyn.
    switch (kind1) {
      case kindNull:
        return internalIsAssignableNull(t2);
      case kindPrimitive:
        return internalIsAssignablePrimitive(t1.getPrimitive(), t2);
      case kindWrapper:
        return internalIsAssignable(m, Decls.newPrimitiveType(t1.getWrapper()), t2);
      default:
        if (kind1 != kind2) {
          return false;
        }
    }

    // Test for when the types must agree.
    switch (kind1) {
        // ERROR, TYPE_PARAM, and DYN handled above.
      case kindAbstract:
        return internalIsAssignableAbstractType(m, t1.getAbstractType(), t2.getAbstractType());
      case kindFunction:
        return internalIsAssignableFunction(m, t1.getFunction(), t2.getFunction());
      case kindList:
        return internalIsAssignable(
            m, t1.getListType().getElemType(), t2.getListType().getElemType());
      case kindMap:
        return internalIsAssignableMap(m, t1.getMapType(), t2.getMapType());
      case kindObject:
        return t1.getMessageType().equals(t2.getMessageType());
      case kindType:
        // A type is a type is a type, any additional parameterization of the
        // type cannot affect method resolution or assignability.
        return true;
      case kindWellKnown:
        return t1.getWellKnown() == t2.getWellKnown();
      default:
        return false;
    }
  }

  /**
   * internalIsAssignableAbstractType returns true if the abstract type names agree and all type
   * parameters are assignable.
   */
  static boolean internalIsAssignableAbstractType(Mapping m, AbstractType a1, AbstractType a2) {
    if (!a1.getName().equals(a2.getName())) {
      return false;
    }
    return internalIsAssignableList(m, a1.getParameterTypesList(), a2.getParameterTypesList());
  }

  /**
   * internalIsAssignableFunction returns true if the function return type and arg types are
   * assignable.
   */
  static boolean internalIsAssignableFunction(Mapping m, FunctionType f1, FunctionType f2) {
    List<Type> f1ArgTypes = flattenFunctionTypes(f1);
    List<Type> f2ArgTypes = flattenFunctionTypes(f2);
    return internalIsAssignableList(m, f1ArgTypes, f2ArgTypes);
  }

  /**
   * internalIsAssignableList returns true if the element types at each index in the list are
   * assignable from l1[i] to l2[i]. The list lengths must also agree for the lists to be
   * assignable.
   */
  static boolean internalIsAssignableList(Mapping m, List<Type> l1, List<Type> l2) {
    if (l1.size() != l2.size()) {
      return false;
    }
    for (int i = 0; i < l1.size(); i++) {
      Type t1 = l1.get(i);
      if (!internalIsAssignable(m, t1, l2.get(i))) {
        return false;
      }
    }
    return true;
  }

  /** internalIsAssignableMap returns true if map m1 may be assigned to map m2. */
  static boolean internalIsAssignableMap(Mapping m, MapType m1, MapType m2) {
    return internalIsAssignableList(
        m,
        Arrays.asList(m1.getKeyType(), m1.getValueType()),
        Arrays.asList(m2.getKeyType(), m2.getValueType()));
  }

  /** internalIsAssignableNull returns true if the type is nullable. */
  static boolean internalIsAssignableNull(Type t) {
    switch (kindOf(t)) {
      case kindAbstract:
      case kindObject:
      case kindNull:
      case kindWellKnown:
      case kindWrapper:
        return true;
      default:
        return false;
    }
  }

  /**
   * internalIsAssignablePrimitive returns true if the target type is the same or if it is a wrapper
   * for the primitive type.
   */
  static boolean internalIsAssignablePrimitive(PrimitiveType p, Type target) {
    switch (kindOf(target)) {
      case kindPrimitive:
        return p == target.getPrimitive();
      case kindWrapper:
        return p == target.getWrapper();
      default:
        return false;
    }
  }

  /** isAssignable returns an updated type substitution mapping if t1 is assignable to t2. */
  static Mapping isAssignable(Mapping m, Type t1, Type t2) {
    Mapping mCopy = m.copy();
    if (internalIsAssignable(mCopy, t1, t2)) {
      return mCopy;
    }
    return null;
  }

  /** isAssignableList returns an updated type substitution mapping if l1 is assignable to l2. */
  static Mapping isAssignableList(Mapping m, List<Type> l1, List<Type> l2) {
    Mapping mCopy = m.copy();
    if (internalIsAssignableList(mCopy, l1, l2)) {
      return mCopy;
    }
    return null;
  }

  /** kindOf returns the kind of the type as defined in the checked.proto. */
  static Kind kindOf(Type t) {
    if (t == null || t.getTypeKindCase() == TypeKindCase.TYPEKIND_NOT_SET) {
      return Kind.kindUnknown;
    }
    switch (t.getTypeKindCase()) {
      case ERROR:
        return Kind.kindError;
      case FUNCTION:
        return Kind.kindFunction;
      case DYN:
        return Kind.kindDyn;
      case PRIMITIVE:
        return Kind.kindPrimitive;
      case WELL_KNOWN:
        return Kind.kindWellKnown;
      case WRAPPER:
        return Kind.kindWrapper;
      case NULL:
        return Kind.kindNull;
      case TYPE:
        return Kind.kindType;
      case LIST_TYPE:
        return Kind.kindList;
      case MAP_TYPE:
        return Kind.kindMap;
      case MESSAGE_TYPE:
        return Kind.kindObject;
      case TYPE_PARAM:
        return Kind.kindTypeParam;
    }
    return Kind.kindUnknown;
  }

  /** mostGeneral returns the more general of two types which are known to unify. */
  static Type mostGeneral(Type t1, Type t2) {
    if (isEqualOrLessSpecific(t1, t2)) {
      return t1;
    }
    return t2;
  }

  /**
   * notReferencedIn checks whether the type doesn't appear directly or transitively within the
   * other type. This is a standard requirement for type unification, commonly referred to as the
   * "occurs check".
   */
  static boolean notReferencedIn(Mapping m, Type t, Type withinType) {
    if (t.equals(withinType)) {
      return false;
    }
    Kind withinKind = kindOf(withinType);
    switch (withinKind) {
      case kindTypeParam:
        Type wtSub = m.find(withinType);
        if (wtSub == null) {
          return true;
        }
        return notReferencedIn(m, t, wtSub);
      case kindAbstract:
        for (Type pt : withinType.getAbstractType().getParameterTypesList()) {
          if (!notReferencedIn(m, t, pt)) {
            return false;
          }
        }
        return true;
      case kindFunction:
        FunctionType fn = withinType.getFunction();
        List<Type> types = flattenFunctionTypes(fn);
        for (Type a : types) {
          if (!notReferencedIn(m, t, a)) {
            return false;
          }
        }
        return true;
      case kindList:
        return notReferencedIn(m, t, withinType.getListType().getElemType());
      case kindMap:
        MapType mt = withinType.getMapType();
        return notReferencedIn(m, t, mt.getKeyType()) && notReferencedIn(m, t, mt.getValueType());
      case kindWrapper:
        return notReferencedIn(m, t, Decls.newPrimitiveType(withinType.getWrapper()));
      default:
        return true;
    }
  }

  /**
   * substitute replaces all direct and indirect occurrences of bound type parameters. Unbound type
   * parameters are replaced by DYN if typeParamToDyn is true.
   */
  static Type substitute(Mapping m, Type t, boolean typeParamToDyn) {
    Type tSub = m.find(t);
    if (tSub != null) {
      return substitute(m, tSub, typeParamToDyn);
    }
    Kind kind = kindOf(t);
    if (typeParamToDyn && kind == Kind.kindTypeParam) {
      return Decls.Dyn;
    }
    switch (kind) {
      case kindAbstract:
        // TODO: implement!
        AbstractType at = t.getAbstractType();
        List<Type> params = new ArrayList<>(at.getParameterTypesCount());
        for (Type p : at.getParameterTypesList()) {
          params.add(substitute(m, p, typeParamToDyn));
        }
        return Decls.newAbstractType(at.getName(), params);
      case kindFunction:
        FunctionType fn = t.getFunction();
        Type rt = substitute(m, fn.getResultType(), typeParamToDyn);
        List<Type> args = new ArrayList<>(fn.getArgTypesCount());
        for (Type a : fn.getArgTypesList()) {
          args.add(substitute(m, a, typeParamToDyn));
        }
        return Decls.newFunctionType(rt, args);
      case kindList:
        return Decls.newListType(substitute(m, t.getListType().getElemType(), typeParamToDyn));
      case kindMap:
        MapType mt = t.getMapType();
        return Decls.newMapType(
            substitute(m, mt.getKeyType(), typeParamToDyn),
            substitute(m, mt.getValueType(), typeParamToDyn));
      case kindType:
        if (t.getType() != Type.getDefaultInstance()) {
          return Decls.newTypeType(substitute(m, t.getType(), typeParamToDyn));
        }
        return t;
      default:
        return t;
    }
  }

  static String typeKey(Type t) {
    return formatCheckedType(t);
  }

  /**
   * flattenFunctionTypes takes a function with arg types T1, T2, ..., TN and result type TR and
   * returns a slice containing {T1, T2, ..., TN, TR}.
   */
  static List<Type> flattenFunctionTypes(FunctionType f) {
    List<Type> argTypes = f.getArgTypesList();
    if (argTypes.isEmpty()) {
      return Collections.singletonList(f.getResultType());
    }
    List<Type> flattened = new ArrayList<>(argTypes.size() + 1);
    flattened.addAll(argTypes);
    flattened.add(f.getResultType());
    return flattened;
  }
}
