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
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.TimestampType;
import static org.projectnessie.cel.common.types.TimestampT.ZoneIdZ;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;
import static org.projectnessie.cel.common.types.UintT.UintType;
import static org.projectnessie.cel.common.types.UintT.UintZero;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import java.time.Instant;
import org.junit.jupiter.api.Test;

public class TestInt {

  @Test
  void IntAdd() {
    assertThat(intOf(4).add(intOf(-3)).equal(intOf(1))).isSameAs(True);
    assertThat(intOf(-1).add(stringOf("-1")))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
    for (int i = 1; i <= 10; i++) {
      assertThat(intOf(Long.MAX_VALUE).add(intOf(i))).isSameAs(errIntOverflow);
      assertThat(intOf(Long.MIN_VALUE).add(intOf(-i))).isSameAs(errIntOverflow);
      assertThat(intOf(Long.MAX_VALUE - i).add(intOf(i))).isEqualTo(intOf(Long.MAX_VALUE));
      assertThat(intOf(Long.MIN_VALUE + i).add(intOf(-i))).isEqualTo(intOf(Long.MIN_VALUE));
    }
  }

  @Test
  void IntCompare() {
    IntT lt = intOf(-1300);
    IntT gt = intOf(204);
    assertThat(lt.compare(gt)).isSameAs(IntNegOne);
    assertThat(gt.compare(lt)).isSameAs(IntOne);
    assertThat(gt.compare(gt)).isSameAs(IntZero);
    assertThat(gt.compare(TypeType))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
  }

  //  @Test
  //	void IntConvertToNative_Any() {
  //		val, err := Int(math.MaxInt64).ConvertToNative(anyValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want, err := anypb.New(wrapperspb.Int64(math.MaxInt64))
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if !proto.Equal(val.(proto.Message), want) {
  //			t.Errorf("Got '%v', wanted %v", val, want)
  //		}
  //	}
  //
  //  @Test
  //	void IntConvertToNative_Error() {
  //		val, err := Int(1).ConvertToNative(jsonStructType)
  //		if err == nil {
  //			t.Errorf("Got '%v', expected error", val)
  //		}
  //	}
  //
  //  @Test
  //	void IntConvertToNative_Int32() {
  //		val, err := Int(20050).ConvertToNative(reflect.TypeOf(int32(0)))
  //		if err != nil {
  //			t.Error(err)
  //		} else if val.(int32) != 20050 {
  //			t.Errorf("Got '%v', expected 20050", val)
  //		}
  //	}
  //
  //  @Test
  //  @Test
  //	void IntConvertToNative_Int64() {
  //		// Value greater than max int32.
  //		val, err := Int(4147483648).ConvertToNative(reflect.TypeOf(int64(0)))
  //		if err != nil {
  //			t.Error(err)
  //		} else if val.(int64) != 4147483648 {
  //			t.Errorf("Got '%v', expected 4147483648", val)
  //		}
  //	}
  //
  //  @Test
  //	void IntConvertToNative_Json() {
  //		// Value can be represented accurately as a JSON number.
  //		val, err := Int(maxIntJSON).ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Error(err)
  //		} else if !proto.Equal(val.(proto.Message),
  //			structpb.NewNumberValue(9007199254740991.0)) {
  //			t.Errorf("Got '%v', expected a json number for a 32-bit int", val)
  //		}
  //
  //		// Value converts to a JSON decimal string.
  //		val, err = Int(maxIntJSON + 1).ConvertToNative(jsonValueType)
  //		if err != nil {
  //			t.Error(err)
  //		} else if !proto.Equal(val.(proto.Message), structpb.NewStringValue("9007199254740992")) {
  //			t.Errorf("Got '%v', expected a json string for a 64-bit int", val)
  //		}
  //	}
  //
  //  @Test
  //	void IntConvertToNative_Ptr_Int32() {
  //		ptrType := int32(0)
  //		val, err := Int(20050).ConvertToNative(reflect.TypeOf(&ptrType))
  //		if err != nil {
  //			t.Error(err)
  //		} else if *val.(*int32) != 20050 {
  //			t.Errorf("Got '%v', expected 20050", val)
  //		}
  //	}
  //
  //  @Test
  //	void IntConvertToNative_Ptr_Int64() {
  //		// Value greater than max int32.
  //		ptrType := int64(0)
  //		val, err := Int(math.MaxInt32 + 1).ConvertToNative(reflect.TypeOf(&ptrType))
  //		if err != nil {
  //			t.Error(err)
  //		} else if *val.(*int64) != math.MaxInt32+1 {
  //			t.Errorf("Got '%v', expected MaxInt32 + 1", val)
  //		}
  //	}
  //
  //  @Test
  //	void IntConvertToNative_Wrapper() {
  //		val, err := Int(math.MinInt32).ConvertToNative(int32WrapperType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want := wrapperspb.Int32(math.MinInt32)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if !proto.Equal(val.(proto.Message), want) {
  //			t.Errorf("Got '%v', wanted %v", val, want)
  //		}
  //
  //		val, err = Int(math.MinInt64).ConvertToNative(int64WrapperType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want2 := wrapperspb.Int64(math.MinInt64)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if !proto.Equal(val.(proto.Message), want2) {
  //			t.Errorf("Got '%v', wanted %v", val, want2)
  //		}
  //	}

  @Test
  void IntConvertToType() {
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
  void IntDivide() {
    assertThat(intOf(3).divide(intOf(2)).equal(intOf(1))).isSameAs(True);
    assertThat(IntZero.divide(IntZero))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("divide by zero");
    assertThat(intOf(1).divide(doubleOf(-1)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
    assertThat(intOf(Long.MIN_VALUE).divide(intOf(-1))).isSameAs(errIntOverflow);
  }

  @Test
  void IntEqual() {
    assertThat(intOf(0).equal(False))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
  }

  @Test
  void IntModulo() {
    assertThat(intOf(21).modulo(intOf(2)).equal(intOf(1))).isSameAs(True);
    assertThat(intOf(21).modulo(IntZero))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("modulus by zero");
    assertThat(intOf(21).modulo(UintZero))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
    assertThat(intOf(Long.MIN_VALUE).modulo(intOf(-1))).isSameAs(errIntOverflow);
  }

  @Test
  void IntMultiply() {
    assertThat(intOf(2).multiply(intOf(-2)).equal(intOf(-4))).isSameAs(True);
    assertThat(intOf(1).multiply(doubleOf(-4.0)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
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
  void IntNegate() {
    assertThat(intOf(1).negate().equal(intOf(-1))).isSameAs(True);
    assertThat(intOf(Long.MIN_VALUE).negate()).isSameAs(errIntOverflow);
    assertThat(intOf(Long.MAX_VALUE).negate().equal(intOf(Long.MIN_VALUE + 1))).isSameAs(True);
  }

  @Test
  void IntSubtract() {
    assertThat(intOf(4).subtract(intOf(-3)).equal(intOf(7))).isSameAs(True);
    assertThat(intOf(1).subtract(uintOf(1)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
    assertThat(intOf(Long.MAX_VALUE).subtract(intOf(-1))).isSameAs(errIntOverflow);
    assertThat(intOf(Long.MIN_VALUE).subtract(intOf(1))).isSameAs(errIntOverflow);
    assertThat(intOf(Long.MAX_VALUE - 1).subtract(intOf(-1)).equal(intOf(Long.MAX_VALUE)))
        .isSameAs(True);
    assertThat(intOf(Long.MIN_VALUE + 1).subtract(intOf(1)).equal(intOf(Long.MIN_VALUE)))
        .isSameAs(True);
  }
}
