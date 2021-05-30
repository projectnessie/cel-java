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

import java.util.ArrayList;
import java.util.List;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.LongArrayList;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.projectnessie.cel.common.Location;
import org.projectnessie.cel.common.Source;
import org.projectnessie.cel.pb.Constant;
import org.projectnessie.cel.pb.Expr;
import org.projectnessie.cel.pb.Expr.StructExpr;
import org.projectnessie.cel.pb.SourceInfo;

final class Helper {
  private final Source source;
  private final Long2LongHashMap positions;
  private long nextID;

  Helper(Source source) {
    this.source = source;
    this.nextID = 1;
    this.positions = new Long2LongHashMap(-1);
  }

  SourceInfo getSourceInfo() {
    return new SourceInfo(source.description(), positions, source.lineOffsets());
  }

  Expr newError(Object ctx) {
    return Expr.error(newExprId(ctx));
  }

  Expr newLiteral(Object ctx, Constant value) {
    return Expr.constExpr(newExprId(ctx), value);
  }

  Expr newLiteralBool(Object ctx, boolean value) {
    return newLiteral(ctx, Constant.boolValue(value));
  }

  Expr newLiteralString(Object ctx, String value) {
    return newLiteral(ctx, Constant.stringValue(value));
  }

  Expr newLiteralBytes(Object ctx, byte[] value) {
    return newLiteral(ctx, Constant.bytesValue(value));
  }

  Expr newLiteralInt(Object ctx, long value) {
    return newLiteral(ctx, Constant.int64Value(value));
  }

  Expr newLiteralUint(Object ctx, long value) {
    return newLiteral(ctx, Constant.uint64Value(value));
  }

  Expr newLiteralDouble(Object ctx, double value) {
    return newLiteral(ctx, Constant.doubleValue(value));
  }

  Expr newIdent(Object ctx, String name) {
    return Expr.ident(newExprId(ctx), name);
  }

  Expr newSelect(Object ctx, Expr operand, String field) {
    return Expr.select(newExprId(ctx), operand, field, false);
  }

  Expr newPresenceTest(Object ctx, Expr operand, String field) {
    return Expr.select(newExprId(ctx), operand, field, true);
  }

  Expr newGlobalCall(Object ctx, String function, Expr... args) {
    return Expr.call(newExprId(ctx), function, null, args);
  }

  Expr newReceiverCall(Object ctx, String function, Expr target, Expr... args) {
    return Expr.call(newExprId(ctx), function, target, args);
  }

  Expr newList(Object ctx, Expr... elements) {
    return Expr.list(newExprId(ctx), elements);
  }

  Expr newMap(Object ctx, StructExpr.Entry... entries) {
    return Expr.structExpr(newExprId(ctx), null, entries);
  }

  StructExpr.Entry newMapEntry(long entryID, Expr key, Expr value) {
    return Expr.createStructEntry(entryID, Expr.createStructMapKey(key), value);
  }

  Expr newObject(Object ctx, String typeName, StructExpr.Entry... entries) {
    return Expr.structExpr(newExprId(ctx), typeName, entries);
  }

  StructExpr.Entry newObjectField(long fieldID, String field, Expr value) {
    return Expr.createStructEntry(fieldID, Expr.createStructFieldKey(field), value);
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
    return Expr.comprehensionExpr(
        newExprId(ctx), accuVar, accuInit, iterVar, iterRange, condition, step, result);
  }

  long newExprId(Object ctx) {
    if (ctx instanceof Long) {
      return (Long) ctx;
    } else {
      return id(ctx);
    }
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
    int characterOffset = (int) positions.get(id);
    Location location = source.offsetLocation(characterOffset);
    return location;
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
