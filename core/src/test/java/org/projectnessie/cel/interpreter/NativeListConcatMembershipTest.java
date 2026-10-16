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
package org.projectnessie.cel.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.projectnessie.cel.CEL.astToCheckedExpr;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.ProgramOption.functions;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import com.google.api.expr.v1alpha1.Type;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.functions.Overload;

class NativeListConcatMembershipTest {
  private static final ExactAdapter ADAPTER = new ExactAdapter();
  private static final Overload ADD = standardImplementation(Operator.Add.id);
  private static final Overload IN = standardImplementation(Operator.In.id);

  @Test
  void plannerRetainsSupportedConcatMembershipAndNativeDisabledParity() {
    Env env = exactEnvironment();
    Interpretable plan = plan(env, "needle in (a + b)");

    assertThat(plan).isInstanceOf(NativeIsland.class);
    Interpretable root = ((NativeIsland) plan).root();
    assertThat(root).isInstanceOf(NativeScalarListConcatMembership.class);
    assertThat(((NativeScalarListConcatMembership) root).sourceCount()).isEqualTo(2);

    Program[] programs = programs(env, "needle in (a + b)");
    for (Map<String, Object> input :
        List.of(
            Map.of("needle", 2L, "a", List.of(1L), "b", List.of(2L)),
            Map.of("needle", 3L, "a", List.of(1L), "b", List.of(2L)),
            Map.of("needle", "not-an-int", "a", List.of(1L), "b", List.of(2L)))) {
      Val enabled = programs[0].eval(input).getVal();
      Val disabled = programs[1].eval(input).getVal();
      assertThat(enabled.getClass()).as(input.toString()).isEqualTo(disabled.getClass());
      assertThat(enabled.toString()).as(input.toString()).isEqualTo(disabled.toString());
    }
  }

  @Test
  void plannerFallsBackForUnsupportedAdapterKindAndReplacedOverloads() {
    Env exact = exactEnvironment();
    assertThat(nativeMembershipRoot(plan(exact, "bytesNeedle in (bytesA + bytesB)"))).isNull();

    Env general =
        newEnv(
            declarations(
                Decls.newVar("needle", Decls.Int),
                Decls.newVar("a", Decls.newListType(Decls.Int)),
                Decls.newVar("b", Decls.newListType(Decls.Int))));
    assertThat(nativeMembershipRoot(plan(general, "needle in (a + b)"))).isNull();

    Overload replacedMembership = Overload.binary(Overloads.InList, (left, right) -> False);
    assertThat(nativeMembershipRoot(plan(exact, "needle in (a + b)", replacedMembership))).isNull();
    var compiledMembership = exact.compile("needle in (a + b)");
    assertThat(compiledMembership.hasIssues())
        .as(compiledMembership.getIssues().toString())
        .isFalse();
    assertThat(
            exact
                .program(compiledMembership.getAst(), functions(replacedMembership))
                .eval(Map.of("needle", 2L, "a", List.of(1L), "b", List.of(2L)))
                .getVal()
                .booleanValue())
        .isFalse();

    Overload replacedAddition = Overload.binary(Overloads.AddList, (left, right) -> left);
    assertThat(nativeMembershipRoot(plan(exact, "needle in (a + b)", replacedAddition))).isNull();
    var compiledAddition = exact.compile("needle in (a + b)");
    assertThat(compiledAddition.hasIssues()).as(compiledAddition.getIssues().toString()).isFalse();
    assertThat(
            exact
                .program(compiledAddition.getAst(), functions(replacedAddition))
                .eval(Map.of("needle", 2L, "a", List.of(1L), "b", List.of(2L)))
                .getVal()
                .booleanValue())
        .isFalse();
  }

  @Test
  void constantMembershipPreservesEverySupportedScalarKind() {
    assertThat(
            membership(
                    NativeScalarKind.BOOLEAN,
                    new NativeBooleanConst(1L, true),
                    Decls.Bool,
                    List.of(List.of(false), List.of(true)))
                .evalBoolean(emptyActivation()))
        .isTrue();
    assertThat(
            membership(
                    NativeScalarKind.INT,
                    new NativeIntConst(1L, 2L),
                    Decls.Int,
                    List.of((Object) new int[] {1}, List.of(2L)))
                .evalBoolean(emptyActivation()))
        .isTrue();
    assertThat(
            membership(
                    NativeScalarKind.UINT,
                    new NativeUintConst(1L, -1L),
                    Decls.Uint,
                    List.of(List.of(ULong.valueOf(1L)), (Object) new long[] {-1L}))
                .evalBoolean(emptyActivation()))
        .isTrue();
    assertThat(
            membership(
                    NativeScalarKind.STRING,
                    new NativeStringConst(1L, "value"),
                    Decls.String,
                    List.of((Object) new String[] {"other"}, List.of("value")))
                .evalBoolean(emptyActivation()))
        .isTrue();
  }

