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

import static org.projectnessie.cel.checker.Env.dynElementType;
import static org.projectnessie.cel.checker.Env.getObjectWellKnownType;
import static org.projectnessie.cel.checker.Env.isObjectWellKnownType;
import static org.projectnessie.cel.checker.Mapping.newMapping;
import static org.projectnessie.cel.checker.Types.isDyn;
import static org.projectnessie.cel.checker.Types.isDynOrError;
import static org.projectnessie.cel.checker.Types.kindOf;
import static org.projectnessie.cel.checker.Types.mostGeneral;
import static org.projectnessie.cel.checker.Types.substitute;
import static org.projectnessie.cel.common.Location.NoLocation;
import static org.projectnessie.cel.common.Location.newLocation;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Decl;
import com.google.api.expr.v1alpha1.Decl.FunctionDecl.Overload;
import com.google.api.expr.v1alpha1.Decl.IdentDecl;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Call;
import com.google.api.expr.v1alpha1.Expr.Comprehension;
import com.google.api.expr.v1alpha1.Expr.CreateList;
import com.google.api.expr.v1alpha1.Expr.CreateStruct;
import com.google.api.expr.v1alpha1.Expr.CreateStruct.Entry;
import com.google.api.expr.v1alpha1.Expr.Ident;
import com.google.api.expr.v1alpha1.Expr.Select;
import com.google.api.expr.v1alpha1.Reference;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.api.expr.v1alpha1.Type;
import com.google.api.expr.v1alpha1.Type.MapType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.checker.Types.Kind;
import org.projectnessie.cel.common.Location;
import org.projectnessie.cel.common.Source;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.parser.Parser.ParseResult;

public class Checker {

  private Env env;
  private final TypeErrors errors;
  private Mapping mappings;
  private int freeTypeVarCounter;
  private final SourceInfo sourceInfo;
  private final Map<Long, Type> types = new HashMap<>();
  private final Map<Long, Reference> references = new HashMap<>();

  private Checker(
      Env env, TypeErrors errors, Mapping mappings, int freeTypeVarCounter, SourceInfo sourceInfo) {
    this.env = env;
    this.errors = errors;
    this.mappings = mappings;
    this.freeTypeVarCounter = freeTypeVarCounter;
    this.sourceInfo = sourceInfo;
  }

  public static final class CheckResult {
    private final CheckedExpr expr;
    private final TypeErrors errors;

    private CheckResult(CheckedExpr expr, TypeErrors errors) {
      this.expr = expr;
      this.errors = errors;
    }

    public CheckedExpr getCheckedExpr() {
      return expr;
    }

    public TypeErrors getErrors() {
      return errors;
    }

    public boolean hasErrors() {
      return errors.hasErrors();
    }

    @Override
    public String toString() {
      return "CheckResult{" + "expr=" + expr + ", errors=" + errors + '}';
    }
  }

  /**
   * Check performs type checking, giving a typed AST. The input is a ParsedExpr proto and an env
   * which encapsulates type binding of variables, declarations of built-in functions, descriptions
   * of protocol buffers, and a registry for errors. Returns a CheckedExpr proto, which might not be
   * usable if there are errors in the error registry.
   */
  public static CheckResult Check(ParseResult parsedExpr, Source source, Env env) {
    TypeErrors errors = new TypeErrors(source);
    Checker c = new Checker(env, errors, newMapping(), 0, parsedExpr.getSourceInfo());

    Expr.Builder b = parsedExpr.getExpr().toBuilder();
    c.check(b);
    Expr e = b.build();

    // Walk over the final type map substituting any type parameters either by their bound value or
    // by DYN.
    Map<Long, Type> m = new HashMap<>();
    c.types.forEach((k, v) -> m.put(k, substitute(c.mappings, v, true)));

    CheckedExpr checkedExpr =
        CheckedExpr.newBuilder()
            .setExpr(e)
            .setSourceInfo(parsedExpr.getSourceInfo())
            .putAllTypeMap(m)
            .putAllReferenceMap(c.references)
            .build();

    return new CheckResult(checkedExpr, errors);
  }

