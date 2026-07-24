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

import static org.projectnessie.cel.common.types.BoolT.BoolType;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.BytesT.BytesType;
import static org.projectnessie.cel.common.types.DoubleT.DoubleType;
import static org.projectnessie.cel.common.types.DurationT.DurationType;
import static org.projectnessie.cel.common.types.Err.anyWithEmptyType;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.noSuchField;
import static org.projectnessie.cel.common.types.Err.unknownType;
import static org.projectnessie.cel.common.types.Err.unsupportedRefValConversionErr;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.ListT.ListType;
import static org.projectnessie.cel.common.types.MapT.MapType;
import static org.projectnessie.cel.common.types.NullT.NullType;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.TimestampType;
import static org.projectnessie.cel.common.types.TypeT.TypeType;
import static org.projectnessie.cel.common.types.TypeT.newObjectTypeValue;
import static org.projectnessie.cel.common.types.UintT.UintType;
import static org.projectnessie.cel.common.types.pb.Db.collectFileDescriptorSet;
import static org.projectnessie.cel.common.types.pb.Db.newDb;
import static org.projectnessie.cel.common.types.pb.DefaultTypeAdapter.maybeUnwrapValue;
import static org.projectnessie.cel.common.types.pb.PbObjectT.newObject;
import static org.projectnessie.cel.common.types.pb.PbTypeDescription.typeNameFromMessage;
import static org.projectnessie.cel.common.types.ref.TypeAdapterSupport.maybeNativeToValue;

import com.google.api.expr.v1alpha1.Type;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf.WireFormat;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.IteratorT;
import org.projectnessie.cel.common.types.MapT;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.TypeT;
import org.projectnessie.cel.common.types.ref.FieldGetter;
import org.projectnessie.cel.common.types.ref.FieldTester;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.StandardScalarFieldProvider;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeAdapterSupport;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Lister;

