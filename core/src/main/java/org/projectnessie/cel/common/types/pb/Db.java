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

import static org.projectnessie.cel.common.types.pb.FileDescription.newFileDescription;
import static org.projectnessie.cel.common.types.pb.FileDescription.sanitizeProtoName;

import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Db maps from file / message / enum name to file description.
 *
 * <p>Each Db is isolated from each other, and while information about protobuf descriptors may be
 * fetched from the global protobuf registry, no descriptors are added to this registry, else the
 * isolation guarantees of the Db object would be violated.
 */
public class Db {

  private final Map<String, FileDescription> revFileDescriptorMap;
  /** files contains the deduped set of FileDescriptions whose types are contained in the pb.Db. */
  private final List<FileDescription> files;

  /** DefaultDb used at evaluation time or unless overridden at check time. */
  public static final Db defaultDb = new Db(new HashMap<>(), new ArrayList<>());

  static {
    // Describe well-known types to ensure they can always be resolved by the check and interpret
    // execution phases.
    //
    // The following subset of message types is enough to ensure that all well-known types can
    // resolved in the runtime, since describing the value results in describing the whole file
    // where the message is declared.
    defaultDb.registerMessage(Any.getDefaultInstance());
    defaultDb.registerMessage(Duration.getDefaultInstance());
    defaultDb.registerMessage(Empty.getDefaultInstance());
    defaultDb.registerMessage(Timestamp.getDefaultInstance());
    defaultDb.registerMessage(Value.getDefaultInstance());
    defaultDb.registerMessage(BoolValue.getDefaultInstance());
  }

  private Db(Map<String, FileDescription> revFileDescriptorMap, List<FileDescription> files) {
    this.revFileDescriptorMap = revFileDescriptorMap;
    this.files = files;
  }

  /** NewDb creates a new `pb.Db` with an empty type name to file description map. */
  public static Db newDb() {
    // The FileDescription objects in the default db contain lazily initialized TypeDescription
    // values which may point to the state contained in the DefaultDb irrespective of this shallow
    // copy; however, the type graph for a field is idempotently computed, and is guaranteed to
    // only be initialized once thanks to atomic values within the TypeDescription objects, so it
    // is safe to share these values across instances.
    return defaultDb.copy();
  }

  /** Copy creates a copy of the current database with its own internal descriptor mapping. */
  public Db copy() {
    Map<String, FileDescription> revFileDescriptorMap = new HashMap<>(this.revFileDescriptorMap);
    List<FileDescription> files = new ArrayList<>(this.files);
    return new Db(revFileDescriptorMap, files);
  }

  /** FileDescriptions returns the set of file descriptions associated with this db. */
  public List<FileDescription> fileDescriptions() {
    return files;
  }

  /**
   * RegisterDescriptor produces a `FileDescription` from a `FileDescriptor` and registers the
   * message and enum types into the `pb.Db`.
   */
  public FileDescription registerDescriptor(FileDescriptor fileDesc) {
    String path = path(fileDesc);
    FileDescription fd = revFileDescriptorMap.get(path);
    if (fd != null) {
      return fd;
    }
    // Make sure to search the global registry to see if a protoreflect.FileDescriptor for
    // the file specified has been linked into the binary. If so, use the copy of the descriptor
    // from the global cache.
    //
    // Note: Proto reflection relies on descriptor values being object equal rather than object
    // equivalence. This choice means that a FieldDescriptor generated from a FileDescriptorProto
    // will be incompatible with the FieldDescriptor in the global registry and any message created
    // from that global registry.
    // TODO is there something like this in Java ??
    //    globalFD := protoregistry.GlobalFiles.FindFileByPath(fileDesc.Path())
    //    if err == nil {
    //      fileDesc = globalFD
    //    }
    fd = newFileDescription(fileDesc);
    for (String enumValName : fd.getEnumNames()) {
      revFileDescriptorMap.put(enumValName, fd);
    }
    for (String msgTypeName : fd.getTypeNames()) {
      revFileDescriptorMap.put(msgTypeName, fd);
    }
    revFileDescriptorMap.put(path, fd);

    // Return the specific file descriptor registered.
    files.add(fd);
    return fd;
  }

  private String path(FileDescriptor fileDesc) {
    return fileDesc.getPackage() + ':' + fileDesc.getFullName();
  }

  /**
   * RegisterMessage produces a `FileDescription` from a `message` and registers the message and all
   * other definitions within the message file into the `pb.Db`.
   */
  public FileDescription registerMessage(Message message) {
    Descriptor msgDesc = message.getDescriptorForType();
    String msgName = msgDesc.getFullName();
    String typeName = sanitizeProtoName(msgName);
    FileDescription fd = revFileDescriptorMap.get(typeName);
    if (fd == null) {
      fd = registerDescriptor(msgDesc.getFile());
      revFileDescriptorMap.put(typeName, fd);
    }
    describeType(typeName).updateReflectType(message);
    return fd;
  }

  /**
   * DescribeEnum takes a qualified enum name and returns an `EnumDescription` if it exists in the
   * `pb.Db`.
   */
  public EnumValueDescription describeEnum(String enumName) {
    enumName = sanitizeProtoName(enumName);
    FileDescription fd = revFileDescriptorMap.get(enumName);
    return fd != null ? fd.getEnumDescription(enumName) : null;
  }

  /** DescribeType returns a `TypeDescription` for the `typeName` if it exists in the `pb.Db`. */
  public TypeDescription describeType(String typeName) {
    typeName = sanitizeProtoName(typeName);
    FileDescription fd = revFileDescriptorMap.get(typeName);
    return fd != null ? fd.getTypeDescription(typeName) : null;
  }

  /**
   * CollectFileDescriptorSet builds a file descriptor set associated with the file where the input
   * message is declared.
   */
  public static Set<FileDescriptor> collectFileDescriptorSet(Message message) {
    Set<FileDescriptor> fdMap = new LinkedHashSet<>();
    Descriptor messageDesc = message.getDescriptorForType();
    FileDescriptor messageFile = messageDesc.getFile();
    fdMap.add(messageFile);
    fdMap.addAll(messageFile.getPublicDependencies());

    //    parentFile = message.ProtoReflect().Descriptor().ParentFile()
    //    fdMap[parentFile.Path()] = parentFile
    //    // Initialize list of dependencies
    //    deps := make([]protoreflect.FileImport, parentFile.Imports().Len())
    //    for i := 0; i < parentFile.Imports().Len(); i++ {
    //      deps[i] = parentFile.Imports().Get(i)
    //    }
    //    // Expand list for new dependencies
    //    for i := 0; i < len(deps); i++ {
    //      dep := deps[i]
    //      if _, found := fdMap[dep.Path()]; found {
    //        continue
    //      }
    //      fdMap[dep.Path()] = dep.FileDescriptor
    //      for j := 0; j < dep.FileDescriptor.Imports().Len(); j++ {
    //        deps = append(deps, dep.FileDescriptor.Imports().Get(j))
    //      }
    //    }
    return fdMap;
  }

  @Override
  public String toString() {
    return "Db{"
        + "revFileDescriptorMap.size="
        + revFileDescriptorMap.size()
        + ", files="
        + files.size()
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
    Db db = (Db) o;
    return Objects.equals(revFileDescriptorMap, db.revFileDescriptorMap)
        && Objects.equals(files, db.files);
  }

  @Override
  public int hashCode() {
    return Objects.hash(revFileDescriptorMap, files);
  }
}
