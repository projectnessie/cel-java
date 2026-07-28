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
package org.projectnessie.cel.tools;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.projectnessie.cel.Env.newCustomEnv;
import static org.projectnessie.cel.EnvOption.container;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.EvalOption.OptOptimize;
import static org.projectnessie.cel.Library.StdLib;
import static org.projectnessie.cel.ProgramOption.evalOptions;

import com.google.api.expr.v1alpha1.Decl;
import java.util.ArrayList;
import java.util.List;
import org.projectnessie.cel.Ast;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.EvalOption;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.RegexEngine;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.TypeRegistry;

/**
 * Immutable, reusable configuration for compiling CEL source text into {@link Script} instances.
 *
 * <p>Configure declarations, types, libraries, a container, and execution policy through {@link
 * #newBuilder()}, then call {@link #compile(String)} for each source expression that uses that
 * configuration. A compiled {@link Script} can be evaluated repeatedly with different inputs.
 *
 * <pre>{@code
 * ScriptCompiler compiler =
 *     ScriptCompiler.newBuilder()
 *         .withDeclarations(Decls.newVar("name", Decls.String))
 *         .build();
 *
 * Script greeting = compiler.compile("'Hello, ' + name");
 * }</pre>
 *
 * <p>Collection inputs are copied when added to the builder. {@link Builder#build()} snapshots the
 * accumulated builder state and supplied type registry, then registers configured types in that
 * owned registry snapshot. A built compiler exposes no configuration mutation. The standard CEL
 * library, {@link RegexEngine#JAVA Java regular expressions}, and {@link EvalOption#OptOptimize
 * constant optimization} are enabled by default.
 *
 * <p>A built compiler may compile sources concurrently. Custom registries, adapters, providers,
 * libraries, and program options used by the configuration must support the concurrent access they
 * receive. The thread-safety and input-stability contracts of compiled scripts are defined by
 * {@link org.projectnessie.cel.Program}.
 */
public final class ScriptCompiler {

  private final Env env;
  private final List<ProgramOption> programOptions;

  private ScriptCompiler(Env env, List<ProgramOption> programOptions) {
    this.env = env;
    this.programOptions = programOptions;
  }

  /**
   * Compiles one CEL source expression using this compiler's configuration.
   *
   * <p>Parse and type-check failures are reported as {@link ScriptCreateException}. Each successful
   * invocation returns a separate script and program. This method may be invoked concurrently,
   * subject to the custom-component concurrency requirements documented on this class.
   *
   * @param sourceText CEL expression source text
   * @return the compiled, reusable script
   * @throws NullPointerException if {@code sourceText} is {@code null}
   * @throws IllegalArgumentException if {@code sourceText} is blank
   * @throws ScriptCreateException if parsing or type checking fails
   */
  public Script compile(String sourceText) throws ScriptCreateException {
    return compile(env, sourceText, programOptions);
  }

  static Script compile(Env env, String sourceText, List<ProgramOption> programOptions)
      throws ScriptCreateException {
    requireNonNull(sourceText, "sourceText");
    if (sourceText.trim().isEmpty()) {
      throw new IllegalArgumentException("No source code.");
    }

    AstIssuesTuple astIssues = env.parse(sourceText);
    if (astIssues.hasIssues()) {
      throw new ScriptCreateException("parse failed", astIssues.getIssues());
    }
    Ast ast = astIssues.getAst();

    astIssues = env.check(ast);
    if (astIssues.hasIssues()) {
      throw new ScriptCreateException("check failed", astIssues.getIssues());
    }

    Program program =
        env.program(
            astIssues.getAst(), programOptions.toArray(new ProgramOption[programOptions.size()]));
    return new Script(env, program);
  }

  /**
   * Returns a new mutable builder for an immutable {@link ScriptCompiler}.
   *
   * @return a new builder with the standard library, Java regular expressions, constant
   *     optimization enabled, native planning permitted, and the default Protobuf type registry
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Mutable construction state for {@link ScriptCompiler}.
   *
   * <p>Configuration methods append to the existing declarations, types, and libraries. A builder
   * is not thread-safe. Each call to {@link #build()} snapshots its current state and returns an
   * independent compiler.
   */
  public static final class Builder {
    private boolean disableOptimize;
    private TypeRegistry registry;
    private RegexEngine regexEngine = RegexEngine.JAVA;
    private String container;
    private final List<Decl> declarations = new ArrayList<>();
    private final List<Object> types = new ArrayList<>();
    private final List<Library> libraries = new ArrayList<>();

    private Builder() {}

    /**
     * Disables {@link EvalOption#OptOptimize} for every source compiled by the resulting compiler.
     *
     * <p>This disables program-creation-time constant optimization. It does not disable
     * planner-selected native evaluation. When established interpreter planning is required, use
     * the lower-level {@link Env#program(Ast, ProgramOption...)} API with {@link
     * ProgramOption#evalOptions(EvalOption...)} and {@link EvalOption#OptDisableNativeEval}.
     *
     * @return this builder
     * @see EvalOption#OptOptimize
     */
    public Builder disableOptimize() {
      this.disableOptimize = true;
      return this;
    }

