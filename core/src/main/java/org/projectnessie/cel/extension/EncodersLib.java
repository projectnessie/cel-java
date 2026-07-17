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

import static java.util.Collections.singletonList;
import static org.projectnessie.cel.common.types.BytesT.bytesOf;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.StringT.stringOf;

import java.util.Base64;
import java.util.List;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.BytesT;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.interpreter.functions.Overload;

/** EncodersLib provides CEL helper functions for common binary-to-text encodings. */
public final class EncodersLib implements Library {
  private static final String BASE64_ENCODE = "base64.encode";
  private static final String BASE64_DECODE = "base64.decode";
  private static final String BASE64_ENCODE_OVERLOAD = "base64_encode_bytes";
  private static final String BASE64_DECODE_OVERLOAD = "base64_decode_string";

  private EncodersLib() {}

  public static EnvOption encoders() {
    return Library.Lib(new EncodersLib());
  }

  @Override
  public List<EnvOption> getCompileOptions() {
    return List.of(
        EnvOption.declarations(
            Decls.newFunction(
                BASE64_ENCODE,
                Decls.newOverload(
                    BASE64_ENCODE_OVERLOAD, singletonList(Decls.Bytes), Decls.String)),
            Decls.newFunction(
                BASE64_DECODE,
                Decls.newOverload(
                    BASE64_DECODE_OVERLOAD, singletonList(Decls.String), Decls.Bytes))));
  }

  @Override
  public List<ProgramOption> getProgramOptions() {
    return List.of(
        ProgramOption.functions(
            Overload.unary(BASE64_ENCODE, EncodersLib::base64Encode),
            Overload.unary(BASE64_ENCODE_OVERLOAD, EncodersLib::base64Encode),
            Overload.unary(BASE64_DECODE, EncodersLib::base64Decode),
            Overload.unary(BASE64_DECODE_OVERLOAD, EncodersLib::base64Decode)));
  }

  private static Val base64Encode(Val value) {
    if (!(value instanceof BytesT)) {
      return noSuchOverload(value, BASE64_ENCODE, BASE64_ENCODE_OVERLOAD, new Val[] {value});
    }
    byte[] bytes = value.convertToNative(byte[].class);
    return stringOf(Base64.getEncoder().encodeToString(bytes));
  }

  private static Val base64Decode(Val value) {
    if (!(value instanceof StringT)) {
      return noSuchOverload(value, BASE64_DECODE, BASE64_DECODE_OVERLOAD, new Val[] {value});
    }
    String text = value.convertToNative(String.class);
    try {
      return bytesOf(Base64.getDecoder().decode(text));
    } catch (IllegalArgumentException e) {
      return newErr(e, "invalid base64 string");
    }
  }
}
