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

plugins {
  `java-library`
  `maven-publish`
  signing
  jacoco
  id("com.diffplug.spotless")
  id("me.champeau.jmh")
  id("org.caffinitas.gradle.aggregatetestresults")
  id("org.caffinitas.gradle.testsummary")
  id("org.caffinitas.gradle.testrerun")
}

dependencies {
  implementation(project(":generated-antlr", "shadow"))
  api(project(":generated-pb"))

  compileOnly(platform(rootProject))

  implementation("org.agrona:agrona:${rootProject.extra["versionAgrona"]}")

  testImplementation(platform(rootProject))
  testImplementation(project(":generated-pb", "testJar"))
  testImplementation("org.assertj:assertj-core")
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

  jmhImplementation(platform(rootProject))
  jmhAnnotationProcessor(platform(rootProject))

  jmhImplementation("org.openjdk.jmh:jmh-core")
  jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess")
}

jmh { jmhVersion.set(rootProject.extra["versionJmh"] as String) }

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
