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
package org.projectnessie.cel.extension;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.projectnessie.cel.common.types.OptionalT.OptionalType;

import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.ExprKindCase;
import java.util.List;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.ErrorWithLocation;
import org.projectnessie.cel.common.Location;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.OptionalT;
import org.projectnessie.cel.interpreter.functions.Overload;
import org.projectnessie.cel.parser.ExprHelper;
import org.projectnessie.cel.parser.Macro;

/**
 * OptionalLib provides CEL optional helper functions.
 *
 * <p>This library provides runtime optional values, ordinary optional constructors/receiver
 * methods, optional access operators, and lazy optMap/optFlatMap macro expansion.
 */
public final class OptionalLib implements Library {
  private static final String OPTIONAL_TYPE = "optional_type";
  private static final String OPTIONAL_NONE = "optional.none";
  private static final String OPTIONAL_OF = "optional.of";
  private static final String OPTIONAL_OF_NON_ZERO_VALUE = "optional.ofNonZeroValue";
  private static final String OPTIONAL_HAS_VALUE = "hasValue";
  private static final String OPTIONAL_VALUE = "value";
  private static final String OPTIONAL_OR = "or";
  private static final String OPTIONAL_OR_VALUE = "orValue";
  private static final String OPTIONAL_OPT_MAP = "optMap";
  private static final String OPTIONAL_OPT_FLAT_MAP = "optFlatMap";
  private static final String OPTIONAL_NONE_OVERLOAD = "optional_none";
  private static final String OPTIONAL_OF_OVERLOAD = "optional_of";
  private static final String OPTIONAL_OF_NON_ZERO_VALUE_OVERLOAD = "optional_of_non_zero_value";
  private static final String OPTIONAL_SELECT_OVERLOAD = "optional_select";
  private static final String OPTIONAL_INDEX_OVERLOAD = "optional_index";
  private static final String OPTIONAL_INDEX_OPTIONAL_OVERLOAD = "optional_index_optional";
  private static final String OPTIONAL_HAS_VALUE_OVERLOAD = "optional_has_value";
  private static final String OPTIONAL_VALUE_OVERLOAD = "optional_value";
  private static final String OPTIONAL_OR_OVERLOAD = "optional_or";
  private static final String OPTIONAL_OR_VALUE_OVERLOAD = "optional_or_value";
  private static final String TYPE_PARAM_A = "A";
  private static final String OPTIONAL_MACRO_TARGET = "@optional_target";
  private static final String OPTIONAL_MACRO_RESULT = "@optional_result";

  private OptionalLib() {}

  public static EnvOption optionals() {
    return Library.Lib(new OptionalLib());
  }

  @Override
  public List<EnvOption> getCompileOptions() {
    var typeParamA = Decls.newTypeParamType(TYPE_PARAM_A);
    var optionalA = Decls.newAbstractType(OPTIONAL_TYPE, singletonList(typeParamA));
    var typeParams = singletonList(TYPE_PARAM_A);

    return List.of(
        EnvOption.types(singletonList(OptionalType)),
        EnvOption.macros(
            Macro.newReceiverMacro(OPTIONAL_OPT_MAP, 2, OptionalLib::makeOptMap),
            Macro.newReceiverMacro(OPTIONAL_OPT_FLAT_MAP, 2, OptionalLib::makeOptFlatMap)),
        EnvOption.declarations(
            Decls.newVar(OPTIONAL_TYPE, Decls.newTypeType(optionalA)),
            Decls.newFunction(
                OPTIONAL_NONE,
                Decls.newParameterizedOverload(
                    OPTIONAL_NONE_OVERLOAD, emptyList(), optionalA, typeParams)),
            Decls.newFunction(
                OPTIONAL_OF,
                Decls.newParameterizedOverload(
                    OPTIONAL_OF_OVERLOAD, singletonList(typeParamA), optionalA, typeParams)),
            Decls.newFunction(
                OPTIONAL_OF_NON_ZERO_VALUE,
                Decls.newParameterizedOverload(
                    OPTIONAL_OF_NON_ZERO_VALUE_OVERLOAD,
                    singletonList(typeParamA),
                    optionalA,
                    typeParams)),
            Decls.newFunction(
                Operator.OptionalSelect.id,
                Decls.newOverload(
                    OPTIONAL_SELECT_OVERLOAD,
                    List.of(Decls.Dyn, Decls.String),
                    Decls.newAbstractType(OPTIONAL_TYPE, singletonList(Decls.Dyn)))),
            Decls.newFunction(
                Operator.OptionalIndex.id,
                Decls.newOverload(
                    OPTIONAL_INDEX_OVERLOAD,
                    List.of(Decls.Dyn, Decls.Dyn),
                    Decls.newAbstractType(OPTIONAL_TYPE, singletonList(Decls.Dyn)))),
            Decls.newFunction(
                Operator.Index.id,
                Decls.newParameterizedOverload(
                    OPTIONAL_INDEX_OPTIONAL_OVERLOAD,
                    List.of(optionalA, Decls.Dyn),
                    Decls.newAbstractType(OPTIONAL_TYPE, singletonList(Decls.Dyn)),
                    typeParams)),
            Decls.newFunction(
                OPTIONAL_HAS_VALUE,
                Decls.newParameterizedInstanceOverload(
                    OPTIONAL_HAS_VALUE_OVERLOAD, singletonList(optionalA), Decls.Bool, typeParams)),
            Decls.newFunction(
                OPTIONAL_VALUE,
                Decls.newParameterizedInstanceOverload(
                    OPTIONAL_VALUE_OVERLOAD, singletonList(optionalA), typeParamA, typeParams)),
            Decls.newFunction(
                OPTIONAL_OR,
                Decls.newParameterizedInstanceOverload(
                    OPTIONAL_OR_OVERLOAD, List.of(optionalA, optionalA), optionalA, typeParams)),
            Decls.newFunction(
                OPTIONAL_OR_VALUE,
                Decls.newParameterizedInstanceOverload(
                    OPTIONAL_OR_VALUE_OVERLOAD,
                    List.of(optionalA, typeParamA),
                    typeParamA,
                    typeParams))));
  }

