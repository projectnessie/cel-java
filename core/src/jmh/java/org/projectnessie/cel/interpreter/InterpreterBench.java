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

import static org.projectnessie.cel.interpreter.Interpreter.optimize;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.projectnessie.cel.interpreter.InterpreterTest.Program;
import org.projectnessie.cel.interpreter.InterpreterTest.TestCase;

@Warmup(iterations = 1, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class InterpreterBench {

  @State(Scope.Benchmark)
  public static class Case {
    Program program;

    @Param({
      "and_false_2nd",
      "call_no_args",
      "call_one_arg",
      "call_two_arg",
      "call_varargs",
      "call_ns_func",
      "call_ns_func_unchecked",
      "call_ns_func_in_pkg",
      "call_ns_func_unchecked_in_pkg",
      "timestamp_eq_timestamp",
      "timestamp_le_timestamp",
      "macro_has_pb3_field",
      "nested_proto_field_with_index",
      "parse_nest_message_literal",
      "select_pb3_wrapper_fields",
      "select_pb3_compare",
      "select_custom_pb3_compare"
    })
    public InterpreterTestCase testCase;

    @Setup
    public void init() {
      TestCase test =
          Arrays.stream(InterpreterTest.testCases())
              .filter(tc -> tc.name == testCase)
              .findFirst()
              .orElseThrow(() -> new RuntimeException("No test case named '" + testCase + '\''));
      this.program = InterpreterTest.program(test, optimize());
    }
  }

  @Benchmark
  @Threads(1)
  public void interpreterSingle(Case state) {
    state.program.interpretable.eval(state.program.activation);
  }

  @Benchmark
  public void interpreterParallel(Case state) {
    state.program.interpretable.eval(state.program.activation);
  }
}
