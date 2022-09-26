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

  repositories {
    mavenCentral() // prefer Maven Central, in case Gradle's repo has issues
    gradlePluginPortal()
    if (System.getProperty("withMavenLocal").toBoolean()) {
      mavenLocal()
    }
  }

  val versionIdeaExtPlugin = "1.1.6"
  val versionJandexPlugin = "1.82"
  val versionNessieBuildPlugins = "0.2.12"
  val versionShadowPlugin = "7.1.2"
  val versionSpotlessPlugin = "6.11.0"

  plugins {
    id("com.diffplug.spotless") version versionSpotlessPlugin
    id("com.github.johnrengelman.plugin-shadow") version versionShadowPlugin
    id("com.github.vlsi.jandex") version versionJandexPlugin
    id("com.google.protobuf") version "0.8.19"
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("me.champeau.jmh") version "0.6.8"
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
    id("org.projectnessie.smallrye-open-api") version versionNessieBuildPlugins

    versions["versionIdeaExtPlugin"] = versionIdeaExtPlugin
    versions["versionSpotlessPlugin"] = versionSpotlessPlugin
    versions["versionJandexPlugin"] = versionJandexPlugin
    versions["versionShadowPlugin"] = versionShadowPlugin
    versions["versionNessieBuildPlugins"] = versionNessieBuildPlugins

    // The project's settings.gradle.kts is "executed" before buildSrc's settings.gradle.kts and
    // build.gradle.kts.
    //
    // Plugin and important dependency versions are defined here and shared with buildSrc via
    // a properties file, and via an 'extra' property with all other modules of the Nessie build.
    val nessieBuildVersionsFile = file("build/nessieBuild/versions.properties")
    nessieBuildVersionsFile.parentFile.mkdirs()
    nessieBuildVersionsFile.outputStream().use {
      versions.store(it, "Nessie Build versions from settings.gradle.kts - DO NOT MODIFY!")
    }
  }

  repositories {
    mavenCentral() // prefer Maven Central, in case Gradle's repo has issues
    gradlePluginPortal()
    if (System.getProperty("withMavenLocal").toBoolean()) {
      mavenLocal()
    }
  }
}

rootProject.name = "cel-parent"

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

fun celProject(name: String) {
  include("cel-$name")
  project(":cel-$name").projectDir = file(name)
}

celProject("generated-antlr")

celProject("generated-pb")

celProject("core")

celProject("jackson")

celProject("conformance")

celProject("tools")

celProject("jacoco")

celProject("bom")
