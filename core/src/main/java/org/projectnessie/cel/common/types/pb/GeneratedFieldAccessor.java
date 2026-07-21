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

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.common.types.ref.FieldGetter;
import org.projectnessie.cel.common.types.ref.FieldTester;

final class GeneratedFieldAccessor {

  private static final MethodType OBJECT_GETTER = MethodType.methodType(Object.class, Object.class);

  private GeneratedFieldAccessor() {}

  static FieldGetter create(PbTypeDescription type, FieldDescription field) {
    return create(type, field, false);
  }

  static FieldGetter createForObject(PbTypeDescription type, FieldDescription field) {
    return create(type, field, true);
  }

  private static FieldGetter create(
      PbTypeDescription type, FieldDescription field, boolean allowOrdinaryMessages) {
    FieldDescriptor descriptor = field.descriptor();
    Message zero = type.zero();
    Class<?> messageClass = type.reflectType();
    if (descriptor.isExtension()
        || zero instanceof DynamicMessage
        || !messageClass.isInstance(zero)) {
      return null;
    }

    String suffix = accessorSuffix(descriptor.getName());
    String methodName;
    Class<?> returnType;
    if (descriptor.isMapField()) {
      FieldDescriptor valueDescriptor = descriptor.getMessageType().findFieldByNumber(2);
      methodName =
          valueDescriptor.getType() == FieldDescriptor.Type.ENUM
              ? "get" + suffix + "ValueMap"
              : "get" + suffix + "Map";
      returnType = Map.class;
    } else if (descriptor.isRepeated()) {
      methodName =
          descriptor.getType() == FieldDescriptor.Type.ENUM
              ? "get" + suffix + "ValueList"
              : "get" + suffix + "List";
      returnType = List.class;
    } else if (descriptor.getType() == FieldDescriptor.Type.ENUM) {
      if (isNullValue(descriptor.getEnumType().getFullName())) {
        return null;
      }
      methodName = "get" + suffix + "Value";
      returnType = int.class;
    } else {
      methodName = "get" + suffix;
      returnType = generatedReturnType(descriptor, zero, allowOrdinaryMessages);
    }
    if (returnType == null) {
      return null;
    }

    for (String candidate : new String[] {methodName, methodName + '_'}) {
      try {
        MethodHandle getter =
            MethodHandles.publicLookup()
                .findVirtual(messageClass, candidate, MethodType.methodType(returnType));
        if (!matchesField(getter, zero, descriptor)) {
          continue;
        }
        MethodHandle objectGetter = getter.asType(OBJECT_GETTER);
        return target -> invoke(objectGetter, target);
      } catch (IllegalAccessException | NoSuchMethodException | RuntimeException e) {
        // Generated-name collisions and inaccessible generated classes use descriptor access.
      }
    }
    return null;
  }

  static FieldTester createTester(PbTypeDescription type, FieldDescription field) {
    FieldDescriptor descriptor = field.descriptor();
    Message zero = type.zero();
    Class<?> messageClass = type.reflectType();
    if (descriptor.isExtension()
        || descriptor.isRepeated()
        || !descriptor.hasPresence()
        || zero instanceof DynamicMessage
        || !messageClass.isInstance(zero)) {
      return null;
    }

    String methodName = "has" + accessorSuffix(descriptor.getName());
    for (String candidate : new String[] {methodName, methodName + '_'}) {
      try {
        MethodHandle tester =
            MethodHandles.publicLookup()
                .findVirtual(messageClass, candidate, MethodType.methodType(boolean.class));
        if (!matchesPresence(tester, zero, descriptor)) {
          continue;
        }
        MethodHandle objectTester =
            tester.asType(MethodType.methodType(boolean.class, Object.class));
        return target -> invokeBoolean(objectTester, target);
      } catch (IllegalAccessException | NoSuchMethodException | RuntimeException e) {
        // Generated-name collisions and inaccessible generated classes use descriptor access.
      }
    }
    return null;
  }

  private static Class<?> generatedReturnType(
      FieldDescriptor descriptor, Message zero, boolean allowOrdinaryMessages) {
    return switch (descriptor.getType()) {
      case BOOL -> boolean.class;
      case STRING -> String.class;
      case BYTES -> ByteString.class;
      case INT32, SINT32, SFIXED32, UINT32, FIXED32 -> int.class;
      case INT64, SINT64, SFIXED64, UINT64, FIXED64 -> long.class;
      case FLOAT -> float.class;
      case DOUBLE -> double.class;
      case MESSAGE -> {
        Object value =
            allowOrdinaryMessages
                    || Checked.CheckedWellKnowns.containsKey(
                        descriptor.getMessageType().getFullName())
                ? zero.getField(descriptor)
                : null;
        yield value instanceof Message ? value.getClass() : null;
      }
      default -> null;
    };
  }

