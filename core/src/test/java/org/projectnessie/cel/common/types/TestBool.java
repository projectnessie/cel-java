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
import static org.projectnessie.cel.common.types.BoolT.boolOf;
import static org.projectnessie.cel.common.types.BoolT.isBool;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.IntT.IntNegOne;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.UintT.UintZero;

import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.ref.Val;

class TestBool {

  @Test
  void BoolCompare() {
    assertThat(False.compare(True)).isEqualTo(IntNegOne);
    assertThat(True.compare(False)).isEqualTo(IntOne);
    assertThat(True.compare(True)).isEqualTo(IntZero);
    assertThat(False.compare(False)).isEqualTo(IntZero);
    assertThat(True.compare(UintZero)).extracting(Err::isError).isEqualTo(true);
  }

  //  @Test
  //  void BoolConvertToNative_Any() {
  //    val, err := True.ConvertToNative(anyValueType)
  //    if err != nil {
  //      t.Error(err)
  //    }
  //    pbVal, err := anypb.New(wrapperspb.Bool(true))
  //    if err != nil {
  //      t.Error(err)
  //    }
  //    if !proto.Equal(val.(proto.Message), pbVal) {
  //      t.Error("Error during conversion to protobuf.Any", val)
  //    }
  //  }

  @Test
  void BoolConvertToNative_Bool() {
    Boolean val = True.convertToNative(Boolean.class);
    assertThat(val).isTrue();
    val = False.convertToNative(boolean.class);
    assertThat(val).isFalse();
  }

  @Test
  void BoolConvertToNative_Error() {
    assertThatThrownBy(() -> True.convertToNative(String.class))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("native type conversion error from 'bool' to 'java.lang.String'");
  }

  //  @Test
  //  void BoolConvertToNative_Json() {
  //    val, err := True.ConvertToNative(jsonValueType)
  //    pbVal := &structpb.Value{Kind: &structpb.Value_BoolValue{BoolValue: true}}
  //    if err != nil {
  //      t.Error(err)
  //    } else if !proto.Equal(val.(proto.Message), pbVal) {
  //      t.Error("Error during conversion to json Value type", val)
  //    }
  //  }
  //
  //  @Test
  //  void BoolConvertToNative_Ptr() {
  //    ptrType := true
  //    refType := reflect.TypeOf(&ptrType)
  //    val, err := True.ConvertToNative(refType)
  //    if err != nil {
  //      t.Error(err)
  //    } else if !*val.(*bool) {
  //      t.Error("Error during conversion to *bool", val)
  //    }
  //  }
  //
  //  @Test
  //  void BoolConvertToNative_Wrapper() {
  //    val, err := True.ConvertToNative(boolWrapperType)
  //    pbVal := wrapperspb.Bool(true)
  //    if err != nil {
  //      t.Error(err)
  //    } else if !proto.Equal(val.(proto.Message), pbVal) {
  //      t.Error("Error during conversion to wrapper value type", val)
  //    }
  //  }

  @Test
  void BoolConvertToType() {
    assertThat(True.convertToType(StringT.StringType).equal(StringT.stringOf("true")))
        .isEqualTo(True);
    assertThat(True.convertToType(BoolT.BoolType)).isEqualTo(True);
    assertThat(True.convertToType(TypeValue.TypeType)).isSameAs(BoolT.BoolType);
    assertThat(True.convertToType(TimestampT.TimestampType))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("type conversion error from 'bool' to 'google.protobuf.Timestamp'");
  }

  @Test
  void BoolEqual() {
    assertThat(True.equal(True)).extracting(Val::booleanValue).isEqualTo(Boolean.TRUE);
    assertThat(False.equal(True)).extracting(Val::booleanValue).isEqualTo(Boolean.FALSE);
    assertThat(doubleOf(0.0d).equal(False))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
    assertThat(False.equal(doubleOf(0.0d)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
  }

  @Test
  void BoolNegate() {
    assertThat(True.negate()).isEqualTo(False);
    assertThat(False.negate()).isEqualTo(True);
  }

  @Test
  void BoolPredefined() {
    assertThat(boolOf(true)).isSameAs(True);
    assertThat(boolOf(false)).isSameAs(False);
    assertThat(boolOf(Boolean.TRUE)).isSameAs(True);
    assertThat(boolOf(Boolean.FALSE)).isSameAs(False);
  }

  @Test
  void IsBool() {
    assertThat(isBool(True)).isTrue();
    assertThat(isBool(False)).isTrue();
    assertThat(isBool(BoolT.BoolType)).isTrue();
    assertThat(isBool(IntZero)).isFalse();
  }
}
