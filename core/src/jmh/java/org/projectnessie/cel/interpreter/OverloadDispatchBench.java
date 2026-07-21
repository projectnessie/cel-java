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

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.TypeT.newObjectTypeValue;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Receiver;
import org.projectnessie.cel.common.types.traits.Trait;
import org.projectnessie.cel.extension.MathLib;
import org.projectnessie.cel.extension.StringsLib;
import org.projectnessie.cel.interpreter.Interpretable.EvalBinary;
import org.projectnessie.cel.interpreter.Interpretable.EvalConst;
import org.projectnessie.cel.interpreter.Interpretable.EvalQuaternary;
import org.projectnessie.cel.interpreter.Interpretable.EvalQuinary;
import org.projectnessie.cel.interpreter.Interpretable.EvalReceiverVarArgs;
import org.projectnessie.cel.interpreter.Interpretable.EvalTernary;
import org.projectnessie.cel.interpreter.Interpretable.EvalUnary;
import org.projectnessie.cel.interpreter.Interpretable.EvalVarArgs;
import org.projectnessie.cel.interpreter.Interpretable.EvalZeroArity;
import org.projectnessie.cel.interpreter.functions.BinaryOp;
import org.projectnessie.cel.interpreter.functions.FunctionOp;
import org.projectnessie.cel.interpreter.functions.QuaternaryOp;
import org.projectnessie.cel.interpreter.functions.QuinaryOp;
import org.projectnessie.cel.interpreter.functions.TernaryOp;
import org.projectnessie.cel.interpreter.functions.UnaryOp;

