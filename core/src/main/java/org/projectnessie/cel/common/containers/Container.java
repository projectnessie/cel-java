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
package org.projectnessie.cel.common.containers;

import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Select;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container holds a reference to an optional qualified container name and set of aliases.
 *
 * <p>The program container can be used to simplify variable, function, and type specification
 * within CEL programs and behaves more or less like a C++ namespace. See ResolveCandidateNames for
 * more details.
 */
public class Container {

  /** DefaultContainer has an empty container name. */
  public static final Container defaultContainer = new Container("", Collections.emptyMap());

  private final String name;
  private final Map<String, String> aliases;

  /** NewContainer creates a new Container with the fully-qualified name. */
  public static Container newContainer(ContainerOption... opts) {
    Container c = defaultContainer;
    for (ContainerOption opt : opts) {
      c = opt.apply(c);
      if (c == null) {
        return null;
      }
    }
    return c;
  }

  private Container(String name, Map<String, String> aliases) {
    this.name = name;
    this.aliases = aliases;
  }

  /**
   * Name returns the fully-qualified name of the container.
   *
   * <p>The name may conceptually be a namespace, package, or type.
   */
  public String name() {
    return name;
  }

  @Override
  public String toString() {
    return name();
  }

  /**
   * Extend creates a new Container with the existing settings and applies a series of
   * ContainerOptions to further configure the new container.
   */
  public Container extend(ContainerOption... opts) {
    // Copy the name and aliases of the existing container.
    Map<String, String> aliasSet = new HashMap<>(aliasSet());
    Container ext = new Container(name(), aliasSet);

    // Apply the new options to the container.
    for (ContainerOption opt : opts) {
      ext = opt.apply(ext);
      if (ext == null) {
        return null;
      }
    }
    return ext;
  }

  /**
   * ResolveCandidateNames returns the candidates name of namespaced identifiers in C++ resolution
   * order.
   *
   * <p>Names which shadow other names are returned first. If a name includes a leading dot ('.'),
   * the name is treated as an absolute identifier which cannot be shadowed.
   *
   * <p>Given a container name a.b.c.M.N and a type name R.s, this will deliver in order:
   *
   * <p>{@code a.b.c.M.N.R.s}<br>
   * {@code a.b.c.M.R.s}<br>
   * {@code a.b.c.R.s}<br>
   * {@code a.b.R.s}<br>
   * {@code a.R.s}<br>
   * {@code R.s}<br>
   *
   * <p>If aliases or abbreviations are configured for the container, then alias names will take
   * precedence over containerized names.
   */
  public String[] resolveCandidateNames(String name) {
    if (name.startsWith(".")) {
      String qn = name.substring(1);
      String alias = findAlias(qn);
      if (alias != null) {
        return new String[] {alias};
      }
      return new String[] {qn};
    }
    String alias = findAlias(name);
    if (alias != null) {
      return new String[] {alias};
    }
    if (name() == null || name().isEmpty()) {
      return new String[] {name};
    }
    String nextCont = name();
    List<String> candidates = new ArrayList<>();
    candidates.add(nextCont + "." + name);
    for (int i = nextCont.lastIndexOf('.'); i >= 0; i = nextCont.lastIndexOf('.', i - 1)) {
      nextCont = nextCont.substring(0, i);
      candidates.add(nextCont + "." + name);
    }
    candidates.add(name);
    return candidates.toArray(new String[0]);
  }

  /**
   * findAlias takes a name as input and returns an alias expansion if one exists.
   *
   * <p>If the name is qualified, the first component of the qualified name is checked against known
   * aliases. Any alias that is found in a qualified name is expanded in the result:
   *
   * <p>{@code alias: R -> my.alias.R}</br> {@code name: R.S.T}</br> {@code output:
   * my.alias.R.S.T}</br>
   *
   * <p>Note, the name must not have a leading dot.
   */
  String findAlias(String name) {
    // If an alias exists for the name, ensure it is searched last.
    String simple = name;
    String qualifier = "";
    int dot = name.indexOf('.');
    if (dot >= 0) {
      simple = name.substring(0, dot);
      qualifier = name.substring(dot);
    }
    String alias = aliasSet().get(simple);
    if (alias == null) {
      return null;
    }
    return alias + qualifier;
  }

  /**
   * ToQualifiedName converts an expression AST into a qualified name if possible, with a boolean +
   * 'found' value that indicates if the conversion is successful.
   */
  public static String toQualifiedName(Expr e) {
    switch (e.getExprKindCase()) {
      case IDENT_EXPR:
        return e.getIdentExpr().getName();
      case SELECT_EXPR:
        Select sel = e.getSelectExpr();
        if (sel.getTestOnly()) {
          return null;
        }
        String qual = toQualifiedName(sel.getOperand());
        if (qual != null) {
          return qual + "." + sel.getField();
        }
        break;
    }
    return null;
  }

  /** aliasSet returns the alias to fully-qualified name mapping stored in the container. */
  Map<String, String> aliasSet() {
    return aliases;
  }

