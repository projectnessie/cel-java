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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.CEL.astToCheckedExpr;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.NullT.NullValue;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import com.google.api.expr.v1alpha1.Type;
import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.MapT;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.interpreter.functions.Overload;

class NativeMapEqualityTest {
  private static final ExactAdapter EXACT = new ExactAdapter();

  private final Env env =
      newEnv(
          customTypeAdapter(EXACT),
          declarations(
              Decls.newVar("left", Decls.newMapType(Decls.String, Decls.Int)),
              Decls.newVar("right", Decls.newMapType(Decls.String, Decls.Int)),
              Decls.newVar("dynLeft", Decls.newMapType(Decls.String, Decls.Dyn)),
              Decls.newVar("dynRight", Decls.newMapType(Decls.String, Decls.Dyn)),
              Decls.newVar(
                  "listLeft", Decls.newMapType(Decls.String, Decls.newListType(Decls.Int))),
              Decls.newVar(
                  "listRight", Decls.newMapType(Decls.String, Decls.newListType(Decls.Int))),
              Decls.newVar("uintLeft", Decls.newMapType(Decls.Uint, Decls.Int)),
              Decls.newVar("uintRight", Decls.newMapType(Decls.Uint, Decls.Int)),
              Decls.newVar("boolLeft", Decls.newMapType(Decls.Bool, Decls.Int)),
              Decls.newVar("boolRight", Decls.newMapType(Decls.Bool, Decls.Int)),
              Decls.newVar("intLeft", Decls.newMapType(Decls.Int, Decls.Int)),
              Decls.newVar("intRight", Decls.newMapType(Decls.Int, Decls.Int)),
              Decls.newVar("nullLeft", Decls.newMapType(Decls.String, Decls.Null)),
              Decls.newVar("nullRight", Decls.newMapType(Decls.String, Decls.Null)),
              Decls.newVar(
                  "wrapperLeft", Decls.newMapType(Decls.String, Decls.newWrapperType(Decls.Uint))),
              Decls.newVar(
                  "wrapperRight", Decls.newMapType(Decls.String, Decls.newWrapperType(Decls.Uint))),
              Decls.newVar("bytesLeft", Decls.newMapType(Decls.String, Decls.Bytes)),
              Decls.newVar("bytesRight", Decls.newMapType(Decls.String, Decls.Bytes)),
              Decls.newVar(
                  "messageLeft",
                  Decls.newMapType(Decls.String, Decls.newObjectType("google.protobuf.Struct"))),
              Decls.newVar(
                  "messageRight",
                  Decls.newMapType(Decls.String, Decls.newObjectType("google.protobuf.Struct"))),
              Decls.newVar(
                  "nestedMapLeft",
                  Decls.newMapType(Decls.String, Decls.newMapType(Decls.String, Decls.Int))),
              Decls.newVar(
                  "nestedMapRight",
                  Decls.newMapType(Decls.String, Decls.newMapType(Decls.String, Decls.Int))),
              Decls.newVar("strings", Decls.newMapType(Decls.String, Decls.String))));

  @Test
  void selectedCheckedValuesMatchEstablishedCheckedChildMaterialization() {
    List<CheckedCase> cases = new ArrayList<>();
    cases.add(new CheckedCase(Decls.Bool, true));
    cases.add(new CheckedCase(Decls.Int, (byte) -7));
    cases.add(new CheckedCase(Decls.Int, (short) -8));
    cases.add(new CheckedCase(Decls.Int, -9));
    cases.add(new CheckedCase(Decls.Int, -10L));
    cases.add(new CheckedCase(Decls.Uint, Long.MIN_VALUE));
    cases.add(new CheckedCase(Decls.Uint, ULong.valueOf(-1L)));
    cases.add(new CheckedCase(Decls.Double, 1.25f));
    cases.add(new CheckedCase(Decls.Double, -0.0d));
    cases.add(new CheckedCase(Decls.String, "value"));
    cases.add(new CheckedCase(Decls.Bytes, new byte[] {1, 2}));
    cases.add(new CheckedCase(Decls.Bytes, ByteString.copyFromUtf8("bytes")));
    cases.add(new CheckedCase(Decls.Null, null));
    cases.add(new CheckedCase(Decls.Dyn, 11L));
    cases.add(new CheckedCase(Decls.newWrapperType(Decls.Uint), Long.MIN_VALUE));
    cases.add(new CheckedCase(Decls.newWrapperType(Decls.String), null));
    cases.add(new CheckedCase(Decls.newListType(Decls.Int), List.of(1L, 2L)));
    cases.add(new CheckedCase(Decls.newMapType(Decls.String, Decls.Int), Map.of("one", 1L)));
    cases.add(
        new CheckedCase(
            Decls.newObjectType("google.protobuf.Struct"), Struct.getDefaultInstance()));
    cases.add(new CheckedCase(Decls.Timestamp, Timestamp.getDefaultInstance()));

    for (CheckedCase checkedCase : cases) {
      Val selected =
          new CheckedValueMaterializer(EXACT, checkedCase.type).materialize(checkedCase.value);
      Val established = establishedSelected(checkedCase.type, checkedCase.value);
      assertEquivalentVal(selected, established, checkedCase.type.toString());
    }
  }

