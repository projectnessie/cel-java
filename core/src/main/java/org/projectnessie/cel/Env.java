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
package org.projectnessie.cel;

import static java.util.Arrays.asList;
import static org.projectnessie.cel.CEL.astToParsedExpr;
import static org.projectnessie.cel.CEL.astToString;
import static org.projectnessie.cel.CEL.newProgram;
import static org.projectnessie.cel.CEL.parsedExprToAst;
import static org.projectnessie.cel.CEL.partialVars;
import static org.projectnessie.cel.EnvOption.EnvFeature.FeatureDisableDynamicAggregateLiterals;
import static org.projectnessie.cel.Issues.newIssues;
import static org.projectnessie.cel.Library.StdLib;
import static org.projectnessie.cel.common.Location.NoLocation;
import static org.projectnessie.cel.common.Source.newTextSource;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newRegistry;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.AstPruner.pruneAst;
import static org.projectnessie.cel.interpreter.AttributePattern.newAttributePattern;
import static org.projectnessie.cel.parser.Parser.parseWithMacros;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Decl;
import com.google.api.expr.v1alpha1.Decl.DeclKindCase;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.ParsedExpr;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.projectnessie.cel.EnvOption.EnvFeature;
import org.projectnessie.cel.checker.Checker;
import org.projectnessie.cel.checker.Checker.CheckResult;
import org.projectnessie.cel.checker.CheckerEnv;
import org.projectnessie.cel.common.Errors;
import org.projectnessie.cel.common.Source;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.internal.ControlledOperation;
import org.projectnessie.cel.internal.OperationController;
import org.projectnessie.cel.internal.OperationScope;
import org.projectnessie.cel.interpreter.Activation.PartialActivation;
import org.projectnessie.cel.interpreter.AttributePattern;
import org.projectnessie.cel.parser.Macro;
import org.projectnessie.cel.parser.Parser.ParseResult;

/**
 * Configures parsing, type checking, and executable-program creation for CEL expressions.
 *
 * <p>{@link #newEnv(EnvOption...)} includes the standard CEL library. Use {@link
 * #newCustomEnv(EnvOption...)} only when assembling a deliberately custom or subset environment. A
 * typical low-level lifecycle is:
 *
 * <pre>{@code
 * Env env = Env.newEnv(EnvOption.declarations(Decls.newVar("name", Decls.String)));
 * Env.AstIssuesTuple compiled = env.compile("name.startsWith('A')");
 * if (compiled.hasIssues()) {
 *   throw compiled.getIssues().err();
 * }
 * Program program = env.program(compiled.getAst());
 * }</pre>
 *
 * <p>An environment may be configured until a call to {@link #check(Ast)} reaches checker
 * initialization. Reaching that boundary permanently freezes the environment configuration, even if
 * checker initialization fails. An invalid direct AST that cannot be converted for checking fails
 * before this boundary. Use {@link #extend(EnvOption...)} to derive a separately configurable
 * environment after freezing. Built-in {@link EnvOption} values and {@link #setFeature(EnvFeature)}
 * throw {@link IllegalStateException} when applied to a frozen environment.
 *
 * <p>Once configuration is complete, parsing and checking may be performed concurrently provided
 * that any custom type provider supports concurrent reads. Registry mutation must be completed
 * before the environment is checked or otherwise shared. Other concurrent uses of custom adapters
 * and providers remain subject to those components' own contracts.
 */
public final class Env {

  private static final String FROZEN_CONFIGURATION_MESSAGE =
      "environment configuration is frozen after the first check; use extend() to configure a new"
          + " environment";

  Container container;
  final List<Decl> declarations;
  final List<Macro> macros;
  TypeAdapter adapter;
  TypeProvider provider;
  private final Set<EnvFeature> features;

  /** program options tied to the environment. */
  private final List<ProgramOption> progOpts;

  /** Internal checker representation */
  private CheckerEnv chk;

  private RuntimeException chkErr;
  private boolean frozen;
  private final Object lifecycleLock = new Object();

