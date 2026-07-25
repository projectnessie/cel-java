/*
 * Copyright (C) 2026 The Authors of CEL-Java
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
  alias(libs.plugins.jmh)
  id("cel-conventions")
}

configurations.all {
  exclude(group = "org.projectnessie.cel", module = "cel-generated-pb")
}

dependencies {
  implementation(project(":cel-core"))
  implementation(project(":cel-jackson3"))
  implementation(project(":cel-generated-pb3"))
  implementation(testFixtures(project(":cel-generated-pb3")))
  implementation(libs.protobuf.java) { version { strictly(libs.versions.protobuf3.get()) } }

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.bundles.junit.testing)
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")

  jmhImplementation(libs.jmh.core)
  jmhAnnotationProcessor(libs.jmh.generator.annprocess)
}

jmh { jmhVersion.set(libs.versions.jmh.get()) }

tasks.named("assemble") { dependsOn(tasks.named("jmhJar")) }
