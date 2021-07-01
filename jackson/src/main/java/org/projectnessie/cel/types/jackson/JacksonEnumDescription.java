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

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ser.std.EnumSerializer;
import java.util.List;
import java.util.stream.Stream;
import org.projectnessie.cel.common.types.pb.Checked;

final class JacksonEnumDescription {

  private final String name;
  private final com.google.api.expr.v1alpha1.Type pbType;
  private final List<Enum<?>> enumValues;

  JacksonEnumDescription(JavaType javaType, EnumSerializer ser) {
    this.name = javaType.getRawClass().getName().replace('$', '.');
    this.enumValues = ser.getEnumValues().enums();
    this.pbType = Checked.checkedInt;
  }

  com.google.api.expr.v1alpha1.Type pbType() {
    return pbType;
  }

  Stream<JacksonEnumValue> buildValues() {
    return enumValues.stream().map(JacksonEnumValue::new);
  }
}
