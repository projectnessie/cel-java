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
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.ProgramOption.functions;
import static org.projectnessie.cel.Util.mapOf;
import static org.projectnessie.cel.checker.Decls.Int;
import static org.projectnessie.cel.checker.Decls.newFunction;
import static org.projectnessie.cel.checker.Decls.newOverload;
import static org.projectnessie.cel.checker.Decls.newVar;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newRegistry;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;
import static org.projectnessie.cel.interpreter.Coster.Cost.estimateCost;
import static org.projectnessie.cel.interpreter.Coster.costOf;
import static org.projectnessie.cel.interpreter.Dispatcher.newDispatcher;
import static org.projectnessie.cel.interpreter.EvalState.newEvalState;
import static org.projectnessie.cel.interpreter.Interpreter.newInterpreter;
import static org.projectnessie.cel.interpreter.Interpreter.trackState;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Reference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.common.Source;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Receiver;
import org.projectnessie.cel.common.types.traits.Trait;
import org.projectnessie.cel.interpreter.Interpretable.EvalBinary;
import org.projectnessie.cel.interpreter.Interpretable.EvalQuaternary;
import org.projectnessie.cel.interpreter.Interpretable.EvalQuinary;
import org.projectnessie.cel.interpreter.Interpretable.EvalReceiverVarArgs;
import org.projectnessie.cel.interpreter.Interpretable.EvalTernary;
import org.projectnessie.cel.interpreter.Interpretable.EvalUnary;
import org.projectnessie.cel.interpreter.Interpretable.EvalVarArgs;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableCall;
import org.projectnessie.cel.interpreter.functions.Overload;
import org.projectnessie.cel.parser.Parser;
import org.projectnessie.cel.parser.Parser.ParseResult;

@SuppressWarnings("removal")
class OverloadDispatchTest {

  private static final long CALL_ID = 100;

  @TestFactory
  Stream<DynamicTest> checkedExactOverloadIdsWinAtEveryCurrentArity() {
    return IntStream.rangeClosed(0, 5)
        .mapToObj(
            arity ->
                DynamicTest.dynamicTest(
                    "arity " + arity,
                    () -> {
                      String function = "checked_exact_" + arity;
                      String overload = function + "_overload";
                      Dispatcher dispatcher = newDispatcher();
                      dispatcher.add(
                          operation(function, arity, -1), operation(overload, arity, arity));

                      Interpretable interpretable =
                          checkedCall(dispatcher, function, arity, reference(overload));

                      assertThat(interpretable.eval(emptyActivation())).isEqualTo(intOf(arity));
                      assertCallShape(interpretable, function, overload, arity);
                    }));
  }

  @TestFactory
  Stream<DynamicTest> checkedFunctionNameFallbackHandlesZeroOrMultipleOverloadIds() {
    return Stream.of(
        DynamicTest.dynamicTest(
            "zero overload IDs", () -> assertCheckedNameFallback(Reference.getDefaultInstance())),
        DynamicTest.dynamicTest(
            "multiple overload IDs",
            () ->
                assertCheckedNameFallback(
                    Reference.newBuilder()
                        .addOverloadId("unused_first")
                        .addOverloadId("unused_second")
                        .build())));
  }

  @Test
  void uncheckedCallsResolveGlobalAndQualifiedNamesDuringPlanning() {
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(
        Overload.unary("global_dispatch", ignored -> intOf(1)),
        Overload.unary("test.namespace.qualified", ignored -> intOf(2)));
    Interpreter interpreter = interpreter(dispatcher);

    Interpretable global = uncheckedCall(interpreter, "global_dispatch(0)");
    Interpretable qualified = uncheckedCall(interpreter, "test.namespace.qualified(0)");

    assertThat(global.eval(emptyActivation())).isEqualTo(intOf(1));
    assertBasicCallShape(global, "global_dispatch", "", 1);
    assertThat(qualified.eval(emptyActivation())).isEqualTo(intOf(2));
    assertBasicCallShape(qualified, "test.namespace.qualified", "", 1);
  }

