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

/**
 * Describes the checked type and host access callbacks for one object field.
 *
 * <p>Providers return a {@code FieldType} from {@link TypeProvider#findFieldType(String, String)}.
 * The descriptor retains the supplied callbacks; callers configuring a provider should therefore
 * treat them as immutable and safe for the provider's supported concurrent use.
 */
public class FieldType {
  /** Checked CEL type of the field. */
  public final com.google.api.expr.v1alpha1.Type type;

  /** Callback that determines whether the field is present on an input object. */
  public final FieldTester isSet;

  /** Callback that retrieves the field value from an input object. */
  public final FieldGetter getFrom;

  /**
   * Creates a field descriptor.
   *
   * @param type checked CEL type of the field
   * @param isSet presence callback
   * @param getFrom value callback
   */
  public FieldType(com.google.api.expr.v1alpha1.Type type, FieldTester isSet, FieldGetter getFrom) {
    this.type = type;
    this.isSet = isSet;
    this.getFrom = getFrom;
  }
}
