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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.projectnessie.cel.CEL.astToCheckedExpr;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Dispatcher.extendDispatcher;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import com.google.api.expr.v1alpha1.Decl;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.functions.Overload;

class NativeMapTraversalTest {
  private static final ExactAdapter ADAPTER = new ExactAdapter();
  private static final Decl STRING_INTS =
      Decls.newVar("values", Decls.newMapType(Decls.String, Decls.Int));
  private static final Env EXACT_ENV =
      newEnv(customTypeAdapter(ADAPTER), declarations(STRING_INTS));
  private static final Env SCALAR_ENV =
      newEnv(
          customTypeAdapter(ADAPTER),
          declarations(
              Decls.newVar("boolKeys", Decls.newMapType(Decls.Bool, Decls.Int)),
              Decls.newVar("intKeys", Decls.newMapType(Decls.Int, Decls.Int)),
              Decls.newVar("uintKeys", Decls.newMapType(Decls.Uint, Decls.Int)),
              Decls.newVar("boolValues", Decls.newMapType(Decls.String, Decls.Bool)),
              Decls.newVar("intValues", Decls.newMapType(Decls.String, Decls.Int)),
              Decls.newVar("uintValues", Decls.newMapType(Decls.String, Decls.Uint)),
              Decls.newVar("doubleValues", Decls.newMapType(Decls.String, Decls.Double)),
              Decls.newVar("stringValues", Decls.newMapType(Decls.String, Decls.String)),
              Decls.newVar("nullValues", Decls.newMapType(Decls.String, Decls.Null))));
  private static final Env EXCEPTION_ENV =
      newEnv(
          customTypeAdapter(ADAPTER),
          declarations(STRING_INTS, Decls.newVar("exceptional", Decls.Bool)));

  @Test
  void keyOnlyTraversalDoesNotReadSizeOrValues() {
    InstrumentedMap map = new InstrumentedMap(Map.of("one", 1L, "two", 2L));
    AtomicInteger resolutions = new AtomicInteger();
    NativeMapTraversalPlan plan =
        traversalPlan(
            activation -> {
              resolutions.incrementAndGet();
              return map;
            },
            NativeScalarKind.STRING,
            null);

    NativeResolvedMapTraversal resolved = plan.resolve(emptyActivation());
    assertThat(resolutions).hasValue(1);
    assertThat(map.entrySetCalls).hasValue(0);
    assertThat(map.sizeCalls).hasValue(0);

    List<String> keys = new ArrayList<>();
    NativeLoopBinding key = new NativeLoopBinding(emptyActivation(), "key");
    assertThat(
            resolved.traverse(
                key,
                null,
                binding -> {
                  keys.add(binding.stringValue(ADAPTER));
                  return false;
                }))
        .isFalse();

    assertThat(keys).containsExactlyInAnyOrder("one", "two");
    assertThat(map.sizeCalls).hasValue(0);
    assertThat(map.getKeyCalls).hasValue(2);
    assertThat(map.getValueCalls).hasValue(0);
  }

  @Test
  void twoVariableTraversalReadsEachValueOnceAndResetsTheMaterializedCache() {
    LinkedHashMap<String, Long> values = new LinkedHashMap<>();
    values.put("one", 1L);
    values.put("two", 2L);
    InstrumentedMap map = new InstrumentedMap(values);
    NativeMapTraversalPlan plan =
        traversalPlan(ignored -> map, NativeScalarKind.STRING, NativeScalarKind.INT);
    NativeLoopBinding key = new NativeLoopBinding(emptyActivation(), "key");
    NativeLoopBinding value = new NativeLoopBinding(key, "value");
    NativeStringLocalIdent keyIdent = new NativeStringLocalIdent(1L, "key", ADAPTER);
    NativeIntLocalIdent valueIdent = new NativeIntLocalIdent(2L, "value", ADAPTER);
    List<String> visited = new ArrayList<>();
    List<Val> materialized = new ArrayList<>();

    plan.resolve(emptyActivation())
        .traverse(
            key,
            value,
            binding -> {
              visited.add(keyIdent.evalString(binding) + "=" + valueIdent.evalInt(binding));
              Object first = binding.resolve("value");
              assertThat(binding.resolve("value")).isSameAs(first);
              materialized.add((Val) first);
              return false;
            });

    assertThat(visited).containsExactly("one=1", "two=2");
    assertThat(map.sizeCalls).hasValue(0);
    assertThat(map.getValueCalls).hasValue(2);
    assertThat(materialized).extracting(Val::intValue).containsExactly(1L, 2L);
    assertThat(materialized.get(0)).isNotSameAs(materialized.get(1));
  }

