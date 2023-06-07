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
  id("org.caffinitas.gradle.aggregatetestresults")
  id("org.caffinitas.gradle.testsummary")
  id("org.caffinitas.gradle.testrerun")
  `cel-conventions`
}

apply<ProtobufPlugin>()

dependencies {
  api(project(":cel-core"))

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.bundles.junit.testing)
  testRuntimeOnly(libs.junit.jupiter.engine)
}

configure<ProtobufExtension> {
  // Configure the protoc executable
  protoc {
    // Download from repositories
    artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
  }
}

tasks.named("extractIncludeTestProto") {
  dependsOn(":cel-core:processJandexIndex", ":cel-generated-pb:processJandexIndex")
}

tasks.named("extractIncludeProto") {
  dependsOn(":cel-core:processJandexIndex", ":cel-generated-pb:processJandexIndex")
}
