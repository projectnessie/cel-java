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
  signing
  `maven-publish`
  id("com.diffplug.spotless")
  alias(libs.plugins.jmh)
  alias(libs.plugins.aggregatetestresults)
  alias(libs.plugins.testsummary)
  alias(libs.plugins.testrerun)
  `cel-conventions`
  `java-test-fixtures`
}

configurations.named("jmhImplementation") { extendsFrom(configurations.testFixturesApi.get()) }

dependencies {
  implementation(project(":cel-generated-antlr", "shadow"))
  api(project(":cel-generated-pb"))

  implementation(libs.agrona)

  testFixturesApi(platform(libs.junit.bom))
  testFixturesApi(libs.bundles.junit.testing)
  testFixturesApi(libs.protobuf.java)
  testFixturesApi(libs.guava)
  testFixturesApi(testFixtures(project(":cel-generated-pb")))
  testFixturesApi(project(":cel-tools"))
  testRuntimeOnly(libs.junit.jupiter.engine)

  jmhImplementation(libs.jmh.core)
  jmhAnnotationProcessor(libs.jmh.generator.annprocess)
}

jmh { jmhVersion.set(libs.versions.jmh.get()) }

sourceSets.test {
  java.srcDir(layout.buildDirectory.dir("generated/source/proto/test/java"))
  java.destinationDirectory.set(layout.buildDirectory.dir("classes/java/generatedTest"))
}

tasks.named("check") { dependsOn(tasks.named("jmh")) }

tasks.named("assemble") { dependsOn(tasks.named("jmhJar")) }