  @Test
  void twoVariableTraversalReadsKeyThenValueBeforeBindingEither() {
    List<String> accesses = new ArrayList<>();
    Map<Object, Object> map =
        new AbstractMap<>() {
          @Override
          public Set<Entry<Object, Object>> entrySet() {
            return Set.of(
                new Entry<>() {
                  @Override
                  public Object getKey() {
                    accesses.add("key");
                    return 1L; // Invalid for the checked string key, but not materialized yet.
                  }

                  @Override
                  public Object getValue() {
                    accesses.add("value");
                    throw new IllegalStateException("value access failed");
                  }

                  @Override
                  public Object setValue(Object value) {
                    throw new UnsupportedOperationException();
                  }
                });
          }
        };
    NativeMapTraversalPlan plan =
        traversalPlan(ignored -> map, NativeScalarKind.STRING, NativeScalarKind.INT);
    NativeLoopBinding key = new NativeLoopBinding(emptyActivation(), "key");
    NativeLoopBinding value = new NativeLoopBinding(key, "value");

    assertFailure(
        () -> plan.resolve(emptyActivation()).traverse(key, value, ignored -> false),
        "value access failed");
    assertThat(accesses).containsExactly("key", "value");
  }

  @Test
  void bindingsUseNearestNameAndAbsoluteNamesBypassEveryLoopScope() {
    Activation parent = newActivation(Map.of("same", 99L));
    NativeLoopBinding outer = new NativeLoopBinding(parent, "same");
    NativeLoopBinding inner = new NativeLoopBinding(outer, "same");
    outer.setInt(10L);
    inner.setInt(20L);

    assertThat(new NativeIntLocalIdent(1L, "same", ADAPTER).evalInt(inner)).isEqualTo(20L);
    assertThat(new NativeIntIdent(2L, ".same", ADAPTER).evalInt(inner)).isEqualTo(99L);
    assertThat(NativeLoopBinding.find(inner, "missing")).isNull();
  }

  @TestFactory
  List<DynamicTest> traversalFailuresBecomeCelErrorsWithoutSourceReplay() {
    List<DynamicTest> tests = new ArrayList<>();
    tests.add(
        DynamicTest.dynamicTest(
            "source",
            () -> {
              AtomicInteger resolutions = new AtomicInteger();
              NativeMapTraversalPlan plan =
                  traversalPlan(
                      ignored -> {
                        resolutions.incrementAndGet();
                        throw new IllegalStateException("source failed");
                      },
                      NativeScalarKind.STRING,
                      null);

              assertFailure(() -> plan.resolve(emptyActivation()), "source failed");
              assertThat(resolutions).hasValue(1);
            }));

    for (FailurePoint point : FailurePoint.values()) {
      tests.add(
          DynamicTest.dynamicTest(
              point.name().toLowerCase(),
              () -> {
                AtomicInteger resolutions = new AtomicInteger();
                NativeMapTraversalPlan plan =
                    traversalPlan(
                        ignored -> {
                          resolutions.incrementAndGet();
                          return new FailingMap(point);
                        },
                        NativeScalarKind.STRING,
                        NativeScalarKind.INT);
                NativeResolvedMapTraversal resolved = plan.resolve(emptyActivation());
                NativeLoopBinding key = new NativeLoopBinding(emptyActivation(), "key");
                NativeLoopBinding value = new NativeLoopBinding(key, "value");

                assertFailure(
                    () -> resolved.traverse(key, value, ignored -> false),
                    point.name().toLowerCase());
                assertThat(resolutions).hasValue(1);
              }));
    }
    return tests;
  }