  @Test
  void traitSuccessUsesBoundOperationAndTraitMismatchUsesReceiver() {
    RecordingReceiver receiver = new RecordingReceiver();
    AtomicInteger boundCalls = new AtomicInteger();
    Interpretable receiverArg = Interpretable.newConstValue(1, receiver);

    EvalUnary traitSuccess =
        new EvalUnary(
            CALL_ID,
            "dispatch",
            "receiver_trait",
            receiverArg,
            Trait.ReceiverType,
            value -> {
              boundCalls.incrementAndGet();
              return intOf(10);
            });
    EvalUnary traitMismatch =
        new EvalUnary(
            CALL_ID,
            "dispatch",
            "adder_trait",
            receiverArg,
            Trait.AdderType,
            value -> {
              boundCalls.incrementAndGet();
              return intOf(20);
            });

    assertThat(traitSuccess.eval(emptyActivation())).isEqualTo(intOf(10));
    assertThat(boundCalls).hasValue(1);
    assertThat(receiver.invocations).isZero();

    assertThat(traitMismatch.eval(emptyActivation())).isEqualTo(intOf(0));
    assertThat(boundCalls).hasValue(1);
    assertThat(receiver.invocations).isOne();
    assertThat(receiver.function).isEqualTo("dispatch");
    assertThat(receiver.overload).isEqualTo("adder_trait");
  }

  @Test
  void ternaryTraitMismatchUsesReceiverWithoutInvokingBoundOperation() {
    RecordingReceiver receiver = new RecordingReceiver();
    AtomicInteger boundCalls = new AtomicInteger();
    EvalTernary call =
        new EvalTernary(
            CALL_ID,
            "dispatch",
            "adder_trait",
            Interpretable.newConstValue(1, receiver),
            Interpretable.newConstValue(2, intOf(1)),
            Interpretable.newConstValue(3, intOf(2)),
            Trait.AdderType,
            (first, second, third) -> {
              boundCalls.incrementAndGet();
              return intOf(20);
            });

    assertThat(call.eval(emptyActivation())).isEqualTo(intOf(2));
    assertThat(boundCalls).hasValue(0);
    assertThat(receiver.invocations).isOne();
    assertThat(receiver.args).extracting(Val::intValue).containsExactly(1L, 2L);
  }

  @Test
  void ternaryTraitMismatchPreservesGenericNoSuchOverloadMessage() {
    Interpretable[] args = {
      Interpretable.newConstValue(1, intOf(1)),
      Interpretable.newConstValue(2, intOf(2)),
      Interpretable.newConstValue(3, intOf(3))
    };
    EvalVarArgs generic =
        new EvalVarArgs(
            CALL_ID, "dispatch", "receiver_trait", args, Trait.ReceiverType, values -> True);
    EvalTernary ternary =
        new EvalTernary(
            CALL_ID,
            "dispatch",
            "receiver_trait",
            args[0],
            args[1],
            args[2],
            Trait.ReceiverType,
            (first, second, third) -> True);

    assertThat(ternary.eval(emptyActivation()).toString())
        .isEqualTo(generic.eval(emptyActivation()).toString());
  }

  @Test
  void quaternaryTraitMismatchPreservesGenericNoSuchOverloadMessage() {
    Interpretable[] args = {
      Interpretable.newConstValue(1, intOf(1)),
      Interpretable.newConstValue(2, intOf(2)),
      Interpretable.newConstValue(3, intOf(3)),
      Interpretable.newConstValue(4, intOf(4))
    };
    EvalVarArgs generic =
        new EvalVarArgs(
            CALL_ID, "dispatch", "receiver_trait", args, Trait.ReceiverType, values -> True);
    EvalQuaternary quaternary =
        new EvalQuaternary(
            CALL_ID,
            "dispatch",
            "receiver_trait",
            args[0],
            args[1],
            args[2],
            args[3],
            Trait.ReceiverType,
            (first, second, third, fourth) -> True);

    assertThat(quaternary.eval(emptyActivation()).toString())
        .isEqualTo(generic.eval(emptyActivation()).toString());
  }

