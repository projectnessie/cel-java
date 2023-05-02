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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.Util.mapOf;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newRegistry;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Activation.newPartialActivation;
import static org.projectnessie.cel.interpreter.AttributePattern.newAttributePattern;
import static org.projectnessie.cel.interpreter.AttributePattern.newPartialAttributeFactory;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.UnknownT;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.interpreter.Activation.PartialActivation;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.AttributeFactory.NamespacedAttribute;
import org.projectnessie.cel.interpreter.AttributeFactory.Qualifier;

class AttributePatternsTest {

  /**
   * attr describes a simplified format for specifying common Attribute and Qualifier values for use
   * in pattern matching tests.
   */
  static class Attr {
    /**
     * unchecked indicates whether the attribute has not been type-checked and thus not gone // the
     * variable and function resolution step.
     */
    boolean unchecked;

    /**
     * container simulates the expression container and is only relevant on 'unchecked' test inputs
     * + as the container is used to resolve the potential fully qualified variable names
     * represented + by an identifier or select expression.
     */
    String container = "";

    /** variable name, fully qualified unless the attr is marked as unchecked=true */
    final String name;

    /** quals contains a list of static qualifiers. */
    Object[] quals;

    Attr(String name) {
      this.name = name;
    }

    Attr unchecked(boolean unchecked) {
      this.unchecked = unchecked;
      return this;
    }

    Attr container(String container) {
      this.container = container;
      return this;
    }

    Attr quals(Object... quals) {
      this.quals = quals;
      return this;
    }

    @Override
    public String toString() {
      return "Attr{"
          + "unchecked="
          + unchecked
          + ", container='"
          + container
          + '\''
          + ", name='"
          + name
          + '\''
          + ", quals="
          + (quals != null
              ? Arrays.stream(quals).map(Object::toString).collect(Collectors.joining(",\n    "))
              : null)
          + '}';
    }
  }

  /**
   * patternTest describes a pattern, and a set of matches and misses for the pattern to highlight +
   * what the pattern will and will not match.
   */
  static class PatternTest {
    final String name;
    final AttributePattern pattern;
    Attr[] matches;
    Attr[] misses;
    String disabled;

    PatternTest(String name, AttributePattern pattern) {
      this.name = name;
      this.pattern = pattern;
    }

    @Override
    public String toString() {
      return name;
    }

    PatternTest disabled(String reason) {
      this.disabled = reason;
      return this;
    }

    PatternTest matches(Attr... matches) {
      this.matches = matches;
      return this;
    }

    PatternTest misses(Attr... misses) {
      this.misses = misses;
      return this;
    }
  }

  @SuppressWarnings("unused")
  static PatternTest[] attributePatternsTestCases() {
    return new PatternTest[] {
      new PatternTest("var", newAttributePattern("var"))
          .matches(new Attr("var"), new Attr("var").quals("field"))
          .misses(new Attr("ns.var")),
      new PatternTest("var_namespace", newAttributePattern("ns.app.var"))
          .matches(
              new Attr("ns.app.var"),
              new Attr("ns.app.var").quals(0L),
              new Attr("ns").quals("app", "var", "foo").container("ns.app").unchecked(true))
          .misses(
              new Attr("ns.var"), new Attr("ns").quals("var").container("ns.app").unchecked(true)),
      new PatternTest("var_field", newAttributePattern("var").qualString("field"))
          .matches(
              new Attr("var"),
              new Attr("var").quals("field"),
              new Attr("var").quals("field").unchecked(true),
              new Attr("var").quals("field", 1L))
          .misses(new Attr("var").quals("other")),
      new PatternTest("var_index", newAttributePattern("var").qualInt(0))
          .matches(new Attr("var"), new Attr("var").quals(0L), new Attr("var").quals(0L, false))
          .misses(new Attr("var").quals(ULong.valueOf(0L)), new Attr("var").quals(1L, false)),
      new PatternTest("var_index_uint", newAttributePattern("var").qualUint(1))
          .matches(
              new Attr("var"),
              new Attr("var").quals(ULong.valueOf(1L)),
              new Attr("var").quals(ULong.valueOf(1L), true))
          .misses(new Attr("var").quals(ULong.valueOf(0L)), new Attr("var").quals(1L, false)),
      new PatternTest("var_index_bool", newAttributePattern("var").qualBool(true))
          .matches(
              new Attr("var"), new Attr("var").quals(true), new Attr("var").quals(true, "name"))
          .misses(new Attr("var").quals(false), new Attr("none")),
      new PatternTest("var_wildcard", newAttributePattern("ns.var").wildcard())
          .matches(
              new Attr("ns.var"),
              // The unchecked attributes consider potential namespacing and field selection
              // when testing variable names.
              new Attr("var").quals(true).container("ns").unchecked(true),
              new Attr("var").quals("name").container("ns").unchecked(true),
              new Attr("var").quals("name").container("ns").unchecked(true))
          .misses(new Attr("var").quals(false), new Attr("none")),
      new PatternTest(
              "var_wildcard_field", newAttributePattern("var").wildcard().qualString("field"))
          .matches(
              new Attr("var"), new Attr("var").quals(true), new Attr("var").quals(10L, "field"))
          .misses(new Attr("var").quals(10L, "other")),
      new PatternTest("var_wildcard_wildcard", newAttributePattern("var").wildcard().wildcard())
          .matches(
              new Attr("var"), new Attr("var").quals(true), new Attr("var").quals(10L, "field"))
          .misses(new Attr("none"))
    };
  }

