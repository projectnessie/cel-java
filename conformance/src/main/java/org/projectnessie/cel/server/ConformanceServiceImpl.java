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
package org.projectnessie.cel.server;

import static org.projectnessie.cel.CEL.astToCheckedExpr;
import static org.projectnessie.cel.CEL.astToParsedExpr;
import static org.projectnessie.cel.CEL.checkedExprToAst;
import static org.projectnessie.cel.CEL.parsedExprToAst;
import static org.projectnessie.cel.Env.newCustomEnv;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.clearMacros;
import static org.projectnessie.cel.EnvOption.container;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.Library.StdLib;
import static org.projectnessie.cel.common.types.BoolT.BoolType;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.BoolT.boolOf;
import static org.projectnessie.cel.common.types.BytesT.BytesType;
import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.DoubleT.DoubleType;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.DurationT.DurationType;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.ListT.ListType;
import static org.projectnessie.cel.common.types.MapT.MapType;
import static org.projectnessie.cel.common.types.NullT.NullType;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.TimestampType;
import static org.projectnessie.cel.common.types.TypeT.TypeType;
import static org.projectnessie.cel.common.types.TypeT.newObjectTypeValue;
import static org.projectnessie.cel.common.types.UintT.UintType;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;

import com.google.api.expr.v1alpha1.CheckRequest;
import com.google.api.expr.v1alpha1.CheckResponse;
import com.google.api.expr.v1alpha1.ConformanceServiceGrpc.ConformanceServiceImplBase;
import com.google.api.expr.v1alpha1.ErrorSet;
import com.google.api.expr.v1alpha1.EvalRequest;
import com.google.api.expr.v1alpha1.EvalResponse;
import com.google.api.expr.v1alpha1.ExprValue;
import com.google.api.expr.v1alpha1.IssueDetails;
import com.google.api.expr.v1alpha1.ListValue;
import com.google.api.expr.v1alpha1.MapValue;
import com.google.api.expr.v1alpha1.MapValue.Entry;
import com.google.api.expr.v1alpha1.ParseRequest;
import com.google.api.expr.v1alpha1.ParseResponse;
import com.google.api.expr.v1alpha1.SourcePosition;
import com.google.api.expr.v1alpha1.UnknownSet;
import com.google.api.expr.v1alpha1.Value;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.rpc.Code;
import com.google.rpc.Status;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.projectnessie.cel.Ast;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.Program.EvalResult;
import org.projectnessie.cel.common.CELError;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.TypeT;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Mapper;

public class ConformanceServiceImpl extends ConformanceServiceImplBase {

  private boolean verboseEvalErrors;

  public void setVerboseEvalErrors(boolean verboseEvalErrors) {
    this.verboseEvalErrors = verboseEvalErrors;
  }

  @Override
  public void parse(
      ParseRequest request, io.grpc.stub.StreamObserver<ParseResponse> responseObserver) {
    try {
      String sourceText = request.getCelSource();
      if (sourceText.trim().isEmpty()) {
        throw new IllegalArgumentException("No source code.");
      }

      // NOTE: syntax_version isn't currently used
      List<EnvOption> parseOptions = new ArrayList<>();
      if (request.getDisableMacros()) {
        parseOptions.add(clearMacros());
      }

      Env env = newEnv(parseOptions.toArray(new EnvOption[0]));
      AstIssuesTuple astIss = env.parse(sourceText);

      ParseResponse.Builder response = ParseResponse.newBuilder();
      if (!astIss.hasIssues()) {
        // Success
        response.setParsedExpr(astToParsedExpr(astIss.getAst()));
      } else {
        // Failure
        appendErrors(astIss.getIssues().getErrors(), response::addIssuesBuilder);
      }

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(
          io.grpc.Status.fromCode(io.grpc.Status.Code.UNKNOWN)
              .withDescription(stacktrace(e))
              .asException());
    }
  }

