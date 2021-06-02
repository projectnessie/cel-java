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
package org.projectnessie.cel.common.types;

public final class Overloads {
  private Overloads() {}

  // Boolean logic overloads
  public static final String Conditional = "conditional";
  public static final String LogicalAnd = "logical_and";
  public static final String LogicalOr = "logical_or";
  public static final String LogicalNot = "logical_not";
  public static final String NotStrictlyFalse = "not_strictly_false";
  public static final String Equals = "equals";
  public static final String NotEquals = "not_equals";
  public static final String LessBool = "less_bool";
  public static final String LessInt64 = "less_int64";
  public static final String LessUint64 = "less_uint64";
  public static final String LessDouble = "less_double";
  public static final String LessString = "less_string";
  public static final String LessBytes = "less_bytes";
  public static final String LessTimestamp = "less_timestamp";
  public static final String LessDuration = "less_duration";
  public static final String LessEqualsBool = "less_equals_bool";
  public static final String LessEqualsInt64 = "less_equals_int64";
  public static final String LessEqualsUint64 = "less_equals_uint64";
  public static final String LessEqualsDouble = "less_equals_double";
  public static final String LessEqualsString = "less_equals_string";
  public static final String LessEqualsBytes = "less_equals_bytes";
  public static final String LessEqualsTimestamp = "less_equals_timestamp";
  public static final String LessEqualsDuration = "less_equals_duration";
  public static final String GreaterBool = "greater_bool";
  public static final String GreaterInt64 = "greater_int64";
  public static final String GreaterUint64 = "greater_uint64";
  public static final String GreaterDouble = "greater_double";
  public static final String GreaterString = "greater_string";
  public static final String GreaterBytes = "greater_bytes";
  public static final String GreaterTimestamp = "greater_timestamp";
  public static final String GreaterDuration = "greater_duration";
  public static final String GreaterEqualsBool = "greater_equals_bool";
  public static final String GreaterEqualsInt64 = "greater_equals_int64";
  public static final String GreaterEqualsUint64 = "greater_equals_uint64";
  public static final String GreaterEqualsDouble = "greater_equals_double";
  public static final String GreaterEqualsString = "greater_equals_string";
  public static final String GreaterEqualsBytes = "greater_equals_bytes";
  public static final String GreaterEqualsTimestamp = "greater_equals_timestamp";
  public static final String GreaterEqualsDuration = "greater_equals_duration";

  // Math overloads
  public static final String AddInt64 = "add_int64";
  public static final String AddUint64 = "add_uint64";
  public static final String AddDouble = "add_double";
  public static final String AddString = "add_string";
  public static final String AddBytes = "add_bytes";
  public static final String AddList = "add_list";
  public static final String AddTimestampDuration = "add_timestamp_duration";
  public static final String AddDurationTimestamp = "add_duration_timestamp";
  public static final String AddDurationDuration = "add_duration_duration";
  public static final String SubtractInt64 = "subtract_int64";
  public static final String SubtractUint64 = "subtract_uint64";
  public static final String SubtractDouble = "subtract_double";
  public static final String SubtractTimestampTimestamp = "subtract_timestamp_timestamp";
  public static final String SubtractTimestampDuration = "subtract_timestamp_duration";
  public static final String SubtractDurationDuration = "subtract_duration_duration";
  public static final String MultiplyInt64 = "multiply_int64";
  public static final String MultiplyUint64 = "multiply_uint64";
  public static final String MultiplyDouble = "multiply_double";
  public static final String DivideInt64 = "divide_int64";
  public static final String DivideUint64 = "divide_uint64";
  public static final String DivideDouble = "divide_double";
  public static final String ModuloInt64 = "modulo_int64";
  public static final String ModuloUint64 = "modulo_uint64";
  public static final String NegateInt64 = "negate_int64";
  public static final String NegateDouble = "negate_double";

  // Index overloads
  public static final String IndexList = "index_list";
  public static final String IndexMap = "index_map";
  public static final String IndexMessage =
      "index_message"; // TODO: introduce concept of types.Message

  // In operators
  public static final String DeprecatedIn = "in";
  public static final String InList = "in_list";
  public static final String InMap = "in_map";
  public static final String InMessage = "in_message"; // TODO: introduce concept of types.Message

  // Size overloads
  public static final String Size = "size";
  public static final String SizeString = "size_string";
  public static final String SizeBytes = "size_bytes";
  public static final String SizeList = "size_list";
  public static final String SizeMap = "size_map";
  public static final String SizeStringInst = "string_size";
  public static final String SizeBytesInst = "bytes_size";
  public static final String SizeListInst = "list_size";
  public static final String SizeMapInst = "map_size";

  // String function names.
  public static final String Contains = "contains";
  public static final String EndsWith = "endsWith";
  public static final String Matches = "matches";
  public static final String StartsWith = "startsWith";

  // String function overload names.
  public static final String ContainsString = "contains_string";
  public static final String EndsWithString = "ends_with_string";
  public static final String MatchesString = "matches_string";
  public static final String StartsWithString = "starts_with_string";

  // Time-based functions.
  public static final String TimeGetFullYear = "getFullYear";
  public static final String TimeGetMonth = "getMonth";
  public static final String TimeGetDayOfYear = "getDayOfYear";
  public static final String TimeGetDate = "getDate";
  public static final String TimeGetDayOfMonth = "getDayOfMonth";
  public static final String TimeGetDayOfWeek = "getDayOfWeek";
  public static final String TimeGetHours = "getHours";
  public static final String TimeGetMinutes = "getMinutes";
  public static final String TimeGetSeconds = "getSeconds";
  public static final String TimeGetMilliseconds = "getMilliseconds";

