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

import static org.projectnessie.cel.common.types.Err.noSuchField;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Types.boolOf;

import org.projectnessie.cel.common.types.ObjectT;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.ref.Val;

final class JacksonObjectT extends ObjectT {

  private JacksonObjectT(JacksonRegistry registry, Object value, JacksonTypeDescription typeDesc) {
    super(registry, value, typeDesc, typeDesc.type());
  }

  static JacksonObjectT newObject(
      JacksonRegistry registry, Object value, JacksonTypeDescription typeDesc) {
    return new JacksonObjectT(registry, value, typeDesc);
  }

  JacksonTypeDescription typeDesc() {
    return (JacksonTypeDescription) typeDesc;
  }

  JacksonRegistry registry() {
    return (JacksonRegistry) adapter;
  }

  @Override
  public Val isSet(Val field) {
    if (!(field instanceof StringT)) {
      return noSuchOverload(this, "isSet", field);
    }
    String fieldName = (String) field.value();

    if (!typeDesc().hasProperty(fieldName)) {
      return noSuchField(fieldName);
    }

    Object value = typeDesc().fromObject(value(), fieldName);

    return boolOf(value != null);
  }

  @Override
  public Val get(Val index) {
    if (!(index instanceof StringT)) {
      return noSuchOverload(this, "get", index);
    }
    String fieldName = (String) index.value();

    if (!typeDesc().hasProperty(fieldName)) {
      return noSuchField(fieldName);
    }

    Object v = typeDesc().fromObject(value(), fieldName);

    return registry().nativeToValue(v);
  }

  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    throw new UnsupportedOperationException();
  }
}
