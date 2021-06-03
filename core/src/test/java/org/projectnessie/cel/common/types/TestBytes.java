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
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.BytesT.BytesType;
import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.IntT.IntNegOne;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class TestBytes {

  @Test
  void bytesAdd() {
    assertThat(bytesOf("hello").add(bytesOf("world")).equal(bytesOf("helloworld"))).isSameAs(True);
    assertThat(bytesOf("hello").add(stringOf("world"))).matches(Err::isError);
  }

  @Test
  void bytesCompare() {
    assertThat(bytesOf("1234").compare(bytesOf("2345")).equal(IntNegOne)).isSameAs(True);
    assertThat(bytesOf("2345").compare(bytesOf("1234")).equal(IntOne)).isSameAs(True);
    assertThat(bytesOf("2345").compare(bytesOf("2345")).equal(IntZero)).isSameAs(True);
    assertThat(bytesOf("1").compare(stringOf("1"))).matches(Err::isError);
  }

  @Test
  void bytesConvertToNative_Any() {
    Any val = bytesOf("123").convertToNative(Any.class);
    Any want = Any.pack(BytesValue.of(ByteString.copyFrom("123".getBytes(StandardCharsets.UTF_8))));
    assertThat(val).isEqualTo(want);
  }

  @Test
  void bytesConvertToNative_ByteSlice() {
    byte[] val = bytesOf("123").convertToNative(byte[].class);
    assertThat(val).containsExactly(49, 50, 51);
  }

  @Test
  void bytesConvertToNative_ByteString() {
    ByteString val = bytesOf("123").convertToNative(ByteString.class);
    assertThat(val).isEqualTo(ByteString.copyFrom(new byte[] {49, 50, 51}));
  }

  @Test
  void bytesConvertToNative_ByteBuffer() {
    ByteBuffer val = bytesOf("123").convertToNative(ByteBuffer.class);
    assertThat(val).isEqualTo(ByteBuffer.wrap(new byte[] {49, 50, 51}));
  }

  @Test
  void bytesConvertToNative_Error() {
    assertThat(bytesOf("123").convertToNative(String.class)).isEqualTo("123");
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void bytesConvertToNative_Json() {
    //  		val = bytesOf("123").convertToNative(jsonValueType)
    //  		if err != nil {
    //  			t.Error(err)
    //  		}
    //  		want := structpb.NewStringValue("MTIz")
    //  		if !proto.equal(val.(proto.Message), want) {
    //  			t.Errorf("Got %v, wanted %v", val, want)
    //  		}
  }

  @Test
  void bytesConvertToNative_Wrapper() {
    byte[] val = bytesOf("123").convertToNative(byte[].class);
    byte[] want = "123".getBytes(StandardCharsets.UTF_8);
    assertThat(val).containsExactly(want);
  }

  @Test
  void bytesConvertToType() {
    assertThat(bytesOf("hello world").convertToType(BytesType).equal(bytesOf("hello world")))
        .isSameAs(True);
    assertThat(bytesOf("hello world").convertToType(StringType).equal(stringOf("hello world")))
        .isSameAs(True);
    assertThat(bytesOf("hello world").convertToType(TypeType).equal(BytesType)).isSameAs(True);
    assertThat(bytesOf("hello").convertToType(IntType)).matches(Err::isError);
  }

  @Test
  void bytesSize() {
    assertThat(bytesOf("1234567890").size().equal(intOf(10))).isSameAs(True);
  }
}
