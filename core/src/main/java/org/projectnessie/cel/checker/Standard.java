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

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static org.projectnessie.cel.checker.Types.formatCheckedType;

import com.google.api.expr.v1alpha1.Decl;
import com.google.api.expr.v1alpha1.Type;
import java.util.ArrayList;
import java.util.List;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.Overloads;

final class Standard {

  private Standard() {}

  // StandardDeclarations returns the Decls for all functions and constants in the evaluator.
  static List<Decl> makeStandardDeclarations() {
    // Some shortcuts we use when building declarations.
    Type paramA = Decls.newTypeParamType("A");
    List<String> typeParamAList = singletonList("A");
    Type listOfA = Decls.newListType(paramA);
    Type paramB = Decls.newTypeParamType("B");
    List<String> typeParamABList = asList("A", "B");
    Type mapOfAB = Decls.newMapType(paramA, paramB);

    List<Decl> idents = new ArrayList<>();
    for (Type t :
        asList(Decls.Int, Decls.Uint, Decls.Bool, Decls.Double, Decls.Bytes, Decls.String)) {
      idents.add(Decls.newVar(formatCheckedType(t), Decls.newTypeType(t)));
    }
    idents.add(Decls.newVar("list", Decls.newTypeType(listOfA)));
    idents.add(Decls.newVar("map", Decls.newTypeType(mapOfAB)));
    idents.add(Decls.newVar("null_type", Decls.newTypeType(Decls.Null)));
    idents.add(Decls.newVar("type", Decls.newTypeType(Decls.newTypeType(null))));

    // Booleans
    // TODO: allow the conditional to return a heterogenous type.
    idents.add(
        Decls.newFunction(
            Operator.Conditional.id,
            Decls.newParameterizedOverload(
                Overloads.Conditional,
                asList(Decls.Bool, paramA, paramA),
                paramA,
                typeParamAList)));

    idents.add(
        Decls.newFunction(
            Operator.LogicalAnd.id,
            Decls.newOverload(Overloads.LogicalAnd, asList(Decls.Bool, Decls.Bool), Decls.Bool)));

    idents.add(
        Decls.newFunction(
            Operator.LogicalOr.id,
            Decls.newOverload(Overloads.LogicalOr, asList(Decls.Bool, Decls.Bool), Decls.Bool)));

    idents.add(
        Decls.newFunction(
            Operator.LogicalNot.id,
            Decls.newOverload(Overloads.LogicalNot, singletonList(Decls.Bool), Decls.Bool)));

    idents.add(
        Decls.newFunction(
            Operator.NotStrictlyFalse.id,
            Decls.newOverload(Overloads.NotStrictlyFalse, singletonList(Decls.Bool), Decls.Bool)));

    // Relations.

    idents.add(
        Decls.newFunction(
            Operator.Less.id,
            Decls.newOverload(Overloads.LessBool, asList(Decls.Bool, Decls.Bool), Decls.Bool),
            Decls.newOverload(Overloads.LessInt64, asList(Decls.Int, Decls.Int), Decls.Bool),
            Decls.newOverload(Overloads.LessUint64, asList(Decls.Uint, Decls.Uint), Decls.Bool),
            Decls.newOverload(Overloads.LessDouble, asList(Decls.Double, Decls.Double), Decls.Bool),
            Decls.newOverload(Overloads.LessString, asList(Decls.String, Decls.String), Decls.Bool),
            Decls.newOverload(Overloads.LessBytes, asList(Decls.Bytes, Decls.Bytes), Decls.Bool),
            Decls.newOverload(
                Overloads.LessTimestamp, asList(Decls.Timestamp, Decls.Timestamp), Decls.Bool),
            Decls.newOverload(
                Overloads.LessDuration, asList(Decls.Duration, Decls.Duration), Decls.Bool)));

    idents.add(
        Decls.newFunction(
            Operator.LessEquals.id,
            Decls.newOverload(Overloads.LessEqualsBool, asList(Decls.Bool, Decls.Bool), Decls.Bool),
            Decls.newOverload(Overloads.LessEqualsInt64, asList(Decls.Int, Decls.Int), Decls.Bool),
            Decls.newOverload(
                Overloads.LessEqualsUint64, asList(Decls.Uint, Decls.Uint), Decls.Bool),
            Decls.newOverload(
                Overloads.LessEqualsDouble, asList(Decls.Double, Decls.Double), Decls.Bool),
            Decls.newOverload(
                Overloads.LessEqualsString, asList(Decls.String, Decls.String), Decls.Bool),
            Decls.newOverload(
                Overloads.LessEqualsBytes, asList(Decls.Bytes, Decls.Bytes), Decls.Bool),
            Decls.newOverload(
                Overloads.LessEqualsTimestamp,
                asList(Decls.Timestamp, Decls.Timestamp),
                Decls.Bool),
            Decls.newOverload(
                Overloads.LessEqualsDuration, asList(Decls.Duration, Decls.Duration), Decls.Bool)));

    idents.add(
        Decls.newFunction(
            Operator.Greater.id,
            Decls.newOverload(Overloads.GreaterBool, asList(Decls.Bool, Decls.Bool), Decls.Bool),
            Decls.newOverload(Overloads.GreaterInt64, asList(Decls.Int, Decls.Int), Decls.Bool),
            Decls.newOverload(Overloads.GreaterUint64, asList(Decls.Uint, Decls.Uint), Decls.Bool),
            Decls.newOverload(
                Overloads.GreaterDouble, asList(Decls.Double, Decls.Double), Decls.Bool),
            Decls.newOverload(
                Overloads.GreaterString, asList(Decls.String, Decls.String), Decls.Bool),
            Decls.newOverload(Overloads.GreaterBytes, asList(Decls.Bytes, Decls.Bytes), Decls.Bool),
            Decls.newOverload(
                Overloads.GreaterTimestamp, asList(Decls.Timestamp, Decls.Timestamp), Decls.Bool),
            Decls.newOverload(
                Overloads.GreaterDuration, asList(Decls.Duration, Decls.Duration), Decls.Bool)));

    idents.add(
        Decls.newFunction(
            Operator.GreaterEquals.id,
            Decls.newOverload(
                Overloads.GreaterEqualsBool, asList(Decls.Bool, Decls.Bool), Decls.Bool),
            Decls.newOverload(
                Overloads.GreaterEqualsInt64, asList(Decls.Int, Decls.Int), Decls.Bool),
            Decls.newOverload(
                Overloads.GreaterEqualsUint64, asList(Decls.Uint, Decls.Uint), Decls.Bool),
            Decls.newOverload(
                Overloads.GreaterEqualsDouble, asList(Decls.Double, Decls.Double), Decls.Bool),
            Decls.newOverload(
                Overloads.GreaterEqualsString, asList(Decls.String, Decls.String), Decls.Bool),
            Decls.newOverload(
                Overloads.GreaterEqualsBytes, asList(Decls.Bytes, Decls.Bytes), Decls.Bool),
            Decls.newOverload(
                Overloads.GreaterEqualsTimestamp,
                asList(Decls.Timestamp, Decls.Timestamp),
                Decls.Bool),
            Decls.newOverload(
                Overloads.GreaterEqualsDuration,
                asList(Decls.Duration, Decls.Duration),
                Decls.Bool)));

    idents.add(
        Decls.newFunction(
            Operator.Equals.id,
            Decls.newParameterizedOverload(
                Overloads.Equals, asList(paramA, paramA), Decls.Bool, typeParamAList)));

    idents.add(
        Decls.newFunction(
            Operator.NotEquals.id,
            Decls.newParameterizedOverload(
                Overloads.NotEquals, asList(paramA, paramA), Decls.Bool, typeParamAList)));

    // Algebra.

    idents.add(
        Decls.newFunction(
            Operator.Subtract.id,
            Decls.newOverload(Overloads.SubtractInt64, asList(Decls.Int, Decls.Int), Decls.Int),
            Decls.newOverload(Overloads.SubtractUint64, asList(Decls.Uint, Decls.Uint), Decls.Uint),
            Decls.newOverload(
                Overloads.SubtractDouble, asList(Decls.Double, Decls.Double), Decls.Double),
            Decls.newOverload(
                Overloads.SubtractTimestampTimestamp,
                asList(Decls.Timestamp, Decls.Timestamp),
                Decls.Duration),
            Decls.newOverload(
                Overloads.SubtractTimestampDuration,
                asList(Decls.Timestamp, Decls.Duration),
                Decls.Timestamp),
            Decls.newOverload(
                Overloads.SubtractDurationDuration,
                asList(Decls.Duration, Decls.Duration),
                Decls.Duration)));

    idents.add(
        Decls.newFunction(
            Operator.Multiply.id,
            Decls.newOverload(Overloads.MultiplyInt64, asList(Decls.Int, Decls.Int), Decls.Int),
            Decls.newOverload(Overloads.MultiplyUint64, asList(Decls.Uint, Decls.Uint), Decls.Uint),
            Decls.newOverload(
                Overloads.MultiplyDouble, asList(Decls.Double, Decls.Double), Decls.Double)));

    idents.add(
        Decls.newFunction(
            Operator.Divide.id,
            Decls.newOverload(Overloads.DivideInt64, asList(Decls.Int, Decls.Int), Decls.Int),
            Decls.newOverload(Overloads.DivideUint64, asList(Decls.Uint, Decls.Uint), Decls.Uint),
            Decls.newOverload(
                Overloads.DivideDouble, asList(Decls.Double, Decls.Double), Decls.Double)));

    idents.add(
        Decls.newFunction(
            Operator.Modulo.id,
            Decls.newOverload(Overloads.ModuloInt64, asList(Decls.Int, Decls.Int), Decls.Int),
            Decls.newOverload(Overloads.ModuloUint64, asList(Decls.Uint, Decls.Uint), Decls.Uint)));

    idents.add(
        Decls.newFunction(
            Operator.Add.id,
            Decls.newOverload(Overloads.AddInt64, asList(Decls.Int, Decls.Int), Decls.Int),
            Decls.newOverload(Overloads.AddUint64, asList(Decls.Uint, Decls.Uint), Decls.Uint),
            Decls.newOverload(
                Overloads.AddDouble, asList(Decls.Double, Decls.Double), Decls.Double),
            Decls.newOverload(
                Overloads.AddString, asList(Decls.String, Decls.String), Decls.String),
            Decls.newOverload(Overloads.AddBytes, asList(Decls.Bytes, Decls.Bytes), Decls.Bytes),
            Decls.newParameterizedOverload(
                Overloads.AddList, asList(listOfA, listOfA), listOfA, typeParamAList),
            Decls.newOverload(
                Overloads.AddTimestampDuration,
                asList(Decls.Timestamp, Decls.Duration),
                Decls.Timestamp),
            Decls.newOverload(
                Overloads.AddDurationTimestamp,
                asList(Decls.Duration, Decls.Timestamp),
                Decls.Timestamp),
            Decls.newOverload(
                Overloads.AddDurationDuration,
                asList(Decls.Duration, Decls.Duration),
                Decls.Duration)));

    idents.add(
        Decls.newFunction(
            Operator.Negate.id,
            Decls.newOverload(Overloads.NegateInt64, singletonList(Decls.Int), Decls.Int),
            Decls.newOverload(Overloads.NegateDouble, singletonList(Decls.Double), Decls.Double)));

    // Index.

    idents.add(
        Decls.newFunction(
            Operator.Index.id,
            Decls.newParameterizedOverload(
                Overloads.IndexList, asList(listOfA, Decls.Int), paramA, typeParamAList),
            Decls.newParameterizedOverload(
                Overloads.IndexMap, asList(mapOfAB, paramA), paramB, typeParamABList)));
    // Decls.newOverload(Overloads.IndexMessage,
    //	[]*expr.Type{Decls.Dyn, Decls.String}, Decls.Dyn)));

    // Collections.

    idents.add(
        Decls.newFunction(
            Overloads.Size,
            Decls.newInstanceOverload(
                Overloads.SizeStringInst, singletonList(Decls.String), Decls.Int),
            Decls.newInstanceOverload(
                Overloads.SizeBytesInst, singletonList(Decls.Bytes), Decls.Int),
            Decls.newParameterizedInstanceOverload(
                Overloads.SizeListInst, singletonList(listOfA), Decls.Int, typeParamAList),
            Decls.newParameterizedInstanceOverload(
                Overloads.SizeMapInst, singletonList(mapOfAB), Decls.Int, typeParamABList),
            Decls.newOverload(Overloads.SizeString, singletonList(Decls.String), Decls.Int),
            Decls.newOverload(Overloads.SizeBytes, singletonList(Decls.Bytes), Decls.Int),
            Decls.newParameterizedOverload(
                Overloads.SizeList, singletonList(listOfA), Decls.Int, typeParamAList),
            Decls.newParameterizedOverload(
                Overloads.SizeMap, singletonList(mapOfAB), Decls.Int, typeParamABList)));

    idents.add(
        Decls.newFunction(
            Operator.In.id,
            Decls.newParameterizedOverload(
                Overloads.InList, asList(paramA, listOfA), Decls.Bool, typeParamAList),
            Decls.newParameterizedOverload(
                Overloads.InMap, asList(paramA, mapOfAB), Decls.Bool, typeParamABList)));

    // Deprecated 'in()' function.

    idents.add(
        Decls.newFunction(
            Overloads.DeprecatedIn,
            Decls.newParameterizedOverload(
                Overloads.InList, asList(paramA, listOfA), Decls.Bool, typeParamAList),
            Decls.newParameterizedOverload(
                Overloads.InMap, asList(paramA, mapOfAB), Decls.Bool, typeParamABList)));
    // Decls.newOverload(Overloads.InMessage,
    //	[]*expr.Type{Dyn, Decls.String},Decls.Bool)));

    // Conversions to type.

    idents.add(
        Decls.newFunction(
            Overloads.TypeConvertType,
            Decls.newParameterizedOverload(
                Overloads.TypeConvertType,
                singletonList(paramA),
                Decls.newTypeType(paramA),
                typeParamAList)));

    // Conversions to int.

    idents.add(
        Decls.newFunction(
            Overloads.TypeConvertInt,
            Decls.newOverload(Overloads.IntToInt, singletonList(Decls.Int), Decls.Int),
            Decls.newOverload(Overloads.UintToInt, singletonList(Decls.Uint), Decls.Int),
            Decls.newOverload(Overloads.DoubleToInt, singletonList(Decls.Double), Decls.Int),
            Decls.newOverload(Overloads.StringToInt, singletonList(Decls.String), Decls.Int),
            Decls.newOverload(Overloads.TimestampToInt, singletonList(Decls.Timestamp), Decls.Int),
            Decls.newOverload(Overloads.DurationToInt, singletonList(Decls.Duration), Decls.Int)));

    // Conversions to uint.

    idents.add(
        Decls.newFunction(
            Overloads.TypeConvertUint,
            Decls.newOverload(Overloads.UintToUint, singletonList(Decls.Uint), Decls.Uint),
            Decls.newOverload(Overloads.IntToUint, singletonList(Decls.Int), Decls.Uint),
            Decls.newOverload(Overloads.DoubleToUint, singletonList(Decls.Double), Decls.Uint),
            Decls.newOverload(Overloads.StringToUint, singletonList(Decls.String), Decls.Uint)));

    // Conversions to double.

    idents.add(
        Decls.newFunction(
            Overloads.TypeConvertDouble,
            Decls.newOverload(Overloads.DoubleToDouble, singletonList(Decls.Double), Decls.Double),
            Decls.newOverload(Overloads.IntToDouble, singletonList(Decls.Int), Decls.Double),
            Decls.newOverload(Overloads.UintToDouble, singletonList(Decls.Uint), Decls.Double),
            Decls.newOverload(
                Overloads.StringToDouble, singletonList(Decls.String), Decls.Double)));

    // Conversions to bool.

    idents.add(
        Decls.newFunction(
            Overloads.TypeConvertBool,
            Decls.newOverload(Overloads.BoolToBool, singletonList(Decls.Bool), Decls.Bool),
            Decls.newOverload(Overloads.StringToBool, singletonList(Decls.String), Decls.Bool)));

    // Conversions to string.

    idents.add(
        Decls.newFunction(
            Overloads.TypeConvertString,
            Decls.newOverload(Overloads.StringToString, singletonList(Decls.String), Decls.String),
            Decls.newOverload(Overloads.BoolToString, singletonList(Decls.Bool), Decls.String),
            Decls.newOverload(Overloads.IntToString, singletonList(Decls.Int), Decls.String),
            Decls.newOverload(Overloads.UintToString, singletonList(Decls.Uint), Decls.String),
            Decls.newOverload(Overloads.DoubleToString, singletonList(Decls.Double), Decls.String),
            Decls.newOverload(Overloads.BytesToString, singletonList(Decls.Bytes), Decls.String),
            Decls.newOverload(
                Overloads.TimestampToString, singletonList(Decls.Timestamp), Decls.String),
            Decls.newOverload(
                Overloads.DurationToString, singletonList(Decls.Duration), Decls.String)));

    // Conversions to bytes.

    idents.add(
        Decls.newFunction(
            Overloads.TypeConvertBytes,
            Decls.newOverload(Overloads.BytesToBytes, singletonList(Decls.Bytes), Decls.Bytes),
            Decls.newOverload(Overloads.StringToBytes, singletonList(Decls.String), Decls.Bytes)));

    // Conversions to timestamps.

    idents.add(
        Decls.newFunction(
            Overloads.TypeConvertTimestamp,
            Decls.newOverload(
                Overloads.TimestampToTimestamp, singletonList(Decls.Timestamp), Decls.Timestamp),
            Decls.newOverload(
                Overloads.StringToTimestamp, singletonList(Decls.String), Decls.Timestamp),
            Decls.newOverload(
                Overloads.IntToTimestamp, singletonList(Decls.Int), Decls.Timestamp)));

    // Conversions to durations.

    idents.add(
        Decls.newFunction(
            Overloads.TypeConvertDuration,
            Decls.newOverload(
                Overloads.DurationToDuration, singletonList(Decls.Duration), Decls.Duration),
            Decls.newOverload(
                Overloads.StringToDuration, singletonList(Decls.String), Decls.Duration),
            Decls.newOverload(Overloads.IntToDuration, singletonList(Decls.Int), Decls.Duration)));

    // Conversions to Dyn.

    idents.add(
        Decls.newFunction(
            Overloads.TypeConvertDyn,
            Decls.newParameterizedOverload(
                Overloads.ToDyn, singletonList(paramA), Decls.Dyn, typeParamAList)));

    // String functions.

    idents.add(
        Decls.newFunction(
            Overloads.Contains,
            Decls.newInstanceOverload(
                Overloads.ContainsString, asList(Decls.String, Decls.String), Decls.Bool)));
    idents.add(
        Decls.newFunction(
            Overloads.EndsWith,
            Decls.newInstanceOverload(
                Overloads.EndsWithString, asList(Decls.String, Decls.String), Decls.Bool)));
    idents.add(
        Decls.newFunction(
            Overloads.Matches,
            Decls.newInstanceOverload(
                Overloads.MatchesString, asList(Decls.String, Decls.String), Decls.Bool)));
    idents.add(
        Decls.newFunction(
            Overloads.StartsWith,
            Decls.newInstanceOverload(
                Overloads.StartsWithString, asList(Decls.String, Decls.String), Decls.Bool)));

    // Date/time functions.

    idents.add(
        Decls.newFunction(
            Overloads.TimeGetFullYear,
            Decls.newInstanceOverload(
                Overloads.TimestampToYear, singletonList(Decls.Timestamp), Decls.Int),
            Decls.newInstanceOverload(
                Overloads.TimestampToYearWithTz,
                asList(Decls.Timestamp, Decls.String),
                Decls.Int)));

    idents.add(
        Decls.newFunction(
            Overloads.TimeGetMonth,
            Decls.newInstanceOverload(
                Overloads.TimestampToMonth, singletonList(Decls.Timestamp), Decls.Int),
            Decls.newInstanceOverload(
                Overloads.TimestampToMonthWithTz,
                asList(Decls.Timestamp, Decls.String),
                Decls.Int)));

    idents.add(
        Decls.newFunction(
            Overloads.TimeGetDayOfYear,
            Decls.newInstanceOverload(
                Overloads.TimestampToDayOfYear, singletonList(Decls.Timestamp), Decls.Int),
            Decls.newInstanceOverload(
                Overloads.TimestampToDayOfYearWithTz,
                asList(Decls.Timestamp, Decls.String),
                Decls.Int)));

    idents.add(
        Decls.newFunction(
            Overloads.TimeGetDayOfMonth,
            Decls.newInstanceOverload(
                Overloads.TimestampToDayOfMonthZeroBased,
                singletonList(Decls.Timestamp),
                Decls.Int),
            Decls.newInstanceOverload(
                Overloads.TimestampToDayOfMonthZeroBasedWithTz,
                asList(Decls.Timestamp, Decls.String),
                Decls.Int)));

    idents.add(
        Decls.newFunction(
            Overloads.TimeGetDate,
            Decls.newInstanceOverload(
                Overloads.TimestampToDayOfMonthOneBased, singletonList(Decls.Timestamp), Decls.Int),
            Decls.newInstanceOverload(
                Overloads.TimestampToDayOfMonthOneBasedWithTz,
                asList(Decls.Timestamp, Decls.String),
                Decls.Int)));

    idents.add(
        Decls.newFunction(
            Overloads.TimeGetDayOfWeek,
            Decls.newInstanceOverload(
                Overloads.TimestampToDayOfWeek, singletonList(Decls.Timestamp), Decls.Int),
            Decls.newInstanceOverload(
                Overloads.TimestampToDayOfWeekWithTz,
                asList(Decls.Timestamp, Decls.String),
                Decls.Int)));

    idents.add(
        Decls.newFunction(
            Overloads.TimeGetHours,
            Decls.newInstanceOverload(
                Overloads.TimestampToHours, singletonList(Decls.Timestamp), Decls.Int),
            Decls.newInstanceOverload(
                Overloads.TimestampToHoursWithTz, asList(Decls.Timestamp, Decls.String), Decls.Int),
            Decls.newInstanceOverload(
                Overloads.DurationToHours, singletonList(Decls.Duration), Decls.Int)));

    idents.add(
        Decls.newFunction(
            Overloads.TimeGetMinutes,
            Decls.newInstanceOverload(
                Overloads.TimestampToMinutes, singletonList(Decls.Timestamp), Decls.Int),
            Decls.newInstanceOverload(
                Overloads.TimestampToMinutesWithTz,
                asList(Decls.Timestamp, Decls.String),
                Decls.Int),
            Decls.newInstanceOverload(
                Overloads.DurationToMinutes, singletonList(Decls.Duration), Decls.Int)));

    idents.add(
        Decls.newFunction(
            Overloads.TimeGetSeconds,
            Decls.newInstanceOverload(
                Overloads.TimestampToSeconds, singletonList(Decls.Timestamp), Decls.Int),
            Decls.newInstanceOverload(
                Overloads.TimestampToSecondsWithTz,
                asList(Decls.Timestamp, Decls.String),
                Decls.Int),
            Decls.newInstanceOverload(
                Overloads.DurationToSeconds, singletonList(Decls.Duration), Decls.Int)));

    idents.add(
        Decls.newFunction(
            Overloads.TimeGetMilliseconds,
            Decls.newInstanceOverload(
                Overloads.TimestampToMilliseconds, singletonList(Decls.Timestamp), Decls.Int),
            Decls.newInstanceOverload(
                Overloads.TimestampToMillisecondsWithTz,
                asList(Decls.Timestamp, Decls.String),
                Decls.Int),
            Decls.newInstanceOverload(
                Overloads.DurationToMilliseconds, singletonList(Decls.Duration), Decls.Int)));

    return unmodifiableList(idents);
  }
}
