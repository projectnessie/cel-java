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

import static org.projectnessie.cel.common.types.pb.FieldDescription.newFieldDescription;

import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
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
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.projectnessie.cel.common.types.TimestampT;

/**
 * TypeDescription is a collection of type metadata relevant to expression checking and evaluation.
 */
public class TypeDescription extends Description {

  private final String typeName;
  private final Descriptor desc;
  // TODO private final protoreflect.MessageType msgType;
  private final Map<String, FieldDescription> fieldMap;
  private Class<?> reflectType;
  private final Message zeroMsg;

  private TypeDescription(
      String typeName,
      Descriptor desc,
      //	TODO protoreflect.MessageType msgType,
      Map<String, FieldDescription> fieldMap,
      Class<?> reflectType,
      Message zeroMsg) {
    this.typeName = typeName;
    this.desc = desc;
    // TODO this.msgType = msgType;
    this.fieldMap = fieldMap;
    this.reflectType = reflectType;
    this.zeroMsg = zeroMsg;
  }

  void updateReflectType(Class<? extends Message> reflectType) {
    this.reflectType = reflectType;
  }

  /**
   * NewTypeDescription produces a TypeDescription value for the fully-qualified proto type name
   * with a given descriptor.
   */
  public static TypeDescription newTypeDescription(String typeName, Descriptor desc) {
    // TODO msgType = dynamicpb.NewMessageType(desc);
    DynamicMessage msgZero = DynamicMessage.getDefaultInstance(desc);
    Map<String, FieldDescription> fieldMap = new HashMap<>();
    List<FieldDescriptor> fields = desc.getFields();
    for (FieldDescriptor f : fields) {
      fieldMap.put(f.getName(), newFieldDescription(f));
    }
    return new TypeDescription(
        typeName,
        desc,
        // TODO msgType,
        fieldMap,
        reflectTypeOf(msgZero),
        zeroValueOf(msgZero));
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
  public Object maybeUnwrap(Db db, Message msg) {
    return unwrap(db, this, msg);
  }

  /** Name returns the fully-qualified name of the type. */
  public String name() {
    return desc.getFullName();
  }

  /** New returns a mutable proto message */
  // TODO ???
  //  public protoreflect.Message newReflect() {
  //    return msgType.New()
  //  }

  /** New returns a mutable proto message */
  public Message.Builder newMessageBuilder() {
    return DynamicMessage.newBuilder(desc);
  }

  public Descriptor getDescriptor() {
    return desc;
  }

  /** ReflectType returns the Golang reflect.Type for this type. */
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
    return "TypeDescription{name: '"
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
    TypeDescription that = (TypeDescription) o;
    return Objects.equals(typeName, that.typeName) && Objects.equals(desc, that.desc);
  }

  @Override
  public int hashCode() {
    return Objects.hash(typeName, desc);
  }