    /**
     * Selects the type registry to snapshot when {@link #build()} is called.
     *
     * <p>The built compiler owns the snapshot. Later mutations of the supplied registry are not
     * visible to it, and configured types do not mutate the supplied registry. When this method is
     * not called, the compiler creates a {@link ProtoTypeRegistry}.
     *
     * @param registry registry to snapshot
     * @return this builder
     * @throws NullPointerException if {@code registry} is {@code null}
     */
    public Builder registry(TypeRegistry registry) {
      this.registry = requireNonNull(registry, "registry");
      return this;
    }

    /**
     * Selects the regular-expression engine for the standard CEL {@code matches} function.
     *
     * <p>The default is {@link RegexEngine#JAVA}.
     *
     * @param regexEngine engine used by every compiled script
     * @return this builder
     * @throws NullPointerException if {@code regexEngine} is {@code null}
     */
    public Builder regexEngine(RegexEngine regexEngine) {
      this.regexEngine = requireNonNull(regexEngine, "regexEngine");
      return this;
    }

    /**
     * Selects the CEL container used to resolve qualified names.
     *
     * @param container CEL container name
     * @return this builder
     * @throws NullPointerException if {@code container} is {@code null}
     */
    public Builder withContainer(String container) {
      this.container = requireNonNull(container, "container");
      return this;
    }

    /**
     * Adds declarations to the compiler configuration.
     *
     * @param declarations variable and function declarations
     * @return this builder
     * @throws NullPointerException if the array or an element is {@code null}
     */
    public Builder withDeclarations(Decl... declarations) {
      return withDeclarations(asList(declarations));
    }

    /**
     * Adds declarations to the compiler configuration.
     *
     * <p>The list contents are copied by this method.
     *
     * @param declarations variable and function declarations
     * @return this builder
     * @throws NullPointerException if the list or an element is {@code null}
     */
    public Builder withDeclarations(List<Decl> declarations) {
      this.declarations.addAll(List.copyOf(declarations));
      return this;
    }

    /**
     * Adds native type descriptions to the compiler configuration.
     *
     * @param types native type descriptions accepted by the configured registry
     * @return this builder
     * @throws NullPointerException if the array or an element is {@code null}
     */
    public Builder withTypes(Object... types) {
      return withTypes(asList(types));
    }

    /**
     * Adds native type descriptions to the compiler configuration.
     *
     * <p>The list contents are copied by this method. Types are registered in the compiler-owned
     * registry during {@link #build()}, before any source can be compiled.
     *
     * @param types native type descriptions accepted by the configured registry
     * @return this builder
     * @throws NullPointerException if the list or an element is {@code null}
     */
    public Builder withTypes(List<Object> types) {
      this.types.addAll(List.copyOf(types));
      return this;
    }

    /**
     * Adds CEL libraries to the compiler configuration.
     *
     * @param libraries libraries providing compile-time and program options
     * @return this builder
     * @throws NullPointerException if the array or an element is {@code null}
     */
    public Builder withLibraries(Library... libraries) {
      return withLibraries(asList(libraries));
    }

    /**
     * Adds CEL libraries to the compiler configuration.
     *
     * <p>The list contents are copied by this method. Library options are obtained while {@link
     * #build()} constructs the compiler environment.
     *
     * @param libraries libraries providing compile-time and program options
     * @return this builder
     * @throws NullPointerException if the list or an element is {@code null}
     */
    public Builder withLibraries(List<Library> libraries) {
      this.libraries.addAll(List.copyOf(libraries));
      return this;
    }

    /**
     * Builds an immutable compiler from a snapshot of this builder's configuration.
     *
     * <p>A supplied registry is copied exactly once. Configured types and libraries are installed
     * into the owned environment before the compiler is returned.
     *
     * @return a reusable compiler
     * @throws RuntimeException if a configured type, declaration, library, or option cannot be
     *     installed
     */
    public ScriptCompiler build() {
      TypeRegistry ownedRegistry =
          registry == null ? ProtoTypeRegistry.newRegistry() : registry.copy();

      List<EnvOption> envOptions = new ArrayList<>();
      envOptions.add(StdLib());
      envOptions.add(declarations(List.copyOf(declarations)));
      envOptions.add(types(List.copyOf(types)));
      if (container != null) {
        envOptions.add(container(container));
      }
      for (Library library : List.copyOf(libraries)) {
        envOptions.add(Library.Lib(library));
      }

      Env env = newCustomEnv(ownedRegistry, envOptions);

      List<ProgramOption> programOptions = new ArrayList<>();
      programOptions.add(ProgramOption.regexEngine(regexEngine));
      if (!disableOptimize) {
        programOptions.add(evalOptions(OptOptimize));
      }
      return new ScriptCompiler(env, List.copyOf(programOptions));
    }
  }
}
