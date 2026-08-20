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
import static org.projectnessie.cel.CEL.astToCheckedExpr;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EvalOption.OptDisableNativeEval;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.ProgramOption.functions;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.interpreter.Activation.newPartialActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.AttributePattern.newAttributePattern;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.functions.Overload.standardOverloads;

import com.google.protobuf.Duration;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import java.util.AbstractCollection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Program;
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

class NativeListConcatTest {
  private final Env env =
      newEnv(
          customTypeAdapter(new ExactAdapter()),
          declarations(
              Decls.newVar("leftInts", Decls.newListType(Decls.Int)),
              Decls.newVar("rightInts", Decls.newListType(Decls.Int)),
              Decls.newVar("thirdInts", Decls.newListType(Decls.Int)),
              Decls.newVar("fourthInts", Decls.newListType(Decls.Int)),
              Decls.newVar("leftUints", Decls.newListType(Decls.Uint)),
              Decls.newVar("rightUints", Decls.newListType(Decls.Uint)),
              Decls.newVar("leftBools", Decls.newListType(Decls.Bool)),
              Decls.newVar("rightBools", Decls.newListType(Decls.Bool)),
              Decls.newVar("leftDoubles", Decls.newListType(Decls.Double)),
              Decls.newVar("rightDoubles", Decls.newListType(Decls.Double)),
              Decls.newVar("leftNulls", Decls.newListType(Decls.Null)),
              Decls.newVar("rightNulls", Decls.newListType(Decls.Null)),
              Decls.newVar("leftBytes", Decls.newListType(Decls.Bytes)),
              Decls.newVar("rightBytes", Decls.newListType(Decls.Bytes)),
              Decls.newVar("leftWrapperInts", Decls.newListType(Decls.newWrapperType(Decls.Int))),
              Decls.newVar("rightWrapperInts", Decls.newListType(Decls.newWrapperType(Decls.Int))),
              Decls.newVar("leftDurations", Decls.newListType(Decls.Duration)),
              Decls.newVar("rightDurations", Decls.newListType(Decls.Duration)),
              Decls.newVar("leftTimestamps", Decls.newListType(Decls.Timestamp)),
              Decls.newVar("rightTimestamps", Decls.newListType(Decls.Timestamp)),
              Decls.newVar("leftDyn", Decls.newListType(Decls.Dyn)),
              Decls.newVar("rightDyn", Decls.newListType(Decls.Dyn)),
              Decls.newVar("leftNested", Decls.newListType(Decls.newListType(Decls.Int))),
              Decls.newVar("rightNested", Decls.newListType(Decls.newListType(Decls.Int))),
              Decls.newVar(
                  "leftMaps", Decls.newListType(Decls.newMapType(Decls.String, Decls.Int))),
              Decls.newVar(
                  "rightMaps", Decls.newListType(Decls.newMapType(Decls.String, Decls.Int))),
              Decls.newVar(
                  "leftStructs", Decls.newListType(Decls.newObjectType("google.protobuf.Struct"))),
              Decls.newVar(
                  "rightStructs", Decls.newListType(Decls.newObjectType("google.protobuf.Struct"))),
              Decls.newVar("leftStrings", Decls.newListType(Decls.String)),
              Decls.newVar("rightStrings", Decls.newListType(Decls.String)),
              Decls.newVar("thirdStrings", Decls.newListType(Decls.String)),
              Decls.newVar("fourthStrings", Decls.newListType(Decls.String)),
              Decls.newVar("needle", Decls.String),
              Decls.newVar("index", Decls.Int),
              Decls.newVar("indexHolder", Decls.newMapType(Decls.String, Decls.Int))));

  @Test
  void exactConcatenationSupportsImmediateSizeAndBoundaryIndexes() {
    Map<String, Object> input =
        Map.of(
            "leftInts",
            new long[] {11L, 22L},
            "rightInts",
            List.of(33L, 44L),
            "leftUints",
            new long[] {-1L},
            "rightUints",
            List.of(ULong.valueOf(1L)),
            "leftStrings",
            List.of("left"),
            "rightStrings",
            new String[] {"right"});

    assertEquivalent("size(leftInts + rightInts)", input);
    assertEquivalent("(leftInts + rightInts)[0]", input);
    assertEquivalent("(leftInts + rightInts)[1]", input);
    assertEquivalent("(leftInts + rightInts)[2]", input);
    assertEquivalent("(leftInts + rightInts)[3]", input);
    assertEquivalent("(leftUints + rightUints)[0]", input);
    assertEquivalent("(leftStrings + rightStrings)[1]", input);
  }

  @Test
  void structuralSizeDoesNotInspectElementsAndSelectedViolationsRemainErrors() {
    List<Object> invalid = Arrays.asList(null, "not-an-int");
    Map<String, Object> input =
        Map.of(
            "leftInts", invalid,
            "rightInts", List.of(1L),
            "leftUints", List.of(ULong.valueOf(1L)),
            "rightUints", List.of(ULong.valueOf(2L)),
            "leftStrings", List.of("left"),
            "rightStrings", List.of("right"));

    assertThat(assertEquivalent("size(leftInts + rightInts)", input).intValue()).isEqualTo(3L);
    assertThat(assertEquivalent("(leftInts + rightInts)[0]", input)).isInstanceOf(Err.class);
    assertThat(assertEquivalent("(leftInts + rightInts)[1]", input)).isInstanceOf(Err.class);
    assertThat(program("(leftInts + rightInts)[2]", false).eval(input).getVal().intValue())
        .isEqualTo(1L);
  }

  @Test
  void outOfRangeIndexesPreserveEstablishedErrors() {
    Map<String, Object> input =
        Map.of(
            "leftInts", List.of(1L),
            "rightInts", List.of(2L),
            "leftUints", List.of(ULong.valueOf(1L)),
            "rightUints", List.of(ULong.valueOf(2L)),
            "leftStrings", List.of("left"),
            "rightStrings", List.of("right"));

    assertThat(assertEquivalent("(leftInts + rightInts)[-1]", input)).isInstanceOf(Err.class);
    assertThat(assertEquivalent("(leftInts + rightInts)[2]", input)).isInstanceOf(Err.class);
  }