  @SuppressWarnings("resource")
  @Test
  void immutableTraversalPlanCanBeResolvedAndTraversedConcurrently() throws Exception {
    NativeMapTraversalPlan plan =
        traversalPlan(
            activation -> activation.resolve("values"),
            NativeScalarKind.STRING,
            NativeScalarKind.INT);
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Long>> results = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        long expected = i;
        results.add(
            executor.submit(
                () -> {
                  NativeLoopBinding key = new NativeLoopBinding(emptyActivation(), "key");
                  NativeLoopBinding value = new NativeLoopBinding(key, "value");
                  long[] observed = {-1L};
                  plan.resolve(newActivation(Map.of("values", Map.of("key", expected))))
                      .traverse(
                          key,
                          value,
                          binding -> {
                            observed[0] = binding.intValue(ADAPTER);
                            return false;
                          });
                  return observed[0];
                }));
      }
      for (int i = 0; i < results.size(); i++) {
        assertThat(results.get(i).get(5, SECONDS)).isEqualTo(i);
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  @Test
  void plannerSelectsNativeMapQuantifiersWithAndWithoutBuiltInOptimization() {
    for (String expression :
        List.of(
            "values.exists(key, key == 'one')",
            "values.all(key, key != 'missing')",
            "values.exists_one(key, key == 'one')",
            "values.exists(key, value, key == 'one' && value == 1)")) {
      assertThat(root(plan(EXACT_ENV, expression)))
          .as(expression)
          .isInstanceOf(NativeMapQuantifierFold.class);
      assertThat(root(plan(EXACT_ENV, expression, Interpreter.optimize())))
          .as(expression)
          .isInstanceOf(NativeMapQuantifierFold.class);
    }
  }

  @TestFactory
  List<DynamicTest> plannedQuantifiersCoverSupportedKeyAndScalarValueKinds() {
    Map<Object, Object> intKeys = new LinkedHashMap<>();
    intKeys.put((byte) 1, 1L);
    Map<Object, Object> uintKeys = new LinkedHashMap<>();
    uintKeys.put(Long.MIN_VALUE, 1L);
    uintKeys.put(ULong.valueOf(-1L), 2L);
    Map<String, Object> nullValues = new LinkedHashMap<>();
    nullValues.put("null", null);
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("boolKeys", Map.of(true, 1L));
    variables.put("intKeys", intKeys);
    variables.put("uintKeys", uintKeys);
    variables.put("boolValues", Map.of("true", true));
    variables.put("intValues", Map.of("one", (byte) 1));
    variables.put("uintValues", Map.of("high", ULong.valueOf(-1L)));
    variables.put("doubleValues", Map.of("double", 1.5f));
    variables.put("stringValues", Map.of("string", "value"));
    variables.put("nullValues", nullValues);

    List<String> expressions =
        List.of(
            "boolKeys.exists(key, key)",
            "intKeys.exists(key, key == 1)",
            "uintKeys.exists(key, key == 9223372036854775808u)",
            "uintKeys.exists(key, key == 18446744073709551615u)",
            "boolValues.all(key, value, value)",
            "intValues.exists(key, value, value == 1)",
            "uintValues.exists(key, value, value == 18446744073709551615u)",
            "doubleValues.all(key, value, value > 1.0)",
            "stringValues.exists_one(key, value, value == 'value')",
            "nullValues.all(key, value, value == null)");
    List<DynamicTest> tests = new ArrayList<>();
    for (String expression : expressions) {
      tests.add(
          DynamicTest.dynamicTest(
              expression, () -> assertNativeParity(SCALAR_ENV, expression, variables)));
    }
    return tests;
  }

  @Test
  void sourceErrorsAndUnknownsPropagateWithNativeDisabledParity() {
    for (Val failure : List.of(newErr("source failed"), unknownOf(71L))) {
      ActivationFunction input =
          name -> name.equals("values") ? failure : ActivationFunction.ABSENT;
      Val[] results =
          evaluateNativeAndEstablished(EXACT_ENV, "values.exists(key, key == 'match')", input);
      assertThat(results[0]).isSameAs(failure);
      assertThat(results[1]).isSameAs(failure);
    }
  }

  @Test
  void predicateErrorsAndUnknownsRetainDecisiveAndExistsOneSemantics() {
    LinkedHashMap<String, Long> values = new LinkedHashMap<>();
    values.put("problem", 1L);
    values.put("match", 2L);
    values.put("stop", 3L);
    String exists = "values.exists(key, key == 'match' || (key == 'problem' && exceptional))";
    String all = "values.all(key, key != 'stop' && (key != 'problem' || exceptional))";
    String existsOne =
        "values.exists_one(key, key == 'match' || (key == 'problem' && exceptional))";

    for (Val exceptional : List.of(newErr("predicate failed"), unknownOf(72L))) {
      Map<String, Object> variables = Map.of("values", values, "exceptional", exceptional);
      Val[] existsResults = evaluateNativeAndEstablished(EXCEPTION_ENV, exists, variables);
      assertThat(existsResults).containsExactly(True, True);
      Val[] allResults = evaluateNativeAndEstablished(EXCEPTION_ENV, all, variables);
      assertThat(allResults).containsExactly(False, False);

      Val[] existsOneResults = evaluateNativeAndEstablished(EXCEPTION_ENV, existsOne, variables);
      for (Val result : existsOneResults) {
        if (exceptional instanceof Err) {
          assertThat(result).isInstanceOf(Err.class);
        } else {
          assertThat(isUnknown(result)).isTrue();
        }
      }
    }
  }

  @Test
  void sortedMapsAndVisitedContractViolationsRespectTheTraversalBoundary() {
    TreeMap<String, Long> sorted = new TreeMap<>();
    sorted.put("first", 1L);
    sorted.put("match", 2L);
    assertNativeParity(EXACT_ENV, "values.exists(key, key == 'match')", Map.of("values", sorted));

    LinkedHashMap<Object, Object> visitedInvalid = new LinkedHashMap<>();
    visitedInvalid.put(1L, 1L);
    visitedInvalid.put("match", 2L);
    Val visited =
        plan(EXACT_ENV, "values.exists(key, key == 'match')")
            .eval(newActivation(Map.of("values", visitedInvalid)));
    assertThat(visited).isInstanceOf(Err.class);

    LinkedHashMap<Object, Object> unvisitedInvalid = new LinkedHashMap<>();
    unvisitedInvalid.put("match", 2L);
    unvisitedInvalid.put(1L, 1L);
    Val unvisited =
        plan(EXACT_ENV, "values.exists(key, key == 'match')")
            .eval(newActivation(Map.of("values", unvisitedInvalid)));
    assertThat(unvisited).isSameAs(True);
  }

  @Test
  void plannerFallsBackForUnsupportedPoliciesShapesAndProvenance() {
    Env defaultEnv = newEnv(declarations(STRING_INTS));
    InterpretableDecorator custom = node -> node;

    assertThat(root(plan(defaultEnv, "values.exists(key, key == 'one')")))
        .isExactlyInstanceOf(EvalFold.class);
    assertThat(root(plan(EXACT_ENV, "values.exists(key, key == 'one')", custom)))
        .isExactlyInstanceOf(EvalFold.class);
    assertThat(root(plan(EXACT_ENV, "values.exists(key, values.exists(other, other == key))")))
        .isExactlyInstanceOf(EvalFold.class);

    Env aggregateValues =
        newEnv(
            customTypeAdapter(ADAPTER),
            declarations(
                Decls.newVar(
                    "aggregateValues",
                    Decls.newMapType(Decls.String, Decls.newListType(Decls.Int)))));
    assertThat(root(plan(aggregateValues, "aggregateValues.exists(key, value, size(value) > 0)")))
        .isExactlyInstanceOf(EvalFold.class);

    Overload logicalReplacement = Overload.binary(Overloads.LogicalOr, (left, right) -> True);
    assertThat(
            root(
                planWithReplacement(
                    EXACT_ENV, "values.exists(key, key == 'one')", logicalReplacement)))
        .isExactlyInstanceOf(EvalFold.class);
  }

  private static NativeMapTraversalPlan traversalPlan(
      Function<Activation, Object> values, NativeScalarKind keyKind, NativeScalarKind valueKind) {
    return new NativeMapTraversalPlan(
        new TestMapSource(values),
        keyKind,
        valueKind,
        new CheckedValueMaterializer(ADAPTER, checkedType(keyKind)),
        valueKind != null ? new CheckedValueMaterializer(ADAPTER, checkedType(valueKind)) : null);
  }

  private static com.google.api.expr.v1alpha1.Type checkedType(NativeScalarKind kind) {
    return switch (kind) {
      case BOOLEAN -> Decls.Bool;
      case INT -> Decls.Int;
      case UINT -> Decls.Uint;
      case DOUBLE -> Decls.Double;
      case STRING -> Decls.String;
      case NULL -> Decls.Null;
    };
  }

  private static void assertFailure(ThrowingRunnable invocation, String message) {
    ValueSignal failure = catchThrowableOfType(invocation::run, ValueSignal.class);
    assertThat(failure).isNotNull();
    assertThat(failure.value).isInstanceOf(Err.class).asString().contains(message);
  }

  private static void assertNativeParity(Env env, String expression, Object variables) {
    Val[] results = evaluateNativeAndEstablished(env, expression, variables);
    assertThat(results[0].getClass()).as(expression).isEqualTo(results[1].getClass());
    assertThat(results[0].toString()).as(expression).isEqualTo(results[1].toString());
  }

  private static Val[] evaluateNativeAndEstablished(Env env, String expression, Object variables) {
    Interpretable nativePlan = plan(env, expression);
    assertThat(root(nativePlan)).as(expression).isInstanceOf(NativeMapQuantifierFold.class);
    Interpretable establishedPlan = plan(env, expression, false);
    return new Val[] {
      nativePlan.eval(newActivation(variables)), establishedPlan.eval(newActivation(variables))
    };
  }

  private static Interpretable plan(
      Env env, String expression, InterpretableDecorator... decorators) {
    return plan(env, expression, true, decorators);
  }

  private static Interpretable plan(
      Env env, String expression, boolean nativeEnabled, InterpretableDecorator... decorators) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    return interpreter(env, dispatcher, nativeEnabled)
        .newInterpretable(astToCheckedExpr(compiled.getAst()), decorators);
  }

