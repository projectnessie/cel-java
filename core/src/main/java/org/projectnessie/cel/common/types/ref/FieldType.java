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
package org.projectnessie.cel.common.types.ref;

/** FieldType represents a field's type value and whether that field supports presence detection. */
public final class FieldType {
  /** Type of the field. */
  public final com.google.api.expr.v1alpha1.Type type;

  /** IsSet indicates whether the field is set on an input object. */
  public final FieldTester isSet;

  /** GetFrom retrieves the field value on the input object, if set. */
  public final FieldGetter getFrom;

  public FieldType(com.google.api.expr.v1alpha1.Type type, FieldTester isSet, FieldGetter getFrom) {
    this.type = type;
    this.isSet = isSet;
    this.getFrom = getFrom;
  }
}
