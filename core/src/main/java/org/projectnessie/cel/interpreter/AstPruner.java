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

// TODO Consider having a separate walk of the AST that finds common
//  subexpressions. This can be called before or after constant folding to find
//  common subexpressions.

import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Call;
import com.google.api.expr.v1alpha1.Expr.Comprehension;
import com.google.api.expr.v1alpha1.Expr.CreateList;
import com.google.api.expr.v1alpha1.Expr.CreateStruct;
import com.google.api.expr.v1alpha1.Expr.CreateStruct.Entry;
import com.google.api.expr.v1alpha1.Expr.Select;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import java.util.ArrayList;
import java.util.List;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Mapper;

/**
 * PruneAst prunes the given AST based on the given EvalState and generates a new AST. Given AST is
 * copied on write and a new AST is returned.
 *
 * <p>Couple of typical use cases this interface would be:
 *
 * <ol>
 *   <li>
 *       <ol>
 *         <li>Evaluate expr with some unknowns,
 *         <li>If result is unknown:
 *             <ol>
 *               <li>PruneAst
 *               <li>Goto 1
 *             </ol>
 *             Functional call results which are known would be effectively cached across
 *             iterations.
 *       </ol>
 *   <li>
 *       <ol>
 *         <li>Compile the expression (maybe via a service and maybe after checking a compiled
 *             expression does not exists in local cache)
 *         <li>Prepare the environment and the interpreter. Activation might be empty.
 *         <li>Eval the expression. This might return unknown or error or a concrete value.
 *         <li>PruneAst
 *         <li>Maybe cache the expression
 *       </ol>
 * </ol>
 *
 * <p>This is effectively constant folding the expression. How the environment is prepared in step 2
 * is flexible. For example, If the caller caches the compiled and constant folded expressions, but
 * is not willing to constant fold(and thus cache results of) some external calls, then they can
 * prepare the overloads accordingly.
 */
public final class AstPruner {
  private final Expr expr;
  private final EvalState state;
  private long nextExprID;

  private AstPruner(Expr expr, EvalState state, long nextExprID) {
    this.expr = expr;
    this.state = state;
    this.nextExprID = nextExprID;
  }

  public static Expr pruneAst(Expr expr, EvalState state) {
    AstPruner pruner = new AstPruner(expr, state, 1);
    Expr newExpr = pruner.prune(expr);
    return newExpr;
  }

  static Expr createLiteral(long id, Constant val) {
    return Expr.newBuilder().setId(id).setConstExpr(val).build();
  }

  Expr maybeCreateLiteral(long id, Val v) {
    Type t = v.type();
    switch (t.typeEnum()) {
      case Bool:
        return createLiteral(id, Constant.newBuilder().setBoolValue((Boolean) v.value()).build());
      case Int:
        return createLiteral(
            id, Constant.newBuilder().setInt64Value(((Number) v.value()).longValue()).build());
      case Uint:
        return createLiteral(
            id, Constant.newBuilder().setUint64Value(((Number) v.value()).longValue()).build());
      case String:
        return createLiteral(
            id, Constant.newBuilder().setStringValue(v.value().toString()).build());
      case Double:
        return createLiteral(
            id, Constant.newBuilder().setDoubleValue(((Number) v.value()).doubleValue()).build());
      case Bytes:
        return createLiteral(
            id,
            Constant.newBuilder().setBytesValue(ByteString.copyFrom((byte[]) v.value())).build());
      case Null:
        return createLiteral(id, Constant.newBuilder().setNullValue(NullValue.NULL_VALUE).build());
    }

    // Attempt to build a list literal.
    if (v instanceof Lister) {
      Lister list = (Lister) v;
      int sz = (int) list.size().intValue();
      List<Expr> elemExprs = new ArrayList<>(sz);
      for (int i = 0; i < sz; i++) {
        Val elem = list.get(intOf(i));
        if (isUnknownOrError(elem)) {
          return null;
        }
        Expr elemExpr = maybeCreateLiteral(nextID(), elem);
        if (elemExpr == null) {
          return null;
        }
        elemExprs.add(elemExpr);
      }
      return Expr.newBuilder()
          .setId(id)
          .setListExpr(CreateList.newBuilder().addAllElements(elemExprs).build())
          .build();
    }

    // Create a map literal if possible.
    if (v instanceof Mapper) {
      Mapper mp = (Mapper) v;
      IteratorT it = mp.iterator();
      List<Entry> entries = new ArrayList<>((int) mp.size().intValue());
      while (it.hasNext() == True) {
        Val key = it.next();
        Val val = mp.get(key);
        if (isUnknownOrError(key) || isUnknownOrError(val)) {
          return null;
        }
        Expr keyExpr = maybeCreateLiteral(nextID(), key);
        if (keyExpr == null) {
          return null;
        }
        Expr valExpr = maybeCreateLiteral(nextID(), val);
        if (valExpr == null) {
          return null;
        }
        Entry entry =
            Entry.newBuilder().setId(nextID()).setMapKey(keyExpr).setValue(valExpr).build();
        entries.add(entry);
      }
      return Expr.newBuilder()
          .setId(id)
          .setStructExpr(CreateStruct.newBuilder().addAllEntries(entries))
          .build();
    }

    // TODO(issues/377) To construct message literals, the type provider will need to support
    //  the enumeration the fields for a given message.
    return null;
  }

