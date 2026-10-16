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
package org.projectnessie.cel.common.types.traits;

/**
 * Behavioral capabilities advertised by a runtime CEL {@link
 * org.projectnessie.cel.common.types.ref.Type}.
 *
 * <p>Each advertised constant corresponds to an interface in this package, except {@link
 * #IterableType} and {@link #IteratorType}, whose interfaces live in {@code common.types}.
 */
public enum Trait {
  /** Types implementing {@link Adder}. */
  AdderType,

  /** Types implementing {@link Comparer}. */
  ComparerType,

  /** Types implementing {@link Container}. */
  ContainerType,

  /** Types implementing {@link Divider}. */
  DividerType,

  /** Types implementing {@link FieldTester}. */
  FieldTesterType,

  /** Types implementing {@link Indexer}. */
  IndexerType,

  /** IterableType types can be iterated over in comprehensions. */
  IterableType,

  /**
   * IteratorType marks low-level interpreter cursor values.
   *
   * <p>It is used for internal traversal dispatch and does not define a CEL language type.
   */
  IteratorType,

  /** Types implementing {@link Matcher}. */
  MatcherType,

  /** Types implementing {@link Modder}. */
  ModderType,

  /** Types implementing {@link Multiplier}. */
  MultiplierType,

  /** Types implementing {@link Negater}. */
  NegatorType,

  /** Types implementing {@link Receiver}. */
  ReceiverType,

  /** Types implementing {@link Sizer}. */
  SizerType,

  /** Types implementing {@link Subtractor}. */
  SubtractorType
}
