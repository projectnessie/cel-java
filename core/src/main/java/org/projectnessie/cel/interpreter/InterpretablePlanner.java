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

import static org.projectnessie.cel.common.types.BoolT.boolOf;
import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.DurationT.durationOf;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.interpreter.Interpretable.newConstValue;

import com.google.api.expr.v1alpha1.CheckedExpr;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Trait;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.AttributeFactory.Qualifier;
import org.projectnessie.cel.interpreter.Interpretable.EvalAnd;
import org.projectnessie.cel.interpreter.Interpretable.EvalAttr;
import org.projectnessie.cel.interpreter.Interpretable.EvalBinary;
import org.projectnessie.cel.interpreter.Interpretable.EvalEq;
import org.projectnessie.cel.interpreter.Interpretable.EvalFold;
import org.projectnessie.cel.interpreter.Interpretable.EvalList;
import org.projectnessie.cel.interpreter.Interpretable.EvalMap;
import org.projectnessie.cel.interpreter.Interpretable.EvalNe;
import org.projectnessie.cel.interpreter.Interpretable.EvalObj;
import org.projectnessie.cel.interpreter.Interpretable.EvalOr;
import org.projectnessie.cel.interpreter.Interpretable.EvalTestOnly;
import org.projectnessie.cel.interpreter.Interpretable.EvalUnary;
import org.projectnessie.cel.interpreter.Interpretable.EvalVarArgs;
import org.projectnessie.cel.interpreter.Interpretable.EvalZeroArity;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableAttribute;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableConst;
import org.projectnessie.cel.interpreter.functions.BinaryOp;
import org.projectnessie.cel.interpreter.functions.FunctionOp;
import org.projectnessie.cel.interpreter.functions.Overload;
import org.projectnessie.cel.interpreter.functions.UnaryOp;

/** interpretablePlanner creates an Interpretable evaluation plan from a proto Expr value. */
public interface InterpretablePlanner {
  /** Plan generates an Interpretable value (or error) from the input proto Expr. */
  Interpretable plan(Expr expr);

  /**
   * newPlanner creates an interpretablePlanner which references a Dispatcher, TypeProvider,
   * TypeAdapter, Container, and CheckedExpr value. These pieces of data are used to resolve
   * functions, types, and namespaced identifiers at plan time rather than at runtime since it only
   * needs to be done once and may be semi-expensive to compute.
   */
  static InterpretablePlanner newPlanner(
      Dispatcher disp,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      Container cont,
      CheckedExpr checked,
      InterpretableDecorator... decorators) {
    return new Planner(
        disp,
        provider,
        adapter,
        attrFactory,
        cont,
        checked.getReferenceMapMap(),
        checked.getTypeMapMap(),
        decorators);
  }

  /**
   * newUncheckedPlanner creates an interpretablePlanner which references a Dispatcher,
   * TypeProvider, TypeAdapter, and Container to resolve functions and types at plan time.
   * Namespaces present in Select expressions are resolved lazily at evaluation time.
   */
  static InterpretablePlanner newUncheckedPlanner(
      Dispatcher disp,
      TypeProvider provider,
      TypeAdapter adapter,
      AttributeFactory attrFactory,
      Container cont,
      InterpretableDecorator... decorators) {
    return new Planner(
        disp, provider, adapter, attrFactory, cont, new HashMap<>(), new HashMap<>(), decorators);
  }

  /** planner is an implementatio of the interpretablePlanner interface. */
  class Planner implements InterpretablePlanner {
    private final Dispatcher disp;
    private final TypeProvider provider;
    private final TypeAdapter adapter;
    private final AttributeFactory attrFactory;
    private final Container container;
    private final Map<Long, Reference> refMap;
    private final Map<Long, Type> typeMap;
    private final InterpretableDecorator[] decorators;

    Planner(
        Dispatcher disp,
        TypeProvider provider,
        TypeAdapter adapter,
        AttributeFactory attrFactory,
        Container container,
        Map<Long, Reference> refMap,
        Map<Long, Type> typeMap,
        InterpretableDecorator[] decorators) {
      this.disp = disp;
      this.provider = provider;
      this.adapter = adapter;
      this.attrFactory = attrFactory;
      this.container = container;
      this.refMap = refMap;
      this.typeMap = typeMap;
      this.decorators = decorators;
    }

