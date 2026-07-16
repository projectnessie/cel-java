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
  id("com.gradleup.shadow")
  id("cel-conventions")
}

val standaloneShadow =
  configurations.create("standaloneShadow") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
  }

dependencies {
  api(project(":cel-tools"))
  api(project(":cel-jackson"))
  api(project(":cel-jackson3"))
  api(project(":cel-generated-antlr"))

  compileOnly(project(":cel-generated-pb"))
  compileOnly(libs.protobuf.java)
  compileOnly(libs.agrona)

  standaloneShadow(project(":cel-core"))
  standaloneShadow(project(":cel-tools"))
  standaloneShadow(project(":cel-jackson"))
  standaloneShadow(project(":cel-jackson3"))
  standaloneShadow(project(":cel-generated-antlr"))
  standaloneShadow(project(":cel-generated-pb"))
  standaloneShadow(libs.protobuf.java)
  standaloneShadow(libs.agrona)
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
  configurations = listOf(standaloneShadow)
}

tasks.named("compileJava").configure { finalizedBy(shadowJar) }

tasks.named("processResources").configure { finalizedBy(shadowJar) }

tasks.named("jar").configure { dependsOn("shadowJar") }

shadowJar.configure {
  outputs.cacheIf { false } // do not cache uber/shaded jars
  archiveClassifier.set("")
  exclude("META-INF/native-image/**/native-image.properties")
  mergeServiceFiles()
  transform(NativeImageReflectionConfigTransformer::class.java)
}

tasks.named<Jar>("jar").configure {
  dependsOn(shadowJar)
  archiveClassifier.set("raw")
}

tasks.withType<ShadowJar>().configureEach { exclude("META-INF/jandex.idx") }

// The following makes :cel-standalone consumable from an including build

shadow {
  addShadowVariantIntoJavaComponent = false
}

listOf("shadowApiElements", "shadowRuntimeElements").forEach { configurationName ->
  configurations.named(configurationName) {
    isCanBeConsumed = false
  }
}

listOf("apiElements", "runtimeElements").forEach { configurationName ->
  configurations.named(configurationName) {
    outgoing.artifacts.clear()
    outgoing.artifact(shadowJar)
    outgoing.variants.removeAll { true }
    attributes {
      attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
    }
  }
}
