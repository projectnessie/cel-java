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
 * Runtime categories of CEL values.
 *
 * <p>This enum supports interpreter dispatch; {@link #getName()} returns the corresponding CEL type
 * name. Object values may have a more specific qualified name through {@link Type#typeName()}.
 */
public enum TypeEnum {
  /** CEL boolean. */
  Bool("bool"),

  /** CEL byte string. */
  Bytes("bytes"),

  /** CEL double-precision number. */
  Double("double"),

  /** CEL duration. */
  Duration("google.protobuf.Duration"),

  /** CEL evaluation error. */
  Err("error"),

  /** CEL signed integer. */
  Int("int"),

  /** CEL list. */
  List("list"),

  /** CEL map. */
  Map("map"),

  /** CEL null. */
  Null("null_type"),

  /** CEL object with a provider-defined qualified type. */
  Object("object"),

  /** CEL string. */
  String("string"),

  /** CEL timestamp. */
  Timestamp("google.protobuf.Timestamp"),

  /** CEL type value. */
  Type("type"),

  /** CEL unsigned integer. */
  Uint("uint"),

  /** CEL unknown value. */
  Unknown("unknown");

  private final String name;

  TypeEnum(java.lang.String name) {
    this.name = name;
  }

  /**
   * Returns the CEL name of this runtime category.
   *
   * @return the CEL type name
   */
  public java.lang.String getName() {
    return name;
  }
}
