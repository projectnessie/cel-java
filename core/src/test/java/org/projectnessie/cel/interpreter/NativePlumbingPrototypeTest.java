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

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.CEL.astToCheckedExpr;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.checker.Decls.Bool;
import static org.projectnessie.cel.checker.Decls.Int;
import static org.projectnessie.cel.checker.Decls.newFunction;
import static org.projectnessie.cel.checker.Decls.newListType;
import static org.projectnessie.cel.checker.Decls.newMapType;
import static org.projectnessie.cel.checker.Decls.newOverload;
import static org.projectnessie.cel.checker.Decls.newVar;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import com.google.api.expr.v1alpha1.Decl;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableCall;
import org.projectnessie.cel.interpreter.functions.Overload;

class NativePlumbingPrototypeTest {
  private static final Decl X = newVar("x", Int);
  private static final Decl Y = newVar("y", Int);
  private static final Decl B = newVar("b", Bool);
  private static final Decl C = newVar("c", Bool);
  private static final Decl S = newVar("s", org.projectnessie.cel.checker.Decls.String);

  @Test
  void composesIntNodesAndEqualityInsideOneRootIsland() {
    for (String expression : List.of("x", "s", "x + 1", "((x + 1) + 2) + 3", "(x + 1) == y")) {
      Plans plans = plans(expression, new Decl[] {X, Y, S});
      Activation activation = newActivation(Map.of("x", 40L, "y", 41L, "s", "value"));

      assertThat(plans.enabled()).isInstanceOf(NativeIsland.class);
      NativeIsland island = (NativeIsland) plans.enabled();
      Val expected = plans.established().eval(activation);
      assertThat(island.root().eval(activation)).isEqualTo(expected);
      assertThat(island.eval(activation)).isEqualTo(expected);
      assertThat(island.id()).isEqualTo(plans.established().id());
      assertThat(island.cost()).isEqualTo(estimateCost(plans.established()));

      if (island.root() instanceof NativeIntCapability intCapability) {
        assertThat(intCapability.evalInt(activation)).isEqualTo(expected.intValue());
      } else if (island.root() instanceof NativeBooleanCapability booleanCapability) {
        assertThat(booleanCapability.evalBoolean(activation)).isEqualTo(expected.booleanValue());
      } else {
        NativeStringCapability stringCapability = (NativeStringCapability) island.root();
        assertThat(stringCapability.evalString(activation)).isEqualTo(expected.value());
      }
    }
  }

  @Test
  void strictFallbackEvaluatesBothOperandsOnceWithoutReplay() {
    Plans plans = plans("x + y", new Decl[] {X, Y});
    Val left = newErr("left");
    Val right = unknownOf(22L);
    List<String> resolutions = new ArrayList<>();
    Activation activation =
        activation(
            name -> {
              resolutions.add(name);
              return name.equals("x") ? left : right;
            });

    Val established = plans.established().eval(activation);
    assertThat(resolutions).containsExactly("x", "y");
    resolutions.clear();

    Val integrated = plans.enabled().eval(activation);
    assertThat(resolutions).containsExactly("x", "y");
    assertThat(integrated).isSameAs(established).isSameAs(left);
  }

  @Test
  void strictFallbackPreservesBothSignalOrdersAndOverflow() {
    Plans addition = plans("x + y", new Decl[] {X, Y});
    for (Object[] values :
        List.of(
            new Object[] {unknownOf(31L), newErr("right")},
            new Object[] {newErr("left"), 1L},
            new Object[] {1L, unknownOf(32L)})) {
      List<String> resolutions = new ArrayList<>();
      Activation activation =
          activation(
              name -> {
                resolutions.add(name);
                return name.equals("x") ? values[0] : values[1];
              });
      assertEquivalent(
          addition.enabled().eval(activation),
          addition.established().eval(newActivation(Map.of("x", values[0], "y", values[1]))),
          List.of(values).toString());
      assertThat(resolutions).containsExactly("x", "y");
    }

    for (long[] values :
        List.of(
            new long[] {Long.MAX_VALUE, 1L},
            new long[] {Long.MIN_VALUE, -1L},
            new long[] {Long.MAX_VALUE, 0L})) {
      Activation activation = newActivation(Map.of("x", values[0], "y", values[1]));
      assertEquivalent(
          addition.enabled().eval(activation),
          addition.established().eval(activation),
          values[0] + ", " + values[1]);
    }
  }

