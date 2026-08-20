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
package org.projectnessie.cel.types.jackson;

import static org.projectnessie.cel.common.types.Err.newErr;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.StandardScalarTypeAdapter;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeAdapterSupport;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;

/**
 * A {@link TypeRegistry} that exposes Jackson 2 bean properties to CEL.
 *
 * <p>Use this registry when application inputs are ordinary Java objects described by a Jackson 2
 * {@link ObjectMapper}. Register a class or instance before checking an expression that selects its
 * fields. Runtime adaptation of an object also discovers its type, but that is too late to make the
 * type available to an already running checker.
 *
 * <p>The registry is for reading host objects. It does not construct Jackson-described objects for
 * CEL object literals: {@link #newValue(String, Map)} and {@link #registerType(Type...)} throw
 * {@link UnsupportedOperationException}.
 *
 * <p>Jackson array fields use the same representations as runtime adaptation: {@code byte[]} is CEL
 * bytes; {@code int[]}, {@code long[]}, and {@code double[]} are typed CEL lists; and reference
 * arrays are typed recursively, with {@code Object[]} and arrays of CEL {@link Val} values using
 * dynamic elements. {@code boolean[]}, {@code short[]}, {@code char[]}, and {@code float[]} are not
 * supported and are rejected during registration. Inferred {@code long[]} fields are signed {@code
 * list<int>} values.
 *
 * <p>Java {@link java.util.Collection} fields become CEL lists. Java {@link Map} fields become CEL
 * maps when their keys map to CEL {@code bool}, {@code int}, {@code uint}, or {@code string}.
 * Unsupported map-key types and container shapes for which Jackson supplies no element, key, or
 * value type are rejected during registration. {@link java.util.Optional} fields use the contained
 * checked type. A null property is absent for CEL presence testing and is read as CEL null.
 *
 * <p>The configured factories snapshot supported Jackson bean-property configuration. The registry
 * supports direct and mutually recursive object schemas, and publishes a newly discovered schema
 * graph only after every type in that graph has been initialized successfully. Failed discovery
 * does not publish a partial graph and can be retried. Discovery is serialized, so a registry can
 * be reused by concurrent compilation and evaluation callers.
 *
 * <p>{@link #copy()} creates independent registration state and another mapper snapshot. Types
 * registered later in one copy are not visible in the other.
 */
public final class JacksonRegistry implements TypeRegistry, StandardScalarTypeAdapter {
  final ObjectMapper objectMapper;
  private final SerializerProvider serializationProvider;
  private final TypeFactory typeFactory;
  private final Map<Class<?>, JacksonTypeDescription> knownTypes = new ConcurrentHashMap<>();
  private volatile Map<String, JacksonTypeDescription> knownTypesByName = Map.of();

  private final Map<Class<?>, JacksonEnumDescription> enumMap = new ConcurrentHashMap<>();
  private final Map<String, JacksonEnumValue> enumValues = new ConcurrentHashMap<>();

  private DiscoveryTransaction activeDiscovery;

  private JacksonRegistry() {
    this(new ObjectMapper());
  }

  private JacksonRegistry(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.serializationProvider = objectMapper.getSerializerProviderInstance();
    this.typeFactory = objectMapper.getTypeFactory();
  }

  /**
   * Creates a registry with default Jackson 2 bean-property configuration.
   *
   * @return a distinct registry instance
   */
  public static TypeRegistry newRegistry() {
    return new JacksonRegistry();
  }

  /**
   * Creates a registry from a snapshot of the supplied Jackson 2 mapper configuration.
   *
   * <p>The registry uses supported Jackson bean-property discovery, including naming strategies,
   * mix-ins, visibility rules, and modules that modify ordinary bean properties. It does not
   * promise to model arbitrary custom serializer output as CEL fields.
   *
   * <p>This method calls {@link ObjectMapper#copy()}. Later changes to {@code objectMapper} do not
   * affect the registry. Custom Jackson extension objects that Jackson itself shares across mapper
   * copies must not be mutated after construction.
   *
   * @param objectMapper the configured caller-owned mapper to snapshot
   * @return a distinct registry instance that owns the mapper snapshot
   * @throws NullPointerException if {@code objectMapper} is null
   */
  public static TypeRegistry newRegistry(ObjectMapper objectMapper) {
    return new JacksonRegistry(Objects.requireNonNull(objectMapper, "objectMapper").copy());
  }

