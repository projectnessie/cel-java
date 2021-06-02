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
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DoubleT.DoubleType;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.Err.errUintOverflow;
import static org.projectnessie.cel.common.types.IntT.IntNegOne;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.MapT.MapType;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;
import static org.projectnessie.cel.common.types.UintT.UintType;
import static org.projectnessie.cel.common.types.UintT.UintZero;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import org.junit.jupiter.api.Test;

public class TestUint {

  @Test
  void Uint_Add() {
    assertThat(uintOf(4).add(uintOf(3)).equal(uintOf(7))).isSameAs(True);
    assertThat(uintOf(1).add(stringOf("-1")))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
    assertThat(uintOf(-1L).add(uintOf(1))).isSameAs(errUintOverflow);
    assertThat(uintOf(-2L).add(uintOf(1)).equal(uintOf(-1L))).isSameAs(True);
  }

  @Test
  void Uint_Compare() {
    UintT lt = uintOf(204);
    UintT gt = uintOf(1300);
    assertThat(lt.compare(gt).equal(IntNegOne)).isSameAs(True);
    assertThat(gt.compare(lt).equal(IntOne)).isSameAs(True);
    assertThat(gt.compare(gt).equal(IntZero)).isSameAs(True);
    assertThat(gt.compare(TypeType))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
  }

  //  @Test
  //	void Uint_ConvertToNative_Any() {
  //		val, err := uintOf(math.MaxUint64).ConvertToNative(anyValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want, err := anypb.New(wrapperspb.UInt64(math.MaxUint64))
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if !proto.equal(val.(proto.Message), want) {
  //			t.Errorf("Got %v, wanted %v", val, want)
  //		}
  //	}
  //
  //  @Test
  //	void Uint_ConvertToNative_Error() {
  //		val, err := uintOf(10000).ConvertToNative(reflect.TypeOf(int(0)))
  //		if err == nil {
  //			t.Errorf("Got '%v', expected error", val)
  //		}
  //	}
  //
  //  @Test
  //	void Uint_ConvertToNative_Json() {
  //		// Value can be represented accurately as a JSON number.
  //		val, err := uintOf(maxIntJSON).ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Error(err)
  //		} else if !proto.equal(val.(proto.Message),
  //			structpb.NewNumberValue(9007199254740991.0)) {
  //			t.Errorf("Got '%v', expected a json number for a 32-bit uint", val)
  //		}
  //
  //		// Value converts to a JSON decimal string
  //		val, err = Int(maxIntJSON + 1).ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Error(err)
  //		} else if !proto.equal(val.(proto.Message), structpb.NewStringValue("9007199254740992")) {
  //			t.Errorf("Got '%v', expected a json string for a 64-bit uint", val)
  //		}
  //	}
  //
  //  @Test
  //	void Uint_ConvertToNative_Ptr_Uint32() {
  //		ptrType := uint32(0)
  //		val, err := uintOf(10000).ConvertToNative(reflect.TypeOf(&ptrType))
  //		if err != nil {
  //			t.Error(err)
  //		} else if *val.(*uint32) != uint32(10000) {
  //			t.Errorf("Error converting uint to *uint32. Got '%v', expected 10000.", val)
  //		}
  //	}
  //
  //  @Test
  //	void Uint_ConvertToNative_Ptr_Uint64() {
  //		ptrType := uint64(0)
  //		val, err := uintOf(18446744073709551612).ConvertToNative(reflect.TypeOf(&ptrType))
  //		if err != nil {
  //			t.Error(err)
  //		} else if *val.(*uint64) != uint64(18446744073709551612) {
  //			t.Errorf("Error converting uint to *uint64. Got '%v', expected 18446744073709551612.", val)
  //		}
  //	}
  //
  //  @Test
  //	void Uint_ConvertToNative_Wrapper() {
  //		val, err := uintOf(math.MaxUint32).ConvertToNative(uint32WrapperType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want := wrapperspb.UInt32(math.MaxUint32)
  //		if !proto.equal(val.(proto.Message), want) {
  //			t.Errorf("Got %v, wanted %v", val, want)
  //		}
  //
  //		val, err = uintOf(math.MaxUint64).ConvertToNative(uint64WrapperType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want2 := wrapperspb.UInt64(math.MaxUint64)
  //		if !proto.equal(val.(proto.Message), want2) {
  //			t.Errorf("Got %v, wanted %v", val, want2)
  //		}
  //	}

  @Test
  void Uint_ConvertToType() {
    // 18446744073709551612L
    // --> 0xFFFFFFFFFFFFFFFCL
    assertThat(uintOf(0xFFFFFFFFFFFFFFFCL).convertToType(IntType))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("range error converting 18446744073709551612 to int");
    assertThat(uintOf(4).convertToType(IntType).equal(intOf(4))).isSameAs(True);
    assertThat(uintOf(4).convertToType(UintType).equal(uintOf(4))).isSameAs(True);
    assertThat(uintOf(4).convertToType(DoubleType).equal(doubleOf(4))).isSameAs(True);
    assertThat(uintOf(4).convertToType(StringType).equal(stringOf("4"))).isSameAs(True);
    assertThat(uintOf(4).convertToType(TypeType).equal(UintType)).isSameAs(True);
    assertThat(uintOf(4).convertToType(MapType))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("type conversion error from 'uint' to 'map'");
  }

  @Test
  void Uint_Divide() {
    assertThat(uintOf(3).divide(uintOf(2)).equal(uintOf(1))).isSameAs(True);
    assertThat(UintZero.divide(UintZero))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("divide by zero");
    assertThat(uintOf(1).divide(doubleOf(-1)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
  }

  @Test
  void Uint_Equal() {
    assertThat(uintOf(0).equal(False))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
  }

  @Test
  void Uint_Modulo() {
    assertThat(uintOf(21).modulo(uintOf(2)).equal(uintOf(1))).isSameAs(True);
    assertThat(uintOf(21).modulo(UintZero))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("modulus by zero");
    assertThat(uintOf(21).modulo(IntOne))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
  }

  @Test
  void Uint_Multiply() {
    assertThat(uintOf(2).multiply(uintOf(2)).equal(uintOf(4))).isSameAs(True);
    assertThat(uintOf(1).multiply(doubleOf(-4.0)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
    // maxUInt64 / 2 --> 0x7fffffffffffffffL
    assertThat(uintOf(0x7fffffffffffffffL).multiply(uintOf(3))).isSameAs(errUintOverflow);
    assertThat(uintOf(0x7fffffffffffffffL).multiply(uintOf(2)).equal(uintOf(0xfffffffffffffffeL)))
        .isSameAs(True);
  }

  @Test
  void Uint_Subtract() {
    assertThat(uintOf(4).subtract(uintOf(3)).equal(uintOf(1))).isSameAs(True);
    assertThat(uintOf(1).subtract(intOf(1)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
    assertThat(uintOf(0xfffffffffffffffeL).subtract(uintOf(0xffffffffffffffffL)))
        .isSameAs(errUintOverflow);
    assertThat(uintOf(0xffffffffffffffffL).subtract(uintOf(0xffffffffffffffffL)).equal(uintOf(0)))
        .isSameAs(True);
  }
}
