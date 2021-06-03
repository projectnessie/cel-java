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

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.BoolT.BoolType;
import static org.projectnessie.cel.common.types.BytesT.BytesType;
import static org.projectnessie.cel.common.types.DoubleT.DoubleType;
import static org.projectnessie.cel.common.types.DurationT.DurationType;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.ListT.ListType;
import static org.projectnessie.cel.common.types.MapT.MapType;
import static org.projectnessie.cel.common.types.NullT.NullType;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.TimestampT.TimestampType;
import static org.projectnessie.cel.common.types.TypeValue.TypeType;
import static org.projectnessie.cel.common.types.UintT.UintType;

import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.ref.Val;

public class TestType {

  @Test
  void typeConvertToType() {
    TypeValue[] stdTypes =
        new TypeValue[] {
          BoolType,
          BytesType,
          DoubleType,
          DurationType,
          IntType,
          ListType,
          MapType,
          NullType,
          StringType,
          TimestampType,
          TypeType,
          UintType,
        };
    for (TypeValue stdType : stdTypes) {
      Val cnv = stdType.convertToType(TypeType);
      assertThat(cnv).isEqualTo(TypeType);
    }
  }

  @Test
  void typeType() {
    assertThat(TypeType.type()).isSameAs(TypeType);
  }
}