  /**
   * Creates an opt-in registry that certifies checked Java aggregate representations.
   *
   * <p>The returned registry accepts the canonical homogeneous representations defined by {@link
   * org.projectnessie.cel.common.types.ref.ExactAggregateTypeAdapter}, recursively. Checked CEL
   * types determine signed versus unsigned {@code long} representation. Aggregate-valued {@link
   * java.util.Optional} fields must be present; an empty optional is a detected contract violation.
   * Null list elements and map values follow their nested checked type; null map keys,
   * CEL-equivalent duplicate keys, incompatible boxed values, and traversed cycles are contract
   * violations. Sources and equality/hash-relevant keys must not be mutated during one evaluation.
   * The default {@link #newRegistry()} deliberately does not acquire this stricter contract. {@link
   * TypeRegistry#copy()} preserves exact mode and registered type state.
   *
   * <p>This exact registry does not certify Jackson 2 scalar field access for native planning.
   * Scalar selectors therefore retain the established evaluator path.
   *
   * @return a distinct registry instance implementing both exact aggregate contracts
   */
  public static TypeRegistry newExactAggregateRegistry() {
    return new ExactJacksonRegistry(new JacksonRegistry());
  }

  /**
   * Creates an exact aggregate registry from a snapshot of the supplied mapper configuration.
   *
   * <p>Mapper ownership and supported property-discovery behavior are the same as for {@link
   * #newRegistry(ObjectMapper)}. Exact aggregate validation is orthogonal to mapper configuration
   * and recursive schema discovery.
   *
   * @param objectMapper the configured caller-owned mapper to snapshot
   * @return a distinct exact aggregate registry that owns the mapper snapshot
   * @throws NullPointerException if {@code objectMapper} is null
   */
  public static TypeRegistry newExactAggregateRegistry(ObjectMapper objectMapper) {
    return new ExactJacksonRegistry(
        new JacksonRegistry(Objects.requireNonNull(objectMapper, "objectMapper").copy()));
  }

  /**
   * Copies mapper configuration and all successfully registered schemas into independent registry
   * state.
   *
   * @return an independently configurable registry
   */
  @Override
  public synchronized TypeRegistry copy() {
    JacksonRegistry copy = new JacksonRegistry(objectMapper.copy());
    knownTypesByName.values().stream()
        .map(JacksonTypeDescription::reflectType)
        .forEach(copy::typeDescription);
    enumMap.keySet().forEach(copy::enumDescription);
    return copy;
  }

  /**
   * Registers a Jackson object type or Java enum.
   *
   * <p>{@code t} may be a {@link Class} or an instance. Object registration recursively discovers
   * bean-property types. Repeated registration is idempotent.
   *
   * <p>Enum classes and instances expose constants under their fully qualified Java names. Their
   * CEL values are integers corresponding to {@link Enum#ordinal()}; Jackson serialization names do
   * not change this CEL representation.
   *
   * @param t the class or representative instance to register
   * @throws NullPointerException if {@code t} is null
   * @throws RuntimeException if Jackson cannot describe the type
   */
  @Override
  public void register(Object t) {
    Class<?> cls = t instanceof Enum<?> ? ((Enum<?>) t).getDeclaringClass() : registeredClass(t);
    if (Enum.class.isAssignableFrom(cls)) {
      enumDescription(cls);
    } else {
      typeDescription(cls);
    }
  }

  /**
   * Jackson registries do not accept CEL runtime type definitions.
   *
   * @param types ignored
   * @throws UnsupportedOperationException always
   */
  @Override
  public void registerType(Type... types) {
    throw new UnsupportedOperationException();
  }

  /**
   * Resolves a previously registered enum constant.
   *
   * @param enumName the fully qualified class and constant name
   * @return the constant's ordinal as a CEL integer, or a CEL error for an unknown name
   */
  @Override
  public Val enumValue(String enumName) {
    JacksonEnumValue enumVal = enumValues.get(enumName);
    if (enumVal == null) {
      return newErr("unknown enum name '%s'", enumName);
    }
    return enumVal.ordinalValue();
  }

  /**
   * Resolves a registered object type or enum constant as a CEL identifier.
   *
   * @param identName fully qualified Java class or enum-constant name
   * @return the CEL type or enum ordinal, or {@code null} if {@code identName} is unknown
   */
  @Override
  public Val findIdent(String identName) {
    JacksonTypeDescription td = knownTypesByName.get(identName);
    if (td != null) {
      return td.type();
    }

    JacksonEnumValue enumVal = enumValues.get(identName);
    if (enumVal != null) {
      return enumVal.ordinalValue();
    }
    return null;
  }

  /**
   * Returns the checked CEL type for a registered Java class name.
   *
   * @param typeName fully qualified Java class name
   * @return the checked type, or {@code null} if the class has not been registered
   */
  @Override
  public com.google.api.expr.v1alpha1.Type findType(String typeName) {
    JacksonTypeDescription td = knownTypesByName.get(typeName);
    if (td == null) {
      return null;
    }
    return td.pbType();
  }

  /**
   * Returns a registered bean property's checked type and accessors.
   *
   * @param messageType fully qualified registered Java class name
   * @param fieldName Jackson-visible property name
   * @return the field metadata, or {@code null} if the type or property is unknown
   */
  @Override
  public FieldType findFieldType(String messageType, String fieldName) {
    JacksonTypeDescription td = knownTypesByName.get(messageType);
    if (td == null) {
      return null;
    }
    return td.fieldType(fieldName);
  }

