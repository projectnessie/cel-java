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

val baseVersion = "0.0"

pluginManagement {
  repositories { gradlePluginPortal() }
  plugins {
    id("com.diffplug.spotless") version "5.12.5"
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.0"
  }
}

rootProject.name = "cel-java"

gradle.beforeProject {
  group = "org.projectnessie.cel"
  version = "${baseVersion}${if (!hasProperty("release")) "-SNAPSHOT" else ""}"
  description =
    when (name) {
      "cel-java" -> "Common-Expression-Language - Java implementation"
      "core" -> "Common-Expression-Language - Java - Core Module"
      else -> name
    }
}

include("core")
