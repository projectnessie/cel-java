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
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.NESSIE_RULES_DENY;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.ORG_BINDINGS_ALL_8_X_8;

import org.junit.jupiter.api.Test;
import org.projectnessie.cel.RealWorldProgramSet.Lifecycle;
import org.projectnessie.cel.benchmark.RealWorldHostFixtures;
import org.projectnessie.cel.benchmark.RealWorldPojoFixtures;
import org.projectnessie.cel.benchmark.RealWorldProtoFixtures;
import org.projectnessie.cel.benchmark.RealWorldWorkloads;

class RealWorldProgramLifecycleTest {
  @Test
  void hostLifecycleRetainsMultiProgramRuleSet() {
    var scenario = RealWorldWorkloads.scenario(NESSIE_RULES_DENY);
    Lifecycle lifecycle =
        RealWorldProgramSet.hostLifecycle(scenario, RealWorldHostFixtures.prepare(scenario));

    assertThat(lifecycle.checkExact()).hasSize(11);
    assertThat(lifecycle.checkGeneral()).hasSize(11);
    assertThat(lifecycle.planExactNative()).hasSize(11);
    assertThat(lifecycle.planExactDisabled()).hasSize(11);
    assertThat(lifecycle.planGeneral()).hasSize(11);
    assertThat(lifecycle.coldExactNative()).isFalse();
    assertThat(lifecycle.coldExactDisabled()).isFalse();
    assertThat(lifecycle.coldGeneral()).isFalse();
  }

  @Test
  void protobufLifecycleProducesEquivalentResult() {
    var scenario = RealWorldWorkloads.scenario(ORG_BINDINGS_ALL_8_X_8);
    Lifecycle lifecycle =
        RealWorldProgramSet.protobufLifecycle(
            scenario,
            RealWorldProtoFixtures.prepare(scenario),
            RealWorldProtoFixtures.registeredTypes(scenario.family()));

    assertThat(lifecycle.coldExactNative()).isTrue();
    assertThat(lifecycle.coldExactDisabled()).isTrue();
    assertThat(lifecycle.coldGeneral()).isTrue();
  }

  @Test
  void jackson3LifecycleProducesEquivalentResult() {
    var scenario = RealWorldWorkloads.scenario(ORG_BINDINGS_ALL_8_X_8);
    Lifecycle lifecycle =
        RealWorldProgramSet.jackson3Lifecycle(
            scenario,
            RealWorldPojoFixtures.prepare(scenario),
            RealWorldPojoFixtures.registeredTypes(scenario.family()));

    assertThat(lifecycle.coldExactNative()).isTrue();
    assertThat(lifecycle.coldExactDisabled()).isTrue();
    assertThat(lifecycle.coldGeneral()).isTrue();
  }
}