@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class OverloadDispatchBench {

  private static final String FUNCTION = "benchmark";
  private static final String OVERLOAD = "benchmark_overload";
  private static final Type RECEIVER_TYPE =
      newObjectTypeValue("benchmark_receiver", Trait.ReceiverType);

  @State(Scope.Thread)
  public static class DispatchState {
    Activation activation;

    FunctionOp zeroOp;
    UnaryOp unaryOp;
    BinaryOp binaryOp;
    TernaryOp ternaryOp;
    QuaternaryOp quaternaryOp;
    QuinaryOp quinaryOp;
    FunctionOp functionOp;

    Val unaryArg;
    Val binaryLhs;
    Val binaryRhs;
    Val[] emptyArgs;
    Val[] arity3Args;
    Val[] arity4Args;
    Val[] arity5Args;

    BenchmarkReceiver receiver;
    Val[] receiverTail0;
    Val[] receiverTail1;
    Val[] receiverTail2;
    Val[] receiverTail3;
    Val[] receiverTail4;

    Interpretable evalZero;
    Interpretable evalUnary;
    Interpretable evalBinary;
    Interpretable evalVarArgs3;
    Interpretable evalTernary3;
    Interpretable evalVarArgs4;
    Interpretable evalQuaternary4;
    Interpretable evalVarArgs5;
    Interpretable evalQuinary5;
    Interpretable evalReceiverTail0;
    Interpretable evalReceiverTail1;
    Interpretable evalReceiverTail2;
    Interpretable evalReceiverTail3;
    Interpretable evalReceiverTail4;
    Interpretable evalBoundGenericTraitSuccess3;
    Interpretable evalBoundGenericTraitFallback3;
    Program stringsIndexOfOffset;
    Program stringsReplaceN;
    Program mathGreatest3;
    Program mathGreatest4;
    Program mathGreatest5;

    @Setup
    public void init() {
      activation = emptyActivation();

      zeroOp = values -> True;
      unaryOp = value -> value;
      binaryOp = (lhs, rhs) -> rhs;
      ternaryOp = (first, second, third) -> third;
      quaternaryOp = (first, second, third, fourth) -> fourth;
      quinaryOp = (first, second, third, fourth, fifth) -> fifth;
      functionOp = values -> values[values.length - 1];

      unaryArg = intOf(1);
      binaryLhs = intOf(2);
      binaryRhs = intOf(3);
      emptyArgs = new Val[0];
      arity3Args = new Val[] {intOf(4), intOf(5), intOf(6)};
      arity4Args = new Val[] {intOf(7), intOf(8), intOf(9), intOf(10)};
      arity5Args = new Val[] {intOf(11), intOf(12), intOf(13), intOf(14), intOf(15)};

      receiver = new BenchmarkReceiver();
      receiverTail0 = new Val[0];
      receiverTail1 = new Val[] {intOf(11)};
      receiverTail2 = new Val[] {intOf(12), intOf(13)};
      receiverTail3 = new Val[] {intOf(14), intOf(15), intOf(16)};
      receiverTail4 = new Val[] {intOf(17), intOf(18), intOf(19), intOf(20)};

      evalZero = new EvalZeroArity(1, FUNCTION, OVERLOAD, zeroOp);
      evalUnary = new EvalUnary(2, FUNCTION, OVERLOAD, constant(2, unaryArg), null, unaryOp);
      evalBinary =
          new EvalBinary(
              3,
              FUNCTION,
              OVERLOAD,
              constant(3, binaryLhs),
              constant(4, binaryRhs),
              null,
              binaryOp);
      evalVarArgs3 =
          new EvalVarArgs(5, FUNCTION, OVERLOAD, constants(5, arity3Args), null, functionOp);
      evalTernary3 =
          new EvalTernary(
              6,
              FUNCTION,
              OVERLOAD,
              constant(8, arity3Args[0]),
              constant(9, arity3Args[1]),
              constant(10, arity3Args[2]),
              null,
              ternaryOp);
      evalVarArgs4 =
          new EvalVarArgs(7, FUNCTION, OVERLOAD, constants(11, arity4Args), null, functionOp);
      evalQuaternary4 =
          new EvalQuaternary(
              8,
              FUNCTION,
              OVERLOAD,
              constant(15, arity4Args[0]),
              constant(16, arity4Args[1]),
              constant(17, arity4Args[2]),
              constant(18, arity4Args[3]),
              null,
              quaternaryOp);
      evalVarArgs5 =
          new EvalVarArgs(9, FUNCTION, OVERLOAD, constants(19, arity5Args), null, functionOp);
      evalQuinary5 =
          new EvalQuinary(
              10,
              FUNCTION,
              OVERLOAD,
              constant(24, arity5Args[0]),
              constant(25, arity5Args[1]),
              constant(26, arity5Args[2]),
              constant(27, arity5Args[3]),
              constant(28, arity5Args[4]),
              null,
              quinaryOp);

      evalReceiverTail0 = new EvalUnary(7, FUNCTION, OVERLOAD, constant(12, receiver), null, null);
      evalReceiverTail1 =
          new EvalBinary(
              8,
              FUNCTION,
              OVERLOAD,
              constant(13, receiver),
              constant(14, receiverTail1[0]),
              null,
              null);
      evalReceiverTail2 =
          new EvalReceiverVarArgs(9, FUNCTION, OVERLOAD, receiverArgs(15, receiver, receiverTail2));
      evalReceiverTail3 =
          new EvalReceiverVarArgs(
              10, FUNCTION, OVERLOAD, receiverArgs(18, receiver, receiverTail3));
      evalReceiverTail4 =
          new EvalReceiverVarArgs(
              11, FUNCTION, OVERLOAD, receiverArgs(22, receiver, receiverTail4));

      var receiverArgs3 = receiverArgs(27, receiver, receiverTail2);
      evalBoundGenericTraitSuccess3 =
          new EvalVarArgs(12, FUNCTION, OVERLOAD, receiverArgs3, Trait.ReceiverType, functionOp);
      evalBoundGenericTraitFallback3 =
          new EvalVarArgs(13, FUNCTION, OVERLOAD, receiverArgs3, Trait.AdderType, functionOp);

      var env =
          Env.newCustomEnv(
              ProtoTypeRegistry.newRegistry(),
              List.of(Library.StdLib(), StringsLib.strings(), MathLib.math()));
      stringsIndexOfOffset = compile(env, "'tacocat'.indexOf('a', 3)");
      stringsReplaceN = compile(env, "'hello hello'.replace('he', 'we', 1)");
      mathGreatest3 = compile(env, "math.greatest(5, 10, 3)");
      mathGreatest4 = compile(env, "math.greatest(5, 10, 3, 8)");
      mathGreatest5 = compile(env, "math.greatest(5, 10, 3, 8, 7)");
    }

    private static EvalConst constant(long id, Val value) {
      return new EvalConst(id, value);
    }

    private static Interpretable[] constants(long firstId, Val[] values) {
      var constants = new Interpretable[values.length];
      for (int i = 0; i < values.length; i++) {
        constants[i] = constant(firstId + i, values[i]);
      }
      return constants;
    }

    private static Interpretable[] receiverArgs(
        long firstId, BenchmarkReceiver receiver, Val[] tail) {
      var values = new Val[tail.length + 1];
      values[0] = receiver;
      System.arraycopy(tail, 0, values, 1, tail.length);
      return constants(firstId, values);
    }

    private static Program compile(Env env, String expression) {
      var ast = env.compile(expression);
      if (ast.hasIssues()) {
        throw new IllegalStateException(ast.getIssues().toString());
      }
      return env.program(ast.getAst());
    }
  }

  @Benchmark
  public Val directFunctionZero(DispatchState state) {
    return state.zeroOp.invoke(state.emptyArgs);
  }

  @Benchmark
  public Val evalZero(DispatchState state) {
    return state.evalZero.eval(state.activation);
  }

  @Benchmark
  public Val directUnary(DispatchState state) {
    return state.unaryOp.invoke(state.unaryArg);
  }

  @Benchmark
  public Val evalUnary(DispatchState state) {
    return state.evalUnary.eval(state.activation);
  }

  @Benchmark
  public Val directBinary(DispatchState state) {
    return state.binaryOp.invoke(state.binaryLhs, state.binaryRhs);
  }

  @Benchmark
  public Val evalBinary(DispatchState state) {
    return state.evalBinary.eval(state.activation);
  }

  @Benchmark
  public Val directFunctionArity3(DispatchState state) {
    return state.functionOp.invoke(state.arity3Args);
  }

  @Benchmark
  public Val directTernary(DispatchState state) {
    return state.ternaryOp.invoke(state.arity3Args[0], state.arity3Args[1], state.arity3Args[2]);
  }

  @Benchmark
  public Val evalVarArgsArity3(DispatchState state) {
    return state.evalVarArgs3.eval(state.activation);
  }

  @Benchmark
  public Val evalTernaryArity3(DispatchState state) {
    return state.evalTernary3.eval(state.activation);
  }

  @Benchmark
  public Val directFunctionArity4(DispatchState state) {
    return state.functionOp.invoke(state.arity4Args);
  }

  @Benchmark
  public Val directQuaternary(DispatchState state) {
    return state.quaternaryOp.invoke(
        state.arity4Args[0], state.arity4Args[1], state.arity4Args[2], state.arity4Args[3]);
  }

  @Benchmark
  public Val evalVarArgsArity4(DispatchState state) {
    return state.evalVarArgs4.eval(state.activation);
  }

  @Benchmark
  public Val evalQuaternaryArity4(DispatchState state) {
    return state.evalQuaternary4.eval(state.activation);
  }

  @Benchmark
  public Val directFunctionArity5(DispatchState state) {
    return state.functionOp.invoke(state.arity5Args);
  }

  @Benchmark
  public Val evalVarArgsArity5(DispatchState state) {
    return state.evalVarArgs5.eval(state.activation);
  }

  @Benchmark
  public Val directQuinary(DispatchState state) {
    return state.quinaryOp.invoke(
        state.arity5Args[0],
        state.arity5Args[1],
        state.arity5Args[2],
        state.arity5Args[3],
        state.arity5Args[4]);
  }

  @Benchmark
  public Val evalQuinaryArity5(DispatchState state) {
    return state.evalQuinary5.eval(state.activation);
  }

  @Benchmark
  public Val directReceiveTail0(DispatchState state) {
    return state.receiver.receive(FUNCTION, OVERLOAD, state.receiverTail0);
  }

  @Benchmark
  public Val evalReceiverTail0(DispatchState state) {
    return state.evalReceiverTail0.eval(state.activation);
  }

  @Benchmark
  public Val directReceiveTail1(DispatchState state) {
    return state.receiver.receive(FUNCTION, OVERLOAD, state.receiverTail1);
  }

  @Benchmark
  public Val evalReceiverTail1(DispatchState state) {
    return state.evalReceiverTail1.eval(state.activation);
  }

  @Benchmark
  public Val directReceiveTail2(DispatchState state) {
    return state.receiver.receive(FUNCTION, OVERLOAD, state.receiverTail2);
  }

  @Benchmark
  public Val evalReceiverTail2(DispatchState state) {
    return state.evalReceiverTail2.eval(state.activation);
  }

  @Benchmark
  public Val directReceiveTail3(DispatchState state) {
    return state.receiver.receive(FUNCTION, OVERLOAD, state.receiverTail3);
  }

  @Benchmark
  public Val evalReceiverTail3(DispatchState state) {
    return state.evalReceiverTail3.eval(state.activation);
  }

  @Benchmark
  public Val directReceiveTail4(DispatchState state) {
    return state.receiver.receive(FUNCTION, OVERLOAD, state.receiverTail4);
  }

  @Benchmark
  public Val evalReceiverTail4(DispatchState state) {
    return state.evalReceiverTail4.eval(state.activation);
  }

  @Benchmark
  public Val evalBoundGenericTraitSuccessArity3(DispatchState state) {
    return state.evalBoundGenericTraitSuccess3.eval(state.activation);
  }

  @Benchmark
  public Val evalBoundGenericTraitFallbackArity3(DispatchState state) {
    return state.evalBoundGenericTraitFallback3.eval(state.activation);
  }

  @Benchmark
  public Val evalStringsIndexOfOffset(DispatchState state) {
    return state.stringsIndexOfOffset.eval(state.activation).getVal();
  }

  @Benchmark
  public Val evalStringsReplaceN(DispatchState state) {
    return state.stringsReplaceN.eval(state.activation).getVal();
  }

  @Benchmark
  public Val evalMathGreatest3(DispatchState state) {
    return state.mathGreatest3.eval(state.activation).getVal();
  }

  @Benchmark
  public Val evalMathGreatest4(DispatchState state) {
    return state.mathGreatest4.eval(state.activation).getVal();
  }

  @Benchmark
  public Val evalMathGreatest5(DispatchState state) {
    return state.mathGreatest5.eval(state.activation).getVal();
  }

  private static final class BenchmarkReceiver extends BaseVal implements Receiver {
    @SuppressWarnings("removal")
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
      return other == this ? True : False;
    }

    @Override
    public Type type() {
      return RECEIVER_TYPE;
    }

    @Override
    public Object value() {
      return "benchmark_receiver";
    }

    @Override
    public Val receive(String function, String overload, Val... args) {
      return args.length == 0 ? this : args[args.length - 1];
    }
  }
}
