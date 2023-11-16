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
import static org.projectnessie.cel.common.types.DurationT.DurationType;
import static org.projectnessie.cel.common.types.Err.errIntOverflow;
import static org.projectnessie.cel.common.types.IntT.IntNegOne;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.IntT.maxIntJSON;
import static org.projectnessie.cel.common.types.NullT.NullValue;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.TimestampType;
import static org.projectnessie.cel.common.types.TimestampT.ZoneIdZ;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.TypeT.TypeType;
import static org.projectnessie.cel.common.types.UintT.UintType;
import static org.projectnessie.cel.common.types.UintT.UintZero;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import com.google.protobuf.Any;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.Value;
import java.time.Instant;
import org.junit.jupiter.api.Test;

public class IntTest {

  @Test
  void intAdd() {
    assertThat(intOf(4).add(intOf(-3)).equal(intOf(1))).isSameAs(True);
    assertThat(intOf(-1).add(stringOf("-1")))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: int.add(string)");
    for (int i = 1; i <= 10; i++) {
      assertThat(intOf(Long.MAX_VALUE).add(intOf(i))).isSameAs(errIntOverflow);
      assertThat(intOf(Long.MIN_VALUE).add(intOf(-i))).isSameAs(errIntOverflow);
      assertThat(intOf(Long.MAX_VALUE - i).add(intOf(i))).isEqualTo(intOf(Long.MAX_VALUE));
      assertThat(intOf(Long.MIN_VALUE + i).add(intOf(-i))).isEqualTo(intOf(Long.MIN_VALUE));
    }
  }

  @Test
  void intCompare() {
    IntT lt = intOf(-1300);
    IntT gt = intOf(204);
    assertThat(lt.compare(gt)).isSameAs(IntNegOne);
    assertThat(gt.compare(lt)).isSameAs(IntOne);
    assertThat(gt.compare(gt)).isSameAs(IntZero);
    assertThat(gt.compare(TypeType))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: int.compare(type)");

    assertThat(intOf(-5).compare(uintOf(5))).isSameAs(IntNegOne);
    assertThat(intOf(5).compare(uintOf(5))).isSameAs(IntZero);
    assertThat(intOf(5).compare(uintOf(0x8000000000000000L))).isSameAs(IntNegOne);

    assertThat(intOf(-5).compare(doubleOf(5))).isSameAs(IntNegOne);
    assertThat(intOf(5).compare(doubleOf(5))).isSameAs(IntZero);
    assertThat(intOf(5).compare(doubleOf(1e70d))).isSameAs(IntNegOne);
    assertThat(intOf(5).compare(doubleOf(1e-70d))).isSameAs(IntOne);
  }

  @Test
  void intConvertToNative_Any() {
    Any val = intOf(Long.MAX_VALUE).convertToNative(Any.class);
    Any want = Any.pack(Int64Value.of(Long.MAX_VALUE));
    assertThat(val).isEqualTo(want);
  }

  @Test
  void intConvertToNative_Error() {
    Value val = intOf(1).convertToNative(Value.class);
    //          		if err == nil {
    //          			t.Errorf("Got '%v', expected error", val)
    //          		}
  }

  @Test
  void intConvertToNative_Int32() {
    Integer val = intOf(20050).convertToNative(Integer.class);
    assertThat(val).isEqualTo(20050);
  }

  @Test
  void intConvertToNative_Int64() {
    // Value greater than max int32.
    Long val = intOf(4147483648L).convertToNative(Long.class);
    assertThat(val).isEqualTo(4147483648L);
  }

  @Test
  void intConvertToNative_Json() {
    // Value can be represented accurately as a JSON number.
    Value val = intOf(maxIntJSON).convertToNative(Value.class);
    assertThat(val).isEqualTo(Value.newBuilder().setNumberValue(9007199254740991.0).build());

    // Value converts to a JSON decimal string.
    val = intOf(maxIntJSON + 1).convertToNative(Value.class);
    assertThat(val).isEqualTo(Value.newBuilder().setStringValue("9007199254740992").build());
  }

  @Test
  void intConvertToNative_Ptr_Int32() {
    Integer val = intOf(20050).convertToNative(Integer.class);
    assertThat(val).isEqualTo(20050);
  }

  @Test
  void intConvertToNative_Ptr_Int64() {
    // Value greater than max int32.
    Long val = intOf(1L + Integer.MAX_VALUE).convertToNative(Long.class);
    assertThat(val).isEqualTo(1L + Integer.MAX_VALUE);
  }

  @Test
  void intConvertToNative_Wrapper() {
    Int32Value val = intOf(Integer.MAX_VALUE).convertToNative(Int32Value.class);
    Int32Value want = Int32Value.of(Integer.MAX_VALUE);
    assertThat(val).isEqualTo(want);

    Int64Value val2 = intOf(Long.MIN_VALUE).convertToNative(Int64Value.class);
    Int64Value want2 = Int64Value.of(Long.MIN_VALUE);
    assertThat(val2).isEqualTo(want2);
  }

