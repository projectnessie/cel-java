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
import static org.projectnessie.cel.common.types.pb.Db.newDb;

import com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes;

/** Required by {@link UnwrapTestCase} et al. */
class UnwrapContext {

  final Db pbdb;
  final PbTypeDescription msgDesc;

  UnwrapContext() {
    pbdb = newDb();
    pbdb.registerMessage(TestAllTypes.getDefaultInstance());
    String msgType = "google.protobuf.Value";
    msgDesc = pbdb.describeType(msgType);
    assertThat(msgDesc).isNotNull();
  }

  private static UnwrapContext instance;

  static synchronized UnwrapContext get() {
    if (instance == null) {
      instance = new UnwrapContext();
    }
    return instance;
  }
}
