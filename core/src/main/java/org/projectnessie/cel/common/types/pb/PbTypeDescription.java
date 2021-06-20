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

import static org.projectnessie.cel.common.types.Err.anyWithEmptyType;
import static org.projectnessie.cel.common.types.pb.FieldDescription.newFieldDescription;

import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.TimestampT;
import org.projectnessie.cel.common.types.ref.TypeDescription;

/**
 * TypeDescription is a collection of type metadata relevant to expression checking and evaluation.
 */
public final class PbTypeDescription extends Description implements TypeDescription {

  private final String typeName;
  private final Descriptor desc;
  private final Map<String, FieldDescription> fieldMap;
  private Class<?> reflectType;
  private Message zeroMsg;

  private PbTypeDescription(
      String typeName,
      Descriptor desc,
      Map<String, FieldDescription> fieldMap,
      Class<?> reflectType,
      Message zeroMsg) {
    this.typeName = typeName;
    this.desc = desc;
    this.fieldMap = fieldMap;
    this.reflectType = reflectType;
    this.zeroMsg = zeroMsg;
  }

  void updateReflectType(Message zeroMsg) {
    this.zeroMsg = zeroMsg;
    this.reflectType = zeroMsg.getClass();
  }

  /**
   * NewTypeDescription produces a TypeDescription value for the fully-qualified proto type name
   * with a given descriptor.
   */
  public static PbTypeDescription newTypeDescription(String typeName, Descriptor desc) {
    DynamicMessage msgZero = DynamicMessage.getDefaultInstance(desc);
    Map<String, FieldDescription> fieldMap = new HashMap<>();
    List<FieldDescriptor> fields = desc.getFields();
    for (FieldDescriptor f : fields) {
      fieldMap.put(f.getName(), newFieldDescription(f));
    }
    return new PbTypeDescription(
        typeName, desc, fieldMap, reflectTypeOf(msgZero), zeroValueOf(msgZero));
  }

  /** FieldMap returns a string field name to FieldDescription map. */
  public Map<String, FieldDescription> fieldMap() {
    return fieldMap;
  }

  /** FieldByName returns (FieldDescription, true) if the field name is declared within the type. */
  public FieldDescription fieldByName(String name) {
    return fieldMap.get(name);
  }