  @ParameterizedTest
  @MethodSource("attributePatternsTestCases")
  void unknownResolution(PatternTest tst) {
    Assumptions.assumeTrue(tst.disabled == null, tst.disabled);

    TypeRegistry reg = newRegistry();
    for (int i = 0; i < tst.matches.length; i++) {
      Attr m = tst.matches[i];
      Container cont = Container.defaultContainer;
      if (m.unchecked) {
        cont = Container.newContainer(Container.name(m.container));
      }
      AttributeFactory fac = newPartialAttributeFactory(cont, reg, reg);
      Attribute attr = genAttr(fac, m);
      PartialActivation partVars = newPartialActivation(emptyActivation(), tst.pattern);
      Object val = attr.resolve(partVars);
      assertThat(val)
          .withFailMessage(() -> String.format("match: '%s', gen: '%s'", m, attr))
          .isInstanceOf(UnknownT.class);
    }
    for (int i = 0; i < tst.misses.length; i++) {
      Attr m = tst.misses[i];
      Container cont = Container.defaultContainer;
      if (m.unchecked) {
        cont = Container.newContainer(Container.name(m.container));
      }
      AttributeFactory fac = newPartialAttributeFactory(cont, reg, reg);
      Attribute attr = genAttr(fac, m);
      PartialActivation partVars = newPartialActivation(emptyActivation(), tst.pattern);
      assertThatThrownBy(() -> attr.resolve(partVars), "miss: '%s', gen: '%s'", m, attr);
    }
  }

  @Test
  void crossReference() {
    TypeRegistry reg = newRegistry();
    AttributeFactory fac = newPartialAttributeFactory(Container.defaultContainer, reg, reg);
    NamespacedAttribute a = fac.absoluteAttribute(1, "a");
    NamespacedAttribute b = fac.absoluteAttribute(2, "b");
    a.addQualifier(b);

    // Ensure that var a[b], the dynamic index into var 'a' is the unknown value
    // returned from attribute resolution.
    PartialActivation partVars =
        newPartialActivation(mapOf("a", new long[] {1L, 2L}), newAttributePattern("b"));
    Object val = a.resolve(partVars);
    assertThat(val).isEqualTo(unknownOf(2));

    // Ensure that a[b], the dynamic index into var 'a' is the unknown value
    // returned from attribute resolution. Note, both 'a' and 'b' have unknown attribute
    // patterns specified. This changes the evaluation behavior slightly, but the end
    // result is the same.
    partVars =
        newPartialActivation(
            mapOf("a", new long[] {1L, 2L}),
            newAttributePattern("a").qualInt(0),
            newAttributePattern("b"));
    val = a.resolve(partVars);
    assertThat(val).isEqualTo(unknownOf(2));

    // Note, that only 'a[0].c' will result in an unknown result since both 'a' and 'b'
    // have values. However, since the attribute being pattern matched is just 'a.b',
    // the outcome will indicate that 'a[b]' is unknown.
    partVars =
        newPartialActivation(
            mapOf("a", new long[] {1, 2}, "b", 0),
            newAttributePattern("a").qualInt(0).qualString("c"));
    val = a.resolve(partVars);
    assertThat(val).isEqualTo(unknownOf(2));

    // Test a positive case that returns a valid value even though the attribugte factory
    // is the partial attribute factory.
    partVars = newPartialActivation(mapOf("a", new long[] {1, 2}, "b", 0));
    val = a.resolve(partVars);
    assertThat(val).isEqualTo(1L);

    // Ensure the unknown attribute id moves when the attribute becomes more specific.
    partVars =
        newPartialActivation(
            mapOf("a", new long[] {1, 2}, "b", 0),
            newAttributePattern("a").qualInt(0).qualString("c"));
    // Qualify a[b] with 'c', a[b].c
    Qualifier c = fac.newQualifier(null, 3, "c");
    a.addQualifier(c);
    // The resolve step should return unknown
    val = a.resolve(partVars);
    assertThat(val).isEqualTo(unknownOf(3));
  }

  static Attribute genAttr(AttributeFactory fac, Attr a) {
    long id = 1L;
    Attribute attr;
    if (a.unchecked) {
      attr = fac.maybeAttribute(1, a.name);
    } else {
      attr = fac.absoluteAttribute(1, a.name);
    }
    if (a.quals != null) {
      for (Object q : a.quals) {
        Qualifier qual = fac.newQualifier(null, id, q);
        attr.addQualifier(qual);
        id++;
      }
    }
    return attr;
  }
}
