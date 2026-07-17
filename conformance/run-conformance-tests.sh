#!/usr/bin/env bash
#
# Copyright (C) 2021 The Authors of CEL-Java
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

wd="$(dirname $0)"

cd "${wd}/.." || exit 1

./gradlew :cel-conformance:shadowJar || exit 1

cel_spec_dir="$(realpath submodules/cel-spec)"
cd "${cel_spec_dir}" || exit 1

cel_java_skips=(
  # Without the checker, it is quite difficult to verify whether an assignment is allowed (by
  # the CEL spec), especially from a 'map' to a 'struct' as in these tests using the expression
  # `TestAllTypes{single_struct: {1: 'uno'}}`. Note: the checker catches this case and the
  # Java implementation currently converts the 'int(1)' to a 'string("1")', which is not strictly
  # allowed, but OTOH overall not a serious issue.
  "--skip_test=dynamic/struct/field_assign_proto2_bad"
  "--skip_test=dynamic/struct/field_assign_proto3_bad"
  # The test expects a -0.0d, but in Java `-0.0d==0.0d` is true, so -0.0d is evaluates as "not set",
  # so it returns the field as empty.
  "--skip_test=dynamic/float/field_assign_proto3_round_to_zero"
  # "Malicious" protobuf message. The actual CEL-spec test produces a request with a too deeply
  # nested protobuf-object-structure, which gets rejected during gRPC/protobuf request
  # deserialization. Just skip those tests.
  "--skip_test=parse/nest/message_literal"
  # Proto equality specialties don't seem to be in effect for Java
  "--skip_test=comparisons/eq_wrapper/eq_proto_nan_equal"
  "--skip_test=comparisons/ne_literal/ne_proto_nan_not_equal"

  # TODO Actual known issue to fix, a protobuf Any returned via this test is wrapped twice (Any in Any).
  "--skip_test=dynamic/any/var"

  # New CEL-Spec v0.25.2 expectations that need follow-up CEL-Java parser/runtime changes.
  "--skip_test=conversions/bool/string_1,string_t,string_0,string_f,string_true_badcase,string_false_badcase"
  "--skip_test=fields/quoted_map_fields/field_access_slash,field_access_dash,field_access_dot,has_field_slash,has_field_dash,has_field_dot"
  "--skip_test=namespace/namespace_shadowing/comprehension_shadowing,comprehension_shadowing_disambiguation,comprehension_shadowing_parse_only,comprehension_shadowing_selector,comprehension_shadowing_selector_parse_only,comprehension_shadowing_namespaced_selector,comprehension_shadowing_namespaced_selector_parse_only,comprehension_shadowing_nesting"
  "--skip_test=proto2/set_null/single_message,single_duration,single_timestamp,repeated_field_timestamp_null_pruned,repeated_field_duration_null_pruned,repeated_field_wrapper_null_pruned,map_timestamp_null_pruned,map_duration_null_pruned,map_wrapper_null_pruned,map_anytype_null_retained,single_scalar,repeated,map,list_value,single_struct"
  "--skip_test=proto2/quoted_fields/set_field_with_quoted_name,get_field_with_quoted_name"
  "--skip_test=proto2/extensions_has/package_scoped_int32,package_scoped_nested_ext,package_scoped_test_all_types_ext,package_scoped_test_all_types_nested_enum_ext,package_scoped_repeated_test_all_types,message_scoped_int64,message_scoped_nested_ext,message_scoped_nested_enum_ext,message_scoped_repeated_test_all_types"
  "--skip_test=proto2/extensions_get/package_scoped_int32,package_scoped_nested_ext,package_scoped_test_all_types_ext,package_scoped_test_all_types_nested_enum_ext,package_scoped_repeated_test_all_types,message_scoped_int64,message_scoped_nested_ext,message_scoped_nested_enum_ext,message_scoped_repeated_test_all_types"
  "--skip_test=proto3/set_null/single_message,single_duration,single_timestamp,repeated_field_timestamp_null_pruned,repeated_field_duration_null_pruned,repeated_field_wrapper_null_pruned,map_timestamp_null_pruned,map_duration_null_pruned,map_wrapper_null_pruned,map_anytype_null_retained,single_scalar,repeated,map,list_value,single_struct"
  "--skip_test=proto3/quoted_fields/set_field,get_field"
  "--skip_test=timestamps/timestamp_conversions/type_comparison"
  "--skip_test=timestamps/duration_conversions/type_comparison"
  "--skip_test=wrappers/bool/to_null"
  "--skip_test=wrappers/int32/to_null"
  "--skip_test=wrappers/int64/to_null"
  "--skip_test=wrappers/uint32/to_null"
  "--skip_test=wrappers/uint64/to_null"
  "--skip_test=wrappers/float/to_null"
  "--skip_test=wrappers/double/to_null"
  "--skip_test=wrappers/bytes/to_null"
  "--skip_test=wrappers/string/to_null"
)

cel_go_skips=(
  "--skip_test=dynamic/int32/field_assign_proto2_range,field_assign_proto3_range"
  "--skip_test=dynamic/uint32/field_assign_proto2_range,field_assign_proto3_range"
  "--skip_test=dynamic/float/field_assign_proto2_range,field_assign_proto3_range"
  "--skip_test=enums/legacy_proto2/assign_standalone_int_too_big,assign_standalone_int_too_neg"
  "--skip_test=enums/legacy_proto3/assign_standalone_int_too_big,assign_standalone_int_too_neg"
  "--skip_test=enums/strong_proto2"
  "--skip_test=enums/strong_proto3"
  # This conformance test is invalid nowadays
  "--skip_test=fields/qualified_identifier_resolution/map_key_float"
  # Unclear why the 'to_json_string' is expected to return a string, unlike the preceding to_json_number test.
  "--skip_test=wrappers/uint64/to_json_string"
  # TODO implement proper "toJson" at some point
  "--skip_test=wrappers/field_mask/to_json"
  "--skip_test=wrappers/timestamp/to_json"
  "--skip_test=wrappers/empty/to_json"
)

test_files=(
  "tests/simple/testdata/basic.textproto"
  "tests/simple/testdata/comparisons.textproto"
  "tests/simple/testdata/conversions.textproto"
  "tests/simple/testdata/dynamic.textproto"
  "tests/simple/testdata/enums.textproto"
  "tests/simple/testdata/fields.textproto"
  "tests/simple/testdata/fp_math.textproto"
  "tests/simple/testdata/integer_math.textproto"
  "tests/simple/testdata/lists.textproto"
  "tests/simple/testdata/logic.textproto"
  "tests/simple/testdata/macros.textproto"
  "tests/simple/testdata/namespace.textproto"
  "tests/simple/testdata/parse.textproto"
  "tests/simple/testdata/plumbing.textproto"
  "tests/simple/testdata/proto2.textproto"
  "tests/simple/testdata/proto3.textproto"
  "tests/simple/testdata/string.textproto"
  # TODO add when implemnting the string-extensions "tests/simple/testdata/string_ext.textproto"
  "tests/simple/testdata/timestamps.textproto"
  "tests/simple/testdata/unknowns.textproto"
  "tests/simple/testdata/wrappers.textproto"
)

java -cp ../../conformance/build/libs/*-all.jar \
  org.projectnessie.cel.server.SimpleConformanceTestRunner \
  "${cel_java_skips[@]}" \
  "${cel_go_skips[@]}" \
  "${test_files[@]}"
code=$?

exit $code