  private Env(
      Container container,
      List<Decl> declarations,
      List<Macro> macros,
      TypeAdapter adapter,
      TypeProvider provider,
      Set<EnvFeature> features,
      List<ProgramOption> progOpts) {
    this.container = container;
    this.declarations = declarations;
    this.macros = macros;
    this.adapter = adapter;
    this.provider = provider;
    this.features = features;
    this.progOpts = progOpts;
  }

  /**
   * Creates an environment with the standard CEL declarations, functions, and macros.
   *
   * <p>The supplied options are applied after the standard library and in argument order.
   *
   * @param opts additional environment configuration
   * @return configurable standard environment
   * @throws RuntimeException if an option cannot be applied
   */
  public static Env newEnv(EnvOption... opts) {
    List<EnvOption> stdOpts = new ArrayList<>(opts.length + 1);
    stdOpts.add(StdLib());
    Collections.addAll(stdOpts, opts);
    return newCustomEnv(stdOpts.toArray(new EnvOption[0]));
  }

  /**
   * Creates a custom environment backed by a supplied type registry.
   *
   * <p>The standard library is not installed automatically. Add {@link Library#StdLib()} when it is
   * wanted. The environment initially uses {@code registry} directly as both its type adapter and
   * provider; it does not copy the registry. Later options may replace either role. Complete
   * registry mutation before checking or sharing the environment.
   *
   * <p>Restricting available functions and macros can reduce the language surface accepted by this
   * environment, but does not by itself impose a CPU, memory, result-size, or latency budget.
   *
   * @param registry registry used for type adaptation and lookup
   * @param opts environment options applied in list order
   * @return configurable custom environment
   * @throws RuntimeException if an option cannot be applied
   */
  public static Env newCustomEnv(TypeRegistry registry, List<EnvOption> opts) {
    return new Env(
            defaultContainer,
            new ArrayList<>(),
            new ArrayList<>(),
            registry,
            registry,
            EnumSet.noneOf(EnvFeature.class),
            new ArrayList<>())
        .configure(opts);
  }

  /**
   * Creates a custom environment with a new default Protobuf type registry.
   *
   * <p>The standard library is not installed automatically. Options are applied in argument order.
   *
   * @param opts environment configuration
   * @return configurable custom environment
   * @throws RuntimeException if an option cannot be applied
   */
  public static Env newCustomEnv(EnvOption... opts) {
    return newCustomEnv(newRegistry(), asList(opts));
  }

  void addProgOpts(List<ProgramOption> progOpts) {
    applyConfiguration(e -> e.progOpts.addAll(progOpts));
  }

  /**
   * Result of parsing, checking, or compiling CEL source.
   *
   * <p>{@link #getIssues()} is always non-null. When {@link #hasIssues()} is {@code true}, {@link
   * #getAst()} may be {@code null} and must not be treated as executable.
   */
  public static final class AstIssuesTuple {
    private final Ast ast;
    private final Issues issues;

    AstIssuesTuple(Ast ast, Issues issues) {
      this.ast = ast;
      this.issues = Objects.requireNonNull(issues);
    }

    /**
     * Reports whether parsing or checking produced errors.
     *
     * @return {@code true} when the result contains at least one error
     */
    public boolean hasIssues() {
      return issues.hasIssues();
    }

    /**
     * Returns the parsed or checked AST, when one was produced.
     *
     * @return AST, or {@code null} when the operation failed before producing a usable AST
     */
    public Ast getAst() {
      return ast;
    }

    /**
     * Returns diagnostics associated with the operation.
     *
     * @return non-null diagnostics
     */
    public Issues getIssues() {
      return issues;
    }
  }

