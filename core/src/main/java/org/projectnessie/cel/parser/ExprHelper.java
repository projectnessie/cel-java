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

/**
 * ExprHelper assists with the manipulation of proto-based Expr values in a manner which is
 * consistent with the source position and expression id generation code leveraged by both the
 * parser and type-checker.
 */
interface ExprHelper {

  /** LiteralBool creates an Expr value for a bool literal. */
  Expr literalBool(boolean value);

  /** LiteralBytes creates an Expr value for a byte literal. */
  Expr literalBytes(byte[] value);

  /** LiteralDouble creates an Expr value for double literal. */
  Expr literalDouble(double value);

  /** LiteralInt creates an Expr value for an int literal. */
  Expr literalInt(long value);

  /** LiteralString creates am Expr value for a string literal. */
  Expr literalString(String value);

  /** LiteralUint creates an Expr value for a uint literal. */
  Expr literalUint(long value);

  /**
   * NewList creates a CreateList instruction where the list is comprised of the optional set of
   * elements provided as arguments.
   */
  Expr newList(Expr... elems);

  /**
   * NewMap creates a CreateStruct instruction for a map where the map is comprised of the optional
   * set of key, value entries.
   */
  Expr newMap(StructExpr.Entry... entries);

  /** NewMapEntry creates a Map Entry for the key, value pair. */
  StructExpr.Entry newMapEntry(Expr key, Expr val);

  /**
   * NewObject creates a CreateStruct instruction for an object with a given type name and optional
   * set of field initializers.
   */
  Expr newObject(String typeName, StructExpr.Entry... fieldInits);

  /** NewObjectFieldInit creates a new Object field initializer from the field name and value. */
  StructExpr.Entry newObjectFieldInit(String field, Expr init);

  /**
   * Fold creates a fold comprehension instruction.
   *
   * <p>- iterVar is the iteration variable name. - iterRange represents the expression that
   * resolves to a list or map where the elements or keys (respectively) will be iterated over. -
   * accuVar is the accumulation variable name, typically parser.AccumulatorName. - accuInit is the
   * initial expression whose value will be set for the accuVar prior to folding. - condition is the
   * expression to test to determine whether to continue folding. - step is the expression to
   * evaluation at the conclusion of a single fold iteration. - result is the computation to
   * evaluate at the conclusion of the fold.
   *
   * <p>The accuVar should not shadow variable names that you would like to reference within the
   * environment in the step and condition expressions. Presently, the name __result__ is commonly
   * used by built-in macros but this may change in the future.
   */
  Expr fold(
      String iterVar,
      Expr iterRange,
      String accuVar,
      Expr accuInit,
      Expr condition,
      Expr step,
      Expr result);

  /** Ident creates an identifier Expr value. */
  Expr ident(String name);

  /** GlobalCall creates a function call Expr value for a global (free) function. */
  Expr globalCall(String function, Expr... args);

  /** ReceiverCall creates a function call Expr value for a receiver-style function. */
  Expr receiverCall(String function, Expr target, Expr... args);

  /** PresenceTest creates a Select TestOnly Expr value for modelling has() semantics. */
  Expr presenceTest(Expr operand, String field);

  /** Select create a field traversal Expr value. */
  Expr select(Expr operand, String field);

  /** OffsetLocation returns the Location of the expression identifier. */
  Location offsetLocation(long exprID);
}
