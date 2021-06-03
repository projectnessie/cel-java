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
 * TypeProvider specifies functions for creating new object instances and for resolving enum values
 * by name.
 */
public interface TypeProvider {
  /** EnumValue returns the numeric value of the given enum value name. */
  Val enumValue(String enumName);

  /** FindIdent takes a qualified identifier name and returns a Value if one exists. */
  Val findIdent(String identName);

  /**
   * FindType looks up the Type given a qualified typeName. Returns false if not found.
   *
   * <p>Used during type-checking only.
   */
  Type findType(String typeName);

  /**
   * FieldFieldType returns the field type for a checked type value. Returns false if the field
   * could not be found.
   *
   * <p>Used during type-checking only.
   */
  FieldType findFieldType(String messageType, String fieldName);

  /**
   * NewValue creates a new type value from a qualified name and map of field name to value.
   *
   * <p>Note, for each value, the Val.ConvertToNative function will be invoked to convert the Val to
   * the field's native type. If an error occurs during conversion, the NewValue will be a
   * types.Err.
   */
  Val newValue(String typeName, Map<String, Val> fields);
}
