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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ResourceLimitsTest {
  @Test
  void unlimitedHasNoFiniteLimits() {
    var limits = ResourceLimits.unlimited();

    assertThat(limits).isSameAs(ResourceLimits.newBuilder().build());
    assertThat(limits.getElapsedTimeLimit()).isEmpty();
    assertThat(limits.getCpuTimeLimit()).isEmpty();
    assertThat(limits.getAllocatedBytesLimit()).isEmpty();
    assertThat(limits.getAstNodeLimit()).isEmpty();
    assertThat(limits.getAstDepthLimit()).isEmpty();
    assertThat(limits.getAstMetadataEntryLimit()).isEmpty();
  }

  @Test
  void builderProducesIndependentSnapshots() {
    var builder = ResourceLimits.newBuilder().elapsedTimeLimit(Duration.ofNanos(12));
    var first = builder.build();
    var second = builder.elapsedTimeLimit(Duration.ofNanos(34)).allocatedBytesLimit(56).build();

    assertThat(first.getElapsedTimeLimit()).contains(Duration.ofNanos(12));
    assertThat(first.getAllocatedBytesLimit()).isEmpty();
    assertThat(second.getElapsedTimeLimit()).contains(Duration.ofNanos(34));
    assertThat(second.getAllocatedBytesLimit()).hasValue(56);
  }

  @Test
  void validatesLimits() {
    assertThatThrownBy(() -> ResourceLimits.newBuilder().elapsedTimeLimit(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> ResourceLimits.newBuilder().elapsedTimeLimit(Duration.ofSeconds(Long.MAX_VALUE)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ResourceLimits.newBuilder().cpuTimeLimit(Duration.ofNanos(-1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ResourceLimits.newBuilder().allocatedBytesLimit(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ResourceLimits.newBuilder().astNodeLimit(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ResourceLimits.newBuilder().astDepthLimit(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ResourceLimits.newBuilder().astMetadataEntryLimit(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
