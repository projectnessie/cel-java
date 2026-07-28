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

/** Metadata connecting a named CEL object type to its Java representation. */
public interface TypeDescription {

  /**
   * Returns the fully qualified CEL type name.
   *
   * @return the name used during checking and evaluation
   */
  String name();

  /**
   * Returns the Java class represented by this description.
   *
   * @return the host-language class accepted by the corresponding adapter and provider
   */
  Class<?> reflectType();
}