  @Test
  void doubleMembershipPreservesNanAndSignedZeroSemantics() {
    NativeScalarListConcatMembership signedZero =
        membership(
            NativeScalarKind.DOUBLE,
            new NativeDoubleConst(1L, 0.0d),
            Decls.Double,
            List.of((Object) new double[] {-0.0d}, List.of(1.0d)));
    NativeScalarListConcatMembership nan =
        membership(
            NativeScalarKind.DOUBLE,
            new NativeDoubleConst(1L, Double.NaN),
            Decls.Double,
            List.of((Object) new double[] {Double.NaN}, List.of(1.0d)));

    assertThat(signedZero.evalBoolean(emptyActivation())).isTrue();
    assertThat(nan.evalBoolean(emptyActivation())).isFalse();
  }

  @Test
  void dynamicNeedleResolvesBeforeEveryRhsSourceExactlyOnce() {
    List<String> resolutions = new ArrayList<>();
    NativeListSourceCapability[] sources = {
      activationSource(2L, "left", Decls.Int),
      activationSource(3L, "right", Decls.Int),
      activationSource(4L, "third", Decls.Int)
    };
    NativeScalarListConcatMembership membership =
        membership(NativeScalarKind.INT, new NativeIntIdent(1L, "needle", ADAPTER), sources);
    Activation activation =
        newActivation(
            (ActivationFunction)
                name -> {
                  resolutions.add(name);
                  return switch (name) {
                    case "needle" -> 3L;
                    case "left" -> List.of(1L);
                    case "right" -> List.of(2L);
                    case "third" -> List.of(3L);
                    default -> ActivationFunction.ABSENT;
                  };
                });

    assertThat(membership.evalBoolean(activation)).isTrue();
    assertThat(resolutions).containsExactly("needle", "left", "right", "third");
    assertThat(membership.sourceCount()).isEqualTo(3);
  }

  @Test
  void needleFailureWinsAfterEveryRhsSourceResolvesAndSizes() {
    List<String> resolutions = new ArrayList<>();
    AtomicInteger laterSizeCalls = new AtomicInteger();
    Val needleFailure = newErr("needle failed");
    NativeListSourceCapability[] sources = {
      activationSource(2L, "left", Decls.Int),
      activationSource(3L, "right", Decls.Int),
      activationSource(4L, "third", Decls.Int)
    };
    NativeScalarListConcatMembership membership =
        membership(NativeScalarKind.INT, new NativeIntIdent(1L, "needle", ADAPTER), sources);
    Activation activation =
        newActivation(
            (ActivationFunction)
                name -> {
                  resolutions.add(name);
                  return switch (name) {
                    case "needle" -> needleFailure;
                    case "left" -> newErr("rhs failed");
                    case "right" -> List.of(2L);
                    case "third" ->
                        new CountingCollection(List.of(3L), laterSizeCalls, new AtomicInteger());
                    default -> ActivationFunction.ABSENT;
                  };
                });

    ValueSignal failure =
        catchThrowableOfType(() -> membership.evalBoolean(activation), ValueSignal.class);

    assertThat(failure.value).isSameAs(needleFailure);
    assertThat(resolutions).containsExactly("needle", "left", "right", "third");
    assertThat(laterSizeCalls).hasValue(1);
  }

  @Test
  void successfulNeedlePropagatesEarliestRhsFailure() {
    Val rhsFailure = newErr("rhs failed");
    NativeListSourceCapability[] sources = {
      source(2L, Decls.Int, ignored -> rhsFailure), source(3L, Decls.Int, ignored -> List.of(2L))
    };
    NativeScalarListConcatMembership membership =
        membership(NativeScalarKind.INT, new NativeIntConst(1L, 2L), sources);

    ValueSignal failure =
        catchThrowableOfType(() -> membership.evalBoolean(emptyActivation()), ValueSignal.class);

    assertThat(failure.value).isSameAs(rhsFailure);
  }

