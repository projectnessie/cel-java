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
import org.projectnessie.cel.interpreter.TestInterpreter.Program;
import org.projectnessie.cel.interpreter.TestInterpreter.TestCase;

@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Threads(2)
@Fork(2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class InterpreterBench {

  @State(Scope.Benchmark)
  public static class Case {
    Program program;

    @Param public TestInterpreterCase testCase;

    @Setup
    public void init() {
      TestCase test =
          Arrays.stream(TestInterpreter.testCases())
              .filter(tc -> tc.name == testCase)
              .findFirst()
              .orElseThrow(() -> new RuntimeException("No test case named '" + testCase + '\''));
      this.program = TestInterpreter.program(test, optimize());
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
