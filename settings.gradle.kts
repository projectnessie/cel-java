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

val baseVersion = file("version.txt").readText().trim()

includeBuild("build-tools")

pluginManagement {
  repositories { gradlePluginPortal() }
  plugins {
    id("com.diffplug.spotless") version "6.4.1"
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.3"
    id("com.google.protobuf") version "0.8.18"
    id("me.champeau.jmh") version "0.6.6"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.caffinitas.gradle.aggregatetestresults") version "0.1"
    id("org.caffinitas.gradle.testsummary") version "0.1.1"
    id("org.caffinitas.gradle.testrerun") version "0.1"
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
  }
}

rootProject.name = "cel-java"

gradle.beforeProject {
  group = "org.projectnessie.cel"
  version = baseVersion
  description =
    when (name) {
      "cel-java" -> "Common-Expression-Language - Java implementation"
      "core" -> "Common-Expression-Language - Java - Core Module"
      "tools" -> "Common-Expression-Language - Script Tools"
      "jackson" -> "Common-Expression-Language - Jackson Type Registry"
      else -> name
    }
}

include("generated-antlr")

include("generated-pb")

include("core")

include("jackson")

include("conformance")

include("tools")

include("jacoco")

include("bom")