    /**
     * Plan implements the interpretablePlanner interface. This implementation of the Plan method
     * also applies decorators to each Interpretable generated as part of the overall plan.
     * Decorators are useful for layering functionality into the evaluation that is not natively
     * understood by CEL, such as state-tracking, expression re-write, and possibly efficient
     * thread-safe memoization of repeated expressions.
     */
    @Override
    public Interpretable plan(Expr expr) {
      switch (expr.getExprKindCase()) {
        case CALL_EXPR:
          return decorate(planCall(expr));
        case IDENT_EXPR:
          return decorate(planIdent(expr));
        case SELECT_EXPR:
          return decorate(planSelect(expr));
        case LIST_EXPR:
          return decorate(planCreateList(expr));
        case STRUCT_EXPR:
          return decorate(planCreateStruct(expr));
        case COMPREHENSION_EXPR:
          return decorate(planComprehension(expr));
        case CONST_EXPR:
          return decorate(planConst(expr));
      }
      throw new IllegalArgumentException(
          String.format("unsupported expr of kind %s: '%s'", expr.getExprKindCase(), expr));
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
      // Plan a constant reference if this is the case for this simple identifier.
      if (identRef.getValue() != Reference.getDefaultInstance().getValue()) {
        return plan(Expr.newBuilder().setId(id).setConstExpr(identRef.getValue()).build());
      }

      // Check to see whether the type map indicates this is a type name. All types should be
      // registered with the provider.
      Type cType = typeMap.get(id);
      if (cType != null && cType.getType() != Type.getDefaultInstance()) {
        Val cVal = provider.findIdent(identRef.getName());
        if (cVal == null) {
          throw new IllegalStateException(
              String.format("reference to undefined type: %s", identRef.getName()));
        }
        return newConstValue(id, cVal);
      }

      // Otherwise, return the attribute for the resolved identifier name.
      return new EvalAttr(adapter, attrFactory.absoluteAttribute(id, identRef.getName()));
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
      Interpretable op = plan(sel.getOperand());

      // Determine the field type if this is a proto message type.
      FieldType fieldType = null;
      Type opType = typeMap.get(sel.getOperand().getId());
      if (opType != null && !opType.getMessageType().isEmpty()) {
        FieldType ft = provider.findFieldType(opType.getMessageType(), sel.getField());
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
        return new EvalTestOnly(expr.getId(), op, stringOf(sel.getField()), fieldType);
      }
      // Build a qualifier.
      Qualifier qual = attrFactory.newQualifier(opType, expr.getId(), sel.getField());
      if (qual == null) {
        return null;
      }
      // Lastly, create a field selection Interpretable.
      if (op instanceof InterpretableAttribute) {
        InterpretableAttribute attr = (InterpretableAttribute) op;
        attr.addQualifier(qual);
        return attr;
      }

      InterpretableAttribute relAttr = relativeAttr(op.id(), op);
      if (relAttr == null) {
        return null;
      }
      relAttr.addQualifier(qual);
      return relAttr;
    }

    /**
     * planCall creates a callable Interpretable while specializing for common functions and
     * invocation patterns. Specifically, conditional operators &&, ||, ?:, and (in)equality
     * functions result in optimized Interpretable values.
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

      // Generate specialized Interpretable operators by function name if possible.
      if (resolvedFunc.fnName.equals(Operator.LogicalAnd.id)) return planCallLogicalAnd(expr, args);
      if (resolvedFunc.fnName.equals(Operator.LogicalOr.id)) return planCallLogicalOr(expr, args);
      if (resolvedFunc.fnName.equals(Operator.Conditional.id))
        return planCallConditional(expr, args);
      if (resolvedFunc.fnName.equals(Operator.Equals.id)) return planCallEqual(expr, args);
      if (resolvedFunc.fnName.equals(Operator.NotEquals.id)) return planCallNotEqual(expr, args);
      if (resolvedFunc.fnName.equals(Operator.Index.id)) return planCallIndex(expr, args);

      // Otherwise, generate Interpretable calls specialized by argument count.
      // Try to find the specific function by overload id.
      Overload fnDef = null;
      if (resolvedFunc.overloadId != null && resolvedFunc.overloadId.isEmpty()) {
        fnDef = disp.findOverload(resolvedFunc.overloadId);
      }
      // If the overload id couldn't resolve the function, try the simple function name.
      if (fnDef == null) {
        fnDef = disp.findOverload(resolvedFunc.fnName);
      }
      switch (argCount) {
        case 0:
          return planCallZero(expr, resolvedFunc.fnName, resolvedFunc.overloadId, fnDef);
        case 1:
          return planCallUnary(expr, resolvedFunc.fnName, resolvedFunc.overloadId, fnDef, args);
        case 2:
          return planCallBinary(expr, resolvedFunc.fnName, resolvedFunc.overloadId, fnDef, args);
        default:
          return planCallVarArgs(expr, resolvedFunc.fnName, resolvedFunc.overloadId, fnDef, args);
      }
    }

    /** planCallZero generates a zero-arity callable Interpretable. */
    Interpretable planCallZero(Expr expr, String function, String overload, Overload impl) {
      if (impl == null || impl.function == null) {
        throw new IllegalArgumentException(String.format("no such overload: %s()", function));
      }
      return new EvalZeroArity(expr.getId(), function, overload, impl.function);
    }