  @Override
  public void check(
      CheckRequest request, io.grpc.stub.StreamObserver<CheckResponse> responseObserver) {
    try {
      // Build the environment.
      List<EnvOption> checkOptions = new ArrayList<>();
      if (!request.getNoStdEnv()) {
        checkOptions.add(StdLib());
      }

      checkOptions.add(container(request.getContainer()));
      checkOptions.add(declarations(request.getTypeEnvList()));
      checkOptions.add(
          types(
              com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance(),
              com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                  .getDefaultInstance()));
      Env env = newCustomEnv(checkOptions.toArray(new EnvOption[0]));

      // Check the expression.
      AstIssuesTuple astIss = env.check(parsedExprToAst(request.getParsedExpr()));
      CheckResponse.Builder resp = CheckResponse.newBuilder();

      if (!astIss.hasIssues()) {
        // Success
        resp.setCheckedExpr(astToCheckedExpr(astIss.getAst()));
      } else {
        // Failure
        appendErrors(astIss.getIssues().getErrors(), resp::addIssuesBuilder);
      }

      responseObserver.onNext(resp.build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(
          io.grpc.Status.fromCode(io.grpc.Status.Code.UNKNOWN)
              .withDescription(stacktrace(e))
              .asException());
    }
  }

  @Override
  public void eval(
      EvalRequest request, io.grpc.stub.StreamObserver<EvalResponse> responseObserver) {
    try {
      Env env =
          newEnv(
              container(request.getContainer()),
              types(
                  com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes
                      .getDefaultInstance(),
                  com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                      .getDefaultInstance()));

      Program prg;
      Ast ast;

      switch (request.getExprKindCase()) {
        case PARSED_EXPR:
          ast = parsedExprToAst(request.getParsedExpr());
          break;
        case CHECKED_EXPR:
          ast = checkedExprToAst(request.getCheckedExpr());
          break;
        default:
          throw new IllegalArgumentException("No expression.");
      }

      prg = env.program(ast);

      Map<String, Object> args = new HashMap<>();
      request
          .getBindingsMap()
          .forEach(
              (name, exprValue) -> {
                Val refVal = exprValueToRefValue(env.getTypeAdapter(), exprValue);
                args.put(name, refVal);
              });

      // NOTE: the EvalState is currently discarded
      EvalResult res = prg.eval(args);
      ExprValue resultExprVal;
      if (!isError(res.getVal())) {
        resultExprVal = refValueToExprValue(res.getVal());
      } else {
        Err err = (Err) res.getVal();

        if (verboseEvalErrors) {
          System.err.printf(
              "%n" + "Eval error (not necessarily a bug!!!):%n" + "  error: %s%n" + "%s",
              err, err.hasCause() ? (stacktrace(err.toRuntimeException()) + "\n") : "");
        }

        resultExprVal =
            ExprValue.newBuilder()
                .setError(
                    ErrorSet.newBuilder().addErrors(Status.newBuilder().setMessage(err.toString())))
                .build();
      }

      EvalResponse.Builder resp = EvalResponse.newBuilder().setResult(resultExprVal);

      responseObserver.onNext(resp.build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(
          io.grpc.Status.fromCode(io.grpc.Status.Code.UNKNOWN)
              .withDescription(stacktrace(e))
              .asException());
    }
  }

  static String stacktrace(Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    pw.flush();
    return sw.toString();
  }

  /**
   * appendErrors converts the errors from errs to Status messages and appends them to the list of
   * issues.
   */
  static void appendErrors(List<CELError> errs, Supplier<Status.Builder> builderSupplier) {
    errs.forEach(e -> errToStatus(e, IssueDetails.Severity.ERROR, builderSupplier.get()));
  }

  /** ErrToStatus converts an Error to a Status message with the given severity. */
  static void errToStatus(CELError e, IssueDetails.Severity severity, Status.Builder status) {
    IssueDetails.Builder detail =
        IssueDetails.newBuilder()
            .setSeverity(severity)
            .setPosition(
                SourcePosition.newBuilder()
                    .setLine(e.getLocation().line())
                    .setColumn(e.getLocation().column())
                    .build());

    status
        .setCode(Code.INVALID_ARGUMENT_VALUE)
        .setMessage(e.getMessage())
        .addDetails(Any.pack(detail.build()));
  }

  /** RefValueToExprValue converts between ref.Val and exprpb.ExprValue. */
  static ExprValue refValueToExprValue(Val res) {
    if (isUnknown(res)) {
      return ExprValue.newBuilder()
          .setUnknown(UnknownSet.newBuilder().addExprs(res.intValue()))
          .build();
    }
    Value v = refValueToValue(res);
    return ExprValue.newBuilder().setValue(v).build();
  }

  // TODO(jimlarson): The following conversion code should be moved to
  //  common/types/provider.go and consolidated/refactored as appropriate.
  //  In particular, make judicious use of types.NativeToValue().

  static final Map<String, TypeT> typeNameToTypeValue = new HashMap<>();

  static {
    typeNameToTypeValue.put("bool", BoolType);
    typeNameToTypeValue.put("bytes", BytesType);
    typeNameToTypeValue.put("double", DoubleType);
    typeNameToTypeValue.put("null_type", NullType);
    typeNameToTypeValue.put("int", IntType);
    typeNameToTypeValue.put("list", ListType);
    typeNameToTypeValue.put("map", MapType);
    typeNameToTypeValue.put("string", StringType);
    typeNameToTypeValue.put("type", TypeType);
    typeNameToTypeValue.put("uint", UintType);
  }

  /**
   * RefValueToValue converts between ref.Val and Value. The ref.Val must not be error or unknown.
   */
  static Value refValueToValue(Val res) {
    if (res.type() == BoolType) {
      return Value.newBuilder().setBoolValue(res.booleanValue()).build();
    } else if (res.type() == BytesType) {
      return Value.newBuilder().setBytesValue(res.convertToNative(ByteString.class)).build();
    } else if (res.type() == DoubleType) {
      return Value.newBuilder().setDoubleValue(res.convertToNative(Double.class)).build();
    } else if (res.type() == IntType) {
      return Value.newBuilder().setInt64Value(res.intValue()).build();
    } else if (res.type() == NullType) {
      return Value.newBuilder().setNullValueValue(0).build();
    } else if (res.type() == StringType) {
      return Value.newBuilder().setStringValue(res.value().toString()).build();
    } else if (res.type() == TypeType) {
      return Value.newBuilder().setTypeValue(((TypeT) res).typeName()).build();
    } else if (res.type() == UintType) {
      return Value.newBuilder().setUint64Value(res.intValue()).build();
    } else if (res.type() == DurationType) {
      Duration d = res.convertToNative(Duration.class);
      Any any = Any.pack(d);
      return Value.newBuilder().setObjectValue(any).build();
    } else if (res.type() == TimestampType) {
      Timestamp t = res.convertToNative(Timestamp.class);
      Any any = Any.pack(t);
      return Value.newBuilder().setObjectValue(any).build();
    } else if (res.type() == ListType) {
      Lister l = (Lister) res;
      ListValue.Builder elts = ListValue.newBuilder();
      for (IteratorT i = l.iterator(); i.hasNext() == True; ) {
        Val v = i.next();
        elts.addValues(refValueToValue(v));
      }
      return Value.newBuilder().setListValue(elts).build();
    } else if (res.type() == MapType) {
      Mapper m = (Mapper) res;
      MapValue.Builder elems = MapValue.newBuilder();
      for (IteratorT i = m.iterator(); i.hasNext() == True; ) {
        Val k = i.next();
        Val v = m.get(k);
        Value kv = refValueToValue(k);
        Value vv = refValueToValue(v);
        elems.addEntriesBuilder().setKey(kv).setValue(vv);
      }
      return Value.newBuilder().setMapValue(elems).build();
    } else {
      // Object type
      Message pb = (Message) res.value();
      Value.Builder v = Value.newBuilder();
      // Somehow the conformance tests
      if (pb instanceof ListValue) {
        v.setListValue((ListValue) pb);
      } else if (pb instanceof MapValue) {
        v.setMapValue((MapValue) pb);
      } else {
        v.setObjectValue(Any.pack(pb));
      }
      return v.build();
    }
  }

  /** ExprValueToRefValue converts between exprpb.ExprValue and ref.Val. */
  static Val exprValueToRefValue(TypeAdapter adapter, ExprValue ev) {
    switch (ev.getKindCase()) {
      case VALUE:
        return valueToRefValue(adapter, ev.getValue());
      case ERROR:
        // An error ExprValue is a repeated set of rpcpb.Status
        // messages, with no convention for the status details.
        // To convert this to a types.Err, we need to convert
        // these Status messages to a single string, and be
        // able to decompose that string on output so we can
        // round-trip arbitrary ExprValue messages.
        // TODO(jimlarson) make a convention for this.
        return newErr("XXX add details later");
      case UNKNOWN:
        return unknownOf(ev.getUnknown().getExprs(0));
    }
    throw new IllegalArgumentException("unknown ExprValue kind " + ev.getKindCase());
  }

  /** ValueToRefValue converts between exprpb.Value and ref.Val. */
  static Val valueToRefValue(TypeAdapter adapter, Value v) {
    switch (v.getKindCase()) {
      case NULL_VALUE:
        return NullT.NullValue;
      case BOOL_VALUE:
        return boolOf(v.getBoolValue());
      case INT64_VALUE:
        return intOf(v.getInt64Value());
      case UINT64_VALUE:
        return uintOf(v.getUint64Value());
      case DOUBLE_VALUE:
        return doubleOf(v.getDoubleValue());
      case STRING_VALUE:
        return stringOf(v.getStringValue());
      case BYTES_VALUE:
        return bytesOf(v.getBytesValue().toByteArray());
      case OBJECT_VALUE:
        Any any = v.getObjectValue();
        return adapter.nativeToValue(any);
      case MAP_VALUE:
        MapValue m = v.getMapValue();
        Map<Val, Val> entries = new HashMap<>();
        for (Entry entry : m.getEntriesList()) {
          Val key = valueToRefValue(adapter, entry.getKey());
          Val pb = valueToRefValue(adapter, entry.getValue());
          entries.put(key, pb);
        }
        return adapter.nativeToValue(entries);
      case LIST_VALUE:
        ListValue l = v.getListValue();
        List<Val> elts =
            l.getValuesList().stream()
                .map(el -> valueToRefValue(adapter, el))
                .collect(Collectors.toList());
        return adapter.nativeToValue(elts);
      case TYPE_VALUE:
        String typeName = v.getTypeValue();
        TypeT tv = typeNameToTypeValue.get(typeName);
        if (tv != null) {
          return tv;
        }
        return newObjectTypeValue(typeName);
      default:
        throw new IllegalArgumentException("unknown value " + v.getKindCase());
    }
  }
}
