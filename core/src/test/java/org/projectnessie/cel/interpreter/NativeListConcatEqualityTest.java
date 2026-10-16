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
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import com.google.api.expr.v1alpha1.Type;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
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

class NativeListConcatEqualityTest {
  private static final ExactAdapter ADAPTER = new ExactAdapter();
  private static final Overload ADD = standardImplementation(Operator.Add.id);

  @Test
  void plannerRetainsEverySupportedOperandShapeAndInequality() {
    Env env = exactEnvironment();

    assertNativePlan(env, "(a + b) == c", NativeListConcatEquality.class, 2, 1);
    assertNativePlan(env, "a == (b + c)", NativeListConcatEquality.class, 1, 2);
    assertNativePlan(env, "(a + b) == (c + d)", NativeListConcatEquality.class, 2, 2);
    assertNativePlan(env, "(a + b) != (c + d)", NativeListConcatInequality.class, 2, 2);
  }

  @Test
  void enabledAndNativeDisabledProgramsRemainEquivalent() {
    Env env = exactEnvironment();
    Map<String, Object> input =
        Map.of(
            "a",
            List.of(1L),
            "b",
            new long[] {2L},
            "c",
            List.of(1L),
            "d",
            new LinkedHashSet<>(List.of(2L)));

    for (String expression :
        List.of("(a + b) == c", "a == (b + c)", "(a + b) == (c + d)", "(a + b) != (c + d)")) {
      Program[] programs = programs(env, expression);
      Val enabled = programs[0].eval(input).getVal();
      Val disabled = programs[1].eval(input).getVal();
      assertThat(enabled.getClass()).as(expression).isEqualTo(disabled.getClass());
      assertThat(enabled.toString()).as(expression).isEqualTo(disabled.toString());
    }
  }

  @Test
  void plannerKeepsUnsupportedAdaptersKindsAndReplacedAdditionEstablished() {
    Env exact = exactEnvironment();
    Interpretable nonScalar = plan(exact, "(bytesA + bytesB) == bytesC");
    assertThat(nonScalar).isExactlyInstanceOf(EvalEq.class);

    Env general =
        newEnv(
            declarations(
                Decls.newVar("a", Decls.newListType(Decls.Int)),
                Decls.newVar("b", Decls.newListType(Decls.Int)),
                Decls.newVar("c", Decls.newListType(Decls.Int))));
    Interpretable defaultAdapter = plan(general, "(a + b) == c");
    assertThat(defaultAdapter).isExactlyInstanceOf(EvalEq.class);

    Overload replacement = Overload.binary(Overloads.AddList, (left, right) -> left);
    Interpretable replaced = plan(exact, "(a + b) == a", replacement);
    assertThat(replaced).isExactlyInstanceOf(EvalEq.class);

    Overload equalityReplacement =
        Overload.binary(
            Overloads.Equals, (left, right) -> org.projectnessie.cel.common.types.BoolT.False);
    assertThat(plan(exact, "(a + b) == a", equalityReplacement)).isExactlyInstanceOf(EvalEq.class);

    var compiled = exact.compile("(a + b) == a");
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    Val result =
        exact
            .program(compiled.getAst(), functions(replacement))
            .eval(Map.of("a", List.of(1L), "b", List.of(2L)))
            .getVal();
    assertThat(result.booleanValue()).isTrue();
  }

  @Test
  void plannedConcatEqualityResolvesLeftThenRightSourcesOnce() {
    Env env = exactEnvironment();
    var compiled = env.compile("(a + b) == (c + d)");
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    Program program = env.program(compiled.getAst());
    List<String> resolutions = new ArrayList<>();

    Val result =
        program
            .eval(
                (ActivationFunction)
                    name -> {
                      resolutions.add(name);
                      return switch (name) {
                        case "a", "c" -> List.of(1L);
                        case "b", "d" -> List.of(2L);
                        default -> ActivationFunction.ABSENT;
                      };
                    })
            .getVal();

    assertThat(result.booleanValue()).isTrue();
    assertThat(resolutions).containsExactly("a", "b", "c", "d");
  }

