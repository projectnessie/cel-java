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
package org.projectnessie.cel.conformance;

import static java.util.stream.Collectors.toCollection;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.projectnessie.cel.CEL.astToCheckedExpr;
import static org.projectnessie.cel.CEL.astToParsedExpr;
import static org.projectnessie.cel.CEL.checkedExprToAst;
import static org.projectnessie.cel.CEL.parsedExprToAst;
import static org.projectnessie.cel.Env.newCustomEnv;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.clearMacros;
import static org.projectnessie.cel.EnvOption.container;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.macros;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.Library.StdLib;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TypeT.newObjectTypeValue;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.extension.EncodersLib.encoders;
import static org.projectnessie.cel.extension.MathLib.math;
import static org.projectnessie.cel.extension.NetworkLib.network;
import static org.projectnessie.cel.extension.OptionalLib.optionals;
import static org.projectnessie.cel.extension.ProtoLib.proto;
import static org.projectnessie.cel.extension.StringsLib.strings;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Decl;
import com.google.api.expr.v1alpha1.ErrorSet;
import com.google.api.expr.v1alpha1.ExprValue;
import com.google.api.expr.v1alpha1.ListValue;
import com.google.api.expr.v1alpha1.MapValue;
import com.google.api.expr.v1alpha1.MapValue.Entry;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.Type;
import com.google.api.expr.v1alpha1.UnknownSet;
import com.google.api.expr.v1alpha1.Value;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.protobuf.Timestamp;
import com.google.protobuf.TypeRegistry;
import com.google.rpc.Status;
import dev.cel.expr.conformance.test.SimpleTest;
import dev.cel.expr.conformance.test.SimpleTest.ResultMatcherCase;
import dev.cel.expr.conformance.test.SimpleTestFile;
import dev.cel.expr.conformance.test.SimpleTestSection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.projectnessie.cel.Ast;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.Program.EvalResult;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.TypeT;
import org.projectnessie.cel.common.types.Types;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;
import org.projectnessie.cel.common.types.traits.Mapper;
import org.projectnessie.cel.parser.Macro;

class SimpleConformanceTest {

  private static final Path TESTDATA_DIR = testdataDir();
  private static final TextFormat.Printer TEXT_PRINTER =
      TextFormat.printer().emittingSingleLine(true);

  private static final List<String> TEST_FILES =
      List.of(
          "basic.textproto",
          "bindings_ext.textproto",
          "block_ext.textproto",
          "comparisons.textproto",
          "conversions.textproto",
          "dynamic.textproto",
          "encoders_ext.textproto",
          "enums.textproto",
          "fields.textproto",
          "fp_math.textproto",
          "integer_math.textproto",
          "lists.textproto",
          "logic.textproto",
          "macros.textproto",
          "math_ext.textproto",
          "namespace.textproto",
          "network_ext.textproto",
          "parse.textproto",
          "plumbing.textproto",
          "proto2.textproto",
          "proto2_ext.textproto",
          "proto3.textproto",
          "string.textproto",
          "string_ext.textproto",
          "timestamps.textproto",
          "type_deduction.textproto",
          "unknowns.textproto",
          "wrappers.textproto");

