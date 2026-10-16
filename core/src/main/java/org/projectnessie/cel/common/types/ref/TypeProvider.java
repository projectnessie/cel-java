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

import com.google.api.expr.v1alpha1.Type;
import java.util.Map;

/**
 * Resolves host types, fields, identifiers, enum constants, and object construction for CEL.
 *
 * <p>A provider participates in both checking and evaluation and may be retained by reusable
 * environments and programs. Complete mutable configuration before sharing it and ensure lookup
 * operations are safe for the intended concurrent use. Lookup methods use Java {@code null} only
 * for “not found” where documented; failures produced while constructing or evaluating a CEL value
 * are returned as CEL error values.
 */
public interface TypeProvider {
  /**
   * Resolves a fully qualified enum value name.
   *
   * @param enumName fully qualified enum value name
   * @return the numeric CEL value, or a CEL error value if the name is unknown
   */
  Val enumValue(String enumName);

  /**
   * Resolves a qualified identifier such as a type or enum value.
   *
   * @param identName qualified identifier
   * @return the resolved CEL value, or {@code null} if no identifier is registered
   */
  Val findIdent(String identName);

  /**
   * Looks up a checked CEL type by qualified name.
   *
   * <p>Used during type-checking only.
   *
   * @param typeName qualified CEL type name
   * @return the checked type, or {@code null} if no type is registered
   */
  Type findType(String typeName);

  /**
   * Looks up a field descriptor on a checked message type.
   *
   * <p>Used during type-checking only.
   *
   * @param messageType qualified message type name
   * @param fieldName field name
   * @return the field descriptor, or {@code null} if the type or field is unknown
   */
  FieldType findFieldType(String messageType, String fieldName);

  /**
   * Creates an object value from a qualified type name and CEL-valued fields.
   *
   * <p>Each field value is converted to the host representation required by that field. An unknown
   * type, field, or failed conversion is represented by a CEL error value.
   *
   * @param typeName qualified type name
   * @param fields field names and CEL values used to construct the object
   * @return the object as a CEL value, or a CEL error value
   */
  Val newValue(String typeName, Map<String, Val> fields);
}
