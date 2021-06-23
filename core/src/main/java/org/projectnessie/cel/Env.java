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
import org.projectnessie.cel.interpreter.Activation.PartialActivation;
import org.projectnessie.cel.interpreter.AttributePattern;
import org.projectnessie.cel.parser.Macro;
import org.projectnessie.cel.parser.Parser.ParseResult;

/**
 * Env encapsulates the context necessary to perform parsing, type checking, or generation of
 * evaluable programs for different expressions.
 */
public final class Env {

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
  private final Object once = new Object();

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
   * NewEnv creates a program environment configured with the standard library of CEL functions and
   * macros. The Env value returned can parse and check any CEL program which builds upon the core
   * features documented in the CEL specification.
   *
   * <p>See the EnvOption helper functions for the options that can be used to configure the
   * environment.
   */
  public static Env newEnv(EnvOption... opts) {
    List<EnvOption> stdOpts = new ArrayList<>(opts.length + 1);
    stdOpts.add(StdLib());
    Collections.addAll(stdOpts, opts);
    return newCustomEnv(stdOpts.toArray(new EnvOption[0]));
  }

  /**
   * NewCustomEnv creates a custom program environment which is not automatically configured with
   * the standard library of functions and macros documented in the CEL spec.
   *
   * <p>The purpose for using a custom environment might be for subsetting the standard library
   * produced by the cel.StdLib() function. Subsetting CEL is a core aspect of its design that
   * allows users to limit the compute and memory impact of a CEL program by controlling the
   * functions and macros that may appear in a given expression.
   *
   * <p>See the EnvOption helper functions for the options that can be used to configure the
   * environment.
   */
  public static Env newCustomEnv(List<EnvOption> opts) {
    TypeRegistry registry = newRegistry();
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

  public static Env newCustomEnv(EnvOption... opts) {
    return newCustomEnv(asList(opts));
  }

  void addProgOpts(List<ProgramOption> progOpts) {
    this.progOpts.addAll(progOpts);
  }

  public static final class AstIssuesTuple {
    private final Ast ast;
    private final Issues issues;

    AstIssuesTuple(Ast ast, Issues issues) {
      this.ast = ast;
      this.issues = Objects.requireNonNull(issues);
    }

    public boolean hasIssues() {
      return issues.hasIssues();
    }

    public Ast getAst() {
      return ast;
    }

    public Issues getIssues() {
      return issues;
    }
  }

  /**
   * Check performs type-checking on the input Ast and yields a checked Ast and/or set of Issues.
   *
   * <p>Checking has failed if the returned Issues value and its Issues.Err() value are non-nil.
   * Issues should be inspected if they are non-nil, but may not represent a fatal error.
   *
   * <p>It is possible to have both non-nil Ast and Issues values returned from this call: however,
   * the mere presence of an Ast does not imply that it is valid for use.
   */
  public AstIssuesTuple check(Ast ast) {
    // Note, errors aren't currently possible on the Ast to ParsedExpr conversion.
    ParsedExpr pe = astToParsedExpr(ast);

    // Construct the internal checker env, erroring if there is an issue adding the declarations.
    synchronized (once) {
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

    // The once call will ensure that this value is set or nil for all invocations.
    if (chkErr != null) {
      Errors errs = new Errors(ast.getSource());
      errs.reportError(NoLocation, "%s", chkErr.toString());
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

  /**
   * Compile combines the Parse and Check phases CEL program compilation to produce an Ast and
   * associated issues.
   *
   * <p>If an error is encountered during parsing the Compile step will not continue with the Check
   * phase. If non-error issues are encountered during Parse, they may be combined with any issues
   * discovered during Check.
   *
   * <p>Note, for parse-only uses of CEL use Parse.
   */
  public AstIssuesTuple compile(String txt) {
    return compileSource(newTextSource(txt));
  }

  /**
   * CompileSource combines the Parse and Check phases CEL program compilation to produce an Ast and
   * associated issues.
   *
   * <p>If an error is encountered during parsing the CompileSource step will not continue with the
   * Check phase. If non-error issues are encountered during Parse, they may be combined with any
   * issues discovered during Check.
   *
   * <p>Note, for parse-only uses of CEL use Parse.
   */
  public AstIssuesTuple compileSource(Source src) {
    AstIssuesTuple aiParse = parseSource(src);
    AstIssuesTuple aiCheck = check(aiParse.ast);
    Issues iss = aiParse.issues.append(aiCheck.issues);
    return new AstIssuesTuple(aiCheck.ast, iss);
  }

  /**
   * Extend the current environment with additional options to produce a new Env.
   *
   * <p>Note, the extended Env value should not share memory with the original. It is possible,
   * however, that a CustomTypeAdapter or CustomTypeProvider options could provide values which are
   * mutable. To ensure separation of state between extended environments either make sure the
   * TypeAdapter and TypeProvider are immutable, or that their underlying implementations are based
   * on the ref.TypeRegistry which provides a Copy method which will be invoked by this method.
   */
  public Env extend(List<EnvOption> opts) {
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
    if (this.adapter instanceof TypeRegistry && this.provider instanceof TypeRegistry) {
      TypeRegistry adapterReg = (TypeRegistry) this.adapter;
      TypeRegistry providerReg = (TypeRegistry) this.provider;
      TypeRegistry reg = providerReg.copy();
      provider = reg;
      // If the adapter and provider are the same object, set the adapter
      // to the same ref.TypeRegistry as the provider.
      if (adapterReg.equals(providerReg)) {
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

    Env ext =
        new Env(this.container, decsCopy, macsCopy, adapter, provider, featuresCopy, progOptsCopy);
    return ext.configure(opts);
  }

  public Env extend(EnvOption... opts) {
    return extend(asList(opts));
  }

  /**
   * HasFeature checks whether the environment enables the given feature flag, as enumerated in
   * options.go.
   */
  public boolean hasFeature(EnvFeature flag) {
    return features.contains(flag);
  }

  /**
   * Parse parses the input expression value `txt` to a Ast and/or a set of Issues.
   *
   * <p>This form of Parse creates a common.Source value for the input `txt` and forwards to the
   * ParseSource method.
   */
  public AstIssuesTuple parse(String txt) {
    Source src = newTextSource(txt);
    return parseSource(src);
  }

  /**
   * ParseSource parses the input source to an Ast and/or set of Issues.
   *
   * <p>Parsing has failed if the returned Issues value and its Issues.Err() value is non-nil.
   * Issues should be inspected if they are non-nil, but may not represent a fatal error.
   *
   * <p>It is possible to have both non-nil Ast and Issues values returned from this call; however,
   * the mere presence of an Ast does not imply that it is valid for use.
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

  /** Program generates an evaluable instance of the Ast within the environment (Env). */
  public Program program(Ast ast, ProgramOption... opts) {
    List<ProgramOption> optSet = progOpts;
    if (opts.length > 0) {
      List<ProgramOption> mergedOpts = new ArrayList<>(progOpts);
      Collections.addAll(mergedOpts, opts);
      optSet = mergedOpts;
    }
    return newProgram(this, ast, optSet.toArray(new ProgramOption[0]));
  }

  /** SetFeature sets the given feature flag, as enumerated in options.go. */
  public void setFeature(EnvFeature flag) {
    features.add(flag);
  }

  Container getContainer() {
    return container;
  }

  /** TypeAdapter returns the `ref.TypeAdapter` configured for the environment. */
  public TypeAdapter getTypeAdapter() {
    return adapter;
  }

  /** TypeProvider returns the `ref.TypeProvider` configured for the environment. */
  public TypeProvider getTypeProvider() {
    return provider;
  }

  /**
   * UnknownVars returns an interpreter.PartialActivation which marks all variables declared in the
   * Env as unknown AttributePattern values.
   *
   * <p>Note, the UnknownVars will behave the same as an interpreter.EmptyActivation unless the
   * PartialAttributes option is provided as a ProgramOption.
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
   * ResidualAst takes an Ast and its EvalDetails to produce a new Ast which only contains the
   * attribute references which are unknown.
   *
   * <p>Residual expressions are beneficial in a few scenarios:
   *
   * <ul>
   *   <li>Optimizing constant expression evaluations away.
   *   <li>Indexing and pruning expressions based on known input arguments.
   *   <li>Surfacing additional requirements that are needed in order to complete an evaluation.
   *   <li>Sharing the evaluation of an expression across multiple machines/nodes.
   * </ul>
   *
   * <p>For example, if an expression targets a 'resource' and 'request' attribute and the possible
   * values for the resource are known, a PartialActivation could mark the 'request' as an unknown
   * interpreter.AttributePattern and the resulting ResidualAst would be reduced to only the parts
   * of the expression that reference the 'request'.
   *
   * <p>Note, the expression ids within the residual AST generated through this method have no
   * correlation to the expression ids of the original AST.
   *
   * <p>See the PartialVars helper for how to construct a PartialActivation.
   *
   * <p>TODO: Consider adding an option to generate a Program.Residual to avoid round-tripping to an
   * Ast format and then Program again.
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

  /** configure applies a series of EnvOptions to the current environment. */
  Env configure(List<EnvOption> opts) {
    // Customized the environment using the provided EnvOption values. If an error is
    // generated at any step this, will be returned as a nil Env with a non-nil error.
    Env e = this;
    for (EnvOption opt : opts) {
      e = opt.apply(e);
    }
    return e;
  }

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
        + "\n    , once="
        + once
        + '}';
  }
}
