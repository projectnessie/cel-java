/*
 * Copyright (C) 2022 The Authors of CEL-Java
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

import org.jetbrains.gradle.ext.*

plugins {
  signing
  `maven-publish`
  alias(libs.plugins.aggregatetestresults)
  alias(libs.plugins.testsummary)
  alias(libs.plugins.testrerun)
  alias(libs.plugins.nexus.publish)
  `cel-conventions`
}

mapOf("versionJacoco" to libs.versions.jacoco.get(), "versionJandex" to libs.versions.jandex.get())
  .forEach { (k, v) -> extra[k] = v }

tasks.named<Wrapper>("wrapper") { distributionType = Wrapper.DistributionType.ALL }

// Pass environment variables:
//    ORG_GRADLE_PROJECT_sonatypeUsername
//    ORG_GRADLE_PROJECT_sonatypePassword
// OR in ~/.gradle/gradle.properties set
//    sonatypeUsername
//    sonatypePassword
// Call targets:
//    publishToSonatype
//    closeAndReleaseSonatypeStagingRepository
nexusPublishing {
  transitionCheckOptions {
    // default==60 (10 minutes), wait up to 60 minutes
    maxRetries.set(360)
    // default 10s
    delayBetween.set(java.time.Duration.ofSeconds(10))
  }
  repositories { sonatype() }
}

val buildToolIntegrationGradle by
  tasks.creating(Exec::class) {
    group = "Verification"
    description =
      "Checks whether bom works fine with Gradle, requires preceding publishToMavenLocal in a separate Gradle invocation"

    workingDir = file("build-tool-integ-tests")
    commandLine("./gradlew", "jar", "-Dcel.version=${project.version}")
  }

val buildToolIntegrationMaven by
  tasks.creating(Exec::class) {
    group = "Verification"
    description =
      "Checks whether bom works fine with Maven, requires preceding publishToMavenLocal in a separate Gradle invocation"

    workingDir = file("build-tool-integ-tests")
    commandLine("./mvnw", "clean", "package", "-Dcel.version=${project.version}")
  }

val buildToolIntegrations by
  tasks.creating {
    group = "Verification"
    description =
      "Checks whether bom works fine with build tools, requires preceding publishToMavenLocal in a separate Gradle invocation"

    dependsOn(buildToolIntegrationGradle)
    dependsOn(buildToolIntegrationMaven)
  }

publishingHelper {
  nessieRepoName.set("cel-java")
  inceptionYear.set("2021")
}
