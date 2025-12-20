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
package org.projectnessie.cel.types.jackson3;

import static org.projectnessie.cel.common.types.Err.newErr;

import java.util.HashMap;
import java.util.Map;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeAdapterSupport;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.GeneratorSettings;
import tools.jackson.databind.cfg.SerializationContexts;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.SerializationContextExt;
import tools.jackson.databind.ser.jdk.EnumSerializer;
import tools.jackson.databind.type.TypeFactory;

/**
 * CEL-Java {@link TypeRegistry} to use Jackson 3 objects as input values for CEL scripts.
 *
 * <p>The implementation does not support the construction of Jackson objects in CEL expressions and
 * therefore returning Jackson objects from CEL expressions is not possible/implemented and results
 * in {@link UnsupportedOperationException}s.
 */
public final class Jackson3Registry implements TypeRegistry {
  final ObjectMapper objectMapper;
  private final SerializationContextExt serializationContextExt;
  private final TypeFactory typeFactory;
  private final Map<Class<?>, JacksonTypeDescription> knownTypes = new HashMap<>();
  private final Map<String, JacksonTypeDescription> knownTypesByName = new HashMap<>();

  private final Map<Class<?>, JacksonEnumDescription> enumMap = new HashMap<>();
  private final Map<String, JacksonEnumValue> enumValues = new HashMap<>();

  private Jackson3Registry() {
    JsonMapper.Builder b = JsonMapper.builder();
    SerializationContexts serializationContexts = b.serializationContexts();
    this.objectMapper = b.build();
    SerializationContexts forMapper =
        serializationContexts.forMapper(
            objectMapper,
            objectMapper.serializationConfig(),
            objectMapper.tokenStreamFactory(),
            b.serializerFactory());
    this.serializationContextExt =
        forMapper.createContext(objectMapper.serializationConfig(), GeneratorSettings.empty());
    this.typeFactory = objectMapper.getTypeFactory();
  }

  public static TypeRegistry newRegistry() {
    return new Jackson3Registry();
  }

  @Override
  public TypeRegistry copy() {
    return this;
  }

  @Override
  public void register(Object t) {
    Class<?> cls = t instanceof Class ? (Class<?>) t : t.getClass();
    typeDescription(cls);
  }

  @Override
  public void registerType(Type... types) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Val enumValue(String enumName) {
    JacksonEnumValue enumVal = enumValues.get(enumName);
    if (enumVal == null) {
      return newErr("unknown enum name '%s'", enumName);
    }
    return enumVal.ordinalValue();
  }

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

  @Override
  public com.google.api.expr.v1alpha1.Type findType(String typeName) {
    JacksonTypeDescription td = knownTypesByName.get(typeName);
    if (td == null) {
      return null;
    }
    return td.pbType();
  }

  @Override
  public FieldType findFieldType(String messageType, String fieldName) {
    JacksonTypeDescription td = knownTypesByName.get(messageType);
    if (td == null) {
      return null;
    }
    return td.fieldType(fieldName);
  }

  @Override
  public Val newValue(String typeName, Map<String, Val> fields) {
    throw new UnsupportedOperationException();
  }

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

  JacksonEnumDescription enumDescription(Class<?> clazz) {
    if (!Enum.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException("only enum allowed here");
    }

    JacksonEnumDescription ed = enumMap.get(clazz);
    if (ed != null) {
      return ed;
    }
    ed = computeEnumDescription(clazz);
    enumMap.put(clazz, ed);
    return ed;
  }

  private JacksonEnumDescription computeEnumDescription(Class<?> clazz) {
    ValueSerializer<?> ser = serializationContextExt.findValueSerializer(clazz);
    JavaType javaType = typeFactory.constructType(clazz);

    JacksonEnumDescription enumDesc = new JacksonEnumDescription(javaType, (EnumSerializer) ser);
    enumMap.put(clazz, enumDesc);

    enumDesc.buildValues().forEach(v -> enumValues.put(v.fullyQualifiedName(), v));

    return enumDesc;
  }

  JacksonTypeDescription typeDescription(Class<?> clazz) {
    if (Enum.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException("enum not allowed here");
    }

    JacksonTypeDescription td = knownTypes.get(clazz);
    if (td != null) {
      return td;
    }
    td = computeTypeDescription(clazz);
    knownTypes.put(clazz, td);
    return td;
  }

  private JacksonTypeDescription computeTypeDescription(Class<?> clazz) {
    ValueSerializer<Object> ser = serializationContextExt.findValueSerializer(clazz);
    JavaType javaType = typeFactory.constructType(clazz);

    JacksonTypeDescription typeDesc = new JacksonTypeDescription(javaType, ser, this::typeQuery);
    knownTypesByName.put(clazz.getName(), typeDesc);

    return typeDesc;
  }

  private com.google.api.expr.v1alpha1.Type typeQuery(JavaType javaType) {
    if (javaType.isEnumType()) {
      return enumDescription(javaType.getRawClass()).pbType();
    }
    return typeDescription(javaType.getRawClass()).pbType();
  }
}
