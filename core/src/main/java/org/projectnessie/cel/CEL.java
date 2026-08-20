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
import java.util.Set;
import org.projectnessie.cel.interpreter.Activation;
import org.projectnessie.cel.interpreter.Activation.PartialActivation;
import org.projectnessie.cel.interpreter.AttributePattern;
import org.projectnessie.cel.interpreter.Coster;
import org.projectnessie.cel.interpreter.Coster.Cost;
import org.projectnessie.cel.interpreter.Dispatcher;
import org.projectnessie.cel.interpreter.InterpretableDecorator;
import org.projectnessie.cel.interpreter.Interpreter;

/**
 * Low-level factories and conversion utilities for CEL environments, programs, activations, and
 * ASTs.
 *
 * <p>Most applications begin with {@link Env#newEnv(EnvOption...)} and follow the
 * parse/check/program/evaluate lifecycle. This class supports integrations that need explicit
 * Protobuf AST conversion, partial activations, attribute patterns, or cost estimates.
 */
public final class CEL {

  /**
   * Creates an executable program for an AST in an environment.
   *
   * <p>Options are applied in argument order. A checked AST provides type and reference metadata
   * used during planning; an unchecked AST is accepted but may require more runtime dispatch. The
   * resulting program can be reused for multiple evaluations.
   *
   * <p>Native evaluation, when permitted by the selected options, is planner-selected evaluation
   * over supported Java-native representations. Unsupported shapes fall back to the established
   * evaluator; this does not generate Java or native machine code.
   *
   * @param e environment supplying types and name resolution
   * @param ast parsed or checked expression
   * @param opts program configuration tokens, normally created by {@link ProgramOption} factories
   * @return reusable executable program
   * @throws NullPointerException if {@code e}, {@code ast}, or {@code opts} is {@code null}, or if
   *     an option is {@code null} or returns {@code null}
   * @throws RuntimeException if program planning or option application fails
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
            disp,
            e.getContainer(),
            e.getTypeProvider(),
            e.getTypeAdapter(),
            p.attrFactory,
            nativePlanningPermitted(p.evalOpts, p.decorators),
            p.regexEngine);
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
            Prog clone =
                new Prog(e, pp.evalOpts, pp.regexEngine, pp.defaultVars, disp, interp, state);
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
            Prog clone =
                new Prog(e, pp.evalOpts, pp.regexEngine, pp.defaultVars, disp, interp, state);
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
    p.interpretable = p.interpreter.newInterpretable(ast.getExpr(), ast.refMap, ast.typeMap, decs);

    return p;
  }

  static boolean nativePlanningPermitted(
      Set<EvalOption> evalOptions, List<InterpretableDecorator> decorators) {
    return decorators.isEmpty()
        && (evalOptions.isEmpty()
            || evalOptions.size() == 1 && evalOptions.contains(EvalOption.OptOptimize));
  }

  /**
   * Converts a checked-expression Protobuf message to an AST.
   *
   * <p>The returned source is reconstructed from the message's {@link SourceInfo}; it need not
   * contain the original source text.
   *
   * @param checkedExpr checked expression message
   * @return an AST that is considered checked when the message contains non-empty type metadata
   * @throws NullPointerException if {@code checkedExpr} is {@code null}
   */
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
   * Converts a checked AST to a checked-expression Protobuf message.
   *
   * <p>The conversion preserves CEL-Java's current expression, reference, type, and source-info
   * shape. It does not establish cross-runtime support for implementation-specific macro
   * expansions.
   *
   * @param a checked AST
   * @return checked-expression message
   * @throws NullPointerException if {@code a} is {@code null}
   * @throws IllegalArgumentException if {@code a} is unchecked
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

  /**
   * Converts a parsed-expression Protobuf message to an unchecked AST.
   *
   * <p>The returned source is reconstructed from the message's {@link SourceInfo}; it need not
   * contain the original source text.
   *
   * @param parsedExpr parsed expression message
   * @return unchecked AST
   * @throws NullPointerException if {@code parsedExpr} is {@code null}
   */
  public static Ast parsedExprToAst(ParsedExpr parsedExpr) {
    SourceInfo si = parsedExpr.getSourceInfo();
    return new Ast(parsedExpr.getExpr(), si, newInfoSource(si));
  }

  /**
   * Converts an AST to a parsed-expression Protobuf message.
   *
   * <p>Checked reference and type maps are not part of {@link ParsedExpr} and are therefore not
   * included.
   *
   * @param a parsed or checked AST
   * @return parsed-expression message
   * @throws NullPointerException if {@code a} is {@code null}
   */
  public static ParsedExpr astToParsedExpr(Ast a) {
    return ParsedExpr.newBuilder().setExpr(a.getExpr()).setSourceInfo(a.getSourceInfo()).build();
  }

  /**
   * Unparses an AST to stable CEL source text.
   *
   * <p>Note, the conversion may not be an exact replica of the original expression, but will
   * produce a string that is semantically equivalent and whose textual representation is stable.
   *
   * @param a AST to unparse
   * @return stable CEL expression text
   * @throws NullPointerException if {@code a} is {@code null}
   */
  public static String astToString(Ast a) {
    Expr expr = a.getExpr();
    SourceInfo info = a.getSourceInfo();
    return unparse(expr, info);
  }

  /**
   * Returns an activation with no variable bindings.
   *
   * @return empty activation
   */
  public static Activation noVars() {
    return emptyActivation();
  }

  /**
   * Creates a partial activation containing bindings and attributes whose values are unknown.
   *
   * <p>{@code vars} may be an {@link Activation}, a Java map, a {@link
   * java.util.function.Function}, or an {@link
   * org.projectnessie.cel.interpreter.ActivationFunction}. The returned activation copies the
   * unknown-pattern array but retains its mutable pattern elements.
   *
   * @param vars bindings accepted by {@link Activation#newActivation(Object)}
   * @param unknowns attribute patterns that evaluate as unknown
   * @return partial activation
   * @throws NullPointerException if {@code vars} or {@code unknowns} is {@code null}
   * @throws IllegalArgumentException if {@code vars} is not a supported activation input
   */
  public static PartialActivation partialVars(Object vars, AttributePattern... unknowns) {
    return newPartialActivation(vars, unknowns);
  }

  /**
   * Creates a mutable attribute pattern matching a top-level variable.
   *
   * <p>For example, {@code attributePattern("a").qualString("b")} represents the access {@code
   * a.b}. It matches attributes {@code a} and {@code a.b}, but not {@code a.c}.
   *
   * <p>When using a CEL expression within a container, e.g. a package or namespace, the variable
   * name in the pattern must match the qualified name produced during the variable namespace
   * resolution. For example, when variable {@code a} is declared within an expression whose
   * container is {@code ns.app}, the fully qualified variable name may be {@code ns.app.a}, {@code
   * ns.a}, or {@code a} per the CEL namespace resolution rules. Use the qualified name selected by
   * resolution as {@code varName}.
   *
   * @param varName top-level or resolved qualified variable name
   * @return mutable attribute pattern
   */
  public static AttributePattern attributePattern(String varName) {
    return newAttributePattern(varName);
  }

  /**
   * Returns the heuristic cost interval reported by an object that implements {@link Coster}.
   *
   * <p>A cost estimate is descriptive only; it does not impose an evaluation budget or runtime
   * limit.
   *
   * @param p program or other cost-reporting object
   * @return reported cost interval, or {@link Cost#Unknown} when {@code p} is not a {@code Coster}
   */
  public static Cost estimateCost(Object p) {
    if (p instanceof Coster) {
      return ((Coster) p).cost();
    }
    return Cost.Unknown;
  }
}