  @Override
  public List<ProgramOption> getProgramOptions() {
    return List.of(
        ProgramOption.functions(
            Overload.function(OPTIONAL_NONE, args -> OptionalT.none()),
            Overload.function(OPTIONAL_NONE_OVERLOAD, args -> OptionalT.none()),
            Overload.unary(OPTIONAL_OF, OptionalT::of),
            Overload.unary(OPTIONAL_OF_OVERLOAD, OptionalT::of),
            Overload.unary(OPTIONAL_OF_NON_ZERO_VALUE, OptionalT::ofNonZeroValue),
            Overload.unary(OPTIONAL_OF_NON_ZERO_VALUE_OVERLOAD, OptionalT::ofNonZeroValue),
            Overload.binary(Operator.OptionalSelect.id, OptionalT::optionalSelect),
            Overload.binary(OPTIONAL_SELECT_OVERLOAD, OptionalT::optionalSelect),
            Overload.binary(Operator.OptionalIndex.id, OptionalT::optionalIndex),
            Overload.binary(OPTIONAL_INDEX_OVERLOAD, OptionalT::optionalIndex)));
  }

  private static Expr makeOptMap(ExprHelper eh, Expr target, List<Expr> args) {
    return makeOptionalMap(eh, target, args, true);
  }

  private static Expr makeOptFlatMap(ExprHelper eh, Expr target, List<Expr> args) {
    return makeOptionalMap(eh, target, args, false);
  }

  private static Expr makeOptionalMap(
      ExprHelper eh, Expr target, List<Expr> args, boolean wrapResult) {
    String variable = extractIdent(args.get(0));
    if (variable == null) {
      Location location = eh.offsetLocation(args.get(0).getId());
      throw new ErrorWithLocation(location, "argument must be a simple name");
    }

    Expr boundTarget = eh.ident(OPTIONAL_MACRO_TARGET);
    Expr value = eh.receiverCall(OPTIONAL_VALUE, boundTarget, emptyList());
    Expr iterRange =
        eh.globalCall(
            Operator.Conditional.id,
            eh.receiverCall(OPTIONAL_HAS_VALUE, boundTarget, emptyList()),
            eh.newList(value),
            eh.newList());
    Expr init = eh.globalCall(OPTIONAL_NONE);
    Expr step = wrapResult ? eh.globalCall(OPTIONAL_OF, args.get(1)) : args.get(1);
    Expr accuIdent = eh.ident(Macro.AccumulatorName);
    Expr result =
        eh.fold(
            variable,
            iterRange,
            Macro.AccumulatorName,
            init,
            eh.literalBool(true),
            step,
            accuIdent);

    Expr outerAccu = eh.ident(OPTIONAL_MACRO_RESULT);
    Expr dynNull = eh.globalCall("dyn", eh.literalNull());
    return eh.fold(
        OPTIONAL_MACRO_TARGET,
        eh.newList(target),
        OPTIONAL_MACRO_RESULT,
        dynNull,
        eh.literalBool(true),
        result,
        outerAccu);
  }

  private static String extractIdent(Expr expression) {
    if (expression.getExprKindCase() == ExprKindCase.IDENT_EXPR) {
      return expression.getIdentExpr().getName();
    }
    return null;
  }
}
