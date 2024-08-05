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

import com.google.protobuf.gradle.ProtobufExtension
import com.google.protobuf.gradle.ProtobufPlugin

plugins {
  `java-library`
  `maven-publish`
  signing
  `cel-conventions`
  `java-test-fixtures`
}

apply<ProtobufPlugin>()

sourceSets.main {
  java.srcDir(layout.buildDirectory.dir("generated/source/proto/main/java"))
  java.destinationDirectory.set(layout.buildDirectory.dir("classes/java/generated"))
}

sourceSets.test {
  java.srcDir(layout.buildDirectory.dir("generated/source/proto/test/java"))
  java.destinationDirectory.set(layout.buildDirectory.dir("classes/java/generatedTest"))
}

dependencies {
  api(libs.protobuf.java)

  // Since we need the protobuf stuff in this cel-core module, it's easy to generate the
  // gRPC code as well. But do not expose the gRPC dependencies "publicly".
  compileOnly(libs.grpc.protobuf)
  compileOnly(libs.grpc.stub)
  compileOnly(libs.tomcat.annotations.api)
}

// *.proto files taken from https://github.com/googleapis/googleapis/ repo, available as a git
// submodule
configure<ProtobufExtension> {
  // Configure the protoc executable
  protoc {
    // Download from repositories
    artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
  }
}
