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
package org.projectnessie.cel.common.types.pb;

import com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import java.util.function.Supplier;

/**
 * Test cases for {@link
 * TypeDescriptorTest#benchmarkTypeDescriptionMaybeUnwrap(org.projectnessie.cel.common.types.pb.UnwrapTestCase)}
 * and {@code TypeDescriptorBnch} JMH benchmark, latter requires this class to be a top-level public
 * enum.
 */
public enum UnwrapTestCase {
  msgDesc_zero(() -> UnwrapContext.get().msgDesc.zero()),
  // Not implemented in Java:
  // msgDesc_new_interface(() -> UnwrapContext.get().msgDesc.New().Interface(),
  dynamicpb_NewMessage(() -> DynamicMessage.newBuilder(ListValue.getDefaultInstance()).build()),
  structpb_NewBoolValue_true(() -> BoolValue.of(true)),
  structpb_NewBoolValue_false(() -> BoolValue.of(false)),
  structpb_NewNullValue(() -> Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()),
  structpb_Value(Value::getDefaultInstance),
  structpb_NewNumberValue(() -> Value.newBuilder().setNumberValue(1.5).build()),
  structpb_NewStringValue(() -> StringValue.of("hello world")),
  wrapperspb_Bool_false(() -> BoolValue.of(false)),
  wrapperspb_Bool_true(() -> BoolValue.of(true)),
  wrapperspb_Bytes(() -> BytesValue.of(ByteString.copyFromUtf8("hello"))),
  wrapperspb_Double(() -> DoubleValue.of(-4.2)),
  wrapperspb_Float(() -> FloatValue.of(4.5f)),
  wrapperspb_Int32(() -> Int32Value.of(123)),
  wrapperspb_Int64(() -> Int64Value.of(456)),
  wrapperspb_String(() -> StringValue.of("goodbye")),
  wrapperspb_UInt32(() -> UInt32Value.of(1234)),
  wrapperspb_UInt64(() -> UInt64Value.of(5678)),
  timestamp(() -> Timestamp.newBuilder().setSeconds(12345).setNanos(0).build()),
  duration(() -> Duration.newBuilder().setSeconds(345).build()),
  proto3pb_TestAllTypes(TestAllTypes::getDefaultInstance);

  UnwrapTestCase(Supplier<Message> message) {
    this.message = message;
  }

  private final Supplier<Message> message;

  public Message message() {
    return message.get();
  }
}