  void check(Expr.Builder e) {
    switch (e.getExprKindCase()) {
      case CONST_EXPR:
        Constant literal = e.getConstExpr();
        switch (literal.getConstantKindCase()) {
          case BOOL_VALUE:
            checkBoolLiteral(e);
            return;
          case BYTES_VALUE:
            checkBytesLiteral(e);
            return;
          case DOUBLE_VALUE:
            checkDoubleLiteral(e);
            return;
          case INT64_VALUE:
            checkInt64Literal(e);
            return;
          case NULL_VALUE:
            checkNullLiteral(e);
            return;
          case STRING_VALUE:
            checkStringLiteral(e);
            return;
          case UINT64_VALUE:
            checkUint64Literal(e);
            return;
        }
        throw new IllegalArgumentException(
            String.format("Unrecognized ast type: %s", e.getClass().getName()));
      case IDENT_EXPR:
        checkIdent(e);
        return;
      case SELECT_EXPR:
        checkSelect(e);
        return;
      case CALL_EXPR:
        checkCall(e);
        return;
      case LIST_EXPR:
        checkCreateList(e);
        return;
      case STRUCT_EXPR:
        checkCreateStruct(e);
        return;
      case COMPREHENSION_EXPR:
        checkComprehension(e);
        return;
      default:
        throw new IllegalArgumentException(
            String.format("Unrecognized ast type: %s", e.getClass().getName()));
    }
  }

  void checkInt64Literal(Expr.Builder e) {
    setType(e, Decls.Int);
  }

  void checkUint64Literal(Expr.Builder e) {
    setType(e, Decls.Uint);
  }

  void checkStringLiteral(Expr.Builder e) {
    setType(e, Decls.String);
  }

  void checkBytesLiteral(Expr.Builder e) {
    setType(e, Decls.Bytes);
  }

  void checkDoubleLiteral(Expr.Builder e) {
    setType(e, Decls.Double);
  }

  void checkBoolLiteral(Expr.Builder e) {
    setType(e, Decls.Bool);
  }

  void checkNullLiteral(Expr.Builder e) {
    setType(e, Decls.Null);
  }

  void checkIdent(Expr.Builder e) {
    Ident.Builder identExpr = e.getIdentExprBuilder();
    // Check to see if the identifier is declared.
    Decl ident = env.lookupIdent(identExpr.getName());
    if (ident != null) {
      setType(e, ident.getIdent().getType());
      setReference(e, newIdentReference(ident.getName(), ident.getIdent().getValue()));
      // Overwrite the identifier with its fully qualified name.
      identExpr.setName(ident.getName());
      return;
    }

    setType(e, Decls.Error);
    errors.undeclaredReference(location(e), env.container.name(), identExpr.getName());
  }

  void checkSelect(Expr.Builder e) {
    Select.Builder sel = e.getSelectExprBuilder();
    // Before traversing down the tree, try to interpret as qualified name.
    String qname = Container.toQualifiedName(e.build());
    if (qname != null) {
      Decl ident = env.lookupIdent(qname);
      if (ident != null) {
        if (sel.getTestOnly()) {
          errors.expressionDoesNotSelectField(location(e));
          setType(e, Decls.Bool);
          return;
        }
        // Rewrite the node to be a variable reference to the resolved fully-qualified
        // variable name.
        setType(e, ident.getIdent().getType());
        setReference(e, newIdentReference(ident.getName(), ident.getIdent().getValue()));
        String identName = ident.getName();
        e.getIdentExprBuilder().setName(identName);
        return;
      }
    }

    // Interpret as field selection, first traversing down the operand.
    check(sel.getOperandBuilder());

    Type targetType = getType(sel.getOperandBuilder());
    // Assume error type by default as most types do not support field selection.
    Type resultType = Decls.Error;
    switch (kindOf(targetType)) {
      case kindMap:
        // Maps yield their value type as the selection result type.
        MapType mapType = targetType.getMapType();
        resultType = mapType.getValueType();
        break;
      case kindObject:
        // Objects yield their field type declaration as the selection result type, but only if
        // the field is defined.
        FieldType fieldType =
            lookupFieldType(location(e), targetType.getMessageType(), sel.getField());
        if (fieldType != null) {
          resultType = fieldType.type;
        }
        break;
      case kindTypeParam:
        // Set the operand type to DYN to prevent assignment to a potentionally incorrect type
        // at a later point in type-checking. The isAssignable call will update the type
        // substitutions for the type param under the covers.
        isAssignable(Decls.Dyn, targetType);
        // Also, set the result type to DYN.
        resultType = Decls.Dyn;
        break;
      default:
        // Dynamic / error values are treated as DYN type. Errors are handled this way as well
        // in order to allow forward progress on the check.
        if (isDynOrError(targetType)) {
          resultType = Decls.Dyn;
        } else {
          errors.typeDoesNotSupportFieldSelection(location(e), targetType);
        }
        break;
    }
    if (sel.getTestOnly()) {
      resultType = Decls.Bool;
    }
    setType(e, resultType);
  }

