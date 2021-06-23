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

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TypeT.TypeType;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newRegistry;

import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.ObjectT;
import org.projectnessie.cel.common.types.ref.Type;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Indexer;

public class PbObjectTest {

  @Test
  void newProtoObject() {
    ProtoTypeRegistry reg = newRegistry();
    ParsedExpr parsedExpr =
        ParsedExpr.newBuilder()
            .setSourceInfo(
                SourceInfo.newBuilder().addAllLineOffsets(Arrays.asList(1, 2, 3)).build())
            .build();
    reg.registerMessage(parsedExpr);
    Indexer obj = (Indexer) reg.nativeToValue(parsedExpr);
    Indexer si = (Indexer) obj.get(stringOf("source_info"));
    Indexer lo = (Indexer) si.get(stringOf("line_offsets"));
    assertThat(lo.get(intOf(2)).equal(intOf(3))).isSameAs(True);

    Indexer expr = (Indexer) obj.get(stringOf("expr"));
    Indexer call = (Indexer) expr.get(stringOf("call_expr"));
    assertThat(call.get(stringOf("function")).equal(stringOf(""))).isSameAs(True);
  }

  @Test
  void protoObjectConvertToNative() throws Exception {
    TypeRegistry reg = newRegistry(Expr.getDefaultInstance());
    ParsedExpr msg =
        ParsedExpr.newBuilder()
            .setSourceInfo(
                SourceInfo.newBuilder().addAllLineOffsets(Arrays.asList(1, 2, 3)).build())
            .build();
    Val objVal = reg.nativeToValue(msg);

    // Proto Message
    ParsedExpr val = objVal.convertToNative(ParsedExpr.class);
    assertThat(val).isEqualTo(msg);

    // Dynamic protobuf
    Val dynPB =
        reg.newValue(
            msg.getDescriptorForType().getFullName(),
            Collections.singletonMap("source_info", reg.nativeToValue(msg.getSourceInfo())));
    Val dynVal = reg.nativeToValue(dynPB);
    val = dynVal.convertToNative(msg.getClass());
    assertThat(val).isEqualTo(msg);

    // google.protobuf.Any
    Any anyVal = objVal.convertToNative(Any.class);
    Message unpackedAny = anyVal.unpack(ParsedExpr.class);
    assertThat(unpackedAny).isEqualTo(objVal.value());
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void protoObjectConvertToNative_JSON() {
    // TODO this is the rest of the above test, the missing JSON part
    //    // JSON
    //    Value jsonVal = objVal.convertToNative(Value.class);
    //    jsonBytes = protojson.Marshal(jsonVal.(proto.Message))
    //    jsonTxt = string(jsonBytes)
    //    outMap := map[string]interface{}{}
    //    err = json.Unmarshal(jsonBytes, &outMap)
    //    want := map[string]interface{}{
    //      "sourceInfo": map[string]interface{}{
    //        "lineOffsets": []interface{}{1.0, 2.0, 3.0},
    //      },
    //    }
    //    if !reflect.DeepEqual(outMap, want) {
    //      t.Errorf("got json '%v', expected %v", outMap, want)
    //    }
  }

  @Test
  void protoObjectIsSet() {
    TypeRegistry reg = newRegistry(Expr.getDefaultInstance());
    ParsedExpr msg =
        ParsedExpr.newBuilder()
            .setSourceInfo(
                SourceInfo.newBuilder().addAllLineOffsets(Arrays.asList(1, 2, 3)).build())
            .build();

    Val obj = reg.nativeToValue(msg);
    assertThat(obj).isInstanceOf(ObjectT.class);
    ObjectT objVal = (ObjectT) obj;

    assertThat(objVal.isSet(stringOf("source_info"))).isSameAs(True);
    assertThat(objVal.isSet(stringOf("expr"))).isSameAs(False);
    assertThat(objVal.isSet(stringOf("bad_field"))).matches(Err::isError);
    assertThat(objVal.isSet(IntZero)).matches(Err::isError);
  }

  @Test
  void protoObjectGet() {
    TypeRegistry reg = newRegistry(Expr.getDefaultInstance());
    ParsedExpr msg =
        ParsedExpr.newBuilder()
            .setSourceInfo(
                SourceInfo.newBuilder().addAllLineOffsets(Arrays.asList(1, 2, 3)).build())
            .build();

    Val obj = reg.nativeToValue(msg);
    assertThat(obj).isInstanceOf(ObjectT.class);
    ObjectT objVal = (ObjectT) obj;

    assertThat(objVal.get(stringOf("source_info")).equal(reg.nativeToValue(msg.getSourceInfo())))
        .isSameAs(True);
    assertThat(objVal.get(stringOf("expr")).equal(reg.nativeToValue(Expr.getDefaultInstance())))
        .isSameAs(True);
    assertThat(objVal.get(stringOf("bad_field"))).matches(Err::isError);
    assertThat(objVal.get(IntZero)).matches(Err::isError);
  }

  @Test
  void protoObjectConvertToType() {
    TypeRegistry reg = newRegistry(Expr.getDefaultInstance());
    ParsedExpr msg =
        ParsedExpr.newBuilder()
            .setSourceInfo(
                SourceInfo.newBuilder().addAllLineOffsets(Arrays.asList(1, 2, 3)).build())
            .build();

    Val obj = reg.nativeToValue(msg);
    assertThat(obj).isInstanceOf(ObjectT.class);
    ObjectT objVal = (ObjectT) obj;

    Type tv = objVal.type();
    assertThat(objVal.convertToType(TypeType).equal(tv)).isSameAs(True);
    assertThat(objVal.convertToType(objVal.type())).isSameAs(objVal);
  }
}