  @Test
  void selectedCheckedValuesRejectTheSameInvalidRepresentations() {
    for (CheckedCase checkedCase :
        List.of(
            new CheckedCase(Decls.Bool, "true"),
            new CheckedCase(Decls.Int, 1.0d),
            new CheckedCase(Decls.Uint, 1),
            new CheckedCase(Decls.Double, 1L),
            new CheckedCase(Decls.String, new StringBuilder("text")),
            new CheckedCase(Decls.Bytes, "bytes"),
            new CheckedCase(Decls.Null, "null"),
            new CheckedCase(Decls.Dyn, intOf(1L)))) {
      Val selected =
          new CheckedValueMaterializer(EXACT, checkedCase.type).materialize(checkedCase.value);
      Val established = establishedSelected(checkedCase.type, checkedCase.value);
      assertThat(selected).as(checkedCase.type.toString()).isInstanceOf(Err.class);
      assertThat(established).as(checkedCase.type.toString()).isInstanceOf(Err.class);
    }
  }

  @Test
  void mapValueEqualityRetainsCelNumericAndExceptionalSemantics() {
    Val leftError = newErr("left failed");
    Val rightError = newErr("right failed");

    assertThat(NativeMapValueEquality.equal(intOf(1L), doubleOf(1.0d))).isSameAs(True);
    assertThat(NativeMapValueEquality.equal(uintOf(1L), doubleOf(1.0d))).isSameAs(True);
    assertThat(NativeMapValueEquality.equal(doubleOf(Double.NaN), doubleOf(Double.NaN)))
        .isSameAs(False);
    assertThat(NativeMapValueEquality.equal(doubleOf(-0.0d), doubleOf(0.0d))).isSameAs(True);
    assertThat(NativeMapValueEquality.equal(intOf(1L), stringOf("1"))).isSameAs(False);
    assertThat(NativeMapValueEquality.equal(NullValue, NullValue)).isSameAs(True);
    assertThat(
            NativeMapValueEquality.equal(
                new CheckedValueMaterializer(EXACT, Decls.Bytes).materialize(new byte[] {1, 2}),
                new CheckedValueMaterializer(EXACT, Decls.Bytes)
                    .materialize(ByteString.copyFrom(new byte[] {1, 2}))))
        .isSameAs(True);
    assertThat(NativeMapValueEquality.equal(leftError, rightError)).isSameAs(leftError);
    assertThat(NativeMapValueEquality.equal(intOf(1L), rightError)).isSameAs(rightError);
    assertThat(NativeMapValueEquality.equal(unknownOf(1L), unknownOf(2L))).isSameAs(True);
  }

  @Test
  void mapValueEqualityRetainsNestedListAndMapSemantics() {
    Val listOne = EXACT.nativeAggregateToValue(List.of(1L, 2L), Decls.newListType(Decls.Int));
    Val listOneCopy = EXACT.nativeAggregateToValue(List.of(1L, 2L), Decls.newListType(Decls.Int));
    Val listTwo = EXACT.nativeAggregateToValue(List.of(1L, 3L), Decls.newListType(Decls.Int));
    Type mapType = Decls.newMapType(Decls.String, Decls.newListType(Decls.Int));
    Val mapOne = EXACT.nativeAggregateToValue(Map.of("values", List.of(1L, 2L)), mapType);
    Val mapOneCopy = EXACT.nativeAggregateToValue(Map.of("values", List.of(1L, 2L)), mapType);
    Val mapTwo = EXACT.nativeAggregateToValue(Map.of("values", List.of(1L, 3L)), mapType);

    assertThat(NativeMapValueEquality.equal(listOne, listOneCopy)).isSameAs(True);
    assertThat(NativeMapValueEquality.equal(listOne, listTwo)).isSameAs(False);
    assertThat(NativeMapValueEquality.equal(mapOne, mapOneCopy)).isSameAs(True);
    assertThat(NativeMapValueEquality.equal(mapOne, mapTwo)).isSameAs(False);
  }

