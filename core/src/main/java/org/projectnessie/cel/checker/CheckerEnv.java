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

import static org.projectnessie.cel.checker.Mapping.newMapping;
import static org.projectnessie.cel.checker.Scopes.newScopes;
import static org.projectnessie.cel.checker.Types.Kind.kindObject;
import static org.projectnessie.cel.checker.Types.formatCheckedType;
import static org.projectnessie.cel.checker.Types.isAssignable;
import static org.projectnessie.cel.checker.Types.kindOf;
import static org.projectnessie.cel.checker.Types.substitute;
import static org.projectnessie.cel.common.types.Err.ErrType;
import static org.projectnessie.cel.common.types.pb.Checked.CheckedWellKnowns;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Decl;
import com.google.api.expr.v1alpha1.Decl.FunctionDecl;
import com.google.api.expr.v1alpha1.Decl.FunctionDecl.Overload;
import com.google.api.expr.v1alpha1.Decl.IdentDecl;
import com.google.api.expr.v1alpha1.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.ref.TypeProvider;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.parser.Macro;

/**
 * Env is the environment for type checking.
 *
 * <p>The Env is comprised of a container, type provider, declarations, and other related objects
 * which can be used to assist with type-checking.
 */
public final class CheckerEnv {

  final Container container;
  final TypeProvider provider;
  private final Scopes declarations;
  int aggLitElemType;

  static final int dynElementType = 0;
  static final int homogenousElementType = 1;

  private CheckerEnv(
      Container container, TypeProvider provider, Scopes declarations, int aggLitElemType) {
    this.container = container;
    this.provider = provider;
    this.declarations = declarations;
    this.aggLitElemType = aggLitElemType;
  }

  /** NewEnv returns a new *Env with the given parameters. */
  public static CheckerEnv newCheckerEnv(Container container, TypeProvider provider) {
    Scopes declarations = newScopes();
    // declarations.push(); // TODO why this ??

    return new CheckerEnv(container, provider, declarations, dynElementType);
  }

  /** NewStandardEnv returns a new *Env with the given params plus standard declarations. */
  public static CheckerEnv newStandardCheckerEnv(Container container, TypeProvider provider) {
    CheckerEnv e = newCheckerEnv(container, provider);
    e.add(Checker.StandardDeclarations);
    // TODO: isolate standard declarations from the custom set which may be provided layer.
    return e;
  }

  /**
   * EnableDynamicAggregateLiterals detmerines whether list and map literals may support mixed
   * element types at check-time. This does not preclude the presence of a dynamic list or map
   * somewhere in the CEL evaluation process.
   */
  public CheckerEnv enableDynamicAggregateLiterals(boolean enabled) {
    aggLitElemType = enabled ? dynElementType : homogenousElementType;
    return this;
  }

  /** Add adds new Decl protos to the Env. Returns an error for identifier redeclarations. */
  public void add(Decl... decls) {
    add(Arrays.asList(decls));
  }

  /** Add adds new Decl protos to the Env. Returns an error for identifier redeclarations. */
  public void add(List<Decl> decls) {
    List<String> errMsgs = new ArrayList<>();
    for (Decl decl : decls) {
      switch (decl.getDeclKindCase()) {
        case IDENT:
          addIdent(sanitizeIdent(decl), errMsgs);
          break;
        case FUNCTION:
          addFunction(sanitizeFunction(decl), errMsgs);
          break;
      }
    }
    if (!errMsgs.isEmpty()) {
      throw new IllegalArgumentException(String.join("\n", errMsgs));
    }
  }

