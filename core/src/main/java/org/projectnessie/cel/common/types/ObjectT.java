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

import static org.projectnessie.cel.common.types.BoolT.boolOf;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchField;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;

import com.google.protobuf.Any;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.google.protobuf.Value;
import java.util.Objects;
import org.projectnessie.cel.common.types.pb.FieldDescription;
import org.projectnessie.cel.common.types.pb.TypeDescription;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.FieldTester;
import org.projectnessie.cel.common.types.traits.Indexer;

public final class ObjectT extends BaseVal implements FieldTester, Indexer, TypeAdapter {
  private final TypeAdapter adapter;
  private final Message value;
  private final TypeDescription typeDesc;
  private final TypeValue typeValue;

  private ObjectT(
      TypeAdapter adapter, Message value, TypeDescription typeDesc, TypeValue typeValue) {
    this.adapter = adapter;
    this.value = value;
    this.typeDesc = typeDesc;
    this.typeValue = typeValue;
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
      TypeAdapter adapter, TypeDescription typeDesc, TypeValue typeValue, Message value) {
    return new ObjectT(adapter, value, typeDesc, typeValue);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T convertToNative(Class<T> typeDesc) {
    Message pb = value;
    if (typeDesc.isAssignableFrom(pb.getClass())) {
      return (T) pb;
    }
    if (typeDesc.isAssignableFrom(getClass())) {
      return (T) this;
    }
    if (typeDesc.isAssignableFrom(value.getClass())) {
      return (T) value;
    }
    if (typeDesc == DynamicMessage.class) {
      return (T) DynamicMessage.newBuilder(value.getDescriptorForType()).mergeFrom(value).build();
    }
    if (typeDesc == Any.class) {
      // anyValueType
      if (pb instanceof Any) {
        return (T) pb;
      }
      return (T) Any.pack(pb);
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
    // TODO this the following as well??
    //		if typeDesc.Kind() == reflect.Ptr {
    //			val := reflect.New(typeDesc.Elem()).Interface()
    //			dstPB, ok := val.(proto.Message)
    //			if ok {
    //				proto.Merge(dstPB, pb)
    //				return dstPB, nil
    //			}
    //		}

    if (Message.class.isAssignableFrom(typeDesc)) {
      return buildFrom(typeDesc);
    }

    if (typeDesc == Val.class || typeDesc == ObjectT.class) {
      return (T) this;
    }

    // impossible cast
    throw new IllegalArgumentException(
        newTypeConversionError(value.getClass().getName(), typeDesc).toString());
  }

  private <T> T buildFrom(Class<T> typeDesc) {
    try {
      Message.Builder builder =
          (Message.Builder) typeDesc.getDeclaredMethod("newBuilder").invoke(null);
      return (T) builder.mergeFrom(value).build();
    } catch (Exception e) {
      throw new RuntimeException(
          String.format("%s: %s", newTypeConversionError(value.getClass().getName(), typeDesc), e));
    }
  }

  @Override
  public Val convertToType(Type typeVal) {
    if (typeVal == TypeType) {
      return typeValue;
    }
    if (type().typeName().equals(typeVal.typeName())) {
      return this;
    }
    return newTypeConversionError(typeDesc.name(), typeVal);
  }

  @Override
  public Val equal(Val other) {
    if (!typeDesc.name().equals(other.type().typeName())) {
      return noSuchOverload(this, "equal", other);
    }
    return boolOf(this.value.equals(other.value()));
  }

  /** IsSet tests whether a field which is defined is set to a non-default value. */
  @Override
  public Val isSet(Val field) {
    if (!(field instanceof StringT)) {
      return noSuchOverload(this, "isSet", field);
    }
    String protoFieldStr = (String) field.value();
    FieldDescription fd = typeDesc.fieldByName(protoFieldStr);
    if (fd == null) {
      return noSuchField(protoFieldStr);
    }
    return boolOf(value.hasField(fd.descriptor()));
  }

  @Override
  public Val get(Val index) {
    if (!(index instanceof StringT)) {
      return noSuchOverload(this, "get", index);
    }
    String protoFieldStr = (String) index.value();
    FieldDescription fd = typeDesc.fieldByName(protoFieldStr);
    if (fd == null) {
      return noSuchField(protoFieldStr);
    }
    Object fv = value.getField(fd.descriptor());
    return nativeToValue(fv);
  }

  @Override
  public Type type() {
    return typeValue;
  }

  @Override
  public Object value() {
    return value;
  }

  @Override
  public Val nativeToValue(Object value) {
    return adapter.nativeToValue(value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ObjectT objectT = (ObjectT) o;
    return Objects.equals(value, objectT.value)
        && Objects.equals(typeDesc, objectT.typeDesc)
        && Objects.equals(typeValue, objectT.typeValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), value, typeDesc, typeValue);
  }
}
