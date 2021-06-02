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
import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.DoubleT.doubleOf;
import static org.projectnessie.cel.common.types.DurationT.durationOf;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.ListT.newGenericArrayList;
import static org.projectnessie.cel.common.types.ListT.newStringArrayList;
import static org.projectnessie.cel.common.types.ListT.newValArrayList;
import static org.projectnessie.cel.common.types.ListT.newWrappedList;
import static org.projectnessie.cel.common.types.MapT.newWrappedMap;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TimestampT.ZoneIdZ;
import static org.projectnessie.cel.common.types.TimestampT.timestampOf;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

/** defaultTypeAdapter converts go native types to CEL values. */
public class DefaultTypeAdapter implements TypeAdapter {
  /** DefaultTypeAdapter adapts canonical CEL types from their equivalent Go values. */
  public static final DefaultTypeAdapter Instance = new DefaultTypeAdapter();

  /** NativeToValue implements the ref.TypeAdapter interface. */
  @Override
  public Val nativeToValue(Object value) {
    Val val = nativeToValue(this, value);
    if (val != null) {
      return val;
    }
    return Err.unsupportedRefValConversionErr(value);
  }

  /**
   * nativeToValue returns the converted (ref.Val, true) of a conversion is found, otherwise (nil,
   * false)
   */
  public static Val nativeToValue(TypeAdapter a, Object value) {
    if (value == null) {
      return NullT.NullValue;
    }
    if (value instanceof Boolean) {
      return boolOf((Boolean) value);
    }
    if (value instanceof byte[]) {
      return bytesOf(((byte[]) value));
    }
    if (value instanceof Float) {
      return doubleOf(((Float) value).doubleValue());
    }
    if (value instanceof Double) {
      return doubleOf((Double) value);
    }
    if (value instanceof Byte) {
      return intOf((Byte) value);
    }
    if (value instanceof Short) {
      return intOf((Short) value);
    }
    if (value instanceof Integer) {
      return intOf((Integer) value);
    }
    if (value instanceof Long) {
      return intOf((Long) value);
    }
    if (value instanceof String) {
      return stringOf((String) value);
    }
    if (value instanceof Duration) {
      return durationOf((Duration) value);
    }
    if (value instanceof Instant) {
      return timestampOf(((Instant) value).atZone(ZoneIdZ));
    }
    if (value instanceof ZonedDateTime) {
      return timestampOf((ZonedDateTime) value);
    }
    if (value instanceof Date) {
      return timestampOf(((Date) value).toInstant().atZone(ZoneIdZ));
    }
    if (value instanceof Calendar) {
      return timestampOf(((Calendar) value).toInstant().atZone(ZoneIdZ));
    }
    if (value instanceof Val) {
      return (Val) value;
    }
    if (value instanceof String[]) {
      return newStringArrayList((String[]) value);
    }
    if (value instanceof Val[]) {
      return newValArrayList(a, (Val[]) value);
    }
    if (value instanceof Object[]) {
      return newGenericArrayList(a, (Object[]) value);
    }
    if (value instanceof List) {
      return newWrappedList(a, (List<?>) value);
    }
    if (value instanceof Map) {
      return newWrappedMap(a, (Map<?, ?>) value);
    }
    return null;
  }