  @Test
  void resolvesBothOperandsOnceInOrderAndKeepsLeftFailurePrecedence() {
    Program program = program("size(leftInts + rightInts)", false);
    List<String> resolutions = new ArrayList<>();
    AtomicInteger leftResolutions = new AtomicInteger();
    AtomicInteger rightResolutions = new AtomicInteger();
    ActivationFunction activation =
        name -> {
          resolutions.add(name);
          if (name.equals("leftInts")) {
            leftResolutions.incrementAndGet();
            return newErr("left failed");
          }
          if (name.equals("rightInts")) {
            rightResolutions.incrementAndGet();
            return newErr("right failed");
          }
          return ActivationFunction.ABSENT;
        };

    Val result = program.eval(activation).getVal();

    assertThat(result).isInstanceOf(Err.class);
    assertThat(result.toString()).contains("left failed");
    assertThat(resolutions).containsExactly("leftInts", "rightInts");
    assertThat(leftResolutions).hasValue(1);
    assertThat(rightResolutions).hasValue(1);
  }

  @Test
  void terminalAndRepeatedConcatenationRemainEstablishedCompatible() {
    Map<String, Object> input =
        Map.of(
            "leftInts", List.of(1L),
            "rightInts", List.of(2L),
            "leftUints", List.of(ULong.valueOf(1L)),
            "rightUints", List.of(ULong.valueOf(2L)),
            "leftStrings", List.of("left"),
            "rightStrings", List.of("right"));

    assertEquivalent("leftInts + rightInts", input);
    assertEquivalent("(leftInts + rightInts) + leftInts", input);
    assertEquivalent("(leftInts + rightInts)[0 + 1]", input);
  }

  @Test
  void constantStringMembershipConsumesFlattenedConcatWithoutBuildingTheList() {
    Map<String, Object> input =
        Map.of(
            "leftStrings",
            new String[] {"first"},
            "rightStrings",
            List.of("second"),
            "thirdStrings",
            new ArrayDeque<>(List.of("third")),
            "fourthStrings",
            new LinkedHashSet<>(List.of("fourth")));
    for (String concat :
        List.of(
            "((leftStrings + rightStrings) + thirdStrings) + fourthStrings",
            "leftStrings + (rightStrings + (thirdStrings + fourthStrings))",
            "(leftStrings + rightStrings) + (thirdStrings + fourthStrings)")) {
      for (String needle : List.of("first", "second", "third", "fourth", "missing")) {
        String expression = "'" + needle + "' in (" + concat + ")";
        assertEquivalent(expression, input);
        Plans plans = plans(expression, true);
        assertThat(plans.enabled()).isInstanceOf(NativeIsland.class);
        NativeScalarListConcatMembership root =
            (NativeScalarListConcatMembership) ((NativeIsland) plans.enabled()).root();
        assertThat(root.sourceCount()).isEqualTo(4);
        assertThat(containsNode(plans.established(), NativeListConcat.class)).isFalse();
      }
    }

    assertThat(
            assertEquivalent(
                    "'missing' in (leftStrings + rightStrings + thirdStrings)",
                    Map.of(
                        "leftStrings", List.of(),
                        "rightStrings", List.of(),
                        "thirdStrings", List.of()))
                .booleanValue())
        .isFalse();
    assertThat(
            assertEquivalent(
                    "'first' in (leftStrings + rightStrings)",
                    Map.of(
                        "leftStrings", new Object[] {"first"}, "rightStrings", List.of("second")))
                .booleanValue())
        .isTrue();
  }

  @Test
  void concatMembershipResolvesAndSizesEverySourceBeforeShortCircuitingTraversal() {
    CountingIterationCollection first =
        new CountingIterationCollection(List.of("needle", "unvisited"));
    CountingIterationCollection second =
        new CountingIterationCollection(List.of("must-not-be-traversed"));
    List<String> resolutions = new ArrayList<>();

    Val value =
        program("'needle' in (leftStrings + rightStrings)", false)
            .eval(
                (ActivationFunction)
                    name -> {
                      resolutions.add(name);
                      return switch (name) {
                        case "leftStrings" -> first;
                        case "rightStrings" -> second;
                        default -> ActivationFunction.ABSENT;
                      };
                    })
            .getVal();

    assertThat(value.booleanValue()).isTrue();
    assertThat(resolutions).containsExactly("leftStrings", "rightStrings");
    assertThat(first.sizeCalls).hasValue(1);
    assertThat(second.sizeCalls).hasValue(1);
    assertThat(first.iteratorCalls).hasValue(1);
    assertThat(second.iteratorCalls).hasValue(0);
  }

  @Test
  void concatMembershipDoesNotLetAnEarlyHitSuppressALaterSourceFailure() {
    Programs programs = programs("'needle' in (leftStrings + rightStrings)");
    CountingIterationCollection enabledFirst =
        new CountingIterationCollection(List.of("needle", "must-not-be-traversed"));
    CountingIterationCollection establishedFirst =
        new CountingIterationCollection(List.of("needle", "must-not-be-traversed"));
    List<String> enabledResolutions = new ArrayList<>();
    List<String> establishedResolutions = new ArrayList<>();

    Val enabled =
        programs
            .enabled()
            .eval(membershipFailureActivation(enabledFirst, enabledResolutions, "later failed"))
            .getVal();
    Val established =
        programs
            .established()
            .eval(
                membershipFailureActivation(
                    establishedFirst, establishedResolutions, "later failed"))
            .getVal();

    assertEquivalent(enabled, established);
    assertThat(enabled).isInstanceOf(Err.class).hasToString("later failed");
    assertThat(enabledResolutions).containsExactly("leftStrings", "rightStrings");
    assertThat(establishedResolutions).containsExactly("leftStrings", "rightStrings");
    assertThat(enabledFirst.sizeCalls).hasValue(1);
    assertThat(enabledFirst.iteratorCalls).hasValue(0);
  }

  @Test
  void concatMembershipKeepsEarliestSourceFailureAndNeverTraversesAfterFailure() {
    CountingIterationCollection third =
        new CountingIterationCollection(List.of("must-not-be-traversed"));
    List<String> resolutions = new ArrayList<>();

    Val value =
        program("'needle' in (leftStrings + rightStrings + thirdStrings)", false)
            .eval(
                (ActivationFunction)
                    name -> {
                      resolutions.add(name);
                      return switch (name) {
                        case "leftStrings" -> newErr("first failed");
                        case "rightStrings" -> newErr("second failed");
                        case "thirdStrings" -> third;
                        default -> ActivationFunction.ABSENT;
                      };
                    })
            .getVal();

    assertThat(value).isInstanceOf(Err.class).hasToString("first failed");
    assertThat(resolutions).containsExactly("leftStrings", "rightStrings", "thirdStrings");
    assertThat(third.sizeCalls).hasValue(1);
    assertThat(third.iteratorCalls).hasValue(0);
  }

