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

import static org.projectnessie.cel.common.types.pb.PbTypeDescription.reflectTypeOf;
import static org.projectnessie.cel.common.types.pb.PbTypeDescription.unwrapDynamic;

import com.google.api.expr.v1alpha1.Type;
import com.google.api.expr.v1alpha1.Type.ListType;
import com.google.api.expr.v1alpha1.Type.MapType;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.EnumValue;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.projectnessie.cel.common.ULong;

/** FieldDescription holds metadata related to fields declared within a type. */
public final class FieldDescription extends Description {

  /** KeyType holds the key FieldDescription for map fields. */
  final FieldDescription keyType;
  /** ValueType holds the value FieldDescription for map fields. */
  final FieldDescription valueType;

  private final FieldDescriptor desc;
  private final Class<?> reflectType;
  private final Message zeroMsg;

  /** NewFieldDescription creates a new field description from a protoreflect.FieldDescriptor. */
  public static FieldDescription newFieldDescription(FieldDescriptor fieldDesc) {
    Objects.requireNonNull(fieldDesc);
    Class<?> reflectType;
    Message zeroMsg = null;
    switch (fieldDesc.getJavaType()) {
      case ENUM:
        reflectType = Enum.class;
        break;
      case MESSAGE:
        zeroMsg = DynamicMessage.getDefaultInstance(fieldDesc.getMessageType());
        reflectType = reflectTypeOf(zeroMsg);
        break;
      default:
        reflectType = reflectTypeOfField(fieldDesc);
        if (fieldDesc.isRepeated() && !fieldDesc.isMapField()) {
          FieldDescriptor.Type t = fieldDesc.getType();
          switch (t.getJavaType()) {
            case ENUM:
              reflectType = Enum.class;
              break;
            case MESSAGE:
              reflectType =
                  fieldDesc.getMessageType().toProto().getDefaultInstanceForType().getClass();
              break;
            case BOOLEAN:
              reflectType = Boolean.class;
              break;
            case BYTE_STRING:
              reflectType = byte[].class;
              break;
            case DOUBLE:
              reflectType = Double.class;
              break;
            case FLOAT:
              reflectType = Float.class;
              break;
            case INT:
              if (t == FieldDescriptor.Type.UINT32 || t == FieldDescriptor.Type.FIXED32) {
                reflectType = ULong.class;
              } else {
                reflectType = Integer.class;
              }
              break;
            case LONG:
              if (t == FieldDescriptor.Type.UINT64 || t == FieldDescriptor.Type.FIXED64) {
                reflectType = ULong.class;
              } else {
                reflectType = Long.class;
              }
              break;
            case STRING:
              reflectType = String.class;
              break;
          }
        }
        break;
    }
    // Ensure the list type is appropriately reflected as a Go-native list.
    if (fieldDesc.isRepeated() && !fieldDesc.isMapField()) { // IsList()
      // TODO j.u.List or array???
      reflectType = Array.newInstance(reflectType, 0).getClass();
    }
    FieldDescription keyType = null;
    FieldDescription valType = null;
    if (fieldDesc.isMapField()) {
      keyType = newFieldDescription(fieldDesc.getMessageType().findFieldByNumber(1));
      valType = newFieldDescription(fieldDesc.getMessageType().findFieldByNumber(2));
    }
    return new FieldDescription(keyType, valType, fieldDesc, reflectType, zeroMsg);
  }

  private static Class<?> reflectTypeOfField(FieldDescriptor fieldDesc) {
    switch (fieldDesc.getType()) {
      case DOUBLE:
        return Double.class;
      case FLOAT:
        return Float.class;
      case STRING:
        return String.class;
      case BOOL:
        return Boolean.class;
      case BYTES:
        return ByteString.class;
      case INT32:
      case SFIXED32:
      case SINT32:
      case FIXED32:
        return Integer.class;
      case INT64:
      case SFIXED64:
      case SINT64:
        return Long.class;
      case UINT32:
      case UINT64:
      case FIXED64:
        return ULong.class;
      case ENUM:
        return Enum.class;
    }
    return reflectTypeOf(fieldDesc.getDefaultValue());
  }

  private FieldDescription(
      FieldDescription keyType,
      FieldDescription valueType,
      FieldDescriptor desc,
      Class<?> reflectType,
      Message zeroMsg) {
    this.keyType = keyType;
    this.valueType = valueType;
    this.desc = desc;
    this.reflectType = reflectType;
    this.zeroMsg = zeroMsg;
  }

  /** CheckedType returns the type-definition used at type-check time. */
  public Type checkedType() {
    if (desc.isMapField()) {
      return Type.newBuilder()
          .setMapType(
              MapType.newBuilder()
                  .setKeyType(keyType.typeDefToType())
                  .setValueType(valueType.typeDefToType()))
          .build();
    }
    if (desc.isRepeated()) { // "isListField()"
      return Type.newBuilder()
          .setListType(ListType.newBuilder().setElemType(typeDefToType()))
          .build();
    }
    return typeDefToType();
  }