  /**
   * LookupIdent returns a Decl proto for typeName as an identifier in the Env. Returns nil if no
   * such identifier is found in the Env.
   */
  public Decl lookupIdent(String name) {
    for (String candidate : container.resolveCandidateNames(name)) {
      Decl ident = declarations.findIdent(candidate);
      if (ident != null) {
        return ident;
      }

      // Next try to import the name as a reference to a message type. If found,
      // the declaration is added to the outest (global) scope of the
      // environment, so next time we can access it faster.
      Type t = provider.findType(candidate);
      if (t != null) {
        Decl decl = Decls.newVar(candidate, t);
        declarations.addIdent(decl);
        return decl;
      }

      // Next try to import this as an enum value by splitting the name in a type prefix and
      // the enum inside.
      Val enumValue = provider.enumValue(candidate);
      if (enumValue.type() != ErrType) {
        Decl decl =
            Decls.newIdent(
                candidate,
                Decls.Int,
                Constant.newBuilder().setInt64Value(enumValue.intValue()).build());
        declarations.addIdent(decl);
        return decl;
      }
    }
    return null;
  }

  /**
   * LookupFunction returns a Decl proto for typeName as a function in env. Returns nil if no such
   * function is found in env.
   */
  public Decl lookupFunction(String name) {
    for (String candidate : container.resolveCandidateNames(name)) {
      Decl fn = declarations.findFunction(candidate);
      if (fn != null) {
        return fn;
      }
    }
    return null;
  }

  /**
   * addOverload adds overload to function declaration f. Returns one or more errorMsg values if the
   * overload overlaps with an existing overload or macro.
   */
  Decl addOverload(Decl f, Overload overload, List<String> errMsgs) {
    FunctionDecl function = f.getFunction();
    Mapping emptyMappings = newMapping();
    Type overloadFunction =
        Decls.newFunctionType(overload.getResultType(), overload.getParamsList());
    Type overloadErased = substitute(emptyMappings, overloadFunction, true);
    boolean hasErr = false;
    for (Overload existing : function.getOverloadsList()) {
      Type existingFunction =
          Decls.newFunctionType(existing.getResultType(), existing.getParamsList());
      Type existingErased = substitute(emptyMappings, existingFunction, true);
      boolean overlap =
          isAssignable(emptyMappings, overloadErased, existingErased) != null
              || isAssignable(emptyMappings, existingErased, overloadErased) != null;
      if (overlap && overload.getIsInstanceFunction() == existing.getIsInstanceFunction()) {
        errMsgs.add(
            overlappingOverloadError(
                f.getName(),
                overload.getOverloadId(),
                overloadFunction,
                existing.getOverloadId(),
                existingFunction));
        hasErr = true;
      }
    }

    for (Macro macro : Macro.AllMacros) {
      if (macro.function().equals(f.getName())
          && macro.isReceiverStyle() == overload.getIsInstanceFunction()
          && macro.argCount() == overload.getParamsCount()) {
        errMsgs.add(overlappingMacroError(f.getName(), macro.argCount()));
        hasErr = true;
      }
    }
    if (hasErr) {
      return f;
    }
    function = function.toBuilder().addOverloads(overload).build();
    f = f.toBuilder().setFunction(function).build();
    return f;
  }

  /**
   * addFunction adds the function Decl to the Env. Adds a function decl if one doesn't already
   * exist, then adds all overloads from the Decl. If overload overlaps with an existing overload,
   * adds to the errors in the Env instead.
   */
  void addFunction(Decl decl, List<String> errMsgs) {
    Decl current = declarations.findFunction(decl.getName());
    if (current == null) {
      // Add the function declaration without overloads and check the overloads below.
      current = Decls.newFunction(decl.getName(), Collections.emptyList());
      declarations.addFunction(current);
    }

    for (Overload overload : decl.getFunction().getOverloadsList()) {
      current = addOverload(current, overload, errMsgs);
    }
    declarations.updateFunction(decl.getName(), current);
  }

  /**
   * addIdent adds the Decl to the declarations in the Env. Returns a non-empty errorMsg if the
   * identifier is already declared in the scope.
   */
  void addIdent(Decl decl, List<String> errMsgs) {
    Decl current = declarations.findIdentInScope(decl.getName());
    if (current != null) {
      errMsgs.add(overlappingIdentifierError(decl.getName()));
      return;
    }
    declarations.addIdent(decl);
  }

