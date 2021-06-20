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
import com.google.protobuf.Descriptors.FileDescriptor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** FileDescription holds a map of all types and enum values declared within a proto file. */
public final class FileDescription {

  private final Map<String, PbTypeDescription> types;
  private final Map<String, EnumValueDescription> enums;

  private FileDescription(
      Map<String, PbTypeDescription> types, Map<String, EnumValueDescription> enums) {
    this.types = types;
    this.enums = enums;
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
    return Objects.equals(types, that.types) && Objects.equals(enums, that.enums);
  }

  @Override
  public int hashCode() {
    return Objects.hash(types, enums);
  }

  /**
   * NewFileDescription returns a FileDescription instance with a complete listing of all the
   * message types and enum values declared within any scope in the file.
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
    return new FileDescription(types, enums);
  }

  /**
   * GetEnumDescription returns an EnumDescription for a qualified enum value name declared within
   * the .proto file.
   */
  public EnumValueDescription getEnumDescription(String enumName) {
    return enums.get(sanitizeProtoName(enumName));
  }

  /** GetEnumNames returns the string names of all enum values in the file. */
  public String[] getEnumNames() {
    return enums.keySet().toArray(new String[0]);
  }

  /**
   * GetTypeDescription returns a TypeDescription for a qualified protobuf message type name
   * declared within the .proto file.
   */
  public PbTypeDescription getTypeDescription(String typeName) {
    return types.get(sanitizeProtoName(typeName));
  }

  /** GetTypeNames returns the list of all type names contained within the file. */
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
  static class FileMetadata {
    /** msgTypes maps from fully-qualified message name to descriptor. */
    final Map<String, Descriptor> msgTypes;
    /** enumValues maps from fully-qualified enum value to enum value descriptor. */
    final Map<String, EnumValueDescriptor> enumValues;
    // TODO: support enum type definitions for use in future type-check enhancements.

    private FileMetadata(
        Map<String, Descriptor> msgTypes, Map<String, EnumValueDescriptor> enumValues) {
      this.msgTypes = msgTypes;
      this.enumValues = enumValues;
    }

    /**
     * collectFileMetadata traverses the proto file object graph to collect message types and enum
     * values and index them by their fully qualified names.
     */
    static FileMetadata collectFileMetadata(FileDescriptor fileDesc) {
      Map<String, Descriptor> msgTypes = new HashMap<>();
      Map<String, EnumValueDescriptor> enumValues = new HashMap<>();

      collectMsgTypes(fileDesc.getMessageTypes(), msgTypes, enumValues);
      collectEnumValues(fileDesc.getEnumTypes(), enumValues);
      return new FileMetadata(msgTypes, enumValues);
    }

    /**
     * collectMsgTypes recursively collects messages, nested messages, and nested enums into a map
     * of fully qualified protobuf names to descriptors.
     */
    private static void collectMsgTypes(
        List<Descriptor> msgTypes,
        Map<String, Descriptor> msgTypeMap,
        Map<String, EnumValueDescriptor> enumValueMap) {
      for (Descriptor msgType : msgTypes) {
        msgTypeMap.put(msgType.getFullName(), msgType);
        List<Descriptor> nestedMsgTypes = msgType.getNestedTypes();
        if (!nestedMsgTypes.isEmpty()) {
          collectMsgTypes(nestedMsgTypes, msgTypeMap, enumValueMap);
        }
        List<EnumDescriptor> nestedEnumTypes = msgType.getEnumTypes();
        if (!nestedEnumTypes.isEmpty()) {
          collectEnumValues(nestedEnumTypes, enumValueMap);
        }
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