  /**
   * Jackson-described host objects cannot be constructed by CEL object literals.
   *
   * @param typeName ignored
   * @param fields ignored
   * @throws UnsupportedOperationException always
   */
  @Override
  public Val newValue(String typeName, Map<String, Val> fields) {
    throw new UnsupportedOperationException();
  }

  /**
   * Adapts a Java value to a CEL value.
   *
   * <p>Standard scalar and aggregate values use the common adapter. Java enum values must have been
   * registered and become CEL integers. Other objects are described and registered on first use,
   * then exposed as CEL objects whose properties are read through Jackson.
   *
   * @param value the value to adapt
   * @return the adapted CEL value, or a CEL error for an unregistered enum constant
   * @throws RuntimeException if Jackson cannot describe an object value
   */
  @Override
  public Val nativeToValue(Object value) {
    if (value instanceof Val) {
      return (Val) value;
    }
    Val maybe = TypeAdapterSupport.maybeNativeToValue(this, value);
    if (maybe != null) {
      return maybe;
    }

    if (value instanceof Enum) {
      String fq = JacksonEnumValue.fullyQualifiedName((Enum<?>) value);
      JacksonEnumValue v = enumValues.get(fq);
      if (v == null) {
        return newErr("unknown enum name '%s'", fq);
      }
      return v.ordinalValue();
    }

    try {
      return JacksonObjectT.newObject(this, value, typeDescription(value.getClass()));
    } catch (Exception e) {
      throw new RuntimeException("oops", e);
    }
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

  synchronized JacksonEnumDescription enumDescription(Class<?> clazz) {
    if (!Enum.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException("only enum allowed here");
    }
    while (!clazz.isEnum()) {
      clazz = clazz.getSuperclass();
    }

    JacksonEnumDescription ed = enumMap.get(clazz);
    if (ed != null) {
      return ed;
    }
    JavaType javaType = typeFactory.constructType(clazz);
    ed = new JacksonEnumDescription(javaType);
    ed.buildValues().forEach(v -> enumValues.put(v.fullyQualifiedName(), v));
    enumMap.put(clazz, ed);
    return ed;
  }

  synchronized JacksonTypeDescription typeDescription(Class<?> clazz) {
    if (Enum.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException("enum not allowed here");
    }

    JacksonTypeDescription td = knownTypes.get(clazz);
    if (td != null) {
      return td;
    }

    boolean outermost = activeDiscovery == null;
    if (outermost) {
      activeDiscovery = new DiscoveryTransaction();
    }

    try {
      td = discoverType(clazz);
      if (outermost) {
        commitDiscovery(activeDiscovery);
      }
      return td;
    } catch (RuntimeException | Error e) {
      if (outermost) {
        rollbackDiscovery(activeDiscovery);
      }
      throw e;
    } finally {
      if (outermost) {
        activeDiscovery = null;
      }
    }
  }

  private JacksonTypeDescription discoverType(Class<?> clazz) {
    JavaType javaType = typeFactory.constructType(clazz);
    JacksonTypeDescription typeDesc = new JacksonTypeDescription(javaType);
    knownTypes.put(clazz, typeDesc);
    activeDiscovery.record(clazz, typeDesc);

    try {
      JsonSerializer<Object> ser = serializationProvider.findValueSerializer(clazz);
      typeDesc.initialize(ser, this::typeQuery);
      return typeDesc;
    } catch (JsonMappingException e) {
      throw new RuntimeException(e);
    }
  }

  private void commitDiscovery(DiscoveryTransaction transaction) {
    Map<String, JacksonTypeDescription> committed = new HashMap<>(knownTypesByName);
    for (JacksonTypeDescription typeDesc : transaction.discovered.values()) {
      if (!typeDesc.initialized()) {
        throw new IllegalStateException(
            String.format("Jackson type '%s' was not initialized", typeDesc.name()));
      }
      committed.put(typeDesc.name(), typeDesc);
    }
    knownTypesByName = Map.copyOf(committed);
  }

  private void rollbackDiscovery(DiscoveryTransaction transaction) {
    transaction.discovered.forEach(knownTypes::remove);
  }

  private com.google.api.expr.v1alpha1.Type typeQuery(JavaType javaType) {
    if (javaType.isEnumType()) {
      return enumDescription(javaType.getRawClass()).pbType();
    }
    return typeDescription(javaType.getRawClass()).pbType();
  }

  private static Class<?> registeredClass(Object value) {
    return value instanceof Class<?> ? (Class<?>) value : value.getClass();
  }

  private static final class DiscoveryTransaction {
    private final Map<Class<?>, JacksonTypeDescription> discovered = new LinkedHashMap<>();

    private void record(Class<?> clazz, JacksonTypeDescription typeDesc) {
      discovered.put(clazz, typeDesc);
    }
  }
}