  @Test
  void exactUintLookupSupportsBothRepresentationsHighBitsAndPresentNull() {
    Map<Object, Object> longs = new LinkedHashMap<>();
    longs.put(-1L, "long");
    longs.put(Long.MIN_VALUE, null);
    Map<Object, Object> unsigned = new LinkedHashMap<>();
    unsigned.put(ULong.valueOf(-1L), "unsigned");
    unsigned.put(ULong.valueOf(Long.MIN_VALUE), null);

    assertThat(NativeMapSources.exactUintLookup(longs, -1L)).isEqualTo("long");
    assertThat(NativeMapSources.exactUintLookup(unsigned, -1L)).isEqualTo("unsigned");
    assertThat(NativeMapSources.exactUintLookup(longs, Long.MIN_VALUE)).isNull();
    assertThat(NativeMapSources.exactUintLookup(unsigned, Long.MIN_VALUE)).isNull();
    assertThat(NativeMapSources.exactUintLookup(longs, 1L)).isSameAs(NativeMapSources.ABSENT);
  }

  @Test
  void exactUintLookupRejectsDuplicatesWithoutMisreadingSortedAliases() {
    Map<Object, Object> duplicate = new LinkedHashMap<>();
    duplicate.put(-1L, "long");
    duplicate.put(ULong.valueOf(-1L), "unsigned");

    assertThatThrownBy(() -> NativeMapSources.exactUintLookup(duplicate, -1L))
        .isInstanceOf(ValueSignal.class)
        .satisfies(
            failure ->
                assertThat(((ValueSignal) failure).value).hasToString("Failed with repeated key"));

    TreeMap<Number, Object> alias =
        new TreeMap<>((left, right) -> Long.compareUnsigned(left.longValue(), right.longValue()));
    alias.put(-1L, "same entry");
    assertThat(NativeMapSources.exactUintLookup(alias, -1L)).isEqualTo("same entry");
  }

  @Test
  void exactUintLookupTreatsIncompatibleSpeculativeProbesAsMisses() {
    TreeMap<Long, Object> oneCompatible = new TreeMap<>();
    oneCompatible.put(-1L, "long");
    assertThat(NativeMapSources.exactUintLookup(oneCompatible, -1L)).isEqualTo("long");

    TreeMap<String, Object> neitherCompatible = new TreeMap<>();
    neitherCompatible.put("key", "value");
    assertThat(NativeMapSources.exactUintLookup(neitherCompatible, -1L))
        .isSameAs(NativeMapSources.ABSENT);
  }

  @Test
  void strictOperandResolutionRunsBothOnceAndKeepsLeftFailurePrecedence() {
    List<String> resolutions = new ArrayList<>();
    Val leftFailure = newErr("left failed");
    NativeExactMapEqualityPlan plan =
        equalityPlan(
            new RawMapSource(
                ignored -> {
                  resolutions.add("left");
                  return leftFailure;
                }),
            new RawMapSource(
                ignored -> {
                  resolutions.add("right");
                  throw new IllegalStateException("right failed");
                }),
            Decls.Int);

    assertThatThrownBy(() -> plan.eval(emptyActivation()))
        .isInstanceOf(ValueSignal.class)
        .satisfies(failure -> assertThat(((ValueSignal) failure).value).isSameAs(leftFailure));
    assertThat(resolutions).containsExactly("left", "right");
  }

