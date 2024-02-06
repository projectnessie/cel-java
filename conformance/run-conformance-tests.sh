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

server_pid_file="$(realpath ./conformance/conformance-server.pid)"

cd submodules/cel-spec || exit 1

# Bazel version 6.4.0 works, 7.0.2 does not work with the conformance tests
export USE_BAZEL_VERSION="6.5.0"

bazel build ... || exit 1

cel_java_skips=(
  # proto2 enums are generated as Java enums, means: it is not possible to assign arbitrary
  # ordinals, so these tests cannot work against the CEL-Java implementation (limitation of the
  # protobuf/Java implementation for proto2).
  "--skip_test=enums/legacy_proto2/select_big,select_neg,assign_standalone_int_big,assign_standalone_int_neg"
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

bazel-bin/tests/simple/simple_test_/simple_test \
  --server ../../conformance/conformance-server.sh \
  "${cel_java_skips[@]}" \
  "${cel_go_skips[@]}" \
  "${test_files[@]}"
code=$?

if [[ -f ${server_pid_file} ]] ; then
  kill "$(cat "${server_pid_file}")" && rm "${server_pid_file}" || exit 1
fi

exit $code