  private static final Set<String> SKIP_TESTS =
      SkipList.parse(
          // Strong enum semantics require typed enum values rather than treating enum literals as
          // ints.
          "enums/strong_proto2/literal_global",
          "enums/strong_proto2/literal_nested",
          "enums/strong_proto2/literal_zero",
          "enums/strong_proto2/type_global",
          "enums/strong_proto2/type_nested",
          "enums/strong_proto2/select_default",
          "enums/strong_proto2/field_type",
          "enums/strong_proto2/assign_standalone_int",
          "enums/strong_proto2/convert_int_inrange",
          "enums/strong_proto2/convert_int_big",
          "enums/strong_proto2/convert_int_neg",
          "enums/strong_proto2/convert_int_too_big",
          "enums/strong_proto2/convert_int_too_neg",
          "enums/strong_proto2/convert_string",
          "enums/strong_proto2/convert_string_bad",
          "enums/strong_proto3/literal_global",
          "enums/strong_proto3/literal_nested",
          "enums/strong_proto3/literal_zero",
          "enums/strong_proto3/type_global",
          "enums/strong_proto3/type_nested",
          "enums/strong_proto3/select_default",
          "enums/strong_proto3/select",
          "enums/strong_proto3/select_big",
          "enums/strong_proto3/select_neg",
          "enums/strong_proto3/field_type",
          "enums/strong_proto3/assign_standalone_int",
          "enums/strong_proto3/assign_standalone_int_big",
          "enums/strong_proto3/assign_standalone_int_neg",
          "enums/strong_proto3/convert_int_inrange",
          "enums/strong_proto3/convert_int_big",
          "enums/strong_proto3/convert_int_neg",
          "enums/strong_proto3/convert_int_too_big",
          "enums/strong_proto3/convert_int_too_neg",
          "enums/strong_proto3/convert_string",
          "enums/strong_proto3/convert_string_bad",
          // Optional list/map/message syntax and runtime support is not implemented yet.
          "block_ext/basic/optional_list",
          "block_ext/basic/optional_map",
          "block_ext/basic/optional_map_chained",
          "block_ext/basic/optional_message");

  private static final Set<String> matchedSkips = new LinkedHashSet<>();
  private static final AtomicInteger total = new AtomicInteger();
  private static final AtomicInteger passed = new AtomicInteger();
  private static final AtomicInteger skipped = new AtomicInteger();

  @TestFactory
  Stream<DynamicNode> simpleConformance() {
    List<DynamicNode> files = new ArrayList<>();
    TEST_FILES.forEach(fileName -> files.add(dynamicContainer(fileName, fileTests(fileName))));
    files.add(dynamicTest("skip list matches testdata", this::assertAllSkipsMatched));
    return files.stream();
  }

  @AfterAll
  static void printSummary() {
    System.out.printf(
        "Conformance tests: %d total, %d passed, %d skipped%n",
        total.get(), passed.get(), skipped.get());
  }

  private Stream<DynamicNode> fileTests(String fileName) {
    SimpleTestFile file;
    try {
      file = parseSimpleFile(TESTDATA_DIR.resolve(fileName));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot parse conformance testdata " + fileName, e);
    }

    return file.getSectionList().stream()
        .map(
            section ->
                dynamicContainer(
                    section.getName(),
                    section.getTestList().stream()
                        .map(test -> dynamicTest(test.getName(), () -> run(file, section, test)))));
  }

  private void run(SimpleTestFile file, SimpleTestSection section, SimpleTest test)
      throws InvalidProtocolBufferException {
    total.incrementAndGet();
    String sectionPath = file.getName() + "/" + section.getName();
    String testPath = sectionPath + "/" + test.getName();
    if (SKIP_TESTS.contains(sectionPath)) {
      matchedSkips.add(sectionPath);
      skipped.incrementAndGet();
      abort("Skipped conformance section " + sectionPath);
    }
    if (SKIP_TESTS.contains(testPath)) {
      matchedSkips.add(testPath);
      skipped.incrementAndGet();
      abort("Skipped conformance test " + testPath);
    }

    ConformanceCaseRunner.run(testPath, test);
    passed.incrementAndGet();
  }