  @Test
  void quinaryTraitMismatchPreservesGenericNoSuchOverloadMessage() {
    Interpretable[] args = {
      Interpretable.newConstValue(1, intOf(1)),
      Interpretable.newConstValue(2, intOf(2)),
      Interpretable.newConstValue(3, intOf(3)),
      Interpretable.newConstValue(4, intOf(4)),
      Interpretable.newConstValue(5, intOf(5))
    };
    EvalVarArgs generic =
        new EvalVarArgs(
            CALL_ID, "dispatch", "receiver_trait", args, Trait.ReceiverType, values -> True);
    EvalQuinary quinary =
        new EvalQuinary(
            CALL_ID,
            "dispatch",
            "receiver_trait",
            args[0],
            args[1],
            args[2],
            args[3],
            args[4],
            Trait.ReceiverType,
            (first, second, third, fourth, fifth) -> True);

    assertThat(quinary.eval(emptyActivation()).toString())
        .isEqualTo(generic.eval(emptyActivation()).toString());
  }

  @Test
  void nonReceiverTraitMismatchProducesNoSuchOverload() {
    AtomicInteger boundCalls = new AtomicInteger();
    EvalUnary call =
        new EvalUnary(
            CALL_ID,
            "dispatch",
            "receiver_trait",
            Interpretable.newConstValue(1, intOf(1)),
            Trait.ReceiverType,
            value -> {
              boundCalls.incrementAndGet();
              return True;
            });

    Val result = call.eval(emptyActivation());

    assertThat(result).isInstanceOf(Err.class);
    assertThat(result.toString()).contains("no such overload");
    assertThat(boundCalls).hasValue(0);
  }

  @TestFactory
  Stream<DynamicTest> receiverFallbackPreservesEveryRelevantTailArity() {
    return IntStream.rangeClosed(0, 5)
        .mapToObj(
            tailArity ->
                DynamicTest.dynamicTest(
                    "tail arity " + tailArity,
                    () -> {
                      RecordingReceiver receiver = new RecordingReceiver();
                      Interpretable[] args = receiverArgs(receiver, tailArity);
                      Interpretable call = receiverCall(args);

                      assertThat(call.eval(emptyActivation())).isEqualTo(intOf(tailArity));
                      assertThat(receiver.invocations).isOne();
                      assertThat(receiver.function).isEqualTo("receive");
                      assertThat(receiver.overload).isEqualTo("receive_overload");
                      assertThat(receiver.args)
                          .extracting(Val::intValue)
                          .containsExactlyElementsOf(
                              IntStream.rangeClosed(1, tailArity)
                                  .mapToLong(i -> i)
                                  .boxed()
                                  .toList());
                      assertCallArgumentsAndCost(call, args);
                    }));
  }

  @TestFactory
  Stream<DynamicTest> genericCallsReturnFirstErrorOrUnknownWithoutInvokingTheOperation() {
    return Stream.of(newErr("argument failed"), unknownOf(999))
        .flatMap(
            terminal ->
                IntStream.range(0, 4)
                    .mapToObj(
                        position ->
                            DynamicTest.dynamicTest(
                                terminal.getClass().getSimpleName() + " at argument " + position,
                                () -> assertGenericTerminalArgument(terminal, position))));
  }

  @TestFactory
  Stream<DynamicTest> ternaryCallsReturnFirstErrorOrUnknownWithoutInvokingTheOperation() {
    return Stream.of(newErr("argument failed"), unknownOf(999))
        .flatMap(
            terminal ->
                IntStream.range(0, 3)
                    .mapToObj(
                        position ->
                            DynamicTest.dynamicTest(
                                terminal.getClass().getSimpleName() + " at argument " + position,
                                () -> assertTernaryTerminalArgument(terminal, position))));
  }