  //    // additional specializations may be added upon request / need.
  //    case *anypb.Any:
  //      if v == nil {
  //        return UnsupportedRefValConversionErr(v), true
  //      }
  //      unpackedAny, err := v.UnmarshalNew()
  //      if err != nil {
  //        return NewErr("anypb.UnmarshalNew() failed for type %q: %v", v.GetTypeUrl(), err), true
  //      }
  //      return a.NativeToValue(unpackedAny), true
  //    case *structpb.NullValue, structpb.NullValue:
  //      return NullValue, true
  //    case *structpb.ListValue:
  //      return NewJSONList(a, v), true
  //    case *structpb.Struct:
  //      return NewJSONStruct(a, v), true
  //    case ref.Val:
  //      return v, true
  //    case protoreflect.EnumNumber:
  //      return Int(v), true
  //    case proto.Message:
  //      if v == nil {
  //        return UnsupportedRefValConversionErr(v), true
  //      }
  //      typeName := string(v.ProtoReflect().Descriptor().FullName())
  //      td, found := pb.DefaultDb.DescribeType(typeName)
  //      if !found {
  //        return nil, false
  //      }
  //      val, unwrapped := td.MaybeUnwrap(v)
  //      if !unwrapped {
  //        return nil, false
  //      }
  //      return a.NativeToValue(val), true
  //    // Note: dynamicpb.Message implements the proto.Message _and_ protoreflect.Message
  // interfaces
  //    // which means that this case must appear after handling a proto.Message type.
  //    case protoreflect.Message:
  //      return a.NativeToValue(v.Interface()), true
  //    default:
  //      refValue := reflect.ValueOf(v)
  //      if refValue.Kind() == reflect.Ptr {
  //        if refValue.IsNil() {
  //          return UnsupportedRefValConversionErr(v), true
  //        }
  //        refValue = refValue.Elem()
  //      }
  //      refKind := refValue.Kind()
  //      switch refKind {
  //      case reflect.Array, reflect.Slice:
  //        return NewDynamicList(a, v), true
  //      case reflect.Map:
  //        return NewDynamicMap(a, v), true
  //      // type aliases of primitive types cannot be asserted as that type, but rather need
  //      // to be downcast to int32 before being converted to a CEL representation.
  //      case reflect.Int32:
  //        intType := reflect.TypeOf(int32(0))
  //        return Int(refValue.Convert(intType).Interface().(int32)), true
  //      case reflect.Int64:
  //        intType := reflect.TypeOf(int64(0))
  //        return Int(refValue.Convert(intType).Interface().(int64)), true
  //      case reflect.Uint32:
  //        uintType := reflect.TypeOf(uint32(0))
  //        return Uint(refValue.Convert(uintType).Interface().(uint32)), true
  //      case reflect.Uint64:
  //        uintType := reflect.TypeOf(uint64(0))
  //        return Uint(refValue.Convert(uintType).Interface().(uint64)), true
  //      case reflect.Float32:
  //        doubleType := reflect.TypeOf(float32(0))
  //        return Double(refValue.Convert(doubleType).Interface().(float32)), true
  //      case reflect.Float64:
  //        doubleType := reflect.TypeOf(float64(0))
  //        return Double(refValue.Convert(doubleType).Interface().(float64)), true
  //      }
  //    }
  //    return nil, false
  //  }
  //
  //  func msgSetField(target protoreflect.Message, field *pb.FieldDescription, val ref.Val) error {
  //    if field.IsList() {
  //      lv := target.NewField(field.Descriptor())
  //      list, ok := val.(traits.Lister)
  //      if !ok {
  //        return unsupportedTypeConversionError(field, val)
  //      }
  //      err := msgSetListField(lv.List(), field, list)
  //      if err != nil {
  //        return err
  //      }
  //      target.Set(field.Descriptor(), lv)
  //      return nil
  //    }
  //    if field.IsMap() {
  //      mv := target.NewField(field.Descriptor())
  //      mp, ok := val.(traits.Mapper)
  //      if !ok {
  //        return unsupportedTypeConversionError(field, val)
  //      }
  //      err := msgSetMapField(mv.Map(), field, mp)
  //      if err != nil {
  //        return err
  //      }
  //      target.Set(field.Descriptor(), mv)
  //      return nil
  //    }
  //    v, err := val.ConvertToNative(field.ReflectType())
  //    if err != nil {
  //      return fieldTypeConversionError(field, err)
  //    }
  //    switch v.(type) {
  //    case proto.Message:
  //      v = v.(proto.Message).ProtoReflect()
  //    }
  //    target.Set(field.Descriptor(), protoreflect.ValueOf(v))
  //    return nil
  //  }
  //
  //  func msgSetListField(target protoreflect.List, listField *pb.FieldDescription, listVal
  // traits.Lister) error {
  //    elemReflectType := listField.ReflectType().Elem()
  //    for i := Int(0); i < listVal.Size().(Int); i++ {
  //      elem := listVal.Get(i)
  //      elemVal, err := elem.ConvertToNative(elemReflectType)
  //      if err != nil {
  //        return fieldTypeConversionError(listField, err)
  //      }
  //      switch ev := elemVal.(type) {
  //      case proto.Message:
  //        elemVal = ev.ProtoReflect()
  //      }
  //      target.Append(protoreflect.ValueOf(elemVal))
  //    }
  //    return nil
  //  }
  //
  //  func msgSetMapField(target protoreflect.Map, mapField *pb.FieldDescription, mapVal
  // traits.Mapper) error {
  //    targetKeyType := mapField.KeyType.ReflectType()
  //    targetValType := mapField.ValueType.ReflectType()
  //    it := mapVal.Iterator()
  //    for it.HasNext() == True {
  //      key := it.Next()
  //      val := mapVal.Get(key)
  //      k, err := key.ConvertToNative(targetKeyType)
  //      if err != nil {
  //        return fieldTypeConversionError(mapField, err)
  //      }
  //      v, err := val.ConvertToNative(targetValType)
  //      if err != nil {
  //        return fieldTypeConversionError(mapField, err)
  //      }
  //      switch v.(type) {
  //      case proto.Message:
  //        v = v.(proto.Message).ProtoReflect()
  //      }
  //      target.Set(protoreflect.ValueOf(k).MapKey(), protoreflect.ValueOf(v))
  //    }
  //    return nil
  //  }
  //
  //  func unsupportedTypeConversionError(field *pb.FieldDescription, val ref.Val) error {
  //    msgName := field.Descriptor().ContainingMessage().FullName()
  //    return fmt.Errorf("unsupported field type for %v.%v: %v", msgName, field.Name(), val.Type())
  //  }
  //
  //  func fieldTypeConversionError(field *pb.FieldDescription, err error) error {
  //    msgName := field.Descriptor().ContainingMessage().FullName()
  //    return fmt.Errorf("field type conversion error for %v.%v value type: %v", msgName,
  // field.Name(), err)
  //  }

}
