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

import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchField;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Types.boolOf;

import com.google.protobuf.Any;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import org.projectnessie.cel.common.types.ObjectT;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

/**
 * CEL object value backed by a generated or dynamic Protocol Buffer message.
 *
 * <p>Instances are created by {@link ProtoTypeRegistry} after the message type has been registered.
 * Field selection and presence testing use protobuf presence/default semantics and return CEL error
 * values for invalid field names or operand types. The wrapped protobuf message is immutable.
 */
public final class PbObjectT extends ObjectT {

  private PbObjectT(
      TypeAdapter adapter, Message value, PbTypeDescription typeDesc, Type typeValue) {
    super(adapter, value, typeDesc, typeValue);
  }

  /**
   * Wraps a registered protobuf message as a CEL object.
   *
   * <p>Callers normally use {@link ProtoTypeRegistry#nativeToValue(Object)}; this low-level factory
   * assumes that {@code typeDesc}, {@code typeValue}, and {@code value} describe the same
   * registered protobuf type.
   *
   * @param adapter adapter used for nested field values
   * @param typeDesc protobuf message metadata
   * @param typeValue CEL runtime object type
   * @param value immutable protobuf message
   * @return the wrapped CEL object value
   */
  public static Val newObject(
      TypeAdapter adapter, PbTypeDescription typeDesc, Type typeValue, Message value) {
    return new PbObjectT(adapter, value, typeDesc, typeValue);
  }

  /**
   * Tests protobuf presence for a named field.
   *
   * @return a CEL boolean, or a CEL error for a non-string or unknown field
   */
  @Override
  public Val isSet(Val field) {
    if (!(field instanceof StringT)) {
      return noSuchOverload(this, "isSet", field);
    }
    String protoFieldStr = (String) field.value();
    FieldType fieldType = fieldType(protoFieldStr, true);
    if (fieldType != null) {
      return boolOf(fieldType.isSet.isSet(value));
    }
    FieldDescription fd = fieldDescription(protoFieldStr);
    if (fd == null) {
      return noSuchField(protoFieldStr);
    }
    return boolOf(fd.hasField(value));
  }

  /**
   * Selects and adapts a named protobuf field.
   *
   * @return the field's CEL value, or a CEL error for a non-string or unknown field
   */
  @Override
  public Val get(Val index) {
    if (!(index instanceof StringT)) {
      return noSuchOverload(this, "get", index);
    }
    String protoFieldStr = (String) index.value();
    FieldType fieldType = fieldType(protoFieldStr, false);
    if (fieldType != null) {
      return adapter.nativeToValue(fieldType.getFrom.getFrom(value));
    }
    FieldDescription fd = fieldDescription(protoFieldStr);
    if (fd == null) {
      return noSuchField(protoFieldStr);
    }
    return adapter.nativeToValue(fd.getField(value, adapter));
  }

  /**
   * Compares protobuf objects using CEL equality semantics.
   *
   * <p>Objects with different protobuf type names are unequal. A message containing a {@code NaN}
   * at any nested or repeated field is unequal, including to itself, as required by CEL numeric
   * equality.
   */
  @Override
  public Val equal(Val other) {
    if (!(other instanceof PbObjectT otherObject)) {
      return super.equal(other);
    }

    if (!typeDesc().name().equals(otherObject.typeDesc().name())) {
      return boolOf(false);
    }
    if (containsNaN(message()) || containsNaN(otherObject.message())) {
      return boolOf(false);
    }
    return boolOf(message().equals(otherObject.message()));
  }

