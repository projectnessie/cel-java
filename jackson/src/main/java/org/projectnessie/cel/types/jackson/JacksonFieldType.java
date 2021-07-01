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
package org.projectnessie.cel.types.jackson;

import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.google.api.expr.v1alpha1.Type;
import org.projectnessie.cel.common.types.ref.FieldGetter;
import org.projectnessie.cel.common.types.ref.FieldTester;
import org.projectnessie.cel.common.types.ref.FieldType;

final class JacksonFieldType extends FieldType {

  private final PropertyWriter propertyWriter;

  JacksonFieldType(
      Type type, FieldTester isSet, FieldGetter getFrom, PropertyWriter propertyWriter) {
    super(type, isSet, getFrom);
    this.propertyWriter = propertyWriter;
  }

  PropertyWriter propertyWriter() {
    return propertyWriter;
  }
}