  /**
   * Type-checks an AST and returns a checked AST or diagnostics.
   *
   * <p>A call that reaches checker initialization permanently freezes this environment's
   * configuration. Invalid direct AST input may fail during conversion before the freeze boundary.
   * Configure a derived environment with {@link #extend(EnvOption...)} when different declarations,
   * types, macros, features, or other options are needed.
   *
   * @param ast parsed AST to check
   * @return checked AST and diagnostics; inspect {@link AstIssuesTuple#hasIssues()} before using
   *     the AST
   * @throws NullPointerException if {@code ast} is {@code null}
   */
  public AstIssuesTuple check(Ast ast) {
    // Note, errors aren't currently possible on the Ast to ParsedExpr conversion.
    ParsedExpr pe = astToParsedExpr(ast);

    // Construct the internal checker env, erroring if there is an issue adding the declarations.
    synchronized (lifecycleLock) {
      frozen = true;
      if (chk == null && chkErr == null) {
        CheckerEnv ce = CheckerEnv.newCheckerEnv(container, provider);
        ce.enableDynamicAggregateLiterals(true);
        if (hasFeature(FeatureDisableDynamicAggregateLiterals)) {
          ce.enableDynamicAggregateLiterals(false);
        }
        try {
          ce.add(declarations);
          chk = ce;
        } catch (RuntimeException e) {
          chkErr = e;
        } catch (Exception e) {
          chkErr = new RuntimeException(e);
        }
      }
    }

    // Checker initialization under the lifecycle lock ensures that this value is stable for all
    // invocations.
    if (chkErr != null) {
      Errors errs = new Errors(ast.getSource());
      errs.reportError(chkErr, NoLocation, "%s", chkErr.toString());
      return new AstIssuesTuple(null, newIssues(errs));
    }

    ParseResult pr = new ParseResult(pe.getExpr(), new Errors(ast.getSource()), pe.getSourceInfo());
    CheckResult checkRes = Checker.Check(pr, ast.getSource(), chk);
    if (checkRes.hasErrors()) {
      return new AstIssuesTuple(null, newIssues(checkRes.getErrors()));
    }
    // Manually create the Ast to ensure that the Ast source information (which may be more
    // detailed than the information provided by Check), is returned to the caller.
    CheckedExpr ce = checkRes.getCheckedExpr();
    ast =
        new Ast(
            ce.getExpr(),
            ce.getSourceInfo(),
            ast.getSource(),
            ce.getReferenceMapMap(),
            ce.getTypeMapMap());
    return new AstIssuesTuple(ast, Issues.noIssues(ast.getSource()));
  }

  /** Creates a cancellation-only one-shot type-check operation. */
  public CancelableOperation<AstIssuesTuple> checkCancelable(Ast ast) {
    return checkCancelable(ast, ResourceLimits.unlimited());
  }

  /**
   * Creates a one-shot type-check operation with resource limits.
   *
   * <p>The environment remains frozen if execution reaches checker initialization, even if the
   * operation is subsequently cancelled or exceeds a limit.
   *
   * @param ast caller-supplied parsed or checked AST
   * @param limits immutable limits for admission and checking
   * @return a lazy one-shot operation
   * @throws NullPointerException if an argument is {@code null}
   */
  public CancelableOperation<AstIssuesTuple> checkCancelable(Ast ast, ResourceLimits limits) {
    Objects.requireNonNull(ast, "ast");
    var controller = operationController(Objects.requireNonNull(limits, "limits"));
    return new ControlledOperation<>(
        controller,
        OperationAbortedException.Phase.SOURCE_ADMISSION,
        () -> {
          AstAdmission.check(ast, controller, OperationAbortedException.Phase.SOURCE_ADMISSION);
          controller.checkpointNow(OperationAbortedException.Phase.CHECK);
          var checked = check(ast);
          if (!checked.hasIssues()) {
            AstAdmission.check(checked.ast, controller, OperationAbortedException.Phase.CHECK);
          }
          return checked;
        });
  }

  /**
   * Parses and type-checks CEL source text.
   *
   * <p>If parsing fails, checking is not attempted and the environment is not frozen by this call.
   * If parsing succeeds, the checking phase freezes the environment as described by {@link
   * #check(Ast)}.
   *
   * @param txt CEL expression source
   * @return checked AST and diagnostics
   * @see #parse(String)
   */
  public AstIssuesTuple compile(String txt) {
    return compileSource(newTextSource(txt));
  }

