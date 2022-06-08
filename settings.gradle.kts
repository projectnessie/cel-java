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

pluginManagement {
  // Cannot use a settings-script global variable/value, so pass the 'versions' Properties via
  // settings.extra around.
  val versions = java.util.Properties()
  settings.extra["nessieBuild.versions"] = versions

  val versionNessieBuildPlugins = "0.1.4"
  versions["versionNessieBuildPlugins"] = versionNessieBuildPlugins
  val versionIdeaExtPlugin = "1.1.4"
  versions["versionIdeaExtPlugin"] = versionIdeaExtPlugin
  val versionSpotlessPlugin = "6.7.0"
  versions["versionSpotlessPlugin"] = versionSpotlessPlugin
  val versionJandexPlugin = "1.80"
  versions["versionJandexPlugin"] = versionJandexPlugin
  val versionShadowPlugin = "7.1.2"
  versions["versionShadowPlugin"] = versionShadowPlugin

  // The project's settings.gradle.kts is "executed" before buildSrc's settings.gradle.kts and
  // build.gradle.kts.
  //
  // Plugin and important dependency versions are defined here and shared with buildSrc via
  // a properties file, and via an 'extra' property with all other modules of the Nessie build.
  //
  // This approach works fine with GitHub's dependabot as well
  val nessieBuildVersionsFile = file("build/nessieBuild/versions.properties")
  nessieBuildVersionsFile.parentFile.mkdirs()
  nessieBuildVersionsFile.outputStream().use {
    versions.store(it, "Nessie Build versions from settings.gradle.kts - DO NOT MODIFY!")
  }

  plugins {
    id("com.diffplug.spotless") version versionSpotlessPlugin
    id("com.github.johnrengelman.plugin-shadow") version versionShadowPlugin
    id("com.google.protobuf") version "0.8.18"
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("me.champeau.jmh") version "0.6.6"
    id("org.caffinitas.gradle.aggregatetestresults") version "0.1"
    id("org.caffinitas.gradle.testsummary") version "0.1.1"
    id("org.caffinitas.gradle.testrerun") version "0.1"
    id("org.jetbrains.gradle.plugin.idea-ext") version versionIdeaExtPlugin
    id("org.projectnessie.buildsupport.checkstyle") version versionNessieBuildPlugins
    id("org.projectnessie.buildsupport.errorprone") version versionNessieBuildPlugins
    id("org.projectnessie.buildsupport.ide-integration") version versionNessieBuildPlugins
    id("org.projectnessie.buildsupport.jacoco") version versionNessieBuildPlugins
    id("org.projectnessie.buildsupport.jacoco-aggregator") version versionNessieBuildPlugins
    id("org.projectnessie.buildsupport.jandex") version versionNessieBuildPlugins
    id("org.projectnessie.buildsupport.protobuf") version versionNessieBuildPlugins
    id("org.projectnessie.buildsupport.publishing") version versionNessieBuildPlugins
    id("org.projectnessie.buildsupport.reflectionconfig") version versionNessieBuildPlugins
    id("org.projectnessie.buildsupport.spotless") version versionNessieBuildPlugins
  }

  repositories {
    mavenCentral() // prefer Maven Central, in case Gradle's repo has issues
    gradlePluginPortal()
    if (System.getProperty("withMavenLocal").toBoolean()) {
      mavenLocal()
    }
  }
}

rootProject.name = "cel"

gradle.beforeProject {
  group = "org.projectnessie.cel"
  version = baseVersion
  description =
    when (name) {
      "cel" -> "Common-Expression-Language - Java implementation"
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