  @Test
  void intConvertToType() {
    assertThat(intOf(-4).convertToType(IntType).equal(intOf(-4))).isSameAs(True);
    assertThat(intOf(-1).convertToType(UintType))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("range error converting -1 to uint");
    assertThat(intOf(-4).convertToType(DoubleType).equal(doubleOf(-4))).isSameAs(True);
    assertThat(intOf(-4).convertToType(StringType).equal(stringOf("-4"))).isSameAs(True);
    assertThat(intOf(-4).convertToType(TypeType)).isSameAs(IntType);
    assertThat(intOf(-4).convertToType(DurationType))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("type conversion error from 'int' to 'google.protobuf.Duration'");
    int celtsSecs = 946684800;
    TimestampT celts = timestampOf(Instant.ofEpochSecond(celtsSecs).atZone(ZoneIdZ));
    assertThat(intOf(celtsSecs).convertToType(TimestampType).equal(celts)).isSameAs(True);
  }

  @Test
  void intDivide() {
    assertThat(intOf(3).divide(intOf(2)).equal(intOf(1))).isSameAs(True);
    assertThat(IntZero.divide(IntZero))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("divide by zero");
    assertThat(intOf(1).divide(doubleOf(-1)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: int.divide(double)");
    assertThat(intOf(Long.MIN_VALUE).divide(intOf(-1))).isSameAs(errIntOverflow);
  }

  @Test
  void intEqual() {
    assertThat(intOf(0).equal(False))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: int.equal(bool)");
    assertThat(intOf(0).equal(NullValue)).isSameAs(False);
    assertThat(intOf(0).equal(stringOf("0"))).isSameAs(True);
    assertThat(intOf(0).equal(stringOf("1"))).isSameAs(False);
    assertThat(intOf(0).equal(intOf(0))).isSameAs(True);
    assertThat(intOf(0).equal(intOf(1))).isSameAs(False);
    assertThat(intOf(0).equal(uintOf(0))).isSameAs(True);
    assertThat(intOf(0).equal(uintOf(1))).isSameAs(False);
    assertThat(intOf(0x8000000000000000L).equal(uintOf(0x8000000000000000L))).isSameAs(False);
    assertThat(intOf(0xffffffffffffffffL).equal(uintOf(0xffffffffffffffffL))).isSameAs(False);
    assertThat(intOf(0).equal(doubleOf(0))).isSameAs(True);
    assertThat(intOf(0).equal(doubleOf(1))).isSameAs(False);
    assertThat(intOf(0).equal(stringOf("0"))).isSameAs(True);
    assertThat(intOf(0).equal(stringOf("1"))).isSameAs(False);
  }

  @Test
  void intModulo() {
    assertThat(intOf(21).modulo(intOf(2)).equal(intOf(1))).isSameAs(True);
    assertThat(intOf(21).modulo(IntZero))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("modulus by zero");
    assertThat(intOf(21).modulo(UintZero))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: int.modulo(uint)");
    assertThat(intOf(Long.MIN_VALUE).modulo(intOf(-1))).isSameAs(errIntOverflow);
  }

  @Test
  void intMultiply() {
    assertThat(intOf(2).multiply(intOf(-2)).equal(intOf(-4))).isSameAs(True);
    assertThat(intOf(1).multiply(doubleOf(-4.0)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: int.multiply(double)");
    assertThat(intOf(Long.MAX_VALUE / 2).multiply(intOf(3))).isSameAs(errIntOverflow);
    assertThat(intOf(Long.MIN_VALUE / 2).multiply(intOf(3))).isSameAs(errIntOverflow);
    assertThat(intOf(Long.MAX_VALUE / 2).multiply(intOf(2)).equal(intOf(Long.MAX_VALUE - 1)))
        .isSameAs(True);
    assertThat(intOf(Long.MIN_VALUE / 2).multiply(intOf(2)).equal(intOf(Long.MIN_VALUE)))
        .isSameAs(True);
    assertThat(intOf(Long.MAX_VALUE / 2).multiply(intOf(-2)).equal(intOf(Long.MIN_VALUE + 2)))
        .isSameAs(True);
    assertThat(intOf((Long.MIN_VALUE + 2) / 2).multiply(intOf(-2)).equal(intOf(Long.MAX_VALUE - 1)))
        .isSameAs(True);
    assertThat(intOf(Long.MIN_VALUE).multiply(intOf(-1))).isSameAs(errIntOverflow);
  }

  @Test
  void intNegate() {
    assertThat(intOf(1).negate().equal(intOf(-1))).isSameAs(True);
    assertThat(intOf(Long.MIN_VALUE).negate()).isSameAs(errIntOverflow);
    assertThat(intOf(Long.MAX_VALUE).negate().equal(intOf(Long.MIN_VALUE + 1))).isSameAs(True);
  }

  @Test
  void intSubtract() {
    assertThat(intOf(4).subtract(intOf(-3)).equal(intOf(7))).isSameAs(True);
    assertThat(intOf(1).subtract(uintOf(1)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload: int.subtract(uint)");
    assertThat(intOf(Long.MAX_VALUE).subtract(intOf(-1))).isSameAs(errIntOverflow);
    assertThat(intOf(Long.MIN_VALUE).subtract(intOf(1))).isSameAs(errIntOverflow);
    assertThat(intOf(Long.MAX_VALUE - 1).subtract(intOf(-1)).equal(intOf(Long.MAX_VALUE)))
        .isSameAs(True);
    assertThat(intOf(Long.MIN_VALUE + 1).subtract(intOf(1)).equal(intOf(Long.MIN_VALUE)))
        .isSameAs(True);
  }
}