  /** Creates a cancellation-only parse-and-check operation. */
  public CancelableOperation<AstIssuesTuple> compileCancelable(String txt) {
    return compileCancelable(txt, ResourceLimits.unlimited());
  }

  /**
   * Creates a one-shot parse-and-check operation with resource limits.
   *
   * @param txt CEL expression source
   * @param limits immutable limits spanning parsing and checking
   * @return a lazy one-shot operation
   * @throws NullPointerException if an argument is {@code null}
   */
  public CancelableOperation<AstIssuesTuple> compileCancelable(String txt, ResourceLimits limits) {
    Objects.requireNonNull(txt, "txt");
    return compileSourceCancelable(newTextSource(txt), limits);
  }

  /**
   * Parses and type-checks a CEL source.
   *
   * <p>If parsing fails, checking is not attempted and the environment is not frozen by this call.
   * If parsing succeeds, the checking phase freezes the environment as described by {@link
   * #check(Ast)}.
   *
   * @param src source text and description
   * @return checked AST and diagnostics
   * @see #parseSource(Source)
   */
  public AstIssuesTuple compileSource(Source src) {
    AstIssuesTuple aiParse = parseSource(src);
    if (aiParse.hasIssues()) {
      return aiParse;
    }
    AstIssuesTuple aiCheck = check(aiParse.ast);
    Issues iss = aiParse.issues.append(aiCheck.issues);
    return new AstIssuesTuple(aiCheck.ast, iss);
  }

  /** Creates a cancellation-only parse-and-check operation for a source. */
  public CancelableOperation<AstIssuesTuple> compileSourceCancelable(Source src) {
    return compileSourceCancelable(src, ResourceLimits.unlimited());
  }

  /**
   * Creates a one-shot parse-and-check operation for a source with resource limits.
   *
   * @param src source text and description
   * @param limits immutable limits spanning parsing and checking
   * @return a lazy one-shot operation
   * @throws NullPointerException if an argument is {@code null}
   */
  public CancelableOperation<AstIssuesTuple> compileSourceCancelable(
      Source src, ResourceLimits limits) {
    Objects.requireNonNull(src, "src");
    var controller = operationController(Objects.requireNonNull(limits, "limits"));
    return new ControlledOperation<>(
        controller,
        OperationAbortedException.Phase.PARSE,
        () -> {
          var parsed = parseSource(src);
          if (parsed.hasIssues()) {
            return parsed;
          }
          controller.checkpointNow(OperationAbortedException.Phase.AST_BUILD);
          AstAdmission.check(parsed.ast, controller, OperationAbortedException.Phase.AST_BUILD);
          controller.checkpointNow(OperationAbortedException.Phase.CHECK);
          var checked = check(parsed.ast);
          if (!checked.hasIssues()) {
            AstAdmission.check(checked.ast, controller, OperationAbortedException.Phase.CHECK);
          }
          return new AstIssuesTuple(checked.ast, parsed.issues.append(checked.issues));
        });
  }

