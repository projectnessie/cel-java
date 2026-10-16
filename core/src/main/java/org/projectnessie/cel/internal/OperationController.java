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
package org.projectnessie.cel.internal;

import java.util.Objects;
import java.util.function.BiFunction;
import org.projectnessie.cel.OperationAbortedException;
import org.projectnessie.cel.OperationAbortedException.Phase;
import org.projectnessie.cel.OperationAbortedException.Reason;
import org.projectnessie.cel.OperationAbortedException.Resource;
import org.projectnessie.cel.ResourceLimits;

/**
 * Internal state for one controlled operation.
 *
 * <p>This type is public only for communication between CEL-Java packages. It is not a supported
 * application API.
 */
public final class OperationController {
  private static final int POLL_INTERVAL = 64;
  private static final OperationController NONE = new OperationController();

  private final ResourceLimits limits;
  private final long elapsedTimeLimit;
  private final long cpuTimeLimit;
  private final long allocatedBytesLimit;
  private final MonotonicClock clock;
  private final BiFunction<ResourceLimits, Phase, ThreadResourceMeter> meterFactory;
  private final boolean controlled;

  private volatile boolean cancelled;
  private long threadId = -1;
  private long elapsedStart;
  private long cpuStart;
  private long allocatedStart;
  private ThreadResourceMeter meter;
  private int countdown = POLL_INTERVAL;
  private Phase phase;
  private Phase phaseOverride;

  private OperationController() {
    limits = ResourceLimits.unlimited();
    elapsedTimeLimit = -1;
    cpuTimeLimit = -1;
    allocatedBytesLimit = -1;
    clock = System::nanoTime;
    meterFactory = ManagementThreadResourceMeter::create;
    controlled = false;
  }

  /** Creates an operation controller using production measurement sources. */
  public OperationController(ResourceLimits limits) {
    this(limits, System::nanoTime, ManagementThreadResourceMeter::create);
  }

  /** Creates an operation controller with injectable sources for repository tests. */
  public OperationController(
      ResourceLimits limits,
      MonotonicClock clock,
      BiFunction<ResourceLimits, Phase, ThreadResourceMeter> meterFactory) {
    this.limits = Objects.requireNonNull(limits, "limits");
    elapsedTimeLimit = limits.getElapsedTimeLimit().map(java.time.Duration::toNanos).orElse(-1L);
    cpuTimeLimit = limits.getCpuTimeLimit().map(java.time.Duration::toNanos).orElse(-1L);
    allocatedBytesLimit = limits.getAllocatedBytesLimit().orElse(-1);
    this.clock = Objects.requireNonNull(clock, "clock");
    this.meterFactory = Objects.requireNonNull(meterFactory, "meterFactory");
    controlled = true;
  }

  /** Returns the shared unrestricted controller. */
  public static OperationController none() {
    return NONE;
  }

  /** Reports whether this controller represents a controlled operation. */
  public boolean isControlled() {
    return controlled;
  }

  /** Starts measurement on the current thread. */
  public void begin(Phase initialPhase) {
    if (!controlled) {
      return;
    }
    phase = Objects.requireNonNull(initialPhase, "initialPhase");
    threadId = Thread.currentThread().getId();
    checkCancellation();
    if (elapsedTimeLimit >= 0) {
      elapsedStart = clock.nanoTime();
    }
    if (cpuTimeLimit >= 0 || allocatedBytesLimit >= 0) {
      meter = meterFactory.apply(limits, phase);
      if (cpuTimeLimit >= 0) {
        cpuStart = reading(meter.cpuTimeNanos(threadId), Resource.THREAD_CPU_TIME);
      }
      if (allocatedBytesLimit >= 0) {
        allocatedStart = reading(meter.allocatedBytes(threadId), Resource.THREAD_ALLOCATED_BYTES);
      }
    }
    checkpointNow(initialPhase);
  }

  /** Requests cooperative cancellation. */
  public void cancel() {
    if (controlled) {
      cancelled = true;
    }
  }

  /** Performs a cheap checkpoint and periodically polls finite limits. */
  public void checkpoint(Phase currentPhase) {
    if (!controlled) {
      return;
    }
    assertThread();
    phase = effectivePhase(currentPhase);
    checkCancellation();
    if (--countdown <= 0) {
      poll();
    }
  }