  private void assertAllSkipsMatched() {
    Set<String> unmatched =
        SKIP_TESTS.stream()
            .filter(skip -> !matchedSkips.contains(skip))
            .collect(toCollection(LinkedHashSet::new));
    if (!unmatched.isEmpty()) {
      fail("Skip did not match any test or section: " + unmatched);
    }
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

  private static Path testdataDir() {
    Path rootRelative = Path.of("submodules/cel-spec/tests/simple/testdata");
    if (Files.isDirectory(rootRelative)) {
      return rootRelative;
    }

    Path moduleRelative = Path.of("../submodules/cel-spec/tests/simple/testdata");
    if (Files.isDirectory(moduleRelative)) {
      return moduleRelative;
    }

    throw new IllegalStateException("Cannot locate CEL-Spec simple conformance testdata");
  }

  private static final class ConformanceCaseRunner {
    private ConformanceCaseRunner() {}

    private static void run(String testPath, SimpleTest test)
        throws InvalidProtocolBufferException {
      if (test.getName().isEmpty()) {
        throw new IllegalArgumentException("simple test has no name");
      }
      if (test.getExpr().isEmpty()) {
        throw new IllegalArgumentException("test has no expression");
      }

      ParsedExpr parsedExpr = ConformanceEvaluator.parse(test);
      CheckedExpr checkedExpr = null;
      if (!test.getDisableCheck()) {
        checkedExpr = ConformanceEvaluator.check(test, parsedExpr);
        if (!checkedExpr.getTypeMapMap().containsKey(parsedExpr.getExpr().getId())) {
          throw new AssertionError("no type for top-level expression");
        }
        if (test.getResultMatcherCase() == ResultMatcherCase.TYPED_RESULT) {
          Type expectedType = convert(test.getTypedResult().getDeducedType(), Type.class);
          Type actualType = checkedExpr.getTypeMapOrThrow(parsedExpr.getExpr().getId());
          if (!actualType.equals(expectedType)) {
            throw new AssertionError(
                "deduced type mismatch, got "
                    + print(actualType)
                    + ", want "
                    + print(expectedType));
          }
        }
      }

      if (test.getCheckOnly()) {
        return;
      }

      match(testPath, test, ConformanceEvaluator.evalParsed(test, parsedExpr));
      if (checkedExpr != null) {
        match(testPath, test, ConformanceEvaluator.evalChecked(test, checkedExpr));
      }
    }
  }

  private static final class ConformanceEvaluator {
    private ConformanceEvaluator() {}

    private static ParsedExpr parse(SimpleTest test) {
      String sourceText = test.getExpr();
      if (sourceText.trim().isEmpty()) {
        throw new IllegalArgumentException("No source code.");
      }

      List<EnvOption> parseOptions = new ArrayList<>();
      if (test.getDisableMacros()) {
        parseOptions.add(clearMacros());
      }
      if (usesTestOnlyBlockMacros(test.getExpr())) {
        parseOptions.add(macros(Macro.TestOnlyBlockMacros));
      }

      Env env = newEnv(parseOptions.toArray(new EnvOption[0]));
      AstIssuesTuple astIss = env.parse(sourceText);
      if (astIss.hasIssues()) {
        throw new AssertionError("fatal parse errors: " + astIss.getIssues().getErrors());
      }
      return astToParsedExpr(astIss.getAst());
    }

    private static CheckedExpr check(SimpleTest test, ParsedExpr parsedExpr)
        throws InvalidProtocolBufferException {
      List<Decl> typeEnv = new ArrayList<>();
      for (dev.cel.expr.Decl decl : test.getTypeEnvList()) {
        typeEnv.add(convert(decl, Decl.class));
      }

      Env env =
          newCustomEnv(
              conformanceEnvOptions(test, StdLib(), declarations(typeEnv))
                  .toArray(new EnvOption[0]));

      AstIssuesTuple astIss = env.check(parsedExprToAst(parsedExpr));
      if (astIss.hasIssues()) {
        throw new AssertionError("fatal check errors: " + astIss.getIssues().getErrors());
      }
      return astToCheckedExpr(astIss.getAst());
    }

    private static ExprValue evalParsed(SimpleTest test, ParsedExpr parsedExpr) {
      return eval(test, parsedExprToAst(parsedExpr));
    }

    private static ExprValue evalChecked(SimpleTest test, CheckedExpr checkedExpr) {
      return eval(test, checkedExprToAst(checkedExpr));
    }

    private static ExprValue eval(SimpleTest test, Ast ast) {
      Env env = newEnv(conformanceEnvOptions(test).toArray(new EnvOption[0]));

      Program program = env.program(ast);
      Map<String, Object> args = new HashMap<>();
      test.getBindingsMap()
          .forEach(
              (name, exprValue) ->
                  args.put(name, exprValueToRefValue(env.getTypeAdapter(), exprValue)));

      EvalResult res = program.eval(args);
      if (!isError(res.getVal())) {
        return refValueToExprValue(res.getVal());
      }

      Err err = (Err) res.getVal();
      return ExprValue.newBuilder()
          .setError(ErrorSet.newBuilder().addErrors(Status.newBuilder().setMessage(err.toString())))
          .build();
    }

    private static List<EnvOption> conformanceEnvOptions(SimpleTest test, EnvOption... options) {
      List<EnvOption> envOptions = new ArrayList<>();
      envOptions.add(container(test.getContainer()));
      envOptions.add(
          types(
              dev.cel.expr.conformance.proto2.TestAllTypes.getDefaultInstance(),
              dev.cel.expr.conformance.proto2.Proto2ExtensionScopedMessage.getDefaultInstance(),
              dev.cel.expr.conformance.proto3.TestAllTypes.getDefaultInstance()));
      if (test.getExpr().startsWith("proto.hasExt(")
          || test.getExpr().startsWith("proto.getExt(")) {
        envOptions.add(proto());
      }
      if (usesStringExtensions(test.getExpr())) {
        envOptions.add(strings());
      }
      if (test.getExpr().contains("base64.")) {
        envOptions.add(encoders());
      }
      if (test.getExpr().contains("math.")) {
        envOptions.add(math());
      }
      if (usesNetworkExtensions(test.getExpr())) {
        envOptions.add(network());
      }
      if (test.getExpr().contains("optional.")) {
        envOptions.add(optionals());
      }
      envOptions.addAll(List.of(options));
      return envOptions;
    }

    private static boolean usesStringExtensions(String expression) {
      return expression.contains(".charAt(")
          || expression.contains(".indexOf(")
          || expression.contains(".lastIndexOf(")
          || expression.contains(".lowerAscii(")
          || expression.contains(".upperAscii(")
          || expression.contains(".replace(")
          || expression.contains(".split(")
          || expression.contains(".substring(")
          || expression.contains(".trim(")
          || expression.contains(".join(")
          || expression.contains("strings.quote(")
          || expression.contains(".format(")
          || expression.contains(".reverse(");
    }

    private static boolean usesNetworkExtensions(String expression) {
      return expression.contains("ip(")
          || expression.contains("cidr(")
          || expression.contains("isIP(")
          || expression.contains("ip.isCanonical(")
          || expression.contains("net.IP")
          || expression.contains("net.CIDR");
    }

    private static boolean usesTestOnlyBlockMacros(String expression) {
      return expression.contains("cel.block(")
          || expression.contains("cel.index(")
          || expression.contains("cel.iterVar(")
          || expression.contains("cel.accuVar(");
    }
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
        if (actual.getKindCase() == ExprValue.KindCase.ERROR) {
          return;
        }
        throw new AssertionError("got " + print(actual) + ", want one of several eval errors");
      case UNKNOWN:
        matchUnknown(testPath, convert(test.getUnknown(), UnknownSet.class), actual);
        return;
      case ANY_UNKNOWNS:
        if (actual.getKindCase() == ExprValue.KindCase.UNKNOWN) {
          return;
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

  private static Val exprValueToRefValue(TypeAdapter adapter, dev.cel.expr.ExprValue ev) {
    try {
      return exprValueToRefValue(adapter, convert(ev, ExprValue.class));
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException("invalid expression value", e);
    }
  }

  private static Val exprValueToRefValue(TypeAdapter adapter, ExprValue ev) {
    switch (ev.getKindCase()) {
      case VALUE:
        return valueToRefValue(adapter, ev.getValue());
      case ERROR:
        return newErr("XXX add details later");
      case UNKNOWN:
        return unknownOf(ev.getUnknown().getExprs(0));
      default:
        throw new IllegalArgumentException("unknown ExprValue kind " + ev.getKindCase());
    }
  }

  private static Val valueToRefValue(TypeAdapter adapter, Value v) {
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
        return adapter.nativeToValue(v.getObjectValue());
      case MAP_VALUE:
        Map<Val, Val> entries = new HashMap<>();
        for (Entry entry : v.getMapValue().getEntriesList()) {
          entries.put(
              valueToRefValue(adapter, entry.getKey()), valueToRefValue(adapter, entry.getValue()));
        }
        return adapter.nativeToValue(entries);
      case LIST_VALUE:
        List<Val> elements = new ArrayList<>();
        for (Value element : v.getListValue().getValuesList()) {
          elements.add(valueToRefValue(adapter, element));
        }
        return adapter.nativeToValue(elements);
      case TYPE_VALUE:
        String typeName = v.getTypeValue();
        org.projectnessie.cel.common.types.ref.Type type = Types.getTypeByName(typeName);
        if (type != null) {
          return type;
        }
        return newObjectTypeValue(typeName);
      case ENUM_VALUE:
        return intOf(v.getEnumValue().getValue());
      default:
        throw new IllegalArgumentException("unknown value " + v.getKindCase());
    }
  }

  private static ExprValue refValueToExprValue(Val res) {
    if (isUnknown(res)) {
      return ExprValue.newBuilder()
          .setUnknown(UnknownSet.newBuilder().addExprs(res.intValue()))
          .build();
    }
    return ExprValue.newBuilder().setValue(refValueToValue(res)).build();
  }

  private static Value refValueToValue(Val res) {
    switch (res.type().typeEnum()) {
      case Bool:
        return Value.newBuilder().setBoolValue(res.booleanValue()).build();
      case Bytes:
        return Value.newBuilder().setBytesValue(res.convertToNative(ByteString.class)).build();
      case Double:
        return Value.newBuilder().setDoubleValue(res.convertToNative(Double.class)).build();
      case Int:
        return Value.newBuilder().setInt64Value(res.intValue()).build();
      case Null:
        return Value.newBuilder().setNullValueValue(0).build();
      case String:
        return Value.newBuilder().setStringValue(res.value().toString()).build();
      case Type:
        return Value.newBuilder().setTypeValue(((TypeT) res).typeName()).build();
      case Uint:
        return Value.newBuilder().setUint64Value(res.intValue()).build();
      case Duration:
        return Value.newBuilder()
            .setObjectValue(Any.pack(res.convertToNative(Duration.class)))
            .build();
      case Timestamp:
        return Value.newBuilder()
            .setObjectValue(Any.pack(res.convertToNative(Timestamp.class)))
            .build();
      case List:
        Lister lister = (Lister) res;
        ListValue.Builder elements = ListValue.newBuilder();
        for (IteratorT i = lister.iterator(); i.hasNext() == True; ) {
          elements.addValues(refValueToValue(i.next()));
        }
        return Value.newBuilder().setListValue(elements).build();
      case Map:
        Mapper mapper = (Mapper) res;
        MapValue.Builder entries = MapValue.newBuilder();
        for (IteratorT i = mapper.iterator(); i.hasNext() == True; ) {
          Val key = i.next();
          entries
              .addEntriesBuilder()
              .setKey(refValueToValue(key))
              .setValue(refValueToValue(mapper.get(key)));
        }
        return Value.newBuilder().setMapValue(entries).build();
      case Object:
        Message pb = (Message) res.value();
        Value.Builder value = Value.newBuilder();
        if (pb instanceof Any) {
          value.setObjectValue(unwrapNestedAny((Any) pb));
        } else if (pb instanceof ListValue) {
          value.setListValue((ListValue) pb);
        } else if (pb instanceof MapValue) {
          value.setMapValue((MapValue) pb);
        } else {
          value.setObjectValue(Any.pack(pb));
        }
        return value.build();
      default:
        throw new IllegalStateException(String.format("Unknown %s", res.type().typeEnum()));
    }
  }

  private static Any unwrapNestedAny(Any any) {
    Any current = any;
    while (current.is(Any.class)) {
      try {
        Any next = current.unpack(Any.class);
        if (next.equals(current)) {
          return current;
        }
        current = next;
      } catch (InvalidProtocolBufferException e) {
        return current;
      }
    }
    return current;
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

  private static final class SkipList {
    private SkipList() {}

    private static Set<String> parse(String... values) {
      Set<String> skipTests = new LinkedHashSet<>();
      for (String value : values) {
        parse(value, skipTests);
      }
      return Set.copyOf(skipTests);
    }

    private static void parse(String value, Set<String> skipTests) {
      int fileSeparator = value.indexOf('/');
      if (fileSeparator < 1 || fileSeparator == value.length() - 1) {
        throw new IllegalArgumentException(
            "skip argument must contain at least <file>/<section>: " + value);
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
