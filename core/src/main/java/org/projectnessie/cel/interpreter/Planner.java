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
package org.projectnessie.cel.interpreter;

import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.DurationT.durationOf;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.interpreter.AttributePattern.newPartialAttributeFactory;
import static org.projectnessie.cel.interpreter.Interpretable.newConstValue;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Call;
import com.google.api.expr.v1alpha1.Expr.Comprehension;
import com.google.api.expr.v1alpha1.Expr.CreateList;
import com.google.api.expr.v1alpha1.Expr.CreateStruct;
import com.google.api.expr.v1alpha1.Expr.CreateStruct.Entry;
import com.google.api.expr.v1alpha1.Expr.Ident;
import com.google.api.expr.v1alpha1.Expr.Select;
import com.google.api.expr.v1alpha1.Reference;
import com.google.api.expr.v1alpha1.Type;
import com.google.api.expr.v1alpha1.Type.PrimitiveType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.BoolT;
import org.projectnessie.cel.common.types.DoubleT;
import org.projectnessie.cel.common.types.IntT;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.UintT;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.ExactAggregateFieldProvider;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.StandardScalarFieldProvider;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Trait;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.AttributeFactory.Qualifier;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableAttribute;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableCall;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableConst;
import org.projectnessie.cel.interpreter.functions.BinaryOp;
import org.projectnessie.cel.interpreter.functions.FunctionOp;
import org.projectnessie.cel.interpreter.functions.Overload;
import org.projectnessie.cel.interpreter.functions.QuaternaryOp;
import org.projectnessie.cel.interpreter.functions.QuinaryOp;
import org.projectnessie.cel.interpreter.functions.TernaryOp;
import org.projectnessie.cel.interpreter.functions.UnaryOp;

/** Plans an {@link Interpretable} evaluation tree from a proto expression. */
final class Planner implements InterpretablePlanner {
  private final Dispatcher disp;
  private final TypeProvider provider;
  private final TypeAdapter adapter;
  private final AttributeFactory attrFactory;
  private final Container container;
  private final Map<Long, Reference> refMap;
  private final Map<Long, Type> typeMap;
  private final Map<String, FieldType> fieldTypes = new HashMap<>();
  private final PlanningPolicy policy;
  private final InterpretableDecorator[] decorators;
  private AttributeFactory partialAttrFactory;
  private int planDepth;
  private NativeLocalVariable nativeLocalVariable;

  Planner(
      Dispatcher disp,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      Container container,
      Map<Long, Reference> refMap,
      Map<Long, Type> typeMap,
      PlanningPolicy policy,
      InterpretableDecorator[] decorators) {
    this.disp = disp;
    this.provider = provider;
    this.adapter = adapter;
    this.attrFactory = attrFactory;
    this.container = container;
    this.refMap = refMap;
    this.typeMap = typeMap;
    this.policy = policy;
    this.decorators = decorators;
  }

  PlanningPolicy policy() {
    return policy;
  }

  /**
   * Plan implements the interpretablePlanner interface. This implementation of the Plan method also
   * applies decorators to each Interpretable generated as part of the overall plan. Decorators are
   * useful for layering functionality into the evaluation that is not natively understood by CEL,
   * such as state-tracking, expression re-write, and possibly efficient thread-safe memoization of
   * repeated expressions.
   */
  @Override
  public Interpretable plan(Expr expr) {
    boolean root = planDepth == 0;
    planDepth++;
    try {
      Interpretable result =
          switch (expr.getExprKindCase()) {
            case CALL_EXPR -> planCall(expr);
            case IDENT_EXPR -> planIdent(expr);
            case SELECT_EXPR -> planSelect(expr);
            case LIST_EXPR -> planCreateList(expr);
            case STRUCT_EXPR -> planCreateStruct(expr);
            case COMPREHENSION_EXPR -> planComprehension(expr);
            case CONST_EXPR -> planConst(expr);
            default ->
                throw new IllegalArgumentException(
                    String.format(
                        "unsupported expr of kind %s: '%s'", expr.getExprKindCase(), expr));
          };
      if (root
          && policy.nativeSpecializationPermitted()
          && NativeIsland.supports(result)
          && !(policy.builtInOptimizationEnabled() && result instanceof InterpretableConst)) {
        result = new NativeIsland(result, adapter);
      }
      return decorate(result);
    } finally {
      planDepth--;
    }
  }

  /**
   * decorate applies the InterpretableDecorator functions to the given Interpretable. Both the
   * Interpretable and error generated by a Plan step are accepted as arguments for convenience.
   */
  Interpretable decorate(Interpretable i) {
    for (InterpretableDecorator dec : decorators) {
      i = dec.decorate(i);
      if (i == null) {
        return null;
      }
    }
    return i;
  }

  /** planIdent creates an Interpretable that resolves an identifier from an Activation. */
  Interpretable planIdent(Expr expr) {
    // Establish whether the identifier is in the reference map.
    Reference identRef = refMap.get(expr.getId());
    if (identRef != null) {
      return planCheckedIdent(expr.getId(), identRef);
    }
    // Create the possible attribute list for the unresolved reference.
    Ident ident = expr.getIdentExpr();
    return new EvalAttr(adapter, attrFactory.maybeAttribute(expr.getId(), ident.getName()));
  }

  Interpretable planCheckedIdent(long id, Reference identRef) {
    String identName = identRef.getName();
    String providerName = identName.startsWith(".") ? identName.substring(1) : identName;
    // Plan a constant reference if this is the case for this simple identifier.
    if (identRef.getValue() != Reference.getDefaultInstance().getValue()) {
      return plan(Expr.newBuilder().setId(id).setConstExpr(identRef.getValue()).build());
    }

    // Check to see whether the type map indicates this is a type name. All types should be
    // registered with the provider.
    Type cType = typeMap.get(id);
    if (cType != null && cType.getType() != Type.getDefaultInstance()) {
      Val cVal = provider.findIdent(providerName);
      if (cVal == null) {
        throw new IllegalStateException(
            String.format("reference to undefined type: %s", providerName));
      }
      return newConstValue(id, cVal);
    }

    // Otherwise, evaluate the checked top-level variable directly for ordinary plans. Decorated
    // programs keep the attribute shape because custom decorators may inspect attributes.
    if (decorators.length == 0) {
      if (adapter instanceof ExactAggregateTypeAdapter exactAdapter && isAggregateType(cType)) {
        CheckedAggregateMaterializer materializer =
            new CheckedAggregateMaterializer(exactAdapter, cType);
        if (nativeCertifiedHostAggregatePlanning()) {
          if (cType.getTypeKindCase() == Type.TypeKindCase.LIST_TYPE) {
            return new NativeExactAggregateIdent(id, identName, adapter, materializer);
          }
          if (cType.getTypeKindCase() == Type.TypeKindCase.MAP_TYPE) {
            return new NativeExactMapIdent(id, identName, adapter, materializer);
          }
        }
        return new EvalExactAggregateIdent(id, identName, adapter, materializer);
      }
      if (nativeScalarPlanning()) {
        NativeLocalVariable local = nativeLocalVariable(identName);
        if (local != null && local.kind == nativeKind(id)) {
          return switch (local.kind) {
            case BOOLEAN -> new NativeBooleanLocalIdent(id, identName, adapter);
            case INT -> new NativeIntLocalIdent(id, identName, adapter);
            case UINT -> new NativeUintLocalIdent(id, identName, adapter);
            case DOUBLE -> new NativeDoubleLocalIdent(id, identName, adapter);
            case STRING -> new NativeStringLocalIdent(id, identName, adapter);
            case NULL -> new NativeNullLocalIdent(id, identName, adapter);
          };
        }
        if (hasPrimitiveType(id, PrimitiveType.INT64)) {
          return new NativeIntIdent(id, identName, adapter);
        }
        if (hasPrimitiveType(id, PrimitiveType.UINT64)) {
          return new NativeUintIdent(id, identName, adapter);
        }
        if (hasPrimitiveType(id, PrimitiveType.BOOL)) {
          return new NativeBooleanIdent(id, identName, adapter);
        }
        if (hasPrimitiveType(id, PrimitiveType.DOUBLE)) {
          return new NativeDoubleIdent(id, identName, adapter);
        }
        if (hasPrimitiveType(id, PrimitiveType.STRING)) {
          return new NativeStringIdent(id, identName, adapter);
        }
        if (hasNullType(id)) {
          return new NativeNullIdent(id, identName, adapter);
        }
        if (hasListType(id)) {
          return new NativeRawIdent(id, identName, adapter);
        }
      }
      return new EvalIdent(id, identName, adapter);
    }
    return new EvalAttr(adapter, attrFactory.absoluteAttribute(id, identName));
  }

  /**
   * planSelect creates an Interpretable with either:
   *
   * <ol>
   *   <li>selects a field from a map or proto.
   *   <li>creates a field presence test for a select within a has() macro.
   *   <li>resolves the select expression to a namespaced identifier.
   * </ol>
   */
  Interpretable planSelect(Expr expr) {
    // If the Select id appears in the reference map from the CheckedExpr proto then it is either
    // a namespaced identifier or enum value.
    Reference identRef = refMap.get(expr.getId());
    if (identRef != null) {
      return planCheckedIdent(expr.getId(), identRef);
    }

    Select sel = expr.getSelectExpr();
    // Plan the operand evaluation.
    Interpretable op =
        decorators.length == 0 ? planSelectOperand(sel.getOperand()) : plan(sel.getOperand());

    // Determine the field type if this is a proto message type.
    FieldType fieldType = null;
    Type opType = typeMap.get(sel.getOperand().getId());
    if (opType != null && !opType.getMessageType().isEmpty()) {
      FieldType ft = findFieldType(opType.getMessageType(), sel.getField());
      if (ft != null && ft.isSet != null && ft.getFrom != null) {
        fieldType = ft;
      }
    }

    // If the Select was marked TestOnly, this is a presence test.
    //
    // Note: presence tests are defined for structured (e.g. proto) and dynamic values (map, json)
    // as follows:
    //  - True if the object field has a non-default value, e.g. obj.str != ""
    //  - True if the dynamic value has the field defined, e.g. key in map
    //
    // However, presence tests are not defined for qualified identifier names with primitive
    // types.
    // If a string named 'a.b.c' is declared in the environment and referenced within
    // `has(a.b.c)`,
    // it is not clear whether has should error or follow the convention defined for structured
    // values.
    if (sel.getTestOnly()) {
      // Return the test only eval expression.
      return new EvalTestOnly(expr.getId(), asEstablished(op), stringOf(sel.getField()), fieldType);
    }
    // Build a qualifier.
    boolean rawExactAggregateField = exactAggregateField(expr, opType, fieldType);
    Qualifier qual =
        fieldType == null
            ? attrFactory.newQualifier(opType, expr.getId(), sel.getField())
            : rawExactAggregateField
                ? new RawExactAggregateFieldQualifier(
                    expr.getId(), sel.getField(), fieldType, adapter)
                : fieldQualifier(expr, sel, fieldType);
    if (qual == null) {
      return null;
    }
    // Lastly, create a field selection Interpretable.
    if (op instanceof InterpretableAttribute attr) {
      attr.addQualifier(qual);
      return specializeSelector(
          expr,
          opType,
          fieldType,
          attr,
          rawExactAggregateField
              ? partialRawExactSelectAttribute(sel, qual)
              : partialSelectAttribute(expr, sel, opType));
    }

    InterpretableAttribute relAttr = relativeAttr(op.id(), asEstablished(op));
    if (relAttr == null) {
      return null;
    }
    relAttr.addQualifier(qual);
    return specializeSelector(
        expr,
        opType,
        fieldType,
        relAttr,
        rawExactAggregateField
            ? partialRawExactSelectAttribute(sel, qual)
            : partialSelectAttribute(expr, sel, opType));
  }

  private boolean exactAggregateField(Expr expr, Type operandType, FieldType fieldType) {
    if (operandType == null
        || operandType.getMessageType().isEmpty()
        || expr.getExprKindCase() != Expr.ExprKindCase.SELECT_EXPR) {
      return false;
    }
    Select select = expr.getSelectExpr();
    Type checkedType = typeMap.get(expr.getId());
    return decorators.length == 0
        && fieldType != null
        && isAggregateType(checkedType)
        && provider == adapter
        && provider instanceof ExactAggregateFieldProvider exactProvider
        && exactProvider.isExactAggregateField(
            operandType.getMessageType(), select.getField(), checkedType)
        && adapter instanceof ExactAggregateTypeAdapter;
  }

  private FieldQualifier fieldQualifier(Expr expr, Select select, FieldType fieldType) {
    Type resultType = typeMap.get(expr.getId());
    CheckedAggregateMaterializer materializer = null;
    Type operandType = typeMap.get(select.getOperand().getId());
    if (isAggregateType(resultType)
        && provider == adapter
        && operandType != null
        && !operandType.getMessageType().isEmpty()
        && provider instanceof ExactAggregateFieldProvider exactProvider
        && exactProvider.isExactAggregateField(
            operandType.getMessageType(), select.getField(), resultType)
        && adapter instanceof ExactAggregateTypeAdapter exactAdapter) {
      materializer = new CheckedAggregateMaterializer(exactAdapter, resultType);
    }
    return new FieldQualifier(expr.getId(), select.getField(), fieldType, adapter, materializer);
  }

  private Interpretable specializeScalarSelector(
      Expr expr,
      Type operandType,
      FieldType fieldType,
      InterpretableAttribute attribute,
      Attribute partialAttribute) {
    if (!nativeScalarPlanning() || operandType == null || partialAttribute == null) {
      return attribute;
    }
    boolean exactMap = exactStringMapResult(expr, operandType);
    boolean exactField =
        fieldType != null
            && provider instanceof StandardScalarFieldProvider
            && !operandType.getMessageType().startsWith("google.protobuf.")
            && (provider instanceof ProtoTypeRegistry
                || hasPrimitiveType(expr.getId(), PrimitiveType.STRING));
    return exactMap || exactField
        ? specializeScalarAttribute(expr, attribute, partialAttribute)
        : attribute;
  }

  private Interpretable specializeSelector(
      Expr expr,
      Type operandType,
      FieldType fieldType,
      InterpretableAttribute attribute,
      Attribute partialAttribute) {
    Type resultType = typeMap.get(expr.getId());
    if (exactAggregateField(expr, operandType, fieldType)
        && resultType != null
        && adapter instanceof ExactAggregateTypeAdapter exactAdapter) {
      CheckedAggregateMaterializer materializer =
          new CheckedAggregateMaterializer(exactAdapter, resultType);
      if (nativeCertifiedHostAggregatePlanning() && partialAttribute != null) {
        if (resultType.getTypeKindCase() == Type.TypeKindCase.LIST_TYPE) {
          return new NativeExactListFieldAttr(
              expr.getId(), attribute.attr(), partialAttribute, materializer);
        }
        if (resultType.getTypeKindCase() == Type.TypeKindCase.MAP_TYPE) {
          return new NativeExactMapFieldAttr(
              expr.getId(), attribute.attr(), partialAttribute, materializer);
        }
      }
      return new EvalExactAggregateFieldAttr(
          expr.getId(), attribute.attr(), partialAttribute, materializer);
    }
    return specializeScalarSelector(expr, operandType, fieldType, attribute, partialAttribute);
  }

  private Interpretable planSelectOperand(Expr operand) {
    if (operand.getExprKindCase() != Expr.ExprKindCase.IDENT_EXPR) {
      return plan(operand);
    }

    Reference identRef = refMap.get(operand.getId());
    if (identRef == null || identRef.getValue() != Reference.getDefaultInstance().getValue()) {
      return plan(operand);
    }

    Type cType = typeMap.get(operand.getId());
    if (cType != null && cType.getType() != Type.getDefaultInstance()) {
      return plan(operand);
    }

    return new EvalAttr(
        adapter, attrFactory.absoluteAttribute(operand.getId(), identRef.getName()));
  }

  private FieldType findFieldType(String messageType, String fieldName) {
    String key = messageType + '\n' + fieldName;
    FieldType ft = fieldTypes.get(key);
    if (ft != null) {
      return ft;
    }
    ft = provider.findFieldType(messageType, fieldName);
    if (ft != null) {
      fieldTypes.put(key, ft);
    }
    return ft;
  }