  /** Performs a cheap checkpoint without changing the current phase. */
  public void checkpoint() {
    if (!controlled) {
      return;
    }
    checkpoint(phase);
  }

  /** Performs an immediate cancellation and finite-limit poll. */
  public void checkpointNow(Phase currentPhase) {
    if (!controlled) {
      return;
    }
    assertThread();
    phase = effectivePhase(currentPhase);
    checkCancellation();
    poll();
  }

  /** Performs an immediate checkpoint without changing the current phase. */
  public void checkpointNow() {
    if (!controlled) {
      return;
    }
    checkpointNow(phase);
  }

  /** Returns the immutable limits associated with this operation. */
  public ResourceLimits limits() {
    return limits;
  }

  /** Temporarily attributes all checkpoints to one construction phase. */
  public PhaseScope overridePhase(Phase forcedPhase) {
    if (!controlled) {
      return PhaseScope.NONE;
    }
    assertThread();
    var previous = phaseOverride;
    var previousPhase = phase;
    phaseOverride = Objects.requireNonNull(forcedPhase, "forcedPhase");
    phase = forcedPhase;
    return () -> {
      phaseOverride = previous;
      phase = previousPhase;
    };
  }

  private Phase effectivePhase(Phase requested) {
    Objects.requireNonNull(requested, "currentPhase");
    return phaseOverride != null ? phaseOverride : requested;
  }

  private void poll() {
    countdown = POLL_INTERVAL;
    if (elapsedTimeLimit >= 0) {
      var observed = clock.nanoTime() - elapsedStart;
      if (observed > elapsedTimeLimit) {
        throw limit(Reason.ELAPSED_TIME_LIMIT, Resource.ELAPSED_TIME, elapsedTimeLimit, observed);
      }
    }
    if (cpuTimeLimit >= 0) {
      var current = reading(meter.cpuTimeNanos(threadId), Resource.THREAD_CPU_TIME);
      var observed = delta(current, cpuStart, Resource.THREAD_CPU_TIME);
      if (observed > cpuTimeLimit) {
        throw limit(Reason.CPU_TIME_LIMIT, Resource.THREAD_CPU_TIME, cpuTimeLimit, observed);
      }
    }
    if (allocatedBytesLimit >= 0) {
      var current = reading(meter.allocatedBytes(threadId), Resource.THREAD_ALLOCATED_BYTES);
      var observed = delta(current, allocatedStart, Resource.THREAD_ALLOCATED_BYTES);
      if (observed > allocatedBytesLimit) {
        throw limit(
            Reason.ALLOCATED_BYTES_LIMIT,
            Resource.THREAD_ALLOCATED_BYTES,
            allocatedBytesLimit,
            observed);
      }
    }
  }

  private void checkCancellation() {
    if (cancelled) {
      throw new OperationAbortedException(Reason.EXPLICIT_CANCELLATION, phase);
    }
    if (Thread.currentThread().isInterrupted()) {
      throw new OperationAbortedException(Reason.THREAD_INTERRUPTED, phase);
    }
  }

  private void assertThread() {
    if (Thread.currentThread().getId() != threadId) {
      throw new IllegalStateException("controlled CEL operation checkpoint changed threads");
    }
  }

  private long reading(long value, Resource resource) {
    if (value < 0) {
      throw unavailable(resource);
    }
    return value;
  }

  private long delta(long current, long baseline, Resource resource) {
    if (current < baseline) {
      throw unavailable(resource);
    }
    return current - baseline;
  }

  private OperationAbortedException unavailable(Resource resource) {
    return new OperationAbortedException(Reason.MEASUREMENT_UNAVAILABLE, phase, resource, -1, -1);
  }

  private OperationAbortedException limit(
      Reason reason, Resource resource, long limit, long observed) {
    return new OperationAbortedException(reason, phase, resource, limit, observed);
  }

  /** Internal non-throwing scope for a temporary phase override. */
  @FunctionalInterface
  public interface PhaseScope extends AutoCloseable {
    PhaseScope NONE = () -> {};

    @Override
    void close();
  }
}