  @Test
  void comparesScalarSegmentsInEncounterOrder() {
    assertThat(
            equality(
                    NativeScalarKind.BOOLEAN,
                    Decls.Bool,
                    List.of((Object) new Boolean[] {true}, List.of(false, true)),
                    List.of((Object) new Boolean[] {true, false}, List.of(true)))
                .evalBoolean(emptyActivation()))
        .isTrue();
    assertThat(
            equality(
                    NativeScalarKind.INT,
                    Decls.Int,
                    List.of((Object) new int[] {1, 2}, new LinkedHashSet<>(List.of(3L))),
                    List.of(List.of(1L), (Object) new long[] {2L, 3L}))
                .evalBoolean(emptyActivation()))
        .isTrue();
    assertThat(
            equality(
                    NativeScalarKind.UINT,
                    Decls.Uint,
                    List.of((Object) new long[] {-1L}, List.of(ULong.valueOf(1L))),
                    List.of(List.of(ULong.valueOf(-1L), ULong.valueOf(1L))))
                .evalBoolean(emptyActivation()))
        .isTrue();
    assertThat(
            equality(
                    NativeScalarKind.STRING,
                    Decls.String,
                    List.of((Object) new String[] {"one"}, List.of("two")),
                    List.of(List.of("one"), (Object) new String[] {"different"}))
                .evalBoolean(emptyActivation()))
        .isFalse();
  }

  @Test
  void preservesDoubleNanAndSignedZeroSemanticsAndInequality() {
    NativeListConcatEquality signedZero =
        equality(
            NativeScalarKind.DOUBLE,
            Decls.Double,
            List.of((Object) new double[] {-0.0d}),
            List.of((Object) new double[] {0.0d}));
    assertThat(signedZero.evalBoolean(emptyActivation())).isTrue();

    NativeListConcatEquality nan =
        equality(
            NativeScalarKind.DOUBLE,
            Decls.Double,
            List.of((Object) new double[] {Double.NaN}),
            List.of((Object) new double[] {Double.NaN}));
    assertThat(nan.evalBoolean(emptyActivation())).isFalse();

    NativeListConcatInequality inequality =
        inequality(
            NativeScalarKind.INT,
            Decls.Int,
            List.of((Object) new long[] {1L}, List.of(2L)),
            List.of(List.of(1L, 3L)));
    assertThat(inequality.evalBoolean(emptyActivation())).isTrue();
  }

  @Test
  void resolvesAndSizesEverySourceBeforeSelectingEarliestFailure() {
    List<String> events = new ArrayList<>();
    Val firstFailure = newErr("first source failed");
    NativeListSourceCapability[] left = {
      source("left-0", Decls.Int, events, () -> firstFailure),
      source(
          "left-1", Decls.Int, events, () -> new LoggingCollection(events, "left-1", List.of(2L)))
    };
    NativeListSourceCapability[] right = {
      source(
          "right-0",
          Decls.Int,
          events,
          () -> {
            throw new IllegalStateException("later raw failure");
          }),
      source(
          "right-1", Decls.Int, events, () -> new LoggingCollection(events, "right-1", List.of(4L)))
    };
    NativeListConcatEquality equality = equality(NativeScalarKind.INT, left, right);

    ValueSignal failure =
        catchThrowableOfType(() -> equality.evalBoolean(emptyActivation()), ValueSignal.class);

    assertThat(failure.value).isSameAs(firstFailure);
    assertThat(events)
        .containsExactly(
            "resolve:left-0",
            "resolve:left-1",
            "resolve:right-0",
            "resolve:right-1",
            "size:left-1",
            "size:right-1");
  }

  @Test
  void earlierSizingFailureBeatsLaterRawFailure() {
    List<String> events = new ArrayList<>();
    NativeListSourceCapability[] left = {
      source(
          "left",
          Decls.Int,
          events,
          () -> new ThrowingSizeCollection(events, "left", "left size failed"))
    };
    NativeListSourceCapability[] right = {
      source(
          "right",
          Decls.Int,
          events,
          () -> {
            throw new IllegalStateException("right raw failed");
          })
    };

    ValueSignal failure =
        catchThrowableOfType(
            () -> equality(NativeScalarKind.INT, left, right).evalBoolean(emptyActivation()),
            ValueSignal.class);

    assertThat(failure.value).isInstanceOf(Err.class);
    assertThat(failure.value.toString()).contains("left size failed");
    assertThat(events).containsExactly("resolve:left", "resolve:right", "size:left");
  }