  @Test
  void booleanEqualityPreservesCompatibilityAndBilateralEvaluation() {
    Plans equality = plans("b == c", new Decl[] {B, C});
    for (Object[] values :
        List.of(
            new Object[] {true, false},
            new Object[] {
              org.projectnessie.cel.common.types.BoolT.True,
              org.projectnessie.cel.common.types.BoolT.False
            },
            new Object[] {"wrong", true},
            new Object[] {newErr("left"), unknownOf(41L)},
            new Object[] {unknownOf(42L), newErr("right")})) {
      List<String> resolutions = new ArrayList<>();
      Activation activation =
          activation(
              name -> {
                resolutions.add(name);
                return name.equals("b") ? values[0] : values[1];
              });
      assertEquivalent(
          equality.enabled().eval(activation),
          equality.established().eval(newActivation(Map.of("b", values[0], "c", values[1]))),
          List.of(values).toString());
      assertThat(resolutions).containsExactly("b", "c");
    }

    Map<String, Object> nullValue = new HashMap<>();
    nullValue.put("b", null);
    nullValue.put("c", true);
    assertEquivalent(
        equality.enabled().eval(newActivation(nullValue)),
        equality.established().eval(newActivation(nullValue)),
        "null");
    assertEquivalent(
        equality.enabled().eval(newActivation(emptyMap())),
        equality.established().eval(newActivation(emptyMap())),
        "absent");
  }

  @Test
  void wrongAndCompatibilityValuesFollowEstablishedContinuation() {
    Plans plans = plans("x + 1", new Decl[] {X});
    List<Object> values = List.of(41L, intOf(41L), "wrong", newErr("carried"), unknownOf(17L));
    for (Object value : values) {
      Activation activation = newActivation(Map.of("x", value));
      assertEquivalent(
          plans.enabled().eval(activation),
          plans.established().eval(activation),
          String.valueOf(value));
    }

    Map<String, Object> nullValue = new HashMap<>();
    nullValue.put("x", null);
    assertEquivalent(
        plans.enabled().eval(newActivation(nullValue)),
        plans.established().eval(newActivation(nullValue)),
        "null");
    assertEquivalent(
        plans.enabled().eval(newActivation(emptyMap())),
        plans.established().eval(newActivation(emptyMap())),
        "absent");
  }

