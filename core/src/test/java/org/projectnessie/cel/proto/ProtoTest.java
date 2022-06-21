/*
 * Copyright (C) 2022 The Authors of CEL-Java
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
package org.projectnessie.cel.proto;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.Env.newCustomEnv;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.Library.StdLib;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.projectnessie.cel.Ast;
import org.projectnessie.cel.Env;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.Program.EvalResult;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.pb.ProtoTypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.proto.tests.ProtoTestTypes;

public class ProtoTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "event.property_bool == true && event2.map_str['key'] == 'value'",
        "event.property_bool == true && event.map_str['key'] == 'value'"
      })
  void protobufMaps(String expression) throws Exception {
    ProtoTestTypes.EventA generatedEventA =
        ProtoTestTypes.EventA.getDefaultInstance().toBuilder()
            .setPropertyBool(true)
            .putMapStr("key", "value")
            .putMapStr("key2", "value2")
            .build();

    Descriptors.Descriptor descriptor = ProtoTestTypes.EventA.getDescriptor();
    DynamicMessage dynamicType = DynamicMessage.newBuilder(descriptor).build();

    List<EnvOption> envOptions = new ArrayList<>();
    envOptions.add(StdLib());
    envOptions.add(
        declarations(
            Decls.newVar(
                "event", Decls.newObjectType(dynamicType.getDescriptorForType().getFullName())),
            Decls.newVar(
                "event2",
                Decls.newObjectType(ProtoTestTypes.EventA.getDescriptor().getFullName()))));
    envOptions.add(types(dynamicType.getDefaultInstanceForType()));

    Env env = newCustomEnv(ProtoTypeRegistry.newRegistry(), envOptions);

    AstIssuesTuple astIss = env.parse(expression);
    assertThat(astIss).extracting(AstIssuesTuple::hasIssues).isEqualTo(FALSE);
    Ast ast = astIss.getAst();

    astIss = env.check(ast);
    assertThat(astIss).extracting(AstIssuesTuple::hasIssues).isEqualTo(FALSE);

    Program prg = env.program(ast);

    DynamicMessage dynamicEventA =
        dynamicType.getParserForType().parseFrom(generatedEventA.toByteString());
    ProtoTestTypes.EventA parsedUsingGeneratedCodeEvent =
        ProtoTestTypes.EventA.parseFrom(generatedEventA.toByteString());

    Map<String, Object> arguments = new HashMap<>();
    arguments.put("event", dynamicEventA);
    arguments.put("event2", parsedUsingGeneratedCodeEvent);
    EvalResult result = prg.eval(arguments);
    Val val = result.getVal();
    assertThat(val).extracting(Val::booleanValue).isEqualTo(TRUE);
  }
}
