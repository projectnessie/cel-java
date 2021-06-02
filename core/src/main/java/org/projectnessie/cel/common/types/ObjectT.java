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

public final class ObjectT {}
// TODO
// public final class ObjectT implements Val, FieldTester, Indexer {
//	private final TypeAdapter adapter;
//	private final proto.Message value;
//	private final pb.TypeDescription typeDesc;
//	private final TypeValue typeValue;
//
//	/**NewObject returns an object based on a proto.Message value which handles
//	 * conversion between protobuf type values and expression type values.
//	 * Objects support indexing and iteration.
//	 *
//	 * Note: the type value is pulled from the list of registered types within the
//	 * type provider. If the proto type is not registered within the type provider,
//	 * then this will result in an error within the type adapter / provider. */
//	public static Val newObject(TypeAdapter adapter,
//			pb.TypeDescription typeDesc,
//			TypeValue typeValue,
//			proto.Message) value {
//		return &protoObj{
//			TypeAdapter: adapter,
//			value:       value,
//			typeDesc:    typeDesc,
//			typeValue:   typeValue}
//	}
//
//	@Override
//	public <T> T convertToNative(Class<T> typeDesc) {
//		pb := o.value
//		if reflect.TypeOf(pb).AssignableTo(typeDesc) {
//			return pb, nil
//		}
//		if reflect.TypeOf(o).AssignableTo(typeDesc) {
//			return o, nil
//		}
//		switch typeDesc {
//		case anyValueType:
//			_, isAny := pb.(*anypb.Any)
//			if isAny {
//				return pb, nil
//			}
//			return anypb.New(pb)
//		case jsonValueType:
//			// Marshal the proto to JSON first, and then rehydrate as protobuf.Value as there is no
//			// support for direct conversion from proto.Message to protobuf.Value.
//			bytes, err := protojson.Marshal(pb)
//			if err != nil {
//				return nil, err
//			}
//			json := &structpb.Value{}
//			err = protojson.Unmarshal(bytes, json)
//			if err != nil {
//				return nil, err
//			}
//			return json, nil
//		default:
//			if typeDesc == o.typeDesc.ReflectType() {
//				return o.value, nil
//			}
//			if typeDesc.Kind() == reflect.Ptr {
//				val := reflect.New(typeDesc.Elem()).Interface()
//				dstPB, ok := val.(proto.Message)
//				if ok {
//					proto.Merge(dstPB, pb)
//					return dstPB, nil
//				}
//			}
//		}
//		return nil, fmt.Errorf("type conversion error from '%T' to '%v'", o.value, typeDesc)
//	}
//
//	@Override
//	public Val convertToType(Type typeValue) {
//		switch typeVal {
//		default:
//			if o.Type().TypeName() == typeVal.TypeName() {
//				return o
//			}
//		case TypeType:
//			return o.typeValue
//		}
//		return NewErr("type conversion error from '%s' to '%s'", o.typeDesc.Name(), typeVal)
//	}
//
//	@Override
//	public Val equal(Val other) {
//		if o.typeDesc.Name() != other.Type().TypeName() {
//			return Err.maybeNoSuchOverloadErr(other);
//		}
//		return Bool(proto.Equal(o.value, other.Value().(proto.Message)))
//	}
//
//	/**IsSet tests whether a field which is defined is set to a non-default value. */
//	@Override
//	public Val isSet(Val field) {
//		protoFieldName, ok := field.(String)
//		if !ok {
//			return Err.maybeNoSuchOverloadErr(field);
//		}
//		protoFieldStr := string(protoFieldName)
//		fd, found := o.typeDesc.FieldByName(protoFieldStr)
//		if !found {
//			return NewErr("no such field '%s'", field)
//		}
//		if fd.IsSet(o.value) {
//			return True
//		}
//		return False
//	}
//
//	@Override
//	public Val get(Val index) {
//		protoFieldName, ok := index.(String)
//		if !ok {
//			return Err.maybeNoSuchOverloadErr(index);
//		}
//		protoFieldStr := string(protoFieldName)
//		fd, found := o.typeDesc.FieldByName(protoFieldStr)
//		if !found {
//			return NewErr("no such field '%s'", index)
//		}
//		fv, err := fd.GetFrom(o.value)
//		if err != nil {
//			return NewErr(err.Error())
//		}
//		return o.NativeToValue(fv)
//	}
//
//	@Override
//	public Type type() {
//		return o.typeValue
//	}
//
//	@Override
//	public Object value() {
//		return o.value
//	}
// }
