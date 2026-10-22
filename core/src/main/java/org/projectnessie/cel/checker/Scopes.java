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
package org.projectnessie.cel.checker;

import com.google.api.expr.v1alpha1.Decl;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nested variable and function declaration scopes used by the checker.
 *
 * <p>Each scope has separate identifier and function namespaces. Lookups search from the innermost
 * scope outward, so inner declarations shadow outer declarations. A pushed scope is a new value
 * referencing its parent; declarations within each scope remain mutable.
 *
 * <p>The root identifier cache supports concurrent checker lookups after declarations are
 * configured. Pushed scopes and declaration mutation are intended to remain confined to one check.
 */
public final class Scopes {
  private final Scopes parent;
  private final Group scopes;

  private Scopes(Scopes parent, Group scopes) {
    this.parent = parent;
    this.scopes = scopes;
  }

  /** Creates an empty root scope. */
  public static Scopes newScopes() {
    return new Scopes(null, newRootGroup());
  }

  /** Creates an empty child scope whose parent is this scope. */
  public Scopes push() {
    return new Scopes(this, newGroup());
  }

  /** Returns the parent scope, or this root scope when no parent exists. */
  public Scopes pop() {
    if (parent != null) {
      return parent;
    }
    // TODO: Consider whether this should be an error / panic.
    return this;
  }

  boolean hasParent() {
    return parent != null;
  }

  /**
   * AddIdent adds the ident Decl in the current scope. Note: If the name collides with an existing
   * identifier in the scope, the Decl is overwritten.
   */
  public void addIdent(Decl decl) {
    scopes.idents.put(decl.getName(), decl);
  }

  /** Returns the nearest matching identifier declaration, or {@code null} when absent. */
  public Decl findIdent(String name) {
    Decl ident = scopes.idents.get(name);
    if (ident != null) {
      return ident;
    }
    if (parent != null) {
      return parent.findIdent(name);
    }
    return null;
  }

  /** Returns the matching identifier in this scope only, or {@code null} when absent. */
  public Decl findIdentInScope(String name) {
    return scopes.idents.get(name);
  }

  /**
   * AddFunction adds the function Decl to the current scope. Note: Any previous entry for a
   * function in the current scope with the same name is overwritten.
   */
  public void addFunction(Decl fn) {
    scopes.functions.put(fn.getName(), fn);
  }

  /** Returns the nearest matching function declaration, or {@code null} when absent. */
  public Decl findFunction(String name) {
    Decl ident = scopes.functions.get(name);
    if (ident != null) {
      return ident;
    }
    if (parent != null) {
      return parent.findFunction(name);
    }
    return null;
  }

  /**
   * Replaces the nearest matching function declaration.
   *
   * @return always {@code null}; retained for compatibility
   */
  public Decl updateFunction(String name, Decl ident) {
    if (scopes.functions.containsKey(name)) {
      scopes.functions.put(name, ident);
    } else {
      if (parent != null) {
        return parent.updateFunction(name, ident);
      }
    }
    return null;
  }

  /** Mutable identifier and function declaration maps associated with one lexical scope. */
  public static final class Group {
    private final Map<String, Decl> idents;
    private final Map<String, Decl> functions;

    private Group(Map<String, Decl> idents, Map<String, Decl> functions) {
      this.idents = idents;
      this.functions = functions;
    }
  }

  static Group newGroup() {
    return new Group(new HashMap<>(), new HashMap<>());
  }

  private static Group newRootGroup() {
    // CheckerEnv lazily caches provider-resolved identifiers in the shared root scope during
    // checking. Function declarations are fully configured before that scope is shared.
    return new Group(new ConcurrentHashMap<>(), new HashMap<>());
  }
}
