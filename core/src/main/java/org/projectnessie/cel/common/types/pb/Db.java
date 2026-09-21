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
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Mutable descriptor database used by the Protocol Buffer checker and runtime adapters.
 *
 * <p>The database indexes files, messages, enum constants, and extensions by their protobuf names.
 * It is low-level registry infrastructure; applications normally configure a {@link
 * ProtoTypeRegistry} instead.
 *
 * <p>A database owns its mutable description and index state. {@link #copy()} duplicates that state
 * while sharing immutable protobuf descriptors. Generated-versus-dynamic message representation
 * bindings are independent between copies.
 *
 * <p>Registration mutates the database and is not a concurrent operation. Complete registration
 * before sharing a database with evaluation callers. Do not mutate {@link #defaultDb}; use {@link
 * #newDb()} to obtain an isolated database initialized with the built-in well-known types.
 */
public final class Db {

  private final Map<String, FileDescription> revFileDescriptorMap;

  /** files contains the deduped set of FileDescriptions whose types are contained in the pb.Db. */
  private final List<FileDescription> files;

  private volatile ExtensionRegistry extensionRegistry;

  /**
   * Shared descriptor database containing Protocol Buffer well-known types.
   *
   * <p>This instance is registry infrastructure and must be treated as read-only.
   */
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
    defaultDb.registerMessage(FieldMask.getDefaultInstance());
    defaultDb.registerMessage(Timestamp.getDefaultInstance());
    defaultDb.registerMessage(Value.getDefaultInstance());
    defaultDb.registerMessage(BoolValue.getDefaultInstance());
  }

  private Db(Map<String, FileDescription> revFileDescriptorMap, List<FileDescription> files) {
    this.revFileDescriptorMap = revFileDescriptorMap;
    this.files = files;
  }

  /**
   * Creates an isolated descriptor database initialized with the built-in well-known types.
   *
   * @return a mutable database independent of {@link #defaultDb}
   */
  public static Db newDb() {
    return defaultDb.copy();
  }

  /**
   * Copies this database with independent mutable indexes and type descriptions.
   *
   * <p>Immutable protobuf descriptors and enum/field descriptions may be shared. Changes to message
   * representation bindings or subsequent registrations in either database are isolated from the
   * other.
   *
   * @return an independent mutable copy
   */
  public Db copy() {
    Map<FileDescription, FileDescription> copiedDescriptions = new IdentityHashMap<>();
    Map<String, FileDescription> revFileDescriptorMap =
        new HashMap<>(this.revFileDescriptorMap.size());
    this.revFileDescriptorMap.forEach(
        (name, description) ->
            revFileDescriptorMap.put(
                name, copiedDescriptions.computeIfAbsent(description, FileDescription::copy)));
    List<FileDescription> files = new ArrayList<>(this.files.size());
    this.files.forEach(
        description ->
            files.add(copiedDescriptions.computeIfAbsent(description, FileDescription::copy)));
    return new Db(revFileDescriptorMap, files);
  }

  /**
   * Returns the registered file descriptions.
   *
   * <p>The returned list is live and registry-owned. Callers must treat it as read-only.
   *
   * @return the live list in registration order
   */
  public List<FileDescription> fileDescriptions() {
    return files;
  }

  /**
   * Registers the messages, enum constants, and extensions declared by one protobuf file.
   *
   * <p>Registration of the same file is idempotent. Dependencies are not traversed by this
   * low-level method; {@link ProtoTypeRegistry#registerMessage(Message)} registers a message's
   * complete descriptor dependency set.
   *
   * @param fileDesc descriptor to register
   * @return the existing or newly created description for that file
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
    for (String extensionName : fd.getExtensionNames()) {
      revFileDescriptorMap.put(extensionName, fd);
    }
    for (String msgTypeName : fd.getTypeNames()) {
      revFileDescriptorMap.put(msgTypeName, fd);
    }
    revFileDescriptorMap.put(path, fd);
    extensionRegistry = null;

    // Return the specific file descriptor registered.
    files.add(fd);
    return fd;
  }

  private String path(FileDescriptor fileDesc) {
    return fileDesc.getPackage() + ':' + fileDesc.getFullName();
  }

  /**
   * Registers the definitions in a message's file and binds its generated or dynamic Java
   * representation.
   *
   * <p>Dependencies are not traversed by this low-level method.
   *
   * @param message representative message instance
   * @return the description for the message's file
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
   * Looks up a protobuf enum constant by qualified name.
   *
   * @param enumName qualified enum-constant name, optionally beginning with a dot
   * @return the description, or {@code null} if the constant is not registered
   */
  public EnumValueDescription describeEnum(String enumName) {
    enumName = sanitizeProtoName(enumName);
    FileDescription fd = revFileDescriptorMap.get(enumName);
    return fd != null ? fd.getEnumDescription(enumName) : null;
  }

  /**
   * Looks up a protobuf message type by qualified name.
   *
   * @param typeName qualified message name, optionally beginning with a dot
   * @return the description, or {@code null} if the type is not registered
   */
  public PbTypeDescription describeType(String typeName) {
    typeName = sanitizeProtoName(typeName);
    FileDescription fd = revFileDescriptorMap.get(typeName);
    return fd != null ? fd.getTypeDescription(typeName) : null;
  }

  /**
   * Looks up an extension and verifies that it extends the requested message type.
   *
   * @param messageType qualified extended-message name
   * @param extensionName qualified extension name
   * @return the extension description, or {@code null} if either name does not match
   */
  public FieldDescription describeExtension(String messageType, String extensionName) {
    extensionName = sanitizeProtoName(extensionName);
    FieldDescription extension = describeExtension(extensionName);
    if (extension == null
        || !sanitizeProtoName(messageType)
            .equals(extension.descriptor().getContainingType().getFullName())) {
      return null;
    }
    return extension;
  }

  /**
   * Looks up a protobuf extension by qualified name.
   *
   * @param extensionName qualified extension name, optionally beginning with a dot
   * @return the extension description, or {@code null} if it is not registered
   */
  public FieldDescription describeExtension(String extensionName) {
    extensionName = sanitizeProtoName(extensionName);
    FileDescription fd = revFileDescriptorMap.get(extensionName);
    return fd != null ? fd.getExtensionDescription(extensionName) : null;
  }

  ExtensionRegistry extensionRegistry() {
    ExtensionRegistry registry = extensionRegistry;
    if (registry != null) {
      return registry;
    }
    synchronized (this) {
      registry = extensionRegistry;
      if (registry == null) {
        registry = ExtensionRegistry.newInstance();
        for (FileDescription file : files) {
          for (FieldDescription extension : file.getExtensionDescriptions()) {
            if (extension.isMessage()) {
              registry.add(extension.descriptor(), extension.zero());
            } else {
              registry.add(extension.descriptor());
            }
          }
        }
        extensionRegistry = registry;
      }
      return registry;
    }
  }

  /**
   * Collects the descriptor for a message's file and all transitive file dependencies.
   *
   * @param message message whose descriptor graph to traverse
   * @return a newly allocated set in dependency-discovery order
   */
  public static Set<FileDescriptor> collectFileDescriptorSet(Message message) {
    Set<FileDescriptor> descriptors = new LinkedHashSet<>();
    Deque<FileDescriptor> pending = new ArrayDeque<>();
    pending.add(message.getDescriptorForType().getFile());
    while (!pending.isEmpty()) {
      FileDescriptor descriptor = pending.removeFirst();
      if (descriptors.add(descriptor)) {
        pending.addAll(descriptor.getDependencies());
      }
    }
    return descriptors;
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
