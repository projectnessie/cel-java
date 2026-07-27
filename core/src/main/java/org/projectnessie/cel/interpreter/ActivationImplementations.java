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

import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.Activation.PartialActivation;

/**
 * Activation backed by a map of named values.
 *
 * <p>Named bindings may lazily supply values through a no-argument {@link Supplier}.
 */
final class MapActivation implements Activation {
  private final Map<String, Object> bindings;

  MapActivation(Map<String, Object> bindings) {
    this.bindings = bindings;
  }

  @Override
  public Object resolve(String name) {
    if (name.startsWith(".")) {
      name = name.substring(1);
    }
    Object obj = bindings.get(name);
    if (obj == null) {
      if (!bindings.containsKey(name)) {
        return ABSENT;
      }
      return null;
    }

    if (obj instanceof Supplier) {
      obj = ((Supplier<?>) obj).get();
      bindings.put(name, obj);
    }
    return obj;
  }

  @SuppressWarnings("removal")
  @Override
  public ResolvedValue resolveName(String name) {
    return ResolvedValue.mapTo(resolve(name));
  }

  @Override
  public String toString() {
    return "MapActivation{" + "bindings=" + bindings + '}';
  }
}

/** Activation backed by a provider of named values. */
final class FunctionActivation implements Activation {
  private final ActivationFunction provider;

  FunctionActivation(ActivationFunction provider) {
    this.provider = provider;
  }

  @Override
  public Object resolve(String name) {
    if (name.startsWith(".")) {
      name = name.substring(1);
    }
    return provider.resolve(name);
  }

  @SuppressWarnings("removal")
  @Override
  public ResolvedValue resolveName(String name) {
    return ResolvedValue.mapTo(resolve(name));
  }

  @Override
  public String toString() {
    return "FunctionActivation{" + "provider=" + provider + '}';
  }
}

/** Activation which resolves against its child before its parent. */
record HierarchicalActivation(Activation parent, Activation child) implements Activation {
  @Override
  public Object resolve(String name) {
    if (name.startsWith(".")) {
      return parent.resolve(name.substring(1));
    }
    var resolvedName = child.resolve(name);
    if (ABSENT != resolvedName) {
      return resolvedName;
    }
    return parent.resolve(name);
  }

  @SuppressWarnings("removal")
  @Override
  public ResolvedValue resolveName(String name) {
    return ResolvedValue.mapTo(resolve(name));
  }

  @SuppressWarnings("NullableProblems")
  @Override
  public String toString() {
    return "HierarchicalActivation{" + "parent=" + parent + ", child=" + child + '}';
  }
}

/** Default implementation of {@link PartialActivation}. */
final class PartActivation implements PartialActivation {
  private final Activation delegate;
  private final AttributePattern[] unknowns;

  PartActivation(Activation delegate, AttributePattern[] unknowns) {
    this.delegate = delegate;
    this.unknowns = unknowns.clone();
  }

  @Override
  public Activation parent() {
    return delegate.parent();
  }

  @Override
  public Object resolve(String name) {
    return delegate.resolve(name);
  }

  @SuppressWarnings("removal")
  @Override
  public ResolvedValue resolveName(String name) {
    return ResolvedValue.mapTo(resolve(name));
  }

  @Override
  public AttributePattern[] unknownAttributePatterns() {
    return unknowns.clone();
  }

  @Override
  public String toString() {
    return "PartActivation{"
        + "delegate="
        + delegate
        + ", unknowns="
        + Arrays.toString(unknowns)
        + '}';
  }
}

/**
 * A single mutable variable binding.
 *
 * <p>This activation type is used only within folds, whose loop controls its lifecycle.
 */
final class VarActivation implements Activation {
  Activation parent;
  String name;
  Val val;

  VarActivation() {}

  @Override
  public Activation parent() {
    return parent;
  }

  @Override
  public Object resolve(String name) {
    if (name.startsWith(".")) {
      return parent.resolve(name.substring(1));
    }
    if (name.equals(this.name)) {
      return val;
    }
    return parent.resolve(name);
  }

  @SuppressWarnings("removal")
  @Override
  public ResolvedValue resolveName(String name) {
    return ResolvedValue.mapTo(resolve(name));
  }

  @Override
  public String toString() {
    return "VarActivation{" + "parent=" + parent + ", name='" + name + '\'' + ", val=" + val + '}';
  }
}