  @Test
  void variableStringMembershipUsesTheScalarConcatConsumer() {
    Plans plans = plans("needle in (leftStrings + rightStrings)", true);
    assertThat(plans.enabled()).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) plans.enabled()).root())
        .isInstanceOf(NativeScalarListConcatMembership.class);
    assertEquivalent(
        "needle in (leftStrings + rightStrings)",
        Map.of(
            "needle", "right",
            "leftStrings", List.of("left"),
            "rightStrings", List.of("right")));
  }

  @Test
  void plansUseOneCheckedExpressionAndKeepUnsupportedConsumersEstablished() {
    Plans size = plans("size(leftInts + rightInts)", true);
    assertThat(size.enabled()).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) size.enabled()).root()).isInstanceOf(NativeListConcatSize.class);
    assertThat(size.established()).isNotInstanceOf(NativeIsland.class);
    assertThat(containsNode(size.established(), NativeListConcat.class)).isFalse();

    Plans constantIndex = plans("(leftInts + rightInts)[0]", true);
    assertThat(constantIndex.enabled()).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) constantIndex.enabled()).root())
        .isInstanceOf(NativeIntListConcatIndex.class);
    assertThat(containsNode(constantIndex.established(), NativeListConcat.class)).isFalse();

    Plans computedIndex = plans("(leftInts + rightInts)[0 + 1]", true);
    assertThat(computedIndex.enabled()).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) computedIndex.enabled()).root())
        .isInstanceOf(NativeIntListConcatIndex.class);

    Plans repeated = plans("size((leftInts + rightInts) + leftInts)", true);
    assertThat(repeated.enabled()).isInstanceOf(NativeIsland.class);
    NativeListConcatSize repeatedSize =
        (NativeListConcatSize) ((NativeIsland) repeated.enabled()).root();
    assertThat(repeatedSize.sourceCount()).isEqualTo(3);

    Plans terminal = plans("leftInts + rightInts", true);
    assertThat(terminal.enabled()).isInstanceOf(NativeListConcat.class);
    assertThat(terminal.enabled()).isNotInstanceOf(NativeIsland.class);

    Plans repeatedTerminal = plans("(leftInts + rightInts) + leftInts", true);
    assertThat(repeatedTerminal.enabled()).isInstanceOf(NativeListConcat.class);
    assertThat(repeatedTerminal.enabled()).isNotInstanceOf(NativeIsland.class);

    Plans general = plans("size(leftInts + rightInts)", false);
    assertThat(containsNode(general.enabled(), NativeListConcat.class)).isFalse();

    Plans generalDynamic = plans("(leftInts + rightInts)[index]", false);
    assertThat(generalDynamic.enabled()).isNotInstanceOf(NativeIsland.class);
    assertThat(containsNode(generalDynamic.enabled(), NativeListConcatIndex.class)).isFalse();
  }

  @Test
  void nSourceAssociationsSupportSizeAndEverySegmentBoundary() {
    Map<String, Object> input =
        Map.of(
            "leftInts", List.of(10L, 11L),
            "rightInts", List.of(20L),
            "thirdInts", List.of(),
            "fourthInts", List.of(40L, 41L, 42L));
    List<String> concats =
        List.of(
            "((leftInts + rightInts) + thirdInts) + fourthInts",
            "leftInts + (rightInts + (thirdInts + fourthInts))",
            "(leftInts + rightInts) + (thirdInts + fourthInts)");

    for (String concat : concats) {
      assertThat(assertEquivalent("size(" + concat + ")", input).intValue()).isEqualTo(6L);
      for (int index = 0; index < 6; index++) {
        assertEquivalent("(" + concat + ")[" + index + "]", input);
      }

      Plans size = plans("size(" + concat + ")", true);
      NativeListConcatSize root = (NativeListConcatSize) ((NativeIsland) size.enabled()).root();
      assertThat(root.sourceCount()).isEqualTo(4);

      Plans last = plans("(" + concat + ")[5]", true);
      NativeListConcatIndex index = (NativeListConcatIndex) ((NativeIsland) last.enabled()).root();
      assertThat(index.sourceCount()).isEqualTo(4);
    }
  }

  @Test
  void nSourceConsumersHandleEmptySegmentsAndAllEmptyInputs() {
    Map<String, Object> input =
        Map.of(
            "leftInts", List.of(),
            "rightInts", List.of(20L),
            "thirdInts", List.of(),
            "fourthInts", List.of(40L));
    String concat = "leftInts + rightInts + thirdInts + fourthInts";

    assertThat(assertEquivalent("size(" + concat + ")", input).intValue()).isEqualTo(2L);
    assertThat(assertEquivalent("(" + concat + ")[0]", input).intValue()).isEqualTo(20L);
    assertThat(assertEquivalent("(" + concat + ")[1]", input).intValue()).isEqualTo(40L);

    Map<String, Object> allEmpty =
        Map.of(
            "leftInts", List.of(),
            "rightInts", List.of(),
            "thirdInts", List.of(),
            "fourthInts", List.of());
    assertThat(assertEquivalent("size(" + concat + ")", allEmpty).intValue()).isZero();
    assertThat(assertEquivalent("(" + concat + ")[0]", allEmpty)).isInstanceOf(Err.class);
  }

  @Test
  void largerConcatTreeRetainsOneLinearConsumerPlan() {
    String concat = String.join(" + ", java.util.Collections.nCopies(16, "leftInts"));
    Plans size = plans("size(" + concat + ")", true);
    Plans index = plans("(" + concat + ")[index]", true);

    assertThat(size.enabled()).isInstanceOf(NativeIsland.class);
    assertThat(((NativeListConcatSize) ((NativeIsland) size.enabled()).root()).sourceCount())
        .isEqualTo(16);
    assertThat(index.enabled()).isInstanceOf(NativeIsland.class);
    assertThat(((NativeListConcatIndex) ((NativeIsland) index.enabled()).root()).sourceCount())
        .isEqualTo(16);
    assertThat(
            assertEquivalent(
                    "(" + concat + ")[index]", Map.of("leftInts", List.of(7L), "index", 15L))
                .intValue())
        .isEqualTo(7L);

    String stringConcat = String.join(" + ", java.util.Collections.nCopies(16, "leftStrings"));
    Plans membership = plans("'missing' in (" + stringConcat + ")", true);
    assertThat(membership.enabled()).isInstanceOf(NativeIsland.class);
    assertThat(
            ((NativeScalarListConcatMembership) ((NativeIsland) membership.enabled()).root())
                .sourceCount())
        .isEqualTo(16);
    assertThat(
            assertEquivalent(
                    "'missing' in (" + stringConcat + ")", Map.of("leftStrings", List.of("value")))
                .booleanValue())
        .isFalse();
  }

  @Test
  void nSourceTypedIndexesPreserveScalarKindsAndFullWidthBounds() {
    Map<String, Object> input =
        Map.of(
            "leftBools", List.of(true),
            "rightBools", List.of(false),
            "leftUints", new long[] {-1L},
            "rightUints", List.of(ULong.valueOf(1L)),
            "leftDoubles", List.of(Double.NaN, -0.0d),
            "rightDoubles", List.of(0.0d),
            "leftStrings", List.of("left"),
            "rightStrings", List.of("right"),
            "leftNulls", Arrays.asList((Object) null),
            "rightNulls", Arrays.asList((Object) null));

    assertEquivalent("(leftBools + rightBools + leftBools)[1]", input);
    assertEquivalent("(leftUints + rightUints + leftUints)[0]", input);
    assertEquivalent("(leftDoubles + rightDoubles + leftDoubles)[0]", input);
    assertEquivalent("(leftDoubles + rightDoubles + leftDoubles)[1]", input);
    assertEquivalent("(leftDoubles + rightDoubles + leftDoubles)[2]", input);
    assertEquivalent("(leftStrings + rightStrings + leftStrings)[1]", input);
    assertEquivalent("(leftNulls + rightNulls + leftNulls)[2]", input);

    for (String expression :
        List.of(
            "(leftStrings + rightStrings + leftStrings)[2147483648]",
            "(leftStrings + rightStrings + leftStrings)[-2147483649]")) {
      Val value = assertEquivalent(expression, input);
      assertThat(value).isInstanceOf(Err.class);
      assertThat(((NativeIsland) plans(expression, true).enabled()).root())
          .isInstanceOf(NativeStringListConcatIndex.class);
    }
  }

  @Test
  void nSourceResolutionContinuesAndUsesEarliestSourceFailure() {
    Program program = program("size(((leftInts + rightInts) + thirdInts) + fourthInts)", false);
    List<String> resolutions = new ArrayList<>();
    ActivationFunction activation =
        name -> {
          resolutions.add(name);
          return switch (name) {
            case "leftInts" -> "not-a-list";
            case "rightInts" -> throw new IllegalStateException("later raw failure");
            case "thirdInts" -> List.of(3L);
            case "fourthInts" -> List.of(4L);
            default -> ActivationFunction.ABSENT;
          };
        };

    Val result = program.eval(activation).getVal();

    assertThat(result).isInstanceOf(Err.class);
    assertThat(result.toString()).contains("incompatible with checked CEL type");
    assertThat(result.toString()).doesNotContain("later raw failure");
    assertThat(resolutions).containsExactly("leftInts", "rightInts", "thirdInts", "fourthInts");
  }

  @Test
  void nSourceSizingContinuesAfterFailureAndKeepsEarliestFailure() {
    CountingSizeCollection third = new CountingSizeCollection(3L);
    CountingSizeCollection fourth = new CountingSizeCollection(4L);
    Map<String, Object> input =
        Map.of(
            "leftInts",
            List.of(1L),
            "rightInts",
            new ThrowingSizeCollection(),
            "thirdInts",
            third,
            "fourthInts",
            fourth);

    Val result =
        program("size(leftInts + rightInts + thirdInts + fourthInts)", false).eval(input).getVal();

    assertThat(result).isInstanceOf(Err.class);
    assertThat(result.toString()).contains("size failed");
    assertThat(third.sizeCalls).hasValue(1);
    assertThat(fourth.sizeCalls).hasValue(1);
  }

  @Test
  void dynamicIndexesEvaluateOnceAfterEverySourceAndCrossSegments() {
    String expression = "(((leftInts + rightInts) + thirdInts) + fourthInts)[index + 1]";
    Map<String, Object> input =
        Map.of(
            "leftInts", List.of(10L),
            "rightInts", List.of(20L, 21L),
            "thirdInts", List.of(),
            "fourthInts", List.of(40L, 41L),
            "index", 2L);

    assertThat(assertEquivalent(expression, input).intValue()).isEqualTo(40L);
    Plans plans = plans(expression, true);
    assertThat(plans.enabled()).isInstanceOf(NativeIsland.class);
    NativeListConcatIndex root = (NativeListConcatIndex) ((NativeIsland) plans.enabled()).root();
    assertThat(root).isInstanceOf(NativeIntListConcatIndex.class);
    assertThat(root.sourceCount()).isEqualTo(4);

    List<String> resolutions = new ArrayList<>();
    Val value =
        program(expression, false)
            .eval(
                (ActivationFunction)
                    name -> {
                      resolutions.add(name);
                      return input.getOrDefault(name, ActivationFunction.ABSENT);
                    })
            .getVal();
    assertThat(value.intValue()).isEqualTo(40L);
    assertThat(resolutions)
        .containsExactly("leftInts", "rightInts", "thirdInts", "fourthInts", "index");
  }

  @Test
  void attributeBackedDynamicIndexReachesTheConcatSpecialization() {
    String expression = "(leftInts + rightInts + thirdInts)[indexHolder.index]";
    Map<String, Object> input =
        Map.of(
            "leftInts", List.of(10L),
            "rightInts", List.of(20L),
            "thirdInts", List.of(30L),
            "indexHolder", Map.of("index", 2L));

    Plans plans = plans(expression, true);
    assertThat(plans.enabled()).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) plans.enabled()).root())
        .isInstanceOf(NativeIntListConcatIndex.class);
    assertThat(assertEquivalent(expression, input).intValue()).isEqualTo(30L);
  }

  @Test
  void dynamicIndexesPreserveEveryTypedResultKindAndSelectedElementErrors() {
    Map<String, Object> input = new java.util.HashMap<>();
    input.put("leftBools", List.of(true));
    input.put("rightBools", List.of(false));
    input.put("leftUints", new long[] {-1L});
    input.put("rightUints", List.of(ULong.valueOf(1L)));
    input.put("leftDoubles", List.of(Double.NaN));
    input.put("rightDoubles", List.of(-0.0d, 0.0d));
    input.put("leftStrings", List.of("left"));
    input.put("rightStrings", List.of("right"));
    input.put("leftNulls", Arrays.asList((Object) null));
    input.put("rightNulls", Arrays.asList((Object) null));
    input.put("index", 1L);

    assertEquivalent("(leftBools + rightBools + leftBools)[index]", input);
    assertEquivalent("(leftUints + rightUints + leftUints)[index]", input);
    assertEquivalent("(leftDoubles + rightDoubles + leftDoubles)[index]", input);
    assertEquivalent("(leftStrings + rightStrings + leftStrings)[index]", input);
    assertEquivalent("(leftNulls + rightNulls + leftNulls)[index]", input);

    Map<String, Object> invalidSelected =
        Map.of(
            "leftInts", List.of("not-an-int"),
            "rightInts", List.of(2L),
            "thirdInts", List.of(3L),
            "index", 0L);
    assertThat(assertEquivalent("(leftInts + rightInts + thirdInts)[index]", invalidSelected))
        .isInstanceOf(Err.class);
  }

  @Test
  void sourceFailureSuppressesDynamicIndexAndPreservesNoReplay() {
    String expression = "(leftInts + rightInts + thirdInts + fourthInts)[index]";
    List<String> resolutions = new ArrayList<>();
    AtomicInteger indexResolutions = new AtomicInteger();
    Val value =
        program(expression, false)
            .eval(
                (ActivationFunction)
                    name -> {
                      resolutions.add(name);
                      return switch (name) {
                        case "leftInts" -> newErr("first source failed");
                        case "rightInts" -> List.of(2L);
                        case "thirdInts" -> List.of(3L);
                        case "fourthInts" -> List.of(4L);
                        case "index" -> {
                          indexResolutions.incrementAndGet();
                          yield 0L;
                        }
                        default -> ActivationFunction.ABSENT;
                      };
                    })
            .getVal();

    assertThat(value).isInstanceOf(Err.class);
    assertThat(value.toString()).contains("first source failed");
    assertThat(resolutions).containsExactly("leftInts", "rightInts", "thirdInts", "fourthInts");
    assertThat(indexResolutions).hasValue(0);
  }

  @Test
  void dynamicIndexFailuresAndFullWidthBoundsRemainCelErrors() {
    String expression = "(leftInts + rightInts + thirdInts)[index]";
    Map<String, Object> lists =
        Map.of(
            "leftInts", List.of(1L),
            "rightInts", List.of(2L),
            "thirdInts", List.of(3L));
    for (Object index : List.of(-1L, 3L, (long) Integer.MAX_VALUE + 1L, Long.MAX_VALUE)) {
      Map<String, Object> input = new java.util.HashMap<>(lists);
      input.put("index", index);
      assertThat(assertEquivalent(expression, input)).isInstanceOf(Err.class);
    }

    Map<String, Object> errorIndex = new java.util.HashMap<>(lists);
    errorIndex.put("index", newErr("index failed"));
    Programs errorPrograms = programs(expression);
    assertThat(errorPrograms.enabled().eval(errorIndex).getVal()).isInstanceOf(Err.class);
    assertThat(errorPrograms.established().eval(errorIndex).getVal()).isInstanceOf(Err.class);

    Map<String, Object> wrongRuntimeKind = new java.util.HashMap<>(lists);
    wrongRuntimeKind.put("index", "not-an-int");
    assertEquivalent(expression, wrongRuntimeKind);

    Map<String, Object> unknownIndex = new java.util.HashMap<>(lists);
    unknownIndex.put("index", unknownOf(71L));
    assertEquivalent(expression, unknownIndex);

    Map<String, Object> nullIndex = new java.util.HashMap<>(lists);
    nullIndex.put("index", null);
    Programs nullPrograms = programs(expression);
    assertThat(nullPrograms.enabled().eval(nullIndex).getVal()).isInstanceOf(Err.class);
    assertThat(nullPrograms.established().eval(nullIndex).getVal()).isInstanceOf(Err.class);

    ActivationFunction missingIndex =
        name -> lists.containsKey(name) ? lists.get(name) : ActivationFunction.ABSENT;
    Programs missingPrograms = programs(expression);
    assertThat(missingPrograms.enabled().eval(missingIndex).getVal()).isInstanceOf(Err.class);
    assertThat(missingPrograms.established().eval(missingIndex).getVal()).isInstanceOf(Err.class);

    ActivationFunction throwingIndex =
        name -> {
          if (name.equals("index")) {
            throw new IllegalStateException("index resolution failed");
          }
          return lists.getOrDefault(name, ActivationFunction.ABSENT);
        };
    Programs throwingPrograms = programs(expression);
    assertThat(throwingPrograms.enabled().eval(throwingIndex).getVal()).isInstanceOf(Err.class);
    assertThat(throwingPrograms.established().eval(throwingIndex).getVal()).isInstanceOf(Err.class);
  }

  @Test
  void sourceUnknownsAndFinalFailuresPreserveOrderAndPrecedence() {
    String expression = "size(leftInts + rightInts + thirdInts + fourthInts)";
    for (String unknownSource : List.of("leftInts", "rightInts", "thirdInts", "fourthInts")) {
      Map<String, Object> input = new java.util.HashMap<>();
      input.put("leftInts", List.of(1L));
      input.put("rightInts", List.of(2L));
      input.put("thirdInts", List.of(3L));
      input.put("fourthInts", List.of(4L));
      input.put(unknownSource, unknownOf(80L));
      assertThat(assertEquivalent(expression, input))
          .isInstanceOf(org.projectnessie.cel.common.types.UnknownT.class);
    }

    List<String> resolutions = new ArrayList<>();
    Val value =
        program(expression, false)
            .eval(
                (ActivationFunction)
                    name -> {
                      resolutions.add(name);
                      return name.equals("fourthInts")
                          ? newErr("final source failed")
                          : List.of(1L);
                    })
            .getVal();
    assertThat(value).isInstanceOf(Err.class);
    assertThat(value.toString()).contains("final source failed");
    assertThat(resolutions).containsExactly("leftInts", "rightInts", "thirdInts", "fourthInts");
  }

  @Test
  void unsupportedConcatLeavesKeepTheCompleteConsumerEstablished() {
    for (String expression :
        List.of(
            "size(leftInts + [1] + rightInts)",
            "size((leftInts + rightInts) + [1])",
            "size([1] + (leftInts + rightInts))",
            "(leftInts + [1] + rightInts)[index]")) {
      Plans plans = plans(expression, true);
      assertThat(plans.enabled()).as(expression).isNotInstanceOf(NativeIsland.class);
      assertEquivalent(
          expression,
          Map.of(
              "leftInts", List.of(1L),
              "rightInts", List.of(2L),
              "index", 1L));
    }
  }

  @Test
  void replacedListAdditionKeepsTheCompleteConsumerEstablished() {
    String expression = "size(leftInts + rightInts + thirdInts)";
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    Overload replacement = Overload.binary(Overloads.AddList, (left, right) -> left);
    Program program = env.program(compiled.getAst(), functions(replacement));

    Val value =
        program
            .eval(
                Map.of(
                    "leftInts", List.of(1L),
                    "rightInts", List.of(2L),
                    "thirdInts", List.of(3L)))
            .getVal();

    assertThat(value.intValue()).isEqualTo(1L);
  }

  @Test
  void dynamicIndexSupportsPartialActivationAndConcurrentPlanReuse() throws Exception {
    String expression = "(leftInts + rightInts + thirdInts + fourthInts)[index]";
    Map<String, Object> input =
        Map.of(
            "leftInts", List.of(10L),
            "rightInts", List.of(20L),
            "thirdInts", List.of(30L),
            "fourthInts", List.of(40L),
            "index", 2L);
    Activation partial = newPartialActivation(input, newAttributePattern("index"));
    Programs partialPrograms = programs(expression);
    assertEquivalent(
        partialPrograms.enabled().eval(partial).getVal(),
        partialPrograms.established().eval(partial).getVal());

    Program reusable = program(expression, false);
    @SuppressWarnings("resource")
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Long>> results = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        long expected = i;
        results.add(
            executor.submit(
                () ->
                    reusable
                        .eval(
                            Map.of(
                                "leftInts", List.of(expected),
                                "rightInts", List.of(expected + 1L),
                                "thirdInts", List.of(expected + 2L),
                                "fourthInts", List.of(expected + 3L),
                                "index", 0L))
                        .getVal()
                        .intValue()));
      }
      for (int i = 0; i < results.size(); i++) {
        assertThat(results.get(i).get(5, SECONDS)).isEqualTo((long) i);
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  @Test
  void structuralSizeSupportsNonScalarCheckedElementKindsWithoutInspection() {
    Map<String, Object> input =
        Map.of(
            "leftBytes", List.of(new byte[] {1}, "invalid"),
            "rightBytes", List.of(new byte[] {2}),
            "leftDyn", List.of("dynamic", 1L),
            "rightDyn", List.of(true),
            "leftNested", List.of(List.of(1L), "invalid"),
            "rightNested", List.of(List.of(2L)),
            "leftMaps", List.of(Map.of("one", 1L), "invalid"),
            "rightMaps", List.of(Map.of("two", 2L)),
            "leftStructs", List.of(Struct.getDefaultInstance(), "invalid"),
            "rightStructs", List.of(Struct.getDefaultInstance()));

    assertNativeStructuralSize("leftBytes", "rightBytes", input, 5L);
    assertNativeStructuralSize("leftDyn", "rightDyn", input, 5L);
    assertNativeStructuralSize("leftNested", "rightNested", input, 5L);
    assertNativeStructuralSize("leftMaps", "rightMaps", input, 5L);
    assertNativeStructuralSize("leftStructs", "rightStructs", input, 5L);
  }

  @Test
  void exactMixedArrayListCollectionAndSetSourcesPreserveEncounterOrder() {
    ArrayDeque<Long> collection = new ArrayDeque<>(List.of(30L, 31L));
    LinkedHashSet<Long> set = new LinkedHashSet<>(List.of(40L, 41L));
    Map<String, Object> input =
        Map.of(
            "leftInts",
            new long[] {10L, 11L},
            "rightInts",
            List.of(20L),
            "thirdInts",
            collection,
            "fourthInts",
            set,
            "index",
            5L);
    String concat = "leftInts + rightInts + thirdInts + fourthInts";

    assertThat(assertEquivalent("size(" + concat + ")", input).intValue()).isEqualTo(7L);
    for (int index = 0; index < 7; index++) {
      assertEquivalent("(" + concat + ")[" + index + "]", input);
    }
    assertThat(assertEquivalent("(" + concat + ")[index]", input).intValue()).isEqualTo(40L);
  }

  @Test
  @SuppressWarnings({"removal", "unchecked"})
  void terminalRepeatedConcatRetainsEstablishedSnapshotAndUintRepresentation() {
    List<Long> left = new ArrayList<>(List.of(1L, 2L));
    LinkedHashSet<Long> right = new LinkedHashSet<>(List.of(3L, 4L));
    Map<String, Object> input = Map.of("leftInts", left, "rightInts", right);
    Programs programs = programs("(leftInts + rightInts) + leftInts");

    Val nativeValue = programs.enabled().eval(input).getVal();
    Val establishedValue = programs.established().eval(input).getVal();
    assertEquivalent(nativeValue, establishedValue);
    assertThat(nativeValue.value().getClass()).isEqualTo(establishedValue.value().getClass());
    assertThat(nativeValue.convertToNative(List.class))
        .containsExactlyElementsOf(establishedValue.convertToNative(List.class));

    left.set(0, 99L);
    right.clear();
    right.add(88L);
    assertThat(nativeValue.convertToNative(List.class)).containsExactly(1L, 2L, 3L, 4L, 1L, 2L);
    assertThat(establishedValue.convertToNative(List.class))
        .containsExactly(1L, 2L, 3L, 4L, 1L, 2L);

    Map<String, Object> uintInput =
        Map.of("leftUints", new long[] {-1L}, "rightUints", List.of(ULong.valueOf(1L)));
    Val nativeUints =
        programs("(leftUints + rightUints) + leftUints").enabled().eval(uintInput).getVal();
    Val establishedUints =
        programs("(leftUints + rightUints) + leftUints").established().eval(uintInput).getVal();
    assertEquivalent(nativeUints, establishedUints);
    assertThat((Object[]) nativeUints.value())
        .containsExactly(ULong.valueOf(-1L), ULong.valueOf(1L), ULong.valueOf(-1L));
  }

  @Test
  void nonTypedElementIndexesUseTheNativeConcatConsumer() {
    Map<String, Object> input =
        new java.util.HashMap<>(
            Map.of(
                "leftBytes",
                List.of(new byte[] {1}),
                "rightBytes",
                List.of(new byte[] {2}),
                "leftDyn",
                List.of("dynamic"),
                "rightDyn",
                List.of(1L),
                "leftNested",
                List.of(List.of(1L)),
                "rightNested",
                List.of(List.of(2L)),
                "leftMaps",
                List.of(Map.of("one", 1L)),
                "rightMaps",
                List.of(Map.of("two", 2L)),
                "leftStructs",
                List.of(Struct.getDefaultInstance()),
                "rightStructs",
                List.of(Struct.getDefaultInstance())));
    input.put("leftWrapperInts", Arrays.asList((Object) null));
    input.put("rightWrapperInts", List.of(2L));
    input.put("leftDurations", List.of(Duration.newBuilder().setSeconds(1L).build()));
    input.put("rightDurations", List.of(Duration.newBuilder().setSeconds(2L).build()));
    input.put("leftTimestamps", List.of(Timestamp.newBuilder().setSeconds(1L).build()));
    input.put("rightTimestamps", List.of(Timestamp.newBuilder().setSeconds(2L).build()));
    input.put("index", 1L);
    for (String expression :
        List.of(
            "(leftBytes + rightBytes + leftBytes)[1]",
            "(leftBytes + rightBytes + leftBytes)[index]",
            "(leftWrapperInts + rightWrapperInts + leftWrapperInts)[0]",
            "(leftWrapperInts + rightWrapperInts + leftWrapperInts)[index]",
            "(leftDurations + rightDurations + leftDurations)[1]",
            "(leftDurations + rightDurations + leftDurations)[index]",
            "(leftTimestamps + rightTimestamps + leftTimestamps)[1]",
            "(leftTimestamps + rightTimestamps + leftTimestamps)[index]",
            "(leftDyn + rightDyn + leftDyn)[1]",
            "(leftDyn + rightDyn + leftDyn)[index]",
            "(leftNested + rightNested + leftNested)[1]",
            "(leftNested + rightNested + leftNested)[index]",
            "(leftMaps + rightMaps + leftMaps)[1]",
            "(leftMaps + rightMaps + leftMaps)[index]",
            "(leftStructs + rightStructs + leftStructs)[1]",
            "(leftStructs + rightStructs + leftStructs)[index]")) {
      Plans plans = plans(expression, true);
      assertThat(plans.enabled())
          .as(expression)
          .isExactlyInstanceOf(NativeValueListConcatIndex.class);
      assertThat(((NativeValueListConcatIndex) plans.enabled()).sourceCount()).isEqualTo(3);
      assertEquivalent(expression, input);
    }

    assertEquivalent("size((leftNested + rightNested)[index])", input);
    assertThat(assertEquivalent("(leftNested + rightNested)[index][0]", input).intValue())
        .isEqualTo(2L);
  }

  @Test
  void nonTypedIndexValidatesSourceContainersBeforeIndexAndBounds() {
    String expression = "(leftDyn + rightDyn)[index]";
    List<String> resolutions = new ArrayList<>();
    AtomicInteger indexResolutions = new AtomicInteger();
    ActivationFunction invalidPrimitiveArray =
        name -> {
          resolutions.add(name);
          return switch (name) {
            case "leftDyn" -> new long[] {1L};
            case "rightDyn" -> List.of("right");
            case "index" -> {
              indexResolutions.incrementAndGet();
              yield 99L;
            }
            default -> ActivationFunction.ABSENT;
          };
        };

    Val value = program(expression, false).eval(invalidPrimitiveArray).getVal();

    assertThat(value).isInstanceOf(Err.class);
    assertThat(value.toString()).contains("incompatible with checked CEL type");
    assertThat(resolutions).containsExactly("leftDyn", "rightDyn");
    assertThat(indexResolutions).hasValue(0);

    Val[] invalidValues = {unknownOf(91L)};
    Map<String, Object> invalidValArray =
        Map.of("leftDyn", invalidValues, "rightDyn", List.of("right"), "index", 99L);
    assertThat(assertEquivalent(expression, invalidValArray))
        .isInstanceOf(Err.class)
        .asString()
        .contains("[Lorg.projectnessie.cel.common.types.ref.Val;", "incompatible");

    assertThat(
            assertEquivalent(
                "(leftBytes + rightBytes)[index]",
                Map.of(
                    "leftBytes",
                    new byte[] {1, 2},
                    "rightBytes",
                    List.of(new byte[] {3}),
                    "index",
                    99L)))
        .isInstanceOf(Err.class);
  }

  @Test
  void nonTypedIndexOnlyMaterializesTheSelectedElement() {
    Map<String, Object> bytes =
        Map.of(
            "leftBytes", List.of("invalid"),
            "rightBytes", List.of(new byte[] {2}),
            "index", 1L);

    assertThat((byte[]) assertEquivalent("(leftBytes + rightBytes)[index]", bytes).value())
        .containsExactly(2);

    Map<String, Object> selectedInvalid =
        Map.of(
            "leftMaps", List.of("invalid"),
            "rightMaps", List.of(Map.of("two", 2L)),
            "index", 0L);
    assertThat(assertEquivalent("(leftMaps + rightMaps)[index]", selectedInvalid))
        .isInstanceOf(Err.class);
  }

  @Test
  void nonTypedIndexPreservesCostFallbackPartialEvaluationAndConcurrentReuse() throws Exception {
    String expression = "(leftDyn + rightDyn + leftDyn)[index]";
    Plans plans = plans(expression, true);
    NativeValueListConcatIndex nativeRoot = (NativeValueListConcatIndex) plans.enabled();
    var expressionAst = env.compile(expression);
    assertThat(expressionAst.hasIssues()).as(expressionAst.getIssues().toString()).isFalse();
    assertThat(nativeRoot.id())
        .isEqualTo(astToCheckedExpr(expressionAst.getAst()).getExpr().getId());
    assertThat(estimateCost(nativeRoot)).isEqualTo(estimateCost(plans.established()));

    Plans general = plans(expression, false);
    assertThat(general.enabled()).isNotInstanceOf(NativeValueListConcatIndex.class);

    Map<String, Object> input =
        Map.of(
            "leftDyn", List.of("left"),
            "rightDyn", List.of("right"),
            "index", 1L);
    Activation partial = newPartialActivation(input, newAttributePattern("index"));
    Programs partialPrograms = programs(expression);
    assertEquivalent(
        partialPrograms.enabled().eval(partial).getVal(),
        partialPrograms.established().eval(partial).getVal());

    var compiled = env.compile("(leftDyn + rightDyn)[0]");
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    Program replacedAddition =
        env.program(
            compiled.getAst(),
            functions(Overload.binary(Overloads.AddList, (left, right) -> right)));
    assertThat(
            replacedAddition
                .eval(Map.of("leftDyn", List.of("left"), "rightDyn", List.of("right")))
                .getVal())
        .isEqualTo(stringOf("right"));

    Program reusable = program(expression, false);
    @SuppressWarnings("resource")
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<String>> results = new ArrayList<>();
      for (int i = 0; i < 50; i++) {
        String expected = "value-" + i;
        results.add(
            executor.submit(
                () ->
                    reusable
                        .eval(
                            Map.of(
                                "leftDyn", List.of(expected),
                                "rightDyn", List.of("right"),
                                "index", 0L))
                        .getVal()
                        .value()
                        .toString()));
      }
      for (int i = 0; i < results.size(); i++) {
        assertThat(results.get(i).get(5, SECONDS)).isEqualTo("value-" + i);
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  private void assertNativeStructuralSize(
      String left, String right, Map<String, Object> input, long expected) {
    String expression = "size(" + left + " + " + right + " + " + left + ")";
    Plans plans = plans(expression, true);
    assertThat(plans.enabled()).isInstanceOf(NativeIsland.class);
    assertThat(((NativeIsland) plans.enabled()).root()).isInstanceOf(NativeListConcatSize.class);
    assertThat(program(expression, false).eval(input).getVal().intValue()).isEqualTo(expected);
  }

  private static ActivationFunction membershipFailureActivation(
      CountingIterationCollection first, List<String> resolutions, String failure) {
    return name -> {
      resolutions.add(name);
      return switch (name) {
        case "leftStrings" -> first;
        case "rightStrings" -> newErr(failure);
        default -> ActivationFunction.ABSENT;
      };
    };
  }

  private Val assertEquivalent(String expression, Object input) {
    Programs programs = programs(expression);
    Val nativeValue = programs.enabled().eval(input).getVal();
    Val establishedValue = programs.established().eval(input).getVal();

    assertEquivalent(nativeValue, establishedValue);
    return nativeValue;
  }

  private void assertEquivalent(Val nativeValue, Val establishedValue) {
    assertThat(nativeValue.getClass()).isEqualTo(establishedValue.getClass());
    assertThat(nativeValue.type()).isEqualTo(establishedValue.type());
    if (nativeValue.type().typeEnum() == org.projectnessie.cel.common.types.ref.TypeEnum.List
        || nativeValue.type().typeEnum() == org.projectnessie.cel.common.types.ref.TypeEnum.Map
        || nativeValue.type().typeEnum() == org.projectnessie.cel.common.types.ref.TypeEnum.Bytes) {
      assertThat(nativeValue.equal(establishedValue).booleanValue()).isTrue();
    } else {
      assertThat(nativeValue.toString()).isEqualTo(establishedValue.toString());
    }
    if (!(nativeValue instanceof Err)
        && nativeValue.type().typeEnum() != org.projectnessie.cel.common.types.ref.TypeEnum.List
        && nativeValue.type().typeEnum() != org.projectnessie.cel.common.types.ref.TypeEnum.Map
        && nativeValue.type().typeEnum() != org.projectnessie.cel.common.types.ref.TypeEnum.Bytes) {
      assertThat(nativeValue.value()).isEqualTo(establishedValue.value());
    }
  }

  private Program program(String expression, boolean disableNative) {
    Programs programs = programs(expression);
    return disableNative ? programs.established() : programs.enabled();
  }

  private Programs programs(String expression) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    return new Programs(
        env.program(compiled.getAst()),
        env.program(compiled.getAst(), evalOptions(OptDisableNativeEval)));
  }

  private Plans plans(String expression, boolean exactAggregateAdapter) {
    var compiled = env.compile(expression);
    assertThat(compiled.hasIssues()).as(compiled.getIssues().toString()).isFalse();
    var checked = astToCheckedExpr(compiled.getAst());
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(standardOverloads());
    TypeAdapter adapter =
        exactAggregateAdapter ? env.getTypeAdapter() : DefaultTypeAdapter.Instance;
    AttributeFactory attributes =
        newAttributeFactory(defaultContainer, adapter, env.getTypeProvider());
    Interpreter enabledInterpreter =
        newInterpreter(
            dispatcher, defaultContainer, env.getTypeProvider(), adapter, attributes, true);
    Interpreter establishedInterpreter =
        newInterpreter(
            dispatcher, defaultContainer, env.getTypeProvider(), adapter, attributes, false);
    return new Plans(
        enabledInterpreter.newInterpretable(checked),
        establishedInterpreter.newInterpretable(checked));
  }

  private static boolean containsNode(Interpretable root, Class<?> nodeType) {
    if (nodeType.isInstance(root)) {
      return true;
    }
    if (root instanceof NativeIsland island) {
      return containsNode(island.root(), nodeType);
    }
    if (root instanceof Interpretable.InterpretableCall call) {
      for (Interpretable argument : call.args()) {
        if (containsNode(argument, nodeType)) {
          return true;
        }
      }
    }
    return false;
  }

  private static final class ExactAdapter
      implements ExactAggregateTypeAdapter, StandardScalarTypeAdapter {
    @Override
    public Val nativeToValue(Object value) {
      return DefaultTypeAdapter.Instance.nativeToValue(value);
    }
  }

  private static final class ThrowingSizeCollection extends AbstractCollection<Long> {
    @Override
    public Iterator<Long> iterator() {
      return List.<Long>of().iterator();
    }

    @Override
    public int size() {
      throw new IllegalStateException("size failed");
    }
  }

  private static final class CountingSizeCollection extends AbstractCollection<Long> {
    private final long value;
    private final AtomicInteger sizeCalls = new AtomicInteger();

    private CountingSizeCollection(long value) {
      this.value = value;
    }

    @Override
    public Iterator<Long> iterator() {
      return List.of(value).iterator();
    }

    @Override
    public int size() {
      sizeCalls.incrementAndGet();
      return 1;
    }
  }

  private static final class CountingIterationCollection extends AbstractCollection<String> {
    private final List<String> values;
    private final AtomicInteger sizeCalls = new AtomicInteger();
    private final AtomicInteger iteratorCalls = new AtomicInteger();

    private CountingIterationCollection(List<String> values) {
      this.values = values;
    }

    @Override
    public Iterator<String> iterator() {
      iteratorCalls.incrementAndGet();
      return values.iterator();
    }

    @Override
    public int size() {
      sizeCalls.incrementAndGet();
      return values.size();
    }
  }

  private record Programs(Program enabled, Program established) {}

  private record Plans(Interpretable enabled, Interpretable established) {}
}