  /**
   * planCall creates a callable Interpretable while specializing for common functions and
   * invocation patterns. Specifically, conditional operators &&, ||, ?:, and (in)equality functions
   * result in optimized Interpretable values.
   */
  Interpretable planCall(Expr expr) {
    Call call = expr.getCallExpr();
    ResolvedFunction resolvedFunc = resolveFunction(expr);
    // target, fnName, oName := p.resolveFunction(expr)
    int argCount = call.getArgsCount();
    int offset = 0;
    if (resolvedFunc.target != null) {
      argCount++;
      offset++;
    }

    Interpretable[] args = new Interpretable[argCount];
    if (resolvedFunc.target != null) {
      Interpretable arg = plan(resolvedFunc.target);
      if (arg == null) {
        return null;
      }
      args[0] = arg;
    }
    for (int i = 0; i < call.getArgsCount(); i++) {
      Expr argExpr = call.getArgs(i);
      Interpretable arg = plan(argExpr);
      args[i + offset] = arg;
    }

    Interpretable foldedConversion = foldConstantConversion(expr, resolvedFunc, args);
    if (foldedConversion != null) {
      return foldedConversion;
    }

    // Generate specialized Interpretable operators by function name if possible.
    if (resolvedFunc.fnName.equals(Operator.LogicalAnd.id))
      return planCallLogicalAnd(expr, resolvedFunc, args);
    if (resolvedFunc.fnName.equals(Operator.LogicalOr.id))
      return planCallLogicalOr(expr, resolvedFunc, args);
    if (resolvedFunc.fnName.equals(Operator.Conditional.id))
      return planCallConditional(expr, resolvedFunc, args);
    if (resolvedFunc.fnName.equals(Operator.Equals.id))
      return planCallEqual(expr, resolvedFunc, args);
    if (resolvedFunc.fnName.equals(Operator.NotEquals.id))
      return planCallNotEqual(expr, resolvedFunc, args);
    if (resolvedFunc.fnName.equals(Operator.Add.id)) {
      Interpretable concat = specializeListConcat(expr, resolvedFunc, args);
      if (concat != null) {
        return concat;
      }
    }
    if (resolvedFunc.fnName.equals(Operator.Index.id))
      return planCallIndex(expr, resolvedFunc, args);
    if (resolvedFunc.fnName.equals(Operator.In.id)) {
      Interpretable constantSetMembership = foldConstantSetMembership(expr, resolvedFunc, args);
      if (constantSetMembership != null) {
        return constantSetMembership;
      }
      Interpretable mapMembership = specializeExactMapMembership(expr, resolvedFunc, args);
      if (mapMembership != null) {
        return mapMembership;
      }
      Interpretable exactSetMembership =
          specializeExactScalarSetMembership(expr, resolvedFunc, args);
      if (exactSetMembership != null) {
        return exactSetMembership;
      }
      Interpretable concatMembership =
          specializeScalarListConcatMembership(expr, resolvedFunc, args);
      if (concatMembership != null) {
        return concatMembership;
      }
      Interpretable literalMembership =
          specializeStringListLiteralMembership(expr, resolvedFunc, args);
      if (literalMembership != null) {
        return literalMembership;
      }
      Interpretable foldMembership = specializeStringListFoldMembership(expr, resolvedFunc, args);
      if (foldMembership != null) {
        return foldMembership;
      }
      Interpretable membership = specializeTopLevelStringListMembership(expr, resolvedFunc, args);
      if (membership != null) {
        return membership;
      }
    }
    if (resolvedFunc.fnName.equals(Overloads.Size)) {
      Interpretable concatSize = specializeListConcatSize(expr, resolvedFunc, args);
      if (concatSize != null) {
        return concatSize;
      }
      Interpretable literalSize = specializeScalarListLiteralSize(expr, resolvedFunc, args);
      if (literalSize != null) {
        return literalSize;
      }
      Interpretable foldSize = specializeScalarListFoldSize(expr, resolvedFunc, args);
      if (foldSize != null) {
        return foldSize;
      }
      Interpretable sourceSize = specializeTopLevelListSize(expr, resolvedFunc, args);
      if (sourceSize != null) {
        return sourceSize;
      }
      Interpretable mapSize = specializeExactMapSize(expr, resolvedFunc, args);
      if (mapSize != null) {
        return mapSize;
      }
    }

    // Otherwise, generate Interpretable calls specialized by argument count.
    Overload fnDef = resolvedFunc.implementation;
    Interpretable nativeScalar = specializeStrictScalarCall(expr, resolvedFunc, args);
    if (nativeScalar != null) {
      return nativeScalar;
    }
    Interpretable[] established = asEstablished(args);
    return switch (argCount) {
      case 0 -> planCallZero(expr, resolvedFunc.fnName, resolvedFunc.overloadId, fnDef);
      case 1 ->
          planCallUnary(expr, resolvedFunc.fnName, resolvedFunc.overloadId, fnDef, established);
      case 2 ->
          planCallBinary(expr, resolvedFunc.fnName, resolvedFunc.overloadId, fnDef, established);
      case 3 ->
          planCallTernary(expr, resolvedFunc.fnName, resolvedFunc.overloadId, fnDef, established);
      case 4 ->
          planCallQuaternary(
              expr, resolvedFunc.fnName, resolvedFunc.overloadId, fnDef, established);
      case 5 ->
          planCallQuinary(expr, resolvedFunc.fnName, resolvedFunc.overloadId, fnDef, established);
      default ->
          planCallVarArgs(expr, resolvedFunc.fnName, resolvedFunc.overloadId, fnDef, established);
    };
  }

  private Interpretable foldConstantConversion(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    if (!policy.builtInOptimizationEnabled()
        || !Overloads.isTypeConversionFunction(resolvedFunction.fnName)
        || arguments.length != 1
        || !(arguments[0] instanceof InterpretableConst)) {
      return null;
    }

    Interpretable call =
        planCallUnary(
            expr,
            resolvedFunction.fnName,
            resolvedFunction.overloadId,
            resolvedFunction.implementation,
            arguments);
    EvalConst folded = BuiltInOptimizer.foldConstantUnary((InterpretableCall) call);
    if (folded == null
        || !nativeScalarPlanning()
        || !StandardOverloadProvenance.isExactStandard(disp, refMap, expr)) {
      return folded;
    }

    Val value = folded.value();
    if (value == null) {
      return folded;
    }
    NativeScalarKind kind = nativeKind(expr.getId());
    if (kind == NativeScalarKind.BOOLEAN && value.getClass() == BoolT.class) {
      return new NativeBooleanConst(expr.getId(), (BoolT) value);
    }
    if (kind == NativeScalarKind.INT && value.getClass() == IntT.class) {
      return new NativeIntConst(expr.getId(), (IntT) value);
    }
    if (kind == NativeScalarKind.UINT && value.getClass() == UintT.class) {
      return new NativeUintConst(expr.getId(), (UintT) value);
    }
    if (kind == NativeScalarKind.DOUBLE && value.getClass() == DoubleT.class) {
      return new NativeDoubleConst(expr.getId(), (DoubleT) value);
    }
    if (kind == NativeScalarKind.STRING
        && value.getClass() == StringT.class
        && value.value() instanceof String) {
      return new NativeStringConst(expr.getId(), (StringT) value);
    }
    return folded;
  }

  private Interpretable foldConstantSetMembership(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    Call call = expr.getCallExpr();
    if (!policy.builtInOptimizationEnabled()
        || !resolvedFunction.overloadId.equals(Overloads.InList)
        || call.hasTarget()
        || call.getArgsCount() != 2
        || arguments.length != 2
        || !(arguments[1] instanceof InterpretableConst)) {
      return null;
    }

    BuiltInOptimizer.ConstantSet constantSet = BuiltInOptimizer.constantSet(arguments[1]);
    if (constantSet == null) {
      return null;
    }

    Interpretable[] established = asEstablished(arguments.clone());
    Interpretable original =
        planCallBinary(
            expr,
            resolvedFunction.fnName,
            resolvedFunction.overloadId,
            resolvedFunction.implementation,
            established);
    return new EvalSetMembership(
        original, established[0], constantSet.typeName(), constantSet.values());
  }

  private Interpretable specializeListConcat(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    if (!nativeCertifiedHostAggregatePlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.overloadId.equals(Overloads.AddList)
        || arguments.length != 2
        || inexactListConcatOperand(arguments[0])
        || inexactListConcatOperand(arguments[1])) {
      return null;
    }
    Call call = expr.getCallExpr();
    Type resultType = typeMap.get(expr.getId());
    Type leftType = typeMap.get(call.getArgs(0).getId());
    Type rightType = typeMap.get(call.getArgs(1).getId());
    if (resultType == null
        || resultType.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE
        || !resultType.equals(leftType)
        || !resultType.equals(rightType)) {
      return null;
    }
    return new NativeListConcat(
        expr.getId(), arguments[0], arguments[1], resolvedFunction.implementation);
  }

  private static boolean inexactListConcatOperand(Interpretable operand) {
    return !(operand instanceof NativeListConcat)
        && (!(operand instanceof NativeListSourceCapability source) || !source.exactListSource());
  }

  private Interpretable specializeListConcatSize(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    if (!nativeCertifiedHostAggregatePlanning()
        || resolvedFunction.nativeDescriptor() == null
        || (!resolvedFunction.overloadId.equals(Overloads.SizeList)
            && !resolvedFunction.overloadId.equals(Overloads.SizeListInst))
        || arguments.length != 1
        || !(arguments[0] instanceof NativeListConcat concat)
        || !hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
      return null;
    }
    NativeListSourceCapability[] sources = NativeListConcatKernel.collectSources(concat);
    if (sources == null) {
      return null;
    }
    return new NativeListConcatSize(
        expr.getId(),
        resolvedFunction.fnName,
        resolvedFunction.overloadId,
        concat,
        sources,
        resolvedFunction.implementation);
  }

  private Interpretable specializeExactMapSize(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    if (!nativeCertifiedHostAggregatePlanning()
        || resolvedFunction.nativeDescriptor() == null
        || (!resolvedFunction.overloadId.equals(Overloads.SizeMap)
            && !resolvedFunction.overloadId.equals(Overloads.SizeMapInst))
        || arguments.length != 1
        || !(arguments[0] instanceof NativeMapSourceCapability source)
        || !source.exactMapSource()
        || !hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
      return null;
    }
    return new NativeMapSize(
        expr.getId(),
        resolvedFunction.fnName,
        resolvedFunction.overloadId,
        arguments[0],
        resolvedFunction.implementation);
  }

  private Interpretable specializeExactMapMembership(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    Call call = expr.getCallExpr();
    if (!nativeCertifiedHostAggregatePlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.overloadId.equals(Overloads.InMap)
        || call.hasTarget()
        || call.getArgsCount() != 2
        || arguments.length != 2
        || !(arguments[1] instanceof NativeMapSourceCapability source)
        || !source.exactMapSource()
        || !hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return null;
    }
    Type mapType = typeMap.get(call.getArgs(1).getId());
    if (mapType == null || mapType.getTypeKindCase() != Type.TypeKindCase.MAP_TYPE) {
      return null;
    }
    ExactMapKey key = exactMapKey(call.getArgs(0), arguments[0], mapType.getMapType().getKeyType());
    return key != null
        ? new NativeMapMembership(
            expr.getId(),
            arguments[0],
            arguments[1],
            key.hostValue,
            key.celValue,
            resolvedFunction.implementation)
        : null;
  }

  private Interpretable specializeStrictScalarCall(
      Expr expr, ResolvedFunction resolved, Interpretable[] args) {
    if (!nativeScalarPlanning() || resolved.nativeDescriptor() == null) {
      return null;
    }
    String overload = resolved.overloadId;
    Overload implementation = resolved.implementation;
    if (args.length == 1) {
      Interpretable argument = args[0];
      if (overload.equals(Overloads.LogicalNot)
          && argument instanceof NativeBooleanCapability
          && hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
        return new NativeBooleanNot(
            expr.getId(), resolved.fnName, overload, argument, implementation);
      }
      if (overload.equals(Overloads.NegateInt64)
          && argument instanceof NativeIntCapability
          && hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
        return new NativeIntNegate(
            expr.getId(), resolved.fnName, overload, argument, implementation);
      }
      if (overload.equals(Overloads.NegateDouble)
          && argument instanceof NativeDoubleCapability
          && hasPrimitiveType(expr.getId(), PrimitiveType.DOUBLE)) {
        return new NativeDoubleNegate(
            expr.getId(), resolved.fnName, overload, argument, implementation);
      }
      return null;
    }
    if (args.length != 2) {
      return null;
    }

    NativeArithmetic arithmetic = arithmetic(overload);
    if (arithmetic != null) {
      if (args[0] instanceof NativeIntCapability
          && args[1] instanceof NativeIntCapability
          && hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
        if (arithmetic == NativeArithmetic.ADD) {
          return new NativeIntAdd(
              expr.getId(), resolved.fnName, overload, args[0], args[1], implementation);
        }
        return new NativeIntBinary(
            expr.getId(), resolved.fnName, overload, args[0], args[1], implementation, arithmetic);
      }
      if (args[0] instanceof NativeDoubleCapability
          && args[1] instanceof NativeDoubleCapability
          && hasPrimitiveType(expr.getId(), PrimitiveType.DOUBLE)) {
        return new NativeDoubleBinary(
            expr.getId(), resolved.fnName, overload, args[0], args[1], implementation, arithmetic);
      }
      return null;
    }

    if (overload.equals(Overloads.AddString)
        && args[0] instanceof NativeStringCapability
        && args[1] instanceof NativeStringCapability
        && hasPrimitiveType(expr.getId(), PrimitiveType.STRING)) {
      return new NativeStringConcat(
          expr.getId(), resolved.fnName, overload, args[0], args[1], implementation);
    }

    NativeComparison comparison = comparison(resolved.fnName);
    NativeScalarKind kind = comparisonKind(overload);
    if (comparison != null
        && kind != null
        && supportsComparison(kind, args[0])
        && supportsComparison(kind, args[1])
        && hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return new NativeScalarComparison(
          expr.getId(),
          resolved.fnName,
          overload,
          args[0],
          args[1],
          implementation,
          kind,
          comparison);
    }
    return null;
  }

  private static NativeArithmetic arithmetic(String overload) {
    return switch (overload) {
      case Overloads.AddInt64, Overloads.AddDouble -> NativeArithmetic.ADD;
      case Overloads.SubtractInt64, Overloads.SubtractDouble -> NativeArithmetic.SUBTRACT;
      case Overloads.MultiplyInt64, Overloads.MultiplyDouble -> NativeArithmetic.MULTIPLY;
      case Overloads.DivideInt64, Overloads.DivideDouble -> NativeArithmetic.DIVIDE;
      default -> overload.equals(Overloads.ModuloInt64) ? NativeArithmetic.MODULO : null;
    };
  }

  private static NativeComparison comparison(String function) {
    if (function.equals(Operator.Less.id)) {
      return NativeComparison.LESS;
    }
    if (function.equals(Operator.LessEquals.id)) {
      return NativeComparison.LESS_EQUALS;
    }
    if (function.equals(Operator.Greater.id)) {
      return NativeComparison.GREATER;
    }
    return function.equals(Operator.GreaterEquals.id) ? NativeComparison.GREATER_EQUALS : null;
  }

  private static NativeScalarKind comparisonKind(String overload) {
    return switch (overload) {
      case Overloads.LessBool,
          Overloads.LessEqualsBool,
          Overloads.GreaterBool,
          Overloads.GreaterEqualsBool ->
          NativeScalarKind.BOOLEAN;
      case Overloads.LessInt64,
          Overloads.LessEqualsInt64,
          Overloads.GreaterInt64,
          Overloads.GreaterEqualsInt64 ->
          NativeScalarKind.INT;
      case Overloads.LessUint64,
          Overloads.LessEqualsUint64,
          Overloads.GreaterUint64,
          Overloads.GreaterEqualsUint64 ->
          NativeScalarKind.UINT;
      case Overloads.LessDouble,
          Overloads.LessEqualsDouble,
          Overloads.GreaterDouble,
          Overloads.GreaterEqualsDouble ->
          NativeScalarKind.DOUBLE;
      case Overloads.LessString,
          Overloads.LessEqualsString,
          Overloads.GreaterString,
          Overloads.GreaterEqualsString ->
          NativeScalarKind.STRING;
      default -> null;
    };
  }

  private static boolean supportsComparison(NativeScalarKind kind, Interpretable argument) {
    return switch (kind) {
      case BOOLEAN -> argument instanceof NativeBooleanCapability;
      case INT -> argument instanceof NativeIntCapability;
      case UINT -> argument instanceof NativeUintCapability;
      case DOUBLE -> argument instanceof NativeDoubleCapability;
      case STRING -> argument instanceof NativeStringCapability;
      case NULL -> false;
    };
  }