  @Test
  void ordinarySlowNeedleUsesCelEqualityAndDoesNotOutrankRhsFailure() {
    NativeListSourceCapability[] successfulSources = {
      source(2L, Decls.Int, ignored -> List.of(1L)), source(3L, Decls.Int, ignored -> List.of(2L))
    };
    NativeScalarListConcatMembership successfulMembership =
        membership(
            NativeScalarKind.INT, new NativeIntIdent(1L, "needle", ADAPTER), successfulSources);
    Activation mismatchedNeedle = newActivation(Map.of("needle", "not-an-int"));

    assertThat(successfulMembership.evalBoolean(mismatchedNeedle)).isFalse();

    Val rhsFailure = newErr("rhs failed");
    NativeListSourceCapability[] failingSources = {
      source(2L, Decls.Int, ignored -> rhsFailure), source(3L, Decls.Int, ignored -> List.of(2L))
    };
    NativeScalarListConcatMembership failingMembership =
        membership(NativeScalarKind.INT, new NativeIntIdent(1L, "needle", ADAPTER), failingSources);

    ValueSignal failure =
        catchThrowableOfType(
            () -> failingMembership.evalBoolean(mismatchedNeedle), ValueSignal.class);
    assertThat(failure.value).isSameAs(rhsFailure);
  }

  @Test
  void unknownNeedleAlsoWinsAfterRhsFailure() {
    Val unknown = unknownOf(91L);
    NativeListSourceCapability[] sources = {
      activationSource(2L, "left", Decls.Int), source(3L, Decls.Int, ignored -> List.of(2L))
    };
    NativeScalarListConcatMembership membership =
        membership(NativeScalarKind.INT, new NativeIntIdent(1L, "needle", ADAPTER), sources);
    Activation activation =
        newActivation(
            (ActivationFunction)
                name ->
                    switch (name) {
                      case "needle" -> unknown;
                      case "left" -> newErr("rhs failed");
                      default -> ActivationFunction.ABSENT;
                    });

    ValueSignal failure =
        catchThrowableOfType(() -> membership.evalBoolean(activation), ValueSignal.class);

    assertThat(failure.value).isSameAs(unknown);
  }

  @Test
  void earlyHitSuppressesLaterElementVisitsButNotResolutionOrSizing() {
    AtomicInteger laterResolutions = new AtomicInteger();
    AtomicInteger laterSizeCalls = new AtomicInteger();
    AtomicInteger laterIteratorCalls = new AtomicInteger();
    NativeListSourceCapability[] sources = {
      source(2L, Decls.Int, ignored -> List.of(2L)),
      source(
          3L,
          Decls.Int,
          ignored -> {
            laterResolutions.incrementAndGet();
            return new CountingCollection(List.of(3L), laterSizeCalls, laterIteratorCalls);
          })
    };
    NativeScalarListConcatMembership membership =
        membership(NativeScalarKind.INT, new NativeIntConst(1L, 2L), sources);

    assertThat(membership.evalBoolean(emptyActivation())).isTrue();
    assertThat(laterResolutions).hasValue(1);
    assertThat(laterSizeCalls).hasValue(1);
    assertThat(laterIteratorCalls).hasValue(0);
  }

  @Test
  void invalidVisitedElementIsAnErrorButUnvisitedInvalidElementIsIgnored() {
    NativeScalarListConcatMembership invalidFirst =
        membership(
            NativeScalarKind.INT,
            new NativeIntConst(1L, 2L),
            Decls.Int,
            List.of(List.of("not-an-int"), List.of(2L)));
    ValueSignal failure =
        catchThrowableOfType(() -> invalidFirst.evalBoolean(emptyActivation()), ValueSignal.class);
    assertThat(failure.value).isInstanceOf(Err.class);

    NativeScalarListConcatMembership invalidAfterHit =
        membership(
            NativeScalarKind.INT,
            new NativeIntConst(1L, 2L),
            Decls.Int,
            List.of(List.of(2L), List.of("not-an-int")));
    assertThat(invalidAfterHit.evalBoolean(emptyActivation())).isTrue();
  }

  private static Env exactEnvironment() {
    return newEnv(
        customTypeAdapter(ADAPTER),
        declarations(
            Decls.newVar("needle", Decls.Int),
            Decls.newVar("a", Decls.newListType(Decls.Int)),
            Decls.newVar("b", Decls.newListType(Decls.Int)),
            Decls.newVar("bytesNeedle", Decls.Bytes),
            Decls.newVar("bytesA", Decls.newListType(Decls.Bytes)),
            Decls.newVar("bytesB", Decls.newListType(Decls.Bytes))));
  }