  Expr maybePruneAndOr(Expr node) {
    if (!existsWithUnknownValue(node.getId())) {
      return null;
    }

    Call call = node.getCallExpr();
    // We know result is unknown, so we have at least one unknown arg
    // and if one side is a known value, we know we can ignore it.
    if (existsWithKnownValue(call.getArgs(0).getId())) {
      return call.getArgs(1);
    }
    if (existsWithKnownValue(call.getArgs(1).getId())) {
      return call.getArgs(0);
    }
    return null;
  }

  Expr maybePruneConditional(Expr node) {
    if (!existsWithUnknownValue(node.getId())) {
      return null;
    }

    Call call = node.getCallExpr();
    Val condVal = value(call.getArgs(0).getId());
    if (condVal == null || isUnknownOrError(condVal)) {
      return null;
    }

    if (condVal == True) {
      return call.getArgs(1);
    }
    return call.getArgs(2);
  }

  Expr maybePruneFunction(Expr node) {
    Call call = node.getCallExpr();
    if (call.getFunction().equals(Operator.LogicalOr.id)
        || call.getFunction().equals(Operator.LogicalAnd.id)) {
      return maybePruneAndOr(node);
    }
    if (call.getFunction().equals(Operator.Conditional.id)) {
      return maybePruneConditional(node);
    }

    return null;
  }