  /** planCallZero generates a zero-arity callable Interpretable. */
  static Interpretable planCallZero(Expr expr, String function, String overload, Overload impl) {
    if (impl == null || impl.function == null) {
      throw new IllegalArgumentException(String.format("no such overload: %s()", function));
    }
    return new EvalZeroArity(expr.getId(), function, overload, impl.function);
  }

  /** planCallUnary generates a unary callable Interpretable. */
  static Interpretable planCallUnary(
      Expr expr, String function, String overload, Overload impl, Interpretable[] args) {
    UnaryOp fn = null;
    Trait trait = null;
    if (impl != null) {
      if (impl.unary == null) {
        throw new IllegalStateException(String.format("no such overload: %s(arg)", function));
      }
      fn = impl.unary;
      trait = impl.operandTrait;
    }
    return new EvalUnary(expr.getId(), function, overload, args[0], trait, fn);
  }

  /** planCallBinary generates a binary callable Interpretable. */
  static Interpretable planCallBinary(
      Expr expr, String function, String overload, Overload impl, Interpretable... args) {
    BinaryOp fn = null;
    Trait trait = null;
    if (impl != null) {
      if (impl.binary == null) {
        throw new IllegalStateException(String.format("no such overload: %s(lhs, rhs)", function));
      }
      fn = impl.binary;
      trait = impl.operandTrait;
    }
    return new EvalBinary(expr.getId(), function, overload, args[0], args[1], trait, fn);
  }

  /** planCallTernary generates a ternary or variable argument callable Interpretable. */
  Interpretable planCallTernary(
      Expr expr, String function, String overload, Overload impl, Interpretable... args) {
    if (impl == null) {
      return new EvalReceiverVarArgs(expr.getId(), function, overload, args);
    }
    if (impl.ternary != null) {
      TernaryOp fn = impl.ternary;
      return new EvalTernary(
          expr.getId(), function, overload, args[0], args[1], args[2], impl.operandTrait, fn);
    }
    if (impl.function != null) {
      return new EvalVarArgs(
          expr.getId(), function, overload, args, impl.operandTrait, impl.function);
    }
    throw new IllegalStateException(String.format("no such overload: %s(...)", function));
  }

  /** planCallQuaternary generates a quaternary or variable argument callable Interpretable. */
  Interpretable planCallQuaternary(
      Expr expr, String function, String overload, Overload impl, Interpretable... args) {
    if (impl == null) {
      return new EvalReceiverVarArgs(expr.getId(), function, overload, args);
    }
    if (impl.quaternary != null) {
      QuaternaryOp fn = impl.quaternary;
      return new EvalQuaternary(
          expr.getId(),
          function,
          overload,
          args[0],
          args[1],
          args[2],
          args[3],
          impl.operandTrait,
          fn);
    }
    if (impl.function != null) {
      return new EvalVarArgs(
          expr.getId(), function, overload, args, impl.operandTrait, impl.function);
    }
    throw new IllegalStateException(String.format("no such overload: %s(...)", function));
  }

  /** planCallQuinary generates a quinary or variable argument callable Interpretable. */
  Interpretable planCallQuinary(
      Expr expr, String function, String overload, Overload impl, Interpretable... args) {
    if (impl == null) {
      return new EvalReceiverVarArgs(expr.getId(), function, overload, args);
    }
    if (impl.quinary != null) {
      QuinaryOp fn = impl.quinary;
      return new EvalQuinary(
          expr.getId(),
          function,
          overload,
          args[0],
          args[1],
          args[2],
          args[3],
          args[4],
          impl.operandTrait,
          fn);
    }
    if (impl.function != null) {
      return new EvalVarArgs(
          expr.getId(), function, overload, args, impl.operandTrait, impl.function);
    }
    throw new IllegalStateException(String.format("no such overload: %s(...)", function));
  }

  /** planCallVarArgs generates a variable argument callable Interpretable. */
  static Interpretable planCallVarArgs(
      Expr expr, String function, String overload, Overload impl, Interpretable... args) {
    if (impl == null) {
      return new EvalReceiverVarArgs(expr.getId(), function, overload, args);
    }
    FunctionOp fn;
    Trait trait;
    if (impl.function == null) {
      throw new IllegalStateException(String.format("no such overload: %s(...)", function));
    }
    fn = impl.function;
    trait = impl.operandTrait;
    return new EvalVarArgs(expr.getId(), function, overload, args, trait, fn);
  }

  /** planCallEqual generates an equals (==) Interpretable. */
  Interpretable planCallEqual(Expr expr, ResolvedFunction resolvedFunction, Interpretable... args) {
    Interpretable exactMapEquality =
        specializeExactMapEquality(expr, resolvedFunction, args, false);
    if (exactMapEquality != null) {
      return exactMapEquality;
    }
    Interpretable concatEquality =
        specializeListConcatEquality(expr, resolvedFunction, args, false);
    if (concatEquality != null) {
      return concatEquality;
    }
    Interpretable exactListEquality =
        specializeExactScalarListEquality(expr, resolvedFunction, args);
    if (exactListEquality != null) {
      return exactListEquality;
    }
    if (nativeScalarPlanning()
        && resolvedFunction.overloadId.equals(Overloads.Equals)
        && args.length == 2
        && hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      NativeScalarKind kind = equalityKind(args[0], args[1]);
      if (kind != null) {
        if (kind == NativeScalarKind.BOOLEAN) {
          return new NativeBooleanEq(expr.getId(), args[0], args[1]);
        }
        if (kind == NativeScalarKind.INT) {
          return new NativeIntEq(expr.getId(), args[0], args[1]);
        }
        return new NativeScalarEq(expr.getId(), args[0], args[1], kind);
      }
    }
    Interpretable[] established = asEstablished(args);
    return new EvalEq(expr.getId(), established[0], established[1]);
  }

  private Interpretable specializeExactScalarListEquality(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    if (!resolvedFunction.overloadId.equals(Overloads.Equals)) {
      return null;
    }
    NativeScalarKind kind = exactScalarListEqualityKind(expr, arguments);
    return kind != null
        ? new NativeExactListEquality(expr.getId(), arguments[0], arguments[1], kind, adapter)
        : null;
  }

  private Interpretable specializeExactScalarListInequality(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    if (!resolvedFunction.overloadId.equals(Overloads.NotEquals)) {
      return null;
    }
    NativeScalarKind kind = exactScalarListEqualityKind(expr, arguments);
    return kind != null
        ? new NativeExactListInequality(expr.getId(), arguments[0], arguments[1], kind, adapter)
        : null;
  }

  private NativeScalarKind exactScalarListEqualityKind(Expr expr, Interpretable[] arguments) {
    if (!nativeCertifiedHostAggregatePlanning()
        || arguments.length != 2
        || !(arguments[0] instanceof NativeListSourceCapability left)
        || !(arguments[1] instanceof NativeListSourceCapability right)
        || !left.exactListSource()
        || !right.exactListSource()
        || !hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return null;
    }
    Type leftType = typeMap.get(expr.getCallExpr().getArgs(0).getId());
    Type rightType = typeMap.get(expr.getCallExpr().getArgs(1).getId());
    if (leftType == null
        || leftType.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE
        || !leftType.equals(rightType)) {
      return null;
    }
    NativeScalarKind kind = nativeKind(leftType.getListType().getElemType());
    return kind != null && kind != NativeScalarKind.NULL ? kind : null;
  }

  /** planCallNotEqual generates a not equals (!=) Interpretable. */
  Interpretable planCallNotEqual(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable... args) {
    Interpretable exactMapInequality =
        specializeExactMapEquality(expr, resolvedFunction, args, true);
    if (exactMapInequality != null) {
      return exactMapInequality;
    }
    Interpretable concatInequality =
        specializeListConcatEquality(expr, resolvedFunction, args, true);
    if (concatInequality != null) {
      return concatInequality;
    }
    Interpretable exactListInequality =
        specializeExactScalarListInequality(expr, resolvedFunction, args);
    if (exactListInequality != null) {
      return exactListInequality;
    }
    if (nativeScalarPlanning()
        && resolvedFunction.overloadId.equals(Overloads.NotEquals)
        && args.length == 2
        && hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      NativeScalarKind kind = equalityKind(args[0], args[1]);
      if (kind != null) {
        return new NativeScalarNe(expr.getId(), args[0], args[1], kind);
      }
    }
    Interpretable[] established = asEstablished(args);
    return new EvalNe(expr.getId(), established[0], established[1]);
  }

  private Interpretable specializeExactMapEquality(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments, boolean inequality) {
    String requiredOverload = inequality ? Overloads.NotEquals : Overloads.Equals;
    Call call = expr.getCallExpr();
    if (!nativeCertifiedHostAggregatePlanning()
        || !resolvedFunction.overloadId.equals(requiredOverload)
        || !exactStandardImplementation(expr)
        || call.hasTarget()
        || call.getArgsCount() != 2
        || arguments.length != 2
        || !(arguments[0] instanceof NativeMapSourceCapability left)
        || !(arguments[1] instanceof NativeMapSourceCapability right)
        || !left.exactMapSource()
        || !right.exactMapSource()
        || !hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return null;
    }
    Type leftType = typeMap.get(call.getArgs(0).getId());
    Type rightType = typeMap.get(call.getArgs(1).getId());
    if (leftType == null
        || leftType.getTypeKindCase() != Type.TypeKindCase.MAP_TYPE
        || !leftType.equals(rightType)) {
      return null;
    }
    NativeScalarKind keyKind = nativeKind(leftType.getMapType().getKeyType());
    if (keyKind == null || keyKind == NativeScalarKind.DOUBLE || keyKind == NativeScalarKind.NULL) {
      return null;
    }
    ExactAggregateTypeAdapter exactAdapter = (ExactAggregateTypeAdapter) adapter;
    CheckedValueMaterializer keyMaterializer =
        new CheckedValueMaterializer(exactAdapter, leftType.getMapType().getKeyType());
    CheckedValueMaterializer valueMaterializer =
        new CheckedValueMaterializer(exactAdapter, leftType.getMapType().getValueType());
    return inequality
        ? new NativeExactMapInequality(
            expr.getId(), arguments[0], arguments[1], keyMaterializer, valueMaterializer)
        : new NativeExactMapEquality(
            expr.getId(), arguments[0], arguments[1], keyMaterializer, valueMaterializer);
  }

  private Interpretable specializeListConcatEquality(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments, boolean inequality) {
    String requiredOverload = inequality ? Overloads.NotEquals : Overloads.Equals;
    if (!nativeCertifiedHostAggregatePlanning()
        || !resolvedFunction.overloadId.equals(requiredOverload)
        || !exactStandardImplementation(expr)
        || arguments.length != 2
        || !hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return null;
    }
    NativeListConcatEqualityOperand left = NativeListConcatEqualityOperand.from(arguments[0]);
    NativeListConcatEqualityOperand right = NativeListConcatEqualityOperand.from(arguments[1]);
    if (left == null || right == null || (!left.concatenated() && !right.concatenated())) {
      return null;
    }
    Call call = expr.getCallExpr();
    if (call.hasTarget() || call.getArgsCount() != 2) {
      return null;
    }
    Type leftType = typeMap.get(call.getArgs(0).getId());
    Type rightType = typeMap.get(call.getArgs(1).getId());
    if (leftType == null
        || leftType.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE
        || !leftType.equals(rightType)) {
      return null;
    }
    NativeScalarKind kind = nativeKind(leftType.getListType().getElemType());
    if (kind == null || kind == NativeScalarKind.NULL) {
      return null;
    }
    return inequality
        ? new NativeListConcatInequality(expr.getId(), left, right, kind, adapter)
        : new NativeListConcatEquality(expr.getId(), left, right, kind, adapter);
  }

  /** planCallLogicalAnd generates a logical and (&&) Interpretable. */
  Interpretable planCallLogicalAnd(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable... args) {
    if (nativeScalarPlanning()
        && resolvedFunction.overloadId.equals(Overloads.LogicalAnd)
        && args.length == 2
        && args[0] instanceof NativeBooleanCapability
        && args[1] instanceof NativeBooleanCapability
        && hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return new NativeLogicalAnd(expr.getId(), args[0], args[1]);
    }
    Interpretable[] established = asEstablished(args);
    return new EvalAnd(expr.getId(), established[0], established[1]);
  }

  /** planCallLogicalOr generates a logical or (||) Interpretable. */
  Interpretable planCallLogicalOr(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable... args) {
    if (nativeScalarPlanning()
        && resolvedFunction.overloadId.equals(Overloads.LogicalOr)
        && args.length == 2
        && args[0] instanceof NativeBooleanCapability
        && args[1] instanceof NativeBooleanCapability
        && hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return new NativeLogicalOr(expr.getId(), args[0], args[1]);
    }
    Interpretable[] established = asEstablished(args);
    return new EvalOr(expr.getId(), established[0], established[1]);
  }

  /** planCallConditional generates a conditional / ternary (c ? t : f) Interpretable. */
  Interpretable planCallConditional(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable... args) {
    Interpretable cond = args[0];
    Interpretable t = args[1];
    Interpretable f = args[2];

    Interpretable establishedCond = asEstablished(cond);
    Interpretable establishedTruthy = asEstablished(t);
    Interpretable establishedFalsy = asEstablished(f);

    Attribute tAttr;
    if (establishedTruthy instanceof InterpretableAttribute truthyAttr) {
      tAttr = truthyAttr.attr();
    } else {
      tAttr = attrFactory.relativeAttribute(establishedTruthy.id(), establishedTruthy);
    }

    Attribute fAttr;
    if (establishedFalsy instanceof InterpretableAttribute falsyAttr) {
      fAttr = falsyAttr.attr();
    } else {
      fAttr = attrFactory.relativeAttribute(establishedFalsy.id(), establishedFalsy);
    }

    Attribute attribute =
        attrFactory.conditionalAttribute(expr.getId(), establishedCond, tAttr, fAttr);
    if (nativeScalarPlanning()
        && resolvedFunction.overloadId.equals(Overloads.Conditional)
        && cond instanceof NativeBooleanCapability condition) {
      if (t instanceof NativeBooleanCapability truthy
          && f instanceof NativeBooleanCapability falsy
          && hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
        return new NativeBooleanConditional(adapter, attribute, condition, truthy, falsy);
      }
      if (t instanceof NativeIntCapability truthy
          && f instanceof NativeIntCapability falsy
          && hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
        return new NativeIntConditional(adapter, attribute, condition, truthy, falsy);
      }
      if (t instanceof NativeUintCapability truthy
          && f instanceof NativeUintCapability falsy
          && hasPrimitiveType(expr.getId(), PrimitiveType.UINT64)) {
        return new NativeUintConditional(adapter, attribute, condition, truthy, falsy);
      }
      if (t instanceof NativeDoubleCapability truthy
          && f instanceof NativeDoubleCapability falsy
          && hasPrimitiveType(expr.getId(), PrimitiveType.DOUBLE)) {
        return new NativeDoubleConditional(adapter, attribute, condition, truthy, falsy);
      }
      if (t instanceof NativeStringCapability truthy
          && f instanceof NativeStringCapability falsy
          && hasPrimitiveType(expr.getId(), PrimitiveType.STRING)) {
        return new NativeStringConditional(adapter, attribute, condition, truthy, falsy);
      }
      if (t instanceof NativeNullCapability truthy
          && f instanceof NativeNullCapability falsy
          && hasNullType(expr.getId())) {
        return new NativeNullConditional(adapter, attribute, condition, truthy, falsy);
      }
    }
    return new EvalAttr(adapter, attribute);
  }

  private static NativeScalarKind equalityKind(Interpretable left, Interpretable right) {
    if (left instanceof NativeBooleanCapability && right instanceof NativeBooleanCapability) {
      return NativeScalarKind.BOOLEAN;
    }
    if (left instanceof NativeIntCapability && right instanceof NativeIntCapability) {
      return NativeScalarKind.INT;
    }
    if (left instanceof NativeUintCapability && right instanceof NativeUintCapability) {
      return NativeScalarKind.UINT;
    }
    if (left instanceof NativeDoubleCapability && right instanceof NativeDoubleCapability) {
      return NativeScalarKind.DOUBLE;
    }
    if (left instanceof NativeStringCapability && right instanceof NativeStringCapability) {
      return NativeScalarKind.STRING;
    }
    return left instanceof NativeNullCapability && right instanceof NativeNullCapability
        ? NativeScalarKind.NULL
        : null;
  }

