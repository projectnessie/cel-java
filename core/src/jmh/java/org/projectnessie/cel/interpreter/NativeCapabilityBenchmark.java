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
package org.projectnessie.cel.interpreter;

import org.projectnessie.cel.common.types.ref.Val;

/** JMH-only typed access to an integrated native-capability root. */
public final class NativeCapabilityBenchmark {
  private final NativeIsland plan;

  private NativeCapabilityBenchmark(NativeIsland plan) {
    this.plan = plan;
  }

  public static NativeCapabilityBenchmark require(Interpretable root) {
    if (!(root instanceof NativeIsland plan)) {
      throw new IllegalStateException("expected integrated native-capability root but got " + root);
    }
    return new NativeCapabilityBenchmark(plan);
  }

  public Val eval(Activation activation) {
    return plan.eval(activation);
  }

  public boolean evalBoolean(Activation activation) {
    return ((NativeBooleanCapability) plan.root()).evalBoolean(activation);
  }

  public long evalInt(Activation activation) {
    return ((NativeIntCapability) plan.root()).evalInt(activation);
  }

  public double evalDouble(Activation activation) {
    return ((NativeDoubleCapability) plan.root()).evalDouble(activation);
  }

  public String evalString(Activation activation) {
    return ((NativeStringCapability) plan.root()).evalString(activation);
  }
}
