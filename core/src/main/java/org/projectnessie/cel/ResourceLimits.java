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
package org.projectnessie.cel;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Immutable limits for one controlled CEL operation.
 *
 * <p>Elapsed time is monotonic wall-clock time measured from the start of the operation's execution
 * method. CPU time and allocated bytes are measured for that executing thread when the JVM exposes
 * the corresponding management counters. Allocated bytes are not retained heap.
 *
 * <p>Limits are cooperative: exhaustion is observed at a CEL-Java checkpoint and may therefore
 * overshoot the configured value. Omitted limits are unlimited.
 */
public final class ResourceLimits {
  private static final long UNLIMITED = -1L;
  private static final ResourceLimits UNLIMITED_INSTANCE =
      new ResourceLimits(UNLIMITED, UNLIMITED, UNLIMITED, UNLIMITED, -1, UNLIMITED);

  private final long elapsedTimeNanos;
  private final long cpuTimeNanos;
  private final long allocatedBytes;
  private final long astNodes;
  private final int astDepth;
  private final long astMetadataEntries;

  private ResourceLimits(
      long elapsedTimeNanos,
      long cpuTimeNanos,
      long allocatedBytes,
      long astNodes,
      int astDepth,
      long astMetadataEntries) {
    this.elapsedTimeNanos = elapsedTimeNanos;
    this.cpuTimeNanos = cpuTimeNanos;
    this.allocatedBytes = allocatedBytes;
    this.astNodes = astNodes;
    this.astDepth = astDepth;
    this.astMetadataEntries = astMetadataEntries;
  }

  /** Returns a reusable value with no finite limits. */
  public static ResourceLimits unlimited() {
    return UNLIMITED_INSTANCE;
  }

  /** Returns a new builder whose limits are all unlimited. */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** Returns the elapsed-time limit. */
  public Optional<Duration> getElapsedTimeLimit() {
    return duration(elapsedTimeNanos);
  }

  /** Returns the executing-thread CPU-time limit. */
  public Optional<Duration> getCpuTimeLimit() {
    return duration(cpuTimeNanos);
  }

  /** Returns the executing-thread allocated-byte limit. */
  public OptionalLong getAllocatedBytesLimit() {
    return optionalLong(allocatedBytes);
  }

  /** Returns the post-expansion AST node-count limit. */
  public OptionalLong getAstNodeLimit() {
    return optionalLong(astNodes);
  }

  /** Returns the post-expansion AST depth limit. The root expression has depth one. */
  public OptionalInt getAstDepthLimit() {
    return astDepth < 0 ? OptionalInt.empty() : OptionalInt.of(astDepth);
  }

  /** Returns the AST metadata-entry limit. */
  public OptionalLong getAstMetadataEntryLimit() {
    return optionalLong(astMetadataEntries);
  }

  long elapsedTimeNanos() {
    return elapsedTimeNanos;
  }

  long cpuTimeNanos() {
    return cpuTimeNanos;
  }

  long allocatedBytes() {
    return allocatedBytes;
  }

  long astNodes() {
    return astNodes;
  }

  int astDepth() {
    return astDepth;
  }

  long astMetadataEntries() {
    return astMetadataEntries;
  }

  private static Optional<Duration> duration(long nanos) {
    return nanos < 0 ? Optional.empty() : Optional.of(Duration.ofNanos(nanos));
  }

  private static OptionalLong optionalLong(long value) {
    return value < 0 ? OptionalLong.empty() : OptionalLong.of(value);
  }

  /** Mutable builder for an immutable {@link ResourceLimits} value. */
  public static final class Builder {
    private long elapsedTimeNanos = UNLIMITED;
    private long cpuTimeNanos = UNLIMITED;
    private long allocatedBytes = UNLIMITED;
    private long astNodes = UNLIMITED;
    private int astDepth = -1;
    private long astMetadataEntries = UNLIMITED;

    private Builder() {}

    /**
     * Sets the monotonic elapsed-time limit.
     *
     * @param limit nonnegative elapsed time
     * @return this builder
     * @throws NullPointerException if {@code limit} is {@code null}
     * @throws IllegalArgumentException if the limit is negative or cannot be represented in
     *     nanoseconds
     */
    public Builder elapsedTimeLimit(Duration limit) {
      elapsedTimeNanos = durationNanos(limit, "elapsed time");
      return this;
    }

    /**
     * Sets the executing-thread CPU-time limit.
     *
     * @param limit nonnegative CPU time
     * @return this builder
     * @throws NullPointerException if {@code limit} is {@code null}
     * @throws IllegalArgumentException if the limit is negative or cannot be represented in
     *     nanoseconds
     */
    public Builder cpuTimeLimit(Duration limit) {
      cpuTimeNanos = durationNanos(limit, "CPU time");
      return this;
    }

    /**
     * Sets the executing-thread allocated-byte limit.
     *
     * <p>This measures cumulative allocation, not retained or live heap.
     *
     * @param limit nonnegative allocated-byte count
     * @return this builder
     * @throws IllegalArgumentException if {@code limit} is negative
     */
    public Builder allocatedBytesLimit(long limit) {
      allocatedBytes = nonNegative(limit, "allocated bytes");
      return this;
    }

    /**
     * Sets the post-expansion AST node-count limit.
     *
     * @param limit nonnegative node count
     * @return this builder
     * @throws IllegalArgumentException if {@code limit} is negative
     */
    public Builder astNodeLimit(long limit) {
      astNodes = nonNegative(limit, "AST nodes");
      return this;
    }

    /**
     * Sets the post-expansion AST depth limit. The root expression has depth one.
     *
     * @param limit nonnegative expression depth
     * @return this builder
     * @throws IllegalArgumentException if {@code limit} is negative
     */
    public Builder astDepthLimit(int limit) {
      if (limit < 0) {
        throw new IllegalArgumentException("AST depth limit must not be negative");
      }
      astDepth = limit;
      return this;
    }

    /**
     * Sets the AST metadata-entry limit.
     *
     * @param limit nonnegative metadata-entry count
     * @return this builder
     * @throws IllegalArgumentException if {@code limit} is negative
     */
    public Builder astMetadataEntryLimit(long limit) {
      astMetadataEntries = nonNegative(limit, "AST metadata entries");
      return this;
    }

    /**
     * Builds an immutable snapshot. Further builder changes do not affect previously built values.
     *
     * @return immutable limits, or the shared {@link ResourceLimits#unlimited()} value when no
     *     finite limit was set
     */
    public ResourceLimits build() {
      if (elapsedTimeNanos == UNLIMITED
          && cpuTimeNanos == UNLIMITED
          && allocatedBytes == UNLIMITED
          && astNodes == UNLIMITED
          && astDepth < 0
          && astMetadataEntries == UNLIMITED) {
        return unlimited();
      }
      return new ResourceLimits(
          elapsedTimeNanos, cpuTimeNanos, allocatedBytes, astNodes, astDepth, astMetadataEntries);
    }

    private static long durationNanos(Duration duration, String name) {
      Objects.requireNonNull(duration, name + " limit");
      if (duration.isNegative()) {
        throw new IllegalArgumentException(name + " limit must not be negative");
      }
      try {
        return duration.toNanos();
      } catch (ArithmeticException e) {
        throw new IllegalArgumentException(name + " limit is too large", e);
      }
    }

    private static long nonNegative(long value, String name) {
      if (value < 0) {
        throw new IllegalArgumentException(name + " limit must not be negative");
      }
      return value;
    }
  }
}