  /**
   * planCallIndex either extends an attribute with the argument to the index operation, or creates
   * a relative attribute based on the return of a function call or operation.
   */
  Interpretable planCallIndex(Expr expr, ResolvedFunction resolvedFunction, Interpretable... args) {
    Interpretable op = args[0];
    Interpretable ind = args[1];
    InterpretableAttribute opAttr = relativeAttr(op.id(), op);
    if (opAttr == null) {
      return null;
    }
    Call call = expr.getCallExpr();
    Expr operandExpr = call.hasTarget() ? call.getTarget() : call.getArgs(0);
    Type opType = typeMap.get(operandExpr.getId());
    if (ind instanceof InterpretableConst indConst) {
      Qualifier qual = attrFactory.newQualifier(opType, expr.getId(), indConst.value());
      if (qual == null) {
        return null;
      }
      opAttr.addQualifier(qual);
      Interpretable mapIndex =
          specializeExactMapIndex(expr, resolvedFunction, opType, op, indConst, opAttr);
      if (mapIndex != opAttr) {
        return mapIndex;
      }
      Interpretable concatIndex =
          specializeListConcatIndex(expr, resolvedFunction, op, indConst, opAttr);
      if (concatIndex != opAttr) {
        return concatIndex;
      }
      if (resolvedFunction.nativeDescriptor() != null
          && resolvedFunction.overloadId.equals(Overloads.IndexMap)
          && exactStringMapResult(expr, opType)) {
        Interpretable specializedMap =
            specializeScalarAttribute(
                expr, opAttr, partialIndexAttribute(expr, operandExpr, opType, indConst.value()));
        if (specializedMap != opAttr) {
          return specializedMap;
        }
      }
      Interpretable specialized =
          specializeScalarListLiteralIndex(expr, resolvedFunction, op, indConst, opAttr);
      if (specialized != opAttr) {
        return specialized;
      }
      specialized = specializeScalarListFoldIndex(expr, resolvedFunction, op, indConst, opAttr);
      if (specialized != opAttr) {
        return specialized;
      }
      specialized =
          specializeTopLevelListIndex(
              expr, resolvedFunction, operandExpr, opType, op, indConst, opAttr);
      if (specialized != opAttr || !NativeIsland.supports(op)) {
        return specialized;
      }
      InterpretableAttribute establishedOp = relativeAttr(op.id(), asEstablished(op));
      if (establishedOp == null) {
        return null;
      }
      establishedOp.addQualifier(qual);
      return specializeDynamicListConcatIndex(expr, resolvedFunction, op, ind, establishedOp);
    }
    if (ind instanceof InterpretableAttribute indAttr) {
      Qualifier qual = attrFactory.newQualifier(opType, expr.getId(), indAttr);
      if (qual == null) {
        return null;
      }
      InterpretableAttribute establishedOp = relativeAttr(op.id(), asEstablished(op));
      if (establishedOp == null) {
        return null;
      }
      establishedOp.addQualifier(qual);
      Interpretable mapIndex =
          specializeDynamicExactMapIndex(expr, resolvedFunction, opType, op, ind, establishedOp);
      if (mapIndex != establishedOp) {
        return mapIndex;
      }
      return specializeDynamicListConcatIndex(expr, resolvedFunction, op, ind, establishedOp);
    }
    InterpretableAttribute indQual = relativeAttr(expr.getId(), asEstablished(ind));
    if (indQual == null) {
      return null;
    }
    InterpretableAttribute establishedOp = relativeAttr(op.id(), asEstablished(op));
    if (establishedOp == null) {
      return null;
    }
    establishedOp.addQualifier(indQual);
    Interpretable mapIndex =
        specializeDynamicExactMapIndex(expr, resolvedFunction, opType, op, ind, establishedOp);
    if (mapIndex != establishedOp) {
      return mapIndex;
    }
    Interpretable concatIndex =
        specializeDynamicListConcatIndex(expr, resolvedFunction, op, ind, establishedOp);
    if (concatIndex != establishedOp) {
      return concatIndex;
    }
    return specializeDynamicTopLevelListIndex(
        expr, resolvedFunction, operandExpr, opType, op, ind, establishedOp);
  }