  @TestFactory
  Stream<DynamicTest> fixedAritiesRetainGenericVarArgsFallback() {
    return IntStream.rangeClosed(3, 5)
        .mapToObj(
            arity ->
                DynamicTest.dynamicTest(
                    "arity " + arity,
                    () -> {
                      String function = "generic_" + arity;
                      String overload = function + "_overload";
                      Dispatcher dispatcher = newDispatcher();
                      dispatcher.add(Overload.function(overload, args -> intOf(args.length)));

                      Interpretable call =
                          checkedCall(dispatcher, function, arity, reference(overload));

                      assertThat(call).isInstanceOf(EvalVarArgs.class);
                      assertThat(call.eval(emptyActivation())).isEqualTo(intOf(arity));
                    }));
  }

  @TestFactory
  Stream<DynamicTest> quaternaryCallsReturnFirstErrorOrUnknownWithoutInvokingTheOperation() {
    return Stream.of(newErr("argument failed"), unknownOf(999))
        .flatMap(
            terminal ->
                IntStream.range(0, 4)
                    .mapToObj(
                        position ->
                            DynamicTest.dynamicTest(
                                terminal.getClass().getSimpleName() + " at argument " + position,
                                () -> assertQuaternaryTerminalArgument(terminal, position))));
  }

  @TestFactory
  Stream<DynamicTest> quinaryCallsReturnFirstErrorOrUnknownWithoutInvokingTheOperation() {
    return Stream.of(newErr("argument failed"), unknownOf(999))
        .flatMap(
            terminal ->
                IntStream.range(0, 5)
                    .mapToObj(
                        position ->
                            DynamicTest.dynamicTest(
                                terminal.getClass().getSimpleName() + " at argument " + position,
                                () -> assertQuinaryTerminalArgument(terminal, position))));
  }

  @Test
  void binaryCallEvaluatesBothArgumentsBeforeReturningTheLeftError() {
    List<Integer> evaluationOrder = new ArrayList<>();
    AtomicInteger boundCalls = new AtomicInteger();
    Val error = newErr("left failed");
    EvalBinary call =
        new EvalBinary(
            CALL_ID,
            "binary",
            "binary_overload",
            recordingArg(0, error, evaluationOrder),
            recordingArg(1, intOf(1), evaluationOrder),
            null,
            (left, right) -> {
              boundCalls.incrementAndGet();
              return True;
            });

    assertThat(call.eval(emptyActivation())).isSameAs(error);
    assertThat(evaluationOrder).containsExactly(0, 1);
    assertThat(boundCalls).hasValue(0);
  }

  @Test
  void checkedCallWithWrongActivationTypeReturnsCelError() {
    String function = "checked_runtime_type";
    String overload = function + "_int";
    var env =
        newEnv(
            declarations(
                newVar("value", Int),
                newFunction(function, newOverload(overload, List.of(Int), Int))));
    AstIssuesTuple ast = env.compile(function + "(value)");
    assertThat(ast.hasIssues()).isFalse();
    Program program =
        env.program(
            ast.getAst(),
            functions(Overload.unary(overload, Trait.NegatorType, ignored -> intOf(1))));

    Val result = program.eval(mapOf("value", "not an int")).getVal();

    assertThat(result).isInstanceOf(Err.class);
    assertThat(result.toString()).contains("no such overload");
  }