  /**
   * Creates an independently configurable environment derived from this environment.
   *
   * <p>Declarations, macros, features, and program-option lists are copied. Type registries used as
   * adapters or providers are copied through {@link TypeRegistry#copy()}. Custom adapters or
   * providers that are not type registries are shared and therefore should be immutable or
   * otherwise safe for the access they receive.
   *
   * <p>Extending does not change whether this environment is frozen. The returned environment is
   * separately configurable until its own first check. Options are applied to the derived
   * environment in list order.
   *
   * @param opts options applied to the derived environment
   * @return independently configurable derived environment
   * @throws RuntimeException if checker initialization previously failed or an option cannot be
   *     applied
   */
  public Env extend(List<EnvOption> opts) {
    Env ext;
    synchronized (lifecycleLock) {
      if (chkErr != null) {
        throw chkErr;
      }
      // Copy slices.
      List<Decl> decsCopy = new ArrayList<>(declarations);
      List<Macro> macsCopy = new ArrayList<>(macros);
      List<ProgramOption> progOptsCopy = new ArrayList<>(progOpts);

      // Copy the adapter / provider if they appear to be mutable.
      TypeAdapter adapter = this.adapter;
      TypeProvider provider = this.provider;
      // In most cases the provider and adapter will be a ref.TypeRegistry;
      // however, in the rare cases where they are not, they are assumed to
      // be immutable. Since it is possible to set the TypeProvider separately
      // from the TypeAdapter, the possible configurations which could use a
      // TypeRegistry as the base implementation are captured below.
      if (this.adapter instanceof TypeRegistry adapterReg
          && this.provider instanceof TypeRegistry providerReg) {
        TypeRegistry reg = providerReg.copy();
        provider = reg;
        // If the adapter and provider are the same object, set the adapter
        // to the same ref.TypeRegistry as the provider.
        if (adapterReg == providerReg) {
          adapter = reg;
        } else {
          // Otherwise, make a copy of the adapter.
          adapter = adapterReg.copy();
        }
      } else if (this.provider instanceof TypeRegistry) {
        provider = ((TypeRegistry) this.provider).copy();
      } else if (this.adapter instanceof TypeRegistry) {
        adapter = ((TypeRegistry) this.adapter).copy();
      }

      Set<EnvFeature> featuresCopy = EnumSet.copyOf(this.features);

      ext =
          new Env(
              this.container, decsCopy, macsCopy, adapter, provider, featuresCopy, progOptsCopy);
    }
    return ext.configure(opts);
  }

  /**
   * Creates an independently configurable environment derived from this environment.
   *
   * @param opts options applied to the derived environment in argument order
   * @return independently configurable derived environment
   * @see #extend(List)
   */
  public Env extend(EnvOption... opts) {
    return extend(asList(opts));
  }

  /**
   * Reports whether an environment feature is enabled.
   *
   * @param flag feature to inspect
   * @return {@code true} when enabled
   */
  public boolean hasFeature(EnvFeature flag) {
    synchronized (lifecycleLock) {
      return features.contains(flag);
    }
  }

  /**
   * Parses CEL source text without type checking it.
   *
   * <p>Parsing alone does not freeze this environment's configuration.
   *
   * @param txt CEL expression source
   * @return unchecked AST and diagnostics
   */
  public AstIssuesTuple parse(String txt) {
    Source src = newTextSource(txt);
    return parseSource(src);
  }

  /** Creates a cancellation-only parse operation. */
  public CancelableOperation<AstIssuesTuple> parseCancelable(String txt) {
    return parseCancelable(txt, ResourceLimits.unlimited());
  }

  /**
   * Creates a one-shot parse operation with resource limits.
   *
   * @param txt CEL expression source
   * @param limits immutable limits for parsing and AST construction
   * @return a lazy one-shot operation
   * @throws NullPointerException if an argument is {@code null}
   */
  public CancelableOperation<AstIssuesTuple> parseCancelable(String txt, ResourceLimits limits) {
    Objects.requireNonNull(txt, "txt");
    return parseSourceCancelable(newTextSource(txt), limits);
  }

  /**
   * Parses a CEL source without type checking it.
   *
   * <p>Parsing alone does not freeze this environment's configuration. Do not mutate environment
   * configuration concurrently with parsing.
   *
   * @param src source text and description
   * @return unchecked AST and diagnostics
   */
  public AstIssuesTuple parseSource(Source src) {
    ParseResult res = parseWithMacros(src, macros);
    if (res.hasErrors()) {
      return new AstIssuesTuple(null, newIssues(res.getErrors()));
    }
    // Manually create the Ast to ensure that the text source information is propagated on
    // subsequent calls to Check.
    return new AstIssuesTuple(
        new Ast(res.getExpr(), res.getSourceInfo(), src), Issues.noIssues(src));
  }

  /** Creates a cancellation-only parse operation for a source. */
  public CancelableOperation<AstIssuesTuple> parseSourceCancelable(Source src) {
    return parseSourceCancelable(src, ResourceLimits.unlimited());
  }

