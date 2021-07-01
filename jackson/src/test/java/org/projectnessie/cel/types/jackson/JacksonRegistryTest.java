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
package org.projectnessie.cel.types.jackson;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.MapT.newMaybeWrappedMap;
import static org.projectnessie.cel.common.types.NullT.NullValue;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.IntT;
import org.projectnessie.cel.common.types.ObjectT;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.types.jackson.types.MetaTest;
import org.projectnessie.cel.types.jackson.types.RefVariantB;

class JacksonRegistryTest {
  @Test
  void nessieBranch() {
    TypeRegistry reg = JacksonRegistry.newRegistry();

    RefVariantB refVariantB = RefVariantB.of("main", "cafebabe123412341234123412341234");

    Val branchVal = reg.nativeToValue(refVariantB);
    assertThat(branchVal).isInstanceOf(ObjectT.class);
    assertThat(branchVal.type().typeEnum()).isSameAs(TypeEnum.Object);
    assertThat(branchVal.type().typeName()).isEqualTo(refVariantB.getClass().getName());

    ObjectT branchObj = (ObjectT) branchVal;
    assertThat(branchObj.isSet(stringOf("foo")))
        .isInstanceOf(Err.class)
        .asString()
        .isEqualTo("no such field 'foo'");
    assertThat(branchObj.isSet(stringOf("name"))).isEqualTo(True);
    assertThat(branchObj.isSet(stringOf("hash"))).isEqualTo(True);
    assertThat(branchObj.get(stringOf("foo")))
        .isInstanceOf(Err.class)
        .asString()
        .isEqualTo("no such field 'foo'");
    assertThat(branchObj.get(stringOf("name"))).isEqualTo(stringOf("main"));
    assertThat(branchObj.get(stringOf("hash")))
        .isEqualTo(stringOf("cafebabe123412341234123412341234"));
  }

  @Test
  void nessieCommitMetaFull() {
    TypeRegistry reg = JacksonRegistry.newRegistry();

    Instant now = Instant.now();
    Instant nowMinus5 = now.minus(5, ChronoUnit.MINUTES);

    MetaTest cm =
        MetaTest.builder()
            .commitTime(now)
            .authorTime(nowMinus5)
            .committer("committer@projectnessie.org")
            .author("author@projectnessie.org")
            .hash("beeffeed123412341234123412341234")
            .message("Feed of beef")
            .signedOffBy("signed-off@projectnessie.org")
            .putProperties("prop-1", "value-1")
            .putProperties("prop-2", "value-2")
            .build();
    Val cmVal = reg.nativeToValue(cm);
    assertThat(cmVal).isInstanceOf(ObjectT.class);
    assertThat(cmVal.type().typeEnum()).isSameAs(TypeEnum.Object);
    assertThat(cmVal.type().typeName()).isEqualTo(cm.getClass().getName());
    assertThat(cmVal.type().typeEnum()).isSameAs(TypeEnum.Object);
    ObjectT cmObj = (ObjectT) cmVal;
    assertThat(cmObj.isSet(stringOf("foo")))
        .isInstanceOf(Err.class)
        .asString()
        .isEqualTo("no such field 'foo'");
    assertThat(cmObj.isSet(stringOf("commitTime"))).isEqualTo(True);
    assertThat(cmObj.isSet(stringOf("authorTime"))).isEqualTo(True);
    assertThat(cmObj.isSet(stringOf("committer"))).isEqualTo(True);
    assertThat(cmObj.isSet(stringOf("author"))).isEqualTo(True);
    assertThat(cmObj.isSet(stringOf("hash"))).isEqualTo(True);
    assertThat(cmObj.isSet(stringOf("message"))).isEqualTo(True);
    assertThat(cmObj.isSet(stringOf("signedOffBy"))).isEqualTo(True);
    assertThat(cmObj.isSet(stringOf("properties"))).isEqualTo(True);
    Map<String, String> expectMap = new HashMap<>();
    expectMap.put("prop-1", "value-1");
    expectMap.put("prop-2", "value-2");
    assertThat(cmObj.get(stringOf("foo")))
        .isInstanceOf(Err.class)
        .asString()
        .isEqualTo("no such field 'foo'");
    assertThat(cmObj.get(stringOf("commitTime"))).isEqualTo(timestampOf(now));
    assertThat(cmObj.get(stringOf("authorTime"))).isEqualTo(timestampOf(nowMinus5));
    assertThat(cmObj.get(stringOf("committer"))).isEqualTo(stringOf("committer@projectnessie.org"));
    assertThat(cmObj.get(stringOf("author"))).isEqualTo(stringOf("author@projectnessie.org"));
    assertThat(cmObj.get(stringOf("hash"))).isEqualTo(stringOf("beeffeed123412341234123412341234"));
    assertThat(cmObj.get(stringOf("message"))).isEqualTo(stringOf("Feed of beef"));
    assertThat(cmObj.get(stringOf("signedOffBy")))
        .isEqualTo(stringOf("signed-off@projectnessie.org"));
    assertThat(cmObj.get(stringOf("properties"))).isEqualTo(newMaybeWrappedMap(reg, expectMap));
  }

