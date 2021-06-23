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

import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchField;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Types.boolOf;

import com.google.protobuf.Any;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.google.protobuf.Value;
import org.projectnessie.cel.common.types.ObjectT;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

public final class PbObjectT extends ObjectT {

  private PbObjectT(
      TypeAdapter adapter, Message value, PbTypeDescription typeDesc, Type typeValue) {
    super(adapter, value, typeDesc, typeValue);
  }

  /**
   * NewObject returns an object based on a proto.Message value which handles conversion between
   * protobuf type values and expression type values. Objects support indexing and iteration.
   *
   * <p>Note: the type value is pulled from the list of registered types within the type provider.
   * If the proto type is not registered within the type provider, then this will result in an error
   * within the type adapter / provider.
   */
  public static Val newObject(
      TypeAdapter adapter, PbTypeDescription typeDesc, Type typeValue, Message value) {
    return new PbObjectT(adapter, value, typeDesc, typeValue);
  }

  /** IsSet tests whether a field which is defined is set to a non-default value. */
  @Override
  public Val isSet(Val field) {
    if (!(field instanceof StringT)) {
      return noSuchOverload(this, "isSet", field);
    }
    String protoFieldStr = (String) field.value();
    FieldDescription fd = typeDesc().fieldByName(protoFieldStr);
    if (fd == null) {
      return noSuchField(protoFieldStr);
    }
    return boolOf(fd.hasField(value));
  }

  @Override
  public Val get(Val index) {
    if (!(index instanceof StringT)) {
      return noSuchOverload(this, "get", index);
    }
    String protoFieldStr = (String) index.value();
    FieldDescription fd = typeDesc().fieldByName(protoFieldStr);
    if (fd == null) {
      return noSuchField(protoFieldStr);
    }
    return nativeToValue(fd.getField(value));
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    if (typeDesc.isAssignableFrom(value.getClass())) {
      return (T) value;
    }
    if (typeDesc.isAssignableFrom(getClass())) {
      return (T) this;
    }
    if (typeDesc.isAssignableFrom(value.getClass())) {
      return (T) value;
    }
    if (typeDesc == DynamicMessage.class) {
      return (T)
          DynamicMessage.newBuilder(message().getDescriptorForType()).mergeFrom(message()).build();
    }
    if (typeDesc == Any.class) {
      // anyValueType
      if (value instanceof Any) {
        return (T) value;
      }
      return (T) Any.pack(message());
    }
    if (typeDesc == Value.class) {
      // jsonValueType
      throw new UnsupportedOperationException("IMPLEMENT proto-to-json");
      // TODO proto-to-json
      //		// Marshal the proto to JSON first, and then rehydrate as protobuf.Value as there is no
      //		// support for direct conversion from proto.Message to protobuf.Value.
      //		bytes, err := protojson.Marshal(pb)
      //		if err != nil {
      //			return nil, err
      //		}
      //		json := &structpb.Value{}
      //		err = protojson.Unmarshal(bytes, json)
      //		if err != nil {
      //			return nil, err
      //		}
      //		return json, nil
    }
    if (typeDesc.isAssignableFrom(this.typeDesc.reflectType()) || typeDesc == Object.class) {
      if (value instanceof Any || value instanceof DynamicMessage) {
        return buildFrom(typeDesc);
      }
      return (T) value;
    }

    if (Message.class.isAssignableFrom(typeDesc)) {
      return buildFrom(typeDesc);
    }

    if (typeDesc == Val.class || typeDesc == PbObjectT.class) {
      return (T) this;
    }

    // impossible cast
    throw new IllegalArgumentException(
        newTypeConversionError(value.getClass().getName(), typeDesc).toString());
  }

  private Message message() {
    return (Message) value;
  }

  private PbTypeDescription typeDesc() {
    return (PbTypeDescription) typeDesc;
  }

  @SuppressWarnings("unchecked")
  private <T> T buildFrom(Class<T> typeDesc) {
    try {
      Message.Builder builder =
          (Message.Builder) typeDesc.getDeclaredMethod("newBuilder").invoke(null);
      return (T) builder.mergeFrom(message()).build();
    } catch (Exception e) {
      throw new RuntimeException(
          String.format("%s: %s", newTypeConversionError(value.getClass().getName(), typeDesc), e));
    }
  }
}
