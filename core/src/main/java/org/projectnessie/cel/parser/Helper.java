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
package org.projectnessie.cel.parser;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Builder;
import com.google.api.expr.v1alpha1.Expr.Call;
import com.google.api.expr.v1alpha1.Expr.Comprehension;
import com.google.api.expr.v1alpha1.Expr.CreateList;
import com.google.api.expr.v1alpha1.Expr.CreateStruct;
import com.google.api.expr.v1alpha1.Expr.CreateStruct.Entry;
import com.google.api.expr.v1alpha1.Expr.Ident;
import com.google.api.expr.v1alpha1.Expr.Select;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.collections.LongArrayList;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.projectnessie.cel.common.Location;
import org.projectnessie.cel.common.Source;

final class Helper {
  private final Source source;
  private final Map<Long, Integer> positions;
  private long nextID;

  Helper(Source source) {
    this.source = source;
    this.nextID = 1;
    this.positions = new HashMap<>();
  }

  SourceInfo getSourceInfo() {
    return SourceInfo.newBuilder()
        .setLocation(source.description())
        .putAllPositions(positions)
        .addAllLineOffsets(source.lineOffsets())
        .build();
  }

  Expr newLiteral(Object ctx, Constant.Builder value) {
    return newExprBuilder(ctx).setConstExpr(value).build();
  }

  Expr newLiteralBool(Object ctx, boolean value) {
    return newLiteral(ctx, Constant.newBuilder().setBoolValue(value));
  }

  Expr newLiteralString(Object ctx, String value) {
    return newLiteral(ctx, Constant.newBuilder().setStringValue(value));
  }

  Expr newLiteralBytes(Object ctx, ByteString value) {
    return newLiteral(ctx, Constant.newBuilder().setBytesValue(value));
  }

  Expr newLiteralInt(Object ctx, long value) {
    return newLiteral(ctx, Constant.newBuilder().setInt64Value(value));
  }

  Expr newLiteralUint(Object ctx, long value) {
    return newLiteral(ctx, Constant.newBuilder().setUint64Value(value));
  }

  Expr newLiteralDouble(Object ctx, double value) {
    return newLiteral(ctx, Constant.newBuilder().setDoubleValue(value));
  }

  Expr newIdent(Object ctx, String name) {
    return newExprBuilder(ctx).setIdentExpr(Ident.newBuilder().setName(name)).build();
  }

  Expr newSelect(Object ctx, Expr operand, String field) {
    return newExprBuilder(ctx)
        .setSelectExpr(Select.newBuilder().setOperand(operand).setField(field))
        .build();
  }

  Expr newPresenceTest(Object ctx, Expr operand, String field) {
    return newExprBuilder(ctx)
        .setSelectExpr(Select.newBuilder().setOperand(operand).setField(field).setTestOnly(true))
        .build();
  }

  Expr newGlobalCall(Object ctx, String function, Expr... args) {
    return newGlobalCall(ctx, function, Arrays.asList(args));
  }

  Expr newGlobalCall(Object ctx, String function, List<Expr> args) {
    return newExprBuilder(ctx)
        .setCallExpr(Call.newBuilder().setFunction(function).addAllArgs(args))
        .build();
  }

  Expr newReceiverCall(Object ctx, String function, Expr target, List<Expr> args) {
    return newExprBuilder(ctx)
        .setCallExpr(Call.newBuilder().setFunction(function).setTarget(target).addAllArgs(args))
        .build();
  }

  Expr newList(Object ctx, List<Expr> elements) {
    return newExprBuilder(ctx)
        .setListExpr(CreateList.newBuilder().addAllElements(elements))
        .build();
  }

  Expr newMap(Object ctx, List<Entry> entries) {
    return newExprBuilder(ctx)
        .setStructExpr(CreateStruct.newBuilder().addAllEntries(entries))
        .build();
  }

  Entry newMapEntry(long entryID, Expr key, Expr value) {
    return Entry.newBuilder().setId(entryID).setMapKey(key).setValue(value).build();
  }

  Expr newObject(Object ctx, String typeName, List<Entry> entries) {
    return newExprBuilder(ctx)
        .setStructExpr(CreateStruct.newBuilder().setMessageName(typeName).addAllEntries(entries))
        .build();
  }

  Entry newObjectField(long fieldID, String field, Expr value) {
    return Entry.newBuilder().setId(fieldID).setFieldKey(field).setValue(value).build();
  }

  Expr newComprehension(
      Object ctx,
      String iterVar,
      Expr iterRange,
      String accuVar,
      Expr accuInit,
      Expr condition,
      Expr step,
      Expr result) {
    return newExprBuilder(ctx)
        .setComprehensionExpr(
            Comprehension.newBuilder()
                .setAccuVar(accuVar)
                .setAccuInit(accuInit)
                .setIterVar(iterVar)
                .setIterRange(iterRange)
                .setLoopCondition(condition)
                .setLoopStep(step)
                .setResult(result)
                .build())
        .build();
  }

  Expr newExpr(Object ctx) {
    return newExprBuilder(ctx).build();
  }

  private Builder newExprBuilder(Object ctx) {
    long exprId = (ctx instanceof Long) ? ((Long) ctx) : id(ctx);
    return Expr.newBuilder().setId(exprId);
  }

  long id(Object ctx) {
    Location location;
    if (ctx instanceof ParserRuleContext) {
      Token token = ((ParserRuleContext) ctx).start;
      location = source.newLocation(token.getLine(), token.getCharPositionInLine());
    } else if (ctx instanceof Token) {
      Token token = (Token) ctx;
      location = source.newLocation(token.getLine(), token.getCharPositionInLine());
    } else if (ctx instanceof Location) {
      location = (Location) ctx;
    } else {
      // This should only happen if the ctx is nil
      return -1L;
    }
    long id = nextID;
    positions.put(id, source.locationOffset(location));
    nextID++;
    return id;
  }

  Location getLocation(long id) {
    int characterOffset = positions.get(id);
    return source.offsetLocation(characterOffset);
  }

  // newBalancer creates a balancer instance bound to a specific function and its first term.
  Balancer newBalancer(String function, Expr term) {
    return new Balancer(function, term);
  }

  class Balancer {
    final String function;
    final List<Expr> terms;
    final LongArrayList ops;

    public Balancer(String function, Expr term) {
      this.function = function;
      this.terms = new ArrayList<>();
      this.terms.add(term);
      this.ops = new LongArrayList();
    }

    // addTerm adds an operation identifier and term to the set of terms to be balanced.
    void addTerm(long op, Expr term) {
      terms.add(term);
      ops.add(op);
    }

    // balance creates a balanced tree from the sub-terms and returns the final Expr value.
    Expr balance() {
      if (terms.size() == 1) {
        return terms.get(0);
      }
      return balancedTree(0, ops.size() - 1);
    }

    // balancedTree recursively balances the terms provided to a commutative operator.
    Expr balancedTree(int lo, int hi) {
      int mid = (lo + hi + 1) / 2;

      Expr left;
      if (mid == lo) {
        left = terms.get(mid);
      } else {
        left = balancedTree(lo, mid - 1);
      }

      Expr right;
      if (mid == hi) {
        right = terms.get(mid + 1);
      } else {
        right = balancedTree(mid + 1, hi);
      }
      return newGlobalCall(ops.get(mid), function, left, right);
    }
  }
}
