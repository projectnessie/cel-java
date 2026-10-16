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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import org.projectnessie.cel.OperationAbortedException;
import org.projectnessie.cel.OperationAbortedException.Phase;
import org.projectnessie.cel.OperationAbortedException.Reason;
import org.projectnessie.cel.OperationAbortedException.Resource;
import org.projectnessie.cel.ResourceLimits;

/**
 * Management-bean-backed internal resource meter.
 *
 * <p>This type is public only for communication between CEL-Java packages. It is not a supported
 * application API.
 */
public final class ManagementThreadResourceMeter implements ThreadResourceMeter {
  private static final Object ENABLE_LOCK = new Object();
  private static volatile boolean cpuReady;
  private static volatile com.sun.management.ThreadMXBean allocationReady;

  private final ThreadMXBean threadBean;
  private final com.sun.management.ThreadMXBean allocationBean;

  private ManagementThreadResourceMeter(
      ThreadMXBean threadBean, com.sun.management.ThreadMXBean allocationBean) {
    this.threadBean = threadBean;
    this.allocationBean = allocationBean;
  }

  /** Creates and validates a meter for the requested limits. */
  public static ThreadResourceMeter create(ResourceLimits limits, Phase phase) {
    var cpuRequested = limits.getCpuTimeLimit().isPresent();
    var allocationRequested = limits.getAllocatedBytesLimit().isPresent();
    final ThreadMXBean bean;
    try {
      bean = ManagementFactory.getThreadMXBean();
    } catch (RuntimeException | LinkageError e) {
      throw unavailable(
          cpuRequested ? Resource.THREAD_CPU_TIME : Resource.THREAD_ALLOCATED_BYTES, phase, e);
    }

    if (cpuRequested && !cpuReady) {
      synchronized (ENABLE_LOCK) {
        try {
          if (!cpuReady) {
            if (!bean.isThreadCpuTimeSupported()) {
              throw unavailable(Resource.THREAD_CPU_TIME, phase, null);
            }
            if (!bean.isThreadCpuTimeEnabled()) {
              bean.setThreadCpuTimeEnabled(true);
            }
            if (!bean.isThreadCpuTimeEnabled()) {
              throw unavailable(Resource.THREAD_CPU_TIME, phase, null);
            }
            cpuReady = true;
          }
        } catch (OperationAbortedException e) {
          throw e;
        } catch (RuntimeException | LinkageError e) {
          throw unavailable(Resource.THREAD_CPU_TIME, phase, e);
        }
      }
    }

    if (allocationRequested && allocationReady == null) {
      synchronized (ENABLE_LOCK) {
        try {
          if (allocationReady == null) {
            if (!(bean instanceof com.sun.management.ThreadMXBean)) {
              throw unavailable(Resource.THREAD_ALLOCATED_BYTES, phase, null);
            }
            var allocationBean = (com.sun.management.ThreadMXBean) bean;
            if (!allocationBean.isThreadAllocatedMemorySupported()) {
              throw unavailable(Resource.THREAD_ALLOCATED_BYTES, phase, null);
            }
            if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
              allocationBean.setThreadAllocatedMemoryEnabled(true);
            }
            if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
              throw unavailable(Resource.THREAD_ALLOCATED_BYTES, phase, null);
            }
            allocationReady = allocationBean;
          }
        } catch (OperationAbortedException e) {
          throw e;
        } catch (RuntimeException | LinkageError e) {
          throw unavailable(Resource.THREAD_ALLOCATED_BYTES, phase, e);
        }
      }
    }
    return new ManagementThreadResourceMeter(bean, allocationRequested ? allocationReady : null);
  }

  @Override
  public long cpuTimeNanos(long threadId) {
    return threadBean.getThreadCpuTime(threadId);
  }

  @Override
  public long allocatedBytes(long threadId) {
    return allocationBean == null ? -1 : allocationBean.getThreadAllocatedBytes(threadId);
  }

  private static OperationAbortedException unavailable(
      Resource resource, Phase phase, Throwable cause) {
    return new OperationAbortedException(
        Reason.MEASUREMENT_UNAVAILABLE, phase, resource, -1, -1, cause);
  }
}
