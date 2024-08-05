/*
 * Copyright (C) 2023 The Authors of CEL-Java
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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  `java-library`
  `maven-publish`
  signing
  id("com.github.johnrengelman.shadow")
  `cel-conventions`
}

dependencies {
  api(project(":cel-tools"))
  api(project(":cel-jackson"))
  api(project(":cel-generated-antlr", configuration = "shadow"))

  compileOnly(libs.protobuf.java)
  compileOnly(libs.agrona)
}

val shadowJar = tasks.named<ShadowJar>("shadowJar")

shadowJar.configure {
  // relocate com.google.api/protobuf/rpc classes
  relocate("com.google", "org.projectnessie.cel.relocated.com.google")
  relocate("org.agrona", "org.projectnessie.cel.relocated.org.agrona")
  manifest {
    attributes["Specification-Title"] = "Common-Expression-Language - dependency-free CEL"
    attributes["Specification-Version"] = libs.protobuf.java.get().version
  }
  configurations = listOf(project.configurations.getByName("compileClasspath"))
  dependencies {
    include(project(":cel-tools"))
    include(project(":cel-core"))
    include(project(":cel-jackson"))
    include(project(":cel-generated-pb"))
    include(project(":cel-generated-antlr"))

    include(dependency(libs.protobuf.java.get()))
    include(dependency(libs.agrona.get()))
  }
}

tasks.named("compileJava").configure { finalizedBy(shadowJar) }

tasks.named("processResources").configure { finalizedBy(shadowJar) }

tasks.named("jar").configure { dependsOn("shadowJar") }

shadowJar.configure {
  outputs.cacheIf { false } // do not cache uber/shaded jars
  archiveClassifier.set("")
  mergeServiceFiles()
}

tasks.named<Jar>("jar").configure {
  dependsOn(shadowJar)
  archiveClassifier.set("raw")
}

tasks.withType<ShadowJar>().configureEach { exclude("META-INF/jandex.idx") }
