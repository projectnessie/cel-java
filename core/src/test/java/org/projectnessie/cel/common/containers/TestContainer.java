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
package org.projectnessie.cel.common.containers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.common.containers.Container.abbrevs;
import static org.projectnessie.cel.common.containers.Container.alias;
import static org.projectnessie.cel.common.containers.Container.defaultContainer;
import static org.projectnessie.cel.common.containers.Container.name;
import static org.projectnessie.cel.common.containers.Container.newContainer;
import static org.projectnessie.cel.common.containers.Container.toQualifiedName;

import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Call;
import com.google.api.expr.v1alpha1.Expr.Ident;
import com.google.api.expr.v1alpha1.Expr.Select;
import org.junit.jupiter.api.Test;

public class TestContainer {

  @Test
  void ResolveCandidateNames() {
    Container c = newContainer(name("a.b.c.M.N"));
    String[] names = c.resolveCandidateNames("R.s");
    String[] want =
        new String[] {
          "a.b.c.M.N.R.s", "a.b.c.M.R.s", "a.b.c.R.s", "a.b.R.s", "a.R.s", "R.s",
        };
    assertThat(names).containsExactly(want);
  }

  @Test
  void ResolveCandidateNames_FullyQualifiedName() {
    Container c = newContainer(name("a.b.c.M.N"));
    // The leading '.' indicates the name is already fully-qualified.
    String[] names = c.resolveCandidateNames(".R.s");
    String[] want = new String[] {"R.s"};
    assertThat(names).containsExactly(want);
  }

  @Test
  void ResolveCandidateNames_EmptyContainer() {
    String[] names = defaultContainer.resolveCandidateNames("R.s");
    String[] want = new String[] {"R.s"};
    assertThat(names).containsExactly(want);
  }

  @Test
  void Abbrevs() {
    Container abbr = defaultContainer.extend(abbrevs("my.alias.R"));
    String[] names = abbr.resolveCandidateNames("R");
    String[] want =
        new String[] {
          "my.alias.R",
        };
    assertThat(names).containsExactly(want);
    Container c = newContainer(name("a.b.c"), abbrevs("my.alias.R"));
    names = c.resolveCandidateNames("R");
    want =
        new String[] {
          "my.alias.R",
        };
    assertThat(names).containsExactly(want);
    names = c.resolveCandidateNames("R.S.T");
    want =
        new String[] {
          "my.alias.R.S.T",
        };
    assertThat(names).containsExactly(want);
    names = c.resolveCandidateNames("S");
    want =
        new String[] {
          "a.b.c.S", "a.b.S", "a.S", "S",
        };
    assertThat(names).containsExactly(want);
  }

  @Test
  void Aliasing_Errors() {
    assertThatThrownBy(() -> newContainer(abbrevs("my.alias.R", "yer.other.R")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "abbreviation collides with existing reference: "
                + "name=yer.other.R, abbreviation=R, existing=my.alias.R");

    assertThatThrownBy(() -> newContainer(name("a.b.c.M.N"), abbrevs("my.alias.a", "yer.other.b")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "abbreviation collides with container name: name=my.alias.a, "
                + "abbreviation=a, container=a.b.c.M.N");

    assertThatThrownBy(() -> newContainer(abbrevs(".bad")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid qualified name: .bad, wanted name of the form 'qualified.name'");

    assertThatThrownBy(() -> newContainer(abbrevs("bad.alias.")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid qualified name: bad.alias., wanted name of the form 'qualified.name'");

    assertThatThrownBy(() -> newContainer(alias("a", "b")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("alias must refer to a valid qualified name: a");

    assertThatThrownBy(() -> newContainer(alias("my.alias", "b.c")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("alias must be non-empty and simple (not qualified): alias=b.c");

    assertThatThrownBy(() -> newContainer(alias(".my.qual.name", "a")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("qualified name must not begin with a leading '.': .my.qual.name");

    assertThatThrownBy(() -> newContainer(alias(".my.qual.name", "a")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("qualified name must not begin with a leading '.': .my.qual.name");
  }

  @Test
  void Extend_Alias() {
    Container c = defaultContainer.extend(alias("test.alias", "alias"));
    assertThat(c.aliasSet()).containsEntry("alias", "test.alias");
    c = c.extend(name("with.container"));
    assertThat(c.name()).isEqualTo("with.container");
    assertThat(c.aliasSet()).containsEntry("alias", "test.alias");
  }

  @Test
  void Extend_Name() {
    Container c = defaultContainer.extend(name(""));
    assertThat(c.name()).isEmpty();
    c = defaultContainer.extend(name("hello.container"));
    assertThat(c.name()).isEqualTo("hello.container");
    c = c.extend(name("goodbye.container"));
    assertThat(c.name()).isEqualTo("goodbye.container");
    Container cc = c;
    assertThatThrownBy(() -> cc.extend(name(".bad.container")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("container name must not contain a leading '.': .bad.container");
  }

  @Test
  void ToQualifiedName() {
    Expr ident = Expr.newBuilder().setId(0).setIdentExpr(Ident.newBuilder().setName("var")).build();
    String idName = toQualifiedName(ident);
    assertThat(idName).isEqualTo("var");
    Expr sel =
        Expr.newBuilder()
            .setId(0)
            .setSelectExpr(Select.newBuilder().setOperand(ident).setField("qualifier"))
            .build();
    String qualName = toQualifiedName(sel);
    assertThat(qualName).isEqualTo("var.qualifier");

    sel =
        Expr.newBuilder()
            .setId(0)
            .setSelectExpr(
                Select.newBuilder().setOperand(ident).setField("qualifier").setTestOnly(true))
            .build();

    assertThat(toQualifiedName(sel)).isNull();

    Expr unary =
        Expr.newBuilder()
            .setId(0)
            .setCallExpr(Call.newBuilder().setFunction("!_").addArgs(ident))
            .build();
    sel =
        Expr.newBuilder()
            .setId(0)
            .setSelectExpr(Select.newBuilder().setOperand(unary).setField("qualifier"))
            .build();
    assertThat(toQualifiedName(sel)).isNull();
  }
}
