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
  id("com.gradleup.shadow")
  id("cel-conventions")
}

apply<ProtobufPlugin>()

val syncedMainProtoDir = layout.buildDirectory.dir("pb-src/proto")
val mainProtoResourcesDir = layout.buildDirectory.dir("generated/proto-resources/main")
val syncMainProtoSources =
  tasks.register<Sync>("syncMainProtoSources") {
    into(syncedMainProtoDir)
    from(layout.settingsDirectory.dir("submodules/googleapis/google/rpc")) { into("google/rpc") }
    from(layout.settingsDirectory.dir("submodules/googleapis/google/api/expr/conformance")) {
      into("google/api/expr/conformance")
    }
  }

val emptyTestProtoDir = layout.buildDirectory.dir("pb-src/test/proto")

sourceSets.main {
  java.setSrcDirs(
    listOf(
      layout.projectDirectory.dir("src/main/java"),
      layout.buildDirectory.dir("generated/sources/proto/main/java"),
      layout.buildDirectory.dir("generated/sources/proto/main/grpc"),
    )
  )
  resources.setSrcDirs(listOf(mainProtoResourcesDir))
  extensions.configure<SourceDirectorySet>("proto") {
    setSrcDirs(listOf(syncedMainProtoDir))
  }
}

sourceSets.test {
  java.setSrcDirs(listOf(layout.projectDirectory.dir("src/test/java")))
  resources.setSrcDirs(emptyList<Any>())
  extensions.configure<SourceDirectorySet>("proto") {
    setSrcDirs(listOf(emptyTestProtoDir))
  }
}

configurations.all { exclude(group = "org.projectnessie.cel", module = "cel-generated-pb") }

dependencies {
  implementation(project(":cel-core"))
  implementation(testFixtures(project(":cel-core")))
  implementation(testFixtures(project(":cel-generated-pb3")))

  implementation(libs.protobuf.java) { version { strictly(libs.versions.protobuf3.get()) } }

  implementation(libs.grpc.protobuf)
  implementation(libs.grpc.stub)
  runtimeOnly(libs.grpc.netty.shaded)
  compileOnly(libs.tomcat.annotations.api)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.bundles.junit.testing)
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Jar>("shadowJar") {
  manifest { attributes("Main-Class" to "org.projectnessie.cel.server.ConformanceServer") }
}

// *.proto files taken from https://github.com/google/cel-spec/ repo, available as a git submodule
configure<ProtobufExtension> {
  // Configure the protoc executable
  protoc {
    // Download from repositories
    artifact = "com.google.protobuf:protoc:${libs.versions.protobuf3.get()}"
  }
  plugins {
    this.create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}" }
  }
  generateProtoTasks { all().configureEach { this.plugins.create("grpc") {} } }
}

tasks.named("generateProto") { dependsOn(syncMainProtoSources) }

tasks.named<JavaCompile>("compileJava") { dependsOn(tasks.named("generateProto")) }

tasks.named("processProtoResources") { dependsOn(syncMainProtoSources) }

tasks.named("processResources") { dependsOn(tasks.named("processProtoResources")) }

tasks.named("generateTestProto") { enabled = false }

tasks.named("processTestProtoResources") { enabled = false }

// The protobuf-plugin should ideally do this
tasks.named<Jar>("sourcesJar") {
  dependsOn(tasks.named("generateProto"))
  dependsOn(tasks.named("processProtoResources"))
}