  @Test
  void mapSelectorAndConstantListIndexResolveTheirSourceOnce() {
    Plans selector =
        plans(
            "attrs.answer + 1",
            new Decl[] {
              newVar("attrs", newMapType(org.projectnessie.cel.checker.Decls.String, Int))
            });
    List<String> selectorResolutions = new ArrayList<>();
    Activation selectorActivation =
        activation(
            name -> {
              selectorResolutions.add(name);
              return Map.of("answer", 41L);
            });
    assertThat(selector.enabled().eval(selectorActivation)).isEqualTo(intOf(42L));
    assertThat(selectorResolutions).containsExactly("attrs");

    Plans index = plans("numbers[1] + 1", new Decl[] {newVar("numbers", newListType(Int))});
    assertThat(index.enabled()).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) index.enabled()).root()).isInstanceOf(NativeIntAdd.class);
    List<String> indexResolutions = new ArrayList<>();
    Activation indexActivation =
        activation(
            name -> {
              indexResolutions.add(name);
              return new long[] {10L, 41L};
            });
    assertThat(index.enabled().eval(indexActivation)).isEqualTo(intOf(42L));
    assertThat(indexResolutions).containsExactly("numbers");
  }

  @Test
  void mapSelectorPreservesEstablishedCompatibilityResultsAndSingleAccess() {
    Plans plans =
        plans(
            "attrs.answer + 1",
            new Decl[] {
              newVar("attrs", newMapType(org.projectnessie.cel.checker.Decls.String, Int))
            });
    Map<String, Object> nullValue = new HashMap<>();
    nullValue.put("answer", null);
    for (Object source :
        List.of(
            Map.of("answer", 41L),
            Map.of("answer", intOf(41L)),
            Map.of("answer", "wrong"),
            Map.of("answer", newErr("carried")),
            Map.of("answer", unknownOf(51L)),
            Map.of("other", 41L),
            nullValue,
            newErr("source"),
            unknownOf(52L),
            "wrong source")) {
      assertEquivalent(
          plans.enabled().eval(newActivation(Map.of("attrs", source))),
          plans.established().eval(newActivation(Map.of("attrs", source))),
          String.valueOf(source));
    }

    Map<String, Object> nullSource = new HashMap<>();
    nullSource.put("attrs", null);
    assertEquivalent(
        plans.enabled().eval(newActivation(nullSource)),
        plans.established().eval(newActivation(nullSource)),
        "null source");

    AtomicInteger gets = new AtomicInteger();
    Map<String, Object> countingMap =
        new HashMap<>() {
          @Override
          public Object get(Object key) {
            gets.incrementAndGet();
            return super.get(key);
          }
        };
    countingMap.put("answer", 41L);
    assertThat(plans.enabled().eval(newActivation(Map.of("attrs", countingMap))))
        .isEqualTo(intOf(42L));
    assertThat(gets).hasValue(1);
  }

  @Test
  void constantListIndexPreservesEstablishedCompatibilityResults() {
    Plans plans = plans("numbers[1] + 1", new Decl[] {newVar("numbers", newListType(Int))});
    for (Object source :
        List.of(
            new long[] {10L, 41L},
            new int[] {10, 41},
            List.of(10L, 41L),
            List.of(intOf(10L), intOf(41L)),
            List.of(10L, "wrong"),
            newErr("carried"),
            unknownOf(19L),
            "wrong container")) {
      assertEquivalent(
          plans.enabled().eval(newActivation(Map.of("numbers", source))),
          plans.established().eval(newActivation(Map.of("numbers", source))),
          source.getClass().getName());
    }

    Map<String, Object> nullSource = new HashMap<>();
    nullSource.put("numbers", null);
    assertEquivalent(
        plans.enabled().eval(newActivation(nullSource)),
        plans.established().eval(newActivation(nullSource)),
        "null source");

    Plans outOfRange = plans("numbers[2]", new Decl[] {newVar("numbers", newListType(Int))});
    Activation shortList = newActivation(Map.of("numbers", new long[] {10L, 41L}));
    assertEquivalent(
        outOfRange.enabled().eval(shortList),
        outOfRange.established().eval(shortList),
        "out of range");

    Activation throwing =
        activation(
            name -> {
              throw new IllegalStateException("source failure");
            });
    assertEquivalent(
        plans.enabled().eval(throwing), plans.established().eval(throwing), "source failure");

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    List<Long> throwingList =
        new AbstractList<>() {
          @Override
          public Long get(int index) {
            throw new IllegalStateException("element failure");
          }

          @Override
          public int size() {
            return 2;
          }
        };
    assertEquivalent(
        plans.enabled().eval(newActivation(Map.of("numbers", throwingList))),
        plans.established().eval(newActivation(Map.of("numbers", throwingList))),
        "element failure");
  }

  @Test
  void identifierAdapterExceptionsMatchEstablishedEvaluation() {
    StandardScalarTypeAdapter throwingAdapter =
        value -> {
          if (value instanceof String) {
            throw new IllegalStateException("adapter failure");
          }
          return org.projectnessie.cel.common.types.pb.DefaultTypeAdapter.Instance.nativeToValue(
              value);
        };
    Plans plans = plans("x + 1", new Decl[] {X}, throwingAdapter);
    Activation activation = newActivation(Map.of("x", "wrong"));

    assertThatThrownBy(() -> plans.established().eval(activation))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("adapter failure");
    assertThatThrownBy(() -> plans.enabled().eval(activation))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("adapter failure");
  }

  @Test
  void establishedOuterCallsReceiveMaximalChildIslands() {
    Decl opaque = newFunction("opaque", newOverload("opaque_int", List.of(Int), Int));
    Overload implementation = Overload.unary("opaque_int", value -> value);
    Plans plans = plans("opaque(x + 1)", new Decl[] {X, opaque}, implementation);

    assertThat(plans.enabled()).isInstanceOf(InterpretableCall.class);
    InterpretableCall outer = (InterpretableCall) plans.enabled();
    assertThat(outer.args()).singleElement().isInstanceOf(NativeIsland.class);
    assertThat(plans.enabled().eval(newActivation(Map.of("x", 41L)))).isEqualTo(intOf(42L));

    Decl opaque2 = newFunction("opaque2", newOverload("opaque2_int", List.of(Int, Int), Int));
    Overload binary = Overload.binary("opaque2_int", (left, right) -> left);
    Plans siblings = plans("opaque2(x + 1, y + 1)", new Decl[] {X, Y, opaque2}, binary);
    assertThat(((InterpretableCall) siblings.enabled()).args())
        .allSatisfy(argument -> assertThat(argument).isInstanceOf(NativeIsland.class));
  }

  @Test
  void establishedAggregateParentsReceiveMaximalChildIslands() {
    Plans list = plans("[x + 1]", new Decl[] {X});
    assertThat(list.enabled()).isInstanceOf(NativeIntListLiteral.class);
    NativeIntListLiteral literal = (NativeIntListLiteral) list.enabled();
    assertThat(literal.elems).singleElement().isInstanceOf(NativeIsland.class);
    assertThat(literal.nativeElements)
        .singleElement()
        .isSameAs(((NativeIsland) literal.elems[0]).root());

    Plans map = plans("{'answer': x + 1}", new Decl[] {X});
    assertThat(map.enabled()).isInstanceOf(EvalMap.class);
    assertThat(((EvalMap) map.enabled()).vals).singleElement().isInstanceOf(NativeIsland.class);

    Plans fold =
        plans(
            "numbers.exists(n, string(n) == string(x))",
            new Decl[] {X, newVar("numbers", newListType(Int))});
    assertThat(fold.enabled()).isInstanceOf(EvalFold.class);
    EvalFold foldRoot = (EvalFold) fold.enabled();
    assertThat(foldRoot.iterRange).isInstanceOf(NativeRawIdent.class);
    InterpretableCall condition = (InterpretableCall) foldRoot.cond;
    assertThat(condition.args()).singleElement().isInstanceOf(NativeIsland.class);
    assertThat(fold.enabled().eval(newActivation(Map.of("x", 42L, "numbers", List.of(42L)))))
        .isEqualTo(org.projectnessie.cel.common.types.BoolT.True);
    assertThat(foldRoot.cost()).isEqualTo(estimateCost(fold.established()));
  }

  @Test
  void listFoldConsumersShareOnePlannedSourceAndChildGraph() {
    Plans plans =
        plans(
            "size(numbers.map(value, value > 0, value + x))",
            new Decl[] {X, newVar("numbers", newListType(Int))});

    assertThat(plans.enabled()).isInstanceOf(NativeIsland.class);
    Interpretable root = ((NativeIsland) plans.enabled()).root();
    assertThat(root).isInstanceOf(NativeListFoldSize.class);
    NativeScalarListFold source = (NativeScalarListFold) ((NativeListFoldSize) root).source;

    assertThat(source.iterRange).isSameAs(source.range);
    assertThat(source.filter).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) source.filter).root()).isSameAs(source.predicate);
    assertThat(source.transform).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) source.transform).root()).isSameAs(source.nativeTransform);

    Activation activation = newActivation(Map.of("x", 1L, "numbers", new long[] {-1L, 1L, 2L}));
    assertThat(plans.enabled().eval(activation)).isEqualTo(intOf(2L));
    assertThat(plans.enabled().eval(activation)).isEqualTo(plans.established().eval(activation));
  }

  @Test
  void mappedStringMembershipSharesItsSinglePlannedListFoldSource() {
    Decl wordTarget = newVar("wordTarget", org.projectnessie.cel.checker.Decls.String);
    Decl words = newVar("words", newListType(org.projectnessie.cel.checker.Decls.String));
    Plans plans =
        plans(
            "wordTarget in words.map(value, value != 'skip', value + '!')",
            new Decl[] {wordTarget, words});

    assertThat(plans.enabled()).isInstanceOf(NativeIsland.class);
    Interpretable root = ((NativeIsland) plans.enabled()).root();
    assertThat(root).isInstanceOf(NativeStringListFoldMembership.class);
    NativeStringListFoldMembership membership = (NativeStringListFoldMembership) root;
    NativeScalarListFold source = (NativeScalarListFold) membership.source;

    assertThat(membership.rhs).isSameAs(source);
    assertThat(source.filter).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) source.filter).root()).isSameAs(source.predicate);
    assertThat(source.transform).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) source.transform).root()).isSameAs(source.nativeTransform);

    Activation activation =
        newActivation(Map.of("wordTarget", "cel!", "words", new String[] {"other", "skip", "cel"}));
    assertThat(plans.enabled().eval(activation))
        .isEqualTo(org.projectnessie.cel.common.types.BoolT.True);
    assertThat(plans.enabled().eval(activation)).isEqualTo(plans.established().eval(activation));
  }

  @Test
  void mappedIntegerQuantifierSharesItsSinglePlannedListFoldSource() {
    Decl numbers = newVar("numbers", newListType(Int));
    Plans plans =
        plans(
            "numbers.map(value, value > 0, value + 1).exists(mapped, mapped == 3)",
            new Decl[] {numbers});

    assertThat(plans.enabled()).isInstanceOf(NativeIsland.class);
    Interpretable root = ((NativeIsland) plans.enabled()).root();
    assertThat(root).isInstanceOf(NativeIntMappedQuantifierFold.class);
    NativeIntMappedLoopFold aggregate = (NativeIntMappedLoopFold) root;
    NativeScalarListFold source = (NativeScalarListFold) aggregate.source;

    assertThat(aggregate.iterRange).isSameAs(source);
    assertThat(source.filter).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) source.filter).root()).isSameAs(source.predicate);
    assertThat(source.transform).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) source.transform).root()).isSameAs(source.nativeTransform);

    Activation activation = newActivation(Map.of("numbers", new long[] {-1L, 1L, 2L}));
    assertThat(plans.enabled().eval(activation))
        .isEqualTo(org.projectnessie.cel.common.types.BoolT.True);
    assertThat(plans.enabled().eval(activation)).isEqualTo(plans.established().eval(activation));
  }

  @Test
  void islandAdaptsOneSuccessAndDoesNotReadaptSignals() {
    CountingAdapter adapter = new CountingAdapter();
    Plans plans = plans("x + 1", new Decl[] {X}, adapter);
    NativeIsland island = (NativeIsland) plans.enabled();

    adapter.reset();
    assertThat(((NativeIntCapability) island.root()).evalInt(newActivation(Map.of("x", 41L))))
        .isEqualTo(42L);
    assertThat(adapter.count()).isZero();

    adapter.reset();
    assertThat(island.eval(newActivation(Map.of("x", 41L)))).isEqualTo(intOf(42L));
    assertThat(adapter.count()).isEqualTo(1);

    adapter.reset();
    assertThat(island.eval(newActivation(Map.of("x", "wrong"))).type().typeName())
        .isEqualTo("error");
    assertThat(adapter.count()).isEqualTo(1);
  }

  @Test
  void disabledDecoratedAndShortCircuitedPlansStayOnEstablishedPath() {
    Plans plans = plans("false && (x + 1 == y)", new Decl[] {X, Y});
    List<String> resolutions = new ArrayList<>();
    assertThat(
            plans
                .enabled()
                .eval(
                    activation(
                        name -> {
                          resolutions.add(name);
                          return 41L;
                        })))
        .isEqualTo(org.projectnessie.cel.common.types.BoolT.False);
    assertThat(resolutions).isEmpty();

    assertThat(plans.established()).isNotInstanceOf(NativeIsland.class);
    Interpretable decorated =
        plans
            .enabledInterpreter()
            .newInterpretable(
                plans.checked(),
                plan -> {
                  assertThat(plan).isNotInstanceOf(NativeIsland.class);
                  assertThat(NativeIsland.supports(plan)).isFalse();
                  return plan;
                });
    assertThat(decorated).isNotInstanceOf(NativeIsland.class);
  }

  @Test
  void immutableIntegratedPlanCanBeReusedConcurrently() throws Exception {
    Plans plans = plans("((x + 1) + 2) == y", new Decl[] {X, Y});
    @SuppressWarnings("resource")
    var executor = Executors.newFixedThreadPool(4);
    try {
      var futures =
          IntStream.range(0, 200)
              .mapToObj(
                  value ->
                      executor.submit(
                          () ->
                              plans
                                  .enabled()
                                  .eval(newActivation(Map.of("x", (long) value, "y", value + 3L)))))
              .toList();
      for (var future : futures) {
        assertThat(future.get(10, TimeUnit.SECONDS).booleanValue()).isTrue();
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  private static Plans plans(String expression, Decl[] declarations, Overload... custom) {
    return plans(expression, declarations, null, custom);
  }

  private static Plans plans(
      String expression,
      Decl[] declarations,
      StandardScalarTypeAdapter adapter,
      Overload... custom) {
    Env env = newEnv(declarations(declarations));
    AstIssuesTuple result = env.compile(expression);
    assertThat(result.hasIssues()).withFailMessage(result.getIssues()::toString).isFalse();

    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    dispatcher.add(custom);
    var effectiveAdapter = adapter != null ? adapter : env.getTypeAdapter();
    AttributeFactory attributes =
        newAttributeFactory(defaultContainer, effectiveAdapter, env.getTypeProvider());
    Interpreter establishedInterpreter =
        newInterpreter(
            dispatcher,
            defaultContainer,
            env.getTypeProvider(),
            effectiveAdapter,
            attributes,
            false);
    Interpreter enabledInterpreter =
        newInterpreter(
            dispatcher,
            defaultContainer,
            env.getTypeProvider(),
            effectiveAdapter,
            attributes,
            true);
    var checked = astToCheckedExpr(result.getAst());
    return new Plans(
        establishedInterpreter.newInterpretable(checked),
        ((ExprInterpreter) enabledInterpreter).checkedPlanner(checked).plan(checked.getExpr()),
        enabledInterpreter,
        checked);
  }

  @SuppressWarnings("removal")
  private static Activation activation(java.util.function.Function<String, Object> values) {
    return new Activation() {
      @Override
      public Object resolve(String name) {
        return values.apply(name);
      }

      @Override
      public ResolvedValue resolveName(String name) {
        return ResolvedValue.mapTo(resolve(name));
      }
    };
  }

  private static void assertEquivalent(Val actual, Val expected, String description) {
    assertThat(actual.getClass()).as(description).isEqualTo(expected.getClass());
    assertThat(actual.type().typeEnum()).as(description).isEqualTo(expected.type().typeEnum());
    assertThat(actual.value()).as(description).isEqualTo(expected.value());
    assertThat(actual.toString()).as(description).isEqualTo(expected.toString());
  }

  private static final class CountingAdapter implements StandardScalarTypeAdapter {
    private final AtomicInteger count = new AtomicInteger();

    @Override
    public Val nativeToValue(Object value) {
      count.incrementAndGet();
      return org.projectnessie.cel.common.types.pb.DefaultTypeAdapter.Instance.nativeToValue(value);
    }

    int count() {
      return count.get();
    }

    void reset() {
      count.set(0);
    }
  }

  private record Plans(
      Interpretable established,
      Interpretable enabled,
      Interpreter enabledInterpreter,
      com.google.api.expr.v1alpha1.CheckedExpr checked) {}
}
