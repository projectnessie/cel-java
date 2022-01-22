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

import com.google.api.expr.v1alpha1.Decl;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.projectnessie.cel.Ast;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.EvalOption;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.TypeRegistry;

/**
 * Manages {@link Script} instances, works like a factory to generate reusable scripts.
 *
 * <p>The current implementation is rather dumb, but it might be extended in the future to cache
 * {@link Script} instances returned by {@link #getOrCreateScript(String, List, List)}.
 */
public final class ScriptHost {

  private final boolean disableOptimize;
  private final TypeRegistry registry;

  private ScriptHost(boolean disableOptimize, TypeRegistry registry) {
    this.disableOptimize = disableOptimize;
    this.registry = registry;
  }

  /** Use {@link #buildScript(String)}. */
  @Deprecated
  public Script getOrCreateScript(String sourceText, List<Decl> declarations, List<Object> types)
      throws ScriptException {
    return buildScript(sourceText).withDeclarations(declarations).withTypes(types).build();
  }

  public ScriptBuilder buildScript(String sourceText) {
    if (sourceText.trim().isEmpty()) {
      throw new IllegalArgumentException("No source code.");
    }
    return new ScriptBuilder(sourceText);
  }

  public final class ScriptBuilder {
    private final String sourceText;
    private String container;
    private final List<Decl> declarations = new ArrayList<>();
    private final List<Object> types = new ArrayList<>();
    private final List<Library> libraries = new ArrayList<>();

    private ScriptBuilder(String sourceText) {
      this.sourceText = sourceText;
    }

    public ScriptBuilder withContainer(String container) {
      this.container = container;
      return this;
    }

    public ScriptBuilder withDeclarations(Decl... declarations) {
      return withDeclarations(asList(declarations));
    }

    public ScriptBuilder withDeclarations(List<Decl> declarations) {
      this.declarations.addAll(declarations);
      return this;
    }

    public ScriptBuilder withTypes(Object... types) {
      return withTypes(asList(types));
    }

    public ScriptBuilder withTypes(List<Object> types) {
      this.types.addAll(types);
      return this;
    }

    public ScriptBuilder withLibraries(Library... libraries) {
      return withLibraries(asList(libraries));
    }

    public ScriptBuilder withLibraries(List<Library> libraries) {
      this.libraries.addAll(libraries);
      return this;
    }

    public Script build() throws ScriptCreateException {
      List<EnvOption> envOptions = new ArrayList<>();
      envOptions.add(StdLib());
      envOptions.add(declarations(declarations));
      envOptions.add(types(types));
      if (container != null) {
        envOptions.add(container(container));
      }
      envOptions.addAll(libraries.stream().map(Library::Lib).collect(Collectors.toList()));

      Env env = newCustomEnv(registry, envOptions);

      AstIssuesTuple astIss = env.parse(sourceText);
      if (astIss.hasIssues()) {
        throw new ScriptCreateException("parse failed", astIss.getIssues());
      }
      Ast ast = astIss.getAst();

      astIss = env.check(ast);
      if (astIss.hasIssues()) {
        throw new ScriptCreateException("check failed", astIss.getIssues());
      }

      List<ProgramOption> programOptions = new ArrayList<>();
      if (!disableOptimize) {
        programOptions.add(evalOptions(OptOptimize));
      }

      Program prg = env.program(ast, programOptions.toArray(new ProgramOption[] {}));

      return new Script(env, prg);
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    private Builder() {}

    private boolean disableOptimize;

    private TypeRegistry registry;

    /**
     * Call to instruct the built {@link ScriptHost} to disable script optimizations.
     *
     * @see EvalOption#OptOptimize
     */
    public Builder disableOptimize() {
      this.disableOptimize = true;
      return this;
    }

    /**
     * Use the given {@link TypeRegistry}.
     *
     * <p>The implementation will fall back to {@link
     * org.projectnessie.cel.common.types.pb.ProtoTypeRegistry}.
     */
    public Builder registry(TypeRegistry registry) {
      this.registry = registry;
      return this;
    }

    public ScriptHost build() {
      TypeRegistry r = registry;
      if (r == null) {
        r = ProtoTypeRegistry.newRegistry();
      }
      return new ScriptHost(disableOptimize, r);
    }
  }
}
