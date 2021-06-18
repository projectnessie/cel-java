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
package org.projectnessie.cel.common.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.IntT.IntNegOne;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UintT.UintZero;

import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Value;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.ref.Val;

class BoolTest {

  @Test
  void boolCompare() {
    assertThat(False.compare(True)).isEqualTo(IntNegOne);
    assertThat(True.compare(False)).isEqualTo(IntOne);
    assertThat(True.compare(True)).isEqualTo(IntZero);
    assertThat(False.compare(False)).isEqualTo(IntZero);
    assertThat(True.compare(UintZero)).matches(Err::isError);
  }

  @Test
  void boolConvertToNative_Any() {
    Object val = True.convertToNative(Any.class);
    Any pbVal = Any.pack(BoolValue.of(true));
    assertThat(val).isEqualTo(pbVal);
  }

  @Test
  void boolConvertToNative_Bool() {
    Boolean val = True.convertToNative(Boolean.class);
    assertThat(val).isTrue();
    val = False.convertToNative(boolean.class);
    assertThat(val).isFalse();
  }

  @Test
  void boolConvertToNative_Error() {
    assertThatThrownBy(() -> True.convertToNative(String.class))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("native type conversion error from 'bool' to 'java.lang.String'");
  }

  @Test
  void boolConvertToNative_Json() {
    Value val = True.convertToNative(Value.class);
    Value pbVal = Value.newBuilder().setBoolValue(true).build();
    assertThat(val).isEqualTo(pbVal);
  }

  @Test
  void boolConvertToNative_Ptr() {
    Boolean val = True.convertToNative(Boolean.class);
    assertThat(val).isTrue();
  }

  @Test
  void boolConvertToNative() {
    Boolean val = True.convertToNative(boolean.class);
    assertThat(val).isTrue();
  }

  @Test
  void boolConvertToNative_Wrapper() {
    BoolValue val = True.convertToNative(BoolValue.class);
    BoolValue pbVal = BoolValue.of(true);
    assertThat(val).isEqualTo(pbVal);
  }

  @Test
  void boolConvertToType() {
    assertThat(True.convertToType(StringT.StringType).equal(StringT.stringOf("true")))
        .isEqualTo(True);
    assertThat(True.convertToType(BoolT.BoolType)).isEqualTo(True);
    assertThat(True.convertToType(TypeT.TypeType)).isSameAs(BoolT.BoolType);
    assertThat(True.convertToType(TimestampT.TimestampType))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("type conversion error from 'bool' to 'google.protobuf.Timestamp'");
  }

  @Test
  void boolEqual() {
    assertThat(True.equal(True)).extracting(Val::booleanValue).isEqualTo(Boolean.TRUE);
    assertThat(False.equal(True)).extracting(Val::booleanValue).isEqualTo(Boolean.FALSE);
    assertThat(doubleOf(0.0d).equal(False))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: double.equal(bool)");
    assertThat(False.equal(doubleOf(0.0d)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: bool.equal(double)");
  }

  @Test
  void boolNegate() {
    assertThat(True.negate()).isEqualTo(False);
    assertThat(False.negate()).isEqualTo(True);
  }

  @Test
  void boolPredefined() {
    assertThat(boolOf(true)).isSameAs(True);
    assertThat(boolOf(false)).isSameAs(False);
    assertThat(boolOf(Boolean.TRUE)).isSameAs(True);
    assertThat(boolOf(Boolean.FALSE)).isSameAs(False);
  }
}
