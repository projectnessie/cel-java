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
package org.projectnessie.cel.tools;

import static java.util.Arrays.asList;
import static org.projectnessie.cel.Env.newCustomEnv;
import static org.projectnessie.cel.EnvOption.container;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.EvalOption.OptOptimize;
import static org.projectnessie.cel.Library.StdLib;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.ProgramOption.regexEngine;

import com.google.api.expr.v1alpha1.Decl;
import java.util.ArrayList;
import java.util.List;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.EvalOption;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.RegexEngine;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.TypeRegistry;

/**
 * Manages {@link Script} instances and their host-scoped type registry.
 *
 * <p>A host owns a registry snapshot. Types contributed through any {@link ScriptBuilder} become
 * part of that host's cumulative type universe and are visible to subsequently built scripts.
 * Equivalent repeated type registrations are allowed. Conflicting or invalid registrations still
 * fail.
 *
 * <p>Complete all type-contributing script builds before sharing the host or its scripts for
 * concurrent use. A host does not support adding types concurrently with script construction or
 * evaluation.
 *
 * <pre>{@code
 * ScriptCompiler compiler =
 *     ScriptCompiler.newBuilder()
 *         .withDeclarations(Decls.newVar("name", Decls.String))
 *         .build();
 * Script script = compiler.compile("'Hello, ' + name");
 * }</pre>
 *
 * @deprecated Use {@link ScriptCompiler} to configure declarations, types, libraries, and policy
 *     once, then compile one or more source expressions. This class remains available for
 *     compatibility with its source-first, cumulative-type lifecycle.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public final class ScriptHost {

  private final boolean disableOptimize;
  private final TypeRegistry registry;
  private final RegexEngine regexEngine;

  private ScriptHost(boolean disableOptimize, TypeRegistry registry, RegexEngine regexEngine) {
    this.disableOptimize = disableOptimize;
    this.registry = registry;
    this.regexEngine = regexEngine;
  }

  /**
   * Compiles a script using declarations and types supplied for this invocation.
   *
   * @param sourceText CEL expression source text
   * @param declarations variable and function declarations
   * @param types native type descriptions accepted by the host registry
   * @return a reusable compiled script
   * @throws ScriptException if parsing or type checking fails
   * @deprecated Use {@link #buildScript(String)}, or preferably {@link ScriptCompiler}.
   */
  @Deprecated
  public Script getOrCreateScript(String sourceText, List<Decl> declarations, List<Object> types)
      throws ScriptException {
    return buildScript(sourceText).withDeclarations(declarations).withTypes(types).build();
  }

  /**
   * Starts configuring a script for the supplied CEL source.
   *
   * <p>The returned builder is tied to this host. Types added to it are registered in the host's
   * cumulative registry when {@link ScriptBuilder#build()} is called.
   *
   * <p>Prefer {@link ScriptCompiler#compile(String)} after configuring a reusable compiler.
   *
   * @param sourceText CEL expression source text
   * @return a mutable builder for that source
   * @throws NullPointerException if {@code sourceText} is {@code null}
   * @throws IllegalArgumentException if {@code sourceText} is blank
   */
  public ScriptBuilder buildScript(String sourceText) {
    if (sourceText.trim().isEmpty()) {
      throw new IllegalArgumentException("No source code.");
    }
    return new ScriptBuilder(sourceText);
  }

  /**
   * Mutable, host-associated construction state for one {@link Script}.
   *
   * <p>Instances are not thread-safe. Declaration, type, and library methods append to their
   * existing configuration.
   *
   * <p>Prefer {@link ScriptCompiler.Builder} for isolated, configuration-first construction.
   */
  public final class ScriptBuilder {
    private final String sourceText;
    private String container;
    private final List<Decl> declarations = new ArrayList<>();
    private final List<Object> types = new ArrayList<>();
    private final List<Library> libraries = new ArrayList<>();

    private ScriptBuilder(String sourceText) {
      this.sourceText = sourceText;
    }

    /**
     * Selects the CEL container used to resolve qualified names.
     *
     * @param container CEL container name
     * @return this builder
     */
    public ScriptBuilder withContainer(String container) {
      this.container = container;
      return this;
    }

    /**
     * Adds declarations used while checking this script.
     *
     * @param declarations variable and function declarations
     * @return this builder
     */
    public ScriptBuilder withDeclarations(Decl... declarations) {
      return withDeclarations(asList(declarations));
    }

    /**
     * Adds declarations used while checking this script.
     *
     * <p>The declaration references are copied into this builder.
     *
     * @param declarations variable and function declarations
     * @return this builder
     */
    public ScriptBuilder withDeclarations(List<Decl> declarations) {
      this.declarations.addAll(declarations);
      return this;
    }

    /**
     * Adds types to this host's cumulative type universe while building the script.
     *
     * @param types native type descriptions accepted by the host registry
     * @return this builder
     */
    public ScriptBuilder withTypes(Object... types) {
      return withTypes(asList(types));
    }

    /**
     * Adds types to this host's cumulative type universe while building the script.
     *
     * <p>The types are visible to scripts built subsequently by the same host. Repeating equivalent
     * registrations is allowed. Complete type registration before sharing the host or its scripts
     * for concurrent use.
     *
     * @param types native type descriptions accepted by the host registry
     * @return this builder
     */
    public ScriptBuilder withTypes(List<Object> types) {
      this.types.addAll(types);
      return this;
    }

    /**
     * Adds libraries used to check and evaluate this script.
     *
     * @param libraries libraries providing compile-time and program options
     * @return this builder
     */
    public ScriptBuilder withLibraries(Library... libraries) {
      return withLibraries(asList(libraries));
    }

    /**
     * Adds libraries used to check and evaluate this script.
     *
     * <p>The library references are copied into this builder.
     *
     * @param libraries libraries providing compile-time and program options
     * @return this builder
     */
    public ScriptBuilder withLibraries(List<Library> libraries) {
      this.libraries.addAll(libraries);
      return this;
    }

    /**
     * Compiles this builder's source and updates the host's cumulative registry with configured
     * types.
     *
     * @return a reusable compiled script
     * @throws ScriptCreateException if parsing or type checking fails
     */
    public Script build() throws ScriptCreateException {
      List<EnvOption> envOptions = new ArrayList<>();
      envOptions.add(StdLib());
      envOptions.add(declarations(declarations));
      envOptions.add(types(types));
      if (container != null) {
        envOptions.add(container(container));
      }
      envOptions.addAll(libraries.stream().map(Library::Lib).toList());

      Env env = newCustomEnv(registry, envOptions);

      List<ProgramOption> programOptions = new ArrayList<>();
      programOptions.add(regexEngine(regexEngine));
      if (!disableOptimize) {
        programOptions.add(evalOptions(OptOptimize));
      }
      return ScriptCompiler.compile(env, sourceText, programOptions);
    }
  }

  /**
   * Returns a builder for a compatibility {@link ScriptHost}.
   *
   * <p>Prefer {@link ScriptCompiler#newBuilder()}.
   *
   * @return a new host builder
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Mutable construction state for {@link ScriptHost}; instances are not thread-safe.
   *
   * <p>Prefer {@link ScriptCompiler.Builder}.
   */
  public static final class Builder {
    private Builder() {}

    private boolean disableOptimize;

    private TypeRegistry registry;
    private RegexEngine regexEngine = RegexEngine.JAVA;

    /**
     * Disables {@link EvalOption#OptOptimize} for scripts built by the resulting host.
     *
     * <p>This does not disable planner-selected native evaluation.
     *
     * @return this builder
     * @see EvalOption#OptOptimize
     */
    public Builder disableOptimize() {
      this.disableOptimize = true;
      return this;
    }

    /**
     * Uses a snapshot of the given {@link TypeRegistry} as the host's cumulative type universe.
     *
     * <p>The snapshot is taken by {@link #build()}. Later mutations of the supplied registry are
     * not visible to the host, and types registered while building scripts do not mutate the
     * supplied registry. The implementation falls back to {@link
     * org.projectnessie.cel.common.types.pb.ProtoTypeRegistry} when no registry is supplied.
     *
     * @param registry registry to snapshot, or {@code null} to restore the default
     * @return this builder
     */
    public Builder registry(TypeRegistry registry) {
      this.registry = registry;
      return this;
    }

    /**
     * Selects the regular-expression engine used by the standard CEL {@code matches} function.
     *
     * <p>The default is {@link RegexEngine#JAVA}. The selection applies to all scripts built by the
     * resulting host.
     *
     * @param regexEngine engine used by every script
     * @return this builder
     * @throws NullPointerException if {@code regexEngine} is {@code null}
     */
    public Builder regexEngine(RegexEngine regexEngine) {
      this.regexEngine = java.util.Objects.requireNonNull(regexEngine, "regexEngine");
      return this;
    }

    /**
     * Builds a host with a snapshot of the configured registry.
     *
     * @return compatibility script host
     */
    public ScriptHost build() {
      TypeRegistry r = registry;
      if (r == null) {
        r = ProtoTypeRegistry.newRegistry();
      } else {
        r = r.copy();
      }
      return new ScriptHost(disableOptimize, r, regexEngine);
    }
  }
}