  /**
   * Converts this value to a compatible protobuf or CEL representation.
   *
   * <p>Supported targets include the wrapped generated message class, {@link DynamicMessage},
   * {@link Any}, selected protobuf JSON {@link Value} representations, {@link Message}, {@link
   * Val}, and {@link PbObjectT}. Conversion to another generated protobuf class rebuilds the
   * message from its protobuf data.
   *
   * @throws IllegalArgumentException if the requested target is not supported
   * @throws RuntimeException if rebuilding a generated message fails
   */
  @SuppressWarnings({"removal", "unchecked"})
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc.isAssignableFrom(value.getClass())) {
      return (T) value;
    }
    if (typeDesc.isAssignableFrom(getClass())) {
      return (T) this;
    }
    if (typeDesc == DynamicMessage.class) {
      return (T)
          DynamicMessage.newBuilder(message().getDescriptorForType()).mergeFrom(message()).build();
    }
    if (typeDesc == Any.class) {
      // anyValueType
      if (value instanceof Any) {
        return (T) value;
      }
      return (T) Any.pack(message());
    }
    if (typeDesc == Value.class) {
      // jsonValueType
      if (value instanceof Empty) {
        return (T) Value.newBuilder().setStructValue(Struct.getDefaultInstance()).build();
      }
      if (value instanceof FieldMask) {
        return (T) Value.newBuilder().setStringValue(fieldMaskJsonValue((FieldMask) value)).build();
      }
      if (value instanceof Timestamp) {
        return adapter.valueToNative(adapter.nativeToValue(value), typeDesc);
      }
    }
    if (typeDesc.isAssignableFrom(this.typeDesc.reflectType()) || typeDesc == Object.class) {
      if (value instanceof Any || value instanceof DynamicMessage) {
        return buildFrom(typeDesc);
      }
      return (T) value;
    }

    if (Message.class.isAssignableFrom(typeDesc)) {
      return buildFrom(typeDesc);
    }

    if (typeDesc == Val.class || typeDesc == PbObjectT.class) {
      return (T) this;
    }

    // impossible cast
    throw new IllegalArgumentException(
        newTypeConversionError(value.getClass().getName(), typeDesc).toString());
  }

  private Message message() {
    return (Message) value;
  }

  private static boolean containsNaN(Message message) {
    for (FieldDescriptor field : message.getAllFields().keySet()) {
      Object fieldValue = message.getField(field);
      if (field.isRepeated()) {
        for (Object element : (Iterable<?>) fieldValue) {
          if (containsNaN(field, element)) {
            return true;
          }
        }
      } else if (containsNaN(field, fieldValue)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsNaN(FieldDescriptor field, Object value) {
    JavaType javaType = field.getJavaType();
    if (javaType == JavaType.DOUBLE) {
      return Double.isNaN((Double) value);
    }
    if (javaType == JavaType.FLOAT) {
      return Float.isNaN((Float) value);
    }
    return javaType == JavaType.MESSAGE && containsNaN((Message) value);
  }

  private static String fieldMaskJsonValue(FieldMask fieldMask) {
    StringBuilder value = new StringBuilder();
    for (String path : fieldMask.getPathsList()) {
      if (!value.isEmpty()) {
        value.append(',');
      }
      value.append(fieldMaskPathJsonValue(path));
    }
    return value.toString();
  }

  private static String fieldMaskPathJsonValue(String path) {
    StringBuilder value = new StringBuilder(path.length());
    boolean upperNext = false;
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if (c == '_') {
        upperNext = true;
      } else if (upperNext) {
        value.append(Character.toUpperCase(c));
        upperNext = false;
      } else {
        value.append(c);
      }
    }
    return value.toString();
  }

  private PbTypeDescription typeDesc() {
    return (PbTypeDescription) typeDesc;
  }

  private FieldDescription fieldDescription(String fieldName) {
    FieldDescription field = typeDesc().fieldByName(fieldName);
    if (field != null || !(adapter instanceof ProtoTypeRegistry)) {
      return field;
    }
    return ((ProtoTypeRegistry) adapter).findFieldDescription(typeDesc().name(), fieldName);
  }

  private FieldType fieldType(String fieldName, boolean presenceTest) {
    if (adapter instanceof ProtoTypeRegistry registry) {
      return registry.findFieldTypeForObjectAccess(typeDesc().name(), fieldName, presenceTest);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private <T> T buildFrom(Class<T> typeDesc) {
    try {
      Message.Builder builder =
          (Message.Builder) typeDesc.getDeclaredMethod("newBuilder").invoke(null);
      return (T) builder.mergeFrom(message()).build();
    } catch (Exception e) {
      throw new RuntimeException(
          String.format("%s: %s", newTypeConversionError(value.getClass().getName(), typeDesc), e));
    }
  }
}