  /**
   * Creates a one-shot parse operation for a source with resource limits.
   *
   * @param src source text and description
   * @param limits immutable limits for parsing and AST construction
   * @return a lazy one-shot operation
   * @throws NullPointerException if an argument is {@code null}
   */
  public CancelableOperation<AstIssuesTuple> parseSourceCancelable(
      Source src, ResourceLimits limits) {
    Objects.requireNonNull(src, "src");
    var controller = operationController(Objects.requireNonNull(limits, "limits"));
    return new ControlledOperation<>(
        controller,
        OperationAbortedException.Phase.PARSE,
        () -> {
          var parsed = parseSource(src);
          if (!parsed.hasIssues()) {
            controller.checkpointNow(OperationAbortedException.Phase.AST_BUILD);
            AstAdmission.check(parsed.ast, controller, OperationAbortedException.Phase.AST_BUILD);
          }
          return parsed;
        });
  }

  /**
   * Creates an executable program for an AST in this environment.
   *
   * <p>Program options configured on the environment are applied first, followed by {@code opts} in
   * argument order. Checked ASTs are preferred; unchecked ASTs are supported with less planning
   * information. Program creation does not itself freeze environment configuration.
   *
   * @param ast parsed or checked AST
   * @param opts additional program options, normally obtained from {@link ProgramOption} factories
   * @return reusable executable program
   * @throws RuntimeException if planning or option application fails
   */
  public Program program(Ast ast, ProgramOption... opts) {
    List<ProgramOption> optSet = progOpts;
    if (opts.length > 0) {
      List<ProgramOption> mergedOpts = new ArrayList<>(progOpts);
      Collections.addAll(mergedOpts, opts);
      optSet = mergedOpts;
    }
    return newProgram(this, ast, optSet.toArray(new ProgramOption[0]));
  }

  /** Creates a cancellation-only program-planning operation. */
  public CancelableOperation<Program> programCancelable(Ast ast, ProgramOption... opts) {
    return programCancelable(ast, ResourceLimits.unlimited(), opts);
  }

  /**
   * Creates a one-shot program-planning operation with resource limits.
   *
   * @param ast parsed or checked AST
   * @param limits immutable limits for admission, optimization, and planning
   * @param opts additional program options, snapshotted when this method returns
   * @return a lazy one-shot operation producing a reusable program
   * @throws NullPointerException if an argument or option array is {@code null}
   */
  public CancelableOperation<Program> programCancelable(
      Ast ast, ResourceLimits limits, ProgramOption... opts) {
    Objects.requireNonNull(ast, "ast");
    Objects.requireNonNull(opts, "opts");
    var optionSnapshot = opts.clone();
    var controller = operationController(Objects.requireNonNull(limits, "limits"));
    return new ControlledOperation<>(
        controller,
        OperationAbortedException.Phase.SOURCE_ADMISSION,
        () -> {
          AstAdmission.check(ast, controller, OperationAbortedException.Phase.SOURCE_ADMISSION);
          controller.checkpointNow(OperationAbortedException.Phase.PLAN);
          return program(ast, optionSnapshot);
        });
  }

  /**
   * Enables an environment feature.
   *
   * <p>Features must be configured before the first call to {@link #check(Ast)}. Use {@link
   * #extend(EnvOption...)} to configure a derived environment after this environment has been
   * frozen.
   *
   * @param flag feature to enable
   * @throws NullPointerException if {@code flag} is {@code null}
   * @throws IllegalStateException if this environment has already been checked
   */
  public void setFeature(EnvFeature flag) {
    applyConfiguration(e -> e.features.add(flag));
  }

  private static OperationController operationController(ResourceLimits limits) {
    var active = OperationScope.current();
    return active.isControlled() && active.limits() == limits
        ? active
        : new OperationController(limits);
  }

  void setFeatures(EnvFeature... flags) {
    applyConfiguration(e -> Collections.addAll(e.features, flags));
  }

  Env applyConfiguration(Consumer<Env> configuration) {
    synchronized (lifecycleLock) {
      if (frozen) {
        throw new IllegalStateException(FROZEN_CONFIGURATION_MESSAGE);
      }
      configuration.accept(this);
      return this;
    }
  }