  @TestFactory
  Stream<DynamicTest> callShapeCostAndStateTrackingRemainStableAcrossArities() {
    return IntStream.rangeClosed(0, 5)
        .mapToObj(
            arity ->
                DynamicTest.dynamicTest(
                    "arity " + arity,
                    () -> {
                      String function = "tracked_" + arity;
                      String overload = function + "_overload";
                      Dispatcher dispatcher = newDispatcher();
                      dispatcher.add(operation(overload, arity, arity));

                      Interpretable call =
                          checkedCall(dispatcher, function, arity, reference(overload));
                      assertCallShape(call, function, overload, arity);
                      assertThat(estimateCost(call)).isEqualTo(costOf(1, 1));

                      EvalState state = newEvalState();
                      Interpretable tracked =
                          checkedCall(
                              dispatcher, function, arity, reference(overload), trackState(state));
                      Val result = tracked.eval(emptyActivation());

                      assertThat(result).isEqualTo(intOf(arity));
                      assertThat(state.value(CALL_ID)).isEqualTo(result);
                      for (int i = 0; i < arity; i++) {
                        assertThat(state.value(CALL_ID + i + 1)).isEqualTo(intOf(i + 1));
                      }
                      assertThat(estimateCost(tracked)).isEqualTo(costOf(1, 1));
                    }));
  }

  private static void assertCheckedNameFallback(Reference reference) {
    String function = "checked_name_fallback";
    Dispatcher dispatcher = newDispatcher();
    dispatcher.add(
        Overload.unary(function, ignored -> intOf(42)),
        Overload.unary("unused_first", ignored -> intOf(1)),
        Overload.unary("unused_second", ignored -> intOf(2)));

    Interpretable interpretable = checkedCall(dispatcher, function, 1, reference);

    assertThat(interpretable.eval(emptyActivation())).isEqualTo(intOf(42));
    assertCallShape(interpretable, function, "", 1);
  }

  private static void assertGenericTerminalArgument(Val terminal, int terminalPosition) {
    List<Integer> evaluationOrder = new ArrayList<>();
    AtomicInteger boundCalls = new AtomicInteger();
    Interpretable[] args = new Interpretable[4];
    for (int i = 0; i < args.length; i++) {
      args[i] = recordingArg(i, i == terminalPosition ? terminal : intOf(i), evaluationOrder);
    }
    EvalVarArgs call =
        new EvalVarArgs(
            CALL_ID,
            "generic",
            "generic_overload",
            args,
            null,
            values -> {
              boundCalls.incrementAndGet();
              return True;
            });

    assertThat(call.eval(emptyActivation())).isSameAs(terminal);
    assertThat(evaluationOrder)
        .containsExactlyElementsOf(IntStream.rangeClosed(0, terminalPosition).boxed().toList());
    assertThat(boundCalls).hasValue(0);
  }

  private static void assertTernaryTerminalArgument(Val terminal, int terminalPosition) {
    List<Integer> evaluationOrder = new ArrayList<>();
    AtomicInteger boundCalls = new AtomicInteger();
    Interpretable[] args = new Interpretable[3];
    for (int i = 0; i < args.length; i++) {
      args[i] = recordingArg(i, i == terminalPosition ? terminal : intOf(i), evaluationOrder);
    }
    EvalTernary call =
        new EvalTernary(
            CALL_ID,
            "ternary",
            "ternary_overload",
            args[0],
            args[1],
            args[2],
            null,
            (first, second, third) -> {
              boundCalls.incrementAndGet();
              return True;
            });

    assertThat(call.eval(emptyActivation())).isSameAs(terminal);
    assertThat(evaluationOrder)
        .containsExactlyElementsOf(IntStream.rangeClosed(0, terminalPosition).boxed().toList());
    assertThat(boundCalls).hasValue(0);
  }

  private static void assertQuaternaryTerminalArgument(Val terminal, int terminalPosition) {
    List<Integer> evaluationOrder = new ArrayList<>();
    AtomicInteger boundCalls = new AtomicInteger();
    Interpretable[] args = new Interpretable[4];
    for (int i = 0; i < args.length; i++) {
      args[i] = recordingArg(i, i == terminalPosition ? terminal : intOf(i), evaluationOrder);
    }
    EvalQuaternary call =
        new EvalQuaternary(
            CALL_ID,
            "quaternary",
            "quaternary_overload",
            args[0],
            args[1],
            args[2],
            args[3],
            null,
            (first, second, third, fourth) -> {
              boundCalls.incrementAndGet();
              return True;
            });

    assertThat(call.eval(emptyActivation())).isSameAs(terminal);
    assertThat(evaluationOrder)
        .containsExactlyElementsOf(IntStream.rangeClosed(0, terminalPosition).boxed().toList());
    assertThat(boundCalls).hasValue(0);
  }

