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
package org.projectnessie.cel.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newRegistry;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Activation.newActivation;
import static org.projectnessie.cel.interpreter.Activation.newPartialActivation;
import static org.projectnessie.cel.interpreter.AstPruner.pruneAst;
import static org.projectnessie.cel.interpreter.AttributePattern.newAttributePattern;
import static org.projectnessie.cel.interpreter.AttributePattern.newPartialAttributeFactory;
import static org.projectnessie.cel.interpreter.EvalState.newEvalState;
import static org.projectnessie.cel.interpreter.Interpreter.exhaustiveEval;
import static org.projectnessie.cel.interpreter.Interpreter.newStandardInterpreter;

import com.google.api.expr.v1alpha1.Expr;
import java.util.Collections;
import java.util.HashMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.cel.common.Source;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.interpreter.Activation.PartialActivation;
import org.projectnessie.cel.parser.Parser;
import org.projectnessie.cel.parser.Parser.ParseResult;
import org.projectnessie.cel.parser.Unparser;

class PruneTest {

  static class TestCase {
    final Object in;
    final String expr;
    final String expect;

    TestCase(Object in, String expr, String expect) {
      this.in = in;
      this.expr = expr;
      this.expect = expect;
    }

    @Override
    public String toString() {
      return expr;
    }
  }

  @SuppressWarnings("unused")
  static TestCase[] pruneTestCases() {
    return new TestCase[] {
      new TestCase(
          Collections.singletonMap("msg", Collections.singletonMap("foo", "bar")),
          "msg",
          "{\"foo\": \"bar\"}"),
      new TestCase(null, "true && false", "false"),
      new TestCase(unknownActivation("x"), "(true || false) && x", "x"),
      new TestCase(unknownActivation("x"), "(false || false) && x", "false"),
      new TestCase(unknownActivation("a"), "a && [1, 1u, 1.0].exists(x, type(x) == uint)", "a"),
      new TestCase(null, "{'hello': 'world'.size()}", "{\"hello\": 5}"),
      new TestCase(null, "[b'bytes-string']", "[b\"bytes-string\"]"),
      new TestCase(
          null,
          "[b\"\\142\\171\\164\\145\\163\\055\\163\\164\\162\\151\\156\\147\"]",
          "[b\"bytes-string\"]"),
      new TestCase(null, "[b'bytes'] + [b'-' + b'string']", "[b\"bytes\", b\"-string\"]"),
      new TestCase(null, "1u + 3u", "4u"),
      new TestCase(null, "2 < 3", "true"),
      new TestCase(unknownActivation(), "test == null", "test == null"),
      new TestCase(unknownActivation(), "test == null && false", "false"),
      new TestCase(unknownActivation("b", "c"), "true ? b < 1.2 : c == ['hello']", "b < 1.2"),
      new TestCase(unknownActivation(), "[1+3, 2+2, 3+1, four]", "[4, 4, 4, four]"),
      new TestCase(unknownActivation(), "test == {'a': 1, 'field': 2}.field", "test == 2"),
      new TestCase(
          unknownActivation(), "test in {'a': 1, 'field': [2, 3]}.field", "test in [2, 3]"),
      new TestCase(
          unknownActivation(), "test == {'field': [1 + 2, 2 + 3]}", "test == {\"field\": [3, 5]}"),
      new TestCase(
          unknownActivation(),
          "test in {'a': 1, 'field': [test, 3]}.field",
          "test in {\"a\": 1, \"field\": [test, 3]}.field")
      // TODO(issues/) the output test relies on tracking macro expansions back to their original
      //  call patterns.
      /* new TestCase(
        unknownActivation(),
      	"[1+3, 2+2, 3+1, four].exists(x, x == four)",
      	"[4, 4, 4, four].exists(x, x == four)"
      ) */
    };
  }

  @ParameterizedTest
  @MethodSource("pruneTestCases")
  void prune(TestCase tc) {
    ParseResult parseResult = Parser.parseAllMacros(Source.newStringSource(tc.expr, "<input>"));
    if (parseResult.hasErrors()) {
      fail(parseResult.getErrors().toDisplayString());
    }
    EvalState state = newEvalState();
    TypeRegistry reg = newRegistry();
    AttributeFactory attrs = newPartialAttributeFactory(Container.defaultContainer, reg, reg);
    Interpreter interp = newStandardInterpreter(Container.defaultContainer, reg, reg, attrs);

    Interpretable interpretable =
        interp.newUncheckedInterpretable(parseResult.getExpr(), exhaustiveEval(state));
    interpretable.eval(testActivation(tc.in));
    Expr newExpr = pruneAst(parseResult.getExpr(), state);
    String actual = Unparser.unparse(newExpr, null);
    assertThat(actual).isEqualTo(tc.expect);
  }

  static PartialActivation unknownActivation(String... vars) {
    AttributePattern[] pats = new AttributePattern[vars.length];
    for (int i = 0; i < vars.length; i++) {
      String v = vars[i];
      pats[i] = newAttributePattern(v);
    }
    return newPartialActivation(new HashMap<>(), pats);
  }

  Activation testActivation(Object in) {
    if (in == null) {
      return emptyActivation();
    }
    return newActivation(in);
  }
}