  @Test
  void strictOperandResolutionConvertsRuntimeFailureAndStillRunsRight() {
    AtomicInteger rightResolutions = new AtomicInteger();
    NativeExactMapEqualityPlan plan =
        equalityPlan(
            new RawMapSource(
                ignored -> {
                  throw new IllegalArgumentException("left exploded");
                }),
            new RawMapSource(
                ignored -> {
                  rightResolutions.incrementAndGet();
                  return Map.of();
                }),
            Decls.Int);

    assertThatThrownBy(() -> plan.eval(emptyActivation()))
        .isInstanceOf(ValueSignal.class)
        .satisfies(
            failure ->
                assertThat(((ValueSignal) failure).value)
                    .hasToString("java.lang.IllegalArgumentException: left exploded"));
    assertThat(rightResolutions).hasValue(1);
  }

  @Test
  void equalityReadsBothSizesOnceAndKeepsLeftSizeFailurePrecedence() {
    CountingSizeMap left = CountingSizeMap.throwing("left size");
    CountingSizeMap right = CountingSizeMap.throwing("right size");
    NativeExactMapEqualityPlan plan =
        equalityPlan(
            new RawMapSource(ignored -> left), new RawMapSource(ignored -> right), Decls.Int);

    assertThatThrownBy(() -> plan.eval(emptyActivation()))
        .isInstanceOf(ValueSignal.class)
        .satisfies(
            failure ->
                assertThat(((ValueSignal) failure).value)
                    .hasToString("java.lang.IllegalStateException: left size"));
    assertThat(left.sizeCalls).hasValue(1);
    assertThat(right.sizeCalls).hasValue(1);
  }

  @Test
  void sizeMismatchDoesNotCreateAnIteratorOrInspectEntries() {
    Map<Object, Object> left =
        new AbstractMap<>() {
          @Override
          public int size() {
            return 1;
          }

          @Override
          public Set<Entry<Object, Object>> entrySet() {
            throw new AssertionError("entry traversal must not start");
          }
        };
    NativeExactMapEqualityPlan plan =
        equalityPlan(
            new RawMapSource(ignored -> left),
            new RawMapSource(ignored -> Collections.emptyMap()),
            Decls.Int);

    assertThat(plan.eval(emptyActivation())).isFalse();
  }

  @Test
  void missingRightKeyPrecedesLeftValueAccess() {
    AtomicInteger valueReads = new AtomicInteger();
    Map<Object, Object> left = singleEntryMap("key", valueReads, true);
    NativeExactMapEqualityPlan plan =
        equalityPlan(
            new RawMapSource(ignored -> left),
            new RawMapSource(ignored -> Map.of("other", 1L)),
            Decls.Int);

    assertThat(plan.eval(emptyActivation())).isFalse();
    assertThat(valueReads).hasValue(0);
  }

  @Test
  void visitedHostTraversalAndLookupFailuresBecomeCelErrors() {
    Map<Object, Object> traversalFailure =
        new AbstractMap<>() {
          @Override
          public int size() {
            return 1;
          }

          @Override
          public Set<Entry<Object, Object>> entrySet() {
            throw new IllegalStateException("entry traversal failed");
          }
        };
    NativeExactMapEqualityPlan traversalPlan =
        equalityPlan(
            new RawMapSource(ignored -> traversalFailure),
            new RawMapSource(ignored -> Map.of("key", 1L)),
            Decls.Int);
    assertThatThrownBy(() -> traversalPlan.eval(emptyActivation()))
        .isInstanceOf(ValueSignal.class)
        .satisfies(
            failure ->
                assertThat(((ValueSignal) failure).value)
                    .hasToString("java.lang.IllegalStateException: entry traversal failed"));

    Map<Object, Object> lookupFailure =
        new AbstractMap<>() {
          @Override
          public int size() {
            return 1;
          }

          @Override
          public Object get(Object key) {
            throw new IllegalStateException("right lookup failed");
          }

          @Override
          public Set<Entry<Object, Object>> entrySet() {
            return Collections.emptySet();
          }
        };
    NativeExactMapEqualityPlan lookupPlan =
        equalityPlan(
            new RawMapSource(ignored -> Map.of("key", 1L)),
            new RawMapSource(ignored -> lookupFailure),
            Decls.Int);
    assertThatThrownBy(() -> lookupPlan.eval(emptyActivation()))
        .isInstanceOf(ValueSignal.class)
        .satisfies(
            failure ->
                assertThat(((ValueSignal) failure).value)
                    .hasToString("java.lang.IllegalStateException: right lookup failed"));
  }

