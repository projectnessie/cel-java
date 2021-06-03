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

/**
 * Scopes represents nested Decl sets where the Scopes value contains a Groups containing all
 * identifiers in scope and an optional parent representing outer scopes. Each Groups value is a
 * mapping of names to Decls in the ident and function namespaces. Lookups are performed such that
 * bindings in inner scopes shadow those in outer scopes.
 */
public class Scopes {
  private final Scopes parent;
  private final Group scopes;

  private Scopes(Scopes parent, Group scopes) {
    this.parent = parent;
    this.scopes = scopes;
  }

  /**
   * NewScopes creates a new, empty Scopes. Some operations can't be safely performed until a Group
   * is added with Push.
   */
  public static Scopes newScopes() {
    return new Scopes(null, newGroup());
  }

  /** Push creates a new Scopes value which references the current Scope as its parent. */
  public Scopes push() {
    return new Scopes(this, newGroup());
  }

  /**
   * Pop returns the parent Scopes value for the current scope, or the current scope if the parent
   * is nil.
   */
  public Scopes pop() {
    if (parent != null) {
      return parent;
    }
    // TODO: Consider whether this should be an error / panic.
    return this;
  }

  /**
   * AddIdent adds the ident Decl in the current scope. Note: If the name collides with an existing
   * identifier in the scope, the Decl is overwritten.
   */
  public void addIdent(Decl decl) {
    scopes.idents.put(decl.getName(), decl);
  }

  /**
   * FindIdent finds the first ident Decl with a matching name in Scopes, or nil if one cannot be
   * found. Note: The search is performed from innermost to outermost.
   */
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

  /**
   * FindIdentInScope finds the first ident Decl with a matching name in the current Scopes value,
   * or nil if one does not exist. Note: The search is only performed on the current scope and does
   * not search outer scopes.
   */
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

  /**
   * FindFunction finds the first function Decl with a matching name in Scopes. The search is
   * performed from innermost to outermost. Returns nil if no such function in Scopes.
   */
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

  /**
   * Group is a set of Decls that is pushed on or popped off a Scopes as a unit. Contains separate
   * namespaces for idenifier and function Decls. (Should be named "Scope" perhaps?)
   */
  public static class Group {
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
}
