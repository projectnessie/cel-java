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

public final class ProtoTypeRegistry {}
// TODO
// public final class ProtoTypeRegistry {
//  private final Map<String, Type> revTypeMap = new HashMap<>();
//  // TODO pbdb       *pb.Db
//
//  /**NewRegistry accepts a list of proto message instances and returns a type
//   * provider which can create new instances of the provided message or any
//   * message that proto depends upon in its FileDescriptor. */
//  func NewRegistry(types ...proto.Message) (ref.TypeRegistry, error) {
//    p := &protoTypeRegistry{
//      revTypeMap: make(map[string]ref.Type),
//      pbdb:       pb.NewDb(),
//    }
//    err := p.RegisterType(
//      BoolType,
//      BytesType,
//      DoubleType,
//      DurationType,
//      IntType,
//      ListType,
//      MapType,
//      NullType,
//      StringType,
//      TimestampType,
//      TypeType,
//      UintType)
//    if err != nil {
//      return nil, err
//    }
//    // This block ensures that the well-known protobuf types are registered by default.
//    for _, fd := range p.pbdb.FileDescriptions() {
//      err = p.registerAllTypes(fd)
//      if err != nil {
//        return nil, err
//      }
//    }
//    for _, msgType := range types {
//      err = p.RegisterMessage(msgType)
//      if err != nil {
//        return nil, err
//      }
//    }
//    return p, nil
//  }
//
//  /**NewEmptyRegistry returns a registry which is completely unconfigured. */
//  func NewEmptyRegistry() ref.TypeRegistry {
//    return &protoTypeRegistry{
//      revTypeMap: make(map[string]ref.Type),
//      pbdb:       pb.NewDb(),
//    }
//  }
//
//  /**Copy implements the ref.TypeRegistry interface method which copies the current state of the
//   * registry into its own memory space. */
//  func (p *protoTypeRegistry) Copy() ref.TypeRegistry {
//    copy := &protoTypeRegistry{
//      revTypeMap: make(map[string]ref.Type),
//      pbdb:       p.pbdb.Copy(),
//    }
//    for k, v := range p.revTypeMap {
//      copy.revTypeMap[k] = v
//    }
//    return copy
//  }
//
//  func (p *protoTypeRegistry) EnumValue(enumName string) ref.Val {
//    enumVal, found := p.pbdb.DescribeEnum(enumName)
//    if !found {
//      return NewErr("unknown enum name '%s'", enumName)
//    }
//    return Int(enumVal.Value())
//  }
//
//  func (p *protoTypeRegistry) FindFieldType(messageType string,
//    fieldName string) (*ref.FieldType, bool) {
//    msgType, found := p.pbdb.DescribeType(messageType)
//    if !found {
//      return nil, false
//    }
//    field, found := msgType.FieldByName(fieldName)
//    if !found {
//      return nil, false
//    }
//    return &ref.FieldType{
//        Type:    field.CheckedType(),
//        IsSet:   field.IsSet,
//        GetFrom: field.GetFrom},
//      true
//  }
//
//  func (p *protoTypeRegistry) FindIdent(identName string) (ref.Val, bool) {
//    if t, found := p.revTypeMap[identName]; found {
//      return t.(ref.Val), true
//    }
//    if enumVal, found := p.pbdb.DescribeEnum(identName); found {
//      return Int(enumVal.Value()), true
//    }
//    return nil, false
//  }
//
//  func (p *protoTypeRegistry) FindType(typeName string) (*exprpb.Type, bool) {
//    if _, found := p.pbdb.DescribeType(typeName); !found {
//      return nil, false
//    }
//    if typeName != "" && typeName[0] == '.' {
//      typeName = typeName[1:]
//    }
//    return &exprpb.Type{
//      TypeKind: &exprpb.Type_Type{
//        Type: &exprpb.Type{
//          TypeKind: &exprpb.Type_MessageType{
//            MessageType: typeName}}}}, true
//  }
//
//  func (p *protoTypeRegistry) NewValue(typeName string, fields map[string]ref.Val) ref.Val {
//    td, found := p.pbdb.DescribeType(typeName)
//    if !found {
//      return NewErr("unknown type '%s'", typeName)
//    }
//    msg := td.New()
//    fieldMap := td.FieldMap()
//    for name, value := range fields {
//      field, found := fieldMap[name]
//      if !found {
//        return NewErr("no such field: %s", name)
//      }
//      err := msgSetField(msg, field, value)
//      if err != nil {
//        return &Err{err}
//      }
//    }
//    return p.NativeToValue(msg.Interface())
//  }
//
//  func (p *protoTypeRegistry) RegisterDescriptor(fileDesc protoreflect.FileDescriptor) error {
//    fd, err := p.pbdb.RegisterDescriptor(fileDesc)
//    if err != nil {
//      return err
//    }
//    return p.registerAllTypes(fd)
//  }
//
//  func (p *protoTypeRegistry) RegisterMessage(message proto.Message) error {
//    fd, err := p.pbdb.RegisterMessage(message)
//    if err != nil {
//      return err
//    }
//    return p.registerAllTypes(fd)
//  }
//
//  func (p *protoTypeRegistry) RegisterType(types ...ref.Type) error {
//    for _, t := range types {
//      p.revTypeMap[t.TypeName()] = t
//    }
//    // TODO: generate an error when the type name is registered more than once.
//    return nil
//  }
//
//  /**NativeToValue converts various "native" types to ref.Val with this specific implementation
//   * providing support for custom proto-based types.
//   *
//   * This method should be the inverse of ref.Val.ConvertToNative. */
//  func (p *protoTypeRegistry) NativeToValue(value interface{}) ref.Val {
//    if val, found := nativeToValue(p, value); found {
//      return val
//    }
//    switch v := value.(type) {
//    case proto.Message:
//      typeName := string(v.ProtoReflect().Descriptor().FullName())
//      td, found := p.pbdb.DescribeType(typeName)
//      if !found {
//        return NewErr("unknown type: '%s'", typeName)
//      }
//      unwrapped, isUnwrapped := td.MaybeUnwrap(v)
//      if isUnwrapped {
//        return p.NativeToValue(unwrapped)
//      }
//      typeVal, found := p.FindIdent(typeName)
//      if !found {
//        return NewErr("unknown type: '%s'", typeName)
//      }
//      return NewObject(p, td, typeVal.(*TypeValue), v)
//    case *pb.Map:
//      return NewProtoMap(p, v)
//    case protoreflect.List:
//      return NewProtoList(p, v)
//    case protoreflect.Message:
//      return p.NativeToValue(v.Interface())
//    case protoreflect.Value:
//      return p.NativeToValue(v.Interface())
//    }
//    return UnsupportedRefValConversionErr(value)
//  }
//
//  func (p *protoTypeRegistry) registerAllTypes(fd *pb.FileDescription) error {
//    for _, typeName := range fd.GetTypeNames() {
//      err := p.RegisterType(NewObjectTypeValue(typeName))
//      if err != nil {
//        return err
//      }
//    }
//    return nil
//  }
//
// }