    /** planCallUnary generates a unary callable Interpretable. */
    Interpretable planCallUnary(
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
    Interpretable planCallBinary(
        Expr expr, String function, String overload, Overload impl, Interpretable... args) {
      BinaryOp fn = null;
      Trait trait = null;
      if (impl != null) {
        if (impl.binary == null) {
          throw new IllegalStateException(
              String.format("no such overload: %s(lhs, rhs)", function));
        }
        fn = impl.binary;
        trait = impl.operandTrait;
      }
      return new EvalBinary(expr.getId(), function, overload, args[0], args[1], trait, fn);
    }

    /** planCallVarArgs generates a variable argument callable Interpretable. */
    Interpretable planCallVarArgs(
        Expr expr, String function, String overload, Overload impl, Interpretable... args) {
      FunctionOp fn = null;
      Trait trait = null;
      if (impl != null) {
        if (impl.function == null) {
          throw new IllegalStateException(String.format("no such overload: %s(...)", function));
        }
        fn = impl.function;
        trait = impl.operandTrait;
      }
      return new EvalVarArgs(expr.getId(), function, overload, args, trait, fn);
    }

    /** planCallEqual generates an equals (==) Interpretable. */
    Interpretable planCallEqual(Expr expr, Interpretable... args) {
      return new EvalEq(expr.getId(), args[0], args[1]);
    }

    /** planCallNotEqual generates a not equals (!=) Interpretable. */
    Interpretable planCallNotEqual(Expr expr, Interpretable... args) {
      return new EvalNe(expr.getId(), args[0], args[1]);
    }

    /** planCallLogicalAnd generates a logical and (&&) Interpretable. */
    Interpretable planCallLogicalAnd(Expr expr, Interpretable... args) {
      return new EvalAnd(expr.getId(), args[0], args[1]);
    }

    /** planCallLogicalOr generates a logical or (||) Interpretable. */
    Interpretable planCallLogicalOr(Expr expr, Interpretable... args) {
      return new EvalOr(expr.getId(), args[0], args[1]);
    }

    /** planCallConditional generates a conditional / ternary (c ? t : f) Interpretable. */
    Interpretable planCallConditional(Expr expr, Interpretable... args) {
      Interpretable cond = args[0];

      Interpretable t = args[1];
      Attribute tAttr;
      if (t instanceof InterpretableAttribute) {
        InterpretableAttribute truthyAttr = (InterpretableAttribute) t;
        tAttr = truthyAttr.attr();
      } else {
        tAttr = attrFactory.relativeAttribute(t.id(), t);
      }

      Interpretable f = args[2];
      Attribute fAttr;
      if (f instanceof InterpretableAttribute) {
        InterpretableAttribute falsyAttr = (InterpretableAttribute) f;
        fAttr = falsyAttr.attr();
      } else {
        fAttr = attrFactory.relativeAttribute(f.id(), f);
      }

      return new EvalAttr(
          adapter, attrFactory.conditionalAttribute(expr.getId(), cond, tAttr, fAttr));
    }

    /**
     * planCallIndex either extends an attribute with the argument to the index operation, or
     * creates a relative attribute based on the return of a function call or operation.
     */
    Interpretable planCallIndex(Expr expr, Interpretable... args) {
      Interpretable op = args[0];
      Interpretable ind = args[1];
      InterpretableAttribute opAttr = relativeAttr(op.id(), op);
      if (opAttr == null) {
        return null;
      }
      Type opType = typeMap.get(expr.getCallExpr().getTarget().getId());
      if (ind instanceof InterpretableConst) {
        InterpretableConst indConst = (InterpretableConst) ind;
        Qualifier qual = attrFactory.newQualifier(opType, expr.getId(), indConst.value());
        if (qual == null) {
          return null;
        }
        opAttr.addQualifier(qual);
        return opAttr;
      }
      if (ind instanceof InterpretableAttribute) {
        InterpretableAttribute indAttr = (InterpretableAttribute) ind;
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

    /** planCreateList generates a list construction Interpretable. */
    Interpretable planCreateList(Expr expr) {
      CreateList list = expr.getListExpr();
      Interpretable[] elems = new Interpretable[list.getElementsCount()];
      for (int i = 0; i < list.getElementsCount(); i++) {
        Expr elem = list.getElements(i);
        Interpretable elemVal = plan(elem);
        if (elemVal == null) {
          return null;
        }
        elems[i] = elemVal;
      }
      return new EvalList(expr.getId(), elems, adapter);
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
      for (int i = 0; i < entries.size(); i++) {
        Entry entry = entries.get(i);
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
      return new EvalMap(expr.getId(), keys, vals, adapter);
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
      for (int i = 0; i < entries.size(); i++) {
        Entry entry = entries.get(i);
        fields[i] = entry.getFieldKey();
        Interpretable val = plan(entry.getValue());
        if (val == null) {
          return null;
        }
        vals[i] = val;
      }
      return new EvalObj(expr.getId(), typeName, fields, vals, provider);
    }

    /** planComprehension generates an Interpretable fold operation. */
    Interpretable planComprehension(Expr expr) {
      Comprehension fold = expr.getComprehensionExpr();
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
          expr.getId(), fold.getAccuVar(), accu, fold.getIterVar(), iterRange, cond, step, result);
    }

    /** planConst generates a constant valued Interpretable. */
    Interpretable planConst(Expr expr) {
      Val val = constValue(expr.getConstExpr());
      if (val == null) {
        return null;
      }
      return newConstValue(expr.getId(), val);
    }

    /** constValue converts a proto Constant value to a ref.Val. */
    @SuppressWarnings("deprecation")
    Val constValue(Constant c) {
      switch (c.getConstantKindCase()) {
        case BOOL_VALUE:
          return boolOf(c.getBoolValue());
        case BYTES_VALUE:
          return bytesOf(c.getBytesValue());
        case DOUBLE_VALUE:
          return doubleOf(c.getDoubleValue());
        case DURATION_VALUE:
          return durationOf(c.getDurationValue());
        case INT64_VALUE:
          return intOf(c.getInt64Value());
        case NULL_VALUE:
          return NullT.NullValue;
        case STRING_VALUE:
          return stringOf(c.getStringValue());
        case TIMESTAMP_VALUE:
          return timestampOf(c.getTimestampValue());
        case UINT64_VALUE:
          return uintOf(c.getUint64Value());
      }
      throw new IllegalArgumentException(
          String.format("unknown constant type: '%s' of kind '%s'", c, c.getConstantKindCase()));
    }

    /**
     * resolveTypeName takes a qualified string constructed at parse time, applies the proto
     * namespace resolution rules to it in a scan over possible matching types in the TypeProvider.
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

      ResolvedFunction(Expr target, String fnName, String overloadId) {
        this.target = target;
        this.fnName = fnName;
        this.overloadId = overloadId;
      }
    }

    /**
     * resolveFunction determines the call target, function name, and overload name from a given
     * Expr value.
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
          return new ResolvedFunction(target, fnName, oRef.getOverloadId(0));
        }
        // Note, this namespaced function name will not appear as a fully qualified name in ASTs
        // built and stored before cel-go v0.5.0; however, this functionality did not work at all
        // before the v0.5.0 release.
        return new ResolvedFunction(target, fnName, "");
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
            return new ResolvedFunction(target, qualifiedName, "");
          }
        }
        // It's possible that the overload was not found, but this situation is accounted for in
        // the planCall phase; however, the leading dot used for denoting fully-qualified
        // namespaced identifiers must be stripped, as all declarations already use fully-qualified
        // names. This stripping behavior is handled automatically by the ResolveCandidateNames
        // call.
        return new ResolvedFunction(target, stripLeadingDot(fnName), "");
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
            return new ResolvedFunction(null, qualifiedName, "");
          }
        }
      }
      // In the default case, the function is exactly as it was advertised: a receiver call on with
      // an expression-based target with the given simple function name.
      return new ResolvedFunction(target, fnName, "");
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
  }
}
