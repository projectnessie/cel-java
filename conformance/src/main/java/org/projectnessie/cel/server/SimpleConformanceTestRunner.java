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
package org.projectnessie.cel.server;

import com.google.api.expr.conformance.v1alpha1.CheckRequest;
import com.google.api.expr.conformance.v1alpha1.CheckResponse;
import com.google.api.expr.conformance.v1alpha1.ConformanceServiceGrpc;
import com.google.api.expr.conformance.v1alpha1.EvalRequest;
import com.google.api.expr.conformance.v1alpha1.EvalResponse;
import com.google.api.expr.conformance.v1alpha1.ParseRequest;
import com.google.api.expr.conformance.v1alpha1.ParseResponse;
import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Decl;
import com.google.api.expr.v1alpha1.ErrorSet;
import com.google.api.expr.v1alpha1.ExprValue;
import com.google.api.expr.v1alpha1.MapValue;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.Type;
import com.google.api.expr.v1alpha1.UnknownSet;
import com.google.api.expr.v1alpha1.Value;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TypeRegistry;
import dev.cel.expr.conformance.test.SimpleTest;
import dev.cel.expr.conformance.test.SimpleTest.ResultMatcherCase;
import dev.cel.expr.conformance.test.SimpleTestFile;
import dev.cel.expr.conformance.test.SimpleTestSection;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Local replacement for the upstream CEL-Spec simple conformance runner.
 *
 * <p>CEL-Spec v0.25.x ships the simple conformance testdata, but no longer builds the old {@code
 * tests/simple/simple_test} binary. The testdata schema moved to {@code dev.cel.expr.*} generated
 * classes, while CEL-Java's conformance service still exposes the historical {@code
 * com.google.api.expr.v1alpha1} API. The message wire layouts for the fields used by the current
 * curated test set are compatible, so this runner converts between the two Java packages by parsing
 * serialized protobuf bytes into the corresponding service message type.
 */
public final class SimpleConformanceTestRunner {

  private static final TextFormat.Printer TEXT_PRINTER =
      TextFormat.printer().emittingSingleLine(true);

  private final ConformanceServiceGrpc.ConformanceServiceBlockingStub stub;
  private final boolean checkedOnly;
  private final boolean skipCheck;
  private final Set<String> skipTests;
  private final List<String> failures = new ArrayList<>();
  private final Set<String> matchedSkips = new HashSet<>();
  private int total;
  private int skipped;
  private int passed;

  private SimpleConformanceTestRunner(
      ConformanceServiceGrpc.ConformanceServiceBlockingStub stub,
      boolean checkedOnly,
      boolean skipCheck,
      Set<String> skipTests) {
    this.stub = stub;
    this.checkedOnly = checkedOnly;
    this.skipCheck = skipCheck;
    this.skipTests = skipTests;
  }

