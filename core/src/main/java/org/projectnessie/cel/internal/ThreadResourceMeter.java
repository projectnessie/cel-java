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

/**
 * Internal per-thread resource meter.
 *
 * <p>This type is public only for communication between CEL-Java packages. It is not a supported
 * application API.
 */
public interface ThreadResourceMeter {
  /**
   * Returns cumulative CPU nanoseconds for the supplied thread, or a negative unavailable value.
   */
  long cpuTimeNanos(long threadId);

  /**
   * Returns cumulative allocated bytes for the supplied thread, or a negative unavailable value.
   */
  long allocatedBytes(long threadId);
}
