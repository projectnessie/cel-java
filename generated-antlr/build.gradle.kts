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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  `java-library`
  antlr
  `maven-publish`
  signing
  id("com.gradleup.shadow")
  id("cel-conventions")
}

dependencies {
  antlr(libs.antlr.antlr4) // TODO remove from runtime-classpath *sigh*
  implementation(libs.antlr.antlr4.runtime)
}

// The antlr-plugin should ideally do this
tasks.named<Jar>("sourcesJar") { dependsOn(tasks.named("generateGrammarSource")) }

tasks.named<Jar>("jar") { archiveClassifier.set("raw") }

val shadowJar =
  tasks.named<ShadowJar>("shadowJar") {
    // The antlr-plugin should ideally do this
    dependsOn(tasks.named("generateGrammarSource"))

    dependencies { include(dependency("org.antlr:antlr4-runtime")) }
    relocate("org.antlr.v4.runtime", "org.projectnessie.cel.shaded.org.antlr.v4.runtime")
    archiveClassifier.set("")
  }

// The following makes :cel-generated-antlr consumable from an including build

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
