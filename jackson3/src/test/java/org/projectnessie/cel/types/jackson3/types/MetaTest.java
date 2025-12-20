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
package org.projectnessie.cel.types.jackson3.types;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.ser.std.StdSerializer;

@Value.Immutable(prehash = true)
@JsonSerialize(as = ImmutableMetaTest.class)
@JsonDeserialize(as = ImmutableMetaTest.class)
public abstract class MetaTest {

  @Nullable
  public abstract String getHash();

  @Nullable
  public abstract String getCommitter();

  @Nullable
  public abstract String getAuthor();

  @Nullable
  public abstract String getSignedOffBy();

  @Nullable
  public abstract String getMessage();

  @Nullable
  @JsonSerialize(using = InstantSerializer.class)
  @JsonDeserialize(using = InstantDeserializer.class)
  public abstract Instant getCommitTime();

  @Nullable
  @JsonSerialize(using = InstantSerializer.class)
  @JsonDeserialize(using = InstantDeserializer.class)
  public abstract Instant getAuthorTime();

  public abstract Map<String, String> getProperties();

  public ImmutableMetaTest.Builder toBuilder() {
    return ImmutableMetaTest.builder().from(this);
  }

  public static ImmutableMetaTest.Builder builder() {
    return ImmutableMetaTest.builder();
  }

  public static MetaTest fromMessage(String message) {
    return ImmutableMetaTest.builder().message(message).build();
  }

  public static class InstantSerializer extends StdSerializer<Instant> {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

    public InstantSerializer() {
      this(Instant.class);
    }

    protected InstantSerializer(Class<Instant> t) {
      super(t);
    }

    @Override
    public void serialize(
        Instant value, JsonGenerator gen, SerializationContext serializationContext) {
      gen.writeString(FORMATTER.format(value));
    }
  }

  public static class InstantDeserializer extends StdDeserializer<Instant> {
    public InstantDeserializer() {
      this(null);
    }

    protected InstantDeserializer(Class<?> vc) {
      super(vc);
    }

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) {
      return Instant.parse(p.getString());
    }
  }
}
