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
import static org.projectnessie.cel.common.types.NullT.NullType;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;

import com.google.protobuf.Any;
import com.google.protobuf.NullValue;
import com.google.protobuf.Value;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class TestNull {

  @Test
  @Disabled("IMPLEMENT ME")
  void nullConvertToNative_Json() {
    //    NullValue expected = NullValue.NULL_VALUE;
    //
    //    		// Json Value
    //    		Object val = NullT.NullValue.convertToNative(jsonValueType);
    //    		assertThat(expected).isEqualTo(val);
  }

  @Test
  void nullConvertToNative() throws Exception {
    Value expected = Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();

    // google.protobuf.Any
    Any val = NullT.NullValue.convertToNative(Any.class);

    Value data = val.unpack(Value.class);
    assertThat(expected).isEqualTo(data);

    // NullValue
    NullValue val2 = NullT.NullValue.convertToNative(NullValue.class);
    assertThat(val2).isEqualTo(NullValue.NULL_VALUE);
  }

  @Test
  void nullConvertToType() {
    assertThat(NullT.NullValue.convertToType(NullType).equal(NullT.NullValue)).isSameAs(True);

    assertThat(NullT.NullValue.convertToType(StringType).equal(stringOf("null"))).isSameAs(True);
    assertThat(NullT.NullValue.convertToType(TypeType).equal(NullType)).isSameAs(True);
  }

  @Test
  void nullEqual() {
    assertThat(NullT.NullValue.equal(NullT.NullValue)).isSameAs(True);
  }

  @Test
  void nullType() {
    assertThat(NullT.NullValue.type()).isSameAs(NullType);
  }

  @Test
  void nullValue() {
    assertThat(NullT.NullValue.value()).isSameAs(NullValue.NULL_VALUE);
  }
}
