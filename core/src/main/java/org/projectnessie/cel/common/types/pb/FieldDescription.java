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

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noMoreElements;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.TypeT.TypeType;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;
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
import java.util.AbstractList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.MapT;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeEnum;
import org.projectnessie.cel.common.types.ref.Val;

/**
 * Protocol Buffer field metadata and low-level access operations.
 *
 * <p>The description maps a protobuf {@link FieldDescriptor} to its checked CEL type, Java
 * representation, presence semantics, and generated/dynamic message access. Repeated fields become
 * CEL lists, protobuf map fields become CEL maps, unsigned integers use {@link ULong}, and
 * well-known wrapper fields use CEL null when absent.
 *
 * <p>Applications normally obtain field metadata through {@link
 * ProtoTypeRegistry#findFieldType(String, String)} rather than constructing descriptions directly.
 */
public final class FieldDescription extends Description {
  private static final Object NO_NATIVE_MAP_KEY = new Object();

  /** KeyType holds the key FieldDescription for map fields. */
  final FieldDescription keyType;

  /** ValueType holds the value FieldDescription for map fields. */
  final FieldDescription valueType;

  private final FieldDescriptor desc;
  private final Class<?> reflectType;
  private final Message zeroMsg;

  /**
   * Creates metadata for a protobuf field.
   *
   * <p>Map-entry key and value descriptions are created recursively.
   *
   * @param fieldDesc immutable protobuf field descriptor
   * @return a new field description
   * @throws NullPointerException if {@code fieldDesc} is null
   */
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
    return switch (fieldDesc.getType()) {
      case DOUBLE -> Double.class;
      case FLOAT -> Float.class;
      case STRING -> String.class;
      case BOOL -> Boolean.class;
      case BYTES -> ByteString.class;
      case INT32, SFIXED32, SINT32 -> Integer.class;
      case INT64, SFIXED64, SINT64 -> Long.class;
      case UINT32, UINT64, FIXED32, FIXED64 -> ULong.class;
      case ENUM -> Enum.class;
      default -> reflectTypeOf(fieldDesc.getDefaultValue());
    };
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

  /**
   * Returns the checked CEL type for this field.
   *
   * <p>Repeated non-map fields become lists and protobuf map fields become maps.
   */
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

  /** Returns the immutable protobuf field descriptor. */
  public FieldDescriptor descriptor() {
    return desc;
  }

  /**
   * Tests field presence using the applicable proto2 or proto3 rules.
   *
   * @param target generated or dynamic protobuf message
   * @return {@code false} if {@code target} is not a message, has no matching field descriptor, or
   *     the field is absent
   */
  public boolean isSet(Object target) {
    if (target instanceof Message v) {
      FieldDescriptor fd = fieldDescriptorFor(v);
      return fd != null && FieldDescription.hasValueForField(fd, v);
    }
    return false;
  }