public class ProtoTypeRegistry
    implements TypeRegistry, StandardScalarTypeAdapter, StandardScalarFieldProvider {
  private static final ProtoTypeRegistry DEFAULT_REGISTRY = newDefaultRegistry();

  private final Map<String, org.projectnessie.cel.common.types.ref.Type> revTypeMap;
  private final Map<String, Map<String, FieldType>> fieldTypeCache;
  private final Db pbdb;

  ProtoTypeRegistry(Map<String, org.projectnessie.cel.common.types.ref.Type> revTypeMap, Db pbdb) {
    this.revTypeMap = revTypeMap;
    this.fieldTypeCache = new ConcurrentHashMap<>();
    this.pbdb = pbdb;
  }

  /**
   * NewRegistry accepts a list of proto message instances and returns a type provider which can
   * create new instances of the provided message or any message that proto depends upon in its
   * FileDescriptor.
   */
  public static ProtoTypeRegistry newRegistry(Message... types) {
    ProtoTypeRegistry p = DEFAULT_REGISTRY.copy();
    for (Message msgType : types) {
      p.registerMessage(msgType);
    }
    return p;
  }

  /**
   * Returns an opt-in protobuf registry whose supported aggregate fields expose certified exact
   * Java representations.
   *
   * <p>The returned registry remains a {@link ProtoTypeRegistry}. Unsupported aggregate fields and
   * all scalar fields retain the default protobuf behavior.
   */
  public static TypeRegistry newExactAggregateRegistry(Message... types) {
    ProtoTypeRegistry p =
        new ExactProtoTypeRegistry(
            new HashMap<>(DEFAULT_REGISTRY.revTypeMap), DEFAULT_REGISTRY.pbdb.copy());
    for (Message msgType : types) {
      p.registerMessage(msgType);
    }
    return p;
  }

  private static ProtoTypeRegistry newDefaultRegistry() {
    ProtoTypeRegistry p = new ProtoTypeRegistry(new HashMap<>(), newDb());
    p.registerType(
        BoolType,
        BytesType,
        DoubleType,
        DurationType,
        IntType,
        ListType,
        MapType,
        NullType,
        StringType,
        TimestampType,
        TypeType,
        UintType);

    Set<FileDescriptor> pbDescriptors =
        new LinkedHashSet<>(
            Arrays.asList(
                DoubleValue.getDescriptor().getFile(),
                Empty.getDescriptor().getFile(),
                FieldMask.getDescriptor().getFile(),
                Timestamp.getDescriptor().getFile(),
                UInt64Value.getDescriptor().getFile(),
                Any.getDescriptor().getFile(),
                com.google.protobuf.NullValue.getDescriptor().getFile(),
                Struct.getDescriptor().getFile(),
                StringValue.getDescriptor().getFile(),
                ListValue.getDescriptor().getFile(),
                BytesValue.getDescriptor().getFile(),
                Value.getDescriptor().getFile(),
                // TODO Struct.FieldsEntry.getDescriptor().getFile(),
                Int32Value.getDescriptor().getFile(),
                UInt32Value.getDescriptor().getFile(),
                Duration.getDescriptor().getFile(),
                FloatValue.getDescriptor().getFile(),
                BoolValue.getDescriptor().getFile(),
                Int64Value.getDescriptor().getFile()));
    for (FileDescriptor fDesc : pbDescriptors) {
      FileDescription fd = FileDescription.newFileDescription(fDesc);
      p.registerAllTypes(fd);
    }

    // This block ensures that the well-known protobuf types are registered by default.
    for (FileDescription fd : p.pbdb.fileDescriptions()) {
      p.registerAllTypes(fd);
    }
    return p;
  }

  /** NewEmptyRegistry returns a registry which is completely unconfigured. */
  public static ProtoTypeRegistry newEmptyRegistry() {
    return new ProtoTypeRegistry(new HashMap<>(), newDb());
  }

  /**
   * Copy implements the ref.TypeRegistry interface method which copies the current state of the
   * registry into its own memory space.
   */
  @Override
  public ProtoTypeRegistry copy() {
    return newCopy(new HashMap<>(this.revTypeMap), pbdb.copy());
  }

  ProtoTypeRegistry newCopy(
      Map<String, org.projectnessie.cel.common.types.ref.Type> copiedTypes, Db copiedDb) {
    return new ProtoTypeRegistry(copiedTypes, copiedDb);
  }

  @Override
  public void register(Object t) {
    if (t instanceof Message) {
      Set<FileDescriptor> fds = collectFileDescriptorSet((Message) t);
      for (FileDescriptor fd : fds) {
        registerDescriptor(fd);
      }
      registerMessage((Message) t);
    } else if (t instanceof org.projectnessie.cel.common.types.ref.Type) {
      registerType((org.projectnessie.cel.common.types.ref.Type) t);
    } else {
      throw new RuntimeException(String.format("unsupported type: %s", t.getClass().getName()));
    }
  }

  @Override
  public Val enumValue(String enumName) {
    EnumValueDescription enumVal = pbdb.describeEnum(enumName);
    if (enumVal == null) {
      return newErr("unknown enum name '%s'", enumName);
    }
    return intOf(enumVal.value());
  }

  @Override
  public FieldType findFieldType(String messageType, String fieldName) {
    Map<String, FieldType> messageFields = fieldTypeCache.get(messageType);
    if (messageFields == null) {
      Map<String, FieldType> newFields = new ConcurrentHashMap<>();
      Map<String, FieldType> existing = fieldTypeCache.putIfAbsent(messageType, newFields);
      messageFields = existing != null ? existing : newFields;
    }
    FieldType fieldType = messageFields.get(fieldName);
    if (fieldType != null) {
      return fieldType;
    }
    FieldType loaded = loadFieldType(messageType, fieldName);
    if (loaded == null) {
      return null;
    }
    FieldType existing = messageFields.putIfAbsent(fieldName, loaded);
    return existing != null ? existing : loaded;
  }

  private FieldType loadFieldType(String messageType, String fieldName) {
    FieldDescription field = findFieldDescription(messageType, fieldName);
    if (field == null) {
      return null;
    }
    FieldTester descriptorTester = field::hasField;
    FieldGetter descriptorGetter =
        target -> normalizeFieldValue(field, target, field.rawField(target), false);
    PbTypeDescription type = pbdb.describeType(messageType);
    FieldGetter generatedGetter = type != null ? GeneratedFieldAccessor.create(type, field) : null;
    FieldGetter objectGetter = generatedGetter;
    if (objectGetter == null && type != null) {
      objectGetter = GeneratedFieldAccessor.createForObject(type, field);
    }
    FieldTester generatedTester =
        type != null ? GeneratedFieldAccessor.createTester(type, field) : null;
    FieldTester tester = descriptorTester;
    if (generatedTester != null) {
      Class<?> generatedType = type.reflectType();
      tester =
          target ->
              generatedType.isInstance(target)
                  ? generatedTester.isSet(target)
                  : field.hasField(target);
    }
    FieldGetter getter =
        generatedGetter != null
            ? bindGeneratedGetter(type, field, generatedGetter, tester)
            : descriptorGetter;
    FieldGetter optimizedObjectGetter =
        objectGetter != null ? bindGeneratedGetter(type, field, objectGetter, tester) : null;
    return new ProtoFieldType(
        field.checkedType(), tester, getter, generatedTester, optimizedObjectGetter);
  }

  private FieldGetter bindGeneratedGetter(
      PbTypeDescription type,
      FieldDescription field,
      FieldGetter generatedGetter,
      FieldTester tester) {
    Class<?> generatedType = type.reflectType();
    if (field.isWrapper()) {
      return target ->
          generatedType.isInstance(target)
              ? !tester.isSet(target)
                  ? com.google.protobuf.NullValue.NULL_VALUE
                  : generatedGetter.getFrom(target)
              : field.getField(target, this);
    }
    if (generatedGetter instanceof FieldGetter.Primitive primitiveGetter) {
      return new FieldGetter.Primitive() {
        @Override
        public Class<?> optimizedTargetType() {
          return primitiveGetter.optimizedTargetType();
        }

        @Override
        public Object getFrom(Object target) {
          return generatedType.isInstance(target)
              ? normalizeFieldValue(field, target, primitiveGetter.getFrom(target), true)
              : normalizeFieldValue(field, target, field.rawField(target), false);
        }

        @Override
        public boolean getBooleanFrom(Object target) {
          return primitiveGetter.getBooleanFrom(target);
        }

        @Override
        public long getLongFrom(Object target) {
          return primitiveGetter.getLongFrom(target);
        }

        @Override
        public double getDoubleFrom(Object target) {
          return primitiveGetter.getDoubleFrom(target);
        }
      };
    }
    return target ->
        generatedType.isInstance(target)
            ? normalizeFieldValue(field, target, generatedGetter.getFrom(target), true)
            : normalizeFieldValue(field, target, field.rawField(target), false);
  }

  Object normalizeFieldValue(
      FieldDescription field, Object target, Object value, boolean generated) {
    return generated
        ? field.adaptGeneratedValue(value, this)
        : field.adaptDescriptorValue(target, value, this);
  }

  FieldType findFieldTypeForObjectAccess(
      String messageType, String fieldName, boolean presenceTest) {
    FieldType fieldType = findFieldType(messageType, fieldName);
    if (!(fieldType instanceof ProtoFieldType protoFieldType)) {
      return null;
    }
    return presenceTest
        ? protoFieldType.objectPresenceFieldType
        : protoFieldType.objectGetterFieldType;
  }

  FieldDescription findFieldDescription(String messageType, String fieldName) {
    PbTypeDescription msgType = pbdb.describeType(messageType);
    if (msgType == null) {
      return null;
    }
    FieldDescription field = msgType.fieldByName(fieldName);
    if (field == null) {
      field = pbdb.describeExtension(messageType, fieldName);
    }
    return field;
  }

  @Override
  public Val findIdent(String identName) {
    org.projectnessie.cel.common.types.ref.Type t = revTypeMap.get(identName);
    if (t != null) {
      return t;
    }
    EnumValueDescription enumVal = pbdb.describeEnum(identName);
    if (enumVal != null) {
      return intOf(enumVal.value());
    }
    if (pbdb.describeExtension(identName) != null) {
      return stringOf(identName);
    }
    return null;
  }

  @Override
  public Type findType(String typeName) {
    if (pbdb.describeType(typeName) == null) {
      return null;
    }
    if (!typeName.isEmpty() && typeName.charAt(0) == '.') {
      typeName = typeName.substring(1);
    }
    return Type.newBuilder().setType(Type.newBuilder().setMessageType(typeName)).build();
  }

  @Override
  public Val newValue(String typeName, Map<String, Val> fields) {
    PbTypeDescription td = pbdb.describeType(typeName);
    if (td == null) {
      return unknownType(typeName);
    }
    Builder builder = td.newMessageBuilder();
    Val err = newValueSetFields(fields, td, builder);
    if (err != null) {
      return err;
    }
    Message msg = builder.build();
    return nativeToValue(msg);
  }

  private Val newValueSetFields(Map<String, Val> fields, PbTypeDescription td, Builder builder) {
    Map<String, FieldDescription> fieldMap = td.fieldMap();
    for (Entry<String, Val> nv : fields.entrySet()) {
      String name = nv.getKey();
      FieldDescription field = fieldMap.get(name);
      if (field == null) {
        return noSuchField(name);
      }

      FieldDescriptor pbDesc = field.descriptor();
      if (nv.getValue() == org.projectnessie.cel.common.types.NullT.NullValue
          && isNullClearedField(pbDesc)) {
        continue;
      }

      try {
        Object value;
        if (pbDesc.isMapField() && nv.getValue() instanceof MapT map) {
          value = toProtoMapStructure(field, map);
        } else {
          value = toNativeFieldValue(nv.getValue(), field);
          if (value.getClass().isArray()) {
            value = Arrays.asList((Object[]) value);
          }

          if (pbDesc.getJavaType() == JavaType.ENUM) {
            value = intToProtoEnumValues(field, value);
          }

          if (pbDesc.isMapField()) {
            value = toProtoMapStructure(pbDesc, value);
          }
        }

        builder.setField(pbDesc, value);
      } catch (RuntimeException e) {
        return newErr(e, "invalid value for field '%s': %s", name, e.getMessage());
      }
    }
    return null;
  }

  private Object toNativeFieldValue(Val value, FieldDescription field) {
    FieldDescriptor fieldDesc = field.descriptor();
    if (fieldDesc.isRepeated()
        && !fieldDesc.isMapField()
        && isNullPrunedMessageField(fieldDesc)
        && value instanceof Lister) {
      return toNativeRepeatedFieldValue((Lister) value, fieldDesc);
    }
    return valueToNative(value, field.reflectType());
  }

  private Object toNativeRepeatedFieldValue(Lister value, FieldDescriptor fieldDesc) {
    Class<?> elementType = messageNativeType(fieldDesc);
    int size = value.nativeSize();
    List<Object> converted = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      Val element = value.nativeGetAt(i);
      if (element == NullT.NullValue && isNullPrunedMessageField(fieldDesc)) {
        continue;
      }
      converted.add(valueToNative(element, elementType));
    }
    return converted;
  }

  private static boolean isNullClearedField(FieldDescriptor field) {
    if (field.getJavaType() != JavaType.MESSAGE || field.isRepeated() || field.isMapField()) {
      return false;
    }
    Type wellKnownType = Checked.CheckedWellKnowns.get(field.getMessageType().getFullName());
    return wellKnownType == null || isNullPrunedMessageField(field);
  }

  private static boolean isNullPrunedMessageField(FieldDescriptor field) {
    if (field.getJavaType() != JavaType.MESSAGE) {
      return false;
    }
    String typeName = field.getMessageType().getFullName();
    Type wellKnownType = Checked.CheckedWellKnowns.get(typeName);
    if (wellKnownType == null) {
      return false;
    }
    return wellKnownType.hasWrapper()
        || typeName.equals("google.protobuf.Duration")
        || typeName.equals("google.protobuf.Timestamp");
  }

  private static Class<?> messageNativeType(FieldDescriptor field) {
    return switch (field.getMessageType().getFullName()) {
      case "google.protobuf.Any" -> Any.class;
      case "google.protobuf.BoolValue" -> BoolValue.class;
      case "google.protobuf.BytesValue" -> BytesValue.class;
      case "google.protobuf.DoubleValue" -> DoubleValue.class;
      case "google.protobuf.Duration" -> Duration.class;
      case "google.protobuf.FieldMask" -> FieldMask.class;
      case "google.protobuf.FloatValue" -> FloatValue.class;
      case "google.protobuf.Int32Value" -> Int32Value.class;
      case "google.protobuf.Int64Value" -> Int64Value.class;
      case "google.protobuf.StringValue" -> StringValue.class;
      case "google.protobuf.Timestamp" -> Timestamp.class;
      case "google.protobuf.UInt32Value" -> UInt32Value.class;
      case "google.protobuf.UInt64Value" -> UInt64Value.class;
      case "google.protobuf.Value" -> Value.class;
      default -> Message.class;
    };
  }

  /** Converts a CEL map directly to protobuf map entries without an intermediate Java map. */
  private Object toProtoMapStructure(FieldDescription field, MapT value) {
    FieldDescriptor fieldDesc = field.descriptor();
    Descriptor entryType = fieldDesc.getMessageType();
    FieldDescriptor keyType = field.keyType.descriptor();
    FieldDescriptor valueType = field.valueType.descriptor();
    WireFormat.FieldType keyFieldType = WireFormat.FieldType.valueOf(keyType.getType().name());
    WireFormat.FieldType valueFieldType = WireFormat.FieldType.valueOf(valueType.getType().name());
    List<MapEntry<?, ?>> entries = new ArrayList<>(value.nativeSize());

    IteratorT iterator = value.iterator();
    while (iterator.hasNext() == True) {
      Val key = iterator.next();
      Val mapValue = value.find(key);
      if (mapValue == NullT.NullValue && isNullPrunedMessageField(valueType)) {
        continue;
      }

      Object nativeKey = toNativeMapEntryValue(key, keyType);
      Object nativeValue = toNativeMapEntryValue(mapValue, valueType);
      entries.add(
          MapEntry.newDefaultInstance(
              entryType, keyFieldType, nativeKey, valueFieldType, nativeValue));
    }
    return entries;
  }

  private Object toNativeMapEntryValue(Val value, FieldDescriptor field) {
    return switch (field.getType()) {
      case DOUBLE -> valueToDouble(value);
      case FLOAT -> valueToNative(value, Float.class);
      case INT64, SINT64, SFIXED64 -> valueToLong(value);
      case UINT64, FIXED64 -> valueToNative(value, ULong.class).longValue();
      case INT32, SINT32, SFIXED32 -> valueToInt(value);
      case UINT32, FIXED32 -> valueToNative(value, ULong.class).intValue();
      case BOOL -> valueToBoolean(value);
      case STRING -> valueToNative(value, String.class);
      case BYTES -> valueToNative(value, ByteString.class);
      case ENUM -> {
        if (value == NullT.NullValue) {
          if (field.getEnumType().getFullName().equals("google.protobuf.NullValue")) {
            yield 0;
          }
          throw new IllegalArgumentException("null is only valid for google.protobuf.NullValue");
        }
        yield valueToInt(value);
      }
      case MESSAGE -> valueToNative(value, messageNativeType(field));
      case GROUP -> throw new IllegalArgumentException("protobuf maps cannot contain group values");
    };
  }

  /**
   * Converts {@code value}, of the map-field {@code fieldDesc} from its Java {@link Map}
   * representation to the protobuf-y {@code {@link List}<{@link MapEntry}>} representation.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Object toProtoMapStructure(FieldDescriptor fieldDesc, Object value) {
    Descriptor mesgType = fieldDesc.getMessageType();
    FieldDescriptor keyType = mesgType.findFieldByNumber(1);
    FieldDescriptor valueType = mesgType.findFieldByNumber(2);
    WireFormat.FieldType keyFieldType = WireFormat.FieldType.valueOf(keyType.getType().name());
    WireFormat.FieldType valueFieldType = WireFormat.FieldType.valueOf(valueType.getType().name());
    if (value instanceof Map) {
      List newList = new ArrayList();
      for (Map.Entry e : ((Map<?, ?>) value).entrySet()) {
        Object v = e.getValue();
        Object k = e.getKey();

        // TODO improve the type-A-to-B-conversion
        // if (!(k instanceof String)) {
        //   return Err.newTypeConversionError(k.getClass().getName(), String.class.getName());
        // }
        if (valueFieldType == WireFormat.FieldType.MESSAGE) {
          if (isNullNativeValue(v) && isNullPrunedMessageField(valueType)) {
            continue;
          }
          if (!(v instanceof Message)) {
            v = valueToNative(nativeToValue(v), messageNativeType(valueType));
          }
        }

        MapEntry newEntry =
            MapEntry.newDefaultInstance(mesgType, keyFieldType, k, valueFieldType, v);
        newList.add(newEntry);
      }
      value = newList;
    }

    return value;
  }

  private static boolean isNullNativeValue(Object value) {
    return value == null || value == com.google.protobuf.NullValue.NULL_VALUE;
  }

  /**
   * Converts a value of type {@link Number} to {@link EnumValueDescriptor}, also works for arrays
   * and {@link List}s containing {@link Number}s.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object intToProtoEnumValues(FieldDescription field, Object value) {
    EnumDescriptor enumType = field.descriptor().getEnumType();
    if (value instanceof Number) {
      int enumValue = ((Number) value).intValue();
      value = enumType.findValueByNumberCreatingIfUnknown(enumValue);
    } else if (value instanceof List list) {
      List newList = new ArrayList(list.size());
      for (Object o : list) {
        int enumValue = ((Number) o).intValue();
        newList.add(enumType.findValueByNumberCreatingIfUnknown(enumValue));
      }
      value = newList;
    } else if (value.getClass().isArray()) {
      int l = Array.getLength(value);
      EnumValueDescriptor[] newArr = new EnumValueDescriptor[l];
      for (int i = 0; i < l; i++) {
        int enumValue = ((Number) Array.get(value, i)).intValue();
        newArr[i] = enumType.findValueByNumberCreatingIfUnknown(enumValue);
      }
      value = newArr;
    }
    return value;
  }

  /** RegisterDescriptor registers the contents of a protocol buffer `FileDescriptor`. */
  public void registerDescriptor(FileDescriptor fileDesc) {
    FileDescription fd = pbdb.registerDescriptor(fileDesc);
    registerAllTypes(fd);
  }

  /** RegisterMessage registers a protocol buffer message and its dependencies. */
  public void registerMessage(Message message) {
    FileDescription fd = pbdb.registerMessage(message);
    fieldTypeCache.remove(typeNameFromMessage(message));
    registerAllTypes(fd);
  }

  @Override
  public void registerType(org.projectnessie.cel.common.types.ref.Type... types) {
    for (org.projectnessie.cel.common.types.ref.Type t : types) {
      revTypeMap.put(t.typeName(), t);
    }
    // TODO: generate an error when the type name is registered more than once.
  }

  /**
   * NativeToValue converts various "native" types to ref.Val with this specific implementation
   * providing support for custom proto-based types.
   *
   * <p>This method should be the inverse of ref.Val.ConvertToNative.
   */
  @Override
  public Val nativeToValue(Object value) {
    if (value instanceof Message v) {
      String typeName = typeNameFromMessage(v);
      if (typeName.isEmpty()) {
        return anyWithEmptyType();
      }
      PbTypeDescription td = pbdb.describeType(typeName);
      if (td == null) {
        return unknownType(typeName);
      }
      Object unwrapped = td.maybeUnwrap(pbdb, v);
      if (unwrapped != null) {
        Object further = maybeUnwrapValue(unwrapped);
        if (further != unwrapped) {
          return nativeToValue(further);
        }

        Val val = maybeNativeToValue(this, unwrapped);
        if (val != null) {
          return val;
        }
        if (unwrapped instanceof Message) {
          v = (Message) unwrapped;
        }
      }
      Val typeVal = findIdent(typeName);
      if (typeVal == null) {
        return unknownType(typeName);
      }

      return newObject(this, td, (TypeT) typeVal, v);
    }

    Val val = DefaultTypeAdapter.nativeToValue(pbdb, this, value);
    if (val != null) {
      return val;
    }

    return unsupportedRefValConversionErr(value);
  }

  @Override
  public Val nativeToValue(boolean value) {
    return TypeAdapterSupport.nativeToValue(value);
  }

  @Override
  public Val nativeToValue(byte value) {
    return TypeAdapterSupport.nativeToValue(value);
  }

  @Override
  public Val nativeToValue(short value) {
    return TypeAdapterSupport.nativeToValue(value);
  }

  @Override
  public Val nativeToValue(int value) {
    return TypeAdapterSupport.nativeToValue(value);
  }

  @Override
  public Val nativeToValue(long value) {
    return TypeAdapterSupport.nativeToValue(value);
  }

  @Override
  public Val nativeToValue(float value) {
    return TypeAdapterSupport.nativeToValue(value);
  }

  @Override
  public Val nativeToValue(double value) {
    return TypeAdapterSupport.nativeToValue(value);
  }

  void registerAllTypes(FileDescription fd) {
    for (String typeName : fd.getTypeNames()) {
      registerType(newObjectTypeValue(typeName));
    }
  }

  @Override
  public String toString() {
    return "ProtoTypeRegistry{" + "revTypeMap.size=" + revTypeMap.size() + ", pbdb=" + pbdb + '}';
  }

  private static final class ProtoFieldType extends FieldType {
    private final FieldType objectPresenceFieldType;
    private final FieldType objectGetterFieldType;

    private ProtoFieldType(
        Type type,
        FieldTester isSet,
        FieldGetter getFrom,
        FieldTester generatedTester,
        FieldGetter objectGetter) {
      super(type, isSet, getFrom);
      this.objectPresenceFieldType =
          generatedTester != null ? new FieldType(type, isSet, getFrom) : null;
      this.objectGetterFieldType =
          objectGetter != null ? new FieldType(type, isSet, objectGetter) : null;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProtoTypeRegistry that = (ProtoTypeRegistry) o;
    return Objects.equals(revTypeMap, that.revTypeMap) && Objects.equals(pbdb, that.pbdb);
  }

  @Override
  public int hashCode() {
    return Objects.hash(revTypeMap, pbdb);
  }
}
