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
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.compile.JavaCompile

plugins {
  `java-library`
  `maven-publish`
  signing
  id("cel-conventions")
  `java-test-fixtures`
}

apply<ProtobufPlugin>()

val syncedMainProtoDir = layout.buildDirectory.dir("pb-src/proto")
val mainProtoResourcesDir = layout.buildDirectory.dir("generated/proto-resources/main")
val syncMainProtoSources =
  tasks.register<Sync>("syncMainProtoSources") {
    into(syncedMainProtoDir)
    from(layout.settingsDirectory.dir("submodules/googleapis/google/rpc")) { into("google/rpc") }
    from(layout.settingsDirectory.dir("submodules/googleapis/google/api/expr/v1alpha1")) {
      into("google/api/expr/v1alpha1")
    }
  }

val emptyTestProtoDir = layout.buildDirectory.dir("pb-src/test/proto")
val syncedTestFixturesProtoDir = layout.buildDirectory.dir("pb-src/testFixtures/proto")
val testFixturesProtoResourcesDir =
  layout.buildDirectory.dir("generated/proto-resources/testFixtures")
val syncTestFixturesProtoSources =
  tasks.register<Sync>("syncTestFixturesProtoSources") {
    into(syncedTestFixturesProtoDir)
    from(layout.settingsDirectory.dir("generated-pb/src/testFixtures/proto")) { include("*.proto") }
    from(layout.settingsDirectory.dir("submodules/cel-spec/proto/test/v1/proto2")) {
      into("proto/test/v1/proto2")
    }
    from(layout.settingsDirectory.dir("submodules/cel-spec/proto/test/v1/proto3")) {
      into("proto/test/v1/proto3")
    }
  }

sourceSets.main {
  java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/sources/proto/main/java")))
  java.destinationDirectory.set(layout.buildDirectory.dir("classes/java/generated"))
  resources.setSrcDirs(listOf(mainProtoResourcesDir))
  extensions.configure<SourceDirectorySet>("proto") {
    setSrcDirs(listOf(syncedMainProtoDir))
  }
}

sourceSets.test {
  java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/sources/proto/test/java")))
  java.destinationDirectory.set(layout.buildDirectory.dir("classes/java/generatedTest"))
  resources.setSrcDirs(emptyList<Any>())
  extensions.configure<SourceDirectorySet>("proto") {
    setSrcDirs(listOf(emptyTestProtoDir))
  }
}

sourceSets.testFixtures {
  java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/sources/proto/testFixtures/java")))
  resources.setSrcDirs(listOf(testFixturesProtoResourcesDir))
  extensions.configure<SourceDirectorySet>("proto") {
    setSrcDirs(listOf(syncedTestFixturesProtoDir))
  }
}

dependencies {
  api(libs.protobuf.java) { version { strictly(libs.versions.protobuf3.get()) } }

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
    artifact = "com.google.protobuf:protoc:${libs.versions.protobuf3.get()}"
  }
}

tasks.named("generateProto") { dependsOn(syncMainProtoSources) }

tasks.named<JavaCompile>("compileJava") { dependsOn(tasks.named("generateProto")) }

tasks.named("processProtoResources") { dependsOn(syncMainProtoSources) }

tasks.named("processResources") { dependsOn(tasks.named("processProtoResources")) }

tasks.named<Jar>("sourcesJar") {
  dependsOn(tasks.named("generateProto"))
  dependsOn(tasks.named("processProtoResources"))
}

tasks.named("generateTestFixturesProto") { dependsOn(syncTestFixturesProtoSources) }

tasks.named<JavaCompile>("compileTestFixturesJava") {
  dependsOn(tasks.named("generateTestFixturesProto"))
}

tasks.named("processTestFixturesProtoResources") { dependsOn(syncTestFixturesProtoSources) }

tasks.named("processTestFixturesResources") {
  dependsOn(tasks.named("processTestFixturesProtoResources"))
}
