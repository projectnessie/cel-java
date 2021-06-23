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

import static org.projectnessie.cel.common.Source.newInfoSource;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Activation.newPartialActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.AttributePattern.newAttributePattern;
import static org.projectnessie.cel.interpreter.AttributePattern.newPartialAttributeFactory;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.EvalState.newEvalState;
import static org.projectnessie.cel.interpreter.Interpreter.exhaustiveEval;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.Interpreter.optimize;
import static org.projectnessie.cel.interpreter.Interpreter.trackState;
import static org.projectnessie.cel.parser.Unparser.unparse;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.Reference;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.api.expr.v1alpha1.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.interpreter.Activation;
import org.projectnessie.cel.interpreter.Activation.PartialActivation;
import org.projectnessie.cel.interpreter.AttributePattern;
import org.projectnessie.cel.interpreter.Coster;
import org.projectnessie.cel.interpreter.Coster.Cost;
import org.projectnessie.cel.interpreter.Dispatcher;
import org.projectnessie.cel.interpreter.InterpretableDecorator;
import org.projectnessie.cel.interpreter.Interpreter;

public final class CEL {

  /**
   * newProgram creates a program instance with an environment, an ast, and an optional list of
   * ProgramOption values.
   *
   * <p>If the program cannot be configured the prog will be nil, with a non-nil error response.
   */
  public static Program newProgram(Env e, Ast ast, ProgramOption... opts) {
    // Build the dispatcher, interpreter, and default program value.
    Dispatcher disp = newDispatcher();

    // Ensure the default attribute factory is set after the adapter and provider are
    // configured.
    Prog p = new Prog(e, disp);

    // Configure the program via the ProgramOption values.
    for (ProgramOption opt : opts) {
      if (opt == null) {
        throw new NullPointerException("program options should be non-nil");
      }
      p = opt.apply(p);
      if (p == null) {
        throw new NullPointerException(
            String.format("program option of type '%s' returned null", opt.getClass().getName()));
      }
    }

    // Set the attribute factory after the options have been set.
    if (p.evalOpts.contains(EvalOption.OptPartialEval)) {
      p.attrFactory =
          newPartialAttributeFactory(e.getContainer(), e.getTypeAdapter(), e.getTypeProvider());
    } else {
      p.attrFactory =
          newAttributeFactory(e.getContainer(), e.getTypeAdapter(), e.getTypeProvider());
    }

    Interpreter interp =
        newInterpreter(
            disp, e.getContainer(), e.getTypeProvider(), e.getTypeAdapter(), p.attrFactory);
    p.interpreter = interp;

    // Translate the EvalOption flags into InterpretableDecorator instances.
    List<InterpretableDecorator> decorators = new ArrayList<>(p.decorators);

    // Enable constant folding first.
    if (p.evalOpts.contains(EvalOption.OptOptimize)) {
      decorators.add(optimize());
    }

    Prog pp = p;

    // Enable exhaustive eval over state tracking since it offers a superset of features.
    if (p.evalOpts.contains(EvalOption.OptExhaustiveEval)) {
      // State tracking requires that each Eval() call operate on an isolated EvalState
      // object; hence, the presence of the factory.
      ProgFactory factory =
          state -> {
            List<InterpretableDecorator> decs = new ArrayList<>(decorators);
            decs.add(exhaustiveEval(state));
            Prog clone = new Prog(e, pp.evalOpts, pp.defaultVars, disp, interp, state);
            return initInterpretable(clone, ast, decs);
          };
      return initProgGen(factory);
    } else if (p.evalOpts.contains(EvalOption.OptTrackState)) {
      // Enable state tracking last since it too requires the factory approach but is less
      // featured than the ExhaustiveEval decorator.
      ProgFactory factory =
          state -> {
            List<InterpretableDecorator> decs = new ArrayList<>(decorators);
            decs.add(trackState(state));
            Prog clone = new Prog(e, pp.evalOpts, pp.defaultVars, disp, interp, state);
            return initInterpretable(clone, ast, decs);
          };
      return initProgGen(factory);
    }
    return initInterpretable(p, ast, decorators);
  }

  /**
   * initProgGen tests the factory object by calling it once and returns a factory-based Program if
   * the test is successful.
   */
  private static Program initProgGen(ProgFactory factory) {
    // Test the factory to make sure that configuration errors are spotted at config
    factory.apply(newEvalState());
    return new ProgGen(factory);
  }

