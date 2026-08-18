/*
 * Copyright (C) 2026 The Authors of CEL-Java
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
package org.projectnessie.cel.extension;

import static java.util.Arrays.asList;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;

import java.util.List;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.FieldTester;
import org.projectnessie.cel.common.types.traits.Indexer;
import org.projectnessie.cel.interpreter.functions.Overload;

/**
 * ProtoLib provides CEL protobuf extension helper functions.
 *
 * <p>The extension-name argument is a fully-qualified protobuf extension field name. Expressions
 * that use extension identifiers instead of string literals need those identifiers registered with
 * the type provider, for example through {@link EnvOption#types(Object...)} with protobuf
 * descriptors that contain the extensions.
 */
public final class ProtoLib implements Library {
  private static final String HAS_EXT = "proto.hasExt";
  private static final String GET_EXT = "proto.getExt";
  private static final String HAS_EXT_OVERLOAD = "proto_has_ext";
  private static final String GET_EXT_OVERLOAD = "proto_get_ext";

  private ProtoLib() {}

  public static EnvOption proto() {
    return Library.Lib(new ProtoLib());
  }

  @Override
  public List<EnvOption> getCompileOptions() {
    return List.of(
        EnvOption.declarations(
            Decls.newFunction(
                HAS_EXT,
                Decls.newOverload(HAS_EXT_OVERLOAD, asList(Decls.Dyn, Decls.String), Decls.Bool)),
            Decls.newFunction(
                GET_EXT,
                Decls.newOverload(GET_EXT_OVERLOAD, asList(Decls.Dyn, Decls.String), Decls.Dyn))));
  }

  @Override
  public List<ProgramOption> getProgramOptions() {
    return List.of(
        ProgramOption.functions(
            Overload.binary(HAS_EXT, ProtoLib::hasExt),
            Overload.binary(HAS_EXT_OVERLOAD, ProtoLib::hasExt),
            Overload.binary(GET_EXT, ProtoLib::getExt),
            Overload.binary(GET_EXT_OVERLOAD, ProtoLib::getExt)));
  }

  private static Val hasExt(Val object, Val extensionName) {
    if (!(object instanceof FieldTester) || !(extensionName instanceof StringT)) {
      return noSuchOverload(object, HAS_EXT, HAS_EXT_OVERLOAD, new Val[] {object, extensionName});
    }
    return ((FieldTester) object).isSet(extensionName);
  }

  private static Val getExt(Val object, Val extensionName) {
    if (!(object instanceof Indexer) || !(extensionName instanceof StringT)) {
      return noSuchOverload(object, GET_EXT, GET_EXT_OVERLOAD, new Val[] {object, extensionName});
    }
    return ((Indexer) object).get(extensionName);
  }
}