  @Test
  void presentKeyMaterializesLeftThenRightAndReturnsTheLeftFailure() {
    List<String> conversions = new ArrayList<>();
    Marker leftMarker = new Marker("left");
    Marker rightMarker = new Marker("right");
    ExactAdapter recording =
        new ExactAdapter(
            value -> {
              if (value instanceof Marker marker) {
                conversions.add(marker.name);
                return newErr(marker.name + " value failed");
              }
              return DefaultTypeAdapter.Instance.nativeToValue(value);
            });
    NativeExactMapEqualityPlan plan =
        equalityPlan(
            new RawMapSource(ignored -> Map.of("key", leftMarker)),
            new RawMapSource(ignored -> Map.of("key", rightMarker)),
            Decls.Dyn,
            recording);

    assertThatThrownBy(() -> plan.eval(emptyActivation()))
        .isInstanceOf(ValueSignal.class)
        .satisfies(
            failure -> assertThat(((ValueSignal) failure).value).hasToString("left value failed"));
    assertThat(conversions).containsExactly("left", "right");
  }

  @Test
  void programsSpecializeOnlyTheExactStandardEligibleShape() {
    Interpretable enabled = plan("left == right", true, EXACT);
    assertThat(enabled).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) enabled).root()).isInstanceOf(NativeExactMapEquality.class);

    Interpretable inequality = plan("left != right", true, EXACT);
    assertThat(inequality).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) inequality).root()).isInstanceOf(NativeExactMapInequality.class);

    assertThat(plan("left == right", false, EXACT)).isExactlyInstanceOf(EvalEq.class);
    assertThat(plan("left == right", true, DefaultTypeAdapter.Instance))
        .isExactlyInstanceOf(EvalEq.class);
    assertThat(plan("left == strings", true, EXACT)).isExactlyInstanceOf(EvalEq.class);
    assertThat(plan("left == {'one': 1}", true, EXACT)).isExactlyInstanceOf(EvalEq.class);
    assertThat(plan("{'one': 1} == right", true, EXACT)).isExactlyInstanceOf(EvalEq.class);

    Interpretable optimized = plan("left == right", true, EXACT, Interpreter.optimize());
    assertThat(optimized).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) optimized).root()).isInstanceOf(NativeExactMapEquality.class);

    Interpretable decorated = plan("left == right", true, EXACT, node -> node);
    assertThat(decorated).isExactlyInstanceOf(EvalEq.class);

    Overload replacement = Overload.binary(Overloads.Equals, (left, right) -> False);
    assertThat(plan("left == right", true, EXACT, replacement)).isExactlyInstanceOf(EvalEq.class);
  }

  @Test
  void nativeAndDisabledProgramsAgreeForRepresentativeMapValues() {
    Map<String, Object> activation = new LinkedHashMap<>();
    activation.put("left", Map.of("one", 1L, "two", 2L));
    activation.put("right", Map.of("one", 1L, "two", 2L));
    activation.put("dynLeft", Map.of("number", 1L, "zero", -0.0d));
    activation.put("dynRight", Map.of("number", 1.0d, "zero", 0.0d));
    activation.put("listLeft", Map.of("values", List.of(1L, 2L)));
    activation.put("listRight", Map.of("values", List.of(1L, 2L)));
    activation.put("uintLeft", Map.of(ULong.valueOf(-1L), 1L));
    activation.put("uintRight", Map.of(-1L, 1L));

    for (String expression :
        List.of(
            "left == right",
            "left != right",
            "dynLeft == dynRight",
            "listLeft == listRight",
            "uintLeft == uintRight")) {
      assertProgramParity(expression, activation);
    }

    activation.put("right", Map.of("one", 1L, "two", 3L));
    assertProgramParity("left == right", activation);
    activation.put("right", Map.of("one", 1L, "missing", 2L));
    assertProgramParity("left == right", activation);
  }

  @Test
  void plannedProgramsCoverKeyRepresentationsAndSelectedCheckedValueKinds() {
    Map<String, Object> nullLeft = new LinkedHashMap<>();
    nullLeft.put("key", null);
    Map<String, Object> nullRight = new LinkedHashMap<>();
    nullRight.put("key", null);

    Map<Object, Object> intLeft = new LinkedHashMap<>();
    intLeft.put((byte) 1, 10L);
    intLeft.put((short) 2, 20L);
    Map<Object, Object> intRight = new LinkedHashMap<>();
    intRight.put(1L, 10L);
    intRight.put(2, 20L);

    Struct message =
        Struct.newBuilder()
            .putFields("field", com.google.protobuf.Value.getDefaultInstance())
            .build();
    Map<String, Object> activation = new LinkedHashMap<>();
    activation.put("boolLeft", Map.of(false, 1L, true, 2L));
    activation.put("boolRight", Map.of(false, 1L, true, 2L));
    activation.put("intLeft", intLeft);
    activation.put("intRight", intRight);
    activation.put("nullLeft", nullLeft);
    activation.put("nullRight", nullRight);
    activation.put("wrapperLeft", Map.of("key", Long.MIN_VALUE));
    activation.put("wrapperRight", Map.of("key", ULong.valueOf(Long.MIN_VALUE)));
    activation.put("bytesLeft", Map.of("key", new byte[] {1, 2, 3}));
    activation.put("bytesRight", Map.of("key", ByteString.copyFrom(new byte[] {1, 2, 3})));
    activation.put("messageLeft", Map.of("key", message));
    activation.put("messageRight", Map.of("key", message));
    activation.put("nestedMapLeft", Map.of("key", Map.of("nested", 1L)));
    activation.put("nestedMapRight", Map.of("key", Map.of("nested", 1L)));

    for (String expression :
        List.of(
            "boolLeft == boolRight",
            "intLeft == intRight",
            "intRight == intLeft",
            "nullLeft == nullRight",
            "wrapperLeft == wrapperRight",
            "bytesLeft == bytesRight",
            "bytesRight == bytesLeft",
            "messageLeft == messageRight",
            "nestedMapLeft == nestedMapRight")) {
      Interpretable planned = plan(expression, true, EXACT);
      assertThat(planned).as(expression).isInstanceOf(NativeIsland.class);
      assertThat(((NativeIsland) planned).root())
          .as(expression)
          .isInstanceOf(NativeExactMapEquality.class);
      assertProgramParity(expression, activation);
      assertThat(program(expression, false).eval(activation).getVal())
          .as(expression)
          .isSameAs(True);
    }

    Map<String, Object> unequalBytes = new LinkedHashMap<>(activation);
    unequalBytes.put("bytesRight", Map.of("key", ByteString.copyFrom(new byte[] {1, 2, 4})));
    assertProgramParity("bytesLeft == bytesRight", unequalBytes);
    assertThat(program("bytesLeft == bytesRight", false).eval(unequalBytes).getVal())
        .isSameAs(False);

    Map<String, Object> unequalMessage = new LinkedHashMap<>(activation);
    unequalMessage.put("messageRight", Map.of("key", Struct.getDefaultInstance()));
    assertProgramParity("messageLeft == messageRight", unequalMessage);
    assertThat(program("messageLeft == messageRight", false).eval(unequalMessage).getVal())
        .isSameAs(False);

    Map<String, Object> unequalNestedMap = new LinkedHashMap<>(activation);
    unequalNestedMap.put("nestedMapRight", Map.of("key", Map.of("nested", 2L)));
    assertProgramParity("nestedMapLeft == nestedMapRight", unequalNestedMap);
    assertThat(program("nestedMapLeft == nestedMapRight", false).eval(unequalNestedMap).getVal())
        .isSameAs(False);
  }

  @Test
  void plannedEqualityPropagatesRightSourceErrorsAndUnknowns() {
    for (Val rightFailure : List.of(newErr("right failed"), unknownOf(42L))) {
      ActivationFunction activation =
          name -> {
            if (name.equals("left")) {
              return Map.of("key", 1L);
            }
            if (name.equals("right")) {
              return rightFailure;
            }
            return ActivationFunction.ABSENT;
          };

      assertThat(program("left == right", false).eval(activation).getVal()).isSameAs(rightFailure);
      assertThat(program("left == right", true).eval(activation).getVal()).isSameAs(rightFailure);
    }
  }

  @Test
  void duplicateEquivalentIntKeysFollowTheExactContractBoundary() {
    Map<Object, Object> validLeft = new LinkedHashMap<>();
    validLeft.put(1L, 10L);
    validLeft.put(2L, 20L);
    Map<Object, Object> duplicateRight = new LinkedHashMap<>();
    duplicateRight.put((byte) 1, 10L);
    duplicateRight.put(1L, 10L);

    assertThat(
            program("intLeft == intRight", false)
                .eval(Map.of("intLeft", validLeft, "intRight", duplicateRight))
                .getVal())
        .isInstanceOf(Err.class)
        .hasToString("Failed with repeated key");

    Map<Object, Object> duplicateLeft = new LinkedHashMap<>();
    duplicateLeft.put((byte) 1, 10L);
    duplicateLeft.put(1L, 10L);
    Map<Object, Object> validRight = new LinkedHashMap<>();
    validRight.put(1L, 10L);
    validRight.put(2L, 20L);

    // Detecting duplicate-equivalent keys in the traversed left map would require the forbidden
    // pre-scan or per-evaluation key snapshot. Its result is outside the certified exact contract.
    assertThat(
            program("intLeft == intRight", false)
                .eval(Map.of("intLeft", duplicateLeft, "intRight", validRight))
                .getVal())
        .isSameAs(True);
  }

  @Test
  void visitedContractViolationsBecomeErrorsWithoutPrescanningUnvisitedEntries() {
    Program program = program("left == right", false);
    Val embedded = intOf(1L);
    assertThat(
            program
                .eval(Map.of("left", Map.of("key", embedded), "right", Map.of("key", 1L)))
                .getVal())
        .isInstanceOf(Err.class);

    Map<Object, Object> invalidKey = new LinkedHashMap<>();
    invalidKey.put(1L, 1L);
    assertThat(program.eval(Map.of("left", invalidKey, "right", Map.of("key", 1L))).getVal())
        .isInstanceOf(Err.class);

    Map<Object, Object> invalidButUnvisited =
        new AbstractMap<>() {
          @Override
          public int size() {
            return 1;
          }

          @Override
          public Set<Entry<Object, Object>> entrySet() {
            throw new AssertionError("size mismatch must not pre-scan");
          }
        };
    assertThat(
            program
                .eval(Map.of("left", invalidButUnvisited, "right", Collections.emptyMap()))
                .getVal())
        .isSameAs(False);
  }

  @Test
  void oneProgramCanEvaluateExactMapEqualityConcurrently() throws Exception {
    Program program = program("left == right", false);
    var executor = Executors.newFixedThreadPool(4);
    try {
      Collection<Callable<Boolean>> tasks = new ArrayList<>();
      for (int i = 0; i < 64; i++) {
        int value = i;
        tasks.add(
            () ->
                program
                    .eval(
                        Map.of(
                            "left", Map.of("key", (long) value),
                            "right", Map.of("key", (long) value)))
                    .getVal()
                    .booleanValue());
      }
      for (var future : executor.invokeAll(tasks, 10, SECONDS)) {
        assertThat(future.isCancelled()).isFalse();
        assertThat(future.get(10, SECONDS)).isTrue();
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  private static Val establishedSelected(Type checkedType, Object value) {
    Val list = EXACT.nativeAggregateToValue(new Object[] {value}, Decls.newListType(checkedType));
    return list instanceof Lister lister ? lister.nativeGetAt(0) : list;
  }

  private static void assertEquivalentVal(Val actual, Val expected, String description) {
    assertThat(actual.getClass()).as(description).isEqualTo(expected.getClass());
    assertThat(actual.type()).as(description).isEqualTo(expected.type());
    if (actual instanceof MapT || actual instanceof Lister) {
      assertThat(actual.equal(expected)).as(description).isSameAs(True);
    } else {
      assertThat(actual.toString()).as(description).isEqualTo(expected.toString());
    }
    assertThat(actual.value()).as(description).isEqualTo(expected.value());
  }

  private NativeExactMapEqualityPlan equalityPlan(
      NativeMapSourceCapability left, NativeMapSourceCapability right, Type valueType) {
    return equalityPlan(left, right, valueType, EXACT);
  }

  private NativeExactMapEqualityPlan equalityPlan(
      NativeMapSourceCapability left,
      NativeMapSourceCapability right,
      Type valueType,
      ExactAggregateTypeAdapter adapter) {
    return new NativeExactMapEqualityPlan(
        left,
        right,
        new CheckedValueMaterializer(adapter, Decls.String),
        new CheckedValueMaterializer(adapter, valueType));
  }

  private Interpretable plan(
      String expression,
      boolean nativeEnabled,
      TypeAdapter adapter,
      InterpretableDecorator... decorators) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    var checked = astToCheckedExpr(compiled.getAst());
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    AttributeFactory attributes =
        newAttributeFactory(defaultContainer, adapter, env.getTypeProvider());
    return newInterpreter(
            dispatcher, defaultContainer, env.getTypeProvider(), adapter, attributes, nativeEnabled)
        .newInterpretable(checked, decorators);
  }

  private Interpretable plan(
      String expression, boolean nativeEnabled, TypeAdapter adapter, Overload replacement) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    var checked = astToCheckedExpr(compiled.getAst());
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    dispatcher.add(replacement);
    AttributeFactory attributes =
        newAttributeFactory(defaultContainer, adapter, env.getTypeProvider());
    return newInterpreter(
            dispatcher, defaultContainer, env.getTypeProvider(), adapter, attributes, nativeEnabled)
        .newInterpretable(checked);
  }

  private void assertProgramParity(String expression, Object activation) {
    Val nativeValue = program(expression, false).eval(activation).getVal();
    Val disabledValue = program(expression, true).eval(activation).getVal();
    assertEquivalentVal(nativeValue, disabledValue, expression);
  }

  private Program program(String expression, boolean disableNative) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    return disableNative
        ? env.program(compiled.getAst(), evalOptions(OptDisableNativeEval))
        : env.program(compiled.getAst());
  }

  private static Activation emptyActivation() {
    return Activation.emptyActivation();
  }

  private static Map<Object, Object> singleEntryMap(
      Object key, AtomicInteger valueReads, boolean failOnValueRead) {
    return new AbstractMap<>() {
      @Override
      public int size() {
        return 1;
      }

      @Override
      public Set<Entry<Object, Object>> entrySet() {
        Entry<Object, Object> entry =
            new Entry<>() {
              @Override
              public Object getKey() {
                return key;
              }

              @Override
              public Object getValue() {
                valueReads.incrementAndGet();
                if (failOnValueRead) {
                  throw new AssertionError("left value must not be read");
                }
                return 1L;
              }

              @Override
              public Object setValue(Object value) {
                throw new UnsupportedOperationException();
              }
            };
        return Collections.singleton(entry);
      }
    };
  }

  private record CheckedCase(Type type, Object value) {}

  private record Marker(String name) {}

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    private final Function<Object, Val> converter;

    private ExactAdapter() {
      this(DefaultTypeAdapter.Instance::nativeToValue);
    }

    private ExactAdapter(Function<Object, Val> converter) {
      this.converter = converter;
    }

    @Override
    public Val nativeToValue(Object value) {
      return converter.apply(value);
    }
  }

  private static final class RawMapSource implements NativeMapSourceCapability {
    private final Function<Activation, Object> evaluator;

    private RawMapSource(Function<Activation, Object> evaluator) {
      this.evaluator = evaluator;
    }

    @Override
    public long id() {
      return 1L;
    }

    @Override
    public Val eval(Activation activation) {
      Object raw = evalRaw(activation);
      return raw instanceof Val value
          ? value
          : EXACT.nativeAggregateToValue(raw, Decls.newMapType(Decls.String, Decls.Dyn));
    }

    @Override
    public Object evalRaw(Activation activation) {
      return evaluator.apply(activation);
    }

    @Override
    public Val materializeResolvedMap(Object value) {
      return EXACT.nativeAggregateToValue(value, Decls.newMapType(Decls.String, Decls.Dyn));
    }

    @Override
    public boolean exactMapSource() {
      return true;
    }
  }

  private static final class CountingSizeMap extends AbstractMap<Object, Object> {
    private final String failureMessage;
    private final AtomicInteger sizeCalls = new AtomicInteger();

    private CountingSizeMap(String failureMessage) {
      this.failureMessage = failureMessage;
    }

    static CountingSizeMap throwing(String failureMessage) {
      return new CountingSizeMap(failureMessage);
    }

    @Override
    public int size() {
      sizeCalls.incrementAndGet();
      throw new IllegalStateException(failureMessage);
    }

    @Override
    public Set<Entry<Object, Object>> entrySet() {
      return Collections.emptySet();
    }
  }
}