  /** Descriptor returns the protoreflect.FieldDescriptor for this type. */
  public FieldDescriptor descriptor() {
    return desc;
  }

  /**
   * IsSet returns whether the field is set on the target value, per the proto presence conventions
   * of proto2 or proto3 accordingly.
   *
   * <p>This function implements the FieldType.IsSet function contract which can be used to operate
   * on more than just protobuf field accesses; however, the target here must be a protobuf.Message.
   */
  public boolean isSet(Object target) {
    if (target instanceof Message) {
      Message v = (Message) target;
      // pbRef = v.ProtoReflect()
      Descriptor pbDesc = v.getDescriptorForType();
      if (pbDesc == desc.getContainingType()) {
        // When the target protobuf shares the same message descriptor instance as the field
        // descriptor, use the cached field descriptor value.
        return FieldDescription.hasValueForField(desc, v);
      }
      // Otherwise, fallback to a dynamic lookup of the field descriptor from the target
      // instance as an attempt to use the cached field descriptor will result in a panic.
      return FieldDescription.hasValueForField(pbDesc.findFieldByName(name()), v);
    }
    return false;
  }

  /**
   * GetFrom returns the accessor method associated with the field on the proto generated struct.
   *
   * <p>If the field is not set, the proto default value is returned instead.
   *
   * <p>This function implements the FieldType.GetFrom function contract which can be used to
   * operate on more than just protobuf field accesses; however, the target here must be a
   * protobuf.Message.
   */
  public Object getFrom(Db db, Object target) {
    if (!(target instanceof Message)) {
      throw new IllegalArgumentException(
          String.format(
              "unsupported field selection target: (%s)%s", target.getClass().getName(), target));
    }
    Message v = (Message) target;
    // pbRef = v.protoReflect();
    Descriptor pbDesc = v.getDescriptorForType();
    Object fieldVal;

    FieldDescriptor fd;
    if (pbDesc == desc.getContainingType()) {
      // When the target protobuf shares the same message descriptor instance as the field
      // descriptor, use the cached field descriptor value.
      fd = desc;
    } else {
      // Otherwise, fallback to a dynamic lookup of the field descriptor from the target
      // instance as an attempt to use the cached field descriptor will result in a panic.
      fd = pbDesc.findFieldByName(name());
    }
    fieldVal = getValueFromField(fd, v);

    Class<?> fieldType = fieldVal.getClass();
    if (fd.getJavaType() != JavaType.MESSAGE
        || fieldType.isPrimitive()
        || fieldType.isEnum()
        || fieldType == byte[].class
        || fieldType == Boolean.class
        || fieldType == Byte.class
        || fieldType == Short.class
        || fieldType == Integer.class
        || fieldType == Long.class
        || fieldType == Float.class
        || fieldType == Double.class
        || fieldType == String.class) {
      // Fast-path return for primitive types.
      return fieldVal;
    }
    if (fieldType == ULong.class) {
      return ((ULong) fieldVal).longValue();
    }
    if (fieldVal instanceof EnumValue) {
      return (long) ((EnumValue) fieldVal).getNumber();
    }
    if (fieldVal instanceof Message) {
      return maybeUnwrapDynamic(db, (Message) fieldVal);
    }
    throw new UnsupportedOperationException("IMPLEMENT ME");
    // TODO implement this
    //    if (field)
    //    switch fv := fieldVal.(type) {
    //    case bool, []byte, float32, float64, int32, int64, string, uint32, uint64,
    // protoreflect.List:
    //      return fv, nil
    //    case protoreflect.Map:
    //      // Return a wrapper around the protobuf-reflected Map types which carries additional
    //      // information about the key and value definitions of the map.
    //      return &Map{Map: fv, KeyType: keyType, ValueType: valueType}, nil
    //    default:
    //      return fv, nil
    //    }
  }

  /** IsEnum returns true if the field type refers to an enum value. */
  public boolean isEnum() {
    return desc.getJavaType() == JavaType.ENUM;
  }

  /** IsMap returns true if the field is of map type. */
  public boolean isMap() {
    return desc.isMapField();
  }

  /** IsMessage returns true if the field is of message type. */
  public boolean isMessage() {
    return desc.getJavaType() == JavaType.MESSAGE;
  }

  /** IsOneof returns true if the field is declared within a oneof block. */
  public boolean isOneof() {
    return desc.getContainingOneof() != null;
  }

  /**
   * IsList returns true if the field is a repeated value.
   *
   * <p>This method will also return true for map values, so check whether the field is also a map.
   */
  public boolean isList() {
    return desc.isRepeated() && !desc.isMapField();
  }

  /**
   * MaybeUnwrapDynamic takes the reflected protoreflect.Message and determines whether the value
   * can be unwrapped to a more primitive CEL type.
   *
   * <p>This function returns the unwrapped value and 'true' on success, or the original value and
   * 'false' otherwise.
   */
  public Object maybeUnwrapDynamic(Db db, Message msg) {
    return unwrapDynamic(db, this, msg);
  }

  /** Name returns the CamelCase name of the field within the proto-based struct. */
  public String name() {
    return desc.getName();
  }