  private static boolean matchesField(
      MethodHandle getter, Message zero, FieldDescriptor descriptor) {
    try {
      Message.Builder builder = zero.newBuilderForType();
      if (descriptor.isMapField()) {
        Message.Builder entry = builder.newBuilderForField(descriptor);
        for (FieldDescriptor entryField : descriptor.getMessageType().getFields()) {
          Object value = validationValue(entryField, entry);
          if (value == null) {
            return false;
          }
          entry.setField(entryField, value);
        }
        builder.addRepeatedField(descriptor, entry.buildPartial());
        return ((Map<?, ?>) getter.invoke(builder.buildPartial())).size() == 1;
      }

      Object expected = validationValue(descriptor, builder);
      if (expected == null) {
        return false;
      }
      if (descriptor.isRepeated()) {
        builder.addRepeatedField(descriptor, expected);
        List<?> actual = (List<?>) getter.invoke(builder.buildPartial());
        if (actual.size() != 1) {
          return false;
        }
        Object actualValue = actual.get(0);
        return descriptor.getType() == FieldDescriptor.Type.ENUM
            ? actualValue.equals(((EnumValueDescriptor) expected).getNumber())
            : expected.equals(actualValue);
      }

      builder.setField(descriptor, expected);
      Object actual = getter.invoke(builder.buildPartial());
      return descriptor.getType() == FieldDescriptor.Type.ENUM
          ? actual.equals(((EnumValueDescriptor) expected).getNumber())
          : expected.equals(actual);
    } catch (Throwable e) {
      return false;
    }
  }

  private static boolean matchesPresence(
      MethodHandle tester, Message zero, FieldDescriptor descriptor) {
    try {
      if ((boolean) tester.invoke(zero)) {
        return false;
      }
      Message.Builder builder = zero.newBuilderForType();
      builder.setField(descriptor, builder.getField(descriptor));
      return (boolean) tester.invoke(builder.buildPartial());
    } catch (Throwable e) {
      return false;
    }
  }

  private static Object validationValue(
      FieldDescriptor descriptor, Message.Builder containingBuilder) {
    return switch (descriptor.getType()) {
      case BOOL -> true;
      case STRING -> "cel_generated_field_accessor";
      case BYTES -> ByteString.copyFromUtf8("cel_generated_field_accessor");
      case INT32, SINT32, SFIXED32, UINT32, FIXED32 -> 0x51a7;
      case INT64, SINT64, SFIXED64, UINT64, FIXED64 -> 0x51a7_19b3_42c5L;
      case FLOAT -> 123.25f;
      case DOUBLE -> 123.25d;
      case ENUM -> {
        List<EnumValueDescriptor> values = descriptor.getEnumType().getValues();
        yield values.get(Math.min(1, values.size() - 1));
      }
      case MESSAGE -> nonDefaultMessage(containingBuilder.newBuilderForField(descriptor));
      default -> null;
    };
  }

  private static Message nonDefaultMessage(Message.Builder builder) {
    for (FieldDescriptor field : builder.getDescriptorForType().getFields()) {
      if (field.isRepeated() || field.getType() == FieldDescriptor.Type.MESSAGE) {
        continue;
      }
      Object value = validationValue(field, builder);
      if (value != null) {
        return builder.setField(field, value).buildPartial();
      }
    }
    return null;
  }

  private static String accessorSuffix(String fieldName) {
    StringBuilder result = new StringBuilder(fieldName.length());
    boolean capitalizeNext = true;
    for (int i = 0; i < fieldName.length(); i++) {
      char c = fieldName.charAt(i);
      if (c >= 'a' && c <= 'z') {
        result.append(capitalizeNext ? Character.toUpperCase(c) : c);
        capitalizeNext = false;
      } else if ((c >= 'A' && c <= 'Z')) {
        result.append(c);
        capitalizeNext = false;
      } else if (c >= '0' && c <= '9') {
        result.append(c);
        capitalizeNext = true;
      } else {
        capitalizeNext = true;
      }
    }
    return result.toString();
  }

  private static boolean isNullValue(String enumTypeName) {
    return enumTypeName.equals("google.protobuf.NullValue");
  }

  private static Object invoke(MethodHandle getter, Object target) {
    try {
      return (Object) getter.invokeExact(target);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean invokeBoolean(MethodHandle tester, Object target) {
    try {
      return (boolean) tester.invokeExact(target);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