  @Test
  void sizeMismatchAndFirstMismatchDoNotVisitUnneededElements() {
    RejectingIteratorCollection leftTail = new RejectingIteratorCollection(2);
    RejectingIteratorCollection rightTail = new RejectingIteratorCollection(3);
    NativeListConcatEquality sizeMismatch =
        equality(
            NativeScalarKind.INT,
            sources(Decls.Int, List.of((Object) leftTail)),
            sources(Decls.Int, List.of((Object) rightTail)));
    assertThat(sizeMismatch.evalBoolean(emptyActivation())).isFalse();
    assertThat(leftTail.iteratorCalls).hasValue(0);
    assertThat(rightTail.iteratorCalls).hasValue(0);

    RejectingIteratorCollection unvisitedLeft = new RejectingIteratorCollection(1);
    RejectingIteratorCollection unvisitedRight = new RejectingIteratorCollection(1);
    NativeListConcatEquality firstMismatch =
        equality(
            NativeScalarKind.INT,
            sources(Decls.Int, List.of(List.of(1L), unvisitedLeft)),
            sources(Decls.Int, List.of(List.of(2L), unvisitedRight)));
    assertThat(firstMismatch.evalBoolean(emptyActivation())).isFalse();
    assertThat(unvisitedLeft.iteratorCalls).hasValue(0);
    assertThat(unvisitedRight.iteratorCalls).hasValue(0);

    // Invalid exact elements are caller contract violations. The exact-adapter contract explicitly
    // permits operations to skip elements which are not needed after a decisive result.
    NativeListConcatEquality invalidAfterSizeMismatch =
        equality(
            NativeScalarKind.INT,
            Decls.Int,
            List.of(List.of(1L), List.of("invalid-but-unvisited")),
            List.of(List.of(1L)));
    assertThat(invalidAfterSizeMismatch.evalBoolean(emptyActivation())).isFalse();

    NativeListConcatEquality invalidAfterFirstMismatch =
        equality(
            NativeScalarKind.INT,
            Decls.Int,
            List.of(List.of(1L), List.of("invalid-but-unvisited")),
            List.of(List.of(2L), List.of("also-invalid-but-unvisited")));
    assertThat(invalidAfterFirstMismatch.evalBoolean(emptyActivation())).isFalse();
  }

  @Test
  void unknownAndErrorSignalsAreNotNegated() {
    Val unknown = unknownOf(41L);
    Val error = newErr("right failed");
    NativeListSourceCapability[] left = {
      source("left", Decls.Int, new ArrayList<>(), () -> unknown)
    };
    NativeListSourceCapability[] right = {
      source("right", Decls.Int, new ArrayList<>(), () -> error)
    };

    ValueSignal equalityFailure =
        catchThrowableOfType(
            () -> equality(NativeScalarKind.INT, left, right).evalBoolean(emptyActivation()),
            ValueSignal.class);
    ValueSignal inequalityFailure =
        catchThrowableOfType(
            () -> inequality(NativeScalarKind.INT, left, right).evalBoolean(emptyActivation()),
            ValueSignal.class);

    assertThat(equalityFailure.value).isSameAs(unknown);
    assertThat(inequalityFailure.value).isSameAs(unknown);

    NativeListSourceCapability[] errorLeft = {
      source("error-left", Decls.Int, new ArrayList<>(), () -> error)
    };
    NativeListSourceCapability[] unknownRight = {
      source("unknown-right", Decls.Int, new ArrayList<>(), () -> unknown)
    };
    ValueSignal reverseEqualityFailure =
        catchThrowableOfType(
            () ->
                equality(NativeScalarKind.INT, errorLeft, unknownRight)
                    .evalBoolean(emptyActivation()),
            ValueSignal.class);
    ValueSignal reverseInequalityFailure =
        catchThrowableOfType(
            () ->
                inequality(NativeScalarKind.INT, errorLeft, unknownRight)
                    .evalBoolean(emptyActivation()),
            ValueSignal.class);

    assertThat(reverseEqualityFailure.value).isSameAs(error);
    assertThat(reverseInequalityFailure.value).isSameAs(error);
  }

