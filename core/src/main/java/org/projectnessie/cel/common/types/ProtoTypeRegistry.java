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

import static org.projectnessie.cel.common.types.BoolT.BoolType;
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
import static org.projectnessie.cel.common.types.ObjectT.newObject;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.TimestampT.TimestampType;
import static org.projectnessie.cel.common.types.TypeT.TypeType;
import static org.projectnessie.cel.common.types.TypeT.newObjectTypeValue;
import static org.projectnessie.cel.common.types.UintT.UintType;
import static org.projectnessie.cel.common.types.pb.Db.newDb;
import static org.projectnessie.cel.common.types.pb.TypeDescription.typeNameFromMessage;

import com.google.api.expr.v1alpha1.Type;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
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
import org.projectnessie.cel.common.types.pb.Db;
import org.projectnessie.cel.common.types.pb.EnumValueDescription;
import org.projectnessie.cel.common.types.pb.FieldDescription;
import org.projectnessie.cel.common.types.pb.FileDescription;
import org.projectnessie.cel.common.types.pb.TypeDescription;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;

public final class ProtoTypeRegistry implements TypeRegistry {
  private final Map<String, org.projectnessie.cel.common.types.ref.Type> revTypeMap;
  private final Db pbdb;

  private ProtoTypeRegistry(
      Map<String, org.projectnessie.cel.common.types.ref.Type> revTypeMap, Db pbdb) {
    this.revTypeMap = revTypeMap;
    this.pbdb = pbdb;
  }

  /**
   * NewRegistry accepts a list of proto message instances and returns a type provider which can
   * create new instances of the provided message or any message that proto depends upon in its
   * FileDescriptor.
   */
  public static TypeRegistry newRegistry(Message... types) {
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
    for (Message msgType : types) {
      p.registerMessage(msgType);
    }
    return p;
  }

  /** NewEmptyRegistry returns a registry which is completely unconfigured. */
  public static TypeRegistry newEmptyRegistry() {
    return new ProtoTypeRegistry(new HashMap<>(), newDb());
  }

  /**
   * Copy implements the ref.TypeRegistry interface method which copies the current state of the
   * registry into its own memory space.
   */
  @Override
  public TypeRegistry copy() {
    return new ProtoTypeRegistry(new HashMap<>(this.revTypeMap), pbdb.copy());
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
    TypeDescription msgType = pbdb.describeType(messageType);
    if (msgType == null) {
      return null;
    }
    FieldDescription field = msgType.fieldByName(fieldName);
    if (field == null) {
      return null;
    }
    return new FieldType(field.checkedType(), field::hasField, field::getField);
  }

  @Override
  public Val findIdent(String identName) {
    org.projectnessie.cel.common.types.ref.Type t = revTypeMap.get(identName);
    if (t != null) {
      return (Val) t;
    }
    EnumValueDescription enumVal = pbdb.describeEnum(identName);
    if (enumVal != null) {
      return intOf(enumVal.value());
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
    TypeDescription td = pbdb.describeType(typeName);
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

  private Val newValueSetFields(Map<String, Val> fields, TypeDescription td, Builder builder) {
    Map<String, FieldDescription> fieldMap = td.fieldMap();
    for (Entry<String, Val> nv : fields.entrySet()) {
      String name = nv.getKey();
      FieldDescription field = fieldMap.get(name);
      if (field == null) {
        return noSuchField(name);
      }

      // TODO resolve inefficiency for maps: first converted from a MapT to a native Java map and
      //  then to a protobuf struct. The intermediate step (the Java map) could be omitted.

      Object value = nv.getValue().convertToNative(field.reflectType());
      if (value.getClass().isArray()) {
        value = Arrays.asList((Object[]) value);
      }

      FieldDescriptor pbDesc = field.descriptor();

      if (pbDesc.getJavaType() == JavaType.ENUM) {
        value = intToProtoEnumValues(field, value);
      }

      if (pbDesc.isMapField()) {
        value = toProtoMapStructure(pbDesc, value);
      }

      builder.setField(pbDesc, value);
    }
    return null;
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
        if (valueFieldType == WireFormat.FieldType.MESSAGE && !(v instanceof Message)) {
          v = nativeToValue(v).convertToNative(Value.class);
        }

        MapEntry newEntry =
            MapEntry.newDefaultInstance(mesgType, keyFieldType, k, valueFieldType, v);
        newList.add(newEntry);
      }
      value = newList;
    }

    return value;
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
    } else if (value instanceof List) {
      List list = (List) value;
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

  @Override
  public void registerDescriptor(FileDescriptor fileDesc) {
    FileDescription fd = pbdb.registerDescriptor(fileDesc);
    registerAllTypes(fd);
  }

  @Override
  public void registerMessage(Message message) {
    FileDescription fd = pbdb.registerMessage(message);
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
    if (value instanceof Message) {
      Message v = (Message) value;
      String typeName = typeNameFromMessage(v);
      if (typeName.isEmpty()) {
        return anyWithEmptyType();
      }
      TypeDescription td = pbdb.describeType(typeName);
      if (td == null) {
        return unknownType(typeName);
      }
      Object unwrapped = td.maybeUnwrap(pbdb, v);
      if (unwrapped != null) {
        Object further = DefaultTypeAdapter.maybeUnwrapValue(unwrapped);
        if (further != unwrapped) {
          return nativeToValue(further);
        }

        Val val = DefaultTypeAdapter.maybeNativeToValue(this, unwrapped);
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

  void registerAllTypes(FileDescription fd) {
    for (String typeName : fd.getTypeNames()) {
      registerType(newObjectTypeValue(typeName));
    }
  }

  @Override
  public String toString() {
    return "ProtoTypeRegistry{" + "revTypeMap.size=" + revTypeMap.size() + ", pbdb=" + pbdb + '}';
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