  public static void main(String[] args) throws Exception {
    Arguments arguments = Arguments.parse(args);
    if (arguments.testFiles.isEmpty()) {
      System.err.println("Usage: SimpleConformanceTestRunner [--skip_test=...] <testdata files>");
      System.exit(2);
    }

    Server server = ServerBuilder.forPort(0).addService(new ConformanceServiceImpl()).build();
    ManagedChannel channel = null;
    try {
      server.start();
      channel =
          ManagedChannelBuilder.forAddress(
                  ConformanceServer.getListenHost(server), server.getPort())
              .usePlaintext()
              .build();

      SimpleConformanceTestRunner runner =
          new SimpleConformanceTestRunner(
              ConformanceServiceGrpc.newBlockingStub(channel),
              arguments.checkedOnly,
              arguments.skipCheck,
              arguments.skipTests);
      int result = runner.run(arguments.testFiles);
      System.exit(result);
    } finally {
      if (channel != null) {
        channel.shutdown();
        channel.awaitTermination(30, TimeUnit.SECONDS);
      }
      server.shutdown();
      server.awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  private int run(List<Path> testFiles) throws IOException {
    for (Path testFile : testFiles) {
      runFile(parseSimpleFile(testFile));
    }

    skipTests.stream()
        .filter(skip -> !matchedSkips.contains(skip))
        .sorted()
        .forEach(skip -> failures.add("Skip did not match any test or section: " + skip));

    System.out.printf(
        "Conformance tests: %d total, %d passed, %d skipped, %d failed%n",
        total, passed, skipped, failures.size());
    failures.forEach(failure -> System.err.println("FAILED: " + failure));
    return failures.isEmpty() ? 0 : 1;
  }

  private static SimpleTestFile parseSimpleFile(Path testFile) throws IOException {
    TypeRegistry typeRegistry =
        TypeRegistry.newBuilder()
            .add(dev.cel.expr.conformance.proto2.TestAllTypes.getDescriptor())
            .add(dev.cel.expr.conformance.proto2.NestedTestAllTypes.getDescriptor())
            .add(dev.cel.expr.conformance.proto3.TestAllTypes.getDescriptor())
            .add(dev.cel.expr.conformance.proto3.NestedTestAllTypes.getDescriptor())
            .build();

    SimpleTestFile.Builder builder = SimpleTestFile.newBuilder();
    ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();
    dev.cel.expr.conformance.proto2.TestAllTypesExtensions.registerAllExtensions(extensionRegistry);
    TextFormat.Parser.newBuilder()
        .setTypeRegistry(typeRegistry)
        .build()
        .merge(Files.readString(testFile, StandardCharsets.UTF_8), extensionRegistry, builder);
    return builder.build();
  }

  private void runFile(SimpleTestFile file) {
    for (SimpleTestSection section : file.getSectionList()) {
      String sectionPath = file.getName() + "/" + section.getName();
      if (skipTests.contains(sectionPath)) {
        matchedSkips.add(sectionPath);
        skipped += section.getTestCount();
        total += section.getTestCount();
        continue;
      }

      for (SimpleTest test : section.getTestList()) {
        String testPath = sectionPath + "/" + test.getName();
        total++;
        if (skipTests.contains(testPath) || (checkedOnly && test.getDisableCheck())) {
          skipTests.stream()
              .filter(skip -> skip.equals(testPath))
              .findFirst()
              .ifPresent(matchedSkips::add);
          skipped++;
          continue;
        }

        try {
          runTest(testPath, test);
          passed++;
        } catch (Throwable e) {
          failures.add(testPath + ": " + e.getMessage());
        }
      }
    }
  }

  private void runTest(String testPath, SimpleTest test) throws InvalidProtocolBufferException {
    if (test.getName().isEmpty()) {
      throw new IllegalArgumentException("simple test has no name");
    }
    if (test.getExpr().isEmpty()) {
      throw new IllegalArgumentException("test has no expression");
    }

    ParseResponse parseResponse =
        stub.parse(
            ParseRequest.newBuilder()
                .setCelSource(test.getExpr())
                .setSourceLocation(test.getName())
                .setDisableMacros(test.getDisableMacros())
                .build());
    ParsedExpr parsedExpr = parseResponse.getParsedExpr();
    if (!parsedExpr.hasExpr()) {
      throw new AssertionError("fatal parse errors: " + parseResponse.getIssuesList());
    }

    CheckedExpr checkedExpr = null;
    if (!test.getDisableCheck() && !skipCheck) {
      CheckRequest.Builder checkRequest =
          CheckRequest.newBuilder().setParsedExpr(parsedExpr).setContainer(test.getContainer());
      for (dev.cel.expr.Decl decl : test.getTypeEnvList()) {
        checkRequest.addTypeEnv(convert(decl, Decl.class));
      }

      CheckResponse checkResponse = stub.check(checkRequest.build());
      checkedExpr = checkResponse.getCheckedExpr();
      if (!checkedExpr.hasExpr()) {
        throw new AssertionError("fatal check errors: " + checkResponse.getIssuesList());
      }

      if (!checkedExpr.getTypeMapMap().containsKey(parsedExpr.getExpr().getId())) {
        throw new AssertionError("no type for top-level expression");
      }

      if (test.getResultMatcherCase() == ResultMatcherCase.TYPED_RESULT) {
        Type expectedType = convert(test.getTypedResult().getDeducedType(), Type.class);
        Type actualType = checkedExpr.getTypeMapOrThrow(parsedExpr.getExpr().getId());
        if (!actualType.equals(expectedType)) {
          throw new AssertionError(
              "deduced type mismatch, got " + print(actualType) + ", want " + print(expectedType));
        }
      }
    }

    if (test.getCheckOnly()) {
      return;
    }

    runEval(
        testPath,
        test,
        EvalRequest.newBuilder()
            .setParsedExpr(parsedExpr)
            .setContainer(test.getContainer())
            .putAllBindings(convertBindings(test.getBindingsMap()))
            .build());

    if (checkedExpr != null) {
      runEval(
          testPath,
          test,
          EvalRequest.newBuilder()
              .setCheckedExpr(checkedExpr)
              .setContainer(test.getContainer())
              .putAllBindings(convertBindings(test.getBindingsMap()))
              .build());
    }
  }

  private void runEval(String testPath, SimpleTest test, EvalRequest request)
      throws InvalidProtocolBufferException {
    EvalResponse evalResponse = stub.eval(request);
    ExprValue actual = evalResponse.getResult();
    if (evalResponse.getIssuesCount() != 0) {
      throw new AssertionError("eval issues: " + evalResponse.getIssuesList());
    }
    if (actual.getKindCase() == ExprValue.KindCase.KIND_NOT_SET) {
      throw new AssertionError("empty eval response");
    }
    match(testPath, test, actual);
  }

  private static void match(String testPath, SimpleTest test, ExprValue actual)
      throws InvalidProtocolBufferException {
    switch (test.getResultMatcherCase()) {
      case VALUE:
        matchValue(testPath, convert(test.getValue(), Value.class), actual);
        return;
      case TYPED_RESULT:
        matchValue(testPath, convert(test.getTypedResult().getResult(), Value.class), actual);
        return;
      case EVAL_ERROR:
        matchError(testPath, convert(test.getEvalError(), ErrorSet.class), actual);
        return;
      case ANY_EVAL_ERRORS:
        for (dev.cel.expr.ErrorSet errorSet : test.getAnyEvalErrors().getErrorsList()) {
          if (actual.getKindCase() == ExprValue.KindCase.ERROR) {
            return;
          }
        }
        throw new AssertionError("got " + print(actual) + ", want one of several eval errors");
      case UNKNOWN:
        matchUnknown(testPath, convert(test.getUnknown(), UnknownSet.class), actual);
        return;
      case ANY_UNKNOWNS:
        for (dev.cel.expr.UnknownSet unknownSet : test.getAnyUnknowns().getUnknownsList()) {
          if (actual.getKindCase() == ExprValue.KindCase.UNKNOWN) {
            return;
          }
        }
        throw new AssertionError("got " + print(actual) + ", want one of several unknowns");
      case RESULTMATCHER_NOT_SET:
        matchValue(testPath, Value.newBuilder().setBoolValue(true).build(), actual);
        return;
    }
    throw new AssertionError("unsupported result matcher " + test.getResultMatcherCase());
  }

  private static void matchValue(String testPath, Value expected, ExprValue actual) {
    if (actual.getKindCase() != ExprValue.KindCase.VALUE) {
      throw new AssertionError("got " + print(actual) + ", want value " + print(expected));
    }
    if (!valuesEqual(expected, actual.getValue())) {
      throw new AssertionError(
          testPath
              + ": eval got ["
              + print(actual.getValue())
              + "], want ["
              + print(expected)
              + "]");
    }
  }

  private static void matchError(String testPath, ErrorSet expected, ExprValue actual) {
    if (actual.getKindCase() != ExprValue.KindCase.ERROR) {
      throw new AssertionError(
          testPath + ": got " + print(actual) + ", want error " + print(expected));
    }
  }

  private static void matchUnknown(String testPath, UnknownSet expected, ExprValue actual) {
    if (actual.getKindCase() != ExprValue.KindCase.UNKNOWN) {
      throw new AssertionError(
          testPath + ": got " + print(actual) + ", want unknown " + print(expected));
    }
  }

  private static boolean valuesEqual(Value expected, Value actual) {
    if (expected.getKindCase() != actual.getKindCase()) {
      return false;
    }

    switch (expected.getKindCase()) {
      case DOUBLE_VALUE:
        double expectedValue = expected.getDoubleValue();
        double actualValue = actual.getDoubleValue();
        return expectedValue == actualValue
            || (Double.isNaN(expectedValue) && Double.isNaN(actualValue));
      case MAP_VALUE:
        return mapsEqual(expected.getMapValue(), actual.getMapValue());
      case LIST_VALUE:
        if (expected.getListValue().getValuesCount() != actual.getListValue().getValuesCount()) {
          return false;
        }
        for (int i = 0; i < expected.getListValue().getValuesCount(); i++) {
          if (!valuesEqual(
              expected.getListValue().getValues(i), actual.getListValue().getValues(i))) {
            return false;
          }
        }
        return true;
      default:
        return expected.equals(actual);
    }
  }

  private static boolean mapsEqual(MapValue expected, MapValue actual) {
    if (expected.getEntriesCount() != actual.getEntriesCount()) {
      return false;
    }
    boolean[] matched = new boolean[actual.getEntriesCount()];
    for (MapValue.Entry expectedEntry : expected.getEntriesList()) {
      boolean found = false;
      for (int i = 0; i < actual.getEntriesCount(); i++) {
        if (!matched[i]
            && valuesEqual(expectedEntry.getKey(), actual.getEntries(i).getKey())
            && valuesEqual(expectedEntry.getValue(), actual.getEntries(i).getValue())) {
          matched[i] = true;
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    }
    return true;
  }

  private static Map<String, ExprValue> convertBindings(
      Map<String, dev.cel.expr.ExprValue> bindings) {
    try {
      java.util.LinkedHashMap<String, ExprValue> converted = new java.util.LinkedHashMap<>();
      for (Map.Entry<String, dev.cel.expr.ExprValue> entry : bindings.entrySet()) {
        converted.put(entry.getKey(), convert(entry.getValue(), ExprValue.class));
      }
      return converted;
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException("invalid binding value", e);
    }
  }

  private static <T extends Message> T convert(Message message, Class<T> targetType)
      throws InvalidProtocolBufferException {
    try {
      @SuppressWarnings("unchecked")
      T converted =
          (T) targetType.getMethod("parseFrom", byte[].class).invoke(null, message.toByteArray());
      return converted;
    } catch (ReflectiveOperationException e) {
      Throwable cause = e.getCause();
      if (cause instanceof InvalidProtocolBufferException) {
        throw (InvalidProtocolBufferException) cause;
      }
      throw new IllegalStateException("cannot convert to " + targetType.getName(), e);
    }
  }

  private static String print(Message message) {
    return TEXT_PRINTER.printToString(message);
  }

  private static final class Arguments {
    private final boolean checkedOnly;
    private final boolean skipCheck;
    private final Set<String> skipTests;
    private final List<Path> testFiles;

    private Arguments(
        boolean checkedOnly, boolean skipCheck, Set<String> skipTests, List<Path> testFiles) {
      this.checkedOnly = checkedOnly;
      this.skipCheck = skipCheck;
      this.skipTests = skipTests;
      this.testFiles = testFiles;
    }

    private static Arguments parse(String[] args) {
      boolean checkedOnly = false;
      boolean skipCheck = false;
      Set<String> skipTests = new HashSet<>();
      List<Path> testFiles = new ArrayList<>();
      for (String arg : args) {
        if ("--checked_only".equals(arg)) {
          checkedOnly = true;
        } else if ("--skip_check".equals(arg)) {
          skipCheck = true;
        } else if (arg.startsWith("--skip_test=")) {
          parseSkipTest(arg.substring("--skip_test=".length()), skipTests);
        } else if (arg.startsWith("--")) {
          throw new IllegalArgumentException("unsupported argument: " + arg);
        } else {
          testFiles.add(Path.of(arg));
        }
      }
      return new Arguments(checkedOnly, skipCheck, skipTests, testFiles);
    }

    private static void parseSkipTest(String value, Set<String> skipTests) {
      int fileSeparator = value.indexOf('/');
      if (fileSeparator < 1 || fileSeparator == value.length() - 1) {
        throw new IllegalArgumentException(
            "skip_test argument must contain at least <file>/<section>: " + value);
      }

      String fileName = value.substring(0, fileSeparator);
      String sectionString = value.substring(fileSeparator + 1);
      for (String sectionValue : sectionString.split(";")) {
        int sectionSeparator = sectionValue.indexOf('/');
        if (sectionSeparator < 0) {
          skipTests.add(fileName + "/" + sectionValue);
        } else {
          String sectionName = sectionValue.substring(0, sectionSeparator);
          String testString = sectionValue.substring(sectionSeparator + 1);
          for (String test : testString.split(",")) {
            skipTests.add(fileName + "/" + sectionName + "/" + test);
          }
        }
      }
    }
  }
}