  private static void assertQuinaryTerminalArgument(Val terminal, int terminalPosition) {
    List<Integer> evaluationOrder = new ArrayList<>();
    AtomicInteger boundCalls = new AtomicInteger();
    Interpretable[] args = new Interpretable[5];
    for (int i = 0; i < args.length; i++) {
      args[i] = recordingArg(i, i == terminalPosition ? terminal : intOf(i), evaluationOrder);
    }
    EvalQuinary call =
        new EvalQuinary(
            CALL_ID,
            "quinary",
            "quinary_overload",
            args[0],
            args[1],
            args[2],
            args[3],
            args[4],
            null,
            (first, second, third, fourth, fifth) -> {
              boundCalls.incrementAndGet();
              return True;
            });

    assertThat(call.eval(emptyActivation())).isSameAs(terminal);
    assertThat(evaluationOrder)
        .containsExactlyElementsOf(IntStream.rangeClosed(0, terminalPosition).boxed().toList());
    assertThat(boundCalls).hasValue(0);
  }

  private static Interpretable receiverCall(Interpretable[] args) {
    return switch (args.length) {
      case 1 -> new EvalUnary(CALL_ID, "receive", "receive_overload", args[0], null, null);
      case 2 ->
          new EvalBinary(CALL_ID, "receive", "receive_overload", args[0], args[1], null, null);
      default -> new EvalReceiverVarArgs(CALL_ID, "receive", "receive_overload", args);
    };
  }

  private static Interpretable[] receiverArgs(RecordingReceiver receiver, int tailArity) {
    Interpretable[] args = new Interpretable[tailArity + 1];
    args[0] = Interpretable.newConstValue(1, receiver);
    for (int i = 1; i < args.length; i++) {
      args[i] = Interpretable.newConstValue(i + 1, intOf(i));
    }
    return args;
  }

  private static void assertCallArgumentsAndCost(
      Interpretable interpretable, Interpretable[] expectedArgs) {
    assertThat(interpretable).isInstanceOf(InterpretableCall.class);
    InterpretableCall call = (InterpretableCall) interpretable;
    assertThat(call.args()).containsExactly(expectedArgs);
    assertThat(estimateCost(call)).isEqualTo(costOf(1, 1));
  }

  private static Interpretable recordingArg(int index, Val value, List<Integer> evaluationOrder) {
    return new Interpretable() {
      @Override
      public long id() {
        return index;
      }

      @Override
      public Val eval(Activation activation) {
        evaluationOrder.add(index);
        return value;
      }
    };
  }

  private static void assertCallShape(
      Interpretable interpretable, String function, String overload, int arity) {
    assertBasicCallShape(interpretable, function, overload, arity);
    InterpretableCall call = (InterpretableCall) interpretable;
    assertThat(call.args())
        .extracting(Interpretable::id)
        .containsExactlyElementsOf(
            IntStream.range(0, arity).mapToLong(i -> CALL_ID + i + 1).boxed().toList());
  }

  private static void assertBasicCallShape(
      Interpretable interpretable, String function, String overload, int arity) {
    assertThat(interpretable).isInstanceOf(InterpretableCall.class);
    InterpretableCall call = (InterpretableCall) interpretable;
    assertThat(call.function()).isEqualTo(function);
    assertThat(call.overloadID()).isEqualTo(overload);
    assertThat(call.args()).hasSize(arity);
  }

  private static Interpretable checkedCall(
      Dispatcher dispatcher, String function, int arity, Reference reference) {
    return checkedCall(dispatcher, function, arity, reference, new InterpretableDecorator[0]);
  }

