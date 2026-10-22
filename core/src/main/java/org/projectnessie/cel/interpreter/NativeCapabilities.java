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

interface NativeRawCapability extends Interpretable {
  Object evalRaw(Activation activation);
}

/**
 * A list-valued expression that can resolve its host representation once and materialize that
 * already-resolved representation without replaying the expression.
 */
interface NativeListSourceCapability extends NativeRawCapability {
  Val materializeResolvedList(Object value);

  Val materializeResolvedElement(Object value);

  boolean exactListSource();
}

/**
 * A map-valued expression that can resolve its host representation once and materialize that
 * already-resolved representation without replaying the expression.
 */
interface NativeMapSourceCapability extends NativeRawCapability {
  Val materializeResolvedMap(Object value);

  boolean exactMapSource();
}

interface NativeScalarListLiteralCapability extends Interpretable {
  int evalSize(Activation activation);
}

interface NativeBooleanListLiteralCapability extends NativeScalarListLiteralCapability {
  boolean evalBooleanAt(Activation activation, int index);
}

interface NativeIntListLiteralCapability extends NativeScalarListLiteralCapability {
  long evalIntAt(Activation activation, int index);
}

interface NativeUintListLiteralCapability extends NativeScalarListLiteralCapability {
  long evalUintAt(Activation activation, int index);
}

interface NativeDoubleListLiteralCapability extends NativeScalarListLiteralCapability {
  double evalDoubleAt(Activation activation, int index);
}

interface NativeStringListLiteralCapability extends NativeScalarListLiteralCapability {
  String evalStringAt(Activation activation, int index);

  boolean evalContains(Activation activation, NativeStringCapability needle);
}

interface NativeScalarListFoldCapability extends Interpretable {
  NativeScalarKind elementKind();

  long evalSize(Activation activation);

  boolean evalBooleanAt(Activation activation, int index);

  long evalIntAt(Activation activation, int index);

  long evalUintAt(Activation activation, int index);

  double evalDoubleAt(Activation activation, int index);

  String evalStringAt(Activation activation, int index);

  void evalNullAt(Activation activation, int index);

  boolean evalStringContains(Activation activation, NativeStringCapability needle);

  NativeIntAggregateValues evalIntValues(Activation activation);
}
