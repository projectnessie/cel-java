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
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.projectnessie.cel.common.types.ref.FieldGetter;

final class GeneratedFieldAccessor {

  private static final MethodType OBJECT_GETTER = MethodType.methodType(Object.class, Object.class);

  private GeneratedFieldAccessor() {}

  static FieldGetter create(PbTypeDescription type, FieldDescription field) {
    FieldDescriptor descriptor = field.descriptor();
    Class<?> returnType = generatedReturnType(descriptor);
    Message zero = type.zero();
    Class<?> messageClass = type.reflectType();
    if (returnType == null
        || descriptor.isExtension()
        || descriptor.isRepeated()
        || zero instanceof DynamicMessage
        || !messageClass.isInstance(zero)) {
      return null;
    }

    String suffix = accessorSuffix(descriptor.getName());
    for (String methodName : new String[] {"get" + suffix, "get" + suffix + '_'}) {
      try {
        MethodHandle getter =
            MethodHandles.publicLookup()
                .findVirtual(messageClass, methodName, MethodType.methodType(returnType));
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

  private static Class<?> generatedReturnType(FieldDescriptor descriptor) {
    return switch (descriptor.getType()) {
      case BOOL -> boolean.class;
      case STRING -> String.class;
      case BYTES -> ByteString.class;
      case INT32, SINT32, SFIXED32 -> int.class;
      case INT64, SINT64, SFIXED64 -> long.class;
      case FLOAT -> float.class;
      case DOUBLE -> double.class;
      default -> null;
    };
  }

  private static boolean matchesField(
      MethodHandle getter, Message zero, FieldDescriptor descriptor) {
    Object expected = validationValue(descriptor);
    Message probe = zero.newBuilderForType().setField(descriptor, expected).buildPartial();
    try {
      return expected.equals(getter.invoke(probe));
    } catch (Throwable e) {
      return false;
    }
  }

  private static Object validationValue(FieldDescriptor descriptor) {
    return switch (descriptor.getType()) {
      case BOOL -> true;
      case STRING -> "cel_generated_field_accessor";
      case BYTES -> ByteString.copyFromUtf8("cel_generated_field_accessor");
      case INT32, SINT32, SFIXED32 -> 0x51a7;
      case INT64, SINT64, SFIXED64 -> 0x51a7_19b3_42c5L;
      case FLOAT -> 123.25f;
      case DOUBLE -> 123.25d;
      default ->
          throw new IllegalArgumentException("Unsupported field type " + descriptor.getType());
    };
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

  private static Object invoke(MethodHandle getter, Object target) {
    try {
      return (Object) getter.invokeExact(target);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