  Container getContainer() {
    return container;
  }

  /**
   * Returns the type adapter configured for this environment.
   *
   * <p>The returned object is the environment's live adapter, not a copy.
   *
   * @return configured adapter
   */
  public TypeAdapter getTypeAdapter() {
    return adapter;
  }

  /**
   * Returns the type provider configured for this environment.
   *
   * <p>The returned object is the environment's live provider, not a copy.
   *
   * @return configured provider
   */
  public TypeProvider getTypeProvider() {
    return provider;
  }

  /**
   * Creates a partial activation that marks every identifier declared by this environment as
   * unknown.
   *
   * <p>The patterns affect evaluation only when the program enables {@link
   * EvalOption#OptPartialEval}.
   *
   * @return partial activation with one unknown pattern for each identifier declaration
   */
  public PartialActivation getUnknownVars() {
    List<AttributePattern> unknownPatterns = new ArrayList<>();
    for (Decl d : declarations) {
      if (d.getDeclKindCase() == DeclKindCase.IDENT) {
        unknownPatterns.add(newAttributePattern(d.getName()));
      }
    }
    return partialVars(emptyActivation(), unknownPatterns.toArray(new AttributePattern[0]));
  }

  /**
   * Produces an AST for the portion of an evaluated expression that remains unresolved.
   *
   * <p>The details should come from evaluating {@code a} with state tracking and, for unknown
   * attributes, partial evaluation enabled. Residual expressions can support:
   *
   * <ul>
   *   <li>Optimizing constant expression evaluations away.
   *   <li>Indexing and pruning expressions based on known input arguments.
   *   <li>Surfacing additional requirements that are needed in order to complete an evaluation.
   * </ul>
   *
   * <p>For example, if an expression targets a 'resource' and 'request' attribute and the possible
   * values for the resource are known, a {@link PartialActivation} can mark {@code request} as an
   * unknown {@link AttributePattern}; the residual expression is then reduced to the parts that
   * reference {@code request}.
   *
   * <p>Expression IDs in the returned AST do not correspond to IDs in the original AST. If the
   * original AST is checked, the residual is reparsed and checked in this environment; that check
   * freezes the environment.
   *
   * @param a AST that was evaluated
   * @param details evaluation details containing state for that evaluation
   * @return parsed residual AST, checked when {@code a} is checked
   * @throws RuntimeException if the residual expression cannot be parsed or checked
   * @see CEL#partialVars(Object, AttributePattern...)
   */
  public Ast residualAst(Ast a, EvalDetails details) {
    Expr pruned = pruneAst(a.getExpr(), details.getState());
    String expr = astToString(parsedExprToAst(ParsedExpr.newBuilder().setExpr(pruned).build()));
    AstIssuesTuple parsedIss = parse(expr);
    if (parsedIss.hasIssues()) {
      throw parsedIss.getIssues().err();
    }
    if (!a.isChecked()) {
      return parsedIss.ast;
    }
    AstIssuesTuple checkedIss = check(parsedIss.ast);
    if (checkedIss.hasIssues()) {
      throw checkedIss.getIssues().err();
    }
    return checkedIss.ast;
  }

  /** Applies environment options in list order. */
  Env configure(List<EnvOption> opts) {
    Env e = this;
    for (EnvOption opt : opts) {
      e = opt.apply(e);
    }
    return e;
  }

  /**
   * Returns a diagnostic representation of this environment's current configuration.
   *
   * <p>The format is not a stable serialization contract.
   *
   * @return diagnostic text
   */
  @Override
  public String toString() {
    return "Env{"
        + "container="
        + container
        + "\n    , declarations="
        + declarations
        + "\n    , macros="
        + macros
        + "\n    , adapter="
        + adapter
        + "\n    , provider="
        + provider
        + "\n    , features="
        + features
        + "\n    , progOpts="
        + progOpts
        + "\n    , chk="
        + chk
        + "\n    , chkErr="
        + chkErr
        + "\n    , frozen="
        + frozen
        + '}';
  }
}