  /**
   * unwrap unwraps the provided proto.Message value, potentially based on the description if the
   * input message is a *dynamicpb.Message which obscures the typing information from Go.
   *
   * <p>Returns the unwrapped value and 'true' if unwrapped, otherwise the input value and 'false'.
   */
  static Object unwrap(Db db, Description desc, Message msg) {
    if (msg instanceof Any) {
      Any v = (Any) msg;
      DynamicMessage dyn = DynamicMessage.newBuilder(v).build();
      return unwrapDynamic(db, desc, dyn);
    }
    if (msg instanceof DynamicMessage) {
      return unwrapDynamic(db, desc, msg);
    }
    if (msg instanceof Duration) {
      Duration d = (Duration) msg;
      return asJavaDuration(d);
    }
    if (msg instanceof Timestamp) {
      Timestamp t = (Timestamp) msg;
      return asJavaTimestamp(t);
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
    if (msg instanceof BoolValue) {
      return ((BoolValue) msg).getValue();
    }
    if (msg instanceof BytesValue) {
      return ((BytesValue) msg).getValue();
    }
    if (msg instanceof DoubleValue) {
      return ((DoubleValue) msg).getValue();
    }
    if (msg instanceof FloatValue) {
      return ((FloatValue) msg).getValue();
    }
    if (msg instanceof Int32Value) {
      return ((Int32Value) msg).getValue();
    }
    if (msg instanceof Int64Value) {
      return ((Int64Value) msg).getValue();
    }
    if (msg instanceof StringValue) {
      return ((StringValue) msg).getValue();
    }
    if (msg instanceof UInt32Value) {
      return ((UInt32Value) msg).getValue();
    }
    if (msg instanceof UInt64Value) {
      return ((UInt64Value) msg).getValue();
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
        {
          // Note, Any values require further unwrapping; however, this unwrapping may or may not
          // be to a well-known type. If the unwrapped value is a well-known type it will be further
          // unwrapped before being returned to the caller. Otherwise, the dynamic protobuf object
          // represented by the Any will be returned.
          Any.Builder unwrappedAny = Any.newBuilder();
          unwrappedAny.mergeFrom(msg);
          Any dynMsg = unwrappedAny.build();
          // TODO this is the original code of the above
          //      dynMsg, err := unwrappedAny.UnmarshalNew()
          //      if err != nil {
          //        // Allow the error to move further up the stack as it should result in an type
          //        // conversion error if the caller does not recover it somehow.
          //        return unwrappedAny;
          //      }
          // Attempt to unwrap the dynamic type, otherwise return the dynamic message.
          // TODO this would recurse endlessly (stack-overflow):
          //    Object unwrapped = unwrapDynamic(desc, dynMsg);
          try {
            String innerTypeName = typeNameFromUrl(dynMsg.getTypeUrl());
            TypeDescription innerType = db.describeType(innerTypeName);
            if (innerType == null) {
              return dynMsg; // throw new RuntimeException(String.format("unknown type '%s'",
              // innerTypeName));
            }
            Class<? extends Message> msgClass = (Class<? extends Message>) innerType.reflectType();
            Message unwrapped = dynMsg.unpack(msgClass);
            return unwrapDynamic(db, desc, unwrapped);
          } catch (Exception e) {
            throw new RuntimeException(
                String.format(
                    "Failed to unpack type '%s' from '%s': %s", dynMsg.getTypeUrl(), typeName, e),
                e);
          }
          // TODO this is the original code of the above
          //      Object unwrapped = unwrapDynamic(desc, dynMsg);
          //      if (unwrapped != null) {
          //        return unwrapped;
          //      }
        }
      case "google.protobuf.BoolValue":
      case "google.protobuf.BytesValue":
      case "google.protobuf.DoubleValue":
      case "google.protobuf.FloatValue":
      case "google.protobuf.Int32Value":
      case "google.protobuf.Int64Value":
      case "google.protobuf.StringValue":
      case "google.protobuf.UInt32Value":
      case "google.protobuf.UInt64Value":
        // The msg value is ignored when dealing with wrapper types as they have a null or value
        // behavior, rather than the standard zero value behavior of other proto message types.
        if (!msg.isInitialized()) {
          return NullValue.NULL_VALUE;
        }
        FieldDescriptor valueField = msg.getDescriptorForType().findFieldByName("value");
        return msg.getField(valueField);
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
    // TODO verify these are correct
    zeroValueMap.put("google.protobuf.Any", Any.newBuilder().build());
    zeroValueMap.put("google.protobuf.Duration", Duration.newBuilder().build());
    zeroValueMap.put("google.protobuf.ListValue", ListValue.newBuilder().build());
    zeroValueMap.put("google.protobuf.Struct", Struct.newBuilder().build());
    zeroValueMap.put("google.protobuf.Value", Value.newBuilder().build());
    zeroValueMap.put("google.protobuf.Timestamp", Timestamp.newBuilder().build());
    zeroValueMap.put("google.protobuf.BoolValue", BoolValue.newBuilder().build());
    zeroValueMap.put("google.protobuf.BytesValue", BytesValue.newBuilder().build());
    zeroValueMap.put("google.protobuf.DoubleValue", DoubleValue.newBuilder().build());
    zeroValueMap.put("google.protobuf.FloatValue", FloatValue.newBuilder().build());
    zeroValueMap.put("google.protobuf.Int32Value", Int32Value.newBuilder().build());
    zeroValueMap.put("google.protobuf.Int64Value", Int64Value.newBuilder().build());
    zeroValueMap.put("google.protobuf.StringValue", StringValue.newBuilder().build());
    zeroValueMap.put("google.protobuf.UInt32Value", UInt32Value.newBuilder().build());
    zeroValueMap.put("google.protobuf.UInt64Value", UInt64Value.newBuilder().build());
  }
}
