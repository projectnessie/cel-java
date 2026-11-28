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

interface NativeIntCapability extends Interpretable {
  long evalInt(Activation activation);
}

interface NativeUintCapability extends Interpretable {
  long evalUint(Activation activation);
}

interface NativeDoubleCapability extends Interpretable {
  double evalDouble(Activation activation);
}

interface NativeBooleanCapability extends Interpretable {
  boolean evalBoolean(Activation activation);
}

interface NativeStringCapability extends Interpretable {
  String evalString(Activation activation);
}

interface NativeNullCapability extends Interpretable {
  void evalNull(Activation activation);
}