  /**
   * initIterpretable creates a checked or unchecked interpretable depending on whether the Ast has
   * been run through the type-checker.
   */
  private static Program initInterpretable(
      Prog p, Ast ast, List<InterpretableDecorator> decorators) {

    InterpretableDecorator[] decs = decorators.toArray(new InterpretableDecorator[0]);

    // Unchecked programs do not contain type and reference information and may be
    // slower to execute than their checked counterparts.
    if (!ast.isChecked()) {
      p.interpretable = p.interpreter.newUncheckedInterpretable(ast.getExpr(), decs);
      return p;
    }
    // When the AST has been checked it contains metadata that can be used to speed up program
    // execution.
    CheckedExpr checked = astToCheckedExpr(ast);
    p.interpretable = p.interpreter.newInterpretable(checked, decs);

    return p;
  }

  /** CheckedExprToAst converts a checked expression proto message to an Ast. */
  public static Ast checkedExprToAst(CheckedExpr checkedExpr) {
    Map<Long, Reference> refMap = checkedExpr.getReferenceMapMap();
    Map<Long, Type> typeMap = checkedExpr.getTypeMapMap();
    return new Ast(
        checkedExpr.getExpr(),
        checkedExpr.getSourceInfo(),
        newInfoSource(checkedExpr.getSourceInfo()),
        refMap,
        typeMap);
  }

  /**
   * AstToCheckedExpr converts an Ast to an protobuf CheckedExpr value.
   *
   * <p>If the Ast.IsChecked() returns false, this conversion method will return an error.
   */
  public static CheckedExpr astToCheckedExpr(Ast a) {
    if (!a.isChecked()) {
      throw new IllegalArgumentException("cannot convert unchecked ast");
    }
    return CheckedExpr.newBuilder()
        .setExpr(a.getExpr())
        .setSourceInfo(a.getSourceInfo())
        .putAllReferenceMap(a.refMap)
        .putAllTypeMap(a.typeMap)
        .build();
  }

  /** ParsedExprToAst converts a parsed expression proto message to an Ast. */
  public static Ast parsedExprToAst(ParsedExpr parsedExpr) {
    SourceInfo si = parsedExpr.getSourceInfo();
    return new Ast(parsedExpr.getExpr(), si, newInfoSource(si));
  }

  /** AstToParsedExpr converts an Ast to an protobuf ParsedExpr value. */
  public static ParsedExpr astToParsedExpr(Ast a) {
    return ParsedExpr.newBuilder().setExpr(a.getExpr()).setSourceInfo(a.getSourceInfo()).build();
  }

  /**
   * AstToString converts an Ast back to a string if possible.
   *
   * <p>Note, the conversion may not be an exact replica of the original expression, but will
   * produce a string that is semantically equivalent and whose textual representation is stable.
   */
  public static String astToString(Ast a) {
    Expr expr = a.getExpr();
    SourceInfo info = a.getSourceInfo();
    return unparse(expr, info);
  }

  /** NoVars returns an empty Activation. */
  public static Activation noVars() {
    return emptyActivation();
  }

  /**
   * PartialVars returns a PartialActivation which contains variables and a set of AttributePattern
   * values that indicate variables or parts of variables whose value are not yet known.
   *
   * <p>The `vars` value may either be an interpreter.Activation or any valid input to the
   * interpreter.NewActivation call.
   */
  public static PartialActivation partialVars(Object vars, AttributePattern... unknowns) {
    return newPartialActivation(vars, unknowns);
  }

  /**
   * AttributePattern returns an AttributePattern that matches a top-level variable. The pattern is
   * mutable, and its methods support the specification of one or more qualifier patterns.
   *
   * <p>For example, the AttributePattern(`a`).QualString(`b`) represents a variable access `a` with
   * a string field or index qualification `b`. This pattern will match Attributes `a`, and `a.b`,
   * but not `a.c`.
   *
   * <p>When using a CEL expression within a container, e.g. a package or namespace, the variable
   * name in the pattern must match the qualified name produced during the variable namespace
   * resolution. For example, when variable `a` is declared within an expression whose container is
   * `ns.app`, the fully qualified variable name may be `ns.app.a`, `ns.a`, or `a` per the CEL
   * namespace resolution rules. Pick the fully qualified variable name that makes sense within the
   * container as the AttributePattern `varName` argument.
   *
   * <p>See the interpreter.AttributePattern and interpreter.AttributeQualifierPattern for more info
   * about how to create and manipulate AttributePattern values.
   */
  public static AttributePattern attributePattern(String varName) {
    return newAttributePattern(varName);
  }

  /** EstimateCost returns the heuristic cost interval for the program. */
  public static Cost estimateCost(Object p) {
    if (p instanceof Coster) {
      return ((Coster) p).cost();
    }
    return Cost.Unknown;
  }
}