  @Test
  void nessieCommitMetaPart() {
    TypeRegistry reg = JacksonRegistry.newRegistry();

    Instant now = Instant.now();

    MetaTest cm =
        MetaTest.builder()
            .commitTime(now)
            .committer("committer@projectnessie.org")
            .hash("beeffeed123412341234123412341234")
            .message("Feed of beef")
            .build();
    Val cmVal = reg.nativeToValue(cm);
    assertThat(cmVal).isInstanceOf(ObjectT.class);
    assertThat(cmVal.type().typeEnum()).isSameAs(TypeEnum.Object);
    assertThat(cmVal.type().typeName()).isEqualTo(cm.getClass().getName());
    assertThat(cmVal.type().typeEnum()).isSameAs(TypeEnum.Object);
    ObjectT cmObj = (ObjectT) cmVal;
    assertThat(cmObj.isSet(stringOf("foo")))
        .isInstanceOf(Err.class)
        .asString()
        .isEqualTo("no such field 'foo'");
    assertThat(cmObj.isSet(stringOf("commitTime"))).isEqualTo(True);
    assertThat(cmObj.isSet(stringOf("authorTime"))).isEqualTo(False);
    assertThat(cmObj.isSet(stringOf("committer"))).isEqualTo(True);
    assertThat(cmObj.isSet(stringOf("author"))).isEqualTo(False);
    assertThat(cmObj.isSet(stringOf("hash"))).isEqualTo(True);
    assertThat(cmObj.isSet(stringOf("message"))).isEqualTo(True);
    assertThat(cmObj.isSet(stringOf("signedOffBy"))).isEqualTo(False);
    assertThat(cmObj.isSet(stringOf("properties"))).isEqualTo(True); // just empty
    assertThat(cmObj.get(stringOf("foo")))
        .isInstanceOf(Err.class)
        .asString()
        .isEqualTo("no such field 'foo'");
    assertThat(cmObj.get(stringOf("commitTime"))).isEqualTo(timestampOf(now));
    assertThat(cmObj.get(stringOf("authorTime"))).isEqualTo(NullValue);
    assertThat(cmObj.get(stringOf("committer"))).isEqualTo(stringOf("committer@projectnessie.org"));
    assertThat(cmObj.get(stringOf("author"))).isEqualTo(NullValue);
    assertThat(cmObj.get(stringOf("hash"))).isEqualTo(stringOf("beeffeed123412341234123412341234"));
    assertThat(cmObj.get(stringOf("message"))).isEqualTo(stringOf("Feed of beef"));
    assertThat(cmObj.get(stringOf("signedOffBy"))).isEqualTo(NullValue);
    assertThat(cmObj.get(stringOf("properties"))).isEqualTo(newMaybeWrappedMap(reg, emptyMap()));
  }

  @Test
  void copy() {
    TypeRegistry reg = JacksonRegistry.newRegistry();
    assertThat(reg).extracting(TypeRegistry::copy).isSameAs(reg);
  }

  @Test
  void registerType() {
    TypeRegistry reg = JacksonRegistry.newRegistry();
    assertThatThrownBy(() -> reg.registerType(IntT.IntType))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