  void checkCall(Expr.Builder e) {
    // Note: similar logic exists within the `interpreter/planner.go`. If making changes here
    // please consider the impact on planner.go and consolidate implementations or mirror code
    // as appropriate.
    Call.Builder call = e.getCallExprBuilder();
    List<Expr.Builder> args = call.getArgsBuilderList();
    String fnName = call.getFunction();

    // Traverse arguments.
    for (Expr.Builder arg : args) {
      check(arg);
    }

    // Regular static call with simple name.
    if (call.getTarget() == Expr.getDefaultInstance()) {
      // Check for the existence of the function.
      Decl fn = env.lookupFunction(fnName);
      if (fn == null) {
        errors.undeclaredReference(location(e), env.container.name(), fnName);
        setType(e, Decls.Error);
        return;
      }
      // Overwrite the function name with its fully qualified resolved name.
      call.setFunction(fn.getName());
      // Check to see whether the overload resolves.
      resolveOverloadOrError(location(e), e, fn, null, args);
      return;
    }

    // If a receiver 'target' is present, it may either be a receiver function, or a namespaced
    // function, but not both. Given a.b.c() either a.b.c is a function or c is a function with
    // target a.b.
    //
    // Check whether the target is a namespaced function name.
    Expr.Builder target = call.getTargetBuilder();
    String qualifiedPrefix = Container.toQualifiedName(target.build());
    if (qualifiedPrefix != null) {
      String maybeQualifiedName = qualifiedPrefix + "." + fnName;
      Decl fn = env.lookupFunction(maybeQualifiedName);
      if (fn != null) {
        // The function name is namespaced and so preserving the target operand would
        // be an inaccurate representation of the desired evaluation behavior.
        // Overwrite with fully-qualified resolved function name sans receiver target.
        call.clearTarget().setFunction(fn.getName());
        resolveOverloadOrError(location(e), e, fn, null, args);
        return;
      }
    }

    // Regular instance call.
    check(target);
    // Overwrite with fully-qualified resolved function name sans receiver target.
    Decl fn = env.lookupFunction(fnName);
    // Function found, attempt overload resolution.
    if (fn != null) {
      resolveOverloadOrError(location(e), e, fn, target, args);
      return;
    }
    // Function name not declared, record error.
    errors.undeclaredReference(location(e), env.container.name(), fnName);
  }

  void resolveOverloadOrError(
      Location loc, Expr.Builder e, Decl fn, Expr.Builder target, List<Expr.Builder> args) {
    // Attempt to resolve the overload.
    overloadResolution resolution = resolveOverload(loc, fn, target, args);
    // No such overload, error noted in the resolveOverload call, type recorded here.
    if (resolution == null) {
      setType(e, Decls.Error);
      return;
    }
    // Overload found.
    setType(e, resolution.type);
    setReference(e, resolution.reference);
  }

  overloadResolution resolveOverload(
      Location loc, Decl fn, Expr.Builder target, List<Expr.Builder> args) {

    List<Type> argTypes = new ArrayList<>();
    if (target != null) {
      argTypes.add(getType(target));
    }
    for (Expr.Builder arg : args) {
      argTypes.add(getType(arg));
    }

    Type resultType = null;
    Reference checkedRef = null;
    for (Overload overload : fn.getFunction().getOverloadsList()) {
      if ((target == null && overload.getIsInstanceFunction())
          || (target != null && !overload.getIsInstanceFunction())) {
        // not a compatible call style.
        continue;
      }

      Type overloadType = Decls.newFunctionType(overload.getResultType(), overload.getParamsList());
      if (overload.getTypeParamsCount() > 0) {
        // Instantiate overload's type with fresh type variables.
        Mapping substitutions = newMapping();
        for (String typePar : overload.getTypeParamsList()) {
          substitutions.add(Decls.newTypeParamType(typePar), newTypeVar());
        }
        overloadType = substitute(substitutions, overloadType, false);
      }

      List<Type> candidateArgTypes = overloadType.getFunction().getArgTypesList();
      if (isAssignableList(argTypes, candidateArgTypes)) {
        if (checkedRef == null) {
          checkedRef = newFunctionReference(Collections.singletonList(overload.getOverloadId()));
        } else {
          checkedRef = checkedRef.toBuilder().addOverloadId(overload.getOverloadId()).build();
        }

        // First matching overload, determines result type.
        Type fnResultType = substitute(mappings, overloadType.getFunction().getResultType(), false);
        if (resultType == null) {
          resultType = fnResultType;
        } else if (!isDyn(resultType) && !fnResultType.equals(resultType)) {
          resultType = Decls.Dyn;
        }
      }
    }

    if (resultType == null) {
      errors.noMatchingOverload(loc, fn.getName(), argTypes, target != null);
      // resultType = Decls.Error;
      return null;
    }

    return newResolution(checkedRef, resultType);
  }

