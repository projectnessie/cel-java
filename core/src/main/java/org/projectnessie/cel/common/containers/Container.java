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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable CEL namespace container with optional aliases.
 *
 * <p>A container expands relative variable, function, and type names from the most-qualified
 * candidate to the least-qualified candidate. Explicit aliases take precedence over namespace
 * expansion. Candidate results are cached internally and returned as defensive array copies, so a
 * configured container can be reused concurrently.
 */
public final class Container {

  /** Container with an empty namespace and no aliases. */
  public static final Container defaultContainer = new Container("", Collections.emptyMap());

  private final String name;
  private final Map<String, String> aliases;
  private final Map<String, String[]> candidateNameCache = new ConcurrentHashMap<>();

  /**
   * Creates a container by applying options in order.
   *
   * @return configured container, or {@code null} if an option returns {@code null}
   */
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
   * Returns the fully-qualified name of the container.
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
   * Returns a separately configured container derived from this container.
   *
   * @return configured copy, or {@code null} if an option returns {@code null}
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
   * Returns namespace candidates in CEL resolution order.
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
    return candidateNameCache.computeIfAbsent(name, this::computeCandidateNames).clone();
  }

  private String[] computeCandidateNames(String name) {
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
   * Converts an identifier/select expression to a qualified name.
   *
   * @return qualified name, or {@code null} when the expression is not a qualified-name shape
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
   * Functional container configuration option.
   *
   * <p>Built-in options require a non-null input. Returning {@code null} stops option application
   * and causes {@link #newContainer(ContainerOption...)} or {@link #extend(ContainerOption...)} to
   * return {@code null}.
   */
  @FunctionalInterface
  public interface ContainerOption {
    /** Applies this option and returns the resulting container. */
    Container apply(Container c);
  }

  /**
   * Configures abbreviations derived from the final component of fully-qualified names.
   *
   * <p>For example, {@code abbrevs("qual.pkg.Message")} makes {@code Message} resolve directly to
   * {@code qual.pkg.Message}. Abbreviations are expanded before namespace search and must be
   * unique.
   *
   * @throws IllegalArgumentException if a name is not qualified or an abbreviation collides
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
   * Associates a fully-qualified name with an explicit simple alias.
   *
   * <p>Prefer {@link #abbrevs(String...)} when the final name component is suitable. Alias
   * expansion precedes namespace search.
   *
   * @throws IllegalArgumentException if either name is invalid or the alias collides
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

  /**
   * Sets the fully-qualified namespace name.
   *
   * @throws IllegalArgumentException if the name starts with a dot
   */
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
