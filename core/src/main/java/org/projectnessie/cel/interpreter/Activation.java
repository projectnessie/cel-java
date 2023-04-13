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
package org.projectnessie.cel.interpreter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.shaded.org.antlr.v4.runtime.misc.Pair;

/**
 * Activation used to resolve identifiers by name and references by id.
 *
 * <p>An Activation is the primary mechanism by which a caller supplies input into a CEL program.
 */
public interface Activation {
  /**
   * ResolveName returns a value from the activation by qualified name, or false if the name could
   * not be found.
   */
  Pair<Object, Boolean> resolveName(String name);

  /**
   * Parent returns the parent of the current activation, may be nil. If non-nil, the parent will be
   * searched during resolve calls.
   */
  Activation parent();

  /** EmptyActivation returns a variable free activation. */
  static Activation emptyActivation() {
    // This call cannot fail.
    return newActivation(new HashMap<String, Object>());
  }

  /**
   * NewActivation returns an activation based on a map-based binding where the map keys are
   * expected to be qualified names used with ResolveName calls.
   *
   * <p>The input `bindings` may either be of type `Activation` or `map[string]interface{}`.
   *
   * <p>Lazy bindings may be supplied within the map-based input in either of the following forms: -
   * func() interface{} - func() ref.Val
   *
   * <p>The output of the lazy binding will overwrite the variable reference in the internal map.
   *
   * <p>Values which are not represented as ref.Val types on input may be adapted to a ref.Val using
   * the ref.TypeAdapter configured in the environment.
   */
  static Activation newActivation(Object bindings) {
    if (bindings == null) {
      throw new NullPointerException("bindings must be non-nil");
    }
    if (bindings instanceof Activation) {
      return (Activation) bindings;
    }
    if (bindings instanceof Map) {
      return new MapActivation((Map<String, Object>) bindings);
    }
    if (bindings instanceof Function) {
      return new FunctionActivation((Function<String, Object>) bindings);
    }
    throw new IllegalArgumentException(
        String.format(
            "activation input must be an activation or map[string]interface: got %s",
            bindings.getClass().getName()));
  }

  /**
   * mapActivation which implements Activation and maps of named values.
   *
   * <p>Named bindings may lazily supply values by providing a function which accepts no arguments
   * and produces an interface value.
   */
  final class MapActivation implements Activation {
    private final Map<String, Object> bindings;

    MapActivation(Map<String, Object> bindings) {
      this.bindings = bindings;
    }

    /** Parent implements the Activation interface method. */
    @Override
    public Activation parent() {
      return null;
    }

    /** ResolveName implements the Activation interface method. */
    @Override
    public Pair<Object, Boolean> resolveName(String name) {
      if (!bindings.containsKey(name)) {
        return new Pair<>(null, false);
      }
      Object obj = bindings.get(name);

      if (obj instanceof Supplier) {
        obj = ((Supplier) obj).get();
        bindings.put(name, obj);
      }
      return new Pair<>(obj, true);
    }

    @Override
    public String toString() {
      return "MapActivation{" + "bindings=" + bindings + '}';
    }
  }

  /** functionActivation which implements Activation and a provider of named values. */
  final class FunctionActivation implements Activation {
    private final Function<String, Object> provider;

    FunctionActivation(Function<String, Object> provider) {
      this.provider = provider;
    }

    /** Parent implements the Activation interface method. */
    @Override
    public Activation parent() {
      return null;
    }

    /** ResolveName implements the Activation interface method. */
    @Override
    public Pair<Object, Boolean> resolveName(String name) {
      return new Pair<>(provider.apply(name), true);
    }

    @Override
    public String toString() {
      return "FunctionActivation{" + "provider=" + provider + '}';
    }
  }

  /**
   * hierarchicalActivation which implements Activation and contains a parent and child activation.
   */
  final class HierarchicalActivation implements Activation {
    private final Activation parent;
    private final Activation child;

    HierarchicalActivation(Activation parent, Activation child) {
      this.parent = parent;
      this.child = child;
    }

    /** Parent implements the Activation interface method. */
    @Override
    public Activation parent() {
      return parent;
    }

    /** ResolveName implements the Activation interface method. */
    @Override
    public Pair<Object, Boolean> resolveName(String name) {
      Pair<Object, Boolean> object = child.resolveName(name);
      if (object.b) {
        return object;
      }
      return parent.resolveName(name);
    }

    @Override
    public String toString() {
      return "HierarchicalActivation{" + "parent=" + parent + ", child=" + child + '}';
    }
  }

  /**
   * NewHierarchicalActivation takes two activations and produces a new one which prioritizes
   * resolution in the child first and parent(s) second.
   */
  static Activation newHierarchicalActivation(Activation parent, Activation child) {
    return new HierarchicalActivation(parent, child);
  }

  /**
   * NewPartialActivation returns an Activation which contains a list of AttributePattern values
   * representing field and index operations that should result in a 'types.Unknown' result.
   *
   * <p>The `bindings` value may be any value type supported by the interpreter.NewActivation call,
   * but is typically either an existing Activation or map[string]interface{}.
   */
  static PartialActivation newPartialActivation(Object bindings, AttributePattern... unknowns) {
    Activation a = newActivation(bindings);
    return new PartActivation(a, unknowns);
  }

  /** PartialActivation extends the Activation interface with a set of UnknownAttributePatterns. */
  interface PartialActivation extends Activation {

    /**
     * UnknownAttributePaths returns a set of AttributePattern values which match Attribute
     * expressions for data accesses whose values are not yet known.
     */
    AttributePattern[] unknownAttributePatterns();
  }

  /** partActivation is the default implementations of the PartialActivation interface. */
  final class PartActivation implements PartialActivation {
    private final Activation delegate;
    private final AttributePattern[] unknowns;

    PartActivation(Activation delegate, AttributePattern[] unknowns) {
      this.delegate = delegate;
      this.unknowns = unknowns;
    }

    @Override
    public Activation parent() {
      return delegate.parent();
    }

    @Override
    public Pair<Object, Boolean> resolveName(String name) {
      return delegate.resolveName(name);
    }

    /** UnknownAttributePatterns implements the PartialActivation interface method. */
    @Override
    public AttributePattern[] unknownAttributePatterns() {
      return unknowns;
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
   * varActivation represents a single mutable variable binding.
   *
   * <p>This activation type should only be used within folds as the fold loop controls the object
   * life-cycle.
   */
  final class VarActivation implements Activation {
    Activation parent;
    String name;
    Val val;

    VarActivation() {}

    /** Parent implements the Activation interface method. */
    @Override
    public Activation parent() {
      return parent;
    }

    /** ResolveName implements the Activation interface method. */
    @Override
    public Pair<Object, Boolean> resolveName(String name) {
      if (name.equals(this.name)) {
        return new Pair<>(val, true);
      }
      return parent.resolveName(name);
    }

    @Override
    public String toString() {
      return "VarActivation{"
          + "parent="
          + parent
          + ", name='"
          + name
          + '\''
          + ", val="
          + val
          + '}';
    }
  }
}
