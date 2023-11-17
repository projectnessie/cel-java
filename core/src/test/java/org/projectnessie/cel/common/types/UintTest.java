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
import static org.projectnessie.cel.common.types.Err.errUintOverflow;
import static org.projectnessie.cel.common.types.IntT.IntNegOne;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.IntT.maxIntJSON;
import static org.projectnessie.cel.common.types.MapT.MapType;
import static org.projectnessie.cel.common.types.NullT.NullValue;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TypeT.TypeType;
import static org.projectnessie.cel.common.types.UintT.UintType;
import static org.projectnessie.cel.common.types.UintT.UintZero;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import com.google.protobuf.Any;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.ULong;

public class UintTest {

  @Test
  void uintAdd() {
    assertThat(uintOf(4).add(uintOf(3)).equal(uintOf(7))).isSameAs(True);
    assertThat(uintOf(1).add(stringOf("-1")))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: uint.add(string)");
    assertThat(uintOf(-1L).add(uintOf(1))).isSameAs(errUintOverflow);
    assertThat(uintOf(-2L).add(uintOf(1)).equal(uintOf(-1L))).isSameAs(True);
  }

  @Test
  void uintCompare() {
    UintT lt = uintOf(204);
    UintT gt = uintOf(1300);
    assertThat(lt.compare(gt).equal(IntNegOne)).isSameAs(True);
    assertThat(gt.compare(lt).equal(IntOne)).isSameAs(True);
    assertThat(gt.compare(gt).equal(IntZero)).isSameAs(True);
    assertThat(gt.compare(TypeType))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: uint.compare(type)");

    assertThat(uintOf(0xffff800000000000L).compare(intOf(5))).isSameAs(IntOne);
    assertThat(uintOf(5).compare(intOf(5))).isSameAs(IntZero);
    assertThat(uintOf(5).compare(intOf(-5))).isSameAs(IntOne);
    assertThat(uintOf(5).compare(intOf(Integer.MAX_VALUE))).isSameAs(IntNegOne);

    assertThat(uintOf(0xffff800000000000L).compare(doubleOf(5))).isSameAs(IntOne);
    assertThat(uintOf(5).compare(doubleOf(5))).isSameAs(IntZero);
    assertThat(uintOf(5).compare(doubleOf(1e70d))).isSameAs(IntNegOne);
    assertThat(uintOf(5).compare(doubleOf(1e-70d))).isSameAs(IntOne);
  }

  @Test
  void uintConvertToNative_Any() {
    Any val = uintOf(-1L).convertToNative(Any.class);
    Any want = Any.pack(UInt64Value.of(-1L));
    assertThat(val).isEqualTo(want);
  }

  @Test
  @Disabled("CANNOT IMPLEMENT - JAVA VS GO - SIGNED VS UNSIGNED")
  void uintConvertToNative_Error() {
    assertThatThrownBy(() -> uintOf(10000).convertToNative(Integer.class));
    assertThatThrownBy(() -> uintOf(10000).convertToNative(int.class));
  }

  @Test
  void uintConvertToWrapper_Error() {
    assertThatThrownBy(() -> uintOf(10000).convertToNative(Int32Value.class));
    assertThatThrownBy(() -> uintOf(10000).convertToNative(Int64Value.class));
  }

  @Test
  void uintConvertToNative_Json() {
    // Value can be represented accurately as a JSON number.
    Value val = uintOf(maxIntJSON).convertToNative(Value.class);
    assertThat(val).isEqualTo(Value.newBuilder().setNumberValue(9007199254740991.0d).build());

    // Value converts to a JSON decimal string
    val = intOf(maxIntJSON + 1).convertToNative(Value.class);
    assertThat(val).isEqualTo(Value.newBuilder().setStringValue("9007199254740992").build());
  }

  @Test
  void uintConvertToNative_Ptr_Uint32() {
    Integer val = uintOf(10000).convertToNative(Integer.class);
    assertThat(val).isEqualTo(10000);
  }

