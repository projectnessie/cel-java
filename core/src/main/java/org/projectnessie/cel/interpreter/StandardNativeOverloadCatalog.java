/*
 * Copyright (C) 2026 The Authors of CEL-Java
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
package org.projectnessie.cel.interpreter;

import static org.projectnessie.cel.common.types.Overloads.AddDouble;
import static org.projectnessie.cel.common.types.Overloads.AddInt64;
import static org.projectnessie.cel.common.types.Overloads.AddList;
import static org.projectnessie.cel.common.types.Overloads.AddString;
import static org.projectnessie.cel.common.types.Overloads.DivideDouble;
import static org.projectnessie.cel.common.types.Overloads.DivideInt64;
import static org.projectnessie.cel.common.types.Overloads.GreaterBool;
import static org.projectnessie.cel.common.types.Overloads.GreaterDouble;
import static org.projectnessie.cel.common.types.Overloads.GreaterEqualsBool;
import static org.projectnessie.cel.common.types.Overloads.GreaterEqualsDouble;
import static org.projectnessie.cel.common.types.Overloads.GreaterEqualsInt64;
import static org.projectnessie.cel.common.types.Overloads.GreaterEqualsString;
import static org.projectnessie.cel.common.types.Overloads.GreaterInt64;
import static org.projectnessie.cel.common.types.Overloads.GreaterString;
import static org.projectnessie.cel.common.types.Overloads.InList;
import static org.projectnessie.cel.common.types.Overloads.InMap;
import static org.projectnessie.cel.common.types.Overloads.IndexList;
import static org.projectnessie.cel.common.types.Overloads.IndexMap;
import static org.projectnessie.cel.common.types.Overloads.LessBool;
import static org.projectnessie.cel.common.types.Overloads.LessDouble;
import static org.projectnessie.cel.common.types.Overloads.LessEqualsBool;
import static org.projectnessie.cel.common.types.Overloads.LessEqualsDouble;
import static org.projectnessie.cel.common.types.Overloads.LessEqualsInt64;
import static org.projectnessie.cel.common.types.Overloads.LessEqualsString;
import static org.projectnessie.cel.common.types.Overloads.LessInt64;
import static org.projectnessie.cel.common.types.Overloads.LessString;
import static org.projectnessie.cel.common.types.Overloads.LogicalNot;
import static org.projectnessie.cel.common.types.Overloads.ModuloInt64;
import static org.projectnessie.cel.common.types.Overloads.MultiplyDouble;
import static org.projectnessie.cel.common.types.Overloads.MultiplyInt64;
import static org.projectnessie.cel.common.types.Overloads.NegateDouble;
import static org.projectnessie.cel.common.types.Overloads.NegateInt64;
import static org.projectnessie.cel.common.types.Overloads.SizeList;
import static org.projectnessie.cel.common.types.Overloads.SizeListInst;
import static org.projectnessie.cel.common.types.Overloads.SizeMap;
import static org.projectnessie.cel.common.types.Overloads.SizeMapInst;
import static org.projectnessie.cel.common.types.Overloads.SubtractDouble;
import static org.projectnessie.cel.common.types.Overloads.SubtractInt64;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.interpreter.functions.Overload;

/** Exact standard-overload provenance used only while planning checked expressions. */
final class StandardNativeOverloadCatalog {
  private StandardNativeOverloadCatalog() {}

  static NativeOverloadDescriptor find(
      Overload implementation, String function, String overloadId) {
    if (implementation == null || function == null || overloadId == null) {
      return null;
    }
    for (NativeOverloadDescriptor descriptor : descriptors(implementation)) {
      if (descriptor.matches(function, overloadId)) {
        return descriptor;
      }
    }
    return null;
  }

  static List<NativeOverloadDescriptor> descriptors(Overload implementation) {
    List<NativeOverloadDescriptor> descriptors = Holder.BY_IMPLEMENTATION.get(implementation);
    return descriptors != null ? descriptors : List.of();
  }

  private static final class Holder {
    private static final Map<Overload, List<NativeOverloadDescriptor>> BY_IMPLEMENTATION =
        createCatalog();
  }

  private static Map<Overload, List<NativeOverloadDescriptor>> createCatalog() {
    Map<String, Overload> implementations = new HashMap<>();
    for (Overload implementation : Overload.standardOverloads()) {
      Overload previous = implementations.put(implementation.operator, implementation);
      if (previous != null) {
        throw new IllegalStateException(
            "duplicate standard overload implementation: " + implementation.operator);
      }
    }

    IdentityHashMap<Overload, List<NativeOverloadDescriptor>> mutable = new IdentityHashMap<>();
    bind(mutable, implementations, Operator.LogicalNot.id, LogicalNot);
    bind(mutable, implementations, Operator.Negate.id, NegateInt64, NegateDouble);
    bind(mutable, implementations, Operator.Add.id, AddInt64, AddDouble, AddString, AddList);
    bind(mutable, implementations, Operator.Subtract.id, SubtractInt64, SubtractDouble);
    bind(mutable, implementations, Operator.Multiply.id, MultiplyInt64, MultiplyDouble);
    bind(mutable, implementations, Operator.Divide.id, DivideInt64, DivideDouble);
    bind(mutable, implementations, Operator.Modulo.id, ModuloInt64);
    bind(mutable, implementations, Operator.Less.id, LessBool, LessInt64, LessDouble, LessString);
    bind(
        mutable,
        implementations,
        Operator.LessEquals.id,
        LessEqualsBool,
        LessEqualsInt64,
        LessEqualsDouble,
        LessEqualsString);
    bind(
        mutable,
        implementations,
        Operator.Greater.id,
        GreaterBool,
        GreaterInt64,
        GreaterDouble,
        GreaterString);
    bind(
        mutable,
        implementations,
        Operator.GreaterEquals.id,
        GreaterEqualsBool,
        GreaterEqualsInt64,
        GreaterEqualsDouble,
        GreaterEqualsString);
    bind(mutable, implementations, Operator.Index.id, IndexList, IndexMap);
    bind(mutable, implementations, Operator.In.id, InList, InMap);
    bind(
        mutable,
        implementations,
        org.projectnessie.cel.common.types.Overloads.Size,
        SizeList,
        SizeListInst,
        SizeMap,
        SizeMapInst);

    IdentityHashMap<Overload, List<NativeOverloadDescriptor>> immutable =
        new IdentityHashMap<>(mutable.size());
    mutable.forEach(
        (implementation, descriptors) -> immutable.put(implementation, List.copyOf(descriptors)));
    return immutable;
  }

  private static void bind(
      IdentityHashMap<Overload, List<NativeOverloadDescriptor>> catalog,
      Map<String, Overload> implementations,
      String function,
      String... overloadIds) {
    Overload implementation = implementations.get(function);
    if (implementation == null) {
      throw new IllegalStateException("missing standard overload implementation: " + function);
    }
    List<NativeOverloadDescriptor> descriptors =
        catalog.computeIfAbsent(implementation, ignored -> new ArrayList<>());
    for (String overloadId : overloadIds) {
      descriptors.add(new NativeOverloadDescriptor(function, overloadId));
    }
  }
}
