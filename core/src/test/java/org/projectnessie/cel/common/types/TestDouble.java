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
import static org.projectnessie.cel.common.types.DoubleT.DoubleType;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.IntT.IntNegOne;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.TimestampType;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;
import static org.projectnessie.cel.common.types.UintT.UintType;

import com.google.protobuf.Any;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class TestDouble {

  @Test
  void doubleAdd() {
    assertThat(doubleOf(4).add(doubleOf(-3.5)).equal(doubleOf(0.5))).isSameAs(True);
    assertThat(doubleOf(-1).add(stringOf("-1"))).matches(Err::isError);
  }

  @Test
  void doubleCompare() {
    DoubleT lt = doubleOf(-1300);
    DoubleT gt = doubleOf(204);
    assertThat(lt.compare(gt).equal(IntNegOne)).isSameAs(True);
    assertThat(gt.compare(lt).equal(IntOne)).isSameAs(True);
    assertThat(gt.compare(gt).equal(IntZero)).isSameAs(True);
    assertThat(gt.compare(TypeType)).matches(Err::isError);
  }

  @Test
  void doubleConvertToNative_Any() {
    Any val = doubleOf(Double.MAX_VALUE).convertToNative(Any.class);
    Any want = Any.pack(DoubleValue.of(1.7976931348623157e+308));
    assertThat(val).isEqualTo(want);
  }

  @Test
  void doubleConvertToNative_Error() {
    assertThatThrownBy(() -> doubleOf(-10000).convertToNative(String.class))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("native type conversion error from 'double' to 'java.lang.String'");
  }

  @Test
  void doubleConvertToNative_Float32() {
    Float val = doubleOf(3.1415).convertToNative(Float.class);
    assertThat(val).isEqualTo(3.1415f);
  }

  @Test
  void doubleConvertToNative_Float64() {
    Double val = doubleOf(30000000.1).convertToNative(Double.class);
    assertThat(val).isEqualTo(30000000.1d);
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void doubleConvertToNative_Json() {
    //  		DoubleT val = doubleOf(-1.4).convertToNative(jsonValueType)
    //  		pbVal := structpb.NewNumberValue(-1.4)
    //  		if err != nil {
    //  			t.Error(err)
    //  		} else if !proto.equal(val.(proto.Message), pbVal) {
    //  			t.Errorf("Got '%v', expected -1.4", val)
    //  		}
    //
    //  		val, err = doubleOf(math.NaN()).convertToNative(jsonValueType)
    //  		if err != nil {
    //  			t.Error(err)
    //  		} else {
    //  			v := val.(*structpb.Value)
    //  			if !math.IsNaN(v.GetNumberValue()) {
    //  				t.Errorf("Got '%v', expected NaN", val)
    //  			}
    //  		}
    //
    //  		val, err = doubleOf(math.Inf(-1)).convertToNative(jsonValueType)
    //  		pbVal = structpb.NewNumberValue(math.Inf(-1))
    //  		if err != nil {
    //  			t.Error(err)
    //  		} else if !proto.equal(val.(proto.Message), pbVal) {
    //  			t.Errorf("Got '%v', expected -Infinity", val)
    //  		}
    //  		val, err = doubleOf(math.Inf(0)).convertToNative(jsonValueType)
    //  		pbVal = structpb.NewNumberValue(math.Inf(0))
    //  		if err != nil {
    //  			t.Error(err)
    //  		} else if !proto.equal(val.(proto.Message), pbVal) {
    //  			t.Errorf("Got '%v', expected Infinity", val)
    //  		}
  }

  @Test
  void doubleConvertToNative_Ptr_Float32() {
    Float val = doubleOf(3.1415).convertToNative(Float.class);
    assertThat(val).isEqualTo(3.1415f);
  }

  @Test
  void doubleConvertToNative_Ptr_Float64() {
    Double val = doubleOf(30000000.1).convertToNative(Double.class);
    assertThat(val).isEqualTo(30000000.1d);
  }

  @Test
  void doubleConvertToNative_Wrapper() {
    FloatValue val = doubleOf(3.1415d).convertToNative(FloatValue.class);
    FloatValue want = FloatValue.of(3.1415f);
    assertThat(val).isEqualTo(want);

    DoubleValue val2 = doubleOf(Double.MAX_VALUE).convertToNative(DoubleValue.class);
    DoubleValue want2 = DoubleValue.of(1.7976931348623157e+308d);
    assertThat(val2).isEqualTo(want2);
  }

  @Test
  void doubleConvertToType() {
    assertThat(doubleOf(-4.5d).convertToType(UintType)).matches(Err::isError);
    assertThat(doubleOf(-4.5d).convertToType(DoubleType).equal(doubleOf(-4.5))).isSameAs(True);
    assertThat(doubleOf(-4.5d).convertToType(StringType).equal(stringOf("-4.5"))).isSameAs(True);
    assertThat(doubleOf(-4.5d).convertToType(TypeType).equal(DoubleType)).isSameAs(True);
    assertThat(doubleOf(-4.5d).convertToType(TimestampType)).matches(Err::isError);
    assertThat(doubleOf(-4.49d).convertToType(IntType).equal(intOf(-4))).isSameAs(True);
    assertThat(doubleOf(-4.51d).convertToType(IntType).equal(intOf(-5))).isSameAs(True);
    // TODO DOCUMENT: Java rounds the following to -4, but Go rounds it to -5 !!!
    assertThat(doubleOf(-4.5d).convertToType(IntType).equal(intOf(-4))).isSameAs(True);
    assertThat(doubleOf(4.49d).convertToType(IntType).equal(intOf(4))).isSameAs(True);
    assertThat(doubleOf(4.51d).convertToType(IntType).equal(intOf(5))).isSameAs(True);
    assertThat(doubleOf(4.5d).convertToType(IntType).equal(intOf(5))).isSameAs(True);
  }

  @Test
  void doubleDivide() {
    assertThat(doubleOf(3).divide(doubleOf(1.5)).equal(doubleOf(2))).isSameAs(True);
    double z = 0.0d; // Avoid 0.0 since const div by zero is an error.
    assertThat(doubleOf(1.1).divide(doubleOf(0)).equal(doubleOf(1.1 / z))).isSameAs(True);
    assertThat(doubleOf(1.1).divide(IntNegOne)).matches(Err::isError);
  }

  @Test
  void doubleEqual() {
    assertThat(doubleOf(0).equal(False)).matches(Err::isError);
  }

  @Test
  void doubleMultiply() {
    assertThat(doubleOf(1.1).multiply(doubleOf(-1.2)).equal(doubleOf(-1.32))).isSameAs(True);
    assertThat(doubleOf(1.1).multiply(IntNegOne)).matches(Err::isError);
  }

  @Test
  void doubleNegate() {
    assertThat(doubleOf(1.1).negate().equal(doubleOf(-1.1))).isSameAs(True);
  }

  @Test
  void doubleSubtract() {
    assertThat(doubleOf(4).subtract(doubleOf(-3.5)).equal(doubleOf(7.5))).isSameAs(True);
    assertThat(doubleOf(1.1).subtract(IntNegOne)).matches(Err::isError);
  }
}