  /**
   * Reads a field and normalizes it to the Java representation expected by CEL.
   *
   * <p>Absent ordinary fields use protobuf defaults. Absent well-known wrapper fields use protobuf
   * null. Dynamic well-known messages are unwrapped with {@code db}.
   *
   * @param db descriptor database used to unwrap nested messages
   * @param target non-null generated or dynamic protobuf message whose schema contains this field
   * @return normalized scalar, list, map, or message value
   * @throws IllegalArgumentException if {@code target} is not a protobuf message
   * @throws NullPointerException if {@code target} is {@code null} or its schema does not contain
   *     this field
   */
  public Object getFrom(Db db, Object target) {
    if (!(target instanceof Message v)) {
      throw new IllegalArgumentException(
          String.format(
              "unsupported field selection target: (%s)%s", target.getClass().getName(), target));
    }
    // pbRef = v.protoReflect();
    FieldDescriptor fd = fieldDescriptorFor(v);
    Object fieldVal = getValueFromField(fd, v);

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
    if (fd.isRepeated() && fieldVal instanceof List) {
      return fieldVal;
    }
    if (fieldVal instanceof Message) {
      return maybeUnwrapDynamic(db, (Message) fieldVal);
    }
    if (fd.isMapField() && fieldVal instanceof Map) {
      return fieldVal;
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

  /**
   * Returns whether this field descriptor has protobuf enum Java type.
   *
   * <p>Repeated enum fields qualify; map fields do not, even when their value type is an enum.
   */
  public boolean isEnum() {
    return desc.getJavaType() == JavaType.ENUM;
  }

  /** Returns whether this is a protobuf map field. */
  public boolean isMap() {
    return desc.isMapField();
  }

  /**
   * Returns whether this field descriptor has protobuf message Java type.
   *
   * <p>Every protobuf map field qualifies because its descriptor uses a synthetic message entry,
   * regardless of the map value type.
   */
  public boolean isMessage() {
    return desc.getJavaType() == JavaType.MESSAGE;
  }

  /** Returns whether the field is declared in a protobuf {@code oneof}. */
  public boolean isOneof() {
    return desc.getContainingOneof() != null;
  }

  /** Returns whether this is a repeated non-map field. */
  public boolean isList() {
    return desc.isRepeated() && !desc.isMapField();
  }

  /**
   * Unwraps a dynamic well-known message to the Java representation expected by CEL.
   *
   * @param db descriptor database used for nested {@code Any} values and extensions
   * @param msg message to inspect
   * @return the unwrapped value, or the original message when no conversion applies
   */
  public Object maybeUnwrapDynamic(Db db, Message msg) {
    return unwrapDynamic(db, this, msg);
  }

  /** Returns the protobuf source name of the field. */
  public String name() {
    return desc.getName();
  }

  /**
   * Returns the Java representation used when converting a CEL value for this field.
   *
   * <p>Map fields return {@link Map}; repeated fields return an array class; scalar fields return
   * their boxed class. Ordinary message fields use {@link DynamicMessage}; recognized well-known
   * message fields use their canonical generated class. Generated optimized field access is
   * selected separately from this descriptor-level representation.
   */
  public Class<?> reflectType() {
    boolean r = desc.isRepeated();
    if (r && desc.isMapField()) {
      return Map.class;
    }
    return switch (desc.getJavaType()) {
      case ENUM, MESSAGE -> reflectType;
      case BOOLEAN -> r ? Boolean[].class : Boolean.class;
      case BYTE_STRING -> r ? ByteString[].class : ByteString.class;
      case DOUBLE -> r ? Double[].class : Double.class;
      case FLOAT -> r ? Float[].class : Float.class;
      case INT -> r ? Integer[].class : Integer.class;
      case LONG -> r ? Long[].class : Long.class;
      case STRING -> r ? String[].class : String.class;
    };
  }

  /**
   * Returns the default protobuf message for a message-valued field.
   *
   * @return a dynamic default message, or {@code null} for non-message fields
   */
  @Override
  public Message zero() {
    return zeroMsg;
  }

  /**
   * Returns the scalar or message CEL type for one value of this field.
   *
   * <p>Unlike {@link #checkedType()}, this method does not wrap repeated and map fields in their
   * aggregate type.
   */
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

  /**
   * Tests presence on a generated or dynamic protobuf message.
   *
   * @return {@code false} for a non-message target, a message without a matching field descriptor,
   *     or an absent field
   */
  public boolean hasField(Object target) {
    if (!(target instanceof Message message)) {
      return false;
    }
    FieldDescriptor fd = fieldDescriptorFor(message);
    return fd != null && hasValueForField(fd, message);
  }

  /**
   * Reads and normalizes this field using {@link DefaultTypeAdapter#Instance} for map values.
   *
   * @param target non-null generated or dynamic protobuf message whose schema contains this field
   * @return a normalized Java value or CEL map wrapper
   * @throws ClassCastException if {@code target} is not a protobuf message
   * @throws NullPointerException if {@code target} is {@code null} or its schema does not contain
   *     this field
   */
  public Object getField(Object target) {
    return getField(target, DefaultTypeAdapter.Instance);
  }

  /**
   * Reads and normalizes this field using the supplied adapter for map values.
   *
   * @param target non-null generated or dynamic protobuf message whose schema contains this field
   * @param adapter adapter retained by protobuf map wrappers for nested conversion
   * @return a normalized Java value or CEL map wrapper
   * @throws ClassCastException if {@code target} is not a protobuf message
   * @throws NullPointerException if {@code target} is {@code null} or its schema does not contain
   *     this field
   */
  public Object getField(Object target, TypeAdapter adapter) {
    Message message = (Message) target;
    FieldDescriptor fd = fieldDescriptorFor(message);
    return adaptDescriptorValue(message, fd, message.getField(fd), adapter);
  }

  Object rawField(Object target) {
    Message message = (Message) target;
    FieldDescriptor fd = fieldDescriptorFor(message);
    return message.getField(fd);
  }

  Object adaptDescriptorValue(Object target, Object value, TypeAdapter adapter) {
    Message message = (Message) target;
    FieldDescriptor fd = fieldDescriptorFor(message);
    return adaptDescriptorValue(message, fd, value, adapter);
  }

  private static Object adaptDescriptorValue(
      Message message, FieldDescriptor fd, Object value, TypeAdapter adapter) {
    if (fd.isMapField() && value instanceof List) {
      return new ProtoMapT(adapter, fd, (List<?>) value);
    }
    return normalizeValueFromField(fd, message, value);
  }

  Object adaptGeneratedValue(Object value, TypeAdapter adapter) {
    if (desc.isMapField() && value instanceof Map) {
      return new ProtoMapT(adapter, desc, (Map<?, ?>) value);
    }
    if (desc.isRepeated()) {
      FieldDescriptor.Type type = desc.getType();
      if (value instanceof List
          && (type == FieldDescriptor.Type.UINT32
              || type == FieldDescriptor.Type.UINT64
              || type == FieldDescriptor.Type.FIXED32
              || type == FieldDescriptor.Type.FIXED64)) {
        return new UnsignedLongList(desc, (List<?>) value);
      }
      return value;
    }
    return normalizeUnsignedValue(desc, value);
  }

  Object exactRepeatedValue(Object value) {
    FieldDescriptor.Type type = desc.getType();
    if (value instanceof List
        && (type == FieldDescriptor.Type.UINT32
            || type == FieldDescriptor.Type.UINT64
            || type == FieldDescriptor.Type.FIXED32
            || type == FieldDescriptor.Type.FIXED64)) {
      return new UnsignedLongList(desc, (List<?>) value);
    }
    return value;
  }

  boolean isWrapper() {
    return isWellKnownType(desc);
  }

  /**
   * Reads a field through protobuf reflection and normalizes unsigned, map, and wrapper values.
   *
   * @param desc descriptor belonging to {@code message}
   * @param message generated or dynamic message
   * @return the normalized Java value
   */
  public static Object getValueFromField(FieldDescriptor desc, Message message) {
    return normalizeValueFromField(desc, message, message.getField(desc));
  }

  private static Object normalizeValueFromField(
      FieldDescriptor desc, Message message, Object fieldValue) {
    if (!desc.isRepeated() && isWellKnownType(desc) && !message.hasField(desc)) {
      return NullValue.NULL_VALUE;
    }

    Object v = fieldValue;
    if (!desc.isMapField() && !desc.isRepeated()) {
      FieldDescriptor.Type type = desc.getType();
      if (v != null
          && (type == FieldDescriptor.Type.UINT32
              || type == FieldDescriptor.Type.UINT64
              || type == FieldDescriptor.Type.FIXED32
              || type == FieldDescriptor.Type.FIXED64)) {
        v = normalizeUnsignedValue(desc, v);
      }
    } else if (desc.isMapField()) {
      // TODO protobuf-java inefficiency
      //  protobuf-java does NOT have a generic way to retrieve the underlying map, but instead
      //  getField() returns a list of com.google.protobuf.MapEntry. It's not great that we have
      //  to have this workaround here to re-build a j.u.Map.
      //  I.e. to access a single map entry we *HAVE TO* touch and re-build the whole map. This
      //  is very inefficient.
      //  There is no way to do a "message.getMapField(desc, key)" (aka a "reflective counterpart"
      //  for the generated map accessor methods like 'getXXXTypeOrThrow()'), too.
      if (v instanceof List<?> lst) {
        Map<Object, Object> map = new HashMap<>(lst.size() * 4 / 3 + 1);
        FieldDescriptor keyDesc = desc.getMessageType().findFieldByNumber(1);
        FieldDescriptor valueDesc = desc.getMessageType().findFieldByNumber(2);
        for (Object e : lst) {
          Object key;
          Object value;
          if (e instanceof MapEntry) {
            key = normalizeUnsignedValue(keyDesc, ((MapEntry<?, ?>) e).getKey());
            value = normalizeUnsignedValue(valueDesc, ((MapEntry<?, ?>) e).getValue());
          } else if (e instanceof DynamicMessage dynMsg) {
            List<FieldDescriptor> fields = dynMsg.getDescriptorForType().getFields();
            if (fields.size() == 2) {
              FieldDescriptor dynKeyDesc = fields.get(0);
              FieldDescriptor dynValueDesc = fields.get(1);
              key = normalizeUnsignedValue(dynKeyDesc, dynMsg.getField(dynKeyDesc));
              value = normalizeUnsignedValue(dynValueDesc, dynMsg.getField(dynValueDesc));
            } else {
              throw new IllegalArgumentException(
                  String.format(
                      "Unexpected %s (%s) in list of map fields, dynamic message with != 2 fields",
                      e.getClass(), e));
            }
          } else {
            throw new IllegalArgumentException(
                String.format("Unexpected %s (%s) in list of map fields", e.getClass(), e));
          }
          map.put(key, value);
        }
        v = map;
      }
    } else if (desc.isRepeated()) {
      FieldDescriptor.Type type = desc.getType();
      // Ensure the right Java representation is used in resulting array.
      if (v != null
          && (type == FieldDescriptor.Type.UINT32
              || type == FieldDescriptor.Type.UINT64
              || type == FieldDescriptor.Type.FIXED32
              || type == FieldDescriptor.Type.FIXED64)) {
        v = new UnsignedLongList(desc, (List<?>) v);
      }
    }
    return v;
  }

  private FieldDescriptor fieldDescriptorFor(Message message) {
    Descriptor messageDesc = message.getDescriptorForType();
    if (messageDesc == desc.getContainingType()) {
      return desc;
    }
    if (!desc.isExtension()) {
      return messageDesc.findFieldByName(name());
    }
    for (FieldDescriptor field : message.getAllFields().keySet()) {
      if (field.getFullName().equals(desc.getFullName())) {
        return field;
      }
    }
    if (messageDesc.getFullName().equals(desc.getContainingType().getFullName())) {
      return desc;
    }
    return null;
  }

  private static final class UnsignedLongList extends AbstractList<ULong> {
    private final FieldDescriptor field;
    private final List<?> repeated;

    private UnsignedLongList(FieldDescriptor field, List<?> repeated) {
      this.field = field;
      this.repeated = repeated;
    }

    @Override
    public ULong get(int index) {
      return (ULong) normalizeUnsignedValue(field, repeated.get(index));
    }

    @Override
    public int size() {
      return repeated.size();
    }
  }

  private static final class ProtoMapT extends MapT {
    private final TypeAdapter adapter;
    private final List<?> entries;
    private final Map<?, ?> map;
    private final FieldDescriptor keyDesc;
    private final FieldDescriptor valueDesc;
    private volatile Map<Val, Val> indexedEntries;
    private volatile Map<Object, Object> canonicalEntries;
    private volatile boolean scanned;

    private ProtoMapT(TypeAdapter adapter, FieldDescriptor field, List<?> entries) {
      this.adapter = adapter;
      this.entries = entries;
      this.map = null;
      this.keyDesc = field.getMessageType().findFieldByNumber(1);
      this.valueDesc = field.getMessageType().findFieldByNumber(2);
    }

    private ProtoMapT(TypeAdapter adapter, FieldDescriptor field, Map<?, ?> map) {
      this.adapter = adapter;
      this.entries = null;
      this.map = map;
      this.keyDesc = field.getMessageType().findFieldByNumber(1);
      this.valueDesc = field.getMessageType().findFieldByNumber(2);
    }

    @SuppressWarnings({"removal", "unchecked"})
    @Override
    public <T> T convertToNative(Class<T> typeDesc) {
      if (typeDesc == Map.class) {
        return (T) toJavaMap();
      }
      return adapter.valueToNative(MapT.newMaybeWrappedMap(adapter, toJavaMap()), typeDesc);
    }

    @Override
    public Val convertToType(org.projectnessie.cel.common.types.ref.Type typeValue) {
      if (typeValue == MapT.MapType) {
        return this;
      }
      if (typeValue == TypeType) {
        return MapT.MapType;
      }
      return newTypeConversionError(MapT.MapType, typeValue);
    }

    @Override
    public IteratorT iterator() {
      return new EntryKeyIterator();
    }

    @Override
    public Val equal(Val other) {
      if (other instanceof ProtoMapT protoOther
          && map != null
          && protoOther.map != null
          && keyDesc.getType() == protoOther.keyDesc.getType()) {
        if (nativeSize() != protoOther.nativeSize()) {
          return False;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          Object key = entry.getKey();
          Object otherRawValue = protoOther.map.get(key);
          if (otherRawValue == null && !protoOther.map.containsKey(key)) {
            return False;
          }
          Val equal =
              mapValueEqual(
                  adapter.nativeToValue(normalizeUnsignedValue(valueDesc, entry.getValue())),
                  protoOther.adapter.nativeToValue(
                      normalizeUnsignedValue(protoOther.valueDesc, otherRawValue)));
          if (equal != True) {
            return equal;
          }
        }
        return True;
      }
      return mapEqual(other);
    }

    @Override
    public Object value() {
      return toJavaMap();
    }

    @Override
    public Val contains(Val value) {
      if (isUnknownOrError(value)) {
        return value;
      }
      if (map != null) {
        Object nativeKey = nativeMapKey(value);
        if (nativeKey != NO_NATIVE_MAP_KEY) {
          return boolOf(map.containsKey(nativeKey));
        }
      }
      return boolOf(find(value) != null);
    }

    @Override
    public Val get(Val index) {
      return find(index);
    }

    @Override
    public Val size() {
      return intOf(entryCount());
    }

    @Override
    public int nativeSize() {
      return entryCount();
    }

    @Override
    protected Iterable<?> mapEntries() {
      return allEntries();
    }

    @Override
    protected Val mapEntryKey(Object entry) {
      return adapter.nativeToValue(nativeEntryKey(entry));
    }

    @Override
    protected Val mapEntryValue(Object entry) {
      return adapter.nativeToValue(nativeEntryValue(entry));
    }

    @Override
    public Val find(Val key) {
      if (map != null) {
        Object nativeKey = nativeMapKey(key);
        if (nativeKey != NO_NATIVE_MAP_KEY) {
          Object value = map.get(nativeKey);
          return value != null || map.containsKey(nativeKey)
              ? adapter.nativeToValue(normalizeUnsignedValue(valueDesc, value))
              : null;
        }
      }
      Map<Val, Val> index = indexedEntries;
      if (index != null) {
        return index.get(key);
      }
      if (!scanned || physicalEntryCount() <= 1) {
        scanned = true;
        return scan(key);
      }
      return indexedEntries().get(key);
    }

    private Object nativeMapKey(Val key) {
      switch (keyDesc.getType()) {
        case BOOL:
          return key.type().typeEnum() == TypeEnum.Bool ? key.booleanValue() : NO_NATIVE_MAP_KEY;
        case STRING:
          return key.type().typeEnum() == TypeEnum.String ? key.value() : NO_NATIVE_MAP_KEY;
        case INT32:
        case SINT32:
        case SFIXED32:
          if (key.type().typeEnum() != TypeEnum.Int) {
            return NO_NATIVE_MAP_KEY;
          }
          long int32 = key.intValue();
          return int32 >= Integer.MIN_VALUE && int32 <= Integer.MAX_VALUE
              ? (int) int32
              : NO_NATIVE_MAP_KEY;
        case INT64:
        case SINT64:
        case SFIXED64:
          return key.type().typeEnum() == TypeEnum.Int ? key.intValue() : NO_NATIVE_MAP_KEY;
        case UINT32:
        case FIXED32:
          if (key.type().typeEnum() != TypeEnum.Uint) {
            return NO_NATIVE_MAP_KEY;
          }
          long uint32 = key.intValue();
          return Long.compareUnsigned(uint32, 0xffff_ffffL) <= 0 ? (int) uint32 : NO_NATIVE_MAP_KEY;
        case UINT64:
        case FIXED64:
          return key.type().typeEnum() == TypeEnum.Uint ? key.intValue() : NO_NATIVE_MAP_KEY;
        default:
          return NO_NATIVE_MAP_KEY;
      }
    }

    private Val scan(Val key) {
      if (entries != null) {
        for (int i = entries.size() - 1; i >= 0; i--) {
          Object entry = entries.get(i);
          Val candidate = adapter.nativeToValue(nativeEntryKey(entry));
          if (candidate.equal(key) == True) {
            return adapter.nativeToValue(nativeEntryValue(entry));
          }
        }
      } else {
        for (Object entry : map.entrySet()) {
          Val candidate = adapter.nativeToValue(nativeEntryKey(entry));
          if (candidate.equal(key) == True) {
            return adapter.nativeToValue(nativeEntryValue(entry));
          }
        }
      }
      return null;
    }

    private Map<Val, Val> indexedEntries() {
      Map<Val, Val> index = indexedEntries;
      if (index != null) {
        return index;
      }
      synchronized (this) {
        index = indexedEntries;
        if (index == null) {
          index = new HashMap<>(entryCount() * 4 / 3 + 1);
          for (Object entry : allEntries()) {
            Val key = adapter.nativeToValue(nativeEntryKey(entry));
            Val value = adapter.nativeToValue(nativeEntryValue(entry));
            index.put(key, value);
          }
          indexedEntries = index;
        }
        return index;
      }
    }

    private Map<Object, Object> toJavaMap() {
      if (entries != null) {
        return new LinkedHashMap<>(canonicalEntries());
      }
      Map<Object, Object> javaMap = new LinkedHashMap<>(map.size() * 4 / 3 + 1);
      for (Object entry : allEntries()) {
        javaMap.put(nativeEntryKey(entry), nativeEntryValue(entry));
      }
      return javaMap;
    }

    private Object nativeEntryKey(Object entry) {
      return ProtoMapSupport.key(entry, keyDesc);
    }

    private Object nativeEntryValue(Object entry) {
      return ProtoMapSupport.value(entry, valueDesc);
    }

    private int entryCount() {
      return entries != null ? canonicalEntries().size() : map.size();
    }

    private int physicalEntryCount() {
      return entries != null ? entries.size() : map.size();
    }

    private Iterable<?> allEntries() {
      return entries != null ? canonicalEntries().entrySet() : map.entrySet();
    }

    private Map<Object, Object> canonicalEntries() {
      Map<Object, Object> result = canonicalEntries;
      if (result != null) {
        return result;
      }
      synchronized (this) {
        result = canonicalEntries;
        if (result == null) {
          result = ProtoMapSupport.canonicalMap(entries, keyDesc, valueDesc);
          canonicalEntries = result;
        }
        return result;
      }
    }

    private final class EntryKeyIterator implements IteratorT {
      private final java.util.Iterator<?> iterator = allEntries().iterator();

      @Override
      public Val hasNext() {
        return boolOf(iterator.hasNext());
      }

      @Override
      public Val next() {
        if (iterator.hasNext()) {
          return adapter.nativeToValue(nativeEntryKey(iterator.next()));
        }
        return noMoreElements();
      }

      @Override
      public boolean equals(Object other) {
        return this == other;
      }

      @Override
      public int hashCode() {
        return System.identityHashCode(this);
      }

      @Override
      public String toString() {
        return "iterator";
      }
    }
  }

  static Object normalizeUnsignedValue(FieldDescriptor desc, Object value) {
    FieldDescriptor.Type type = desc.getType();
    if (value instanceof Number number) {
      if (type == FieldDescriptor.Type.UINT32 || type == FieldDescriptor.Type.FIXED32) {
        return ULong.valueOf(Integer.toUnsignedLong(number.intValue()));
      }
      if (type == FieldDescriptor.Type.UINT64 || type == FieldDescriptor.Type.FIXED64) {
        return ULong.valueOf(number.longValue());
      }
    }
    return value;
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

  /**
   * Tests protobuf field presence.
   *
   * <p>Repeated fields are present when non-empty; singular fields delegate to {@link
   * Message#hasField(FieldDescriptor)}.
   */
  public static boolean hasValueForField(FieldDescriptor desc, Message message) {
    if (desc.isRepeated()) {
      return message.getRepeatedFieldCount(desc) > 0;
    }
    return message.hasField(desc);
  }
}
