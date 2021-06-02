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
import static org.projectnessie.cel.common.types.BoolT.BoolType;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.BytesT.BytesType;
import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.DoubleT.DoubleType;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.DurationT.DurationType;
import static org.projectnessie.cel.common.types.DurationT.durationOf;
import static org.projectnessie.cel.common.types.IntT.IntNegOne;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.MapT.MapType;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.TimestampType;
import static org.projectnessie.cel.common.types.TimestampT.ZoneIdZ;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;
import static org.projectnessie.cel.common.types.UintT.UintType;
import static org.projectnessie.cel.common.types.UintT.uintOf;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.ref.Val;

public class TestString {

  @Test
  void String_Add() {
    assertThat(stringOf("hello").add(stringOf(" world"))).isEqualTo(stringOf("hello world"));
    assertThat(stringOf("hello").add(stringOf(" world")).equal(stringOf("hello world")))
        .isEqualTo(True);

    assertThat(stringOf("goodbye").add(IntT.intOf(1)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
  }

  @Test
  void String_Compare() {
    StringT a = stringOf("a");
    StringT a2 = stringOf("a");
    StringT b = stringOf("bbbb");
    StringT c = stringOf("c");
    assertThat(a.compare(b)).isSameAs(IntNegOne);
    assertThat(a.compare(a)).isSameAs(IntZero);
    assertThat(a.compare(a2)).isSameAs(IntZero);
    assertThat(c.compare(b)).isSameAs(IntOne);
    assertThat(a.compare(True))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
  }

  //  @Test
  //	void String_ConvertToNative_Any() {
  //		val, err := String("hello").ConvertToNative(anyValueType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want, err := anypb.New(wrapperspb.String("hello"))
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		if !proto.Equal(val.(proto.Message), want) {
  //			t.Errorf("Got '%v', expected '%v'", val, want)
  //		}
  //	}
  //
  //  @Test
  //	void String_ConvertToNative_Error() {
  //		val, err := String("hello").ConvertToNative(reflect.TypeOf(0))
  //		if err == nil {
  //			t.Errorf("Got '%v', expected error", val)
  //		}
  //	}
  //
  //  @Test
  //	void String_ConvertToNative_Json() {
  //		val, err := String("hello").ConvertToNative(jsonValueType)
  //		pbVal := structpb.NewStringValue("hello")
  //		if err != nil {
  //			t.Error(err)
  //		} else if !proto.Equal(val.(proto.Message), pbVal) {
  //			t.Errorf("Got '%v', expected json Value type", val)
  //		}
  //	}
  //
  //  @Test
  //	void String_ConvertToNative_Ptr() {
  //		ptrType := ""
  //		val, err := String("hello").ConvertToNative(reflect.TypeOf(&ptrType))
  //		if err != nil {
  //			t.Error(err)
  //		} else if *val.(*string) != "hello" {
  //			t.Errorf("Got '%v', expected 'hello'", val)
  //		}
  //	}

  @Test
  void String_ConvertToNative_String() {
    assertThat(stringOf("hello").convertToNative(String.class))
        .isInstanceOf(String.class)
        .isEqualTo("hello");
  }

  //  @Test
  //	void String_ConvertToNative_Wrapper() {
  //		val, err := String("hello").ConvertToNative(stringWrapperType)
  //		if err != nil {
  //			t.Error(err)
  //		}
  //		want := wrapperspb.String("hello")
  //		if !proto.Equal(val.(proto.Message), want) {
  //			t.Errorf("Got '%v', expected '%v'", val, want)
  //		}
  //	}

  @Test
  void String_ConvertToType() {
    assertThat(stringOf("-1").convertToType(IntType).equal(IntNegOne)).isSameAs(True);
    assertThat(stringOf("false").convertToType(BoolType).equal(False)).isSameAs(True);
    assertThat(stringOf("1").convertToType(UintType).equal(uintOf(1))).isSameAs(True);
    assertThat(stringOf("2.5").convertToType(DoubleType).equal(doubleOf(2.5))).isSameAs(True);
    assertThat(
            stringOf("hello")
                .convertToType(BytesType)
                .equal(bytesOf("hello".getBytes(StandardCharsets.UTF_8))))
        .isSameAs(True);
    assertThat(stringOf("goodbye").convertToType(TypeType).equal(StringType)).isSameAs(True);
    StringT gb = stringOf("goodbye");
    assertThat(gb.convertToType(StringType)).isSameAs(gb);
    assertThat(stringOf("goodbye").convertToType(StringType).equal(stringOf("goodbye")))
        .isSameAs(True);
    assertThat(
            stringOf("2017-01-01T00:00:00Z")
                .convertToType(TimestampType)
                .equal(timestampOf(Instant.ofEpochSecond(1483228800).atZone(ZoneIdZ))))
        .isSameAs(True);
    assertThat(
            stringOf("1h5s")
                .convertToType(DurationType)
                .equal(durationOf(Duration.ofSeconds(3605))))
        .isSameAs(True);
    assertThat(stringOf("map{}").convertToType(MapType))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("type conversion error from 'string' to 'map'");
  }

  @Test
  void String_Equal() {
    assertThat(stringOf("hello").equal(stringOf("hello"))).isSameAs(True);
    assertThat(stringOf("hello").equal(stringOf("hell"))).isSameAs(False);
    assertThat(stringOf("c").equal(intOf(99)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
  }

  @Test
  void String_Match() {
    StringT str = stringOf("hello 1 world");
    assertThat(str.match(stringOf("^hello"))).isSameAs(True);
    assertThat(str.match(stringOf("llo 1 w"))).isSameAs(True);
    assertThat(str.match(stringOf("llo w"))).isSameAs(False);
    assertThat(str.match(stringOf("\\d world$"))).isSameAs(True);
    assertThat(str.match(stringOf("ello 1 worlds"))).isSameAs(False);
    assertThat(str.match(intOf(1)))
        .isInstanceOf(Err.class)
        .extracting(Object::toString)
        .isEqualTo("no such overload");
  }

  @Test
  void String_Contains() {
    Val y =
        stringOf("goodbye").receive(Overloads.Contains, Overloads.ContainsString, stringOf("db"));
    assertThat(y).isSameAs(True);

    Val n =
        stringOf("goodbye")
            .receive(Overloads.Contains, Overloads.ContainsString, stringOf("ggood"));
    assertThat(n).isSameAs(False);
  }

  @Test
  void String_EndsWith() {
    Val y =
        stringOf("goodbye").receive(Overloads.EndsWith, Overloads.EndsWithString, stringOf("bye"));
    assertThat(y).isSameAs(True);

    Val n =
        stringOf("goodbye").receive(Overloads.EndsWith, Overloads.EndsWithString, stringOf("good"));
    assertThat(n).isSameAs(False);
  }

  @Test
  void String_StartsWith() {
    Val y =
        stringOf("goodbye")
            .receive(Overloads.StartsWith, Overloads.StartsWithString, stringOf("good"));
    assertThat(y).isSameAs(True);

    Val n =
        stringOf("goodbye")
            .receive(Overloads.StartsWith, Overloads.StartsWithString, stringOf("db"));
    assertThat(n).isSameAs(False);
  }

  @Test
  void String_Size() {
    assertThat(stringOf("").size()).isSameAs(IntZero);
    assertThat(stringOf("hello world").size()).isEqualTo(intOf(11));
    assertThat(stringOf("\u65e5\u672c\u8a9e").size()).isEqualTo(intOf(3));
  }
}