  /**
   * ContainerOption specifies a functional configuration option for a Container.
   *
   * <p>Note, ContainerOption implementations must be able to handle nil container inputs.
   */
  @FunctionalInterface
  public interface ContainerOption {
    Container apply(Container c);
  }

  /**
   * Abbrevs configures a set of simple names as abbreviations for fully-qualified names. // // An
   * abbreviation (abbrev for short) is a simple name that expands to a fully-qualified name. //
   * Abbreviations can be useful when working with variables, functions, and especially types from
   * // multiple namespaces: // // // CEL object construction // qual.pkg.version.ObjTypeName{ //
   * field: alt.container.ver.FieldTypeName{value: ...} // } // // Only one the qualified names
   * above may be used as the CEL container, so at least one of these // references must be a long
   * qualified name within an otherwise short CEL program. Using the // following abbreviations, the
   * program becomes much simpler: // // // CEL Go option // Abbrevs("qual.pkg.version.ObjTypeName",
   * "alt.container.ver.FieldTypeName") // // Simplified Object construction // ObjTypeName{field:
   * FieldTypeName{value: ...}} // // There are a few rules for the qualified names and the simple
   * abbreviations generated from them: // - Qualified names must be dot-delimited, e.g.
   * `package.subpkg.name`. // - The last element in the qualified name is the abbreviation. // -
   * Abbreviations must not collide with each other. // - The abbreviation must not collide with
   * unqualified names in use. // // Abbreviations are distinct from container-based references in
   * the following important ways: // - Abbreviations must expand to a fully-qualified name. // -
   * Expanded abbreviations do not participate in namespace resolution. // - Abbreviation expansion
   * is done instead of the container search for a matching identifier. // - Containers follow C++
   * namespace resolution rules with searches from the most qualified name // to the least qualified
   * name. // - Container references within the CEL program may be relative, and are resolved to
   * fully // qualified names at either type-check time or program plan time, whichever comes first.
   * // // If there is ever a case where an identifier could be in both the container and as an //
   * abbreviation, the abbreviation wins as this will ensure that the meaning of a program is //
   * preserved between compilations even as the container evolves.
   */
  public static ContainerOption abbrevs(String... qualifiedNames) {
    return c -> {
      for (String qn : qualifiedNames) {
        int ind = qn.lastIndexOf('.');
        if (ind <= 0 || ind >= qn.length() - 1) {
          throw new IllegalArgumentException(
              String.format(
                  "invalid qualified name: %s, wanted name of the form 'qualified.name'", qn));
        }
        String alias = qn.substring(ind + 1);
        c = aliasAs("abbreviation", qn, alias).apply(c);
        if (c == null) {
          return null;
        }
      }
      return c;
    };
  }

  /**
   * Alias associates a fully-qualified name with a user-defined alias. // // In general, Abbrevs is
   * preferred to Alias since the names generated from the Abbrevs option // are more easily traced
   * back to source code. The Alias option is useful for propagating alias // configuration from one
   * Container instance to another, and may also be useful for remapping // poorly chosen protobuf
   * message / package names. // // Note: all of the rules that apply to Abbrevs also apply to
   * Alias.
   */
  public static ContainerOption alias(String qualifiedName, String alias) {
    return aliasAs("alias", qualifiedName, alias);
  }

  static ContainerOption aliasAs(String kind, String qualifiedName, String alias) {
    return c -> {
      if (alias.isEmpty() || alias.indexOf('.') != -1) {
        throw new IllegalArgumentException(
            String.format(
                "%s must be non-empty and simple (not qualified): %s=%s", kind, kind, alias));
      }

      if (qualifiedName.charAt(0) == '.') {
        throw new IllegalArgumentException(
            String.format("qualified name must not begin with a leading '.': %s", qualifiedName));
      }
      int ind = qualifiedName.lastIndexOf('.');
      if (ind <= 0 || ind == qualifiedName.length() - 1) {
        throw new IllegalArgumentException(
            String.format("%s must refer to a valid qualified name: %s", kind, qualifiedName));
      }
      String aliasRef = c.aliasSet().get(alias);
      if (aliasRef != null) {
        throw new IllegalArgumentException(
            String.format(
                "%s collides with existing reference: name=%s, %s=%s, existing=%s",
                kind, qualifiedName, kind, alias, aliasRef));
      }
      if (c.name().startsWith(alias + ".") || c.name().equals(alias)) {
        throw new IllegalArgumentException(
            String.format(
                "%s collides with container name: name=%s, %s=%s, container=%s",
                kind, qualifiedName, kind, alias, c.name()));
      }
      Map<String, String> aliases = new HashMap<>(c.aliasSet());
      aliases.put(alias, qualifiedName);
      c = new Container(c.name, aliases);
      return c;
    };
  }

  /** Name sets the fully-qualified name of the Container. */
  public static ContainerOption name(String name) {
    return c -> {
      if (!name.isEmpty() && name.charAt(0) == '.') {
        throw new IllegalArgumentException(
            String.format("container name must not contain a leading '.': %s", name));
      }
      if (c.name.equals(name)) {
        return c;
      }
      c = new Container(name, c.aliases);
      return c;
    };
  }
}
