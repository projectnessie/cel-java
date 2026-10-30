/*
 * Copyright (C) 2026 The Authors of CEL-Java
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
package org.projectnessie.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_DEPOSIT;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.DAPR_TYPE;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.Family.DAPR_HOST;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.Family.NESSIE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.projectnessie.cel.RealWorldProgramSet.ProgramModes;
import org.projectnessie.cel.benchmark.PreparedFixture;
import org.projectnessie.cel.benchmark.RealWorldHostFixtures;
import org.projectnessie.cel.benchmark.RealWorldWorkloads;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.StringT;

class RealWorldHostWorkloadTest {
  @TestFactory
  Stream<DynamicTest> retainedHostScenarios() {
    return RealWorldWorkloads.hostScenarios().stream()
        .map(
            scenario ->
                DynamicTest.dynamicTest(
                    scenario.id(),
                    () -> {
                      PreparedFixture fixture = RealWorldHostFixtures.prepare(scenario);
                      RealWorldProgramSet programs = RealWorldProgramSet.host(scenario, fixture);
                      assertThat(programs.exactNative())
                          .isInstanceOf(Boolean.class)
                          .isEqualTo(scenario.expected());
                      assertThat(programs.exactDisabled()).isEqualTo(scenario.expected());
                      assertThat(programs.general()).isEqualTo(scenario.expected());
                      assertThat(programs.direct()).isEqualTo(scenario.expected());
                    }));
  }

  @Test
  void invalidDaprAmountIsCelErrorInEveryMode() {
    ProgramModes modes = RealWorldProgramSet.hostPrograms(DAPR_HOST, DAPR_DEPOSIT);
    Map<String, Object> activation =
        Map.of("event", Map.of("type", "deposit", "data", Map.of("amount", "not-a-number")));
    assertCelErrors(modes, activation);
  }

  @Test
  void missingTopLevelVariableIsCelErrorInEveryMode() {
    ProgramModes modes = RealWorldProgramSet.hostPrograms(DAPR_HOST, DAPR_TYPE);
    assertCelErrors(modes, Map.of());
  }

  @Test
  void nonBooleanResultFailsNativeConversionInEveryMode() {
    ProgramModes modes = RealWorldProgramSet.hostPrograms(NESSIE, "'not-a-boolean'");
    Map<String, Object> activation =
        Map.of(
            "op", "",
            "role", "",
            "roles", List.of(),
            "ref", "",
            "path", "",
            "contentType", "");
    List<Class<?>> failureClasses = new ArrayList<>();
    for (Program program : List.of(modes.exactNative(), modes.exactDisabled(), modes.general())) {
      assertThat(program.eval(activation).getVal()).isEqualTo(StringT.stringOf("not-a-boolean"));
      Throwable failure = catchThrowable(() -> RealWorldProgramSet.evaluate(program, activation));
      assertThat(failure).isInstanceOf(RuntimeException.class);
      failureClasses.add(failure.getClass());
    }
    assertThat(failureClasses).containsOnly(failureClasses.get(0));
  }

  private static void assertCelErrors(ProgramModes modes, Map<String, Object> activation) {
    for (Program program : List.of(modes.exactNative(), modes.exactDisabled(), modes.general())) {
      assertThat(Err.isError(program.eval(activation).getVal())).isTrue();
    }
  }
}
