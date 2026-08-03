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
package org.projectnessie.cel.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.OperationAbortedException.Phase.EVALUATE;
import static org.projectnessie.cel.OperationAbortedException.Phase.OPTIMIZE;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.OperationAbortedException;
import org.projectnessie.cel.OperationAbortedException.Reason;
import org.projectnessie.cel.ResourceLimits;

class OperationControllerTest {
  @Test
  void elapsedTimeUsesMonotonicDelta() {
    var now = new AtomicLong(100);
    var controller =
        new OperationController(
            ResourceLimits.newBuilder().elapsedTimeLimit(Duration.ofNanos(10)).build(),
            now::get,
            (limits, phase) -> new FixedMeter(0, 0));

    controller.begin(EVALUATE);
    now.set(111);

    assertThatThrownBy(() -> controller.checkpointNow(EVALUATE))
        .isInstanceOfSatisfying(
            OperationAbortedException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(Reason.ELAPSED_TIME_LIMIT);
              assertThat(e.getLimit()).hasValue(10);
              assertThat(e.getObserved()).hasValue(11);
            });
  }

  @Test
  void elapsedLimitIsInclusiveAndNanoTimeWrapUsesSignedSubtraction() {
    var now = new AtomicLong(Long.MAX_VALUE - 5);
    var controller =
        new OperationController(
            ResourceLimits.newBuilder().elapsedTimeLimit(Duration.ofNanos(11)).build(),
            now::get,
            (limits, phase) -> new FixedMeter(0, 0));

    controller.begin(EVALUATE);
    now.set(Long.MIN_VALUE + 5);
    controller.checkpointNow(EVALUATE);
    now.incrementAndGet();

    assertThatThrownBy(() -> controller.checkpointNow(EVALUATE))
        .isInstanceOfSatisfying(
            OperationAbortedException.class, e -> assertThat(e.getObserved()).hasValue(12));
  }

  @Test
  void cheapCheckpointsPollEverySixtyFourInvocations() {
    var clockReads = new AtomicInteger();
    var controller =
        new OperationController(
            ResourceLimits.newBuilder().elapsedTimeLimit(Duration.ofSeconds(1)).build(),
            clockReads::getAndIncrement,
            (limits, phase) -> new FixedMeter(0, 0));

    controller.begin(EVALUATE);
    assertThat(clockReads).hasValue(2);
    for (int i = 0; i < 63; i++) {
      controller.checkpoint(EVALUATE);
    }
    assertThat(clockReads).hasValue(2);
    controller.checkpoint(EVALUATE);
    assertThat(clockReads).hasValue(3);
  }

  @Test
  void phaseOverrideAttributesInternalEvaluationToOptimizer() {
    var now = new AtomicLong();
    var controller =
        new OperationController(
            ResourceLimits.newBuilder().elapsedTimeLimit(Duration.ofNanos(1)).build(),
            now::get,
            (limits, phase) -> new FixedMeter(0, 0));
    controller.begin(OPTIMIZE);
    now.set(2);

    try (var ignored = controller.overridePhase(OPTIMIZE)) {
      assertThatThrownBy(() -> controller.checkpointNow(EVALUATE))
          .isInstanceOfSatisfying(
              OperationAbortedException.class, e -> assertThat(e.getPhase()).isEqualTo(OPTIMIZE));
    }
  }

  @Test
  void cpuAndAllocationUseOperationBaselines() {
    var cpu = new AtomicLong(40);
    var allocated = new AtomicLong(80);
    var limits =
        ResourceLimits.newBuilder()
            .cpuTimeLimit(Duration.ofNanos(5))
            .allocatedBytesLimit(100)
            .build();
    var controller =
        new OperationController(
            limits,
            () -> 0,
            (ignored, phase) ->
                new ThreadResourceMeter() {
                  @Override
                  public long cpuTimeNanos(long threadId) {
                    return cpu.get();
                  }

                  @Override
                  public long allocatedBytes(long threadId) {
                    return allocated.get();
                  }
                });

    controller.begin(EVALUATE);
    cpu.set(46);

    assertThatThrownBy(() -> controller.checkpointNow(EVALUATE))
        .isInstanceOfSatisfying(
            OperationAbortedException.class,
            e -> assertThat(e.getReason()).isEqualTo(Reason.CPU_TIME_LIMIT));
  }

  @Test
  void allocationUsesItsOwnOperationBaseline() {
    var allocated = new AtomicLong(1_000);
    var controller =
        new OperationController(
            ResourceLimits.newBuilder().allocatedBytesLimit(5).build(),
            () -> 0,
            (limits, phase) -> new FixedMeter(new AtomicLong(), allocated));

    controller.begin(EVALUATE);
    allocated.set(1_006);

    assertThatThrownBy(() -> controller.checkpointNow(EVALUATE))
        .isInstanceOfSatisfying(
            OperationAbortedException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(Reason.ALLOCATED_BYTES_LIMIT);
              assertThat(e.getObserved()).hasValue(6);
            });
  }

  @Test
  void preCancellationDoesNotInitializeManagementMeter() {
    var factoryCalls = new AtomicInteger();
    var controller =
        new OperationController(
            ResourceLimits.newBuilder().allocatedBytesLimit(1).build(),
            () -> 0,
            (limits, phase) -> {
              factoryCalls.incrementAndGet();
              return new FixedMeter(0, 0);
            });
    controller.cancel();

    assertThatThrownBy(() -> controller.begin(EVALUATE))
        .isInstanceOfSatisfying(
            OperationAbortedException.class,
            e -> assertThat(e.getReason()).isEqualTo(Reason.EXPLICIT_CANCELLATION));
    assertThat(factoryCalls).hasValue(0);
  }

  @Test
  void preExistingInterruptionIsObservedAndPreserved() {
    var controller = new OperationController(ResourceLimits.unlimited());
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> controller.begin(EVALUATE))
          .isInstanceOfSatisfying(
              OperationAbortedException.class,
              e -> assertThat(e.getReason()).isEqualTo(Reason.THREAD_INTERRUPTED));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void negativeOrRegressingMeasurementsFailClosed() {
    var negative =
        new OperationController(
            ResourceLimits.newBuilder().cpuTimeLimit(Duration.ofNanos(1)).build(),
            () -> 0,
            (limits, phase) -> new FixedMeter(-1, 0));
    assertThatThrownBy(() -> negative.begin(EVALUATE))
        .isInstanceOfSatisfying(
            OperationAbortedException.class,
            e -> assertThat(e.getReason()).isEqualTo(Reason.MEASUREMENT_UNAVAILABLE));

    var cpu = new AtomicLong(10);
    var regressing =
        new OperationController(
            ResourceLimits.newBuilder().cpuTimeLimit(Duration.ofNanos(100)).build(),
            () -> 0,
            (limits, phase) -> new FixedMeter(cpu, new AtomicLong()));
    regressing.begin(EVALUATE);
    cpu.set(9);
    assertThatThrownBy(() -> regressing.checkpointNow(EVALUATE))
        .isInstanceOfSatisfying(
            OperationAbortedException.class,
            e -> assertThat(e.getReason()).isEqualTo(Reason.MEASUREMENT_UNAVAILABLE));
  }

  @Test
  void controlledOperationRestoresScopeAfterArbitraryFailure() {
    var original = OperationScope.current();
    var controller = new OperationController(ResourceLimits.unlimited());
    var operation =
        new ControlledOperation<Object>(
            controller,
            EVALUATE,
            () -> {
              assertThat(OperationScope.current()).isSameAs(controller);
              throw new IllegalArgumentException("action failure");
            });

    assertThatThrownBy(operation::execute)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("action failure");
    assertThat(OperationScope.current()).isSameAs(original);
    assertThatThrownBy(operation::execute).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void checkpointsAreConfinedToTheExecutingThread() throws InterruptedException {
    var controller = new OperationController(ResourceLimits.unlimited());
    controller.begin(EVALUATE);
    var failure = new AtomicReference<Throwable>();
    var thread =
        new Thread(
            () -> {
              try {
                controller.checkpoint(EVALUATE);
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    thread.start();
    thread.join();

    assertThat(failure.get()).isInstanceOf(IllegalStateException.class);
  }

  private record FixedMeter(AtomicLong cpu, AtomicLong allocated) implements ThreadResourceMeter {
    FixedMeter(long cpu, long allocated) {
      this(new AtomicLong(cpu), new AtomicLong(allocated));
    }

    @Override
    public long cpuTimeNanos(long threadId) {
      return cpu.get();
    }

    @Override
    public long allocatedBytes(long threadId) {
      return allocated.get();
    }
  }
}