  void checkCreateList(Expr.Builder e) {
    CreateList.Builder create = e.getListExprBuilder();
    Type elemType = null;
    for (int i = 0; i < create.getElementsBuilderList().size(); i++) {
      Expr.Builder el = create.getElementsBuilderList().get(i);
      check(el);
      elemType = joinTypes(location(el), elemType, getType(el));
    }
    if (elemType == null) {
      // If the list is empty, assign free type var to elem type.
      elemType = newTypeVar();
    }
    setType(e, Decls.newListType(elemType));
  }

  void checkCreateStruct(Expr.Builder e) {
    CreateStruct.Builder str = e.getStructExprBuilder();
    if (!str.getMessageName().isEmpty()) {
      checkCreateMessage(e);
    } else {
      checkCreateMap(e);
    }
  }

  void checkCreateMap(Expr.Builder e) {
    CreateStruct.Builder mapVal = e.getStructExprBuilder();
    Type keyType = null;
    Type valueType = null;
    for (Entry.Builder ent : mapVal.getEntriesBuilderList()) {
      Expr.Builder key = ent.getMapKeyBuilder();
      check(key);
      keyType = joinTypes(location(key), keyType, getType(key));

      Expr.Builder val = ent.getValueBuilder();
      check(val);
      valueType = joinTypes(location(val), valueType, getType(val));
    }
    if (keyType == null) {
      // If the map is empty, assign free type variables to typeKey and value type.
      keyType = newTypeVar();
      valueType = newTypeVar();
    }
    setType(e, Decls.newMapType(keyType, valueType));
  }

  void checkCreateMessage(Expr.Builder e) {
    CreateStruct.Builder msgVal = e.getStructExprBuilder();
    // Determine the type of the message.
    Type messageType = Decls.Error;
    Decl decl = env.lookupIdent(msgVal.getMessageName());
    if (decl == null) {
      errors.undeclaredReference(location(e), env.container.name(), msgVal.getMessageName());
      return;
    }
    // Ensure the type name is fully qualified in the AST.
    msgVal.setMessageName(decl.getName());
    setReference(e, newIdentReference(decl.getName(), null));
    IdentDecl ident = decl.getIdent();
    Types.Kind identKind = kindOf(ident.getType());
    if (identKind != Kind.kindError) {
      if (identKind != Kind.kindType) {
        errors.notAType(location(e), ident.getType());
      } else {
        messageType = ident.getType().getType();
        if (kindOf(messageType) != Kind.kindObject) {
          errors.notAMessageType(location(e), messageType);
          messageType = Decls.Error;
        }
      }
    }
    if (isObjectWellKnownType(messageType)) {
      setType(e, getObjectWellKnownType(messageType));
    } else {
      setType(e, messageType);
    }

    // Check the field initializers.
    for (Entry.Builder ent : msgVal.getEntriesBuilderList()) {
      String field = ent.getFieldKey();
      Expr.Builder value = ent.getValueBuilder();
      check(value);

      Type fieldType = Decls.Error;
      FieldType t = lookupFieldType(locationByID(ent.getId()), messageType.getMessageType(), field);
      if (t != null) {
        fieldType = t.type;
      }
      if (!isAssignable(fieldType, getType(value))) {
        errors.fieldTypeMismatch(locationByID(ent.getId()), field, fieldType, getType(value));
      }
    }
  }

  void checkComprehension(Expr.Builder e) {
    Comprehension.Builder comp = e.getComprehensionExprBuilder();
    check(comp.getIterRangeBuilder());
    check(comp.getAccuInitBuilder());
    Type accuType = getType(comp.getAccuInitBuilder());
    Type rangeType = getType(comp.getIterRangeBuilder());
    Type varType;

    switch (kindOf(rangeType)) {
      case kindList:
        varType = rangeType.getListType().getElemType();
        break;
      case kindMap:
        // Ranges over the keys.
        varType = rangeType.getMapType().getKeyType();
        break;
      case kindDyn:
      case kindError:
      case kindTypeParam:
        // Set the range type to DYN to prevent assignment to a potentionally incorrect type
        // at a later point in type-checking. The isAssignable call will update the type
        // substitutions for the type param under the covers.
        isAssignable(Decls.Dyn, rangeType);
        // Set the range iteration variable to type DYN as well.
        varType = Decls.Dyn;
        break;
      default:
        errors.notAComprehensionRange(location(comp.getIterRangeBuilder()), rangeType);
        varType = Decls.Error;
        break;
    }

    // Create a scope for the comprehension since it has a local accumulation variable.
    // This scope will contain the accumulation variable used to compute the result.
    env = env.enterScope();
    env.add(Decls.newVar(comp.getAccuVar(), accuType));
    // Create a block scope for the loop.
    env = env.enterScope();
    env.add(Decls.newVar(comp.getIterVar(), varType));
    // Check the variable references in the condition and step.
    check(comp.getLoopConditionBuilder());
    assertType(comp.getLoopConditionBuilder(), Decls.Bool);
    check(comp.getLoopStepBuilder());
    assertType(comp.getLoopStepBuilder(), accuType);
    // Exit the loop's block scope before checking the result.
    env = env.exitScope();
    check(comp.getResultBuilder());
    // Exit the comprehension scope.
    env = env.exitScope();
    setType(e, getType(comp.getResultBuilder()));
  }

