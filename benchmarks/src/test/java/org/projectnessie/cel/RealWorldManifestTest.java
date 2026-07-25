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
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.Representation.HOST;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.Representation.JACKSON3;
import static org.projectnessie.cel.benchmark.RealWorldWorkloads.Representation.PROTOBUF;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.benchmark.RealWorldWorkloads;
import org.projectnessie.cel.benchmark.RealWorldWorkloads.Scenario;

class RealWorldManifestTest {
  @Test
  void retainedCardinalityAndUniqueIds() {
    assertThat(RealWorldWorkloads.hostScenarios()).hasSize(21);
    assertThat(RealWorldWorkloads.pairedScenarios()).hasSize(39);
    assertThat(
            Stream.concat(
                    RealWorldWorkloads.hostScenarios().stream(),
                    RealWorldWorkloads.pairedScenarios().stream())
                .map(Scenario::id))
        .doesNotHaveDuplicates();
  }

  @Test
  void representationContractsAreComplete() {
    assertThat(RealWorldWorkloads.hostScenarios())
        .allSatisfy(
            scenario -> {
              assertThat(scenario.representations()).containsExactly(HOST);
              assertThat(scenario.expressions(HOST)).isNotEmpty();
            });
    assertThat(RealWorldWorkloads.pairedScenarios())
        .allSatisfy(
            scenario -> {
              assertThat(scenario.representations()).containsExactlyInAnyOrder(PROTOBUF, JACKSON3);
              assertThat(scenario.expressions(PROTOBUF)).isNotEmpty();
              assertThat(scenario.expressions(JACKSON3)).isNotEmpty();
            });
  }

  @Test
  void metadataRequiredForReproductionIsPresent() {
    assertThat(
            Stream.concat(
                RealWorldWorkloads.hostScenarios().stream(),
                RealWorldWorkloads.pairedScenarios().stream()))
        .allSatisfy(
            scenario -> {
              assertThat(scenario.sourceExpressions()).isNotEmpty();
              assertThat(scenario.resultClass()).isEqualTo(Boolean.class);
              assertThat(scenario.adaptationNote()).isNotBlank();
              assertThat(scenario.provenanceUrl()).startsWith("https://");
            });
  }
}