  private static Interpretable planWithReplacement(
      Env env, String expression, Overload replacement) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    Dispatcher standards = newDispatcher();
    standards.add(standardOverloads());
    Dispatcher dispatcher = extendDispatcher(standards);
    dispatcher.add(replacement);
    return interpreter(env, dispatcher).newInterpretable(astToCheckedExpr(compiled.getAst()));
  }

  private static Interpreter interpreter(Env env, Dispatcher dispatcher) {
    return interpreter(env, dispatcher, true);
  }

  private static Interpreter interpreter(Env env, Dispatcher dispatcher, boolean nativeEnabled) {
    TypeAdapter adapter = env.getTypeAdapter();
    AttributeFactory attributes =
        newAttributeFactory(defaultContainer, adapter, env.getTypeProvider());
    return newInterpreter(
        dispatcher, defaultContainer, env.getTypeProvider(), adapter, attributes, nativeEnabled);
  }

  private static Interpretable root(Interpretable plan) {
    return plan instanceof NativeIsland island ? island.root() : plan;
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static final class TestMapSource extends AbstractEval
      implements NativeMapSourceCapability {
    private final Function<Activation, Object> values;
    private final CheckedAggregateMaterializer materializer =
        new CheckedAggregateMaterializer(ADAPTER, Decls.newMapType(Decls.String, Decls.Int));

    private TestMapSource(Function<Activation, Object> values) {
      super(1L);
      this.values = values;
    }

    @Override
    public Val eval(Activation activation) {
      try {
        return materializeResolvedMap(evalRaw(activation));
      } catch (ValueSignal failure) {
        return failure.value;
      }
    }

    @Override
    public Object evalRaw(Activation activation) {
      return values.apply(activation);
    }

    @Override
    public Val materializeResolvedMap(Object value) {
      return materializer.materialize(value);
    }

    @Override
    public boolean exactMapSource() {
      return true;
    }
  }

  private static final class InstrumentedMap extends AbstractMap<String, Long> {
    private final List<Entry<String, Long>> entries;
    private final AtomicInteger entrySetCalls = new AtomicInteger();
    private final AtomicInteger sizeCalls = new AtomicInteger();
    private final AtomicInteger getKeyCalls = new AtomicInteger();
    private final AtomicInteger getValueCalls = new AtomicInteger();

    private InstrumentedMap(Map<String, Long> values) {
      entries = new ArrayList<>();
      values.forEach(
          (key, value) ->
              entries.add(
                  new SimpleImmutableEntry<>(key, value) {
                    @Override
                    public String getKey() {
                      getKeyCalls.incrementAndGet();
                      return super.getKey();
                    }

                    @Override
                    public Long getValue() {
                      getValueCalls.incrementAndGet();
                      return super.getValue();
                    }
                  }));
    }

    @Override
    public Set<Entry<String, Long>> entrySet() {
      entrySetCalls.incrementAndGet();
      return new AbstractSet<>() {
        @Override
        public Iterator<Entry<String, Long>> iterator() {
          return entries.iterator();
        }

        @Override
        public int size() {
          throw new AssertionError("entry set size must not be read");
        }
      };
    }

    @Override
    public int size() {
      sizeCalls.incrementAndGet();
      throw new AssertionError("map size must not be read");
    }
  }

  private enum FailurePoint {
    ENTRY_SET,
    ITERATOR,
    HAS_NEXT,
    NEXT,
    GET_KEY,
    GET_VALUE
  }

  private static final class FailingMap extends AbstractMap<String, Long> {
    private final FailurePoint point;

    private FailingMap(FailurePoint point) {
      this.point = point;
    }

    @Override
    public Set<Entry<String, Long>> entrySet() {
      fail(FailurePoint.ENTRY_SET);
      return new AbstractSet<>() {
        @Override
        public Iterator<Entry<String, Long>> iterator() {
          fail(FailurePoint.ITERATOR);
          return new Iterator<>() {
            private boolean available = true;

            @Override
            public boolean hasNext() {
              fail(FailurePoint.HAS_NEXT);
              return available;
            }

            @Override
            public Entry<String, Long> next() {
              fail(FailurePoint.NEXT);
              if (!available) {
                throw new NoSuchElementException();
              }
              available = false;
              return new SimpleImmutableEntry<>("key", 1L) {
                @Override
                public String getKey() {
                  fail(FailurePoint.GET_KEY);
                  return super.getKey();
                }

                @Override
                public Long getValue() {
                  fail(FailurePoint.GET_VALUE);
                  return super.getValue();
                }
              };
            }
          };
        }

        @Override
        public int size() {
          throw new AssertionError("entry set size must not be read");
        }
      };
    }

    private void fail(FailurePoint candidate) {
      if (point == candidate) {
        throw new IllegalStateException(candidate.name().toLowerCase());
      }
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