  private static Program[] programs(Env env, String expression) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    return new Program[] {
      env.program(compiled.getAst()),
      env.program(compiled.getAst(), evalOptions(OptDisableNativeEval))
    };
  }

  private static Interpretable plan(Env env, String expression, Overload... replacements) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    var checked = astToCheckedExpr(compiled.getAst());
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    dispatcher.add(replacements);
    TypeAdapter adapter = env.getTypeAdapter();
    AttributeFactory attributes =
        newAttributeFactory(defaultContainer, adapter, env.getTypeProvider());
    Interpreter interpreter =
        newInterpreter(
            dispatcher, defaultContainer, env.getTypeProvider(), adapter, attributes, true);
    return interpreter.newInterpretable(checked);
  }

  private static NativeScalarListConcatMembership nativeMembershipRoot(Interpretable plan) {
    Interpretable root = plan instanceof NativeIsland island ? island.root() : plan;
    return root instanceof NativeScalarListConcatMembership membership ? membership : null;
  }

  private static NativeScalarListConcatMembership membership(
      NativeScalarKind kind, Interpretable needle, Type elementType, List<Object> rawSources) {
    NativeListSourceCapability[] sources = new NativeListSourceCapability[rawSources.size()];
    for (int i = 0; i < rawSources.size(); i++) {
      Object raw = rawSources.get(i);
      sources[i] = source(i + 2L, elementType, ignored -> raw);
    }
    return membership(kind, needle, sources);
  }

  private static NativeScalarListConcatMembership membership(
      NativeScalarKind kind, Interpretable needle, NativeListSourceCapability[] sources) {
    NativeListConcat concat = concat(sources);
    NativeListTraversalPlan traversal = NativeListTraversalPlan.concat(concat);
    return new NativeScalarListConcatMembership(100L, needle, concat, traversal, IN, kind, ADAPTER);
  }

  private static NativeListConcat concat(NativeListSourceCapability[] sources) {
    if (sources.length < 2) {
      throw new IllegalArgumentException("test concat requires at least two sources");
    }
    NativeListConcat concat = new NativeListConcat(50L, sources[0], sources[1], ADD);
    for (int i = 2; i < sources.length; i++) {
      concat = new NativeListConcat(50L + i, concat, sources[i], ADD);
    }
    return concat;
  }

  private static NativeListSourceCapability activationSource(
      long id, String name, Type elementType) {
    return source(id, elementType, activation -> activation.resolve(name));
  }

  private static NativeListSourceCapability source(
      long id, Type elementType, Function<Activation, Object> value) {
    return new TestListSource(id, Decls.newListType(elementType), value);
  }

  private static Overload standardImplementation(String function) {
    return Arrays.stream(Overload.standardOverloads())
        .filter(overload -> overload.operator.equals(function))
        .findFirst()
        .orElseThrow();
  }

  private static final class TestListSource extends AbstractEval
      implements NativeListSourceCapability {
    private final CheckedAggregateMaterializer materializer;
    private final Function<Activation, Object> value;

    private TestListSource(long id, Type checkedType, Function<Activation, Object> value) {
      super(id);
      this.materializer = new CheckedAggregateMaterializer(ADAPTER, checkedType);
      this.value = value;
    }

    @Override
    public Val eval(Activation activation) {
      try {
        return materializeResolvedList(evalRaw(activation));
      } catch (ValueSignal failure) {
        return failure.value;
      }
    }

    @Override
    public Object evalRaw(Activation activation) {
      return value.apply(activation);
    }

    @Override
    public Val materializeResolvedList(Object value) {
      return value instanceof Val val && (isError(val) || isUnknown(val))
          ? val
          : materializer.materialize(value);
    }

    @Override
    public Val materializeResolvedElement(Object value) {
      return materializer.materializeListElement(value);
    }

    @Override
    public boolean exactListSource() {
      return true;
    }
  }

  private static final class CountingCollection extends AbstractCollection<Object> {
    private final List<?> values;
    private final AtomicInteger sizeCalls;
    private final AtomicInteger iteratorCalls;

    private CountingCollection(
        List<?> values, AtomicInteger sizeCalls, AtomicInteger iteratorCalls) {
      this.values = values;
      this.sizeCalls = sizeCalls;
      this.iteratorCalls = iteratorCalls;
    }

    @Override
    public Iterator<Object> iterator() {
      iteratorCalls.incrementAndGet();
      @SuppressWarnings("unchecked")
      Iterator<Object> iterator = (Iterator<Object>) values.iterator();
      return iterator;
    }

    @Override
    public int size() {
      sizeCalls.incrementAndGet();
      return values.size();
    }
  }

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }
}
