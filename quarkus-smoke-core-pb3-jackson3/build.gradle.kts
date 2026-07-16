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
  alias(libs.plugins.quarkus)
  id("cel-conventions")
  `java-library`
}

description = "Quarkus native smoke test for cel-core, cel-generated-pb3, and cel-jackson3"

val testNativeRequested =
  gradle.startParameter.taskNames.any { taskName ->
    taskName == "testNative" || taskName == "${project.path}:testNative"
  }

if (testNativeRequested) {
  quarkus {
    set("native.enabled", "true")
    set("native.container-build", "true")
    set("package.jar.enabled", "false")
  }
}

dependencies {
  implementation(platform(libs.quarkus.bom))
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-rest-jackson")
  implementation(project(":cel-core"))
  implementation(project(":cel-generated-pb3"))
  implementation(testFixtures(project(":cel-generated-pb3")))
  implementation(project(":cel-jackson3"))

  testImplementation("io.quarkus:quarkus-junit5")
  testImplementation("io.rest-assured:rest-assured")
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