  /**
   * MaybeUnwrap accepts a proto message as input and unwraps it to a primitive CEL type if
   * possible.
   *
   * <p>This method returns the unwrapped value and 'true', else the original value and 'false'.
   */
  public Object maybeUnwrap(Db db, Object m) {
    Message msg = (Message) m;
    try {
      if (this.reflectType == Any.class) {
        String realTypeUrl;
        ByteString realValue;
        if (msg instanceof DynamicMessage) {
          DynamicMessage dyn = (DynamicMessage) msg;
          Descriptor dynDesc = dyn.getDescriptorForType();
          FieldDescriptor fTypeUrl = dynDesc.findFieldByName("type_url");
          FieldDescriptor fValue = dynDesc.findFieldByName("value");
          realTypeUrl = (String) dyn.getField(fTypeUrl);
          realValue = (ByteString) dyn.getField(fValue);
        } else if (msg instanceof Any) {
          Any any = (Any) msg;
          realTypeUrl = any.getTypeUrl();
          realValue = any.getValue();
        } else {
          return anyWithEmptyType();
        }
        String realTypeName = typeNameFromUrl(realTypeUrl);
        if (realTypeName.isEmpty() || realTypeName.equals(typeName)) {
          return anyWithEmptyType();
        }
        PbTypeDescription realTypeDescriptor = db.describeType(realTypeName);
        Message realMsg = realTypeDescriptor.zeroMsg.getParserForType().parseFrom(realValue);
        return realTypeDescriptor.maybeUnwrap(db, realMsg);
      }

      if (!(zeroMsg instanceof DynamicMessage)) {
        if (msg instanceof Any) {
          Any any = (Any) msg;
          msg = zeroMsg.getParserForType().parseFrom(any.getValue());
        } else if (msg instanceof DynamicMessage) {
          DynamicMessage dyn = (DynamicMessage) msg;
          msg = zeroMsg.getParserForType().parseFrom(dyn.toByteString());
        }
      }
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
    return unwrap(db, this, msg);
  }

  /** Name returns the fully-qualified name of the type. */
  @Override
  public String name() {
    return desc.getFullName();
  }

  /** New returns a mutable proto message */
  public Message.Builder newMessageBuilder() {
    return zeroMsg.newBuilderForType();
  }

  public Descriptor getDescriptor() {
    return desc;
  }

  /** ReflectType returns the Golang reflect.Type for this type. */
  @Override
  public Class<?> reflectType() {
    return reflectType;
  }

  /** Zero returns the zero proto.Message value for this type. */
  @Override
  public Message zero() {
    return zeroMsg;
  }

  @Override
  public String toString() {
    return "PbTypeDescription{name: '"
        + typeName
        + '\''
        + ", fieldMap: "
        + fieldMap
        + ", reflectType: "
        + reflectType
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PbTypeDescription that = (PbTypeDescription) o;
    return Objects.equals(typeName, that.typeName) && Objects.equals(desc, that.desc);
  }

  @Override
  public int hashCode() {
    return Objects.hash(typeName, desc);
  }

  private static final Map<Class<?>, Function<Message, Object>> MessageToObjectExact =
      new IdentityHashMap<>();

  static {
    MessageToObjectExact.put(BoolValue.class, msg -> ((BoolValue) msg).getValue());
    MessageToObjectExact.put(BytesValue.class, msg -> ((BytesValue) msg).getValue());
    MessageToObjectExact.put(DoubleValue.class, msg -> ((DoubleValue) msg).getValue());
    MessageToObjectExact.put(FloatValue.class, msg -> ((FloatValue) msg).getValue());
    MessageToObjectExact.put(Int32Value.class, msg -> ((Int32Value) msg).getValue());
    MessageToObjectExact.put(Int64Value.class, msg -> ((Int64Value) msg).getValue());
    MessageToObjectExact.put(StringValue.class, msg -> ((StringValue) msg).getValue());
    MessageToObjectExact.put(
        UInt32Value.class, msg -> ULong.valueOf(((UInt32Value) msg).getValue()));
    MessageToObjectExact.put(
        UInt64Value.class, msg -> ULong.valueOf(((UInt64Value) msg).getValue()));
    MessageToObjectExact.put(Duration.class, msg -> asJavaDuration((Duration) msg));
    MessageToObjectExact.put(Timestamp.class, msg -> asJavaTimestamp((Timestamp) msg));
  }

  /**
   * unwrap unwraps the provided proto.Message value, potentially based on the description if the
   * input message is a *dynamicpb.Message which obscures the typing information from Go.
   *
   * <p>Returns the unwrapped value and 'true' if unwrapped, otherwise the input value and 'false'.
   */
  static Object unwrap(Db db, Description desc, Message msg) {
    Function<Message, Object> conv = MessageToObjectExact.get(msg.getClass());
    if (conv != null) {
      return conv.apply(msg);
    }

    if (msg instanceof Any) {
      Any v = (Any) msg;
      DynamicMessage dyn = DynamicMessage.newBuilder(v).build();
      return unwrapDynamic(db, desc, dyn);
    }
    if (msg instanceof DynamicMessage) {
      return unwrapDynamic(db, desc, msg);
    }
    if (msg instanceof Value) {
      Value v = (Value) msg;
      switch (v.getKindCase()) {
        case BOOL_VALUE:
          return v.getBoolValue();
        case LIST_VALUE:
          return v.getListValue();
        case NULL_VALUE:
          return v.getNullValue();
        case NUMBER_VALUE:
          return v.getNumberValue();
        case STRING_VALUE:
          return v.getStringValue();
        case STRUCT_VALUE:
          return v.getStructValue();
        default:
          return NullValue.NULL_VALUE;
      }
    }

    return msg;
  }

  private static java.time.Duration asJavaDuration(Duration d) {
    return java.time.Duration.ofSeconds(d.getSeconds(), d.getNanos());
  }

  private static ZonedDateTime asJavaTimestamp(Timestamp t) {
    return ZonedDateTime.of(
        LocalDateTime.ofEpochSecond(t.getSeconds(), t.getNanos(), ZoneOffset.UTC),
        TimestampT.ZoneIdZ);
  }

  /**
   * unwrapDynamic unwraps a reflected protobuf Message value.
   *
   * <p>Returns the unwrapped value and 'true' if unwrapped, otherwise the input value and 'false'.
   */
  static Object unwrapDynamic(Db db, Description desc, Message refMsg) {
    Message msg = refMsg;
    if (!msg.isInitialized()) {
      msg = desc.zero();
    }
    // In order to ensure that these wrapped types match the expectations of the CEL type system
    // the dynamicpb.Message must be merged with an protobuf instance of the well-known type
    // value.
    String typeName = refMsg.getDescriptorForType().getFullName();
    switch (typeName) {
      case "google.protobuf.Any":
        return unwrapDynamicAny(db, desc, refMsg);
      case "google.protobuf.BoolValue":
      case "google.protobuf.BytesValue":
      case "google.protobuf.DoubleValue":
      case "google.protobuf.FloatValue":
      case "google.protobuf.Int32Value":
      case "google.protobuf.Int64Value":
      case "google.protobuf.StringValue":
        // The msg value is ignored when dealing with wrapper types as they have a null or value
        // behavior, rather than the standard zero value behavior of other proto message types.
        if (msg == msg.getDefaultInstanceForType()) {
          return NullValue.NULL_VALUE;
        }
        FieldDescriptor valueField = msg.getDescriptorForType().findFieldByName("value");
        return msg.getField(valueField);
      case "google.protobuf.UInt32Value":
      case "google.protobuf.UInt64Value":
        // The msg value is ignored when dealing with wrapper types as they have a null or value
        // behavior, rather than the standard zero value behavior of other proto message types.
        if (msg == msg.getDefaultInstanceForType()) {
          return NullValue.NULL_VALUE;
        }
        valueField = msg.getDescriptorForType().findFieldByName("value");
        Number value = (Number) msg.getField(valueField);
        return ULong.valueOf(value.longValue());
      case "google.protobuf.Duration":
        return asJavaDuration(Duration.newBuilder().mergeFrom(msg).build());
      case "google.protobuf.ListValue":
        return ListValue.newBuilder().mergeFrom(msg).build();
      case "google.protobuf.NullValue":
        return NullValue.NULL_VALUE;
      case "google.protobuf.Struct":
        return Struct.newBuilder().mergeFrom(msg).build();
      case "google.protobuf.Timestamp":
        return asJavaTimestamp(Timestamp.newBuilder().mergeFrom(msg).build());
      case "google.protobuf.Value":
        Value.Builder unwrapped = Value.newBuilder();
        unwrapped.mergeFrom(msg);
        return unwrap(db, desc, Value.newBuilder().mergeFrom(msg).build());
    }
    return msg;
  }

  @SuppressWarnings("unchecked")
  private static Object unwrapDynamicAny(Db db, Description desc, Message refMsg) {
    // Note, Any values require further unwrapping; however, this unwrapping may or may not
    // be to a well-known type. If the unwrapped value is a well-known type it will be further
    // unwrapped before being returned to the caller. Otherwise, the dynamic protobuf object
    // represented by the Any will be returned.
    DynamicMessage dyn = (DynamicMessage) refMsg;
    Any any = Any.newBuilder().mergeFrom(dyn).build();
    String typeUrl = any.getTypeUrl();
    if (typeUrl.isEmpty()) {
      return anyWithEmptyType();
    }
    String innerTypeName = typeNameFromUrl(typeUrl);
    PbTypeDescription innerType = db.describeType(innerTypeName);
    if (innerType == null) {
      return refMsg;
    }
    try {
      Class<? extends Message> msgClass = (Class<? extends Message>) innerType.reflectType();
      Message unwrapped = any.unpack(msgClass);
      return unwrapDynamic(db, desc, unwrapped);
    } catch (Exception e) {
      return refMsg;
    }
  }

  public static String typeNameFromMessage(Message message) {
    if (message instanceof DynamicMessage) {
      DynamicMessage dyn = (DynamicMessage) message;
      Descriptor dynDesc = dyn.getDescriptorForType();
      if (dynDesc.getFullName().equals("google.protobuf.Any")) {
        FieldDescriptor f = dynDesc.findFieldByName("type_url");
        String typeUrl = (String) dyn.getField(f);
        return typeNameFromUrl(typeUrl);
      }
    } else if (message instanceof Any) {
      Any any = (Any) message;
      String typeUrl = any.getTypeUrl();
      return typeNameFromUrl(typeUrl);
    }
    return message.getDescriptorForType().getFullName();
  }

  public static String typeNameFromUrl(String typeUrl) {
    return typeUrl.substring(typeUrl.indexOf('/') + 1);
  }

  /**
   * reflectTypeOf intercepts the reflect.Type call to ensure that dynamicpb.Message types preserve
   * well-known protobuf reflected types expected by the CEL type system.
   */
  static Class<?> reflectTypeOf(Object val) {
    if (val instanceof Message) {
      val = zeroValueOf((Message) val);
    }
    return val.getClass();
  }

  /**
   * zeroValueOf will return the strongest possible proto.Message representing the default protobuf
   * message value of the input msg type.
   */
  static Message zeroValueOf(Message msg) {
    if (msg == null) {
      return null;
    }
    String typeName = msg.getDescriptorForType().getFullName();
    return zeroValueMap.getOrDefault(typeName, msg);
  }

  private static final Map<String, Message> zeroValueMap = new HashMap<>();

  static {
    zeroValueMap.put("google.protobuf.Any", Any.getDefaultInstance());
    zeroValueMap.put("google.protobuf.Duration", Duration.getDefaultInstance());
    zeroValueMap.put("google.protobuf.ListValue", ListValue.getDefaultInstance());
    zeroValueMap.put("google.protobuf.Struct", Struct.getDefaultInstance());
    zeroValueMap.put("google.protobuf.Value", Value.getDefaultInstance());
    zeroValueMap.put("google.protobuf.Timestamp", Timestamp.getDefaultInstance());
    zeroValueMap.put("google.protobuf.BoolValue", BoolValue.getDefaultInstance());
    zeroValueMap.put("google.protobuf.BytesValue", BytesValue.getDefaultInstance());
    zeroValueMap.put("google.protobuf.DoubleValue", DoubleValue.getDefaultInstance());
    zeroValueMap.put("google.protobuf.FloatValue", FloatValue.getDefaultInstance());
    zeroValueMap.put("google.protobuf.Int32Value", Int32Value.getDefaultInstance());
    zeroValueMap.put("google.protobuf.Int64Value", Int64Value.getDefaultInstance());
    zeroValueMap.put("google.protobuf.StringValue", StringValue.getDefaultInstance());
    zeroValueMap.put("google.protobuf.UInt32Value", UInt32Value.getDefaultInstance());
    zeroValueMap.put("google.protobuf.UInt64Value", UInt64Value.getDefaultInstance());
  }
}
