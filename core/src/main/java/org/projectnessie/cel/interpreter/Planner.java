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
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.StandardScalarFieldProvider;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;
import org.projectnessie.cel.common.types.ref.Val;
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
      if (nativeScalarPlanning()) {
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
    Qualifier qual =
        fieldType == null
            ? attrFactory.newQualifier(opType, expr.getId(), sel.getField())
            : new FieldQualifier(expr.getId(), sel.getField(), fieldType, adapter);
    if (qual == null) {
      return null;
    }
    // Lastly, create a field selection Interpretable.
    if (op instanceof InterpretableAttribute attr) {
      attr.addQualifier(qual);
      return specializeScalarSelector(
          expr, opType, fieldType, attr, partialSelectAttribute(expr, sel, opType));
    }

    InterpretableAttribute relAttr = relativeAttr(op.id(), asEstablished(op));
    if (relAttr == null) {
      return null;
    }
    relAttr.addQualifier(qual);
    return specializeScalarSelector(
        expr, opType, fieldType, relAttr, partialSelectAttribute(expr, sel, opType));
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
    if (resolvedFunc.fnName.equals(Operator.Index.id))
      return planCallIndex(expr, resolvedFunc, args);
    if ("optional_or".equals(resolvedFunc.overloadId)
        || (resolvedFunc.overloadId.isEmpty()
            && "or".equals(resolvedFunc.fnName)
            && disp.findOverload("optional_or") != null)) {
      Interpretable[] established = asEstablished(args);
      return new EvalOptionalOr(
          expr.getId(),
          resolvedFunc.fnName,
          resolvedFunc.overloadId,
          established[0],
          established[1],
          false);
    }
    if ("optional_or_value".equals(resolvedFunc.overloadId)
        || (resolvedFunc.overloadId.isEmpty()
            && "orValue".equals(resolvedFunc.fnName)
            && disp.findOverload("optional_or_value") != null)) {
      Interpretable[] established = asEstablished(args);
      return new EvalOptionalOr(
          expr.getId(),
          resolvedFunc.fnName,
          resolvedFunc.overloadId,
          established[0],
          established[1],
          true);
    }
    if (resolvedFunc.fnName.equals(Operator.In.id)) {
      Interpretable constantSetMembership = foldConstantSetMembership(expr, resolvedFunc, args);
      if (constantSetMembership != null) {
        return constantSetMembership;
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

  /** planCallNotEqual generates a not equals (!=) Interpretable. */
  Interpretable planCallNotEqual(
      Expr expr, ResolvedFunction resolvedFunction, Interpretable... args) {
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
    Interpretable op = asEstablished(args[0]);
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
      if (ind instanceof InterpretableConst indConst2
          && resolvedFunction.nativeDescriptor() != null
          && resolvedFunction.overloadId.equals(Overloads.IndexMap)
          && exactStringMapResult(expr, opType)) {
        return specializeScalarAttribute(
            expr, opAttr, partialIndexAttribute(expr, operandExpr, opType, indConst2.value()));
      }
      return opAttr;
    }
    if (ind instanceof InterpretableAttribute indAttr) {
      Qualifier qual = attrFactory.newQualifier(opType, expr.getId(), indAttr);
      if (qual == null) {
        return null;
      }
      opAttr.addQualifier(qual);
      return opAttr;
    }
    InterpretableAttribute indQual = relativeAttr(expr.getId(), ind);
    if (indQual == null) {
      return null;
    }
    opAttr.addQualifier(indQual);
    return opAttr;
  }

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
    return new EvalList(expr.getId(), asEstablished(elems), optionalIndices, adapter);
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
    return folded;
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
      Interpretable iterRange = plan(fold.getIterRange());
      if (iterRange == null) {
        return null;
      }
      Interpretable filter = null;
      if (macroListFold.filter != null) {
        filter = plan(macroListFold.filter);
        if (filter == null) {
          return null;
        }
      }
      Interpretable transform = plan(macroListFold.transform);
      if (transform == null) {
        return null;
      }
      return new EvalListFold(
          expr.getId(),
          fold.getIterVar(),
          fold.getIterVar2(),
          asEstablished(iterRange),
          asEstablished(filter),
          asEstablished(transform),
          adapter);
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

  private static MacroListFold macroListFold(Comprehension fold) {
    if (!isEmptyList(fold.getAccuInit())
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

    Expr transform = appendedValue(fold.getAccuVar(), step);
    if (transform == null
        || referencesIdent(transform, fold.getAccuVar())
        || (filter != null && referencesIdent(filter, fold.getAccuVar()))) {
      return null;
    }
    return new MacroListFold(filter, transform);
  }

  private static Expr appendedValue(String accuVar, Expr step) {
    if (!isCall(step, Operator.Add.id, 2)) {
      return null;
    }
    Call add = step.getCallExpr();
    if (!isIdent(add.getArgs(0), accuVar)) {
      return null;
    }
    Expr list = add.getArgs(1);
    if (list.getExprKindCase() != Expr.ExprKindCase.LIST_EXPR
        || list.getListExpr().getElementsCount() != 1) {
      return null;
    }
    return list.getListExpr().getElements(0);
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
        && expr.getListExpr().getElementsCount() == 0;
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

  private static final class MacroListFold {
    final Expr filter;
    final Expr transform;

    private MacroListFold(Expr filter, Expr transform) {
      this.filter = filter;
      this.transform = transform;
    }
  }

  private static final class MacroMapFold {
    final Expr filter;
    final Expr transform;

    private MacroMapFold(Expr filter, Expr transform) {
      this.filter = filter;
      this.transform = transform;
    }
  }

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

  private static boolean isBoolConst(Expr expr, boolean value) {
    return expr.getExprKindCase() == Expr.ExprKindCase.CONST_EXPR
        && expr.getConstExpr().getConstantKindCase() == Constant.ConstantKindCase.BOOL_VALUE
        && expr.getConstExpr().getBoolValue() == value;
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
}
