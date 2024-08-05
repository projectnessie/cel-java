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
  id("com.github.johnrengelman.shadow")
  id("org.caffinitas.gradle.aggregatetestresults")
  id("org.caffinitas.gradle.testsummary")
  id("org.caffinitas.gradle.testrerun")
  `cel-conventions`
}

apply<ProtobufPlugin>()

sourceSets.main {
  java.srcDir(layout.buildDirectory.dir("generated/source/proto/main/java"))
  java.srcDir(layout.buildDirectory.dir("generated/source/proto/main/grpc"))
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
  testRuntimeOnly(libs.junit.jupiter.engine)
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

// The protobuf-plugin should ideally do this
tasks.named<Jar>("sourcesJar") { dependsOn(tasks.named("generateProto")) }
