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
  signing
  `maven-publish`
  id("com.diffplug.spotless")
  alias(libs.plugins.jmh)
  alias(libs.plugins.aggregatetestresults)
  alias(libs.plugins.testsummary)
  alias(libs.plugins.testrerun)
  `cel-conventions`
}

apply<ProtobufPlugin>()

dependencies {
  implementation(project(":cel-generated-antlr", "shadow"))
  api(project(":cel-generated-pb"))

  implementation(libs.agrona)

  testImplementation(project(":cel-generated-pb", "testJar"))

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.bundles.junit.testing)
  testRuntimeOnly(libs.junit.jupiter.engine)

  jmhImplementation(libs.jmh.core)
  jmhAnnotationProcessor(libs.jmh.generator.annprocess)
}

jmh { jmhVersion.set(libs.versions.jmh.get()) }

tasks.named("compileJmhJava") { dependsOn(tasks.named("processTestJandexIndex")) }

tasks.named("jmhCompileGeneratedClasses") { dependsOn(tasks.named("processJmhJandexIndex")) }

tasks.named("jmhRunBytecodeGenerator") { dependsOn(tasks.named("processJmhJandexIndex")) }

sourceSets.test {
  java.srcDir(project.buildDir.resolve("generated/source/proto/test/java"))
  java.destinationDirectory.set(project.buildDir.resolve("classes/java/generatedTest"))
}

configure<ProtobufExtension> {
  // Configure the protoc executable
  protoc {
    // Download from repositories
    artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
  }
}

val testJar by
  configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = true
  }

tasks.register<Jar>("testJar") {
  val testClasses = tasks.getByName<JavaCompile>("compileTestJava")
  val baseJar = tasks.getByName<Jar>("jar")
  from(testClasses.destinationDirectory)
  dependsOn(testClasses)
  dependsOn(tasks.named("processTestResources"))
  archiveBaseName.set(baseJar.archiveBaseName)
  destinationDirectory.set(baseJar.destinationDirectory)
  archiveClassifier.set("tests")
}

artifacts {
  val testJar = tasks.getByName<Jar>("testJar")
  add("testJar", testJar.archiveFile) { builtBy(testJar) }
}

tasks.named("check") { dependsOn(tasks.named("jmh")) }

tasks.named("assemble") { dependsOn(tasks.named("jmhJar")) }
