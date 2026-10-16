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

import static java.util.Objects.requireNonNull;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

/** One-way adaptation boundary around a maximal typed evaluation island. */
final class NativeIsland implements Interpretable, Coster {
  private final Interpretable root;
  private final TypeAdapter adapter;

  NativeIsland(Interpretable root, TypeAdapter adapter) {
    if (!(root instanceof NativeIntCapability)
        && !(root instanceof NativeUintCapability)
        && !(root instanceof NativeDoubleCapability)
        && !(root instanceof NativeBooleanCapability)
        && !(root instanceof NativeStringCapability)
        && !(root instanceof NativeNullCapability)) {
      throw new IllegalArgumentException("native island root has no terminal scalar capability");
    }
    this.root = requireNonNull(root, "root");
    this.adapter = requireNonNull(adapter, "adapter");
  }

  static boolean supports(Interpretable interpretable) {
    return interpretable instanceof NativeIntCapability
        || interpretable instanceof NativeUintCapability
        || interpretable instanceof NativeDoubleCapability
        || interpretable instanceof NativeBooleanCapability
        || interpretable instanceof NativeStringCapability
        || interpretable instanceof NativeNullCapability;
  }

  Interpretable root() {
    return root;
  }

  @Override
  public long id() {
    return root.id();
  }

  @Override
  public Val eval(Activation activation) {
    try {
      if (root instanceof NativeIntCapability intCapability) {
        return adapter.nativeToValue(intCapability.evalInt(activation));
      }
      if (root instanceof NativeUintCapability uintCapability) {
        return uintOf(uintCapability.evalUint(activation));
      }
      if (root instanceof NativeBooleanCapability booleanCapability) {
        return adapter.nativeToValue(booleanCapability.evalBoolean(activation));
      }
      if (root instanceof NativeDoubleCapability doubleCapability) {
        return adapter.nativeToValue(doubleCapability.evalDouble(activation));
      }
      if (root instanceof NativeStringCapability stringCapability) {
        return adapter.nativeToValue(stringCapability.evalString(activation));
      }
      ((NativeNullCapability) root).evalNull(activation);
      return org.projectnessie.cel.common.types.NullT.NullValue;
    } catch (ValueSignal signal) {
      return signal.value;
    }
  }

  @Override
  public Cost cost() {
    return Cost.estimateCost(root);
  }
}
