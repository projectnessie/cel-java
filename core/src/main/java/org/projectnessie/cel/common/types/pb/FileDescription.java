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

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Flattened metadata for the messages, enum constants, and extensions declared by one protobuf
 * file.
 *
 * <p>Nested declarations are indexed by fully qualified protobuf name. Instances are normally
 * created and owned by {@link Db}; applications normally interact through {@link
 * ProtoTypeRegistry}.
 */
public final class FileDescription {

  private final Map<String, PbTypeDescription> types;
  private final Map<String, EnumValueDescription> enums;
  private final Map<String, FieldDescription> extensions;

  private FileDescription(
      Map<String, PbTypeDescription> types,
      Map<String, EnumValueDescription> enums,
      Map<String, FieldDescription> extensions) {
    this.types = types;
    this.enums = enums;
    this.extensions = extensions;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FileDescription that = (FileDescription) o;
    return Objects.equals(types, that.types)
        && Objects.equals(enums, that.enums)
        && Objects.equals(extensions, that.extensions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(types, enums, extensions);
  }

  /**
   * Describes all message types, enum constants, and extensions declared at any scope in a file.
   *
   * @param fileDesc protobuf file descriptor to inspect
   * @return a new flattened description
   */
  public static FileDescription newFileDescription(FileDescriptor fileDesc) {
    FileMetadata metadata = FileMetadata.collectFileMetadata(fileDesc);
    Map<String, EnumValueDescription> enums = new HashMap<>();
    metadata.enumValues.forEach(
        (name, enumVal) ->
            enums.put(name, EnumValueDescription.newEnumValueDescription(name, enumVal)));
    Map<String, PbTypeDescription> types = new HashMap<>();
    metadata.msgTypes.forEach(
        (name, msgType) -> types.put(name, PbTypeDescription.newTypeDescription(name, msgType)));
    Map<String, FieldDescription> extensions = new HashMap<>();
    metadata.extensions.forEach(
        (name, extension) -> extensions.put(name, FieldDescription.newFieldDescription(extension)));
    return new FileDescription(types, enums, extensions);
  }

  /**
   * Returns a file description whose mutable containers and type representation bindings are
   * independent of this description.
   */
  FileDescription copy() {
    Map<String, PbTypeDescription> copiedTypes = new HashMap<>(types.size());
    types.forEach((name, type) -> copiedTypes.put(name, type.copy()));
    return new FileDescription(copiedTypes, new HashMap<>(enums), new HashMap<>(extensions));
  }

  /**
   * Looks up an enum constant declared in the file.
   *
   * @param enumName qualified enum-constant name, optionally beginning with a dot
   * @return its description, or {@code null} if the qualified name is unknown
   */
  public EnumValueDescription getEnumDescription(String enumName) {
    return enums.get(sanitizeProtoName(enumName));
  }

  /** Returns a new array containing all qualified enum-constant names in unspecified order. */
  public String[] getEnumNames() {
    return enums.keySet().toArray(new String[0]);
  }

  /**
   * Looks up an extension declared in the file.
   *
   * @param extensionName qualified extension name, optionally beginning with a dot
   * @return its field description, or {@code null} if the qualified name is unknown
   */
  public FieldDescription getExtensionDescription(String extensionName) {
    return extensions.get(sanitizeProtoName(extensionName));
  }

  /** Returns a new array containing all qualified extension names in unspecified order. */
  public String[] getExtensionNames() {
    return extensions.keySet().toArray(new String[0]);
  }

  /**
   * Returns all extension descriptions.
   *
   * <p>The iterable is a live, registry-owned view and must be treated as read-only.
   */
  public Iterable<FieldDescription> getExtensionDescriptions() {
    return extensions.values();
  }

  /**
   * Looks up a message type declared in the file.
   *
   * @param typeName qualified message name, optionally beginning with a dot
   * @return its description, or {@code null} if the qualified name is unknown
   */
  public PbTypeDescription getTypeDescription(String typeName) {
    return types.get(sanitizeProtoName(typeName));
  }

  /** Returns a new array containing all qualified message type names in unspecified order. */
  public String[] getTypeNames() {
    return types.keySet().toArray(new String[0]);
  }

  /** sanitizeProtoName strips the leading '.' from the proto message name. */
  static String sanitizeProtoName(String name) {
    if (name != null && !name.isEmpty() && name.charAt(0) == '.') {
      return name.substring(1);
    }
    return name;
  }

  /** fileMetadata is a flattened view of message types and enum values within a file descriptor. */
  static final class FileMetadata {
    /** msgTypes maps from fully-qualified message name to descriptor. */
    final Map<String, Descriptor> msgTypes;

    /** enumValues maps from fully-qualified enum value to enum value descriptor. */
    final Map<String, EnumValueDescriptor> enumValues;

    /** extensions maps from fully-qualified extension name to field descriptor. */
    final Map<String, FieldDescriptor> extensions;

    // TODO: support enum type definitions for use in future type-check enhancements.

    private FileMetadata(
        Map<String, Descriptor> msgTypes,
        Map<String, EnumValueDescriptor> enumValues,
        Map<String, FieldDescriptor> extensions) {
      this.msgTypes = msgTypes;
      this.enumValues = enumValues;
      this.extensions = extensions;
    }

    /**
     * collectFileMetadata traverses the proto file object graph to collect message types and enum
     * values and index them by their fully qualified names.
     */
    static FileMetadata collectFileMetadata(FileDescriptor fileDesc) {
      Map<String, Descriptor> msgTypes = new HashMap<>();
      Map<String, EnumValueDescriptor> enumValues = new HashMap<>();
      Map<String, FieldDescriptor> extensions = new HashMap<>();

      collectMsgTypes(fileDesc.getMessageTypes(), msgTypes, enumValues, extensions);
      collectEnumValues(fileDesc.getEnumTypes(), enumValues);
      collectExtensions(fileDesc.getExtensions(), extensions);
      return new FileMetadata(msgTypes, enumValues, extensions);
    }

    /**
     * collectMsgTypes recursively collects messages, nested messages, and nested enums into a map
     * of fully qualified protobuf names to descriptors.
     */
    private static void collectMsgTypes(
        List<Descriptor> msgTypes,
        Map<String, Descriptor> msgTypeMap,
        Map<String, EnumValueDescriptor> enumValueMap,
        Map<String, FieldDescriptor> extensionMap) {
      for (Descriptor msgType : msgTypes) {
        msgTypeMap.put(msgType.getFullName(), msgType);
        List<Descriptor> nestedMsgTypes = msgType.getNestedTypes();
        if (!nestedMsgTypes.isEmpty()) {
          collectMsgTypes(nestedMsgTypes, msgTypeMap, enumValueMap, extensionMap);
        }
        List<EnumDescriptor> nestedEnumTypes = msgType.getEnumTypes();
        if (!nestedEnumTypes.isEmpty()) {
          collectEnumValues(nestedEnumTypes, enumValueMap);
        }
        collectExtensions(msgType.getExtensions(), extensionMap);
      }
    }

    private static void collectExtensions(
        List<FieldDescriptor> extensions, Map<String, FieldDescriptor> extensionMap) {
      for (FieldDescriptor extension : extensions) {
        extensionMap.put(extension.getFullName(), extension);
      }
    }

    /** collectEnumValues accumulates the enum values within an enum declaration. */
    private static void collectEnumValues(
        List<EnumDescriptor> enumTypes, Map<String, EnumValueDescriptor> enumValueMap) {
      for (EnumDescriptor enumType : enumTypes) {
        List<EnumValueDescriptor> enumTypeValues = enumType.getValues();
        for (EnumValueDescriptor enumValue : enumTypeValues) {
          String enumValueName =
              String.format("%s.%s", enumType.getFullName(), enumValue.getName());
          enumValueMap.put(enumValueName, enumValue);
        }
      }
    }
  }
}
