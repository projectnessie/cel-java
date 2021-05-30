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

import org.projectnessie.cel.common.Location;
import org.projectnessie.cel.pb.Expr;
import org.projectnessie.cel.pb.Expr.StructExpr;

public class ExprHelperImpl implements ExprHelper {

  final Helper parserHelper;
  final long id;

  public ExprHelperImpl(Helper parserHelper, long id) {
    this.parserHelper = parserHelper;
    this.id = id;
  }

  long nextMacroID() {
    return parserHelper.id(parserHelper.getLocation(id));
  }

  // LiteralBool implements the ExprHelper interface method.
  @Override
  public Expr literalBool(boolean value) {
    return parserHelper.newLiteralBool(nextMacroID(), value);
  }

  // LiteralBytes implements the ExprHelper interface method.
  @Override
  public Expr literalBytes(byte[] value) {
    return parserHelper.newLiteralBytes(nextMacroID(), value);
  }

  // LiteralDouble implements the ExprHelper interface method.
  @Override
  public Expr literalDouble(double value) {
    return parserHelper.newLiteralDouble(nextMacroID(), value);
  }

  // LiteralInt implements the ExprHelper interface method.
  @Override
  public Expr literalInt(long value) {
    return parserHelper.newLiteralInt(nextMacroID(), value);
  }

  // LiteralString implements the ExprHelper interface method.
  @Override
  public Expr literalString(String value) {
    return parserHelper.newLiteralString(nextMacroID(), value);
  }

  // LiteralUint implements the ExprHelper interface method.
  @Override
  public Expr literalUint(long value) {
    return parserHelper.newLiteralUint(nextMacroID(), value);
  }

  // NewList implements the ExprHelper interface method.
  @Override
  public Expr newList(Expr... elems) {
    return parserHelper.newList(nextMacroID(), elems);
  }

  // NewMap implements the ExprHelper interface method.
  @Override
  public Expr newMap(StructExpr.Entry... entries) {
    return parserHelper.newMap(nextMacroID(), entries);
  }

  // NewMapEntry implements the ExprHelper interface method.
  @Override
  public StructExpr.Entry newMapEntry(Expr key, Expr val) {
    return parserHelper.newMapEntry(nextMacroID(), key, val);
  }

  // NewObject implements the ExprHelper interface method.
  @Override
  public Expr newObject(String typeName, StructExpr.Entry... fieldInits) {
    return parserHelper.newObject(nextMacroID(), typeName, fieldInits);
  }

  // NewObjectFieldInit implements the ExprHelper interface method.
  @Override
  public StructExpr.Entry newObjectFieldInit(String field, Expr init) {
    return parserHelper.newObjectField(nextMacroID(), field, init);
  }

  // Fold implements the ExprHelper interface method.
  @Override
  public Expr fold(
      String iterVar,
      Expr iterRange,
      String accuVar,
      Expr accuInit,
      Expr condition,
      Expr step,
      Expr result) {
    return parserHelper.newComprehension(
        nextMacroID(), iterVar, iterRange, accuVar, accuInit, condition, step, result);
  }

  // Ident implements the ExprHelper interface method.
  @Override
  public Expr ident(String name) {
    return parserHelper.newIdent(nextMacroID(), name);
  }

  // GlobalCall implements the ExprHelper interface method.
  @Override
  public Expr globalCall(String function, Expr... args) {
    return parserHelper.newGlobalCall(nextMacroID(), function, args);
  }

  // ReceiverCall implements the ExprHelper interface method.
  @Override
  public Expr receiverCall(String function, Expr target, Expr... args) {
    return parserHelper.newReceiverCall(nextMacroID(), function, target, args);
  }

  // PresenceTest implements the ExprHelper interface method.
  @Override
  public Expr presenceTest(Expr operand, String field) {
    return parserHelper.newPresenceTest(nextMacroID(), operand, field);
  }

  // Select implements the ExprHelper interface method.
  @Override
  public Expr select(Expr operand, String field) {
    return parserHelper.newSelect(nextMacroID(), operand, field);
  }

  // OffsetLocation implements the ExprHelper interface method.
  @Override
  public Location offsetLocation(long exprID) {
    return parserHelper.getLocation(exprID);
  }
}
