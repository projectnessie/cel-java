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
package org.projectnessie.cel.types.jackson.types;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable(prehash = true)
@JsonSerialize(as = ImmutableCommitMeta.class)
@JsonDeserialize(as = ImmutableCommitMeta.class)
public abstract class CommitMeta {

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

  public ImmutableCommitMeta.Builder toBuilder() {
    return ImmutableCommitMeta.builder().from(this);
  }

  public static ImmutableCommitMeta.Builder builder() {
    return ImmutableCommitMeta.builder();
  }

  public static CommitMeta fromMessage(String message) {
    return ImmutableCommitMeta.builder().message(message).build();
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
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
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
    public Instant deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {
      return Instant.parse(p.getText());
    }
  }
}