  /** Checks compatibility of joined types, and returns the most general common type. */
  Type joinTypes(Location loc, Type previous, Type current) {
    if (previous == null) {
      return current;
    }
    if (isAssignable(previous, current)) {
      return mostGeneral(previous, current);
    }
    if (dynAggregateLiteralElementTypesEnabled()) {
      return Decls.Dyn;
    }
    errors.typeMismatch(loc, previous, current);
    return Decls.Error;
  }

  boolean dynAggregateLiteralElementTypesEnabled() {
    return env.aggLitElemType == dynElementType;
  }

  Type newTypeVar() {
    int id = freeTypeVarCounter;
    freeTypeVarCounter++;
    return Decls.newTypeParamType(String.format("_var%d", id));
  }

  boolean isAssignable(Type t1, Type t2) {
    Mapping subs = Types.isAssignable(mappings, t1, t2);
    if (subs != null) {
      mappings = subs;
      return true;
    }

    return false;
  }

  boolean isAssignableList(List<Type> l1, List<Type> l2) {
    Mapping subs = Types.isAssignableList(mappings, l1, l2);
    if (subs != null) {
      mappings = subs;
      return true;
    }

    return false;
  }

  FieldType lookupFieldType(Location l, String messageType, String fieldName) {
    if (env.provider.findType(messageType) == null) {
      // This should not happen, anyway, report an error.
      errors.unexpectedFailedResolution(l, messageType);
      return null;
    }

    FieldType ft = env.provider.findFieldType(messageType, fieldName);
    if (ft != null) {
      return ft;
    }

    errors.undefinedField(l, fieldName);
    return null;
  }

  void setType(Expr.Builder e, Type t) {
    Type old = types.get(e.getId());
    if (old != null && !old.equals(t)) {
      throw new IllegalStateException(
          String.format(
              "(Incompatible) Type already exists for expression: %s(%d) old:%s, new:%s",
              e, e.getId(), old, t));
    }
    types.put(e.getId(), t);
  }

  Type getType(Expr.Builder e) {
    return types.get(e.getId());
  }

  void setReference(Expr.Builder e, Reference r) {
    Reference old = references.get(e.getId());
    if (old != null && !old.equals(r)) {
      throw new IllegalStateException(
          String.format(
              "Reference already exists for expression: %s(%d) old:%s, new:%s",
              e, e.getId(), old, r));
    }
    references.put(e.getId(), r);
  }

  void assertType(Expr.Builder e, Type t) {
    if (!isAssignable(t, getType(e))) {
      errors.typeMismatch(location(e), t, getType(e));
    }
  }

  static class overloadResolution {
    final Reference reference;
    final Type type;

    public overloadResolution(Reference reference, Type type) {
      this.reference = reference;
      this.type = type;
    }
  }

  static overloadResolution newResolution(Reference checkedRef, Type t) {
    return new overloadResolution(checkedRef, t);
  }

  Location location(Expr.Builder e) {
    return locationByID(e.getId());
  }

  Location locationByID(long id) {
    Map<Long, Integer> positions = sourceInfo.getPositionsMap();
    int line = 1;
    Integer offset = positions.get(id);
    if (offset != null) {
      int col = offset;
      for (Integer lineOffset : sourceInfo.getLineOffsetsList()) {
        if (lineOffset < offset) {
          line++;
          col = offset - lineOffset;
        } else {
          break;
        }
      }
      return newLocation(line, col);
    }
    return NoLocation;
  }

  static Reference newIdentReference(String name, Constant value) {
    Reference.Builder refBuilder = Reference.newBuilder().setName(name);
    if (value != null) {
      refBuilder = refBuilder.setValue(value);
    }
    return refBuilder.build();
  }

  static Reference newFunctionReference(List<String> overloads) {
    return Reference.newBuilder().addAllOverloadId(overloads).build();
  }
}