  // sanitizeFunction replaces well-known types referenced by message name with their equivalent
  // CEL built-in type instances.
  Decl sanitizeFunction(Decl decl) {
    FunctionDecl fn = decl.getFunction();
    // Determine whether the declaration requires replacements from proto-based message type
    // references to well-known CEL type references.
    boolean needsSanitizing = false;
    for (Overload o : fn.getOverloadsList()) {
      if (isObjectWellKnownType(o.getResultType())) {
        needsSanitizing = true;
        break;
      }
      for (Type p : o.getParamsList()) {
        if (isObjectWellKnownType(p)) {
          needsSanitizing = true;
          break;
        }
      }
    }

    // Early return if the declaration requires no modification.
    if (!needsSanitizing) {
      return decl;
    }

    // Sanitize all of the overloads if any overload requires an update to its type references.
    List<Overload> overloads = new ArrayList<>(fn.getOverloadsCount());
    for (Overload o : fn.getOverloadsList()) {
      boolean sanitized = false;
      Type rt = o.getResultType();
      if (isObjectWellKnownType(rt)) {
        rt = getObjectWellKnownType(rt);
        sanitized = true;
      }
      List<Type> params = new ArrayList<>(o.getParamsCount());
      for (Type p : o.getParamsList()) {
        if (isObjectWellKnownType(p)) {
          params.add(getObjectWellKnownType(p));
          sanitized = true;
        } else {
          params.add(p);
        }
      }
      // If sanitized, replace the overload definition.
      Overload ov;
      if (sanitized) {
        if (o.getIsInstanceFunction()) {
          ov = Decls.newInstanceOverload(o.getOverloadId(), params, rt);
        } else {
          ov = Decls.newOverload(o.getOverloadId(), params, rt);
        }
      } else {
        // Otherwise, preserve the original overload.
        ov = o;
      }
      overloads.add(ov);
    }
    return Decls.newFunction(decl.getName(), overloads);
  }

  /**
   * sanitizeIdent replaces the identifier's well-known types referenced by message name with
   * references to CEL built-in type instances.
   */
  Decl sanitizeIdent(Decl decl) {
    IdentDecl id = decl.getIdent();
    Type t = id.getType();
    if (!isObjectWellKnownType(t)) {
      return decl;
    }
    return Decls.newIdent(decl.getName(), getObjectWellKnownType(t), id.getValue());
  }

  /**
   * isObjectWellKnownType returns true if the input type is an OBJECT type with a message name that
   * corresponds the message name of a built-in CEL type.
   */
  static boolean isObjectWellKnownType(Type t) {
    if (kindOf(t) != kindObject) {
      return false;
    }
    return CheckedWellKnowns.containsKey(t.getMessageType());
  }

  /**
   * getObjectWellKnownType returns the built-in CEL type declaration for input type's message name.
   */
  static Type getObjectWellKnownType(Type t) {
    return CheckedWellKnowns.get(t.getMessageType());
  }

  /** enterScope creates a new Env instance with a new innermost declaration scope. */
  CheckerEnv enterScope() {
    Scopes childDecls = declarations.push();
    return new CheckerEnv(this.container, this.provider, childDecls, this.aggLitElemType);
  }

  // exitScope creates a new Env instance with the nearest outer declaration scope.
  CheckerEnv exitScope() {
    Scopes parentDecls = declarations.pop();
    return new CheckerEnv(this.container, this.provider, parentDecls, this.aggLitElemType);
  }

  // errorMsg is a type alias meant to represent error-based return values which
  // may be accumulated into an error at a later point in execution.
  //  type errorMsg string

  String overlappingIdentifierError(String name) {
    return String.format("overlapping identifier for name '%s'", name);
  }

  String overlappingOverloadError(
      String name, String overloadID1, Type f1, String overloadID2, Type f2) {
    return String.format(
        "overlapping overload for name '%s' (type '%s' with overloadId: '%s' "
            + "cannot be distinguished from '%s' with overloadId: '%s')",
        name, formatCheckedType(f1), overloadID1, formatCheckedType(f2), overloadID2);
  }

  String overlappingMacroError(String name, int argCount) {
    return String.format("overlapping macro for name '%s' with %d args", name, argCount);
  }
}
