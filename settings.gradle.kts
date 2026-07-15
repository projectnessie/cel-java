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

import com.gradle.develocity.agent.gradle.scan.BuildScanPublishingConfiguration
import java.time.Duration
import org.gradle.api.specs.Spec
import org.gradle.kotlin.dsl.support.serviceOf

if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
  throw GradleException("Build requires Java 17")
}

val baseVersion =
  providers.fileContents(layout.settingsDirectory.file("version.txt")).asText.map { it.trim() }
val isCI = providers.environmentVariable("CI").isPresent
val isBuildScanRequested = gradle.startParameter.isBuildScan
val configurationCacheRequested =
  gradle.serviceOf<BuildFeatures>().configurationCache.requested.getOrElse(false)

if (isCI && configurationCacheRequested) {
  throw GradleException(
    "Gradle configuration cache must not be enabled in CI because it can persist build configuration state to disk."
  )
}

pluginManagement {
  includeBuild("build-logic") { name = "cel-java-build-logic" }

  repositories {
    mavenCentral() // prefer Maven Central, in case Gradle's repo has issues
    gradlePluginPortal()
    if (providers.systemProperty("withMavenLocal").map(String::toBoolean).getOrElse(false)) {
      mavenLocal()
    }
  }
}

plugins {
  id("com.gradle.develocity") version ("4.5.0")
  id("com.gradleup.nmcp.settings") version ("1.6.1")
}

develocity {
  if (isCI) {
    buildScan {
      termsOfUseUrl = "https://gradle.com/terms-of-service"
      termsOfUseAgree = "yes"
      publishing.onlyIf(RequestedBuildScanPublishingSpec(true))
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
          "GITHUB_WORKFLOW",
        )
        .forEach { e ->
          val v = providers.environmentVariable(e).orNull
          if (v != null) {
            value(e, v)
          }
        }
      val ghUrl = providers.environmentVariable("GITHUB_SERVER_URL").orNull
      if (ghUrl != null) {
        val ghRepo = providers.environmentVariable("GITHUB_REPOSITORY").orNull
        val ghRunId = providers.environmentVariable("GITHUB_RUN_ID").orNull
        link("Summary", "$ghUrl/$ghRepo/actions/runs/$ghRunId")
        link("PRs", "$ghUrl/$ghRepo/pulls")
      }
    }
  } else {
    buildScan { publishing.onlyIf(RequestedBuildScanPublishingSpec(isBuildScanRequested)) }
  }
}

class RequestedBuildScanPublishingSpec(private val enabled: Boolean) :
  Spec<BuildScanPublishingConfiguration.PublishingContext> {
  override fun isSatisfiedBy(context: BuildScanPublishingConfiguration.PublishingContext): Boolean =
    enabled
}

rootProject.name = "cel-parent"

// Pass environment variables:
//    ORG_GRADLE_PROJECT_sonatypeUsername
//    ORG_GRADLE_PROJECT_sonatypePassword
// Gradle targets:
//    publishAggregationToCentralPortal
//    publishAggregationToCentralPortalSnapshots
//    (nmcpZipAggregation to just generate the single, aggregated deployment zip)
// Ref: Maven Central Publisher API:
//    https://central.sonatype.org/publish/publish-portal-api/#uploading-a-deployment-bundle
nmcpSettings {
  centralPortal {
    providers.environmentVariable("ORG_GRADLE_PROJECT_sonatypeUsername").orNull?.let(username::set)
    providers.environmentVariable("ORG_GRADLE_PROJECT_sonatypePassword").orNull?.let(password::set)
    publishingType.set(if (isCI) "AUTOMATIC" else "USER_MANAGED")
    publishingTimeout.set(Duration.ofMinutes(120))
    validationTimeout.set(Duration.ofMinutes(120))
    publicationName.set("cel-parent-${baseVersion.get()}")
  }
}

gradle.beforeProject {
  group = "org.projectnessie.cel"
  version = baseVersion.get()
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

celProject("generated-pb3")

celProject("core")

celProject("jackson")

celProject("jackson3")

celProject("conformance")

celProject("tools")

celProject("standalone")

celProject("quarkus-smoke-standalone")

celProject("quarkus-smoke-core-pb3-jackson3")

celProject("bom")