  // Timestamp overloads for time functions without timezones.
  public static final String TimestampToYear = "timestamp_to_year";
  public static final String TimestampToMonth = "timestamp_to_month";
  public static final String TimestampToDayOfYear = "timestamp_to_day_of_year";
  public static final String TimestampToDayOfMonthZeroBased = "timestamp_to_day_of_month";
  public static final String TimestampToDayOfMonthOneBased = "timestamp_to_day_of_month_1_based";
  public static final String TimestampToDayOfWeek = "timestamp_to_day_of_week";
  public static final String TimestampToHours = "timestamp_to_hours";
  public static final String TimestampToMinutes = "timestamp_to_minutes";
  public static final String TimestampToSeconds = "timestamp_to_seconds";
  public static final String TimestampToMilliseconds = "timestamp_to_milliseconds";

  // Timestamp overloads for time functions with timezones.
  public static final String TimestampToYearWithTz = "timestamp_to_year_with_tz";
  public static final String TimestampToMonthWithTz = "timestamp_to_month_with_tz";
  public static final String TimestampToDayOfYearWithTz = "timestamp_to_day_of_year_with_tz";
  public static final String TimestampToDayOfMonthZeroBasedWithTz =
      "timestamp_to_day_of_month_with_tz";
  public static final String TimestampToDayOfMonthOneBasedWithTz =
      "timestamp_to_day_of_month_1_based_with_tz";
  public static final String TimestampToDayOfWeekWithTz = "timestamp_to_day_of_week_with_tz";
  public static final String TimestampToHoursWithTz = "timestamp_to_hours_with_tz";
  public static final String TimestampToMinutesWithTz = "timestamp_to_minutes_with_tz";
  public static final String TimestampToSecondsWithTz = "timestamp_to_seconds_tz";
  public static final String TimestampToMillisecondsWithTz = "timestamp_to_milliseconds_with_tz";

  // Duration overloads for time functions.
  public static final String DurationToHours = "duration_to_hours";
  public static final String DurationToMinutes = "duration_to_minutes";
  public static final String DurationToSeconds = "duration_to_seconds";
  public static final String DurationToMilliseconds = "duration_to_milliseconds";

  // Type conversion methods and overloads
  public static final String TypeConvertInt = "int";
  public static final String TypeConvertUint = "uint";
  public static final String TypeConvertDouble = "double";
  public static final String TypeConvertBool = "bool";
  public static final String TypeConvertString = "string";
  public static final String TypeConvertBytes = "bytes";
  public static final String TypeConvertTimestamp = "timestamp";
  public static final String TypeConvertDuration = "duration";
  public static final String TypeConvertType = "type";
  public static final String TypeConvertDyn = "dyn";

  // Int conversion functions.
  public static final String IntToInt = "int64_to_int64";
  public static final String UintToInt = "uint64_to_int64";
  public static final String DoubleToInt = "double_to_int64";
  public static final String StringToInt = "string_to_int64";
  public static final String TimestampToInt = "timestamp_to_int64";
  public static final String DurationToInt = "duration_to_int64";

  // Uint conversion functions.
  public static final String UintToUint = "uint64_to_uint64";
  public static final String IntToUint = "int64_to_uint64";
  public static final String DoubleToUint = "double_to_uint64";
  public static final String StringToUint = "string_to_uint64";

  // Double conversion functions.
  public static final String DoubleToDouble = "double_to_double";
  public static final String IntToDouble = "int64_to_double";
  public static final String UintToDouble = "uint64_to_double";
  public static final String StringToDouble = "string_to_double";

  // Bool conversion functions.
  public static final String BoolToBool = "bool_to_bool";
  public static final String StringToBool = "string_to_bool";

  // Bytes conversion functions.
  public static final String BytesToBytes = "bytes_to_bytes";
  public static final String StringToBytes = "string_to_bytes";

  // String conversion functions.
  public static final String StringToString = "string_to_string";
  public static final String BoolToString = "bool_to_string";
  public static final String IntToString = "int64_to_string";
  public static final String UintToString = "uint64_to_string";
  public static final String DoubleToString = "double_to_string";
  public static final String BytesToString = "bytes_to_string";
  public static final String TimestampToString = "timestamp_to_string";
  public static final String DurationToString = "duration_to_string";

  // Timestamp conversion functions
  public static final String TimestampToTimestamp = "timestamp_to_timestamp";
  public static final String StringToTimestamp = "string_to_timestamp";
  public static final String IntToTimestamp = "int64_to_timestamp";

  // Convert duration from string
  public static final String DurationToDuration = "duration_to_duration";
  public static final String StringToDuration = "string_to_duration";
  public static final String IntToDuration = "int64_to_duration";

  // Convert to dyn
  public static final String ToDyn = "to_dyn";

  // Comprehensions helper methods, not directly accessible via a developer.
  public static final String Iterator = "@iterator";
  public static final String HasNext = "@hasNext";
  public static final String Next = "@next";

  // IsTypeConversionFunction returns whether the input function is a standard library type
  // conversion function.
  public static boolean isTypeConversionFunction(String function) {
    switch (function) {
      case TypeConvertBool:
      case TypeConvertBytes:
      case TypeConvertDouble:
      case TypeConvertDuration:
      case TypeConvertDyn:
      case TypeConvertInt:
      case TypeConvertString:
      case TypeConvertTimestamp:
      case TypeConvertType:
      case TypeConvertUint:
        return true;
      default:
        return false;
    }
  }
}