  @Test
  void uintConvertToNative_Ptr_Uint64() {
    // 18446744073709551612 --> -4L
    ULong val = uintOf(-4L).convertToNative(ULong.class);
    assertThat(val).isEqualTo(ULong.valueOf(-4L));
  }

  @Test
  void uintConvertToNative_Wrapper() {
    long uint32max = 0xffffffffL;

    UInt32Value val = uintOf(uint32max).convertToNative(UInt32Value.class);
    UInt32Value want = UInt32Value.of((int) uint32max);
    assertThat(val).isEqualTo(want);

    long uint64max = 0xffffffffffffffffL;

    UInt64Value val2 = uintOf(uint64max).convertToNative(UInt64Value.class);
    UInt64Value want2 = UInt64Value.of(uint64max);
    assertThat(val2).isEqualTo(want2);
  }

  @Test
  void uintConvertToType() {
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
  void uintDivide() {
    assertThat(uintOf(3).divide(uintOf(2)).equal(uintOf(1))).isSameAs(True);
    assertThat(UintZero.divide(UintZero))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("divide by zero");
    assertThat(uintOf(1).divide(doubleOf(-1)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: uint.divide(double)");
  }

  @Test
  void uintEqual() {
    assertThat(uintOf(0).equal(False))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: uint.equal(bool)");
    assertThat(uintOf(0).equal(NullValue)).isSameAs(False);
    assertThat(uintOf(0).equal(intOf(0))).isSameAs(True);
    assertThat(uintOf(0).equal(intOf(1))).isSameAs(False);
    assertThat(uintOf(0x8000000000000000L).equal(intOf(0x8000000000000000L))).isSameAs(False);
    assertThat(uintOf(0xffffffffffffffffL).equal(intOf(0xffffffffffffffffL))).isSameAs(False);
    assertThat(uintOf(0).equal(uintOf(0))).isSameAs(True);
    assertThat(uintOf(0).equal(uintOf(1))).isSameAs(False);
    assertThat(uintOf(0).equal(doubleOf(0))).isSameAs(True);
    assertThat(uintOf(0).equal(doubleOf(1))).isSameAs(False);
    assertThat(uintOf(0).equal(stringOf("0"))).isSameAs(True);
    assertThat(uintOf(0).equal(stringOf("1"))).isSameAs(False);
  }

  @Test
  void uintModulo() {
    assertThat(uintOf(21).modulo(uintOf(2)).equal(uintOf(1))).isSameAs(True);
    assertThat(uintOf(21).modulo(UintZero))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("modulus by zero");
    assertThat(uintOf(21).modulo(IntOne))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: uint.modulo(int)");
  }

  @Test
  void uintMultiply() {
    assertThat(uintOf(2).multiply(uintOf(2)).equal(uintOf(4))).isSameAs(True);
    assertThat(uintOf(1).multiply(doubleOf(-4.0)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: uint.multiply(double)");
    // maxUInt64 / 2 --> 0x7fffffffffffffffL
    assertThat(uintOf(0x7fffffffffffffffL).multiply(uintOf(3))).isSameAs(errUintOverflow);
    assertThat(uintOf(0x7fffffffffffffffL).multiply(uintOf(2)).equal(uintOf(0xfffffffffffffffeL)))
        .isSameAs(True);
  }

  @Test
  void uintSubtract() {
    assertThat(uintOf(4).subtract(uintOf(3)).equal(uintOf(1))).isSameAs(True);
    assertThat(uintOf(1).subtract(intOf(1)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: uint.subtract(int)");
    assertThat(uintOf(0xfffffffffffffffeL).subtract(uintOf(0xffffffffffffffffL)))
        .isSameAs(errUintOverflow);
    assertThat(uintOf(0xffffffffffffffffL).subtract(uintOf(0xffffffffffffffffL)).equal(uintOf(0)))
        .isSameAs(True);
  }
}