  Expr prune(Expr node) {
    if (node == null) {
      return null;
    }
    Val val = value(node.getId());
    if (val != null && !isUnknownOrError(val)) {
      Expr newNode = maybeCreateLiteral(node.getId(), val);
      if (newNode != null) {
        return newNode;
      }
    }

    // We have either an unknown/error value, or something we dont want to
    // transform, or expression was not evaluated. If possible, drill down
    // more.

    switch (node.getExprKindCase()) {
      case SELECT_EXPR:
        Select select = node.getSelectExpr();
        Expr operand = prune(select.getOperand());
        if (operand != null && operand != select.getOperand()) {
          return Expr.newBuilder()
              .setId(node.getId())
              .setSelectExpr(
                  Select.newBuilder()
                      .setOperand(operand)
                      .setField(select.getField())
                      .setTestOnly(select.getTestOnly()))
              .build();
        }
        break;
      case CALL_EXPR:
        Call call = node.getCallExpr();
        Expr newExpr = maybePruneFunction(node);
        if (newExpr != null) {
          newExpr = prune(newExpr);
          return newExpr;
        }
        boolean prunedCall = false;
        List<Expr> args = call.getArgsList();
        List<Expr> newArgs = new ArrayList<>(args.size());
        for (int i = 0; i < args.size(); i++) {
          Expr arg = args.get(i);
          newArgs.add(arg);
          Expr newArg = prune(arg);
          if (newArg != null && newArg != arg) {
            prunedCall = true;
            newArgs.set(i, newArg);
          }
        }
        Call newCall =
            Call.newBuilder()
                .setFunction(call.getFunction())
                .setTarget(call.getTarget())
                .addAllArgs(newArgs)
                .build();
        Expr newTarget = prune(call.getTarget());
        if (newTarget != null && newTarget != call.getTarget()) {
          prunedCall = true;
          newCall =
              Call.newBuilder()
                  .setFunction(call.getFunction())
                  .setTarget(newTarget)
                  .addAllArgs(newArgs)
                  .build();
        }
        if (prunedCall) {
          return Expr.newBuilder().setId(node.getId()).setCallExpr(newCall).build();
        }
        break;
      case LIST_EXPR:
        CreateList list = node.getListExpr();
        List<Expr> elems = list.getElementsList();
        List<Expr> newElems = new ArrayList<>(elems.size());
        boolean prunedList = false;
        for (int i = 0; i < elems.size(); i++) {
          Expr elem = elems.get(i);
          newElems.add(elem);
          Expr newElem = prune(elem);
          if (newElem != null && newElem != elem) {
            newElems.set(i, newElem);
            prunedList = true;
          }
        }
        if (prunedList) {
          return Expr.newBuilder()
              .setId(node.getId())
              .setListExpr(CreateList.newBuilder().addAllElements(newElems))
              .build();
        }
        break;
      case STRUCT_EXPR:
        boolean prunedStruct = false;
        CreateStruct struct = node.getStructExpr();
        List<Entry> entries = struct.getEntriesList();
        String messageType = struct.getMessageName();
        List<Entry> newEntries = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
          Entry entry = entries.get(i);
          newEntries.add(entry);
          Expr mapKey = entry.getMapKey();
          Expr newKey = mapKey != Entry.getDefaultInstance().getMapKey() ? prune(mapKey) : null;
          Expr newValue = prune(entry.getValue());
          if ((newKey == null || newKey == mapKey)
              && (newValue == null || newValue == entry.getValue())) {
            continue;
          }
          prunedStruct = true;
          Entry newEntry;
          if (!messageType.isEmpty()) {
            newEntry =
                Entry.newBuilder().setFieldKey(entry.getFieldKey()).setValue(newValue).build();
          } else {
            newEntry = Entry.newBuilder().setMapKey(newKey).setValue(newValue).build();
          }
          newEntries.set(i, newEntry);
        }
        if (prunedStruct) {
          return Expr.newBuilder()
              .setId(node.getId())
              .setStructExpr(
                  CreateStruct.newBuilder().setMessageName(messageType).addAllEntries(entries))
              .build();
        }
        break;
      case COMPREHENSION_EXPR:
        Comprehension compre = node.getComprehensionExpr();
        // Only the range of the comprehension is pruned since the state tracking only records
        // the last iteration of the comprehension and not each step in the evaluation which
        // means that the any residuals computed in between might be inaccurate.
        Expr newRange = prune(compre.getIterRange());
        if (newRange != null && newRange != compre.getIterRange()) {
          return Expr.newBuilder()
              .setId(node.getId())
              .setComprehensionExpr(
                  Comprehension.newBuilder()
                      .setIterVar(compre.getIterVar())
                      .setIterRange(newRange)
                      .setAccuVar(compre.getAccuVar())
                      .setAccuInit(compre.getAccuInit())
                      .setLoopCondition(compre.getLoopCondition())
                      .setLoopStep(compre.getLoopStep())
                      .setResult(compre.getResult()))
              .build();
        }
    }

    // Note: original Go implementation returns "node, false". We could wrap 'node' in some
    // 'PruneResult' wrapper, but that would just exchange allocation cost at one point w/
    // allocation cost at another point. So go with the simple approach - at least for now.
    return node;
  }

  Val value(long id) {
    return state.value(id);
  }

  boolean existsWithUnknownValue(long id) {
    Val val = value(id);
    return isUnknown(val);
  }

  boolean existsWithKnownValue(long id) {
    Val val = value(id);
    return val != null && !isUnknown(val);
  }

  long nextID() {
    while (true) {
      if (state.value(nextExprID) != null) {
        nextExprID++;
      } else {
        return nextExprID++;
      }
    }
  }
}