  private Interpretable specializeListConcatIndex(
      Expr expr,
      ResolvedFunction resolvedFunction,
      Interpretable operand,
      InterpretableConst index,
      InterpretableAttribute established) {
    Call call = expr.getCallExpr();
    Expr indexExpression = call.hasTarget() ? call.getArgs(0) : call.getArgs(1);
    if (!nativeCertifiedHostAggregatePlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.overloadId.equals(Overloads.IndexList)
        || !(operand instanceof NativeListConcat concat)
        || indexExpression.getExprKindCase() != Expr.ExprKindCase.CONST_EXPR
        || indexExpression.getConstExpr().getConstantKindCase()
            != Constant.ConstantKindCase.INT64_VALUE
        || !hasPrimitiveType(indexExpression.getId(), PrimitiveType.INT64)) {
      return established;
    }
    long indexValue = index.value().intValue();
    NativeListSourceCapability[] sources = NativeListConcatKernel.collectSources(concat);
    if (sources == null) {
      return established;
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return new NativeBooleanListConcatIndex(
          expr.getId(), adapter, established.attr(), concat, sources, indexValue);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
      return new NativeIntListConcatIndex(
          expr.getId(), adapter, established.attr(), concat, sources, indexValue);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.UINT64)) {
      return new NativeUintListConcatIndex(
          expr.getId(), adapter, established.attr(), concat, sources, indexValue);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.DOUBLE)) {
      return new NativeDoubleListConcatIndex(
          expr.getId(), adapter, established.attr(), concat, sources, indexValue);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.STRING)) {
      return new NativeStringListConcatIndex(
          expr.getId(), adapter, established.attr(), concat, sources, indexValue);
    }
    if (hasNullType(expr.getId())) {
      return new NativeNullListConcatIndex(
          expr.getId(), adapter, established.attr(), concat, sources, indexValue);
    }
    return supportsMaterializedListConcatIndex(expr)
        ? new NativeValueListConcatIndex(
            expr.getId(), established.attr(), concat, sources, indexValue)
        : established;
  }

  private Interpretable specializeDynamicListConcatIndex(
      Expr expr,
      ResolvedFunction resolvedFunction,
      Interpretable operand,
      Interpretable index,
      InterpretableAttribute established) {
    Call call = expr.getCallExpr();
    Expr indexExpression = call.hasTarget() ? call.getArgs(0) : call.getArgs(1);
    if (!nativeCertifiedHostAggregatePlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.overloadId.equals(Overloads.IndexList)
        || !(operand instanceof NativeListConcat concat)
        || !(index instanceof NativeIntCapability nativeIndex)
        || !hasPrimitiveType(indexExpression.getId(), PrimitiveType.INT64)) {
      return established;
    }
    NativeListSourceCapability[] sources = NativeListConcatKernel.collectSources(concat);
    if (sources == null) {
      return established;
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return new NativeBooleanListConcatIndex(
          expr.getId(), adapter, established.attr(), concat, sources, nativeIndex);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
      return new NativeIntListConcatIndex(
          expr.getId(), adapter, established.attr(), concat, sources, nativeIndex);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.UINT64)) {
      return new NativeUintListConcatIndex(
          expr.getId(), adapter, established.attr(), concat, sources, nativeIndex);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.DOUBLE)) {
      return new NativeDoubleListConcatIndex(
          expr.getId(), adapter, established.attr(), concat, sources, nativeIndex);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.STRING)) {
      return new NativeStringListConcatIndex(
          expr.getId(), adapter, established.attr(), concat, sources, nativeIndex);
    }
    if (hasNullType(expr.getId())) {
      return new NativeNullListConcatIndex(
          expr.getId(), adapter, established.attr(), concat, sources, nativeIndex);
    }
    return supportsMaterializedListConcatIndex(expr)
        ? new NativeValueListConcatIndex(
            expr.getId(), established.attr(), concat, sources, nativeIndex)
        : established;
  }

  private boolean supportsMaterializedListConcatIndex(Expr expr) {
    Call call = expr.getCallExpr();
    Expr operandExpression = call.hasTarget() ? call.getTarget() : call.getArgs(0);
    Type listType = typeMap.get(operandExpression.getId());
    Type resultType = typeMap.get(expr.getId());
    if (listType == null
        || listType.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE
        || resultType == null
        || !listType.getListType().getElemType().equals(resultType)) {
      return false;
    }
    return switch (resultType.getTypeKindCase()) {
      case PRIMITIVE -> resultType.getPrimitive() == PrimitiveType.BYTES;
      case WRAPPER, WELL_KNOWN, MESSAGE_TYPE, DYN, LIST_TYPE, MAP_TYPE -> true;
      default -> false;
    };
  }

  private Interpretable specializeExactMapIndex(
      Expr expr,
      ResolvedFunction resolvedFunction,
      Type operandType,
      Interpretable operand,
      InterpretableConst index,
      InterpretableAttribute established) {
    if (!nativeCertifiedHostAggregatePlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.overloadId.equals(Overloads.IndexMap)
        || !(operand instanceof NativeMapSourceCapability source)
        || !source.exactMapSource()
        || operandType == null
        || operandType.getTypeKindCase() != Type.TypeKindCase.MAP_TYPE) {
      return established;
    }
    Call call = expr.getCallExpr();
    Expr indexExpression = call.hasTarget() ? call.getArgs(0) : call.getArgs(1);
    ExactMapKey key = exactMapKey(indexExpression, index, operandType.getMapType().getKeyType());
    Type resultType = typeMap.get(expr.getId());
    if (key == null
        || resultType == null
        || !resultType.equals(operandType.getMapType().getValueType())) {
      return established;
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return new NativeBooleanMapIndex(
          expr.getId(), adapter, established.attr(), source, key.hostValue, key.celValue);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
      return new NativeIntMapIndex(
          expr.getId(), adapter, established.attr(), source, key.hostValue, key.celValue);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.UINT64)) {
      return new NativeUintMapIndex(
          expr.getId(), adapter, established.attr(), source, key.hostValue, key.celValue);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.DOUBLE)) {
      return new NativeDoubleMapIndex(
          expr.getId(), adapter, established.attr(), source, key.hostValue, key.celValue);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.STRING)) {
      return new NativeStringMapIndex(
          expr.getId(), adapter, established.attr(), source, key.hostValue, key.celValue);
    }
    if (hasNullType(expr.getId())) {
      return new NativeNullMapIndex(
          expr.getId(), adapter, established.attr(), source, key.hostValue, key.celValue);
    }
    if (resultType.getTypeKindCase() == Type.TypeKindCase.LIST_TYPE
        && adapter instanceof ExactAggregateTypeAdapter exactAdapter) {
      return new NativeMapListIndex(
          expr.getId(),
          source,
          key.hostValue,
          key.celValue,
          new CheckedAggregateMaterializer(exactAdapter, resultType));
    }
    if (resultType.getTypeKindCase() == Type.TypeKindCase.MAP_TYPE
        && adapter instanceof ExactAggregateTypeAdapter exactAdapter) {
      return new NativeMapMapIndex(
          expr.getId(),
          source,
          key.hostValue,
          key.celValue,
          new CheckedAggregateMaterializer(exactAdapter, resultType));
    }
    return established;
  }

  private Interpretable specializeDynamicExactMapIndex(
      Expr expr,
      ResolvedFunction resolvedFunction,
      Type operandType,
      Interpretable operand,
      Interpretable index,
      InterpretableAttribute established) {
    if (!nativeCertifiedHostAggregatePlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.overloadId.equals(Overloads.IndexMap)
        || !(operand instanceof NativeMapSourceCapability source)
        || !source.exactMapSource()
        || operandType == null
        || operandType.getTypeKindCase() != Type.TypeKindCase.MAP_TYPE) {
      return established;
    }
    Call call = expr.getCallExpr();
    Expr indexExpression = call.hasTarget() ? call.getArgs(0) : call.getArgs(1);
    NativeMapDynamicKey dynamicKey =
        dynamicMapKey(indexExpression, index, operandType.getMapType().getKeyType());
    if (dynamicKey == null) {
      return established;
    }
    Type resultType = typeMap.get(expr.getId());
    if (resultType == null || !resultType.equals(operandType.getMapType().getValueType())) {
      return established;
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return new NativeBooleanMapIndex(
          expr.getId(), adapter, established.attr(), source, dynamicKey);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
      return new NativeIntMapIndex(expr.getId(), adapter, established.attr(), source, dynamicKey);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.UINT64)) {
      return new NativeUintMapIndex(expr.getId(), adapter, established.attr(), source, dynamicKey);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.DOUBLE)) {
      return new NativeDoubleMapIndex(
          expr.getId(), adapter, established.attr(), source, dynamicKey);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.STRING)) {
      return new NativeStringMapIndex(
          expr.getId(), adapter, established.attr(), source, dynamicKey);
    }
    return hasNullType(expr.getId())
        ? new NativeNullMapIndex(expr.getId(), adapter, established.attr(), source, dynamicKey)
        : established;
  }

  private NativeStringCapability dynamicStringKey(
      Expr expression, Interpretable plannedExpression) {
    if (!hasPrimitiveType(expression.getId(), PrimitiveType.STRING)) {
      return null;
    }
    if (plannedExpression instanceof NativeStringCapability nativeString) {
      return nativeString;
    }
    if (!(plannedExpression instanceof InterpretableAttribute attribute)
        || expression.getExprKindCase() != Expr.ExprKindCase.SELECT_EXPR) {
      return null;
    }
    Select select = expression.getSelectExpr();
    Type operandType = typeMap.get(select.getOperand().getId());
    Attribute partialAttribute = partialSelectAttribute(expression, select, operandType);
    return partialAttribute != null
        ? new NativeStringAttr(expression.getId(), adapter, attribute.attr(), partialAttribute)
        : null;
  }

  private NativeMapDynamicKey dynamicMapKey(
      Expr expression, Interpretable plannedExpression, Type declaredKeyType) {
    Type expressionType = typeMap.get(expression.getId());
    if (expressionType == null
        || !expressionType.equals(declaredKeyType)
        || declaredKeyType.getTypeKindCase() != Type.TypeKindCase.PRIMITIVE) {
      return null;
    }
    return switch (declaredKeyType.getPrimitive()) {
      case STRING -> {
        NativeStringCapability capability = dynamicStringKey(expression, plannedExpression);
        yield capability != null ? NativeMapDynamicKey.string(capability) : null;
      }
      case BOOL -> {
        NativeBooleanCapability capability = dynamicBooleanKey(expression, plannedExpression);
        yield capability != null ? NativeMapDynamicKey.bool(capability) : null;
      }
      case INT64 -> {
        NativeIntCapability capability = dynamicIntKey(expression, plannedExpression);
        yield capability != null ? NativeMapDynamicKey.integer(capability) : null;
      }
      default -> null;
    };
  }

  private NativeBooleanCapability dynamicBooleanKey(
      Expr expression, Interpretable plannedExpression) {
    if (plannedExpression instanceof NativeBooleanCapability nativeBoolean) {
      return nativeBoolean;
    }
    if (!(plannedExpression instanceof InterpretableAttribute attribute)
        || expression.getExprKindCase() != Expr.ExprKindCase.SELECT_EXPR) {
      return null;
    }
    Select select = expression.getSelectExpr();
    Type operandType = typeMap.get(select.getOperand().getId());
    Attribute partialAttribute = partialSelectAttribute(expression, select, operandType);
    return partialAttribute != null
        ? new NativeBooleanAttr(expression.getId(), adapter, attribute.attr(), partialAttribute)
        : null;
  }

  private NativeIntCapability dynamicIntKey(Expr expression, Interpretable plannedExpression) {
    if (plannedExpression instanceof NativeIntCapability nativeInt) {
      return nativeInt;
    }
    if (!(plannedExpression instanceof InterpretableAttribute attribute)
        || expression.getExprKindCase() != Expr.ExprKindCase.SELECT_EXPR) {
      return null;
    }
    Select select = expression.getSelectExpr();
    Type operandType = typeMap.get(select.getOperand().getId());
    Attribute partialAttribute = partialSelectAttribute(expression, select, operandType);
    return partialAttribute != null
        ? new NativeIntAttr(expression.getId(), adapter, attribute.attr(), partialAttribute)
        : null;
  }

  private ExactMapKey exactMapKey(Expr expression, Interpretable planned, Type declaredKeyType) {
    if (!(planned instanceof InterpretableConst constant)
        || expression.getExprKindCase() != Expr.ExprKindCase.CONST_EXPR) {
      return null;
    }
    Type keyType = typeMap.get(expression.getId());
    if (keyType == null
        || !keyType.equals(declaredKeyType)
        || keyType.getTypeKindCase() != Type.TypeKindCase.PRIMITIVE) {
      return null;
    }
    return switch (keyType.getPrimitive()) {
      case STRING ->
          expression.getConstExpr().getConstantKindCase() == Constant.ConstantKindCase.STRING_VALUE
              ? new ExactMapKey(expression.getConstExpr().getStringValue(), constant.value())
              : null;
      case BOOL ->
          expression.getConstExpr().getConstantKindCase() == Constant.ConstantKindCase.BOOL_VALUE
              ? new ExactMapKey(expression.getConstExpr().getBoolValue(), constant.value())
              : null;
      case INT64 ->
          expression.getConstExpr().getConstantKindCase() == Constant.ConstantKindCase.INT64_VALUE
              ? new ExactMapKey(expression.getConstExpr().getInt64Value(), constant.value())
              : null;
      default -> null;
    };
  }

  private record ExactMapKey(Object hostValue, Val celValue) {}

  private Interpretable specializeScalarAttribute(
      Expr expr, InterpretableAttribute attribute, Attribute partialAttribute) {
    if (partialAttribute == null) {
      return attribute;
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return new NativeBooleanAttr(expr.getId(), adapter, attribute.attr(), partialAttribute);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
      return new NativeIntAttr(expr.getId(), adapter, attribute.attr(), partialAttribute);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.UINT64)) {
      return new NativeUintAttr(expr.getId(), adapter, attribute.attr(), partialAttribute);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.DOUBLE)) {
      return new NativeDoubleAttr(expr.getId(), adapter, attribute.attr(), partialAttribute);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.STRING)) {
      return new NativeStringAttr(expr.getId(), adapter, attribute.attr(), partialAttribute);
    }
    return hasNullType(expr.getId())
        ? new NativeNullAttr(expr.getId(), adapter, attribute.attr(), partialAttribute)
        : attribute;
  }

  private Attribute partialSelectAttribute(Expr expr, Select select, Type operandType) {
    return partialQualifiedAttribute(
        expr.getId(), select.getOperand(), operandType, select.getField());
  }

  private Attribute partialRawExactSelectAttribute(Select select, Qualifier qualifier) {
    Reference reference = topLevelReference(select.getOperand());
    if (reference == null) {
      return null;
    }
    Attribute attribute =
        partialAttributeFactory()
            .absoluteAttribute(select.getOperand().getId(), reference.getName());
    attribute.addQualifier(qualifier);
    return attribute;
  }

  private Attribute partialIndexAttribute(Expr expr, Expr operand, Type operandType, Val index) {
    return partialQualifiedAttribute(expr.getId(), operand, operandType, index);
  }

  private Attribute partialQualifiedAttribute(
      long id, Expr operand, Type operandType, Object qualifierValue) {
    Reference reference = topLevelReference(operand);
    if (reference == null) {
      return null;
    }
    AttributeFactory partial = partialAttributeFactory();
    Attribute attribute = partial.absoluteAttribute(operand.getId(), reference.getName());
    Qualifier qualifier = partial.newQualifier(operandType, id, qualifierValue);
    if (qualifier == null) {
      return null;
    }
    attribute.addQualifier(qualifier);
    return attribute;
  }

  private Reference topLevelReference(Expr expression) {
    if (expression.getExprKindCase() != Expr.ExprKindCase.IDENT_EXPR) {
      return null;
    }
    Reference reference = refMap.get(expression.getId());
    if (reference == null || reference.getValue() != Reference.getDefaultInstance().getValue()) {
      return null;
    }
    Type checkedType = typeMap.get(expression.getId());
    return checkedType != null && checkedType.getType() == Type.getDefaultInstance()
        ? reference
        : null;
  }

  private AttributeFactory partialAttributeFactory() {
    if (partialAttrFactory == null) {
      partialAttrFactory = newPartialAttributeFactory(container, adapter, provider);
    }
    return partialAttrFactory;
  }

  private boolean exactStringMapResult(Expr expr, Type operandType) {
    Type resultType = typeMap.get(expr.getId());
    return resultType != null
        && operandType != null
        && operandType.getTypeKindCase() == Type.TypeKindCase.MAP_TYPE
        && operandType.getMapType().getKeyType().getPrimitive() == PrimitiveType.STRING
        && resultType.equals(operandType.getMapType().getValueType());
  }

  private Interpretable specializeTopLevelListIndex(
      Expr expr,
      ResolvedFunction resolvedFunction,
      Expr operandExpression,
      Type operandType,
      Interpretable operand,
      InterpretableConst index,
      InterpretableAttribute established) {
    Call call = expr.getCallExpr();
    Expr indexExpression = call.hasTarget() ? call.getArgs(0) : call.getArgs(1);
    Type resultType = typeMap.get(expr.getId());
    Type indexType = typeMap.get(indexExpression.getId());
    if (!nativeListTraversalPlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.fnName.equals(Operator.Index.id)
        || !resolvedFunction.overloadId.equals(Overloads.IndexList)
        || !(operand instanceof NativeListSourceCapability raw)
        || !retainedListSource(operandExpression, raw)
        || indexExpression.getExprKindCase() != Expr.ExprKindCase.CONST_EXPR
        || indexExpression.getConstExpr().getConstantKindCase()
            != Constant.ConstantKindCase.INT64_VALUE
        || indexType == null
        || indexType.getTypeKindCase() != Type.TypeKindCase.PRIMITIVE
        || indexType.getPrimitive() != PrimitiveType.INT64
        || operandType == null
        || operandType.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE
        || resultType == null
        || !resultType.equals(operandType.getListType().getElemType())) {
      return established;
    }
    long indexValue = index.value().intValue();
    if (indexValue < Integer.MIN_VALUE || indexValue > Integer.MAX_VALUE) {
      return established;
    }
    // Top-level list indexing historically resolves the raw identifier first. Under partial
    // activation that makes any matching pattern yield the identifier's expression ID, including a
    // qualified pattern. Keep that behavior rather than switching this family to qualified
    // attribute matching during the ownership migration.
    boolean directArrayAccess = raw.exactListSource() || nativePlannerOwnedAggregatePlanning();
    if (hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return new NativeBooleanListIndex(
          expr.getId(), adapter, established.attr(), null, raw, indexValue, directArrayAccess);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
      return new NativeIntListIndex(
          expr.getId(), adapter, established.attr(), null, raw, indexValue, directArrayAccess);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.UINT64)) {
      return new NativeUintListIndex(
          expr.getId(), adapter, established.attr(), null, raw, indexValue, directArrayAccess);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.DOUBLE)) {
      return new NativeDoubleListIndex(
          expr.getId(), adapter, established.attr(), null, raw, indexValue, directArrayAccess);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.STRING)) {
      return new NativeStringListIndex(
          expr.getId(), adapter, established.attr(), null, raw, indexValue, directArrayAccess);
    }
    return hasNullType(expr.getId())
        ? new NativeNullListIndex(
            expr.getId(), adapter, established.attr(), null, raw, indexValue, directArrayAccess)
        : established;
  }

  private Interpretable specializeDynamicTopLevelListIndex(
      Expr expr,
      ResolvedFunction resolvedFunction,
      Expr operandExpression,
      Type operandType,
      Interpretable operand,
      Interpretable index,
      InterpretableAttribute established) {
    Call call = expr.getCallExpr();
    Expr indexExpression = call.hasTarget() ? call.getArgs(0) : call.getArgs(1);
    Type resultType = typeMap.get(expr.getId());
    Type indexType = typeMap.get(indexExpression.getId());
    if (!nativeListTraversalPlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.fnName.equals(Operator.Index.id)
        || !resolvedFunction.overloadId.equals(Overloads.IndexList)
        || !(operand instanceof NativeListSourceCapability source)
        || !(index instanceof NativeIntCapability nativeIndex)
        || !retainedListSource(operandExpression, source)
        || indexType == null
        || indexType.getTypeKindCase() != Type.TypeKindCase.PRIMITIVE
        || indexType.getPrimitive() != PrimitiveType.INT64
        || operandType == null
        || operandType.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE
        || resultType == null
        || !resultType.equals(operandType.getListType().getElemType())) {
      return established;
    }
    boolean directArrayAccess = source.exactListSource() || nativePlannerOwnedAggregatePlanning();
    if (hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return new NativeBooleanListIndex(
          expr.getId(), adapter, established.attr(), source, nativeIndex, directArrayAccess);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
      return new NativeIntListIndex(
          expr.getId(), adapter, established.attr(), source, nativeIndex, directArrayAccess);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.UINT64)) {
      return new NativeUintListIndex(
          expr.getId(), adapter, established.attr(), source, nativeIndex, directArrayAccess);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.DOUBLE)) {
      return new NativeDoubleListIndex(
          expr.getId(), adapter, established.attr(), source, nativeIndex, directArrayAccess);
    }
    if (hasPrimitiveType(expr.getId(), PrimitiveType.STRING)) {
      return new NativeStringListIndex(
          expr.getId(), adapter, established.attr(), source, nativeIndex, directArrayAccess);
    }
    return hasNullType(expr.getId())
        ? new NativeNullListIndex(
            expr.getId(), adapter, established.attr(), source, nativeIndex, directArrayAccess)
        : established;
  }

  private Interpretable specializeScalarListLiteralIndex(
      Expr expr,
      ResolvedFunction resolvedFunction,
      Interpretable operand,
      InterpretableConst index,
      InterpretableAttribute established) {
    Call call = expr.getCallExpr();
    Expr indexExpression = call.hasTarget() ? call.getArgs(0) : call.getArgs(1);
    if (!nativePlannerOwnedAggregatePlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.fnName.equals(Operator.Index.id)
        || !resolvedFunction.overloadId.equals(Overloads.IndexList)
        || indexExpression.getExprKindCase() != Expr.ExprKindCase.CONST_EXPR
        || indexExpression.getConstExpr().getConstantKindCase()
            != Constant.ConstantKindCase.INT64_VALUE
        || !hasPrimitiveType(indexExpression.getId(), PrimitiveType.INT64)) {
      return established;
    }
    long indexValue = index.value().intValue();
    if (indexValue < Integer.MIN_VALUE || indexValue > Integer.MAX_VALUE) {
      return established;
    }
    int effectiveIndex = Math.toIntExact(indexValue);
    if (operand instanceof NativeBooleanListLiteralCapability list
        && hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return new NativeBooleanListLiteralIndex(
          expr.getId(), adapter, established.attr(), list, effectiveIndex);
    }
    if (operand instanceof NativeIntListLiteralCapability list
        && hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
      return new NativeIntListLiteralIndex(
          expr.getId(), adapter, established.attr(), list, effectiveIndex);
    }
    if (operand instanceof NativeUintListLiteralCapability list
        && hasPrimitiveType(expr.getId(), PrimitiveType.UINT64)) {
      return new NativeUintListLiteralIndex(
          expr.getId(), adapter, established.attr(), list, effectiveIndex);
    }
    if (operand instanceof NativeDoubleListLiteralCapability list
        && hasPrimitiveType(expr.getId(), PrimitiveType.DOUBLE)) {
      return new NativeDoubleListLiteralIndex(
          expr.getId(), adapter, established.attr(), list, effectiveIndex);
    }
    return operand instanceof NativeStringListLiteralCapability list
            && hasPrimitiveType(expr.getId(), PrimitiveType.STRING)
        ? new NativeStringListLiteralIndex(
            expr.getId(), adapter, established.attr(), list, effectiveIndex)
        : established;
  }

  private Interpretable specializeScalarListLiteralSize(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    if (!nativePlannerOwnedAggregatePlanning()
        || resolvedFunction.nativeDescriptor() == null
        || (!resolvedFunction.overloadId.equals(Overloads.SizeList)
            && !resolvedFunction.overloadId.equals(Overloads.SizeListInst))
        || arguments.length != 1
        || !(arguments[0] instanceof NativeScalarListLiteralCapability)
        || !hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
      return null;
    }
    return new NativeListLiteralSize(
        expr.getId(),
        resolvedFunction.fnName,
        resolvedFunction.overloadId,
        arguments[0],
        resolvedFunction.implementation);
  }

  private Interpretable specializeScalarListFoldSize(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    if (!nativeListTraversalPlanning()
        || resolvedFunction.nativeDescriptor() == null
        || (!resolvedFunction.overloadId.equals(Overloads.SizeList)
            && !resolvedFunction.overloadId.equals(Overloads.SizeListInst))
        || arguments.length != 1
        || !(arguments[0] instanceof NativeScalarListFoldCapability)
        || !hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
      return null;
    }
    return new NativeListFoldSize(
        expr.getId(),
        resolvedFunction.fnName,
        resolvedFunction.overloadId,
        arguments[0],
        resolvedFunction.implementation);
  }

  private Interpretable specializeTopLevelListSize(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    Call call = expr.getCallExpr();
    Expr operandExpression = call.hasTarget() ? call.getTarget() : call.getArgs(0);
    if (!nativeListTraversalPlanning()
        || resolvedFunction.nativeDescriptor() == null
        || (!resolvedFunction.overloadId.equals(Overloads.SizeList)
            && !resolvedFunction.overloadId.equals(Overloads.SizeListInst))
        || arguments.length != 1
        || !(arguments[0] instanceof NativeListSourceCapability source)
        || !retainedListSource(operandExpression, source)
        || !hasPrimitiveType(expr.getId(), PrimitiveType.INT64)
        || !hasListType(operandExpression.getId())) {
      return null;
    }
    return new NativeListSourceSize(
        expr.getId(),
        resolvedFunction.fnName,
        resolvedFunction.overloadId,
        arguments[0],
        resolvedFunction.implementation,
        source.exactListSource() || nativePlannerOwnedAggregatePlanning());
  }

  private Interpretable specializeScalarListFoldIndex(
      Expr expr,
      ResolvedFunction resolvedFunction,
      Interpretable operand,
      InterpretableConst index,
      InterpretableAttribute established) {
    Call call = expr.getCallExpr();
    Expr indexExpression = call.hasTarget() ? call.getArgs(0) : call.getArgs(1);
    if (!nativeListTraversalPlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.fnName.equals(Operator.Index.id)
        || !resolvedFunction.overloadId.equals(Overloads.IndexList)
        || !(operand instanceof NativeScalarListFoldCapability source)
        || indexExpression.getExprKindCase() != Expr.ExprKindCase.CONST_EXPR
        || indexExpression.getConstExpr().getConstantKindCase()
            != Constant.ConstantKindCase.INT64_VALUE
        || !hasPrimitiveType(indexExpression.getId(), PrimitiveType.INT64)
        || nativeKind(typeMap.get(expr.getId())) != source.elementKind()) {
      return established;
    }
    long indexValue = index.value().intValue();
    if (indexValue < Integer.MIN_VALUE || indexValue > Integer.MAX_VALUE) {
      return established;
    }
    int effectiveIndex = Math.toIntExact(indexValue);
    return switch (source.elementKind()) {
      case BOOLEAN ->
          new NativeBooleanListFoldIndex(
              expr.getId(), adapter, established.attr(), source, effectiveIndex);
      case INT ->
          new NativeIntListFoldIndex(
              expr.getId(), adapter, established.attr(), source, effectiveIndex);
      case UINT ->
          new NativeUintListFoldIndex(
              expr.getId(), adapter, established.attr(), source, effectiveIndex);
      case DOUBLE ->
          new NativeDoubleListFoldIndex(
              expr.getId(), adapter, established.attr(), source, effectiveIndex);
      case STRING ->
          new NativeStringListFoldIndex(
              expr.getId(), adapter, established.attr(), source, effectiveIndex);
      case NULL ->
          new NativeNullListFoldIndex(
              expr.getId(), adapter, established.attr(), source, effectiveIndex);
    };
  }

  private Interpretable specializeStringListLiteralMembership(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    Call call = expr.getCallExpr();
    if (!nativePlannerOwnedAggregatePlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.overloadId.equals(Overloads.InList)
        || call.hasTarget()
        || call.getArgsCount() != 2
        || arguments.length != 2
        || !(arguments[0] instanceof NativeStringCapability)
        || !(arguments[1] instanceof NativeStringListLiteralCapability)
        || !hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)
        || !hasPrimitiveType(call.getArgs(0).getId(), PrimitiveType.STRING)) {
      return null;
    }
    return new NativeStringListLiteralMembership(
        expr.getId(), arguments[0], arguments[1], resolvedFunction.implementation);
  }

  private Interpretable specializeScalarListConcatMembership(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    Call call = expr.getCallExpr();
    if (!nativeCertifiedHostAggregatePlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.overloadId.equals(Overloads.InList)
        || call.hasTarget()
        || call.getArgsCount() != 2
        || arguments.length != 2
        || !(arguments[1] instanceof NativeListConcat concat)
        || !hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return null;
    }
    Type needleType = typeMap.get(call.getArgs(0).getId());
    Type listType = typeMap.get(call.getArgs(1).getId());
    if (listType == null
        || listType.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE
        || needleType == null
        || !needleType.equals(listType.getListType().getElemType())) {
      return null;
    }
    NativeScalarKind kind = nativeKind(needleType);
    NativeListTraversalPlan traversal = NativeListTraversalPlan.concat(concat);
    return kind != null
            && kind != NativeScalarKind.NULL
            && supportsNativeKind(kind, arguments[0])
            && traversal != null
        ? new NativeScalarListConcatMembership(
            expr.getId(),
            arguments[0],
            concat,
            traversal,
            resolvedFunction.implementation,
            kind,
            adapter)
        : null;
  }

  private Interpretable specializeTopLevelStringListMembership(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    Call call = expr.getCallExpr();
    if (!nativeListTraversalPlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.overloadId.equals(Overloads.InList)
        || call.hasTarget()
        || call.getArgsCount() != 2
        || arguments.length != 2
        || !(arguments[0] instanceof NativeStringCapability)
        || !(arguments[1] instanceof NativeListSourceCapability source)
        || !retainedListSource(call.getArgs(1), source)
        || !hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)
        || !hasPrimitiveType(call.getArgs(0).getId(), PrimitiveType.STRING)) {
      return null;
    }
    Type listType = typeMap.get(call.getArgs(1).getId());
    if (listType == null
        || listType.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE
        || listType.getListType().getElemType().getPrimitive() != PrimitiveType.STRING) {
      return null;
    }
    return new NativeStringListMembership(
        expr.getId(),
        arguments[0],
        arguments[1],
        resolvedFunction.implementation,
        source.exactListSource() || nativePlannerOwnedAggregatePlanning());
  }

  private Interpretable specializeExactScalarSetMembership(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    Call call = expr.getCallExpr();
    if (!nativeCertifiedHostAggregatePlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.overloadId.equals(Overloads.InList)
        || call.hasTarget()
        || call.getArgsCount() != 2
        || arguments.length != 2
        || !(arguments[1] instanceof NativeListSourceCapability source)
        || !source.exactListSource()
        || !retainedListSource(call.getArgs(1), source)
        || !hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return null;
    }
    Type listType = typeMap.get(call.getArgs(1).getId());
    if (listType == null || listType.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE) {
      return null;
    }
    NativeScalarKind kind = nativeKind(listType.getListType().getElemType());
    if (kind == null
        || kind == NativeScalarKind.NULL
        || nativeKind(call.getArgs(0).getId()) != kind) {
      return null;
    }
    boolean supportedNeedle =
        switch (kind) {
          case BOOLEAN -> arguments[0] instanceof NativeBooleanCapability;
          case INT -> arguments[0] instanceof NativeIntCapability;
          case UINT -> arguments[0] instanceof NativeUintCapability;
          case DOUBLE -> arguments[0] instanceof NativeDoubleCapability;
          case STRING -> arguments[0] instanceof NativeStringCapability;
          //noinspection DataFlowIssue
          case NULL -> false;
        };
    return supportedNeedle
        ? new NativeExactSetMembership(
            expr.getId(), arguments[0], arguments[1], resolvedFunction.implementation, kind)
        : null;
  }

  private Interpretable specializeStringListFoldMembership(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable[] arguments) {
    Call call = expr.getCallExpr();
    if (!nativeListTraversalPlanning()
        || resolvedFunction.nativeDescriptor() == null
        || !resolvedFunction.overloadId.equals(Overloads.InList)
        || call.hasTarget()
        || call.getArgsCount() != 2
        || arguments.length != 2
        || !(arguments[0] instanceof NativeStringCapability)
        || !(arguments[1] instanceof NativeScalarListFoldCapability source)
        || source.elementKind() != NativeScalarKind.STRING
        || !hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)
        || !hasPrimitiveType(call.getArgs(0).getId(), PrimitiveType.STRING)) {
      return null;
    }
    Type listType = typeMap.get(call.getArgs(1).getId());
    if (listType == null
        || listType.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE
        || listType.getListType().getElemType().getPrimitive() != PrimitiveType.STRING) {
      return null;
    }
    return new NativeStringListFoldMembership(
        expr.getId(), arguments[0], arguments[1], resolvedFunction.implementation);
  }

  /** planCreateList generates a list construction Interpretable. */
  Interpretable planCreateList(Expr expr) {
    CreateList list = expr.getListExpr();
    Interpretable[] elems = new Interpretable[list.getElementsCount()];
    boolean[] optionalIndices = new boolean[list.getElementsCount()];
    for (int index : list.getOptionalIndicesList()) {
      optionalIndices[index] = true;
    }
    for (int i = 0; i < list.getElementsCount(); i++) {
      Expr elem = list.getElements(i);
      Interpretable elemVal = plan(elem);
      if (elemVal == null) {
        return null;
      }
      elems[i] = elemVal;
    }
    Interpretable folded = foldConstantList(expr, elems, optionalIndices);
    if (folded != null) {
      return folded;
    }
    Interpretable nativeLiteral = specializeScalarListLiteral(expr, elems, optionalIndices);
    return nativeLiteral != null
        ? nativeLiteral
        : new EvalList(expr.getId(), asEstablished(elems), optionalIndices, adapter);
  }

  private Interpretable foldConstantList(
      Expr expr, Interpretable[] elements, boolean[] optionalIndices) {
    if (!policy.builtInOptimizationEnabled()) {
      return null;
    }
    EvalConst folded =
        BuiltInOptimizer.foldList(
            new EvalList(expr.getId(), elements.clone(), optionalIndices.clone(), adapter));
    if (folded == null) {
      return null;
    }
    for (boolean optional : optionalIndices) {
      if (optional) {
        return folded;
      }
    }
    if (!nativePlannerOwnedAggregatePlanning() || !(folded.value() instanceof Lister list)) {
      return folded;
    }
    Type listType = typeMap.get(expr.getId());
    if (listType == null || listType.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE) {
      return folded;
    }
    NativeScalarKind kind = nativeKind(listType.getListType().getElemType());
    if (kind == null
        || kind == NativeScalarKind.NULL
        || !constantListValuesMatch(list, kind, elements.length)) {
      return folded;
    }
    return switch (kind) {
      case BOOLEAN -> new NativeConstantBooleanListLiteral(expr.getId(), list);
      case INT -> new NativeConstantIntListLiteral(expr.getId(), list);
      case UINT -> new NativeConstantUintListLiteral(expr.getId(), list);
      case DOUBLE -> new NativeConstantDoubleListLiteral(expr.getId(), list);
      case STRING -> new NativeConstantStringListLiteral(expr.getId(), list);
      //noinspection DataFlowIssue
      case NULL -> folded;
    };
  }

  private static boolean constantListValuesMatch(
      Lister list, NativeScalarKind kind, int expectedSize) {
    try {
      if (list.nativeSize() != expectedSize) {
        return false;
      }
      for (int index = 0; index < expectedSize; index++) {
        Val value = list.nativeGetAt(index);
        boolean matches =
            switch (kind) {
              case BOOLEAN -> value.getClass() == BoolT.class;
              case INT -> value.getClass() == IntT.class;
              case UINT -> value.getClass() == UintT.class;
              case DOUBLE -> value.getClass() == DoubleT.class;
              case STRING -> value.getClass() == StringT.class && value.value() instanceof String;
              case NULL -> false;
            };
        if (!matches) {
          return false;
        }
      }
      return true;
    } catch (RuntimeException failure) {
      return false;
    }
  }

  private Interpretable specializeScalarListLiteral(
      Expr expr, Interpretable[] elements, boolean[] optionalIndices) {
    if (!nativePlannerOwnedAggregatePlanning()) {
      return null;
    }
    for (boolean optional : optionalIndices) {
      if (optional) {
        return null;
      }
    }
    Type listType = typeMap.get(expr.getId());
    if (listType == null || listType.getTypeKindCase() != Type.TypeKindCase.LIST_TYPE) {
      return null;
    }
    Type elementType = listType.getListType().getElemType();
    if (elementType.getTypeKindCase() != Type.TypeKindCase.PRIMITIVE) {
      return null;
    }
    boolean constantElements = true;
    for (Expr element : expr.getListExpr().getElementsList()) {
      constantElements &= element.getExprKindCase() == Expr.ExprKindCase.CONST_EXPR;
    }
    PrimitiveType primitiveType = elementType.getPrimitive();
    if (primitiveType == PrimitiveType.BOOL && allBooleanCapabilities(elements)) {
      return new NativeBooleanListLiteral(expr.getId(), elements, constantElements, adapter);
    }
    if (primitiveType == PrimitiveType.INT64 && allIntCapabilities(elements)) {
      return new NativeIntListLiteral(expr.getId(), elements, constantElements, adapter);
    }
    if (primitiveType == PrimitiveType.UINT64 && allUintCapabilities(elements)) {
      return new NativeUintListLiteral(expr.getId(), elements, constantElements, adapter);
    }
    if (primitiveType == PrimitiveType.DOUBLE && allDoubleCapabilities(elements)) {
      return new NativeDoubleListLiteral(expr.getId(), elements, constantElements, adapter);
    }
    return primitiveType == PrimitiveType.STRING && allStringCapabilities(elements)
        ? new NativeStringListLiteral(expr.getId(), elements, constantElements, adapter)
        : null;
  }

  private static boolean allBooleanCapabilities(Interpretable[] elements) {
    for (Interpretable element : elements) {
      if (!(element instanceof NativeBooleanCapability)) {
        return false;
      }
    }
    return true;
  }

  private static boolean allIntCapabilities(Interpretable[] elements) {
    for (Interpretable element : elements) {
      if (!(element instanceof NativeIntCapability)) {
        return false;
      }
    }
    return true;
  }

  private static boolean allUintCapabilities(Interpretable[] elements) {
    for (Interpretable element : elements) {
      if (!(element instanceof NativeUintCapability)) {
        return false;
      }
    }
    return true;
  }

  private static boolean allDoubleCapabilities(Interpretable[] elements) {
    for (Interpretable element : elements) {
      if (!(element instanceof NativeDoubleCapability)) {
        return false;
      }
    }
    return true;
  }

  private static boolean allStringCapabilities(Interpretable[] elements) {
    for (Interpretable element : elements) {
      if (!(element instanceof NativeStringCapability)) {
        return false;
      }
    }
    return true;
  }

  /** planCreateStruct generates a map or object construction Interpretable. */
  Interpretable planCreateStruct(Expr expr) {
    CreateStruct str = expr.getStructExpr();
    if (!str.getMessageName().isEmpty()) {
      return planCreateObj(expr);
    }
    List<Entry> entries = str.getEntriesList();
    Interpretable[] keys = new Interpretable[entries.size()];
    Interpretable[] vals = new Interpretable[entries.size()];
    boolean[] optionalEntries = new boolean[entries.size()];
    for (int i = 0; i < entries.size(); i++) {
      Entry entry = entries.get(i);
      optionalEntries[i] = entry.getOptionalEntry();
      Interpretable keyVal = plan(entry.getMapKey());
      if (keyVal == null) {
        return null;
      }
      keys[i] = keyVal;

      Interpretable valVal = plan(entry.getValue());
      if (valVal == null) {
        return null;
      }
      vals[i] = valVal;
    }
    if (policy.builtInOptimizationEnabled()) {
      EvalConst folded =
          BuiltInOptimizer.foldMap(
              new EvalMap(
                  expr.getId(), keys.clone(), vals.clone(), optionalEntries.clone(), adapter));
      if (folded != null) {
        return folded;
      }
    }
    return new EvalMap(
        expr.getId(),
        asEstablished(keys.clone()),
        asEstablished(vals.clone()),
        optionalEntries,
        adapter);
  }

  /** planCreateObj generates an object construction Interpretable. */
  Interpretable planCreateObj(Expr expr) {
    CreateStruct obj = expr.getStructExpr();
    String typeName = resolveTypeName(obj.getMessageName());
    if (typeName == null) {
      throw new IllegalStateException(String.format("unknown type: %s", obj.getMessageName()));
    }
    List<Entry> entries = obj.getEntriesList();
    String[] fields = new String[entries.size()];
    Interpretable[] vals = new Interpretable[entries.size()];
    boolean[] optionalEntries = new boolean[entries.size()];
    for (int i = 0; i < entries.size(); i++) {
      Entry entry = entries.get(i);
      fields[i] = entry.getFieldKey();
      optionalEntries[i] = entry.getOptionalEntry();
      Interpretable val = plan(entry.getValue());
      if (val == null) {
        return null;
      }
      vals[i] = val;
    }
    return new EvalObj(
        expr.getId(), typeName, fields, asEstablished(vals), optionalEntries, provider);
  }

  /** planComprehension generates an Interpretable fold operation. */
  Interpretable planComprehension(Expr expr) {
    Comprehension fold = expr.getComprehensionExpr();
    Interpretable directQuantifier = planDirectQuantifier(expr, fold);
    if (directQuantifier != null) {
      return directQuantifier;
    }
    MacroMapFold macroMapFold = macroMapFold(fold);
    if (macroMapFold != null) {
      Interpretable iterRange = plan(fold.getIterRange());
      if (iterRange == null) {
        return null;
      }
      Interpretable filter = null;
      if (macroMapFold.filter != null) {
        filter = plan(macroMapFold.filter);
        if (filter == null) {
          return null;
        }
      }
      Interpretable transform = plan(macroMapFold.transform);
      if (transform == null) {
        return null;
      }
      return new EvalMapFold(
          expr.getId(),
          fold.getIterVar(),
          fold.getIterVar2(),
          asEstablished(iterRange),
          asEstablished(filter),
          asEstablished(transform),
          adapter);
    }

    MacroListFold macroListFold = macroListFold(fold);
    if (macroListFold != null) {
      return planMacroListFold(expr, fold, macroListFold);
    }

    Interpretable accu = plan(fold.getAccuInit());
    if (accu == null) {
      return null;
    }
    Interpretable iterRange = plan(fold.getIterRange());
    if (iterRange == null) {
      return null;
    }
    Interpretable cond = plan(fold.getLoopCondition());
    if (cond == null) {
      return null;
    }
    Interpretable step = plan(fold.getLoopStep());
    if (step == null) {
      return null;
    }
    Interpretable result = plan(fold.getResult());
    if (result == null) {
      return null;
    }
    return new EvalFold(
        expr.getId(),
        fold.getAccuVar(),
        asEstablished(accu),
        fold.getIterVar(),
        fold.getIterVar2(),
        asEstablished(iterRange),
        asEstablished(cond),
        asEstablished(step),
        asEstablished(result));
  }

  private Interpretable planMacroListFold(
      Expr expr, Comprehension fold, MacroListFold macroListFold) {
    Expr rangeExpression = fold.getIterRange();
    Interpretable range = plan(rangeExpression);
    if (range == null) {
      return null;
    }

    Type rangeType = typeMap.get(rangeExpression.getId());
    Type resultType = typeMap.get(expr.getId());
    NativeScalarKind inputKind =
        rangeType != null && rangeType.getTypeKindCase() == Type.TypeKindCase.LIST_TYPE
            ? nativeKind(rangeType.getListType().getElemType())
            : null;
    NativeScalarKind outputKind =
        resultType != null && resultType.getTypeKindCase() == Type.TypeKindCase.LIST_TYPE
            ? nativeKind(resultType.getListType().getElemType())
            : null;
    NativeListTraversalPlan traversal = retainedListTraversal(rangeExpression, range);
    boolean candidate =
        nativeListTraversalPlanning()
            && fold.getIterVar2().isEmpty()
            && traversal != null
            && macroListFoldGlueIsExact(fold)
            && inputKind != null
            && inputKind != NativeScalarKind.NULL
            && outputKind != null;

    NativeLocalVariable previousLocal = nativeLocalVariable;
    if (candidate) {
      nativeLocalVariable =
          new NativeLocalVariable(fold.getIterVar(), inputKind, nativeLocalVariable);
    }
    Interpretable filter;
    Interpretable transform;
    try {
      filter = macroListFold.filter != null ? plan(macroListFold.filter) : null;
      transform = plan(macroListFold.transform);
    } finally {
      nativeLocalVariable = previousLocal;
    }
    if ((macroListFold.filter != null && filter == null) || transform == null) {
      return null;
    }

    Interpretable establishedRange = asEstablished(range);
    Interpretable establishedFilter = asEstablished(filter);
    Interpretable establishedTransform = asEstablished(transform);
    if (candidate
        && (filter == null || filter instanceof NativeBooleanCapability)
        && supportsNativeKind(outputKind, transform)) {
      return new NativeScalarListFold(
          expr.getId(),
          fold.getIterVar(),
          establishedRange,
          traversal,
          establishedFilter,
          establishedTransform,
          inputKind,
          (NativeBooleanCapability) filter,
          transform,
          outputKind,
          adapter);
    }
    return new EvalListFold(
        expr.getId(),
        fold.getIterVar(),
        fold.getIterVar2(),
        establishedRange,
        establishedFilter,
        establishedTransform,
        adapter);
  }

  private static boolean supportsNativeKind(NativeScalarKind kind, Interpretable interpretable) {
    return switch (kind) {
      case BOOLEAN -> interpretable instanceof NativeBooleanCapability;
      case INT -> interpretable instanceof NativeIntCapability;
      case UINT -> interpretable instanceof NativeUintCapability;
      case DOUBLE -> interpretable instanceof NativeDoubleCapability;
      case STRING -> interpretable instanceof NativeStringCapability;
      case NULL -> interpretable instanceof NativeNullCapability;
    };
  }

  private Interpretable planDirectQuantifier(Expr expr, Comprehension fold) {
    if (!nativeListTraversalPlanning() || !hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
      return null;
    }
    DirectQuantifierPattern pattern = directQuantifierPattern(fold);
    if (pattern == null
        || referencesIdent(pattern.predicate, fold.getAccuVar())
        || containsComprehension(pattern.predicate)) {
      return null;
    }
    Expr rangeExpression = fold.getIterRange();
    Type rangeType = typeMap.get(rangeExpression.getId());
    if (rangeType == null) {
      return null;
    }

    boolean mapRange = rangeType.getTypeKindCase() == Type.TypeKindCase.MAP_TYPE;
    NativeScalarKind elementKind;
    NativeScalarKind valueKind = null;
    if (rangeType.getTypeKindCase() == Type.TypeKindCase.LIST_TYPE) {
      if (!fold.getIterVar2().isEmpty()) {
        return null;
      }
      elementKind = nativeKind(rangeType.getListType().getElemType());
      if (elementKind == null || elementKind == NativeScalarKind.NULL) {
        return null;
      }
    } else if (mapRange && nativeCertifiedHostAggregatePlanning()) {
      elementKind = nativeKind(rangeType.getMapType().getKeyType());
      if (elementKind == null
          || elementKind == NativeScalarKind.DOUBLE
          || elementKind == NativeScalarKind.NULL) {
        return null;
      }
      if (!fold.getIterVar2().isEmpty()) {
        valueKind = nativeKind(rangeType.getMapType().getValueType());
        if (valueKind == null) {
          return null;
        }
      }
    } else {
      return null;
    }

    Interpretable accumulatorInitial = plan(fold.getAccuInit());
    Interpretable range = plan(rangeExpression);
    if (accumulatorInitial == null || range == null) {
      return null;
    }
    if (mapRange
        && (!(range instanceof NativeMapSourceCapability source) || !source.exactMapSource())) {
      return null;
    }

    NativeLocalVariable previousLocal = nativeLocalVariable;
    nativeLocalVariable =
        new NativeLocalVariable(fold.getIterVar(), elementKind, nativeLocalVariable);
    if (mapRange && !fold.getIterVar2().isEmpty()) {
      nativeLocalVariable =
          new NativeLocalVariable(fold.getIterVar2(), valueKind, nativeLocalVariable);
    }
    Interpretable condition;
    Interpretable step;
    try {
      condition = plan(fold.getLoopCondition());
      step = plan(fold.getLoopStep());
    } finally {
      nativeLocalVariable = previousLocal;
    }
    Interpretable result = plan(fold.getResult());
    if (condition == null || step == null || result == null) {
      return null;
    }

    NativeBooleanCapability predicate = directQuantifierPredicate(pattern, step);
    Interpretable establishedAccumulator = asEstablished(accumulatorInitial);
    Interpretable establishedRange = asEstablished(range);
    Interpretable establishedCondition = asEstablished(condition);
    Interpretable establishedStep = asEstablished(step);
    Interpretable establishedResult = asEstablished(result);
    if (predicate == null || !directQuantifierGlueIsExact(pattern, fold)) {
      return new EvalFold(
          expr.getId(),
          fold.getAccuVar(),
          establishedAccumulator,
          fold.getIterVar(),
          fold.getIterVar2(),
          establishedRange,
          establishedCondition,
          establishedStep,
          establishedResult);
    }
    if (mapRange) {
      NativeMapSourceCapability source = (NativeMapSourceCapability) range;
      ExactAggregateTypeAdapter exactAdapter = (ExactAggregateTypeAdapter) adapter;
      CheckedValueMaterializer keyMaterializer =
          new CheckedValueMaterializer(exactAdapter, rangeType.getMapType().getKeyType());
      CheckedValueMaterializer valueMaterializer =
          fold.getIterVar2().isEmpty()
              ? null
              : new CheckedValueMaterializer(exactAdapter, rangeType.getMapType().getValueType());
      NativeMapTraversalPlan traversal =
          new NativeMapTraversalPlan(
              source, elementKind, valueKind, keyMaterializer, valueMaterializer);
      return new NativeMapQuantifierFold(
          expr.getId(),
          fold.getAccuVar(),
          establishedAccumulator,
          fold.getIterVar(),
          fold.getIterVar2(),
          establishedRange,
          traversal,
          establishedCondition,
          establishedStep,
          establishedResult,
          predicate,
          pattern.quantifier,
          pattern.existsOne);
    }
    if (range instanceof NativeScalarListFoldCapability mappedSource
        && mappedSource.elementKind() == NativeScalarKind.INT) {
      if (pattern.existsOne) {
        return new NativeIntMappedExistsOneFold(
            expr.getId(),
            fold.getAccuVar(),
            establishedAccumulator,
            fold.getIterVar(),
            establishedRange,
            establishedCondition,
            establishedStep,
            establishedResult,
            predicate);
      }
      return new NativeIntMappedQuantifierFold(
          expr.getId(),
          fold.getAccuVar(),
          establishedAccumulator,
          fold.getIterVar(),
          establishedRange,
          establishedCondition,
          establishedStep,
          establishedResult,
          predicate,
          pattern.quantifier);
    }
    NativeListTraversalPlan traversal = retainedListTraversal(rangeExpression, range);
    if (traversal == null) {
      return new EvalFold(
          expr.getId(),
          fold.getAccuVar(),
          establishedAccumulator,
          fold.getIterVar(),
          fold.getIterVar2(),
          establishedRange,
          establishedCondition,
          establishedStep,
          establishedResult);
    }
    if (pattern.existsOne) {
      return new NativeExistsOneFold(
          expr.getId(),
          fold.getAccuVar(),
          establishedAccumulator,
          fold.getIterVar(),
          establishedRange,
          traversal,
          establishedCondition,
          establishedStep,
          establishedResult,
          elementKind,
          predicate,
          adapter);
    }
    return new NativeQuantifierFold(
        expr.getId(),
        fold.getAccuVar(),
        establishedAccumulator,
        fold.getIterVar(),
        establishedRange,
        traversal,
        establishedCondition,
        establishedStep,
        establishedResult,
        elementKind,
        predicate,
        pattern.quantifier,
        adapter);
  }

  private static NativeBooleanCapability directQuantifierPredicate(
      DirectQuantifierPattern pattern, Interpretable step) {
    if (pattern.existsOne) {
      return step instanceof NativeIntConditional conditional ? conditional.condition : null;
    }
    if (pattern.quantifier == NativeQuantifier.ALL && step instanceof NativeLogicalAnd logical) {
      return logical.rhs instanceof NativeBooleanCapability predicate ? predicate : null;
    }
    if (pattern.quantifier == NativeQuantifier.EXISTS && step instanceof NativeLogicalOr logical) {
      return logical.rhs instanceof NativeBooleanCapability predicate ? predicate : null;
    }
    return null;
  }

  private boolean directQuantifierGlueIsExact(DirectQuantifierPattern pattern, Comprehension fold) {
    if (!exactStandardImplementation(fold.getLoopStep())) {
      return false;
    }
    if (pattern.existsOne) {
      return exactStandardImplementation(fold.getResult())
          && exactStandardImplementation(fold.getLoopStep().getCallExpr().getArgs(1));
    }
    Expr condition = fold.getLoopCondition();
    if (!exactStandardImplementation(condition)) {
      return false;
    }
    return pattern.quantifier != NativeQuantifier.EXISTS
        || exactStandardImplementation(condition.getCallExpr().getArgs(0));
  }

  private boolean macroListFoldGlueIsExact(Comprehension fold) {
    Expr step = fold.getLoopStep();
    if (isCall(step, Operator.Conditional.id, 3)) {
      if (!exactStandardImplementation(step)) {
        return false;
      }
      step = step.getCallExpr().getArgs(1);
    }
    return exactStandardImplementation(step);
  }

  private boolean exactStandardImplementation(Expr expression) {
    return StandardOverloadProvenance.isExactStandard(disp, refMap, expression);
  }

  private DirectQuantifierPattern directQuantifierPattern(Comprehension fold) {
    String accumulator = fold.getAccuVar();
    Expr condition = fold.getLoopCondition();
    Expr step = fold.getLoopStep();
    if (isIdent(fold.getResult(), accumulator)
        && isBoolConst(fold.getAccuInit(), true)
        && matchesCheckedCall(
            condition, Operator.NotStrictlyFalse.id, Overloads.NotStrictlyFalse, 1)
        && isIdent(condition.getCallExpr().getArgs(0), accumulator)
        && matchesCheckedCall(step, Operator.LogicalAnd.id, Overloads.LogicalAnd, 2)
        && isIdent(step.getCallExpr().getArgs(0), accumulator)) {
      return new DirectQuantifierPattern(
          NativeQuantifier.ALL, false, step.getCallExpr().getArgs(1));
    }
    if (isIdent(fold.getResult(), accumulator)
        && isBoolConst(fold.getAccuInit(), false)
        && matchesCheckedCall(
            condition, Operator.NotStrictlyFalse.id, Overloads.NotStrictlyFalse, 1)
        && matchesCheckedCall(
            condition.getCallExpr().getArgs(0), Operator.LogicalNot.id, Overloads.LogicalNot, 1)
        && isIdent(condition.getCallExpr().getArgs(0).getCallExpr().getArgs(0), accumulator)
        && matchesCheckedCall(step, Operator.LogicalOr.id, Overloads.LogicalOr, 2)
        && isIdent(step.getCallExpr().getArgs(0), accumulator)) {
      return new DirectQuantifierPattern(
          NativeQuantifier.EXISTS, false, step.getCallExpr().getArgs(1));
    }
    if (isNonIntConst(fold.getAccuInit(), 0)
        || !isBoolConst(condition, true)
        || !matchesCheckedCall(fold.getResult(), Operator.Equals.id, Overloads.Equals, 2)
        || !isIdent(fold.getResult().getCallExpr().getArgs(0), accumulator)
        || isNonIntConst(fold.getResult().getCallExpr().getArgs(1), 1)
        || !matchesCheckedCall(step, Operator.Conditional.id, Overloads.Conditional, 3)) {
      return null;
    }
    Call conditional = step.getCallExpr();
    Expr increment = conditional.getArgs(1);
    if (!matchesCheckedCall(increment, Operator.Add.id, Overloads.AddInt64, 2)
        || !isIdent(increment.getCallExpr().getArgs(0), accumulator)
        || isNonIntConst(increment.getCallExpr().getArgs(1), 1)
        || !isIdent(conditional.getArgs(2), accumulator)) {
      return null;
    }
    return new DirectQuantifierPattern(null, true, conditional.getArgs(0));
  }

  private boolean matchesCheckedCall(
      Expr expression, String function, String overload, int argumentCount) {
    if (!isCall(expression, function, argumentCount)) {
      return false;
    }
    Reference reference = refMap.get(expression.getId());
    return reference != null
        && reference.getOverloadIdCount() == 1
        && reference.getOverloadId(0).equals(overload);
  }

  private static MacroMapFold macroMapFold(Comprehension fold) {
    if (!isEmptyMap(fold.getAccuInit())
        || !isBoolConst(fold.getLoopCondition(), true)
        || !isIdent(fold.getResult(), fold.getAccuVar())) {
      return null;
    }

    Expr step = fold.getLoopStep();
    Expr filter = null;
    if (isCall(step, Operator.Conditional.id, 3)) {
      Call conditional = step.getCallExpr();
      if (!isIdent(conditional.getArgs(2), fold.getAccuVar())) {
        return null;
      }
      filter = conditional.getArgs(0);
      step = conditional.getArgs(1);
    }

    Expr transform = mapEntryValue(fold.getIterVar(), step);
    if (transform == null
        || referencesIdent(transform, fold.getAccuVar())
        || (filter != null && referencesIdent(filter, fold.getAccuVar()))) {
      return null;
    }
    return new MacroMapFold(filter, transform);
  }

  private MacroListFold macroListFold(Comprehension fold) {
    if (!isEmptyList(fold.getAccuInit())
        || !isBoolConst(fold.getLoopCondition(), true)
        || !isIdent(fold.getResult(), fold.getAccuVar())) {
      return null;
    }

    Expr step = fold.getLoopStep();
    Expr filter = null;
    if (matchesCheckedCall(step, Operator.Conditional.id, Overloads.Conditional, 3)) {
      Call conditional = step.getCallExpr();
      if (!isIdent(conditional.getArgs(2), fold.getAccuVar())) {
        return null;
      }
      filter = conditional.getArgs(0);
      step = conditional.getArgs(1);
    }

    Expr transform = appendedValue(fold.getAccuVar(), step);
    if (transform == null
        || referencesIdent(transform, fold.getAccuVar())
        || (filter != null && referencesIdent(filter, fold.getAccuVar()))) {
      return null;
    }
    return new MacroListFold(filter, transform);
  }

  private Expr appendedValue(String accuVar, Expr step) {
    if (!matchesCheckedCall(step, Operator.Add.id, Overloads.AddList, 2)) {
      return null;
    }
    Call add = step.getCallExpr();
    if (!isIdent(add.getArgs(0), accuVar)) {
      return null;
    }
    Expr list = add.getArgs(1);
    if (list.getExprKindCase() != Expr.ExprKindCase.LIST_EXPR
        || list.getListExpr().getElementsCount() != 1
        || list.getListExpr().getOptionalIndicesCount() != 0) {
      return null;
    }
    return list.getListExpr().getElements(0);
  }

  private boolean retainedListSource(Expr expression, NativeListSourceCapability source) {
    return topLevelReference(expression) != null
        || source instanceof NativeMapListIndex
        || source instanceof NativeExactListFieldAttr;
  }

  private NativeListTraversalPlan retainedListTraversal(Expr expression, Interpretable range) {
    if (range instanceof NativeListConcat concat) {
      return NativeListTraversalPlan.concat(concat);
    }
    if (range instanceof NativeListSourceCapability source
        && retainedListSource(expression, source)) {
      return NativeListTraversalPlan.singleSource(source);
    }
    return null;
  }

  private static boolean isCall(Expr expr, String function, int argCount) {
    return expr.getExprKindCase() == Expr.ExprKindCase.CALL_EXPR
        && expr.getCallExpr().getFunction().equals(function)
        && expr.getCallExpr().getArgsCount() == argCount
        && !expr.getCallExpr().hasTarget();
  }

  private static boolean isIdent(Expr expr, String name) {
    return expr.getExprKindCase() == Expr.ExprKindCase.IDENT_EXPR
        && expr.getIdentExpr().getName().equals(name);
  }

  private static boolean isEmptyList(Expr expr) {
    return expr.getExprKindCase() == Expr.ExprKindCase.LIST_EXPR
        && expr.getListExpr().getElementsCount() == 0
        && expr.getListExpr().getOptionalIndicesCount() == 0;
  }

  private static boolean isEmptyMap(Expr expr) {
    return expr.getExprKindCase() == Expr.ExprKindCase.STRUCT_EXPR
        && expr.getStructExpr().getMessageName().isEmpty()
        && expr.getStructExpr().getEntriesCount() == 0;
  }

  private static Expr mapEntryValue(String keyVar, Expr step) {
    if (step.getExprKindCase() != Expr.ExprKindCase.STRUCT_EXPR
        || !step.getStructExpr().getMessageName().isEmpty()
        || step.getStructExpr().getEntriesCount() != 1) {
      return null;
    }
    Entry entry = step.getStructExpr().getEntries(0);
    if (!isIdent(entry.getMapKey(), keyVar)) {
      return null;
    }
    return entry.getValue();
  }

  private static boolean isBoolConst(Expr expr, boolean value) {
    return expr.getExprKindCase() == Expr.ExprKindCase.CONST_EXPR
        && expr.getConstExpr().getConstantKindCase() == Constant.ConstantKindCase.BOOL_VALUE
        && expr.getConstExpr().getBoolValue() == value;
  }

  private static boolean isNonIntConst(Expr expr, long value) {
    return expr.getExprKindCase() != Expr.ExprKindCase.CONST_EXPR
        || expr.getConstExpr().getConstantKindCase() != Constant.ConstantKindCase.INT64_VALUE
        || expr.getConstExpr().getInt64Value() != value;
  }

  private static boolean referencesIdent(Expr expr, String name) {
    switch (expr.getExprKindCase()) {
      case IDENT_EXPR:
        return expr.getIdentExpr().getName().equals(name);
      case SELECT_EXPR:
        return referencesIdent(expr.getSelectExpr().getOperand(), name);
      case CALL_EXPR:
        Call call = expr.getCallExpr();
        if (call.hasTarget() && referencesIdent(call.getTarget(), name)) {
          return true;
        }
        for (Expr arg : call.getArgsList()) {
          if (referencesIdent(arg, name)) {
            return true;
          }
        }
        return false;
      case LIST_EXPR:
        for (Expr elem : expr.getListExpr().getElementsList()) {
          if (referencesIdent(elem, name)) {
            return true;
          }
        }
        return false;
      case STRUCT_EXPR:
        for (Entry entry : expr.getStructExpr().getEntriesList()) {
          if (referencesIdent(entry.getValue(), name)) {
            return true;
          }
        }
        return false;
      case COMPREHENSION_EXPR:
        Comprehension comprehension = expr.getComprehensionExpr();
        return referencesIdent(comprehension.getIterRange(), name)
            || referencesIdent(comprehension.getAccuInit(), name)
            || (!comprehension.getIterVar().equals(name)
                && !comprehension.getIterVar2().equals(name)
                && !comprehension.getAccuVar().equals(name)
                && referencesIdent(comprehension.getLoopCondition(), name))
            || (!comprehension.getIterVar().equals(name)
                && !comprehension.getIterVar2().equals(name)
                && !comprehension.getAccuVar().equals(name)
                && referencesIdent(comprehension.getLoopStep(), name))
            || (!comprehension.getAccuVar().equals(name)
                && referencesIdent(comprehension.getResult(), name));
      default:
        return false;
    }
  }

  private static boolean containsComprehension(Expr expr) {
    return switch (expr.getExprKindCase()) {
      case COMPREHENSION_EXPR -> true;
      case SELECT_EXPR -> containsComprehension(expr.getSelectExpr().getOperand());
      case CALL_EXPR -> {
        Expr.Call call = expr.getCallExpr();
        if (call.hasTarget() && containsComprehension(call.getTarget())) {
          yield true;
        }
        boolean found = false;
        for (Expr arg : call.getArgsList()) {
          if (containsComprehension(arg)) {
            found = true;
            break;
          }
        }
        yield found;
      }
      case LIST_EXPR ->
          expr.getListExpr().getElementsList().stream().anyMatch(Planner::containsComprehension);
      case STRUCT_EXPR ->
          expr.getStructExpr().getEntriesList().stream()
              .anyMatch(entry -> containsComprehension(entry.getValue()));
      case IDENT_EXPR, CONST_EXPR, EXPRKIND_NOT_SET -> false;
    };
  }

  private record MacroListFold(Expr filter, Expr transform) {}

  private record MacroMapFold(Expr filter, Expr transform) {}

  /** planConst generates a constant valued Interpretable. */
  Interpretable planConst(Expr expr) {
    Val val = constValue(expr.getConstExpr());
    if (val == null) {
      return null;
    }
    if (nativeScalarPlanning()) {
      if (hasPrimitiveType(expr.getId(), PrimitiveType.INT64)) {
        return new NativeIntConst(expr.getId(), val.intValue());
      }
      if (hasPrimitiveType(expr.getId(), PrimitiveType.UINT64)) {
        return new NativeUintConst(expr.getId(), val.intValue());
      }
      if (hasPrimitiveType(expr.getId(), PrimitiveType.BOOL)) {
        return new NativeBooleanConst(expr.getId(), val.booleanValue());
      }
      if (hasPrimitiveType(expr.getId(), PrimitiveType.DOUBLE)) {
        return new NativeDoubleConst(expr.getId(), val.doubleValue());
      }
      if (hasPrimitiveType(expr.getId(), PrimitiveType.STRING)) {
        return new NativeStringConst(expr.getId(), (String) val.value());
      }
      if (hasNullType(expr.getId())) {
        return new NativeNullConst(expr.getId());
      }
    }
    return newConstValue(expr.getId(), val);
  }

  /** constValue converts a proto Constant value to a ref.Val. */
  @SuppressWarnings("deprecation")
  static Val constValue(Constant c) {
    return switch (c.getConstantKindCase()) {
      case BOOL_VALUE -> boolOf(c.getBoolValue());
      case BYTES_VALUE -> bytesOf(c.getBytesValue());
      case DOUBLE_VALUE -> doubleOf(c.getDoubleValue());
      case DURATION_VALUE -> durationOf(c.getDurationValue());
      case INT64_VALUE -> intOf(c.getInt64Value());
      case NULL_VALUE -> NullT.NullValue;
      case STRING_VALUE -> stringOf(c.getStringValue());
      case TIMESTAMP_VALUE -> timestampOf(c.getTimestampValue());
      case UINT64_VALUE -> uintOf(c.getUint64Value());
      default ->
          throw new IllegalArgumentException(
              String.format(
                  "unknown constant type: '%s' of kind '%s'", c, c.getConstantKindCase()));
    };
  }

  /**
   * resolveTypeName takes a qualified string constructed at parse time, applies the proto namespace
   * resolution rules to it in a scan over possible matching types in the TypeProvider.
   */
  String resolveTypeName(String typeName) {
    for (String qualifiedTypeName : container.resolveCandidateNames(typeName)) {
      if (provider.findType(qualifiedTypeName) != null) {
        return qualifiedTypeName;
      }
    }
    return null;
  }

  static class ResolvedFunction {
    final Expr target;
    final String fnName;
    final String overloadId;
    final Overload implementation;

    ResolvedFunction(Expr target, String fnName, String overloadId, Overload implementation) {
      this.target = target;
      this.fnName = fnName;
      this.overloadId = overloadId;
      this.implementation = implementation;
    }

    NativeOverloadDescriptor nativeDescriptor() {
      return null;
    }
  }

  static final class NativeResolvedFunction extends ResolvedFunction {
    private final NativeOverloadDescriptor nativeDescriptor;

    NativeResolvedFunction(
        Expr target,
        String fnName,
        String overloadId,
        Overload implementation,
        NativeOverloadDescriptor nativeDescriptor) {
      super(target, fnName, overloadId, implementation);
      this.nativeDescriptor = nativeDescriptor;
    }

    @Override
    NativeOverloadDescriptor nativeDescriptor() {
      return nativeDescriptor;
    }
  }

  /**
   * resolveFunction determines the call target, function name, and overload name from a given Expr
   * value.
   *
   * <p>The resolveFunction resolves ambiguities where a function may either be a receiver-style
   * invocation or a qualified global function name.
   *
   * <ul>
   *   <li>The target expression may only consist of ident and select expressions.
   *   <li>The function is declared in the environment using its fully-qualified name.
   *   <li>The fully-qualified function name matches the string serialized target value.
   * </ul>
   */
  ResolvedFunction resolveFunction(Expr expr) {
    // Note: similar logic exists within the `checker/checker.go`. If making changes here
    // please consider the impact on checker.go and consolidate implementations or mirror code
    // as appropriate.
    Call call = expr.getCallExpr();
    Expr target = call.hasTarget() ? call.getTarget() : null;
    String fnName = call.getFunction();

    // Checked expressions always have a reference map entry, and _should_ have the fully
    // qualified
    // function name as the fnName value.
    Reference oRef = refMap.get(expr.getId());
    if (oRef != null) {
      if (oRef.getOverloadIdCount() == 1) {
        return resolvedFunction(target, fnName, oRef.getOverloadId(0));
      }
      // Note, this namespaced function name will not appear as a fully qualified name in ASTs
      // built and stored before cel-go v0.5.0; however, this functionality did not work at all
      // before the v0.5.0 release.
      return resolvedFunction(target, fnName, "");
    }

    // Parse-only expressions need to handle the same logic as is normally performed at check
    // time,
    // but with potentially much less information. The only reliable source of information about
    // which functions are configured is the dispatcher.
    if (target == null) {
      // If the user has a parse-only expression, then it should have been configured as such in
      // the interpreter dispatcher as it may have been omitted from the checker environment.
      for (String qualifiedName : container.resolveCandidateNames(fnName)) {
        if (disp.findOverload(qualifiedName) != null) {
          return resolvedFunction(null, qualifiedName, "");
        }
      }
      // It's possible that the overload was not found, but this situation is accounted for in
      // the planCall phase; however, the leading dot used for denoting fully-qualified
      // namespaced identifiers must be stripped, as all declarations already use fully-qualified
      // names. This stripping behavior is handled automatically by the ResolveCandidateNames
      // call.
      return resolvedFunction(null, stripLeadingDot(fnName), "");
    }

    // Handle the situation where the function target actually indicates a qualified function
    // name.
    String qualifiedPrefix = toQualifiedName(target);
    if (qualifiedPrefix != null) {
      String maybeQualifiedName = qualifiedPrefix + "." + fnName;
      for (String qualifiedName : container.resolveCandidateNames(maybeQualifiedName)) {
        if (disp.findOverload(qualifiedName) != null) {
          // Clear the target to ensure the proper arity is used for finding the
          // implementation.
          return resolvedFunction(null, qualifiedName, "");
        }
      }
    }
    // In the default case, the function is exactly as it was advertised: a receiver call on with
    // an expression-based target with the given simple function name.
    return resolvedFunction(target, fnName, "");
  }

  private ResolvedFunction resolvedFunction(Expr target, String function, String overloadId) {
    Overload implementation = null;
    if (overloadId != null && !overloadId.isEmpty()) {
      implementation = disp.findOverload(overloadId);
    }
    if (implementation == null) {
      implementation = disp.findOverload(function);
    }
    NativeOverloadDescriptor nativeDescriptor =
        policy.nativeSpecializationPermitted()
            ? StandardNativeOverloadCatalog.find(implementation, function, overloadId)
            : null;
    return nativeDescriptor != null
        ? new NativeResolvedFunction(target, function, overloadId, implementation, nativeDescriptor)
        : new ResolvedFunction(target, function, overloadId, implementation);
  }

  private Interpretable[] asEstablished(Interpretable[] arguments) {
    if (!policy.nativeSpecializationPermitted()) {
      return arguments;
    }
    for (int i = 0; i < arguments.length; i++) {
      Interpretable argument = arguments[i];
      if (NativeIsland.supports(argument)
          && !(policy.builtInOptimizationEnabled() && argument instanceof InterpretableConst)) {
        arguments[i] = new NativeIsland(argument, adapter);
      }
    }
    return arguments;
  }

  private Interpretable asEstablished(Interpretable argument) {
    if (policy.nativeSpecializationPermitted()
        && NativeIsland.supports(argument)
        && !(policy.builtInOptimizationEnabled() && argument instanceof InterpretableConst)) {
      return new NativeIsland(argument, adapter);
    }
    return argument;
  }

  private boolean nativeScalarPlanning() {
    return policy.nativeSpecializationPermitted() && adapter instanceof StandardScalarTypeAdapter;
  }

  private boolean nativePlannerOwnedAggregatePlanning() {
    return nativeScalarPlanning()
        && (adapter == DefaultTypeAdapter.Instance || adapter instanceof ProtoTypeRegistry);
  }

  private boolean nativeCertifiedHostAggregatePlanning() {
    return nativeScalarPlanning() && adapter instanceof ExactAggregateTypeAdapter;
  }

  private boolean nativeListTraversalPlanning() {
    return nativePlannerOwnedAggregatePlanning() || nativeCertifiedHostAggregatePlanning();
  }

  private NativeScalarKind nativeKind(long id) {
    return nativeKind(typeMap.get(id));
  }

  private static NativeScalarKind nativeKind(Type type) {
    if (type == null) {
      return null;
    }
    if (Decls.Null.equals(type)) {
      return NativeScalarKind.NULL;
    }
    if (type.getTypeKindCase() != Type.TypeKindCase.PRIMITIVE) {
      return null;
    }
    return switch (type.getPrimitive()) {
      case BOOL -> NativeScalarKind.BOOLEAN;
      case INT64 -> NativeScalarKind.INT;
      case UINT64 -> NativeScalarKind.UINT;
      case DOUBLE -> NativeScalarKind.DOUBLE;
      case STRING -> NativeScalarKind.STRING;
      default -> null;
    };
  }

  private boolean hasPrimitiveType(long id, PrimitiveType primitiveType) {
    Type type = typeMap.get(id);
    return type != null
        && type.getTypeKindCase() == Type.TypeKindCase.PRIMITIVE
        && type.getPrimitive() == primitiveType;
  }

  private boolean hasListType(long id) {
    Type type = typeMap.get(id);
    return type != null && type.getTypeKindCase() == Type.TypeKindCase.LIST_TYPE;
  }

  private static boolean isAggregateType(Type type) {
    if (type == null) {
      return false;
    }
    return type.getTypeKindCase() == Type.TypeKindCase.LIST_TYPE
        || type.getTypeKindCase() == Type.TypeKindCase.MAP_TYPE;
  }

  private boolean hasNullType(long id) {
    return org.projectnessie.cel.checker.Decls.Null.equals(typeMap.get(id));
  }

  InterpretableAttribute relativeAttr(long id, Interpretable eval) {
    InterpretableAttribute eAttr;
    if (eval instanceof InterpretableAttribute) {
      eAttr = (InterpretableAttribute) eval;
    } else {
      eAttr = new EvalAttr(adapter, attrFactory.relativeAttribute(id, eval));
    }
    Interpretable decAttr = decorate(eAttr);
    if (decAttr == null) {
      return null;
    }
    if (!(decAttr instanceof InterpretableAttribute)) {
      throw new IllegalStateException(
          String.format(
              "invalid attribute decoration: %s(%s)", decAttr, decAttr.getClass().getName()));
    }
    eAttr = (InterpretableAttribute) decAttr;
    return eAttr;
  }

  /**
   * toQualifiedName converts an expression AST into a qualified name if possible, with a boolean
   * 'found' value that indicates if the conversion is successful.
   */
  String toQualifiedName(Expr operand) {
    // If the checker identified the expression as an attribute by the type-checker, then it can't
    // possibly be part of qualified name in a namespace.
    if (refMap.containsKey(operand.getId())) {
      return "";
    }
    // Since functions cannot be both namespaced and receiver functions, if the operand is not an
    // qualified variable name, return the (possibly) qualified name given the expressions.
    return Container.toQualifiedName(operand);
  }

  String stripLeadingDot(String name) {
    return name.startsWith(".") ? name.substring(1) : name;
  }

  private NativeLocalVariable nativeLocalVariable(String name) {
    if (name.startsWith(".")) {
      return null;
    }
    for (NativeLocalVariable local = nativeLocalVariable; local != null; local = local.parent) {
      if (local.name.equals(name)) {
        return local;
      }
    }
    return null;
  }

  private record NativeLocalVariable(
      String name, NativeScalarKind kind, NativeLocalVariable parent) {}

  private record DirectQuantifierPattern(
      NativeQuantifier quantifier, boolean existsOne, Expr predicate) {}
}