  private static Interpretable checkedCall(
      Dispatcher dispatcher,
      String function,
      int arity,
      Reference reference,
      InterpretableDecorator... decorators) {
    return interpreter(dispatcher)
        .newInterpretable(
            callExpr(function, arity), mapOf(CALL_ID, reference), emptyMap(), decorators);
  }

  private static Interpretable uncheckedCall(Interpreter interpreter, String expression) {
    ParseResult parsed = Parser.parseAllMacros(Source.newTextSource(expression));
    assertThat(parsed.hasErrors()).withFailMessage(parsed.getErrors()::toDisplayString).isFalse();
    return interpreter.newUncheckedInterpretable(parsed.getExpr());
  }

  private static Interpreter interpreter(Dispatcher dispatcher) {
    TypeRegistry registry = newRegistry();
    AttributeFactory attributes = newAttributeFactory(defaultContainer, registry, registry);
    return newInterpreter(dispatcher, defaultContainer, registry, registry, attributes);
  }

  private static Expr callExpr(String function, int arity) {
    Expr.Call.Builder call = Expr.Call.newBuilder().setFunction(function);
    for (int i = 0; i < arity; i++) {
      call.addArgs(
          Expr.newBuilder()
              .setId(CALL_ID + i + 1)
              .setConstExpr(Constant.newBuilder().setInt64Value(i + 1)));
    }
    return Expr.newBuilder().setId(CALL_ID).setCallExpr(call).build();
  }

  private static Reference reference(String overload) {
    return Reference.newBuilder().addOverloadId(overload).build();
  }

  private static Overload operation(String name, int arity, long result) {
    return switch (arity) {
      case 0 -> Overload.function(name, args -> intOf(result));
      case 1 -> Overload.unary(name, arg -> intOf(result));
      case 2 -> Overload.binary(name, (left, right) -> intOf(result));
      case 3 -> Overload.ternary(name, (first, second, third) -> intOf(result));
      case 4 -> Overload.quaternary(name, (first, second, third, fourth) -> intOf(result));
      case 5 -> Overload.quinary(name, (first, second, third, fourth, fifth) -> intOf(result));
      default -> Overload.function(name, args -> intOf(result));
    };
  }

  private static final Type RECEIVER_TYPE =
      new Type() {
        @Override
        public boolean hasTrait(Trait trait) {
          return trait == Trait.ReceiverType;
        }

        @Override
        public String typeName() {
          return "recording_receiver";
        }

        @Override
        public TypeEnum typeEnum() {
          return TypeEnum.Object;
        }

        @Override
        public <T> T convertToNative(Class<T> typeDesc) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Val convertToType(Type typeValue) {
          return this;
        }

        @Override
        public Val equal(Val other) {
          return boolOf(other == this);
        }

        @Override
        public Type type() {
          return this;
        }

        @Override
        public Object value() {
          return typeName();
        }

        @Override
        public boolean booleanValue() {
          throw new UnsupportedOperationException();
        }

        @Override
        public long intValue() {
          throw new UnsupportedOperationException();
        }

        @Override
        public double doubleValue() {
          throw new UnsupportedOperationException();
        }
      };

  private static final class RecordingReceiver extends BaseVal implements Receiver {
    private int invocations;
    private String function;
    private String overload;
    private List<Val> args = List.of();

    @Override
    public <T> T convertToNative(Class<T> typeDesc) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Val convertToType(Type typeValue) {
      return this;
    }

    @Override
    public Val equal(Val other) {
      return boolOf(other == this);
    }

    @Override
    public Type type() {
      return RECEIVER_TYPE;
    }

    @Override
    public Object value() {
      return this;
    }

    @Override
    public Val receive(String function, String overload, Val... args) {
      invocations++;
      this.function = function;
      this.overload = overload;
      this.args = Arrays.asList(args.clone());
      return intOf(args.length);
    }
  }
}