  private static Env exactEnvironment() {
    return newEnv(
        customTypeAdapter(ADAPTER),
        declarations(
            Decls.newVar("a", Decls.newListType(Decls.Int)),
            Decls.newVar("b", Decls.newListType(Decls.Int)),
            Decls.newVar("c", Decls.newListType(Decls.Int)),
            Decls.newVar("d", Decls.newListType(Decls.Int)),
            Decls.newVar("bytesA", Decls.newListType(Decls.Bytes)),
            Decls.newVar("bytesB", Decls.newListType(Decls.Bytes)),
            Decls.newVar("bytesC", Decls.newListType(Decls.Bytes))));
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

  private static void assertNativePlan(
      Env env,
      String expression,
      Class<? extends Interpretable> expected,
      int leftSources,
      int rightSources) {
    Interpretable plan = plan(env, expression);
    assertThat(plan).as(expression).isInstanceOf(NativeIsland.class);
    Interpretable root = ((NativeIsland) plan).root();
    assertThat(root).as(expression).isInstanceOf(expected);
    if (root instanceof NativeListConcatEquality equality) {
      assertThat(equality.leftSourceCount()).isEqualTo(leftSources);
      assertThat(equality.rightSourceCount()).isEqualTo(rightSources);
    } else {
      NativeListConcatInequality inequality = (NativeListConcatInequality) root;
      assertThat(inequality.leftSourceCount()).isEqualTo(leftSources);
      assertThat(inequality.rightSourceCount()).isEqualTo(rightSources);
    }
  }

  private static NativeListConcatEquality equality(
      NativeScalarKind kind, Type elementType, List<Object> left, List<Object> right) {
    return equality(kind, sources(elementType, left), sources(elementType, right));
  }

  private static NativeListConcatEquality equality(
      NativeScalarKind kind,
      NativeListSourceCapability[] left,
      NativeListSourceCapability[] right) {
    return new NativeListConcatEquality(1L, operand(left), operand(right), kind, ADAPTER);
  }

  private static NativeListConcatInequality inequality(
      NativeScalarKind kind, Type elementType, List<Object> left, List<Object> right) {
    return inequality(kind, sources(elementType, left), sources(elementType, right));
  }

  private static NativeListConcatInequality inequality(
      NativeScalarKind kind,
      NativeListSourceCapability[] left,
      NativeListSourceCapability[] right) {
    return new NativeListConcatInequality(1L, operand(left), operand(right), kind, ADAPTER);
  }

  private static NativeListConcatEqualityOperand operand(NativeListSourceCapability[] sources) {
    Interpretable expression = sources[0];
    for (int i = 1; i < sources.length; i++) {
      expression = new NativeListConcat(10L + i, expression, sources[i], ADD);
    }
    return NativeListConcatEqualityOperand.from(expression);
  }

  private static NativeListSourceCapability[] sources(Type elementType, List<Object> rawValues) {
    NativeListSourceCapability[] sources = new NativeListSourceCapability[rawValues.size()];
    for (int i = 0; i < rawValues.size(); i++) {
      Object raw = rawValues.get(i);
      sources[i] = new TestListSource(i + 1L, Decls.newListType(elementType), () -> raw);
    }
    return sources;
  }

  private static NativeListSourceCapability source(
      String name, Type elementType, List<String> events, Supplier<Object> value) {
    return new TestListSource(
        name.hashCode(),
        Decls.newListType(elementType),
        () -> {
          events.add("resolve:" + name);
          return value.get();
        });
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
    private final Supplier<Object> value;

    private TestListSource(long id, Type checkedType, Supplier<Object> value) {
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
      return value.get();
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

  private static class LoggingCollection extends AbstractCollection<Object> {
    private final List<String> events;
    private final String name;
    private final List<?> values;

    private LoggingCollection(List<String> events, String name, List<?> values) {
      this.events = events;
      this.name = name;
      this.values = values;
    }

    @Override
    public Iterator<Object> iterator() {
      events.add("iterate:" + name);
      @SuppressWarnings("unchecked")
      Iterator<Object> iterator = (Iterator<Object>) values.iterator();
      return iterator;
    }

    @Override
    public int size() {
      events.add("size:" + name);
      return values.size();
    }
  }

  private static final class ThrowingSizeCollection extends LoggingCollection {
    private final String failure;

    private ThrowingSizeCollection(List<String> events, String name, String failure) {
      super(events, name, List.of());
      this.failure = failure;
    }

    @Override
    public int size() {
      super.size();
      throw new IllegalStateException(failure);
    }
  }

  private static final class RejectingIteratorCollection extends AbstractCollection<Object> {
    private final int size;
    private final AtomicInteger iteratorCalls = new AtomicInteger();

    private RejectingIteratorCollection(int size) {
      this.size = size;
    }

    @Override
    public Iterator<Object> iterator() {
      iteratorCalls.incrementAndGet();
      throw new AssertionError("elements must not be visited");
    }

    @Override
    public int size() {
      return size;
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