  /** ReflectType returns the Golang reflect.Type for this field. */
  public Class<?> reflectType() {
    boolean r = desc.isRepeated();
    if (r && desc.isMapField()) {
      return Map.class;
    }
    switch (desc.getJavaType()) {
      case ENUM:
      case MESSAGE:
        return reflectType;
      case BOOLEAN:
        return r ? Boolean[].class : Boolean.class;
      case BYTE_STRING:
        return r ? ByteString[].class : ByteString.class;
      case DOUBLE:
        return r ? Double[].class : Double.class;
      case FLOAT:
        return r ? Float[].class : Float.class;
      case INT:
        return r ? Integer[].class : Integer.class;
      case LONG:
        return r ? Long[].class : Long.class;
      case STRING:
        return r ? String[].class : String.class;
    }
    return reflectType;
  }

  /**
   * String returns the fully qualified name of the field within its type as well as whether the
   * field occurs within a oneof. func (fd *FieldDescription) String() string { return
   * fmt.Sprintf("%v.%s `oneof=%t`", desc.ContainingMessage().FullName(), name(), isOneof()) }
   *
   * <p>/** Zero returns the zero value for the protobuf message represented by this field.
   *
   * <p>If the field is not a proto.Message type, the zero value is nil.
   */
  @Override
  public Message zero() {
    return zeroMsg;
  }

  public Type typeDefToType() {
    switch (desc.getJavaType()) {
      case MESSAGE:
        String msgType = desc.getMessageType().getFullName();
        Type wk = Checked.CheckedWellKnowns.get(msgType);
        if (wk != null) {
          return wk;
        }
        return Checked.checkedMessageType(msgType);
      case ENUM:
        return Checked.checkedInt;
      case BOOLEAN:
        return Checked.checkedBool;
      case BYTE_STRING:
        return Checked.checkedBytes;
      case DOUBLE:
      case FLOAT:
        return Checked.checkedDouble;
      case INT:
        if (desc.getType() == FieldDescriptor.Type.UINT32
            || desc.getType() == FieldDescriptor.Type.FIXED32) {
          return Checked.checkedUint;
        }
        return Checked.checkedInt;
      case LONG:
        if (desc.getType() == FieldDescriptor.Type.UINT64
            || desc.getType() == FieldDescriptor.Type.FIXED64) {
          return Checked.checkedUint;
        }
        return Checked.checkedInt;
      case STRING:
        return Checked.checkedString;
    }
    throw new UnsupportedOperationException("Unknown JavaType " + desc.getJavaType());
  }

  @Override
  public String toString() {
    return checkedType().toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FieldDescription that = (FieldDescription) o;
    return Objects.equals(desc, that.desc) && Objects.equals(reflectType, that.reflectType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(desc, reflectType);
  }

  public boolean hasField(Object target) {
    return hasValueForField(desc, (Message) target);
  }

  public Object getField(Object target) {
    return getValueFromField(desc, (Message) target);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Object getValueFromField(FieldDescriptor desc, Message message) {

    if (isWellKnownType(desc) && !message.hasField(desc)) {
      return NullValue.NULL_VALUE;
    }

    Object v = message.getField(desc);

    if (!desc.isMapField()) {
      FieldDescriptor.Type type = desc.getType();
      if (v != null
          && (type == FieldDescriptor.Type.UINT32
              || type == FieldDescriptor.Type.UINT64
              || type == FieldDescriptor.Type.FIXED32
              || type == FieldDescriptor.Type.FIXED64)) {
        v = ULong.valueOf(((Number) v).longValue());
      }
    } else {
      // TODO protobuf-java inefficiency
      //  protobuf-java does NOT have a generic way to retrieve the underlying map, but instead
      //  getField() returns a list of com.google.protobuf.MapEntry. It's not great that we have
      //  to have this workaround here to re-build a j.u.Map.
      //  I.e. to access a single map entry we *HAVE TO* touch and re-build the whole map. This
      //  is very inefficient.
      //  There is no way to do a "message.getMapField(desc, key)" (aka a "reflective counterpart"
      //  for the generated map accessor methods like 'getXXXTypeOrThrow()'), too.
      if (v instanceof List) {
        List<MapEntry> lst = (List) v;
        Map map = new HashMap(lst.size() * 4 / 3 + 1);
        for (MapEntry e : lst) {
          map.put(e.getKey(), e.getValue());
        }
        v = map;
      }
    }
    return v;
  }

  private static boolean isWellKnownType(FieldDescriptor desc) {
    if (desc.getJavaType() != JavaType.MESSAGE) {
      return false;
    }
    Type wellKnown = Checked.CheckedWellKnowns.get(desc.getMessageType().getFullName());
    if (wellKnown == null) {
      return false;
    }
    return wellKnown.hasWrapper();
  }

  public static boolean hasValueForField(FieldDescriptor desc, Message message) {
    if (desc.isRepeated()) {
      return message.getRepeatedFieldCount(desc) > 0;
    }
    return message.hasField(desc);
  }
}
