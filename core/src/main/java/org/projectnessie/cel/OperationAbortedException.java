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

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Unchecked termination of a controlled CEL operation.
 *
 * <p>This is a host-control outcome, not a CEL error or unknown value. Default messages contain
 * only stable reason/phase/resource metadata and never expression source or activation values.
 */
public final class OperationAbortedException extends RuntimeException {
  /** Stable termination reason. */
  public enum Reason {
    EXPLICIT_CANCELLATION,
    THREAD_INTERRUPTED,
    ELAPSED_TIME_LIMIT,
    CPU_TIME_LIMIT,
    ALLOCATED_BYTES_LIMIT,
    MEASUREMENT_UNAVAILABLE,
    AST_NODE_LIMIT,
    AST_DEPTH_LIMIT,
    AST_METADATA_LIMIT
  }

  /** CEL processing phase in which termination was observed. */
  public enum Phase {
    SOURCE_ADMISSION,
    PARSE,
    AST_BUILD,
    CHECK,
    OPTIMIZE,
    PLAN,
    EVALUATE,
    RESULT_CONVERSION
  }

  /** Resource associated with a limit or unavailable measurement. */
  public enum Resource {
    ELAPSED_TIME,
    THREAD_CPU_TIME,
    THREAD_ALLOCATED_BYTES,
    AST_NODES,
    AST_DEPTH,
    AST_METADATA_ENTRIES
  }

  private final Reason reason;
  private final Phase phase;
  private final Resource resource;
  private final long limit;
  private final long observed;

  /** Creates a cancellation or interruption outcome without numeric resource data. */
  public OperationAbortedException(Reason reason, Phase phase) {
    this(reason, phase, null, -1, -1, null);
  }

  /** Creates a resource outcome with a configured limit and observed consumption. */
  public OperationAbortedException(
      Reason reason, Phase phase, Resource resource, long limit, long observed) {
    this(reason, phase, resource, limit, observed, null);
  }

  /** Creates a resource outcome and retains its cause. */
  public OperationAbortedException(
      Reason reason, Phase phase, Resource resource, long limit, long observed, Throwable cause) {
    super(message(reason, phase, resource, limit, observed), cause);
    this.reason = Objects.requireNonNull(reason, "reason");
    this.phase = Objects.requireNonNull(phase, "phase");
    this.resource = resource;
    this.limit = limit;
    this.observed = observed;
  }

  /** Returns the stable termination reason. */
  public Reason getReason() {
    return reason;
  }

  /** Returns the processing phase. */
  public Phase getPhase() {
    return phase;
  }

  /** Returns the associated resource, when applicable. */
  public Optional<Resource> getResource() {
    return Optional.ofNullable(resource);
  }

  /** Returns the configured numeric limit, when applicable. Durations use nanoseconds. */
  public OptionalLong getLimit() {
    return limit < 0 ? OptionalLong.empty() : OptionalLong.of(limit);
  }

  /** Returns observed consumption, when available. Durations use nanoseconds. */
  public OptionalLong getObserved() {
    return observed < 0 ? OptionalLong.empty() : OptionalLong.of(observed);
  }

  private static String message(
      Reason reason, Phase phase, Resource resource, long limit, long observed) {
    var message = new StringBuilder("CEL operation aborted: reason=").append(reason);
    message.append(", phase=").append(phase);
    if (resource != null) {
      message.append(", resource=").append(resource);
    }
    if (limit >= 0) {
      message.append(", limit=").append(limit);
    }
    if (observed >= 0) {
      message.append(", observed=").append(observed);
    }
    return message.toString();
  }
}
