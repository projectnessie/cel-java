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
  repositories {
    mavenCentral() // prefer Maven Central, in case Gradle's repo has issues
    gradlePluginPortal()
    if (System.getProperty("withMavenLocal").toBoolean()) {
      mavenLocal()
    }
  }
}

plugins { id("com.gradle.develocity") version ("3.17.2") }

develocity {
  if (System.getenv("CI") != null) {
    buildScan {
      termsOfUseUrl = "https://gradle.com/terms-of-service"
      termsOfUseAgree = "yes"
      // Add some potentially interesting information from the environment
      listOf(
          "GITHUB_ACTION_REPOSITORY",
          "GITHUB_ACTOR",
          "GITHUB_BASE_REF",
          "GITHUB_HEAD_REF",
          "GITHUB_JOB",
          "GITHUB_REF",
          "GITHUB_REPOSITORY",
          "GITHUB_RUN_ID",
          "GITHUB_RUN_NUMBER",
          "GITHUB_SHA",
          "GITHUB_WORKFLOW"
        )
        .forEach { e ->
          val v = System.getenv(e)
          if (v != null) {
            value(e, v)
          }
        }
      val ghUrl = System.getenv("GITHUB_SERVER_URL")
      if (ghUrl != null) {
        val ghRepo = System.getenv("GITHUB_REPOSITORY")
        val ghRunId = System.getenv("GITHUB_RUN_ID")
        link("Summary", "$ghUrl/$ghRepo/actions/runs/$ghRunId")
        link("PRs", "$ghUrl/$ghRepo/pulls")
      }
    }
  } else {
    buildScan { publishing { onlyIf { gradle.startParameter.isBuildScan } } }
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
      "standalone" -> "Common-Expression-Language - CEL with relocated protobuf-java"
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

celProject("standalone")

celProject("bom")
